package io.github.arnavdugad.flick.core.detect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaveDetectorTest {

    private val config = WaveDetector.Config(minCoverMs = 30, maxCoverMs = 900, cooldownMs = 700)

    /** Covers the sensor at [start] for [duration] ms; returns whether the wave fired. */
    private fun WaveDetector.wave(start: Long, duration: Long): Boolean {
        onSample(near = true, nowMs = start)
        return onSample(near = false, nowMs = start + duration)
    }

    @Test
    fun `quick cover and uncover is a wave`() {
        assertTrue(WaveDetector(config).wave(start = 0, duration = 200))
    }

    @Test
    fun `cover shorter than minimum is ignored as noise`() {
        assertFalse(WaveDetector(config).wave(start = 0, duration = 10))
    }

    @Test
    fun `cover longer than maximum never fires`() {
        assertFalse(WaveDetector(config).wave(start = 0, duration = 5_000))
    }

    @Test
    fun `boundaries are inclusive`() {
        assertTrue(WaveDetector(config).wave(start = 0, duration = 30))
        assertTrue(WaveDetector(config).wave(start = 0, duration = 900))
    }

    @Test
    fun `repeated near samples keep the original cover start`() {
        val detector = WaveDetector(config)
        detector.onSample(near = true, nowMs = 0)
        detector.onSample(near = true, nowMs = 500)
        detector.onSample(near = true, nowMs = 800)
        // Covered for 1000 ms in total, which exceeds the maximum.
        assertFalse(detector.onSample(near = false, nowMs = 1_000))
    }

    @Test
    fun `far without prior near does nothing`() {
        val detector = WaveDetector(config)
        assertFalse(detector.onSample(near = false, nowMs = 0))
        assertFalse(detector.onSample(near = false, nowMs = 100))
    }

    @Test
    fun `cooldown suppresses rapid second wave`() {
        val detector = WaveDetector(config)
        assertTrue(detector.wave(start = 0, duration = 100)) // fires at 100
        assertFalse(detector.wave(start = 300, duration = 100)) // fires at 400 < 800
        assertTrue(detector.wave(start = 700, duration = 100)) // fires at 800
    }

    @Test
    fun `counts waves in a realistic stream`() {
        val detector = WaveDetector(config)
        var fired = 0
        var t = 0L
        repeat(5) {
            if (detector.wave(start = t, duration = 150)) fired++
            t += 1_000
        }
        assertEquals(5, fired)
    }

    @Test
    fun `reset clears an in-progress cover`() {
        val detector = WaveDetector(config)
        detector.onSample(near = true, nowMs = 0)
        assertTrue(detector.isCovered)
        detector.reset()
        assertFalse(detector.isCovered)
        assertFalse(detector.onSample(near = false, nowMs = 100))
    }

    @Test
    fun `changing config updates cooldown`() {
        val detector = WaveDetector(config)
        assertTrue(detector.wave(start = 0, duration = 100))
        detector.config = config.copy(cooldownMs = 0)
        assertTrue(detector.wave(start = 150, duration = 100))
    }

    @Test
    fun `invalid config is rejected`() {
        assertFailsWith<IllegalArgumentException> { WaveDetector.Config(minCoverMs = 500, maxCoverMs = 100) }
        assertFailsWith<IllegalArgumentException> { WaveDetector.Config(cooldownMs = -1) }
    }
}
