package io.github.arnavdugad.flick.core.detect

import io.github.arnavdugad.flick.core.model.TriggerSpecs
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlin.math.asin
import kotlin.math.atan2

/** One face observation. Blendshape scores are 0..1; angles are degrees (absolute, not neutral-relative). */
data class FaceSample(
    val eyeBlinkLeft: Float,
    val eyeBlinkRight: Float,
    val jawOpen: Float,
    val smileLeft: Float,
    val smileRight: Float,
    val browInnerUp: Float,
    val browOuterUpLeft: Float,
    val browOuterUpRight: Float,
    val pitchDeg: Float,
    val yawDeg: Float,
    val rollDeg: Float,
)

data class HeadAngles(val pitchDeg: Float, val yawDeg: Float, val rollDeg: Float)

object HeadPose {
    private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

    /**
     * Extracts Euler angles from MediaPipe's 4x4 facial transformation matrix (column-major).
     * Pitch is rotation about X (nodding), yaw about Y (turning), roll about Z (tilting sideways).
     * Only *changes* from the user's neutral pose are used, so a consistent sign is all that matters.
     */
    fun fromMatrix(m: FloatArray): HeadAngles {
        require(m.size >= 16) { "Expected a 4x4 matrix" }
        val pitch = atan2(m[6], m[10]) * RAD_TO_DEG
        val yaw = asin((-m[2]).coerceIn(-1f, 1f)) * RAD_TO_DEG
        val roll = atan2(m[1], m[0]) * RAD_TO_DEG
        return HeadAngles(pitch, yaw, roll)
    }
}

/**
 * Turns face samples into head and face triggers. All head angles are measured relative to an
 * adaptive neutral pose, so it works however the phone is held.
 */
class FaceGestureDetector {

    private var active: Set<TriggerType> = emptySet()

    private val pitchBase = NeutralBaseline(adaptLimit = 6f)
    private val yawBase = NeutralBaseline(adaptLimit = 6f)
    private val rollBase = NeutralBaseline(adaptLimit = 6f)

    private val levelDetectors = HashMap<TriggerType, LevelDetector>()
    private val nod = NodDetector(threshold = 0.25f, cooldownMs = 900)
    private val blink = BlinkDetector()

    private val levels = HashMap<TriggerType, Float>()

    /** Latest normalised level for [type], for calibration meters. */
    fun level(type: TriggerType): Float = levels[type] ?: 0f

    fun configure(active: Set<TriggerType>, tuning: (TriggerType) -> TriggerTuning) {
        this.active = active intersect FACE_TRIGGERS
        for (type in LEVEL_TRIGGERS) {
            val t = tuning(type)
            val spec = TriggerSpecs[type]
            var hold = t.holdMs
            // A slow nod would otherwise also count as a held tilt; give the nod its window first.
            if (TriggerType.HEAD_NOD in this.active &&
                (type == TriggerType.HEAD_TILT_DOWN || type == TriggerType.HEAD_TILT_UP)
            ) {
                hold = maxOf(hold, NodDetector.WINDOW_MS + 50)
            }
            val d = levelDetectors.getOrPut(type) { LevelDetector(spec.threshold(t.sensitivity), hold, t.cooldownMs) }
            d.threshold = spec.threshold(t.sensitivity)
            d.holdMs = hold
            d.cooldownMs = t.cooldownMs
        }
        tuning(TriggerType.HEAD_NOD).let {
            nod.threshold = TriggerSpecs[TriggerType.HEAD_NOD].threshold(it.sensitivity)
            nod.cooldownMs = it.cooldownMs
        }
        val longBlink = tuning(TriggerType.FACE_LONG_BLINK)
        val doubleBlink = tuning(TriggerType.FACE_DOUBLE_BLINK)
        blink.configure(
            closedThreshold = TriggerSpecs[TriggerType.FACE_LONG_BLINK].threshold(longBlink.sensitivity),
            doubleThreshold = TriggerSpecs[TriggerType.FACE_DOUBLE_BLINK].threshold(doubleBlink.sensitivity),
            longHoldMs = longBlink.holdMs,
            longCooldownMs = longBlink.cooldownMs,
            doubleCooldownMs = doubleBlink.cooldownMs,
        )
    }

