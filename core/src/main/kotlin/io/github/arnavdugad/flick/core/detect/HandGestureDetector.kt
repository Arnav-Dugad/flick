package io.github.arnavdugad.flick.core.detect

import io.github.arnavdugad.flick.core.model.Direction
import io.github.arnavdugad.flick.core.model.TriggerSpecs
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlin.math.abs

/**
 * One hand observation. [x]/[y] are the palm centre in the *user's* frame (0..1, x grows to the
 * user's right, y grows downward), so callers must un-mirror front-camera coordinates.
 */
data class HandSample(
    val gesture: String?,
    val score: Float,
    val x: Float,
    val y: Float,
)

/** Static hand poses from the MediaPipe Gesture Recognizer plus air swipes from palm motion. */
class HandGestureDetector {

    private var active: Set<TriggerType> = emptySet()
    private val poseDetectors = HashMap<TriggerType, LevelDetector>()
    private val swipe = AirSwipeDetector(distance = 0.25f, cooldownMs = 900)
    private val levels = HashMap<TriggerType, Float>()

    fun level(type: TriggerType): Float = levels[type] ?: 0f

    fun configure(active: Set<TriggerType>, tuning: (TriggerType) -> TriggerTuning) {
        this.active = active intersect HAND_TRIGGERS
        for (type in GESTURE_LABELS.values) {
            val t = tuning(type)
            val threshold = TriggerSpecs[type].threshold(t.sensitivity)
            val d = poseDetectors.getOrPut(type) { LevelDetector(threshold, t.holdMs, t.cooldownMs) }
            d.threshold = threshold
            d.holdMs = t.holdMs
            d.cooldownMs = t.cooldownMs
        }
        // Swipes share one detector; use the most sensitive active swipe's settings.
        val swipeTunings = SWIPE_TRIGGERS.filter { it in this.active }.map { it to tuning(it) }
        val chosen = swipeTunings.maxByOrNull { it.second.sensitivity }
            ?: (TriggerType.HAND_SWIPE_LEFT to tuning(TriggerType.HAND_SWIPE_LEFT))
        swipe.distance = TriggerSpecs[chosen.first].threshold(chosen.second.sensitivity)
        swipe.cooldownMs = chosen.second.cooldownMs
    }

    fun onHand(s: HandSample, nowMs: Long): List<TriggerType> {
        val fired = ArrayList<TriggerType>(1)
        val current = s.gesture?.let(GESTURE_LABELS::get)
        for ((_, type) in GESTURE_LABELS) {
            val level = if (type == current) s.score.coerceIn(0f, 1f) else 0f
            levels[type] = level
            if (type in active && poseDetectors[type]?.onSample(level, nowMs) == true) fired += type
        }
        if (SWIPE_TRIGGERS.any { it in active }) {
            val dir = swipe.onSample(s.x, s.y, nowMs)
            val travel = swipe.lastTravel
            levels[TriggerType.HAND_SWIPE_LEFT] = travel
            levels[TriggerType.HAND_SWIPE_RIGHT] = travel
            levels[TriggerType.HAND_SWIPE_UP] = travel
            levels[TriggerType.HAND_SWIPE_DOWN] = travel
            val t = when (dir) {
                Direction.LEFT -> TriggerType.HAND_SWIPE_LEFT
                Direction.RIGHT -> TriggerType.HAND_SWIPE_RIGHT
                Direction.UP -> TriggerType.HAND_SWIPE_UP
                Direction.DOWN -> TriggerType.HAND_SWIPE_DOWN
                null -> null
            }
            if (t != null && t in active) fired += t
        }
        return fired
    }

    fun onHandLost(nowMs: Long) {
        for (d in poseDetectors.values) d.onSample(0f, nowMs)
        swipe.onLost()
        levels.clear()
    }

    fun reset() {
        poseDetectors.values.forEach { it.reset() }
        swipe.onLost()
        levels.clear()
    }

    companion object {
        /** MediaPipe canned gesture labels. */
        val GESTURE_LABELS: Map<String, TriggerType> = mapOf(
            "Open_Palm" to TriggerType.HAND_OPEN_PALM,
            "Closed_Fist" to TriggerType.HAND_FIST,
            "Thumb_Up" to TriggerType.HAND_THUMBS_UP,
            "Thumb_Down" to TriggerType.HAND_THUMBS_DOWN,
            "Victory" to TriggerType.HAND_VICTORY,
            "Pointing_Up" to TriggerType.HAND_POINTING_UP,
        )
        val SWIPE_TRIGGERS = setOf(
            TriggerType.HAND_SWIPE_LEFT, TriggerType.HAND_SWIPE_RIGHT,
            TriggerType.HAND_SWIPE_UP, TriggerType.HAND_SWIPE_DOWN,
        )
        val HAND_TRIGGERS: Set<TriggerType> = GESTURE_LABELS.values.toSet() + SWIPE_TRIGGERS
    }
}

/**
 * Detects a hand moving quickly across the camera: at least [distance] of the frame within
 * [windowMs], mostly along one axis.
 */
class AirSwipeDetector(var distance: Float, cooldownMs: Long, val windowMs: Long = 450) {

    private data class Point(val t: Long, val x: Float, val y: Float)

    private val points = ArrayDeque<Point>()
    private val cooldown = Cooldown(cooldownMs)

    var cooldownMs: Long
        get() = cooldown.durationMs
        set(value) {
            cooldown.durationMs = value
        }

    /** Largest single-axis travel inside the current window, for meters. */
    var lastTravel: Float = 0f
        private set

    fun onSample(x: Float, y: Float, nowMs: Long): Direction? {
        points.addLast(Point(nowMs, x, y))
        while (points.isNotEmpty() && nowMs - points.first().t > windowMs) points.removeFirst()
        val first = points.first()
        val dx = x - first.x
        val dy = y - first.y
        lastTravel = maxOf(abs(dx), abs(dy)).coerceIn(0f, 1f)
        val dir = when {
            abs(dx) >= distance && abs(dx) >= DOMINANCE * abs(dy) -> if (dx > 0) Direction.RIGHT else Direction.LEFT
            abs(dy) >= distance && abs(dy) >= DOMINANCE * abs(dx) -> if (dy > 0) Direction.DOWN else Direction.UP
            else -> null
        } ?: return null
        points.clear()
        return if (cooldown.tryFire(nowMs)) dir else null
    }

    fun onLost() {
        points.clear()
        lastTravel = 0f
    }

    private companion object {
        const val DOMINANCE = 1.5f
    }
}
