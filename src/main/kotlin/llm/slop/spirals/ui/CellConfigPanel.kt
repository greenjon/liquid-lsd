package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiColorEditFlags
import imgui.type.ImInt
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.LfoSpeedMode
import llm.slop.spirals.parameters.ModulationOperator
import llm.slop.spirals.parameters.Waveform

/**
 * Draws the Cell Config panel contents.
 * Call this inside an ImGui.begin("Cell Config") / ImGui.end() block.
 */
object CellConfigPanel {

    private val subdivisionOptions = floatArrayOf(
        0.0625f, 0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f
    )
    private val subdivisionLabels = arrayOf(
        "1/16", "1/8", "1/4", "1/2", "1", "2", "4", "8", "16", "32", "64", "128", "256"
    )
    private val waveformLabels = arrayOf("Sine", "Triangle", "Square")
    private val speedLabels = arrayOf("Slow", "Medium", "Fast")
    private val operatorLabels = arrayOf("ADD", "MUL")

    fun draw(state: PatchGridState) {
        val cell = state.selectedCell
        val param = state.selectedParam

        if (cell == null || param == null) {
            ImGui.textDisabled("Click a cell in the Patch Grid to configure it.")
            return
        }

        val cvId = cell.cvSourceId
        val paramKey = cell.paramKey
        val isBeat = cvId == "beatPhase"
        val isLfo = cvId == "lfo"
        val isSnh = cvId == "sampleAndHold"
        val hasAdvanced = isBeat || isLfo || isSnh

        ImGui.textColored(0.4f, 0.9f, 1.0f, 1.0f, paramKey)
        ImGui.sameLine()
        ImGui.textDisabled("  <--  $cvId")
        ImGui.separator()
        ImGui.spacing()

        val existing = state.editingModulator

        if (existing == null) {
            // Empty cell: offer to create
            ImGui.textDisabled("No patch at this intersection.")
            ImGui.spacing()
            if (ImGui.button("+ Add Patch", ImGui.getContentRegionAvailX(), 28f)) {
                val newMod = CvModulator(sourceId = cvId)
                param.modulators.add(newMod)
                state.editingModulator = newMod
            }
            return
        }

        // ── Bypass / Delete buttons ──────────────────────────────
        val bypassed = existing.bypassed
        val bypassLabel = if (bypassed) "BYPASSED" else "ACTIVE"
        if (bypassed) ImGui.pushStyleColor(0, 0.5f, 0.5f, 0.5f, 1f)
        else ImGui.pushStyleColor(0, 0.2f, 0.8f, 0.4f, 1f)
        if (ImGui.button(bypassLabel, 100f, 24f)) {
            replaceModulator(state, param, existing.copy(bypassed = !bypassed))
        }
        ImGui.popStyleColor()

        ImGui.sameLine()
        ImGui.pushStyleColor(0, 0.8f, 0.2f, 0.2f, 1f)
        if (ImGui.button("Delete Patch", 120f, 24f)) {
            param.modulators.remove(existing)
            state.editingModulator = null
        }
        ImGui.popStyleColor()

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ── Operator (ADD / MUL) ─────────────────────────────────
        ImGui.text("Operator")
        val opIdx = ImInt(if (existing.operator == ModulationOperator.ADD) 0 else 1)
        ImGui.pushItemWidth(120f)
        if (ImGui.combo("##op", opIdx, operatorLabels)) {
            val newOp = if (opIdx.get() == 0) ModulationOperator.ADD else ModulationOperator.MUL
            replaceModulator(state, param, existing.copy(operator = newOp))
        }
        ImGui.popItemWidth()
        ImGui.spacing()

        // ── Weight ───────────────────────────────────────────────
        ImGui.text("Weight")
        val weight = floatArrayOf(existing.weight)
        ImGui.pushItemWidth(-1f)
        if (ImGui.sliderFloat("##weight", weight, -1f, 1f, "%.3f")) {
            replaceModulator(state, param, existing.copy(weight = weight[0]))
        }
        ImGui.popItemWidth()
        ImGui.spacing()

        if (!hasAdvanced) return

        // ── Waveform (Beat / LFO only) ───────────────────────────
        if (!isSnh) {
            ImGui.text("Waveform")
            val wfIdx = ImInt(existing.waveform.ordinal)
            ImGui.pushItemWidth(120f)
            if (ImGui.combo("##waveform", wfIdx, waveformLabels)) {
                replaceModulator(state, param, existing.copy(waveform = Waveform.entries[wfIdx.get()]))
            }
            ImGui.popItemWidth()
            ImGui.spacing()
        }

        // ── Subdivision (Beat / S&H) ─────────────────────────────
        if (isBeat || isSnh) {
            ImGui.text("Subdivision")
            val currentSubIdx = subdivisionOptions.indexOfFirst { it == existing.subdivision }.coerceAtLeast(4)
            val subIdx = ImInt(currentSubIdx)
            ImGui.pushItemWidth(100f)
            if (ImGui.combo("##subdiv", subIdx, subdivisionLabels)) {
                replaceModulator(state, param, existing.copy(subdivision = subdivisionOptions[subIdx.get()]))
            }
            ImGui.popItemWidth()
            ImGui.spacing()
        }

        // ── LFO Period / Speed ───────────────────────────────────
        if (isLfo) {
            ImGui.text("Speed Range")
            val speedIdx = ImInt(existing.lfoSpeedMode.ordinal)
            ImGui.pushItemWidth(100f)
            if (ImGui.combo("##speed", speedIdx, speedLabels)) {
                replaceModulator(state, param, existing.copy(lfoSpeedMode = LfoSpeedMode.entries[speedIdx.get()]))
            }
            ImGui.popItemWidth()

            val period = floatArrayOf(existing.subdivision)
            val periodLabel = when (existing.lfoSpeedMode) {
                LfoSpeedMode.FAST -> "Period: %.2fs".format(existing.subdivision * 10.0)
                LfoSpeedMode.MEDIUM -> {
                    val s = (existing.subdivision * 900).toInt()
                    "Period: %02dm:%02ds".format(s / 60, s % 60)
                }
                LfoSpeedMode.SLOW -> {
                    val m = (existing.subdivision * 1440).toInt()
                    "Period: %02dh:%02dm".format(m / 60, m % 60)
                }
            }
            ImGui.text(periodLabel)
            ImGui.pushItemWidth(-1f)
            if (ImGui.sliderFloat("##lfosub", period, 0.001f, 1f, "%.3f")) {
                replaceModulator(state, param, existing.copy(subdivision = period[0]))
            }
            ImGui.popItemWidth()
            ImGui.spacing()
        }

        // ── Phase Offset ─────────────────────────────────────────
        ImGui.text("Phase Offset")
        val phase = floatArrayOf(existing.phaseOffset)
        ImGui.pushItemWidth(-1f)
        if (ImGui.sliderFloat("##phase", phase, 0f, 1f, "%.3f")) {
            replaceModulator(state, param, existing.copy(phaseOffset = phase[0]))
        }
        ImGui.popItemWidth()
        ImGui.spacing()

        // ── Slope / Duty (Triangle, Square, S&H) ────────────────
        val needsSlope = isSnh ||
                existing.waveform == Waveform.TRIANGLE ||
                existing.waveform == Waveform.SQUARE
        if (needsSlope) {
            val slopeLabel = when {
                isSnh -> "Glide"
                existing.waveform == Waveform.TRIANGLE -> "Slope"
                else -> "Duty Cycle"
            }
            ImGui.text(slopeLabel)
            val slope = floatArrayOf(existing.slope)
            ImGui.pushItemWidth(-1f)
            if (ImGui.sliderFloat("##slope", slope, 0f, 1f, "%.3f")) {
                replaceModulator(state, param, existing.copy(slope = slope[0]))
            }
            ImGui.popItemWidth()
            ImGui.spacing()
        }

        // ── Live value bar ───────────────────────────────────────
        ImGui.spacing()
        ImGui.separator()
        val liveVal = param.value
        ImGui.text("Live Value: %.3f".format(liveVal))
        val barW = ImGui.getContentRegionAvailX()
        val dl = ImGui.getWindowDrawList()
        val cx = ImGui.getCursorScreenPosX()
        val cy = ImGui.getCursorScreenPosY()
        dl.addRectFilled(cx, cy, cx + barW, cy + 8f, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
        dl.addRectFilled(cx, cy, cx + barW * liveVal.coerceIn(0f, 1f), cy + 8f,
            ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 1f))
        ImGui.dummy(barW, 8f)
    }

    private fun replaceModulator(state: PatchGridState, param: llm.slop.spirals.parameters.ModulatableParameter, newMod: CvModulator) {
        val idx = param.modulators.indexOfFirst { it.sourceId == newMod.sourceId }
        if (idx >= 0) param.modulators[idx] = newMod
        state.editingModulator = newMod
    }
}
