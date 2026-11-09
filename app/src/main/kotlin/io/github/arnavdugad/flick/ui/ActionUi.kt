package io.github.arnavdugad.flick.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.FrontHand
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SignLanguage
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.Direction
import io.github.arnavdugad.flick.core.model.GlobalAction
import io.github.arnavdugad.flick.core.model.MediaCommand
import io.github.arnavdugad.flick.core.model.TriggerCategory
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlin.math.roundToInt

@Composable
fun actionLabel(action: Action): String = when (action) {
    is Action.Swipe -> stringResource(
        when (action.direction) {
            Direction.UP -> R.string.act_next
            Direction.DOWN -> R.string.act_previous
            Direction.LEFT -> R.string.act_swipe_left
            Direction.RIGHT -> R.string.act_swipe_right
        },
    )
    is Action.Scroll -> stringResource(
        when (action.direction) {
            Direction.DOWN -> R.string.act_scroll_down
            Direction.UP -> R.string.act_scroll_up
            Direction.LEFT -> R.string.act_scroll_left
            Direction.RIGHT -> R.string.act_scroll_right
        },
    )
    is Action.Tap ->
        if (action.x == 0.5f && action.y == 0.5f) {
            stringResource(R.string.act_tap)
        } else {
            stringResource(R.string.act_tap_at, (action.x * 100).roundToInt(), (action.y * 100).roundToInt())
        }
    is Action.DoubleTap -> stringResource(R.string.act_double_tap)
    is Action.LongPress -> stringResource(R.string.act_long_press)
    is Action.Global -> stringResource(action.action.labelRes)
    is Action.Media -> stringResource(action.command.labelRes)
    Action.AutoScroll -> stringResource(R.string.act_auto_scroll)
    is Action.RunMacro -> stringResource(R.string.act_run_macro)
    Action.PauseFlick -> stringResource(R.string.act_pause_flick)
}

fun actionIcon(action: Action): ImageVector = when (action) {
    is Action.Swipe -> when (action.direction) {
        Direction.UP -> Icons.Rounded.SwipeUp
        Direction.DOWN -> Icons.Rounded.SwipeDown
        Direction.LEFT -> Icons.Rounded.SwipeLeft
        Direction.RIGHT -> Icons.Rounded.SwipeRight
    }
    is Action.Scroll -> when (action.direction) {
        Direction.DOWN -> Icons.Rounded.KeyboardDoubleArrowDown
        Direction.UP -> Icons.Rounded.KeyboardDoubleArrowUp
        Direction.LEFT -> Icons.Rounded.KeyboardDoubleArrowLeft
        Direction.RIGHT -> Icons.Rounded.KeyboardDoubleArrowRight
    }
    is Action.Tap, is Action.LongPress -> Icons.Rounded.TouchApp
    is Action.DoubleTap -> Icons.Rounded.Favorite
    is Action.Global -> when (action.action) {
        GlobalAction.BACK -> Icons.AutoMirrored.Rounded.ArrowBack
        GlobalAction.HOME -> Icons.Rounded.Home
        GlobalAction.RECENTS -> Icons.Rounded.Layers
        GlobalAction.NOTIFICATIONS -> Icons.Rounded.Notifications
        GlobalAction.QUICK_SETTINGS -> Icons.Rounded.Tune
        GlobalAction.POWER_DIALOG -> Icons.Rounded.PowerSettingsNew
        GlobalAction.SPLIT_SCREEN -> Icons.Rounded.VerticalSplit
        GlobalAction.LOCK_SCREEN -> Icons.Rounded.Lock
        GlobalAction.SCREENSHOT -> Icons.Rounded.Screenshot
        GlobalAction.DISMISS_NOTIFICATION_SHADE -> Icons.Rounded.ExpandLess
        GlobalAction.ALL_APPS -> Icons.Rounded.Apps
    }
    is Action.Media -> when (action.command) {
        MediaCommand.PLAY_PAUSE -> Icons.Rounded.PlayArrow
        MediaCommand.NEXT -> Icons.Rounded.SkipNext
        MediaCommand.PREVIOUS -> Icons.Rounded.SkipPrevious
        MediaCommand.VOLUME_UP -> Icons.AutoMirrored.Rounded.VolumeUp
        MediaCommand.VOLUME_DOWN -> Icons.AutoMirrored.Rounded.VolumeDown
        MediaCommand.MUTE_TOGGLE -> Icons.AutoMirrored.Rounded.VolumeOff
    }
    Action.AutoScroll -> Icons.Rounded.KeyboardDoubleArrowDown
    is Action.RunMacro -> Icons.Rounded.PlayArrow
    Action.PauseFlick -> Icons.Rounded.PowerSettingsNew
}

