package llm.slop.liquidlsd.ui.browser

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `stock transition shader files become ids in transition playlists only`() {
        assertEquals("wipe", PlaylistItems.tokenForPayload(BrowseKind.TRANS, "/x/isf/wipe.fs"))
        assertEquals("/lib/t/one.lsdtrans", PlaylistItems.tokenForPayload(BrowseKind.TRANS, "/lib/t/one.lsdtrans"))
        assertEquals("/x/isf/wipe.fs", PlaylistItems.tokenForPayload(BrowseKind.FX, "/x/isf/wipe.fs"))
    }
}
