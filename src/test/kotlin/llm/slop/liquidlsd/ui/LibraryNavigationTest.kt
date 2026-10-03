package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.LibraryPanel.SelectionSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryNavigationTest {
    @AfterTest
    fun reset() {
        LibraryPanel.viewMode = LibraryViewMode.PRESETS
        LibraryPanel.activeSelectionSource = null
    }

    @Test
    fun stepTabWrapsInBothDirections() {
        LibraryNavigation.stepTab(1)
        assertEquals(LibraryViewMode.FX, LibraryPanel.viewMode)
        LibraryNavigation.stepTab(1)
        assertEquals(LibraryViewMode.TRANS, LibraryPanel.viewMode)
        LibraryNavigation.stepTab(1)
        assertEquals(LibraryViewMode.PRESETS, LibraryPanel.viewMode)
        LibraryNavigation.stepTab(-1)
        assertEquals(LibraryViewMode.TRANS, LibraryPanel.viewMode)
    }

    @Test
    fun changingTabDropsTheSelectionSourceButReselectingTheSameTabKeepsIt() {
        LibraryPanel.activeSelectionSource = SelectionSource.QUEUE_AB
        LibraryNavigation.setViewMode(LibraryViewMode.PRESETS)
        assertEquals(SelectionSource.QUEUE_AB, LibraryPanel.activeSelectionSource)
        LibraryNavigation.setViewMode(LibraryViewMode.FX)
        assertNull(LibraryPanel.activeSelectionSource)
    }
}

class PickerCursorTest {
    @Test
    fun chainListCursorMovesClampedAndAppliesTheCursorRow() {
        val applied = ArrayList<String>()
        val items = listOf("a", "b", "c").map { AssetItem(path = "/x/$it", name = it, type = AssetType.FX_CHAIN) }
        ChainListBrowse.reset()
        ChainListBrowse.publish(items, { applied += it.name }, {})
        assertEquals(false, ChainListBrowse.accept())          // no cursor yet
        ChainListBrowse.move(1)
        ChainListBrowse.move(1)
        ChainListBrowse.move(5)                                // clamps at the last row
        assertEquals(true, ChainListBrowse.accept())
        ChainListBrowse.move(-9)                               // clamps at the first row
        ChainListBrowse.accept()
        assertEquals(listOf("c", "a"), applied)
        assertEquals(true, ChainListBrowse.isShowing)
    }
}
