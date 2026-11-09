package io.github.arnavdugad.flick.core.detect

/**
 * Groups discrete pulses (blinks, knocks) into sequences. Pulses closer than [minGapMs] are merged
 * as bounce. A sequence ends when no pulse arrives within [maxGapMs]; [tick] then reports how many
 * pulses it contained.
 */
class PulseSequence(
    val minGapMs: Long,
    val maxGapMs: Long,
) {
    init {
        require(minGapMs in 0 until maxGapMs) { "need 0 <= minGapMs < maxGapMs" }
    }

    private var count = 0
    private var lastPulseMs: Long? = null

    val pending: Int get() = count

    /** Registers a pulse. Returns false if it was ignored as bounce. */
    fun pulse(nowMs: Long): Boolean {
        val last = lastPulseMs
        if (last != null && nowMs - last < minGapMs) return false
        if (last != null && nowMs - last > maxGapMs) count = 0
        count++
        lastPulseMs = nowMs
        return true
    }

    /** Call regularly. Returns the pulse count once a sequence has ended, otherwise null. */
    fun tick(nowMs: Long): Int? {
        val last = lastPulseMs ?: return null
        if (count == 0 || nowMs - last <= maxGapMs) return null
        val finished = count
        count = 0
        lastPulseMs = null
        return finished
    }

    fun reset() {
        count = 0
        lastPulseMs = null
    }
}
