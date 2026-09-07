package llm.slop.liquidlsd.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun testParseReleaseJson() {
        val sampleJson = """
            {
              "tag_name": "v1.0.0-beta.42",
              "name": "v1.0.0-beta.42 - New Audio Filters",
              "html_url": "https://github.com/greenjon/liquid-lsd/releases/tag/v1.0.0-beta.42",
              "body": "### Highlights\n- Fixed audio filter curves\n- Added update checker",
              "published_at": "2026-09-06T20:00:00Z"
            }
        """.trimIndent()

        val release = UpdateChecker.parseReleaseJson(sampleJson)
        assertNotNull(release)
        assertEquals("v1.0.0-beta.42", release.tagName)
        assertEquals("v1.0.0-beta.42 - New Audio Filters", release.name)
        assertEquals("https://github.com/greenjon/liquid-lsd/releases/tag/v1.0.0-beta.42", release.htmlUrl)
        assertTrue(release.body.contains("Fixed audio filter curves"))
        assertEquals("2026-09-06T20:00:00Z", release.publishedAt)
    }

    @Test
    fun testParseInvalidJsonReturnsNull() {
        assertNull(UpdateChecker.parseReleaseJson("not valid json"))
        assertNull(UpdateChecker.parseReleaseJson("{}")) // missing tag_name
    }

    @Test
    fun testVersionComparisonLogic() {
        val currentVer = "1.0.0-beta.41"
        val currentSemVer = SemVer.parse(currentVer)

        val newerRelease = ReleaseInfo(
            tagName = "v1.0.0-beta.42",
            htmlUrl = "https://github.com/greenjon/liquid-lsd/releases/tag/v1.0.0-beta.42",
            name = "v1.0.0-beta.42",
            body = "",
            publishedAt = ""
        )
        val newerSemVer = SemVer.parse(newerRelease.tagName)
        assertTrue(newerSemVer > currentSemVer, "beta.42 should be detected as an update over beta.41")

        val olderRelease = ReleaseInfo(
            tagName = "v1.0.0-beta.40",
            htmlUrl = "https://github.com/greenjon/liquid-lsd/releases/tag/v1.0.0-beta.40",
            name = "v1.0.0-beta.40",
            body = "",
            publishedAt = ""
        )
        val olderSemVer = SemVer.parse(olderRelease.tagName)
        assertTrue(olderSemVer < currentSemVer, "beta.40 should not be detected as an update over beta.41")
    }
}
