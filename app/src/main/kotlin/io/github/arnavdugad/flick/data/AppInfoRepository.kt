package io.github.arnavdugad.flick.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val packageName: String, val label: String)

/**
 * Installed, launchable apps for the profile editor. Visibility comes from the MAIN/LAUNCHER
 * `<queries>` entry in the manifest — Flick never asks for QUERY_ALL_PACKAGES.
 */
class AppInfoRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val icons = LruCache<String, ImageBitmap>(64)
    private val labels = LruCache<String, String>(256)

    suspend fun launchableApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { AppEntry(it, labelBlocking(it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun isInstalled(packageName: String): Boolean =
        runCatching { pm.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)

    suspend fun label(packageName: String): String = withContext(Dispatchers.IO) { labelBlocking(packageName) }

    private fun labelBlocking(packageName: String): String {
        labels.get(packageName)?.let { return it }
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        labels.put(packageName, label)
        return label
    }

    suspend fun icon(packageName: String, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "$packageName@$sizePx"
        icons.get(key)?.let { return@withContext it }
        runCatching {
            pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
        }.getOrNull()?.also { icons.put(key, it) }
    }
}
