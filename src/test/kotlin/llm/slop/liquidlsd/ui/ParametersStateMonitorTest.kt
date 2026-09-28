package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParametersStateMonitorTest {

    @Test
    fun openFromMonitor_whenCollapsed_opensParams() {
        val state = ParametersState()
        state.collapseAllRackModules()

        state.openFromMonitor(MacroEngine.DECK_A, "Deck A")

        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.SectionMode.PARAMS, state.sectionModeFor(MacroEngine.DECK_A))
        assertEquals("Deck A", state.activeTopTab)
    }

    @Test
    fun openFromMonitor_whenAlreadyInParams_staysInParams() {
        val state = ParametersState()
        state.openParams(MacroEngine.DECK_A)
        assertEquals(ParametersState.SectionMode.PARAMS, state.sectionModeFor(MacroEngine.DECK_A))

        state.openFromMonitor(MacroEngine.DECK_B, "Deck B")

        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))
        assertEquals(ParametersState.SectionMode.PARAMS, state.sectionModeFor(MacroEngine.DECK_B))
        assertEquals("Deck B", state.activeTopTab)
    }

    @Test
    fun openFromMonitor_whenInBrowseGen_switchesDeckAndStaysInGenBrowse() {
        val state = ParametersState()
        state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
        assertEquals(ParametersState.SectionMode.BROWSE, state.sectionModeFor(MacroEngine.DECK_A))
        assertTrue(state.browseTargetFor(MacroEngine.DECK_A) is ParametersState.BrowseTarget.Gen)

        state.openFromMonitor(MacroEngine.DECK_B, "Deck B")

        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))
        assertEquals(ParametersState.SectionMode.BROWSE, state.sectionModeFor(MacroEngine.DECK_B))
        assertTrue(state.browseTargetFor(MacroEngine.DECK_B) is ParametersState.BrowseTarget.Gen)
        assertEquals("SRC", state.activeDeckBSubTab)
        assertEquals("Deck B", state.activeTopTab)
    }

    @Test
    fun openFromMonitor_whenInBrowseFx_switchesDeckAndStaysInFxBrowse() {
        val state = ParametersState()
        state.openFxChainBrowse(MacroEngine.DECK_A, "Deck A", slotIndex = 2)
        assertEquals(ParametersState.SectionMode.BROWSE, state.sectionModeFor(MacroEngine.DECK_A))
        assertTrue(state.browseTargetFor(MacroEngine.DECK_A) is ParametersState.BrowseTarget.FxChain)

        state.openFromMonitor(MacroEngine.DECK_B, "Deck B")

        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))
        assertEquals(ParametersState.SectionMode.BROWSE, state.sectionModeFor(MacroEngine.DECK_B))
        val targetB = state.browseTargetFor(MacroEngine.DECK_B)
        assertTrue(targetB is ParametersState.BrowseTarget.FxChain)
        assertEquals(2, targetB.slotIndex)
        assertEquals("FX", state.activeDeckBSubTab)
        assertEquals("Deck B", state.activeTopTab)
    }

    @Test
    fun reproducingUserScenario_destickifiesDeckBays() {
        val state = ParametersState()

        // 1. Open source browser on Deck A, then collapse
        state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
        state.collapseAllRackModules()

        // 2. Open fx browser on Deck B, then collapse
        state.openFxChainBrowse(MacroEngine.DECK_B, "Deck B", slotIndex = null)
        state.collapseAllRackModules()

        // 3. Open edit on Deck BG, then collapse
        state.openParams(MacroEngine.DECK_BG)
        state.collapseAllRackModules()

        // All are collapsed
        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_B))
        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_BG))

        // 4. Click monitor for A -> opens Editor (PARAMS)
        state.openFromMonitor(MacroEngine.DECK_A, "Deck A")
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.SectionMode.PARAMS, state.sectionModeFor(MacroEngine.DECK_A))

        // 5. Click monitor for B -> stays in Editor (PARAMS)
        state.openFromMonitor(MacroEngine.DECK_B, "Deck B")
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))
        assertEquals(ParametersState.SectionMode.PARAMS, state.sectionModeFor(MacroEngine.DECK_B))

        // 6. Click monitor for BG -> stays in Editor (PARAMS)
        state.openFromMonitor(MacroEngine.DECK_BG, "Deck BG")
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_BG))
        assertEquals(ParametersState.SectionMode.PARAMS, state.sectionModeFor(MacroEngine.DECK_BG))
    }
}
