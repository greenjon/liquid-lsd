package llm.slop.liquidlsd.ui.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistItemsTest {
    private val abc = listOf("a", "b", "c")

    @Test
    fun `move puts the item at the target index`() {
        assertEquals(listOf("b", "c", "a"), PlaylistItems.move(abc, 0, 2))
        assertEquals(listOf("c", "a", "b"), PlaylistItems.move(abc, 2, 0))
        assertEquals(abc, PlaylistItems.move(abc, 5, 0))
        assertEquals(listOf("b", "c", "a"), PlaylistItems.move(abc, 0, 99))
    }

    @Test
    fun `insert and remove`() {
        assertEquals(listOf("a", "x", "y", "b", "c"), PlaylistItems.insert(abc, listOf("x", "y"), 1))
        assertEquals(listOf("a", "b", "c", "x"), PlaylistItems.insert(abc, listOf("x"), 99))
        assertEquals(listOf("b"), PlaylistItems.remove(abc, listOf(2, 0, 7)))
    }

    @Test
    fun `saved files become short tokens relative to their root`() {
        val roots = listOf(java.io.File("/lib/fx"), java.io.File("/lib/fx_chains"))
        assertEquals("sub/a.lsdfx", PlaylistItems.shortToken("/lib/fx/sub/a.lsdfx", roots))
        assertEquals("c.lsdfxchain", PlaylistItems.shortToken("/lib/fx_chains/c.lsdfxchain", roots))
        assertEquals("/elsewhere/a.lsdfx", PlaylistItems.shortToken("/elsewhere/a.lsdfx", roots))
        assertTrue(PlaylistItems.tokenNamesFile("sub/a.lsdfx", "/lib/fx/sub/a.lsdfx"))
    }

    @Test
    fun `stock transition shader files become ids in transition playlists only`() {
        assertEquals("wipe", PlaylistItems.tokenForPayload(BrowseKind.TRANS, "/x/isf/wipe.fs"))
        assertEquals("/lib/t/one.lsdtrans", PlaylistItems.tokenForPayload(BrowseKind.TRANS, "/lib/t/one.lsdtrans"))
        assertEquals("/x/isf/wipe.fs", PlaylistItems.tokenForPayload(BrowseKind.FX, "/x/isf/wipe.fs"))
    }

    @Test
    fun `tokenNamesFile matches bare, relative and absolute tokens of a saved file only`() {
        val path = "/lib/fx/sub/glow.lsdfx"
        for (token in listOf(path, "sub/glow.lsdfx", "glow.lsdfx", "glow")) assertEquals(true, PlaylistItems.tokenNamesFile(token, path), token)
        for (token in listOf("blur", "low", "other/glow.lsdfx", "/lib/fx/glow.lsdfx")) assertEquals(false, PlaylistItems.tokenNamesFile(token, path), token)
    }
}
