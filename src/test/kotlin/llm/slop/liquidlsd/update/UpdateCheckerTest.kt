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
              "tag_name": "v0.9.2",
              "name": "v0.9.2 - New Audio Filters",
              "html_url": "https://github.com/greenjon/liquid-lsd/releases/tag/v0.9.2",
              "body": "### Highlights\n- Fixed audio filter curves\n- Added update checker",
              "published_at": "2026-09-06T20:00:00Z"
            }
        """.trimIndent()

        val release = UpdateChecker.parseReleaseJson(sampleJson)
        assertNotNull(release)
        assertEquals("v0.9.2", release.tagName)
        assertEquals("v0.9.2 - New Audio Filters", release.name)
        assertEquals("https://github.com/greenjon/liquid-lsd/releases/tag/v0.9.2", release.htmlUrl)
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
        val currentVer = "0.9.1"
        val currentSemVer = SemVer.parse(currentVer)

        val newerRelease = ReleaseInfo(
            tagName = "v0.9.2",
            htmlUrl = "https://github.com/greenjon/liquid-lsd/releases/tag/v0.9.2",
            name = "v0.9.2",
            body = "",
            publishedAt = ""
        )
        val newerSemVer = SemVer.parse(newerRelease.tagName)
        assertTrue(newerSemVer > currentSemVer, "v0.9.2 should be detected as an update over v0.9.1")

        val olderRelease = ReleaseInfo(
            tagName = "v0.9.0",
            htmlUrl = "https://github.com/greenjon/liquid-lsd/releases/tag/v0.9.0",
            name = "v0.9.0",
            body = "",
            publishedAt = ""
        )
        val olderSemVer = SemVer.parse(olderRelease.tagName)
        assertTrue(olderSemVer < currentSemVer, "v0.9.0 should not be detected as an update over v0.9.1")
    }
}
