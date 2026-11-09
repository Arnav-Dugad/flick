package io.github.arnavdugad.flick.core.detect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LevelDetectorTest {

    /** Feeds [level] every 10 ms from [from] to [to] (exclusive) and counts firings. */
    private fun LevelDetector.feed(level: Float, from: Long, to: Long): Int {
        var fired = 0
        var t = from
        while (t < to) {
            if (onSample(level, t)) fired++
            t += 10
        }
        return fired
    }

    @Test
    fun `fires only after the hold time`() {
        val d = LevelDetector(threshold = 0.5f, holdMs = 200, cooldownMs = 0)
        assertEquals(0, d.feed(0.8f, 0, 200))
        assertTrue(d.onSample(0.8f, 200))
    }

    @Test
    fun `zero hold fires immediately`() {
        val d = LevelDetector(threshold = 0.5f, holdMs = 0, cooldownMs = 0)
        assertTrue(d.onSample(0.6f, 0))
    }

    @Test
    fun `below threshold never fires`() {
        val d = LevelDetector(threshold = 0.5f, holdMs = 0, cooldownMs = 0)
        assertEquals(0, d.feed(0.49f, 0, 2_000))
    }

    @Test
    fun `holding fires once until the signal returns to neutral`() {
        val d = LevelDetector(threshold = 0.5f, holdMs = 100, cooldownMs = 0)
        assertEquals(1, d.feed(0.9f, 0, 3_000))
        assertTrue(d.isLatched)
        // Dropping only slightly (above release level 0.3) keeps it latched.
        assertEquals(0, d.feed(0.4f, 3_000, 3_200))
        assertEquals(0, d.feed(0.9f, 3_200, 4_000))
        // Full release re-arms it.
        d.feed(0.1f, 4_000, 4_050)
        assertFalse(d.isLatched)
        assertEquals(1, d.feed(0.9f, 4_050, 4_500))
    }

    @Test
    fun `small dips inside the hysteresis band do not restart the hold`() {
        val d = LevelDetector(threshold = 0.5f, holdMs = 200, cooldownMs = 0)
        d.feed(0.8f, 0, 100)
        d.onSample(0.45f, 100) // dip, still above release (0.3)
        assertTrue(d.feed(0.8f, 110, 300) == 1)
    }

    @Test
    fun `a dip below release restarts the hold`() {
        val d = LevelDetector(threshold = 0.5f, holdMs = 200, cooldownMs = 0)
        d.feed(0.8f, 0, 150)
        d.onSample(0.1f, 150)
        assertEquals(0, d.feed(0.8f, 160, 350)) // only 190 ms since restart
        assertTrue(d.onSample(0.8f, 360))
    }

    @Test
    fun `cooldown spaces out separate gestures`() {
        val d = LevelDetector(threshold = 0.5f, holdMs = 0, cooldownMs = 1_000)
        assertTrue(d.onSample(0.9f, 0))
        d.onSample(0.0f, 50)
        assertFalse(d.onSample(0.9f, 100))
        assertFalse(d.onSample(0.9f, 900))
        assertTrue(d.onSample(0.9f, 1_000))
    }

    @Test
    fun `threshold is clamped to a sane range`() {
        val d = LevelDetector(threshold = -3f, holdMs = 0, cooldownMs = 0)
        assertTrue(d.threshold > 0f)
        d.threshold = 5f
        assertEquals(1f, d.threshold)
    }
}
