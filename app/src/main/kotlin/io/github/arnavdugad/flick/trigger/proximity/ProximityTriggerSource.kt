package io.github.arnavdugad.flick.trigger.proximity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.arnavdugad.flick.core.detect.WaveDetector
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.trigger.SourceStatus
import io.github.arnavdugad.flick.trigger.TriggerSink
import io.github.arnavdugad.flick.trigger.TriggerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/** "Wave to scroll": a hand passed over the proximity sensor near the earpiece. */
class ProximityTriggerSource(context: Context) : TriggerSource, SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    // Prefer a non-wake-up sensor so we never keep the CPU awake; fall back to the default.
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY, false)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val detector = WaveDetector()
    private var sink: TriggerSink? = null

    private val _near = MutableStateFlow(false)

    /** Live "is something covering the sensor" signal, for the UI. */
    val near: StateFlow<Boolean> = _near.asStateFlow()

    /** Name of the sensor, shown in diagnostics (some phones only offer a virtual one). */
    val sensorName: String? get() = sensor?.name

    private val _status = MutableStateFlow(if (sensor == null) SourceStatus.NO_HARDWARE else SourceStatus.READY)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    override val triggers: Set<TriggerType> = setOf(TriggerType.PROXIMITY_WAVE)

    override fun refreshStatus() = Unit

    override fun start(active: Set<TriggerType>, tuning: (TriggerType) -> TriggerTuning, sink: TriggerSink) {
        val s = sensor ?: return
        val t = tuning(TriggerType.PROXIMITY_WAVE)
        detector.config = WaveDetector.Config(
            minCoverMs = 30,
            maxCoverMs = 500 + (t.sensitivity * 1_000).toLong(),
            cooldownMs = t.cooldownMs,
        )
        val wasRunning = this.sink != null
        this.sink = sink
        if (wasRunning) return
        detector.reset()
        val ok = runCatching {
            sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME) == true
        }.getOrDefault(false)
        if (ok) {
            _status.value = SourceStatus.RUNNING
        } else {
            this.sink = null
            _status.value = SourceStatus.ERROR
        }
    }

    override fun stop() {
        if (sink == null) return
        runCatching { sensorManager?.unregisterListener(this) }
        sink = null
        detector.reset()
        _near.value = false
        _status.value = SourceStatus.READY
    }

    override fun level(type: TriggerType): Float = if (_near.value) 1f else 0f

    override fun onSensorChanged(event: SensorEvent) {
        val s = sensor ?: return
        // Many proximity sensors are binary (0 = near, maximumRange = far); others report cm.
        val threshold = if (s.maximumRange > 0f) min(s.maximumRange, NEAR_CM) else 1f
        val isNear = event.values[0] < threshold
        _near.value = isNear
        val nowMs = event.timestamp / 1_000_000L
        if (detector.onSample(isNear, nowMs)) sink?.onTrigger(TriggerType.PROXIMITY_WAVE)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val NEAR_CM = 5f
    }
}
