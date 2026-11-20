package com.example.mediminder.updater

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** How the updater reads GitHub's answer and decides whether to recommend an update. */
@RunWith(AndroidJUnit4::class)
class UpdaterTest {
    private val release = """
        {
          "tag_name": "v1.0.3",
          "body": "Report page fixed\n* Updates are recommended automatically\n\n**Full Changelog**: https://github.com/x/y/compare/v1.0.2...v1.0.3",
          "assets": [
            {"name": "app-release.apk", "size": 44900000, "browser_download_url": "https://github.com/x/y/releases/download/v1.0.3/app-release.apk"},
            {"name": "update.json", "size": 120, "browser_download_url": "https://github.com/x/y/releases/download/v1.0.3/update.json"}
          ]
        }
    """.trimIndent()

    @Test fun `reads the APK, the update description and plain notes from a release`() {
        val r = parseRelease(release)
        assertEquals("v1.0.3", r.tag)
        assertTrue(r.apkUrl!!.endsWith("app-release.apk"))
        assertTrue(r.metaUrl!!.endsWith("update.json"))
        val info = r.toInfo(parseMeta("""{"versionCode":26,"versionName":"1.0.3","sha256":"ABC","size":44900001}"""))!!
        assertEquals(26, info.versionCode)
        assertEquals("1.0.3", info.versionName)
        assertEquals(44900001L, info.apkSize)
        assertEquals("ABC", info.sha256)
        // GitHub's automatic changelog link is dropped; bullets become plain lines
        assertEquals(listOf("Report page fixed", "Updates are recommended automatically"), info.notes)
    }

    @Test fun `a higher build number is an update, whatever the version name says`() {
        val info = parseRelease(release).toInfo(parseMeta("""{"versionCode":26,"versionName":"1.0.3"}"""))!!
        // The test phone ran "2.1.0" (build 21) before the renumbering to 1.0.x
        assertTrue(isUpdate(info, currentCode = 21, currentName = "2.1.0"))
        assertTrue(isUpdate(info, currentCode = 25, currentName = "1.0.2"))
        assertFalse(isUpdate(info, currentCode = 26, currentName = "1.0.3"))
        assertFalse(isUpdate(info, currentCode = 30, currentName = "1.0.7"))
    }

    @Test fun `older releases without update json fall back to comparing names`() {
        val noMeta = parseRelease(release.replace(""""name": "update.json"""", """"name": "other.txt""""))
        assertNull(noMeta.metaUrl)
        val info = noMeta.toInfo(null)!!
        assertNull(info.versionCode)
        assertTrue(isUpdate(info, currentCode = 25, currentName = "1.0.2"))
        assertFalse(isUpdate(info, currentCode = 26, currentName = "1.0.3"))
    }

    @Test fun `update json alone is enough - no GitHub API needed`() {
        // What the release workflow publishes since 1.0.4: the APK link and the notes travel inside it
        val full = parseMeta(
            """{"versionCode":27,"versionName":"1.0.4","sha256":"ab","size":10,"apk":"https://example.org/app.apk","notes":["Updates arrive reliably","","New pictures"]}"""
        ).toInfo("https://github.com/o/r")
        assertEquals(27, full.versionCode)
        assertEquals("https://example.org/app.apk", full.apkUrl)
        assertEquals(listOf("Updates arrive reliably", "New pictures"), full.notes)
        // 1.0.3's update.json had neither: the APK is found under the release's tag
        val plain = parseMeta("""{"versionCode":26,"versionName":"1.0.3","size":5}""").toInfo("https://github.com/o/r")
        assertEquals("https://github.com/o/r/releases/download/v1.0.3/app-release.apk", plain.apkUrl)
        assertTrue(plain.notes.isEmpty())
        assertTrue(isUpdate(plain, currentCode = 25, currentName = "1.0.2"))
    }

    @Test fun `a release without an APK offers nothing`() {
        assertNull(parseRelease("""{"tag_name":"v9.9.9","assets":[]}""").toInfo(null))
    }

    @Test fun `only a higher version name counts as newer`() {
        assertTrue(isNewer("v2.0.1", "v2.0.0"))
        assertTrue(isNewer("v2.0.10", "v2.0.9"))
        assertTrue(isNewer("v3.0", "v2.9.9"))
        assertFalse(isNewer("v1.0.10", "v2.0.0"))
        assertFalse(isNewer("v2.0.0", "v2.0.0"))
    }
}
