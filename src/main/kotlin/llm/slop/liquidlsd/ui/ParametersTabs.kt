package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.VisualSource
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.roundToInt

object ParametersTabs {
    private val TRANSFORM_PARAM_NAMES = setOf(
        "Zoom", "Rotate X", "Rotate Y", "Rotate Z",
        "Cam Rotate X", "Cam Rotate Y", "Cam Rotate Z"
    )

    private val PREFERRED_TRANSFORM_ORDER = listOf(
        "Zoom", "Rotate X", "Rotate Y", "Rotate Z",
        "Cam Rotate X", "Cam Rotate Y", "Cam Rotate Z"
    )

    var activeBtnMinX: Float = 0f
    var activeBtnMinY: Float = 0f
    var activeBtnMaxX: Float = 0f
    var activeBtnMaxY: Float = 0f

    private val fxEnabledBuf = imgui.type.ImBoolean()
    private val fxSlotEnabledBufs = Array(Deck.FX_SLOT_COUNT) { imgui.type.ImBoolean() }
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

    fun drawLeftTabs(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, mixer: Mixer? = null, topOffset: Float = 36f) {
        if (topOffset > 0f) {
            ImGui.dummy(0f, topOffset)
        }
        val deckAEmpty = mixer?.deckA?.isEmpty == true
        val deckBEmpty = mixer?.deckB?.isEmpty == true
        val deckBGEmpty = mixer?.deckBG?.isEmpty == true
        val deckPVEmpty = mixer?.deckPV?.isEmpty == true

        val tabs = listOf(
            Triple("MIX", "Mixer", "Mixer controls, Deck sources, and Crossfader parameters."),
            Triple("A",   "Deck A", if (deckAEmpty) "Deck A [EMPTY] — Click to assign a source or preset." else "Deck A visual source, geometry, color, and feedback parameters."),
            Triple("B",   "Deck B", if (deckBEmpty) "Deck B [EMPTY] — Click to assign a source or preset." else "Deck B visual source, geometry, color, and feedback parameters."),
            Triple("BG",  "Deck BG", if (deckBGEmpty) "Deck BG [EMPTY] — Click to assign a source or preset." else "Deck BG (Background) visual source, geometry, color, and feedback parameters."),
            Triple("PV",  "Deck PV", if (deckPVEmpty) "Deck PV [EMPTY] — Click to assign a source or preset." else "Deck PV (Preview) visual source, geometry, color, and feedback parameters.")
        )
        val buttonWidth = calculateLeftTabsWidth(session)
        val buttonHeight = session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 14f }.coerceAtLeast(30f)

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 0f, 4f)
        tabs.forEach { (shortLabel, fullTab, tooltipText) ->
            val isActive = state.activeTopTab == fullTab
            val activeCol = getDeckColor(fullTab, 1f)
            val bgCol = if (isActive) {
                activeCol
            } else {
                ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 1f)
            }
            val hoverCol = if (isActive) {
                activeCol
            } else {
                ImGui.colorConvertFloat4ToU32(0.22f, 0.22f, 0.22f, 1f)
            }
            val activeClickCol = if (isActive) {
                activeCol
            } else {
                ImGui.colorConvertFloat4ToU32(0.32f, 0.32f, 0.32f, 1f)
            }

            val pMinX = ImGui.getCursorScreenPosX()
            val pMinY = ImGui.getCursorScreenPosY()
            val pMaxX = pMinX + buttonWidth
            val pMaxY = pMinY + buttonHeight

            if (ImGui.invisibleButton("##left_tab_$shortLabel", buttonWidth.coerceAtLeast(1f), buttonHeight.coerceAtLeast(1f))) {
                state.activeTopTab = fullTab
            }
            val isHovered = ImGui.isItemHovered()
            val isItemActive = ImGui.isItemActive()

            if (isActive) {
                activeBtnMinX = pMinX
                activeBtnMinY = pMinY
                activeBtnMaxX = pMaxX
                activeBtnMaxY = pMaxY
            }

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

    private fun getDeckSubTabs(deck: Deck): List<String> {
        if (deck.isEmpty) {
            return listOf("Empty")
        }
        val tabs = mutableListOf<String>()
        tabs.add("SRC")
        tabs.add("FX")
        tabs.add("View")
        return tabs.distinct()
    }

    fun calculateSourceTabWidth(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, deck: Deck): Float {
        if (deck.isEmpty) return 0f
        val sourceName = deck.source.displayName
        val displayLabel = "$sourceName  ${Icons.CHEVRON_DOWN}"
        var tw = 0f
        session.uiTheme.withFont(UITheme.FontLevel.H3) { tw = ImGui.calcTextSize(displayLabel).x }
        return (tw + 18f).coerceAtLeast(48f)
    }

    fun calculateSectionTabsWidth(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, deck: Deck): Float {
        val tabs = getDeckSubTabs(deck)
        if (tabs.isEmpty() || tabs == listOf("Empty")) return 0f
        var totalW = 0f
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            tabs.forEachIndexed { i, tab ->
                val tw = ImGui.calcTextSize(tab).x
                val btnW = (tw + 18f).coerceAtLeast(44f)
                totalW += btnW
                if (i > 0) totalW += 4f
            }
        }
        return totalW
    }

    fun calculateSubTabsWidth(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, deck: Deck): Float {
        return calculateSourceTabWidth(session, state, deck) + calculateSectionTabsWidth(session, state, deck)
    }

    private fun ensureValidSubTab(state: ParametersState, tabs: List<String>): String {
        val activeSubTab = when (state.activeTopTab) {
            "Deck A" -> state.activeDeckASubTab
            "Deck B" -> state.activeDeckBSubTab
            "Deck BG" -> state.activeDeckBGSubTab
            "Deck PV" -> state.activeDeckPVSubTab
            else -> state.activeDeckASubTab
        }
        if (activeSubTab !in tabs && tabs.isNotEmpty()) {
            when (state.activeTopTab) {
                "Deck A" -> state.activeDeckASubTab = tabs.first()
                "Deck B" -> state.activeDeckBSubTab = tabs.first()
                "Deck BG" -> state.activeDeckBGSubTab = tabs.first()
                "Deck PV" -> state.activeDeckPVSubTab = tabs.first()
            }
            return tabs.first()
        }
        return activeSubTab
    }

    /**
     * Renders the Video Source dropdown selector button (e.g. [Mandala ▾]) in the Parameters title bar.
     */
    fun drawSourceTab(
        session: llm.slop.liquidlsd.SessionContext,
        state: ParametersState,
        mixer: Mixer,
        btnH: Float? = null,
        deckPresetController: DeckPresetController? = null
    ) {
        if (state.activeTopTab == "Mixer") return

        val deck = when (state.activeTopTab) {
            "Deck A" -> mixer.deckA
            "Deck B" -> mixer.deckB
            "Deck BG" -> mixer.deckBG
            "Deck PV" -> mixer.deckPV
            else -> mixer.deckA
        }
        if (deck.isEmpty) return

        val sourceName = deck.source.displayName
        val displayLabel = "$sourceName  ${Icons.CHEVRON_DOWN}"

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 4f)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1f))
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.28f, 0.28f, 0.28f, 1f))
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.38f, 0.38f, 0.38f, 1f))

        var tw = 0f
        session.uiTheme.withFont(UITheme.FontLevel.H3) { tw = ImGui.calcTextSize(displayLabel).x }
        val btnW = (tw + 18f).coerceAtLeast(48f)
        val subTabH = btnH ?: session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 8f }.coerceAtLeast(26f)

        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            if (ImGui.button(displayLabel, btnW, subTabH)) {
                val deckLabel = state.activeTopTab
                ShaderPickerPopup.show("Select Source for $deckLabel", ShaderPickerPopup.PickerType.SOURCE) { newSourceId ->
                    if (newSourceId == null) return@show
                    val newSource = if (newSourceId.startsWith("ext_video:")) {
                        val serverName = newSourceId.removePrefix("ext_video:")
                        llm.slop.liquidlsd.rendering.ExternalVideoSource(serverName = serverName)
                    } else {
                        VisualSourceRegistry.availableSources.find { it.id == newSourceId }
                    }
                    if (newSource != null) {
                        if (deckPresetController != null) {
                            deckPresetController.changeVisualSourceSafely(mixer, deck, deckLabel, newSource, state)
                        } else {
                            deck.source = newSource.clone()
                            deck.isEmpty = false
                            session.presetManager.clearDeckActivePreset(deck, mixer)
                            state.clearSelection()
                            state.setDeckSubTab(deckLabel, "SRC")
                            ParametersUndo.pushUndoState(state, mixer)
                        }
                    }
                }
            }
        }
        itemTooltip("Click to change Visual Source for ${state.activeTopTab}.")
        ImGui.popStyleColor(3)
        ImGui.popStyleVar(1)
    }

    /**
     * Renders the parameter section subtabs (e.g. [SRC], [FX], [View]) above the first parameter name.
     */
    fun drawSectionTabs(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, mixer: Mixer, btnH: Float? = null) {
        if (state.activeTopTab == "Mixer") return

        val deck = when (state.activeTopTab) {
            "Deck A" -> mixer.deckA
            "Deck B" -> mixer.deckB
            "Deck BG" -> mixer.deckBG
            "Deck PV" -> mixer.deckPV
            else -> mixer.deckA
        }
        if (deck.isEmpty) return

        val tabs = getDeckSubTabs(deck)
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
                    }
                }
                val tooltip = when (tab) {
                    "SRC" -> "Source: Parameters for active visual generator (${deck.source.displayName})."
                    "FX" -> "FX: Color, shading, and feedback loop parameters."
                    "View" -> "View: 3D perspective, zoom, and rotation parameters."
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
            state.activeTopTab == "Mixer"
        } else {
            val activeSubTab = when (parentLabel) {
                "Deck A" -> state.activeDeckASubTab
                "Deck B" -> state.activeDeckBSubTab
                "Deck BG" -> state.activeDeckBGSubTab
                "Deck PV" -> state.activeDeckPVSubTab
                else -> ""
            }
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

        if (activeSource is DynamicVisualSource) {
            val transformParams = mutableListOf<Map.Entry<String, ModulatableParameter>>()
            val otherParams     = mutableListOf<Map.Entry<String, ModulatableParameter>>()

            activeSource.parameters.forEach { entry ->
                if (TRANSFORM_PARAM_NAMES.contains(entry.key)) transformParams.add(entry)
                else otherParams.add(entry)
            }

            transformParams.sortBy { (key, _) ->
                val idx = PREFERRED_TRANSFORM_ORDER.indexOf(key)
                if (idx >= 0) idx else 999
            }

            drawSubGroupContent(session, deckLabel, "SRC", state) {
                otherParams.forEachIndexed { i, (name, param) ->
                    ParametersRenderer.drawParamRow(session, name, "$deckLabel/${activeSource.displayName}/$name", param, state, labelColW, mixer, gridStartX, i, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
                ParametersRenderer.drawParamRow(session, "Gain", "$deckLabel/${activeSource.displayName}/Gain", activeSource.globalAlpha, state, labelColW, mixer, gridStartX, otherParams.size, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            drawSubGroupContent(session, deckLabel, "FX", state) {
                drawFxSubgroupContent(session, deckLabel, deck, state, labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            drawSubGroupContent(session, deckLabel, "View", state) {
                drawDeckViewSubgroup(session, deckLabel, deck, state, labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo, transformParams)
            }
        } else if (activeSource is llm.slop.liquidlsd.rendering.ExternalVideoSource) {
            drawSubGroupContent(session, deckLabel, "SRC", state) {
                ParametersRenderer.drawParamRow(session, "Gain", "$deckLabel/External Video/Gain", activeSource.globalAlpha, state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            drawSubGroupContent(session, deckLabel, "FX", state) {
                drawFxSubgroupContent(session, deckLabel, deck, state, labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            drawSubGroupContent(session, deckLabel, "View", state) {
                drawDeckViewSubgroup(session, deckLabel, deck, state, labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }
        } else {
            drawSubGroupContent(session, deckLabel, "FX", state) {
                drawFxSubgroupContent(session, deckLabel, deck, state, labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            drawSubGroupContent(session, deckLabel, "View", state) {
                drawDeckViewSubgroup(session, deckLabel, deck, state, labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }
        }
    }

    private fun drawDeckViewSubgroup(
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
        onPushUndo: () -> Unit,
        transformParams: List<Map.Entry<String, ModulatableParameter>> = emptyList()
    ) {
        var row = 0
        if (!deck.source.is3D) {
            ParametersRenderer.drawParamRow(session, "Zoom", "$deckLabel/View/Zoom", deck.viewZoom, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "Rotate Z", "$deckLabel/View/RotateZ", deck.viewRotateZ, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

            transformParams.forEach { (name, param) ->
                ParametersRenderer.drawParamRow(session, name, "$deckLabel/${deck.source.displayName}/$name", param, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }
        } else {
            if (transformParams.isEmpty()) {
                imgui.ImGui.spacing()
                imgui.ImGui.textDisabled("3D source handles projection internally.")
            } else {
                transformParams.forEach { (name, param) ->
                    ParametersRenderer.drawParamRow(session, name, "$deckLabel/${deck.source.displayName}/$name", param, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
            }
        }
    }

    private fun drawFxSubgroupContent(
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
        var row = 0

        // --- FX Chain Header Bar ---
        ImGui.textDisabled("FX CHAIN")
        ImGui.sameLine(labelColW - 24f)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.MORE_VERTICAL}##fx_chain_kebab_$deckLabel", 22f, 20f)) {
                ImGui.openPopup("FXChainKebabPopup_$deckLabel")
            }
        }
        itemTooltip("FX Chain Options (Save, Copy, Paste, Clear)")

        if (ImGui.beginPopup("FXChainKebabPopup_$deckLabel")) {
            if (ImGui.menuItem("Save Chain As...")) {
                val chainDto = deck.toFxChainDto("fx_chain")
                SavePresetModal.request(
                    title = "Save FX Chain As",
                    confirmLabel = "Save",
                    defaultName = "fx_chain",
                    targetDir = FileSystemManager.getFxChainsRoot(),
                    extension = "lsdfxchain"
                ) { name, tags ->
                    val file = java.io.File(FileSystemManager.getFxChainsRoot(), "$name.lsdfxchain")
                    session.presetManager.saveFxChainAsync(file, name, chainDto, tags)
                }
            }
            if (ImGui.menuItem("Copy Chain")) {
                llm.slop.liquidlsd.models.ClipboardManager.copyFxChain(deck.toFxChainDto("chain"))
            }
            val canPasteChain = llm.slop.liquidlsd.models.ClipboardManager.fxChainClipboard != null
            if (ImGui.menuItem("Paste Chain", "", false, canPasteChain)) {
                llm.slop.liquidlsd.models.ClipboardManager.fxChainClipboard?.let {
                    deck.applyFxChain(it)
                    onPushUndo()
                }
            }
            if (ImGui.menuItem("Clear All Slots")) {
                for (c in 0 until Deck.FX_SLOT_COUNT) {
                    deck.clearFxSlot(c)
                }
                onPushUndo()
            }
            ImGui.endPopup()
        }

        ImGui.separator()
        ImGui.spacing()

        // --- Per-Slot Controls ---
        for (i in deck.fxSlots.indices) {
            val slotNum = i + 1
            val fx = deck.fxSlots[i]
            val filterName = fx?.displayName ?: "None"
            val collapseKey = "$deckLabel/FX$slotNum"
            val isCollapsed = state.fxSlotCollapsed[collapseKey] == true

            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.smallButton("${if (isCollapsed) Icons.CHEVRON_DOWN else Icons.CHEVRON_UP}##fx${slotNum}_collapse_$deckLabel")) {
                    state.fxSlotCollapsed[collapseKey] = !isCollapsed
                }
            }
            itemTooltip(if (isCollapsed) "Expand Slot $slotNum." else "Collapse Slot $slotNum.")
            ImGui.sameLine()

            ImGui.textDisabled("Slot $slotNum")
            ImGui.sameLine()
            ImGui.setNextItemWidth((labelColW - 85f).coerceAtLeast(30f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("$filterName  ${Icons.CHEVRON_DOWN}##fx${slotNum}_selector_$deckLabel", (labelColW - 85f).coerceAtLeast(30f), 0f)) {
                    ShaderPickerPopup.show("Select FX Slot $slotNum for $deckLabel", fxSlotPickerTypes[i]) { newFilterId ->
                        if (newFilterId == null) {
                            deck.clearFxSlot(i)
                            onPushUndo()
                        } else {
                            val filter = llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.createFilter(newFilterId)
                            if (filter != null) {
                                deck.fxSlots[i]?.dispose()
                                deck.fxSlots[i] = filter
                                onPushUndo()
                            }
                        }
                    }
                }
            }

            ImGui.sameLine()
            if (fx != null) {
                val enabledBuf = fxSlotEnabledBufs[i]
                enabledBuf.set(fx.enabled)
                if (ImGui.checkbox("##fx${slotNum}_enabled_$deckLabel", enabledBuf)) {
                    fx.enabled = enabledBuf.get()
                    onPushUndo()
                }
                itemTooltip("Bypass Slot $slotNum filter.")
                ImGui.sameLine()
            }

            // Per-Slot Kebab Menu
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.MORE_VERTICAL}##fx_slot_kebab_${slotNum}_$deckLabel", 22f, 20f)) {
                    ImGui.openPopup("FXSlotKebabPopup_${slotNum}_$deckLabel")
                }
            }
            itemTooltip("Slot $slotNum Options (Save, Copy, Paste, Reset)")

            if (ImGui.beginPopup("FXSlotKebabPopup_${slotNum}_$deckLabel")) {
                val hasFx = deck.fxSlots[i] != null
                if (ImGui.menuItem("Save Slot Preset As...", "", false, hasFx)) {
                    deck.toFxSlotDto(i)?.let { slotDto ->
                        SavePresetModal.request(
                            title = "Save FX Slot Preset As",
                            confirmLabel = "Save",
                            defaultName = fx?.displayName?.lowercase()?.replace(" ", "_") ?: "fx_preset",
                            targetDir = FileSystemManager.getFxPresetsRoot(),
                            extension = "lsdfx"
                        ) { name, tags ->
                            val file = java.io.File(FileSystemManager.getFxPresetsRoot(), "$name.lsdfx")
                            session.presetManager.saveFxPresetAsync(file, name, slotDto, tags)
                        }
                    }
                }
                if (ImGui.menuItem("Copy Slot", "", false, hasFx)) {
                    deck.toFxSlotDto(i)?.let { llm.slop.liquidlsd.models.ClipboardManager.copyFxSlot(it) }
                }
                val canPasteSlot = llm.slop.liquidlsd.models.ClipboardManager.fxSlotClipboard != null
                if (ImGui.menuItem("Paste Slot", "", false, canPasteSlot)) {
                    llm.slop.liquidlsd.models.ClipboardManager.fxSlotClipboard?.let {
                        deck.applyFxSlot(i, it)
                        onPushUndo()
                    }
                }
                if (ImGui.menuItem("Reset Slot", "", false, hasFx)) {
                    deck.clearFxSlot(i)
                    onPushUndo()
                }
                ImGui.endPopup()
            }

            if (fx != null && !isCollapsed) {
                ParametersRenderer.drawParamRow(session, "Dry/Wet", "$deckLabel/FX$slotNum/DryWet", fx.dryWet, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                fx.parameters.forEach { (name, param) ->
                    ParametersRenderer.drawParamRow(session, name, "$deckLabel/FX$slotNum/$name", param, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
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
                            session.presetManager.loadFxPresetAsync(file).thenAccept { presetDto ->
                                deck.applyFxSlot(i, presetDto.slot)
                                onPushUndo()
                            }
                        } else if (ext == "lsdfxchain") {
                            session.presetManager.loadFxChainAsync(file).thenAccept { chainDto ->
                                deck.applyFxChain(chainDto)
                                onPushUndo()
                            }
                        }
                    }
                }
                ImGui.endDragDropTarget()
            }

            if (i < deck.fxSlots.lastIndex) {
                ImGui.separator()
            }
        }
    }
}


