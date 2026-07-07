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
            listOf("LFO", /*"GEN 2",*/ "AUDIO", "TRIG")
        } else {
            listOf("LFO" /*, "GEN 2"*/)
        }
    }


    // Size of each cell square (px)
    private const val CELL = 35f
    private const val CELL_PAD = 5f

    private fun getColumnOffset(colId: String): Float {
        // Build the visible column list dynamically
        val visibleCols = mutableListOf("final", "midi")
        visibleCols.addAll(getCvColumns())
        
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

        // Row 1: Fixed/Top Tabs
        PatchGridTabs.drawTopTabs(state)
        ImGui.spacing()

        // Row 2: Dynamic/Sub Tabs
        PatchGridTabs.drawSubTabs(state, mixer)
        ImGui.spacing()
        ImGui.spacing()

        ImGui.separator()
        ImGui.spacing()

        // Row 3: Column Headers
        drawColumnHeaders(labelColW, state, mixer)

        if (ImGui.beginChild("##patch_grid_scroll", 0f, 0f, false)) {
            if (state.activeTopTab == "Mixer") {
                PatchGridTabs.drawSubGroupContent("Mixer", "Mixer", state) {
                    PatchGridRenderer.drawParamRow("Deck A Source", "Deck A/FB/Source",   mixer.deckA.sourceSelect, state, labelColW, mixer, gridStartX, 0, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("Deck B Source", "Deck B/FB/Source",   mixer.deckB.sourceSelect, state, labelColW, mixer, gridStartX, 1, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("Deck C Source", "Deck C/FB/Source",   mixer.deckC.sourceSelect, state, labelColW, mixer, gridStartX, 2, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("crossfade",  "Mixer/crossfade",  mixer.crossfade,  state, labelColW, mixer, gridStartX, 3, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("master Alpha",   "Mixer/masterAlpha", mixer.masterAlpha, state, labelColW, mixer, gridStartX, 4, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("bloom",      "Mixer/bloom",       mixer.bloom,       state, labelColW, mixer, gridStartX, 5, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("fade speed",  "Mixer/xfadeSpeed",  mixer.xfadeSpeed,  state, labelColW, mixer, gridStartX, 6, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("queue prev", "Mixer/queuePrev", mixer.queuePrev, state, labelColW, mixer, gridStartX, 7, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
                    PatchGridRenderer.drawParamRow("queue next", "Mixer/queueNext", mixer.queueNext, state, labelColW, mixer, gridStartX, 8, ::getCvColumns, ::getColumnOffset, ::getCvColor) { PatchGridUndo.pushUndoState(state, mixer) }
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
        val headerH = 28f
        
        // Reserve vertical space for headers
        ImGui.dummy(10f, headerH)
        val afterHeadersY = ImGui.getCursorScreenPosY()
        
        val lineCol = UITheme.colorU32(UITheme.Colors.BORDER_R, UITheme.Colors.BORDER_G, UITheme.Colors.BORDER_B, 0.55f)
        val bottomY = startY + ImGui.getWindowHeight() // align to parent window height
        
        val finalColX = startX + labelColW + getColumnOffset("final")
        dl.addLine(finalColX - CELL_PAD * 0.5f, startY, finalColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        val isFinalHeaderHovered = drawHeaderBadge(dl, finalColX, startY, "VAL", getCvColor("final"), mousePos)
        if (isFinalHeaderHovered && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("FINAL: Base parameter value and modulation bounds/limits.")
        }

        val midiColX = startX + labelColW + getColumnOffset("midi")
        dl.addLine(midiColX - CELL_PAD * 0.5f, startY, midiColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        val isMidiHeaderHovered = drawHeaderBadge(dl, midiColX, startY, "MIDI", getCvColor("midi"), mousePos)
        if (isMidiHeaderHovered && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("MIDI: Map MIDI CC/Notes from controllers to modulate this parameter.")
        }

        val cvCols = getCvColumns()
        val cvLabels = getCvLabels()
        for ((idx, label) in cvLabels.withIndex()) {
            val cvId = cvCols[idx]
            val colX = startX + labelColW + getColumnOffset(cvId)
            dl.addLine(colX - CELL_PAD * 0.5f, startY, colX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
            val compactLabel = when (label) {
                "AUDIO" -> "AUD"
                "TRIG" -> "TRG"
                else -> label
            }
            val isCvHeaderHovered = drawHeaderBadge(dl, colX, startY, compactLabel, getCvColor(cvId), mousePos)
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

    private fun drawHeaderBadge(dl: ImDrawList, x: Float, y: Float, label: String, color: Int, mousePos: imgui.ImVec2): Boolean {
        val badgeY = y + 4f
        val badgeH = 20f
        val hovered = mousePos.x >= x && mousePos.x <= (x + CELL) && mousePos.y >= y && mousePos.y <= (y + 28f)
        val bgAlpha = if (hovered) 0.26f else 0.16f
        val bgCol = UITheme.colorU32(1f, 1f, 1f, bgAlpha)

        dl.addRectFilled(x + 1f, badgeY, x + CELL - 1f, badgeY + badgeH, bgCol, 4f)

        var textW = 0f
        var textH = 0f
        UITheme.withFont(UITheme.FontLevel.CAPTION) {
            val size = ImGui.calcTextSize(label)
            textW = size.x
            textH = size.y
        }

        ImGui.setCursorScreenPos(x + ((CELL - textW) * 0.5f).coerceAtLeast(1f), badgeY + ((badgeH - textH) * 0.5f))
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, color)
        UITheme.caption(label)
        ImGui.popStyleColor()
        return hovered
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