    /** Uses the current pose as neutral. */
    fun setNeutral(sample: FaceSample, nowMs: Long) {
        pitchBase.set(sample.pitchDeg, nowMs)
        yawBase.set(sample.yawDeg, nowMs)
        rollBase.set(sample.rollDeg, nowMs)
    }

    fun onFace(s: FaceSample, nowMs: Long): List<TriggerType> {
        val pitch = pitchBase.update(s.pitchDeg, nowMs) / HEAD_FULL_SCALE
        val yaw = yawBase.update(s.yawDeg, nowMs) / HEAD_FULL_SCALE
        val roll = rollBase.update(s.rollDeg, nowMs) / HEAD_FULL_SCALE

        val signals = mapOf(
            TriggerType.HEAD_TILT_UP to pitch,
            TriggerType.HEAD_TILT_DOWN to -pitch,
            TriggerType.HEAD_TILT_LEFT to roll,
            TriggerType.HEAD_TILT_RIGHT to -roll,
            TriggerType.HEAD_TURN_LEFT to yaw,
            TriggerType.HEAD_TURN_RIGHT to -yaw,
            TriggerType.FACE_MOUTH_OPEN to s.jawOpen,
            TriggerType.FACE_SMILE to (s.smileLeft + s.smileRight) / 2f,
            TriggerType.FACE_EYEBROW_RAISE to maxOf(s.browInnerUp, (s.browOuterUpLeft + s.browOuterUpRight) / 2f),
        )
        val fired = ArrayList<TriggerType>(2)
        for ((type, value) in signals) {
            val level = value.coerceIn(0f, 1f)
            levels[type] = level
            if (type !in active) continue
            if (levelDetectors[type]?.onSample(level, nowMs) == true) fired += type
        }

        levels[TriggerType.HEAD_NOD] = (-pitch).coerceIn(0f, 1f)
        if (TriggerType.HEAD_NOD in active && nod.onSample(-pitch, nowMs)) fired += TriggerType.HEAD_NOD

        val closed = minOf(s.eyeBlinkLeft, s.eyeBlinkRight)
        levels[TriggerType.FACE_LONG_BLINK] = closed
        levels[TriggerType.FACE_DOUBLE_BLINK] = closed
        for (t in blink.onSample(closed, nowMs)) if (t in active) fired += t
        return fired
    }

    /** Call when no face is visible: releases all holds so nothing fires from stale state. */
    fun onFaceLost(nowMs: Long) {
        for (d in levelDetectors.values) d.onSample(0f, nowMs)
        blink.onSample(0f, nowMs)
        nod.reset()
        levels.clear()
    }

    fun reset() {
        levelDetectors.values.forEach { it.reset() }
        nod.reset()
        blink.reset()
        pitchBase.reset()
        yawBase.reset()
        rollBase.reset()
        levels.clear()
    }

    companion object {
        const val HEAD_FULL_SCALE = 45f

        private val LEVEL_TRIGGERS = listOf(
            TriggerType.HEAD_TILT_UP, TriggerType.HEAD_TILT_DOWN,
            TriggerType.HEAD_TILT_LEFT, TriggerType.HEAD_TILT_RIGHT,
            TriggerType.HEAD_TURN_LEFT, TriggerType.HEAD_TURN_RIGHT,
            TriggerType.FACE_MOUTH_OPEN, TriggerType.FACE_SMILE, TriggerType.FACE_EYEBROW_RAISE,
        )

        val FACE_TRIGGERS: Set<TriggerType> = LEVEL_TRIGGERS.toSet() + setOf(
            TriggerType.HEAD_NOD,
            TriggerType.FACE_LONG_BLINK,
            TriggerType.FACE_DOUBLE_BLINK,
        )
    }
}

