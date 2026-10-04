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
import llm.slop.liquidlsd.ui.browser.BrowserPane
import llm.slop.liquidlsd.ui.browser.FXBrowserPanel
import llm.slop.liquidlsd.ui.browser.PresetListPanel
import llm.slop.liquidlsd.ui.browser.TransitionBrowserPanel

class LibraryNavigationTest {
    @AfterTest
    fun reset() {
        LibraryPanel.viewMode = LibraryViewMode.PRESETS
        LibraryPanel.activeSelectionSource = null
        BrowserPane.enabled = false
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
        assertEquals(LibraryViewMode.MAPS, LibraryPanel.viewMode)
        LibraryNavigation.stepTab(1)
        assertEquals(LibraryViewMode.PRESETS, LibraryPanel.viewMode)
        LibraryNavigation.stepTab(-1)
        assertEquals(LibraryViewMode.MAPS, LibraryPanel.viewMode)
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
    fun unifiedPaneOrdersTreeListThenQueuesAndLeavesTheClassicColumnsAlone() {
        val classic = LibraryViewMode.values().filter { it != LibraryViewMode.MAPS }.associateWith {
            LibraryPanel.viewMode = it
            LibraryNavigation.panes()
        }
        BrowserPane.enabled = true
        LibraryPanel.viewMode = LibraryViewMode.PRESETS
        assertEquals(listOf(SelectionSource.TREE, SelectionSource.PRESETS, SelectionSource.QUEUE_BG, SelectionSource.QUEUE_AB), LibraryNavigation.panes())
        LibraryPanel.viewMode = LibraryViewMode.FX
        assertEquals(listOf(SelectionSource.TREE, SelectionSource.PRESETS, SelectionSource.FX_QUEUE_BG, SelectionSource.FX_QUEUE_AB), LibraryNavigation.panes())
        LibraryPanel.viewMode = LibraryViewMode.TRANS
        assertEquals(listOf(SelectionSource.TREE, SelectionSource.PRESETS, SelectionSource.TRANSITION_QUEUE), LibraryNavigation.panes())
        LibraryPanel.viewMode = LibraryViewMode.MAPS
        assertTrue(LibraryNavigation.panes().isEmpty())
        BrowserPane.enabled = false
        for ((mode, panes) in classic) {
            LibraryPanel.viewMode = mode
            assertEquals(panes, LibraryNavigation.panes())
            assertFalse(SelectionSource.TREE in panes)
        }
    }

    @Test
    fun unifiedPaneStartsInTheListAndStepsBackToTheTree() {
        BrowserPane.enabled = true
        val a = AssetItem(path = "/x/a.lsdpreset", name = "a", type = AssetType.PRESET)
        PresetListPanel.filteredPresets = listOf(a)
        val session = mockk<SessionContext>(relaxed = true)
        val mixer = mockk<llm.slop.liquidlsd.rendering.Mixer>(relaxed = true)
        // Fresh tab, no cursor: previous-pane goes from the list to the tree, which always has rows (All).
        LibraryNavigation.stepPane(-1, session, mixer)
        assertEquals(SelectionSource.TREE, LibraryPanel.activeSelectionSource)
        // Next pane from the tree is the list, and it puts the cursor on its first row.
        LibraryNavigation.stepPane(1, session, mixer)
        assertEquals(SelectionSource.PRESETS, LibraryPanel.activeSelectionSource)
    }

    @Test
    fun treeCursorMovesWithoutSelectingAndAcceptSelectsTheScope() {
        BrowserPane.enabled = true
        val kind = llm.slop.liquidlsd.ui.browser.BrowseKind.SRC
        val session = mockk<SessionContext>(relaxed = true)
        val mixer = mockk<llm.slop.liquidlsd.rendering.Mixer>(relaxed = true)
        LibraryPanel.activeSelectionSource = SelectionSource.TREE
        val before = BrowserPane.scopeOf(kind)
        LibraryNavigation.step(1, session, mixer)
        assertEquals(before, BrowserPane.scopeOf(kind), "stepping never applies")
        val cursor = BrowserPane.treeCursorOf(kind)
        assertTrue(cursor != before)
        PresetListPanel.selection.setSingle(AssetItem(path = "/x/a.lsdpreset", name = "a", type = AssetType.PRESET))
        LibraryNavigation.accept(session, mixer, mockk(relaxed = true))
        assertEquals(cursor, BrowserPane.scopeOf(kind))
        assertTrue(PresetListPanel.selection.isEmpty, "list selection resets when the scope changes")
        BrowserPane.stepTree(kind, -9)
        BrowserPane.acceptTree(kind)
        assertEquals(llm.slop.liquidlsd.ui.browser.BrowseScope.All, BrowserPane.scopeOf(kind))
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
