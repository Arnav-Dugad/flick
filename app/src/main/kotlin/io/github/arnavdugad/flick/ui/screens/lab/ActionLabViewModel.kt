package io.github.arnavdugad.flick.ui.screens.lab

import androidx.lifecycle.ViewModel
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.di.AppContainer
import io.github.arnavdugad.flick.engine.EngineState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ActionLabViewModel(private val container: AppContainer) : ViewModel() {

    val engineState: StateFlow<EngineState> = container.engine.state

    private val _delay = MutableStateFlow(3)
    val delaySeconds: StateFlow<Int> = _delay.asStateFlow()

    /** Actions waiting to run, with seconds remaining. */
    private val _pending = MutableStateFlow<Map<Action, Int>>(emptyMap())
    val pending: StateFlow<Map<Action, Int>> = _pending.asStateFlow()

    private val _results = MutableSharedFlow<Pair<Action, Boolean>>(extraBufferCapacity = 8)
    val results: SharedFlow<Pair<Action, Boolean>> = _results.asSharedFlow()

    fun setDelay(seconds: Int) {
        _delay.value = seconds
    }

    /**
     * Runs on the app scope rather than viewModelScope so a countdown keeps going after the user
     * leaves Flick to watch the action happen in another app.
     */
    fun run(action: Action) {
        if (_pending.value.containsKey(action)) return
        val seconds = _delay.value
        container.appScope.launch {
            for (remaining in seconds downTo 1) {
                _pending.update { it + (action to remaining) }
                delay(1_000)
            }
            _pending.update { it + (action to 0) }
            val ok = container.engine.runAction(action)
            _pending.update { it - action }
            _results.emit(action to ok)
        }
    }

    companion object {
        val DELAYS = listOf(0, 3, 6)
    }
}
