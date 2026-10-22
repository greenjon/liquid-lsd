package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.FxChain

/**
 * Describes an FX bank's fixed knob mapping ([llm.slop.liquidlsd.macro.FxMacroSync]); the Edit-row
 * [PerformanceMacroStrip] shows it read-only because FX knobs are not user-bindable.
 */
object FxMacroSummary {

    /** What FX knob [i] does in [chain]'s current mode (group vs. focused slot). */
    fun knobRole(chain: FxChain, i: Int, control: llm.slop.liquidlsd.macro.MacroControl): String {
        val focused = chain.focusedSlot
        return when {
            focused == null && i == 0 -> "Chain Super Knob"
            focused == null -> "Slot $i Metaknob" + linkNote(chain, i - 1)
            i == 0 -> "Slot ${focused + 1} Metaknob" + linkNote(chain, focused)
            else -> if (control.bindings.isEmpty()) "(unused on this page)" else "Slot ${focused + 1} parameter"
        }
    }

    private fun linkNote(chain: FxChain, slot: Int): String = when {
        chain.slotSuperKnobLink.getOrNull(slot) != true -> ""
        chain.isAwaitingPickup(slot) -> " (linked to Super, waiting for pickup)"
        else -> " (linked to Super)"
    }
}
