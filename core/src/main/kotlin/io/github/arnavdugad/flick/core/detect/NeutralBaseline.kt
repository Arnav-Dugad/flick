package io.github.arnavdugad.flick.core.detect

import kotlin.math.abs
import kotlin.math.exp

/**
 * Tracks the user's resting position of a signal (e.g. how their head or phone is normally held),
 * so triggers measure *changes* from neutral rather than absolute angles.
 *
 * The baseline adapts slowly (time constant [timeConstantMs]) but only while the signal is within
 * [adaptLimit] of it, so a deliberate held tilt is never absorbed into "neutral".
 */
class NeutralBaseline(
    var adaptLimit: Float,
    val timeConstantMs: Long = 4_000,
) {
    private var value: Float? = null
    private var lastMs: Long? = null

    val current: Float? get() = value

    /** Feeds a sample and returns the signal relative to the baseline. */
    fun update(sample: Float, nowMs: Long): Float {
        val base = value
        if (base == null) {
            value = sample
            lastMs = nowMs
            return 0f
        }
        val dt = (nowMs - (lastMs ?: nowMs)).coerceAtLeast(0)
        lastMs = nowMs
        val delta = sample - base
        if (abs(delta) <= adaptLimit && dt > 0) {
            val alpha = 1f - exp(-dt.toFloat() / timeConstantMs)
            value = base + delta * alpha
        }
        return sample - (value ?: sample)
    }

    /** Snap the baseline to [sample] (explicit "set neutral"). */
    fun set(sample: Float, nowMs: Long) {
        value = sample
        lastMs = nowMs
    }

    fun reset() {
        value = null
        lastMs = null
    }
}
