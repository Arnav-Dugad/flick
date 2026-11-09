package io.github.arnavdugad.flick.overlay

import android.accessibilityservice.AccessibilityService
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.core.macro.Macro
import io.github.arnavdugad.flick.core.mindful.Nudge
import io.github.arnavdugad.flick.core.model.Actions
import io.github.arnavdugad.flick.data.FlickSettings
import io.github.arnavdugad.flick.engine.EngineStatus
import io.github.arnavdugad.flick.feature.autoscroll.AutoScrollStatus
import io.github.arnavdugad.flick.trigger.SourceStatus
import io.github.arnavdugad.flick.vision.VisionPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Decides which overlays are on screen and keeps them in sync with Flick's state. Lives exactly as
 * long as the accessibility service connection.
 */
class OverlayController(private val service: AccessibilityService) {

    private val c = service.container
    private val host = OverlayHost(service)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val density = service.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private var settings = FlickSettings()

    // Edge glow
    private var glowView: View? = null
    private var glowPulse by mutableIntStateOf(0)
    private var glowRemoval: Job? = null

    // Privacy badge
    private var badgeView: View? = null
    private var badgePaused by mutableStateOf(false)
    private var badgePoints by mutableStateOf<VisionPreview.Points?>(null)
    private var badgePreviewJob: Job? = null
    private var pausedTimer: Job? = null

    // Floating bubble
    private var bubbleView: View? = null
    private var bubbleOnRight = true
    private val bubbleParams = OverlayHost.params(gravity = Gravity.TOP or Gravity.END)

    // Auto-scroll controls
    private var pillView: View? = null
    private var pillStatus by mutableStateOf(AutoScrollStatus.OFF)
    private var pillSpeed by mutableIntStateOf(4)

    // Mindful nudge
    private var nudgeView: View? = null
    private var nudge by mutableStateOf<Nudge?>(null)
    private var nudgeTimer: Job? = null

    // Macro recorder
    private var captureView: TouchCaptureView? = null
    private var captureParams: WindowManager.LayoutParams? = null
    private var barView: View? = null
    private var recordedSteps by mutableIntStateOf(0)

    fun start() {
        c.mindful // make sure mindful mode is counting
        scope.launch { c.settings.settings.collect { settings = it } }

        scope.launch {
            c.engine.events.collect { e ->
                if (e.executed && settings.edgeGlow && captureView == null) pulseGlow()
            }
        }

        scope.launch {
            combine(c.camera.status, c.settings.settings) { cam, s ->
                (cam == SourceStatus.RUNNING && s.privacyBadge) to s.trackingPreview
            }.distinctUntilChanged().collect { (show, preview) -> setBadge(show || badgePaused, preview && show) }
        }

        scope.launch {
            combine(c.settings.settings, c.engine.state, c.macroRecording.recording) { s, st, rec ->
                s.bubble && !rec && st.status == EngineStatus.ACTIVE &&
                    st.foregroundPackage != null && st.foregroundPackage != service.packageName
            }.distinctUntilChanged().collect(::setBubble)
        }

        scope.launch {
            combine(c.autoScroller.status, c.settings.settings) { status, s -> status to s.autoScroll.speed }
                .collect { (status, speed) ->
                    pillStatus = status
                    pillSpeed = speed
                    setPill(status != AutoScrollStatus.OFF)
                }
        }

        scope.launch { c.mindful.nudges.collect(::showNudge) }

        scope.launch { c.macroRecording.recording.collect(::setRecorder) }
        scope.launch { c.macroRecording.steps.collect { recordedSteps = it } }
    }

    fun stop() {
        scope.cancel()
        c.visionPreview.release(this)
        if (c.macroRecording.recording.value) c.macroRecording.cancel()
        host.destroy()
    }

    // ---- Edge glow -------------------------------------------------------------------------

