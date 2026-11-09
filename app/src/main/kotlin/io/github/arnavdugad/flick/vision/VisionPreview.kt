package io.github.arnavdugad.flick.vision

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/**
 * Tracking dots for the calibration screen and privacy badge, in *selfie* (mirrored) normalised
 * coordinates. Only landmark positions are kept — never camera pixels — and only while someone has
 * [acquire]d the preview.
 */
class VisionPreview {

    /** Interleaved x,y pairs. */
    class Points(val face: FloatArray?, val hand: FloatArray?)

    private val users = Collections.synchronizedSet(HashSet<Any>())

    val enabled: Boolean get() = users.isNotEmpty()

    fun acquire(user: Any) {
        users += user
    }

    fun release(user: Any) {
        users -= user
        if (users.isEmpty()) _points.value = EMPTY
    }

    private val _points = MutableStateFlow(EMPTY)
    val points: StateFlow<Points> = _points.asStateFlow()

    fun publishFace(points: FloatArray?) {
        if (enabled) _points.value = Points(points, _points.value.hand)
    }

    fun publishHand(points: FloatArray?) {
        if (enabled) _points.value = Points(_points.value.face, points)
    }

    private companion object {
        val EMPTY = Points(null, null)
    }
}
