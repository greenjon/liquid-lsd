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
    fun pairTableCoversEveryDeckAndTheMasterPair() {
        assertEquals(listOf("A", "B", "BG", "PV", "MASTER"), PerfRows.PAIRS.map { it.tag })
        assertEquals(listOf("deck.A.src", "deck.A.fx"), PerfRows.pairFor("A")!!.rowIds)
        assertEquals(listOf("trans", "master.fx"), PerfRows.pairFor("MASTER")!!.rowIds)
        assertNull(PerfRows.pairFor("XF"))
        assertTrue(PerfRows.PAIRS.all { p -> p.rowIds.all { it in PerfRows.CATALOG } })
        assertEquals("A", PerfRows.pairForBank(MacroEngine.DECK_A_FX)?.tag)
        assertEquals("MASTER", PerfRows.pairForBank(MacroEngine.TRANS)?.tag)
        assertEquals("MASTER", PerfRows.pairForBank(MacroEngine.MASTER_FX)?.tag)
        // Master MST + Clock share the other half of the MIXER page but have nothing to browse: no pair-view pair.
        assertNull(PerfRows.pairForBank(MacroEngine.MASTER))
        assertNull(PerfRows.pairForBank(MacroEngine.GLOBAL))
        assertEquals("MASTER", PerfRows.pairForModule(MacroEngine.MASTER)?.tag, "the Master module (dock selections, Edit) is the Master pair")
    }

    @Test
    fun everyPairIsAHalfOfATwisterPage() {
        // The rule: a pair is the two rows that share a half (knobs 1-8 or 9-16) of a Perform page.
        val halves = PerfPageStore.default.all().flatMap { it.rows.map { r -> r.row }.chunked(2) }
        for (pair in PerfRows.PAIRS) assertTrue(pair.rowIds in halves, "${pair.tag} is a half of a page")
        // And Edit's MST pair (master.mix + global) is the other half of the MIXER page.
        assertTrue(listOf("master.mix", "global") in halves)
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
    fun masterWithNothingTouchedBrowsesItsFxChainThenTheHalfLastTouched() {
        val state = offPage()
        val master = PerfRows.pairFor("MASTER")!!
        assertEquals(ParametersState.BrowseTarget.FxChain(null), state.pairBrowseTarget(master))
        state.openFxChainBrowse(MacroEngine.MASTER, null, 2)
        assertEquals(ParametersState.BrowseTarget.FxChain(2), state.pairBrowseTarget(master))
        state.leavePair()
        assertEquals(ParametersState.BrowseTarget.FxChain(null), state.pairBrowseTarget(master))
        state.openTransitionBrowse()
        state.leavePair()
        assertEquals(ParametersState.BrowseTarget.Transition, state.pairBrowseTarget(master), "the OUT tab's last half is the default")
        assertEquals(ParametersState.BrowseTarget.Gen, state.pairBrowseTarget(PerfRows.pairFor("B")!!))
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
        assertEquals("MASTER", state.focusedPair)
        assertEquals(ParametersState.BrowseTarget.Transition, state.dockSelection?.target)

        state.openParams(MacroEngine.MASTER)
        state.activeMixerSubTab = "CTRL"
        assertNull(state.browseSelectionForEdit(MacroEngine.MASTER), "MST has nothing to browse")
        state.activeMixerSubTab = "LEVELS"
        assertNull(state.browseSelectionForEdit(MacroEngine.MASTER), "MST has nothing to browse")
        state.activeMixerSubTab = "FX"
        assertEquals(ParametersState.BrowseTarget.FxChain(null), state.browseSelectionForEdit(MacroEngine.MASTER)?.target)
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

        state.focusPair("MASTER")
        assertEquals(listOf(MacroEngine.TRANS, MacroEngine.MASTER_FX), cache.rows(page, ctx, state, { it }, pages).map { it.bankId })

        state.leavePair()
        assertEquals(pagerows.map { it.bankId }, cache.rows(page, ctx, state, { it }, pages).map { it.bankId })
    }
}
