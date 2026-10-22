package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParametersStateMonitorTest {

    @Test
    fun openFromMonitor_focusesTheDecksPair() {
        val state = offPage()
        state.collapseAllRackModules()

        state.openFromMonitor(MacroEngine.DECK_A, "Deck A")

        assertEquals("A", state.focusedPair)
        assertFalse(state.anyRackModuleExpanded())
        assertEquals("Deck A", state.activeTopTab)
    }

    @Test
    fun openFromMonitor_switchesPairs_andDropsTheOldBinding() {
        val state = offPage()
        state.openFxChainBrowse(MacroEngine.DECK_A, "Deck A", slotIndex = 2)
        assertEquals("A", state.focusedPair)

        state.openFromMonitor(MacroEngine.DECK_B, "Deck B")

        assertEquals("B", state.focusedPair)
        assertEquals(ParametersState.DockSelection(MacroEngine.DECK_B, ParametersState.BrowseTarget.Gen), state.dockSelection, "the new pair's default half")
        assertEquals("Deck B", state.activeTopTab)
    }

    @Test
    fun openFromMonitor_onMasterFocusesTheMasterPair() {
        val state = offPage()
        state.openFromMonitor(MacroEngine.MASTER, "Mixer")
        assertEquals(PerfRows.PAIR_MASTER, state.focusedPair)
    }

    @Test
    fun dockSelection_isSingleAndClearable() {
        val state = offPage()
        assertNull(state.dockSelection)
        state.openFxChainBrowse(MacroEngine.DECK_A, "Deck A", slotIndex = 1)
        state.openGenBrowse(MacroEngine.DECK_B, "Deck B")
        assertEquals(MacroEngine.DECK_B, state.dockSelection?.moduleId)
        assertEquals("B", state.focusedPair)
        state.clearDockSelection()
        assertNull(state.dockSelection)
    }

    @Test
    fun selectingARowSlotFocusesItsPairAndBindsTheBrowse() {
        val state = offPage()
        state.collapseAllRackModules()
        state.selectFxChain(MacroEngine.DECK_A, "Deck A", slotIndex = 2)
        assertFalse(state.anyRackModuleExpanded())
        assertEquals("A", state.focusedPair)
        assertEquals(ParametersState.DockSelection(MacroEngine.DECK_A, ParametersState.BrowseTarget.FxChain(2)), state.dockSelection)
        assertEquals("FX", state.activeDeckASubTab)
        state.selectTransition()
        assertEquals(PerfRows.PAIR_MASTER, state.focusedPair)
        assertEquals(ParametersState.BrowseTarget.Transition, state.dockSelection?.target)
    }

    @Test
    fun selectingWhileARowIsOpenClosesItAndFocusesThePair() {
        val state = offPage()
        state.openParams(MacroEngine.DECK_A)
        state.selectGen(MacroEngine.DECK_A, "Deck A")
        assertEquals("A", state.focusedPair)
        assertFalse(state.anyRackModuleExpanded())
    }

    @Test
    fun pairBrowseTargetIsTheHalfLastTouchedElseTheFirstBrowsableHalf() {
        val state = offPage()
        val deckA = PerfRows.pairFor("A")!!
        state.focusPair("A")
        assertEquals(ParametersState.BrowseTarget.Gen, state.pairBrowseTarget(deckA))
        state.selectFxChain(MacroEngine.DECK_A, "Deck A", slotIndex = 1)
        assertEquals(ParametersState.BrowseTarget.FxChain(1), state.pairBrowseTarget(deckA))
        state.focusPair(PerfRows.PAIR_MASTER)
        assertEquals(ParametersState.BrowseTarget.FxChain(null), state.pairBrowseTarget(PerfRows.pairFor(PerfRows.PAIR_MASTER)!!)) // the Master FX chain until the transition is touched
    }

    @Test
    fun paramsButtonOpensTheEditViewOnTheTouchedHalf() {
        val state = offPage()
        state.selectFxChain(MacroEngine.DECK_B, "Deck B", slotIndex = null)
        state.openParamsForPair("B")
        assertNull(state.focusedPair)
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))
        assertEquals("FX", state.activeDeckBSubTab)
    }

    @Test
    fun reproducingUserScenario_destickifiesDeckBays() {
        val state = offPage()
        state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
        state.leavePair()
        state.openFxChainBrowse(MacroEngine.DECK_B, "Deck B", slotIndex = null)
        state.leavePair()
        state.openParams(MacroEngine.DECK_BG)
        state.collapseAllRackModules()

        assertNull(state.focusedPair)
        assertNull(state.dockSelection)
        assertFalse(state.anyRackModuleExpanded())
        assertTrue(state.rackModuleDisclosure.values.all { it == ParametersState.DisclosureLevel.COLLAPSED })
    }
}
