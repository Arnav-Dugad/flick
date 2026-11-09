package io.github.arnavdugad.flick.core.mindful

import kotlinx.serialization.Serializable

@Serializable
data class MindfulSettings(
    val enabled: Boolean = false,
    /** Nudge after this many short videos in a session (0 = never). */
    val reelLimit: Int = 50,
    /** Act after this many minutes in short-video apps in a session (0 = never). */
    val minuteLimit: Int = 20,
    /** At the minute limit, pause Flick instead of only nudging. */
    val autoPause: Boolean = false,
)

enum class NudgeKind { REELS, MINUTES, AUTO_PAUSED }

data class Nudge(val kind: NudgeKind, val reels: Int, val minutes: Int)

/**
 * Counts short videos and time spent in short-video apps, and decides when to nudge. A session
 * resets after [sessionGapMs] away from those apps. Each limit nudges once per session, then
 * again every time the count doubles, so reminders stay gentle.
 */
class MindfulTracker(private val sessionGapMs: Long = 10 * 60_000L) {

    var reels: Int = 0
        private set
    var activeMs: Long = 0
        private set

    private var lastSeenMs: Long? = null
    private var lastTickMs: Long? = null
    private var nextReelNudge = 0
    private var nextMinuteNudge = 0

    val minutes: Int get() = (activeMs / 60_000L).toInt()

    /** A short video was advanced. */
    fun onReel(nowMs: Long, settings: MindfulSettings): Nudge? {
        maybeResetSession(nowMs)
        lastSeenMs = nowMs
        reels++
        if (!settings.enabled || settings.reelLimit <= 0) return null
        if (nextReelNudge == 0) nextReelNudge = settings.reelLimit
        if (reels >= nextReelNudge) {
            nextReelNudge *= 2
            return Nudge(NudgeKind.REELS, reels, minutes)
        }
        return null
    }

    /** Call periodically (e.g. every few seconds). [inShortVideoApp] while one is in front. */
    fun onTick(nowMs: Long, inShortVideoApp: Boolean, settings: MindfulSettings): Nudge? {
        val last = lastTickMs
        lastTickMs = nowMs
        if (!inShortVideoApp) return null
        maybeResetSession(nowMs)
        lastSeenMs = nowMs
        if (last != null) activeMs += (nowMs - last).coerceIn(0, MAX_TICK_MS)
        if (!settings.enabled || settings.minuteLimit <= 0) return null
        if (nextMinuteNudge == 0) nextMinuteNudge = settings.minuteLimit
        if (minutes >= nextMinuteNudge) {
            nextMinuteNudge *= 2
            return Nudge(if (settings.autoPause) NudgeKind.AUTO_PAUSED else NudgeKind.MINUTES, reels, minutes)
        }
        return null
    }

    private fun maybeResetSession(nowMs: Long) {
        val seen = lastSeenMs ?: return
        if (nowMs - seen > sessionGapMs) {
            reels = 0
            activeMs = 0
            nextReelNudge = 0
            nextMinuteNudge = 0
        }
    }

    private companion object {
        const val MAX_TICK_MS = 30_000L
    }
}
