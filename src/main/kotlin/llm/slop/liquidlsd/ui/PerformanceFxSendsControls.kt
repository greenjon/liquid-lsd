package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.macro.MacroEngine

/** FX WET/DRY row right-side control: Resync. (The WET/DRY title badge is drawn by [PerformanceMatrixPanel].) */
internal object PerformanceFxSendsControls {

    fun drawRightControls(
        startX: Float,
        startY: Float,
        ctrlH: Float,
        width: Float
    ) {
        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        if (ImGui.button("Resync##perf_fxsends_resync", width, ctrlH)) {
            val bank = MacroEngine.getBank(MacroEngine.FX_SENDS)
            bank?.knobs?.forEach { knob ->
                knob.value = 1.0f
            }
        }
        itemTooltip("Reset all FX Wet/Dry Send knobs to 100%.")

        ImGui.endGroup()
    }
}
