package llm.slop.spirals.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import imgui.flag.ImGuiKey
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.parameters.ParameterResolver
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.DynamicVisualSource
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.models.ClipboardManager
import llm.slop.spirals.models.CellClipboardData
import llm.slop.spirals.models.RowClipboardData
import llm.slop.spirals.models.toDto
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Draws the Patch Grid panel. Rows = grouped ModulatableParameters.
 * Columns = CV sources. Each intersection is a clickable cell.
 */
object PatchGridPanel {

    private fun getCvColumns(): List<String> {
        return if (UITheme.audioEngineEnabled) {
            listOf("gen1", /*"gen2",*/ "audio", "trigger")
        } else {
            listOf("gen1" /*, "gen2"*/)
        }
    }

    private fun getCvLabels(): List<String> {
        return if (UITheme.audioEngineEnabled) {
            listOf("LFO", /*"GEN 2",*/ "AUDIO", "TRIGGER")
        } else {
            listOf("LFO" /*, "GEN 2"*/)
        }
    }


    // Size of each cell square (px)
    private const val CELL = 35f
    private const val CELL_PAD = 5f
    private const val GROUP_GAP = 10f

    private fun getColumnOffset(colId: String): Float {
        // Build the visible column list dynamically
        val visibleCols = mutableListOf("final", "midi")
        visibleCols.addAll(getCvColumns())
        
        // Find the index of this column in the visible list
        val index = visibleCols.indexOf(colId)
        if (index < 0) return 0f
        
        // Calculate offset based on position in visible columns
        // Add GROUP_GAP after "midi" (between special columns and CV columns)
        val gapAfterMidi = if (index > 1) GROUP_GAP else 0f
        return index * (CELL + CELL_PAD) + gapAfterMidi
    }

