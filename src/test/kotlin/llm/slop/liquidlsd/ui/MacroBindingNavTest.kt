package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** "Go to target" in the macro binding inspector must land on the Deep Edit module/tab that shows the parameter. */
class MacroBindingNavTest {

    private fun nav(path: String) = MacroBindingInspector.navTargetFor(path)

    @Test
    fun deckSourceParamsOpenDeckSrc() {
        assertEquals(MacroBindingInspector.NavTarget(MacroEngine.DECK_A, "Deck A", "SRC"), nav("Deck A/fbZoom"))
        assertEquals(MacroBindingInspector.NavTarget(MacroEngine.DECK_BG, "Deck BG", "SRC"), nav("Deck BG/Mandala/L1"))
    }

    @Test
    fun deckFxParamsOpenDeckFx() {
        assertEquals(MacroBindingInspector.NavTarget(MacroEngine.DECK_PV, "Deck PV", "FX"), nav("Deck PV/FX/Slot1/amount"))
    }

    @Test
    fun mixerParamsOpenMasterOnMatchingSubTab() {
        assertEquals(MacroBindingInspector.NavTarget(MacroEngine.MASTER, "Mixer", "CTRL"), nav("Mixer/crossfade"))
        assertEquals(MacroBindingInspector.NavTarget(MacroEngine.MASTER, "Mixer", "TRANS"), nav("Mixer/Transition/DryWet"))
        assertEquals(MacroBindingInspector.NavTarget(MacroEngine.MASTER, "Mixer", "FX"), nav("Master/FX/DryWet"))
    }

    @Test
    fun unknownPathsHaveNoTarget() {
        assertNull(nav("crossfade"))
        assertNull(nav("FX1/DryWet"))
        assertNull(nav("MFX/DryWet"))
    }
}
