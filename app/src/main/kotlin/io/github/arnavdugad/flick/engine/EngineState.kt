package io.github.arnavdugad.flick.engine

import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.system.ScreenState

enum class EngineStatus {
    /** The accessibility service is not connected. */
    SERVICE_OFF,

    /** The user switched Flick off with the master toggle / kill switch. */
    PAUSED_BY_USER,

    /** Screen off or locked: everything sleeps. */
    SCREEN_OFF,

    /** Listening for gestures and running actions. */
    ACTIVE,
}

data class EngineState(
    val serviceConnected: Boolean = false,
    val masterEnabled: Boolean = true,
    val screen: ScreenState = ScreenState(interactive = true, locked = false),
    val practiceMode: Boolean = false,
    val foregroundPackage: String? = null,
) {
    val status: EngineStatus
        get() = when {
            !serviceConnected -> EngineStatus.SERVICE_OFF
            !masterEnabled -> EngineStatus.PAUSED_BY_USER
            !screen.usable -> EngineStatus.SCREEN_OFF
            else -> EngineStatus.ACTIVE
        }

    /** Whether trigger sources should be running. Practice mode listens even when paused. */
    val listening: Boolean
        get() = status == EngineStatus.ACTIVE || (practiceMode && screen.usable)
}

/** Emitted every time a trigger is recognised, whether or not an action ran. */
data class FiredTrigger(
    val trigger: TriggerType,
    val action: Action?,
    val executed: Boolean,
    val practice: Boolean,
    val atElapsedMs: Long,
)
