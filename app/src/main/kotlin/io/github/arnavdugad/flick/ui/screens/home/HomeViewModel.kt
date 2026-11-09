package io.github.arnavdugad.flick.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.di.AppContainer
import io.github.arnavdugad.flick.engine.EngineState
import io.github.arnavdugad.flick.engine.FiredTrigger
import io.github.arnavdugad.flick.trigger.SourceStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val engine = container.engine

    val engineState: StateFlow<EngineState> = engine.state

    val settings: StateFlow<FlickSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val statuses: StateFlow<Map<TriggerType, SourceStatus>> = engine.statuses

    val lastApp: StateFlow<String?> = engine.lastApp

    /** Live trigger events (no replay). */
    val events: SharedFlow<FiredTrigger> = engine.events

    fun setMasterEnabled(enabled: Boolean) = engine.setMasterEnabled(enabled)

    fun setPracticeMode(enabled: Boolean) = engine.setPracticeMode(enabled)

    fun refreshSources() = engine.refreshSources()

    override fun onCleared() {
        engine.setPracticeMode(false)
    }
}
