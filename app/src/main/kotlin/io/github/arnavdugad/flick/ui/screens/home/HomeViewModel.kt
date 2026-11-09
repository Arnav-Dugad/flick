package io.github.arnavdugad.flick.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.di.AppContainer
import io.github.arnavdugad.flick.engine.EngineState
import io.github.arnavdugad.flick.engine.FiredTrigger
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val engine = container.engine

    val engineState: StateFlow<EngineState> = engine.state

    val settings: StateFlow<FlickSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Live trigger events (no replay). */
    val events: SharedFlow<FiredTrigger> = engine.events

    val lastEvent: StateFlow<FiredTrigger?> = engine.events
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sensorCovered: StateFlow<Boolean> = container.proximity.near

    val proximityAvailable: Boolean get() = container.proximity.isAvailable

    fun setMasterEnabled(enabled: Boolean) = engine.setMasterEnabled(enabled)

    fun setPracticeMode(enabled: Boolean) = engine.setPracticeMode(enabled)

    fun setWaveAction(action: Action) {
        viewModelScope.launch { container.settings.setWaveAction(action) }
    }

    override fun onCleared() {
        engine.setPracticeMode(false)
    }
}
