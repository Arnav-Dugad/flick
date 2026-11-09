package io.github.arnavdugad.flick.core.detect

import io.github.arnavdugad.flick.core.model.TriggerSpecs
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandGestureDetectorTest {

    private fun detector(vararg active: TriggerType) =
        HandGestureDetector().apply { configure(active.toSet()) { TriggerSpecs[it].defaults } }

    @Test
    fun `held thumbs up fires once`() {
        val d = detector(TriggerType.HAND_THUMBS_UP)
        val fired = mutableListOf<TriggerType>()
        var t = 0L
        while (t < 2_000) {
            fired += d.onHand(HandSample("Thumb_Up", 0.9f, 0.5f, 0.5f), t)
            t += 50
        }
        assertEquals(listOf(TriggerType.HAND_THUMBS_UP), fired)
    }

    @Test
    fun `low confidence gesture is ignored`() {
        val d = detector(TriggerType.HAND_FIST)
        var fired = 0
        var t = 0L
        while (t < 2_000) {
            fired += d.onHand(HandSample("Closed_Fist", 0.3f, 0.5f, 0.5f), t).size
            t += 50
        }
        assertEquals(0, fired)
    }

    @Test
    fun `fast horizontal motion is an air swipe in the right direction`() {
        val d = detector(TriggerType.HAND_SWIPE_LEFT, TriggerType.HAND_SWIPE_RIGHT)
        val fired = mutableListOf<TriggerType>()
        var t = 0L
        var x = 0.8f
        while (t < 300) {
            fired += d.onHand(HandSample("None", 0.9f, x, 0.5f), t)
            x -= 0.05f
            t += 33
        }
        assertEquals(listOf(TriggerType.HAND_SWIPE_LEFT), fired)
    }

    @Test
    fun `slow drift is not a swipe`() {
        val d = detector(TriggerType.HAND_SWIPE_RIGHT)
        var fired = 0
        var t = 0L
        var x = 0.1f
        while (t < 5_000) {
            fired += d.onHand(HandSample(null, 0f, x, 0.5f), t).size
            x += 0.002f
            t += 33
        }
        assertEquals(0, fired)
    }
}

class MotionDetectorTest {

    private val g = 9.81f

    private fun detector(vararg active: TriggerType) =
        MotionDetector().apply { configure(active.toSet()) { TriggerSpecs[it].defaults } }

    /** Phone held upright facing the user, rotated sideways by [rollDeg] (positive = left edge down). */
    private fun MotionDetector.hold(rollDeg: Double, from: Long, to: Long, stepMs: Long = 20): List<TriggerType> {
        val r = rollDeg * PI / 180
        val out = mutableListOf<TriggerType>()
        var t = from
        while (t < to) {
            out += onAccel((g * sin(r)).toFloat(), (g * cos(r) * 0.8).toFloat(), (g * cos(r) * 0.6).toFloat(), t)
            t += stepMs
        }
        return out
    }

    @Test
    fun `steady phone fires nothing`() {
        val d = detector(TriggerType.PHONE_TILT_LEFT, TriggerType.PHONE_TILT_RIGHT, TriggerType.PHONE_SHAKE)
        assertTrue(d.hold(0.0, 0, 5_000).isEmpty())
    }

    @Test
    fun `tilting left fires tilt left once`() {
        val d = detector(TriggerType.PHONE_TILT_LEFT, TriggerType.PHONE_TILT_RIGHT)
        d.hold(0.0, 0, 2_000)
        val fired = d.hold(35.0, 2_000, 4_000) + d.hold(0.0, 4_000, 5_000)
        assertEquals(listOf(TriggerType.PHONE_TILT_LEFT), fired)
    }

    @Test
    fun `tilting right fires tilt right`() {
        val d = detector(TriggerType.PHONE_TILT_LEFT, TriggerType.PHONE_TILT_RIGHT)
        d.hold(0.0, 0, 2_000)
        val fired = d.hold(-35.0, 2_000, 4_000)
        assertEquals(listOf(TriggerType.PHONE_TILT_RIGHT), fired)
    }

    @Test
    fun `vigorous shaking fires shake`() {
        val d = detector(TriggerType.PHONE_SHAKE)
        d.hold(0.0, 0, 1_000)
        val fired = mutableListOf<TriggerType>()
        var t = 1_000L
        while (t < 2_000) {
            val swing = if ((t / 100) % 2 == 0L) 25f else -25f
            fired += d.onAccel(swing, g * 0.8f, g * 0.6f, t)
            t += 10
        }
        assertEquals(listOf(TriggerType.PHONE_SHAKE), fired)
    }

    @Test
    fun `two sharp knocks fire a double knock`() {
        val d = detector(TriggerType.BACK_DOUBLE_KNOCK, TriggerType.BACK_TRIPLE_KNOCK)
        val fired = mutableListOf<TriggerType>()
        var t = 0L
        fun still(until: Long) {
            while (t < until) {
                fired += d.onAccel(0f, g * 0.8f, g * 0.6f, t)
                t += 5
            }
        }
        fun knock() {
            fired += d.onAccel(0f, g * 0.8f, g * 0.6f + 6f, t)
            t += 5
        }
        still(2_000)
        knock()
        still(2_200)
        knock()
        still(3_000)
        assertEquals(listOf(TriggerType.BACK_DOUBLE_KNOCK), fired)
    }
}

class KeywordSpotterTest {

    @Test
    fun `finds keywords in a sentence`() {
        val s = KeywordSpotter()
        assertEquals(listOf(TriggerType.VOICE_NEXT), s.onTranscript("okay Next please"))
    }

    @Test
    fun `growing partial results fire each word once`() {
        val s = KeywordSpotter()
        assertEquals(listOf(TriggerType.VOICE_NEXT), s.onTranscript("next"))
        assertTrue(s.onTranscript("next").isEmpty())
        assertEquals(listOf(TriggerType.VOICE_NEXT), s.onTranscript("next next"))
        assertEquals(listOf(TriggerType.VOICE_LIKE), s.onTranscript("next next like"))
    }

    @Test
    fun `reset starts a new utterance`() {
        val s = KeywordSpotter()
        s.onTranscript("pause")
        s.reset()
        assertEquals(listOf(TriggerType.VOICE_PAUSE), s.onTranscript("pause"))
    }

    @Test
    fun `words inside other words do not match`() {
        val s = KeywordSpotter()
        assertTrue(s.onTranscript("backpack unlikely stopwatch").isEmpty())
    }
}

class TriggerSpecsTest {

    @Test
    fun `higher sensitivity means a lower threshold`() {
        for (type in TriggerType.entries) {
            val spec = TriggerSpecs[type]
            if (spec.thresholdRange == null) continue
            assertTrue(spec.threshold(1f) < spec.threshold(0f), "$type")
        }
    }

    @Test
    fun `defaults are within their ranges`() {
        for (type in TriggerType.entries) {
            val spec = TriggerSpecs[type]
            assertEquals(spec.defaults, spec.defaults.sanitized(spec), "$type")
        }
    }
}
