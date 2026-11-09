package io.github.arnavdugad.flick.feature.autoscroll

import android.accessibilityservice.GestureDescription
import io.github.arnavdugad.flick.action.ActionExecutor
import io.github.arnavdugad.flick.core.autoscroll.AutoScrollPlan
import io.github.arnavdugad.flick.core.autoscroll.AutoScrollSettings
import io.github.arnavdugad.flick.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AutoScrollStatus {
    OFF,
    RUNNING,

    /** Paused from the controls. */
    PAUSED,

    /** Waiting for a face to look at the screen again. */
    WAITING_FOR_FACE,

    /** The user touched the screen; resumes by itself in a moment. */
    TOUCHED,
}

/**
 * Scrolls the current app on its own by driving a slow finger drag (see [AutoScrollPlan]). Stops
 * when the app changes, the screen turns off or Flick pauses; the engine calls [stop] for those.
 */
class AutoScroller(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val _status = MutableStateFlow(AutoScrollStatus.OFF)
    val status: StateFlow<AutoScrollStatus> = _status.asStateFlow()

    private val _speed = MutableStateFlow(AutoScrollSettings().speed)
    val speed: StateFlow<Int> = _speed.asStateFlow()

    /** Supplied by the engine: the accessibility executor (null when the service is off). */
    var executor: () -> ActionExecutor? = { null }

    /** Supplied by the engine when face-away pausing is possible (camera allowed here). */
    var facePresent: StateFlow<Boolean>? = null

    private var job: Job? = null
    private var userPaused = false

    val isActive: Boolean get() = _status.value != AutoScrollStatus.OFF

    fun toggle() = if (isActive) stop() else start()

    fun start() {
        if (job?.isActive == true) return
        userPaused = false
        job = scope.launch { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = AutoScrollStatus.OFF
    }

    fun pauseOrResume() {
        if (!isActive) return
        userPaused = !userPaused
    }

    fun changeSpeed(delta: Int) {
        scope.launch {
            val s = settings.settings.first().autoScroll
            val next = (s.speed + delta).coerceIn(AutoScrollSettings.MIN_SPEED, AutoScrollSettings.MAX_SPEED)
            settings.setAutoScroll(s.copy(speed = next))
        }
    }

    private suspend fun run() {
        var finger: GestureDescription.StrokeDescription? = null
        var fingerY = 0f
        var planned: AutoScrollSettings? = null
        var plan: AutoScrollPlan? = null
        try {
            while (scope.isActive) {
                val ex = executor() ?: break
                val (w, h) = ex.screenSize() ?: break
                val s = settings.settings.first().autoScroll.sanitized()
                _speed.value = s.speed
                if (s != planned) {
                    planned = s
                    plan = AutoScrollPlan(h, s)
                }
                val p = plan ?: break
                val x = w / 2f
                val faceAway = s.pauseOnFaceAway && facePresent?.value == false
                if (userPaused || faceAway) {
                    finger = lift(ex, finger, x, fingerY)
                    _status.value = if (userPaused) AutoScrollStatus.PAUSED else AutoScrollStatus.WAITING_FOR_FACE
                    delay(POLL_MS)
                    continue
                }
                _status.value = AutoScrollStatus.RUNNING
                val seg = p.next()
                if (p.isStepMode) {
                    val moved = ex.dragSegment(null, x, seg.fromY, seg.toY, seg.durationMs, willContinue = true)
                    if (moved == null || lift(ex, moved, x, seg.toY) != null) {
                        touched(p)
                        continue
                    }
                    delay(p.stepIntervalMs)
                } else {
                    val next = ex.dragSegment(finger, x, seg.fromY, seg.toY, seg.durationMs, willContinue = true)
                    if (next == null) {
                        finger = null
                        touched(p)
                        continue
                    }
                    fingerY = seg.toY
                    finger = if (seg.lift) lift(ex, next, x, seg.toY) else next
                }
            }
        } finally {
            _status.value = AutoScrollStatus.OFF
        }
    }

    /** Holds the finger still briefly so the app doesn't fling, then lifts it. Returns null. */
    private suspend fun lift(
        ex: ActionExecutor,
        finger: GestureDescription.StrokeDescription?,
        x: Float,
        y: Float,
    ): GestureDescription.StrokeDescription? {
        if (finger == null) return null
        ex.dragSegment(finger, x, y, y, AutoScrollPlan.HOLD_BEFORE_LIFT_MS, willContinue = false)
        return null
    }

    private suspend fun touched(plan: AutoScrollPlan) {
        _status.value = AutoScrollStatus.TOUCHED
        plan.reset()
        delay(TOUCH_PAUSE_MS)
    }

    private companion object {
        const val POLL_MS = 250L
        const val TOUCH_PAUSE_MS = 2_500L
    }
}
