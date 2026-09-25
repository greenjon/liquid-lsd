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
        /** True for rows that draw side controls (deck/Master mode pills, chain header, bypass) or a Master/Transitions header bar. */
        val hasExtraHeader: Boolean = false,
        /** When false, the modular rack disclosure chevron and collapse controls are omitted. */
        val canExpand: Boolean = true
    )

    // Canonical deck colors matching BrowserDeckButtons are in PerformanceColors.
    companion object {
        private const val EXTRA_HEADER_H = 28f

        /**
         * Floor on grid row height. Below this the knobs get too small to use, so instead of
         * shrinking further the grid keeps this height and its child region scrolls vertically.
         * Meant to match the row height with the Library dock at half height (~56px knobs).
         */
        private const val MIN_ROW_H = 112f

        private val TAB_ROWS: Array<List<RowDescriptor>> = arrayOf(
            // DECKS: one row per deck (knobs 0–3 each: Deck A, Deck B, Deck BG, Deck PV)
            listOf(
                RowDescriptor(MacroEngine.DECK_A,  0, PerformanceColors.COLOR_DECK_A,  "DECK A",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_B,  0, PerformanceColors.COLOR_DECK_B,  "DECK B",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_BG, 0, PerformanceColors.COLOR_DECK_BG, "DECK BG", hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_PV, 0, PerformanceColors.COLOR_DECK_PV, "DECK PV", hasExtraHeader = true),
            ),
            // MASTER: Master (crossfader header bar + [MIX|FX] knob-assign: composite alphas or
            // Master FX chain), Transitions (transition picker + queue nav), FX Wet/Dry (per-deck
            // FX sends), Clock (tempo header bar + Global macro knobs) -- 1 row of 4 knobs each;
            // Master/Transitions/Clock also reserve header space (see drawMatrix).
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

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, parametersState: ParametersState, deckPresetController: DeckPresetController? = null) {
        ctx.deckPresetController = deckPresetController
        val theme = session.uiTheme

        deepEditBay.beginFrame(parametersState, mixer)
        drawTabStrip(session, theme, mixer, parametersState)
        ImGui.spacing()

        // Modular Rack: when any module is above Tier 1, every *other* (still-collapsed) row is
        // hidden from the grid entirely -- rather than reserving a fixed-height band for all 16
        // knobs regardless of disclosure state -- so the expanded row(s) and the Deep-Edit
        // region below get the screen space instead. If a Learn-pinned/expanded module doesn't
        // have a row on the *current* tab (e.g. it was expanded on a different tab), the grid falls
        // back to showing every row on this tab rather than rendering nothing.
        val tabIdx = theme.performanceMatrixTab.coerceIn(0, Tab.entries.size - 1)
        val visibleRowCount = visibleRowsForTab(tabIdx, parametersState).size
        val anyExpanded = parametersState.anyRackModuleExpanded()
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)
        val compactRowH = 175f
        val gridH = if (!anyExpanded) availH else (compactRowH * visibleRowCount).coerceIn(160f, (availH - 160f).coerceAtLeast(160f))
        val bayH = (availH - gridH - (if (anyExpanded) ImGui.getStyle().getItemSpacingY() else 0f)).coerceAtLeast(0f)

        if (ImGui.beginChild("##rack_grid_area", 0f, gridH, false)) {
            drawMatrix(session, theme, mixer, parametersState)
        }
        ImGui.endChild()

        if (anyExpanded) {
            deepEditBay.drawRackBay(session, mixer, parametersState, bayH)
        }
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
        val tabH = 28f
        val showAllDice = session.uiTheme.randomizationEnabled
        val allDiceBtnW = if (showAllDice) 80f else 0f
        val totalTabAreaW = (if (showAllDice) availW - allDiceBtnW - gap else availW).coerceAtLeast(1f)
        val tabW = ((totalTabAreaW - gap * (tabs.size - 1)) / tabs.size).coerceAtLeast(1f)

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

        if (showAllDice) {
            ImGui.sameLine(0f, gap)
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

        drawRackToolbarRow(session, parametersState)
    }

    /** Modular Rack toolbar row: Solo/Multi accordion toggle, Collapse All, persistent Learn indicator. */
    private fun drawRackToolbarRow(session: llm.slop.liquidlsd.SessionContext, parametersState: ParametersState) {
        val btnH = 22f
        val isSolo = parametersState.rackSoloMode
        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.22f, 0.25f, 0.30f, 1f))
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (ImGui.button((if (isSolo) "${Icons.LINK} SOLO" else "${Icons.UNLINK} MULTI") + "##rack_solo_toggle", 90f, btnH)) {
                parametersState.rackSoloMode = !parametersState.rackSoloMode
                AppPreferencesStore.savePreferences()
            }
        }
        ImGui.popStyleColor(2)
        itemTooltip(
            if (isSolo) "SOLO: expanding one module's Deep Edit auto-collapses the others.\nClick to switch to MULTI (several modules can stay expanded at once)."
            else "MULTI: several modules can stay expanded at once.\nClick to switch to SOLO accordion behavior."
        )

        ImGui.sameLine(0f, 6f)
        val anyExpanded = parametersState.anyRackModuleExpanded()
        ImGui.beginDisabled(!anyExpanded)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (ImGui.button("Collapse All##rack_collapse_all", 100f, btnH)) {
                parametersState.collapseAllRackModules()
            }
        }
        ImGui.endDisabled()
        itemTooltip("Collapse every expanded Rack module back to Tier 1 (Esc does the same).")

        ImGui.sameLine(0f, 12f)
        llm.slop.liquidlsd.ui.rack.RackUnit.drawLearnIndicator()
    }

    // -- 4x4 Knob Grid -----------------------------------------------------------

    /** A run of consecutive [RowDescriptor]s sharing one bank/group label, enclosed in one box. */
    private data class RowGroup(val startRow: Int, val rowCount: Int, val descriptor: RowDescriptor)

    private fun drawMatrix(session: llm.slop.liquidlsd.SessionContext, theme: UITheme, mixer: Mixer, parametersState: ParametersState) {
        val tabIdx = theme.performanceMatrixTab.coerceIn(0, Tab.entries.size - 1)
        // Deck/Master rows' bankIds follow their [SRC|FX] / [MIX|FX] toggles, substituted in
        // visibleRowsForTab rather than baked into the static table. Also hides every
        // still-collapsed row while some module is expanded -- see the comment on visibleRowsForTab.
        val rows = visibleRowsForTab(tabIdx, parametersState)

        val groups = mutableListOf<RowGroup>()
        var gi = 0
        while (gi < rows.size) {
            var gj = gi + 1
            while (gj < rows.size && rows[gj].bankId == rows[gi].bankId && rows[gj].groupLabel == rows[gi].groupLabel) gj++
            groups.add(RowGroup(gi, gj - gi, rows[gi]))
            gi = gj
        }

        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(4f)
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)

        val gridW = availW
        // Rows share the available height down to MIN_ROW_H; past that the grid overflows and the
        // ##rack_grid_area child scrolls (see the cursor advance at the end of this function).
        val rowH = (availH / rows.size.toFloat()).coerceAtLeast(MIN_ROW_H)
        val gridTotalH = rowH * rows.size

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
        val groupLabelH = session.uiTheme.withFont(UITheme.FontLevel.H1) { ImGui.getTextLineHeight() }
        val subLabelH = captionH
        val boxMarginY = 3f   // gap between a group's box and the next group's / grid's edge
        val boxLabelGap = 3f  // gap above and below the group title, inside the box
        val subLabelGap = 2f  // gap between a sub-label and the knobs below it
        val boxPad = 3f       // inner padding between the box border and the knobs it contains
        val pad = 6f

        val hasDeckRows = rows.any {
            it.bankId in listOf(MacroEngine.DECK_A, MacroEngine.DECK_B, MacroEngine.DECK_BG, MacroEngine.DECK_PV) ||
            it.groupLabel.startsWith("DECK")
        }
        val hasFxRow = rows.any { it.bankId in llm.slop.liquidlsd.macro.FxMacroSync.FX_BANK_IDS }
        val hasMasterRow = rows.any { it.bankId == MacroEngine.MASTER || it.bankId == MacroEngine.MASTER_FX }

        val deckComboW = (gridW * 0.13f).coerceIn(100f, 150f)
        val deckRow1W = 28f + 4f + 74f + 4f + deckComboW + 4f + 24f + (if (session.uiTheme.randomizationEnabled) 4f + 24f else 0f) + 4f + 82f
        val deckLeftW = deckRow1W
        val deckRightW = 60f
        // Master row: [MIX] pill over [FX] pill + chain header; FX Wet/Dry: badge + Resync.
        val masterLeftW = deckLeftW
        val masterRightW = 60f
        val fxSendsW = 76f
        val hasFxSendsRow = rows.any { it.bankId == MacroEngine.FX_SENDS }

        val maxLeftW = maxOf(
            if (hasDeckRows) deckLeftW else 0f,
            if (hasMasterRow) masterLeftW else 0f,
            if (hasFxSendsRow) fxSendsW else 0f
        )
        val maxRightW = maxOf(
            if (hasDeckRows) deckRightW else 0f,
            if (hasMasterRow) masterRightW else 0f,
            if (hasFxSendsRow) masterRightW else 0f
        )

        val leftBoundary = gridStartX + pad + (if (maxLeftW > 0f) maxLeftW + 12f else 0f)
        val rightBoundary = gridStartX + gridW - pad - (if (maxRightW > 0f) maxRightW + 12f else 0f)
        val middleW = (rightBoundary - leftBoundary).coerceAtLeast(100f)

        val maxColW = middleW / 4f
        val diamByWidth = (maxColW - 12f).coerceAtLeast(8f)

        var diamByHeight = Float.MAX_VALUE
        val anyExpandedInMatrix = parametersState.anyRackModuleExpanded()
        val textBelowH = if (anyExpandedInMatrix) captionH * 2f + 20f else captionH
        for (group in groups) {
            val groupH = group.rowCount * rowH
            val hasSubLabel = rows[group.startRow].subLabel != null
            val groupBankId = rows[group.startRow].bankId
            val isFx = groupBankId in llm.slop.liquidlsd.macro.FxMacroSync.FX_BANK_IDS
            val hasTopBar = rows[group.startRow].hasExtraHeader &&
                groupBankId in listOf(MacroEngine.MASTER, MacroEngine.MASTER_FX, MacroEngine.TRANS, MacroEngine.GLOBAL)
            val extraHeaderH = if (hasTopBar) EXTRA_HEADER_H + boxLabelGap else 0f
            // Title is placed to the right above UI elements, not above the central knob column.
            // Only extraHeaderH (e.g. Master/Transitions header bar) takes vertical space across the whole row.
            val contentH = groupH - boxMarginY * 2f - boxPad * 2f - extraHeaderH
            val subRowH = contentH / group.rowCount
            val knobAreaH = if (hasSubLabel) subRowH - subLabelH - subLabelGap else subRowH
            val rowTextBelowH = if (isFx) textBelowH + FxSlotCell.HEIGHT + 4f else textBelowH
            diamByHeight = minOf(diamByHeight, (knobAreaH - rowTextBelowH).coerceAtLeast(8f))
        }
        val diameter = minOf(diamByWidth, diamByHeight).coerceIn(8f, 100f)

        val targetColW = if (hasFxRow) maxColW else if (maxLeftW > 0f) (diameter + 24f).coerceIn(72f, 96f) else (diameter + 28f).coerceIn(80f, 130f)
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
            val groupTopY = gridStartY + group.startRow * rowH
            val groupBottomY = gridStartY + (group.startRow + group.rowCount) * rowH

            val boxTopY = groupTopY + boxMarginY
            val titleTopY = boxTopY + boxLabelGap
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
            // MIX and FX modes of the Master row -- both keep the crossfader header bar.
            val isMasterRow = descriptor.bankId == MacroEngine.MASTER || descriptor.bankId == MacroEngine.MASTER_FX
            val displayLabel = descriptor.groupLabel

            val rawModuleId = descriptor.bankId
            val canonicalId = ctx.canonicalModuleId(rawModuleId)
            val isModuleExpanded = parametersState.disclosureFor(canonicalId) != ParametersState.DisclosureLevel.COLLAPSED ||
                                   parametersState.disclosureFor(rawModuleId) != ParametersState.DisclosureLevel.COLLAPSED
            val activeModuleId = if (parametersState.disclosureFor(canonicalId) != ParametersState.DisclosureLevel.COLLAPSED) canonicalId else rawModuleId
            val moduleId = activeModuleId
            val chevronSize = groupLabelH.coerceIn(16f, 22f)

            // The Master, Transitions and Clock rows each reserve a header-controls bar at the top
            // of the box (crossfader/crossfader-time on Master, transition picker/queue nav on
            // Transitions, tempo on Clock), so their titles stay there too; every other row's title sits to the
            // left of its knobs, top-aligned with them, so its Y is derived from the same knob-top
            // geometry the row loop computes below.
            val isSpecialHeaderRow = descriptor.hasExtraHeader && (isTransRow || isMasterRow || isClockRow)
            val titleY = if (isSpecialHeaderRow) {
                titleTopY
            } else {
                val firstRow = rows[group.startRow]
                val contentTopYForTitle = boxTopY + boxPad
                val subRowHForTitle = (boxBottomY - boxPad - contentTopYForTitle) / group.rowCount
                val knobAreaTopYForTitle = if (firstRow.subLabel != null) contentTopYForTitle + subLabelH + subLabelGap else contentTopYForTitle
                val knobAreaCenterYForTitle = knobAreaTopYForTitle + (subRowHForTitle - (knobAreaTopYForTitle - contentTopYForTitle)) / 2f
                val knobTopYForTitleCentered = knobAreaCenterYForTitle - diameter / 2f - textBelowH / 2f
                (knobAreaTopYForTitle + knobTopYForTitleCentered) / 2f
            }

            // Group title, left-aligned to the box, top-aligned with the knob row it labels.
            if (h1Pushable) ImGui.pushFont(h1Font, UITheme.FONT_H1)
            dl.addText(boxX1 + pad, titleY, borderCol, displayLabel)
            if (h1Pushable) ImGui.popFont()

            val afterTitleY = titleTopY + groupLabelH + boxLabelGap

            // Drop target placed over the header area so it does not occlude the header buttons or knob grid.
            val dropAreaH = if (descriptor.hasExtraHeader && isTransRow) EXTRA_HEADER_H + boxLabelGap else groupLabelH + boxLabelGap
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
                    ImGui.invisibleButton("##perf_deck_drop_${group.startRow}_$dropTag", boxX2 - boxX1, dropAreaH.coerceAtLeast(1f))
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
                    // Title band only -- the crossfader header bar below keeps its own hit-testing.
                    ImGui.setCursorScreenPos(boxX1, boxTopY)
                    ImGui.setNextItemAllowOverlap()
                    ImGui.invisibleButton("##perf_master_drop_${group.startRow}", boxX2 - boxX1, dropAreaH.coerceAtLeast(1f))
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
                } else if (isTransRow) {
                    ImGui.setCursorScreenPos(boxX1, boxTopY)
                    ImGui.setNextItemAllowOverlap()
                    ImGui.invisibleButton("##perf_trans_drop", boxX2 - boxX1, dropAreaH.coerceAtLeast(1f))
                    applyDragScroll()
                    if (ImGui.beginDragDropTarget()) {
                        val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                        if (payload != null) {
                            val file = File(payload)
                            if (file.extension.equals("lsdtrans", ignoreCase = true) && file.exists()) {
                                session.presetRepository.loadTransitionPresetAsync(file).thenAccept { dto ->
                                    mixer.applyTransitionPreset(dto)
                                }
                            } else {
                                val id = if (file.extension.equals("fs", ignoreCase = true) || file.extension.equals("isf", ignoreCase = true)) {
                                    file.nameWithoutExtension
                                } else {
                                    file.nameWithoutExtension.ifBlank { file.name }
                                }
                                mixer.setTransition(id)
                            }
                        }
                        ImGui.endDragDropTarget()
                    }
                }
            }

            // Modular Rack disclosure chevron & Collapse button -- drawn after the drop-target invisible buttons
            // above (which span the whole title band) so it isn't swallowed by their hit-testing.
            if (descriptor.canExpand) {
                if (isModuleExpanded) {
                    ImGui.setCursorScreenPos(boxX2 - chevronSize - 80f, titleTopY)
                    session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                        if (ImGui.smallButton("${Icons.CHEVRON_UP} Collapse##row_collapse_${tabIdx}_${group.startRow}")) {
                            parametersState.setDisclosure(activeModuleId, ParametersState.DisclosureLevel.COLLAPSED)
                            parametersState.setDisclosure(rawModuleId, ParametersState.DisclosureLevel.COLLAPSED)
                        }
                    }
                    itemTooltip("Collapse module back to standard row view.")
                }
                ImGui.setCursorScreenPos(boxX2 - chevronSize - 4f, titleTopY)
                llm.slop.liquidlsd.ui.rack.RackUnit.drawChevron(
                    parametersState, activeModuleId, chevronSize, "${tabIdx}_${group.startRow}"
                )
            }

            val contentTopY = if (descriptor.hasExtraHeader && isMasterRow) {
                PerformanceMasterControls.draw(session, mixer, parametersState, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                afterTitleY + EXTRA_HEADER_H + boxLabelGap
            } else if (descriptor.hasExtraHeader && isTransRow) {
                PerformanceTransitionsControls.draw(session, mixer, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                afterTitleY + EXTRA_HEADER_H + boxLabelGap
            } else if (descriptor.hasExtraHeader && isClockRow) {
                PerformanceClockControls.draw(session, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                afterTitleY + EXTRA_HEADER_H + boxLabelGap
            } else {
                boxTopY + boxPad
            }
            val contentBottomY = boxBottomY - boxPad
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
                val effectiveTextBelowH = if (isFxBankId) textBelowH + FxSlotCell.HEIGHT + 4f else textBelowH
                val knobAreaCenterY = knobAreaTopY + (subBottomY - knobAreaTopY) / 2f
                val knobTopYCentered = knobAreaCenterY - diameter / 2f - effectiveTextBelowH / 2f
                // Halfway between top-anchored and fully centered -- halves the dead space above the
                // knob (most visible when a module is expanded and its row fills the whole panel height)
                // without pushing the Val/Learn content below it past the box.
                val knobTopY = (knobAreaTopY + knobTopYCentered) / 2f
                val knobCenterY = knobTopY + diameter / 2f
                val ctrlH = PerformanceColors.CTRL_H
                // Bottom-aligned with the knob face, so short rows push the controls down into the
                // space beside the knob caption rather than up into the row title; never above the
                // title (first sub-row only -- that's where the title sits) or below the box.
                val ctrlMinY = if (k == 0 && !isSpecialHeaderRow) titleY + groupLabelH + 2f else subTopY
                val ctrlY = (knobTopY + diameter - ctrlH)
                    .coerceAtMost(subBottomY - ctrlH)
                    .coerceAtLeast(ctrlMinY)

                if (descriptor.hasExtraHeader) {
                    if (isDeckRow || isMasterRow) {
                        val stackGap = 3f
                        val row2Y = (knobTopY + diameter - ctrlH)
                            .coerceAtMost(subBottomY - ctrlH)
                        val row1Y = (row2Y - ctrlH - stackGap)
                            .coerceAtLeast(ctrlMinY)
                        val row2YFinal = maxOf(row2Y, row1Y + ctrlH + stackGap)

                        when {
                            isMasterRow -> {
                                PerformanceMasterControls.drawModeControls(session, mixer, parametersState, ctx, boxX1 + pad, row1Y, row2YFinal, ctrlH, maxLeftW)
                                PerformanceMasterControls.drawBypassControls(mixer, boxX2 - pad - masterRightW, row2YFinal, ctrlH, masterRightW)
                            }
                            isDeckA -> {
                                deckControls.drawDeckRowLeftControls(session, mixer, parametersState, "Deck A", mixer.deckA, boxX1 + pad, row1Y, row2YFinal, ctrlH, deckComboW, deckRow1W)
                                deckControls.drawDeckRowRightControls(session, mixer, "Deck A", mixer.deckA, boxX2 - pad - deckRightW, row2YFinal, ctrlH)
                            }
                            isDeckB -> {
                                deckControls.drawDeckRowLeftControls(session, mixer, parametersState, "Deck B", mixer.deckB, boxX1 + pad, row1Y, row2YFinal, ctrlH, deckComboW, deckRow1W)
                                deckControls.drawDeckRowRightControls(session, mixer, "Deck B", mixer.deckB, boxX2 - pad - deckRightW, row2YFinal, ctrlH)
                            }
                            isDeckBG -> {
                                deckControls.drawDeckRowLeftControls(session, mixer, parametersState, "Deck BG", mixer.deckBG, boxX1 + pad, row1Y, row2YFinal, ctrlH, deckComboW, deckRow1W)
                                deckControls.drawDeckRowRightControls(session, mixer, "Deck BG", mixer.deckBG, boxX2 - pad - deckRightW, row2YFinal, ctrlH)
                            }
                            isDeckPV -> {
                                deckControls.drawDeckRowLeftControls(session, mixer, parametersState, "Deck PV", mixer.deckPV, boxX1 + pad, row1Y, row2YFinal, ctrlH, deckComboW, deckRow1W)
                                deckControls.drawDeckRowRightControls(session, mixer, "Deck PV", mixer.deckPV, boxX2 - pad - deckRightW, row2YFinal, ctrlH)
                            }
                        }
                    } else {
                        if (descriptor.bankId == MacroEngine.FX_SENDS) {
                            PerformanceFxSendsControls.drawLeftControls(session, boxX1 + pad, ctrlY, ctrlH)
                            PerformanceFxSendsControls.drawRightControls(boxX2 - pad - masterRightW, ctrlY, ctrlH, masterRightW)
                        }
                    }
                }

                // 4 knobs for this row.
                for (col in 0 until 4) {
                    val knobIdx = row.knobOffset + col
                    val control = bank.knobs.getOrNull(knobIdx) ?: continue

                    val cellCenterX = knobClusterStartX + col * knobColW + knobColW / 2f

                    // Clickable link icon for FX slots (any FX row or Deck row in FX mode, cols 1..3)
                    val rowChain = if (isFxBankId) ctx.resolveFxChain(mixer, row.bankId) else null
                    if (descriptor.hasExtraHeader && isFxBankId && col in 1..3 && rowChain?.isFocused() != true) {
                        val slotIdx = col - 1
                        val chain = rowChain ?: ctx.resolveFxChain(mixer, row.bankId)
                        val isLinked = chain.slotSuperKnobLink.getOrNull(slotIdx) == true
                        val btnSize = 18f
                        val btnX = (cellCenterX - diameter / 2f - btnSize - 2f).coerceAtLeast(gridStartX + 2f)
                        val btnY = knobTopY + (diameter - btnSize) / 2f
                        ImGui.setCursorScreenPos(btnX, btnY)

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
                    val cardPadX = 6f
                    val cardPadY = 4f
                    val cardX1 = cellCenterX - knobColW / 2f + cardPadX
                    val cardX2 = cellCenterX + knobColW / 2f - cardPadX
                    val cardY1 = knobTopY - cardPadY
                    val cardY2 = knobTopY + diameter + (if (isModuleExpanded) captionH * 2f + 24f else captionH) + cardPadY

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
                        val btnY = knobTopY + diameter + 3f + captionH * 2f + 3f
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
                        val cellY = knobTopY + diameter + 3f + (if (isModuleExpanded) captionH * 2f + 24f else captionH) + 4f

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

        // Advance the ImGui cursor past the grid so the window scrollbar is correct.
        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), gridStartY + gridTotalH)
        ImGui.dummy(0f, 0f)
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


