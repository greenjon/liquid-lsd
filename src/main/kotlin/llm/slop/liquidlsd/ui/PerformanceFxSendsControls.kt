package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine

/** FX WET/DRY row side controls: "FX SENDS" badge (left) and Resync (right). */
internal object PerformanceFxSendsControls {

    fun drawLeftControls(
        session: SessionContext,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val badgeW = 76f
        val accent = PerformanceColors.COLOR_FX
        val bgCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.20f)
        val borderCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.85f)
        val textCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.95f)

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(startX, startY, startX + badgeW, startY + ctrlH, bgCol, 4f)
        dl.addRect(startX, startY, startX + badgeW, startY + ctrlH, borderCol, 4f, 0, 1.5f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val label = "FX SENDS"
            val textSz = ImGui.calcTextSize(label)
            val tx = startX + (badgeW - textSz.x) * 0.5f
            val ty = startY + (ctrlH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(startX + 2f), ty, textCol, label)
        }
        ImGui.invisibleButton("##perf_fxsends_badge", badgeW, ctrlH)
        itemTooltip("Per-deck FX send levels (each deck's FX chain wet/dry).")

        ImGui.endGroup()
    }

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
