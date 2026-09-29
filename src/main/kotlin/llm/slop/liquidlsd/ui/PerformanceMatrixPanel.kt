package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImDrawFlags
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseCursor
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.macro.MacroOscBridge
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons
import llm.slop.liquidlsd.ui.browser.PresetListPanel
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
 * Knob sizing and every position on a row come from [PerfRowGeometry] (window size + fonts only),
 * and what each knob shows from [PerfKnobResolver] -- so a row's mode (SRC/FX, MIX/FX, FX focus)
 * changes content, never knob size or position. See docs/developer/ui.md.
 *
 * Each row of 4 knobs is enclosed in a rounded, accent-colored box with a title badge on its left.
 */
class PerformanceMatrixPanel {

    // -- Tab definitions ----------------------------------------------------------

    // internal (not private): UITheme needs Tab.entries.size to coerce the persisted tab index
    // without hardcoding a count that silently drifts when a tab is added/removed.
    internal enum class Tab(val label: String, val tooltip: String) {
        DECKS("DECKS", "One row per deck (Deck A / Deck B / Deck BG / Deck PV), knobs 1-4 each.\nEach row's [SRC|FX] pills switch its knobs between the visual source and the deck's FX chain."),
        MASTER("MASTER", "Master ([MIX|FX]: composite alphas or Master FX chain), Transitions (crossfader + picker + queue),\nper-deck FX wet/dry, and Clock (tap tempo / resync / clock source + 4 Global macro knobs).")
    }

