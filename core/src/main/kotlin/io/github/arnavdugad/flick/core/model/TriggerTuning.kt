package io.github.arnavdugad.flick.core.model

import kotlinx.serialization.Serializable

/**
 * User-adjustable tuning for one trigger.
 * - [sensitivity] 0..1: higher = fires on smaller movements.
 * - [holdMs]: how long a pose must be held before it fires (ignored by instantaneous triggers).
 * - [cooldownMs]: minimum time between two firings.
 */
@Serializable
data class TriggerTuning(
    val sensitivity: Float = 0.5f,
    val holdMs: Long = 0,
    val cooldownMs: Long = 800,
) {
    fun sanitized(spec: TriggerSpec) = copy(
        sensitivity = sensitivity.coerceIn(0f, 1f),
        holdMs = if (spec.holdRange != null) holdMs.coerceIn(spec.holdRange) else 0,
        cooldownMs = cooldownMs.coerceIn(spec.cooldownRange),
    )
}

/**
 * Static description of how a trigger is tuned. Signals are normalised to 0..1 against a
 * full-scale value per signal type (e.g. 45° for head angles), so a single threshold range works
 * across very different inputs.
 *
 * [thresholdRange] is the normalised threshold: `endInclusive` at sensitivity 0 (hard to fire) and
 * `start` at sensitivity 1 (easy to fire). Null when the trigger has no threshold.
 */
data class TriggerSpec(
    val type: TriggerType,
    val thresholdRange: ClosedFloatingPointRange<Float>?,
    val holdRange: LongRange?,
    val cooldownRange: LongRange,
    val defaults: TriggerTuning,
) {
    val hasSensitivity: Boolean get() = thresholdRange != null || type == TriggerType.PROXIMITY_WAVE
    val hasHold: Boolean get() = holdRange != null

    /** Normalised threshold for a sensitivity value. */
    fun threshold(sensitivity: Float): Float {
        val r = thresholdRange ?: return 0.5f
        val s = sensitivity.coerceIn(0f, 1f)
        return r.endInclusive + (r.start - r.endInclusive) * s
    }
}

object TriggerSpecs {

    private val COOLDOWN = 200L..3_000L
    private val HOLD = 0L..1_500L

    private fun spec(
        type: TriggerType,
        threshold: ClosedFloatingPointRange<Float>?,
        hold: Long?,
        cooldown: Long,
        holdRange: LongRange? = HOLD,
    ) = TriggerSpec(
        type = type,
        thresholdRange = threshold,
        holdRange = if (hold == null) null else holdRange,
        cooldownRange = COOLDOWN,
        defaults = TriggerTuning(sensitivity = 0.5f, holdMs = hold ?: 0, cooldownMs = cooldown),
    )

    private val all: Map<TriggerType, TriggerSpec> = TriggerType.entries.associateWith { t ->
        when (t) {
            // Head angles, full scale 45°.
            TriggerType.HEAD_TILT_UP, TriggerType.HEAD_TILT_DOWN ->
                spec(t, 0.16f..0.5f, hold = 200, cooldown = 900)
            TriggerType.HEAD_TILT_LEFT, TriggerType.HEAD_TILT_RIGHT ->
                spec(t, 0.2f..0.55f, hold = 200, cooldown = 900)
            TriggerType.HEAD_TURN_LEFT, TriggerType.HEAD_TURN_RIGHT ->
                spec(t, 0.25f..0.65f, hold = 200, cooldown = 900)
            TriggerType.HEAD_NOD -> spec(t, 0.14f..0.45f, hold = null, cooldown = 900)

            // Face blendshapes, native 0..1.
            TriggerType.FACE_LONG_BLINK ->
                spec(t, 0.35f..0.75f, hold = 500, cooldown = 1_000, holdRange = 300L..1_500L)
            TriggerType.FACE_DOUBLE_BLINK -> spec(t, 0.35f..0.75f, hold = null, cooldown = 1_000)
            TriggerType.FACE_MOUTH_OPEN -> spec(t, 0.25f..0.7f, hold = 250, cooldown = 1_000)
            TriggerType.FACE_SMILE -> spec(t, 0.35f..0.8f, hold = 300, cooldown = 1_200)
            TriggerType.FACE_EYEBROW_RAISE -> spec(t, 0.3f..0.8f, hold = 250, cooldown = 1_000)

            // Hand gesture classifier scores, 0..1.
            TriggerType.HAND_OPEN_PALM, TriggerType.HAND_FIST, TriggerType.HAND_THUMBS_UP,
            TriggerType.HAND_THUMBS_DOWN, TriggerType.HAND_VICTORY, TriggerType.HAND_POINTING_UP,
            -> spec(t, 0.5f..0.85f, hold = 300, cooldown = 1_200)

            // Hand travel as a fraction of the camera frame.
            TriggerType.HAND_SWIPE_LEFT, TriggerType.HAND_SWIPE_RIGHT,
            TriggerType.HAND_SWIPE_UP, TriggerType.HAND_SWIPE_DOWN,
            -> spec(t, 0.12f..0.4f, hold = null, cooldown = 900)

            TriggerType.PROXIMITY_WAVE -> spec(t, null, hold = null, cooldown = 700)

            // Phone tilt, full scale 60°.
            TriggerType.PHONE_TILT_LEFT, TriggerType.PHONE_TILT_RIGHT,
            TriggerType.PHONE_TILT_FORWARD, TriggerType.PHONE_TILT_BACK,
            -> spec(t, 0.25f..0.6f, hold = 150, cooldown = 900)

            // Linear acceleration, full scale 30 m/s².
            TriggerType.PHONE_SHAKE -> spec(t, 0.35f..0.8f, hold = null, cooldown = 1_500)

            // High-passed back-of-phone impulse, full scale 10 m/s².
            TriggerType.BACK_DOUBLE_KNOCK, TriggerType.BACK_TRIPLE_KNOCK ->
                spec(t, 0.12f..0.6f, hold = null, cooldown = 800)

            TriggerType.VOLUME_UP, TriggerType.VOLUME_DOWN -> spec(t, null, hold = null, cooldown = 250)

            TriggerType.VOICE_NEXT, TriggerType.VOICE_BACK, TriggerType.VOICE_LIKE,
            TriggerType.VOICE_PAUSE, TriggerType.VOICE_STOP,
            -> spec(t, null, hold = null, cooldown = 800)
        }
    }

    operator fun get(type: TriggerType): TriggerSpec = all.getValue(type)

    fun tuningFor(type: TriggerType, overrides: Map<TriggerType, TriggerTuning>): TriggerTuning {
        val spec = get(type)
        return (overrides[type] ?: spec.defaults).sanitized(spec)
    }
}
