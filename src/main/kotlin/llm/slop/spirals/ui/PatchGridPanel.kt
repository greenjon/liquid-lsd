package llm.slop.spirals.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.Mixer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the Patch Grid panel. Rows = grouped ModulatableParameters.
 * Columns = CV sources. Each intersection is a clickable cell.
 */
object PatchGridPanel {

    // CV columns shown in the grid, in display order
    private val CV_COLUMNS = listOf(
        "amp", "bass", "mid", "high", "bassFlux", "onset", "accent",
        "beatPhase", "lfo", "sampleAndHold"
    )
    private val CV_LABELS = listOf(
        "AMP", "BASS", "MID", "HIGH", "FLUX", "ONSET", "ACCENT",
        "BEAT", "LFO", "RAND"
    )

    // Vertical version of labels for space-saving column headers
    private val CV_LABELS_VERTICAL = CV_LABELS.map { it.toList().joinToString("\n") }


    // Size of each cell square (px)
    private const val CELL = 35f
    private const val CELL_PAD = 5f

    private var gridStartX = 0f

    fun draw(mixer: Mixer, state: PatchGridState) {
        val avail = ImGui.getContentRegionAvailX()
        gridStartX = ImGui.getCursorScreenPosX()
        val extraColsW = 2 * (CELL + CELL_PAD)
        val labelColW = (avail - CV_COLUMNS.size * (CELL + CELL_PAD) - extraColsW).coerceAtLeast(120f)

        drawColumnHeaders(labelColW, state, mixer)
        ImGui.separator()
        ImGui.spacing()

        drawGroup("Mixer", state) {
            drawParamRow("crossfade",  "Mixer/crossfade",  mixer.crossfade,  state, labelColW)
            drawParamRow("master α",   "Mixer/masterAlpha", mixer.masterAlpha, state, labelColW)
        }

        drawDeckGroup("Deck A", mixer.deckA, state, labelColW)
        drawDeckGroup("Deck B", mixer.deckB, state, labelColW)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun drawColumnHeaders(labelColW: Float, state: PatchGridState, mixer: Mixer) {
        val dl = ImGui.getWindowDrawList()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        // Calculate the maximum height needed for the vertical labels
        var maxH = 0f
        UITheme.withFont(UITheme.FontLevel.CAPTION) {
            for (label in CV_LABELS_VERTICAL) {
                val h = ImGui.calcTextSize(label).y
                if (h > maxH) maxH = h
            }
            val hFinal = ImGui.calcTextSize("F\nI\nN\nA\nL").y
            val hBase = ImGui.calcTextSize("B\nA\nS\nE").y
            if (hFinal > maxH) maxH = hFinal
            if (hBase > maxH) maxH = hBase
        }
        
        // Reserve vertical space for headers
        ImGui.dummy(10f, maxH + 5f)
        val afterHeadersY = ImGui.getCursorScreenPosY()

        // Draw Randomize All button in the top-left empty space of the column headers
        ImGui.setCursorScreenPos(startX, startY + (maxH + 5f - 24f) * 0.5f)
        if (ImGui.button("🎲 Randomize All", labelColW - CELL_PAD, 24f)) {
            mixer.deckA.randomizeModulators()
            mixer.deckB.randomizeModulators()
            listOf(mixer.crossfade, mixer.masterAlpha).forEach { param ->
                val randomized = param.modulators.map { it.randomizeActiveValues() }
                param.modulators.clear()
                param.modulators.addAll(randomized)
                param.randomizeBaseValue()
            }
            // Refresh selection
            val cell = state.selectedCell
            val param = state.selectedParam
            if (cell != null && param != null) {
                state.editingModulator = param.modulators.find { it.sourceId == cell.cvSourceId }
            }
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

        // Draw each column header vertically
        for ((idx, label) in CV_LABELS_VERTICAL.withIndex()) {
            val colX = startX + labelColW + 2 * (CELL + CELL_PAD) + idx * (CELL + CELL_PAD)
            dl.addLine(colX - CELL_PAD * 0.5f, startY, colX - CELL_PAD * 0.5f, startY + maxH + 5f, lineCol, 1f)
            
            var tw = 0f
            UITheme.withFont(UITheme.FontLevel.CAPTION) { tw = ImGui.calcTextSize(label).x }
            val offX = ((CELL - tw) * 0.5f).coerceAtLeast(0f)
            ImGui.setCursorScreenPos(colX + offX, startY)
            UITheme.caption(label)
        }
        
        // Draw final separator line on the right edge
        val rightColX = startX + labelColW + (CV_LABELS_VERTICAL.size + 2) * (CELL + CELL_PAD)
        dl.addLine(rightColX - CELL_PAD * 0.5f, startY, rightColX - CELL_PAD * 0.5f, startY + maxH + 5f, lineCol, 1f)
        
        // Restore cursor to where the dummy left off
        ImGui.setCursorScreenPos(startX, afterHeadersY)
    }

    private inline fun drawGroup(label: String, state: PatchGridState, block: () -> Unit) {
        val open = state.groupOpen.getValue(label)
        val flags = ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.SpanFullWidth
        if (ImGui.treeNodeEx(label, if (open) flags else ImGuiTreeNodeFlags.None)) {
            state.groupOpen[label] = true
            block()
            ImGui.treePop()
        } else {
            state.groupOpen[label] = false
        }
    }

    private inline fun drawSubGroup(label: String, state: PatchGridState, block: () -> Unit) {
        val key = "sub_$label"
        val flags = ImGuiTreeNodeFlags.DefaultOpen or ImGuiTreeNodeFlags.SpanFullWidth
        if (ImGui.treeNodeEx(label, flags)) {
            state.groupOpen[key] = true
            block()
            ImGui.treePop()
        } else {
            state.groupOpen[key] = false
        }
    }

    private fun drawDeckGroup(deckLabel: String, deck: Deck, state: PatchGridState, labelColW: Float) {
        drawGroup(deckLabel, state) {
            val mandala = deck.source as? Mandala

            if (mandala != null) {
                drawSubGroup("Geometry", state) {
                    drawParamRow("L1",       "$deckLabel/Geometry/L1",       mandala.parameters["L1"]!!,       state, labelColW)
                    drawParamRow("L2",       "$deckLabel/Geometry/L2",       mandala.parameters["L2"]!!,       state, labelColW)
                    drawParamRow("L3",       "$deckLabel/Geometry/L3",       mandala.parameters["L3"]!!,       state, labelColW)
                    drawParamRow("L4",       "$deckLabel/Geometry/L4",       mandala.parameters["L4"]!!,       state, labelColW)
                    drawParamRow("Scale",    "$deckLabel/Geometry/Scale",    mandala.parameters["Scale"]!!,    state, labelColW)
                    drawParamRow("Rotation", "$deckLabel/Geometry/Rotation", mandala.parameters["Rotation"]!!, state, labelColW)
                }
                drawSubGroup("Color", state) {
                    drawParamRow("Thickness",  "$deckLabel/Color/Thickness",  mandala.parameters["Thickness"]!!,  state, labelColW)
                    drawParamRow("Hue Offset", "$deckLabel/Color/HueOffset",  mandala.parameters["Hue Offset"]!!, state, labelColW)
                    drawParamRow("Hue Sweep",  "$deckLabel/Color/HueSweep",   mandala.parameters["Hue Sweep"]!!,  state, labelColW)
                    drawParamRow("Depth",      "$deckLabel/Color/Depth",      mandala.parameters["Depth"]!!,      state, labelColW)
                }
                drawParamRow("Gain",  "$deckLabel/Gain",  mandala.globalAlpha, state, labelColW)
                drawParamRow("Scale", "$deckLabel/GScale", mandala.globalScale, state, labelColW)
            }

            drawSubGroup("Feedback", state) {
                drawParamRow("FB Decay",    "$deckLabel/FB/Decay",    deck.fbDecay,    state, labelColW)
                drawParamRow("FB Gain",     "$deckLabel/FB/Gain",     deck.fbGain,     state, labelColW)
                drawParamRow("FB Zoom",     "$deckLabel/FB/Zoom",     deck.fbZoom,     state, labelColW)
                drawParamRow("FB Rotate",   "$deckLabel/FB/Rotate",   deck.fbRotate,   state, labelColW)
                drawParamRow("FB Hue Shift","$deckLabel/FB/HueShift", deck.fbHueShift, state, labelColW)
                drawParamRow("FB Blur",     "$deckLabel/FB/Blur",     deck.fbBlur,     state, labelColW)
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
        labelColW: Float
    ) {
        ImGui.pushID(paramKey)

        // Row label
        val rowX = ImGui.getCursorPosX()
        val rowY = ImGui.getCursorPosY()
        val rowScreenY = ImGui.getCursorScreenPosY()
        
        ImGui.setCursorPosY(rowY + (CELL - ImGui.getTextLineHeight()) * 0.5f)
        UITheme.body(label)
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
        val angle = (3.0 * PI / 2.0) - liveVal * 2.0 * PI
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
        val angleVal = (3.0 * PI / 2.0) - param.baseValue * 2.0 * PI
        val baseDotX = cx + baseCircleR * cos(angleVal).toFloat()
        val baseDotY = cy + baseCircleR * sin(angleVal).toFloat()
        val baseDotCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f) // solid gold
        dl.addCircleFilled(baseDotX, baseDotY, 3f, baseDotCol)
        dl.addRect(baseX, baseY, baseX + CELL, baseY + CELL, baseBorderCol, 3f)

        // 3. CV cells
        for ((colIdx, cvId) in CV_COLUMNS.withIndex()) {
            val cellId = PatchCellId(paramKey, cvId)
            val isSelected = state.selectedCell == cellId
            val modulator = param.modulators.find { it.sourceId == cvId }
            val hasModulator = modulator != null && !modulator.bypassed
            val isBypassed = modulator != null && modulator.bypassed

            val x = gridStartX + labelColW + 2 * (CELL + CELL_PAD) + colIdx * (CELL + CELL_PAD)
            val y = rowScreenY

            ImGui.setCursorScreenPos(x, y)
            ImGui.invisibleButton("##cell_$cvId", CELL, CELL)
            if (ImGui.isItemClicked()) {
                state.select(cellId, param)
            }

            // Cell background
            val bgCol = when {
                isSelected    -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
                hasModulator  -> ImGui.colorConvertFloat4ToU32(0.05f, 0.15f, 0.2f, 1f)
                else          -> ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 1f)
            }
            dl.addRectFilled(x, y, x + CELL, y + CELL, bgCol, 3f)
            val borderCol = when {
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
                    val liveVal = modulator!!.evaluateValue()
                    // Full circle: val goes 0..1 counterclockwise: angle = 3PI/2 - val*2*PI
                    val angle = (3.0 * PI / 2.0) - liveVal * 2.0 * PI
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
}

fun Deck.randomizeModulators() {
    val allParams = mutableListOf<llm.slop.spirals.parameters.ModulatableParameter>()
    allParams.addAll(this.source.parameters.values)
    allParams.add(this.source.globalAlpha)
    allParams.add(this.source.globalScale)
    allParams.add(this.fbDecay)
    allParams.add(this.fbGain)
    allParams.add(this.fbZoom)
    allParams.add(this.fbRotate)
    allParams.add(this.fbHueShift)
    allParams.add(this.fbBlur)

    for (param in allParams) {
        val randomized = param.modulators.map { it.randomizeActiveValues() }
        param.modulators.clear()
        param.modulators.addAll(randomized)
        param.randomizeBaseValue()
    }
}
