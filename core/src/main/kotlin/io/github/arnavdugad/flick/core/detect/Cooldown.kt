package io.github.arnavdugad.flick.core.detect

/**
 * Rate-limits a trigger: after firing, it cannot fire again until [durationMs] has elapsed.
 * Time is supplied by the caller so behaviour is deterministic and testable.
 */
class Cooldown(var durationMs: Long) {

    private var lastFireMs: Long? = null

    fun isReady(nowMs: Long): Boolean {
        val last = lastFireMs ?: return true
        return nowMs - last >= durationMs
    }

    /** Returns true and starts the cooldown if ready; otherwise returns false. */
    fun tryFire(nowMs: Long): Boolean {
        if (!isReady(nowMs)) return false
        lastFireMs = nowMs
        return true
    }

    fun reset() {
        lastFireMs = null
    }
}
