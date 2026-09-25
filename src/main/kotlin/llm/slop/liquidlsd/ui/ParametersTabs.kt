package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.isf.MetaLinkMode
import kotlin.math.roundToInt

object ParametersTabs {

    private val fxEnabledBuf = imgui.type.ImBoolean()
    private val fxSlotPickerTypes = listOf(
        ShaderPickerPopup.PickerType.FX_SLOT_1,
        ShaderPickerPopup.PickerType.FX_SLOT_2,
        ShaderPickerPopup.PickerType.FX_SLOT_3,
        ShaderPickerPopup.PickerType.FX_SLOT_4
    )

    fun getDeckColor(tab: String, alpha: Float = 1f): Int {
        val rgb = when (tab) {
            "Deck A", "A" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorA()
            "Deck B", "B" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorB()
            "Deck BG", "BG" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorBG()
            "Deck PV", "PV" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorPV()
            else -> floatArrayOf(0.4f, 0.4f, 0.4f) // Mixer / MIX
        }
        return ImGui.colorConvertFloat4ToU32(rgb[0], rgb[1], rgb[2], alpha)
    }

    fun getSubTabColor(state: ParametersState, alpha: Float): Int {
        return getDeckColor(state.activeTopTab, alpha)
    }

    fun calculateLeftTabsHeight(session: llm.slop.liquidlsd.SessionContext): Float {
        return session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 14f }.coerceAtLeast(30f)
    }

    fun calculateLeftTabsWidth(session: llm.slop.liquidlsd.SessionContext): Float {
        // Keep the same height, but make them a square
        return calculateLeftTabsHeight(session)
    }

    /**
     * Renders the 5-channel vertical side rail for Performance Mode's Deep Edit:
     * [MIX], [A], [B], [BG], [PV].
     */
    fun drawPerformanceDeepEditSideTabs(
        session: llm.slop.liquidlsd.SessionContext,
        state: ParametersState,
        mixer: Mixer? = null,
        topOffset: Float = 0f,
        onSelectSection: (String) -> Unit
    ) {
        if (topOffset > 0f) {
            ImGui.dummy(0f, topOffset)
        }
        val deckAEmpty = mixer?.deckA?.isEmpty == true
        val deckBEmpty = mixer?.deckB?.isEmpty == true
        val deckBGEmpty = mixer?.deckBG?.isEmpty == true
        val deckPVEmpty = mixer?.deckPV?.isEmpty == true

        val tabs = listOf(
            Triple("MIX", "Mixer",   "Mixer controls (CTRL), Master FX (FX), and Crossfader/Transitions (TRANS)."),
            Triple("A",   "Deck A",  if (deckAEmpty) "Deck A [EMPTY] — Click to assign a source or preset." else "Deck A: Visual source (SRC) and insert FX (FX)."),
            Triple("B",   "Deck B",  if (deckBEmpty) "Deck B [EMPTY] — Click to assign a source or preset." else "Deck B: Visual source (SRC) and insert FX (FX)."),
            Triple("BG",  "Deck BG", if (deckBGEmpty) "Deck BG [EMPTY] — Click to assign a source or preset." else "Deck BG: Visual source (SRC) and insert FX (FX)."),
            Triple("PV",  "Deck PV", if (deckPVEmpty) "Deck PV [EMPTY] — Click to assign a source or preset." else "Deck PV: Visual source (SRC) and insert FX (FX).")
        )
        val buttonWidth = calculateLeftTabsWidth(session)
        val buttonHeight = session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 14f }.coerceAtLeast(30f)

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 0f, 4f)
        tabs.forEach { (shortLabel, fullTab, tooltipText) ->
            val isActive = state.activeTopTab == fullTab
            val activeCol = getDeckColor(fullTab, 1f)
            val bgCol = if (isActive) activeCol else ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 1f)
            val hoverCol = if (isActive) activeCol else ImGui.colorConvertFloat4ToU32(0.22f, 0.22f, 0.22f, 1f)
            val activeClickCol = if (isActive) activeCol else ImGui.colorConvertFloat4ToU32(0.32f, 0.32f, 0.32f, 1f)

            val pMinX = ImGui.getCursorScreenPosX()
            val pMinY = ImGui.getCursorScreenPosY()
            val pMaxX = pMinX + buttonWidth
            val pMaxY = pMinY + buttonHeight

            if (ImGui.invisibleButton("##perf_deep_side_$shortLabel", buttonWidth.coerceAtLeast(1f), buttonHeight.coerceAtLeast(1f))) {
                onSelectSection(fullTab)
            }
            val isHovered = ImGui.isItemHovered()
            val isItemActive = ImGui.isItemActive()

            val drawCol = when {
                isItemActive -> activeClickCol
                isHovered    -> hoverCol
                else         -> bgCol
            }

            val dl = ImGui.getWindowDrawList()
            // Draw button background with left corners rounded (4f) and right corners sharp (0f)
            dl.addRectFilled(pMinX, pMinY, pMaxX, pMaxY, drawCol, 4f)
            dl.addRectFilled(pMaxX - 6f, pMinY, pMaxX, pMaxY, drawCol, 0f)

            // Draw centered text label
            var tw = 0f
            var th = 0f
            session.uiTheme.withFont(UITheme.FontLevel.H3) {
                val sz = ImGui.calcTextSize(shortLabel)
                tw = sz.x
                th = sz.y
            }
            val textX = pMinX + (buttonWidth - tw) * 0.5f
            val textY = pMinY + (buttonHeight - th) * 0.5f
            val textCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, if (isActive) 1f else 0.8f)
            session.uiTheme.withFont(UITheme.FontLevel.H3) {
                dl.addText(textX, textY, textCol, shortLabel)
            }

            if (isHovered) {
                showTooltip(tooltipText, (pMinX.toInt() shl 16) xor (pMinY.toInt() and 0xFFFF))
            }
        }
        ImGui.popStyleVar()
    }

    internal fun getDeckSubTabs(isEmpty: Boolean): List<String> {
        if (isEmpty) {
            return listOf("Empty")
        }
        val tabs = mutableListOf<String>()
        tabs.add("SRC")
        tabs.add("FX")
        return tabs.distinct()
    }

    internal fun getDeckSubTabs(deck: Deck): List<String> = getDeckSubTabs(deck.isEmpty)

    private fun ensureValidSubTab(state: ParametersState, tabs: List<String>): String {
        val activeSubTab = when (state.activeTopTab) {
            "Deck A" -> state.activeDeckASubTab
            "Deck B" -> state.activeDeckBSubTab
            "Deck BG" -> state.activeDeckBGSubTab
            "Deck PV" -> state.activeDeckPVSubTab
            "Mixer" -> state.activeMixerSubTab
            else -> state.activeDeckASubTab
        }
        if (activeSubTab !in tabs && tabs.isNotEmpty()) {
            when (state.activeTopTab) {
                "Deck A" -> state.activeDeckASubTab = tabs.first()
                "Deck B" -> state.activeDeckBSubTab = tabs.first()
                "Deck BG" -> state.activeDeckBGSubTab = tabs.first()
                "Deck PV" -> state.activeDeckPVSubTab = tabs.first()
                "Mixer" -> state.activeMixerSubTab = tabs.first()
            }
            return tabs.first()
        }
        return activeSubTab
    }
    /**
     * Renders the parameter section subtabs (e.g. [SRC], [FX], [View] for Decks or [CTRL], [TRANS], [FX] for Mixer) above the first parameter name.
     */
    fun drawSectionTabs(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, mixer: Mixer, btnH: Float? = null) {
        val tabs = if (state.activeTopTab == "Mixer") {
            listOf("CTRL", "FX", "TRANS")
        } else {
            val deck = when (state.activeTopTab) {
                "Deck A" -> mixer.deckA
                "Deck B" -> mixer.deckB
                "Deck BG" -> mixer.deckBG
                "Deck PV" -> mixer.deckPV
                else -> mixer.deckA
            }
            if (deck.isEmpty) return
            getDeckSubTabs(deck)
        }
        if (tabs.isEmpty() || tabs == listOf("Empty")) return

        val currentSubTab = ensureValidSubTab(state, tabs)

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 4f)
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 4f, 0f)

        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            tabs.forEachIndexed { i, tab ->
                if (i > 0) ImGui.sameLine()
                val isActive = currentSubTab == tab

                if (isActive) {
                    val bgCol = getSubTabColor(state, 1f)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        bgCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, bgCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  bgCol)
                } else {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
                }

                val tw = ImGui.calcTextSize(tab).x
                val btnW = (tw + 18f).coerceAtLeast(44f)
                val subTabH = btnH ?: (ImGui.getTextLineHeight() + 8f).coerceAtLeast(26f)

                if (ImGui.button(tab, btnW, subTabH)) {
                    when (state.activeTopTab) {
                        "Deck A" -> state.activeDeckASubTab = tab
                        "Deck B" -> state.activeDeckBSubTab = tab
                        "Deck BG" -> state.activeDeckBGSubTab = tab
                        "Deck PV" -> state.activeDeckPVSubTab = tab
                        "Mixer" -> state.activeMixerSubTab = tab
                    }
                }
                val tooltip = when (tab) {
                    "SRC" -> "Source: Parameters for active visual generator, zoom, and rotation."
                    "FX" -> if (state.activeTopTab == "Mixer") "Master FX: a chain of 3 serial ISF effect slots on the master output." else "FX: this deck's chain of 3 serial ISF effect slots."
                    "CTRL" -> "Control: Master controls, channel levels, queue & clock triggers, and morph triggers."
                    "TRANS" -> "Transition: Transition shader selection, bypass, dry/wet, and dynamic parameters."
                    else -> "$tab parameters"
                }
                itemTooltip(tooltip)
                ImGui.popStyleColor(3)
            }
        }
        ImGui.popStyleVar(2)
    }

    /**
     * Renders content only when the named section matches the deck's active sub-tab.
     * For the Mixer top-tab, always renders when Mixer is the active top-tab.
     */
    const val PARAM_INDENT = 6f

    fun drawSubGroupContent(
        session: llm.slop.liquidlsd.SessionContext,
        parentLabel: String,
        label: String,
        state: ParametersState,
        content: () -> Unit
    ) {
        val key = "$parentLabel/$label"

        val isVisible = if (parentLabel == "Mixer") {
            state.activeTopTab == "Mixer" && state.activeMixerSubTab == label
        } else {
            val rawSubTab = when (parentLabel) {
                "Deck A" -> state.activeDeckASubTab
                "Deck B" -> state.activeDeckBSubTab
                "Deck BG" -> state.activeDeckBGSubTab
                "Deck PV" -> state.activeDeckPVSubTab
                else -> ""
            }
            val activeSubTab = if (rawSubTab in listOf("SRC", "FX")) rawSubTab else "SRC"
            activeSubTab == label
        }

        if (!isVisible) return

        val startY  = ImGui.getCursorScreenPosY()
        val dl      = ImGui.getWindowDrawList()
        val subStartY = startY

        ImGui.indent(PARAM_INDENT)
        content()
        ImGui.unindent(PARAM_INDENT)

        val endY = ImGui.getCursorScreenPosY()

        state.subgroupHeight[key] = endY - startY
    }

    fun drawMixerGroupContent(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        state: ParametersState,
        labelColW: Float,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        drawSubGroupContent(session, "Mixer", "CTRL", state) {
            drawMixerCtrlTab(session, mixer, state, labelColW, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }

        drawSubGroupContent(session, "Mixer", "FX", state) {
            drawMixerFxTab(session, mixer, state, labelColW, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }

        drawSubGroupContent(session, "Mixer", "TRANS", state) {
            drawMixerTransTab(session, mixer, state, labelColW, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }
    }

    private fun drawMixerCtrlTab(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        state: ParametersState,
        labelColW: Float,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        var row = 0
        ParametersRenderer.drawParamRow(session, "crossfade", "Mixer/crossfade", mixer.crossfade, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "master level", "Mixer/masterLevel", mixer.masterLevel, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "fade speed", "Mixer/xfadeSpeed", mixer.xfadeSpeed, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

        ParametersRenderer.drawParamRow(session, "queue prev", "Mixer/queuePrev", mixer.queuePrev, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "queue next", "Mixer/queueNext", mixer.queueNext, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "bg queue prev", "Mixer/bgQueuePrev", mixer.bgQueuePrev, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "bg queue next", "Mixer/bgQueueNext", mixer.bgQueueNext, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "trans queue prev", "Mixer/transQueuePrev", mixer.transQueuePrev, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "trans queue next", "Mixer/transQueueNext", mixer.transQueueNext, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "tap tempo", "Mixer/tapTempo", mixer.tapTempo, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

        if (session.uiTheme.randomizationEnabled) {
            ParametersRenderer.drawParamRow(session, "rand Deck A", "Mixer/randDeckA", mixer.randDeckA, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "rand Deck B", "Mixer/randDeckB", mixer.randDeckB, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "rand Deck BG", "Mixer/randDeckBG", mixer.randDeckBG, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "rand Deck PV", "Mixer/randDeckPV", mixer.randDeckPV, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "rand All", "Mixer/randAll", mixer.randAll, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }
    }

    private fun drawMixerTransTab(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        state: ParametersState,
        labelColW: Float,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        var row = 0
        val trans = mixer.transitionFilter
        val transName = trans?.displayName ?: "Linear Crossfade"

        ImGui.textDisabled("TRANSITION SHADER")
        ImGui.sameLine()
        ImGui.setNextItemWidth((labelColW - 130f).coerceAtLeast(30f))
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("$transName  ${Icons.CHEVRON_DOWN}##mixer_trans_selector", (labelColW - 130f).coerceAtLeast(30f), 0f)) {
                ShaderPickerPopup.show("Select Transition Shader", ShaderPickerPopup.PickerType.MIXER_TRANSITION) { newTransId ->
                    if (newTransId == null) {
                        mixer.setTransition(null)
                    } else {
                        mixer.setTransition(newTransId)
                    }
                    onPushUndo()
                }
            }
        }
        itemTooltip("Select active ISF Transition Shader for Deck A/B crossfader.")

        if (trans != null) {
            ImGui.sameLine()
            val enabledBuf = fxEnabledBuf
            enabledBuf.set(trans.enabled)
            if (ImGui.checkbox("##mixer_trans_enabled", enabledBuf)) {
                trans.enabled = enabledBuf.get()
                onPushUndo()
            }
            itemTooltip("Bypass Transition Shader.")

            ImGui.sameLine()
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.MORE_VERTICAL}##mixer_trans_kebab", 22f, 20f)) {
                    ImGui.openPopup("MixerTransKebabPopup")
                }
            }
            itemTooltip("Transition Options (Reset to Default)")

            if (ImGui.beginPopup("MixerTransKebabPopup")) {
                if (ImGui.menuItem("Reset Transition")) {
                    mixer.setTransition("linear_crossfade")
                    onPushUndo()
                }
                ImGui.endPopup()
            }

            ParametersRenderer.drawParamRow(session, "Dry/Wet", "Mixer/Transition/DryWet", trans.dryWet, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

            trans.parameters.forEach { (name, param) ->
                ParametersRenderer.drawParamRow(session, name, "Mixer/Transition/$name", param, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }
        } else {
            ImGui.spacing()
            ImGui.textDisabled("No transition filter active. Defaulting to linear crossfade.")
        }
    }

    fun drawMixerFxTab(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        state: ParametersState,
        labelColW: Float,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        drawMasterFxContent(session, mixer, state, labelColW, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
    }


    fun drawDeckGroupContent(
        session: llm.slop.liquidlsd.SessionContext,
        deckLabel: String,
        deck: Deck,
        state: ParametersState,
        labelColW: Float,
        mixer: Mixer,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        val activeSource = deck.source

        drawSubGroupContent(session, deckLabel, "SRC", state) {
            var row = 0
            val gainPath = if (activeSource is llm.slop.liquidlsd.rendering.ExternalVideoSource) {
                "$deckLabel/External Video/Gain"
            } else {
                "$deckLabel/${activeSource.displayName}/Gain"
            }
            ParametersRenderer.drawParamRow(session, "Gain", gainPath, activeSource.globalAlpha, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

            if (!activeSource.is3D) {
                ParametersRenderer.drawParamRow(session, "Zoom", "$deckLabel/View/Zoom", deck.viewZoom, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                ParametersRenderer.drawParamRow(session, "Rotate Z", "$deckLabel/View/RotateZ", deck.viewRotateZ, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }
            if (activeSource is DynamicVisualSource) {
                activeSource.parameters.forEach { (name, param) ->
                    ParametersRenderer.drawParamRow(session, name, "$deckLabel/${activeSource.displayName}/$name", param, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
            }
        }

        drawSubGroupContent(session, deckLabel, "FX", state) {
            ImGui.indent(PARAM_INDENT)
            drawFxChainContent(
                session, deck.fxChain, "$deckLabel/FX", "$deckLabel FX", state,
                labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo
            )
            ImGui.unindent(PARAM_INDENT)
        }
    }

    /** Renders the Master FX chain -- the same slot editor each deck's FX sub-tab uses. */
    fun drawMasterFxContent(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        state: ParametersState,
        labelColW: Float,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        ImGui.indent(PARAM_INDENT)
        drawFxChainContent(
            session, mixer.masterFxChain, Mixer.MASTER_FX_PREFIX, "Master FX", state,
            labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo
        )
        ImGui.unindent(PARAM_INDENT)
    }

    fun drawFxChainContent(
        session: llm.slop.liquidlsd.SessionContext,
        chain: FxChain,
        chainPrefix: String,
        chainDisplayName: String,
        state: ParametersState,
        labelColW: Float,
        mixer: Mixer,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit,
        startRow: Int = 0
    ): Int {
        var row = startRow
        val rowStartX = ImGui.getCursorPosX()

        ImGui.textDisabled(chainDisplayName.uppercase() + if (chain.name.isNotEmpty()) " (${chain.name})" else "")
        ImGui.sameLine(labelColW - 24f)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.MORE_VERTICAL}##fx_chain_kebab_$chainPrefix", 22f, 20f)) {
                ImGui.openPopup("FXChainKebabPopup_$chainPrefix")
            }
        }
        itemTooltip("$chainDisplayName Options (Save Chain, Copy, Paste, Clear)")

        ImGui.setCursorPosX(rowStartX)
        ParametersRenderer.drawParamRow(session, "Chain Wet/Dry", "$chainPrefix/DryWet", chain.dryWet, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

        if (ImGui.beginPopup("FXChainKebabPopup_$chainPrefix")) {
            if (ImGui.menuItem("Save Chain As...")) {
                val defaultChainName = chain.name.ifEmpty { chainPrefix.replace('/', '_').lowercase() }
                val chainDto = chain.toFxChainDto(defaultChainName)
                SavePresetModal.request(
                    title = "Save FX Chain As",
                    confirmLabel = "Save",
                    defaultName = defaultChainName,
                    targetDir = FileSystemManager.getFxChainsRoot(),
                    extension = "lsdfxchain"
                ) { name, tags ->
                    val file = java.io.File(FileSystemManager.getFxChainsRoot(), "$name.lsdfxchain")
                    session.presetRepository.saveFxChainAsync(file, name, chainDto, tags)
                }
            }
            if (ImGui.menuItem("Copy Chain")) {
                llm.slop.liquidlsd.models.ClipboardManager.copyFxChain(chain.toFxChainDto(chain.name.ifEmpty { chainPrefix }))
            }
            val canPasteChain = llm.slop.liquidlsd.models.ClipboardManager.fxChainClipboard != null
            if (ImGui.menuItem("Paste Chain", "", false, canPasteChain)) {
                llm.slop.liquidlsd.models.ClipboardManager.fxChainClipboard?.let {
                    llm.slop.liquidlsd.presets.FxOps.applyChain(chain, it)
                    onPushUndo()
                }
            }
            if (ImGui.menuItem("Clear Chain Slots")) {
                llm.slop.liquidlsd.presets.FxOps.clearChain(chain)
                onPushUndo()
            }
            ImGui.endPopup()
        }

        // Drag and Drop Target for Chain
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = java.io.File(payload)
                if (file.exists() && file.extension.lowercase() == "lsdfxchain") {
                    llm.slop.liquidlsd.presets.FxOps.loadChain(session, file, chain)
                    onPushUndo()
                }
            }
            ImGui.endDragDropTarget()
        }

        ImGui.separator()
        ImGui.spacing()

        row = FXChainMacroStrip.draw(
            session, chain, chainPrefix, state,
            grid = FXChainMacroStrip.GridContext(mixer, labelColW, gridStartX, getCvColumns, getColumnOffset, getCvColor),
            startRow = row,
            onPushUndo = onPushUndo
        )

        ImGui.separator()
        ImGui.spacing()

        // --- Per-Slot Controls for Chain ---
        for (i in chain.slots.indices) {
            val slotNum = i + 1
            val fx = chain.slots[i]
            val filterName = fx?.displayName ?: "None"
            val collapseKey = "$chainPrefix/FX$slotNum"
            val isCollapsed = state.fxSlotCollapsed[collapseKey] == true

            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.smallButton("${if (isCollapsed) Icons.CHEVRON_DOWN else Icons.CHEVRON_UP}##fx${slotNum}_collapse_$chainPrefix")) {
                    state.fxSlotCollapsed[collapseKey] = !isCollapsed
                }
            }
            itemTooltip(if (isCollapsed) "Expand Slot $slotNum." else "Collapse Slot $slotNum.")
            ImGui.sameLine()

            ImGui.textDisabled("Slot $slotNum")
            ImGui.sameLine()
            ImGui.setNextItemWidth((labelColW - 85f).coerceAtLeast(30f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("$filterName  ${Icons.CHEVRON_DOWN}##fx${slotNum}_selector_$chainPrefix", (labelColW - 85f).coerceAtLeast(30f), 0f)) {
                    ShaderPickerPopup.show("Select FX Slot $slotNum for $chainDisplayName", fxSlotPickerTypes[i]) { newFilterId ->
                        llm.slop.liquidlsd.presets.FxOps.setSlotFilter(chain, i, newFilterId)
                        onPushUndo()
                    }
                }
                fx?.header?.DESCRIPTION?.takeIf { it.isNotBlank() }?.let { itemTooltip(it) }
            }

            ImGui.sameLine()

            // Per-Slot Kebab Menu
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.MORE_VERTICAL}##fx_slot_kebab_${slotNum}_$chainPrefix", 22f, 20f)) {
                    ImGui.openPopup("FXSlotKebabPopup_${slotNum}_$chainPrefix")
                }
            }
            itemTooltip("Slot $slotNum Options (Save, Copy, Paste, Reset)")

            if (ImGui.beginPopup("FXSlotKebabPopup_${slotNum}_$chainPrefix")) {
                val hasFx = chain.slots[i] != null
                if (ImGui.menuItem("Save Slot Preset As...", "", false, hasFx)) {
                    chain.toFxSlotDto(i)?.let { slotDto ->
                        SavePresetModal.request(
                            title = "Save FX Slot Preset As",
                            confirmLabel = "Save",
                            defaultName = fx?.displayName?.lowercase()?.replace(" ", "_") ?: "fx_preset",
                            targetDir = FileSystemManager.getFxPresetsRoot(),
                            extension = "lsdfx"
                        ) { name, tags ->
                            val file = java.io.File(FileSystemManager.getFxPresetsRoot(), "$name.lsdfx")
                            session.presetRepository.saveFxPresetAsync(file, name, slotDto, tags)
                        }
                    }
                }
                if (ImGui.menuItem("Copy Slot", "", false, hasFx)) {
                    chain.toFxSlotDto(i)?.let { llm.slop.liquidlsd.models.ClipboardManager.copyFxSlot(it) }
                }
                val canPasteSlot = llm.slop.liquidlsd.models.ClipboardManager.fxSlotClipboard != null
                if (ImGui.menuItem("Paste Slot", "", false, canPasteSlot)) {
                    llm.slop.liquidlsd.models.ClipboardManager.fxSlotClipboard?.let {
                        llm.slop.liquidlsd.presets.FxOps.applySlot(chain, i, it)
                        onPushUndo()
                    }
                }
                if (ImGui.menuItem("Reset Slot", "", false, hasFx)) {
                    llm.slop.liquidlsd.presets.FxOps.clearSlot(chain, i)
                    onPushUndo()
                }
                ImGui.endPopup()
            }

            if (fx != null && !isCollapsed) {
                ParametersRenderer.drawParamRow(session, "Dry/Wet", "$chainPrefix/FX$slotNum/DryWet", fx.dryWet, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                fx.parameters.forEach { (name, param) ->
                    val existingBinding = fx.getBindingForParam(name)
                    val inputLabel = fx.header.INPUTS.find { it.NAME == name }?.LABEL
                    val descWithLink = if (existingBinding != null) {
                        val invStr = if (existingBinding.invert) " (Inverted)" else ""
                        val linkStr = "Metaknob: ${existingBinding.linkMode.name}$invStr"
                        if (inputLabel != null) "$inputLabel\n[$linkStr]" else "[$linkStr]"
                    } else inputLabel

                    ParametersRenderer.drawParamRow(
                        session, name, "$chainPrefix/FX$slotNum/$name", param, state, labelColW, mixer, gridStartX, row++,
                        getCvColumns, getColumnOffset, getCvColor, onPushUndo,
                        extraMenuItems = {
                            ImGui.separator()
                            ImGui.textDisabled("Metaknob Link")
                            if (ImGui.menuItem("Unlinked", "", existingBinding == null)) {
                                fx.setParamLink(name, null)
                                onPushUndo()
                            }
                            if (ImGui.menuItem("Full Range (0%–100%)", "", existingBinding?.linkMode == MetaLinkMode.FULL)) {
                                fx.setParamLink(name, MetaLinkMode.FULL, existingBinding?.invert ?: false)
                                onPushUndo()
                            }
                            if (ImGui.menuItem("First Half (0%–50%)", "", existingBinding?.linkMode == MetaLinkMode.FIRST_HALF)) {
                                fx.setParamLink(name, MetaLinkMode.FIRST_HALF, existingBinding?.invert ?: false)
                                onPushUndo()
                            }
                            if (ImGui.menuItem("Second Half (50%–100%)", "", existingBinding?.linkMode == MetaLinkMode.SECOND_HALF)) {
                                fx.setParamLink(name, MetaLinkMode.SECOND_HALF, existingBinding?.invert ?: false)
                                onPushUndo()
                            }
                            if (ImGui.menuItem("Triangle Peak (0%–100%–0%)", "", existingBinding?.linkMode == MetaLinkMode.TRIANGLE)) {
                                fx.setParamLink(name, MetaLinkMode.TRIANGLE, existingBinding?.invert ?: false)
                                onPushUndo()
                            }
                            if (ImGui.menuItem("Bipolar (Center-0)", "", existingBinding?.linkMode == MetaLinkMode.BIPOLAR)) {
                                fx.setParamLink(name, MetaLinkMode.BIPOLAR, existingBinding?.invert ?: false)
                                onPushUndo()
                            }
                            ImGui.separator()
                            if (ImGui.menuItem("Invert Direction", "", existingBinding?.invert == true)) {
                                val currMode = existingBinding?.linkMode ?: MetaLinkMode.FULL
                                fx.setParamLink(name, currMode, !(existingBinding?.invert ?: false))
                                onPushUndo()
                            }
                        },
                        descriptionOverride = descWithLink
                    )
                }
            }

            // Drag and Drop Target for FX Slot
            if (ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (payload != null) {
                    val file = java.io.File(payload)
                    if (file.exists()) {
                        val ext = file.extension.lowercase()
                        if (ext == "lsdfx") {
                            llm.slop.liquidlsd.presets.FxOps.loadSlot(session, file, chain, i)
                            onPushUndo()
                        } else if (ext == "lsdfxchain") {
                            llm.slop.liquidlsd.presets.FxOps.loadChain(session, file, chain)
                            onPushUndo()
                        }
                    }
                }
                ImGui.endDragDropTarget()
            }

            if (i < chain.slots.lastIndex) {
                ImGui.separator()
            }
        }
        return row
    }
}


