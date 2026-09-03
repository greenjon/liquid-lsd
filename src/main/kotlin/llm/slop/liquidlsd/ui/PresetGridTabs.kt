package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.VisualSource
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.roundToInt

object PresetGridTabs {
    private val TRANSFORM_PARAM_NAMES = setOf(
        "Zoom", "Rotate X", "Rotate Y", "Rotate Z",
        "Cam Rotate X", "Cam Rotate Y", "Cam Rotate Z"
    )

    var activeBtnMinX: Float = 0f
    var activeBtnMinY: Float = 0f
    var activeBtnMaxX: Float = 0f
    var activeBtnMaxY: Float = 0f

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

    fun getSubTabColor(state: PresetGridState, alpha: Float): Int {
        return getDeckColor(state.activeTopTab, alpha)
    }

    fun calculateLeftTabsWidth(session: llm.slop.liquidlsd.SessionContext): Float {
        val labels = listOf("MIX", "A", "B", "BG", "PV")
        var maxW = 0f
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            labels.forEach { label ->
                val w = ImGui.calcTextSize(label).x
                if (w > maxW) maxW = w
            }
        }
        return (maxW + 28f).coerceAtLeast(60f)
    }

    fun drawLeftTabs(session: llm.slop.liquidlsd.SessionContext, state: PresetGridState, mixer: Mixer? = null, topOffset: Float = 36f) {
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

            if (isHovered && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip(tooltipText)
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

    fun calculateSourceTabWidth(session: llm.slop.liquidlsd.SessionContext, state: PresetGridState, deck: Deck): Float {
        if (deck.isEmpty) return 0f
        val sourceName = deck.source.displayName
        val displayLabel = "$sourceName  ${Icons.CHEVRON_DOWN}"
        var tw = 0f
        session.uiTheme.withFont(UITheme.FontLevel.H3) { tw = ImGui.calcTextSize(displayLabel).x }
        return (tw + 18f).coerceAtLeast(48f)
    }

    fun calculateSectionTabsWidth(session: llm.slop.liquidlsd.SessionContext, state: PresetGridState, deck: Deck): Float {
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

    fun calculateSubTabsWidth(session: llm.slop.liquidlsd.SessionContext, state: PresetGridState, deck: Deck): Float {
        return calculateSourceTabWidth(session, state, deck) + calculateSectionTabsWidth(session, state, deck)
    }

    private fun ensureValidSubTab(state: PresetGridState, tabs: List<String>): String {
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
     * Renders the Video Source dropdown selector button (e.g. [Mandala ▾]) in the Preset Grid title bar.
     */
    fun drawSourceTab(
        session: llm.slop.liquidlsd.SessionContext,
        state: PresetGridState,
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
                ImGui.openPopup("##header_source_popup_${state.activeTopTab}")
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Click to change Visual Source for ${state.activeTopTab}.")
        }
        ImGui.popStyleColor(3)

        if (ImGui.beginPopup("##header_source_popup_${state.activeTopTab}")) {
            ImGui.textDisabled("Select Visual Source:")
            ImGui.separator()

            val deckLabel = state.activeTopTab
            val changeSource = { newSource: VisualSource ->
                if (deckPresetController != null) {
                    deckPresetController.changeVisualSourceSafely(mixer, deck, deckLabel, newSource, state)
                } else {
                    deck.source = newSource.clone()
                    deck.isEmpty = false
                    session.presetManager.clearDeckActivePreset(deck, mixer)
                    state.clearSelection()
                    state.setDeckSubTab(deckLabel, "SRC")
                    PresetGridUndo.pushUndoState(state, mixer)
                }
            }

            VisualSourceRegistry.availableSources.forEach { source ->
                if (ImGui.menuItem(source.displayName)) {
                    if (deck.source != source) {
                        changeSource(source)
                    }
                }
            }
            ImGui.endPopup()
        }
        ImGui.popStyleVar(1)
    }

    /**
     * Renders the parameter section subtabs (e.g. [SRC], [FX], [View]) above the first parameter name.
     */
    fun drawSectionTabs(session: llm.slop.liquidlsd.SessionContext, state: PresetGridState, mixer: Mixer, btnH: Float? = null) {
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
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    val tooltip = when (tab) {
                        "SRC" -> "Source: Parameters for active visual generator (${deck.source.displayName})."
                        "FX" -> "FX: Color, shading, and feedback loop parameters."
                        "View" -> "View: 3D perspective, zoom, and rotation parameters."
                        else -> "$tab parameters"
                    }
                    ImGui.setTooltip(tooltip)
                }
                ImGui.popStyleColor(3)
            }
        }
        ImGui.popStyleVar(2)
    }

    /**
     * Renders content only when the named section matches the deck's active sub-tab.
     * For the Mixer top-tab, always renders when Mixer is the active top-tab.
     */
    fun drawSubGroupContent(
        session: llm.slop.liquidlsd.SessionContext,
        parentLabel: String,
        label: String,
        state: PresetGridState,
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

        ImGui.indent()
        content()
        ImGui.unindent()

        val endY = ImGui.getCursorScreenPosY()

        state.subgroupHeight[key] = endY - startY
    }

    fun drawDeckGroupContent(
        session: llm.slop.liquidlsd.SessionContext,
        deckLabel: String,
        deck: Deck,
        state: PresetGridState,
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

            drawSubGroupContent(session, deckLabel, "SRC", state) {
                otherParams.forEachIndexed { i, (name, param) ->
                    PresetGridRenderer.drawParamRow(session, name, "$deckLabel/${activeSource.displayName}/$name", param, state, labelColW, mixer, gridStartX, i, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
                PresetGridRenderer.drawParamRow(session, "Gain", "$deckLabel/${activeSource.displayName}/Gain", activeSource.globalAlpha, state, labelColW, mixer, gridStartX, otherParams.size, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            drawSubGroupContent(session, deckLabel, "FX", state) {
                var row = 0
                PresetGridRenderer.drawParamRow(session, "Feedback",     "$deckLabel/FB/Decay",    deck.fbDecay,    state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Gain",      "$deckLabel/FB/Gain",     deck.fbGain,     state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Zoom",      "$deckLabel/FB/Zoom",     deck.fbZoom,     state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Rotate",    "$deckLabel/FB/Rotate",   deck.fbRotate,   state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Hue Shift", "$deckLabel/FB/HueShift", deck.fbHueShift, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Blur",      "$deckLabel/FB/Blur",     deck.fbBlur,     state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Chroma",    "$deckLabel/FB/Chroma",   deck.fbChroma,   state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Mode",      "$deckLabel/FB/Mode",     deck.fbMode,     state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Kaleido",   "$deckLabel/FB/Kaleido",  deck.fbKaleido,  state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            drawSubGroupContent(session, deckLabel, "View", state) {
                var row = 0
                PresetGridRenderer.drawParamRow(session, "3D Mode", "$deckLabel/View/3DMode", deck.view3DMode, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "Zoom", "$deckLabel/View/Zoom", deck.viewZoom, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "Rotate X", "$deckLabel/View/RotateX", deck.viewRotateX, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "Rotate Y", "$deckLabel/View/RotateY", deck.viewRotateY, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "Rotate Z", "$deckLabel/View/RotateZ", deck.viewRotateZ, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                val modeVal = deck.view3DMode.value
                if (modeVal >= 0.5f) {
                    PresetGridRenderer.drawParamRow(session, "3D Persp", "$deckLabel/View/Persp", deck.viewPersp, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PresetGridRenderer.drawParamRow(session, "Depth Dim", "$deckLabel/View/DepthDim", deck.viewDepthDim, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PresetGridRenderer.drawParamRow(session, "Separation", "$deckLabel/View/Separation", deck.viewSeparation, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PresetGridRenderer.drawParamRow(session, "Blend Mode", "$deckLabel/View/BlendMode", deck.viewBlendMode, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }

                transformParams.forEach { (name, param) ->
                    PresetGridRenderer.drawParamRow(session, name, "$deckLabel/View/$name", param, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
            }
        } else {
            drawSubGroupContent(session, deckLabel, "FX", state) {
                var row = 0
                PresetGridRenderer.drawParamRow(session, "Feedback",     "$deckLabel/FB/Decay",    deck.fbDecay,    state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Gain",      "$deckLabel/FB/Gain",     deck.fbGain,     state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Zoom",      "$deckLabel/FB/Zoom",     deck.fbZoom,     state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Rotate",    "$deckLabel/FB/Rotate",   deck.fbRotate,   state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Hue Shift", "$deckLabel/FB/HueShift", deck.fbHueShift, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Blur",      "$deckLabel/FB/Blur",     deck.fbBlur,     state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Chroma",    "$deckLabel/FB/Chroma",   deck.fbChroma,   state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Mode",      "$deckLabel/FB/Mode",     deck.fbMode,     state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "FB Kaleido",   "$deckLabel/FB/Kaleido",  deck.fbKaleido,  state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            drawSubGroupContent(session, deckLabel, "View", state) {
                var row = 0
                PresetGridRenderer.drawParamRow(session, "3D Mode", "$deckLabel/View/3DMode", deck.view3DMode, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "Zoom", "$deckLabel/View/Zoom", deck.viewZoom, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "Rotate X", "$deckLabel/View/RotateX", deck.viewRotateX, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "Rotate Y", "$deckLabel/View/RotateY", deck.viewRotateY, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PresetGridRenderer.drawParamRow(session, "Rotate Z", "$deckLabel/View/RotateZ", deck.viewRotateZ, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                val modeVal = deck.view3DMode.value
                if (modeVal >= 0.5f) {
                    PresetGridRenderer.drawParamRow(session, "3D Persp", "$deckLabel/View/Persp", deck.viewPersp, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PresetGridRenderer.drawParamRow(session, "Depth Dim", "$deckLabel/View/DepthDim", deck.viewDepthDim, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PresetGridRenderer.drawParamRow(session, "Separation", "$deckLabel/View/Separation", deck.viewSeparation, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PresetGridRenderer.drawParamRow(session, "Blend Mode", "$deckLabel/View/BlendMode", deck.viewBlendMode, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
            }
        }
    }
}


