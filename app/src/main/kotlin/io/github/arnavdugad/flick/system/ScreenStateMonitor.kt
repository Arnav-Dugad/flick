package io.github.arnavdugad.flick.system

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenState(val interactive: Boolean, val locked: Boolean) {
    /** True when the user is actively looking at an unlocked phone. */
    val usable: Boolean get() = interactive && !locked
}

/** Tracks screen on/off and lock state so triggers only run while the phone is in use. */
class ScreenStateMonitor(private val context: Context) {

    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<ScreenState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _state.value = read()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // System broadcasts are still delivered to non-exported receivers.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Re-reads the state; call when something else (e.g. an activity resume) hints at a change. */
    fun refresh() {
        _state.value = read()
    }

    private fun read() = ScreenState(
        interactive = power?.isInteractive ?: true,
        locked = keyguard?.isKeyguardLocked ?: false,
    )
}
