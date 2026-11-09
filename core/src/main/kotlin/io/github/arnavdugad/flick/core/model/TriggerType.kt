package io.github.arnavdugad.flick.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TriggerCategory { HEAD, FACE, HAND, PHONE, BUTTON, VOICE }

/** The hardware or permission a trigger depends on. */
enum class TriggerRequirement { CAMERA, PROXIMITY, MOTION, KEYS, MICROPHONE }

/** Every input Flick can react to. Each one can be bound to any [Action]. */
@Serializable
enum class TriggerType(val category: TriggerCategory, val requirement: TriggerRequirement) {
    HEAD_TILT_UP(TriggerCategory.HEAD, TriggerRequirement.CAMERA),
    HEAD_TILT_DOWN(TriggerCategory.HEAD, TriggerRequirement.CAMERA),
    HEAD_TILT_LEFT(TriggerCategory.HEAD, TriggerRequirement.CAMERA),
    HEAD_TILT_RIGHT(TriggerCategory.HEAD, TriggerRequirement.CAMERA),
    HEAD_TURN_LEFT(TriggerCategory.HEAD, TriggerRequirement.CAMERA),
    HEAD_TURN_RIGHT(TriggerCategory.HEAD, TriggerRequirement.CAMERA),
    HEAD_NOD(TriggerCategory.HEAD, TriggerRequirement.CAMERA),

    FACE_LONG_BLINK(TriggerCategory.FACE, TriggerRequirement.CAMERA),
    FACE_DOUBLE_BLINK(TriggerCategory.FACE, TriggerRequirement.CAMERA),
    FACE_MOUTH_OPEN(TriggerCategory.FACE, TriggerRequirement.CAMERA),
    FACE_SMILE(TriggerCategory.FACE, TriggerRequirement.CAMERA),
    FACE_EYEBROW_RAISE(TriggerCategory.FACE, TriggerRequirement.CAMERA),

    HAND_OPEN_PALM(TriggerCategory.HAND, TriggerRequirement.CAMERA),
    HAND_FIST(TriggerCategory.HAND, TriggerRequirement.CAMERA),
    HAND_THUMBS_UP(TriggerCategory.HAND, TriggerRequirement.CAMERA),
    HAND_THUMBS_DOWN(TriggerCategory.HAND, TriggerRequirement.CAMERA),
    HAND_VICTORY(TriggerCategory.HAND, TriggerRequirement.CAMERA),
    HAND_POINTING_UP(TriggerCategory.HAND, TriggerRequirement.CAMERA),
    HAND_SWIPE_LEFT(TriggerCategory.HAND, TriggerRequirement.CAMERA),
    HAND_SWIPE_RIGHT(TriggerCategory.HAND, TriggerRequirement.CAMERA),
    HAND_SWIPE_UP(TriggerCategory.HAND, TriggerRequirement.CAMERA),
    HAND_SWIPE_DOWN(TriggerCategory.HAND, TriggerRequirement.CAMERA),

    PROXIMITY_WAVE(TriggerCategory.PHONE, TriggerRequirement.PROXIMITY),
    PHONE_TILT_LEFT(TriggerCategory.PHONE, TriggerRequirement.MOTION),
    PHONE_TILT_RIGHT(TriggerCategory.PHONE, TriggerRequirement.MOTION),
    PHONE_TILT_FORWARD(TriggerCategory.PHONE, TriggerRequirement.MOTION),
    PHONE_TILT_BACK(TriggerCategory.PHONE, TriggerRequirement.MOTION),
    PHONE_SHAKE(TriggerCategory.PHONE, TriggerRequirement.MOTION),
    BACK_DOUBLE_KNOCK(TriggerCategory.PHONE, TriggerRequirement.MOTION),
    BACK_TRIPLE_KNOCK(TriggerCategory.PHONE, TriggerRequirement.MOTION),

    VOLUME_UP(TriggerCategory.BUTTON, TriggerRequirement.KEYS),
    VOLUME_DOWN(TriggerCategory.BUTTON, TriggerRequirement.KEYS),

    VOICE_NEXT(TriggerCategory.VOICE, TriggerRequirement.MICROPHONE),
    VOICE_BACK(TriggerCategory.VOICE, TriggerRequirement.MICROPHONE),
    VOICE_LIKE(TriggerCategory.VOICE, TriggerRequirement.MICROPHONE),
    VOICE_PAUSE(TriggerCategory.VOICE, TriggerRequirement.MICROPHONE),
    VOICE_STOP(TriggerCategory.VOICE, TriggerRequirement.MICROPHONE),
}
