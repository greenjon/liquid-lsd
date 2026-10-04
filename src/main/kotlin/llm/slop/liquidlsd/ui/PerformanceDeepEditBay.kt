package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.presets.QueueNextUp
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Modular Rack Deep Edit bay (Tier 2/3):
 * Scrollable region beneath the Tier-1 macro knob grid showing every module currently above COLLAPSED.
 * Hosts the 3-column layout: 5-channel side tabs, parameter grid with column headers, and properties CV detail editor.
 */
internal class PerformanceDeepEditBay(private val ctx: PerformanceUiContext) {

    private val browseBay = PerformanceBrowseBay(ctx)

    /**
     * The open Deep Edit that receives Ctrl+S/C/V and Delete -- the one last clicked, else the first
     * open. Null when no Deep Edit is open, in which case only Ctrl+Z (undo) is handled.
     */
    var keyboardOwnerModuleId: String? = null
        private set

    companion object {
        /** Deep Edit parameter-grid label column width and the gap between its three columns. */
        const val DEEP_EDIT_LABEL_COL_W = 185f
        const val DEEP_EDIT_GAP = 8f

        /**
         * Properties-column width reserved by [calculateMinWidth]. Deep Edit itself will shrink
         * Properties to 280px; 450px keeps the Mixer column from squeezing it that far on most screens.
         */
        const val DEEP_EDIT_RESERVED_PROPS_W = 450f

        /** Canonical module ids that have a Deep Edit editor. */
        val deepEditModuleIds = setOf(MacroEngine.DECK_A, MacroEngine.DECK_B, MacroEngine.DECK_BG, MacroEngine.DECK_PV, MacroEngine.MASTER)
    }

    /**
     * Updates keyboard focus ownership and handles fallback shortcuts when no Deep Edit is active.
     */
    fun beginFrame(parametersState: ParametersState, mixer: Mixer) {
        val expandedIds = expandedDeepEditModuleIds(parametersState)
        if (keyboardOwnerModuleId !in expandedIds) keyboardOwnerModuleId = expandedIds.firstOrNull()
        if (keyboardOwnerModuleId == null) handleDeepEditKeys(parametersState, mixer, fullSet = false)
    }

    /** Friendly title for a rack module id shown in the Deep-Edit region's header line. */
    fun rackModuleDisplayLabel(moduleId: String): String = when (moduleId) {
        MacroEngine.DECK_A -> "DECK A"
        MacroEngine.DECK_B -> "DECK B"
        MacroEngine.DECK_BG -> "DECK BG"
        MacroEngine.DECK_PV -> "DECK PV"
        MacroEngine.DECK_A_FX -> "DECK A FX"
        MacroEngine.DECK_B_FX -> "DECK B FX"
        MacroEngine.DECK_BG_FX -> "DECK BG FX"
        MacroEngine.DECK_PV_FX -> "DECK PV FX"
        MacroEngine.TRANS -> "TRANSITIONS"
        MacroEngine.MASTER -> "MASTER"
        MacroEngine.FX_SENDS -> "FX WET/DRY"
        MacroEngine.MASTER_FX -> "MASTER FX"
        else -> moduleId
    }

    fun expandedDeepEditModuleIds(parametersState: ParametersState): List<String> =
        parametersState.rackModuleDisclosure.entries
            .filter { it.value != ParametersState.DisclosureLevel.COLLAPSED && it.key != MacroEngine.FX_SENDS }
            .map { it.key }

    /**
     * Runs [ParametersKeyboard] shortcuts. [fullSet] = false handles only undo. Cell edits also
     * require the Performance window to be focused, so Delete in the Library doesn't also reset
     * the Deep Edit selection.
     */
    fun handleDeepEditKeys(parametersState: ParametersState, mixer: Mixer, fullSet: Boolean) {
        val onPushUndo = { s: ParametersState, m: Mixer -> ParametersUndo.pushUndoState(s, m) }
        val onPerformUndo = { s: ParametersState, m: Mixer -> ParametersUndo.performUndo(s, m) }
        ParametersKeyboard.handleKeyboardShortcuts(
            state = parametersState,
            mixer = mixer,
            deckPresetController = ctx.deckPresetController,
            onPushUndo = onPushUndo,
            onPerformUndo = onPerformUndo,
            allowSave = fullSet,
            allowCellEdits = fullSet && ImGui.isWindowFocused(imgui.flag.ImGuiFocusedFlags.RootAndChildWindows)
        )
    }

