package io.github.arnavdugad.flick.core.detect

import io.github.arnavdugad.flick.core.model.TriggerSpecs
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun face(
    blink: Float = 0f,
    jaw: Float = 0f,
    smile: Float = 0f,
    brow: Float = 0f,
    pitch: Float = 0f,
    yaw: Float = 0f,
    roll: Float = 0f,
) = FaceSample(blink, blink, jaw, smile, smile, brow, brow, brow, pitch, yaw, roll)

private fun detector(vararg active: TriggerType, tuning: (TriggerType) -> TriggerTuning = { TriggerSpecs[it].defaults }) =
    FaceGestureDetector().apply { configure(active.toSet(), tuning) }

/** Feeds [sample] at 30 fps between [from] and [to]; returns everything that fired. */
private fun FaceGestureDetector.run(sample: FaceSample, from: Long, to: Long): List<TriggerType> {
    val out = mutableListOf<TriggerType>()
    var t = from
    while (t < to) {
        out += onFace(sample, t)
        t += 33
    }
    return out
}

class FaceGestureDetectorTest {

    @Test
    fun `held smile fires once`() {
        val d = detector(TriggerType.FACE_SMILE)
        val fired = d.run(face(smile = 0.9f), 0, 3_000)
        assertEquals(listOf(TriggerType.FACE_SMILE), fired)
    }

    @Test
    fun `a brief smile shorter than the hold is ignored`() {
        val d = detector(TriggerType.FACE_SMILE)
        val fired = d.run(face(smile = 0.9f), 0, 150) + d.run(face(), 150, 1_000)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `inactive triggers never fire`() {
        val d = detector(TriggerType.FACE_SMILE)
        val fired = d.run(face(jaw = 1f, brow = 1f), 0, 2_000)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `head tilt is relative to the neutral pose`() {
        val d = detector(TriggerType.HEAD_TILT_DOWN, TriggerType.HEAD_TILT_UP)
        // User normally holds their head 20° up; that must not count as a tilt.
        assertTrue(d.run(face(pitch = 20f), 0, 2_000).isEmpty())
        // Looking 20° down from there does.
        val fired = d.run(face(pitch = 0f), 2_000, 3_000)
        assertEquals(listOf(TriggerType.HEAD_TILT_DOWN), fired)
    }

    @Test
    fun `quick nod fires nod`() {
        val d = detector(TriggerType.HEAD_NOD)
        d.run(face(), 0, 500)
        val fired = mutableListOf<TriggerType>()
        // Down 20° and back over 400 ms.
        var t = 500L
        while (t < 900) {
            val phase = (t - 500) / 400.0
            fired += d.onFace(face(pitch = (-20 * sin(phase * PI)).toFloat()), t)
            t += 33
        }
        fired += d.run(face(), 900, 1_500)
        assertEquals(listOf(TriggerType.HEAD_NOD), fired)
    }

    @Test
    fun `with nod active a slow look down is a tilt, not a nod`() {
        val d = detector(TriggerType.HEAD_NOD, TriggerType.HEAD_TILT_DOWN)
        d.run(face(), 0, 500)
        val fired = d.run(face(pitch = -20f), 500, 2_000) + d.run(face(), 2_000, 2_500)
        assertEquals(listOf(TriggerType.HEAD_TILT_DOWN), fired)
    }

    @Test
    fun `long blink fires once per closure`() {
        val d = detector(TriggerType.FACE_LONG_BLINK)
        val fired = d.run(face(blink = 0.9f), 0, 2_000) + d.run(face(), 2_000, 2_500)
        assertEquals(listOf(TriggerType.FACE_LONG_BLINK), fired)
    }

    @Test
    fun `natural single blink does nothing`() {
        val d = detector(TriggerType.FACE_LONG_BLINK, TriggerType.FACE_DOUBLE_BLINK)
        val fired = d.run(face(blink = 0.9f), 0, 120) + d.run(face(), 120, 2_000)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `two quick blinks are a double blink`() {
        val d = detector(TriggerType.FACE_DOUBLE_BLINK)
        val fired = d.run(face(blink = 0.9f), 0, 130) +
            d.run(face(), 130, 330) +
            d.run(face(blink = 0.9f), 330, 460) +
            d.run(face(), 460, 1_500)
        assertEquals(listOf(TriggerType.FACE_DOUBLE_BLINK), fired)
    }

    @Test
    fun `losing the face releases holds`() {
        val d = detector(TriggerType.FACE_MOUTH_OPEN)
        d.run(face(jaw = 0.9f), 0, 150)
        d.onFaceLost(160)
        val fired = d.run(face(jaw = 0.9f), 500, 600)
        assertTrue(fired.isEmpty())
    }
}

class HeadPoseTest {

    private fun rotX(deg: Double): FloatArray {
        val r = deg * PI / 180
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        // Column-major 4x4.
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, c, s, 0f,
            0f, -s, c, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    @Test
    fun `identity is zero`() {
        val a = HeadPose.fromMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        assertEquals(0f, a.pitchDeg, 0.01f)
        assertEquals(0f, a.yawDeg, 0.01f)
        assertEquals(0f, a.rollDeg, 0.01f)
    }

    @Test
    fun `rotation about x is pitch`() {
        val a = HeadPose.fromMatrix(rotX(20.0))
        assertEquals(20f, a.pitchDeg, 0.01f)
        assertEquals(0f, a.yawDeg, 0.01f)
        assertEquals(0f, a.rollDeg, 0.01f)
    }
}
