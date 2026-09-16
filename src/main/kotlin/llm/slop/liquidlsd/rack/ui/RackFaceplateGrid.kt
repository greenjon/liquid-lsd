package llm.slop.liquidlsd.rack.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rack.DeckGeneratorUnit
import llm.slop.liquidlsd.rack.FeedbackProcessorUnit
import llm.slop.liquidlsd.rack.GenericRackUnit
import llm.slop.liquidlsd.rack.ISFProcessorUnit
import llm.slop.liquidlsd.rack.MixerTransitionUnit
import llm.slop.liquidlsd.rack.RackUnit

/**
 * Grid-based faceplate layout system snapping parameter controls into an 8-column modular grid.
 */
object RackFaceplateGrid {

    const val GRID_COLUMNS = 8
    const val COLUMN_GAP = 6.0f

    fun drawFaceplate(
        unit: RackUnit,
        faceplateWidth: Float,
        faceplateHeight: Float
    ) {
        if (unit.isCollapsed) return

        val paddingX = 12.0f
        val paddingY = 8.0f
        val usableW = faceplateWidth - (paddingX * 2f)
        val colW = (usableW - (COLUMN_GAP * (GRID_COLUMNS - 1))) / GRID_COLUMNS

        ImGui.setCursorPosX(paddingX)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + paddingY)

        when (unit) {
            is DeckGeneratorUnit -> drawGeneratorFaceplate(unit, usableW, colW)
            is FeedbackProcessorUnit -> drawFeedbackFaceplate(unit, usableW, colW)
            is ISFProcessorUnit -> drawISFFaceplate(unit, usableW, colW)
            is MixerTransitionUnit -> drawTransitionFaceplate(unit, usableW, colW)
            else -> drawGenericFaceplate(unit, usableW, colW)
        }
    }

    private fun drawGeneratorFaceplate(unit: DeckGeneratorUnit, usableW: Float, colW: Float) {
        val deck = unit.deck
        val params = unit.getParameters()

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Column 1 & 2: Source info & 3D toggle
        val col12W = (colW * 2f) + COLUMN_GAP
        ImGui.beginGroup()
        ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.70f, 0.75f, 1.0f)
        ImGui.textUnformatted("SOURCE: ${deck.source.displayName}")
        ImGui.popStyleColor()

        // 3D View mode indicator
        val is3D = deck.source.is3D
        if (is3D) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 1.0f, 1.0f)
            ImGui.textUnformatted("MODE: 3D Raymarch")
            ImGui.popStyleColor()
        } else {
            val v3d = deck.view3DMode.value.toInt()
            val modeName = when (v3d) {
                1 -> "Tri-Axial"
                2 -> "Cube Cage"
                3 -> "Hex-Planar"
                4 -> "Tetra Kaleido"
                else -> "2D Flat"
            }
            ImGui.textUnformatted("VIEW: $modeName")
        }
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 3 to 8: Parameters mapped into 2 rows of 3 columns
        val remainingW = usableW - col12W - COLUMN_GAP
        val paramColW = (remainingW - (COLUMN_GAP * 2f)) / 3f

        val activeParams = params.take(6)
        ImGui.beginGroup()
        for (i in activeParams.indices) {
            if (i > 0 && i % 3 != 0) {
                ImGui.sameLine(0f, COLUMN_GAP)
            }
            val param = activeParams[i]
            drawParamSlider(param, paramColW, "gen_${unit.id}_$i")
        }
        ImGui.endGroup()

        ImGui.popStyleVar(2)
    }

    private fun drawFeedbackFaceplate(unit: FeedbackProcessorUnit, usableW: Float, colW: Float) {
        val params = unit.getParameters()
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // 8 parameters across 8 columns
        val activeParams = params.take(8)
        for (i in activeParams.indices) {
            if (i > 0) ImGui.sameLine(0f, COLUMN_GAP)
            drawParamSlider(activeParams[i], colW, "fb_${unit.id}_$i")
        }

        ImGui.popStyleVar(2)
    }

    private fun drawISFFaceplate(unit: ISFProcessorUnit, usableW: Float, colW: Float) {
        val filter = unit.filter
        val params = unit.getParameters()

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Column 1 & 2: Dry/Wet slider
        val col12W = (colW * 2f) + COLUMN_GAP
        ImGui.beginGroup()
        drawParamSlider(filter.dryWet, col12W, "drywet_${unit.id}", customLabel = "DRY / WET")
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 3 to 8: Filter parameters (up to 6)
        val remainingW = usableW - col12W - COLUMN_GAP
        val filterParams = filter.parameters.values.take(6).toList()
        val paramColW = if (filterParams.isNotEmpty()) {
            (remainingW - (COLUMN_GAP * (filterParams.size - 1))) / filterParams.size
        } else {
            remainingW
        }

        ImGui.beginGroup()
        for (i in filterParams.indices) {
            if (i > 0) ImGui.sameLine(0f, COLUMN_GAP)
            drawParamSlider(filterParams[i], paramColW, "isf_${unit.id}_$i")
        }
        ImGui.endGroup()

        ImGui.popStyleVar(2)
    }

    private fun drawTransitionFaceplate(unit: MixerTransitionUnit, usableW: Float, colW: Float) {
        val mixer = unit.mixer
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Columns 1 to 4: Crossfader slider (Deck A <-> Deck B)
        val crossfaderW = (colW * 4f) + (COLUMN_GAP * 3f)
        ImGui.beginGroup()
        drawParamSlider(mixer.crossfade, crossfaderW, "crossfade_${unit.id}", customLabel = "CROSSFADER [A <-> B]")
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 5 to 8: Mode, Master Alpha, Bloom
        val masterColW = colW
        drawParamSlider(mixer.mode, masterColW, "mm_${unit.id}", customLabel = "MODE")
        ImGui.sameLine(0f, COLUMN_GAP)
        drawParamSlider(mixer.masterAlpha, masterColW, "ma_${unit.id}", customLabel = "ALPHA")
        ImGui.sameLine(0f, COLUMN_GAP)
        drawParamSlider(mixer.bloom, masterColW, "mb_${unit.id}", customLabel = "BLOOM")

        ImGui.popStyleVar(2)
    }

    private fun drawGenericFaceplate(unit: RackUnit, usableW: Float, colW: Float) {
        val params = unit.getParameters().take(GRID_COLUMNS)
        if (params.isEmpty()) {
            ImGui.textDisabled("No exposed faceplate parameters")
            return
        }
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        for (i in params.indices) {
            if (i > 0) ImGui.sameLine(0f, COLUMN_GAP)
            drawParamSlider(params[i], colW, "gen_${unit.id}_$i")
        }

        ImGui.popStyleVar(2)
    }

    private fun drawParamSlider(
        param: ModulatableParameter,
        width: Float,
        idSuffix: String,
        customLabel: String? = null
    ) {
        ImGui.pushID(idSuffix)
        ImGui.beginGroup()

        // Parameter label
        val label = customLabel ?: "Param"
        ImGui.pushStyleColor(ImGuiCol.Text, 0.70f, 0.75f, 0.80f, 1.0f)
        ImGui.textUnformatted(label.take(14))
        ImGui.popStyleColor()

        // Slider
        ImGui.setNextItemWidth(width)
        val arr = floatArrayOf(param.baseValue)
        if (ImGui.sliderFloat("##val", arr, param.minClamp, param.maxClamp, "%.2f")) {
            param.baseValue = arr[0]
        }

        ImGui.endGroup()
        ImGui.popID()
    }
}
