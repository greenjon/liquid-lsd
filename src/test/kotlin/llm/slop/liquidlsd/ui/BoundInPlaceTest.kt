package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import java.io.File
import kotlin.test.*

/** A fresh state whose rows are on no Perform page, so slot clicks and monitors always open the pair view (see [ParametersState.pageHasRow]). */
internal fun offPage(): ParametersState = ParametersState().apply { pageHasRow = { false } }

/**
 * Target-as-selection: a slot click on a row of the current Perform page binds the Perform dock in place ([ParametersState.boundInPlace])
 * instead of opening the pair view; off-page targets still open the pair view.
 */
class BoundInPlaceTest {
    private val preferencesFile = File("lsd-preferences.properties")
    private val backupFile = File("lsd-preferences.properties.boundinplacetest.bak")
    private var hadBackup = false
    private var savedMode = UITheme.LibraryMode.HALF

    /** The A/B page: both of deck A's and deck B's rows. */
    private val abRows = setOf(MacroEngine.DECK_A, MacroEngine.DECK_A_FX, MacroEngine.DECK_B, MacroEngine.DECK_B_FX)

    private fun onAbPage(): ParametersState = ParametersState().apply { pageHasRow = { it in abRows } }

    @BeforeTest
    fun setUp() {
        hadBackup = preferencesFile.exists()
        if (hadBackup) preferencesFile.copyTo(backupFile, overwrite = true)
        UITheme.rackExpandedModules = emptyMap()
        savedMode = UITheme.libraryMode
        UITheme.libraryMode = UITheme.LibraryMode.HALF
    }

    @AfterTest
    fun tearDown() {
        UITheme.libraryMode = savedMode
        if (hadBackup) { backupFile.copyTo(preferencesFile, overwrite = true); backupFile.delete() } else preferencesFile.delete()
    }

    @Test
    fun slotOnThePageBindsInPlaceWithoutFocusingAPair() {
        val state = onAbPage()
        state.selectFxChain(MacroEngine.DECK_B, "Deck B", slotIndex = 1)
        assertNull(state.focusedPair, "the rows stay where they are")
        assertTrue(state.boundInPlace)
        assertEquals(ParametersState.DockSelection(MacroEngine.DECK_B, ParametersState.BrowseTarget.FxChain(1)), state.dockSelection)
        assertEquals("FX", state.activeDeckBSubTab)
    }

    @Test
    fun clickingTheBoundSlotAgainUnbindsAndAnotherSlotRebinds() {
        val state = onAbPage()
        state.selectGen(MacroEngine.DECK_A, "Deck A")
        val epoch = state.dockSelectionEpoch
        state.selectFxChain(MacroEngine.DECK_A, "Deck A", slotIndex = null)
        assertEquals(ParametersState.BrowseTarget.FxChain(null), state.dockSelection?.target)
        assertEquals(epoch + 1, state.dockSelectionEpoch, "a rebind re-syncs the dock's tab")
        state.selectFxChain(MacroEngine.DECK_A, "Deck A", slotIndex = null)
        assertNull(state.dockSelection)
        assertFalse(state.boundInPlace)
    }

    @Test
    fun aSlotOffThePageOpensThePairView() {
        val state = onAbPage()
        state.selectGen(MacroEngine.DECK_BG, "Deck BG")
        assertEquals("BG", state.focusedPair)
        assertFalse(state.boundInPlace)
        state.selectTransition()
        assertEquals(PerfRows.PAIR_XF, state.focusedPair, "the Transitions row is not on the A/B page either")
    }

    @Test
    fun inThePairViewAClickOnAPageRowStaysInThePairView() {
        val state = onAbPage()
        state.focusPair("A")
        state.selectFxChain(MacroEngine.DECK_A, "Deck A", slotIndex = 0)
        assertEquals("A", state.focusedPair)
        assertFalse(state.boundInPlace)
    }

    @Test
    fun backUnbindsInOnePress() {
        val state = onAbPage()
        state.selectGen(MacroEngine.DECK_B, "Deck B")
        assertTrue(BackNavigation.back(state, null))
        assertNull(state.dockSelection)
        assertFalse(BackNavigation.back(state, null))
    }

