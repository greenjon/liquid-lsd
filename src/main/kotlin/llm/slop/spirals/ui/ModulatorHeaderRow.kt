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
        
        // Push styled button colors: Green for active, Red for bypassed
        val btnColor = if (bypassed) ImGui.colorConvertFloat4ToU32(0.7f, 0.2f, 0.2f, 1f) else ImGui.colorConvertFloat4ToU32(0.1f, 0.6f, 0.2f, 1f)
        val btnHoverColor = if (bypassed) ImGui.colorConvertFloat4ToU32(0.8f, 0.3f, 0.3f, 1f) else ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.3f, 1f)
        val btnActiveColor = if (bypassed) ImGui.colorConvertFloat4ToU32(0.9f, 0.4f, 0.4f, 1f) else ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 0.4f, 1f)
        
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, btnColor)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, btnHoverColor)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, btnActiveColor)
        
        if (ImGui.button("${Icons.POWER}##bypass_bar_$idx", btnWidth, btnHeight)) {
            onReplace(existing.copy(bypassed = !bypassed))
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip(if (bypassed) "Enable modulator (Active)" else "Bypass modulator")
        }
        ImGui.popStyleColor(3)

        // 2. Dice icon (Randomize button)
        ImGui.sameLine(0f, 10f)
        if (ImGui.button("${Icons.DICES}##rand_bar_$idx", btnWidth, btnHeight)) {
            val randomized = existing
                .randomizeAmplitude()
                .randomizeDcOffset()
                .randomizeSubdivision()
                .randomizePhaseOffset()
                .randomizeSlope()
            onReplace(randomized)
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Randomize primary LFO / modulator values")
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
            
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.8f, 0.2f, 0.2f, 1f)) // Red on hover
            if (ImGui.button("${Icons.TRASH}##reset_bar_$idx", resetWidth, btnHeight)) {
                onReset()
                ImGui.popStyleColor(2)
                if (isVirtual) {
                    ImGui.endDisabled()
                }
                ImGui.unindent(10f)
                return
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Clear/reset modulators")
            }
            ImGui.popStyleColor(2)
            if (isVirtual) {
                ImGui.endDisabled()
            }
        }
    }
}
