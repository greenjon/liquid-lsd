package llm.slop.liquidlsd.rack.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rack.MixerTransitionUnit
import llm.slop.liquidlsd.rack.RackUnit
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.ui.MacroKnobWidget
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip

/**
 * Faceplate layout for rack units: a fixed 4-column x 2-row knob grid + 2-column x 2-row button
 * grid (mirroring Classic view's [llm.slop.liquidlsd.ui.MacroPanel.drawMacroGrid]) with a
 * confidence preview monitor to the right. Master additionally renders its crossfader/mode/alpha/
 * bloom row below.
 */
object RackFaceplateGrid {

    const val COLUMN_GAP = 6.0f

    fun drawFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: RackUnit,
        faceplateWidth: Float,
        faceplateHeight: Float,
        renderer: Renderer? = null
    ) {
        if (unit.isCollapsed) return

        val paddingX = 12.0f
        val paddingY = 6.0f
        val usableW = faceplateWidth - (paddingX * 2f)

        // Relative to the caller's current cursor (not an absolute window-relative jump) so this
        // renders correctly whether the unit occupies the full bay width or one half of a
        // two-column row -- see RackPanel's per-unit ImGui.setCursorScreenPos before this call.
        ImGui.setCursorPosX(ImGui.getCursorPosX() + paddingX)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + paddingY)

        drawKnobButtonPreviewRow(session, unit, usableW, renderer)

        if (unit is MixerTransitionUnit) {
            drawMasterExtraControls(unit, usableW)
        }
    }

    private fun drawKnobButtonPreviewRow(
        session: llm.slop.liquidlsd.SessionContext,
        unit: RackUnit,
        usableW: Float,
        renderer: Renderer?
    ) {
        val bank = unit.macroBank
        val dl = ImGui.getWindowDrawList()

        val knobCols = 4
        val switchCols = 2
        val totalCols = knobCols + switchCols

        // Reserve ~60% of the width for the 4x2 knob + 2x2 button grid; the rest goes to the
        // preview monitor, letterboxed to the session's render aspect ratio.
        val knobSwitchW = (usableW * 0.60f).coerceAtLeast(totalCols * 40f)
        val previewAvailW = (usableW - knobSwitchW - COLUMN_GAP).coerceAtLeast(60f)

        val cellW = knobSwitchW / totalCols
        val diameter = (cellW - 10f).coerceIn(28f, 56f)
        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        val rowH = diameter + captionH + 6f
        val gridH = rowH * 2f

        val aspect = session.uiTheme.renderAspectRatio // height / width
        var previewW = previewAvailW
        var previewH = previewW * aspect
        if (previewH > gridH) {
            previewH = gridH
            previewW = if (aspect > 0f) previewH / aspect else previewAvailW
        }

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        // 1. Knobs: 4 columns x 2 rows, fixed grid position per index. A preset only "claims" the
        // slots it binds -- unbound slots render as a faint ghost outline instead of a live knob,
        // marking where a configured knob would sit without cluttering the surface.
        bank.knobs.forEachIndexed { i, control ->
            val row = i / knobCols
            val col = i % knobCols
            val cx = startX + col * cellW + (cellW - diameter) / 2f
            val cy = startY + row * rowH
            ImGui.setCursorScreenPos(cx, cy)
            if (control.bindings.isNotEmpty()) {
                drawLiveKnob(session, unit, i, control, diameter)
            } else {
                drawGhostKnob(dl, cx, cy, diameter)
            }
        }

        // 2. Buttons: 2 columns x 2 rows, to the right of the knobs.
        val switchH = 28f
        val switchW = (cellW - COLUMN_GAP).coerceAtLeast(20f)
        bank.switches.forEachIndexed { i, control ->
            val row = i / switchCols
            val col = knobCols + (i % switchCols)
            val sx = startX + col * cellW + COLUMN_GAP / 2f
            val sy = startY + row * rowH + (diameter - switchH) / 2f
            ImGui.setCursorScreenPos(sx, sy)
            if (control.bindings.isNotEmpty()) {
                drawLiveSwitch(session, unit, i, control, switchW, switchH)
            } else {
                drawGhostSwitch(dl, sx, sy, switchW, switchH)
            }
        }

        // 3. Preview monitor, to the right of the buttons.
        ImGui.setCursorScreenPos(startX + knobSwitchW + COLUMN_GAP, startY)
        RackMicroMonitor.draw(session, unit, previewW, previewH, renderer)

        ImGui.setCursorScreenPos(startX, startY + gridH)
        ImGui.dummy(0f, 0f)
    }

    private fun drawLiveKnob(
        session: llm.slop.liquidlsd.SessionContext,
        unit: RackUnit,
        index: Int,
        control: MacroControl,
        diameter: Float
    ) {
        val isSelected = MacroLearnState.selectedControlId == control.id
        val isLearning = MacroLearnState.isControlLearning(control.id)
        ImGui.pushID("knob_${unit.id}_$index")
        // See RackFaceplateGrid's old drawCuratedMacrosRow comment: wrapping the knob (which
        // repositions the cursor to paint its label, then restores it via a trailing dummy item)
        // in a group makes that whole dance count as one atomic item.
        ImGui.beginGroup()
        MacroKnobWidget.draw(
            session = session,
            id = "unit_${unit.id}_knob_$index",
            label = control.label.ifEmpty { "K${index + 1}" },
            value = control.value,
            diameter = diameter,
            isSelected = isSelected,
            isLearning = isLearning,
            bindings = control.bindings,
            onSelect = { MacroLearnState.selectedControlId = control.id },
            onToggleLearn = {
                if (isLearning) MacroLearnState.cancelLearn() else MacroLearnState.startLearn(control.id)
            },
            onChanged = { control.value = it }
        )
        ImGui.endGroup()
        ImGui.popID()
    }

    private fun drawLiveSwitch(
        session: llm.slop.liquidlsd.SessionContext,
        unit: RackUnit,
        index: Int,
        control: MacroControl,
        width: Float,
        height: Float
    ) {
        val isSelected = MacroLearnState.selectedControlId == control.id
        val isLearning = MacroLearnState.isControlLearning(control.id)
        ImGui.pushID("switch_${unit.id}_$index")
        MacroKnobWidget.drawSwitch(
            session = session,
            id = "unit_${unit.id}_switch_$index",
            label = control.label.ifEmpty { "SW${index + 1}" },
            control = control,
            width = width,
            height = height,
            isSelected = isSelected,
            isLearning = isLearning,
            onSelect = { MacroLearnState.selectedControlId = control.id },
            onToggleLearn = {
                if (isLearning) MacroLearnState.cancelLearn() else MacroLearnState.startLearn(control.id)
            }
        )
        ImGui.popID()
    }

    private fun drawGhostKnob(dl: ImDrawList, x: Float, y: Float, diameter: Float) {
        val radius = diameter / 2f
        val col = ImGui.colorConvertFloat4ToU32(0.45f, 0.48f, 0.52f, 0.16f)
        dl.addCircle(x + radius, y + radius, radius - 2f, col, 24, 1.0f)
    }

    private fun drawGhostSwitch(dl: ImDrawList, x: Float, y: Float, width: Float, height: Float) {
        val col = ImGui.colorConvertFloat4ToU32(0.45f, 0.48f, 0.52f, 0.14f)
        dl.addRect(x, y, x + width, y + height, col, 4f, 0, 1.0f)
    }

    private fun drawMasterExtraControls(unit: MixerTransitionUnit, usableW: Float) {
        val mixer = unit.mixer
        ImGui.spacing()

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        val crossfaderW = usableW * 0.4f
        drawParamSlider(
            mixer.crossfade, crossfaderW, "crossfade_${unit.id}",
            customLabel = "CROSSFADER [A <-> B]",
            tooltip = "Blend between Deck A (-1.0) and Deck B (+1.0)."
        )
        ImGui.sameLine(0f, COLUMN_GAP)

        val restW = ((usableW - crossfaderW - COLUMN_GAP * 4f) / 3f).coerceAtLeast(40f)
        drawParamSlider(
            mixer.mode, restW, "mm_${unit.id}",
            customLabel = "MODE",
            tooltip = "Blend mode: 0=Add, 1=Screen, 2=Mult, 3=Max, 4=Crossfade."
        )
        ImGui.sameLine(0f, COLUMN_GAP)
        drawParamSlider(
            mixer.masterAlpha, restW, "ma_${unit.id}",
            customLabel = "ALPHA",
            tooltip = "Master output gain/opacity."
        )
        ImGui.sameLine(0f, COLUMN_GAP)
        drawParamSlider(
            mixer.bloom, restW, "mb_${unit.id}",
            customLabel = "BLOOM",
            tooltip = "Post-process bloom/glow intensity."
        )

        ImGui.popStyleVar(2)
    }

    private fun drawParamSlider(
        param: ModulatableParameter,
        width: Float,
        idSuffix: String,
        customLabel: String? = null,
        tooltip: String? = null
    ) {
        ImGui.pushID(idSuffix)
        ImGui.beginGroup()

        val label = customLabel ?: "Param"
        ImGui.pushStyleColor(ImGuiCol.Text, 0.70f, 0.75f, 0.80f, 1.0f)
        ImGui.textUnformatted(label.take(20))
        ImGui.popStyleColor()

        ImGui.setNextItemWidth(width)
        val arr = floatArrayOf(param.baseValue)
        if (ImGui.sliderFloat("##val", arr, param.minClamp, param.maxClamp, "%.2f")) {
            param.baseValue = arr[0]
        }
        if (tooltip != null) itemTooltip(tooltip)

        ImGui.endGroup()
        ImGui.popID()
    }
}
