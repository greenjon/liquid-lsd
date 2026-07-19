package llm.slop.liquidlsd.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.models.ClipboardManager
import llm.slop.liquidlsd.models.CellClipboardData
import llm.slop.liquidlsd.models.RowClipboardData
import llm.slop.liquidlsd.models.toDto
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Draws the Patch Grid panel. Rows = grouped ModulatableParameters.
 * Columns = CV sources. Each intersection is a clickable cell.
 */
object PatchGridPanel {

    private fun getCvColumns(session: llm.slop.liquidlsd.SessionContext): List<String> {
        return if (session.uiTheme.audioEngineEnabled) {
            listOf("lfo", "audio", "trigger")
        } else {
            listOf("lfo")
        }
    }

    private fun getCvLabels(session: llm.slop.liquidlsd.SessionContext): List<String> {
        return if (session.uiTheme.audioEngineEnabled) {
            listOf("LFO", "AUDIO", "TRIG")
        } else {
            listOf("LFO")
        }
    }

    private fun getVisibleColumns(session: llm.slop.liquidlsd.SessionContext): List<String> {
        val visibleCols = mutableListOf("final", "midi")
        visibleCols.addAll(getCvColumns(session))
        return visibleCols
    }

    // Size of each cell square (px)
    private const val CELL = 35f
    private const val CELL_PAD = 5f

    private fun getColumnOffset(session: llm.slop.liquidlsd.SessionContext, colId: String): Float {
        // Build the visible column list dynamically
        val visibleCols = getVisibleColumns(session)
        
        // Find the index of this column in the visible list
        val index = visibleCols.indexOf(colId)
        if (index < 0) return 0f
        
        // Calculate offset based on position in visible columns
        return index * (CELL + CELL_PAD)
    }

