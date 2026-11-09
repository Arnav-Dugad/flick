package io.github.arnavdugad.flick.trigger.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import io.github.arnavdugad.flick.core.detect.HandGestureDetector
import io.github.arnavdugad.flick.core.detect.HandSample
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.vision.CameraController
import io.github.arnavdugad.flick.vision.CameraFrame
import io.github.arnavdugad.flick.vision.VisionPreview
import kotlinx.coroutines.CoroutineScope

/** Hand poses (MediaPipe Gesture Recognizer) and air swipes (palm motion from its landmarks). */
class HandTriggerSource(
    private val context: Context,
    camera: CameraController,
    scope: CoroutineScope,
    private val preview: VisionPreview,
) : CameraTriggerSource<GestureRecognizer>(camera, scope) {

    override val triggers: Set<TriggerType> = HandGestureDetector.HAND_TRIGGERS

    private val detector = HandGestureDetector()

    override fun createTask(): GestureRecognizer = try {
        build(Delegate.GPU)
    } catch (e: Throwable) {
        Log.w(TAG, "GPU delegate unavailable, using CPU", e)
        build(Delegate.CPU)
    }

    private fun build(delegate: Delegate): GestureRecognizer {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL)
            .setDelegate(delegate)
            .build()
        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { e ->
                Log.w(TAG, "Gesture recognizer error", e)
                onResultDone()
            }
            .build()
        return GestureRecognizer.createFromOptions(context, options)
    }

    override fun submit(task: GestureRecognizer, frame: CameraFrame, timestampMs: Long) {
        val image = BitmapImageBuilder(frame.bitmap).build()
        val options = ImageProcessingOptions.builder().setRotationDegrees(frame.rotationDegrees).build()
        task.recognizeAsync(image, options, timestampMs)
    }

    override fun applyConfig(config: Config) {
        detector.configure(config.active) { config.tuning.getValue(it) }
    }

    override fun applyNeutral(): Boolean = true

    private fun onResult(result: GestureRecognizerResult) {
        onResultDone()
        val now = SystemClock.uptimeMillis()
        val landmarks = result.landmarks().firstOrNull()
        if (landmarks.isNullOrEmpty()) {
            detector.onHandLost(now)
            publishLevels(triggers) { 0f }
            preview.publishHand(null)
            return
        }
        subjectSeen()
        val top = result.gestures().firstOrNull()?.firstOrNull()
        // Palm centre from wrist + finger bases; the front camera image is not mirrored, so
        // flip x to get the user's own left/right.
        var px = 0f
        var py = 0f
        for (i in PALM) {
            px += landmarks[i].x()
            py += landmarks[i].y()
        }
        px /= PALM.size
        py /= PALM.size
        val sample = HandSample(
            gesture = top?.categoryName(),
            score = top?.score() ?: 0f,
            x = 1f - px,
            y = py,
        )
        val fired = detector.onHand(sample, now)
        publishLevels(triggers, detector::level)
        fire(fired)

        if (preview.enabled) {
            val out = FloatArray(landmarks.size * 2)
            landmarks.forEachIndexed { i, l ->
                out[i * 2] = 1f - l.x()
                out[i * 2 + 1] = l.y()
            }
            preview.publishHand(out)
        }
    }

    private companion object {
        const val TAG = "FlickHand"
        const val MODEL = "gesture_recognizer.task"
        val PALM = intArrayOf(0, 5, 9, 13, 17)
    }
}
