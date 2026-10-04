package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.midi.MidiLearnTarget
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
 * focuses the matching Deep Edit tab (see [focusDeepEditTab]); bindings (labels, curves, ranges) are
 * edited in the Edit-row [PerformanceMacroStrip] and the Properties editor.
 *
 * The active page (see [PerfPageStore]) is persisted via [UITheme.performancePageId] / [AppPreferences.performancePageId].
 *
 * Knob sizing and every position on a row come from [PerfRowGeometry] (window size + fonts only),
 * and what each knob shows from [PerfKnobResolver] -- so a row's mode (SRC/FX, MIX/FX, FX focus)
 * changes content, never knob size or position. See docs/developer/ui.md.
 *
 * Each row of 4 knobs is enclosed in a rounded, accent-colored box with a title badge on its left.
 */
class PerformanceMatrixPanel {

    // Canonical deck colors matching BrowserDeckButtons are in PerformanceColors.
    companion object {
        /**
         * Floor on grid row height: low enough that all four rows fit above a HALF Library in a
         * 1280x720 display's ~688px window (~74px per row; knobs ~40-45px). Below this the grid scrolls.
         */
        private const val MIN_ROW_H = 68f
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
        // in Perform view; the selected knob's Learn button hangs below it, over the bay's toggle line.
        val pages = PerfPageStore.default.all()
        val page = pages.firstOrNull { it.id == theme.performancePageId } ?: pages.first()
        val visibleRows = visibleRowsForPage(page, pages, parametersState)
        val anyExpanded = parametersState.anyRackModuleExpanded()
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)
        // Only the Perform-view page's row *count* sizes rows -- never their modes (see PerfRowGeometry).
        val baseRowH = ((availH - hiddenLibraryH).coerceAtLeast(4f) / PerfPageDef.ROWS).coerceAtLeast(MIN_ROW_H)
        val gridH = if (!anyExpanded) availH else (visibleRows.size * baseRowH).coerceAtMost((availH - 160f).coerceAtLeast(160f))
        val bayH = (availH - gridH - (if (anyExpanded) ImGui.getStyle().getItemSpacingY() else 0f)).coerceAtLeast(0f)

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        overhangCount = 0
        badgeSlot = 0
        if (ImGui.beginChild("##rack_grid_area", 0f, gridH, false)) {
            drawMatrix(session, theme, mixer, parametersState, visibleRows, baseRowH)
        }
        ImGui.endChild()
        ImGui.popStyleVar()

        if (anyExpanded) {
            deepEditBay.drawRackBay(session, mixer, parametersState, bayH)
        }

