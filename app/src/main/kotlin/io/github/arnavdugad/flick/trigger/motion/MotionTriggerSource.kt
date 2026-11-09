package io.github.arnavdugad.flick.trigger.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import io.github.arnavdugad.flick.BuildConfig
import io.github.arnavdugad.flick.core.detect.MotionDetector
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.trigger.SourceStatus
import io.github.arnavdugad.flick.trigger.TriggerSink
import io.github.arnavdugad.flick.trigger.TriggerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Tilt, shake and back-knock from the accelerometer. Readings are processed on a dedicated
 * background thread; the sensor runs at its fastest rate only while knock detection is needed.
 */
class MotionTriggerSource(context: Context) : TriggerSource, SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    // Owned by the sensor thread.
    private val detector = MotionDetector()
    private val levels = ConcurrentHashMap<TriggerType, Float>()

    private var sink: TriggerSink? = null
    private var active: Set<TriggerType> = emptySet()
    private var highRate = false

    private val _status = MutableStateFlow(if (sensor == null) SourceStatus.NO_HARDWARE else SourceStatus.READY)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    override val triggers: Set<TriggerType> = MotionDetector.MOTION_TRIGGERS

    override fun refreshStatus() = Unit

    override fun start(active: Set<TriggerType>, tuning: (TriggerType) -> TriggerTuning, sink: TriggerSink) {
        val s = sensor ?: return
        this.sink = sink
        this.active = active
        val snapshot = triggers.associateWith(tuning)
        val wantHigh = active.any { it == TriggerType.BACK_DOUBLE_KNOCK || it == TriggerType.BACK_TRIPLE_KNOCK }

        val h = handler ?: HandlerThread("flick-motion").let { t ->
            t.start()
            thread = t
            Handler(t.looper).also { handler = it }
        }
        h.post { detector.configure(active) { snapshot.getValue(it) } }

        if (_status.value == SourceStatus.RUNNING && wantHigh == highRate) return
        runCatching { sensorManager?.unregisterListener(this) }
        highRate = wantHigh
        // Android 12+ caps apps at 200 Hz unless they hold HIGH_SAMPLING_RATE_SENSORS, and
        // SENSOR_DELAY_FASTEST then throws. 200 Hz is plenty to catch a knock's impulse.
        val periodUs = if (wantHigh) KNOCK_PERIOD_US else SensorManager.SENSOR_DELAY_GAME
        val ok = runCatching { sensorManager?.registerListener(this, s, periodUs, h) == true }
            .onFailure { Log.w(TAG, "Accelerometer registration failed", it) }
            .getOrDefault(false)
        _status.value = if (ok) SourceStatus.RUNNING else SourceStatus.ERROR
    }

    override fun stop() {
        if (sink == null) return
        runCatching { sensorManager?.unregisterListener(this) }
        sink = null
        active = emptySet()
        val h = handler
        handler = null
        h?.post { detector.reset() }
        thread?.quitSafely()
        thread = null
        levels.clear()
        _status.value = SourceStatus.READY
    }

    override fun level(type: TriggerType): Float = levels[type] ?: 0f

    override fun setNeutral() {
        handler?.post { detector.setNeutral(lastMs) }
    }

    @Volatile
    private var lastMs = 0L

    override fun onSensorChanged(event: SensorEvent) {
        val nowMs = event.timestamp / 1_000_000L
        lastMs = nowMs
        val fired = detector.onAccel(event.values[0], event.values[1], event.values[2], nowMs)
        for (t in triggers) levels[t] = detector.level(t)
        if (BuildConfig.DEBUG && highRate) logKnockDiagnostics(nowMs)
        val out = sink ?: return
        for (t in fired) out.onTrigger(t)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val TAG = "FlickMotion"

        /** 200 Hz, the most apps get without a special permission. */
        const val KNOCK_PERIOD_US = 5_000
    }

    // Debug builds only: peak linear acceleration per 250 ms window, to tune knock detection.
    private val diagMax = FloatArray(3)
    private val diagMin = FloatArray(3)
    private var diagStart = 0L
    private var diagSamples = 0

    private fun logKnockDiagnostics(nowMs: Long) {
        val l = detector.linear
        for (i in 0..2) {
            diagMax[i] = maxOf(diagMax[i], l[i])
            diagMin[i] = minOf(diagMin[i], l[i])
        }
        diagSamples++
        if (diagStart == 0L) diagStart = nowMs
        if (nowMs - diagStart < 250) return
        val peak = maxOf(diagMax.max(), -diagMin.min())
        if (peak > 0.6f) {
            Log.d(
                "FlickKnock",
                "n=$diagSamples x=[%.2f,%.2f] y=[%.2f,%.2f] z=[%.2f,%.2f] still=%.2f".format(
                    diagMin[0], diagMax[0], diagMin[1], diagMax[1], diagMin[2], diagMax[2], detector.motionEnergy,
                ),
            )
        }
        diagMax.fill(0f)
        diagMin.fill(0f)
        diagSamples = 0
        diagStart = nowMs
    }
}
