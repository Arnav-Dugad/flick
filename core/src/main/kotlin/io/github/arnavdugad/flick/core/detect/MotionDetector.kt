package io.github.arnavdugad.flick.core.detect

import io.github.arnavdugad.flick.core.model.TriggerSpecs
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Phone-motion triggers from the raw accelerometer (m/s², device axes): tilt, shake and knocks on
 * the back. A low-pass filter separates gravity (orientation) from linear acceleration (motion).
 */
class MotionDetector {

    private var active: Set<TriggerType> = emptySet()

    private val gravity = FloatArray(3)
    private var hasGravity = false
    private var lastMs: Long? = null
    private var stillness = 0f

    private val rollBase = NeutralBaseline(adaptLimit = 8f, timeConstantMs = 5_000)
    private val pitchBase = NeutralBaseline(adaptLimit = 8f, timeConstantMs = 5_000)
    private val tilt = HashMap<TriggerType, LevelDetector>()
    private val shake = ShakeDetector(threshold = 0.5f, cooldownMs = 1_500)
    private val knock = KnockDetector(threshold = 0.3f, cooldownMs = 800)
    private val levels = HashMap<TriggerType, Float>()

    /** Latest linear acceleration (m/s², gravity removed), for diagnostics. */
    val linear = FloatArray(3)

    /** Smoothed motion energy used to reject knocks while the phone is moving. */
    val motionEnergy: Float get() = stillness

    /** True when knocks are wanted; the sensor then needs its fastest rate. */
    val needsHighRate: Boolean get() = active.any { it in KNOCKS }

    fun level(type: TriggerType): Float = levels[type] ?: 0f

    fun configure(active: Set<TriggerType>, tuning: (TriggerType) -> TriggerTuning) {
        this.active = active intersect MOTION_TRIGGERS
        for (type in TILTS) {
            val t = tuning(type)
            val threshold = TriggerSpecs[type].threshold(t.sensitivity)
            val d = tilt.getOrPut(type) { LevelDetector(threshold, t.holdMs, t.cooldownMs) }
            d.threshold = threshold
            d.holdMs = t.holdMs
            d.cooldownMs = t.cooldownMs
        }
        tuning(TriggerType.PHONE_SHAKE).let {
            shake.threshold = TriggerSpecs[TriggerType.PHONE_SHAKE].threshold(it.sensitivity)
            shake.cooldownMs = it.cooldownMs
        }
        val knockType = if (TriggerType.BACK_DOUBLE_KNOCK in this.active) {
            TriggerType.BACK_DOUBLE_KNOCK
        } else {
            TriggerType.BACK_TRIPLE_KNOCK
        }
        tuning(knockType).let {
            knock.threshold = TriggerSpecs[knockType].threshold(it.sensitivity)
            knock.cooldownMs = it.cooldownMs
        }
    }

    /** Re-centre tilt on the current orientation. */
    fun setNeutral(nowMs: Long) {
        if (!hasGravity) return
        rollBase.set(rollDeg(), nowMs)
        pitchBase.set(pitchDeg(), nowMs)
    }

    fun onAccel(x: Float, y: Float, z: Float, nowMs: Long): List<TriggerType> {
        val dt = (nowMs - (lastMs ?: nowMs)).coerceIn(0, 200)
        lastMs = nowMs
        if (!hasGravity) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            hasGravity = true
        } else {
            val a = 1f - exp(-dt / GRAVITY_TAU_MS)
            gravity[0] += (x - gravity[0]) * a
            gravity[1] += (y - gravity[1]) * a
            gravity[2] += (z - gravity[2]) * a
        }
        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        val linear = sqrt(lx * lx + ly * ly + lz * lz)
        this.linear[0] = lx
        this.linear[1] = ly
        this.linear[2] = lz

        val fired = ArrayList<TriggerType>(1)