@get:StringRes
val GlobalAction.labelRes: Int
    get() = when (this) {
        GlobalAction.BACK -> R.string.act_back
        GlobalAction.HOME -> R.string.act_home
        GlobalAction.RECENTS -> R.string.act_recents
        GlobalAction.NOTIFICATIONS -> R.string.act_notifications
        GlobalAction.QUICK_SETTINGS -> R.string.act_quick_settings
        GlobalAction.POWER_DIALOG -> R.string.act_power_dialog
        GlobalAction.SPLIT_SCREEN -> R.string.act_split_screen
        GlobalAction.LOCK_SCREEN -> R.string.act_lock_screen
        GlobalAction.SCREENSHOT -> R.string.act_screenshot
        GlobalAction.DISMISS_NOTIFICATION_SHADE -> R.string.act_dismiss_shade
        GlobalAction.ALL_APPS -> R.string.act_all_apps
    }

@get:StringRes
val MediaCommand.labelRes: Int
    get() = when (this) {
        MediaCommand.PLAY_PAUSE -> R.string.act_play_pause
        MediaCommand.NEXT -> R.string.act_next_track
        MediaCommand.PREVIOUS -> R.string.act_previous_track
        MediaCommand.VOLUME_UP -> R.string.act_volume_up
        MediaCommand.VOLUME_DOWN -> R.string.act_volume_down
        MediaCommand.MUTE_TOGGLE -> R.string.act_mute
    }

@Composable
fun triggerLabel(trigger: TriggerType): String = stringResource(trigger.labelRes)

@Composable
fun triggerDescription(trigger: TriggerType): String = stringResource(trigger.descriptionRes)

@get:StringRes
val TriggerCategory.labelRes: Int
    get() = when (this) {
        TriggerCategory.HEAD -> R.string.cat_head
        TriggerCategory.FACE -> R.string.cat_face
        TriggerCategory.HAND -> R.string.cat_hand
        TriggerCategory.PHONE -> R.string.cat_phone
        TriggerCategory.BUTTON -> R.string.cat_button
        TriggerCategory.VOICE -> R.string.cat_voice
    }

@get:StringRes
val TriggerType.labelRes: Int
    get() = when (this) {
        TriggerType.HEAD_TILT_UP -> R.string.t_head_tilt_up
        TriggerType.HEAD_TILT_DOWN -> R.string.t_head_tilt_down
        TriggerType.HEAD_TILT_LEFT -> R.string.t_head_tilt_left
        TriggerType.HEAD_TILT_RIGHT -> R.string.t_head_tilt_right
        TriggerType.HEAD_TURN_LEFT -> R.string.t_head_turn_left
        TriggerType.HEAD_TURN_RIGHT -> R.string.t_head_turn_right
        TriggerType.HEAD_NOD -> R.string.t_head_nod
        TriggerType.FACE_LONG_BLINK -> R.string.t_face_long_blink
        TriggerType.FACE_DOUBLE_BLINK -> R.string.t_face_double_blink
        TriggerType.FACE_MOUTH_OPEN -> R.string.t_face_mouth_open
        TriggerType.FACE_SMILE -> R.string.t_face_smile
        TriggerType.FACE_EYEBROW_RAISE -> R.string.t_face_eyebrow_raise
        TriggerType.HAND_OPEN_PALM -> R.string.t_hand_open_palm
        TriggerType.HAND_FIST -> R.string.t_hand_fist
        TriggerType.HAND_THUMBS_UP -> R.string.t_hand_thumbs_up
        TriggerType.HAND_THUMBS_DOWN -> R.string.t_hand_thumbs_down
        TriggerType.HAND_VICTORY -> R.string.t_hand_victory
        TriggerType.HAND_POINTING_UP -> R.string.t_hand_pointing_up
        TriggerType.HAND_SWIPE_LEFT -> R.string.t_hand_swipe_left
        TriggerType.HAND_SWIPE_RIGHT -> R.string.t_hand_swipe_right
        TriggerType.HAND_SWIPE_UP -> R.string.t_hand_swipe_up
        TriggerType.HAND_SWIPE_DOWN -> R.string.t_hand_swipe_down
        TriggerType.PROXIMITY_WAVE -> R.string.t_proximity_wave
        TriggerType.PHONE_TILT_LEFT -> R.string.t_phone_tilt_left
        TriggerType.PHONE_TILT_RIGHT -> R.string.t_phone_tilt_right
        TriggerType.PHONE_TILT_FORWARD -> R.string.t_phone_tilt_forward
        TriggerType.PHONE_TILT_BACK -> R.string.t_phone_tilt_back
        TriggerType.PHONE_SHAKE -> R.string.t_phone_shake
        TriggerType.BACK_DOUBLE_KNOCK -> R.string.t_back_double_knock
        TriggerType.BACK_TRIPLE_KNOCK -> R.string.t_back_triple_knock
        TriggerType.VOLUME_UP -> R.string.t_volume_up
        TriggerType.VOLUME_DOWN -> R.string.t_volume_down
        TriggerType.VOICE_NEXT -> R.string.t_voice_next
        TriggerType.VOICE_BACK -> R.string.t_voice_back
        TriggerType.VOICE_LIKE -> R.string.t_voice_like
        TriggerType.VOICE_PAUSE -> R.string.t_voice_pause
        TriggerType.VOICE_STOP -> R.string.t_voice_stop
    }