    /**
     * Describes one row of 4 knobs: which bank to pull from, which 4-knob offset within that
     * bank (0 = knobs 0–3, 4 = knobs 4–7), and the RGB accent color for the row. Each row is
     * drawn in its own box (see [drawMatrix]).
     */
    private data class RowDescriptor(
        val bankId: String,
        val knobOffset: Int,
        val accent: FloatArray,
        val groupLabel: String,
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

        /** A deck row's tag, source-macro bank, FX-chain bank and accent. */
        private data class DeckRowBanks(val tag: String, val srcBankId: String, val fxBankId: String, val accent: FloatArray)

        private val DECK_ROW_BANKS = listOf(
            DeckRowBanks("A",  MacroEngine.DECK_A,  MacroEngine.DECK_A_FX,  PerformanceColors.COLOR_DECK_A),
            DeckRowBanks("B",  MacroEngine.DECK_B,  MacroEngine.DECK_B_FX,  PerformanceColors.COLOR_DECK_B),
            DeckRowBanks("BG", MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX, PerformanceColors.COLOR_DECK_BG),
            DeckRowBanks("PV", MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX, PerformanceColors.COLOR_DECK_PV),
        )

        private val TAB_ROWS: Array<List<RowDescriptor>> = arrayOf(
            // DECKS: one row per deck (knobs 0–3 each: Deck A, Deck B, Deck BG, Deck PV)
            listOf(
                RowDescriptor(MacroEngine.DECK_A,  0, PerformanceColors.COLOR_DECK_A,  "DECK A",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_B,  0, PerformanceColors.COLOR_DECK_B,  "DECK B",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_BG, 0, PerformanceColors.COLOR_DECK_BG, "DECK BG", hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_PV, 0, PerformanceColors.COLOR_DECK_PV, "DECK PV", hasExtraHeader = true),
            ),
            // MASTER: Master ([MIX] over [FX] + chain header; knobs on composite alphas or the
            // Master FX chain), Transitions (transition picker + queue nav over crossfader), FX
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

        // Modular Rack: when a module is in Deep Edit, every other row is hidden from the grid and
        // the Deep-Edit bay below gets the rest of the height. The open row is exactly as tall as
        // in Perform view plus [expandedExtraH] for the value readout and Learn button under its knobs.
        val tabIdx = theme.performanceMatrixTab.coerceIn(0, Tab.entries.size - 1)
        val visibleRows = visibleRowsForTab(tabIdx, parametersState)
        val anyExpanded = parametersState.anyRackModuleExpanded()
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)
        // Only the Perform-view tab's row *count* sizes rows -- never their modes (see PerfRowGeometry).
        val layoutRowCount = TAB_ROWS[layoutTabIdx(tabIdx, visibleRows, anyExpanded)].size
        val baseRowH = ((availH - hiddenLibraryH).coerceAtLeast(4f) / layoutRowCount).coerceAtLeast(MIN_ROW_H)
        val gridH = if (!anyExpanded) availH else (visibleRows.size * (baseRowH + expandedExtraH(session))).coerceAtMost((availH - 160f).coerceAtLeast(160f))
        val bayH = (availH - gridH - (if (anyExpanded) ImGui.getStyle().getItemSpacingY() else 0f)).coerceAtLeast(0f)

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        if (ImGui.beginChild("##rack_grid_area", 0f, gridH, false)) {
            drawMatrix(session, theme, mixer, parametersState, visibleRows, baseRowH)
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
        DECK_ROW_BANKS.firstOrNull { moduleId == it.srcBankId || moduleId == it.fxBankId }?.let { deck ->
            val template = RowDescriptor(deck.srcBankId, 0, deck.accent, "DECK ${deck.tag}", hasExtraHeader = true)
            return withDeckRowMode(template, parametersState)
        }
        return when (moduleId) {
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

    /** [row] with the per-deck [SRC|FX] or Master [MIX|FX] toggle applied (retargeted to the FX bank when on). */
    private fun withDeckRowMode(row: RowDescriptor, parametersState: ParametersState?): RowDescriptor {
        val deck = DECK_ROW_BANKS.firstOrNull { it.srcBankId == row.bankId }
        return when {
            deck != null && ctx.isDeckRowFx(deck.tag, parametersState) ->
                row.copy(bankId = deck.fxBankId, groupLabel = "DECK ${deck.tag} (FX)")
            row.bankId == MacroEngine.MASTER && parametersState != null && ctx.isMasterRowFx(parametersState) ->
                row.copy(bankId = MacroEngine.MASTER_FX, groupLabel = "MASTER (FX)")
            else -> row
        }
    }

    /** This tab's rows with the per-deck [SRC|FX] and Master [MIX|FX] toggles applied. */
    private fun substitutedRowsForTab(tabIdx: Int, parametersState: ParametersState? = null): List<RowDescriptor> =
        TAB_ROWS[tabIdx].map { withDeckRowMode(it, parametersState) }

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

    // -- 4x4 Knob Grid -----------------------------------------------------------

    /**
     * Draws [rows] (the visible rows -- see [visibleRowsForTab]), each [rowH] tall in the
     * Perform-view band (plus [expandedExtraH] below it while in Deep Edit).
     *
     * Every position comes from one [PerfRowGeometry] built from the window size and fonts only --
     * never from a row's mode or bank -- and what each knob shows comes from [PerfKnobResolver].
     * So toggling SRC/FX, MIX/FX or FX focus on any row changes content, never knob size or position.
     */
    private fun drawMatrix(
        session: llm.slop.liquidlsd.SessionContext,
        theme: UITheme,
        mixer: Mixer,
        parametersState: ParametersState,
        rows: List<RowDescriptor>,
        rowH: Float
    ) {
        val tabIdx = theme.performanceMatrixTab.coerceIn(0, Tab.entries.size - 1)

        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)
        val gridW = ImGui.getContentRegionAvailX().coerceAtLeast(4f)
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
        val bodyLineH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() }
        val boxMarginY = PerfRowGeometry.BOX_MARGIN_Y
        val boxPad = PerfRowGeometry.BOX_PAD
        val pad = PerfRowGeometry.PAD

        val isCompactRow = rowH < 95f
        val ctrlH = if (isCompactRow) 21f else PerformanceColors.CTRL_H
        val stackGap = if (isCompactRow) 2f else 3f

        // Every row: a title badge spanning both control lines, then two stacked control lines,
        // then the knobs. Deck badges are a large A/B/BG/PV; MASTER-tab badges are wider for words.
        val deckBadgeW = 42f
        val deckComboW = (gridW * 0.11f).coerceIn(85f, 140f)
        // Reserved at the full-height CTRL_H (the wider case) so compact rows never widen it.
        val deckRow1W = DeckRowMetrics.row1Width(PerformanceColors.CTRL_H, deckComboW)
        val deckLeftW = deckBadgeW + 6f + deckRow1W
        val deckRightW = 56f
        // MASTER tab: Master ([MIX] + crossfader over [FX] + chain header), Transitions (picker +
        // queue nav), FX Wet/Dry (badge only), Clock (source/BPM/beat over tempo actions).
        val masterTabBadgeW = 78f
        val masterRowW = maxOf(deckRow1W, (gridW * 0.38f).coerceAtMost(420f))
        val masterTabLeftW = masterTabBadgeW + 6f + masterRowW
        val masterRightW = 56f

        // Reserved unconditionally (not just on the tab that currently needs it) so the knob
        // cluster -- and therefore diameter and column pitch -- is the same on DECKS and MASTER.
        val geo = PerfRowGeometry(
            gridW = gridW,
            rowH = rowH,
            bodyLineH = bodyLineH,
            leftW = maxOf(deckLeftW, masterTabLeftW),
            rightW = maxOf(deckRightW, masterRightW)
        )
        val diameter = geo.diameter
        val sideBtnSize = PerfRowGeometry.SIDE_BTN

        // Explicit nonzero size rather than UITheme.withFont(H1) (which passes 0f for "native
        // baked size") -- on this draw-list addText path, 0f renders H1 no bigger than H3, so the
        // size is requested explicitly to get the real 22px glyphs.
        val h1Font = session.uiTheme.fontFor(UITheme.FontLevel.H1)
        val h1Pushable = h1Font != null && h1Font.ptr != 0L

        for ((rowIdx, row) in rows.withIndex()) {
            val descriptor = row
            val rowTopY = gridStartY + rowTopOffsets[rowIdx]
            val rowBottomY = gridStartY + rowTopOffsets[rowIdx + 1]
            // Controls and knobs are laid out in the Perform-view band; an expanded row's extra
            // height only extends the box downward.
            val layoutBottomY = rowTopY + rowH

            val boxTopY = rowTopY + boxMarginY
            val boxBottomY = rowBottomY - boxMarginY
            val boxX1 = gridStartX + 2f
            val boxX2 = gridStartX + gridW - 2f

            // Rounded box (faint fill + accent border) around the row.
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
                    ImGui.invisibleButton("##perf_deck_drop_${rowIdx}_$dropTag", deckBadgeW.coerceAtLeast(1f), (boxBottomY - boxTopY).coerceAtLeast(1f))
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
                        val stockSourcePayload = ImGui.acceptDragDropPayload<String>(PresetListPanel.PAYLOAD_STOCK_SOURCE)
                        if (stockSourcePayload != null) {
                            VisualSourceRegistry.availableSources.find { it.id == stockSourcePayload }?.let { source ->
                                val deckLabel = when {
                                    isDeckA -> "Deck A"
                                    isDeckB -> "Deck B"
                                    isDeckBG -> "Deck BG"
                                    else -> "Deck PV"
                                }
                                UIManager.changeVisualSourceSafely(mixer, targetDeck, deckLabel, source, parametersState)
                            }
                        }
                        ImGui.endDragDropTarget()
                    }
                } else if (isMasterRow) {
                    ImGui.setCursorScreenPos(boxX1, boxTopY)
                    ImGui.setNextItemAllowOverlap()
                    ImGui.invisibleButton("##perf_master_drop_${rowIdx}", masterTabBadgeW.coerceAtLeast(1f), (boxBottomY - boxTopY).coerceAtLeast(1f))
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
                    ImGui.invisibleButton("##perf_trans_drop_${rowIdx}", masterTabBadgeW.coerceAtLeast(1f), (boxBottomY - boxTopY).coerceAtLeast(1f))
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

            // Modular Rack disclosure toggle in top-right of box: [EDIT]. Only for rows with no
            // title badge to dock into -- badge rows get the icon-only gear in the badge's own
            // gap instead (see drawEditGearInBadge, called per-branch below).
            if (descriptor.canExpand && !descriptor.hasExtraHeader) {
                val chevronY = boxTopY + 3f
                val editBtnW = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                    ImGui.calcTextSize("EDIT").x + ImGui.getStyle().framePaddingX * 2f
                }
                ImGui.setCursorScreenPos(boxX2 - pad - editBtnW, chevronY)
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                    llm.slop.liquidlsd.ui.rack.RackUnit.drawChevron(
                        parametersState, activeModuleId, "${tabIdx}_${rowIdx}"
                    )
                }
            }

