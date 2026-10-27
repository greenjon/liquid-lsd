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

    fun getDeckColorRGB(tab: String): FloatArray = when (tab) {
        "Deck A", "A" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorA()
        "Deck B", "B" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorB()
        "Deck BG", "BG" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorBG()
        "Deck PV", "PV" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorPV()
        else -> floatArrayOf(0.4f, 0.4f, 0.4f) // Mixer / MIX
    }

    fun getDeckColor(tab: String, alpha: Float = 1f): Int {
        val rgb = getDeckColorRGB(tab)
        return ImGui.colorConvertFloat4ToU32(rgb[0], rgb[1], rgb[2], alpha)
    }

    fun getSubTabColor(state: ParametersState, alpha: Float): Int {
        return getDeckColor(state.activeTopTab, alpha)
    }

    fun calculateLeftTabsHeight(session: llm.slop.liquidlsd.SessionContext): Float {
        return session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 14f }.coerceAtLeast(30f)
    }

    /** Side-rail tab width: square, widened to fit the longest label ("MST") so no label spills past the rail. */
    fun calculateLeftTabsWidth(session: llm.slop.liquidlsd.SessionContext): Float {
        val widest = session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.calcTextSize("MST").x }
        return maxOf(calculateLeftTabsHeight(session), widest + 12f)
    }

    /**
     * Renders the vertical side rail for the Edit view:
     * [MST], [OUT], [A], [B], [BG], [PV].
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

        // MST and OUT are both the "Mixer" top tab; they differ by [ParametersState.activeMixerSubTab] (LEVELS/CTRL vs FX/TRANS).
        val tabs = listOf(
            Triple("MST", "Mixer",   "Master: deck levels, master level (LEVELS), and queue, clock and randomize triggers (CTRL). Shown with the Clock row."),
            Triple("OUT", "Mixer",   "Output stage: Master FX (FX) and Crossfader/Transitions (TRANS)."),
            Triple("A",   "Deck A",  if (deckAEmpty) "Deck A [EMPTY] — Click to assign a source or preset." else "Deck A: its source (SRC) and FX chain (FX)."),
            Triple("B",   "Deck B",  if (deckBEmpty) "Deck B [EMPTY] — Click to assign a source or preset." else "Deck B: its source (SRC) and FX chain (FX)."),
            Triple("BG",  "Deck BG", if (deckBGEmpty) "Deck BG [EMPTY] — Click to assign a source or preset." else "Deck BG: its source (SRC) and FX chain (FX)."),
            Triple("PV",  "Deck PV", if (deckPVEmpty) "Deck PV [EMPTY] — Click to assign a source or preset." else "Deck PV: its source (SRC) and FX chain (FX).")
        )
        val buttonWidth = calculateLeftTabsWidth(session)
        val buttonHeight = calculateLeftTabsHeight(session)

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 0f, 4f)
        tabs.forEach { (shortLabel, fullTab, tooltipText) ->
            val isActive = state.activeTopTab == fullTab &&
                (fullTab != "Mixer" || ParametersState.isMstSubTab(state.activeMixerSubTab) == (shortLabel == "MST"))
            val activeCol = getDeckColor(fullTab, 1f)
            val bgCol = if (isActive) activeCol else ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 1f)
            val hoverCol = if (isActive) activeCol else ImGui.colorConvertFloat4ToU32(0.22f, 0.22f, 0.22f, 1f)
            val activeClickCol = if (isActive) activeCol else ImGui.colorConvertFloat4ToU32(0.32f, 0.32f, 0.32f, 1f)

            val pMinX = ImGui.getCursorScreenPosX()
            val pMinY = ImGui.getCursorScreenPosY()
            val pMaxX = pMinX + buttonWidth
            val pMaxY = pMinY + buttonHeight

            if (ImGui.invisibleButton("##perf_deep_side_$shortLabel", buttonWidth.coerceAtLeast(1f), buttonHeight.coerceAtLeast(1f))) {
                if (fullTab == "Mixer") {
                    state.activeMixerSubTab = if (shortLabel == "MST") state.lastMstTab else state.lastMixerChainTab
                }
                onSelectSection(fullTab)
            }
            llm.slop.liquidlsd.tour.WidgetRegistry.note { "edit_side_$shortLabel" }
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
            val textCol = if (isActive) {
                val ink = TangoPalette.inkFor(getDeckColorRGB(fullTab))
                ImGui.colorConvertFloat4ToU32(ink[0], ink[1], ink[2], 1f)
            } else {
                ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.8f)
            }
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
     * Renders the parameter section subtabs (e.g. [SRC], [FX], [View] for Decks, [LEVELS], [CTRL] for the Mixer's MST side tab or [FX], [TRANS] for its OUT side tab) above the first parameter name.
     */
    fun drawSectionTabs(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, mixer: Mixer, btnH: Float? = null) {
        val tabs = if (state.activeTopTab == "Mixer") {
            // The MST side tab holds LEVELS and CTRL; the OUT side tab holds FX and TRANS.
            if (ParametersState.isMstSubTab(state.activeMixerSubTab)) listOf("LEVELS", "CTRL") else listOf("FX", "TRANS")
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

                var pushedTabInk = false
                if (isActive) {
                    val bgCol = getSubTabColor(state, 1f)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        bgCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, bgCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  bgCol)
                    val ink = TangoPalette.inkFor(getDeckColorRGB(state.activeTopTab))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
                    pushedTabInk = true
                } else {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
                }

                val tw = ImGui.calcTextSize(tab).x
                val btnW = (tw + 9f).coerceAtLeast(22f)
                val subTabH = btnH ?: (ImGui.getTextLineHeight() + 8f).coerceAtLeast(26f)

                if (ButtonChrome.button(tab, btnW, subTabH)) {
                    llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection(state.activeTopTab, tab)
                    when (state.activeTopTab) {
                        "Deck A" -> state.activeDeckASubTab = tab
                        "Deck B" -> state.activeDeckBSubTab = tab
                        "Deck BG" -> state.activeDeckBGSubTab = tab
                        "Deck PV" -> state.activeDeckPVSubTab = tab
                        "Mixer" -> state.activeMixerSubTab = tab
                    }
                }
                val tooltip = when (tab) {
                    "SRC" -> "Source: the deck's Gain, Deck Zoom and Deck Rotate, then the source's own parameters."
                    "FX" -> if (state.activeTopTab == "Mixer") "Master FX: a chain of 3 serial ISF effect slots on the master output." else "FX: this deck's chain of 3 serial ISF effect slots."
                    "LEVELS" -> "Levels: the master level and each deck's level fader. A deck's Gain is on its SRC tab."
                    "CTRL" -> "Control: queue and randomize triggers. The same triggers are on the Clock row in Perform."
                    "TRANS" -> "Transition: crossfade and fade speed, transition shader selection, bypass, dry/wet, and dynamic parameters."
                    else -> "$tab parameters"
                }
                itemTooltip(tooltip)
                ImGui.popStyleColor(if (pushedTabInk) 4 else 3)
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
        drawSubGroupContent(session, "Mixer", "LEVELS", state) {
            drawMixerLevelsTab(session, mixer, state, labelColW, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }

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

    /**
     * MST > LEVELS: the master level, then each deck's level (the mixer fader, `Mixer/level<Deck>`). A deck's Gain is on its SRC tab.
     */
    private fun drawMixerLevelsTab(
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
        ParametersRenderer.drawParamRow(session, "Master level", "Mixer/masterLevel", mixer.masterLevel, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        for (i in MIXER_LEVEL_LABELS.indices) {
            val level = when (i) { 0 -> mixer.levelA; 1 -> mixer.levelB; 2 -> mixer.levelBG; else -> mixer.levelPV }
            ParametersRenderer.drawParamRow(session, MIXER_LEVEL_LABELS[i], MIXER_LEVEL_PATHS[i], level, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }
    }

    private const val DECK_ZOOM_TIP = "Scales the deck's picture (2D sources). 1 = the source fills the frame height. Separate from a generator's own Zoom, if it has one. A preset keeps its value; picking a new source resets it."
    private const val DECK_ROTATE_TIP = "Rolls the deck's picture (2D sources). Separate from a generator's own rotation, if it has one. A preset keeps its value; picking a new source resets it."
    private val MIXER_LEVEL_LABELS = arrayOf("Level A", "Level B", "Level BG", "Level PV")
    private val MIXER_LEVEL_PATHS = arrayOf("Mixer/levelA", "Mixer/levelB", "Mixer/levelBG", "Mixer/levelPV")


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
        ParametersRenderer.drawParamRow(session, "Queue prev", "Mixer/queuePrev", mixer.queuePrev, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "Queue next", "Mixer/queueNext", mixer.queueNext, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "BG queue prev", "Mixer/bgQueuePrev", mixer.bgQueuePrev, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "BG queue next", "Mixer/bgQueueNext", mixer.bgQueueNext, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "Trans queue prev", "Mixer/transQueuePrev", mixer.transQueuePrev, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "Trans queue next", "Mixer/transQueueNext", mixer.transQueueNext, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "A/B FX queue prev", "Mixer/fxQueuePrev", mixer.fxQueuePrev, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "A/B FX queue next", "Mixer/fxQueueNext", mixer.fxQueueNext, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "BG FX queue prev", "Mixer/fxBgQueuePrev", mixer.fxBgQueuePrev, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "BG FX queue next", "Mixer/fxBgQueueNext", mixer.fxBgQueueNext, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        ParametersRenderer.drawParamRow(session, "Tap tempo", "Mixer/tapTempo", mixer.tapTempo, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

        if (session.uiTheme.randomizationEnabled) {
            ParametersRenderer.drawParamRow(session, "Randomize deck A", "Mixer/randDeckA", mixer.randDeckA, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "Randomize deck B", "Mixer/randDeckB", mixer.randDeckB, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "Randomize deck BG", "Mixer/randDeckBG", mixer.randDeckBG, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "Randomize deck PV", "Mixer/randDeckPV", mixer.randDeckPV, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "Randomize all", "Mixer/randAll", mixer.randAll, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
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
        // The crossfader and its auto-fade time lead the tab: the output stage the transition shader and its parameters act on.
        val crossfadeRow = {
            ParametersRenderer.drawParamRow(session, "Crossfade", "Mixer/crossfade", mixer.crossfade, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            ParametersRenderer.drawParamRow(session, "Fade speed", "Mixer/xfadeSpeed", mixer.xfadeSpeed, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }

        // The selector spans the label column, less the bypass checkbox and kebab beside it, so the shader's name fits.
        val selectorW = (labelColW - 2f * ImGui.getFrameHeight() - 2f * ImGui.getStyle().itemSpacingX).coerceAtLeast(30f)
        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            val shown = TextFit.ellipsize(transName, selectorW - ImGui.calcTextSize("  ${Icons.CHEVRON_DOWN}").x - 2f * ImGui.getStyle().framePaddingX)
            if (ButtonChrome.button("$shown  ${Icons.CHEVRON_DOWN}##mixer_trans_selector", selectorW, 0f)) {
                state.openTransitionBrowse()
            }
        }
        itemTooltip("Transition shader: $transName. Click to browse transitions for the Deck A/B crossfader.")

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
                if (ButtonChrome.button("${Icons.MORE_VERTICAL}##mixer_trans_kebab", 22f, 20f)) {
                    ImGui.openPopup("MixerTransKebabPopup")
                }
            }
            itemTooltip("Transition Options (Save, Reset, Save as Default)")

            pushOpenDropdownPadding()
            if (ImGui.beginPopup("MixerTransKebabPopup")) {
                pushOpenDropdownFont()
                if (ImGui.menuItem("Save Transition As...")) {
                    TransitionSave.requestSaveCurrent(session, mixer)
                }
                if (ImGui.menuItem("Reset Transition")) {
                    llm.slop.liquidlsd.presets.TransitionOps.setStock("linear_crossfade")
                    onPushUndo()
                }
                TransitionSave.drawDefaultMenuItems(mixer)
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()

            crossfadeRow()
            ParametersRenderer.drawParamRow(session, "Dry/Wet", "Mixer/Transition/DryWet", trans.dryWet, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

            trans.parameters.forEach { (name, param) ->
                val label = ParamLabels.of(trans.header, name)
                ParametersRenderer.drawParamRow(session, label, "Mixer/Transition/$name", param, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }
        } else {
            crossfadeRow()
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
            ParametersRenderer.drawParamRow(session, "Gain", "$deckLabel/Gain", activeSource.globalAlpha, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

            if (!activeSource.is3D) {
                ParametersRenderer.drawParamRow(session, "Deck Zoom", "$deckLabel/View/Zoom", deck.viewZoom, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo,
                    descriptionOverride = DECK_ZOOM_TIP)
                ParametersRenderer.drawParamRow(session, "Deck Rotate", "$deckLabel/View/RotateZ", deck.viewRotateZ, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo,
                    descriptionOverride = DECK_ROTATE_TIP)
            }
            if (activeSource is llm.slop.liquidlsd.rendering.StructuredEditorSource && activeSource is DynamicVisualSource) {
                StructuredEditorPanel.draw(
                    session, deckLabel, activeSource, activeSource, row, state, labelColW, mixer, gridStartX,
                    getCvColumns, getColumnOffset, getCvColor, onPushUndo
                )
            } else if (activeSource is DynamicVisualSource) {
                val header = (activeSource as? llm.slop.liquidlsd.rendering.isf.ISFVisualSource)?.header
                activeSource.parameters.forEach { (name, param) ->
                    ParametersRenderer.drawParamRow(session, ParamLabels.of(header, name), "$deckLabel/${activeSource.displayName}/$name", param, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
            }
        }

        drawSubGroupContent(session, deckLabel, "FX", state) {
            ImGui.indent(PARAM_INDENT)
            drawFxChainContent(
                session, deck.fxChain, "$deckLabel/FX", "$deckLabel FX", state,
                labelColW, mixer, gridStartX, getCvColumns, getColumnOffset, getCvColor, onPushUndo,
                deckLabel = deckLabel
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

    /**
     * Collapsible list of a slot's Metaknob targets under its header: one [FxMetaBindingEditor] block per target plus an
     * "Add Target" menu of the parameters that aren't targets yet. The Edit-row strip edits the same list; this is the mouse-first copy.
     */
    private fun drawMetaLinks(state: ParametersState, chainPrefix: String, chain: FxChain, fx: llm.slop.liquidlsd.rendering.isf.ISFFilter, slotNum: Int, width: Float) {
        val edit = FxMetaBindingEditor.editFor(state, chainPrefix, chain, fx)
        val id = "$chainPrefix/FX$slotNum"
        if (!ImGui.treeNodeEx("Metaknob targets (${fx.metaBindings.size})##metalinks_$id", imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth)) return
        itemTooltip("What ${fx.displayName}'s Metaknob drives: each target has its own range, response curve and zone of the knob's travel.")
        for (binding in fx.metaBindings.toList()) {
            ImGui.textDisabled(FxMetaEdit.targetLabel(fx, binding))
            if (fx.isMissingTarget(binding)) itemTooltip("${fx.displayName} has no parameter by this name (renamed, or the target came from another effect), so this target drives nothing. Remove it or add the right parameter.")
            FxMetaBindingEditor.drawFull(fx, binding, width, edit)
        }
        if (ImGui.smallButton("Add Target##metaadd_$id")) ImGui.openPopup("metaadd_pop_$id")
        pushOpenDropdownPadding()
        if (ImGui.beginPopup("metaadd_pop_$id")) {
            pushOpenDropdownFont()
            val linked = fx.metaBindings.mapTo(HashSet()) { it.targetParamName }
            var any = false
            for (input in fx.header.INPUTS) {
                if (!input.TYPE.equals("float", ignoreCase = true) || input.NAME in linked || fx.parameters[input.NAME] == null) continue
                any = true
                if (ImGui.menuItem(ParamLabels.of(fx.header, input.NAME))) edit.run(null) { it.setParamLink(input.NAME, MetaLinkMode.FULL) }
            }
            if (null !in linked) {
                any = true
                if (ImGui.menuItem("Dry/Wet")) edit.run(null) { it.metaBindings.add(llm.slop.liquidlsd.rendering.isf.FxMetaBinding.DRY_WET_SAFETY_NET) }
            }
            if (!any) ImGui.textDisabled("Every parameter is already a target")
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        ImGui.treePop()
    }

    /**
     * Collapsible "Knobs" list under a slot header: the parameters in focus-knob order (knobs 2-4, three per page) with
     * move up / down and hide, and a Hidden list to bring them back. A parameter the Metaknob owns is marked: its knob is locked.
     */
    private fun drawKnobLayout(state: ParametersState, chainPrefix: String, chain: FxChain, fx: llm.slop.liquidlsd.rendering.isf.ISFFilter, slotNum: Int) {
        val edit = FxMetaBindingEditor.editFor(state, chainPrefix, chain, fx)
        val id = "$chainPrefix/FX$slotNum"
        val shown = FxMetaEdit.shownKnobs(fx)
        if (!ImGui.treeNodeEx("Knobs (${shown.size})##knobs_$id", imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth)) return
        itemTooltip("Which parameters ${fx.displayName} puts on focus-mode knobs 2-4, three per page. Reorder or hide them; the order is saved with the slot.")
        fun label(n: String) = ParamLabels.of(fx.header, n)
        for ((i, name) in shown.withIndex()) {
            if (i % 3 == 0) ImGui.textDisabled("Page ${i / 3 + 1}")
            val owned = fx.metaBindings.any { it.enabled && it.targetParamName == name }
            ImGui.text("K${2 + i % 3}  ${label(name)}")
            if (owned) {
                ImGui.sameLine(); ImGui.textDisabled(Icons.LOCK)
                itemTooltip("The Metaknob drives this parameter, so its knob does nothing while that target is enabled.")
            }
            ImGui.sameLine(0f, 12f)
            if (ImGui.smallButton("${Icons.CHEVRON_UP}##kup_${id}_$name")) edit.run(null) { FxMetaEdit.moveKnob(it, i, -1) }
            itemTooltip("Move up")
            ImGui.sameLine()
            if (ImGui.smallButton("${Icons.CHEVRON_DOWN}##kdn_${id}_$name")) edit.run(null) { FxMetaEdit.moveKnob(it, i, 1) }
            itemTooltip("Move down")
            ImGui.sameLine()
            if (ImGui.smallButton("${Icons.X}##khide_${id}_$name")) edit.run(null) { FxMetaEdit.hideKnob(it, name) }
            itemTooltip("Take off the knobs")
        }
        val hidden = FxMetaEdit.hiddenKnobs(fx)
        if (hidden.isNotEmpty()) {
            ImGui.textDisabled("Hidden")
            for (name in hidden) {
                ImGui.text(label(name))
                ImGui.sameLine(0f, 12f)
                if (ImGui.smallButton("Show##kshow_${id}_$name")) edit.run(null) { FxMetaEdit.showKnob(it, name) }
            }
        }
        if (fx.knobLayout != null && ImGui.smallButton("Reset order##kreset_$id")) edit.run(null) { FxMetaEdit.resetKnobs(it) }
        ImGui.treePop()
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
        startRow: Int = 0,
        deckLabel: String? = null
    ): Int {
        var row = startRow
        val rowStartX = ImGui.getCursorPosX()
        val canonicalModuleId = state.deepEditModuleForTopTab(deckLabel ?: "Mixer") ?: llm.slop.liquidlsd.macro.MacroEngine.MASTER

        // Clip the title so it stops short of the kebab instead of running underneath it.
        val fullTitle = chain.name.ifEmpty { chainDisplayName.uppercase() }
        var title = fullTitle
        val titleMaxW = labelColW - 24f - ImGui.getStyle().itemSpacingX
        if (ImGui.calcTextSize(title).x > titleMaxW) {
            while (title.length > 1 && ImGui.calcTextSize("$title…").x > titleMaxW) title = title.dropLast(1)
            title = title.trimEnd() + "…"
        }
        ImGui.textDisabled(title.replace("%", "%%"))
        if (title != fullTitle) itemTooltip(fullTitle)
        ImGui.sameLine(labelColW - 24f)
        val chainMenuId = "FXChainKebabPopup_$chainPrefix"
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ButtonChrome.button("${Icons.MORE_VERTICAL}##fx_chain_kebab_$chainPrefix", 22f, 20f)) {
                ImGui.openPopup(chainMenuId)
            }
        }
        itemTooltip("Chain operations (Focus, Save As, Revert, Clear, Copy/Paste).")
        llm.slop.liquidlsd.macro.FxMacroSync.bankIdFor(chain, mixer)?.let { bankId ->
            FxChainHeader.drawChainMenu(session, mixer, chain, bankId, llm.slop.liquidlsd.macro.FxMacroSync.labelFor(bankId) ?: chainDisplayName, chainMenuId)
        }

        ImGui.setCursorPosX(rowStartX)
        ParametersRenderer.drawParamRow(session, "Chain Dry/Wet", "$chainPrefix/DryWet", chain.dryWet, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

        // Drag and Drop Target for Chain
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = java.io.File(payload)
                if (file.exists() && file.extension.lowercase() == "lsdfxchain") {
                    llm.slop.liquidlsd.presets.FxOps.loadChain(session, file, chain, undoable = true)
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

        // --- Per-Slot Controls for Chain (accordion: one slot open at a time) ---
        val expandedSlot = state.fxExpandedSlot[chainPrefix] ?: chain.slots.indexOfFirst { it != null }
        for (i in chain.slots.indices) {
            val slotNum = i + 1
            val fx = chain.slots[i]
            val filterName = fx?.displayName ?: "None"
            val isCollapsed = expandedSlot != i

            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.smallButton("${if (isCollapsed) Icons.CHEVRON_DOWN else Icons.CHEVRON_UP}##fx${slotNum}_collapse_$chainPrefix")) {
                    state.fxExpandedSlot[chainPrefix] = if (isCollapsed) i else ParametersState.NO_SLOT_EXPANDED
                }
            }
            itemTooltip(if (isCollapsed) "Expand Slot $slotNum (collapses the others)." else "Collapse Slot $slotNum.")
            ImGui.sameLine()

            ImGui.textDisabled("Slot $slotNum")
            ImGui.sameLine()
            ImGui.setNextItemWidth((labelColW - 85f).coerceAtLeast(30f))
            session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
                if (ButtonChrome.button("$filterName  ${Icons.CHEVRON_DOWN}##fx${slotNum}_selector_$chainPrefix", (labelColW - 85f).coerceAtLeast(30f), 0f)) {
                    state.openFxChainBrowse(canonicalModuleId, deckLabel, i)
                }
                fx?.header?.DESCRIPTION?.takeIf { it.isNotBlank() }?.let { itemTooltip(it) }
            }

            if (isCollapsed && fx != null) {
                ImGui.sameLine()
                ImGui.textDisabled("${(fx.dryWet.baseValue * 100f).roundToInt()}%%") // textDisabled takes a printf format
                itemTooltip("Slot $slotNum Dry/Wet")
            }
            ImGui.sameLine()

            // Per-Slot Kebab Menu
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ButtonChrome.button("${Icons.MORE_VERTICAL}##fx_slot_kebab_${slotNum}_$chainPrefix", 22f, 20f)) {
                    ImGui.openPopup("FXSlotKebabPopup_${slotNum}_$chainPrefix")
                }
            }
            itemTooltip("Slot $slotNum Options (Save, Copy, Paste, Reset)")

            pushOpenDropdownPadding()
            if (ImGui.beginPopup("FXSlotKebabPopup_${slotNum}_$chainPrefix")) {
                pushOpenDropdownFont()
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
                        llm.slop.liquidlsd.presets.FxOps.applySlot(chain, i, it, undoable = true)
                    }
                }
                if (ImGui.menuItem("Clear Slot", "", false, hasFx)) {
                    llm.slop.liquidlsd.presets.FxOps.clearSlot(chain, i, undoable = true)
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()

            if (fx != null && !isCollapsed) {
                drawMetaLinks(state, chainPrefix, chain, fx, slotNum, labelColW)
                drawKnobLayout(state, chainPrefix, chain, fx, slotNum)
                ParametersRenderer.drawParamRow(session, "Dry/Wet", "$chainPrefix/FX$slotNum/DryWet", fx.dryWet, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                fx.parameters.forEach { (name, param) ->
                    val existingBinding = fx.getBindingForParam(name)
                    val inputLabel = fx.header.INPUTS.find { it.NAME == name }?.LABEL
                    val descWithTarget = if (existingBinding != null) {
                        val note = "[Metaknob target: ${LinkModeButton.zoneName(existingBinding.linkMode)}${if (existingBinding.invert) ", inverted" else ""}]"
                        if (inputLabel != null) "$inputLabel\n$note" else note
                    } else inputLabel

                    ParametersRenderer.drawParamRow(
                        session, ParamLabels.of(fx.header, name), "$chainPrefix/FX$slotNum/$name", param, state, labelColW, mixer, gridStartX, row++,
                        getCvColumns, getColumnOffset, getCvColor, onPushUndo,
                        // A shortcut to add or remove this parameter as a target; its zone, curve and range are edited in Metaknob targets above.
                        extraMenuItems = if (fx.header.INPUTS.any { it.NAME == name && it.TYPE.equals("float", ignoreCase = true) }) { {
                            ImGui.separator()
                            if (ImGui.menuItem("Metaknob Target", "", existingBinding != null)) {
                                FxMetaEdit.apply(state, chainPrefix, chain, fx) { it.setParamLink(name, if (existingBinding == null) MetaLinkMode.FULL else null) }
                            }
                            itemTooltip(if (existingBinding == null) "Let ${fx.displayName}'s Metaknob drive this parameter." else "Stop the Metaknob driving this parameter.")
                        } } else null,
                        descriptionOverride = descWithTarget
                    )
                }
            }

            // Drag and Drop Target for FX Slot
            if (ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (payload != null) {
                    val file = java.io.File(payload)
                    if (file.exists()) llm.slop.liquidlsd.presets.FxOps.dropAsset(session, file, chain, i, undoable = true)
                }
                ImGui.acceptDragDropPayload<String>(llm.slop.liquidlsd.ui.FxSlotCell.PAYLOAD_STOCK_FILTER)?.let { filterId ->
                    llm.slop.liquidlsd.presets.FxOps.setSlotFilter(chain, i, filterId, undoable = true)
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