        // Tilt, relative to an adaptive neutral, full scale 60°.
        val roll = rollBase.update(rollDeg(), nowMs) / TILT_FULL_SCALE
        val pitch = pitchBase.update(pitchDeg(), nowMs) / TILT_FULL_SCALE
        val tiltSignals = mapOf(
            TriggerType.PHONE_TILT_LEFT to roll,
            TriggerType.PHONE_TILT_RIGHT to -roll,
            TriggerType.PHONE_TILT_BACK to pitch,
            TriggerType.PHONE_TILT_FORWARD to -pitch,
        )
        for ((type, value) in tiltSignals) {
            val level = value.coerceIn(0f, 1f)
            levels[type] = level
            if (type in active && tilt[type]?.onSample(level, nowMs) == true) fired += type
        }

        val shakeLevel = (linear / SHAKE_FULL_SCALE).coerceIn(0f, 1f)
        levels[TriggerType.PHONE_SHAKE] = shakeLevel
        if (TriggerType.PHONE_SHAKE in active && shake.onSample(lx, ly, lz, shakeLevel, nowMs)) {
            fired += TriggerType.PHONE_SHAKE
        }

        // Knocks: classified by impulse shape (see KnockDetector), only counted when the phone was
        // otherwise still (rejects walking and shaking).
        val zNorm = (lz / KNOCK_FULL_SCALE).coerceIn(-1f, 1f)
        val impulse = zNorm.coerceAtLeast(0f)
        levels[TriggerType.BACK_DOUBLE_KNOCK] = impulse
        levels[TriggerType.BACK_TRIPLE_KNOCK] = impulse
        if (needsHighRate) {
            val count = knock.onSample(zNorm, stillness < STILL_LIMIT, nowMs)
            when (count) {
                2 -> if (TriggerType.BACK_DOUBLE_KNOCK in active) fired += TriggerType.BACK_DOUBLE_KNOCK
                3 -> if (TriggerType.BACK_TRIPLE_KNOCK in active) fired += TriggerType.BACK_TRIPLE_KNOCK
            }
        }
        // Stillness is updated after the knock check so a knock doesn't veto itself.
        val sa = 1f - exp(-dt / STILL_TAU_MS)
        stillness += (linear - stillness) * sa
        return fired
    }

    fun reset() {
        hasGravity = false
        lastMs = null
        stillness = 0f
        rollBase.reset()
        pitchBase.reset()
        tilt.values.forEach { it.reset() }
        shake.reset()
        knock.reset()
        levels.clear()
    }

    /** Sideways inclination: positive when the left edge dips. */
    private fun rollDeg(): Float =
        atan2(gravity[0], sqrt(gravity[1] * gravity[1] + gravity[2] * gravity[2])) * RAD_TO_DEG

    /** Forward/back inclination: positive when the top edge rises towards the user. */
    private fun pitchDeg(): Float = atan2(gravity[1], gravity[2]) * RAD_TO_DEG

    companion object {
        private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
        private const val GRAVITY_TAU_MS = 180f
        private const val STILL_TAU_MS = 400f
        private const val STILL_LIMIT = 2.5f
        const val TILT_FULL_SCALE = 60f
        const val SHAKE_FULL_SCALE = 30f
        const val KNOCK_FULL_SCALE = 10f

        private val TILTS = listOf(
            TriggerType.PHONE_TILT_LEFT, TriggerType.PHONE_TILT_RIGHT,
            TriggerType.PHONE_TILT_FORWARD, TriggerType.PHONE_TILT_BACK,
        )
        private val KNOCKS = setOf(TriggerType.BACK_DOUBLE_KNOCK, TriggerType.BACK_TRIPLE_KNOCK)
        val MOTION_TRIGGERS: Set<TriggerType> = TILTS.toSet() + KNOCKS + TriggerType.PHONE_SHAKE
    }
}

/**
 * Three or more strong acceleration peaks within a second, each in roughly the opposite direction
 * to the previous one (a back-and-forth shake, not a single jolt or a car's vibration).
 */
class ShakeDetector(var threshold: Float, cooldownMs: Long) {
    private val peaks = ArrayDeque<Long>()
    private val cooldown = Cooldown(cooldownMs)
    private val lastDir = FloatArray(3)
    private var hasDir = false

