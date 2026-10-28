package llm.slop.liquidlsd.ui

import java.io.File
import kotlin.test.*

/** Chain view's state and rows: entering and leaving, the play / browse toggle, and the slot rows (1-3) never moving. */
class ChainViewTest {
    private val preferencesFile = File("lsd-preferences.properties")
    private val backupFile = File("lsd-preferences.properties.chainviewtest.bak")
    private var hadBackup = false
    private val ctx = PerformanceUiContext()
    private val identity: (String) -> String = { it }
    private val page = PerfPageDef("t", "T", rows = listOf("deck.A.src", "deck.A.fx", "deck.B.src", "deck.B.fx").map(::RowPlacement))

    @BeforeTest
    fun setUp() {
        hadBackup = preferencesFile.exists()
        if (hadBackup) preferencesFile.copyTo(backupFile, overwrite = true)
        UITheme.rackExpandedModules = emptyMap()
    }

    @AfterTest
    fun tearDown() {
        if (hadBackup) { backupFile.copyTo(preferencesFile, overwrite = true); backupFile.delete() } else preferencesFile.delete()
    }

    private fun state() = ParametersState().also { it.rackModuleDisclosure.clear() }

    private fun rowIds(state: ParametersState): List<String> =
        PerfRows.visibleRowsForPage(page, ctx, state, identity).map { row ->
            if (row.loadRow) "load" else PerfRows.CATALOG.entries.first { it.value === row }.key
        }

    @Test
    fun entersInPlayWithTheSourceRowAtTheBottomAndTheDockOnSlotOne() {
        val s = state()
        s.enterChainView("A")
        assertEquals("A", s.focusedPair)
        assertEquals(ParametersState.ChainViewState.PLAY, s.chainView)
        assertEquals(listOf("deck.A.fx1", "deck.A.fx2", "deck.A.fx3", "deck.A.src"), rowIds(s))
        assertEquals(ParametersState.BrowseTarget.FxChain(0), s.dockSelection?.target)
    }

    @Test
    fun browseSwapsOnlyTheControlRow() {
        val s = state()
        s.enterChainView("B")
        val play = PerfRows.visibleRowsForPage(page, ctx, s, identity)
        s.toggleChainBrowse()
        assertEquals(ParametersState.ChainViewState.BROWSE, s.chainView)
        val browse = PerfRows.visibleRowsForPage(page, ctx, s, identity)
        assertTrue(browse[3].loadRow)
        assertEquals(play.dropLast(1), browse.dropLast(1))
        s.toggleChainBrowse()
        assertEquals(ParametersState.ChainViewState.PLAY, s.chainView)
    }

    @Test
    fun masterChainViewHasTransitionsAtTheBottom() {
        val s = state()
        s.enterChainView(PerfRows.PAIR_MASTER)
        assertEquals(listOf("master.fx1", "master.fx2", "master.fx3", "trans"), rowIds(s))
    }

    @Test
    fun steppingPairsKeepsChainViewAndLeavingClearsIt() {
        val s = state()
        s.enterChainView("A")
        s.toggleChainBrowse()
        s.focusPair("BG")
        assertEquals(ParametersState.ChainViewState.BROWSE, s.chainView)
        assertEquals("deck.BG.fx1", rowIds(s).first())
        assertEquals("load", rowIds(s).last())
        s.leavePair()
        assertNull(s.chainView)
        assertEquals(page.rows.map { it.row }, rowIds(s))
    }

    @Test
    fun toggleLeavesFromTheSamePairAndSwitchesFromAnother() {
        val s = state()
        s.toggleChainView("A")
        s.toggleChainView("B")
        assertEquals("B", s.focusedPair)
        assertNotNull(s.chainView)
        s.toggleChainView("B")
        assertNull(s.focusedPair)
        assertNull(s.chainView)
    }

    @Test
    fun backLeavesChainViewInOnePress() {
        val s = state()
        s.enterChainView("A")
        s.toggleChainBrowse()
        assertTrue(BackNavigation.back(s, null))
        assertNull(s.focusedPair)
        assertNull(s.chainView)
    }

    @Test
    fun openingEditEndsChainView() {
        val s = state()
        s.enterChainView("A")
        s.openParams(llm.slop.liquidlsd.macro.MacroEngine.DECK_A)
        assertNull(s.chainView)
        s.collapseAllRackModules()
    }

    @Test
    fun theRowsCacheFollowsTheToggle() {
        val s = state()
        val cache = PerfRows.RowsCache()
        s.enterChainView("A")
        assertFalse(cache.rows(page, ctx, s, identity)[3].loadRow)
        s.toggleChainBrowse()
        assertTrue(cache.rows(page, ctx, s, identity)[3].loadRow)
    }
}
