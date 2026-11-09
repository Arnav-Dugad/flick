package io.github.arnavdugad.flick.trigger.camera

import android.os.SystemClock
import android.util.Log
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.trigger.SourceStatus
import io.github.arnavdugad.flick.trigger.TriggerSink
import io.github.arnavdugad.flick.trigger.TriggerSource
import io.github.arnavdugad.flick.vision.CameraController
import io.github.arnavdugad.flick.vision.CameraFrame
import io.github.arnavdugad.flick.vision.FrameConsumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Shared plumbing for MediaPipe-backed sources: loads the model off the main thread, takes at most
 * one frame at a time from the [CameraController], and hands config changes to the result thread
 * safely. Subclasses only build the task and turn results into triggers.
 */
abstract class CameraTriggerSource<T : AutoCloseable>(
    private val camera: CameraController,
    private val scope: CoroutineScope,
) : TriggerSource, FrameConsumer {

    protected class Config(val active: Set<TriggerType>, val tuning: Map<TriggerType, TriggerTuning>)

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "flick-model") }

    @Volatile
    private var task: T? = null

    @Volatile
    private var running = false

    @Volatile
    private var busy = false

    @Volatile
    private var config: Config? = null
    private var appliedConfig: Config? = null

    @Volatile
    private var neutralRequested = false

    @Volatile
    protected var sink: TriggerSink? = null

    private var lastTimestamp = 0L
    private val levels = ConcurrentHashMap<TriggerType, Float>()

    private val modelFailed = MutableStateFlow(false)
    private val _status = MutableStateFlow(SourceStatus.READY)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()
    private var statusJob: Job? = null

    /** Builds the MediaPipe task (on a worker thread). Throw to report failure. */
    protected abstract fun createTask(): T

    /** Submits a frame asynchronously; must eventually call [onResultDone]. */
    protected abstract fun submit(task: T, frame: CameraFrame, timestampMs: Long)

    /** Applies a new config to the detector (result thread). */
    protected abstract fun applyConfig(config: Config)

    /** Re-centre the detector on the next sample (result thread). Return true if handled. */
    protected abstract fun applyNeutral(): Boolean

    override fun refreshStatus() = camera.refresh()

    override fun start(active: Set<TriggerType>, tuning: (TriggerType) -> TriggerTuning, sink: TriggerSink) {
        this.sink = sink
        config = Config(active, triggers.associateWith(tuning))
        if (running) return
        running = true
        if (statusJob == null) {
            statusJob = scope.launch {
                combine(camera.status, modelFailed) { cam, failed -> if (failed) SourceStatus.ERROR else cam }
                    .collect { _status.value = it }
            }
        }
        worker.execute {
            if (task == null) {
                task = runCatching { createTask() }
                    .onFailure { Log.e(TAG, "Model load failed", it) }
                    .getOrNull()
                modelFailed.value = task == null
            }
        }
        camera.addConsumer(this)
    }

    override fun stop() {
        if (!running) return
        running = false
        sink = null
        camera.removeConsumer(this)
        worker.execute {
            runCatching { task?.close() }
            task = null
            busy = false
        }
        levels.clear()
    }

    override fun setNeutral() {
        neutralRequested = true
    }

    override fun level(type: TriggerType): Float = levels[type] ?: 0f

    override fun wantsFrame(): Boolean = running && task != null && !busy

    override fun onFrame(frame: CameraFrame) {
        val t = task ?: return
        busy = true
        val ts = maxOf(lastTimestamp + 1, frame.timestampMs)
        lastTimestamp = ts
        try {
            submit(t, frame, ts)
        } catch (e: Throwable) {
            busy = false
            Log.w(TAG, "Submit failed", e)
        }
    }

    /** Subclasses call this from their result/error listener before processing. */
    protected fun onResultDone() {
        busy = false
        val c = config
        if (c != null && c !== appliedConfig) {
            appliedConfig = c
            applyConfig(c)
        }
        if (neutralRequested && applyNeutral()) neutralRequested = false
    }

    protected fun publishLevels(types: Collection<TriggerType>, read: (TriggerType) -> Float) {
        for (t in types) levels[t] = read(t)
    }

    protected fun subjectSeen() = camera.governor.onSubjectSeen(SystemClock.uptimeMillis())

    protected fun fire(types: List<TriggerType>) {
        val out = sink ?: return
        for (t in types) out.onTrigger(t)
    }

    private companion object {
        const val TAG = "FlickVision"
    }
}
