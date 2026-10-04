package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.LibraryPanel.SelectionSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import io.mockk.mockk
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.ui.browser.FXBrowserPanel
import llm.slop.liquidlsd.ui.browser.PresetListPanel
import llm.slop.liquidlsd.ui.browser.TransitionBrowserPanel

class LibraryNavigationTest {
    @AfterTest
    fun reset() {
        LibraryPanel.viewMode = LibraryViewMode.PRESETS
        LibraryPanel.activeSelectionSource = null
        PresetListPanel.selection.clear()
        PresetListPanel.filteredPresets = emptyList()
        FXBrowserPanel.selectedAsset = null
        FXBrowserPanel.shouldFocusSearch = false
        TransitionBrowserPanel.shouldFocusSearch = false
        PresetListPanel.shouldFocusSearch = false
    }

    @Test
    fun ctrlFFocusesTheSearchOfTheVisibleTabOnly() {
        for (mode in LibraryViewMode.values()) {
            LibraryPanel.viewMode = mode
            LibraryPanel.focusActiveSearch()
            assertEquals(mode == LibraryViewMode.PRESETS, PresetListPanel.shouldFocusSearch, "$mode sources")
            assertEquals(mode == LibraryViewMode.FX, FXBrowserPanel.shouldFocusSearch, "$mode fx")
            assertEquals(mode == LibraryViewMode.TRANS, TransitionBrowserPanel.shouldFocusSearch, "$mode trans")
            PresetListPanel.shouldFocusSearch = false
            FXBrowserPanel.shouldFocusSearch = false
            TransitionBrowserPanel.shouldFocusSearch = false
        }
    }

    @Test
    fun enqueueTargetsIgnoreTheHiddenSourcesSelectionWhenAnotherTabIsShown() {
        val a = AssetItem(path = "/x/a.lsdpreset", name = "a", type = AssetType.PRESET)
        PresetListPanel.filteredPresets = listOf(a)
        PresetListPanel.selection.setSingle(a)
        LibraryPanel.activeSelectionSource = SelectionSource.PRESETS
        LibraryPanel.viewMode = LibraryViewMode.FX
        // FX tab with nothing selected there: must not fall back to the Sources selection.
        assertTrue(LibraryNavigation.enqueueTargets(mockk<SessionContext>(relaxed = true)).isEmpty())
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
    fun acceptOnTheABQueuePlaysTheIndexLikeADoubleClick() {
        val session = mockk<SessionContext>(relaxed = true)
        val mixer = mockk<llm.slop.liquidlsd.rendering.Mixer>(relaxed = true)
        val queue = llm.slop.liquidlsd.presets.PlayQueueManager
        io.mockk.mockkObject(queue)
        io.mockk.every { session.playQueueManager } returns queue
        io.mockk.every { queue.playIndex(any(), any()) } returns Unit
        LibraryPanel.activeSelectionSource = SelectionSource.QUEUE_AB
        llm.slop.liquidlsd.ui.browser.QueueActionsPanel.selectedIndex = 2
        try {
            LibraryNavigation.accept(session, mixer, mockk(relaxed = true))
            io.mockk.verify(exactly = 1) { queue.playIndex(2, mixer) }
        } finally {
            io.mockk.unmockkObject(queue)
            llm.slop.liquidlsd.ui.browser.QueueActionsPanel.clearSelection()
        }
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
