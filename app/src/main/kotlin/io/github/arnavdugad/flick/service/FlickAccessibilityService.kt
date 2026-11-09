package io.github.arnavdugad.flick.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.arnavdugad.flick.action.ActionExecutor
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.engine.ServiceBridge
import io.github.arnavdugad.flick.overlay.OverlayController
import io.github.arnavdugad.flick.system.FlickTileService
import io.github.arnavdugad.flick.system.StatusNotifier
import android.content.ComponentName
import android.service.quicksettings.TileService

/**
 * Thin Android entry point. All logic lives in FlickEngine; this class only connects the engine to
 * the system (gesture dispatch, global actions, window events, volume keys) while the user has it
 * enabled.
 */
class FlickAccessibilityService : AccessibilityService(), ServiceBridge {

    private val actionExecutor by lazy { ActionExecutor(this) }
    private var overlays: OverlayController? = null
    private var notifier: StatusNotifier? = null

    override val executor: ActionExecutor get() = actionExecutor

    override fun onServiceConnected() {
        super.onServiceConnected()
        container.engine.attach(this)
        overlays = OverlayController(this).also { it.start() }
        notifier = StatusNotifier(this).also { it.start() }
        TileService.requestListeningState(this, ComponentName(this, FlickTileService::class.java))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let(container.engine::onWindowStateChanged)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean =
        runCatching { container.engine.onKeyEvent(event) }.getOrDefault(false)

    override fun setKeyEventFiltering(enabled: Boolean) {
        val info = serviceInfo ?: return
        val flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        val wanted = if (enabled) info.flags or flag else info.flags and flag.inv()
        if (wanted != info.flags) {
            info.flags = wanted
            runCatching { serviceInfo = info }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        overlays?.stop()
        overlays = null
        notifier?.stop()
        notifier = null
        container.engine.detach(this)
    }
}
