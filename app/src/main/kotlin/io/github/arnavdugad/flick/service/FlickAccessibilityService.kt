package io.github.arnavdugad.flick.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.arnavdugad.flick.action.ActionExecutor
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.engine.ServiceBridge

/**
 * Thin Android entry point. All logic lives in FlickEngine; this class only connects the engine to
 * the system (gesture dispatch, global actions, window events, volume keys) while the user has it
 * enabled.
 */
class FlickAccessibilityService : AccessibilityService(), ServiceBridge {

    private val actionExecutor by lazy { ActionExecutor(this) }

    override val executor: ActionExecutor get() = actionExecutor

    override fun onServiceConnected() {
        super.onServiceConnected()
        container.engine.attach(this)
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
        container.engine.detach(this)
    }
}
