package io.github.arnavdugad.flick.action

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import io.github.arnavdugad.flick.core.model.GlobalAction
import io.github.arnavdugad.flick.core.model.MediaCommand

fun GlobalAction.isSupported(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= minSdk

/** Maps to the platform constant. Callers must check [isSupported] first. */
@SuppressLint("InlinedApi")
fun GlobalAction.platformId(): Int = when (this) {
    GlobalAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
    GlobalAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
    GlobalAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
    GlobalAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
    GlobalAction.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
    GlobalAction.POWER_DIALOG -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
    GlobalAction.SPLIT_SCREEN -> AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
    GlobalAction.LOCK_SCREEN -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
    GlobalAction.SCREENSHOT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
    GlobalAction.DISMISS_NOTIFICATION_SHADE -> AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
    GlobalAction.ALL_APPS -> AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS
}

/** Media keys and volume through AudioManager; needs no permission and works on every API level. */
class MediaActions(private val audio: AudioManager?) {

    fun perform(command: MediaCommand): Boolean {
        val am = audio ?: return false
        return runCatching {
            when (command) {
                MediaCommand.PLAY_PAUSE -> sendKey(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                MediaCommand.NEXT -> sendKey(am, KeyEvent.KEYCODE_MEDIA_NEXT)
                MediaCommand.PREVIOUS -> sendKey(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                MediaCommand.VOLUME_UP -> adjust(am, AudioManager.ADJUST_RAISE)
                MediaCommand.VOLUME_DOWN -> adjust(am, AudioManager.ADJUST_LOWER)
                MediaCommand.MUTE_TOGGLE -> adjust(am, AudioManager.ADJUST_TOGGLE_MUTE)
            }
            true
        }.getOrDefault(false)
    }

    private fun sendKey(am: AudioManager, code: Int) {
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun adjust(am: AudioManager, direction: Int) {
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }
}