    private fun pulseGlow() {
        if (glowView == null) {
            glowView = host.addCompose(
                OverlayHost.params(
                    width = WindowManager.LayoutParams.MATCH_PARENT,
                    height = WindowManager.LayoutParams.MATCH_PARENT,
                    touchable = false,
                ),
            ) { EdgeGlow(glowPulse) }
        }
        glowPulse++
        glowRemoval?.cancel()
        glowRemoval = scope.launch {
            delay(GLOW_KEEP_MS)
            host.remove(glowView)
            glowView = null
        }
    }

    // ---- Privacy badge ---------------------------------------------------------------------

    private fun setBadge(show: Boolean, preview: Boolean) {
        if (preview) {
            c.visionPreview.acquire(this)
            if (badgePreviewJob == null) {
                badgePreviewJob = scope.launch { c.visionPreview.points.collect { badgePoints = it } }
            }
        } else {
            c.visionPreview.release(this)
            badgePreviewJob?.cancel()
            badgePreviewJob = null
            badgePoints = null
        }
        if (show && badgeView == null) {
            badgeView = host.addCompose(
                OverlayHost.params(gravity = Gravity.TOP or Gravity.END, x = dp(12), y = dp(44)),
            ) {
                PrivacyBadge(
                    points = badgePoints,
                    paused = badgePaused,
                    onPause = ::killSwitch,
                    onUndo = ::undoKillSwitch,
                )
            }
        } else if (!show && badgeView != null) {
            host.remove(badgeView)
            badgeView = null
        }
    }

    /** One tap on the badge pauses everything; a short "Undo" follows. */
    private fun killSwitch() {
        c.autoScroller.stop()
        c.engine.setMasterEnabled(false)
        badgePaused = true
        setBadge(show = true, preview = false)
        pausedTimer?.cancel()
        pausedTimer = scope.launch {
            delay(UNDO_MS)
            badgePaused = false
            setBadge(show = c.camera.status.value == SourceStatus.RUNNING && settings.privacyBadge, preview = false)
        }
    }

    private fun undoKillSwitch() {
        pausedTimer?.cancel()
        badgePaused = false
        c.engine.setMasterEnabled(true)
        setBadge(show = false, preview = false)
    }

    // ---- Floating bubble -------------------------------------------------------------------

    private fun setBubble(show: Boolean) {
        if (show && bubbleView == null) {
            val screenH = service.resources.displayMetrics.heightPixels
            bubbleParams.y = (screenH * 0.42f).toInt()
            bubbleParams.x = 0
            bubbleView = host.addCompose(bubbleParams) {
                FloatingBubble(
                    onDrag = ::dragBubble,
                    onDragEnd = ::snapBubble,
                    onAction = ::bubbleAction,
                    onExpandedChange = { },
                )
            }
        } else if (!show && bubbleView != null) {
            host.remove(bubbleView)
            bubbleView = null
        }
    }

    private fun dragBubble(dx: Float, dy: Float) {
        val view = bubbleView ?: return
        bubbleParams.x += if (bubbleOnRight) -dx.toInt() else dx.toInt()
        bubbleParams.y += dy.toInt()
        host.update(view, bubbleParams)
    }

    /** Snaps to whichever side is closer; the controller grows away from that edge. */
    private fun snapBubble() {
        val view = bubbleView ?: return
        val screenW = service.resources.displayMetrics.widthPixels
        val left = if (bubbleOnRight) screenW - bubbleParams.x - view.width else bubbleParams.x
        bubbleOnRight = left + view.width / 2 > screenW / 2
        bubbleParams.gravity = Gravity.TOP or if (bubbleOnRight) Gravity.END else Gravity.START
        bubbleParams.x = 0
        bubbleParams.y = bubbleParams.y.coerceIn(dp(48), service.resources.displayMetrics.heightPixels - dp(120))
        host.update(view, bubbleParams)
    }

