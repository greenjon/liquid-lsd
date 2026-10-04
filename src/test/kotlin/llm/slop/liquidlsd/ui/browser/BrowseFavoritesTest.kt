package llm.slop.liquidlsd.ui.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BrowseFavoritesTest {
    @Test
    fun `toggle stars and unstars, bumps version and persists`(@TempDir dir: File) {
        val file = File(dir, "src_favorites.json")
        BrowseFavorites.resetForTest(BrowseKind.SRC, file)
        val v0 = BrowseFavorites.version(BrowseKind.SRC)

        BrowseFavorites.toggle(BrowseKind.SRC, "plasma")
        BrowseFavorites.toggle(BrowseKind.SRC, "/lib/presets/a.lsd")
        assertTrue(BrowseFavorites.isFavorite(BrowseKind.SRC, "plasma"))
        assertEquals(v0 + 2, BrowseFavorites.version(BrowseKind.SRC))

        // A fresh load from disk sees the same keys, in starred order.
        BrowseFavorites.resetForTest(BrowseKind.SRC, file)
        assertEquals(listOf("plasma", "/lib/presets/a.lsd"), BrowseFavorites.keys(BrowseKind.SRC).toList())

        BrowseFavorites.toggle(BrowseKind.SRC, "plasma")
        assertFalse(BrowseFavorites.isFavorite(BrowseKind.SRC, "plasma"))
    }

    @Test
    fun `kinds are independent`(@TempDir dir: File) {
        BrowseFavorites.resetForTest(BrowseKind.SRC, File(dir, "s.json"))
        BrowseFavorites.resetForTest(BrowseKind.TRANS, File(dir, "t.json"))
        BrowseFavorites.toggle(BrowseKind.TRANS, "wipe")
        assertTrue(BrowseFavorites.isFavorite(BrowseKind.TRANS, "wipe"))
        assertFalse(BrowseFavorites.isFavorite(BrowseKind.SRC, "wipe"))
    }
}
