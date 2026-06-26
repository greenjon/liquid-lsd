package llm.slop.spirals.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import imgui.flag.ImGuiKey
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.models.ClipboardManager
import llm.slop.spirals.models.CellClipboardData
import llm.slop.spirals.models.RowClipboardData
import llm.slop.spirals.models.toDto
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the Patch Grid panel. Rows = grouped ModulatableParameters.
 * Columns = CV sources. Each intersection is a clickable cell.
 */
object PatchGridPanel {

    private fun getCvColumns(): List<String> {
        return if (UITheme.audioEngineEnabled) {
            listOf("gen1", "gen2", "lfo", "sampleAndHold", "beatPhase", "audio", "amp", "bass", "mid", "high", "trigger", "onset", "accent")
        } else {
            listOf("gen1", "gen2", "lfo", "sampleAndHold")
        }
    }

    private fun getCvLabels(): List<String> {
        return if (UITheme.audioEngineEnabled) {
            listOf("GEN 1", "GEN 2", "LFO", "RAND", "BEAT", "AUDIO", "AMP", "BASS", "MID", "HIGH", "TRIGGER", "ONSET", "ACCENT")
        } else {
            listOf("GEN 1", "GEN 2", "LFO", "RAND")
        }
    }


    // Size of each cell square (px)
    private const val CELL = 35f
    private const val CELL_PAD = 5f
    private const val GROUP_GAP = 10f

    private fun getColumnOffset(colId: String): Float {
        return when (colId) {
            "final"          -> 0 * (CELL + CELL_PAD)
            "base"           -> 1 * (CELL + CELL_PAD) + GROUP_GAP
            "midi"           -> 2 * (CELL + CELL_PAD) + 2 * GROUP_GAP
            "gen1"           -> 3 * (CELL + CELL_PAD) + 2 * GROUP_GAP
            "gen2"           -> 4 * (CELL + CELL_PAD) + 2 * GROUP_GAP
            "lfo"            -> 5 * (CELL + CELL_PAD) + 2 * GROUP_GAP
            "sampleAndHold"  -> 6 * (CELL + CELL_PAD) + 2 * GROUP_GAP
            "beatPhase"      -> 7 * (CELL + CELL_PAD) + 2 * GROUP_GAP
            "audio"          -> 8 * (CELL + CELL_PAD) + 3 * GROUP_GAP
            "amp"            -> 9 * (CELL + CELL_PAD) + 3 * GROUP_GAP
            "bass"           -> 10 * (CELL + CELL_PAD) + 3 * GROUP_GAP
            "mid"            -> 11 * (CELL + CELL_PAD) + 3 * GROUP_GAP
            "high"           -> 12 * (CELL + CELL_PAD) + 3 * GROUP_GAP
            "trigger"        -> 13 * (CELL + CELL_PAD) + 4 * GROUP_GAP
            "onset"          -> 14 * (CELL + CELL_PAD) + 4 * GROUP_GAP
            "accent"         -> 15 * (CELL + CELL_PAD) + 4 * GROUP_GAP
            else             -> 0f
        }
    }

