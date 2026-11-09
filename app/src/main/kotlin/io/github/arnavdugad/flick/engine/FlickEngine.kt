package io.github.arnavdugad.flick.engine

import android.os.SystemClock
import io.github.arnavdugad.flick.action.ActionExecutor
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.data.SettingsRepository
import io.github.arnavdugad.flick.feedback.Haptics
import io.github.arnavdugad.flick.system.ScreenStateMonitor
import io.github.arnavdugad.flick.trigger.TriggerSource
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

/**
 * The heart of Flick: decides which trigger sources run, maps recognised triggers to actions, and
 * runs them through the accessibility service's [ActionExecutor].
 *
 * It is a process-wide singleton; the accessibility service [attach]es its executor when bound and
 * [detach]es it when unbound, so the UI can observe state even when the service is off.
 */
class FlickEngine(
    private val settings: SettingsRepository,
    private val screenState: ScreenStateMonitor,
    private val haptics: Haptics,
    proximity: TriggerSource,
    private val scope: CoroutineScope,
) {

    private val sources: List<TriggerSource> = listOf(proximity)

    private val executor = MutableStateFlow<ActionExecutor?>(null)
    private val practice = MutableStateFlow(false)
    private val foreground = MutableStateFlow<String?>(null)
    private var latestSettings = FlickSettings()

    val state: StateFlow<EngineState> = combine(
        executor,
        settings.settings,
        screenState.state,
        practice,
        foreground,
    ) { ex, s, screen, practicing, pkg ->
        EngineState(
            serviceConnected = ex != null,
            masterEnabled = s.masterEnabled,
            screen = screen,
            practiceMode = practicing,
            foregroundPackage = pkg,
        )
    }.stateIn(scope, SharingStarted.Eagerly, EngineState())

    private val _events = MutableSharedFlow<FiredTrigger>(extraBufferCapacity = 16)

    /** Every recognised trigger, for live feedback in the UI. */
    val events: SharedFlow<FiredTrigger> = _events.asSharedFlow()

    init {
        scope.launch { settings.settings.collect { latestSettings = it } }
        scope.launch {
            state.map { it.listening }.distinctUntilChanged().collect { listen ->
                sources.forEach { source ->
                    if (listen && source.isAvailable) source.start(::onTrigger) else source.stop()
                }
            }
        }
    }

    fun attach(actionExecutor: ActionExecutor) {
        executor.value = actionExecutor
        screenState.refresh()
    }

    fun detach(actionExecutor: ActionExecutor) {
        if (executor.value === actionExecutor) executor.value = null
    }

    fun onWindowStateChanged(packageName: String) {
        foreground.value = packageName
    }

    /** Practice mode: triggers are recognised and reported, but no action runs. */
    fun setPracticeMode(enabled: Boolean) {
        practice.value = enabled
        if (enabled) screenState.refresh()
    }

    fun setMasterEnabled(enabled: Boolean) {
        scope.launch { settings.setMasterEnabled(enabled) }
    }

    /** Runs an action directly (Action lab). Returns false if the service is not connected. */
    suspend fun runAction(action: Action): Boolean = executor.value?.execute(action) ?: false

    private fun actionFor(trigger: TriggerType, s: FlickSettings): Action? = when (trigger) {
        TriggerType.PROXIMITY_WAVE -> s.waveAction
        else -> null
    }

    private fun onTrigger(trigger: TriggerType) {
        val current = state.value
        val s = latestSettings
        val action = actionFor(trigger, s)
        val toRun = action?.takeIf { !current.practiceMode && current.status == EngineStatus.ACTIVE }
        if (s.haptics) {
            if (toRun != null) haptics.confirm() else haptics.tick()
        }
        scope.launch {
            val executed = toRun?.let { executor.value?.execute(it) } ?: false
            _events.emit(
                FiredTrigger(
                    trigger = trigger,
                    action = action,
                    executed = executed,
                    practice = current.practiceMode,
                    atElapsedMs = SystemClock.elapsedRealtime(),
                ),
            )
        }
    }
}
