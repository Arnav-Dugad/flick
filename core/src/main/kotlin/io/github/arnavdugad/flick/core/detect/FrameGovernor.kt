package io.github.arnavdugad.flick.core.detect

/**
 * Decides which camera frames are worth analysing. Runs at [activeFps] while a face or hand is in
 * view and drops to [idleFps] after [idleAfterMs] without one. [throttle] (0..1] scales the rate
 * down under thermal pressure or battery saver.
 */
class FrameGovernor(
    var activeFps: Int = 15,
    var idleFps: Int = 4,
    val idleAfterMs: Long = 5_000,
) {
    var throttle: Float = 1f
        set(value) {
            field = value.coerceIn(0.1f, 1f)
        }

    private var lastAcceptedMs: Long? = null
    private var nextDueMs: Float? = null
    private var lastSubjectMs: Long? = null

    val isIdle: Boolean get() = idle(lastAcceptedMs ?: 0)

    private fun idle(nowMs: Long): Boolean {
        val seen = lastSubjectMs ?: return false
        return nowMs - seen > idleAfterMs
    }

    fun currentFps(nowMs: Long): Float = (if (idle(nowMs)) idleFps else activeFps) * throttle

    /** True if the frame at [nowMs] should be analysed. */
    fun accept(nowMs: Long): Boolean {
        if (lastSubjectMs == null) lastSubjectMs = nowMs
        val interval = 1_000f / currentFps(nowMs)
        val due = nextDueMs
        if (due != null && nowMs < due) return false
        // Schedule from the due time (not the frame time) so the average rate matches the target
        // even when camera frames don't line up with it; resync after long gaps.
        nextDueMs = if (due == null || nowMs - due > interval) nowMs + interval else due + interval
        lastAcceptedMs = nowMs
        return true
    }

    /** A face or hand was found; keep the full rate. */
    fun onSubjectSeen(nowMs: Long) {
        lastSubjectMs = nowMs
    }

    fun reset() {
        lastAcceptedMs = null
        nextDueMs = null
        lastSubjectMs = null
    }
}
