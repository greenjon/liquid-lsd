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
 * Reads the active [MacroBank] directly -- selecting a knob here is inspecting/editing the same
 * [llm.slop.liquidlsd.macro.MacroControl] object Performance Mode's own knobs drag and arm Learn
 * on, not a copy. This panel itself doesn't render rotary knobs or drag-to-adjust value; the knob
 * selector strip below is a row of labeled chips (label + binding count) purely for picking which
 * control's bindings the inspector shows -- value drag and hardware/parameter-bind Learn both live
 * on the actual knobs in Performance Mode (see [PerformanceMatrixPanel]), whose inline Learn button
 * jumps back here automatically once armed.
 *
 * Layout, top to bottom: deck tab strip, knob selector strip, binding inspector accordion drawer,
 * then the single-deck preview monitor at the bottom.
 * Note: Header mode toggle `[ MIXER | MACROS ]` is drawn at the Column 3 window level by
 * [Column3HeaderToggle].
 */
class MacroPanel(
    private val parametersState: ParametersState
) {
    private val linkBufs = Array(llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) { imgui.type.ImBoolean(true) }
    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        drawDeckTabs()
        ImGui.spacing()

        drawLearnBanner()

        val topTab = parametersState.activeTopTab
        val bank = MacroEngine.getBank(activeBankId()) ?: MacroEngine.bankForParamPath(parametersState.activeTopTab)
        val isFxTab = topTab in setOf("A FX", "B FX", "BG FX", "PV FX", "MST FX")
        if (isFxTab) {
            val fxChain = when (topTab) {
                "A FX"   -> mixer.deckA.fxChain
                "B FX"   -> mixer.deckB.fxChain
                "BG FX"  -> mixer.deckBG.fxChain
                "PV FX"  -> mixer.deckPV.fxChain
                else     -> mixer.masterFxBank.activeChain
            }
            drawFxRackView(session, topTab, fxChain, bank)
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
        "A FX" -> MacroEngine.DECK_A_FX
        "B FX" -> MacroEngine.DECK_B_FX
        "BG FX" -> MacroEngine.DECK_BG_FX
        "PV FX" -> MacroEngine.DECK_PV_FX
        "MST FX" -> MacroEngine.MASTER_FX
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
        "A FX" to "A FX",
        "B FX" to "B FX",
        "BG FX" to "BG FX",
        "PV FX" to "PV FX",
        "MST FX" to "MST FX"
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

    // -- Dedicated FX Rack View (Traktor/Mixxx-style Super Knob + 3 Metaknobs) --------------------
    // Replaces the generic knob grid for the five independent FX tabs (A FX / B FX / BG FX /
    // PV FX / MST FX): each deck owns its own FxChain and there is no chain-picker here. The
    // Super Link checkboxes mirror the link toggles in the main parameters panel and in
    // PerformanceMatrixPanel, writing through to the same FxChain object.

    private fun drawFxRackView(
        session: llm.slop.liquidlsd.SessionContext,
        tabId: String,
        chain: llm.slop.liquidlsd.rendering.FxChain,
        bank: MacroBank
    ) {
        val displayLabel = when (tabId) {
            "A FX"   -> "DECK A FX"
            "B FX"   -> "DECK B FX"
            "BG FX"  -> "DECK BG FX"
            "PV FX"  -> "DECK PV FX"
            "MST FX" -> "MASTER FX"
            else     -> tabId
        }
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("FX RACK: $displayLabel") }
        ImGui.spacing()

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled("SUPER LINK:")
            for (slotIdx in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) {
                ImGui.sameLine(0f, 8f)
                val linked = chain.slotSuperKnobLink.getOrNull(slotIdx) ?: false
                val buf = linkBufs[slotIdx]
                buf.set(linked)
                val slot = chain.slots.getOrNull(slotIdx)
                val slotName = slot?.displayName?.takeIf { it.isNotBlank() } ?: "S${slotIdx + 1}"
                if (ImGui.checkbox("$slotName##macro_fx_link_${tabId}_$slotIdx", buf)) {
                    chain.setSlotLinked(slotIdx, buf.get())
                    val bankId = activeBankId()
                    if (tabId == "MST FX") {
                        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(bankId, "MFX", chain, 0)
                    } else {
                        val deckLabel = when (tabId) {
                            "A FX"  -> "Deck A"
                            "B FX"  -> "Deck B"
                            "BG FX" -> "Deck BG"
                            else    -> "Deck PV"
                        }
                        llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(bankId, deckLabel, chain)
                    }
                }
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        drawMacroGrid(session, bank)
    }

    // -- Knob Selector Strip (picks which knob's bindings the inspector below shows) -----------------
    // No rotary widgets here -- dragging a macro's value and arming/cancelling its Learn both live
    // in the Binding Inspector header right below (and, for playing a set, in Performance Mode's
    // own knobs, whose inline Learn button already jumps here -- see PerformanceMatrixPanel). This
    // strip's only job is selection, so it's a row of labeled chips instead of a knob grid.

    private fun drawMacroGrid(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("MACRO CONTROLS") }
        ImGui.spacing()

        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(1f)
        val gap = 4f
        val n = bank.knobs.size.coerceAtLeast(1)
        val segW = ((availW - gap * (n - 1)) / n).coerceAtLeast(1f)
        val btnH = 30f

        bank.knobs.forEachIndexed { i, control ->
            if (i > 0) ImGui.sameLine(0f, gap)
            val isSelected = (llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId == control.id) ||
                (llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId == null && i == 0)
            val isLearningThis = llm.slop.liquidlsd.macro.MacroLearnState.isControlLearning(control.id)

            val btnColor = when {
                isLearningThis -> ImGui.colorConvertFloat4ToU32(0.55f, 0.16f, 0.16f, 1f)
                isSelected -> ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f)
                else -> ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f)
            }
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, btnColor)
            val label = control.label.ifEmpty { "K${i + 1}" }
            val countSuffix = if (control.bindings.isNotEmpty()) " (${control.bindings.size})" else ""
            if (ImGui.button("$label$countSuffix##macro_select_${control.id}", segW, btnH)) {
                llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId = control.id
            }
            ImGui.popStyleColor()
            itemTooltip(if (isLearningThis) "Learning -- click a parameter to bind, or Cancel below." else "Select to inspect/edit $label's bindings below.")
        }
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
            parametersState.activeTopTab == "A FX"   -> "PREVIEW: DECK A (FX)"
            parametersState.activeTopTab == "B FX"   -> "PREVIEW: DECK B (FX)"
            parametersState.activeTopTab == "BG FX"  -> "PREVIEW: DECK BG (FX)"
            parametersState.activeTopTab == "PV FX"  -> "PREVIEW: DECK PV (FX)"
            parametersState.activeTopTab == "MST FX" -> "PREVIEW: MASTER (FX)"
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
            "Deck A", "A FX"    -> mixer.deckA.getOutputTexture()
            "Deck B", "B FX"    -> mixer.deckB.getOutputTexture()
            "Deck BG", "BG FX"  -> mixer.deckBG.getOutputTexture()
            "Deck PV", "PV FX"  -> mixer.deckPV.getOutputTexture()
            "Mixer"             -> if (parametersState.activeMixerSubTab == "TRANS") mixer.blendFBO.texture else mixer.masterFBO.texture
            "TRANS", "Transition" -> mixer.blendFBO.texture
            "Master", "MST", "MST FX" -> mixer.masterFBO.texture
            else                -> mixer.masterFBO.texture
        }
    }
}