    private fun bubbleAction(action: BubbleAction) {
        scope.launch {
            when (action) {
                BubbleAction.SCROLL_UP -> c.engine.runAction(Actions.ScrollUp)
                BubbleAction.SCROLL_DOWN -> c.engine.runAction(Actions.ScrollDown)
                BubbleAction.BACK -> c.engine.runAction(Actions.Back)
                BubbleAction.HOME -> c.engine.runAction(Actions.Home)
                BubbleAction.AUTO_SCROLL -> c.autoScroller.toggle()
                BubbleAction.PAUSE -> c.engine.setMasterEnabled(false)
            }
        }
    }

    // ---- Auto-scroll controls --------------------------------------------------------------

    private fun setPill(show: Boolean) {
        if (show && pillView == null) {
            pillView = host.addCompose(
                OverlayHost.params(gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, y = dp(96)),
            ) {
                AutoScrollPill(
                    status = pillStatus,
                    speed = pillSpeed,
                    onPauseResume = c.autoScroller::pauseOrResume,
                    onSlower = { c.autoScroller.changeSpeed(-1) },
                    onFaster = { c.autoScroller.changeSpeed(1) },
                    onStop = c.autoScroller::stop,
                )
            }
        } else if (!show && pillView != null) {
            host.remove(pillView)
            pillView = null
        }
    }

    // ---- Mindful nudge ---------------------------------------------------------------------

    private fun showNudge(n: Nudge) {
        nudge = n
        if (nudgeView == null) {
            nudgeView = host.addCompose(
                OverlayHost.params(
                    width = WindowManager.LayoutParams.MATCH_PARENT,
                    gravity = Gravity.BOTTOM,
                    y = dp(120),
                ),
            ) {
                nudge?.let { current ->
                    NudgeCard(
                        nudge = current,
                        onKeepGoing = ::hideNudge,
                        onPause = {
                            c.engine.setMasterEnabled(false)
                            hideNudge()
                        },
                        onResume = {
                            c.engine.setMasterEnabled(true)
                            hideNudge()
                        },
                    )
                }
            }
        }
        nudgeTimer?.cancel()
        nudgeTimer = scope.launch {
            delay(NUDGE_MS)
            hideNudge()
        }
    }

    private fun hideNudge() {
        nudgeTimer?.cancel()
        host.remove(nudgeView)
        nudgeView = null
        nudge = null
    }

    // ---- Macro recorder --------------------------------------------------------------------

    private fun setRecorder(recording: Boolean) {
        if (recording && captureView == null) {
            val rec = c.macroRecording
            val params = OverlayHost.params(
                width = WindowManager.LayoutParams.MATCH_PARENT,
                height = WindowManager.LayoutParams.MATCH_PARENT,
            )
            val view = TouchCaptureView(
                service,
                onDown = { x, y, t -> rec.recorder?.down(x, y, t) },
                onMove = { x, y, t -> rec.recorder?.move(x, y, t) },
                onUp = { x, y, t ->
                    val step = rec.recorder?.up(x, y, t)
                    rec.onStepRecorded()
                    if (step != null) replay(Macro("replay", "", listOf(step.copy(delayMs = 0))))
                },
                onCancel = { rec.recorder?.cancelStroke() },
            )
            captureView = view
            captureParams = params
            host.add(view, params)
            barView = host.addCompose(
                OverlayHost.params(gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL, y = dp(40)),
            ) {
                MacroRecorderBar(steps = recordedSteps, onDone = rec::finish, onCancel = rec::cancel)
            }
        } else if (!recording && captureView != null) {
            host.remove(barView)
            host.remove(captureView)
            barView = null
            captureView = null
            captureParams = null
        }
    }

    /** Lets a just-recorded stroke reach the app: stop catching touches, replay it, catch again. */
    private fun replay(step: Macro) {
        val view = captureView ?: return
        val params = captureParams ?: return
        view.replaying = true
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        host.update(view, params)
        scope.launch {
            c.engine.executor?.playMacro(step)
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            host.update(view, params)
            view.replaying = false
        }
    }

    private companion object {
        const val GLOW_KEEP_MS = 900L
        const val UNDO_MS = 4_000L
        const val NUDGE_MS = 15_000L
    }
}
