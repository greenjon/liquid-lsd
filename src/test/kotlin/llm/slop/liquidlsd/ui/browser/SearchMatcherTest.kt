package llm.slop.liquidlsd.ui.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchMatcherTest {
    private fun m(query: String, vararg fields: String, extra: List<String> = emptyList()) =
        SearchMatcher.matches(SearchMatcher.tokens(query), fields.asList(), extra)

    @Test
    fun emptyOrBlankQueryMatchesEverything() {
        assertTrue(m("", "Gyroid"))
        assertTrue(m("   ", "Gyroid"))
        assertEquals(emptyList<String>(), SearchMatcher.tokens("  "))
    }

    @Test
    fun matchingIsCaseInsensitiveSubstring() {
        assertTrue(m("GYRO", "Gyroid"))
        assertTrue(m("roi", "Gyroid"))
        assertFalse(m("spiral", "Gyroid"))
    }

    @Test
    fun everyWordMustMatchSomeFieldInAnyOrder() {
        assertTrue(m("neon tunnel", "Tunnel Warp", "fx/neon"))
        assertTrue(m("tunnel neon", "Tunnel Warp", "fx/neon"))
        assertFalse(m("neon spiral", "Tunnel Warp", "fx/neon"))
    }

    @Test
    fun categoriesAndTagsCountAsFields() {
        assertTrue(m("glitch", "Pixel Sort", extra = listOf("Glitch", "Color")))
        assertFalse(m("fractal", "Pixel Sort", extra = listOf("Glitch")))
    }
}
