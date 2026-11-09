package io.github.arnavdugad.flick.trigger

import io.github.arnavdugad.flick.core.model.TriggerType

/**
 * A pluggable input. The engine starts a source only while the active profile maps one of its
 * triggers, and stops it otherwise. Implementations must never throw from [start]/[stop]:
 * missing hardware or revoked permissions are reported through [isAvailable].
 */
interface TriggerSource {

    /** Triggers this source can emit. */
    val triggers: Set<TriggerType>

    /** False when the hardware is missing or a required permission is not granted. */
    val isAvailable: Boolean

    val isRunning: Boolean

    /** Start listening. Calling start while running just replaces the callback. */
    fun start(onTrigger: (TriggerType) -> Unit)

    fun stop()
}
