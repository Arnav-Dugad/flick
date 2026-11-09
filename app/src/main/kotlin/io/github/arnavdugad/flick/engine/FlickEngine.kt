package io.github.arnavdugad.flick.engine

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import io.github.arnavdugad.flick.action.ActionExecutor
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.core.profiles.ForegroundTracker
import io.github.arnavdugad.flick.core.profiles.ProfileKind
import io.github.arnavdugad.flick.core.profiles.ProfileResolver
import io.github.arnavdugad.flick.feature.autoscroll.AutoScrollStatus
import io.github.arnavdugad.flick.feature.autoscroll.AutoScroller
import io.github.arnavdugad.flick.trigger.camera.FaceTriggerSource
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.data.SettingsRepository
import io.github.arnavdugad.flick.feedback.Haptics
import io.github.arnavdugad.flick.system.ScreenStateMonitor
import io.github.arnavdugad.flick.trigger.SourceStatus
import io.github.arnavdugad.flick.trigger.TriggerSink
import io.github.arnavdugad.flick.trigger.TriggerSource
import io.github.arnavdugad.flick.trigger.keys.VolumeKeyTriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the engine needs from the bound accessibility service. */
interface ServiceBridge {
    val executor: ActionExecutor

    /** Volume-key triggers need the key-event filter; it is only switched on while required. */
    fun setKeyEventFiltering(enabled: Boolean)
}

/**
 * The heart of Flick: decides which trigger sources run, maps recognised triggers to actions, and
 * runs them through the accessibility service.
 *
 * A source runs only while one of its triggers is wanted: bound and the engine active (or the user
 * is practising), or open on the calibration screen. That rule is what keeps the camera and
 * microphone off whenever they aren't needed.
 */
