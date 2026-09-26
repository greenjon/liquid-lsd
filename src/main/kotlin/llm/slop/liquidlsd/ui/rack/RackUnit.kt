package llm.slop.liquidlsd.ui.rack

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.itemTooltip

/**
 * Shared, stateless drawing helpers for the Modular Rack's disclosure-tier chevron and its
 * scrollable Deep-Edit content frame (see docs/user_guide/macros_and_rack.md).
 *
 * Per-module rack content (DeckRackUnit, FxRackUnit, MasterRackUnit) lives alongside
 * [llm.slop.liquidlsd.ui.PerformanceMatrixPanel], which owns the fixed-height Tier-1 faceplate
 * grid; this object only draws the chevron affordance and the Deep-Edit scroll region that
 * appears beneath that grid. Disclosure changes here call [ParametersState.setDisclosure] only --
 * never any FX focus-switch or FxMacroSync re-run path, per the focus-swap decoupling rule.
 */
object RackUnit {

    /** Toggles COLLAPSED <-> DEEP_EDIT. */
    fun nextLevel(level: ParametersState.DisclosureLevel): ParametersState.DisclosureLevel = when (level) {
        ParametersState.DisclosureLevel.COLLAPSED -> ParametersState.DisclosureLevel.DEEP_EDIT
        ParametersState.DisclosureLevel.DEEP_EDIT -> ParametersState.DisclosureLevel.COLLAPSED
    }

    /**
     * Draws an "Edit" / "Collapse" toggle button at the current ImGui cursor that cycles
     * [moduleId]'s disclosure tier. Caller is responsible for positioning the cursor first (e.g.
     * `ImGui.setCursorScreenPos(...)`).
     */
    fun drawChevron(parametersState: ParametersState, moduleId: String, idSuffix: String) {
        val level = parametersState.disclosureFor(moduleId)
        val isExpanded = level != ParametersState.DisclosureLevel.COLLAPSED
        val label = if (isExpanded) "Collapse" else "Edit"
        if (isExpanded) {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.15f, 0.62f, 0.82f, 1f))
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 0.85f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.24f, 0.27f, 0.32f, 1f))
        }
        if (ImGui.smallButton("$label##rack_chevron_$idSuffix")) {
            parametersState.setDisclosure(moduleId, nextLevel(level))
        }
        ImGui.popStyleColor(2)
        itemTooltip(
            if (isExpanded) "Collapse module back to standard row view." else "Expand Deep Edit (full parameter editor)."
        )
    }

    /**
     * Persistent one-line indicator for an armed Macro Learn, shown outside all Rack Units (e.g.
     * in the toolbar) so the state is never invisible even if the owning module's Deep Edit was
     * collapsed by other means. Returns true if it drew anything.
     */
    fun drawLearnIndicator(): Boolean {
        val session = MacroLearnState.activeSession ?: return false
        val label = MacroLearnState.findControl(session.controlId)?.label?.ifEmpty { "Knob" } ?: "Knob"
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(1f, 0.75f, 0.15f, 1f))
        ImGui.text("${llm.slop.liquidlsd.ui.Icons.REFRESH} Learning: $label -- Esc to cancel")
        ImGui.popStyleColor()
        return true
    }
}
