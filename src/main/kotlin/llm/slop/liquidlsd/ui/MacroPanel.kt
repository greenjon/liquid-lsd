package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Column 3's `MACROS` mode view (Macro Controls system -- see
 * docs/user_guide/macros_and_rack.md for details).
 *
 * Shows one of the canonical per-deck/mixer/FX-bank banks at a time ([MacroEngine.CANONICAL_BANK_IDS])
 * -- the same resident banks the Rack's per-deck faceplates read and write directly, so editing a
 * knob here and seeing it on the Rack (or vice versa) is the same object, not a copy. Which bank is
 * showing follows [ParametersState.activeTopTab] -- the same "which deck is focused" state Columns
 * 1/2 already use -- so clicking a deck tab in Parameters or a confidence monitor elsewhere
 * automatically flips this panel to that deck's knobs too, and the tab strip drawn here writes
 * back into [ParametersState.activeTopTab] so the reverse holds as well.
 *
 * Reads and writes the active [MacroBank] directly: dragging a knob mutates its
 * [llm.slop.liquidlsd.macro.MacroControl.value] in place.
 *
 * Layout, top to bottom: deck tab strip, 4-column x 2-row knob grid, binding inspector
 * accordion drawer, then the single-deck preview monitor at the bottom.
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

        val topTab = parametersState.activeTopTab
        val bank = MacroEngine.getBank(activeBankId()) ?: MacroEngine.bankForParamPath(parametersState.activeTopTab)
        if (topTab == "FX1" || topTab == "FX2" || topTab == "MFX") {
            val fxBank = when (topTab) {
                "FX1" -> mixer.fxBank1
                "FX2" -> mixer.fxBank2
                else -> mixer.masterFxBank
            }
            drawFxRackView(session, topTab, fxBank, bank)
        } else {
            drawMacroGrid(session, bank)
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        drawBindingInspectorDrawer(session, bank, mixer)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        drawPreviewMonitor(session, mixer)
    }

    /** Maps [ParametersState.activeTopTab] to its canonical bank id ("Mixer" -> TRANS or MASTER depending on subtab). */
    private fun activeBankId(): String = when (parametersState.activeTopTab) {
        "Deck A" -> MacroEngine.DECK_A
        "Deck B" -> MacroEngine.DECK_B
        "Deck BG" -> MacroEngine.DECK_BG
        "Deck PV" -> MacroEngine.DECK_PV
        "FX1" -> MacroEngine.FX_BANK_1
        "FX2" -> MacroEngine.FX_BANK_2
        "MFX" -> MacroEngine.MASTER_FX
        "Master", "MST" -> MacroEngine.MASTER
        "TRANS", "Transition" -> MacroEngine.TRANS
        "Mixer" -> if (parametersState.activeMixerSubTab == "CTRL") MacroEngine.MASTER else MacroEngine.TRANS
        else -> MacroEngine.TRANS
    }

    private val deckTabs = listOf(
        "Deck A" to "A",
        "Deck B" to "B",
        "Deck BG" to "BG",
        "Deck PV" to "PV",
        "TRANS" to "TRANS",
        "MST" to "MST",
        "FX1" to "FX1",
        "FX2" to "FX2",
        "MFX" to "MFX"
    )

    private fun drawDeckTabs() {
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(1f)
        val gap = 4f
        val segW = ((availW - gap * (deckTabs.size - 1)) / deckTabs.size).coerceAtLeast(1f)
        val btnH = 24f

        for ((i, tab) in deckTabs.withIndex()) {
            val (tabId, shortLabel) = tab
            if (i > 0) ImGui.sameLine(0f, gap)
            val isActive = when (tabId) {
                "TRANS" -> parametersState.activeTopTab == "TRANS" || (parametersState.activeTopTab == "Mixer" && parametersState.activeMixerSubTab == "TRANS")
                "MST" -> parametersState.activeTopTab == "Master" || parametersState.activeTopTab == "MST" || (parametersState.activeTopTab == "Mixer" && parametersState.activeMixerSubTab == "CTRL")
                else -> parametersState.activeTopTab == tabId
            }
            if (isActive) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f))
            } else {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
            }
            if (ImGui.button("$shortLabel##macro_deck_tab_$tabId", segW, btnH)) {
                when (tabId) {
                    "TRANS" -> {
                        parametersState.activeTopTab = "Mixer"
                        parametersState.activeMixerSubTab = "TRANS"
                    }
                    "MST" -> {
                        parametersState.activeTopTab = "Mixer"
                        parametersState.activeMixerSubTab = "CTRL"
                    }
                    else -> {
                        parametersState.activeTopTab = tabId
                    }
                }
            }
            ImGui.popStyleColor()
            val tip = when (tabId) {
                "TRANS" -> "Show Transition macro knobs."
                "MST" -> "Show Master composite macro knobs."
                else -> "Show $tabId's macro knobs."
            }
            itemTooltip(tip)
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

    // -- Dedicated FX Rack View (Traktor/Mixxx-style Chain Super Knob + 3 Metaknobs) -----------------
    // Replaces the generic knob grid for FX1/FX2/MFX: these banks' actual macro surface is each
    // chain's Super Knob/Metaknobs (see FxChain/ISFFilter), not arbitrary Learn-Mode bindings.

    private fun drawFxRackView(
        session: llm.slop.liquidlsd.SessionContext,
        bankLabel: String,
        fxBank: llm.slop.liquidlsd.rendering.FxBank,
        bank: MacroBank
    ) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("FX RACK: $bankLabel") }
        ImGui.spacing()

        val activeChainIndex = parametersState.getActiveChainIndex(fxBank)
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(1f)
        val gap = 4f
        val segW = ((availW - gap * 2) / 3f).coerceAtLeast(1f)

        for (i in 0 until 3) {
            if (i > 0) ImGui.sameLine(0f, gap)
            val isActive = activeChainIndex == i
            ImGui.pushStyleColor(
                imgui.flag.ImGuiCol.Button,
                if (isActive) ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f)
            )
            if (ImGui.button("Chain ${i + 1}##macro_fx_chain_tab_${bankLabel}_$i", segW, 24f)) {
                parametersState.setActiveChainIndex(fxBank, i)
            }
            ImGui.popStyleColor()
        }

        ImGui.spacing()

        val chain = fxBank.chains[activeChainIndex]
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled("SUPER LINK:")
            for (slotIdx in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) {
                ImGui.sameLine(0f, 8f)
                val linked = chain.slotSuperKnobLink.getOrNull(slotIdx) ?: false
                val buf = imgui.type.ImBoolean(linked)
                val slot = chain.slots.getOrNull(slotIdx)
                val slotName = slot?.displayName?.takeIf { it.isNotBlank() } ?: "S${slotIdx + 1}"
                if (ImGui.checkbox("$slotName##macro_fx_link_${bankLabel}_$slotIdx", buf)) {
                    chain.setSlotLinked(slotIdx, buf.get())
                    val bankId = activeBankId()
                    llm.slop.liquidlsd.macro.FxMacroSync.syncChain(bankId, bankLabel, chain, activeChainIndex)
                }
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        drawMacroGrid(session, bank)
    }

    // -- 4-Column Macro Knob Grid (row count follows the active bank's knob count) ------------------

    private fun drawMacroGrid(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("MACRO CONTROLS") }
        ImGui.spacing()

        val cols = 4
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(2f)
        val cellW = availW / cols
        val diameter = (cellW - 10f).coerceIn(36f, 60f)
        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        val rowH = diameter + captionH + 6f

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        val currentBankId = activeBankId()
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
                id = "${currentBankId}_knob_$i",
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

        val totalRows = (bank.knobs.size + cols - 1) / cols
        ImGui.setCursorScreenPos(startX, startY + totalRows * rowH)
        ImGui.dummy(0f, 0f)
    }

    // -- Binding Inspector Accordion Drawer --------------------------------------------------------

    private fun drawBindingInspectorDrawer(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank, mixer: Mixer) {
        val selectedControl = bank.knobs.find { it.id == llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId }
            ?: bank.knobs.firstOrNull()

        val countText = if (selectedControl != null) " (${selectedControl.bindings.size}/4)" else ""
        val ctrlName = selectedControl?.label?.ifEmpty { selectedControl.id } ?: "None"
        val headerLabel = "BINDING INSPECTOR: $ctrlName$countText###macro_binding_inspector"

        if (ImGui.collapsingHeader(headerLabel, imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            val availH = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
            val maxInspectorH = (availH * 0.45f).coerceIn(120f, 220f)
            if (ImGui.beginChild("##macro_inspector_scroll", 0f, maxInspectorH, true)) {
                MacroBindingInspector.draw(session, selectedControl, parametersState, mixer)
            }
            ImGui.endChild()
        }
    }

    // -- Single-deck preview monitor (bottom) ------------------------------------------------------

    private fun drawPreviewMonitor(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        val previewTitle = when {
            parametersState.activeTopTab == "Mixer" && parametersState.activeMixerSubTab == "CTRL" -> "PREVIEW: MASTER"
            parametersState.activeTopTab == "Mixer" && parametersState.activeMixerSubTab == "TRANS" -> "PREVIEW: TRANSITION"
            parametersState.activeTopTab == "Master" || parametersState.activeTopTab == "MST" -> "PREVIEW: MASTER"
            parametersState.activeTopTab == "TRANS" || parametersState.activeTopTab == "Transition" -> "PREVIEW: TRANSITION"
            else -> "PREVIEW: ${parametersState.activeTopTab.uppercase()}"
        }
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled(previewTitle)
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
     * texture for the "Mixer" tab (or transition FBO when on TRANS).
     */
    private fun resolvePreviewTexture(mixer: Mixer): Int {
        return when (parametersState.activeTopTab) {
            "Deck A" -> mixer.deckA.getOutputTexture()
            "Deck B" -> mixer.deckB.getOutputTexture()
            "Deck BG" -> mixer.deckBG.getOutputTexture()
            "Deck PV" -> mixer.deckPV.getOutputTexture()
            "Mixer" -> if (parametersState.activeMixerSubTab == "TRANS") mixer.blendFBO.texture else mixer.masterFBO.texture
            "TRANS", "Transition" -> mixer.blendFBO.texture
            "Master", "MST" -> mixer.masterFBO.texture
            else -> mixer.masterFBO.texture
        }
    }
}
