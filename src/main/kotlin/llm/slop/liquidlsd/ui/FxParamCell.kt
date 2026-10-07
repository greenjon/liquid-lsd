package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.parameters.ModulatableParameter
import java.util.Locale

/**
 * An individual shader parameter's control cell, drawn under Knobs 2-4 on FX rows when in
 * Focus Mode (matching [FxSlotCell.HEIGHT] = 20px so matrix row geometry never shifts):
 *
 *   `Parameter Name  ●`
 *
 * Mirrors [FxSlotCell]'s "name cell" shape so a focused knob's footprint matches a group-mode
 * slot knob's -- the current value is drawn inside the knob face itself ([MacroKnobWidget]'s
 * `valueOverlay`) and the reset control lives in the left-side button stack ([drawResetButton]),
 * keeping this cell free for just the name.
 *
 * - **●**: accent dot shown if this parameter has active modulators.
 * - Tooltip displays parameter name, value, default, min/max clamp, and modulator status.
 */
object FxParamCell {

    const val HEIGHT = 20f
    private const val RESET_GLYPH = Icons.ROTATE_CCW

    /**
     * Draws the parameter's name cell at ([x], [y]) with width [w].
     *
     * @param bankId The FX bank id (e.g. `deckA_fx`, `masterFx`).
     * @param knobIndex The 1-based knob index on the row (2, 3, or 4).
     * @param paramName Authored shader parameter key (e.g. "intensity", "speed"), or null if blank.
     * @param param The live [ModulatableParameter], or null if blank.
     * @param accent Accent color for modulation indicator and hover states.
     */
    fun draw(
        session: SessionContext,
        bankId: String,
        knobIndex: Int,
        paramName: String?,
        param: ModulatableParameter?,
        x: Float,
        y: Float,
        w: Float,
        accent: FloatArray
    ) {
        val dl = ImGui.getWindowDrawList()
        val h = HEIGHT
        val idBase = "fxparam_${bankId}_k${knobIndex}"

        val bgAlpha = if (param == null) 0.20f else 0.40f
        dl.addRectFilled(x, y, x + w, y + h, TangoPalette.u32(TangoPalette.FX_CELL_BG_RGB, bgAlpha), 4f)

        if (param == null || paramName == null) {
            // Blank / unused knob on this page
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                val placeholder = "—"
                val tw = ImGui.calcTextSize(placeholder).x
                val col = TangoPalette.FX_PLACEHOLDER.u32()
                dl.addText(x + (w - tw) * 0.5f, TextFit.centeredY(y, h, ImGui.getTextLineHeight()), col, placeholder)
            }
            return
        }

        val hasModulation = param.hasActiveModulator()
        val modDotW = if (hasModulation) 10f else 0f
        val nameW = (w - modDotW).coerceAtLeast(10f)

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val shown = TextFit.ellipsize(paramName, nameW - 4f)
            val tw = ImGui.calcTextSize(shown).x
            val textCol = TangoPalette.FX_PARAM_NAME.u32()
            dl.addText(x + (nameW - tw) * 0.5f, TextFit.centeredY(y, h, ImGui.getTextLineHeight()), textCol, shown)
        }

        // Invisible button over the whole cell for the tooltip
        ImGui.setCursorScreenPos(x, y)
        ImGui.invisibleButton("##val_$idBase", w, h)
        itemTooltip(
            "$paramName\n" +
            "Value: ${String.format(Locale.ROOT, "%.3f", param.baseValue)}\n" +
            "Range: [${formatVal(param.minClamp)}, ${formatVal(param.maxClamp)}]\n" +
            "Default: ${formatVal(param.defaultValue)}" +
            if (hasModulation) "\nModulated by active CV" else ""
        )

        // Modulation dot indicator
        if (hasModulation) {
            val dotX = x + w - 6f
            val dotY = y + h * 0.5f
            val modCol = TangoPalette.u32(accent, 0.90f)
            dl.addCircleFilled(dotX, dotY, 2.5f, modCol, 8)
        }
    }

    /**
     * Square reset-to-default button for a focused parameter, drawn at ([x], [y]) in the knob's
     * left-side button stack (mirroring [FxSlotCell.drawBypassButton]'s slot).
     */
    fun drawResetButton(bankId: String, knobIndex: Int, paramName: String, param: ModulatableParameter, x: Float, y: Float, size: Float) {
        val range = param.maxClamp - param.minClamp
        val isModified = kotlin.math.abs(param.baseValue - param.defaultValue) > 0.001f
        ImGui.setCursorScreenPos(x, y)
        val (bg, bgHover, text) = when {
            isModified -> Triple(
                TangoPalette.FX_RESET_BG.u32(),
                TangoPalette.FX_RESET_HOVER.u32(),
                TangoPalette.FX_RESET_TEXT.u32()
            )
            else -> Triple(
                TangoPalette.FX_BTN_IDLE_BG.u32(),
                TangoPalette.FX_BTN_IDLE_HOVER.u32(),
                TangoPalette.FX_BTN_IDLE_TEXT.u32()
            )
        }
        ImGui.pushStyleColor(ImGuiCol.Button, bg)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, bgHover)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, bgHover)
        ImGui.pushStyleColor(ImGuiCol.Text, text)
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 1f, 1f)
        if (ButtonChrome.button("$RESET_GLYPH##fxparam_reset_${bankId}_k$knobIndex", size, size)) {
            param.baseValue = param.defaultValue
            MacroEngine.getBank(bankId)?.knobs?.getOrNull(knobIndex - 1)?.let { control ->
                val normVal = if (range > 0f) ((param.baseValue - param.minClamp) / range).coerceIn(0f, 1f) else 0f
                control.value = normVal
            }
        }
        ImGui.popStyleVar()
        ImGui.popStyleColor(4)
        itemTooltip(
            if (isModified) "Reset $paramName to default (${formatVal(param.defaultValue)})"
            else "$paramName is at default (${formatVal(param.defaultValue)})"
        )
    }

    private fun formatVal(v: Float): String {
        return if (v == v.toInt().toFloat() && kotlin.math.abs(v) < 1000f) {
            v.toInt().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", v)
        }
    }
}
