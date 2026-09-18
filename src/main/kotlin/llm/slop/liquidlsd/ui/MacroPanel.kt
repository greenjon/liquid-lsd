package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Column 3's `MACROS` mode view (Macro Controls system -- see
 * docs/user_guide/macros_and_rack.md for details).
 *
 * Shows one of the five canonical per-deck/mixer banks at a time ([MacroEngine.CANONICAL_BANK_IDS])
 * -- the same resident banks the Rack's per-deck faceplates read and write directly, so editing a
 * knob here and seeing it on the Rack (or vice versa) is the same object, not a copy. Which bank is
 * showing follows [ParametersState.activeTopTab] -- the same "which deck is focused" state Columns
 * 1/2 already use -- so clicking a deck tab in Parameters or a confidence monitor elsewhere
 * automatically flips this panel to that deck's knobs too, and the tab strip drawn here writes
 * back into [ParametersState.activeTopTab] so the reverse holds as well.
 *
 * Reads and writes the active [MacroBank] directly: dragging a knob mutates its
 * [llm.slop.liquidlsd.macro.MacroControl.value] in place, and switches go through
 * [llm.slop.liquidlsd.macro.MacroControl.onPress]/[llm.slop.liquidlsd.macro.MacroControl.onRelease].
 *
 * Layout, top to bottom: deck tab strip, 4-column x 2-row knob grid, a row of 4 switches,
 * binding inspector accordion drawer, then the single-deck preview monitor at the bottom.
 * Note: Header mode toggle `[ MIXER | MACROS ]` is drawn at the Column 3 window level by
 * [Column3HeaderToggle].
 */
class MacroPanel(
    private val parametersState: ParametersState
) {
    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        drawDeckTabs()
        ImGui.spacing()

        drawLearnBanner()

        val bank = MacroEngine.getBank(activeBankId()) ?: MacroEngine.bankForParamPath(parametersState.activeTopTab)

        drawMacroGrid(session, bank)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        drawBindingInspectorDrawer(session, bank, mixer)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        drawPreviewMonitor(session, mixer)
    }

    /** Maps [ParametersState.activeTopTab] to its canonical bank id ("Mixer" and anything unrecognized -> [MacroEngine.TRANS]). */
    private fun activeBankId(): String = when (parametersState.activeTopTab) {
        "Deck A" -> MacroEngine.DECK_A
        "Deck B" -> MacroEngine.DECK_B
        "Deck BG" -> MacroEngine.DECK_BG
        "Deck PV" -> MacroEngine.DECK_PV
        else -> MacroEngine.TRANS
    }

    private val deckTabs = listOf("Deck A" to "A", "Deck B" to "B", "Deck BG" to "BG", "Deck PV" to "PV", "Mixer" to "TRANS")

    private fun drawDeckTabs() {
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(1f)
        val gap = 4f
        val segW = ((availW - gap * (deckTabs.size - 1)) / deckTabs.size).coerceAtLeast(1f)
        val btnH = 24f

        for ((i, tab) in deckTabs.withIndex()) {
            val (topTabValue, shortLabel) = tab
            if (i > 0) ImGui.sameLine(0f, gap)
            val isActive = parametersState.activeTopTab == topTabValue
            if (isActive) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f))
            } else {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
            }
            if (ImGui.button("$shortLabel##macro_deck_tab_$topTabValue", segW, btnH)) {
                parametersState.activeTopTab = topTabValue
            }
            ImGui.popStyleColor()
            itemTooltip("Show $topTabValue's macro knobs & switches.")
        }
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

    // -- 6-Column Macro Grid: 8 Knobs (Cols 1-4, 2 Rows) + 4 Switches (Cols 5-6, 2 Rows) ---------

    private fun drawMacroGrid(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("MACRO CONTROLS") }
        ImGui.spacing()

        val cols = 6
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(2f)
        val cellW = availW / cols
        val diameter = (cellW - 10f).coerceIn(36f, 60f)
        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        val rowH = diameter + captionH + 6f

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        // 1. Draw 8 Knobs in Cols 0..3 (4 columns x 2 rows)
        bank.knobs.forEachIndexed { i, control ->
            val row = i / 4
            val col = i % 4
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
                bindings = control.bindings,
                onSelect = { llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId = control.id },
                onToggleLearn = {
                    if (isLearningThis) llm.slop.liquidlsd.macro.MacroLearnState.cancelLearn()
                    else llm.slop.liquidlsd.macro.MacroLearnState.startLearn(control.id)
                },
                onChanged = { newVal -> control.value = newVal }
            )
        }

        // 2. Draw 4 Switches in Cols 4..5 (2 columns x 2 rows)
        val gap = 4f
        val switchW = (cellW - gap).coerceAtLeast(20f)
        val switchH = 32f

        bank.switches.forEachIndexed { i, control ->
            val row = i / 2
            val col = 4 + (i % 2)
            val sx = startX + col * cellW + gap / 2f
            val sy = startY + row * rowH + (diameter - switchH) / 2f
            ImGui.setCursorScreenPos(sx, sy)
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

        val totalRows = 2
        ImGui.setCursorScreenPos(startX, startY + totalRows * rowH)
        ImGui.dummy(0f, 0f)
    }

    // -- Binding Inspector Accordion Drawer --------------------------------------------------------

    private fun drawBindingInspectorDrawer(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank, mixer: Mixer) {
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
                MacroBindingInspector.draw(session, bank, selectedControl, parametersState, mixer)
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