    private fun getCvColor(colId: String, alpha: Float = 1f): Int {
        return when (colId) {
            // Special
            "final"          -> ImGui.colorConvertFloat4ToU32(0.0f, 1.0f, 0.7f, alpha)
            "base"           -> ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, alpha)
            "midi"           -> ImGui.colorConvertFloat4ToU32(0.7f, 0.3f, 1.0f, alpha)
            // Synthetic / Generators
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

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, state: PatchGridState) {
        rowIndex = 0
        val avail = ImGui.getContentRegionAvailX()
        gridStartX = ImGui.getCursorScreenPosX()
        
        val lastVisibleCol = getCvColumns(session).lastOrNull() ?: "midi"
        val maxGridW = getColumnOffset(session, lastVisibleCol) + CELL + CELL_PAD * 0.5f
        val labelColW = (avail - maxGridW - 20f).coerceAtLeast(120f)

        PatchGridKeyboard.handleKeyboardShortcuts(state, mixer, { s, m -> PatchGridUndo.pushUndoState(s, m) }, { s, m -> PatchGridUndo.performUndo(s, m) })

        // Row 1: Fixed/Top Tabs
        PatchGridTabs.drawTopTabs(session, state)
        ImGui.spacing()

        // Row 2: Dynamic/Sub Tabs
        PatchGridTabs.drawSubTabs(session, state, mixer)
        ImGui.spacing()
        ImGui.spacing()

        ImGui.separator()
        ImGui.spacing()

        // Row 3: Column Headers
        drawColumnHeaders(session, labelColW, state, mixer)

        if (ImGui.beginChild("##patch_grid_scroll", 0f, 0f, false)) {
            if (state.activeTopTab == "Mixer") {
                PatchGridTabs.drawSubGroupContent(session, "Mixer", "Mixer", state) {
                    PatchGridRenderer.drawParamRow(session, "Deck A Source", "Deck A/FB/Source",   mixer.deckA.sourceSelect, state, labelColW, mixer, gridStartX, 0, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow(session, "Deck B Source", "Deck B/FB/Source",   mixer.deckB.sourceSelect, state, labelColW, mixer, gridStartX, 1, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow(session, "Deck C Source", "Deck C/FB/Source",   mixer.deckC.sourceSelect, state, labelColW, mixer, gridStartX, 2, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow(session, "crossfade",  "Mixer/crossfade",  mixer.crossfade,  state, labelColW, mixer, gridStartX, 3, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow(session, "master Alpha",   "Mixer/masterAlpha", mixer.masterAlpha, state, labelColW, mixer, gridStartX, 4, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow(session, "bloom",      "Mixer/bloom",       mixer.bloom,       state, labelColW, mixer, gridStartX, 5, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow(session, "fade speed",  "Mixer/xfadeSpeed",  mixer.xfadeSpeed,  state, labelColW, mixer, gridStartX, 6, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow(session, "queue prev", "Mixer/queuePrev", mixer.queuePrev, state, labelColW, mixer, gridStartX, 7, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow(session, "queue next", "Mixer/queueNext", mixer.queueNext, state, labelColW, mixer, gridStartX, 8, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                }
            } else if (state.activeTopTab == "Deck A") {
                PatchGridTabs.drawDeckGroupContent(session, "Deck A", mixer.deckA, state, labelColW, mixer, gridStartX, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
            } else if (state.activeTopTab == "Deck B") {
                PatchGridTabs.drawDeckGroupContent(session, "Deck B", mixer.deckB, state, labelColW, mixer, gridStartX, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
            } else if (state.activeTopTab == "Deck C") {
                PatchGridTabs.drawDeckGroupContent(session, "Deck C", mixer.deckC, state, labelColW, mixer, gridStartX, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
            }
        }
        ImGui.endChild()
    }

    // -- Helpers --------------------------------------------------------------

    private fun drawColumnHeaders(session: llm.slop.liquidlsd.SessionContext, labelColW: Float, state: PatchGridState, mixer: Mixer) {
        val dl = ImGui.getWindowDrawList()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val mousePos = ImGui.getIO().mousePos
        
        // Calculate the maximum height needed for the vertical labels
        var maxH = 0f
        val cvLabelsVertical = getCvLabels(session).map { it.toList().joinToString("\n") }
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            for (label in cvLabelsVertical) {
                val h = ImGui.calcTextSize(label).y
                if (h > maxH) maxH = h
            }
            val hFinal = ImGui.calcTextSize("F\nI\nN\nA\nL").y
            val hMidi = ImGui.calcTextSize("M\nI\nD\nI").y
            if (hFinal > maxH) maxH = hFinal
            if (hMidi > maxH) maxH = hMidi
        }
        val headerH = (maxH + 5f).coerceAtLeast(40f)
        
        // Reserve vertical space for headers
        ImGui.dummy(10f, headerH)
        val afterHeadersY = ImGui.getCursorScreenPosY()
        
        val lineCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f) // VERY subtle extended grid line
        val bottomY = startY + ImGui.getWindowHeight() // align to parent window height
        
        // Draw FINAL header
        val finalColX = startX + labelColW + getColumnOffset(session, "final")
        dl.addLine(finalColX - CELL_PAD * 0.5f, startY, finalColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        
        val isFinalHovered = mousePos.x >= finalColX && mousePos.x <= (finalColX + CELL) && mousePos.y >= startY && mousePos.y <= bottomY
        if (isFinalHovered) {
            dl.addRectFilled(finalColX, startY, finalColX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
        }
        
        var twFinal = 0f
        val labelFinal = "F\nI\nN\nA\nL"
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { twFinal = ImGui.calcTextSize(labelFinal).x }
        var offsetX = ((CELL - twFinal) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(finalColX + offsetX, startY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("final"))
        session.uiTheme.caption(labelFinal)
        ImGui.popStyleColor()
        val isFinalHeaderHovered = mousePos.x >= finalColX && mousePos.x <= (finalColX + CELL) && mousePos.y >= startY && mousePos.y <= (startY + headerH)
        if (isFinalHeaderHovered && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("FINAL: Base parameter value and modulation bounds/limits.")
        }

        // Draw MIDI header
        val midiColX = startX + labelColW + getColumnOffset(session, "midi")
        dl.addLine(midiColX - CELL_PAD * 0.5f, startY, midiColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        
        val isMidiHovered = mousePos.x >= midiColX && mousePos.x <= (midiColX + CELL) && mousePos.y >= startY && mousePos.y <= bottomY
        if (isMidiHovered) {
            dl.addRectFilled(midiColX, startY, midiColX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
        }
        
        var twMidi = 0f
        val labelMidi = "M\nI\nD\nI"
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { twMidi = ImGui.calcTextSize(labelMidi).x }
        offsetX = ((CELL - twMidi) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(midiColX + offsetX, startY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("midi"))
        session.uiTheme.caption(labelMidi)
        ImGui.popStyleColor()
        val isMidiHeaderHovered = mousePos.x >= midiColX && mousePos.x <= (midiColX + CELL) && mousePos.y >= startY && mousePos.y <= (startY + headerH)
        if (isMidiHeaderHovered && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("MIDI: Map MIDI CC/Notes from controllers to modulate this parameter.")
        }

        // Draw each column header vertically
        val cvCols = getCvColumns(session)
        for ((idx, label) in cvLabelsVertical.withIndex()) {
            val cvId = cvCols[idx]
            val colX = startX + labelColW + getColumnOffset(session, cvId)
            dl.addLine(colX - CELL_PAD * 0.5f, startY, colX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
            
            val isCvHovered = mousePos.x >= colX && mousePos.x <= (colX + CELL) && mousePos.y >= startY && mousePos.y <= bottomY
            if (isCvHovered) {
                dl.addRectFilled(colX, startY, colX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
            }
            
            var tw = 0f
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { tw = ImGui.calcTextSize(label).x }
            val offX = ((CELL - tw) * 0.5f).coerceAtLeast(0f)
            ImGui.setCursorScreenPos(colX + offX, startY)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor(cvId))
            session.uiTheme.caption(label)
            ImGui.popStyleColor()
            val isCvHeaderHovered = mousePos.x >= colX && mousePos.x <= (colX + CELL) && mousePos.y >= startY && mousePos.y <= (startY + headerH)
            if (isCvHeaderHovered && session.uiTheme.tooltipsEnabled) {
                val cvDesc = when (cvId) {
                    "lfo" -> "LFO: Synthetic low-frequency oscillator waveforms (Sine, Triangle, Square, Random)."
                    "audio" -> "AUDIO: Modulator envelopes tracked from input audio frequency bands (Bass, Mid, High, Amplitude)."
                    "trigger" -> "TRIGGER: Modulator envelopes tracked from transient onsets or peak accents."
                    else -> "CV Modulator source."
                }
                ImGui.setTooltip(cvDesc)
            }
        }
        
        // Draw final separator line on the right edge
        val lastColId = if (cvCols.isNotEmpty()) cvCols.last() else "midi"
        val rightColX = startX + labelColW + getColumnOffset(session, lastColId) + CELL + CELL_PAD * 0.5f
        dl.addLine(rightColX, startY, rightColX, bottomY, lineCol, 1f)
        
        // Restore cursor to where the dummy left off
        ImGui.setCursorScreenPos(startX, afterHeadersY)
    }
}

fun Deck.randomizeModulators() {
    val allParams = mutableListOf<llm.slop.liquidlsd.parameters.ModulatableParameter>()
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
