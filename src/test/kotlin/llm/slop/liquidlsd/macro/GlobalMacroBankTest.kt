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
    fun globalBankHasNoKnobsForNow() {
        assertTrue(MacroEngine.newBankFor(MacroEngine.GLOBAL).knobs.isEmpty())
        assertEquals(4, MacroEngine.defaultKnobCountFor(MacroEngine.MASTER))
    }

    @Test
    fun globalKnobsMayBindToAnySection() {
        assertNull(MacroLearnState.sectionFor(MacroEngine.GLOBAL))
        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.GLOBAL, "Deck A/fbZoom"))
        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.GLOBAL, "Deck B/FX/FX1/DryWet"))
        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.GLOBAL, "Master/FX/Super"))
    }
}
