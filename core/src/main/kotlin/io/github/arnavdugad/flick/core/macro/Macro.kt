package io.github.arnavdugad.flick.core.macro

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.hypot

/** A point of a recorded touch, in screen fractions, at [t] ms after the stroke started. */
@Serializable
data class MacroPoint(val x: Float, val y: Float, val t: Long)

/** One finger stroke (tap, long-press or swipe), replayed [delayMs] after the previous one ended. */
@Serializable
data class MacroStep(val points: List<MacroPoint>, val delayMs: Long) {
    val durationMs: Long get() = (points.lastOrNull()?.t ?: 0L).coerceAtLeast(MIN_DURATION_MS)

    companion object {
        const val MIN_DURATION_MS = 40L
    }
}

@Serializable
data class Macro(val id: String, val name: String, val steps: List<MacroStep>)

/**
 * Turns raw touch samples into compact, replayable steps. Fractions keep macros working after
 * rotation; long gaps are capped so a macro never "hangs".
 */
class MacroRecorder(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val maxDelayMs: Long = 3_000,
    private val maxPointsPerStep: Int = 32,
) {
    private val steps = ArrayList<MacroStep>()
    private val current = ArrayList<MacroPoint>()
    private var strokeStartMs = 0L
    private var lastUpMs: Long? = null
    private var pendingDelay = 0L

    val stepCount: Int get() = steps.size

    fun down(xPx: Float, yPx: Float, nowMs: Long) {
        current.clear()
        strokeStartMs = nowMs
        pendingDelay = lastUpMs?.let { (nowMs - it).coerceIn(0, maxDelayMs) } ?: 0L
        current += point(xPx, yPx, nowMs)
    }

    fun move(xPx: Float, yPx: Float, nowMs: Long) {
        if (current.isEmpty()) return
        current += point(xPx, yPx, nowMs)
    }

    /** Ends the stroke and returns the step it produced. */
    fun up(xPx: Float, yPx: Float, nowMs: Long): MacroStep? {
        if (current.isEmpty()) return null
        current += point(xPx, yPx, nowMs)
        val step = MacroStep(simplify(current, maxPointsPerStep), pendingDelay)
        steps += step
        current.clear()
        lastUpMs = nowMs
        return step
    }

    fun cancelStroke() = current.clear()

    fun build(id: String, name: String) = Macro(id, name, steps.toList())

    private fun point(x: Float, y: Float, nowMs: Long) = MacroPoint(
        x = (x / screenWidth).coerceIn(0f, 1f),
        y = (y / screenHeight).coerceIn(0f, 1f),
        t = nowMs - strokeStartMs,
    )

    companion object {
        /**
         * Keeps the first and last points and evenly spaced points in between, dropping points that
         * barely moved. A tap collapses to its start and end point.
         */
        fun simplify(points: List<MacroPoint>, max: Int): List<MacroPoint> {
            if (points.size <= 2) return points.toList()
            val moved = ArrayList<MacroPoint>(points.size)
            moved += points.first()
            for (i in 1 until points.size - 1) {
                val p = points[i]
                val last = moved.last()
                if (hypot(p.x - last.x, p.y - last.y) >= MIN_MOVE || abs(p.t - last.t) >= MIN_TIME_GAP) moved += p
            }
            moved += points.last()
            if (moved.size <= max) return moved
            val out = ArrayList<MacroPoint>(max)
            val step = (moved.size - 1).toFloat() / (max - 1)
            for (i in 0 until max) out += moved[(i * step).toInt().coerceAtMost(moved.size - 1)]
            out[out.lastIndex] = moved.last()
            return out
        }

        private const val MIN_MOVE = 0.004f
        private const val MIN_TIME_GAP = 150L
    }
}
