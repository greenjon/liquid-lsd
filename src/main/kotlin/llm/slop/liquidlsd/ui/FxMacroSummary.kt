package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.rendering.FxChain

/**
 * Read-only view of an FX bank's fixed knob mapping ([llm.slop.liquidlsd.macro.FxMacroSync]) for the
 * Macros tab's FX pages, plus each visible slot's Metaknob targets with a rebind menu. The FX knobs
 * themselves are not user-bindable, so there is no Binding Inspector here.
 */
object FxMacroSummary {

    /** What FX knob [i] does in [chain]'s current mode (group vs. focused slot). */
    fun knobRole(chain: FxChain, i: Int, control: llm.slop.liquidlsd.macro.MacroControl): String {
        val focused = chain.focusedSlot
        return when {
            focused == null && i == 0 -> "Chain Super Knob"
            focused == null -> if (chain.slotSuperKnobLink.getOrNull(i - 1) == true) "Slot $i Metaknob (linked to Super)" else "Slot $i Metaknob"
            i == 0 -> "Slot ${focused + 1} Metaknob"
            else -> if (control.bindings.isEmpty()) "(unused on this page)" else "Slot ${focused + 1} parameter"
        }
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank, chain: FxChain) {
        val focused = chain.focusedSlot
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled(if (focused != null) "KNOBS (FOCUS: SLOT ${focused + 1})" else "KNOBS (GROUP)")
        }
        ImGui.spacing()

        bank.knobs.forEachIndexed { i, control ->
            val what = knobRole(chain, i, control)
            ImGui.text("K${i + 1}  ${control.label.ifEmpty { "-" }}")
            ImGui.sameLine(110f)
            ImGui.textDisabled(what)
        }
        itemTooltip("FX knobs have a fixed layout per mode (Super + 3 Metaknobs, or Metaknob + 3 parameters when a slot is focused). Change what a Metaknob controls below.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("METAKNOB TARGETS") }
        val slots = if (focused != null) listOf(focused) else (0 until FxChain.SLOT_COUNT).toList()
        for (slotIdx in slots) {
            val fx = chain.slots.getOrNull(slotIdx)
            ImGui.pushID("fx_meta_summary_$slotIdx")
            if (fx == null) {
                ImGui.textDisabled("Slot ${slotIdx + 1}: empty")
            } else {
                val targets = fx.metaBindings.filter { it.enabled }.joinToString(", ") { b ->
                    val name = b.targetParamName ?: "Dry/Wet"
                    "%s %.2f-%.2f".format(name, b.minVal, b.maxVal)
                }.ifEmpty { "none" }
                ImGui.text("Slot ${slotIdx + 1}: ${fx.displayName}")
                ImGui.textDisabled("   -> $targets")
                ImGui.sameLine()
                if (ImGui.smallButton("Rebind")) ImGui.openPopup("rebind")
                pushOpenDropdownPadding()
                if (ImGui.beginPopup("rebind")) {
                    pushOpenDropdownFont()
                    ImGui.textDisabled("Rebind Metaknob")
                    ImGui.separator()
                    FXChainMacroStrip.drawRebindMenuItems(fx)
                    popOpenDropdownFont()
                    ImGui.endPopup()
                }
                popOpenDropdownPadding()
            }
            ImGui.popID()
        }
    }
}
