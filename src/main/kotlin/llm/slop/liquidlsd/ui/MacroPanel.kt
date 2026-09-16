package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Column 3's `MACROS` mode view (Phase 2 of the Macro Controls system -- see
 * docs/developer/macro_controls_and_parameter_linking_proposal.md §2 for the target layout and
 * §7 for phase scope).
 *
 * Reads and writes [MacroEngine.globalBank] directly: dragging a knob mutates its
 * [llm.slop.liquidlsd.macro.MacroControl.value] in place, and switches go through
 * [llm.slop.liquidlsd.macro.MacroControl.onPress]/[llm.slop.liquidlsd.macro.MacroControl.onRelease]. There is
 * intentionally no Learn Mode / Binding Inspector here (Phase 3) and no serialization or MIDI/OSC
 * wiring (Phase 4) -- turning a knob with nothing bound to it is expected to visibly do nothing.
 *
 * Layout, top to bottom: `[ MIXER | MACROS ]` header toggle, 2-column x 4-row knob grid, a row of
 * 4 switches, then the single-deck preview monitor at the bottom (per proposal §2's diagram).
 */
class MacroPanel(
    private val parametersState: ParametersState
) {
    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        drawModeToggle(session)

        ImGui.spacing()
        drawLearnBanner()

        val bank = MacroEngine.globalBank()

        drawKnobGrid(session, bank)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        drawSwitchRow(session, bank)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        drawBindingInspectorDrawer(session, bank)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        drawPreviewMonitor(session, mixer)
    }

    private fun drawLearnBanner() {
        val status = llm.slop.liquidlsd.macro.MacroLearnState.getActiveStatus()
        val isLearning = llm.slop.liquidlsd.macro.MacroLearnState.isLearning()
        if (isLearning || status != null) {
            val text = status ?: "LEARN MODE: Click any parameter slider or modulator to bind"
            val bgCol = if (isLearning) {
                val alpha = (kotlin.math.sin(System.currentTimeMillis() * 0.008) * 0.2 + 0.6).toFloat()
                ImGui.colorConvertFloat4ToU32(0.0f, 0.55f, 0.75f, alpha)
            } else {
                ImGui.colorConvertFloat4ToU32(0.18f, 0.38f, 0.24f, 0.9f)
            }
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, bgCol)
            val btnW = ImGui.getContentRegionAvailX().coerceAtLeast(1f)
            val actionLabel = if (isLearning) "${Icons.REFRESH} $text (Click to Cancel)" else text
            if (ImGui.button(actionLabel, btnW, 26f)) {
                if (isLearning) llm.slop.liquidlsd.macro.MacroLearnState.cancelLearn()
                else llm.slop.liquidlsd.macro.MacroLearnState.clearStatus()
            }
            ImGui.popStyleColor()
            ImGui.spacing()
        }
    }

    // -- [ MIXER | MACROS ] header toggle ------------------------------------------------------

    private fun drawModeToggle(session: llm.slop.liquidlsd.SessionContext) {
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(2f)
        val btnH = session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 12f }.coerceAtLeast(28f)
        val gap = 3f
        val segW = ((availW - gap) / 2f).coerceAtLeast(1f)

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        val current = session.uiTheme.column3Mode

        fun segmentColor(isSelected: Boolean, isActiveItem: Boolean, isHovered: Boolean): Int = when {
            isSelected -> ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f)
            isActiveItem -> ImGui.colorConvertFloat4ToU32(0.32f, 0.32f, 0.32f, 1f)
            isHovered -> ImGui.colorConvertFloat4ToU32(0.24f, 0.24f, 0.24f, 1f)
            else -> ImGui.colorConvertFloat4ToU32(0.14f, 0.14f, 0.14f, 1f)
        }

        // -- MIXER segment (left, rounded on the outer/left edge) --
        run {
            val pMinX = startX
            val pMaxX = startX + segW
            ImGui.setCursorScreenPos(pMinX, startY)
            ImGui.invisibleButton("##col3_mode_mixer", segW, btnH)
            val isHovered = ImGui.isItemHovered()
            val isActiveItem = ImGui.isItemActive()
            if (ImGui.isItemClicked(0) && current != UITheme.Column3Mode.MIXER) {
                session.uiTheme.column3Mode = UITheme.Column3Mode.MIXER
                AppPreferencesStore.savePreferences()
            }
            val isSelected = current == UITheme.Column3Mode.MIXER
            val bgCol = segmentColor(isSelected, isActiveItem, isHovered)
            dl.addRectFilled(pMinX, startY, pMaxX, startY + btnH, bgCol, 4f)
            dl.addRectFilled(pMaxX - 6f, startY, pMaxX, startY + btnH, bgCol, 0f)
            drawSegmentLabel(session, "MIXER", pMinX, startY, segW, btnH, isSelected)
            itemTooltip("Classic 4-deck crossfader mixer view.")
        }

        // -- MACROS segment (right, rounded on the outer/right edge) --
        run {
            val pMinX = startX + segW + gap
            val pMaxX = pMinX + segW
            ImGui.setCursorScreenPos(pMinX, startY)
            ImGui.invisibleButton("##col3_mode_macros", segW, btnH)
            val isHovered = ImGui.isItemHovered()
            val isActiveItem = ImGui.isItemActive()
            if (ImGui.isItemClicked(0) && current != UITheme.Column3Mode.MACROS) {
                session.uiTheme.column3Mode = UITheme.Column3Mode.MACROS
                AppPreferencesStore.savePreferences()
            }
            val isSelected = current == UITheme.Column3Mode.MACROS
            val bgCol = segmentColor(isSelected, isActiveItem, isHovered)
            dl.addRectFilled(pMinX, startY, pMaxX, startY + btnH, bgCol, 4f)
            dl.addRectFilled(pMinX, startY, pMinX + 6f, startY + btnH, bgCol, 0f)
            drawSegmentLabel(session, "MACROS", pMinX, startY, segW, btnH, isSelected)
            itemTooltip("Macro Knobs, Switches, and single-deck preview.")
        }

        ImGui.setCursorScreenPos(startX, startY + btnH)
        ImGui.dummy(0f, 0f)
    }

    private fun drawSegmentLabel(
        session: llm.slop.liquidlsd.SessionContext,
        text: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        isSelected: Boolean
    ) {
        var tw = 0f
        var th = 0f
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            val sz = ImGui.calcTextSize(text)
            tw = sz.x
            th = sz.y
        }
        val textX = x + (w - tw) / 2f
        val textY = y + (h - th) / 2f
        val col = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, if (isSelected) 1f else 0.8f)
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.getWindowDrawList().addText(textX, textY, col, text)
        }
    }

    // -- 2x4 Macro Knob grid --------------------------------------------------------------------

    private fun drawKnobGrid(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("MACRO KNOBS") }
        ImGui.spacing()

        val cols = 2
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(2f)
        val cellW = availW / cols
        val diameter = (cellW - 24f).coerceIn(40f, 72f)
        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        val rowH = diameter + captionH + 10f

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        bank.knobs.forEachIndexed { i, control ->
            val row = i / cols
            val col = i % cols
            val cx = startX + col * cellW + (cellW - diameter) / 2f
            val cy = startY + row * rowH
            ImGui.setCursorScreenPos(cx, cy)
            val isSelected = (llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId == control.id) ||
                (llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId == null && i == 0)
            val isLearningThis = llm.slop.liquidlsd.macro.MacroLearnState.isControlLearning(control.id)
            MacroKnobWidget.draw(
                session = session,
                id = "global_knob_$i",
                label = control.label,
                value = control.value,
                diameter = diameter,
                isSelected = isSelected,
                isLearning = isLearningThis,
                onSelect = { llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId = control.id },
                onToggleLearn = {
                    if (isLearningThis) llm.slop.liquidlsd.macro.MacroLearnState.cancelLearn()
                    else llm.slop.liquidlsd.macro.MacroLearnState.startLearn(control.id)
                },
                onChanged = { newVal -> control.value = newVal }
            )
        }

        val totalRows = (bank.knobs.size + cols - 1) / cols
        ImGui.setCursorScreenPos(startX, startY + totalRows * rowH)
        ImGui.dummy(0f, 0f)
    }

    // -- 4 Macro Switches -------------------------------------------------------------------------

    private fun drawSwitchRow(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("MACRO SWITCHES") }
        ImGui.spacing()

        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(2f)
        val count = bank.switches.size.coerceAtLeast(1)
        val gap = 6f
        val switchW = ((availW - gap * (count - 1)) / count).coerceAtLeast(20f)
        val switchH = 34f

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        bank.switches.forEachIndexed { i, control ->
            val x = startX + i * (switchW + gap)
            ImGui.setCursorScreenPos(x, startY)
            val isSelected = llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId == control.id
            val isLearningThis = llm.slop.liquidlsd.macro.MacroLearnState.isControlLearning(control.id)
            MacroKnobWidget.drawSwitch(
                session = session,
                id = "global_switch_$i",
                label = control.label,
                control = control,
                width = switchW,
                height = switchH,
                isSelected = isSelected,
                isLearning = isLearningThis,
                onSelect = { llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId = control.id },
                onToggleLearn = {
                    if (isLearningThis) llm.slop.liquidlsd.macro.MacroLearnState.cancelLearn()
                    else llm.slop.liquidlsd.macro.MacroLearnState.startLearn(control.id)
                }
            )
        }

        ImGui.setCursorScreenPos(startX, startY + switchH)
        ImGui.dummy(0f, 0f)
    }

    // -- Binding Inspector Accordion Drawer --------------------------------------------------------

    private fun drawBindingInspectorDrawer(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank) {
        val selectedControl = bank.knobs.find { it.id == llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId }
            ?: bank.switches.find { it.id == llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId }
            ?: bank.knobs.firstOrNull()

        val countText = if (selectedControl != null) " (${selectedControl.bindings.size}/4)" else ""
        val ctrlName = selectedControl?.label?.ifEmpty { selectedControl.id } ?: "None"
        val headerLabel = "BINDING INSPECTOR: $ctrlName$countText###macro_binding_inspector"

        if (ImGui.collapsingHeader(headerLabel, imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            val availH = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
            val maxInspectorH = (availH * 0.45f).coerceIn(120f, 220f)
            if (ImGui.beginChild("##macro_inspector_scroll", 0f, maxInspectorH, true)) {
                MacroBindingInspector.draw(session, bank, selectedControl, parametersState)
            }
            ImGui.endChild()
        }
    }

    // -- Single-deck preview monitor (bottom) ------------------------------------------------------

    private fun drawPreviewMonitor(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled("PREVIEW: ${parametersState.activeTopTab.uppercase()}")
        }
        ImGui.spacing()

        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(1f)
        val remainingH = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
        val aspect = session.uiTheme.renderAspectRatio // height / width

        var previewW = availW
        var previewH = availW * aspect
        if (previewH > remainingH) {
            previewH = remainingH
            previewW = if (aspect > 0f) previewH / aspect else availW
        }
        val offsetX = (availW - previewW) / 2f

        val startX = ImGui.getCursorScreenPosX() + offsetX
        val startY = ImGui.getCursorScreenPosY()

        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(startX, startY, startX + previewW, startY + previewH, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))

        val textureId = resolvePreviewTexture(mixer)
        ImGui.setCursorScreenPos(startX, startY)
        ImGui.image(textureId.toLong(), previewW, previewH, 0f, 1f, 1f, 0f)

        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), startY + previewH)
        ImGui.dummy(0f, 0f)
    }

    /**
     * Resolves the deck currently focused by Columns 1/2 ([ParametersState.activeTopTab]) to its
     * output texture. The Macros preview intentionally follows this same "which deck" selection
     * rather than introducing a second, independent picker. Falls back to the master output
     * texture for the "Mixer" tab (no single deck focused), matching what the Mixer view's own
     * master monitor already shows.
     */
    private fun resolvePreviewTexture(mixer: Mixer): Int {
        return when (parametersState.activeTopTab) {
            "Deck A" -> mixer.deckA.getOutputTexture()
            "Deck B" -> mixer.deckB.getOutputTexture()
            "Deck BG" -> mixer.deckBG.getOutputTexture()
            "Deck PV" -> mixer.deckPV.getOutputTexture()
            else -> mixer.masterFBO.texture
        }
    }
}
