package io.github.arnavdugad.flick.feature.macro

import io.github.arnavdugad.flick.core.macro.MacroRecorder
import io.github.arnavdugad.flick.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Coordinates macro recording between the UI (start), the recorder overlay (touches) and storage.
 * While [recording], the overlay captures touches over other apps and replays each one straight
 * away so the app underneath responds normally.
 */
class MacroRecording(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps.asStateFlow()

    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Name of each macro saved, for a confirmation. */
    val saved: SharedFlow<String> = _saved.asSharedFlow()

    var recorder: MacroRecorder? = null
        private set

    fun start(screenWidth: Int, screenHeight: Int) {
        recorder = MacroRecorder(screenWidth, screenHeight)
        _steps.value = 0
        _recording.value = true
    }

    fun onStepRecorded() {
        _steps.value = recorder?.stepCount ?: 0
    }

    fun cancel() {
        recorder = null
        _recording.value = false
    }

    fun finish() {
        val r = recorder
        recorder = null
        _recording.value = false
        if (r == null || r.stepCount == 0) return
        scope.launch {
            val n = settings.settings.first().macros.size + 1
            val name = "Macro $n"
            settings.upsertMacro(r.build(UUID.randomUUID().toString(), name))
            _saved.tryEmit(name)
        }
    }
}
