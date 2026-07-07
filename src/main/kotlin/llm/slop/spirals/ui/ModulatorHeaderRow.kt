package llm.slop.spirals.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.ModulationOperator

object ModulatorHeaderRow {
    private val operatorLabels = arrayOf("ADD", "MUL", "SCALE")

    fun draw(
        state: PatchGridState,
        param: ModulatableParameter,
        existing: CvModulator,
        idx: Int,
        modsToDraw: List<CvModulator>,
        activeMods: List<CvModulator>,
        isVirtual: Boolean,
        isLfo: Boolean,
        hasAdvanced: Boolean,
        onReplace: (CvModulator) -> Unit,
        onReset: () -> Unit
    ) {
        val bypassed = existing.bypassed
        val currentThemeColor = CvTheme.getThemeColor(existing.sourceId)
        
        // Draw background panel for modulator
        if (bypassed) {
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)
        }
        
        val bandLabel = when (existing.sourceId) {
            "audio_amp" -> "Amplitude"
            "audio_bass" -> "Low"
            "audio_mid" -> "Mid"
            "audio_high" -> "High"
            "trigger_onset" -> "Onset / Transient"
            "trigger_accent" -> "Accent / Peak"
            else -> null
        }
        val titleText = bandLabel ?: if (modsToDraw.size > 1) {
            val typeLabel = if (isLfo) "LFO" else if (hasAdvanced) "Oscillator" else "Modulator"
            "$typeLabel ${idx + 1}"
        } else {
            val typeLabel = if (isLfo) "LFO" else if (hasAdvanced) "Oscillator" else "Modulator"
            typeLabel
        }

        ImGui.indent(10f) // Indent controls slightly

        val btnHeight = ImGui.getFrameHeight()
        val scale = btnHeight / 30f
        val btnWidth = 50f * scale

        if (bypassed) {
            ImGui.popStyleVar() // Draw header controls at full opacity
        }

        // 1. Power icon (Active/Bypass button)
        val btnY2 = ImGui.getCursorScreenPosY()
        
        if (UITheme.iconButton("##bypass_bar_$idx", Icons.POWER, if (bypassed) "Enable modulator (Active)" else "Bypass modulator", active = !bypassed, width = btnWidth, height = btnHeight)) {
            onReplace(existing.copy(bypassed = !bypassed))
        }

        // 2. Dice icon (Randomize button)
        ImGui.sameLine(0f, 10f)
        if (UITheme.iconButton("##rand_bar_$idx", Icons.DICES, "Randomize primary LFO / modulator values", width = btnWidth, height = btnHeight)) {
            val randomized = existing
                .randomizeAmplitude()
                .randomizeDcOffset()
                .randomizeSubdivision()
                .randomizePhaseOffset()
                .randomizeSlope()
            onReplace(randomized)
        }
        
        // 3. Operator dropdown (ADD/MUL/SCALE combo box)
        ImGui.sameLine(0f, 10f)
        val opIdx = ImInt(when (existing.operator) {
            ModulationOperator.ADD -> 0
            ModulationOperator.MUL -> 1
            ModulationOperator.SCALE -> 2
        })
        ImGui.pushItemWidth(100f)
        if (ImGui.combo("##op", opIdx, operatorLabels)) {
            val newOp = when (opIdx.get()) {
                0 -> ModulationOperator.ADD
                1 -> ModulationOperator.MUL
                else -> ModulationOperator.SCALE
            }
            onReplace(existing.copy(operator = newOp))
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Modulation Operator:\nADD: Modulator value is added to parameter's base.\nMUL: Modulator multiplies the base value.\nSCALE: Modulator scales the remaining range.")
        }
        ImGui.popItemWidth()

        // 4. Title Text (vertically centered in the row, left-aligned)
        ImGui.sameLine(0f, 115f)
        val alignY = btnY2 + (btnHeight - ImGui.getTextLineHeightWithSpacing()) / 2f
        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), alignY)
        UITheme.h2(titleText)

        // 5. Reset button (trash can icon)
        if (idx == 0) {
            val resetWidth = 50f * scale
            ImGui.sameLine(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - resetWidth)
            if (isVirtual) {
                ImGui.beginDisabled()
            }
            
            if (UITheme.iconButton("##reset_bar_$idx", Icons.TRASH, "Clear/reset modulators", width = resetWidth, height = btnHeight)) {
                onReset()
                if (isVirtual) {
                    ImGui.endDisabled()
                }
                ImGui.unindent(10f)
                return
            }
            if (isVirtual) {
                ImGui.endDisabled()
            }
        }
    }
}
