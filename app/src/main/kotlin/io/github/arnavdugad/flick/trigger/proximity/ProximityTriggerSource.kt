package io.github.arnavdugad.flick.trigger.proximity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.arnavdugad.flick.core.detect.WaveDetector
import io.github.arnavdugad.flick.core.model.TriggerType
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
    private var callback: ((TriggerType) -> Unit)? = null

    private val _near = MutableStateFlow(false)

    /** Live "is something covering the sensor" signal, for the UI. */
    val near: StateFlow<Boolean> = _near.asStateFlow()

    private val _samples = MutableStateFlow(0L)

    /** Number of readings received since the last start; 0 hints at a virtual/inactive sensor. */
    val sampleCount: StateFlow<Long> = _samples.asStateFlow()

    override val triggers: Set<TriggerType> = setOf(TriggerType.PROXIMITY_WAVE)

    override val isAvailable: Boolean get() = sensor != null

    override val isRunning: Boolean get() = callback != null

    override fun start(onTrigger: (TriggerType) -> Unit) {
        val s = sensor ?: return
        val wasRunning = callback != null
        callback = onTrigger
        if (wasRunning) return
        detector.reset()
        _samples.value = 0
        val ok = runCatching {
            sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME) == true
        }.getOrDefault(false)
        if (!ok) callback = null
    }

    override fun stop() {
        if (callback == null) return
        runCatching { sensorManager?.unregisterListener(this) }
        callback = null
        detector.reset()
        _near.value = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val s = sensor ?: return
        // Many proximity sensors are binary (0 = near, maximumRange = far); others report cm.
        val threshold = if (s.maximumRange > 0f) min(s.maximumRange, NEAR_CM) else 1f
        val isNear = event.values[0] < threshold
        _near.value = isNear
        _samples.value += 1
        val nowMs = event.timestamp / 1_000_000L
        if (detector.onSample(isNear, nowMs)) {
            callback?.invoke(TriggerType.PROXIMITY_WAVE)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val NEAR_CM = 5f
    }
}
