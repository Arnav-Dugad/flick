package io.github.arnavdugad.flick.core.detect

/**
 * Fires when a normalised signal stays at or above [threshold] for [holdMs].
 *
 * - **Hysteresis:** brief dips between `threshold * releaseRatio` and `threshold` don't reset the
 *   hold timer, so a noisy signal still counts as "held".
 * - **Neutral gate:** after firing, the detector stays latched until the signal falls below
 *   `threshold * releaseRatio`. Holding a smile fires once, not repeatedly.
 * - **Cooldown:** at most one firing per [cooldownMs].
 */
class LevelDetector(
    threshold: Float,
    var holdMs: Long,
    cooldownMs: Long,
    val releaseRatio: Float = 0.6f,
) {
    init {
        require(releaseRatio in 0f..1f) { "releaseRatio must be in 0..1" }
    }

    var threshold: Float = threshold
        set(value) {
            field = value.coerceIn(MIN_THRESHOLD, 1f)
        }

    var cooldownMs: Long
        get() = cooldown.durationMs
        set(value) {
            cooldown.durationMs = value
        }

    private val cooldown = Cooldown(cooldownMs)
    private var aboveSinceMs: Long? = null
    private var latched = false

    init {
        this.threshold = threshold
    }

    /** True after firing until the signal returns to neutral. */
    val isLatched: Boolean get() = latched

    fun onSample(level: Float, nowMs: Long): Boolean {
        val release = threshold * releaseRatio
        if (latched) {
            if (level < release) {
                latched = false
                aboveSinceMs = null
            }
            return false
        }
        if (level >= threshold) {
            val since = aboveSinceMs ?: nowMs.also { aboveSinceMs = it }
            if (nowMs - since >= holdMs && cooldown.tryFire(nowMs)) {
                latched = true
                return true
            }
        } else if (level < release) {
            aboveSinceMs = null
        }
        return false
    }

    fun reset() {
        aboveSinceMs = null
        latched = false
        cooldown.reset()
    }

    private companion object {
        const val MIN_THRESHOLD = 0.01f
    }
}
