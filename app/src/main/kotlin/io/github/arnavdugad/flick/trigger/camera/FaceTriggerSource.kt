package io.github.arnavdugad.flick.trigger.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.arnavdugad.flick.core.detect.FaceGestureDetector
import io.github.arnavdugad.flick.core.detect.FaceSample
import io.github.arnavdugad.flick.core.detect.HeadPose
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.vision.CameraController
import io.github.arnavdugad.flick.vision.CameraFrame
import io.github.arnavdugad.flick.vision.VisionPreview
import kotlinx.coroutines.CoroutineScope

/** Head pose and facial expressions from MediaPipe Face Landmarker (blendshapes + pose matrix). */
class FaceTriggerSource(
    private val context: Context,
    camera: CameraController,
    scope: CoroutineScope,
    private val preview: VisionPreview,
) : CameraTriggerSource<FaceLandmarker>(camera, scope) {

    override val triggers: Set<TriggerType> = FaceGestureDetector.FACE_TRIGGERS

    // Result thread only.
    private val detector = FaceGestureDetector()
    private var lastSample: FaceSample? = null

    /** "GPU" or "CPU", for diagnostics. */
    @Volatile
    var delegateInUse: String? = null
        private set

    override fun createTask(): FaceLandmarker = try {
        build(Delegate.GPU).also { delegateInUse = "GPU" }
    } catch (e: Throwable) {
        Log.w(TAG, "GPU delegate unavailable, using CPU", e)
        build(Delegate.CPU).also { delegateInUse = "CPU" }
    }

    private fun build(delegate: Delegate): FaceLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL)
            .setDelegate(delegate)
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { e ->
                Log.w(TAG, "Face landmarker error", e)
                onResultDone()
            }
            .build()
        return FaceLandmarker.createFromOptions(context, options)
    }

    override fun submit(task: FaceLandmarker, frame: CameraFrame, timestampMs: Long) {
        val image = BitmapImageBuilder(frame.bitmap).build()
        val options = ImageProcessingOptions.builder().setRotationDegrees(frame.rotationDegrees).build()
        task.detectAsync(image, options, timestampMs)
    }

    override fun applyConfig(config: Config) {
        detector.configure(config.active) { config.tuning.getValue(it) }
    }

    override fun applyNeutral(): Boolean {
        val s = lastSample ?: return false
        detector.setNeutral(s, SystemClock.uptimeMillis())
        return true
    }

    private fun onResult(result: FaceLandmarkerResult) {
        onResultDone()
        val now = SystemClock.uptimeMillis()
        val shapes = result.faceBlendshapes().orElse(null)?.firstOrNull()
        val matrix = result.facialTransformationMatrixes().orElse(null)?.firstOrNull()
        if (shapes == null || matrix == null) {
            lastSample = null
            detector.onFaceLost(now)
            publishLevels(triggers) { 0f }
            preview.publishFace(null)
            return
        }
        subjectSeen()

        var blinkL = 0f
        var blinkR = 0f
        var jaw = 0f
        var smileL = 0f
        var smileR = 0f
        var browInner = 0f
        var browOuterL = 0f
        var browOuterR = 0f
        for (c in shapes) {
            when (c.categoryName()) {
                "eyeBlinkLeft" -> blinkL = c.score()
                "eyeBlinkRight" -> blinkR = c.score()
                "jawOpen" -> jaw = c.score()
                "mouthSmileLeft" -> smileL = c.score()
                "mouthSmileRight" -> smileR = c.score()
                "browInnerUp" -> browInner = c.score()
                "browOuterUpLeft" -> browOuterL = c.score()
                "browOuterUpRight" -> browOuterR = c.score()
            }
        }
        val angles = HeadPose.fromMatrix(matrix)
        val sample = FaceSample(
            eyeBlinkLeft = blinkL,
            eyeBlinkRight = blinkR,
            jawOpen = jaw,
            smileLeft = smileL,
            smileRight = smileR,
            browInnerUp = browInner,
            browOuterUpLeft = browOuterL,
            browOuterUpRight = browOuterR,
            pitchDeg = angles.pitchDeg,
            yawDeg = angles.yawDeg,
            rollDeg = angles.rollDeg,
        )
        lastSample = sample
        val fired = detector.onFace(sample, now)
        publishLevels(triggers, detector::level)
        fire(fired)

        if (preview.enabled) {
            val landmarks = result.faceLandmarks().firstOrNull()
            if (landmarks != null) {
                val step = 4
                val out = FloatArray((landmarks.size / step + 1) * 2)
                var i = 0
                var j = 0
                while (i < landmarks.size && j + 1 < out.size) {
                    out[j++] = 1f - landmarks[i].x() // mirror for a selfie view
                    out[j++] = landmarks[i].y()
                    i += step
                }
                preview.publishFace(out.copyOf(j))
            }
        }
    }

    private companion object {
        const val TAG = "FlickFace"
        const val MODEL = "face_landmarker.task"
    }
}
