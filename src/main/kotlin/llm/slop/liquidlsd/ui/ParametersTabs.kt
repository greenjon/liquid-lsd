package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FxBank
import llm.slop.liquidlsd.rendering.FxChain
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
            "FX1" -> floatArrayOf(0.55f, 0.35f, 0.85f)
            "FX2" -> floatArrayOf(0.85f, 0.35f, 0.65f)
            "MFX" -> floatArrayOf(0.9f, 0.25f, 0.35f)
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
            Triple("PV",  "Deck PV", if (deckPVEmpty) "Deck PV [EMPTY] — Click to assign a source or preset." else "Deck PV (Preview) visual source, geometry, color, and feedback parameters."),
            Triple("FX1", "FX1", "FX Bank 1: 3 shared filter slots + wet/dry, routable from any deck."),
            Triple("FX2", "FX2", "FX Bank 2: 3 shared filter slots + wet/dry, routable from any deck."),
            Triple("MFX", "MFX", "Master FX: 3 serial ISF effect slots + wet/dry on the final composited output.")
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

    fun calculateSectionTabsWidth(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, mixer: Mixer): Float {
        val tabs = if (state.activeTopTab == "Mixer") {
            listOf("CTRL", "TRANS")
        } else {
            val deck = when (state.activeTopTab) {
                "Deck A" -> mixer.deckA
                "Deck B" -> mixer.deckB
                "Deck BG" -> mixer.deckBG
                "Deck PV" -> mixer.deckPV
                else -> mixer.deckA
            }
            getDeckSubTabs(deck)
        }
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
                            session.deckLifecycleManager.clearDeckActivePreset(deck, mixer)
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
     * Renders the parameter section subtabs (e.g. [SRC], [FX], [View] for Decks or [CTRL], [TRANS], [FX] for Mixer) above the first parameter name.
     */
    fun drawSectionTabs(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, mixer: Mixer, btnH: Float? = null) {
        val tabs = if (state.activeTopTab == "Mixer") {
            listOf("CTRL", "TRANS")
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
                    "SRC" -> "Source: Parameters for active visual generator."
                    "FX" -> if (state.activeTopTab == "Mixer") "Master FX: 4 serial ISF effect slots on master output." else "FX: Color, shading, and feedback loop parameters."
                    "View" -> "View: 3D perspective, zoom, and rotation parameters."
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
        if (mixer.transitionFilter == null) {
            ParametersRenderer.drawParamRow(session, "mix mode", "Mixer/mode", mixer.mode, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }
        ParametersRenderer.drawParamRow(session, "master Alpha", "Mixer/masterAlpha", mixer.masterAlpha, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "bloom", "Mixer/bloom", mixer.bloom, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
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
        drawFxBankGroupContent(session, mixer.masterFxBank.label, mixer.masterFxBank, state, labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
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

            drawSubGroupContent(session, deckLabel, "View", state) {
                drawDeckViewSubgroup(session, deckLabel, deck, state, labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo, transformParams)
            }
        } else if (activeSource is llm.slop.liquidlsd.rendering.ExternalVideoSource) {
            drawSubGroupContent(session, deckLabel, "SRC", state) {
                ParametersRenderer.drawParamRow(session, "Gain", "$deckLabel/External Video/Gain", activeSource.globalAlpha, state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            drawSubGroupContent(session, deckLabel, "View", state) {
                drawDeckViewSubgroup(session, deckLabel, deck, state, labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }
        } else {
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
        ParametersRenderer.drawParamRow(session, "FX Route", "$deckLabel/View/FxRouting", deck.fxRouting, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        // This deck's send into its routed FxBank (see FX1/FX2 tabs for the bank's own 3-slot
        // chain + wet/dry) -- unassigned decks still show it, it's just a no-op until routed.
        if (deck.assignedFxBank != null) {
            ParametersRenderer.drawParamRow(session, "FX Send Level", "$deckLabel/FXChain/DryWet", deck.fxSendLevel, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }
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

    /**
     * Renders an [FxBank]'s own tab (FX1/FX2): the 3 shared filter slots + master wet/dry that
     * any deck routed to this bank shares (see FxBank). Unlike the old per-deck FX section this
     * replaces, there's no deck indirection here -- the bank is addressed directly.
     */
    fun drawFxBankGroupContent(
        session: llm.slop.liquidlsd.SessionContext,
        bankLabel: String,
        bank: FxBank,
        state: ParametersState,
        labelColW: Float,
        mixer: Mixer,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        ImGui.indent(PARAM_INDENT)
        var row = 0
        val rowStartX = ImGui.getCursorPosX()

        // --- Bank Header Bar ---
        // No separate "Bank Enabled" control: Bank Wet/Dry at ~0 is the bypass signal (Renderer
        // already skips a bank whose dryWet<=0 the same as a disabled one), so there's nothing
        // else to toggle here.
        ImGui.textDisabled("FX BANK: $bankLabel")
        ImGui.sameLine(labelColW - 24f)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.MORE_VERTICAL}##fx_bank_kebab_$bankLabel", 22f, 20f)) {
                ImGui.openPopup("FXBankKebabPopup_$bankLabel")
            }
        }
        itemTooltip("FX Bank Options (Save Bank, Copy Bank, Paste Bank, Clear All)")

        ImGui.setCursorPosX(rowStartX)
        ParametersRenderer.drawParamRow(session, "Bank Wet/Dry", "$bankLabel/DryWet", bank.masterWetDry, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

        if (ImGui.beginPopup("FXBankKebabPopup_$bankLabel")) {
            if (ImGui.menuItem("Save Bank Preset As...")) {
                val bankDto = bank.toFxBankDto(bankLabel.lowercase())
                SavePresetModal.request(
                    title = "Save FX Bank As",
                    confirmLabel = "Save",
                    defaultName = "${bankLabel.lowercase()}_bank",
                    targetDir = FileSystemManager.getFxBanksRoot(),
                    extension = "lsdfxbank"
                ) { name, tags ->
                    val file = java.io.File(FileSystemManager.getFxBanksRoot(), "$name.lsdfxbank")
                    session.presetRepository.saveFxBankAsync(file, name, bankDto, tags)
                }
            }
            if (ImGui.menuItem("Copy Bank")) {
                llm.slop.liquidlsd.models.ClipboardManager.copyFxBank(bank.toFxBankDto(bankLabel))
            }
            val canPasteBank = llm.slop.liquidlsd.models.ClipboardManager.fxBankClipboard != null
            if (ImGui.menuItem("Paste Bank", "", false, canPasteBank)) {
                llm.slop.liquidlsd.models.ClipboardManager.fxBankClipboard?.let {
                    bank.applyFxBank(it)
                    onPushUndo()
                }
            }
            if (ImGui.menuItem("Clear All Chains")) {
                bank.reset()
                onPushUndo()
            }
            ImGui.endPopup()
        }

        // Drag and drop for bank
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = java.io.File(payload)
                if (file.exists() && file.extension.lowercase() == "lsdfxbank") {
                    session.presetRepository.loadFxBankAsync(file).thenAccept { bankDto ->
                        bank.applyFxBank(bankDto)
                        onPushUndo()
                    }
                }
            }
            ImGui.endDragDropTarget()
        }

        ImGui.separator()
        ImGui.spacing()

        // --- Chain Subtabs [ 1 ] [ 2 ] [ 3 ] ---
        val activeChainIndex = state.getActiveChainIndex(bank)
        val chainTabs = listOf("1", "2", "3")

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 4f)
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 4f, 0f)

        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            chainTabs.forEachIndexed { i, tabLabel ->
                if (i > 0) ImGui.sameLine()
                val isActive = activeChainIndex == i

                if (isActive) {
                    val bgCol = getSubTabColor(state, 1f)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, bgCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, bgCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, bgCol)
                } else {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
                }

                val chainName = bank.chains[i].name.ifEmpty { "Chain ${i + 1}" }
                val btnText = "Chain $tabLabel"
                val tw = ImGui.calcTextSize(btnText).x
                val btnW = (tw + 18f).coerceAtLeast(60f)
                val subTabH = (ImGui.getTextLineHeight() + 8f).coerceAtLeast(26f)

                if (ImGui.button("$btnText##chain_tab_${bankLabel}_$i", btnW, subTabH)) {
                    state.setActiveChainIndex(bank, i)
                }
                itemTooltip("$chainName: Click to view effects in chain ${i + 1}")
                ImGui.popStyleColor(3)
            }
        }
        ImGui.popStyleVar(2)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- Active Chain Header & Controls ---
        val chainNum = activeChainIndex + 1
        val chain = bank.chains[activeChainIndex]
        val chainPrefix = "$bankLabel/C$chainNum"

        // No separate "Chain Enabled" control: Chain Wet/Dry at ~0 is the bypass signal.
        ImGui.textDisabled("CHAIN $chainNum ${if (chain.name.isNotEmpty()) "(${chain.name})" else ""}")
        ImGui.sameLine(labelColW - 24f)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.MORE_VERTICAL}##fx_chain_kebab_${bankLabel}_$activeChainIndex", 22f, 20f)) {
                ImGui.openPopup("FXChainKebabPopup_${bankLabel}_$activeChainIndex")
            }
        }
        itemTooltip("Chain $chainNum Options (Save Chain, Copy, Paste, Clear)")

        ImGui.setCursorPosX(rowStartX)
        ParametersRenderer.drawParamRow(session, "Chain Wet/Dry", "$chainPrefix/DryWet", chain.dryWet, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

        if (ImGui.beginPopup("FXChainKebabPopup_${bankLabel}_$activeChainIndex")) {
            if (ImGui.menuItem("Save Chain As...")) {
                val chainDto = chain.toFxChainDto(chain.name.ifEmpty { "chain_$chainNum" })
                SavePresetModal.request(
                    title = "Save FX Chain As",
                    confirmLabel = "Save",
                    defaultName = "chain_$chainNum",
                    targetDir = FileSystemManager.getFxChainsRoot(),
                    extension = "lsdfxchain"
                ) { name, tags ->
                    val file = java.io.File(FileSystemManager.getFxChainsRoot(), "$name.lsdfxchain")
                    session.presetRepository.saveFxChainAsync(file, name, chainDto, tags)
                }
            }
            if (ImGui.menuItem("Copy Chain")) {
                llm.slop.liquidlsd.models.ClipboardManager.copyFxChain(chain.toFxChainDto(chain.name.ifEmpty { "chain_$chainNum" }))
            }
            val canPasteChain = llm.slop.liquidlsd.models.ClipboardManager.fxChainClipboard != null
            if (ImGui.menuItem("Paste Chain", "", false, canPasteChain)) {
                llm.slop.liquidlsd.models.ClipboardManager.fxChainClipboard?.let {
                    chain.applyFxChain(it)
                    onPushUndo()
                }
            }
            if (ImGui.menuItem("Clear Chain Slots")) {
                for (c in 0 until FxChain.SLOT_COUNT) {
                    chain.clearFxSlot(c)
                }
                onPushUndo()
            }
            ImGui.endPopup()
        }

        // Drag and Drop Target for Chain
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = java.io.File(payload)
                if (file.exists()) {
                    val ext = file.extension.lowercase()
                    if (ext == "lsdfxchain") {
                        session.presetRepository.loadFxChainAsync(file).thenAccept { chainDto ->
                            chain.applyFxChain(chainDto)
                            onPushUndo()
                        }
                    }
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

        // --- Per-Slot Controls for Active Chain ---
        for (i in chain.slots.indices) {
            val slotNum = i + 1
            val fx = chain.slots[i]
            val filterName = fx?.displayName ?: "None"
            val collapseKey = "$chainPrefix/FX$slotNum"
            val isCollapsed = state.fxSlotCollapsed[collapseKey] == true

            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.smallButton("${if (isCollapsed) Icons.CHEVRON_DOWN else Icons.CHEVRON_UP}##fx${slotNum}_collapse_${bankLabel}_$activeChainIndex")) {
                    state.fxSlotCollapsed[collapseKey] = !isCollapsed
                }
            }
            itemTooltip(if (isCollapsed) "Expand Slot $slotNum." else "Collapse Slot $slotNum.")
            ImGui.sameLine()

            ImGui.textDisabled("Slot $slotNum")
            ImGui.sameLine()
            ImGui.setNextItemWidth((labelColW - 85f).coerceAtLeast(30f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("$filterName  ${Icons.CHEVRON_DOWN}##fx${slotNum}_selector_${bankLabel}_$activeChainIndex", (labelColW - 85f).coerceAtLeast(30f), 0f)) {
                    ShaderPickerPopup.show("Select FX Slot $slotNum for $bankLabel Chain $chainNum", fxSlotPickerTypes[i]) { newFilterId ->
                        if (newFilterId == null) {
                            chain.clearFxSlot(i)
                            onPushUndo()
                        } else {
                            val filter = llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.createFilter(newFilterId)
                            if (filter != null) {
                                chain.slots[i]?.dispose()
                                chain.slots[i] = filter
                                chain.armSlotTakeover(i)
                                onPushUndo()
                            }
                        }
                    }
                }
                fx?.header?.DESCRIPTION?.takeIf { it.isNotBlank() }?.let { itemTooltip(it) }
            }

            // No separate slot bypass control: this slot's Dry/Wet row at ~0 is the bypass signal.
            ImGui.sameLine()

            // Per-Slot Kebab Menu
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.MORE_VERTICAL}##fx_slot_kebab_${slotNum}_${bankLabel}_$activeChainIndex", 22f, 20f)) {
                    ImGui.openPopup("FXSlotKebabPopup_${slotNum}_${bankLabel}_$activeChainIndex")
                }
            }
            itemTooltip("Slot $slotNum Options (Save, Copy, Paste, Reset)")

            if (ImGui.beginPopup("FXSlotKebabPopup_${slotNum}_${bankLabel}_$activeChainIndex")) {
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
                        chain.applyFxSlot(i, it)
                        onPushUndo()
                    }
                }
                if (ImGui.menuItem("Reset Slot", "", false, hasFx)) {
                    chain.clearFxSlot(i)
                    onPushUndo()
                }
                ImGui.endPopup()
            }

            if (fx != null && !isCollapsed) {
                ParametersRenderer.drawParamRow(session, "Dry/Wet", "$chainPrefix/FX$slotNum/DryWet", fx.dryWet, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                fx.parameters.forEach { (name, param) ->
                    ParametersRenderer.drawParamRow(session, name, "$chainPrefix/FX$slotNum/$name", param, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
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
                            session.presetRepository.loadFxPresetAsync(file).thenAccept { presetDto ->
                                chain.applyFxSlot(i, presetDto.slot)
                                onPushUndo()
                            }
                        } else if (ext == "lsdfxchain") {
                            session.presetRepository.loadFxChainAsync(file).thenAccept { chainDto ->
                                chain.applyFxChain(chainDto)
                                onPushUndo()
                            }
                        }
                    }
                }
                ImGui.endDragDropTarget()
            }

            if (i < chain.slots.lastIndex) {
                ImGui.separator()
            }
        }
        ImGui.unindent(PARAM_INDENT)
    }
}


