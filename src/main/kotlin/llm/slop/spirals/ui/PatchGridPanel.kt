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

    // Size of each cell square (px)
    private const val CELL = 28f
    private const val CELL_PAD = 4f

    fun draw(mixer: Mixer, state: PatchGridState) {
        val avail = ImGui.getContentRegionAvailX()
        val labelColW = (avail - CV_COLUMNS.size * (CELL + CELL_PAD)).coerceAtLeast(120f)

        drawColumnHeaders(labelColW)
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

    private fun drawColumnHeaders(labelColW: Float) {
        ImGui.dummy(labelColW, 1f)
        for (label in CV_LABELS) {
            ImGui.sameLine(0f, CELL_PAD)
            val tw = ImGui.calcTextSize(label).x
            val offset = ((CELL - tw) * 0.5f).coerceAtLeast(0f)
            ImGui.setCursorPosX(ImGui.getCursorPosX() + offset)
            ImGui.textDisabled(label)
            // Manually advance for uniform cell width (ImGui.sameLine handles spacing)
            if (label != CV_LABELS.last()) ImGui.sameLine(0f, CELL - tw - offset + CELL_PAD)
        }
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
        val rowY = ImGui.getCursorPosY()
        ImGui.setCursorPosY(rowY + (CELL - ImGui.getTextLineHeight()) * 0.5f)
        ImGui.text(label)
        ImGui.setCursorPosY(rowY)

        // CV cells
        for ((colIdx, cvId) in CV_COLUMNS.withIndex()) {
            val cellId = PatchCellId(paramKey, cvId)
            val isSelected = state.selectedCell == cellId
            val modulator = param.modulators.find { it.sourceId == cvId }
            val hasModulator = modulator != null && !modulator.bypassed
            val isBypassed = modulator != null && modulator.bypassed

            ImGui.sameLine(labelColW + colIdx * (CELL + CELL_PAD), 0f)
            ImGui.setCursorPosY(rowY)

            // Draw custom cell button via DrawList
            val dl = ImGui.getWindowDrawList()
            val x = ImGui.getCursorScreenPosX()
            val y = ImGui.getCursorScreenPosY()
            val r = CELL * 0.5f
            val cx = x + r
            val cy = y + r

            // Invisible button to capture clicks
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
                val circleR = r - 5f
                val circleCol = if (isBypassed)
                    ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 0.4f)
                else
                    ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 0.25f)
                dl.addCircle(cx, cy, circleR, circleCol, 32, 1.5f)

                if (!isBypassed) {
                    // Moving dot: 6 o'clock = 0.0 and 1.0, noon = 0.5
                    // Map value: 0.0 -> bottom (PI/2*3 = 270°), 0.5 -> top (PI/2 = 90°... wait, noon=top=0.5)
                    // 6 o'clock in standard angles = 90° below = 270deg = 3*PI/2
                    val liveVal = param.value
                    // Map 0..1 -> angle: 6 o'clock (3PI/2) going counterclockwise to noon (PI/2)
                    // At val=0: angle = 3PI/2 (bottom). At val=0.5: angle = PI/2 (top)... going counterclockwise
                    // Full circle: val goes 0->1 counterclockwise: angle = 3PI/2 - val*2*PI
                    val angle = (3.0 * PI / 2.0) - liveVal * 2.0 * PI
                    val dotX = cx + circleR * cos(angle).toFloat()
                    val dotY = cy + circleR * sin(angle).toFloat()
                    val dotCol = ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f)
                    dl.addCircleFilled(dotX, dotY, 3f, dotCol)
                }
            }
        }

        ImGui.popID()
        ImGui.spacing()
    }
}