            val contentTopY = boxTopY + boxPad
            val contentBottomY = layoutBottomY - boxMarginY - boxPad
            val totalCtrlH = ctrlH * 2f + stackGap
            val row1Y = contentTopY + ((contentBottomY - contentTopY) - totalCtrlH).coerceAtLeast(0f) * 0.5f
            val row2YFinal = row1Y + ctrlH + stackGap
            val ctrlY = (contentTopY + contentBottomY - ctrlH) * 0.5f

            if (descriptor.hasExtraHeader) {
                val badgeX = boxX1
                val badgeY = boxTopY
                val badgeH = ((row2YFinal + ctrlH) - boxTopY) * 0.5f
                val masterTabStartX = badgeX + masterTabBadgeW + 6f
                if (isMasterRow) {
                    drawTitleBadge(session, badgeX, badgeY, masterTabBadgeW, badgeH, descriptor.accent, "MASTER", UITheme.FontLevel.H2)
                    drawEditGearInBadge(session, parametersState, descriptor, activeModuleId, tabIdx, rowIdx, badgeX, badgeY, masterTabBadgeW, badgeH)
                    PerformanceMasterControls.drawModeControls(session, mixer, parametersState, ctx, masterTabStartX, row1Y, row2YFinal, ctrlH, masterRowW)
                    PerformanceMasterControls.drawBypassControls(session, mixer, boxX2 - pad - masterRightW, row2YFinal, ctrlH, masterRightW)
                } else if (isTransRow) {
                    drawTitleBadge(session, badgeX, badgeY, masterTabBadgeW, badgeH, descriptor.accent, "TRANS", UITheme.FontLevel.H2)
                    drawEditGearInBadge(session, parametersState, descriptor, activeModuleId, tabIdx, rowIdx, badgeX, badgeY, masterTabBadgeW, badgeH)
                    PerformanceTransitionsControls.draw(session, mixer, parametersState, masterTabStartX, row1Y, row2YFinal, ctrlH, masterRowW)
                    PerformanceTransitionsControls.drawRightControls(session, mixer, boxX2 - pad - masterRightW, row1Y, ctrlH, masterRightW)
                } else if (isClockRow) {
                    drawTitleBadge(session, badgeX, badgeY, masterTabBadgeW, badgeH, descriptor.accent, "CLOCK", UITheme.FontLevel.H2)
                    PerformanceClockControls.draw(session, masterTabStartX, row1Y, row2YFinal, ctrlH)
                } else if (descriptor.bankId == MacroEngine.FX_SENDS) {
                    drawTitleBadge(session, badgeX, badgeY, masterTabBadgeW, badgeH, descriptor.accent, "WET/DRY", UITheme.FontLevel.H2,
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
                    drawTitleBadge(session, badgeX, badgeY, deckBadgeW, badgeH, descriptor.accent, deckTag, UITheme.FontLevel.H1)
                    drawEditGearInBadge(session, parametersState, descriptor, activeModuleId, tabIdx, rowIdx, badgeX, badgeY, deckBadgeW, badgeH)

                    val leftStartX = badgeX + deckBadgeW + 6f
                    deckControls.drawDeckRowLeftControls(session, mixer, parametersState, deckLabel, targetDeck, leftStartX, row1Y, row2YFinal, ctrlH, deckComboW, deckRow1W)
                    deckControls.drawDeckRowRightControls(
                        session, mixer, parametersState, deckLabel, targetDeck,
                        boxX2 - pad - deckRightW, row1Y, row2YFinal, ctrlH, deckRightW
                    )
                }
            }

            // 4 knobs for this row: content from the resolver, every position from [geo].
            val bank: MacroBank = MacroEngine.getBank(row.bankId) ?: MacroEngine.bankForParamPath(row.bankId)
            val isFxBankId = row.bankId in llm.slop.liquidlsd.macro.FxMacroSync.FX_BANK_IDS
            val rowChain = if (isFxBankId && descriptor.hasExtraHeader) ctx.resolveFxChain(mixer, row.bankId) else null
            val specs = PerfKnobResolver.resolve(bank, row.knobOffset, rowChain?.let { FxRowState.of(it) })
            val chainLabel = llm.slop.liquidlsd.macro.FxMacroSync.labelFor(row.bankId) ?: "FX"
            val openDeepEdit = {
                parametersState.setDisclosure(ctx.canonicalModuleId(row.bankId), ParametersState.DisclosureLevel.DEEP_EDIT)
                ctx.navigateMacroPanelTo(parametersState, row.bankId)
            }
            val knobTopY = rowTopY + geo.knobTop
            val stripY = rowTopY + geo.stripTop

            for (spec in specs) {
                val col = spec.col
                val control = spec.control
                val knobIdx = spec.knobIndex
                val cellCenterX = gridStartX + geo.colCenterX(col)
                val sideBtnX = gridStartX + geo.sideBtnX(col)

                when (val side = spec.side) {
                    SideButtons.None -> {}
                    is SideButtons.LinkAndBypass -> {
                        FxSlotCell.drawLinkButton(session, mixer, row.bankId, side.slotIndex, control.label, sideBtnX, rowTopY + geo.sideBtnY(0, 2), sideBtnSize)
                        FxSlotCell.drawBypassButton(session, mixer, row.bankId, side.slotIndex, sideBtnX, rowTopY + geo.sideBtnY(1, 2), sideBtnSize, row.accent)
                    }
                    is SideButtons.Bypass ->
                        FxSlotCell.drawBypassButton(session, mixer, row.bankId, side.slotIndex, sideBtnX, rowTopY + geo.sideBtnY(0, 1), sideBtnSize, row.accent)
                    is SideButtons.Reset ->
                        FxParamCell.drawResetButton(row.bankId, knobIdx + 1, side.name, side.param, sideBtnX, rowTopY + geo.sideBtnY(0, 1), sideBtnSize)
                }

                val isSelectedKnob = isModuleExpanded && (control.id == parametersState.selectedRackMacroId[moduleId])
                // Deep Edit extras, below the strip: value readout, then the Learn button.
                val valueLineY = stripY + geo.stripH + 1f
                val learnBtnY = valueLineY + captionH + 3f
                if (isSelectedKnob) {
                    val cardX1 = cellCenterX - geo.colW / 2f + 6f
                    val cardX2 = cellCenterX + geo.colW / 2f - 6f
                    val selFill = ImGui.colorConvertFloat4ToU32(0.10f, 0.65f, 0.92f, 0.14f)
                    val selBorder = ImGui.colorConvertFloat4ToU32(0.20f, 0.85f, 1.0f, 0.85f)
                    dl.addRectFilled(cardX1, knobTopY - 4f, cardX2, learnBtnY + 18f + 4f, selFill, 6f)
                    dl.addRect(cardX1, knobTopY - 4f, cardX2, learnBtnY + 18f + 4f, selBorder, 6f, 0, 1.5f)
                }

                val midiPath = MacroEngine.midiPathFor(bank, control)
                val isMidiLearning = midiPath != null &&
                    parametersState.midiLearnTarget.let { it is MidiLearnTarget.MacroTarget && it.macroPath == midiPath }
                val knobLabel = control.label.ifEmpty { "K${knobIdx + 1}" }

                ImGui.setCursorScreenPos(gridStartX + geo.knobX(col), knobTopY)
                // The widget draws the face only -- the caption and value readout go into the fixed strip/extras below.
                MacroKnobWidget.draw(
                    session = session,
                    id = "perf_${tabIdx}_r${rowIdx}_c${col}",
                    label = knobLabel,
                    value = control.value,
                    meterType = spec.meterType,
                    diameter = diameter,
                    defaultValue = 0.5f,
                    pixelsForFullSweep = 200f,
                    isSelected = isSelectedKnob,
                    isLearning = isMidiLearning,
                    accentColor = row.accent,
                    bindings = control.bindings,
                    valueOverlay = spec.valueOverlay,
                    oscAddress = MacroOscBridge.getKnobAddress(row.bankId, knobIdx),
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
                                parametersState.midiLearnTarget = MidiLearnTarget.MacroTarget(midiPath, knobLabel)
                                parametersState.midiLearnStartTimeMs = System.currentTimeMillis()
                                if (llm.slop.liquidlsd.midi.MidiEngine.getActiveDeviceCount() == 0) {
                                    PopupManager.globalPendingMidiWarning = true
                                }
                            }
                        }
                    },
                    onChanged = { newVal -> control.value = newVal }
                )

                // The strip: the knob's caption, or the FX slot / focused-parameter cell.
                val stripX = gridStartX + geo.stripX(col)
                when (val under = spec.under) {
                    is UnderKnob.Label -> drawStripLabel(session, under.text, stripX, stripY, geo.stripW, geo.stripH, isSelectedKnob)
                    is UnderKnob.SlotCell -> FxSlotCell.draw(
                        session = session,
                        mixer = mixer,
                        bankId = row.bankId,
                        chainLabel = chainLabel,
                        slotIndex = under.slotIndex,
                        x = stripX,
                        y = stripY,
                        w = geo.stripW,
                        accent = row.accent,
                        onEditInDeepEdit = openDeepEdit,
                        onOpenBrowse = { slotIndex ->
                            parametersState.openFxChainBrowse(ctx.canonicalModuleId(row.bankId), ctx.deckLabelForModuleId(row.bankId), slotIndex)
                        }
                    )
                    is UnderKnob.ParamCell -> FxParamCell.draw(
                        session = session,
                        bankId = row.bankId,
                        knobIndex = knobIdx + 1,
                        paramName = under.name,
                        param = under.param,
                        x = stripX,
                        y = stripY,
                        w = geo.stripW,
                        accent = row.accent
                    )
                }

                if (isModuleExpanded) {
                    val valStr = "Val: ${"%.2f".format(control.value)}"
                    val valCol = if (isSelectedKnob) TangoPalette.u32(TangoPalette.SYNC.normal)
                                 else ImGui.getColorU32(ImGuiCol.TextDisabled)
                    session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                        dl.addText(cellCenterX - ImGui.calcTextSize(valStr).x / 2f, valueLineY, valCol, valStr)
                    }
                }

                // If expanded and selected, draw compact Learn/Cancel button beneath the value readout
                if (isSelectedKnob) {
                    val btnW = 54f
                    val btnX = cellCenterX - btnW / 2f
                    val btnY = learnBtnY
                    val btnH = 18f
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
            }
        }

        // Advance the ImGui cursor past the grid only when overflowing so the child window scrolls.
        if (gridTotalH > availH + 0.5f) {
            ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), gridStartY + gridTotalH)
            ImGui.dummy(0f, 0f)
        }
    }

    /**
     * A knob's caption, ellipsized to and vertically centered in its strip -- BODY, like the FX
     * cells that share the strip, so text sits at the same place whatever the row's mode. The
     * full label is in the knob's tooltip.
     */
    private fun drawStripLabel(session: llm.slop.liquidlsd.SessionContext, text: String, x: Float, y: Float, w: Float, h: Float, isSelected: Boolean) {
        val col = if (isSelected) TangoPalette.u32(TangoPalette.SYNC.normal)
                  else ImGui.getColorU32(ImGuiCol.Text)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val shown = TextFit.ellipsize(text, w - 4f)
            val tw = ImGui.calcTextSize(shown).x
            ImGui.getWindowDrawList().addText(x + (w - tw) / 2f, TextFit.centeredY(y, h, ImGui.getTextLineHeight()), col, shown)
        }
    }

    /**
     * Row title badge: accent-tinted box merged with the row's top-left corner, with [text] centered in
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
        val bg = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.14f)
        val border = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.85f)
        val cornerFlags = ImDrawFlags.RoundCornersTopLeft or ImDrawFlags.RoundCornersBottomRight
        dl.addRectFilled(x, y, x + w, y + h, bg, 8f, cornerFlags)
        dl.addRect(x, y, x + w, y + h, border, 8f, cornerFlags, 1.5f)
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
     * Icon-only EDIT toggle docked in the bottom half of a row's title-badge column -- [drawTitleBadge]
     * only fills the top half (see its `h` param there), leaving this gap free for the disclosure
     * toggle instead of a separate top-right corner button. Inset slightly (`gearShiftX = 2f`) so its
     * frame clears the row's outer border stroke.
     */
    private fun drawEditGearInBadge(
        session: llm.slop.liquidlsd.SessionContext,
        parametersState: ParametersState,
        descriptor: RowDescriptor,
        activeModuleId: String,
        tabIdx: Int,
        rowIdx: Int,
        badgeX: Float,
        badgeY: Float,
        badgeW: Float,
        badgeH: Float
    ) {
        if (!descriptor.canExpand) return
        val gearShiftX = 2f
        ImGui.setCursorScreenPos(badgeX + gearShiftX, badgeY + badgeH)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            llm.slop.liquidlsd.ui.rack.RackUnit.drawChevronIcon(
                parametersState, activeModuleId, "${tabIdx}_${rowIdx}", badgeW, badgeH
            )
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


