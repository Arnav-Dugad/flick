package io.github.arnavdugad.flick.vision

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracking dots for the calibration screen, in *selfie* (mirrored) normalised coordinates. Only
 * landmark positions are kept — never camera pixels — and only while a screen asks for them.
 */
class VisionPreview {

    /** Interleaved x,y pairs. */
    class Points(val face: FloatArray?, val hand: FloatArray?)

    @Volatile
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) _points.value = EMPTY
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
