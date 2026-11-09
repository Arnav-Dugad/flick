package io.github.arnavdugad.flick.action

import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import io.github.arnavdugad.flick.core.geometry.ScreenPoint
import io.github.arnavdugad.flick.core.geometry.SwipePath

/** Builds [GestureDescription]s from pixel geometry. Durations are clamped to platform limits. */
object GestureFactory {

    const val TAP_MS = 50L
    /** Gap between the two taps of a double tap; well under the 300 ms double-tap timeout. */
    const val DOUBLE_TAP_GAP_MS = 90L

    fun swipe(path: SwipePath, durationMs: Long): GestureDescription {
        val p = Path().apply {
            moveTo(path.start.x, path.start.y)
            lineTo(path.end.x, path.end.y)
        }
        return GestureDescription.Builder()
            .addStroke(StrokeDescription(p, 0, clampDuration(durationMs)))
            .build()
    }

    fun tap(point: ScreenPoint): GestureDescription =
        GestureDescription.Builder()
            .addStroke(StrokeDescription(pointPath(point), 0, TAP_MS))
            .build()

    fun doubleTap(point: ScreenPoint): GestureDescription =
        GestureDescription.Builder()
            .addStroke(StrokeDescription(pointPath(point), 0, TAP_MS))
            .addStroke(StrokeDescription(pointPath(point), TAP_MS + DOUBLE_TAP_GAP_MS, TAP_MS))
            .build()

    fun longPress(point: ScreenPoint, durationMs: Long): GestureDescription =
        GestureDescription.Builder()
            .addStroke(StrokeDescription(pointPath(point), 0, clampDuration(durationMs)))
            .build()

    private fun pointPath(point: ScreenPoint) = Path().apply {
        moveTo(point.x, point.y)
        lineTo(point.x, point.y)
    }

    private fun clampDuration(ms: Long): Long =
        ms.coerceIn(1L, GestureDescription.getMaxGestureDuration())
}
