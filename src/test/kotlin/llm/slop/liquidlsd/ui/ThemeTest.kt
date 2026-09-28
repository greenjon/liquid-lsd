package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeTest {

    @Test
    fun testThemeEnumAndFormatting() {
        val themes = UITheme.Theme.values()
        assertTrue(themes.contains(UITheme.Theme.GREY_ACID), "Theme enum must contain GREY_ACID")
        assertTrue(themes.contains(UITheme.Theme.ORANGE_SUNSHINE), "Theme enum must contain ORANGE_SUNSHINE")

        val themeNames = themes.map { theme ->
            theme.name.split("_")
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { it.uppercaseChar() }
                }
        }

        assertTrue(themeNames.contains("Grey Acid"), "Formatted names must include 'Grey Acid'")
        assertTrue(themeNames.contains("Orange Sunshine"), "Formatted names must include 'Orange Sunshine'")
    }

    @Test
    fun testPreferencesThemeAssignment() {
        val prefs = AppPreferences(theme = UITheme.Theme.ORANGE_SUNSHINE)
        assertEquals(UITheme.Theme.ORANGE_SUNSHINE, prefs.theme)

        val resolved = try {
            UITheme.Theme.valueOf("ORANGE_SUNSHINE")
        } catch (e: Exception) {
            UITheme.Theme.GREY_ACID
        }
        assertEquals(UITheme.Theme.ORANGE_SUNSHINE, resolved)

        val fallback = try {
            UITheme.Theme.valueOf("NON_EXISTENT_THEME")
        } catch (e: Exception) {
            UITheme.Theme.GREY_ACID
        }
        assertEquals(UITheme.Theme.GREY_ACID, fallback)
    }
}