    /**
     * Scrollable region beneath the Tier-1 grid showing every module currently above COLLAPSED
     * (normally one; a Learn-pinned module can keep a second open). Never re-runs
     * [llm.slop.liquidlsd.macro.FxMacroSync] -- expand/collapse is strictly a display detail.
     */
    fun drawRackBay(session: SessionContext, mixer: Mixer, parametersState: ParametersState, bayH: Float) {
        val expandedModules = expandedDeepEditModuleIds(parametersState)
        if (expandedModules.isEmpty()) return

        if (ImGui.beginChild("##rack_bay_area", 0f, bayH, true)) {
            for ((idx, moduleId) in expandedModules.withIndex()) {
                if (idx > 0) {
                    ImGui.spacing(); ImGui.separator(); ImGui.spacing()
                }
                ImGui.beginGroup()
                drawRackBayModule(session, mixer, parametersState, moduleId)
                ImGui.endGroup()
                val clicked = ImGui.isMouseClicked(0) || ImGui.isMouseClicked(1)
                if (clicked && ImGui.isMouseHoveringRect(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY())) {
                    keyboardOwnerModuleId = moduleId
                }
            }
        }
        ImGui.endChild()
    }

    /**
     * The Deep Edit tier: the full parameter/CV editor, reusing
     * [ParametersTabs.drawDeckGroupContent]/[ParametersTabs.drawMasterFxContent] and
     * [PropertiesPanel.draw] (see [drawRackDeepEdit]). Macro binding is edited in the Edit-row [PerformanceMacroStrip]
     * and the Properties editor; pressing Learn on a Tier-1 knob focuses this tab (see [focusDeepEditTab]). No title or Collapse button here: the
     * row above already says which deck/section this is, and it has its own Collapse (as does Esc).
     *
     * A module that has any Browse target (every [deepEditModuleIds] member does) gets a tab row
     * (Edit | SRC/TRANS | Chain | FX1-3, see [drawModeTabs]) above its content; clicking a row's generator badge/FX slot/FX
     * chain name/transition name jumps straight into Browse (see [ParametersState.openBrowse]),
     * bypassing the tabs, which are how you get back to Params or move between targets.
     */
    fun drawRackBayModule(session: SessionContext, mixer: Mixer, parametersState: ParametersState, moduleId: String) {
        if (moduleId in deepEditModuleIds) {
            drawModeTabs(session, parametersState, moduleId, ctx.deckLabelForModuleId(moduleId))
        }
        when (parametersState.sectionModeFor(moduleId)) {
            ParametersState.SectionMode.BROWSE -> browseBay.draw(session, mixer, parametersState, moduleId)
            ParametersState.SectionMode.PARAMS -> drawRackDeepEdit(session, mixer, parametersState, moduleId)
        }
    }