/**
 * A quick nod: head dips below the threshold and comes back up within [WINDOW_MS]. Input is the
 * neutral-relative downward pitch (positive = looking down), normalised.
 */
class NodDetector(var threshold: Float, cooldownMs: Long) {

    private enum class State { IDLE, DOWN, WAIT_RELEASE }

    private var state = State.IDLE
    private var downSinceMs = 0L
    private val cooldown = Cooldown(cooldownMs)

    var cooldownMs: Long
        get() = cooldown.durationMs
        set(value) {
            cooldown.durationMs = value
        }

    fun onSample(down: Float, nowMs: Long): Boolean {
        val release = threshold * RELEASE_RATIO
        when (state) {
            State.IDLE -> if (down >= threshold) {
                state = State.DOWN
                downSinceMs = nowMs
            }
            State.DOWN -> when {
                down < release -> {
                    state = State.IDLE
                    return nowMs - downSinceMs <= WINDOW_MS && cooldown.tryFire(nowMs)
                }
                nowMs - downSinceMs > WINDOW_MS -> state = State.WAIT_RELEASE
            }
            State.WAIT_RELEASE -> if (down < release) state = State.IDLE
        }
        return false
    }

    fun reset() {
        state = State.IDLE
        cooldown.reset()
    }

    companion object {
        const val WINDOW_MS = 700L
        private const val RELEASE_RATIO = 0.4f
    }
}

/**
 * Deliberate blinks, using the *less* closed eye so winks and sideways glances don't count.
 * - Long blink: eyes closed for at least the hold time (fires once per closure).
 * - Double blink: exactly two short blinks in quick succession.
 */
class BlinkDetector {

    private var closedThreshold = 0.55f
    private var doubleThreshold = 0.55f
    private var longHoldMs = 500L
    private val longCooldown = Cooldown(1_000)
    private val doubleCooldown = Cooldown(1_000)
    private val sequence = PulseSequence(minGapMs = 80, maxGapMs = 450)

    private var closedSinceMs: Long? = null
    private var longFired = false

    fun configure(
        closedThreshold: Float,
        doubleThreshold: Float,
        longHoldMs: Long,
        longCooldownMs: Long,
        doubleCooldownMs: Long,
    ) {
        this.closedThreshold = closedThreshold
        this.doubleThreshold = doubleThreshold
        this.longHoldMs = longHoldMs
        longCooldown.durationMs = longCooldownMs
        doubleCooldown.durationMs = doubleCooldownMs
    }

    fun onSample(closed: Float, nowMs: Long): List<TriggerType> {
        var fired: TriggerType? = null
        val threshold = minOf(closedThreshold, doubleThreshold)
        val since = closedSinceMs
        if (since == null) {
            if (closed >= threshold) {
                closedSinceMs = nowMs
                longFired = false
            }
        } else {
            val duration = nowMs - since
            if (closed < threshold * OPEN_RATIO) {
                closedSinceMs = null
                if (!longFired && duration in SHORT_BLINK_MIN_MS..SHORT_BLINK_MAX_MS) {
                    sequence.pulse(nowMs)
                }
            } else if (!longFired && closed >= closedThreshold && duration >= longHoldMs) {
                longFired = true
                sequence.reset()
                if (longCooldown.tryFire(nowMs)) fired = TriggerType.FACE_LONG_BLINK
            }
        }
        val count = sequence.tick(nowMs)
        if (count == 2 && doubleCooldown.tryFire(nowMs)) fired = TriggerType.FACE_DOUBLE_BLINK
        return if (fired == null) emptyList() else listOf(fired)
    }

    fun reset() {
        closedSinceMs = null
        longFired = false
        sequence.reset()
        longCooldown.reset()
        doubleCooldown.reset()
    }

    private companion object {
        const val OPEN_RATIO = 0.7f
        const val SHORT_BLINK_MIN_MS = 40L
        const val SHORT_BLINK_MAX_MS = 350L
    }
}
