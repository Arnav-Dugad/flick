package io.github.arnavdugad.flick.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import io.github.arnavdugad.flick.core.detect.FrameGovernor
import io.github.arnavdugad.flick.trigger.SourceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** An upright-able camera frame. [rotationDegrees] rotates the bitmap to the user's view. */
class CameraFrame(val bitmap: Bitmap, val rotationDegrees: Int, val timestampMs: Long)

interface FrameConsumer {
    /** False while busy; the frame is then skipped for this consumer. Any thread. */
    fun wantsFrame(): Boolean

    /** Called on the analysis thread. The bitmap must not be recycled by consumers. */
    fun onFrame(frame: CameraFrame)
}

/**
 * Owns the front camera for the whole process. The camera runs only while at least one consumer is
 * registered, bound to this object's own lifecycle — like Google's Project Gameface, the
 * accessibility-service process keeps camera access while other apps are in the foreground.
 *
 * Low resolution, one frame in flight, and an adaptive frame rate keep it light on battery.
 */
class CameraController(
    private val context: Context,
    private val scope: CoroutineScope,
) : LifecycleOwner {

    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    private val consumers = CopyOnWriteArraySet<FrameConsumer>()
    private var analysisExecutor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var bindJob: Job? = null

    val governor = FrameGovernor(activeFps = 15, idleFps = 4)

    private val _status = MutableStateFlow(SourceStatus.READY)
    val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private val _fps = MutableStateFlow(0f)

    /** Frames actually analysed per second, for diagnostics. */
    val fps: StateFlow<Float> = _fps.asStateFlow()
    private var fpsWindowStart = 0L
    private var fpsCount = 0

    private val power = context.getSystemService(PowerManager::class.java)
    private var thermalListener: Any? = null

    private val stateObserver = Observer<CameraState> { state ->
        _status.value = when (state.error?.code) {
            CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE -> SourceStatus.BUSY
            CameraState.ERROR_CAMERA_DISABLED -> SourceStatus.BLOCKED
            CameraState.ERROR_CAMERA_FATAL_ERROR, CameraState.ERROR_STREAM_CONFIG,
            CameraState.ERROR_OTHER_RECOVERABLE_ERROR, CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED,
            -> SourceStatus.ERROR
            else -> when (state.type) {
                CameraState.Type.OPEN, CameraState.Type.OPENING -> SourceStatus.RUNNING
                else -> if (consumers.isEmpty()) SourceStatus.READY else _status.value
            }
        }
        if (state.error != null) Log.w(TAG, "Camera state ${state.type} error ${state.error?.code}")
    }

    init {
        registry.currentState = Lifecycle.State.CREATED
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Main thread. */
    fun addConsumer(consumer: FrameConsumer) {
        consumers += consumer
        ensureRunning()
    }

    /** Main thread. */
    fun removeConsumer(consumer: FrameConsumer) {
        consumers -= consumer
        if (consumers.isEmpty()) shutDown()
    }

    /** Re-checks permission; call after the user returns from a permission prompt. */
    fun refresh() {
        if (consumers.isNotEmpty()) ensureRunning() else if (!hasPermission()) _status.value = SourceStatus.NO_PERMISSION
    }

    private fun ensureRunning() {
        if (!hasPermission()) {
            _status.value = SourceStatus.NO_PERMISSION
            return
        }
        if (camera != null || bindJob?.isActive == true) return
        bindJob = scope.launch {
            try {
                val p = provider ?: ProcessCameraProvider.awaitInstance(context).also { provider = it }
                if (consumers.isEmpty()) return@launch
                bind(p)
            } catch (t: Throwable) {
                Log.w(TAG, "Camera bind failed", t)
                _status.value = if (t is SecurityException) SourceStatus.NO_PERMISSION else SourceStatus.ERROR
            }
        }
    }

    private fun bind(p: ProcessCameraProvider) {
        val selector = CameraSelector.DEFAULT_FRONT_CAMERA
        if (!p.hasCamera(selector)) {
            _status.value = SourceStatus.NO_HARDWARE
            return
        }
        val executor = analysisExecutor ?: Executors.newSingleThreadExecutor { r ->
            Thread(r, "flick-camera").apply { priority = Thread.NORM_PRIORITY - 1 }
        }.also { analysisExecutor = it }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(TARGET_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(executor, ::analyze)

        p.unbindAll()
        governor.reset()
        registry.currentState = Lifecycle.State.STARTED
        val cam = p.bindToLifecycle(this, selector, analysis)
        camera = cam
        cam.cameraInfo.cameraState.observeForever(stateObserver)
        startThermalWatch()
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val now = SystemClock.uptimeMillis()
            if (consumers.none { it.wantsFrame() } || !governor.accept(now)) return
            val frame = CameraFrame(proxy.toBitmap(), proxy.imageInfo.rotationDegrees, now)
            for (c in consumers) if (c.wantsFrame()) c.onFrame(frame)
            countFps(now)
        } catch (t: Throwable) {
            Log.w(TAG, "Frame analysis failed", t)
        } finally {
            proxy.close()
        }
    }

    private fun countFps(now: Long) {
        if (fpsWindowStart == 0L) fpsWindowStart = now
        fpsCount++
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1_000) {
            _fps.value = fpsCount * 1_000f / elapsed
            fpsCount = 0
            fpsWindowStart = now
        }
    }

    private fun shutDown() {
        bindJob?.cancel()
        bindJob = null
        camera?.cameraInfo?.cameraState?.removeObserver(stateObserver)
        camera = null
        registry.currentState = Lifecycle.State.CREATED
        runCatching { provider?.unbindAll() }
        stopThermalWatch()
        _fps.value = 0f
        _status.value = if (hasPermission()) SourceStatus.READY else SourceStatus.NO_PERMISSION
    }

    /** Lowers the frame rate when the phone is hot or in battery saver. */
    private fun startThermalWatch() {
        val pm = power ?: return
        updateThrottle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener == null) {
            val l = PowerManager.OnThermalStatusChangedListener { updateThrottle() }
            pm.addThermalStatusListener(ContextCompat.getMainExecutor(context), l)
            thermalListener = l
        }
    }

    private fun stopThermalWatch() {
        val pm = power ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (thermalListener as? PowerManager.OnThermalStatusChangedListener)?.let(pm::removeThermalStatusListener)
        }
        thermalListener = null
    }

    private fun updateThrottle() {
        val pm = power ?: return
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> 1f
                PowerManager.THERMAL_STATUS_MODERATE -> 0.6f
                else -> 0.3f
            }
        } else {
            1f
        }
        val saver = if (pm.isPowerSaveMode) 0.7f else 1f
        governor.throttle = minOf(thermal, saver)
    }

    private companion object {
        const val TAG = "FlickCamera"
        val TARGET_SIZE = Size(640, 480)
    }
}
