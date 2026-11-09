package io.github.arnavdugad.flick.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import io.github.arnavdugad.flick.action.ActionExecutor
import io.github.arnavdugad.flick.container

/**
 * Thin Android entry point. All logic lives in FlickEngine; this class only connects the engine to
 * the system (gesture dispatch, global actions, window events) while the user has it enabled.
 */
class FlickAccessibilityService : AccessibilityService() {

    private var executor: ActionExecutor? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val ex = ActionExecutor(this)
        executor = ex
        container.engine.attach(ex)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let(container.engine::onWindowStateChanged)
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
        executor?.let(container.engine::detach)
        executor = null
    }
}
