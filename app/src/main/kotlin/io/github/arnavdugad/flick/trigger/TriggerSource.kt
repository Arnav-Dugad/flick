package io.github.arnavdugad.flick.trigger

import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlinx.coroutines.flow.StateFlow

/** Why a source can or can't run right now. */
enum class SourceStatus {
    READY,
    RUNNING,
    NO_HARDWARE,
    NO_PERMISSION,
    UNSUPPORTED,

    /** Another app is using the camera; resumes automatically. */
    BUSY,

    /** The system refused access (e.g. camera blocked for background apps). */
    BLOCKED,
    ERROR,
}

/** Receives recognised triggers. May be called from any thread. */
fun interface TriggerSink {
    fun onTrigger(type: TriggerType)
}

/**
 * A pluggable input. The engine calls [start] with exactly the triggers it currently needs (and
 * calls it again whenever that set or the tuning changes), and [stop] when none are needed.
 * Implementations must never throw: missing hardware or revoked permissions show up in [status].
 */
interface TriggerSource {

    val triggers: Set<TriggerType>

    val status: StateFlow<SourceStatus>

    /** Re-check hardware and permissions (e.g. after returning from system settings). */
    fun refreshStatus()

    /** Main thread. Idempotent: repeated calls update the active set and tuning in place. */
    fun start(active: Set<TriggerType>, tuning: (TriggerType) -> TriggerTuning, sink: TriggerSink)

    /** Main thread. */
    fun stop()

    /** Latest normalised signal (0..1) for calibration meters. Safe from any thread. */
    fun level(type: TriggerType): Float

    /** Re-centre pose-based triggers on the current position. */
    fun setNeutral() = Unit
}
