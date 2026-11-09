package io.github.arnavdugad.flick.system

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build

object InstallSource {

    private const val SHELL = "com.android.shell"

    /**
     * Best guess at whether Android 13+ will block the accessibility toggle until the user taps
     * "Allow restricted settings". Installs from a downloaded or local file are restricted; store
     * installs and adb installs are not. When unsure we say yes, because showing the help is cheap.
     */
    fun likelyRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val info = runCatching { context.packageManager.getInstallSourceInfo(context.packageName) }
            .getOrNull() ?: return true
        if (info.initiatingPackageName == SHELL) return false // adb / Android Studio
        return when (info.packageSource) {
            PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE, PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE -> true
            PackageInstaller.PACKAGE_SOURCE_STORE, PackageInstaller.PACKAGE_SOURCE_OTHER -> false
            // Unspecified (common on Android 13): restricted unless installed by the shell.
            else -> info.installingPackageName != null && info.installingPackageName != SHELL
        }
    }
}
