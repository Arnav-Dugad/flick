package io.github.arnavdugad.flick.core.detect

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PulseSequenceTest {

    @Test
    fun `counts pulses and reports when the sequence ends`() {
        val s = PulseSequence(minGapMs = 80, maxGapMs = 400)
        s.pulse(0)
        s.pulse(200)
        assertNull(s.tick(500)) // 300 ms since last: still open
        assertEquals(2, s.tick(601))
        assertNull(s.tick(700)) // already reported
    }

    @Test
    fun `bounce inside min gap is ignored`() {
        val s = PulseSequence(minGapMs = 80, maxGapMs = 400)
        assertTrue(s.pulse(0))
        assertFalse(s.pulse(30))
        assertEquals(1, s.tick(500))
    }

    @Test
    fun `late pulse starts a new sequence`() {
        val s = PulseSequence(minGapMs = 80, maxGapMs = 400)
        s.pulse(0)
        s.pulse(1_000) // never ticked in between
        assertEquals(1, s.tick(1_500))
    }
}

class NeutralBaselineTest {

    @Test
    fun `first sample becomes neutral`() {
        val b = NeutralBaseline(adaptLimit = 5f)
        assertEquals(0f, b.update(12f, 0))
        assertEquals(12f, b.current)
    }

    @Test
    fun `slow drift is absorbed`() {
        val b = NeutralBaseline(adaptLimit = 5f, timeConstantMs = 1_000)
        b.update(0f, 0)
        var t = 0L
        repeat(1_000) { t += 20; b.update(3f, t) } // 20 s at +3°
        assertTrue(abs(b.current!! - 3f) < 0.1f)
    }

    @Test
    fun `a deliberate large tilt is not absorbed`() {
        val b = NeutralBaseline(adaptLimit = 5f, timeConstantMs = 1_000)
        b.update(0f, 0)
        var t = 0L
        var relative = 0f
        repeat(500) { t += 20; relative = b.update(25f, t) }
        assertEquals(0f, b.current)
        assertEquals(25f, relative)
    }

    @Test
    fun `set snaps to the given value`() {
        val b = NeutralBaseline(adaptLimit = 5f)
        b.update(0f, 0)
        b.set(30f, 10)
        assertEquals(0f, b.update(30f, 20), 0.001f)
    }
}

class FrameGovernorTest {

    @Test
    fun `accepts frames at the active rate`() {
        val g = FrameGovernor(activeFps = 10, idleFps = 2, idleAfterMs = 5_000)
        var accepted = 0
        var t = 0L
        while (t < 1_000) {
            if (g.accept(t)) {
                accepted++
                g.onSubjectSeen(t)
            }
            t += 33 // ~30 fps camera
        }
        assertTrue(accepted in 9..11, "accepted=$accepted")
    }

    @Test
    fun `drops to idle rate when nothing is seen`() {
        val g = FrameGovernor(activeFps = 15, idleFps = 2, idleAfterMs = 1_000)
        var t = 0L
        while (t < 1_500) { g.accept(t); t += 33 } // nothing seen
        var accepted = 0
        val start = t
        while (t < start + 2_000) {
            if (g.accept(t)) accepted++
            t += 33
        }
        assertTrue(accepted in 3..5, "accepted=$accepted")
        assertTrue(g.isIdle)
    }

    @Test
    fun `throttle reduces the rate`() {
        val g = FrameGovernor(activeFps = 20, idleFps = 2)
        g.throttle = 0.5f
        assertEquals(10f, g.currentFps(0))
    }
}
