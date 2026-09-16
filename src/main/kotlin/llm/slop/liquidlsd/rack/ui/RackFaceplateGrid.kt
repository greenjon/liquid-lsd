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
        session: llm.slop.liquidlsd.SessionContext,
        unit: RackUnit,
        faceplateWidth: Float,
        faceplateHeight: Float
    ) {
        if (unit.isCollapsed) return

        val paddingX = 12.0f
        val paddingY = 6.0f
        val usableW = faceplateWidth - (paddingX * 2f)
        val colW = (usableW - (COLUMN_GAP * (GRID_COLUMNS - 1))) / GRID_COLUMNS

        ImGui.setCursorPosX(paddingX)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + paddingY)

        if (unit.isMacroCurationOpen) {
            RackUnitMacroCuration.draw(session, unit, usableW)
            return
        }

        // Draw curated active Macro Controls for this unit if any exist
        drawCuratedMacrosRow(session, unit, usableW)

        when (unit) {
            is DeckGeneratorUnit -> drawGeneratorFaceplate(session, unit, usableW, colW, faceplateHeight)
            is FeedbackProcessorUnit -> drawFeedbackFaceplate(session, unit, usableW, colW, faceplateHeight)
            is ISFProcessorUnit -> drawISFFaceplate(session, unit, usableW, colW, faceplateHeight)
            is MixerTransitionUnit -> drawTransitionFaceplate(session, unit, usableW, colW, faceplateHeight)
            else -> drawGenericFaceplate(session, unit, usableW, colW, faceplateHeight)
        }
    }

    private fun drawCuratedMacrosRow(session: llm.slop.liquidlsd.SessionContext, unit: RackUnit, usableW: Float) {
        val activeKnobs = unit.macroBank.knobs.filter { it.bindings.isNotEmpty() || it.label.isNotEmpty() }
        val activeSwitches = unit.macroBank.switches.filter { it.bindings.isNotEmpty() || it.label.isNotEmpty() }

        if (activeKnobs.isEmpty() && activeSwitches.isEmpty()) return

        ImGui.beginGroup()
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2.0f, 2.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8.0f, 2.0f)

        // Render knobs
        for (i in activeKnobs.indices) {
            val knob = activeKnobs[i]
            if (i > 0) ImGui.sameLine(0f, 8f)

            val isSelected = llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId == knob.id
            val isLearning = llm.slop.liquidlsd.macro.MacroLearnState.isControlLearning(knob.id)
            llm.slop.liquidlsd.ui.MacroKnobWidget.draw(
                session = session,
                id = "unit_${unit.id}_knob_$i",
                label = knob.label.ifEmpty { "K${i + 1}" },
                value = knob.value,
                diameter = 40f,
                isSelected = isSelected,
                isLearning = isLearning,
                onSelect = { llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId = knob.id },
                onToggleLearn = {
                    if (isLearning) {
                        llm.slop.liquidlsd.macro.MacroLearnState.cancelLearn()
                    } else {
                        llm.slop.liquidlsd.macro.MacroLearnState.startLearn(knob.id)
                    }
                },
                onChanged = { knob.value = it }
            )
        }

        // Render switches
        if (activeSwitches.isNotEmpty()) {
            if (activeKnobs.isNotEmpty()) ImGui.sameLine(0f, 16f)
            for (i in activeSwitches.indices) {
                val sw = activeSwitches[i]
                if (i > 0) ImGui.sameLine(0f, 6f)

                val swActive = sw.value >= 0.5f
                if (swActive) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.95f, 0.75f, 0.15f, 1.0f)
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.1f, 0.1f, 0.1f, 1.0f)
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.22f, 0.25f, 1.0f)
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.70f, 0.75f, 1.0f)
                }
                val label = sw.label.ifEmpty { "SW ${i + 1}" }
                if (ImGui.button("$label##sw_${unit.id}_$i", 52f, 22f)) {
                    sw.onPress()
                }
                ImGui.popStyleColor(2)
            }
        }

        ImGui.popStyleVar(2)
        ImGui.endGroup()

        // Subtle divider before standard parameters
        val dl = ImGui.getWindowDrawList()
        val sepY = ImGui.getCursorScreenPosY() + 2f
        val startX = ImGui.getCursorScreenPosX()
        val sepCol = ImGui.colorConvertFloat4ToU32(0.20f, 0.22f, 0.26f, 0.6f)
        dl.addLine(startX, sepY, startX + usableW, sepY, sepCol, 1.0f)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 6f)
    }

    private fun drawGeneratorFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: DeckGeneratorUnit,
        usableW: Float,
        colW: Float,
        faceplateHeight: Float
    ) {
        val deck = unit.deck
        val params = unit.getParameters()

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Columns 1 & 2: Confidence Micro-Monitor + Source info
        val col12W = (colW * 2f) + COLUMN_GAP
        val monitorH = (col12W * session.uiTheme.renderAspectRatio).coerceIn(40f, 86f)

        ImGui.beginGroup()
        RackMicroMonitor.draw(session, unit, col12W, monitorH)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 2f)

        ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.70f, 0.75f, 1.0f)
        ImGui.textUnformatted("SRC: ${deck.source.displayName.take(18)}")
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

    private fun drawFeedbackFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: FeedbackProcessorUnit,
        usableW: Float,
        colW: Float,
        faceplateHeight: Float
    ) {
        val params = unit.getParameters()
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Columns 1 & 2: Feedback Confidence Micro-Monitor
        val col12W = (colW * 2f) + COLUMN_GAP
        val monitorH = (col12W * session.uiTheme.renderAspectRatio).coerceIn(40f, 86f)

        ImGui.beginGroup()
        RackMicroMonitor.draw(session, unit, col12W, monitorH)
        ImGui.pushStyleColor(ImGuiCol.Text, 0.50f, 0.55f, 0.60f, 1.0f)
        ImGui.textUnformatted("OPTICAL LOOP")
        ImGui.popStyleColor()
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 3 to 8: 6 high-impact feedback parameters (Gain, Zoom, Rotate, Decay, Hue, Blur)
        val remainingW = usableW - col12W - COLUMN_GAP
        val paramColW = (remainingW - (COLUMN_GAP * 2f)) / 3f
        val activeParams = params.take(6)

        ImGui.beginGroup()
        for (i in activeParams.indices) {
            if (i > 0 && i % 3 != 0) {
                ImGui.sameLine(0f, COLUMN_GAP)
            }
            drawParamSlider(activeParams[i], paramColW, "fb_${unit.id}_$i")
        }
        ImGui.endGroup()

        ImGui.popStyleVar(2)
    }

    private fun drawISFFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: ISFProcessorUnit,
        usableW: Float,
        colW: Float,
        faceplateHeight: Float
    ) {
        val filter = unit.filter
        val params = unit.getParameters()

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Column 1 & 2: Confidence Micro-Monitor + Dry/Wet slider
        val col12W = (colW * 2f) + COLUMN_GAP
        val monitorH = (col12W * session.uiTheme.renderAspectRatio).coerceIn(36f, 64f)

        ImGui.beginGroup()
        RackMicroMonitor.draw(session, unit, col12W, monitorH)
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

    private fun drawTransitionFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: MixerTransitionUnit,
        usableW: Float,
        colW: Float,
        faceplateHeight: Float
    ) {
        val mixer = unit.mixer
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Columns 1 & 2: Master Output Confidence Micro-Monitor
        val col12W = (colW * 2f) + COLUMN_GAP
        val monitorH = (col12W * session.uiTheme.renderAspectRatio).coerceIn(40f, 86f)

        ImGui.beginGroup()
        RackMicroMonitor.draw(session, unit, col12W, monitorH)
        ImGui.pushStyleColor(ImGuiCol.Text, 0.50f, 0.55f, 0.60f, 1.0f)
        ImGui.textUnformatted("MASTER OUT")
        ImGui.popStyleColor()
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 3 to 5: Crossfader slider (Deck A <-> Deck B)
        val crossfaderW = (colW * 3f) + (COLUMN_GAP * 2f)
        ImGui.beginGroup()
        drawParamSlider(mixer.crossfade, crossfaderW, "crossfade_${unit.id}", customLabel = "CROSSFADER [A <-> B]")
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 6 to 8: Mode, Master Alpha, Bloom
        val masterColW = colW
        drawParamSlider(mixer.mode, masterColW, "mm_${unit.id}", customLabel = "MODE")
        ImGui.sameLine(0f, COLUMN_GAP)
        drawParamSlider(mixer.masterAlpha, masterColW, "ma_${unit.id}", customLabel = "ALPHA")
        ImGui.sameLine(0f, COLUMN_GAP)
        drawParamSlider(mixer.bloom, masterColW, "mb_${unit.id}", customLabel = "BLOOM")

        ImGui.popStyleVar(2)
    }

    private fun drawGenericFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: RackUnit,
        usableW: Float,
        colW: Float,
        faceplateHeight: Float
    ) {
        val params = unit.getParameters()
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Columns 1 & 2: Confidence Micro-Monitor
        val col12W = (colW * 2f) + COLUMN_GAP
        val monitorH = (col12W * session.uiTheme.renderAspectRatio).coerceIn(40f, 86f)

        ImGui.beginGroup()
        RackMicroMonitor.draw(session, unit, col12W, monitorH)
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 3 to 8: Up to 6 parameters
        val remainingW = usableW - col12W - COLUMN_GAP
        val activeParams = params.take(6)
        val paramColW = if (activeParams.isNotEmpty()) {
            (remainingW - (COLUMN_GAP * (activeParams.size - 1))) / activeParams.size
        } else {
            remainingW
        }

        if (activeParams.isEmpty()) {
            ImGui.textDisabled("No exposed faceplate parameters")
        } else {
            ImGui.beginGroup()
            for (i in activeParams.indices) {
                if (i > 0) ImGui.sameLine(0f, COLUMN_GAP)
                drawParamSlider(activeParams[i], paramColW, "gen_${unit.id}_$i")
            }
            ImGui.endGroup()
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
