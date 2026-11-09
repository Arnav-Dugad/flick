package io.github.arnavdugad.flick.core

import io.github.arnavdugad.flick.core.autoscroll.AutoScrollMode
import io.github.arnavdugad.flick.core.autoscroll.AutoScrollPlan
import io.github.arnavdugad.flick.core.autoscroll.AutoScrollSettings
import io.github.arnavdugad.flick.core.macro.Macro
import io.github.arnavdugad.flick.core.macro.MacroPoint
import io.github.arnavdugad.flick.core.macro.MacroRecorder
import io.github.arnavdugad.flick.core.mindful.MindfulSettings
import io.github.arnavdugad.flick.core.mindful.MindfulTracker
import io.github.arnavdugad.flick.core.mindful.NudgeKind
import io.github.arnavdugad.flick.core.model.Action
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacroRecorderTest {

    @Test
    fun `a tap becomes one short step`() {
        val r = MacroRecorder(1000, 2000)
        r.down(500f, 1000f, 0)
        val step = r.up(500f, 1000f, 60)
        assertNotNull(step)
        assertEquals(0.5f, step.points.first().x)
        assertEquals(0.5f, step.points.first().y)
        assertEquals(60, step.durationMs)
        assertEquals(0, step.delayMs)
    }

    @Test
    fun `delay between strokes is recorded and capped`() {
        val r = MacroRecorder(1000, 2000, maxDelayMs = 3_000)
        r.down(1f, 1f, 0); r.up(1f, 1f, 50)
        r.down(1f, 1f, 1_050); val second = r.up(1f, 1f, 1_100)
        r.down(1f, 1f, 20_000); val third = r.up(1f, 1f, 20_050)
        assertEquals(1_000, second!!.delayMs)
        assertEquals(3_000, third!!.delayMs)
        assertEquals(3, r.stepCount)
    }

    @Test
    fun `long swipes are simplified to the point limit but keep both ends`() {
        val r = MacroRecorder(1000, 2000, maxPointsPerStep = 8)
        r.down(500f, 1800f, 0)
        for (i in 1..100) r.move(500f, 1800f - i * 12f, i * 3L)
        val step = r.up(500f, 600f, 310)!!
        assertTrue(step.points.size <= 8)
        assertEquals(0.9f, step.points.first().y, 0.001f)
        assertEquals(0.3f, step.points.last().y, 0.001f)
    }

    @Test
    fun `coordinates are clamped to the screen`() {
        val r = MacroRecorder(1000, 2000)
        r.down(-50f, 3000f, 0)
        val p = r.up(-50f, 3000f, 50)!!.points.first()
        assertEquals(MacroPoint(0f, 1f, 0), p)
    }

    @Test
    fun `macro and new actions serialise`() {
        val json = Json
        val r = MacroRecorder(1000, 2000)
        r.down(100f, 100f, 0); r.up(100f, 100f, 50)
        val m = r.build("m1", "Like and next")
        assertEquals(m, json.decodeFromString(Macro.serializer(), json.encodeToString(Macro.serializer(), m)))
        for (a in listOf(Action.AutoScroll, Action.PauseFlick, Action.RunMacro("m1"))) {
            assertEquals(a, json.decodeFromString(Action.serializer(), json.encodeToString(Action.serializer(), a)))
        }
    }
}

class AutoScrollPlanTest {

    @Test
    fun `continuous mode moves content up and lifts before the top`() {
        val plan = AutoScrollPlan(2000, AutoScrollSettings(AutoScrollMode.READING, speed = 10))
        var lifts = 0
        repeat(200) {
            val seg = plan.next()
            assertTrue(seg.toY <= seg.fromY, "finger moves up")
            assertTrue(seg.toY >= 2000 * 0.28f - 0.01f, "never above the top margin")
            if (seg.lift) lifts++
        }
        assertTrue(lifts > 0)
    }

    @Test
    fun `higher speed scrolls faster and teleprompter is gentler than reading`() {
        val slow = AutoScrollPlan(2000, AutoScrollSettings(AutoScrollMode.READING, speed = 1)).pixelsPerSecond
        val fast = AutoScrollPlan(2000, AutoScrollSettings(AutoScrollMode.READING, speed = 10)).pixelsPerSecond
        val tele = AutoScrollPlan(2000, AutoScrollSettings(AutoScrollMode.TELEPROMPTER, speed = 10)).pixelsPerSecond
        assertTrue(fast > slow)
        assertTrue(tele < fast)
    }

    @Test
    fun `cooking mode steps a third of a page and faster speed means shorter waits`() {
        val plan = AutoScrollPlan(3000, AutoScrollSettings(AutoScrollMode.COOKING, speed = 5))
        val seg = plan.next()
        assertTrue(seg.lift)
        assertEquals(990f, seg.fromY - seg.toY, 1f)
        val faster = AutoScrollPlan(3000, AutoScrollSettings(AutoScrollMode.COOKING, speed = 9))
        assertTrue(faster.stepIntervalMs < plan.stepIntervalMs)
    }

    @Test
    fun `speed is clamped`() {
        assertEquals(10, AutoScrollSettings(speed = 99).sanitized().speed)
        assertEquals(1, AutoScrollSettings(speed = -3).sanitized().speed)
    }
}

class MindfulTrackerTest {

    private val on = MindfulSettings(enabled = true, reelLimit = 3, minuteLimit = 2, autoPause = false)

    @Test
    fun `nudges at the reel limit then when it doubles`() {
        val t = MindfulTracker()
        val nudges = (1..12).mapNotNull { t.onReel(it * 1_000L, on) }
        assertEquals(listOf(3, 6, 12), nudges.map { it.reels })
        assertTrue(nudges.all { it.kind == NudgeKind.REELS })
    }

    @Test
    fun `disabled never nudges but still counts`() {
        val t = MindfulTracker()
        repeat(100) { assertNull(t.onReel(it * 1_000L, on.copy(enabled = false))) }
        assertEquals(100, t.reels)
    }

    @Test
    fun `time only counts in short video apps and auto pause is reported`() {
        val t = MindfulTracker()
        var now = 0L
        var nudge: io.github.arnavdugad.flick.core.mindful.Nudge? = null
        repeat(30) { now += 5_000; t.onTick(now, inShortVideoApp = false, on) }
        assertEquals(0, t.minutes)
        while (nudge == null && now < 10 * 60_000) {
            now += 5_000
            nudge = t.onTick(now, inShortVideoApp = true, on.copy(autoPause = true))
        }
        assertEquals(NudgeKind.AUTO_PAUSED, nudge?.kind)
        assertEquals(2, nudge?.minutes)
    }

    @Test
    fun `a long break starts a new session`() {
        val t = MindfulTracker(sessionGapMs = 60_000)
        repeat(5) { t.onReel(it * 1_000L, on) }
        t.onReel(10 * 60_000L, on)
        assertEquals(1, t.reels)
    }
}
