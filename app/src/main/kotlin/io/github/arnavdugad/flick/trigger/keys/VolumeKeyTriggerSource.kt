package io.github.arnavdugad.flick.trigger.keys

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import io.github.arnavdugad.flick.core.detect.Cooldown
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.trigger.SourceStatus
import io.github.arnavdugad.flick.trigger.TriggerSink
import io.github.arnavdugad.flick.trigger.TriggerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Volume buttons as triggers, fed by the accessibility service's key-event filter.
 *
 * A short press fires the trigger. Holding the key (when [holdAdjustsVolume] is on) changes the
 * volume as normal, so the buttons keep working. Keys are never intercepted during calls or while
 * ringing.
 */
class VolumeKeyTriggerSource(context: Context) : TriggerSource {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var sink: TriggerSink? = null
    private var active: Set<TriggerType> = emptySet()
    private val cooldowns = HashMap<TriggerType, Cooldown>()

    /** Set by the engine from user settings. */
    var holdAdjustsVolume: Boolean = true

    private var pressed: TriggerType? = null
    private var holding = false
    private var pressedKeyCode = 0

    private val _status = MutableStateFlow(SourceStatus.READY)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    override val triggers: Set<TriggerType> = setOf(TriggerType.VOLUME_UP, TriggerType.VOLUME_DOWN)

    override fun refreshStatus() = Unit

    override fun start(active: Set<TriggerType>, tuning: (TriggerType) -> TriggerTuning, sink: TriggerSink) {
        this.sink = sink
        this.active = active
        for (t in triggers) cooldowns.getOrPut(t) { Cooldown(0) }.durationMs = tuning(t).cooldownMs
        _status.value = SourceStatus.RUNNING
    }

    override fun stop() {
        sink = null
        active = emptySet()
        cancelHold()
        pressed = null
        _status.value = SourceStatus.READY
    }

    override fun level(type: TriggerType): Float = if (pressed == type) 1f else 0f

    /** Main thread. Returns true to consume the key event. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val out = sink ?: return false
        val type = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> TriggerType.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> TriggerType.VOLUME_DOWN
            else -> return false
        }
        if (type !in active || inCallOrRinging()) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0 && pressed == null) {
                    pressed = type
                    pressedKeyCode = event.keyCode
                    holding = false
                    if (holdAdjustsVolume) main.postDelayed(holdStart, HOLD_MS)
                }
            }
            KeyEvent.ACTION_UP -> {
                if (pressed == type) {
                    val wasHolding = holding
                    cancelHold()
                    pressed = null
                    if (!wasHolding && cooldowns[type]?.tryFire(SystemClock.uptimeMillis()) != false) {
                        out.onTrigger(type)
                    }
                }
            }
        }
        return true
    }

    private val holdStart = Runnable {
        holding = true
        repeatAdjust.run()
    }

    private val repeatAdjust: Runnable = object : Runnable {
        override fun run() {
            if (pressed == null) return
            val direction = if (pressedKeyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                AudioManager.ADJUST_RAISE
            } else {
                AudioManager.ADJUST_LOWER
            }
            runCatching {
                audio?.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
            }
            main.postDelayed(this, REPEAT_MS)
        }
    }

    private fun cancelHold() {
        main.removeCallbacks(holdStart)
        main.removeCallbacks(repeatAdjust)
        holding = false
    }

    private fun inCallOrRinging(): Boolean = when (audio?.mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
        else -> false
    }

    private companion object {
        const val HOLD_MS = 450L
        const val REPEAT_MS = 120L
    }
}
