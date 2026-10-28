package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueueSplitTest {
    @Test
    fun `only the Library view's browser tabs split, and only with the setting on`() {
        for (tab in LibraryViewMode.entries) {
            val browse = tab in setOf(LibraryViewMode.PRESETS, LibraryViewMode.FX, LibraryViewMode.TRANS)
            assertEquals(browse, QueueSplit.shows(libraryFull = true, splitOn = true, tab = tab), "$tab")
            assertFalse(QueueSplit.shows(libraryFull = false, splitOn = true, tab = tab), "HALF $tab")
            assertFalse(QueueSplit.shows(libraryFull = true, splitOn = false, tab = tab), "off $tab")
        }
    }

    @Test
    fun `heights share the space by the ratio and keep both panes usable`() {
        val half = QueueSplit.heights(600f, 0.5f)!!
        assertEquals(300f, half.top)
        assertEquals(300f, half.bottom)

        val tallBrowser = QueueSplit.heights(600f, 0.9f)!!
        assertEquals(QueueSplit.MIN_PANE_H, tallBrowser.bottom)
        assertEquals(600f, tallBrowser.top + tallBrowser.bottom)

        val tallQueues = QueueSplit.heights(600f, 0.1f)!!
        assertEquals(QueueSplit.MIN_PANE_H, tallQueues.top)
    }

    @Test
    fun `no room for both minimums means no split`() {
        assertNull(QueueSplit.heights(2f * QueueSplit.MIN_PANE_H - 1f, 0.5f))
        assertTrue(QueueSplit.heights(2f * QueueSplit.MIN_PANE_H, 0.5f) != null)
    }
}