    /**
     * The bay's single tab row: `Edit | SRC | Chain | FX1 | FX2 | FX3` for a deck, with TRANS in place
     * of SRC on Master. Edit is the Params editor; the rest are Browse targets (see
     * [ParametersState.BrowseTarget]), so the active tab is derived from [ParametersState.sectionModeFor]
     * and [ParametersState.browseTargetFor] rather than stored separately.
     */
    private fun drawModeTabs(session: SessionContext, parametersState: ParametersState, moduleId: String, deckLabel: String?) {
        val inBrowse = parametersState.sectionModeFor(moduleId) == ParametersState.SectionMode.BROWSE
        val target = parametersState.browseTargetFor(moduleId)
        val lead = if (deckLabel != null) "SRC" else "TRANS"
        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            fun tab(label: String, tip: String, active: Boolean, onClick: () -> Unit) {
                if (active) ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getStyle().getColor(ImGuiCol.ButtonActive))
                if (ImGui.button("$label##bay_tab_${moduleId}_$label")) onClick()
                if (active) ImGui.popStyleColor()
                itemTooltip(tip)
            }
            tab("Edit", "Edit parameters, modulation and properties.", !inBrowse) { parametersState.openParams(moduleId) }
            // Edit shows parameters; every tab after the label opens a picker for that slot.
            ImGui.sameLine(0f, 12f)
            ImGui.alignTextToFramePadding()
            ImGui.textDisabled("Pick:")
            ImGui.sameLine()
            if (deckLabel != null) {
                tab(lead, "Pick this deck's source.", inBrowse && target is ParametersState.BrowseTarget.Gen) {
                    parametersState.openGenBrowse(moduleId, deckLabel)
                }
            } else {
                tab(lead, "Pick the active mixer transition.", inBrowse && target is ParametersState.BrowseTarget.Transition) {
                    parametersState.openTransitionBrowse()
                }
            }
            val fxSlot = (target as? ParametersState.BrowseTarget.FxChain)?.let { it.slotIndex }
            val inFx = inBrowse && target is ParametersState.BrowseTarget.FxChain
            for (i in -1 until FxChain.SLOT_COUNT) {
                ImGui.sameLine()
                val label = if (i == -1) "Chain" else "FX${i + 1}"
                val tip = if (i == -1) "Load or clear a saved FX chain." else "Pick the effect in slot ${i + 1}."
                val slot = if (i == -1) null else i
                tab(label, tip, inFx && fxSlot == slot) { parametersState.openFxChainBrowse(moduleId, deckLabel, slot) }
            }
            drawQueueNextUp(session, deckLabel)
        }
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
    }

    /**
     * Right-aligned, on the tab row: what the queue feeding this module plays next. The Library (and its queue
     * columns) is off screen in Edit view, so without this a performer can't see what's coming. Decks A/B and
     * Master show the play queue, BG its own queue; PV has none.
     */
    private fun drawQueueNextUp(session: SessionContext, deckLabel: String?) {
        val text = when {
            deckLabel == "Deck BG" -> session.bgQueueManager.let {
                QueueNextUp.describe(it.queue, it.activeIndex, it.isShuffleEnabled, it.isRepeatEnabled)
            }
            deckLabel == "Deck PV" -> return
            else -> session.playQueueManager.let {
                val staged = when {
                    it.stagedDeckA -> "A"
                    it.stagedDeckB -> "B"
                    else -> null
                }
                QueueNextUp.describe(it.queue, it.activeIndex, it.isShuffleEnabled, it.isRepeatEnabled, staged)
            }
        }
        val left = ImGui.getCursorPosX() + ImGui.getStyle().itemSpacingX
        val avail = ImGui.getWindowWidth() - left - ImGui.getStyle().windowPaddingX
        if (avail < 60f) return
        val fitted = TextFit.ellipsize(text, avail)
        ImGui.sameLine()
        ImGui.setCursorPosX((ImGui.getWindowWidth() - ImGui.getStyle().windowPaddingX - ImGui.calcTextSize(fitted).x).coerceAtLeast(left))
        ImGui.alignTextToFramePadding()
        ImGui.textDisabled(fitted)
        itemTooltip(text + "\nThe Library queue columns are hidden in Edit view; Esc returns to them.")
    }

    fun deepEditParamsWidth(session: SessionContext, metrics: GridMetrics): Float {
        val lastCol = rackVisibleColumns(session).last()
        val maxGridW = rackColumnOffset(session, lastCol, metrics) + metrics.cell + metrics.cellPad * 0.5f
        // Reserve just the scrollbar's own width plus a thin sliver of breathing room -- not a full margin.
        val scrollbarMargin = ImGui.getStyle().scrollbarSize + 3.5f
        return DEEP_EDIT_LABEL_COL_W + maxGridW + scrollbarMargin
    }

    /**
     * Width the Performance window needs for Deep Edit's side rail + parameter grid + a
     * [DEEP_EDIT_RESERVED_PROPS_W] Properties column. UIManager uses it to cap the Mixer column,
     * so it never squeezes Deep Edit.
     */
    fun calculateMinWidth(session: SessionContext): Float {
        val metrics = GridMetrics.compute(session)
        val style = ImGui.getStyle()
        // Outer window + the bordered bay child each add padding on both sides.
        val padding = (style.windowPaddingX + style.windowBorderSize) * 4f
        return ParametersTabs.calculateLeftTabsWidth(session) + DEEP_EDIT_GAP + deepEditParamsWidth(session, metrics) +
            DEEP_EDIT_GAP + DEEP_EDIT_RESERVED_PROPS_W + padding
    }

    fun rackCvColumns(session: SessionContext): List<String> = ParameterGridHeaders.getCvColumns(session)

    fun rackVisibleColumns(session: SessionContext): List<String> = ParameterGridHeaders.getVisibleColumns(session)

    fun rackColumnOffset(session: SessionContext, colId: String, metrics: GridMetrics): Float {
        val visible = rackVisibleColumns(session)
        val targetId = if (colId == "final") "value" else colId
        val index = visible.indexOf(targetId)
        if (index < 0) return 0f
        return index * (metrics.cell + metrics.cellPad)
    }

    /**
     * Tier 3 (Deep Edit): the full parameter/CV editor -- side rail, then
     * [ParameterGridHeaders.drawColumnHeaders] (VAL/MIDI/LFO/SEQ/AUD headers, which also draw the
     * SRC/FX, CTRL/FX/TRANS section tabs) over [ParametersTabs.drawDeckGroupContent] (decks),
     * [ParametersTabs.drawMasterFxContent] (Master FX) or [ParametersTabs.drawMixerGroupContent]
     * (Transitions/Master), and [PropertiesPanel.draw] on the right for the per-parameter CV
     * detail (LFO/MIDI/SEQ/AUD) of whichever cell is selected.
     *
     * [ParametersState.activeTopTab] and [ParametersState.selectedCell]/[ParametersState.selectedParam]
     * are shared globals read by the drawers above, so they're saved, temporarily pointed at this
     * module's own state ([ParametersState.rackSelectedCell]), and restored afterward -- this keeps
     * two simultaneously open Deep Edits (Multi mode) from fighting over one shared selection.
     */
    fun drawRackDeepEdit(session: SessionContext, mixer: Mixer, parametersState: ParametersState, moduleId: String) {
        val deckLabel = ctx.deckLabelForModuleId(moduleId)
        val isMasterFx = moduleId == MacroEngine.MASTER_FX
        if (deckLabel != null && moduleId.endsWith("_fx")) {
            parametersState.setDeckSubTab(deckLabel, "FX")
        }
        // Transitions and Master both live under the "Mixer" top tab
        // (CTRL: crossfade/master level/queue nav/tap tempo; FX: master FX; TRANS: transition shader + dry/wet +
        // its own parameters) -- reuse that set of subtabs verbatim rather than reimplementing.
        val isMixerModule = moduleId == MacroEngine.TRANS || moduleId == MacroEngine.MASTER || moduleId == MacroEngine.MASTER_FX || moduleId == "Mixer"

        val ownsKeyboard = moduleId == keyboardOwnerModuleId
        if (deckLabel == null && !isMasterFx && !isMixerModule) {
            if (ownsKeyboard) handleDeepEditKeys(parametersState, mixer, fullSet = false)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                ImGui.textDisabled("Deep Edit isn't available for this module.")
            }
            return
        }

        val deck = deckLabel?.let { ctx.deckForLabel(mixer, it) }
        if (deck != null && deck.isEmpty) {
            if (ownsKeyboard) handleDeepEditKeys(parametersState, mixer, fullSet = false)
            DeckSourcePicker.drawLaunchpad(session, deckLabel, deck, parametersState, mixer, ctx.deckPresetController)
            return
        }

        val savedTopTab = parametersState.activeTopTab
        val savedCell = parametersState.selectedCell
        val savedParam = parametersState.selectedParam
        parametersState.selectedCell = parametersState.rackSelectedCell[moduleId]
        parametersState.selectedParam = parametersState.selectedCell?.let { cell ->
            ParameterResolver.findParameterByPath(mixer, cell.paramKey)
        }
        parametersState.activeTopTab = deckLabel ?: if (isMixerModule) "Mixer" else parametersState.activeTopTab
        val moduleTopTab = parametersState.activeTopTab
        var nextTopTab = moduleTopTab
        if (ownsKeyboard) handleDeepEditKeys(parametersState, mixer, fullSet = true)

        // 3-Column Layout:
        // Left Column: 5-channel side rail (MIX, A, B, BG, PV)
        // Middle Column: Parameter grid with column headers & section subtabs
        // Right Column: Properties CV detail editor
        val sideTabWidth = ParametersTabs.calculateLeftTabsWidth(session)
        val metrics = GridMetrics.compute(session)
        val cvColumnsFn = { rackCvColumns(session) }
        val columnOffsetFn = { colId: String -> rackColumnOffset(session, colId, metrics) }
        val colorFn = { colId: String, alpha: Float -> CvTheme.getThemeColor(colId, alpha) }
        val onPushUndo = { ParametersUndo.pushUndoState(parametersState, mixer) }
        val labelColW = DEEP_EDIT_LABEL_COL_W
        val paramsW = deepEditParamsWidth(session, metrics)

        val totalAvailW = ImGui.getContentRegionAvailX()
        val gap = DEEP_EDIT_GAP
        val propsW = (totalAvailW - sideTabWidth - gap - paramsW - gap).coerceAtLeast(280f)
        val headerH = ParameterGridHeaders.calculateHeaderHeight(session)

        // 1. Left column: 5-channel side tabs (MIX, A, B, BG, PV)
        val rowTopY = ImGui.getCursorScreenPosY()
        val sideTabsX0 = ImGui.getCursorScreenPosX()
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        if (ImGui.beginChild("##rack_deep_side_tabs_$moduleId", sideTabWidth, 0f, false)) {
            ParametersTabs.drawPerformanceDeepEditSideTabs(session, parametersState, mixer, topOffset = headerH) { targetSection ->
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection(targetSection, parametersState.getActiveSubTab(targetSection))
                nextTopTab = targetSection
                parametersState.activeTopTab = targetSection
                when (targetSection) {
                    "Mixer" -> parametersState.openParams(MacroEngine.MASTER)
                    "Deck A" -> parametersState.openParams(MacroEngine.DECK_A)
                    "Deck B" -> parametersState.openParams(MacroEngine.DECK_B)
                    "Deck BG" -> parametersState.openParams(MacroEngine.DECK_BG)
                    "Deck PV" -> parametersState.openParams(MacroEngine.DECK_PV)
                }
            }
        }
        ImGui.endChild()
        ImGui.popStyleVar()

        ImGui.sameLine(0f, gap)

        // 2. Middle column: Parameter grid
        val paramsX0 = ImGui.getCursorScreenPosX()
        var contentBottomY = rowTopY
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        if (ImGui.beginChild("##rack_deep_params_$moduleId", paramsW, 0f, false)) {
            val gridStartX = ImGui.getCursorScreenPosX()

            ParameterGridHeaders.drawColumnHeaders(session, labelColW, parametersState, mixer, metrics, headerH)

            if (deck != null) {
                ParametersTabs.drawDeckGroupContent(session, deckLabel, deck, parametersState, labelColW, mixer, gridStartX, cvColumnsFn, columnOffsetFn, colorFn, onPushUndo)
            } else if (isMasterFx) {
                ParametersTabs.drawMasterFxContent(session, mixer, parametersState, labelColW, gridStartX, cvColumnsFn, columnOffsetFn, colorFn, onPushUndo)
            } else if (isMixerModule) {
                ParametersTabs.drawMixerGroupContent(session, mixer, parametersState, labelColW, gridStartX, cvColumnsFn, columnOffsetFn, colorFn, onPushUndo)
            }

            contentBottomY = ImGui.getCursorScreenPosY()
            ImGui.dummy(0f, 0f)
        }
        ImGui.endChild()
        ImGui.popStyleVar()

        // Deck-colored card behind the header + grid, flush against the side rail (no dead gap
        // between the vertical tabs and the grid) and flush against the top of the CTRL/FX/TRANS
        // tab row (no dead gap above the first param row).
        run {
            val dl = ImGui.getWindowDrawList()
            val deckColorTab = deckLabel ?: "Mixer"
            val accentColor = ParametersTabs.getDeckColor(deckColorTab, 0.7f)
            val accentFill = ParametersTabs.getDeckColor(deckColorTab, 0.04f)
            val boxMinX = sideTabsX0 + sideTabWidth
            val boxMaxX = paramsX0 + paramsW
            val boxMinY = rowTopY
            val boxMaxY = contentBottomY.coerceAtLeast(boxMinY + 60f)
            dl.addRectFilled(boxMinX, boxMinY, boxMaxX, boxMaxY, accentFill, 4f)
            dl.addRect(boxMinX, boxMinY, boxMaxX, boxMaxY, accentColor, 4f, 0, 1.5f)
        }

        ImGui.sameLine(0f, gap)

        // 3. Right column: Properties detail
        if (ImGui.beginChild("##rack_deep_props_$moduleId", propsW, 0f, true)) {
            PropertiesPanel.draw(session, parametersState, mixer)
        }
        ImGui.endChild()

        parametersState.rackSelectedCell[moduleId] = parametersState.selectedCell
        parametersState.selectedCell = savedCell
        parametersState.selectedParam = savedParam
        // Keep the focus on whichever open Deep Edit the user last picked (Multi mode can show several);
        // only claim it for this module if the focus doesn't point at any open Deep Edit.
        val savedModule = parametersState.deepEditModuleForTopTab(savedTopTab)
        val savedIsOpen = savedModule != null && parametersState.disclosureFor(savedModule) != ParametersState.DisclosureLevel.COLLAPSED
        parametersState.activeTopTab = if (nextTopTab == moduleTopTab && savedIsOpen) savedTopTab else nextTopTab
    }
}
