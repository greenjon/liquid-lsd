package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BrowseRecentsTest {
    @Test
    fun `note moves to the top, drops duplicates, caps the list and persists`(@TempDir dir: File) {
        val file = File(dir, "fx_recent.json")
        BrowseRecents.resetForTest(BrowseKind.FX, file)
        BrowseRecents.note(AssetItem("${BrowseCatalogs.STOCK_FX_PREFIX}blur", "Blur", AssetType.FX_STOCK))
        BrowseRecents.note(AssetItem("/lib/fx/a.lsdfxchain", "a", AssetType.FX_CHAIN))
        BrowseRecents.note(AssetItem("${BrowseCatalogs.STOCK_FX_PREFIX}blur", "Blur", AssetType.FX_STOCK))
        assertEquals(listOf("blur", "/lib/fx/a.lsdfxchain"), BrowseRecents.keys(BrowseKind.FX))

        repeat(BrowseRecents.LIMIT + 5) { BrowseRecents.note(BrowseKind.FX, "k$it") }
        assertEquals(BrowseRecents.LIMIT, BrowseRecents.keys(BrowseKind.FX).size)
        assertEquals("k${BrowseRecents.LIMIT + 4}", BrowseRecents.keys(BrowseKind.FX).first())

        val saved = BrowseRecents.keys(BrowseKind.FX).toList()
        BrowseRecents.resetForTest(BrowseKind.FX, file)
        assertEquals(saved, BrowseRecents.keys(BrowseKind.FX))
    }

    @Test
    fun `playlists and folders are not recorded`(@TempDir dir: File) {
        BrowseRecents.resetForTest(BrowseKind.SRC, File(dir, "src_recent.json"))
        BrowseRecents.note(AssetItem("/lib/playlists/a.lsdplay", "a", AssetType.PLAYLIST))
        assertEquals(emptyList<String>(), BrowseRecents.keys(BrowseKind.SRC))
    }
}
