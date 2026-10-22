package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import java.io.File
import kotlin.test.*

/** The pair focus view's state: the pair table, exclusivity with a Params Edit module, and the Esc unwind. */
class PairFocusTest {
    private val preferencesFile = File("lsd-preferences.properties")
    private val backupFile = File("lsd-preferences.properties.pairfocustest.bak")
    private var hadBackup = false

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

    @Test
    fun pairTableCoversEveryDeckMasterAndXf() {
        assertEquals(listOf("A", "B", "BG", "PV", "MASTER", "XF"), PerfRows.PAIRS.map { it.tag })
        assertEquals(listOf("deck.A.src", "deck.A.fx"), PerfRows.pairFor("A")!!.rowIds)
        assertEquals(listOf("master.mix", "master.fx"), PerfRows.pairFor("MASTER")!!.rowIds)
        assertEquals(listOf("trans", "global"), PerfRows.pairFor("XF")!!.rowIds)
        assertTrue(PerfRows.PAIRS.all { p -> p.rowIds.all { it in PerfRows.CATALOG } })
        assertEquals("A", PerfRows.pairForBank(MacroEngine.DECK_A_FX)?.tag)
        assertEquals("XF", PerfRows.pairForBank(MacroEngine.GLOBAL)?.tag)
    }

    @Test
    fun focusPairAndParamsEditAreMutuallyExclusive() {
        val state = offPage()
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        state.focusPair("B")
        assertEquals("B", state.focusedPair)
        assertFalse(state.anyRackModuleExpanded())

        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertNull(state.focusedPair)
        assertTrue(state.anyRackModuleExpanded())
    }

    @Test
    fun unknownPairIsIgnored() {
        val state = offPage()
        state.focusPair("nope")
        assertNull(state.focusedPair)
    }

    @Test
    fun backLeavesThePairInOnePress() {
        val state = offPage()
        state.focusPair("MASTER")
        assertTrue(BackNavigation.back(state, null))
        assertNull(state.focusedPair)
        assertFalse(BackNavigation.back(state, null))
    }

    @Test
    fun masterWithNothingTouchedBrowsesItsFxChain() {
        val state = offPage()
        val master = PerfRows.pairFor("MASTER")!!
        assertNull(state.pairBrowseTarget(master))
        assertEquals(ParametersState.BrowseTarget.FxChain(null), state.pairDockTarget(master))
        state.openFxChainBrowse(MacroEngine.MASTER, null, 2)
        assertEquals(ParametersState.BrowseTarget.FxChain(2), state.pairDockTarget(master))
        assertEquals(ParametersState.BrowseTarget.Gen, state.pairDockTarget(PerfRows.pairFor("B")!!))
    }

    @Test
    fun browseFromEditGoesToTheSamePairAndHalfKeepingTheSlot() {
        val state = offPage()
        state.openFxChainBrowse(MacroEngine.DECK_A, "Deck A", 2)
        state.openParamsForPair("A")
        assertEquals("FX", state.activeDeckASubTab)
        assertEquals(ParametersState.BrowseTarget.FxChain(2), state.browseSelectionForEdit(MacroEngine.DECK_A)?.target)
        state.openBrowseFromEdit(MacroEngine.DECK_A)
        assertEquals("A", state.focusedPair)
        assertFalse(state.anyRackModuleExpanded())
        assertEquals(ParametersState.BrowseTarget.FxChain(2), state.dockSelection?.target)

        state.openParamsFromRow(MacroEngine.DECK_B)
        assertEquals(ParametersState.DockSelection(MacroEngine.DECK_B, ParametersState.BrowseTarget.Gen), state.browseSelectionForEdit(MacroEngine.DECK_B))

        state.openParamsFromRow(MacroEngine.TRANS)
        state.openBrowseFromEdit(MacroEngine.MASTER)
        assertEquals("XF", state.focusedPair)
        assertEquals(ParametersState.BrowseTarget.Transition, state.dockSelection?.target)

        state.openParams(MacroEngine.MASTER)
        state.activeMixerSubTab = "CTRL"
        assertNull(state.browseSelectionForEdit(MacroEngine.MASTER), "MIX has nothing to browse")
        state.openBrowseFromEdit(MacroEngine.MASTER)
        assertEquals("MASTER", state.focusedPair)
    }

    @Test
    fun knobTouchRetargetDoesNotBumpTheTabEpoch() {
        val state = offPage()
        state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
        val epoch = state.dockSelectionEpoch
        state.openBrowse(MacroEngine.DECK_A, ParametersState.BrowseTarget.FxChain(0), retab = false)
        assertEquals(epoch, state.dockSelectionEpoch)
        assertEquals(ParametersState.BrowseTarget.FxChain(0), state.dockSelection?.target)
        state.openBrowse(MacroEngine.DECK_A, ParametersState.BrowseTarget.Gen)
        assertEquals(epoch + 1, state.dockSelectionEpoch)
    }

    @Test
    fun dirtyPromptIsAskedOncePerPairView() {
        val state = offPage()
        state.focusPair("A")
        assertFalse(state.skipsDirtyPrompt("A"))
        state.noteDirtyPromptAnswered("A")
        assertTrue(state.skipsDirtyPrompt("A"))
        assertFalse(state.skipsDirtyPrompt("B"), "another deck still asks")
        state.openBrowse(MacroEngine.DECK_A, ParametersState.BrowseTarget.FxChain(null))
        assertTrue(state.skipsDirtyPrompt("A"), "retargeting inside the pair keeps the answer")
        state.focusPair("B")
        state.focusPair("A")
        assertFalse(state.skipsDirtyPrompt("A"), "a new pair-view session asks again")
        state.noteDirtyPromptAnswered("B")
        assertFalse(state.skipsDirtyPrompt("B"), "an answer for a deck whose pair is not focused does not count")
        state.noteDirtyPromptAnswered("A")
        state.leavePair()
        assertFalse(state.skipsDirtyPrompt("A"))
    }

    @Test
    fun libraryReturnIsClearedByOpeningAnotherFocus() {
        val state = offPage()
        state.focusPair("BG")
        state.rememberLibraryReturn()
        state.leavePair()
        assertNotNull(state.libraryReturn)
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertNull(state.libraryReturn)
        state.collapseAllRackModules()
        assertFalse(BackNavigation.back(state, null))

        state.setDisclosure(MacroEngine.DECK_PV, ParametersState.DisclosureLevel.DEEP_EDIT)
        state.rememberLibraryReturn()
        state.collapseAllRackModules()
        state.leaveEditForPageChange()
        assertNull(state.libraryReturn)
    }

    @Test
    fun rowsCacheFollowsPairSwitches() {
        val state = offPage()
        val ctx = PerformanceUiContext()
        val pages = PerfPageStore.default.all()
        val page = pages.first()
        val cache = PerfRows.RowsCache()
        val pagerows = cache.rows(page, ctx, state, { it }, pages)

        state.focusPair("A")
        val a = cache.rows(page, ctx, state, { it }, pages)
        assertEquals(listOf(MacroEngine.DECK_A, MacroEngine.DECK_A_FX), a.map { it.bankId })
        assertSame(a, cache.rows(page, ctx, state, { it }, pages))

        state.focusPair("XF")
        assertEquals(listOf(MacroEngine.TRANS, MacroEngine.GLOBAL), cache.rows(page, ctx, state, { it }, pages).map { it.bankId })

        state.leavePair()
        assertEquals(pagerows.map { it.bankId }, cache.rows(page, ctx, state, { it }, pages).map { it.bankId })
    }
}