class FlickEngine(
    private val settings: SettingsRepository,
    private val screenState: ScreenStateMonitor,
    private val haptics: Haptics,
    private val sources: List<TriggerSource>,
    private val volumeKeys: VolumeKeyTriggerSource,
    private val face: FaceTriggerSource,
    val autoScroller: AutoScroller,
    private val scope: CoroutineScope,
    /** Flick's own package: nothing ever runs while Flick itself is in front. */
    private val ownPackage: String,
    /** Overlays that don't count as switching apps (system UI, keyboards). */
    transientPackages: Set<String>,
) {

    private val main = Handler(Looper.getMainLooper())
    private val bridge = MutableStateFlow<ServiceBridge?>(null)
    private val practice = MutableStateFlow(false)
    private val calibrating = MutableStateFlow<TriggerType?>(null)
    private val foreground = MutableStateFlow<String?>(null)
    private val foregroundTracker = ForegroundTracker(transientPackages)
    private var latestSettings = FlickSettings()

    private val sourceByTrigger: Map<TriggerType, TriggerSource> =
        sources.flatMap { s -> s.triggers.map { it to s } }.toMap()

    val state: StateFlow<EngineState> = combine(
        bridge, settings.settings, screenState.state, practice, calibrating,
    ) { b, s, screen, practicing, calibrated ->
        EngineState(
            serviceConnected = b != null,
            masterEnabled = s.masterEnabled,
            screen = screen,
            practiceMode = practicing,
            calibrating = calibrated,
        )
    }.combine(foreground) { st, pkg -> st.copy(foregroundPackage = pkg) }
        .combine(settings.settings) { st, s ->
            st.copy(activeProfile = ProfileResolver.resolve(st.foregroundPackage, s.profiles, setOf(ownPackage)))
        }
        .stateIn(scope, SharingStarted.Eagerly, EngineState())

    /** Live status of the source behind every trigger. */
    val statuses: StateFlow<Map<TriggerType, SourceStatus>> =
        combine(sources.map { it.status }) { values ->
            buildMap {
                sources.forEachIndexed { i, source -> source.triggers.forEach { put(it, values[i]) } }
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _events = MutableSharedFlow<FiredTrigger>(extraBufferCapacity = 16)

    /** Every recognised trigger, for live feedback in the UI. */
    val events: SharedFlow<FiredTrigger> = _events.asSharedFlow()

    private val sink = TriggerSink { type -> main.post { onTrigger(type) } }

    /**
     * The plan: which triggers each source should run, the tuning it was computed with, and whether
     * the face tracker must run for auto-scroll's "pause when you look away" (profile apps only).
     */
    private data class Plan(val wanted: Set<TriggerType>, val settings: FlickSettings, val faceTracking: Boolean)

    init {
        scope.launch {
            combine(state, settings.settings, autoScroller.status) { st, s, scroll ->
                val faceTracking = scroll != AutoScrollStatus.OFF && s.autoScroll.pauseOnFaceAway &&
                    st.screen.usable && st.activeProfile?.kind == ProfileKind.APPS
                Plan(st.wantedTriggers(ProfileResolver.allTriggers(s.profiles)), s, faceTracking)
            }
                .distinctUntilChanged { a, b ->
                    a.wanted == b.wanted && a.settings.tuning == b.settings.tuning &&
                        a.settings.volumeHoldAdjusts == b.settings.volumeHoldAdjusts && a.faceTracking == b.faceTracking
                }
                .collect(::apply)
        }
        autoScroller.executor = { bridge.value?.executor }
        // Auto-scroll belongs to one app: stop it when the app changes, the screen sleeps or Flick pauses.
        scope.launch {
            state.map { it.status to it.foregroundPackage }.distinctUntilChanged().collect { (status, _) ->
                // Any change of app or status ends auto-scroll; starting it never changes either.
                if (autoScroller.isActive || status != EngineStatus.ACTIVE) autoScroller.stop()
            }
        }
        scope.launch { settings.settings.collect { latestSettings = it } }
    }

    private fun apply(plan: Plan) {
        volumeKeys.holdAdjustsVolume = plan.settings.volumeHoldAdjusts
        for (source in sources) {
            val wanted = source.triggers intersect plan.wanted
            if (source === face && wanted.isEmpty() && plan.faceTracking) {
                source.start(emptySet(), plan.settings::tuningFor, sink)
            } else if (wanted.isEmpty()) {
                source.stop()
            } else {
                source.start(wanted, plan.settings::tuningFor, sink)
            }
        }
        bridge.value?.setKeyEventFiltering(volumeKeys.triggers.any { it in plan.wanted })
        autoScroller.facePresent = if (plan.faceTracking) face.facePresent else null
    }

    fun attach(serviceBridge: ServiceBridge) {
        bridge.value = serviceBridge
        screenState.refresh()
        serviceBridge.setKeyEventFiltering(volumeKeys.triggers.any { it in currentWanted() })
    }

    fun detach(serviceBridge: ServiceBridge) {
        if (bridge.value === serviceBridge) bridge.value = null
    }

    private fun currentWanted(): Set<TriggerType> =
        state.value.wantedTriggers(ProfileResolver.allTriggers(latestSettings.profiles))

    private val _lastApp = MutableStateFlow<String?>(null)

    /** The most recent app in front other than Flick itself, for the Home screen. */
    val lastApp: StateFlow<String?> = _lastApp

    /**
     * True while one of Flick's own activities is on screen. Flick's overlay windows (floating
     * controller, badge, glow) also report window changes under Flick's package; those must not
     * count as "the user switched to Flick", or the active profile would flip off and on.
     */
    @Volatile
    var appUiVisible: Boolean = false
        set(value) {
            field = value
            if (value) onWindowStateChanged(ownPackage)
        }

    fun onWindowStateChanged(packageName: String) {
        if (packageName == ownPackage && !appUiVisible) return
        if (foregroundTracker.onWindow(packageName)) {
            val pkg = foregroundTracker.current
            foreground.value = pkg
            if (pkg != null && pkg != ownPackage) _lastApp.value = pkg
        }
    }

    /** From the accessibility service's key filter. Returns true to consume the key. */
    fun onKeyEvent(event: KeyEvent): Boolean = volumeKeys.onKeyEvent(event)

    /** Practice mode: triggers are recognised and reported, but no action runs. */
    fun setPracticeMode(enabled: Boolean) {
        practice.value = enabled
        if (enabled) screenState.refresh()
    }

    /** Calibration screen open for [type] (or closed when null). */
    fun setCalibrating(type: TriggerType?) {
        calibrating.value = type
        if (type != null) screenState.refresh()
    }

    fun setMasterEnabled(enabled: Boolean) {
        scope.launch { settings.setMasterEnabled(enabled) }
    }

    fun sourceFor(type: TriggerType): TriggerSource? = sourceByTrigger[type]

    fun status(type: TriggerType): StateFlow<SourceStatus>? = sourceByTrigger[type]?.status

    fun level(type: TriggerType): Float = sourceByTrigger[type]?.level(type) ?: 0f

    fun setNeutral(type: TriggerType) {
        sourceByTrigger[type]?.setNeutral()
    }

    /** Re-check permissions/hardware for every source (after returning from a prompt). */
    fun refreshSources() {
        sources.forEach { it.refreshStatus() }
    }

    /** Runs an action directly (Action lab, floating controller). False if the service is off. */
    suspend fun runAction(action: Action): Boolean = when (action) {
        Action.PauseFlick -> {
            settings.setMasterEnabled(false)
            true
        }
        Action.AutoScroll -> {
            autoScroller.toggle()
            true
        }
        is Action.RunMacro -> {
            val macro = latestSettings.macro(action.macroId)
            val ex = bridge.value?.executor
            macro != null && ex != null && ex.playMacro(macro)
        }
        else -> bridge.value?.executor?.execute(action) ?: false
    }

    /** The executor while the accessibility service is connected (overlays, macro recorder). */
    val executor: ActionExecutor? get() = bridge.value?.executor

    private fun onTrigger(trigger: TriggerType) {
        val current = state.value
        val s = latestSettings
        if (trigger !in current.wantedTriggers(ProfileResolver.allTriggers(s.profiles))) return
        val action = current.activeBindings[trigger]
        val toRun = action?.takeIf { current.actionsEnabled }
        if (s.haptics) {
            if (toRun != null) haptics.confirm() else haptics.tick()
        }
        scope.launch {
            val executed = when (toRun) {
                null -> false
                Action.PauseFlick -> {
                    settings.setMasterEnabled(false)
                    true
                }
                Action.AutoScroll -> {
                    autoScroller.toggle()
                    true
                }
                is Action.RunMacro -> {
                    val macro = s.macro(toRun.macroId)
                    val ex = bridge.value?.executor
                    macro != null && ex != null && ex.playMacro(macro)
                }
                else -> bridge.value?.executor?.execute(toRun) ?: false
            }
            _events.emit(
                FiredTrigger(
                    trigger = trigger,
                    action = action,
                    executed = executed,
                    practice = !current.actionsEnabled,
                    atElapsedMs = SystemClock.elapsedRealtime(),
                ),
            )
        }
    }
}
