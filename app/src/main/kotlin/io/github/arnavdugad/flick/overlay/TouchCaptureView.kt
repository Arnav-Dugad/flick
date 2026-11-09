package io.github.arnavdugad.flick.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * Full-screen, see-through view that records the user's touches for a macro. A thin red frame
 * shows recording is on. Each finished stroke is handed to [onStroke] (which replays it so the app
 * underneath responds).
 */
@SuppressLint("ViewConstructor")
class TouchCaptureView(
    context: Context,
    private val onDown: (x: Float, y: Float, t: Long) -> Unit,
    private val onMove: (x: Float, y: Float, t: Long) -> Unit,
    private val onUp: (x: Float, y: Float, t: Long) -> Unit,
    private val onCancel: () -> Unit,
) : View(context) {

    private val frame = Paint().apply {
        color = Color.argb(200, 255, 69, 58)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 4
    }

    /** True while a stroke is being replayed; touches are ignored. */
    var replaying: Boolean = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = frame.strokeWidth / 2
        canvas.drawRect(h, h, width - h, height - h, frame)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (replaying) return true
        if (event.getPointerId(event.actionIndex) != 0 && event.actionMasked != MotionEvent.ACTION_CANCEL) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_MOVE -> onMove(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_UP -> onUp(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_CANCEL -> onCancel()
        }
        return true
    }
}
