package io.github.arnavdugad.flick.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Dispatches gestures one at a time. A new dispatch can cancel one already in flight, so every
 * gesture waits for the previous one to complete (or time out) before starting.
 */
class GestureQueue(private val service: AccessibilityService) {

    private val mutex = Mutex()

    /** @return true if the system reported the gesture as completed. */
    suspend fun dispatch(gesture: GestureDescription): Boolean = mutex.withLock {
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val callback = object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                    val accepted = runCatching { service.dispatchGesture(gesture, callback, null) }
                        .getOrDefault(false)
                    if (!accepted && cont.isActive) cont.resume(false)
                }
            } ?: false
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
