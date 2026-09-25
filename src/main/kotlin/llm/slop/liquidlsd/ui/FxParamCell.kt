package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.parameters.ModulatableParameter
import java.util.Locale

/**
 * An individual shader parameter's control cell, drawn under Knobs 2-4 on FX rows when in
 * Focus Mode (matching [FxSlotCell.HEIGHT] = 20px so matrix row geometry never shifts):
 *
 *   `[⟲]  0.75  ●`
 *
 * - **⟲**: resets this parameter's baseValue to its authored default.
 * - **Value**: formatted base value readout.
 * - **●**: accent dot shown if this parameter has active modulators.
 * - Tooltip displays parameter name, value, default, min/max clamp, and modulator status.
 */
object FxParamCell {

    const val HEIGHT = 20f
    private const val RESET_BTN_W = 16f
    private const val RESET_GLYPH = "⟲"

    /**
     * Draws the parameter cell at ([x], [y]) with width [w].
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
        dl.addRectFilled(x, y, x + w, y + h, ImGui.colorConvertFloat4ToU32(0.08f, 0.09f, 0.11f, bgAlpha), 4f)

        if (param == null || paramName == null) {
            // Blank / unused knob on this page
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val placeholder = "—"
                val tw = ImGui.calcTextSize(placeholder).x
                val th = ImGui.getTextLineHeight()
                val col = ImGui.colorConvertFloat4ToU32(0.40f, 0.42f, 0.48f, 0.6f)
                dl.addText(x + (w - tw) * 0.5f, y + (h - th) * 0.5f, col, placeholder)
            }
            return
        }

        val range = param.maxClamp - param.minClamp
        val isModified = kotlin.math.abs(param.baseValue - param.defaultValue) > 0.001f
        val hasModulation = param.hasActiveModulator()

        // 1. [⟲] Reset button
        ImGui.setCursorScreenPos(x, y)
        if (ImGui.invisibleButton("##reset_$idBase", RESET_BTN_W, h)) {
            param.baseValue = param.defaultValue
            // Update the macro bank knob value to match
            MacroEngine.getBank(bankId)?.knobs?.getOrNull(knobIndex - 1)?.let { control ->
                val normVal = if (range > 0f) ((param.baseValue - param.minClamp) / range).coerceIn(0f, 1f) else 0f
                control.value = normVal
            }
        }
        val resetHovered = ImGui.isItemHovered()
        val resetCol = when {
            resetHovered -> ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f)
            isModified -> ImGui.colorConvertFloat4ToU32(0.85f, 0.70f, 0.30f, 0.90f)
            else -> ImGui.colorConvertFloat4ToU32(0.45f, 0.48f, 0.55f, 0.60f)
        }
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val sz = ImGui.calcTextSize(RESET_GLYPH)
            dl.addText(x + (RESET_BTN_W - sz.x) * 0.5f, y + (h - sz.y) * 0.5f, resetCol, RESET_GLYPH)
        }
        itemTooltip(
            if (isModified) "Reset $paramName to default (${formatVal(param.defaultValue)})"
            else "$paramName is at default (${formatVal(param.defaultValue)})"
        )

        // 2. Value readout
        val valX = x + RESET_BTN_W
        val modDotW = if (hasModulation) 10f else 0f
        val valW = (w - RESET_BTN_W - modDotW).coerceAtLeast(10f)

        val valStr = formatVal(param.baseValue)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val tw = ImGui.calcTextSize(valStr).x
            val th = ImGui.getTextLineHeight()
            val textCol = ImGui.colorConvertFloat4ToU32(0.85f, 0.88f, 0.92f, 0.95f)
            dl.addText(valX + (valW - tw) * 0.5f, y + (h - th) * 0.5f, textCol, valStr)
        }

        // Invisible button over value area for tooltip
        ImGui.setCursorScreenPos(valX, y)
        ImGui.invisibleButton("##val_$idBase", valW + modDotW, h)
        itemTooltip(
            "$paramName\n" +
            "Value: ${String.format(Locale.ROOT, "%.3f", param.baseValue)}\n" +
            "Range: [${formatVal(param.minClamp)}, ${formatVal(param.maxClamp)}]\n" +
            "Default: ${formatVal(param.defaultValue)}" +
            if (hasModulation) "\nModulated by active CV" else ""
        )

        // 3. Modulation dot indicator
        if (hasModulation) {
            val dotX = x + w - 6f
            val dotY = y + h * 0.5f
            val modCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.90f)
            dl.addCircleFilled(dotX, dotY, 2.5f, modCol, 8)
        }
    }

    private fun formatVal(v: Float): String {
        return if (v == v.toInt().toFloat() && kotlin.math.abs(v) < 1000f) {
            v.toInt().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", v)
        }
    }
}