    var cooldownMs: Long
        get() = cooldown.durationMs
        set(value) {
            cooldown.durationMs = value
        }

    fun onSample(x: Float, y: Float, z: Float, level: Float, nowMs: Long): Boolean {
        while (peaks.isNotEmpty() && nowMs - peaks.first() > WINDOW_MS) peaks.removeFirst()
        if (peaks.isEmpty()) hasDir = false
        if (level >= threshold) {
            val reversed = !hasDir || (x * lastDir[0] + y * lastDir[1] + z * lastDir[2]) < 0f
            val spaced = peaks.isEmpty() || nowMs - peaks.last() >= MIN_PEAK_GAP_MS
            if (reversed && spaced) {
                peaks.addLast(nowMs)
                lastDir[0] = x; lastDir[1] = y; lastDir[2] = z
                hasDir = true
            }
        }
        if (peaks.size >= PEAKS && cooldown.tryFire(nowMs)) {
            peaks.clear()
            hasDir = false
            return true
        }
        return false
    }

    fun reset() {
        peaks.clear()
        hasDir = false
        cooldown.reset()
    }

    private companion object {
        const val WINDOW_MS = 1_000L
        const val MIN_PEAK_GAP_MS = 60L
        const val PEAKS = 3
    }
}

/**
 * Counts knocks on the back of the phone into sequences and returns the count when one ends.
 *
 * Each impact is classified from a short window of the (normalised, signed) Z acceleration:
 * - A knock on the back pushes the phone towards the user and rings in both directions, so its
 *   positive peak is large and comparable to its negative peak.
 * - A tap on the screen pushes the phone away: a large negative peak with a small positive one.
 * Calibrated from recordings on a Galaxy S23+ (knocks +7..+10 m/s², taps -5..-14 m/s²).
 */
class KnockDetector(var threshold: Float, cooldownMs: Long) {
    private val sequence = PulseSequence(minGapMs = 120, maxGapMs = 500)
    private val cooldown = Cooldown(cooldownMs)

    private var eventStartMs: Long? = null
    private var eventStill = false
    private var positivePeak = 0f
    private var negativePeak = 0f
    private var rearmed = true

    var cooldownMs: Long
        get() = cooldown.durationMs
        set(value) {
            cooldown.durationMs = value
        }

    /** [z] is signed Z acceleration normalised to the knock full scale (±1). */
    fun onSample(z: Float, wasStill: Boolean, nowMs: Long): Int? {
        val start = eventStartMs
        val trigger = minOf(threshold, ONSET)
        if (start == null) {
            if (!rearmed) {
                if (kotlin.math.abs(z) < trigger * 0.5f) rearmed = true
            } else if (kotlin.math.abs(z) >= trigger) {
                eventStartMs = nowMs
                eventStill = wasStill
                positivePeak = maxOf(0f, z)
                negativePeak = minOf(0f, z)
            }
        } else {
            positivePeak = maxOf(positivePeak, z)
            negativePeak = minOf(negativePeak, z)
            if (nowMs - start >= EVENT_MS) {
                eventStartMs = null
                rearmed = false
                val isKnock = positivePeak >= threshold && positivePeak >= -negativePeak * SYMMETRY
                if (isKnock && (eventStill || sequence.pending > 0)) sequence.pulse(start)
            }
        }
        val count = sequence.tick(nowMs) ?: return null
        return if (count in 2..3 && cooldown.tryFire(nowMs)) count else null
    }

    fun reset() {
        sequence.reset()
        cooldown.reset()
        eventStartMs = null
        rearmed = true
    }

    private companion object {
        /** Window that captures one impact including its ringing. */
        const val EVENT_MS = 60L

        /** |z| that opens an event window (0.25 = 2.5 m/s²). */
        const val ONSET = 0.25f

        /** Positive peak must be at least this fraction of the negative peak. */
        const val SYMMETRY = 0.5f
    }
}
