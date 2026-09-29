package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMapModeState

/**
 * Renders [OscLearnState]'s status banner as a borderless top-center overlay, visible from any
 * view -- not just the OSC Preferences panel where the banner was previously confined. Armed
 * right-click "Learn OSC" from a slider deep in Deep Edit was otherwise "fire and forget" with no
 * feedback anywhere on screen (see osc_ux_analysis.md item #2).
 *
 * Also doubles as the persistent indicator for [OscMapModeState]: while Map Mode is active but no
 * specific control is currently armed, it falls back to a standing "click a control to bind"
 * banner instead of disappearing between binds.
 */
object OscLearnStatusOverlay {
    fun draw(displayWidth: Float, displayHeight: Float) {
        val status = OscLearnState.getActiveStatus()
            ?: if (OscMapModeState.active) "OSC MAP MODE: click a control to bind it. Ctrl+Shift+O to exit." else null
            ?: return

        val flags = ImGuiWindowFlags.NoDecoration or ImGuiWindowFlags.NoInputs or
            ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.NoFocusOnAppearing or
            ImGuiWindowFlags.NoNav or ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoMove

        ImGui.setNextWindowPos(displayWidth * 0.5f, 44f, ImGuiCond.Always, 0.5f, 0f)
        ImGui.setNextWindowBgAlpha(0.92f)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Border, TangoPalette.u32(TangoPalette.ALERT.normal, 0.9f))
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowBorderSize, 1.5f)
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 6f)
        if (ImGui.begin("##osc_learn_status_overlay", flags)) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, TangoPalette.u32(TangoPalette.ALERT.normal))
            ImGui.text("${Icons.ACTIVITY} $status")
            ImGui.popStyleColor()
        }
        ImGui.end()
        ImGui.popStyleVar(2)
        ImGui.popStyleColor()
    }
}
