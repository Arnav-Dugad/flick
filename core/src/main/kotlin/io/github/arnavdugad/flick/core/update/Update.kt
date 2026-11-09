package io.github.arnavdugad.flick.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A semantic version (MAJOR.MINOR.PATCH). Tags like "v1.2.3" are accepted. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    /** The Android versionCode Flick's release pipeline derives from a tag. */
    val versionCode: Int get() = major * 10_000 + minor * 100 + patch

    override fun compareTo(other: AppVersion): Int = versionCode.compareTo(other.versionCode)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d{1,3})\.(\d{1,2})\.(\d{1,2})(?:[-+].*)?$""")

        fun parse(text: String): AppVersion? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            val (a, b, c) = m.destructured
            return AppVersion(a.toInt(), b.toInt(), c.toInt())
        }
    }
}

/** Subset of GitHub's "get the latest release" response. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
) {
    fun asset(name: String): GitHubAsset? = assets.firstOrNull { it.name == name }
}

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

/** `version.json`, published by the release workflow next to the APK. */
@Serializable
data class VersionManifest(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val sha256: String,
    val minSdk: Int = 26,
)

/** Everything needed to show and install an update. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
    val releaseUrl: String?,
)

sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck
    data object UpToDate : UpdateCheck

    /** A newer version exists but the user chose to skip it. */
    data class Skipped(val versionCode: Int) : UpdateCheck

    /** Newer, but needs a newer Android than this phone has. */
    data class Incompatible(val minSdk: Int) : UpdateCheck

    /** The release is malformed (missing APK, manifest or checksum). */
    data class Invalid(val reason: String) : UpdateCheck
}

object UpdateLogic {

    const val MANIFEST_ASSET = "version.json"

    val json = Json { ignoreUnknownKeys = true }

    fun parseRelease(body: String): GitHubRelease = json.decodeFromString(GitHubRelease.serializer(), body)

    fun parseManifest(body: String): VersionManifest = json.decodeFromString(VersionManifest.serializer(), body)

    fun isValidSha256(hex: String): Boolean = hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }

    /**
     * Decides whether [release] (with its parsed [manifest]) is an update for the installed
     * [currentVersionCode]. The manifest's versionCode wins; the tag is only a cross-check.
     */
    fun decide(
        release: GitHubRelease,
        manifest: VersionManifest,
        currentVersionCode: Int,
        deviceSdk: Int,
        skippedVersionCode: Int?,
    ): UpdateCheck {
        if (release.draft || release.prerelease) return UpdateCheck.UpToDate
        val tag = AppVersion.parse(release.tagName)
            ?: return UpdateCheck.Invalid("Tag ${release.tagName} is not a version")
        if (tag.versionCode != manifest.versionCode) {
            return UpdateCheck.Invalid("Tag ${release.tagName} doesn't match versionCode ${manifest.versionCode}")
        }
        if (manifest.versionCode <= currentVersionCode) return UpdateCheck.UpToDate
        if (manifest.minSdk > deviceSdk) return UpdateCheck.Incompatible(manifest.minSdk)
        val sha = manifest.sha256.lowercase()
        if (!isValidSha256(sha)) return UpdateCheck.Invalid("Bad checksum in $MANIFEST_ASSET")
        val apk = release.asset(manifest.apk) ?: return UpdateCheck.Invalid("APK ${manifest.apk} missing")
        if (skippedVersionCode == manifest.versionCode) return UpdateCheck.Skipped(manifest.versionCode)
        return UpdateCheck.Available(
            UpdateInfo(
                versionCode = manifest.versionCode,
                versionName = manifest.versionName,
                notes = release.body.orEmpty().trim(),
                apkUrl = apk.downloadUrl,
                apkSize = apk.size,
                sha256 = sha,
                releaseUrl = release.htmlUrl,
            ),
        )
    }
}