    @Test
    fun libraryFullEndsTheBindingAndBackOrHalfReturnsToIt() {
        val state = onAbPage()
        state.selectFxChain(MacroEngine.DECK_A, "Deck A", slotIndex = 2)
        UITheme.libraryMode = UITheme.LibraryMode.FULL
        state.libraryWentFull()
        assertNull(state.dockSelection, "FULL never shows a bound row")
        assertFalse(state.boundInPlace)
        assertTrue(BackNavigation.back(state, null))
        assertEquals(UITheme.LibraryMode.HALF, UITheme.libraryMode)
        assertNull(state.focusedPair)
        assertEquals(ParametersState.DockSelection(MacroEngine.DECK_A, ParametersState.BrowseTarget.FxChain(2)), state.dockSelection)

        // Space / the maximize button back to HALF re-binds too.
        UITheme.libraryMode = UITheme.LibraryMode.FULL
        state.libraryWentFull()
        assertTrue(state.restoreBoundReturn())
        assertTrue(state.boundInPlace)
        assertFalse(state.restoreBoundReturn(), "the way back is used once")
    }

    @Test
    fun editFromABoundRowBrowsesInThePairViewAndClosingEditUnbinds() {
        val state = onAbPage()
        state.selectFxChain(MacroEngine.DECK_B, "Deck B", slotIndex = 0)
        state.toggleRowEdit(MacroEngine.DECK_B_FX)
        assertTrue(state.anyRackModuleExpanded())
        assertFalse(state.boundInPlace, "Edit is its own view")
        assertEquals(ParametersState.BrowseTarget.FxChain(0), state.browseSelectionForEdit(MacroEngine.DECK_B)?.target)
        state.openBrowseFromEdit(MacroEngine.DECK_B)
        assertEquals("B", state.focusedPair)

        state.openParamsForPair("B")
        state.collapseAllRackModules()
        assertNull(state.dockSelection, "leaving Edit lands in the plain Perform view")
    }

    @Test
    fun dirtyPromptIsAskedOncePerInPlaceSession() {
        val state = onAbPage()
        state.selectGen(MacroEngine.DECK_A, "Deck A")
        assertFalse(state.skipsDirtyPrompt("A"))
        state.noteDirtyPromptAnswered("A")
        assertTrue(state.skipsDirtyPrompt("A"))
        state.selectFxChain(MacroEngine.DECK_A, "Deck A", slotIndex = null)
        assertTrue(state.skipsDirtyPrompt("A"), "another slot of the same deck keeps the answer")
        state.selectGen(MacroEngine.DECK_B, "Deck B")
        state.selectGen(MacroEngine.DECK_A, "Deck A")
        assertFalse(state.skipsDirtyPrompt("A"), "a new session on the deck asks again")
        state.noteDirtyPromptAnswered("A")
        state.clearDockSelection()
        assertFalse(state.skipsDirtyPrompt("A"), "unbound: the Library always asks")
    }

    @Test
    fun pageChangesAndPageEditsEndTheBinding() {
        val state = onAbPage()
        state.selectGen(MacroEngine.DECK_A, "Deck A")
        state.leaveEditForPageChange()
        assertNull(state.dockSelection)

        state.selectGen(MacroEngine.DECK_A, "Deck A")
        assertFalse(state.boundRowLeftPage())
        state.pageHasRow = { false }
        assertTrue(state.boundRowLeftPage())
    }

    @Test
    fun monitorsAndSwitchesBindInPlaceWhenTheRowIsOnThePage() {
        val state = onAbPage()
        state.openFromMonitor(MacroEngine.DECK_B, "Deck B")
        assertNull(state.focusedPair)
        assertEquals(ParametersState.DockSelection(MacroEngine.DECK_B, ParametersState.BrowseTarget.Gen), state.dockSelection)

        state.switchBrowseTo("A")
        assertEquals(ParametersState.DockSelection(MacroEngine.DECK_A, ParametersState.BrowseTarget.Gen), state.dockSelection)
        assertTrue(state.boundInPlace)
        state.switchBrowseTo(PerfRows.PAIR_MASTER)
        assertEquals(PerfRows.PAIR_MASTER, state.focusedPair, "Master FX is not on the A/B page")
        assertEquals(ParametersState.BrowseTarget.FxChain(null), state.dockSelection?.target)

        val mixer = ParametersState().apply { pageHasRow = { it == MacroEngine.MASTER_FX } }
        mixer.openFromMonitor(MacroEngine.MASTER, "Mixer")
        assertNull(mixer.focusedPair)
        assertEquals(ParametersState.DockSelection(MacroEngine.MASTER, ParametersState.BrowseTarget.FxChain(null)), mixer.dockSelection)
    }
}