    private fun getCvColor(colId: String, alpha: Float = 1f): Int {
        return when (colId) {
            // Special
            "final"          -> ImGui.colorConvertFloat4ToU32(0.0f, 1.0f, 0.7f, alpha)
            "base"           -> ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, alpha)
            "midi"           -> ImGui.colorConvertFloat4ToU32(0.7f, 0.3f, 1.0f, alpha)
            // Synthetic / Generators
            "gen1"           -> ImGui.colorConvertFloat4ToU32(0.0f, 0.7f, 1.0f, alpha)
            "gen2"           -> ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 0.7f, alpha)
            "lfo"            -> ImGui.colorConvertFloat4ToU32(0.0f, 0.7f, 1.0f, alpha)
            "sampleAndHold"  -> ImGui.colorConvertFloat4ToU32(0.7f, 0.4f, 1.0f, alpha)
            "beatPhase"      -> ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 1.0f, alpha)
            // Amplitude / Spectral
            "audio"          -> ImGui.colorConvertFloat4ToU32(0.3f, 0.9f, 0.0f, alpha)
            "amp"            -> ImGui.colorConvertFloat4ToU32(0.3f, 0.9f, 0.0f, alpha)
            "bass"           -> ImGui.colorConvertFloat4ToU32(0.9f, 0.2f, 0.2f, alpha)
            "mid"            -> ImGui.colorConvertFloat4ToU32(0.9f, 0.5f, 0.1f, alpha)
            "high"           -> ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.2f, alpha)
            // Transients / Triggers
            "trigger"        -> ImGui.colorConvertFloat4ToU32(1.0f, 0.0f, 0.5f, alpha)
            "onset"          -> ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.6f, alpha)
            "accent"         -> ImGui.colorConvertFloat4ToU32(0.9f, 0.1f, 0.9f, alpha)
            else             -> ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, alpha)
        }
    }

    private var gridStartX = 0f
    private var rowIndex = 0

    fun draw(mixer: Mixer, state: PatchGridState) {
        rowIndex = 0
        val avail = ImGui.getContentRegionAvailX()
        gridStartX = ImGui.getCursorScreenPosX()
        
        val lastVisibleCol = getCvColumns().lastOrNull() ?: "midi"
        val maxGridW = getColumnOffset(lastVisibleCol) + CELL + CELL_PAD * 0.5f
        val labelColW = (avail - maxGridW - 20f).coerceAtLeast(120f)

        PatchGridKeyboard.handleKeyboardShortcuts(state, mixer, { s, m -> PatchGridUndo.pushUndoState(s, m) }, { s, m -> PatchGridUndo.performUndo(s, m) })

        drawColumnHeaders(labelColW, state, mixer)
        ImGui.separator()
        ImGui.spacing()

        PatchGridTabs.drawSubTabs(state, mixer)
        ImGui.spacing()
        ImGui.spacing()

        if (ImGui.beginChild("##patch_grid_scroll", 0f, 0f, false)) {
            if (state.activeTopTab == "Mixer") {
                PatchGridTabs.drawSubGroupContent("Mixer", "Mixer", state) {
                    PatchGridRenderer.drawParamRow("Deck A Source", "Deck A/FB/Source",   mixer.deckA.sourceSelect, state, labelColW, mixer, gridStartX, 0, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("Deck B Source", "Deck B/FB/Source",   mixer.deckB.sourceSelect, state, labelColW, mixer, gridStartX, 1, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("crossfade",  "Mixer/crossfade",  mixer.crossfade,  state, labelColW, mixer, gridStartX, 2, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("master Alpha",   "Mixer/masterAlpha", mixer.masterAlpha, state, labelColW, mixer, gridStartX, 3, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("bloom",      "Mixer/bloom",       mixer.bloom,       state, labelColW, mixer, gridStartX, 4, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("fade speed",  "Mixer/xfadeSpeed",  mixer.xfadeSpeed,  state, labelColW, mixer, gridStartX, 5, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("queue prev", "Mixer/queuePrev", mixer.queuePrev, state, labelColW, mixer, gridStartX, 6, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("queue next", "Mixer/queueNext", mixer.queueNext, state, labelColW, mixer, gridStartX, 7, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                }
            } else if (state.activeTopTab == "Deck A") {
                PatchGridTabs.drawDeckGroupContent("Deck A", mixer.deckA, state, labelColW, mixer, gridStartX, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
            } else if (state.activeTopTab == "Deck B") {
                PatchGridTabs.drawDeckGroupContent("Deck B", mixer.deckB, state, labelColW, mixer, gridStartX, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
            } else if (state.activeTopTab == "Deck C") {
                PatchGridTabs.drawDeckGroupContent("Deck C", mixer.deckC, state, labelColW, mixer, gridStartX, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
            }
        }
        ImGui.endChild()
    }

    // -- Helpers --------------------------------------------------------------

    private fun drawColumnHeaders(labelColW: Float, state: PatchGridState, mixer: Mixer) {
        val dl = ImGui.getWindowDrawList()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val mousePos = ImGui.getIO().mousePos
        
        // Calculate the maximum height needed for the vertical labels
        var maxH = 0f
        val cvLabelsVertical = getCvLabels().map { it.toList().joinToString("\n") }
        UITheme.withFont(UITheme.FontLevel.CAPTION) {
            for (label in cvLabelsVertical) {
                val h = ImGui.calcTextSize(label).y
                if (h > maxH) maxH = h
            }
            val hFinal = ImGui.calcTextSize("F\nI\nN\nA\nL").y
            val hMidi = ImGui.calcTextSize("M\nI\nD\nI").y
            if (hFinal > maxH) maxH = hFinal
            if (hMidi > maxH) maxH = hMidi
        }
        
        val btnHeight = ImGui.getFrameHeight()
        val spacing = 4f
        val buttonsH = 4 * (btnHeight + spacing)
        val headerH = (maxH + 5f).coerceAtLeast(buttonsH + 28f)
        
        // Reserve vertical space for headers
        ImGui.dummy(10f, headerH)
        val afterHeadersY = ImGui.getCursorScreenPosY()

        val scale = btnHeight / 30f
        val btnWidth = 50f * scale

        // Draw the 3 randomize rows vertically
        drawRandomizeRow("rand_all", "Randomize all", startX, startY, btnWidth, btnHeight, scale) {
            PatchGridUndo.pushUndoState(state, mixer)
            mixer.deckA.randomizeModulators()
            mixer.deckB.randomizeModulators()
            mixer.deckC.randomizeModulators()
            listOf(mixer.crossfade, mixer.masterAlpha).forEach { param ->
                val randomized = param.modulators.map { it.randomizeActiveValues() }
                param.modulators.clear()
                param.modulators.addAll(randomized)
                param.randomizeBaseValue()
            }
        }

        drawRandomizeRow("rand_deck_a", "Randomize Deck A", startX, startY + btnHeight + spacing, btnWidth, btnHeight, scale) {
            PatchGridUndo.pushUndoState(state, mixer)
            mixer.deckA.randomizeModulators()
        }

        drawRandomizeRow("rand_deck_b", "Randomize Deck B", startX, startY + (btnHeight + spacing) * 2f, btnWidth, btnHeight, scale) {
            PatchGridUndo.pushUndoState(state, mixer)
            mixer.deckB.randomizeModulators()
        }

        drawRandomizeRow("rand_deck_c", "Randomize Deck C", startX, startY + (btnHeight + spacing) * 3f, btnWidth, btnHeight, scale) {
            PatchGridUndo.pushUndoState(state, mixer)
            mixer.deckC.randomizeModulators()
        }
        
        // Draw Top Tab Row at the bottom-left of the header area (just above the separator)
        ImGui.setCursorScreenPos(startX, startY + headerH - 24f)
        PatchGridTabs.drawTopTabs(state)
        
        val lineCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f) // VERY subtle extended grid line
        val bottomY = startY + ImGui.getWindowHeight() // align to parent window height
        
        // Draw FINAL header
        val finalColX = startX + labelColW + getColumnOffset("final")
        dl.addLine(finalColX - CELL_PAD * 0.5f, startY, finalColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        
        val isFinalHovered = mousePos.x >= finalColX && mousePos.x <= (finalColX + CELL) && mousePos.y >= startY && mousePos.y <= bottomY
        if (isFinalHovered) {
            dl.addRectFilled(finalColX, startY, finalColX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
        }
        
        var twFinal = 0f
        val labelFinal = "F\nI\nN\nA\nL"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twFinal = ImGui.calcTextSize(labelFinal).x }
        var offsetX = ((CELL - twFinal) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(finalColX + offsetX, startY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("final"))
        UITheme.caption(labelFinal)
        ImGui.popStyleColor()
        val isFinalHeaderHovered = mousePos.x >= finalColX && mousePos.x <= (finalColX + CELL) && mousePos.y >= startY && mousePos.y <= (startY + headerH)
        if (isFinalHeaderHovered && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("FINAL: Base parameter value and modulation bounds/limits.")
        }

        // Draw MIDI header
        val midiColX = startX + labelColW + getColumnOffset("midi")
        dl.addLine(midiColX - CELL_PAD * 0.5f, startY, midiColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        
        val isMidiHovered = mousePos.x >= midiColX && mousePos.x <= (midiColX + CELL) && mousePos.y >= startY && mousePos.y <= bottomY
        if (isMidiHovered) {
            dl.addRectFilled(midiColX, startY, midiColX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
        }
        
        var twMidi = 0f
        val labelMidi = "M\nI\nD\nI"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twMidi = ImGui.calcTextSize(labelMidi).x }
        offsetX = ((CELL - twMidi) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(midiColX + offsetX, startY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("midi"))
        UITheme.caption(labelMidi)
        ImGui.popStyleColor()
        val isMidiHeaderHovered = mousePos.x >= midiColX && mousePos.x <= (midiColX + CELL) && mousePos.y >= startY && mousePos.y <= (startY + headerH)
        if (isMidiHeaderHovered && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("MIDI: Map MIDI CC/Notes from controllers to modulate this parameter.")
        }

        // Draw each column header vertically
        val cvCols = getCvColumns()
        for ((idx, label) in cvLabelsVertical.withIndex()) {
            val cvId = cvCols[idx]
            val colX = startX + labelColW + getColumnOffset(cvId)
            dl.addLine(colX - CELL_PAD * 0.5f, startY, colX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
            
            val isCvHovered = mousePos.x >= colX && mousePos.x <= (colX + CELL) && mousePos.y >= startY && mousePos.y <= bottomY
            if (isCvHovered) {
                dl.addRectFilled(colX, startY, colX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
            }
            
            var tw = 0f
            UITheme.withFont(UITheme.FontLevel.CAPTION) { tw = ImGui.calcTextSize(label).x }
            val offX = ((CELL - tw) * 0.5f).coerceAtLeast(0f)
            ImGui.setCursorScreenPos(colX + offX, startY)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor(cvId))
            UITheme.caption(label)
            ImGui.popStyleColor()
            val isCvHeaderHovered = mousePos.x >= colX && mousePos.x <= (colX + CELL) && mousePos.y >= startY && mousePos.y <= (startY + headerH)
            if (isCvHeaderHovered && UITheme.tooltipsEnabled) {
                val cvDesc = when (cvId) {
                    "gen1" -> "LFO: Synthetic low-frequency oscillator waveforms (Sine, Triangle, Square, Random)."
                    "audio" -> "AUDIO: Modulator envelopes tracked from input audio frequency bands (Bass, Mid, High, Amplitude)."
                    "trigger" -> "TRIGGER: Modulator envelopes tracked from transient onsets or peak accents."
                    else -> "CV Modulator source."
                }
                ImGui.setTooltip(cvDesc)
            }
        }
        
        // Draw final separator line on the right edge
        val lastColId = if (cvCols.isNotEmpty()) cvCols.last() else "midi"
        val rightColX = startX + labelColW + getColumnOffset(lastColId) + CELL + CELL_PAD * 0.5f
        dl.addLine(rightColX, startY, rightColX, bottomY, lineCol, 1f)
        
        // Restore cursor to where the dummy left off
        ImGui.setCursorScreenPos(startX, afterHeadersY)
    }



    private fun drawRandomizeRow(
        id: String,
        label: String,
        startX: Float,
        startY: Float,
        btnWidth: Float,
        btnHeight: Float,
        scale: Float,
        onClick: () -> Unit
    ) {
        if (PatchGridRenderer.drawDiceButton(id, startX, startY, scale, btnWidth, btnHeight)) {
            onClick()
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            val scopeDesc = when(id) {
                "rand_all" -> "Randomize modulator depths and values for all decks (A, B, C)."
                "rand_deck_a" -> "Randomize modulator depths and values for Deck A."
                "rand_deck_b" -> "Randomize modulator depths and values for Deck B."
                "rand_deck_c" -> "Randomize modulator depths and values for Deck C."
                else -> "Randomize values."
            }
            ImGui.setTooltip(scopeDesc)
        }
        ImGui.sameLine(0f, 6f)
        val textY = startY + (btnHeight - ImGui.getTextLineHeight()) * 0.5f
        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), textY)
        UITheme.body(label)
    }
}

fun Deck.randomizeModulators() {
    val allParams = mutableListOf<llm.slop.spirals.parameters.ModulatableParameter>()
    allParams.addAll(this.source.parameters.values)
    allParams.add(this.source.globalAlpha)
    allParams.add(this.sourceSelect)
    allParams.add(this.fbDecay)
    allParams.add(this.fbGain)
    allParams.add(this.fbZoom)
    allParams.add(this.fbRotate)
    allParams.add(this.fbHueShift)
    allParams.add(this.fbBlur)
    allParams.add(this.fbChroma)
    allParams.add(this.fbMode)

    for (param in allParams) {
        val randomized = param.modulators.map { it.randomizeActiveValues() }
        param.modulators.clear()
        param.modulators.addAll(randomized)
        param.randomizeBaseValue()
    }
}
