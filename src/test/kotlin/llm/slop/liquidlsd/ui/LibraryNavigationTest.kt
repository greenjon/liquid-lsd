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
import llm.slop.liquidlsd.ui.browser.BrowseKind
import llm.slop.liquidlsd.ui.browser.BrowserPane
import llm.slop.liquidlsd.ui.browser.FXBrowserPanel
import llm.slop.liquidlsd.ui.browser.PresetListPanel

class LibraryNavigationTest {
    @AfterTest
    fun reset() {
        LibraryPanel.viewMode = LibraryViewMode.PRESETS
        LibraryPanel.activeSelectionSource = null
        PresetListPanel.selection.clear()
        PresetListPanel.filteredPresets = emptyList()
        FXBrowserPanel.selectedAsset = null
    }

    @Test
    fun ctrlFFocusesTheSearchOfTheVisibleKindOnly() {
        val expected = mapOf(
            LibraryViewMode.PRESETS to BrowseKind.SRC,
            LibraryViewMode.FX to BrowseKind.FX,
            LibraryViewMode.TRANS to BrowseKind.TRANS,
        )
        for (mode in LibraryViewMode.values()) {
            BrowserPane.searchFocusRequests.clear()
            LibraryPanel.viewMode = mode
            LibraryPanel.focusActiveSearch()
            assertEquals(listOfNotNull(expected[mode]), BrowserPane.searchFocusRequests.toList(), "$mode")
        }
        BrowserPane.searchFocusRequests.clear()
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
    fun unifiedPaneOrdersTreeListThenQueues() {
        LibraryPanel.viewMode = LibraryViewMode.PRESETS
        assertEquals(listOf(SelectionSource.TREE, SelectionSource.PRESETS, SelectionSource.QUEUE_BG, SelectionSource.QUEUE_AB), LibraryNavigation.panes())
        LibraryPanel.viewMode = LibraryViewMode.FX
        assertEquals(listOf(SelectionSource.TREE, SelectionSource.PRESETS, SelectionSource.FX_QUEUE_BG, SelectionSource.FX_QUEUE_AB), LibraryNavigation.panes())
        LibraryPanel.viewMode = LibraryViewMode.TRANS
        assertEquals(listOf(SelectionSource.TREE, SelectionSource.PRESETS, SelectionSource.TRANSITION_QUEUE), LibraryNavigation.panes())
        LibraryPanel.viewMode = LibraryViewMode.MAPS
        assertTrue(LibraryNavigation.panes().isEmpty())
    }

    @Test
    fun unifiedPaneStartsInTheListAndStepsBackToTheTree() {
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