@get:StringRes
val TriggerType.descriptionRes: Int
    get() = when (this) {
        TriggerType.HEAD_TILT_UP -> R.string.d_head_tilt_up
        TriggerType.HEAD_TILT_DOWN -> R.string.d_head_tilt_down
        TriggerType.HEAD_TILT_LEFT -> R.string.d_head_tilt_left
        TriggerType.HEAD_TILT_RIGHT -> R.string.d_head_tilt_right
        TriggerType.HEAD_TURN_LEFT -> R.string.d_head_turn_left
        TriggerType.HEAD_TURN_RIGHT -> R.string.d_head_turn_right
        TriggerType.HEAD_NOD -> R.string.d_head_nod
        TriggerType.FACE_LONG_BLINK -> R.string.d_face_long_blink
        TriggerType.FACE_DOUBLE_BLINK -> R.string.d_face_double_blink
        TriggerType.FACE_MOUTH_OPEN -> R.string.d_face_mouth_open
        TriggerType.FACE_SMILE -> R.string.d_face_smile
        TriggerType.FACE_EYEBROW_RAISE -> R.string.d_face_eyebrow_raise
        TriggerType.HAND_OPEN_PALM -> R.string.d_hand_open_palm
        TriggerType.HAND_FIST -> R.string.d_hand_fist
        TriggerType.HAND_THUMBS_UP -> R.string.d_hand_thumbs_up
        TriggerType.HAND_THUMBS_DOWN -> R.string.d_hand_thumbs_down
        TriggerType.HAND_VICTORY -> R.string.d_hand_victory
        TriggerType.HAND_POINTING_UP -> R.string.d_hand_pointing_up
        TriggerType.HAND_SWIPE_LEFT -> R.string.d_hand_swipe
        TriggerType.HAND_SWIPE_RIGHT -> R.string.d_hand_swipe
        TriggerType.HAND_SWIPE_UP -> R.string.d_hand_swipe
        TriggerType.HAND_SWIPE_DOWN -> R.string.d_hand_swipe
        TriggerType.PROXIMITY_WAVE -> R.string.d_proximity_wave
        TriggerType.PHONE_TILT_LEFT -> R.string.d_phone_tilt_left
        TriggerType.PHONE_TILT_RIGHT -> R.string.d_phone_tilt_right
        TriggerType.PHONE_TILT_FORWARD -> R.string.d_phone_tilt_forward
        TriggerType.PHONE_TILT_BACK -> R.string.d_phone_tilt_back
        TriggerType.PHONE_SHAKE -> R.string.d_phone_shake
        TriggerType.BACK_DOUBLE_KNOCK -> R.string.d_back_double_knock
        TriggerType.BACK_TRIPLE_KNOCK -> R.string.d_back_triple_knock
        TriggerType.VOLUME_UP -> R.string.d_volume
        TriggerType.VOLUME_DOWN -> R.string.d_volume
        TriggerType.VOICE_NEXT -> R.string.d_voice
        TriggerType.VOICE_BACK -> R.string.d_voice
        TriggerType.VOICE_LIKE -> R.string.d_voice
        TriggerType.VOICE_PAUSE -> R.string.d_voice
        TriggerType.VOICE_STOP -> R.string.d_voice
    }

