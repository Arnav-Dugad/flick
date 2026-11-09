package io.github.arnavdugad.flick.update

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.arnavdugad.flick.BuildConfig
import io.github.arnavdugad.flick.core.update.UpdateCheck
import io.github.arnavdugad.flick.core.update.UpdateInfo
import io.github.arnavdugad.flick.core.update.UpdateLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UpdatePrefs(
    val autoCheck: Boolean = true,
    val wifiOnly: Boolean = false,
    val skippedVersionCode: Int? = null,
    val lastCheckMs: Long = 0,
    val notifiedVersionCode: Int? = null,
)

sealed interface UpdateError {
    data object Offline : UpdateError
    data class RateLimited(val resetEpochSeconds: Long?) : UpdateError
    data object NoReleases : UpdateError
    data class InvalidRelease(val reason: String) : UpdateError
    data class Incompatible(val minSdk: Int) : UpdateError
    data object ChecksumMismatch : UpdateError
    data object SignatureMismatch : UpdateError
    data object Storage : UpdateError
    data object Cancelled : UpdateError
    data class InstallFailed(val message: String?) : UpdateError
    data class Unknown(val message: String?) : UpdateError
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class NeedsInstallPermission(val info: UpdateInfo) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val error: UpdateError, val info: UpdateInfo? = null) : UpdateState
}

private val Context.updateStore: DataStore<Preferences> by preferencesDataStore(name = "updates")

/**
 * Checks GitHub for a newer release, downloads it, verifies its SHA-256 and signing certificate,
 * and hands it to Android's package installer. All failures end in [UpdateState.Failed] with a
 * reason the UI can explain; nothing here ever crashes the app.
 */
class UpdateRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: GitHubClient = GitHubClient(),
) {
    private val store = context.updateStore

    private object Keys {
        val autoCheck = booleanPreferencesKey("auto_check")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val skipped = intPreferencesKey("skipped_version")
        val lastCheck = longPreferencesKey("last_check")
        val notified = intPreferencesKey("notified_version")
        val etag = stringPreferencesKey("etag")
        val cachedRelease = stringPreferencesKey("cached_release")
    }

    val prefs: Flow<UpdatePrefs> = store.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            UpdatePrefs(
                autoCheck = p[Keys.autoCheck] ?: true,
                wifiOnly = p[Keys.wifiOnly] ?: false,
                skippedVersionCode = p[Keys.skipped],
                lastCheckMs = p[Keys.lastCheck] ?: 0,
                notifiedVersionCode = p[Keys.notified],
            )
        }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    suspend fun setAutoCheck(value: Boolean) = store.edit { it[Keys.autoCheck] = value }
    suspend fun setWifiOnly(value: Boolean) = store.edit { it[Keys.wifiOnly] = value }
    suspend fun clearSkipped() = store.edit { it.remove(Keys.skipped) }
    suspend fun markNotified(versionCode: Int) = store.edit { it[Keys.notified] = versionCode }

    fun skip(info: UpdateInfo) {
        scope.launch { store.edit { it[Keys.skipped] = info.versionCode } }
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.Installing) {
            _state.value = UpdateState.Idle
        }
    }

    /**
     * Checks for an update. Unless [force], does nothing if checked within [THROTTLE_MS]
     * (respecting GitHub's 60 requests/hour limit for apps without accounts).
     */
    suspend fun check(force: Boolean): UpdateState {
        val p = prefs.first()
        if (!force && System.currentTimeMillis() - p.lastCheckMs < THROTTLE_MS) return _state.value
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return _state.value
        _state.value = UpdateState.Checking
        val result = runCatching { fetch(p) }.getOrElse { e -> UpdateState.Failed(errorFor(e)) }
        store.edit { it[Keys.lastCheck] = System.currentTimeMillis() }
        _state.value = result
        return result
    }

    private suspend fun fetch(p: UpdatePrefs): UpdateState {
        val stored = store.data.first()
        val releaseBody = when (val r = client.latestRelease(stored[Keys.etag])) {
            is LatestRelease.Fresh -> {
                store.edit {
                    it[Keys.cachedRelease] = r.body
                    if (r.etag != null) it[Keys.etag] = r.etag else it.remove(Keys.etag)
                }
                r.body
            }
            LatestRelease.NotModified -> stored[Keys.cachedRelease] ?: run {
                store.edit { it.remove(Keys.etag) }
                return fetch(p)
            }
        }
        val release = UpdateLogic.parseRelease(releaseBody)
        val manifestAsset = release.asset(UpdateLogic.MANIFEST_ASSET)
            ?: return UpdateState.Failed(UpdateError.InvalidRelease("${UpdateLogic.MANIFEST_ASSET} missing"))
        val manifest = UpdateLogic.parseManifest(client.downloadText(manifestAsset.downloadUrl))
        return when (val d = UpdateLogic.decide(release, manifest, currentVersionCode, Build.VERSION.SDK_INT, p.skippedVersionCode)) {
            is UpdateCheck.Available -> UpdateState.Available(d.info)
            is UpdateCheck.Skipped, UpdateCheck.UpToDate -> UpdateState.UpToDate
            is UpdateCheck.Incompatible -> UpdateState.Failed(UpdateError.Incompatible(d.minSdk))
            is UpdateCheck.Invalid -> UpdateState.Failed(UpdateError.InvalidRelease(d.reason))
        }
    }

    /** Downloads, verifies and installs [info]. */
    fun downloadAndInstall(info: UpdateInfo) {
        if (job?.isActive == true) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(info)
            return
        }
        job = scope.launch {
            val apk = File(updatesDir(), "flick-${info.versionName}.apk")
            try {
                _state.value = UpdateState.Downloading(info, 0f)
                val sha = client.downloadFile(info.apkUrl, apk, info.apkSize) { progress ->
                    _state.value = UpdateState.Downloading(info, progress)
                }
                if (!sha.equals(info.sha256, ignoreCase = true)) {
                    apk.delete()
                    _state.value = UpdateState.Failed(UpdateError.ChecksumMismatch, info)
                    return@launch
                }
                val verdict = withContext(Dispatchers.IO) { ApkVerifier.verify(context, apk) }
                if (verdict != ApkVerifier.Result.Ok) {
                    apk.delete()
                    _state.value = UpdateState.Failed(
                        if (verdict == ApkVerifier.Result.SignatureMismatch) {
                            UpdateError.SignatureMismatch
                        } else {
                            UpdateError.InvalidRelease(verdict.toString())
                        },
                        info,
                    )
                    return@launch
                }
                _state.value = UpdateState.Installing(info)
                withContext(Dispatchers.IO) { ApkInstaller.install(context, apk) }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) {
                    apk.delete()
                    _state.value = UpdateState.Failed(UpdateError.Cancelled, info)
                    throw e
                }
                _state.value = UpdateState.Failed(errorFor(e), info)
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
    }

    /** From [InstallResultReceiver]. */
    fun onInstallFailed(status: Int, message: String?) {
        val info = (_state.value as? UpdateState.Installing)?.info
        _state.value = UpdateState.Failed(
            when (status) {
                PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateError.Cancelled
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INVALID -> UpdateError.SignatureMismatch
                PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateError.Storage
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> UpdateError.Incompatible(0)
                else -> UpdateError.InstallFailed(message)
            },
            info,
        )
    }

    fun onInstalled() {
        _state.value = UpdateState.Idle
    }

    /** Removes downloaded APKs (called at startup; an installed update no longer needs them). */
    fun cleanUp() {
        scope.launch(Dispatchers.IO) { updatesDir().listFiles()?.forEach { it.delete() } }
    }

    private fun updatesDir() = File(context.cacheDir, "updates")

    private fun errorFor(e: Throwable): UpdateError = when (e) {
        is NetworkFailure.Offline -> UpdateError.Offline
        is NetworkFailure.RateLimited -> UpdateError.RateLimited(e.resetEpochSeconds)
        is NetworkFailure.NoReleases -> UpdateError.NoReleases
        is NetworkFailure.Http -> UpdateError.Unknown("HTTP ${e.code}")
        is java.io.IOException -> if (e.message?.contains("ENOSPC") == true) UpdateError.Storage else UpdateError.Offline
        is kotlinx.serialization.SerializationException, is IllegalArgumentException -> UpdateError.InvalidRelease(e.message ?: "")
        else -> UpdateError.Unknown(e.message)
    }

    companion object {
        const val THROTTLE_MS = 6 * 60 * 60 * 1000L
    }
}