    private fun getCvColor(colId: String, alpha: Float = 1f): Int {
        return when (colId) {
            // Special
            "final"          -> ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, alpha)
            "base"           -> ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, alpha)
            "midi"           -> ImGui.colorConvertFloat4ToU32(0.3f, 1.0f, 0.4f, alpha)
            // Synthetic / Generators
            "gen1"           -> ImGui.colorConvertFloat4ToU32(0.1f, 0.7f, 0.9f, alpha)
            "gen2"           -> ImGui.colorConvertFloat4ToU32(0.1f, 0.8f, 0.7f, alpha)
            "lfo"            -> ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, alpha)
            "sampleAndHold"  -> ImGui.colorConvertFloat4ToU32(0.7f, 0.4f, 1.0f, alpha)
            "beatPhase"      -> ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 1.0f, alpha)
            // Amplitude / Spectral
            "audio"          -> ImGui.colorConvertFloat4ToU32(0.4f, 0.9f, 0.1f, alpha)
            "amp"            -> ImGui.colorConvertFloat4ToU32(0.7f, 0.9f, 0.1f, alpha)
            "bass"           -> ImGui.colorConvertFloat4ToU32(0.9f, 0.2f, 0.2f, alpha)
            "mid"            -> ImGui.colorConvertFloat4ToU32(0.9f, 0.5f, 0.1f, alpha)
            "high"           -> ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.2f, alpha)
            // Transients / Triggers
            "trigger"        -> ImGui.colorConvertFloat4ToU32(0.9f, 0.2f, 0.7f, alpha)
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
        
        // Calculate label column width based on the maximum possible columns (12 columns in total)
        // so that the grid stays left-aligned instead of expanding when columns are hidden.
        val maxGridW = getColumnOffset("accent") + CELL + CELL_PAD * 0.5f
        val labelColW = (avail - maxGridW).coerceAtLeast(120f)

        handleKeyboardShortcuts(state, mixer)

        drawColumnHeaders(labelColW, state, mixer)
        ImGui.separator()
        ImGui.spacing()

        drawGroup("Mixer", state, true) {
            drawParamRow("crossfade",  "Mixer/crossfade",  mixer.crossfade,  state, labelColW, mixer)
            drawParamRow("master α",   "Mixer/masterAlpha", mixer.masterAlpha, state, labelColW, mixer)
            drawParamRow("bloom",      "Mixer/bloom",       mixer.bloom,       state, labelColW, mixer)
            drawParamRow("setlist prev", "Mixer/setlistPrev", mixer.setlistPrev, state, labelColW, mixer)
            drawParamRow("setlist next", "Mixer/setlistNext", mixer.setlistNext, state, labelColW, mixer)
        }
        ImGui.spacing()
        ImGui.spacing()

        drawDeckGroup("Deck A", mixer.deckA, state, labelColW, mixer)
        ImGui.spacing()
        ImGui.spacing()

        drawDeckGroup("Deck B", mixer.deckB, state, labelColW, mixer)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun drawColumnHeaders(labelColW: Float, state: PatchGridState, mixer: Mixer) {
        val dl = ImGui.getWindowDrawList()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        // Calculate the maximum height needed for the vertical labels
        var maxH = 0f
        val cvLabelsVertical = getCvLabels().map { it.toList().joinToString("\n") }
        UITheme.withFont(UITheme.FontLevel.CAPTION) {
            for (label in cvLabelsVertical) {
                val h = ImGui.calcTextSize(label).y
                if (h > maxH) maxH = h
            }
            val hFinal = ImGui.calcTextSize("F\nI\nN\nA\nL").y
            val hBase = ImGui.calcTextSize("B\nA\nS\nE").y
            val hMidi = ImGui.calcTextSize("M\nI\nD\nI").y
            if (hFinal > maxH) maxH = hFinal
            if (hBase > maxH) maxH = hBase
            if (hMidi > maxH) maxH = hMidi
        }
        
        // Reserve vertical space for headers
        ImGui.dummy(10f, maxH + 5f)
        val afterHeadersY = ImGui.getCursorScreenPosY()

        // Draw Randomize All button in the top-left empty space of the column headers
        ImGui.setCursorScreenPos(startX, startY + (maxH + 5f - 24f) * 0.5f)
        if (ImGui.button("Randomize All", labelColW - CELL_PAD, 24f)) {
            mixer.deckA.randomizeModulators()
            mixer.deckB.randomizeModulators()
            listOf(mixer.crossfade, mixer.masterAlpha).forEach { param ->
                val randomized = param.modulators.map { it.randomizeActiveValues() }
                param.modulators.clear()
                param.modulators.addAll(randomized)
                param.randomizeBaseValue()
            }
            // Refresh selection

        }
        
        val lineCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f) // VERY subtle extended grid line
        val bottomY = startY + ImGui.getWindowHeight() + ImGui.getScrollMaxY() + 1000f // ensure it goes all the way down

        // Draw FINAL header
        val finalColX = startX + labelColW + getColumnOffset("final")
        dl.addLine(finalColX - CELL_PAD * 0.5f, startY, finalColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        var twFinal = 0f
        val labelFinal = "F\nI\nN\nA\nL"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twFinal = ImGui.calcTextSize(labelFinal).x }
        var offsetX = ((CELL - twFinal) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(finalColX + offsetX, startY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("final"))
        UITheme.caption(labelFinal)
        ImGui.popStyleColor()

        // Draw BASE header
        val baseColX = startX + labelColW + getColumnOffset("base")
        dl.addLine(baseColX - CELL_PAD * 0.5f, startY, baseColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        var twBase = 0f
        val labelBase = "B\nA\nS\nE"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twBase = ImGui.calcTextSize(labelBase).x }
        offsetX = ((CELL - twBase) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(baseColX + offsetX, startY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("base"))
        UITheme.caption(labelBase)
        ImGui.popStyleColor()

        // Draw MIDI header
        val midiColX = startX + labelColW + getColumnOffset("midi")
        dl.addLine(midiColX - CELL_PAD * 0.5f, startY, midiColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        var twMidi = 0f
        val labelMidi = "M\nI\nD\nI"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twMidi = ImGui.calcTextSize(labelMidi).x }
        offsetX = ((CELL - twMidi) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(midiColX + offsetX, startY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("midi"))
        UITheme.caption(labelMidi)
        ImGui.popStyleColor()

        // Draw each column header vertically
        val cvCols = getCvColumns()
        for ((idx, label) in cvLabelsVertical.withIndex()) {
            val cvId = cvCols[idx]
            val colX = startX + labelColW + getColumnOffset(cvId)
            dl.addLine(colX - CELL_PAD * 0.5f, startY, colX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
            
            var tw = 0f
            UITheme.withFont(UITheme.FontLevel.CAPTION) { tw = ImGui.calcTextSize(label).x }
            val offX = ((CELL - tw) * 0.5f).coerceAtLeast(0f)
            ImGui.setCursorScreenPos(colX + offX, startY)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor(cvId))
            UITheme.caption(label)
            ImGui.popStyleColor()
        }
        
        // Draw final separator line on the right edge
        val lastColId = if (cvCols.isNotEmpty()) cvCols.last() else "midi"
        val rightColX = startX + labelColW + getColumnOffset(lastColId) + CELL + CELL_PAD * 0.5f
        dl.addLine(rightColX, startY, rightColX, bottomY, lineCol, 1f)
        
        // Restore cursor to where the dummy left off
        ImGui.setCursorScreenPos(startX, afterHeadersY)
    }

    private inline fun drawGroup(label: String, state: PatchGridState, isTopLevel: Boolean = false, block: () -> Unit) {
        val open = state.groupOpen.getValue(label)
        val flags = ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.SpanFullWidth

        val needsCollapse = state.groupNeedsCollapse.getValue(label)
        if (needsCollapse) {
            ImGui.setNextItemOpen(false)
            state.groupNeedsCollapse[label] = false
        }
        val needsExpand = state.groupNeedsExpand.getValue(label)
        if (needsExpand) {
            ImGui.setNextItemOpen(true)
            state.groupNeedsExpand[label] = false
        }

        if (isTopLevel) {
            val bgCol = when(label) {
                "Deck A" -> ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.8f, 0.15f)
                "Deck B" -> ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 0.2f, 0.15f)
                else -> ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0f)
            }
            if (bgCol != 0) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Header, bgCol)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.HeaderHovered, bgCol) // make it look like a static background
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.HeaderActive, bgCol)
            }
        }

        val treeOpen = if (isTopLevel) {
            var res = false
            UITheme.withFont(UITheme.FontLevel.H2) {
                res = ImGui.treeNodeEx(label, if (open) flags else ImGuiTreeNodeFlags.None)
            }
            res
        } else {
            ImGui.treeNodeEx(label, if (open) flags else ImGuiTreeNodeFlags.None)
        }

        if (isTopLevel) {
            val hasBg = (label == "Deck A" || label == "Deck B")
            if (hasBg) ImGui.popStyleColor(3)
        }

        if (treeOpen) {
            state.groupOpen[label] = true
            block()
            ImGui.treePop()
        } else {
            state.groupOpen[label] = false
        }
    }

    private inline fun drawSubGroup(parentLabel: String, label: String, state: PatchGridState, block: () -> Unit) {
        val key = "$parentLabel/$label"
        val open = state.groupOpen.getValue(key)
        val flags = ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.SpanFullWidth

        val needsCollapse = state.groupNeedsCollapse.getValue(key)
        if (needsCollapse) {
            ImGui.setNextItemOpen(false)
            state.groupNeedsCollapse[key] = false
        }
        val needsExpand = state.groupNeedsExpand.getValue(key)
        if (needsExpand) {
            ImGui.setNextItemOpen(true)
            state.groupNeedsExpand[key] = false
        }

        if (ImGui.treeNodeEx(label, if (open) flags else ImGuiTreeNodeFlags.None)) {
            val wasClosed = !open
            if (wasClosed && UITheme.autocollapseEnabled) {
                syncAndCollapseSubgroups(label, state)
            }
            state.groupOpen[key] = true
            
            // Record start Y for the margin line
            val startY = ImGui.getCursorScreenPosY()
            val dl = ImGui.getWindowDrawList()
            
            ImGui.indent()
            block()
            ImGui.unindent()
            
            // Draw margin line
            val endY = ImGui.getCursorScreenPosY()
            val startX = gridStartX + 5f // Slightly indented from the absolute left margin
            
            // Choose color based on subgroup
            val lineCol = when(label) {
                "Geometry" -> ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 0.5f, 0.6f) // Greenish
                "Color" -> ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 0.6f, 0.6f)    // Pinkish
                "Background" -> ImGui.colorConvertFloat4ToU32(0.4f, 0.5f, 0.8f, 0.6f) // Blueish
                "Feedback" -> ImGui.colorConvertFloat4ToU32(0.8f, 0.7f, 0.3f, 0.6f)   // Yellowish
                else -> ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.6f)
            }
            
            dl.addLine(startX, startY, startX, endY - 2f, lineCol, 3f)

            ImGui.treePop()
        } else {
            val wasOpen = open
            if (wasOpen && UITheme.autocollapseEnabled) {
                syncCloseSubgroup(label, state)
            }
            state.groupOpen[key] = false
        }
    }

    private fun syncAndCollapseSubgroups(activeSubgroupLabel: String, state: PatchGridState) {
        val decks = listOf("Deck A", "Deck B")
        val subgroups = listOf("Geometry", "Color", "Background", "Feedback")

        for (deck in decks) {
            for (sub in subgroups) {
                val k = "$deck/$sub"
                if (sub == activeSubgroupLabel) {
                    state.groupOpen[k] = true
                    state.groupNeedsExpand[k] = true
                } else {
                    state.groupOpen[k] = false
                    state.groupNeedsCollapse[k] = true
                }
            }
        }
    }

    private fun syncCloseSubgroup(activeSubgroupLabel: String, state: PatchGridState) {
        val decks = listOf("Deck A", "Deck B")
        for (deck in decks) {
            val k = "$deck/$activeSubgroupLabel"
            state.groupOpen[k] = false
            state.groupNeedsCollapse[k] = true
        }
    }

    private fun drawDeckGroup(deckLabel: String, deck: Deck, state: PatchGridState, labelColW: Float, mixer: Mixer) {
        drawGroup(deckLabel, state, true) {
            val mandala = deck.source as? Mandala

            if (mandala != null) {
                drawSubGroup(deckLabel, "Geometry", state) {
                    drawParamRow("Lobe Count", "$deckLabel/Geometry/Lobes",    mandala.parameters["Lobes"]!!,         state, labelColW, mixer)
                    drawParamRow("Recipe ID",  "$deckLabel/Geometry/Recipe",   mandala.parameters["Recipe Select"]!!,  state, labelColW, mixer)
                    drawParamRow("L1",       "$deckLabel/Geometry/L1",       mandala.parameters["L1"]!!,       state, labelColW, mixer)
                    drawParamRow("L2",       "$deckLabel/Geometry/L2",       mandala.parameters["L2"]!!,       state, labelColW, mixer)
                    drawParamRow("L3",       "$deckLabel/Geometry/L3",       mandala.parameters["L3"]!!,       state, labelColW, mixer)
                    drawParamRow("L4",       "$deckLabel/Geometry/L4",       mandala.parameters["L4"]!!,       state, labelColW, mixer)
                    drawParamRow("Scale",    "$deckLabel/Geometry/Scale",    mandala.parameters["Scale"]!!,    state, labelColW, mixer)
                    drawParamRow("Rotation", "$deckLabel/Geometry/Rotation", mandala.parameters["Rotation"]!!, state, labelColW, mixer)
                }
                drawSubGroup(deckLabel, "Color", state) {
                    drawParamRow("Thickness",  "$deckLabel/Color/Thickness",  mandala.parameters["Thickness"]!!,  state, labelColW, mixer)
                    drawParamRow("Hue Offset", "$deckLabel/Color/HueOffset",  mandala.parameters["Hue Offset"]!!, state, labelColW, mixer)
                    drawParamRow("Hue Sweep",  "$deckLabel/Color/HueSweep",   mandala.parameters["Hue Sweep"]!!,  state, labelColW, mixer)
                    drawParamRow("Depth",      "$deckLabel/Color/Depth",      mandala.parameters["Depth"]!!,      state, labelColW, mixer)
                    drawParamRow("Gain",       "$deckLabel/Color/Gain",       mandala.globalAlpha,                 state, labelColW, mixer)
                }
                drawSubGroup(deckLabel, "Background", state) {
                    drawParamRow("Bg Style",    "$deckLabel/Background/Style",    mandala.parameters["Bg Style"]!!,    state, labelColW, mixer)
                    drawParamRow("Bg Feedback", "$deckLabel/Background/Feedback", mandala.parameters["Bg Feedback"]!!, state, labelColW, mixer)
                    drawParamRow("Bg Hue",      "$deckLabel/Background/Hue",      mandala.parameters["Bg Hue"]!!,      state, labelColW, mixer)
                    drawParamRow("Bg Sat",      "$deckLabel/Background/Sat",      mandala.parameters["Bg Sat"]!!,      state, labelColW, mixer)
                    drawParamRow("Bg Val",      "$deckLabel/Background/Val",      mandala.parameters["Bg Val"]!!,      state, labelColW, mixer)
                    drawParamRow("Bg Sweep",    "$deckLabel/Background/Sweep",    mandala.parameters["Bg Sweep"]!!,    state, labelColW, mixer)
                    drawParamRow("Bg Speed",    "$deckLabel/Background/Speed",    mandala.parameters["Bg Speed"]!!,    state, labelColW, mixer)
                    drawParamRow("Bg Zoom",     "$deckLabel/Background/Zoom",     mandala.parameters["Bg Zoom"]!!,     state, labelColW, mixer)
                }
            }

            drawSubGroup(deckLabel, "Feedback", state) {
                drawParamRow("Feedback",    "$deckLabel/FB/Decay",    deck.fbDecay,    state, labelColW, mixer)
                drawParamRow("FB Gain",     "$deckLabel/FB/Gain",     deck.fbGain,     state, labelColW, mixer)
                drawParamRow("FB Zoom",     "$deckLabel/FB/Zoom",     deck.fbZoom,     state, labelColW, mixer)
                drawParamRow("FB Rotate",   "$deckLabel/FB/Rotate",   deck.fbRotate,   state, labelColW, mixer)
                drawParamRow("FB Hue Shift","$deckLabel/FB/HueShift", deck.fbHueShift, state, labelColW, mixer)
                drawParamRow("FB Blur",     "$deckLabel/FB/Blur",     deck.fbBlur,     state, labelColW, mixer)
                drawParamRow("FB Chroma",   "$deckLabel/FB/Chroma",   deck.fbChroma,   state, labelColW, mixer)
                drawParamRow("FB Mode",     "$deckLabel/FB/Mode",     deck.fbMode,     state, labelColW, mixer)
            }
        }
    }

    /**
     * Draws one horizontal row: the parameter label + one cell per CV column.
     */
    private fun drawParamRow(
        label: String,
        paramKey: String,
        param: ModulatableParameter,
        state: PatchGridState,
        labelColW: Float,
        mixer: Mixer
    ) {
        val isEven = (rowIndex % 2 == 0)
        rowIndex++

        val mousePos = ImGui.getIO().mousePos
        val rowScreenY = ImGui.getCursorScreenPosY()
        val rowWidth = labelColW + getColumnOffset("accent") + CELL + CELL_PAD * 0.5f
        val isHoveredRow = mousePos.y >= rowScreenY && mousePos.y <= (rowScreenY + CELL) && mousePos.x >= gridStartX && mousePos.x <= (gridStartX + rowWidth)

        ImGui.pushID(paramKey)

        if (isEven || isHoveredRow) {
            val dl = ImGui.getWindowDrawList()
            val stripeCol = if (isHoveredRow) {
                ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.06f)
            } else {
                ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.03f)
            }
            dl.addRectFilled(gridStartX, rowScreenY, gridStartX + rowWidth, rowScreenY + CELL, stripeCol)
        }

        // Row label
        val rowX = ImGui.getCursorPosX()
        val rowY = ImGui.getCursorPosY()
        
        ImGui.setCursorPosY(rowY + (CELL - ImGui.getTextLineHeight()) * 0.5f)
        val cursorStartX = ImGui.getCursorPosX()
        val indent = ImGui.getCursorScreenPosX() - gridStartX
        val labelBtnW = labelColW - indent - CELL_PAD
        UITheme.body(label)
        ImGui.sameLine(cursorStartX)
        ImGui.invisibleButton("row_label_btn_$paramKey", labelBtnW, CELL)
        if (ImGui.beginPopupContextItem("row_menu_$paramKey")) {
            if (ImGui.menuItem("Copy Row Modulations")) {
                ClipboardManager.rowClipboard = RowClipboardData(paramKey, param.toDto())
            }
            val hasRowClip = ClipboardManager.rowClipboard != null
            if (ImGui.menuItem("Paste Row Modulations", null, false, hasRowClip)) {
                ClipboardManager.rowClipboard?.let { ClipboardManager.applyRowClipboard(param, it, mixer) }
            }
            ImGui.separator()
            if (ImGui.menuItem("Reset Parameter to Default")) {
                param.reset()
            }
            if (ImGui.menuItem("Clear all CVs")) {
                param.modulators.clear()
            }
            val hasMidiMap = llm.slop.spirals.midi.MidiMappingManager.hasMapping(paramKey)
            if (ImGui.menuItem("Clear MIDI mapping", null, false, hasMidiMap)) {
                llm.slop.spirals.midi.MidiMappingManager.removeMapping(paramKey)
                llm.slop.spirals.midi.MidiMappingManager.saveActiveProfile()
            }
            ImGui.endPopup()
        }
        ImGui.setCursorPosY(rowY)

        val dl = ImGui.getWindowDrawList()
        val r = CELL * 0.5f

        // 1. FINAL Cell
        val finalX = gridStartX + labelColW + getColumnOffset("final")
        val finalY = rowScreenY
        val isFinalSelected = state.selectedCell?.paramKey == paramKey && state.selectedCell?.cvSourceId == "final"
        val isFinalHoveredCol = mousePos.x >= finalX && mousePos.x <= (finalX + CELL)
        
        ImGui.setCursorScreenPos(finalX, finalY)
        ImGui.invisibleButton("##final_cell", CELL, CELL)
        if (ImGui.isItemClicked()) {
            state.select(PatchCellId(paramKey, "final"), param)
        }
        
        val finalBgCol = when {
            isFinalSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            else            -> getCvColor("final", 0.05f) // Subtle background tint
        }
        val finalBorderCol = when {
            isFinalSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            else            -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        val finalColor = getCvColor("final") // Active meter color
        
        drawKnobMeter(
            dl = dl, x = finalX, y = finalY, r = r,
            value = param.value, min = param.minClamp, max = param.maxClamp,
            meterType = param.meterType,
            baseValue = null, baseMin = null, baseMax = null,
            color = finalColor, bgCol = finalBgCol, borderCol = finalBorderCol,
            isHoveredRow = isHoveredRow, isHoveredCol = isFinalHoveredCol
        )

        // 2. BASE Cell
        val baseX = gridStartX + labelColW + getColumnOffset("base")
        val baseY = rowScreenY
        val isBaseSelected = state.selectedCell?.paramKey == paramKey && state.selectedCell?.cvSourceId == "base"
        val isBaseHoveredCol = mousePos.x >= baseX && mousePos.x <= (baseX + CELL)
        
        ImGui.setCursorScreenPos(baseX, baseY)
        ImGui.invisibleButton("##base_cell", CELL, CELL)
        if (ImGui.isItemClicked()) {
            state.select(PatchCellId(paramKey, "base"), param)
        }
        
        val baseBgCol = when {
            isBaseSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            else           -> getCvColor("base", 0.05f)
        }
        val baseBorderCol = when {
            isBaseSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            else           -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        val baseColor = getCvColor("base")
        
        drawKnobMeter(
            dl = dl, x = baseX, y = baseY, r = r,
            value = param.baseValue, min = param.minClamp, max = param.maxClamp,
            meterType = param.meterType,
            baseValue = param.baseValue, baseMin = param.baseMin, baseMax = param.baseMax,
            color = baseColor, bgCol = baseBgCol, borderCol = baseBorderCol,
            isHoveredRow = isHoveredRow, isHoveredCol = isBaseHoveredCol
        )

        // 2.5 MIDI Cell
        val midiX = gridStartX + labelColW + getColumnOffset("midi")
        val midiY = rowScreenY
        val midiCellId = PatchCellId(paramKey, "midi")
        val isMidiSelected = state.selectedCell == midiCellId
        val isMidiHoveredCol = mousePos.x >= midiX && mousePos.x <= (midiX + CELL)
        val isMidiCrosshair = isHoveredRow && isMidiHoveredCol
        
        // Find any MIDI modulator for this parameter
        val midiMods = param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
        val hasMidiMod = midiMods.any { !it.bypassed }
        val isMidiBypassed = midiMods.isNotEmpty() && midiMods.all { it.bypassed }
        
        val isMidiTarget = state.midiLearnTarget?.let {
            it is MidiLearnTarget.GridCell && it.cellId == midiCellId
        } ?: false

        ImGui.setCursorScreenPos(midiX, midiY)
        ImGui.invisibleButton("##midi_cell", CELL, CELL)
        if (ImGui.isItemClicked()) {
            if (state.isMidiLearnMode) {
                state.midiLearnTarget = MidiLearnTarget.GridCell(midiCellId, param)
            } else {
                state.select(midiCellId, param)
            }
        }
        
        if (ImGui.beginPopupContextItem("midi_cell_menu_$paramKey")) {
            if (midiMods.isNotEmpty()) {
                if (ImGui.menuItem("Clear MIDI Modulator")) {
                    param.modulators.removeAll(midiMods)
                }
                if (ImGui.menuItem(if (isMidiBypassed) "Enable MIDI Modulator" else "Bypass MIDI Modulator")) {
                    val updated = param.modulators.map {
                        if (it.sourceId.startsWith("midi_cc_")) it.copy(bypassed = !it.bypassed) else it
                    }
                    param.modulators.clear()
                    param.modulators.addAll(updated)
                }
            }
            ImGui.endPopup()
        }

        val midiBgCol = when {
            isMidiTarget   -> ImGui.colorConvertFloat4ToU32(0.0f, 0.4f, 0.5f, 1f)
            isMidiSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            hasMidiMod     -> ImGui.colorConvertFloat4ToU32(0.05f, 0.15f, 0.2f, 1f)
            else           -> getCvColor("midi", 0.05f)
        }
        val midiBorderCol = when {
            isMidiTarget   -> ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 1.0f, 1f)
            isMidiSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            hasMidiMod     -> ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.7f, 0.8f)
            else           -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        
        if (hasMidiMod || isMidiBypassed) {
            val liveVal = llm.slop.spirals.cv.getCombinedModulatorValue(midiMods).coerceIn(-1f, 1f)
            // Map the -1..1 modulator value to the parameter's visual range for display
            val displayValue = if (param.meterType == llm.slop.spirals.parameters.MeterType.BIPOLAR) {
                // -1..1 maps to min..max
                val range = param.maxClamp - param.minClamp
                param.minClamp + ((liveVal + 1f) / 2f) * range
            } else {
                // CV 0..1 maps to min..max (assuming monopolar modulators typically pulse 0..1, though natively they are -1..1)
                // Actually, just map the positive part for monopolar visualization
                val range = param.maxClamp - param.minClamp
                param.minClamp + ((liveVal.coerceAtLeast(0f))) * range
            }
            
            drawKnobMeter(
                dl = dl, x = midiX, y = midiY, r = r,
                value = displayValue, min = param.minClamp, max = param.maxClamp,
                meterType = param.meterType,
                baseValue = null, baseMin = null, baseMax = null,
                color = getCvColor("midi"),
                bgCol = midiBgCol, borderCol = midiBorderCol,
                isBypassed = isMidiBypassed,
                isHoveredRow = isHoveredRow, isHoveredCol = isMidiHoveredCol
            )
        } else {
            // Draw empty cell
            dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, midiBgCol, 3f)
            if (isMidiCrosshair) {
                dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), 3f)
            } else if (isMidiHoveredCol) {
                dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f), 3f)
            }
            val border = if (isMidiCrosshair) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f) else midiBorderCol
            dl.addRect(midiX, midiY, midiX + CELL, midiY + CELL, border, 3f)
        }

        // 3. CV cells
        val cvCols = getCvColumns()
        for (cvId in cvCols) {
            val cellId = PatchCellId(paramKey, cvId)
            val isSelected = state.selectedCell == cellId
            val activeMods = if (cvId == "audio") {
                param.modulators.filter { it.sourceId in setOf("audio_amp", "audio_bass", "audio_mid", "audio_high") }
            } else if (cvId == "trigger") {
                param.modulators.filter { it.sourceId in setOf("trigger_onset", "trigger_accent") }
            } else {
                param.modulators.filter { it.sourceId == cvId }
            }
            val hasModulator = activeMods.any { !it.bypassed }
            val isBypassed = activeMods.isNotEmpty() && activeMods.all { it.bypassed }

            val x = gridStartX + labelColW + getColumnOffset(cvId)
            val y = rowScreenY

            val isHoveredCol = mousePos.x >= x && mousePos.x <= (x + CELL)
            val isCrosshair = isHoveredRow && isHoveredCol

            val isTarget = state.midiLearnTarget?.let {
                it is MidiLearnTarget.GridCell && it.cellId == cellId
            } ?: false

            ImGui.setCursorScreenPos(x, y)
            ImGui.invisibleButton("##cell_$cvId", CELL, CELL)
            if (ImGui.isItemClicked()) {
                if (state.isMidiLearnMode) {
                    state.midiLearnTarget = MidiLearnTarget.GridCell(cellId, param)
                } else {
                    state.select(cellId, param)
                }
            }
            if (ImGui.beginPopupContextItem("cell_menu_$paramKey-$cvId")) {
                if (ImGui.menuItem("Copy Cell Modulators")) {
                    ClipboardManager.cellClipboard = CellClipboardData(paramKey, cvId, activeMods.map { it.toDto() })
                }
                val hasCellClip = ClipboardManager.cellClipboard != null
                if (ImGui.menuItem("Paste Modulator(s)", null, false, hasCellClip)) {
                    ClipboardManager.cellClipboard?.let { ClipboardManager.applyCellClipboard(param, cvId, it) }
                }
                if (activeMods.isNotEmpty()) {
                    if (ImGui.menuItem("Clear Modulator(s)")) {
                        param.modulators.removeAll(activeMods)
                    }
                    if (ImGui.menuItem(if (isBypassed) "Enable Modulator(s)" else "Bypass Modulator(s)")) {
                        val updated = param.modulators.map { mod ->
                            if (activeMods.any { it.id == mod.id }) {
                                mod.copy(bypassed = !mod.bypassed)
                            } else mod
                        }
                        param.modulators.clear()
                        param.modulators.addAll(updated)
                    }
                }
                ImGui.endPopup()
            }

            val bgCol = when {
                isTarget      -> ImGui.colorConvertFloat4ToU32(0.0f, 0.4f, 0.5f, 1f) // listening target
                isSelected    -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
                hasModulator  -> ImGui.colorConvertFloat4ToU32(0.05f, 0.15f, 0.2f, 1f)
                else          -> getCvColor(cvId, 0.05f)
            }
            val borderCol = when {
                isTarget     -> ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 1.0f, 1f) // bright cyan
                isSelected   -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
                hasModulator -> ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.7f, 0.8f)
                else         -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
            }

            if (hasModulator || isBypassed) {
                val liveVal = llm.slop.spirals.cv.getCombinedModulatorValue(activeMods).coerceIn(-1f, 1f)
                val displayValue = if (param.meterType == llm.slop.spirals.parameters.MeterType.BIPOLAR) {
                    val range = param.maxClamp - param.minClamp
                    param.minClamp + ((liveVal + 1f) / 2f) * range
                } else {
                    val range = param.maxClamp - param.minClamp
                    param.minClamp + ((liveVal.coerceAtLeast(0f))) * range
                }
                
                drawKnobMeter(
                    dl = dl, x = x, y = y, r = r,
                    value = displayValue, min = param.minClamp, max = param.maxClamp,
                    meterType = param.meterType,
                    baseValue = null, baseMin = null, baseMax = null,
                    color = getCvColor(cvId),
                    bgCol = bgCol, borderCol = borderCol,
                    isBypassed = isBypassed,
                    isHoveredRow = isHoveredRow, isHoveredCol = isHoveredCol
                )
            } else {
                dl.addRectFilled(x, y, x + CELL, y + CELL, bgCol, 3f)
                if (isCrosshair) {
                    dl.addRectFilled(x, y, x + CELL, y + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), 3f)
                } else if (isHoveredCol) {
                    dl.addRectFilled(x, y, x + CELL, y + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f), 3f)
                }
                val border = if (isCrosshair) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f) else borderCol
                dl.addRect(x, y, x + CELL, y + CELL, border, 3f)
            }
        }

        ImGui.popID()
        ImGui.setCursorPos(rowX, rowY + CELL)
    }

    private fun drawKnobMeter(
        dl: ImDrawList,
        x: Float, y: Float, r: Float,
        value: Float,
        min: Float, max: Float,
        meterType: llm.slop.spirals.parameters.MeterType,
        baseValue: Float?,
        baseMin: Float?,
        baseMax: Float?,
        color: Int,
        bgCol: Int,
        borderCol: Int,
        isBypassed: Boolean = false,
        isHoveredRow: Boolean = false,
        isHoveredCol: Boolean = false
    ) {
        val cx = x + r
        val cy = y + r

        dl.addRectFilled(x, y, x + r * 2f, y + r * 2f, bgCol, 3f)
        
        if (isHoveredRow && isHoveredCol) {
            dl.addRectFilled(x, y, x + r * 2f, y + r * 2f, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), 3f)
        } else if (isHoveredCol) {
            dl.addRectFilled(x, y, x + r * 2f, y + r * 2f, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f), 3f)
        }

        val border = if (isHoveredRow && isHoveredCol) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f) else borderCol
        dl.addRect(x, y, x + r * 2f, y + r * 2f, border, 3f)

        val trackRadius = r - 5f
        val trackCol = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, if (isBypassed) 0.2f else 0.4f)
        val fillCol = if (isBypassed) ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.5f) else color

        val aMin = PI.toFloat() * 0.75f  // 135 deg
        val aMax = PI.toFloat() * 2.25f  // 405 deg
        val aCenter = PI.toFloat() * 1.5f // 270 deg

        val range = max - min
        val normalized = if (range == 0f) 0.5f else ((value - min) / range).coerceIn(0f, 1f)

        when (meterType) {
            llm.slop.spirals.parameters.MeterType.ENDLESS, llm.slop.spirals.parameters.MeterType.DISCRETE -> {
                dl.addCircle(cx, cy, trackRadius, trackCol, 32, 1.5f)
                val angle = (PI / 2.0) + normalized * 2.0 * PI
                val dotX = cx + trackRadius * cos(angle).toFloat()
                val dotY = cy + trackRadius * sin(angle).toFloat()
                dl.addCircleFilled(dotX, dotY, 3f, fillCol)

                if (baseValue != null) {
                    val bNorm = if (range == 0f) 0.5f else ((baseValue - min) / range).coerceIn(0f, 1f)
                    val bAngle = (PI / 2.0) + bNorm * 2.0 * PI
                    val bX = cx + trackRadius * cos(bAngle).toFloat()
                    val bY = cy + trackRadius * sin(bAngle).toFloat()
                    val bCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)
                    dl.addCircleFilled(bX, bY, 3f, bCol)
                }
            }
            llm.slop.spirals.parameters.MeterType.MONOPOLAR -> {
                dl.pathArcTo(cx, cy, trackRadius, aMin, aMax, 32)
                dl.pathStroke(trackCol, 0, 1.5f)

                if (normalized > 0f) {
                    val fillAngle = aMin + normalized * (aMax - aMin)
                    dl.pathArcTo(cx, cy, trackRadius, aMin, fillAngle, 32)
                    dl.pathStroke(fillCol, 0, 2.5f)
                }

                val valAngle = aMin + normalized * (aMax - aMin)
                val dotX = cx + trackRadius * cos(valAngle)
                val dotY = cy + trackRadius * sin(valAngle)
                dl.addCircleFilled(dotX, dotY, 3f, fillCol)

                if (baseValue != null) {
                    val bNorm = if (range == 0f) 0.5f else ((baseValue - min) / range).coerceIn(0f, 1f)
                    val bAngle = aMin + bNorm * (aMax - aMin)
                    val bX = cx + trackRadius * cos(bAngle)
                    val bY = cy + trackRadius * sin(bAngle)
                    val bCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)
                    dl.addCircleFilled(bX, bY, 2.5f, bCol)

                    if (baseMin != null && baseMax != null && baseMin != baseMax) {
                        val rMinNorm = if (range == 0f) 0.5f else ((baseMin - min) / range).coerceIn(0f, 1f)
                        val rMaxNorm = if (range == 0f) 0.5f else ((baseMax - min) / range).coerceIn(0f, 1f)
                        val rMinA = aMin + rMinNorm * (aMax - aMin)
                        val rMaxA = aMin + rMaxNorm * (aMax - aMin)
                        val rangeCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 0.4f)
                        dl.pathArcTo(cx, cy, trackRadius - 3f, rMinA, rMaxA, 16)
                        dl.pathStroke(rangeCol, 0, 2f)
                    }
                }
            }
            llm.slop.spirals.parameters.MeterType.BIPOLAR -> {
                dl.pathArcTo(cx, cy, trackRadius, aMin, aMax, 32)
                dl.pathStroke(trackCol, 0, 1.5f)

                val cX = cx + (trackRadius - 2f) * cos(aCenter)
                val cY = cy + (trackRadius - 2f) * sin(aCenter)
                val cX2 = cx + (trackRadius + 2f) * cos(aCenter)
                val cY2 = cy + (trackRadius + 2f) * sin(aCenter)
                dl.addLine(cX, cY, cX2, cY2, trackCol, 1.5f)

                if (normalized != 0.5f) {
                    val valAngle = aMin + normalized * (aMax - aMin)
                    if (normalized > 0.5f) {
                        dl.pathArcTo(cx, cy, trackRadius, aCenter, valAngle, 16)
                    } else {
                        dl.pathArcTo(cx, cy, trackRadius, valAngle, aCenter, 16)
                    }
                    dl.pathStroke(fillCol, 0, 2.5f)
                }

                val valAngle = aMin + normalized * (aMax - aMin)
                val dotX = cx + trackRadius * cos(valAngle)
                val dotY = cy + trackRadius * sin(valAngle)
                dl.addCircleFilled(dotX, dotY, 3f, fillCol)

                if (baseValue != null) {
                    val bNorm = if (range == 0f) 0.5f else ((baseValue - min) / range).coerceIn(0f, 1f)
                    val bAngle = aMin + bNorm * (aMax - aMin)
                    val bX = cx + trackRadius * cos(bAngle)
                    val bY = cy + trackRadius * sin(bAngle)
                    val bCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)
                    dl.addCircleFilled(bX, bY, 2.5f, bCol)

                    if (baseMin != null && baseMax != null && baseMin != baseMax) {
                        val rMinNorm = if (range == 0f) 0.5f else ((baseMin - min) / range).coerceIn(0f, 1f)
                        val rMaxNorm = if (range == 0f) 0.5f else ((baseMax - min) / range).coerceIn(0f, 1f)
                        val rMinA = aMin + rMinNorm * (aMax - aMin)
                        val rMaxA = aMin + rMaxNorm * (aMax - aMin)
                        val rangeCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 0.4f)
                        dl.pathArcTo(cx, cy, trackRadius - 3f, rMinA, rMaxA, 16)
                        dl.pathStroke(rangeCol, 0, 2f)
                    }
                }
            }
        }
    }

    private fun handleKeyboardShortcuts(state: PatchGridState, mixer: Mixer) {
        val io = ImGui.getIO()
        val ctrl = io.keyCtrl
        if (ctrl) {
            val cKey = ImGui.getKeyIndex(ImGuiKey.C)
            val vKey = ImGui.getKeyIndex(ImGuiKey.V)

            if (ImGui.isKeyPressed(cKey)) {
                val cell = state.selectedCell
                val param = state.selectedParam
                if (cell != null && param != null && cell.cvSourceId != "base" && cell.cvSourceId != "final") {
                    val activeMods = if (cell.cvSourceId == "audio") {
                        param.modulators.filter { it.sourceId in setOf("audio_amp", "audio_bass", "audio_mid", "audio_high") }
                    } else if (cell.cvSourceId == "trigger") {
                        param.modulators.filter { it.sourceId in setOf("trigger_onset", "trigger_accent") }
                    } else {
                        param.modulators.filter { it.sourceId == cell.cvSourceId }
                    }
                    ClipboardManager.cellClipboard = CellClipboardData(cell.paramKey, cell.cvSourceId, activeMods.map { it.toDto() })
                }
            }
            if (ImGui.isKeyPressed(vKey)) {
                val cell = state.selectedCell
                val param = state.selectedParam
                if (cell != null && param != null && cell.cvSourceId != "base" && cell.cvSourceId != "final") {
                    ClipboardManager.cellClipboard?.let { clip ->
                        ClipboardManager.applyCellClipboard(param, cell.cvSourceId, clip)
                    }
                }
            }
        }
    }
}

fun Deck.randomizeModulators() {
    val allParams = mutableListOf<llm.slop.spirals.parameters.ModulatableParameter>()
    allParams.addAll(this.source.parameters.values)
    allParams.add(this.source.globalAlpha)
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
