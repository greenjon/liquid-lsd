package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.ui.ParametersState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalMacroBankTest {

    @Test
    fun globalBankIsCanonicalSoItIsSavedWithTheSession() {
        assertTrue(MacroEngine.GLOBAL in MacroEngine.CANONICAL_BANK_IDS)
        assertEquals(MacroEngine.GLOBAL, MacroEngine.canonicalIdForDeckLabel("Global"))
        assertEquals(MacroEngine.GLOBAL, MacroEngine.canonicalIdForDeckLabel("GLB"))
    }

    @Test
    fun globalBankDefaultsToFourUnboundKnobs() {
        val bank = MacroEngine.newBankFor(MacroEngine.GLOBAL)
        assertEquals(listOf("GLOBAL 1", "GLOBAL 2", "GLOBAL 3", "GLOBAL 4"), bank.knobs.map { it.label })
        assertTrue(bank.knobs.all { it.bindings.isEmpty() })
    }

    @Test
    fun globalKnobsMayBindToAnySection() {
        assertNull(MacroLearnState.sectionFor(MacroEngine.GLOBAL))
        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.GLOBAL, "Deck A/fbZoom"))
        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.GLOBAL, "Deck B/FX/FX1/DryWet"))
        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.GLOBAL, "Master/FX/Super"))
    }

    @Test
    fun macrosGlbTabDropsBackWhenNavigationMovesOn() {
        val state = ParametersState()
        state.activeTopTab = "Deck A"
        state.showGlobalMacros()
        assertTrue(state.isGlobalMacrosShown())

        state.setDeckSubTab("Deck A", "FX")
        assertFalse(state.isGlobalMacrosShown())

        state.showGlobalMacros()
        state.hideGlobalMacros()
        assertFalse(state.isGlobalMacrosShown())
    }
}
