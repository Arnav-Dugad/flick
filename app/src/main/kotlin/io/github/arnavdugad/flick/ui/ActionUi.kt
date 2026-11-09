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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.Direction
import io.github.arnavdugad.flick.core.model.GlobalAction
import io.github.arnavdugad.flick.core.model.MediaCommand
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
fun triggerLabel(trigger: TriggerType): String = when (trigger) {
    TriggerType.PROXIMITY_WAVE -> stringResource(R.string.trigger_proximity_wave)
    else -> trigger.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Suppress("UNUSED_PARAMETER") // Per-trigger icons arrive with the camera/motion triggers in M2.
fun triggerIcon(trigger: TriggerType): ImageVector = Icons.Rounded.Waves

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
