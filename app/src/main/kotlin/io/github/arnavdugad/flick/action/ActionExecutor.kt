package io.github.arnavdugad.flick.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.util.DisplayMetrics
import android.view.Display
import io.github.arnavdugad.flick.core.geometry.GestureGeometry
import io.github.arnavdugad.flick.core.macro.Macro
import kotlinx.coroutines.delay
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.Direction

/** Performs [Action]s on behalf of the user. Lives only while the accessibility service is bound. */
class ActionExecutor(private val service: AccessibilityService) {

    private val gestures = GestureQueue(service)
    private val media = MediaActions(service.getSystemService(AudioManager::class.java))
    private val displays = service.getSystemService(DisplayManager::class.java)

    fun isSupported(action: Action): Boolean = when (action) {
        is Action.Global -> action.action.isSupported()
        else -> true
    }

    /** @return true if the action was carried out. Never throws. */
    suspend fun execute(action: Action): Boolean = runCatching {
        when (action) {
            is Action.Swipe -> swipe(action.direction, action.distance, action.durationMs)
            is Action.Scroll -> scroll(action.direction)
            is Action.Tap -> withSize { w, h ->
                gestures.dispatch(GestureFactory.tap(GestureGeometry.point(action.x, action.y, w, h)))
            }
            is Action.DoubleTap -> withSize { w, h ->
                gestures.dispatch(GestureFactory.doubleTap(GestureGeometry.point(action.x, action.y, w, h)))
            }
            is Action.LongPress -> withSize { w, h ->
                gestures.dispatch(
                    GestureFactory.longPress(GestureGeometry.point(action.x, action.y, w, h), action.durationMs),
                )
            }
            is Action.Global ->
                action.action.isSupported() && service.performGlobalAction(action.action.platformId())
            is Action.Media -> media.perform(action.command)
            // Flick-level actions are handled by the engine, not the accessibility layer.
            Action.AutoScroll, is Action.RunMacro, Action.PauseFlick -> false
        }
    }.getOrDefault(false)

    private suspend fun swipe(direction: Direction, distance: Float, durationMs: Long): Boolean =
        withSize { w, h ->
            gestures.dispatch(GestureFactory.swipe(GestureGeometry.swipe(direction, distance, w, h), durationMs))
        }

    private suspend fun scroll(direction: Direction): Boolean {
        val byNode = runCatching { NodeScroller.scroll(service.rootInActiveWindow, direction) }
            .getOrDefault(false)
        if (byNode) return true
        // Scrolling content DOWN means the finger travels UP.
        return swipe(direction.opposite, SCROLL_FALLBACK_DISTANCE, Action.DEFAULT_SWIPE_MS)
    }

    /** Screen size in pixels (current rotation), or null if unknown. */
    fun screenSize(): Pair<Int, Int>? {
        val metrics = DisplayMetrics()
        val display = displays?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return if (metrics.widthPixels > 0 && metrics.heightPixels > 0) metrics.widthPixels to metrics.heightPixels else null
    }

    /**
     * One segment of a finger drag at [x] from [fromY] to [toY]. Pass the previous segment to keep
     * the finger down; with [willContinue] false the finger lifts at the end. Returns the stroke to
     * continue from, or null if the gesture was cancelled (e.g. the user touched the screen).
     */
    suspend fun dragSegment(
        previous: GestureDescription.StrokeDescription?,
        x: Float,
        fromY: Float,
        toY: Float,
        durationMs: Long,
        willContinue: Boolean,
    ): GestureDescription.StrokeDescription? {
        val path = Path().apply {
            moveTo(x, fromY)
            lineTo(x, toY)
        }
        val duration = durationMs.coerceIn(1, GestureDescription.getMaxGestureDuration())
        val stroke = runCatching {
            previous?.continueStroke(path, 0, duration, willContinue)
                ?: GestureDescription.StrokeDescription(path, 0, duration, willContinue)
        }.getOrNull() ?: return null
        val ok = gestures.dispatch(GestureDescription.Builder().addStroke(stroke).build())
        return if (ok) stroke else null
    }

    /** Replays a recorded macro step by step. Returns false if any step was cancelled. */
    suspend fun playMacro(macro: Macro): Boolean {
        val (w, h) = screenSize() ?: return false
        for (step in macro.steps) {
            if (step.delayMs > 0) delay(step.delayMs)
            val pts = step.points
            if (pts.isEmpty()) continue
            val path = Path().apply {
                moveTo(pts[0].x * w, pts[0].y * h)
                if (pts.size == 1) lineTo(pts[0].x * w, pts[0].y * h)
                for (i in 1 until pts.size) lineTo(pts[i].x * w, pts[i].y * h)
            }
            val duration = step.durationMs.coerceIn(1, GestureDescription.getMaxGestureDuration())
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            if (!gestures.dispatch(gesture)) return false
        }
        return true
    }

    private inline fun withSize(block: (Int, Int) -> Boolean): Boolean {
        val metrics = DisplayMetrics()
        val display = displays?.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) return false
        return block(metrics.widthPixels, metrics.heightPixels)
    }

    private companion object {
        const val SCROLL_FALLBACK_DISTANCE = 0.35f
    }
}
