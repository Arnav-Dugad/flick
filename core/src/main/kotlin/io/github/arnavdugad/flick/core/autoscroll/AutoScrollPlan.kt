package io.github.arnavdugad.flick.core.autoscroll

import kotlinx.serialization.Serializable

@Serializable
enum class AutoScrollMode {
    /** Smooth, continuous crawl for articles. */
    READING,

    /** Very smooth and slow, for reading aloud. */
    TELEPROMPTER,

    /** A third of a page every few seconds, for recipes. */
    COOKING,
}

@Serializable
data class AutoScrollSettings(
    val mode: AutoScrollMode = AutoScrollMode.READING,
    /** 1..10. */
    val speed: Int = 4,
    /** Pause while no face is looking at the screen (needs the camera, profile apps only). */
    val pauseOnFaceAway: Boolean = false,
) {
    fun sanitized() = copy(speed = speed.coerceIn(MIN_SPEED, MAX_SPEED))

    companion object {
        const val MIN_SPEED = 1
        const val MAX_SPEED = 10
    }
}

/** One finger segment of an auto-scroll, in pixels. */
data class ScrollSegment(val fromY: Float, val toY: Float, val durationMs: Long, val lift: Boolean)

/**
 * Plans auto-scroll as a finger dragging content upwards. Continuous modes chain short segments
 * without lifting the finger, then hold still briefly before lifting so the app doesn't fling; the
 * finger is re-placed lower down when it nears the top. Cooking mode does one measured step every
 * few seconds.
 */
class AutoScrollPlan(private val screenHeight: Int, settings: AutoScrollSettings) {

    private val s = settings.sanitized()
    private val top = screenHeight * TOP
    private val bottom = screenHeight * BOTTOM
    private var y = bottom

    /** Continuous speed in pixels per second. */
    val pixelsPerSecond: Float = when (s.mode) {
        AutoScrollMode.READING -> screenHeight * (0.02f + s.speed * 0.012f)
        AutoScrollMode.TELEPROMPTER -> screenHeight * (0.008f + s.speed * 0.006f)
        AutoScrollMode.COOKING -> 0f
    }

    /** Pause between cooking steps. */
    val stepIntervalMs: Long = (22_000L - s.speed * 2_000L).coerceAtLeast(2_000L)

    val isStepMode: Boolean get() = s.mode == AutoScrollMode.COOKING

    /** The next segment. After a segment with [ScrollSegment.lift], call [next] to start afresh. */
    fun next(): ScrollSegment {
        if (isStepMode) {
            val distance = screenHeight * STEP_FRACTION
            return ScrollSegment(bottom, bottom - distance, STEP_DURATION_MS, lift = true)
        }
        val travel = pixelsPerSecond * SEGMENT_MS / 1_000f
        val from = y
        val to = from - travel
        return if (to <= top) {
            y = bottom
            ScrollSegment(from, top.coerceAtMost(from), SEGMENT_MS, lift = true)
        } else {
            y = to
            ScrollSegment(from, to, SEGMENT_MS, lift = false)
        }
    }

    /** Restart from the bottom (e.g. after the user touched the screen). */
    fun reset() {
        y = bottom
    }

    companion object {
        const val SEGMENT_MS = 250L
        const val HOLD_BEFORE_LIFT_MS = 120L
        private const val STEP_DURATION_MS = 450L
        private const val STEP_FRACTION = 0.33f
        private const val TOP = 0.28f
        private const val BOTTOM = 0.78f
    }
}
