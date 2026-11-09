package io.github.arnavdugad.flick.core.detect

/**
 * Detects a hand waved over the proximity sensor: the sensor goes "near" and then "far" again
 * within a short window.
 *
 * - Covers shorter than [Config.minCoverMs] are treated as sensor noise.
 * - Covers longer than [Config.maxCoverMs] are ignored (phone in a pocket, face on a call, resting
 *   a hand), so leaving the sensor covered never fires.
 * - After firing, [Config.cooldownMs] must pass before the next wave counts.
 */
class WaveDetector(config: Config = Config()) {

    data class Config(
        val minCoverMs: Long = 30,
        val maxCoverMs: Long = 900,
        val cooldownMs: Long = 700,
    ) {
        init {
            require(minCoverMs >= 0) { "minCoverMs must be >= 0" }
            require(maxCoverMs > minCoverMs) { "maxCoverMs must be > minCoverMs" }
            require(cooldownMs >= 0) { "cooldownMs must be >= 0" }
        }
    }

    var config: Config = config
        set(value) {
            field = value
            cooldown.durationMs = value.cooldownMs
        }

    private val cooldown = Cooldown(config.cooldownMs)
    private var coveredSinceMs: Long? = null

    /** True while the sensor currently reads "near". */
    val isCovered: Boolean get() = coveredSinceMs != null

    /**
     * Feed one proximity reading. Readings may repeat; only transitions matter.
     * @return true when a wave is recognised.
     */
    fun onSample(near: Boolean, nowMs: Long): Boolean {
        if (near) {
            if (coveredSinceMs == null) coveredSinceMs = nowMs
            return false
        }
        val since = coveredSinceMs ?: return false
        coveredSinceMs = null
        val coveredFor = nowMs - since
        if (coveredFor < config.minCoverMs || coveredFor > config.maxCoverMs) return false
        return cooldown.tryFire(nowMs)
    }

    fun reset() {
        coveredSinceMs = null
        cooldown.reset()
    }
}
