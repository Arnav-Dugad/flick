package io.github.arnavdugad.flick.trigger.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.arnavdugad.flick.core.detect.Cooldown
import io.github.arnavdugad.flick.core.detect.KeywordSpotter
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.trigger.SourceStatus
import io.github.arnavdugad.flick.trigger.TriggerSink
import io.github.arnavdugad.flick.trigger.TriggerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Experimental: offline voice keywords ("next", "back", "like", "pause", "stop") using Android's
 * on-device speech recogniser (Android 12+). Never falls back to network recognition. Listens in
 * a loop, restarting after each utterance with a short backoff after errors.
 */
class VoiceTriggerSource(private val context: Context) : TriggerSource {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val spotter = KeywordSpotter()
    private val cooldowns = HashMap<TriggerType, Cooldown>()

    private var sink: TriggerSink? = null
    private var active: Set<TriggerType> = emptySet()
    private var listening = false
    private var errorStreak = 0

    private val _status = MutableStateFlow(computeStatus())
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private val _heard = MutableStateFlow("")

    /** Latest transcript, shown on the calibration screen. */
    val heard: StateFlow<String> = _heard.asStateFlow()

    override val triggers: Set<TriggerType> = KeywordSpotter.DEFAULT_KEYWORDS.values.toSet()

    private fun computeStatus(): SourceStatus = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> SourceStatus.UNSUPPORTED
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> SourceStatus.UNSUPPORTED
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED -> SourceStatus.NO_PERMISSION
        recognizer != null -> SourceStatus.RUNNING
        else -> SourceStatus.READY
    }

    override fun refreshStatus() {
        _status.value = computeStatus()
        if (sink != null && recognizer == null && _status.value == SourceStatus.READY) startRecognizer()
    }

    override fun start(active: Set<TriggerType>, tuning: (TriggerType) -> TriggerTuning, sink: TriggerSink) {
        this.sink = sink
        this.active = active
        for (t in triggers) cooldowns.getOrPut(t) { Cooldown(0) }.durationMs = tuning(t).cooldownMs
        if (recognizer == null) startRecognizer()
    }

    private fun startRecognizer() {
        _status.value = computeStatus()
        if (_status.value != SourceStatus.READY || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
            .onFailure { Log.w(TAG, "Recogniser unavailable", it) }
            .getOrNull()
            ?.also { it.setRecognitionListener(listener) }
        _status.value = if (recognizer != null) SourceStatus.RUNNING else SourceStatus.UNSUPPORTED
        errorStreak = 0
        listen()
    }

    override fun stop() {
        sink = null
        active = emptySet()
        main.removeCallbacks(restart)
        listening = false
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
        _heard.value = ""
        _status.value = computeStatus()
    }

    override fun level(type: TriggerType): Float = if (listening) 0.1f else 0f

    private fun listen() {
        val r = recognizer ?: return
        spotter.reset()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        runCatching { r.startListening(intent) }
            .onSuccess { listening = true }
            .onFailure { scheduleRestart(error = true) }
    }

    private val restart = Runnable { if (sink != null) listen() }

    private fun scheduleRestart(error: Boolean) {
        listening = false
        main.removeCallbacks(restart)
        errorStreak = if (error) errorStreak + 1 else 0
        val delay = if (errorStreak == 0) 150L else minOf(5_000L, 300L shl minOf(errorStreak, 4))
        main.postDelayed(restart, delay)
    }

    private fun handleTranscript(text: String) {
        if (text.isBlank()) return
        _heard.value = text
        val out = sink ?: return
        val now = SystemClock.uptimeMillis()
        for (t in spotter.onTranscript(text)) {
            if (t in active && cooldowns[t]?.tryFire(now) != false) out.onTrigger(t)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.let(::handleTranscript)
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.let(::handleTranscript)
            scheduleRestart(error = false)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    _status.value = SourceStatus.NO_PERMISSION
                    listening = false
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    _status.value = SourceStatus.UNSUPPORTED
                    listening = false
                }
                // Silence or no match is normal: listen again straight away.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(error = false)
                else -> scheduleRestart(error = true)
            }
        }
    }

    private companion object {
        const val TAG = "FlickVoice"
    }
}
