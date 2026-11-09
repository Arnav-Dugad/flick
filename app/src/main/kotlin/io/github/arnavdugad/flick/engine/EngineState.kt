package io.github.arnavdugad.flick.engine

import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.core.profiles.Profile
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
    /** Trigger shown on the calibration screen; it listens even if unbound, and no actions run. */
    val calibrating: TriggerType? = null,
    val foregroundPackage: String? = null,
    /** Profile for the app in front; null outside any profile (or inside Flick itself). */
    val activeProfile: Profile? = null,
) {
    val status: EngineStatus
        get() = when {
            !serviceConnected -> EngineStatus.SERVICE_OFF
            !masterEnabled -> EngineStatus.PAUSED_BY_USER
            !screen.usable -> EngineStatus.SCREEN_OFF
            else -> EngineStatus.ACTIVE
        }

    /** Actions only run for real when active and nobody is practising or calibrating. */
    val actionsEnabled: Boolean
        get() = status == EngineStatus.ACTIVE && !practiceMode && calibrating == null

    /** Bindings that apply right now. */
    val activeBindings: Map<TriggerType, Action>
        get() = activeProfile?.effectiveBindings.orEmpty()

    /**
     * The triggers that should be listening. Normally only the active profile's; while practising,
     * every enabled profile's (so any of your gestures can be tried from Flick's own screen).
     * The camera and microphone therefore only run inside apps that have a profile using them.
     */
    fun wantedTriggers(practiceSet: Set<TriggerType>): Set<TriggerType> {
        if (!screen.usable) return emptySet()
        val base = when {
            practiceMode -> practiceSet
            status == EngineStatus.ACTIVE -> activeBindings.keys
            else -> emptySet()
        }
        return if (calibrating != null) base + calibrating else base
    }
}

/** Emitted every time a trigger is recognised, whether or not an action ran. */
data class FiredTrigger(
    val trigger: TriggerType,
    val action: Action?,
    val executed: Boolean,
    val practice: Boolean,
    val atElapsedMs: Long,
)
