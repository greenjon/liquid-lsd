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
            listOf("lfo", "sampleAndHold", "amp", "bass", "mid", "high", "bassFlux", "onset", "accent", "beatPhase")
        } else {
            listOf("lfo", "sampleAndHold")
        }
    }

    private fun getCvLabels(): List<String> {
        return if (UITheme.audioEngineEnabled) {
            listOf("LFO", "RAND", "AMP", "BASS", "MID", "HIGH", "FLUX", "ONSET", "ACCENT", "BEAT")
        } else {
            listOf("LFO", "RAND")
        }
    }


    // Size of each cell square (px)
    private const val CELL = 35f
    private const val CELL_PAD = 5f

    private var gridStartX = 0f

    fun draw(mixer: Mixer, state: PatchGridState) {
        val avail = ImGui.getContentRegionAvailX()
        gridStartX = ImGui.getCursorScreenPosX()
        val extraColsW = 3 * (CELL + CELL_PAD)
        val cvCols = getCvColumns()
        
        // Calculate label column width based on the maximum possible columns (10 audio/CV)
        // so that the grid stays left-aligned instead of expanding when columns are hidden.
        val maxCvCols = 10
        val labelColW = (avail - maxCvCols * (CELL + CELL_PAD) - extraColsW).coerceAtLeast(120f)

        handleKeyboardShortcuts(state, mixer)

        drawColumnHeaders(labelColW, state, mixer)
        ImGui.separator()
        ImGui.spacing()

        drawGroup("Mixer", state) {
            drawParamRow("crossfade",  "Mixer/crossfade",  mixer.crossfade,  state, labelColW, mixer)
            drawParamRow("master α",   "Mixer/masterAlpha", mixer.masterAlpha, state, labelColW, mixer)
            drawParamRow("bloom",      "Mixer/bloom",       mixer.bloom,       state, labelColW, mixer)
        }

        drawDeckGroup("Deck A", mixer.deckA, state, labelColW, mixer)
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
        
        val lineCol = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.5f)

        // Draw FINAL header
        val finalColX = startX + labelColW
        dl.addLine(finalColX - CELL_PAD * 0.5f, startY, finalColX - CELL_PAD * 0.5f, startY + maxH + 5f, lineCol, 1f)
        var twFinal = 0f
        val labelFinal = "F\nI\nN\nA\nL"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twFinal = ImGui.calcTextSize(labelFinal).x }
        var offsetX = ((CELL - twFinal) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(finalColX + offsetX, startY)
        UITheme.caption(labelFinal)

        // Draw BASE header
        val baseColX = startX + labelColW + (CELL + CELL_PAD)
        dl.addLine(baseColX - CELL_PAD * 0.5f, startY, baseColX - CELL_PAD * 0.5f, startY + maxH + 5f, lineCol, 1f)
        var twBase = 0f
        val labelBase = "B\nA\nS\nE"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twBase = ImGui.calcTextSize(labelBase).x }
        offsetX = ((CELL - twBase) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(baseColX + offsetX, startY)
        UITheme.caption(labelBase)

        // Draw MIDI header
        val midiColX = startX + labelColW + 2 * (CELL + CELL_PAD)
        dl.addLine(midiColX - CELL_PAD * 0.5f, startY, midiColX - CELL_PAD * 0.5f, startY + maxH + 5f, lineCol, 1f)
        var twMidi = 0f
        val labelMidi = "M\nI\nD\nI"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twMidi = ImGui.calcTextSize(labelMidi).x }
        offsetX = ((CELL - twMidi) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(midiColX + offsetX, startY)
        UITheme.caption(labelMidi)

        // Draw each column header vertically
        for ((idx, label) in cvLabelsVertical.withIndex()) {
            val colX = startX + labelColW + 3 * (CELL + CELL_PAD) + idx * (CELL + CELL_PAD)
            dl.addLine(colX - CELL_PAD * 0.5f, startY, colX - CELL_PAD * 0.5f, startY + maxH + 5f, lineCol, 1f)
            
            var tw = 0f
            UITheme.withFont(UITheme.FontLevel.CAPTION) { tw = ImGui.calcTextSize(label).x }
            val offX = ((CELL - tw) * 0.5f).coerceAtLeast(0f)
            ImGui.setCursorScreenPos(colX + offX, startY)
            UITheme.caption(label)
        }
        
        // Draw final separator line on the right edge
        val rightColX = startX + labelColW + (cvLabelsVertical.size + 3) * (CELL + CELL_PAD)
        dl.addLine(rightColX - CELL_PAD * 0.5f, startY, rightColX - CELL_PAD * 0.5f, startY + maxH + 5f, lineCol, 1f)
        
        // Restore cursor to where the dummy left off
        ImGui.setCursorScreenPos(startX, afterHeadersY)
    }

    private inline fun drawGroup(label: String, state: PatchGridState, block: () -> Unit) {
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

        if (ImGui.treeNodeEx(label, if (open) flags else ImGuiTreeNodeFlags.None)) {
            val wasClosed = !open
            if (wasClosed && UITheme.autocollapseEnabled) {
                collapseSiblingGroups(label, state)
            }
            state.groupOpen[label] = true
            block()
            ImGui.treePop()
        } else {
            state.groupOpen[label] = false
        }
    }

    private fun collapseSiblingGroups(activeLabel: String, state: PatchGridState) {
        val groups = listOf("Mixer", "Deck A", "Deck B")
        for (g in groups) {
            if (g != activeLabel) {
                state.groupOpen[g] = false
                state.groupNeedsCollapse[g] = true
            }
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
                collapseSiblingSubgroups(parentLabel, key, state)
            }
            state.groupOpen[key] = true
            block()
            ImGui.treePop()
        } else {
            state.groupOpen[key] = false
        }
    }

    private fun collapseSiblingSubgroups(parentLabel: String, activeKey: String, state: PatchGridState) {
        val subgroups = listOf("Geometry", "Color", "Background", "Feedback")
        for (sub in subgroups) {
            val k = "$parentLabel/$sub"
            if (k != activeKey) {
                state.groupOpen[k] = false
                state.groupNeedsCollapse[k] = true
            }
        }
    }

    private fun drawDeckGroup(deckLabel: String, deck: Deck, state: PatchGridState, labelColW: Float, mixer: Mixer) {
        drawGroup(deckLabel, state) {
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
        ImGui.pushID(paramKey)

        // Row label
        val rowX = ImGui.getCursorPosX()
        val rowY = ImGui.getCursorPosY()
        val rowScreenY = ImGui.getCursorScreenPosY()
        
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
            val hasMidiMap = param.mappedMidiId != null
            if (ImGui.menuItem("Clear MIDI mapping", null, false, hasMidiMap)) {
                param.mappedMidiId = null
                param.midiMapMin = 0f
                param.midiMapMax = 1f
            }
            ImGui.endPopup()
        }
        ImGui.setCursorPosY(rowY)

        val dl = ImGui.getWindowDrawList()
        val r = CELL * 0.5f

        // 1. FINAL Cell
        val finalX = gridStartX + labelColW
        val finalY = rowScreenY
        val isFinalSelected = state.selectedCell?.paramKey == paramKey && state.selectedCell?.cvSourceId == "final"
        
        ImGui.setCursorScreenPos(finalX, finalY)
        ImGui.invisibleButton("##final_cell", CELL, CELL)
        if (ImGui.isItemClicked()) {
            state.select(PatchCellId(paramKey, "final"), param)
        }
        
        val finalBgCol = when {
            isFinalSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            else            -> ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 1f)
        }
        dl.addRectFilled(finalX, finalY, finalX + CELL, finalY + CELL, finalBgCol, 3f)
        
        val finalBorderCol = when {
            isFinalSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            else            -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        
        // Draw a circle with a pointer representing the live modulated final value
        val circleR = r - 5f
        val circleCol = ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 0.25f)
        dl.addCircle(finalX + r, finalY + r, circleR, circleCol, 32, 1.5f)

        val liveVal = param.value
        val angle = (PI / 2.0) + liveVal * 2.0 * PI
        val dotX = (finalX + r) + circleR * cos(angle).toFloat()
        val dotY = (finalY + r) + circleR * sin(angle).toFloat()
        val dotCol = ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f)
        dl.addCircleFilled(dotX, dotY, 3f, dotCol)
        dl.addRect(finalX, finalY, finalX + CELL, finalY + CELL, finalBorderCol, 3f)

        // 2. BASE Cell
        val baseX = gridStartX + labelColW + CELL + CELL_PAD
        val baseY = rowScreenY
        val isBaseSelected = state.selectedCell?.paramKey == paramKey && state.selectedCell?.cvSourceId == "base"
        
        ImGui.setCursorScreenPos(baseX, baseY)
        ImGui.invisibleButton("##base_cell", CELL, CELL)
        if (ImGui.isItemClicked()) {
            state.select(PatchCellId(paramKey, "base"), param)
        }
        
        val baseBgCol = when {
            isBaseSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            else           -> ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 1f)
        }
        dl.addRectFilled(baseX, baseY, baseX + CELL, baseY + CELL, baseBgCol, 3f)
        
        val baseBorderCol = when {
            isBaseSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            else           -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        
        // Draw circle with a pointer representing the static baseValue
        val cx = baseX + r
        val cy = baseY + r
        val baseCircleR = r - 5f
        val baseCircleCol = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.4f)
        dl.addCircle(cx, cy, baseCircleR, baseCircleCol, 32, 1.5f)

        // Draw base range vertical bar inside the circle if baseMin != baseMax
        val baseMinY = param.baseMin * (CELL - 6f)
        val baseMaxY = param.baseMax * (CELL - 6f)
        if (param.baseMin != param.baseMax) {
            val rangeCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 0.4f) // transparent gold
            dl.addLine(cx, baseY + CELL - 3f - baseMaxY, cx, baseY + CELL - 3f - baseMinY, rangeCol, 3f)
        }

        // Draw pointer dot for static baseValue
        val angleVal = (PI / 2.0) + param.baseValue * 2.0 * PI
        val baseDotX = cx + baseCircleR * cos(angleVal).toFloat()
        val baseDotY = cy + baseCircleR * sin(angleVal).toFloat()
        val baseDotCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f) // solid gold
        dl.addCircleFilled(baseDotX, baseDotY, 3f, baseDotCol)
        dl.addRect(baseX, baseY, baseX + CELL, baseY + CELL, baseBorderCol, 3f)

        // 2.5 MIDI Cell
        val midiX = gridStartX + labelColW + 2 * (CELL + CELL_PAD)
        val midiY = rowScreenY
        val midiCellId = PatchCellId(paramKey, "midi")
        val isMidiSelected = state.selectedCell == midiCellId
        
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

        // Draw MIDI cell background and border
        val midiBgCol = when {
            isMidiTarget   -> ImGui.colorConvertFloat4ToU32(0.0f, 0.4f, 0.5f, 1f)
            isMidiSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            hasMidiMod     -> ImGui.colorConvertFloat4ToU32(0.05f, 0.15f, 0.2f, 1f)
            else           -> ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 1f)
        }
        dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, midiBgCol, 3f)
        val midiBorderCol = when {
            isMidiTarget   -> ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 1.0f, 1f)
            isMidiSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            hasMidiMod     -> ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.7f, 0.8f)
            else           -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        dl.addRect(midiX, midiY, midiX + CELL, midiY + CELL, midiBorderCol, 3f)

        // Draw dynamic indicator dot for MIDI CC if active
        if (hasMidiMod || isMidiBypassed) {
            val cx = midiX + r
            val cy = midiY + r
            val circleR = r - 5f
            val circleCol = if (isMidiBypassed)
                ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.4f)
            else
                ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 0.25f)
            dl.addCircle(cx, cy, circleR, circleCol, 32, 1.5f)

            if (!isMidiBypassed) {
                val liveVal = llm.slop.spirals.cv.getCombinedModulatorValue(midiMods).coerceIn(-1f, 1f)
                val angle = (PI / 2.0) + liveVal * 2.0 * PI
                val dotX = cx + circleR * cos(angle).toFloat()
                val dotY = cy + circleR * sin(angle).toFloat()
                val dotCol = ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f)
                dl.addCircleFilled(dotX, dotY, 3f, dotCol)
            }
        }

        // 3. CV cells
        val cvCols = getCvColumns()
        for ((colIdx, cvId) in cvCols.withIndex()) {
            val cellId = PatchCellId(paramKey, cvId)
            val isSelected = state.selectedCell == cellId
            val activeMods = param.modulators.filter {
                it.sourceId == cvId
            }
            val hasModulator = activeMods.any { !it.bypassed }
            val isBypassed = activeMods.isNotEmpty() && activeMods.all { it.bypassed }

            val x = gridStartX + labelColW + 3 * (CELL + CELL_PAD) + colIdx * (CELL + CELL_PAD)
            val y = rowScreenY

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
                        val updated = param.modulators.map {
                            if (it.sourceId == cvId) {
                                it.copy(bypassed = !it.bypassed)
                            } else it
                        }
                        param.modulators.clear()
                        param.modulators.addAll(updated)
                    }
                }
                ImGui.endPopup()
            }

            // Cell background
            val bgCol = when {
                isTarget      -> ImGui.colorConvertFloat4ToU32(0.0f, 0.4f, 0.5f, 1f) // listening target
                isSelected    -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
                hasModulator  -> ImGui.colorConvertFloat4ToU32(0.05f, 0.15f, 0.2f, 1f)
                else          -> ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 1f)
            }
            dl.addRectFilled(x, y, x + CELL, y + CELL, bgCol, 3f)
            val borderCol = when {
                isTarget     -> ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 1.0f, 1f) // bright cyan
                isSelected   -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
                hasModulator -> ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.7f, 0.8f)
                else         -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
            }
            dl.addRect(x, y, x + CELL, y + CELL, borderCol, 3f)

            // If a modulator is active, draw the indicator circle + moving dot
            if (hasModulator || isBypassed) {
                val cx = x + r
                val cy = y + r
                val circleR = r - 5f
                val circleCol = if (isBypassed)
                    ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.4f)
                else
                    ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 0.25f)
                dl.addCircle(cx, cy, circleR, circleCol, 32, 1.5f)

                if (!isBypassed) {
                    val liveVal = llm.slop.spirals.cv.getCombinedModulatorValue(activeMods).coerceIn(-1f, 1f)
                    // Full circle: val goes 0..1 clockwise: angle = PI/2 + val*2*PI
                    val angle = (PI / 2.0) + liveVal * 2.0 * PI
                    val dotX = cx + circleR * cos(angle).toFloat()
                    val dotY = cy + circleR * sin(angle).toFloat()
                    val dotCol = ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f)
                    dl.addCircleFilled(dotX, dotY, 3f, dotCol)
                }
            }
        }

        ImGui.popID()
        ImGui.setCursorPos(rowX, rowY + CELL)
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
                    val activeMods = param.modulators.filter { it.sourceId == cell.cvSourceId }
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
