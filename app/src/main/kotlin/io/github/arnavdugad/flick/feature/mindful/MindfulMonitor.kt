package io.github.arnavdugad.flick.feature.mindful

import android.os.SystemClock
import io.github.arnavdugad.flick.core.mindful.MindfulTracker
import io.github.arnavdugad.flick.core.mindful.Nudge
import io.github.arnavdugad.flick.core.mindful.NudgeKind
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.Direction
import io.github.arnavdugad.flick.core.profiles.ProfileTemplates
import io.github.arnavdugad.flick.data.SettingsRepository
import io.github.arnavdugad.flick.engine.FlickEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class MindfulStats(val reels: Int = 0, val minutes: Int = 0)

/**
 * Mindful mode: counts short videos Flick advanced and time spent in short-video apps, then nudges
 * (or pauses Flick) at the user's limits. Counting happens even when mindful mode is off so the
 * stats are there when it's turned on; nothing is stored or sent anywhere.
 */
class MindfulMonitor(
    private val scope: CoroutineScope,
    private val engine: FlickEngine,
    private val settings: SettingsRepository,
) {
    private val tracker = MindfulTracker()

    private val _stats = MutableStateFlow(MindfulStats())
    val stats: StateFlow<MindfulStats> = _stats.asStateFlow()

    private val _nudges = MutableSharedFlow<Nudge>(extraBufferCapacity = 2)
    val nudges: SharedFlow<Nudge> = _nudges.asSharedFlow()

    private val shortVideoApps: Set<String> =
        ProfileTemplates.REELS.packages + ProfileTemplates.YOUTUBE.packages

    fun start() {
        scope.launch {
            engine.events.collect { e ->
                val next = e.action is Action.Swipe && e.action.direction == Direction.UP
                if (e.executed && next && engine.state.value.foregroundPackage in shortVideoApps) {
                    handle(tracker.onReel(SystemClock.elapsedRealtime(), settings.settings.first().mindful))
                }
            }
        }
        scope.launch {
            while (true) {
                val st = engine.state.value
                val inApp = st.screen.usable && st.foregroundPackage in shortVideoApps
                handle(tracker.onTick(SystemClock.elapsedRealtime(), inApp, settings.settings.first().mindful))
                delay(TICK_MS)
            }
        }
    }

    private fun handle(nudge: Nudge?) {
        _stats.value = MindfulStats(tracker.reels, tracker.minutes)
        if (nudge == null) return
        if (nudge.kind == NudgeKind.AUTO_PAUSED) engine.setMasterEnabled(false)
        _nudges.tryEmit(nudge)
    }

    private companion object {
        const val TICK_MS = 5_000L
    }
}
