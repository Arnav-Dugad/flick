package io.github.arnavdugad.flick.system

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import io.github.arnavdugad.flick.service.FlickAccessibilityService

object AccessibilityStatus {

    fun component(context: Context) = ComponentName(context, FlickAccessibilityService::class.java)

    /**
     * Whether the user has switched Flick on in Accessibility settings. This can be true while the
     * service is not actually connected (e.g. killed by an OEM battery manager); compare with
     * EngineState.serviceConnected to detect that.
     */
    fun isEnabledInSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val mine = component(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }
}
