package io.github.arnavdugad.flick.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/** Deep links into system Settings, each with a safe fallback. */
object SystemIntents {

    private const val ACTION_ACCESSIBILITY_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    fun openAccessibilitySettings(context: Context) {
        val component = AccessibilityStatus.component(context).flattenToString()
        // Not public API, but honoured by Settings on Android 11+; jumps straight to Flick's page.
        // Falls back to the accessibility list when the device doesn't support it.
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(ACTION_ACCESSIBILITY_DETAILS).putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        } else {
            null
        }
        launch(context, detail, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openAppDetails(context: Context) {
        launch(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    fun openUrl(context: Context, url: String) {
        launch(context, Intent(Intent.ACTION_VIEW, url.toUri()), null)
    }

    private fun launch(context: Context, primary: Intent?, fallback: Intent?) {
        for (intent in listOfNotNull(primary, fallback)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // try the next one
            } catch (_: SecurityException) {
                // try the next one
            }
        }
    }
}
