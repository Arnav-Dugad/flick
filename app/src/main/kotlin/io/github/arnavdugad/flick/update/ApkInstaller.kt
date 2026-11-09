package io.github.arnavdugad.flick.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.IntentCompat
import io.github.arnavdugad.flick.container
import java.io.File
import java.security.MessageDigest

/** Checks that a downloaded APK really is a newer Flick signed with the same key. */
object ApkVerifier {

    sealed interface Result {
        data object Ok : Result
        data object NotAnApk : Result
        data object WrongPackage : Result
        data object NotNewer : Result
        data object SignatureMismatch : Result
    }

    fun verify(context: Context, apk: File): Result {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: return Result.NotAnApk
        if (archive.packageName != context.packageName) return Result.WrongPackage
        val installed = installedInfo(pm, context.packageName) ?: return Result.NotAnApk
        if (versionCode(archive) <= versionCode(installed)) return Result.NotNewer
        val newSigners = signers(archive)
        val oldSigners = signers(installed)
        // Key rotation (APK Signature Scheme v3) keeps the old certificate in the new lineage.
        if (newSigners.isEmpty() || newSigners.intersect(oldSigners).isEmpty()) return Result.SignatureMismatch
        return Result.Ok
    }

    /** SHA-256 fingerprint(s) of Flick's own signing certificate, for the About screen. */
    fun ownFingerprints(context: Context): List<String> =
        installedInfo(context.packageManager, context.packageName)?.let(::signers)?.toList().orEmpty()

    private fun flags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(flags().toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.path, flags())
        }
    }.getOrNull()

    private fun installedInfo(pm: PackageManager, pkg: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags().toLong()))
        } else {
            pm.getPackageInfo(pkg, flags())
        }
    }.getOrNull()

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

    private fun signers(info: PackageInfo): Set<String> {
        val sigs: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val s = info.signingInfo ?: return emptySet()
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return sigs.mapTo(HashSet()) { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString(":") { "%02X".format(it) }
        }
    }
}

/**
 * Installs an APK through a PackageInstaller session. On Android 12+ Flick asks to update itself
 * without a prompt; Android may still ask the user to confirm, which [InstallResultReceiver]
 * handles by showing the system dialog.
 */
object ApkInstaller {

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                // Must be mutable: the system adds the status extras (required on targetSdk 35+).
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, id, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (t: Throwable) {
            runCatching { installer.abandonSession(id) }
            throw t
        }
    }
}

/** Receives PackageInstaller results. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val updates = context.container.updates
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { updates.onInstallFailed(status, it.message) }
                } else {
                    updates.onInstallFailed(status, "No confirmation screen")
                }
            }
            // On success Android replaces and restarts Flick; nothing else to do.
            PackageInstaller.STATUS_SUCCESS -> updates.onInstalled()
            else -> updates.onInstallFailed(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
