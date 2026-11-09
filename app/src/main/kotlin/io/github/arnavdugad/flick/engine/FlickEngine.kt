package io.github.arnavdugad.flick.engine

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import io.github.arnavdugad.flick.action.ActionExecutor
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.core.profiles.ForegroundTracker
import io.github.arnavdugad.flick.core.profiles.ProfileResolver
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

    /** The plan: which triggers each source should run, plus the tuning it was computed with. */
    private data class Plan(val wanted: Set<TriggerType>, val settings: FlickSettings)

    init {
        scope.launch {
            combine(state, settings.settings) { st, s -> Plan(st.wantedTriggers(ProfileResolver.allTriggers(s.profiles)), s) }
                .distinctUntilChanged { a, b ->
                    a.wanted == b.wanted && a.settings.tuning == b.settings.tuning &&
                        a.settings.volumeHoldAdjusts == b.settings.volumeHoldAdjusts
                }
                .collect(::apply)
        }
        scope.launch { settings.settings.collect { latestSettings = it } }
    }

    private fun apply(plan: Plan) {
        volumeKeys.holdAdjustsVolume = plan.settings.volumeHoldAdjusts
        for (source in sources) {
            val wanted = source.triggers intersect plan.wanted
            if (wanted.isEmpty()) {
                source.stop()
            } else {
                source.start(wanted, plan.settings::tuningFor, sink)
            }
        }
        bridge.value?.setKeyEventFiltering(volumeKeys.triggers.any { it in plan.wanted })
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

    fun onWindowStateChanged(packageName: String) {
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

    /** Runs an action directly (Action lab). Returns false if the service is not connected. */
    suspend fun runAction(action: Action): Boolean = bridge.value?.executor?.execute(action) ?: false

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
            val executed = toRun?.let { bridge.value?.executor?.execute(it) } ?: false
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