        // The selected knob's card + Learn button extend below the row, past the grid child's clip
        // rect, so they're drawn here in the parent window, on top of the bay's toggle line.
        if (overhangCount > 0) {
            val cx = ImGui.getCursorScreenPosX()
            val cy = ImGui.getCursorScreenPosY()
            for (i in 0 until overhangCount) drawOverhang(session, parametersState, overhangPool[i])
            ImGui.setCursorScreenPos(cx, cy)
            ImGui.dummy(0f, 0f) // ImGui asserts if a SetCursorPos isn't followed by an item
            overhangCount = 0
        }
    }

    /**
     * Selected-knob extras deferred until after the grid child ends (see [draw]). Pooled records rather than
     * closures, so a selected knob costs no allocation per frame. [kind] is [OVERHANG_CARD] or [OVERHANG_LEARN].
     */
    private class Overhang {
        var kind = 0
        var cardX1 = 0f; var cardX2 = 0f; var cardTop = 0f; var cardBottom = 0f
        var selFill = 0; var selBorder = 0
        var cellCenterX = 0f; var btnY = 0f
        var bankId = ""
        var control: MacroControl? = null
    }
    private val overhangPool = ArrayList<Overhang>().also { for (i in 0 until 8) it.add(Overhang()) }
    private var overhangCount = 0
    private fun nextOverhang(kind: Int): Overhang {
        if (overhangCount == overhangPool.size) overhangPool.add(Overhang())
        return overhangPool[overhangCount++].also { it.kind = kind }
    }
    private val OVERHANG_CARD = 0
    private val OVERHANG_LEARN = 1

    // Per-frame ids/labels built once per distinct value (the render path must not allocate).
    private val knobIdCache = ArrayList<String?>()
    private fun knobId(tabIdx: Int, rowIdx: Int, col: Int): String {
        val key = (tabIdx * 16 + rowIdx.coerceIn(0, 15)) * 4 + col.coerceIn(0, 3)
        while (knobIdCache.size <= key) knobIdCache.add(null)
        return knobIdCache[key] ?: "perf_${tabIdx}_r${rowIdx}_c${col}".also { knobIdCache[key] = it }
    }
    private val knobFallbackLabels = Array(16) { "K${it + 1}" }
    private fun knobFallbackLabel(knobIdx: Int): String = knobFallbackLabels.getOrNull(knobIdx) ?: "K${knobIdx + 1}"
    private val rackIdCache = ArrayList<String?>()
    private fun rackId(tabIdx: Int, rowIdx: Int): String {
        val key = tabIdx * 16 + rowIdx.coerceIn(0, 15)
        while (rackIdCache.size <= key) rackIdCache.add(null)
        return rackIdCache[key] ?: "${tabIdx}_${rowIdx}".also { rackIdCache[key] = it }
    }
    private val deckLabels = Array(PerfRows.DECK_TAGS.size) { "Deck ${PerfRows.DECK_TAGS[it]}" }
    private val deckBadgeTips = Array(PerfRows.DECK_TAGS.size) { "${deckLabels[it]} Unit\nConfigure ${deckLabels[it]} video source and FX" }
    private fun deckTagIndex(tag: String): Int = PerfRows.DECK_TAGS.indexOf(tag).coerceAtLeast(0)
    private val dropIdCaches = Array(8) { TipCache() }
    private val badgeIdCaches = Array(16) { TipCache() }
    private var badgeSlot = 0
    private val cancelIdCache = TipCache()
    private val learnIdCache = TipCache()
    private val clipRect = FloatArray(4)

    /** One "edit in Deep Edit" callback per bank, reused across frames (rebuilt if the ParametersState changes). */
    private val openDeepEditByBank = HashMap<String, () -> Unit>()
    private var openDeepEditOwner: ParametersState? = null
    private fun openDeepEditFor(parametersState: ParametersState, bankId: String): () -> Unit {
        if (openDeepEditOwner !== parametersState) { openDeepEditByBank.clear(); openDeepEditOwner = parametersState }
        return openDeepEditByBank.getOrPut(bankId) {
            {
                parametersState.setDisclosure(ctx.canonicalModuleId(bankId), ParametersState.DisclosureLevel.DEEP_EDIT)
                ctx.focusDeepEditTab(parametersState, bankId)
            }
        }
    }
    private var rowOffsets = FloatArray(8)

    private fun drawOverhang(session: llm.slop.liquidlsd.SessionContext, parametersState: ParametersState, o: Overhang) {
        if (o.kind == OVERHANG_CARD) {
            val parentDl = ImGui.getWindowDrawList()
            parentDl.addRectFilled(o.cardX1, o.cardTop, o.cardX2, o.cardBottom, o.selFill, 6f)
            parentDl.addRect(o.cardX1, o.cardTop, o.cardX2, o.cardBottom, o.selBorder, 6f, 0, 1.5f)
            return
        }
        val control = o.control ?: return
        val bankId = o.bankId
        val btnW = 72f
        val btnX = o.cellCenterX - btnW / 2f
        val btnY = o.btnY
        val btnH = 18f
        val isParamLearning = MacroLearnState.isControlLearning(control.id)
        ImGui.setCursorScreenPos(btnX, btnY)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (isParamLearning) {
                if (overhangButton(session, cancelIdCache.get(control.id) { "${Icons.X} Cancel##inline_cancel_${control.id}" }, btnX, btnY, btnW, btnH, TangoPalette.CANCEL_BTN_BG.u32(), "Cancel adding a target.")) {
                    MacroLearnState.cancelLearn()
                }
            } else {
                val canLearn = control.bindings.size < MacroControl.MAX_BINDINGS_PER_CONTROL
                if (canLearn) {
                    val learnTip = if (bankId == MacroEngine.GLOBAL) "Add a target: open any Edit row, then click a parameter slider or modulator property -- Global knobs can target anything."
                        else "Add a target: open this row's Edit, then click a parameter slider or modulator property in this row's deck and section."
                    if (overhangButton(session, learnIdCache.get(control.id) { "Add Target##inline_learn_${control.id}" }, btnX, btnY, btnW, btnH, TangoPalette.LEARN_BTN_BG.u32(), learnTip)) {
                        startLearnFor(session, parametersState, bankId, control)
                    }
                } else {
                    ImGui.textDisabled("Max 4")
                }
            }
        }
    }

    /** Arms Learn for [control] and shows its row's Deep Edit *Params* (not a Browse picker) so there's something to click. */
    private fun startLearnFor(session: llm.slop.liquidlsd.SessionContext, parametersState: ParametersState, bankId: String, control: MacroControl) {
        MacroLearnState.startLearn(control.id)
        MacroLearnState.selectedControlId = control.id
        // Dormant until the v1.1 free-knob row: GLOBAL has 0 knobs today (DECISIONS.md, "Column 3 MACROS Tab Removed"). Kept on purpose.
        // GLOBAL has no Deep Edit of its own: stay put and let the guest strip appear in whichever Edit the user opens.
        if (bankId == MacroEngine.GLOBAL) {
            if (!LibraryPanel.isEditView(session)) MacroLearnState.setStatus("ADD TARGET: Open any Edit and click a parameter or property.", 6000L)
            return
        }
        ctx.focusDeepEditTab(parametersState, bankId)
        val learnModuleId = ctx.canonicalModuleId(bankId)
        if (learnModuleId in PerformanceDeepEditBay.deepEditModuleIds) parametersState.openParams(learnModuleId)
    }

    /**
     * A button for the overhang, which hangs below the grid child's rect and so sits under the grid and bay
     * *child windows* -- ImGui gives them hover priority, which left only a few pixels of a normal
     * [ImGui.button] clickable. Hit-tested by rect against the parent window instead (no popup open).
     */
    private fun overhangButton(session: llm.slop.liquidlsd.SessionContext, label: String, x: Float, y: Float, w: Float, h: Float, bg: Int, tip: String): Boolean {
        val hovered = ImGui.isWindowHovered(imgui.flag.ImGuiHoveredFlags.ChildWindows) &&
            !ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId or imgui.flag.ImGuiPopupFlags.AnyPopupLevel) &&
            ImGui.isMouseHoveringRect(x, y, x + w, y + h, false)
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(x, y, x + w, y + h, bg, 3f)
        if (hovered) dl.addRectFilled(x, y, x + w, y + h, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, if (ImGui.isMouseDown(0)) 0.25f else 0.12f), 3f)
        val text = label.substringBefore("##")
        val ts = ImGui.calcTextSize(text)
        dl.addText(x + (w - ts.x) * 0.5f, y + (h - ts.y) * 0.5f, ImGui.getColorU32(ImGuiCol.Text), text)
        if (hovered) showTooltip(tip)
        return hovered && ImGui.isMouseClicked(0)
    }

    /** The rows to draw: the active page's rows, or the expanded module's row(s) in Deep Edit. See [PerfRows]. */
    private fun visibleRowsForPage(page: PerfPageDef, pages: List<PerfPageDef>, parametersState: ParametersState): List<RowDescriptor> =
        rowsCache.rows(page, ctx, parametersState, rackLabelFor, pages)

    private val rowsCache = PerfRows.RowsCache()
    private val rackLabelFor: (String) -> String = { deepEditBay.rackModuleDisplayLabel(it) }

    // -- 4x4 Knob Grid -----------------------------------------------------------

    /**
     * Draws [rows] (the visible rows -- see [visibleRowsForPage]), each [rowH] tall in the
     * Perform-view band.
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
        // Widget/disclosure ids embed the page's position so two pages never share ImGui ids.
        val tabIdx = PerfPageStore.default.indexOf(theme.performancePageId).coerceAtLeast(0)

        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)
        val gridW = ImGui.getContentRegionAvailX().coerceAtLeast(4f)
        // Rows past MIN_ROW_H overflow and the ##rack_grid_area child scrolls (see the cursor
        // advance at the end of this function).
        if (rowOffsets.size < rows.size + 1) rowOffsets = FloatArray(rows.size + 1)
        val rowTopOffsets = rowOffsets
        rowTopOffsets[0] = 0f
        for (i in rows.indices) rowTopOffsets[i + 1] = rowTopOffsets[i] + rowH
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
        // MASTER tab: Master ([MIX] over [FX] + chain header), Transitions (picker +
        // queue nav over crossfader), FX Wet/Dry (badge only), Clock (source/BPM/beat over tempo actions).
        // Uniform 42px title badge width across both DECKS and MASTER tabs.
        val masterTabBadgeW = 42f
        val transRowW = maxOf(deckRow1W, (gridW * 0.38f).coerceAtMost(420f))
        val masterRowW = DeckRowMetrics.row1Width(ctrlH, deckComboW)
        val masterTabLeftW = masterTabBadgeW + 6f + transRowW
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

        // Edit-row binding strip: which knob is selected and in which bank (see macroStripModeFor).
        val isEditView = LibraryPanel.isEditView(session)
        val stripControl = if (isEditView) MacroLearnState.selectedControlId?.let { MacroLearnState.findControl(it) } else null
        val stripBankId = stripControl?.let { MacroEngine.bankKeyOfControl(it.id) }
        // Dormant (GLOBAL has 0 knobs until the v1.1 free-knob row). A GLOBAL Learn armed in Perform view starts its timeout over once an Edit view opens.
        if (isEditView && !wasEditView && MacroLearnState.activeSession?.controlId?.let { MacroEngine.bankKeyOfControl(it) } == MacroEngine.GLOBAL) {
            MacroLearnState.restartLearnTimeout()
        }
        wasEditView = isEditView
        val stripLeftW = maxOf(deckLeftW, masterTabLeftW) - deckBadgeW - 6f

        for (rowIdx in rows.indices) {
            val row = rows[rowIdx]
            val descriptor = row
            val rowTopY = gridStartY + rowTopOffsets[rowIdx]
            val rowBottomY = gridStartY + rowTopOffsets[rowIdx + 1]
            val layoutBottomY = rowTopY + rowH

            val boxTopY = rowTopY + boxMarginY
            val boxBottomY = rowBottomY - boxMarginY
            val boxX1 = gridStartX + 2f
            val boxX2 = gridStartX + gridW - 2f

            // Rounded box (faint fill + accent border) around the row.
            val fillCol = TangoPalette.u32(descriptor.accent, 0.07f)
            val borderCol = TangoPalette.u32(descriptor.accent, 0.85f)
            val rawModuleId = descriptor.bankId
            val canonicalId = ctx.canonicalModuleId(rawModuleId)
            val isModuleExpanded = parametersState.disclosureFor(canonicalId) != ParametersState.DisclosureLevel.COLLAPSED ||
                                   parametersState.disclosureFor(rawModuleId) != ParametersState.DisclosureLevel.COLLAPSED
            val activeModuleId = if (parametersState.disclosureFor(canonicalId) != ParametersState.DisclosureLevel.COLLAPSED) canonicalId else rawModuleId
            val moduleId = activeModuleId

            val bank: MacroBank = MacroEngine.getBank(row.bankId) ?: MacroEngine.bankForParamPath(row.bankId)
            val isFxBankId = row.bankId in llm.slop.liquidlsd.macro.FxMacroSync.FX_BANK_IDS
            val rowChain = if (isFxBankId && descriptor.hasExtraHeader) ctx.resolveFxChain(mixer, row.bankId) else null
            val specs = PerfKnobResolver.resolve(bank, row.knobOffset, rowChain?.let { FxRowState.of(it) }, mixer)
            // The selected knob's card hangs below the row (see overhangDraws); the row border is
            // left open across its column so the card reads as a tab of the row.
            // Rows showing the macro strip carry their own Learn, so the selected knob's card stays inside the row
            // (no open border gap); other rows hang the card below the row for the Learn button.
            val rowHasStrip = stripControl != null && descriptor.hasExtraHeader &&
                macroStripModeFor(isEditView, row.bankId, stripBankId) != MacroStripMode.NONE
            val selectedCol = if (isModuleExpanded && !rowHasStrip) specs.firstOrNull { it.control.id == MacroLearnState.selectedControlId }?.col else null

            dl.addRectFilled(boxX1, boxTopY, boxX2, boxBottomY, fillCol, 8f)
            if (selectedCol == null) {
                dl.addRect(boxX1, boxTopY, boxX2, boxBottomY, borderCol, 8f, 0, 2f)
            } else {
                val gapX1 = gridStartX + geo.colCenterX(selectedCol) - geo.colW / 2f + 6f
                val gapX2 = gridStartX + geo.colCenterX(selectedCol) + geo.colW / 2f - 6f
                val pad = 3f
                for (clip in 0..2) {
                    when (clip) {
                        0 -> { clipRect[0] = boxX1 - pad; clipRect[1] = boxTopY - pad; clipRect[2] = gapX1; clipRect[3] = boxBottomY + pad }
                        1 -> { clipRect[0] = gapX2; clipRect[1] = boxTopY - pad; clipRect[2] = boxX2 + pad; clipRect[3] = boxBottomY + pad }
                        else -> { clipRect[0] = gapX1; clipRect[1] = boxTopY - pad; clipRect[2] = gapX2; clipRect[3] = boxBottomY - pad }
                    }
                    dl.pushClipRect(clipRect[0], clipRect[1], clipRect[2], clipRect[3], true)
                    dl.addRect(boxX1, boxTopY, boxX2, boxBottomY, borderCol, 8f, 0, 2f)
                    dl.popClipRect()
                }
            }

            val isDeckA = descriptor.bankId == MacroEngine.DECK_A || descriptor.bankId == MacroEngine.DECK_A_FX
            val isDeckB = descriptor.bankId == MacroEngine.DECK_B || descriptor.bankId == MacroEngine.DECK_B_FX
            val isDeckBG = descriptor.bankId == MacroEngine.DECK_BG || descriptor.bankId == MacroEngine.DECK_BG_FX
            val isDeckPV = descriptor.bankId == MacroEngine.DECK_PV || descriptor.bankId == MacroEngine.DECK_PV_FX
            val isDeckRow = isDeckA || isDeckB || isDeckBG || isDeckPV

            val isTransRow = descriptor.bankId == MacroEngine.TRANS
            val isClockRow = descriptor.bankId == MacroEngine.GLOBAL
            val isMasterRow = descriptor.bankId == MacroEngine.MASTER || descriptor.bankId == MacroEngine.MASTER_FX
            val displayLabel = descriptor.groupLabel

            var stripMode = if (stripControl != null && descriptor.hasExtraHeader) macroStripModeFor(isEditView, row.bankId, stripBankId) else MacroStripMode.NONE
            // Dormant until the v1.1 free-knob row. A visiting GLOBAL strip rides only on the row whose Edit is open.
            if (stripMode == MacroStripMode.GUEST && !isModuleExpanded) stripMode = MacroStripMode.NONE
            val stripOn = stripMode != MacroStripMode.NONE
            badgeClicked = false

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
                    ImGui.invisibleButton(dropIdCaches[rowIdx.coerceIn(0, 7)].get(rowIdx, dropTag) { "##perf_deck_drop_${rowIdx}_$dropTag" }, deckBadgeW.coerceAtLeast(1f), (boxBottomY - boxTopY).coerceAtLeast(1f))
                    applyDragScroll()
                    if (ImGui.beginDragDropTarget()) {
                        val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                        if (payload != null) {
                            val file = File(payload)
                            if (file.exists() && file.extension.lowercase() in listOf("lsdfx", "lsdfxchain")) {
                                // A single FX takes the first vacant slot; a full chain needs a slot named (drop on the slot instead).
                                if (!llm.slop.liquidlsd.presets.FxOps.dropAsset(session, file, targetDeck.fxChain)) {
                                    llm.slop.liquidlsd.ui.ToastOverlay.show("${deckLabels[deckTagIndex(dropTag)]} FX slots are full. Drop the effect onto a slot to replace it.")
                                }
                            } else if (file.exists() && file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                                UIManager.loadDeckPresetSafely(mixer, targetDeck, file)
                            }
                        }
                        val stockSourcePayload = ImGui.acceptDragDropPayload<String>(PresetListPanel.PAYLOAD_STOCK_SOURCE)
                        if (stockSourcePayload != null) {
                            VisualSourceRegistry.availableSources.find { it.id == stockSourcePayload }?.let { source ->
                                val deckLabel = deckLabels[deckTagIndex(dropTag)]
                                UIManager.changeVisualSourceSafely(mixer, targetDeck, deckLabel, source, parametersState)
                            }
                        }
                        ImGui.endDragDropTarget()
                    }
                } else if (isMasterRow) {
                    ImGui.setCursorScreenPos(boxX1, boxTopY)
                    ImGui.setNextItemAllowOverlap()
                    ImGui.invisibleButton(dropIdCaches[rowIdx.coerceIn(0, 7)].get(rowIdx, "master") { "##perf_master_drop_${rowIdx}" }, masterTabBadgeW.coerceAtLeast(1f), (boxBottomY - boxTopY).coerceAtLeast(1f))
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
                    ImGui.invisibleButton(dropIdCaches[rowIdx.coerceIn(0, 7)].get(rowIdx, "trans") { "##perf_trans_drop_${rowIdx}" }, masterTabBadgeW.coerceAtLeast(1f), (boxBottomY - boxTopY).coerceAtLeast(1f))
                    applyDragScroll()
                    if (ImGui.beginDragDropTarget()) {
                        val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                        if (payload != null) {
                            val file = File(payload)
                            llm.slop.liquidlsd.presets.TransitionOps.applyItem(file)
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
                        parametersState, activeModuleId, rackId(tabIdx, rowIdx)
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
                    drawTitleBadge(
                        session, badgeX, badgeY, masterTabBadgeW, badgeH, descriptor.accent, "M", UITheme.FontLevel.H1,
                        tooltip = "Master Unit\nConfigure Master parameters and FX"
                    )
                    drawEditGearInBadge(session, parametersState, descriptor, activeModuleId, tabIdx, rowIdx, badgeX, badgeY, masterTabBadgeW, badgeH)
                    ImGui.pushID(rowIdx)
                    if (!stripOn) PerformanceMasterControls.drawModeControls(session, mixer, parametersState, ctx, masterTabStartX, row1Y, row2YFinal, ctrlH, masterRowW, descriptor.pinnedMode)
                    if (descriptor.pinnedMode != "MIX") {
                        PerformanceMasterControls.drawBypassControls(session, mixer, boxX2 - pad - masterRightW, row2YFinal, ctrlH, masterRightW)
                    }
                    ImGui.popID()
                } else if (isTransRow) {
                    drawTitleBadge(
                        session, badgeX, badgeY, masterTabBadgeW, badgeH, descriptor.accent, "TR", UITheme.FontLevel.H1,
                        tooltip = "Transitions Unit\nConfigure video crossfader and transition shaders"
                    )
                    drawEditGearInBadge(session, parametersState, descriptor, activeModuleId, tabIdx, rowIdx, badgeX, badgeY, masterTabBadgeW, badgeH)
                    if (!stripOn) PerformanceTransitionsControls.draw(session, mixer, parametersState, masterTabStartX, row1Y, row2YFinal, ctrlH, transRowW)
                    PerformanceTransitionsControls.drawRightControls(session, mixer, boxX2 - pad - masterRightW, row1Y, ctrlH, masterRightW)
                } else if (isClockRow) {
                    drawTitleBadge(
                        session, badgeX, badgeY, masterTabBadgeW, badgeH, descriptor.accent, "CLK", UITheme.FontLevel.H2,
                        tooltip = "Clock Unit\nConfigure tempo, BPM, and synchronization"
                    )
                    if (!stripOn) PerformanceClockControls.draw(session, masterTabStartX, row1Y, row2YFinal, ctrlH)
                } else if (descriptor.bankId == MacroEngine.FX_SENDS) {
                    drawTitleBadge(
                        session, badgeX, badgeY, masterTabBadgeW, badgeH, descriptor.accent, "W/D", UITheme.FontLevel.H2,
                        tooltip = "FX Wet/Dry Unit\nConfigure per-deck FX wet/dry send levels"
                    )
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
                    val deckTagIdx = deckTagIndex(deckTag)
                    val deckLabel = deckLabels[deckTagIdx]

                    drawTitleBadge(
                        session, badgeX, badgeY, deckBadgeW, badgeH, descriptor.accent, deckTag, UITheme.FontLevel.H1,
                        tooltip = deckBadgeTips[deckTagIdx]
                    )
                    drawEditGearInBadge(session, parametersState, descriptor, activeModuleId, tabIdx, rowIdx, badgeX, badgeY, deckBadgeW, badgeH)

                    val leftStartX = badgeX + deckBadgeW + 6f
                    // Per-slot ImGui id scope: the same deck may sit in two slots (or pages), so tag-based ids must not collide.
                    ImGui.pushID(rowIdx)
                    if (!stripOn) deckControls.drawDeckRowLeftControls(session, mixer, parametersState, deckLabel, targetDeck, leftStartX, row1Y, row2YFinal, ctrlH, deckComboW, deckRow1W, descriptor.pinnedMode)
                    deckControls.drawDeckRowRightControls(
                        session, mixer, parametersState, deckLabel, targetDeck,
                        boxX2 - pad - deckRightW, row1Y, row2YFinal, ctrlH, deckRightW, descriptor.pinnedMode
                    )
                    ImGui.popID()
                }

                if (stripOn && stripControl != null) {
                    ImGui.pushID(rowIdx)
                    val closed = PerformanceMacroStrip.draw(
                        session, mixer, parametersState, stripMode, stripBankId ?: row.bankId, stripControl,
                        if (stripBankId in llm.slop.liquidlsd.macro.FxMacroSync.FX_BANK_IDS) ctx.resolveFxChain(mixer, stripBankId!!) else null,
                        masterTabStartX, row1Y, row2YFinal, stripLeftW, ctrlH
                    ) { startLearnFor(session, parametersState, stripBankId ?: row.bankId, stripControl) }
                    ImGui.popID()
                    if (closed || badgeClicked) {
                        if (MacroLearnState.isControlLearning(stripControl.id)) MacroLearnState.cancelLearn()
                        MacroLearnState.selectedControlId = null
                    }
                }
            }

            // 4 knobs for this row: content from the resolver, every position from [geo].
            val chainLabel = llm.slop.liquidlsd.macro.FxMacroSync.labelFor(row.bankId) ?: "FX"
            val openDeepEdit = openDeepEditFor(parametersState, row.bankId)
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

                val isSelectedKnob = isModuleExpanded && (control.id == MacroLearnState.selectedControlId)
                // Deep Edit extra, below the strip: the Learn button (the value shows in the knob face).
                val learnBtnY = stripY + geo.stripH + 4f
                if (isSelectedKnob) {
                    val cardX1 = cellCenterX - geo.colW / 2f + 6f
                    val cardX2 = cellCenterX + geo.colW / 2f - 6f
                    val selFill = TangoPalette.u32(TangoPalette.SYNC.normal, 0.14f)
                    val selBorder = TangoPalette.u32(TangoPalette.SYNC.bright, 0.85f)
                    val card = nextOverhang(OVERHANG_CARD)
                    card.cardX1 = cardX1; card.cardX2 = cardX2
                    card.cardTop = knobTopY - 4f; card.cardBottom = if (stripOn) stripY + geo.stripH + 4f else learnBtnY + 18f + 4f
                    card.selFill = selFill; card.selBorder = selBorder
                }

                val midiPath = MacroEngine.midiPathFor(bank, control)
                val isMidiLearning = midiPath != null &&
                    parametersState.midiLearnTarget.let { it is MidiLearnTarget.MacroTarget && it.macroPath == midiPath }
                val knobLabel = control.label.ifEmpty { knobFallbackLabel(knobIdx) }

                ImGui.setCursorScreenPos(gridStartX + geo.knobX(col), knobTopY)
                // The widget draws the face only -- the caption and value readout go into the fixed strip/extras below.
                MacroKnobWidget.draw(
                    session = session,
                    id = knobId(tabIdx, rowIdx, col),
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
                    alwaysShowReadout = isModuleExpanded,
                    oscAddress = MacroOscBridge.getKnobAddress(row.bankId, knobIdx),
                    onSelect = {
                        if (isModuleExpanded) {
                            // Picking a knob in the Edit view opens its binding strip (see macroStripModeFor).
                            MacroLearnState.selectedControlId = control.id
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

                // If expanded and selected, draw compact Learn/Cancel button beneath the value readout
                // (The Edit-view strip carries its own Learn/Cancel, so its row drops this one.)
                if (isSelectedKnob && !stripOn && !llm.slop.liquidlsd.macro.FxMacroSync.isFxBank(row.bankId)) {
                    val learn = nextOverhang(OVERHANG_LEARN)
                    learn.cellCenterX = cellCenterX; learn.btnY = learnBtnY
                    learn.bankId = row.bankId; learn.control = control
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
    private var badgeClicked = false
    private var wasEditView = false

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
        val bg = TangoPalette.u32(accent, 0.14f)
        val border = TangoPalette.u32(accent, 0.85f)
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
            ImGui.setNextItemAllowOverlap()
            ImGui.invisibleButton(badgeIdCaches[badgeSlot++ and 15].get(text, x.toInt(), y.toInt()) { "##title_badge_${text}_${x.toInt()}_${y.toInt()}" }, w.coerceAtLeast(1f), h.coerceAtLeast(1f))
            applyDragScroll()
            itemTooltip(tooltip)
            badgeClicked = ImGui.isItemClicked(0)
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
                parametersState, activeModuleId, rackId(tabIdx, rowIdx), badgeW, badgeH
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


