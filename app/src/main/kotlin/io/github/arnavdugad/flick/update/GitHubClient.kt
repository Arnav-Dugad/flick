package io.github.arnavdugad.flick.update

import io.github.arnavdugad.flick.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Why talking to GitHub failed. */
sealed class NetworkFailure : Exception() {
    data object Offline : NetworkFailure()
    data class RateLimited(val resetEpochSeconds: Long?) : NetworkFailure()
    data object NoReleases : NetworkFailure()
    data class Http(val code: Int) : NetworkFailure()
}

/** The latest release JSON, or "unchanged" when GitHub answered 304 to our ETag. */
sealed interface LatestRelease {
    data class Fresh(val body: String, val etag: String?) : LatestRelease
    data object NotModified : LatestRelease
}

/**
 * Flick's only network code: the GitHub Releases API and downloads of release assets. No
 * account, no token, no tracking headers, just a generic User-Agent as GitHub requires.
 */
class GitHubClient(private val repo: String = BuildConfig.GITHUB_REPO) {

    private val userAgent = "Flick-Android/${BuildConfig.VERSION_NAME}"

    suspend fun latestRelease(etag: String?): LatestRelease = withContext(Dispatchers.IO) {
        val conn = open("https://api.github.com/repos/$repo/releases/latest").apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (etag != null) setRequestProperty("If-None-Match", etag)
        }
        try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    LatestRelease.Fresh(conn.inputStream.bufferedReader().use { it.readText() }, conn.getHeaderField("ETag"))
                HttpURLConnection.HTTP_NOT_MODIFIED -> LatestRelease.NotModified
                HttpURLConnection.HTTP_NOT_FOUND -> throw NetworkFailure.NoReleases
                HttpURLConnection.HTTP_FORBIDDEN, 429 -> {
                    val remaining = conn.getHeaderField("x-ratelimit-remaining")
                    if (code == 429 || remaining == "0") {
                        throw NetworkFailure.RateLimited(conn.getHeaderField("x-ratelimit-reset")?.toLongOrNull())
                    }
                    throw NetworkFailure.Http(code)
                }
                else -> throw NetworkFailure.Http(code)
            }
        } catch (e: IOException) {
            throw NetworkFailure.Offline
        } finally {
            conn.disconnect()
        }
    }

    /** Small text asset (e.g. version.json). */
    suspend fun downloadText(url: String, maxBytes: Int = 64 * 1024): String = withContext(Dispatchers.IO) {
        val conn = open(url)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw NetworkFailure.Http(conn.responseCode)
            val bytes = conn.inputStream.use { it.readNBytesCompat(maxBytes) }
            String(bytes, Charsets.UTF_8)
        } catch (e: IOException) {
            throw NetworkFailure.Offline
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Streams [url] into [target] while hashing it. Returns the lowercase hex SHA-256.
     * [onProgress] receives 0..1 (or -1 when the size is unknown).
     */
    suspend fun downloadFile(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Float) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val conn = open(url).apply { readTimeout = 60_000 }
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw NetworkFailure.Http(conn.responseCode)
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            target.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        if (read - lastReport > 256 * 1024) {
                            lastReport = read
                            onProgress(if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else -1f)
                        }
                    }
                }
            }
            onProgress(1f)
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: IOException) {
            target.delete()
            throw NetworkFailure.Offline
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Only HTTPS is allowed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
