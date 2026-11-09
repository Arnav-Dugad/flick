package io.github.arnavdugad.flick.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A direction on screen. */
@Serializable
enum class Direction { UP, DOWN, LEFT, RIGHT;

    val opposite: Direction
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
}

/** System-wide actions. [minSdk] is the first Android API level that supports the action. */
@Serializable
enum class GlobalAction(val minSdk: Int) {
    BACK(16),
    HOME(16),
    RECENTS(16),
    NOTIFICATIONS(16),
    QUICK_SETTINGS(17),
    POWER_DIALOG(21),
    SPLIT_SCREEN(24),
    LOCK_SCREEN(28),
    SCREENSHOT(28),
    DISMISS_NOTIFICATION_SHADE(31),
    ALL_APPS(31),
}

@Serializable
enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, MUTE_TOGGLE }

/**
 * Something Flick does on the user's behalf. Coordinates are fractions (0..1) of the screen so
 * actions survive rotation and different screen sizes.
 */
@Serializable
sealed interface Action {

    /**
     * A finger swipe travelling in [direction]. Swiping [Direction.UP] reveals content below,
     * e.g. the next reel. [distance] is the fraction of the screen dimension travelled.
     */
    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val direction: Direction,
        val distance: Float = DEFAULT_SWIPE_DISTANCE,
        val durationMs: Long = DEFAULT_SWIPE_MS,
    ) : Action

    /**
     * Scroll the main scrollable view so content in [direction] comes into view
     * (DOWN = further down the page). Uses accessibility node actions and falls back to a swipe.
     */
    @Serializable
    @SerialName("scroll")
    data class Scroll(val direction: Direction) : Action

    @Serializable
    @SerialName("tap")
    data class Tap(val x: Float = 0.5f, val y: Float = 0.5f) : Action

    @Serializable
    @SerialName("double_tap")
    data class DoubleTap(val x: Float = 0.5f, val y: Float = 0.5f) : Action

    @Serializable
    @SerialName("long_press")
    data class LongPress(
        val x: Float = 0.5f,
        val y: Float = 0.5f,
        val durationMs: Long = DEFAULT_LONG_PRESS_MS,
    ) : Action

    @Serializable
    @SerialName("global")
    data class Global(val action: GlobalAction) : Action

    @Serializable
    @SerialName("media")
    data class Media(val command: MediaCommand) : Action

    /** Start or stop auto-scroll in the current app. */
    @Serializable
    @SerialName("auto_scroll")
    data object AutoScroll : Action

    /** Replay a recorded macro. */
    @Serializable
    @SerialName("macro")
    data class RunMacro(val macroId: String) : Action

    /** Pause Flick entirely (the kill switch). */
    @Serializable
    @SerialName("pause_flick")
    data object PauseFlick : Action

    companion object {
        const val DEFAULT_SWIPE_DISTANCE = 0.45f
        const val DEFAULT_SWIPE_MS = 280L
        const val DEFAULT_LONG_PRESS_MS = 650L
    }
}

/** Common, named actions used for defaults and quick pickers. */
object Actions {
    val Next = Action.Swipe(Direction.UP)
    val Previous = Action.Swipe(Direction.DOWN)
    val ScrollDown = Action.Scroll(Direction.DOWN)
    val ScrollUp = Action.Scroll(Direction.UP)
    val Like = Action.DoubleTap()
    val TapCenter = Action.Tap()
    val Back = Action.Global(GlobalAction.BACK)
    val Home = Action.Global(GlobalAction.HOME)
    val PlayPause = Action.Media(MediaCommand.PLAY_PAUSE)
}
