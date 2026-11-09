package io.github.arnavdugad.flick.core.geometry

import io.github.arnavdugad.flick.core.model.Direction

data class ScreenPoint(val x: Float, val y: Float)

data class SwipePath(val start: ScreenPoint, val end: ScreenPoint)

/**
 * Converts fraction-based actions into pixel coordinates. Swipes are centred on the screen and
 * kept away from the edges so they never trigger the system back/home edge gestures.
 */
object GestureGeometry {

    /** Fraction of each screen dimension kept clear at the edges. */
    const val EDGE_MARGIN = 0.1f
    private const val MIN_DISTANCE = 0.05f

    fun swipe(direction: Direction, distance: Float, width: Int, height: Int): SwipePath {
        require(width > 0 && height > 0) { "Screen size must be positive" }
        val d = distance.coerceIn(MIN_DISTANCE, 1f - 2 * EDGE_MARGIN)
        val cx = width / 2f
        val cy = height / 2f
        val halfV = d * height / 2f
        val halfH = d * width / 2f
        return when (direction) {
            Direction.UP -> SwipePath(ScreenPoint(cx, cy + halfV), ScreenPoint(cx, cy - halfV))
            Direction.DOWN -> SwipePath(ScreenPoint(cx, cy - halfV), ScreenPoint(cx, cy + halfV))
            Direction.LEFT -> SwipePath(ScreenPoint(cx + halfH, cy), ScreenPoint(cx - halfH, cy))
            Direction.RIGHT -> SwipePath(ScreenPoint(cx - halfH, cy), ScreenPoint(cx + halfH, cy))
        }
    }

    /** A point from fractions of the screen, clamped inside the screen bounds. */
    fun point(xFraction: Float, yFraction: Float, width: Int, height: Int): ScreenPoint {
        require(width > 0 && height > 0) { "Screen size must be positive" }
        val x = (xFraction.coerceIn(0f, 1f) * width).coerceIn(1f, width - 1f)
        val y = (yFraction.coerceIn(0f, 1f) * height).coerceIn(1f, height - 1f)
        return ScreenPoint(x, y)
    }
}
