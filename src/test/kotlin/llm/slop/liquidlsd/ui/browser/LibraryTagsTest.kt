package llm.slop.liquidlsd.ui.browser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LibraryTagsTest {
    @Test
    fun `typed tags are trimmed, lowercased and deduplicated`() {
        assertEquals(listOf("dark", "slow"), LibraryTags.parse(" Dark, slow ,dark,, "))
    }

    @Test
    fun `editing tags rewrites only the tags field`(@TempDir dir: File) {
        val file = File(dir, "a.lsdfx").apply { writeText("""{"name":"a","weird":{"keep":1.50},"tags":["old"],"slot":{"filterId":"blur"}}""") }
        assertEquals(1, LibraryTags.add(listOf(file), listOf("Dark"), emptyList()))
        assertEquals(listOf("old", "dark"), LibraryTags.read(file))
        val obj = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(1.5, obj["weird"]!!.jsonObject["keep"].toString().toDouble(), "other fields are kept")
        assertEquals("blur", obj["slot"]!!.jsonObject["filterId"].toString().trim('"'))

        assertEquals(0, LibraryTags.add(listOf(file), listOf("dark"), emptyList()), "an existing tag changes nothing")
        LibraryTags.rename(listOf(file), "old", "dark", emptyList())
        assertEquals(listOf("dark"), LibraryTags.read(file), "renaming onto a tag the file has merges them")
        LibraryTags.remove(listOf(file), "DARK", emptyList())
        assertEquals(emptyList<String>(), LibraryTags.read(file))
    }

    @Test
    fun `a file without a tags field gets one`(@TempDir dir: File) {
        val file = File(dir, "p.lsd").apply { writeText("""{"name":"p"}""") }
        LibraryTags.add(listOf(file), listOf("live"), emptyList())
        assertEquals(listOf("live"), LibraryTags.read(file))
    }

    @Test
    fun `tags group lists saved entries' tags as crates, not stock categories`() {
        fun saved(path: String, vararg tags: String) =
            BrowseEntry(AssetItem(path, path, AssetType.PRESET, tags = tags.toList()), BrowseSection.SAVED, path)
        val stock = BrowseEntry(AssetItem("stock://x", "x", AssetType.SOURCE_STOCK, tags = listOf("Generator")), BrowseSection.STOCK, "x")
        val c = BrowseCatalog(BrowseKind.SRC, listOf(stock, saved("/a.lsd", "Dark", "slow"), saved("/b.lsd", "dark"), saved("/c.lsd")))
        val labels = c.tree().map { "${" ".repeat(it.depth)}${it.label}(${it.count})" }
        assertTrue(listOf("Tags(2)", " dark(2)", " slow(1)").all { it in labels }, labels.toString())
        assertFalse(labels.any { "generator" in it.lowercase() && it.startsWith(" ") })
        assertEquals(listOf("/a.lsd", "/b.lsd"), c.rows(BrowseScope.Tag("dark")).map { it.key })
        assertEquals(listOf("dark", "slow"), c.tags())
    }

    @Test
    fun `saved FX favorites live beside the stock shortlist`(@TempDir dir: File) {
        BrowseFavorites.resetForTest(BrowseKind.FX, File(dir, "fx_saved_favorites.json"))
        llm.slop.liquidlsd.presets.FxShortlist.setForTest(listOf("blur"))
        BrowseFavorites.toggle(BrowseKind.FX, "/lib/fx/glow.lsdfx")
        assertTrue(BrowseFavorites.isFavorite(BrowseKind.FX, "/lib/fx/glow.lsdfx"))
        assertTrue(BrowseFavorites.isFavorite(BrowseKind.FX, "blur"))
        assertEquals(setOf("blur", "/lib/fx/glow.lsdfx"), BrowseFavorites.keys(BrowseKind.FX))
        assertEquals(listOf("blur"), llm.slop.liquidlsd.presets.FxShortlist.favorites(), "the slot stepper's list stays stock-only")

        BrowseFavorites.relocate(BrowseKind.FX, mapOf("/lib/fx/glow.lsdfx" to "/lib/fx/g/glow.lsdfx"))
        assertTrue(BrowseFavorites.isFavorite(BrowseKind.FX, "/lib/fx/g/glow.lsdfx"))
        llm.slop.liquidlsd.presets.FxShortlist.setForTest(emptyList())
    }
}
