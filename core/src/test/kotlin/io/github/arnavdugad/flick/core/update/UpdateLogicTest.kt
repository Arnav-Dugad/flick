package io.github.arnavdugad.flick.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    @Test
    fun `parses tags with and without v`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(0, 4, 0), AppVersion.parse("0.4.0"))
        assertEquals(AppVersion(2, 0, 1), AppVersion.parse("V2.0.1-beta+7"))
    }

    @Test
    fun `rejects non versions`() {
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.2"))
        assertNull(AppVersion.parse("1.2.3.4"))
        assertNull(AppVersion.parse("1.100.0")) // minor must fit in two digits
    }

    @Test
    fun `version code ordering matches semantic ordering`() {
        val ordered = listOf("0.1.9", "0.2.0", "0.10.0", "1.0.0", "1.0.10", "1.1.0", "10.0.0").map { AppVersion.parse(it)!! }
        assertEquals(ordered, ordered.shuffled().sorted())
        assertEquals(10_203, AppVersion(1, 2, 3).versionCode)
        assertTrue(AppVersion(0, 10, 0) > AppVersion(0, 9, 99))
    }
}

class UpdateDecisionTest {

    private val sha = "a".repeat(64)

    private fun release(tag: String = "v0.5.0", prerelease: Boolean = false, apk: String = "flick-0.5.0.apk") = GitHubRelease(
        tagName = tag,
        body = "## What's new\n- Things",
        htmlUrl = "https://github.com/x/y/releases/tag/$tag",
        prerelease = prerelease,
        assets = listOf(
            GitHubAsset(apk, "https://example.com/$apk", 40_000_000),
            GitHubAsset("version.json", "https://example.com/version.json", 120),
        ),
    )

    private fun manifest(code: Int = 500, sha256: String = sha, minSdk: Int = 26) =
        VersionManifest(code, "0.5.0", "flick-0.5.0.apk", sha256, minSdk)

    @Test
    fun `newer release is available`() {
        val r = UpdateLogic.decide(release(), manifest(), currentVersionCode = 400, deviceSdk = 37, skippedVersionCode = null)
        assertIs<UpdateCheck.Available>(r)
        assertEquals("0.5.0", r.info.versionName)
        assertEquals(sha, r.info.sha256)
        assertEquals("https://example.com/flick-0.5.0.apk", r.info.apkUrl)
    }

    @Test
    fun `same or older is up to date`() {
        assertEquals(UpdateCheck.UpToDate, UpdateLogic.decide(release(), manifest(), 500, 37, null))
        assertEquals(UpdateCheck.UpToDate, UpdateLogic.decide(release(), manifest(), 900, 37, null))
    }

    @Test
    fun `prereleases are ignored`() {
        assertEquals(UpdateCheck.UpToDate, UpdateLogic.decide(release(prerelease = true), manifest(), 1, 37, null))
    }

    @Test
    fun `skipped version is reported as skipped`() {
        assertEquals(UpdateCheck.Skipped(500), UpdateLogic.decide(release(), manifest(), 400, 37, skippedVersionCode = 500))
    }

    @Test
    fun `too new for this android`() {
        assertEquals(UpdateCheck.Incompatible(40), UpdateLogic.decide(release(), manifest(minSdk = 40), 400, 37, null))
    }

    @Test
    fun `tag and manifest must agree`() {
        assertIs<UpdateCheck.Invalid>(UpdateLogic.decide(release(tag = "v0.6.0"), manifest(), 400, 37, null))
    }

    @Test
    fun `missing apk or bad checksum is invalid`() {
        assertIs<UpdateCheck.Invalid>(UpdateLogic.decide(release(apk = "other.apk"), manifest(), 400, 37, null))
        assertIs<UpdateCheck.Invalid>(UpdateLogic.decide(release(), manifest(sha256 = "xyz"), 400, 37, null))
    }

    @Test
    fun `parses github json ignoring unknown fields`() {
        val body = """
            {"url":"x","tag_name":"v0.5.0","name":"Flick 0.5.0","body":"notes","draft":false,"prerelease":false,
             "html_url":"https://github.com/a/b/releases/tag/v0.5.0","author":{"login":"a"},
             "assets":[{"name":"flick-0.5.0.apk","size":123,"browser_download_url":"https://dl/flick.apk","digest":null}]}
        """.trimIndent()
        val r = UpdateLogic.parseRelease(body)
        assertEquals("v0.5.0", r.tagName)
        assertEquals(123, r.asset("flick-0.5.0.apk")?.size)
        val m = UpdateLogic.parseManifest("""{"versionCode":500,"versionName":"0.5.0","apk":"flick-0.5.0.apk","sha256":"$sha","minSdk":26}""")
        assertEquals(500, m.versionCode)
    }
}
