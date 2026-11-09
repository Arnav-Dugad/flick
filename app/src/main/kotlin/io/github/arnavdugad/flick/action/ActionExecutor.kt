package io.github.arnavdugad.flick.action

import android.accessibilityservice.AccessibilityService
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.util.DisplayMetrics
import android.view.Display
import io.github.arnavdugad.flick.core.geometry.GestureGeometry
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
