package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseCursor
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons
import java.io.File

/**
 * Performance Mode 4×4 Macro Knob Matrix (see docs/user_guide/macros_and_rack.md).
 *
 * Displays up to 16 knobs arranged in rows of 4 columns across 2 tabs ([DECKS] and [MASTER]),
 * mapped to canonical [MacroEngine] banks according to the active layout tab. Deck rows and the
 * Master row each carry their own knob-assign toggle ([SRC|FX] / [MIX|FX]), so there are no
 * standalone FX rows. Knob drag adjusts the underlying
 * [llm.slop.liquidlsd.macro.MacroControl.value] directly, and right-click arms hardware MIDI
 * Learn for that knob (the pulsing cyan ring shows an armed knob; a repeat right-click cancels).
 * A selected knob also shows an inline "Learn" button to arm parameter-bind Learn -- pressing it
 * jumps Column 3 to [UITheme.Column3Mode.MACROS] on the matching bank/tab (see
 * [navigateMacroPanelTo]), since the actual binding inspector (labels, bindings, curves, ranges)
 * lives there, not in this panel.
 *
 * Active tab is persisted via [UITheme.performanceMatrixTab] / [AppPreferences.performanceMatrixTab].
 *
 * Knob sizing: `diameter = min(availW/4 − pad, availH/rowCount − labelH − pad)` so every knob and
 * its label always fits on screen regardless of window aspect ratio or the active tab's row count.
 *
 * Each row of 4 knobs is enclosed in a rounded, accent-colored group box with a large centered
 * label (e.g. "DECK A") above it, so the current grouping is obvious at a glance.
 */
class PerformanceMatrixPanel {

    // -- Tab definitions ----------------------------------------------------------

    // internal (not private): UITheme needs Tab.entries.size to coerce the persisted tab index
    // without hardcoding a count that silently drifts when a tab is added/removed.
    internal enum class Tab(val label: String, val tooltip: String) {
        DECKS("DECKS", "One row per deck (Deck A / Deck B / Deck BG / Deck PV), knobs 1-4 each.\nEach row's [SRC|FX] pills switch its knobs between the visual source and the deck's FX chain."),
        MASTER("MASTER", "Master (crossfader + [MIX|FX]: composite alphas or Master FX chain), Transitions (picker + queue),\nper-deck FX wet/dry, and Clock (tap tempo / resync / clock source + 4 Global macro knobs).")
    }

    /**
     * Describes one row of 4 knobs: which bank to pull from, which 4-knob offset within that
     * bank (0 = knobs 0–3, 4 = knobs 4–7), and the RGB accent color for the row. Consecutive rows
     * that share both [bankId] and [groupLabel] are enclosed in a single group box (see
     * [drawMatrix]); [subLabel] (e.g. "1-4") distinguishes rows sharing one box.
     */
    private data class RowDescriptor(
        val bankId: String,
        val knobOffset: Int,
        val accent: FloatArray,
        val groupLabel: String,
        val subLabel: String? = null,
        /** True for rows that draw a title badge and side controls (deck/Master mode pills, chain header, bypass, Transitions/Clock lines). */
        val hasExtraHeader: Boolean = false,
        /** When false, the modular rack disclosure chevron and collapse controls are omitted. */
        val canExpand: Boolean = true
    )

    // Canonical deck colors matching BrowserDeckButtons are in PerformanceColors.
    companion object {
        /**
         * Floor on grid row height: low enough that all four rows fit above a HALF Library in a
         * 1280x720 display's ~688px window (~74px per row; knobs ~40-45px). Below this the grid scrolls.
         */
        private const val MIN_ROW_H = 68f

        private val TAB_ROWS: Array<List<RowDescriptor>> = arrayOf(
            // DECKS: one row per deck (knobs 0–3 each: Deck A, Deck B, Deck BG, Deck PV)
            listOf(
                RowDescriptor(MacroEngine.DECK_A,  0, PerformanceColors.COLOR_DECK_A,  "DECK A",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_B,  0, PerformanceColors.COLOR_DECK_B,  "DECK B",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_BG, 0, PerformanceColors.COLOR_DECK_BG, "DECK BG", hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_PV, 0, PerformanceColors.COLOR_DECK_PV, "DECK PV", hasExtraHeader = true),
            ),
            // MASTER: Master ([MIX] + crossfader over [FX] + chain header; knobs on composite
            // alphas or the Master FX chain), Transitions (transition picker + queue nav), FX
            // Wet/Dry (per-deck FX sends), Clock (tempo controls + Global macro knobs) -- 1 row of
            // 4 knobs each, laid out like deck rows (title badge + two control lines, see drawMatrix).
            listOf(
                RowDescriptor(MacroEngine.MASTER,    0, PerformanceColors.COLOR_MASTER, "MASTER", hasExtraHeader = true),
                RowDescriptor(MacroEngine.TRANS,     0, PerformanceColors.COLOR_TRANS,  "TRANSITIONS", hasExtraHeader = true),
                RowDescriptor(MacroEngine.FX_SENDS,  0, PerformanceColors.COLOR_FX,     "FX WET/DRY", hasExtraHeader = true, canExpand = false),
                RowDescriptor(MacroEngine.GLOBAL,    0, PerformanceColors.COLOR_GLOBAL, "CLOCK & GLOBAL", hasExtraHeader = true, canExpand = false),
            ),
        )
    }

    internal val ctx = PerformanceUiContext()
    private val deckControls = PerformanceDeckControls(ctx)
    private val deepEditBay = PerformanceDeepEditBay(ctx)

    // -- Draw ---------------------------------------------------------------------

