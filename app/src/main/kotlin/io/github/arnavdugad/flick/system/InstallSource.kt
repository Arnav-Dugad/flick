package io.github.arnavdugad.flick.system

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build

object InstallSource {

    /**
     * Best guess at whether Android 13+ will block the accessibility toggle until the user taps
     * "Allow restricted settings". Installs from a downloaded or local file are restricted; store
     * installs and adb installs are not. When unsure we say yes, because showing the help is cheap.
     */
    fun likelyRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val info = runCatching { context.packageManager.getInstallSourceInfo(context.packageName) }
            .getOrNull() ?: return true
        return when (info.packageSource) {
            PackageInstaller.PACKAGE_SOURCE_STORE -> false
            PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED -> info.installingPackageName != null &&
                info.installingPackageName != "com.android.shell"
            else -> true
        }
    }
}
