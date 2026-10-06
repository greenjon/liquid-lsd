package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.macro.MacroEngine

/**
 * The outline a row draws around the slot the dock is bound to (its SRC badge, chain name, FX slot cell or transition name),
 * in the row's own colour, so you can see what you are changing while you browse it.
 */
internal object DockOutline {
    private fun canonical(bankId: String): String = when (bankId) {
        MacroEngine.DECK_A_FX -> MacroEngine.DECK_A
        MacroEngine.DECK_B_FX -> MacroEngine.DECK_B
        MacroEngine.DECK_BG_FX -> MacroEngine.DECK_BG
        MacroEngine.DECK_PV_FX -> MacroEngine.DECK_PV
        MacroEngine.MASTER_FX, MacroEngine.TRANS, "Mixer" -> MacroEngine.MASTER
        else -> bankId
    }

    private fun colorFor(bankId: String): FloatArray = when (canonical(bankId)) {
        MacroEngine.DECK_A -> PerformanceColors.COLOR_DECK_A
        MacroEngine.DECK_B -> PerformanceColors.COLOR_DECK_B
        MacroEngine.DECK_BG -> PerformanceColors.COLOR_DECK_BG
        MacroEngine.DECK_PV -> PerformanceColors.COLOR_DECK_PV
        else -> PerformanceColors.COLOR_MASTER
    }

    /** True when the dock is bound to [target] on the row behind [bankId] (a deck's source or FX bank, or Master). */
    fun selects(parametersState: ParametersState, bankId: String, target: ParametersState.BrowseTarget): Boolean =
        parametersState.dockSelection == ParametersState.DockSelection(canonical(bankId), target)

    /** Outlines the rectangle ([x0],[y0])-([x1],[y1]) in the colour of [bankId]'s row. */
    fun draw(bankId: String, x0: Float, y0: Float, x1: Float, y1: Float) {
        ImGui.getWindowDrawList().addRect(x0 - 1f, y0 - 1f, x1 + 1f, y1 + 1f, TangoPalette.u32(colorFor(bankId)), 4f, 0, 2f)
    }

    /** Outlines the item just submitted. */
    fun drawAroundLastItem(bankId: String) =
        draw(bankId, ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY())
}