    /**
     * @param hiddenLibraryH height the HALF Library would take if it were on screen -- non-zero only
     *   in Edit view, where the Library is hidden. Rows are sized from the Perform-view height
     *   (window height minus this), so a row keeps the same knob size and control positions when it
     *   opens in Deep Edit.
     */
    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        deckPresetController: DeckPresetController? = null,
        hiddenLibraryH: Float = 0f
    ) {
        ctx.deckPresetController = deckPresetController
        val theme = session.uiTheme

        deepEditBay.beginFrame(parametersState, mixer)
        drawTabStrip(session, theme, mixer, parametersState)
        ImGui.dummy(0f, 2f)

        // Modular Rack: when a module is in Deep Edit, every other row is hidden from the grid and
        // the Deep-Edit bay below gets the rest of the height. The open row is exactly as tall as
        // in Perform view plus [expandedExtraH] for the value readout and Learn button under its knobs.
        val tabIdx = theme.performanceMatrixTab.coerceIn(0, Tab.entries.size - 1)
        val visibleRows = visibleRowsForTab(tabIdx, parametersState)
        val anyExpanded = parametersState.anyRackModuleExpanded()
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)
        val layoutRows = substitutedRowsForTab(layoutTabIdx(tabIdx, visibleRows, anyExpanded), parametersState)
        val baseRowH = ((availH - hiddenLibraryH).coerceAtLeast(4f) / layoutRows.size).coerceAtLeast(MIN_ROW_H)
        val gridH = if (!anyExpanded) availH else (visibleRows.size * (baseRowH + expandedExtraH(session))).coerceAtMost((availH - 160f).coerceAtLeast(160f))
        val bayH = (availH - gridH - (if (anyExpanded) ImGui.getStyle().getItemSpacingY() else 0f)).coerceAtLeast(0f)

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        if (ImGui.beginChild("##rack_grid_area", 0f, gridH, false)) {
            drawMatrix(session, theme, mixer, parametersState, visibleRows, layoutRows, baseRowH)
        }
        ImGui.endChild()
        ImGui.popStyleVar()

        if (anyExpanded) {
            deepEditBay.drawRackBay(session, mixer, parametersState, bayH)
        }
    }

    /** Extra row height while a row is in Deep Edit: the second caption line (value readout) plus the Learn button. */
    private fun expandedExtraH(session: llm.slop.liquidlsd.SessionContext): Float =
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() } + 24f

    /** The tab whose Perform-view rows size the grid: the current tab, or in Edit view the tab that holds the open row. */
    private fun layoutTabIdx(tabIdx: Int, visibleRows: List<RowDescriptor>, anyExpanded: Boolean): Int {
        if (!anyExpanded) return tabIdx
        val moduleId = visibleRows.firstOrNull()?.let { ctx.canonicalModuleId(it.bankId) } ?: return tabIdx
        val idx = TAB_ROWS.indexOfFirst { rows -> rows.any { ctx.canonicalModuleId(it.bankId) == moduleId } }
        return if (idx >= 0) idx else tabIdx
    }

    /** Resolves the active 4-knob row descriptor for a specific module id based on the current subtab/mode. */
    private fun rowDescriptorForModule(moduleId: String, parametersState: ParametersState): RowDescriptor {
        return when (moduleId) {
            MacroEngine.DECK_A, MacroEngine.DECK_A_FX -> {
                val isFx = ctx.deckRowMode["A"] == "FX" || parametersState.activeDeckASubTab == "FX"
                if (isFx) {
                    RowDescriptor(MacroEngine.DECK_A_FX, 0, PerformanceColors.COLOR_DECK_A, "DECK A (FX)", hasExtraHeader = true)
                } else {
                    RowDescriptor(MacroEngine.DECK_A, 0, PerformanceColors.COLOR_DECK_A, "DECK A", hasExtraHeader = true)
                }
            }
            MacroEngine.DECK_B, MacroEngine.DECK_B_FX -> {
                val isFx = ctx.deckRowMode["B"] == "FX" || parametersState.activeDeckBSubTab == "FX"
                if (isFx) {
                    RowDescriptor(MacroEngine.DECK_B_FX, 0, PerformanceColors.COLOR_DECK_B, "DECK B (FX)", hasExtraHeader = true)
                } else {
                    RowDescriptor(MacroEngine.DECK_B, 0, PerformanceColors.COLOR_DECK_B, "DECK B", hasExtraHeader = true)
                }
            }
            MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX -> {
                val isFx = ctx.deckRowMode["BG"] == "FX" || parametersState.activeDeckBGSubTab == "FX"
                if (isFx) {
                    RowDescriptor(MacroEngine.DECK_BG_FX, 0, PerformanceColors.COLOR_DECK_BG, "DECK BG (FX)", hasExtraHeader = true)
                } else {
                    RowDescriptor(MacroEngine.DECK_BG, 0, PerformanceColors.COLOR_DECK_BG, "DECK BG", hasExtraHeader = true)
                }
            }
            MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX -> {
                val isFx = ctx.deckRowMode["PV"] == "FX" || parametersState.activeDeckPVSubTab == "FX"
                if (isFx) {
                    RowDescriptor(MacroEngine.DECK_PV_FX, 0, PerformanceColors.COLOR_DECK_PV, "DECK PV (FX)", hasExtraHeader = true)
                } else {
                    RowDescriptor(MacroEngine.DECK_PV, 0, PerformanceColors.COLOR_DECK_PV, "DECK PV", hasExtraHeader = true)
                }
            }
            MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.MASTER_FX, "Mixer" -> {
                when {
                    parametersState.activeMixerSubTab == "TRANS" -> RowDescriptor(MacroEngine.TRANS, 0, PerformanceColors.COLOR_TRANS, "TRANSITIONS", hasExtraHeader = true)
                    ctx.isMasterRowFx(parametersState) -> RowDescriptor(MacroEngine.MASTER_FX, 0, PerformanceColors.COLOR_MASTER, "MASTER (FX)", hasExtraHeader = true)
                    else -> RowDescriptor(MacroEngine.MASTER, 0, PerformanceColors.COLOR_MASTER, "MASTER", hasExtraHeader = true)
                }
            }
            else -> RowDescriptor(moduleId, 0, PerformanceColors.COLOR_MASTER, deepEditBay.rackModuleDisplayLabel(moduleId), hasExtraHeader = true)
        }
    }

    /** This tab's rows with the per-deck [SRC|FX] and Master [MIX|FX] toggles applied. */
    private fun substitutedRowsForTab(tabIdx: Int, parametersState: ParametersState? = null): List<RowDescriptor> {
        val templateRows = TAB_ROWS[tabIdx]
        return templateRows.map { row ->
            when {
                row.bankId == MacroEngine.DECK_A && (ctx.deckRowMode["A"] == "FX" || parametersState?.activeDeckASubTab == "FX") -> {
                    row.copy(bankId = MacroEngine.DECK_A_FX, groupLabel = "DECK A (FX)")
                }
                row.bankId == MacroEngine.DECK_B && (ctx.deckRowMode["B"] == "FX" || parametersState?.activeDeckBSubTab == "FX") -> {
                    row.copy(bankId = MacroEngine.DECK_B_FX, groupLabel = "DECK B (FX)")
                }
                row.bankId == MacroEngine.DECK_BG && (ctx.deckRowMode["BG"] == "FX" || parametersState?.activeDeckBGSubTab == "FX") -> {
                    row.copy(bankId = MacroEngine.DECK_BG_FX, groupLabel = "DECK BG (FX)")
                }
                row.bankId == MacroEngine.DECK_PV && (ctx.deckRowMode["PV"] == "FX" || parametersState?.activeDeckPVSubTab == "FX") -> {
                    row.copy(bankId = MacroEngine.DECK_PV_FX, groupLabel = "DECK PV (FX)")
                }
                row.bankId == MacroEngine.MASTER && parametersState != null && ctx.isMasterRowFx(parametersState) -> {
                    row.copy(bankId = MacroEngine.MASTER_FX, groupLabel = "MASTER (FX)")
                }
                else -> row
            }
        }
    }

    /**
     * When any module is in Deep Edit, returns the macro row(s) corresponding to the expanded module(s)
     * (reflecting active subtab e.g. SRC vs FX), decoupling the row from the matrix tab.
     * When all modules are collapsed, returns the current tab's 4 rows.
     */
    private fun visibleRowsForTab(tabIdx: Int, parametersState: ParametersState): List<RowDescriptor> {
        val expandedModuleIds = parametersState.rackModuleDisclosure
            .filterValues { it != ParametersState.DisclosureLevel.COLLAPSED }
            .keys
            .filter { it != MacroEngine.FX_SENDS }
        if (expandedModuleIds.isEmpty()) return substitutedRowsForTab(tabIdx, parametersState)

        // Show the active macro row for each expanded module
        return expandedModuleIds.map { rowDescriptorForModule(it, parametersState) }
    }

    // -- Tab strip ----------------------------------------------------------------

    private fun drawTabStrip(
        session: llm.slop.liquidlsd.SessionContext,
        theme: UITheme,
        mixer: Mixer,
        parametersState: ParametersState
    ) {
        val tabs = Tab.values()
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(1f)
        val gap = 4f
        val tabH = 26f
        val tabW = 85f
        val showAllDice = session.uiTheme.randomizationEnabled
        val allDiceBtnW = if (showAllDice) 76f else 0f

        for ((i, tab) in tabs.withIndex()) {
            if (i > 0) ImGui.sameLine(0f, gap)
            val isActive = theme.performanceMatrixTab == i
            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f))
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.15f, 0.62f, 0.82f, 1f))
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.22f, 0.25f, 0.30f, 1f))
            }
            session.uiTheme.withFont(UITheme.FontLevel.H3) {
                if (ImGui.button("${tab.label}##perf_tab_$i", tabW, tabH)) {
                    theme.performanceMatrixTab = i
                    AppPreferencesStore.savePreferences()
                }
            }
            itemTooltip(tab.tooltip)
            ImGui.popStyleColor(2)
        }

        // Modular Rack toolbar: Collapse All, Learn indicator
        ImGui.sameLine(0f, 10f)
        val anyExpanded = parametersState.anyRackModuleExpanded()
        ImGui.beginDisabled(!anyExpanded)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (ImGui.button("Close Edit##rack_collapse_all", 92f, tabH)) {
                parametersState.collapseAllRackModules()
            }
        }
        ImGui.endDisabled()
        itemTooltip("Close Deep Edit and return to the rows + Library (Esc does the same).")

        ImGui.sameLine(0f, 10f)
        llm.slop.liquidlsd.ui.rack.RackUnit.drawLearnIndicator()

        if (showAllDice) {
            val diceStartX = (availW - allDiceBtnW).coerceAtLeast(ImGui.getCursorPosX() + gap)
            ImGui.sameLine(diceStartX, 0f)
            ImGui.pushStyleColor(ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.25f, 0.18f, 0.32f, 0.90f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.38f, 0.25f, 0.48f, 1f))
            ImGui.pushStyleColor(ImGuiCol.Text,          ImGui.colorConvertFloat4ToU32(0.95f, 0.85f, 1.0f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.DICES} ALL##perf_rand_all", allDiceBtnW, tabH)) {
                    ParametersUndo.pushUndoState(parametersState, mixer)
                    mixer.randomizeAll()
                }
            }
            ImGui.popStyleColor(3)
            itemTooltip("Randomize ALL Decks (A, B, BG, PV) and Modulators.\nClick to randomize all decks with undo support.")
        }
    }

    // -- 4x4 Knob Grid -----------------------------------------------------------

    /** A run of consecutive [RowDescriptor]s sharing one bank/group label, enclosed in one box. */
    private data class RowGroup(val startRow: Int, val rowCount: Int, val descriptor: RowDescriptor)

    private fun groupRows(rows: List<RowDescriptor>): List<RowGroup> {
        val groups = mutableListOf<RowGroup>()
        var gi = 0
        while (gi < rows.size) {
            var gj = gi + 1
            while (gj < rows.size && rows[gj].bankId == rows[gi].bankId && rows[gj].groupLabel == rows[gi].groupLabel) gj++
            groups.add(RowGroup(gi, gj - gi, rows[gi]))
            gi = gj
        }
        return groups
    }

    /**
     * Draws [rows] (the visible rows -- see [visibleRowsForTab]). Knob size and control positions
     * come from [layoutRows] at [rowH] -- the Perform-view layout of the tab -- so a row opened in
     * Deep Edit looks identical except for [expandedExtraH] added below its knobs.
     */
    private fun drawMatrix(
        session: llm.slop.liquidlsd.SessionContext,
        theme: UITheme,
        mixer: Mixer,
        parametersState: ParametersState,
        rows: List<RowDescriptor>,
        layoutRows: List<RowDescriptor>,
        rowH: Float
    ) {
        val tabIdx = theme.performanceMatrixTab.coerceIn(0, Tab.entries.size - 1)
        val groups = groupRows(rows)

        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(4f)
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)

        val gridW = availW
        val extraH = expandedExtraH(session)
        fun isRowExpanded(row: RowDescriptor): Boolean =
            parametersState.disclosureFor(ctx.canonicalModuleId(row.bankId)) != ParametersState.DisclosureLevel.COLLAPSED ||
                parametersState.disclosureFor(row.bankId) != ParametersState.DisclosureLevel.COLLAPSED
        // Rows past MIN_ROW_H overflow and the ##rack_grid_area child scrolls (see the cursor
        // advance at the end of this function).
        val rowTopOffsets = FloatArray(rows.size + 1)
        for (i in rows.indices) rowTopOffsets[i + 1] = rowTopOffsets[i] + rowH + (if (isRowExpanded(rows[i])) extraH else 0f)
        val gridTotalH = rowTopOffsets[rows.size]

        val gridStartX = ImGui.getCursorScreenPosX()
        val gridStartY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()

        // Grid-wide background hit area, submitted first with overlap allowed so every knob/button
        // drawn later takes priority -- a click-drag on empty row space lands here and scrolls the
        // grid (see applyDragScroll), and it also keeps the drag from moving the host window.
        ImGui.setNextItemAllowOverlap()
        ImGui.invisibleButton("##perf_grid_drag_scroll", gridW, gridTotalH)
        applyDragScroll()
        ImGui.setCursorScreenPos(gridStartX, gridStartY)

        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        val subLabelH = captionH
        val boxMarginY = 2f   // gap between a group's box and the next group's / grid's edge
        val subLabelGap = 2f  // gap between a sub-label and the knobs below it
        val boxPad = 2.5f     // inner padding between the box border and the knobs it contains
        val pad = 6f

        val hasDeckRows = layoutRows.any {
            it.bankId in listOf(MacroEngine.DECK_A, MacroEngine.DECK_B, MacroEngine.DECK_BG, MacroEngine.DECK_PV) ||
            it.groupLabel.startsWith("DECK")
        }

        val isCompactRow = rowH < 95f
        val ctrlH = if (isCompactRow) 21f else PerformanceColors.CTRL_H
        val stackGap = if (isCompactRow) 2f else 3f

        // Every row: a title badge spanning both control lines, then two stacked control lines,
        // then the knobs. Deck badges are a large A/B/BG/PV; MASTER-tab badges are wider for words.
        val deckBadgeW = 54f
        val deckComboW = (gridW * 0.11f).coerceIn(85f, 140f)
        val deckRow1W = 28f + 4f + 70f + 4f + deckComboW + 4f + 22f + (if (session.uiTheme.randomizationEnabled) 4f + 22f else 0f) + 4f + 78f
        val deckLeftW = deckBadgeW + 6f + deckRow1W
        val deckRightW = 56f
        // MASTER tab: Master ([MIX] + crossfader over [FX] + chain header), Transitions (picker +
        // queue nav), FX Wet/Dry (badge only), Clock (source/BPM/beat over tempo actions).
        val masterTabBadgeW = 90f
        val masterRowW = maxOf(deckRow1W, (gridW * 0.38f).coerceAtMost(420f))
        val masterTabLeftW = masterTabBadgeW + 6f + masterRowW
        val masterRightW = 56f
        val isMasterTabLayout = layoutRows.any {
            it.bankId in listOf(MacroEngine.MASTER, MacroEngine.MASTER_FX, MacroEngine.TRANS, MacroEngine.FX_SENDS, MacroEngine.GLOBAL)
        }

        val maxLeftW = maxOf(
            if (hasDeckRows) deckLeftW else 0f,
            if (isMasterTabLayout) masterTabLeftW else 0f
        )
        val maxRightW = maxOf(
            if (hasDeckRows) deckRightW else 0f,
            if (isMasterTabLayout) masterRightW else 0f
        )

        val leftBoundary = gridStartX + pad + (if (maxLeftW > 0f) maxLeftW + 12f else 0f)
        val rightBoundary = gridStartX + gridW - pad - (if (maxRightW > 0f) maxRightW + 12f else 0f)
        val middleW = (rightBoundary - leftBoundary).coerceAtLeast(100f)

        val maxColW = middleW / 4f
        val diamByWidth = (maxColW - 12f).coerceAtLeast(8f)

        var diamByHeight = Float.MAX_VALUE
        // Knob label: 3f gap above caption + captionH + 4f margin below. An expanded row's second
        // line and Learn button sit in the extra [extraH] below, so they don't shrink the knob.
        val baseTextBelowH = captionH + 7f
        // FX rows: in group mode the slot knobs have no caption (the slot cell under them names
        // the effect), so only SUPER's caption and the slot cells share the line; in focus mode
        // every knob keeps its caption (parameter name) with the value cell below it.
        fun fxTextBelowH(bankId: String): Float =
            if (ctx.resolveFxChain(mixer, bankId).isFocused()) baseTextBelowH + FxSlotCell.HEIGHT + 4f
            else 7f + maxOf(captionH, FxSlotCell.HEIGHT)
        for (group in groupRows(layoutRows)) {
            val groupH = group.rowCount * rowH
            val hasSubLabel = layoutRows[group.startRow].subLabel != null
            val groupBankId = layoutRows[group.startRow].bankId
            val isFx = groupBankId in llm.slop.liquidlsd.macro.FxMacroSync.FX_BANK_IDS
            val contentH = groupH - boxMarginY * 2f - boxPad * 2f
            val subRowH = contentH / group.rowCount
            val knobAreaH = if (hasSubLabel) subRowH - subLabelH - subLabelGap else subRowH
            val rowTextBelowH = if (isFx) fxTextBelowH(groupBankId) else baseTextBelowH
            diamByHeight = minOf(diamByHeight, (knobAreaH - rowTextBelowH).coerceAtLeast(8f))
        }
        val diameter = minOf(diamByWidth, diamByHeight).coerceIn(20f, 100f)

        // Deck rows in SRC mode use the same uniform column pitch as FX (maxColW) so knob spacing
        // does not jump when toggling between SRC and FX.
        val targetColW = maxColW
        val knobColW = minOf(targetColW, maxColW)
        val knobsTotalW = 4 * knobColW
        val knobClusterStartX = leftBoundary + (middleW - knobsTotalW) / 2f

        // Explicit nonzero size rather than UITheme.withFont(H1) (which passes 0f for "native
        // baked size") -- on this draw-list addText path, 0f renders H1 no bigger than H3, so the
        // size is requested explicitly to get the real 22px glyphs.
        val h1Font = session.uiTheme.fontFor(UITheme.FontLevel.H1)
        val h1Pushable = h1Font != null && h1Font.ptr != 0L

        for (group in groups) {
            val descriptor = group.descriptor
            val groupTopY = gridStartY + rowTopOffsets[group.startRow]
            val groupBottomY = gridStartY + rowTopOffsets[group.startRow + group.rowCount]
            // Controls and knobs are laid out in the Perform-view band; an expanded row's extra
            // height only extends the box downward.
            val layoutBottomY = groupTopY + group.rowCount * rowH

            val boxTopY = groupTopY + boxMarginY
            val boxBottomY = groupBottomY - boxMarginY
            val boxX1 = gridStartX + 2f
            val boxX2 = gridStartX + gridW - 2f

            // Rounded group box (faint fill + accent border) around the group's knobs.
            val fillCol = ImGui.colorConvertFloat4ToU32(descriptor.accent[0], descriptor.accent[1], descriptor.accent[2], 0.07f)
            val borderCol = ImGui.colorConvertFloat4ToU32(descriptor.accent[0], descriptor.accent[1], descriptor.accent[2], 0.85f)
            dl.addRectFilled(boxX1, boxTopY, boxX2, boxBottomY, fillCol, 8f)
            dl.addRect(boxX1, boxTopY, boxX2, boxBottomY, borderCol, 8f, 0, 2f)

            val isDeckA = descriptor.bankId == MacroEngine.DECK_A || descriptor.bankId == MacroEngine.DECK_A_FX
            val isDeckB = descriptor.bankId == MacroEngine.DECK_B || descriptor.bankId == MacroEngine.DECK_B_FX
            val isDeckBG = descriptor.bankId == MacroEngine.DECK_BG || descriptor.bankId == MacroEngine.DECK_BG_FX
            val isDeckPV = descriptor.bankId == MacroEngine.DECK_PV || descriptor.bankId == MacroEngine.DECK_PV_FX
            val isDeckRow = isDeckA || isDeckB || isDeckBG || isDeckPV

            val isTransRow = descriptor.bankId == MacroEngine.TRANS
            val isClockRow = descriptor.bankId == MacroEngine.GLOBAL
            val isMasterRow = descriptor.bankId == MacroEngine.MASTER || descriptor.bankId == MacroEngine.MASTER_FX
            val displayLabel = descriptor.groupLabel

            val rawModuleId = descriptor.bankId
            val canonicalId = ctx.canonicalModuleId(rawModuleId)
            val isModuleExpanded = parametersState.disclosureFor(canonicalId) != ParametersState.DisclosureLevel.COLLAPSED ||
                                   parametersState.disclosureFor(rawModuleId) != ParametersState.DisclosureLevel.COLLAPSED
            val activeModuleId = if (parametersState.disclosureFor(canonicalId) != ParametersState.DisclosureLevel.COLLAPSED) canonicalId else rawModuleId
            val moduleId = activeModuleId
            val chevronSize = 18f

            val isSpecialHeaderRow = descriptor.hasExtraHeader && (isTransRow || isMasterRow || isClockRow)

            // Fallback title for expanded custom rack modules
            if (!isDeckRow && !isSpecialHeaderRow && descriptor.bankId != MacroEngine.FX_SENDS) {
                if (h1Pushable) ImGui.pushFont(h1Font, UITheme.FONT_H1)
                dl.addText(boxX1 + pad, boxTopY + boxPad, borderCol, displayLabel)
                if (h1Pushable) ImGui.popFont()
            }

            // Drop target placed over the deck badge / header area
            if (descriptor.hasExtraHeader) {
                if (isDeckRow) {
                    val targetDeck = when {
                        isDeckA -> mixer.deckA
                        isDeckB -> mixer.deckB
                        isDeckBG -> mixer.deckBG
                        else -> mixer.deckPV
                    }
                    val dropTag = when {
                        isDeckA -> "A"
                        isDeckB -> "B"
                        isDeckBG -> "BG"
                        else -> "PV"
                    }
                    ImGui.setCursorScreenPos(boxX1, boxTopY)
                    ImGui.setNextItemAllowOverlap()
                    ImGui.invisibleButton("##perf_deck_drop_${group.startRow}_$dropTag", (pad + deckBadgeW + 4f).coerceAtLeast(1f), (boxBottomY - boxTopY).coerceAtLeast(1f))
                    applyDragScroll()
                    if (ImGui.beginDragDropTarget()) {
                        val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                        if (payload != null) {
                            val file = File(payload)
                            if (file.exists() && file.extension.lowercase() == "lsdfxchain") {
                                llm.slop.liquidlsd.presets.FxOps.loadChain(session, file, targetDeck.fxChain)
                            } else if (file.exists() && file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                                val isDirty = session.presetManager.isDeckDirty(targetDeck, mixer)
                                if (!isDirty) {
                                    session.presetRepository.loadDeckPresetAsync(
                                        file,
                                        isDeckA = isDeckA,
                                        isDeckBG = isDeckBG,
                                        isDeckPV = isDeckPV
                                    )
                                } else {
                                    UIManager.triggerDeckDragDrop(file, targetDeck, isDeckA, mixer)
                                }
                            }
                        }
                        ImGui.endDragDropTarget()
                    }
                } else if (isMasterRow) {
                    ImGui.setCursorScreenPos(boxX1, boxTopY)
                    ImGui.setNextItemAllowOverlap()
                    ImGui.invisibleButton("##perf_master_drop_${group.startRow}", (pad + masterTabBadgeW + 4f).coerceAtLeast(1f), (boxBottomY - boxTopY).coerceAtLeast(1f))
                    applyDragScroll()
                    if (ImGui.beginDragDropTarget()) {
                        val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                        if (payload != null) {
                            val file = File(payload)
                            if (file.exists() && file.extension.lowercase() == "lsdfxchain") {
                                llm.slop.liquidlsd.presets.FxOps.loadChain(session, file, mixer.masterFxChain)
                            }
                        }
                        ImGui.endDragDropTarget()
                    }
                }
            }

            // Modular Rack disclosure chevron & Collapse button in top-right of box
            if (descriptor.canExpand) {
                val chevronY = boxTopY + 3f
                if (isModuleExpanded) {
                    ImGui.setCursorScreenPos(boxX2 - chevronSize - 80f, chevronY)
                    session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                        if (ImGui.smallButton("${Icons.CHEVRON_UP} Collapse##row_collapse_${tabIdx}_${group.startRow}")) {
                            parametersState.setDisclosure(activeModuleId, ParametersState.DisclosureLevel.COLLAPSED)
                            parametersState.setDisclosure(rawModuleId, ParametersState.DisclosureLevel.COLLAPSED)
                        }
                    }
                    itemTooltip("Collapse module back to standard row view.")
                }
                ImGui.setCursorScreenPos(boxX2 - chevronSize - 4f, chevronY)
                llm.slop.liquidlsd.ui.rack.RackUnit.drawChevron(
                    parametersState, activeModuleId, chevronSize, "${tabIdx}_${group.startRow}"
                )
            }

            val contentTopY = boxTopY + boxPad
            val contentBottomY = layoutBottomY - boxMarginY - boxPad
            val subRowH = (contentBottomY - contentTopY) / group.rowCount

            for (k in 0 until group.rowCount) {
                val rowIdx = group.startRow + k
                val row = rows[rowIdx]
                val bank: MacroBank = MacroEngine.getBank(row.bankId) ?: MacroEngine.bankForParamPath(row.bankId)

                val subTopY = contentTopY + k * subRowH
                val subBottomY = subTopY + subRowH

                val knobAreaTopY = if (row.subLabel != null) {
                    session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                        val subTextW = ImGui.calcTextSize(row.subLabel).x
                        dl.addText(gridStartX + (gridW - subTextW) / 2f, subTopY, borderCol, row.subLabel)
                    }
                    subTopY + subLabelH + subLabelGap
                } else {
                    subTopY
                }

                val isFxBankId = row.bankId in llm.slop.liquidlsd.macro.FxMacroSync.FX_BANK_IDS
                val effectiveTextBelowH = if (isFxBankId) fxTextBelowH(row.bankId) else baseTextBelowH
                val totalWidgetH = diameter + effectiveTextBelowH
                val availKnobH = subBottomY - knobAreaTopY
                val knobTopY = (knobAreaTopY + (availKnobH - totalWidgetH) * 0.5f)
                    .coerceIn(knobAreaTopY, (subBottomY - totalWidgetH).coerceAtLeast(knobAreaTopY))
                val knobCenterY = knobTopY + diameter / 2f

                val totalCtrlH = ctrlH * 2f + stackGap
                val row1Y = subTopY + ((subBottomY - subTopY) - totalCtrlH).coerceAtLeast(0f) * 0.5f
                val row2YFinal = row1Y + ctrlH + stackGap
                val ctrlY = (subTopY + subBottomY - ctrlH) * 0.5f

                if (descriptor.hasExtraHeader) {
                    val badgeX = boxX1 + pad
                    val masterTabStartX = badgeX + masterTabBadgeW + 6f
                    if (isMasterRow) {
                        drawTitleBadge(session, badgeX, row1Y, masterTabBadgeW, totalCtrlH, descriptor.accent, "MASTER", UITheme.FontLevel.H2)
                        PerformanceMasterControls.drawModeControls(session, mixer, parametersState, ctx, masterTabStartX, row1Y, row2YFinal, ctrlH, masterRowW)
                        PerformanceMasterControls.drawBypassControls(mixer, boxX2 - pad - masterRightW, row2YFinal, ctrlH, masterRightW)
                    } else if (isTransRow) {
                        drawTitleBadge(session, badgeX, row1Y, masterTabBadgeW, totalCtrlH, descriptor.accent, "TRANS", UITheme.FontLevel.H2)
                        PerformanceTransitionsControls.draw(session, mixer, masterTabStartX, ctrlY, ctrlH, masterRowW)
                    } else if (isClockRow) {
                        drawTitleBadge(session, badgeX, row1Y, masterTabBadgeW, totalCtrlH, descriptor.accent, "CLOCK", UITheme.FontLevel.H2)
                        PerformanceClockControls.draw(session, masterTabStartX, row1Y, row2YFinal, ctrlH)
                    } else if (descriptor.bankId == MacroEngine.FX_SENDS) {
                        drawTitleBadge(session, badgeX, row1Y, masterTabBadgeW, totalCtrlH, descriptor.accent, "WET/DRY", UITheme.FontLevel.H2,
                            tooltip = "Per-deck FX send levels (each deck's FX chain wet/dry).")
                        PerformanceFxSendsControls.drawRightControls(boxX2 - pad - masterRightW, row2YFinal, ctrlH, masterRightW)
                    } else if (isDeckRow) {
                        val deckTag = when {
                            isDeckA -> "A"
                            isDeckB -> "B"
                            isDeckBG -> "BG"
                            else -> "PV"
                        }
                        val targetDeck = when {
                            isDeckA -> mixer.deckA
                            isDeckB -> mixer.deckB
                            isDeckBG -> mixer.deckBG
                            else -> mixer.deckPV
                        }
                        val deckLabel = "Deck $deckTag"

                        // Deck title badge: just the deck letter(s) -- the [SRC]/[FX] pills beside it show the mode.
                        drawTitleBadge(session, badgeX, row1Y, deckBadgeW, totalCtrlH, descriptor.accent, deckTag, UITheme.FontLevel.H1)

                        val leftStartX = badgeX + deckBadgeW + 6f
                        deckControls.drawDeckRowLeftControls(session, mixer, parametersState, deckLabel, targetDeck, leftStartX, row1Y, row2YFinal, ctrlH, deckComboW, deckRow1W)
                        deckControls.drawDeckRowRightControls(session, mixer, deckLabel, targetDeck, boxX2 - pad - deckRightW, row2YFinal, ctrlH)
                    }
                }

                // 4 knobs for this row.
                for (col in 0 until 4) {
                    val knobIdx = row.knobOffset + col
                    val control = bank.knobs.getOrNull(knobIdx) ?: continue

                    val cellCenterX = knobClusterStartX + col * knobColW + knobColW / 2f

                    // FX slot side buttons, stacked left of the knob: Super Knob link over slot bypass.
                    // Group mode: slot knobs (cols 1..3) get both. Focus mode: knob 1 (the focused
                    // slot's dry/wet) gets just the bypass.
                    val rowChain = if (isFxBankId) ctx.resolveFxChain(mixer, row.bankId) else null
                    val isFocusMode = rowChain?.isFocused() == true
                    val sideSlotIdx = when {
                        !descriptor.hasExtraHeader || rowChain == null -> null
                        isFocusMode -> if (col == 0) rowChain.focusedSlot else null
                        col in 1..3 -> col - 1
                        else -> null
                    }
                    val sideBtnSize = 18f
                    val sideBtnGap = 2f
                    val sideBtnX = (cellCenterX - diameter / 2f - sideBtnSize - 2f).coerceAtLeast(gridStartX + 2f)
                    val sideStackTopY = knobTopY + diameter / 2f - (if (isFocusMode) sideBtnSize / 2f else sideBtnSize + sideBtnGap / 2f)
                    if (sideSlotIdx != null) {
                        val bypassY = if (isFocusMode) sideStackTopY else sideStackTopY + sideBtnSize + sideBtnGap
                        FxSlotCell.drawBypassButton(session, mixer, row.bankId, sideSlotIdx, sideBtnX, bypassY, sideBtnSize, row.accent)
                    }
                    if (sideSlotIdx != null && !isFocusMode) {
                        val slotIdx = sideSlotIdx
                        val chain = rowChain!!
                        val isLinked = chain.slotSuperKnobLink.getOrNull(slotIdx) == true
                        val btnSize = sideBtnSize
                        ImGui.setCursorScreenPos(sideBtnX, sideStackTopY)

                        val icon = if (isLinked) Icons.LINK else Icons.UNLINK
                        if (isLinked) {
                            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.10f, 0.45f, 0.40f, 0.75f))
                            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.14f, 0.55f, 0.48f, 0.90f))
                            ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0.35f, 0.95f, 0.85f, 1f))
                        } else {
                            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.50f))
                            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.22f, 0.25f, 0.32f, 0.80f))
                            ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0.55f, 0.58f, 0.65f, 0.80f))
                        }
                        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 1f, 1f)
                        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                            if (ImGui.button("$icon##perf_fx_link_${row.bankId}_${rowIdx}_$slotIdx", btnSize, btnSize)) {
                                val newLinked = !isLinked
                                chain.setSlotLinked(slotIdx, newLinked)
                                llm.slop.liquidlsd.macro.FxMacroSync.syncFor(row.bankId, mixer)
                            }
                        }
                        ImGui.popStyleVar()
                        ImGui.popStyleColor(3)
                        itemTooltip(
                            if (isLinked) "Slot ${slotIdx + 1} (${control.label}) is linked to Super Knob.\nClick to unlink."
                            else "Slot ${slotIdx + 1} (${control.label}) is unlinked.\nClick to link to Super Knob."
                        )
                    }

                    val isSelectedKnob = isModuleExpanded && (control.id == parametersState.selectedRackMacroId[moduleId])
                    // Group-mode FX slot knobs drop their "META" caption -- the slot cell below names the effect.
                    val showKnobLabel = !(descriptor.hasExtraHeader && isFxBankId && col in 1..3 && !isFocusMode)
                    val captionBlockH = captionH * ((if (showKnobLabel) 1 else 0) + (if (isModuleExpanded) 1 else 0))
                    val learnBtnSpaceH = if (isModuleExpanded) 24f else 0f
                    val cardPadX = 6f
                    val cardPadY = 4f
                    val cardX1 = cellCenterX - knobColW / 2f + cardPadX
                    val cardX2 = cellCenterX + knobColW / 2f - cardPadX
                    val cardY1 = knobTopY - cardPadY
                    val cardY2 = knobTopY + diameter + captionBlockH + learnBtnSpaceH + cardPadY

                    if (isSelectedKnob) {
                        val selFill = ImGui.colorConvertFloat4ToU32(0.10f, 0.65f, 0.92f, 0.14f)
                        val selBorder = ImGui.colorConvertFloat4ToU32(0.20f, 0.85f, 1.0f, 0.85f)
                        dl.addRectFilled(cardX1, cardY1, cardX2, cardY2, selFill, 6f)
                        dl.addRect(cardX1, cardY1, cardX2, cardY2, selBorder, 6f, 0, 1.5f)
                    }

                    ImGui.setCursorScreenPos(cellCenterX - diameter / 2f, knobTopY)

                    val midiPath = MacroEngine.midiPathFor(bank, control)
                    val isMidiLearning = midiPath != null &&
                        parametersState.midiLearnTarget.let { it is MidiLearnTarget.MacroTarget && it.macroPath == midiPath }

                    MacroKnobWidget.draw(
                        session = session,
                        id = "perf_${tabIdx}_r${rowIdx}_c${col}",
                        label = control.label.ifEmpty { "K${knobIdx + 1}" },
                        value = control.value,
                        diameter = diameter,
                        defaultValue = 0.5f,
                        pixelsForFullSweep = 200f,
                        isSelected = isSelectedKnob,
                        isLearning = isMidiLearning,
                        accentColor = row.accent,
                        bindings = control.bindings,
                        showValue = isModuleExpanded,
                        showLabel = showKnobLabel,
                        onSelect = {
                            if (isModuleExpanded) {
                                parametersState.selectedRackMacroId[moduleId] = control.id
                            }
                        },
                        onToggleLearn = {
                            if (midiPath != null) {
                                if (isMidiLearning) {
                                    parametersState.midiLearnTarget = null
                                } else {
                                    parametersState.midiLearnTarget = MidiLearnTarget.MacroTarget(midiPath, control.label.ifEmpty { "K${knobIdx + 1}" })
                                    parametersState.midiLearnStartTimeMs = System.currentTimeMillis()
                                    if (llm.slop.liquidlsd.midi.MidiEngine.getActiveDeviceCount() == 0) {
                                        PopupManager.globalPendingMidiWarning = true
                                    }
                                }
                            }
                        },
                        onChanged = { newVal -> control.value = newVal }
                    )

                    // If expanded and selected, draw compact Learn/Cancel button beneath the knob's Val readout
                    if (isSelectedKnob) {
                        val btnW = 54f
                        val btnH = 18f
                        val btnX = cellCenterX - btnW / 2f
                        val btnY = knobTopY + diameter + 3f + captionBlockH + 3f
                        val isParamLearning = MacroLearnState.isControlLearning(control.id)
                        ImGui.setCursorScreenPos(btnX, btnY)
                        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                            if (isParamLearning) {
                                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.85f, 0.2f, 0.2f, 0.85f))
                                if (ImGui.button("${Icons.X} Cancel##inline_cancel_${control.id}", btnW, btnH)) {
                                    MacroLearnState.cancelLearn()
                                }
                                ImGui.popStyleColor()
                                itemTooltip("Cancel Learn Mode.")
                            } else {
                                val canLearn = control.bindings.size < MacroControl.MAX_BINDINGS_PER_CONTROL
                                if (canLearn) {
                                    ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.18f, 0.38f, 0.24f, 0.85f))
                                    if (ImGui.button("${Icons.REFRESH} Learn##inline_learn_${control.id}", btnW, btnH)) {
                                        MacroLearnState.startLearn(control.id)
                                        MacroLearnState.selectedControlId = control.id
                                        ctx.navigateMacroPanelTo(parametersState, row.bankId)
                                        session.uiTheme.column3Mode = UITheme.Column3Mode.MACROS
                                        // Learn needs a parameter to click: open this row's Deep Edit if it's closed.
                                        val learnModuleId = ctx.canonicalModuleId(row.bankId)
                                        if (learnModuleId in PerformanceDeepEditBay.deepEditModuleIds && parametersState.disclosureFor(learnModuleId) == ParametersState.DisclosureLevel.COLLAPSED) {
                                            parametersState.setDisclosure(learnModuleId, ParametersState.DisclosureLevel.DEEP_EDIT)
                                        }
                                    }
                                    ImGui.popStyleColor()
                                    itemTooltip(
                                        if (row.bankId == MacroEngine.GLOBAL) "Arm Learn Mode and open the Mixer panel's Macros tab. Then open any Deep Edit and click a parameter slider or modulator property -- Global knobs can bind anywhere."
                                        else "Arm Learn Mode, open this row's Deep Edit and the Mixer panel's Macros tab. Then click a parameter slider or modulator property in this row's deck and section."
                                    )
                                } else {
                                    ImGui.textDisabled("Max 4")
                                }
                            }
                        }
                    }

                    // Dedicated slot/parameter control cell under knobs on FX rows
                    if (descriptor.hasExtraHeader && isFxBankId) {
                        val chain = ctx.resolveFxChain(mixer, row.bankId)
                        val chainLabel = llm.slop.liquidlsd.macro.FxMacroSync.labelFor(row.bankId) ?: "FX"
                        val cellW = (knobColW - 6f).coerceAtLeast(40f)
                        val cellX = cellCenterX - cellW / 2f
                        val cellY = knobTopY + diameter + 3f + captionBlockH + learnBtnSpaceH + 4f

                        if (chain.isFocused()) {
                            val focusedSlot = chain.focusedSlot!!
                            if (col == 0) {
                                // Knob 1 (Col 0) = Focused slot's individual Dry/Wet knob -> draw focused slot cell
                                FxSlotCell.draw(
                                    session = session,
                                    mixer = mixer,
                                    bankId = row.bankId,
                                    chainLabel = chainLabel,
                                    slotIndex = focusedSlot,
                                    x = cellX,
                                    y = cellY,
                                    w = cellW,
                                    accent = row.accent,
                                    onEditInDeepEdit = {
                                        val modId = ctx.canonicalModuleId(row.bankId)
                                        parametersState.setDisclosure(modId, ParametersState.DisclosureLevel.DEEP_EDIT)
                                        ctx.navigateMacroPanelTo(parametersState, row.bankId)
                                    }
                                )
                            } else if (col in 1..3) {
                                // Knobs 2-4 (Cols 1-3) = Focused slot's parameters -> draw FxParamCell
                                val slot = chain.slots.getOrNull(focusedSlot)
                                val paramEntries = slot?.parameters?.entries?.toList() ?: emptyList()
                                val paramIdx = chain.focusParamPage * 3 + (col - 1)
                                val entry = paramEntries.getOrNull(paramIdx)
                                FxParamCell.draw(
                                    session = session,
                                    bankId = row.bankId,
                                    knobIndex = col + 1,
                                    paramName = entry?.key,
                                    param = entry?.value,
                                    x = cellX,
                                    y = cellY,
                                    w = cellW,
                                    accent = row.accent
                                )
                            }
                        } else if (col in 1..3) {
                            // Standard Group Mode: draw slot cells for slots 1-3
                            val slotIdx = col - 1
                            FxSlotCell.draw(
                                session = session,
                                mixer = mixer,
                                bankId = row.bankId,
                                chainLabel = chainLabel,
                                slotIndex = slotIdx,
                                x = cellX,
                                y = cellY,
                                w = cellW,
                                accent = row.accent,
                                onEditInDeepEdit = {
                                    val modId = ctx.canonicalModuleId(row.bankId)
                                    parametersState.setDisclosure(modId, ParametersState.DisclosureLevel.DEEP_EDIT)
                                    ctx.navigateMacroPanelTo(parametersState, row.bankId)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Advance the ImGui cursor past the grid only when overflowing so the child window scrolls.
        if (gridTotalH > availH + 0.5f) {
            ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), gridStartY + gridTotalH)
            ImGui.dummy(0f, 0f)
        }
    }

    /**
     * Row title badge: accent-tinted box spanning both control lines, with [text] centered in
     * [level]'s font. The font is pushed at its explicit size -- on this draw-list addText path
     * UITheme.withFont passes 0f ("native baked size"), which renders H1/H2 no bigger than H3.
     */
    private fun drawTitleBadge(
        session: llm.slop.liquidlsd.SessionContext,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        accent: FloatArray,
        text: String,
        level: UITheme.FontLevel,
        /** Only for rows without a drop target over the badge (it would otherwise cover that button). */
        tooltip: String? = null
    ) {
        val dl = ImGui.getWindowDrawList()
        val bg = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.12f)
        val border = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.85f)
        dl.addRectFilled(x, y, x + w, y + h, bg, 4f)
        dl.addRect(x, y, x + w, y + h, border, 4f, 0, 1.5f)
        val font = session.uiTheme.fontFor(level)
        val size = if (level == UITheme.FontLevel.H1) UITheme.FONT_H1 else UITheme.FONT_H2
        val pushable = font != null && font.ptr != 0L
        if (pushable) ImGui.pushFont(font, size)
        val sz = ImGui.calcTextSize(text)
        dl.addText(x + (w - sz.x) * 0.5f, y + (h - sz.y) * 0.5f, border, text)
        if (pushable) ImGui.popFont()
        if (tooltip != null) {
            ImGui.setCursorScreenPos(x, y)
            ImGui.invisibleButton("##title_badge_$text", w.coerceAtLeast(1f), h.coerceAtLeast(1f))
            itemTooltip(tooltip)
        }
    }

    /**
     * Click-drag-to-scroll for the last submitted item: while it's held and dragged vertically,
     * scrolls the current window (the ##rack_grid_area child) by the mouse delta. Called after the
     * grid background hit area and the title-band drop zones, i.e. the row space outside the
     * knobs and controls. No-op when the grid fits without scrolling.
     */
    private fun applyDragScroll() {
        if (!ImGui.isItemActive() || ImGui.getScrollMaxY() <= 0f) return
        ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS)
        val dy = ImGui.getIO().mouseDelta.y
        if (dy != 0f) ImGui.setScrollY(ImGui.getScrollY() - dy)
    }

    fun calculateMinWidth(session: llm.slop.liquidlsd.SessionContext): Float =
        deepEditBay.calculateMinWidth(session)
}


