package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLearnState
import kotlin.test.*

class MacroStripVisibilityTest {
    private fun mode(edit: Boolean, row: String, sel: String?) = macroStripModeFor(edit, row, sel)

    @Test
    fun perform_view_never_shows_strip() {
        assertEquals(MacroStripMode.NONE, mode(false, MacroEngine.DECK_A, MacroEngine.DECK_A))
        assertEquals(MacroStripMode.NONE, mode(false, MacroEngine.DECK_A, MacroEngine.GLOBAL))
    }

    @Test
    fun nothing_selected_shows_none() {
        assertEquals(MacroStripMode.NONE, mode(true, MacroEngine.DECK_A, null))
    }

    @Test
    fun deck_src_and_fx_are_separate_banks() {
        assertEquals(MacroStripMode.STRIP, mode(true, MacroEngine.DECK_A, MacroEngine.DECK_A))
        assertEquals(MacroStripMode.NONE, mode(true, MacroEngine.DECK_A, MacroEngine.DECK_A_FX))
        assertEquals(MacroStripMode.STRIP, mode(true, MacroEngine.DECK_A_FX, MacroEngine.DECK_A_FX))
        assertEquals(MacroStripMode.NONE, mode(true, MacroEngine.DECK_A_FX, MacroEngine.DECK_A))
        assertEquals(MacroStripMode.NONE, mode(true, MacroEngine.DECK_B, MacroEngine.DECK_A))
    }

    @Test
    fun master_mix_and_fx() {
        assertEquals(MacroStripMode.STRIP, mode(true, MacroEngine.MASTER, MacroEngine.MASTER))
        assertEquals(MacroStripMode.NONE, mode(true, MacroEngine.MASTER_FX, MacroEngine.MASTER))
        assertEquals(MacroStripMode.STRIP, mode(true, MacroEngine.MASTER_FX, MacroEngine.MASTER_FX))
    }

    @Test
    fun global_visits_every_other_row() {
        assertEquals(MacroStripMode.GUEST, mode(true, MacroEngine.DECK_B, MacroEngine.GLOBAL))
        assertEquals(MacroStripMode.GUEST, mode(true, MacroEngine.MASTER_FX, MacroEngine.GLOBAL))
        assertEquals(MacroStripMode.STRIP, mode(true, MacroEngine.GLOBAL, MacroEngine.GLOBAL))
    }

    @Test
    fun selected_binding_index_clamps_when_bindings_are_removed() {
        val control = MacroControl()
        repeat(3) { control.bindings.add(MacroBinding(parameterId = "Deck A/p$it", targetType = llm.slop.liquidlsd.macro.MacroTargetType.PARAM_BASE_VALUE)) }
        MacroLearnState.selectBinding(control, 2)
        assertEquals(2, MacroLearnState.selectedBindingIdx(control))
        control.bindings.removeAt(2)
        assertEquals(1, MacroLearnState.selectedBindingIdx(control))
        control.bindings.clear()
        assertEquals(0, MacroLearnState.selectedBindingIdx(control))
        MacroLearnState.selectBinding(control, -4)
        assertEquals(0, MacroLearnState.selectedBindingIdx(control))
    }
}