fun triggerIcon(trigger: TriggerType): ImageVector = when (trigger) {
    TriggerType.HEAD_TILT_UP -> Icons.Rounded.ArrowUpward
    TriggerType.HEAD_TILT_DOWN -> Icons.Rounded.ArrowDownward
    TriggerType.HEAD_TILT_LEFT -> Icons.AutoMirrored.Rounded.RotateLeft
    TriggerType.HEAD_TILT_RIGHT -> Icons.AutoMirrored.Rounded.RotateRight
    TriggerType.HEAD_TURN_LEFT -> Icons.AutoMirrored.Rounded.RotateLeft
    TriggerType.HEAD_TURN_RIGHT -> Icons.AutoMirrored.Rounded.RotateRight
    TriggerType.HEAD_NOD -> Icons.Rounded.Face
    TriggerType.FACE_LONG_BLINK -> Icons.Rounded.VisibilityOff
    TriggerType.FACE_DOUBLE_BLINK -> Icons.Rounded.RemoveRedEye
    TriggerType.FACE_MOUTH_OPEN -> Icons.Rounded.RecordVoiceOver
    TriggerType.FACE_SMILE -> Icons.Rounded.SentimentVerySatisfied
    TriggerType.FACE_EYEBROW_RAISE -> Icons.Rounded.Face3
    TriggerType.HAND_OPEN_PALM -> Icons.Rounded.FrontHand
    TriggerType.HAND_FIST -> Icons.Rounded.SportsMma
    TriggerType.HAND_THUMBS_UP -> Icons.Rounded.ThumbUp
    TriggerType.HAND_THUMBS_DOWN -> Icons.Rounded.ThumbDown
    TriggerType.HAND_VICTORY -> Icons.Rounded.SignLanguage
    TriggerType.HAND_POINTING_UP -> Icons.Rounded.BackHand
    TriggerType.HAND_SWIPE_LEFT -> Icons.Rounded.SwipeLeft
    TriggerType.HAND_SWIPE_RIGHT -> Icons.Rounded.SwipeRight
    TriggerType.HAND_SWIPE_UP -> Icons.Rounded.SwipeUp
    TriggerType.HAND_SWIPE_DOWN -> Icons.Rounded.SwipeDown
    TriggerType.PROXIMITY_WAVE -> Icons.Rounded.Waves
    TriggerType.PHONE_TILT_LEFT -> Icons.Rounded.ScreenRotation
    TriggerType.PHONE_TILT_RIGHT -> Icons.Rounded.ScreenRotation
    TriggerType.PHONE_TILT_FORWARD -> Icons.Rounded.ScreenRotation
    TriggerType.PHONE_TILT_BACK -> Icons.Rounded.ScreenRotation
    TriggerType.PHONE_SHAKE -> Icons.Rounded.Vibration
    TriggerType.BACK_DOUBLE_KNOCK -> Icons.Rounded.DoorFront
    TriggerType.BACK_TRIPLE_KNOCK -> Icons.Rounded.DoorFront
    TriggerType.VOLUME_UP -> Icons.AutoMirrored.Rounded.VolumeUp
    TriggerType.VOLUME_DOWN -> Icons.AutoMirrored.Rounded.VolumeDown
    TriggerType.VOICE_NEXT -> Icons.Rounded.Mic
    TriggerType.VOICE_BACK -> Icons.Rounded.Mic
    TriggerType.VOICE_LIKE -> Icons.Rounded.Mic
    TriggerType.VOICE_PAUSE -> Icons.Rounded.Mic
    TriggerType.VOICE_STOP -> Icons.Rounded.Mic
}

/** Marketing name of an Android API level, for "Needs Android X" hints. */
fun androidVersionName(sdk: Int): String = when (sdk) {
    in 0..25 -> "7"
    26, 27 -> "8"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31, 32 -> "12"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    36 -> "16"
    else -> "17+"
}

/** Actions offered in quick pickers, in display order. */
object ActionCatalog {
    val gestures: List<Action> = listOf(
        Action.Swipe(Direction.UP),
        Action.Swipe(Direction.DOWN),
        Action.Swipe(Direction.LEFT),
        Action.Swipe(Direction.RIGHT),
        Action.Scroll(Direction.DOWN),
        Action.Scroll(Direction.UP),
        Action.Tap(),
        Action.DoubleTap(),
        Action.LongPress(),
    )
    val system: List<Action> = GlobalAction.entries.map { Action.Global(it) }
    val media: List<Action> = MediaCommand.entries.map { Action.Media(it) }
}
