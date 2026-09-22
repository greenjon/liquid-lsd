package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons
import java.io.File

/**
 * Performance Mode 4×4 Macro Knob Matrix (see docs/user_guide/macros_and_rack.md).
 *
 * Displays up to 16 knobs arranged in rows of 4 columns (row count varies by tab -- e.g. DUAL
 * DECKS shows 2 rows of 4, MASTER_AND_FX shows 4), mapped to per-deck [MacroEngine] banks
 * according to the active layout tab. Knob drag adjusts the underlying
 * [llm.slop.liquidlsd.macro.MacroControl.value] directly, and right-click arms hardware MIDI
 * Learn for that knob (the pulsing cyan ring shows an armed knob; a repeat right-click cancels).
 * There is no parameter-bind Learn or binding inspector here -- that editing (labels, bindings,
 * curves, ranges) stays in Classic mode's Column 3 MACROS tab.
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
        LIVE_QUAD("LIVE QUAD", "One row per deck (Deck A / Deck B / Deck BG / Deck PV), knobs 1-4 each."),
        MASTER_AND_FX("MASTER & FX", "Transitions, Master composite alphas, FX sends, and Master FX."),
        LIVE_CONSOLE("LIVE CONSOLE", "Deck A / Deck B / focused FX bank+chain / Master & Transitions -- a single 4x4 surface for live shows.")
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
        /** True only for LIVE_CONSOLE's FX row: reserves header space for the bank/chain/bypass switcher buttons. */
        val hasExtraHeader: Boolean = false
    )

    // Canonical deck colors matching BrowserDeckButtons.
    companion object {
        private val COLOR_DECK_A   = floatArrayOf(0.2f,  0.4f,  0.8f)
        private val COLOR_DECK_B   = floatArrayOf(0.8f,  0.4f,  0.2f)
        private val COLOR_DECK_BG  = floatArrayOf(0.85f, 0.65f, 0.2f)
        private val COLOR_DECK_PV  = floatArrayOf(0.2f,  0.7f,  0.5f)
        private val COLOR_TRANS    = floatArrayOf(0.7f,  0.4f,  0.9f)
        private val COLOR_MASTER   = floatArrayOf(0.9f,  0.25f, 0.35f)
        private val COLOR_FX       = floatArrayOf(0.15f, 0.75f, 0.65f)

        private const val EXTRA_HEADER_H = 28f

        private val TAB_ROWS: Array<List<RowDescriptor>> = arrayOf(
            // LIVE QUAD: one row per deck (knobs 0–3 each: Deck A, Deck B, Deck BG, Deck PV)
            listOf(
                RowDescriptor(MacroEngine.DECK_A,  0, COLOR_DECK_A,  "DECK A",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_B,  0, COLOR_DECK_B,  "DECK B",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_BG, 0, COLOR_DECK_BG, "DECK BG", hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_PV, 0, COLOR_DECK_PV, "DECK PV", hasExtraHeader = true),
            ),
            // MASTER & FX: Transitions, Master, FX Sends, Master FX (1 row of 4 knobs each)
            listOf(
                RowDescriptor(MacroEngine.TRANS,     0, COLOR_TRANS,  "TRANSITIONS"),
                RowDescriptor(MacroEngine.MASTER,    0, COLOR_MASTER, "MASTER"),
                RowDescriptor(MacroEngine.FX_SENDS,  0, COLOR_FX,     "FX SENDS"),
                RowDescriptor(MacroEngine.MASTER_FX, 0, COLOR_FX,     "MASTER FX"),
            ),
            // LIVE CONSOLE: Deck A, Deck B, focused FX bank/chain (bankId placeholder rewritten to
            // the current focusedFxBankId each frame -- see drawMatrix), Master/Transitions.
            listOf(
                RowDescriptor(MacroEngine.DECK_A,    0, COLOR_DECK_A, "DECK A", hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_B,    0, COLOR_DECK_B, "DECK B", hasExtraHeader = true),
                RowDescriptor(MacroEngine.FX_BANK_1, 0, COLOR_FX,     "FX", hasExtraHeader = true),
                RowDescriptor(MacroEngine.TRANS,     0, COLOR_TRANS,  "MASTER / TRANSITIONS", hasExtraHeader = true),
            ),
        )
    }

    /** LIVE_CONSOLE-only: which FX bank Row 3 is currently focused on. Local UI state -- Classic
     *  Mode shows all 3 banks as separate subtabs simultaneously, so there's no shared "current
     *  bank" concept to read from. Chain selection *within* that bank, however, reads/writes the
     *  shared [llm.slop.liquidlsd.rendering.FxBank.activeChainIndex]. */
    private var focusedFxBankId: String = MacroEngine.FX_BANK_1
    private val presetSearchA = ImString(64)
    private val presetSearchB = ImString(64)
    private val presetSearchBG = ImString(64)
    private val presetSearchPV = ImString(64)
    private var comboWasOpenA = false
    private var comboWasOpenB = false
    private var comboWasOpenBG = false
    private var comboWasOpenPV = false

    // -- Draw ---------------------------------------------------------------------

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, parametersState: ParametersState) {
        val theme = session.uiTheme
        drawTabStrip(session, theme, mixer, parametersState)
        ImGui.spacing()
        drawMatrix(session, theme, mixer, parametersState)
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
    }

    // -- 4x4 Knob Grid -----------------------------------------------------------

    /** A run of consecutive [RowDescriptor]s sharing one bank/group label, enclosed in one box. */
    private data class RowGroup(val startRow: Int, val rowCount: Int, val descriptor: RowDescriptor)

    private fun drawMatrix(session: llm.slop.liquidlsd.SessionContext, theme: UITheme, mixer: Mixer, parametersState: ParametersState) {
        val tabIdx = theme.performanceMatrixTab.coerceIn(0, Tab.entries.size - 1)
        val templateRows = TAB_ROWS[tabIdx]
        // LIVE_CONSOLE's FX row bankId is dynamic (whichever bank is focused), not baked into the
        // static table -- substitute it here rather than forking a separate row-list per bank.
        val rows = if (tabIdx == Tab.LIVE_CONSOLE.ordinal) {
            templateRows.map { if (it.hasExtraHeader && (it.bankId == MacroEngine.FX_BANK_1 || it.bankId.startsWith("fx_") || it.bankId == MacroEngine.MASTER_FX)) it.copy(bankId = focusedFxBankId) else it }
        } else {
            templateRows
        }

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
        val rowH = (availH / rows.size.toFloat()).coerceAtLeast(1f)

        // Per group: box margin + box top border + large centered group title, then for each
        // sub-row it contains: an optional small "1-4"/"5-8" sub-label + knob diameter + per-knob
        // caption. All 16 knobs share one uniform diameter, so use the tightest group's budget.
        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        val groupLabelH = session.uiTheme.withFont(UITheme.FontLevel.H1) { ImGui.getTextLineHeight() }
        val subLabelH = captionH
        val boxMarginY = 3f   // gap between a group's box and the next group's / grid's edge
        val boxLabelGap = 3f  // gap above and below the group title, inside the box
        val subLabelGap = 2f  // gap between a sub-label and the knobs below it
        val boxPad = 6f       // inner padding between the box border and the knobs it contains
        val rowPad = 8f
        val colW = gridW / 4f
        val diamByWidth = (colW - rowPad).coerceAtLeast(8f)

        var diamByHeight = Float.MAX_VALUE
        for (group in groups) {
            val groupH = group.rowCount * rowH
            val hasSubLabel = rows[group.startRow].subLabel != null
            val extraHeaderH = if (rows[group.startRow].hasExtraHeader) EXTRA_HEADER_H + boxLabelGap else 0f
            val contentH = groupH - boxMarginY * 2f - boxLabelGap * 2f - groupLabelH - boxPad - extraHeaderH
            val subRowH = contentH / group.rowCount
            val knobAreaH = if (hasSubLabel) subRowH - subLabelH - subLabelGap else subRowH
            diamByHeight = minOf(diamByHeight, (knobAreaH - captionH).coerceAtLeast(8f))
        }
        val diameter = minOf(diamByWidth, diamByHeight).coerceIn(8f, 120f)

        val gridStartX = ImGui.getCursorScreenPosX()
        val gridStartY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()

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

            // Large centered group title just under the box's top border.
            val isDeckA = descriptor.bankId == MacroEngine.DECK_A
            val isDeckB = descriptor.bankId == MacroEngine.DECK_B
            val isDeckBG = descriptor.bankId == MacroEngine.DECK_BG
            val isDeckPV = descriptor.bankId == MacroEngine.DECK_PV
            val isDeckRow = isDeckA || isDeckB || isDeckBG || isDeckPV
            val isFxRow = descriptor.bankId in listOf(MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2, MacroEngine.MASTER_FX)
            val isTransRow = descriptor.bankId == MacroEngine.TRANS
            val displayLabel = when {
                descriptor.hasExtraHeader && isFxRow -> "FX: ${fxBankDisplayName(focusedFxBankId)}"
                else -> descriptor.groupLabel
            }
            if (h1Pushable) ImGui.pushFont(h1Font, UITheme.FONT_H1)
            val textW = ImGui.calcTextSize(displayLabel).x
            dl.addText(gridStartX + (gridW - textW) / 2f, titleTopY, borderCol, displayLabel)
            if (h1Pushable) ImGui.popFont()

            val afterTitleY = titleTopY + groupLabelH + boxLabelGap

            // Drop target placed over the title header area so it does not occlude the header buttons or knob grid.
            if (descriptor.hasExtraHeader) {
                if (isFxRow) {
                    ImGui.setCursorScreenPos(boxX1, boxTopY)
                    ImGui.invisibleButton("##perf_fx_drop_target", boxX2 - boxX1, (afterTitleY - boxTopY).coerceAtLeast(1f))
                    if (ImGui.beginDragDropTarget()) {
                        val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                        if (payload != null) {
                            val file = java.io.File(payload)
                            if (file.exists() && file.extension.lowercase() == "lsdfxchain") {
                                val fxBank = resolveFxBank(mixer, focusedFxBankId)
                                session.presetRepository.loadFxChainAsync(file).thenAccept { chainDto ->
                                    fxBank.activeChain.applyFxChain(chainDto)
                                }
                            }
                        }
                        ImGui.endDragDropTarget()
                    }
                } else if (isDeckRow) {
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
                    ImGui.invisibleButton("##perf_deck_drop_$dropTag", boxX2 - boxX1, (afterTitleY - boxTopY).coerceAtLeast(1f))
                    if (ImGui.beginDragDropTarget()) {
                        val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                        if (payload != null) {
                            val file = File(payload)
                            if (file.exists() && file.extension.lowercase() in listOf("patch", "lsd", "json")) {
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
                } else if (isTransRow) {
                    ImGui.setCursorScreenPos(boxX1, boxTopY)
                    ImGui.invisibleButton("##perf_trans_drop", boxX2 - boxX1, (afterTitleY - boxTopY).coerceAtLeast(1f))
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

            val contentTopY = if (descriptor.hasExtraHeader) {
                when {
                    isFxRow -> drawFxRowHeaderControls(session, mixer, parametersState, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                    isDeckA -> drawDeckRowHeaderControls(session, mixer, parametersState, "Deck A", mixer.deckA, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                    isDeckB -> drawDeckRowHeaderControls(session, mixer, parametersState, "Deck B", mixer.deckB, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                    isDeckBG -> drawDeckRowHeaderControls(session, mixer, parametersState, "Deck BG", mixer.deckBG, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                    isDeckPV -> drawDeckRowHeaderControls(session, mixer, parametersState, "Deck PV", mixer.deckPV, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                    isTransRow -> drawMasterTransitionsHeaderControls(session, mixer, parametersState, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                }
                afterTitleY + EXTRA_HEADER_H + boxLabelGap
            } else {
                afterTitleY
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

                val knobAreaCenterY = knobAreaTopY + (subBottomY - knobAreaTopY) / 2f
                val knobTopY = knobAreaCenterY - diameter / 2f - captionH / 2f

                // 4 knobs for this row.
                for (col in 0 until 4) {
                    val knobIdx = row.knobOffset + col
                    val control = bank.knobs.getOrNull(knobIdx) ?: continue

                    val cellCenterX = gridStartX + col * colW + colW / 2f

                    // Clickable link icon for FX slots (LIVE_CONSOLE FX row, cols 1..3)
                    if (descriptor.hasExtraHeader && isFxRow && col in 1..3) {
                        val slotIdx = col - 1
                        val fxBank = resolveFxBank(mixer, focusedFxBankId)
                        val isLinked = fxBank.activeChain.slotSuperKnobLink.getOrNull(slotIdx) == true
                        val btnSize = 20f
                        val btnX = (cellCenterX - diameter / 2f - btnSize - 4f).coerceAtLeast(gridStartX + col * colW + 2f)
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
                            if (ImGui.button("$icon##perf_fx_link_${focusedFxBankId}_$slotIdx", btnSize, btnSize)) {
                                val newLinked = !isLinked
                                fxBank.activeChain.setSlotLinked(slotIdx, newLinked)
                                val bankLabel = fxBankDisplayName(focusedFxBankId)
                                llm.slop.liquidlsd.macro.FxMacroSync.syncChain(focusedFxBankId, bankLabel, fxBank.activeChain, fxBank.activeChainIndex)
                            }
                        }
                        ImGui.popStyleVar()
                        ImGui.popStyleColor(3)
                        itemTooltip(
                            if (isLinked) "Slot ${slotIdx + 1} (${control.label}) is linked to Super Knob.\nClick to unlink."
                            else "Slot ${slotIdx + 1} (${control.label}) is unlinked.\nClick to link to Super Knob."
                        )
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
                        isSelected = false,
                        isLearning = isMidiLearning,
                        accentColor = row.accent,
                        bindings = control.bindings,
                        onSelect = {},
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
                }
            }
        }

        // Advance the ImGui cursor past the grid so the window scrollbar is correct.
        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), gridStartY + availH)
        ImGui.dummy(0f, 0f)
    }

    // -- LIVE_CONSOLE FX row header: bank switcher, chain switcher, bypass, resync -------------

    private fun resolveFxBank(mixer: Mixer, bankId: String): llm.slop.liquidlsd.rendering.FxBank = when (bankId) {
        MacroEngine.FX_BANK_1 -> mixer.fxBank1
        MacroEngine.FX_BANK_2 -> mixer.fxBank2
        MacroEngine.MASTER_FX -> mixer.masterFxBank
        else -> mixer.fxBank1
    }

    private fun fxBankDisplayName(bankId: String): String = when (bankId) {
        MacroEngine.FX_BANK_1 -> "FX1"
        MacroEngine.FX_BANK_2 -> "FX2"
        MacroEngine.MASTER_FX -> "MFX"
        else -> bankId
    }

    /** Drawn in place of the plain group title for LIVE_CONSOLE's FX row (see [RowDescriptor.hasExtraHeader]). */
    private fun drawFxRowHeaderControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        boxX1: Float,
        boxX2: Float,
        headerY: Float,
        headerH: Float
    ) {
        val fxBank = resolveFxBank(mixer, focusedFxBankId)
        val pad = 6f
        val gap = 4f
        val availW = (boxX2 - boxX1 - pad * 2f).coerceAtLeast(1f)

        val bankSegW = availW * 0.28f
        val chainSegW = availW * 0.34f
        val bypassSegW = availW * 0.20f
        val resyncSegW = availW * 0.14f

        ImGui.setCursorScreenPos(boxX1 + pad, headerY)
        ImGui.beginGroup()

        // Bank switcher [FX1][FX2][MFX]
        val bankIds = listOf(MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2, MacroEngine.MASTER_FX)
        val bankBtnW = ((bankSegW - gap * 2f) / 3f).coerceAtLeast(1f)
        for ((i, bankId) in bankIds.withIndex()) {
            if (i > 0) ImGui.sameLine(0f, gap)
            val isActive = bankId == focusedFxBankId
            ImGui.pushStyleColor(ImGuiCol.Button, if (isActive) ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
            if (ImGui.button("${fxBankDisplayName(bankId)}##perf_fx_bank_$bankId", bankBtnW, headerH)) {
                focusedFxBankId = bankId
                llm.slop.liquidlsd.macro.FxMacroSync.sync(bankId, resolveFxBank(mixer, bankId))
            }
            ImGui.popStyleColor()
        }
        itemTooltip("Focus Row 3 on FX Bank 1, FX Bank 2, or the post-crossfader Master FX bank.")

        ImGui.sameLine(0f, gap * 2f)

        // Chain switcher [C1][C2][C3] -- genuinely switches which chain is live (FxBank.activeChainIndex).
        val chainBtnW = ((chainSegW - gap * 2f) / 3f).coerceAtLeast(1f)
        for (i in 0 until 3) {
            if (i > 0) ImGui.sameLine(0f, gap)
            val isActive = fxBank.activeChainIndex == i
            ImGui.pushStyleColor(ImGuiCol.Button, if (isActive) ImGui.colorConvertFloat4ToU32(0.10f, 0.72f, 0.52f, 1f) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
            if (ImGui.button("C${i + 1}##perf_fx_chain_$i", chainBtnW, headerH)) {
                parametersState.setActiveChainIndex(fxBank, i)
            }
            ImGui.popStyleColor()
        }
        itemTooltip("Switch which of the 3 alternative chains is live in this bank.")

        ImGui.sameLine(0f, gap * 2f)

        // Bank-level bypass -- hard-mutes the whole bank regardless of which chain is active.
        val isBypassed = !fxBank.enabled
        ImGui.pushStyleColor(ImGuiCol.Button, if (isBypassed) ImGui.colorConvertFloat4ToU32(0.6f, 0.15f, 0.15f, 1f) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
        if (ImGui.button((if (isBypassed) "BYPASS" else "FX ON") + "##perf_fx_bypass", bypassSegW.coerceAtLeast(1f), headerH)) {
            fxBank.enabled = !fxBank.enabled
        }
        ImGui.popStyleColor()
        itemTooltip("Hard-bypasses the whole ${fxBankDisplayName(focusedFxBankId)} bank regardless of which chain is active.")

        ImGui.sameLine(0f, gap * 2f)

        // Explicit opt-back-in to the smart default, for a knob the user (or a prior focus
        // change) left manually retargeted -- see FxMacroSync's ownership rule.
        if (ImGui.button("Resync##perf_fx_resync", resyncSegW.coerceAtLeast(1f), headerH)) {
            llm.slop.liquidlsd.macro.FxMacroSync.sync(focusedFxBankId, fxBank, forceResync = true)
        }
        itemTooltip("Reset these 4 knobs to the Super Knob + 3 Metaknobs smart default, even if one was manually retargeted.")

        ImGui.endGroup()
    }

    /**
     * Drawn in place of the plain group title for Deck rows with headers (Deck A, B, BG, PV).
     * Provides quick generator badge, preset selector combo, eject button, randomize die button,
     * play queue / bg queue navigation, and FX send routing.
     */
    private fun drawDeckRowHeaderControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        deckLabel: String,
        deck: Deck,
        boxX1: Float,
        boxX2: Float,
        headerY: Float,
        headerH: Float
    ) {
        val pad = 6f
        val gap = 4f
        val availW = (boxX2 - boxX1 - pad * 2f).coerceAtLeast(1f)
        val isDeckA = deck === mixer.deckA
        val isDeckB = deck === mixer.deckB
        val isDeckBG = deck === mixer.deckBG
        val isDeckPV = deck === mixer.deckPV
        val tag = when {
            isDeckA -> "A"
            isDeckB -> "B"
            isDeckBG -> "BG"
            else -> "PV"
        }

        ImGui.setCursorScreenPos(boxX1 + pad, headerY)
        ImGui.beginGroup()

        // 1. Generator badge (fixed ~100px or scaled)
        val genBadgeW = (availW * 0.16f).coerceIn(70f, 130f)
        val genName = deck.source.displayName
        val genBorderCol = ImGui.colorConvertFloat4ToU32(0.35f, 0.40f, 0.50f, 0.70f)
        val genBgCol = ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.85f)
        val genTextCol = ImGui.colorConvertFloat4ToU32(0.80f, 0.85f, 0.95f, 1f)
        val curX = ImGui.getCursorScreenPosX()
        val curY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(curX, curY, curX + genBadgeW, curY + headerH, genBgCol, 4f)
        dl.addRect(curX, curY, curX + genBadgeW, curY + headerH, genBorderCol, 4f, 0, 1f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val textSz = ImGui.calcTextSize(genName)
            val tx = curX + (genBadgeW - textSz.x) * 0.5f
            val ty = curY + (headerH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(curX + 4f), ty, genTextCol, genName)
        }
        ImGui.invisibleButton("##perf_gen_badge_$tag", genBadgeW, headerH)
        itemTooltip("Generator: $genName ($deckLabel)")

        ImGui.sameLine(0f, gap)

        // 2. Preset dropdown combo with quick-search
        val activePreset = when {
            isDeckA -> session.presetManager.activePresetA
            isDeckB -> session.presetManager.activePresetB
            isDeckBG -> session.presetManager.activePresetBG
            else -> session.presetManager.activePresetPV
        }
        val isDirty = session.presetManager.isDeckDirty(deck, mixer)
        val dirtyMarker = if (isDirty) " *" else ""
        val presetDisplay = (activePreset ?: "Default") + dirtyMarker
        val comboW = (availW * 0.28f).coerceIn(90f, 250f)

        ImGui.setNextItemWidth(comboW)
        val searchBuf = when {
            isDeckA -> presetSearchA
            isDeckB -> presetSearchB
            isDeckBG -> presetSearchBG
            else -> presetSearchPV
        }
        val wasOpen = when {
            isDeckA -> comboWasOpenA
            isDeckB -> comboWasOpenB
            isDeckBG -> comboWasOpenBG
            else -> comboWasOpenPV
        }
        val isComboOpen = ImGui.beginCombo("##perf_preset_combo_$tag", presetDisplay)
        if (isComboOpen) {
            if (!wasOpen) {
                ImGui.setKeyboardFocusHere()
                when {
                    isDeckA -> comboWasOpenA = true
                    isDeckB -> comboWasOpenB = true
                    isDeckBG -> comboWasOpenBG = true
                    else -> comboWasOpenPV = true
                }
            }
            ImGui.setNextItemWidth(-1f)
            ImGui.inputTextWithHint("##preset_search_$tag", "Search presets... (Esc to clear)", searchBuf)
            if (ImGui.isItemActive() && ImGui.isKeyPressed(ImGuiKey.Escape)) {
                searchBuf.set("")
            }
            ImGui.separator()

            val query = searchBuf.get().trim()
            val allPresets = FileSystemManager.scanAllPresets()
            val filtered = if (query.isEmpty()) allPresets else allPresets.filter { it.name.contains(query, ignoreCase = true) }

            if (filtered.isEmpty()) {
                ImGui.textDisabled(if (query.isEmpty()) "No presets found" else "No matching presets")
            } else {
                for (preset in filtered) {
                    val isSelected = preset.name == activePreset
                    if (ImGui.selectable("${preset.name}##perf_pselect_${tag}_${preset.path.hashCode()}", isSelected)) {
                        session.presetRepository.loadDeckPresetAsync(
                            File(preset.path),
                            isDeckA = isDeckA,
                            isDeckBG = isDeckBG,
                            isDeckPV = isDeckPV
                        )
                        searchBuf.set("")
                    }
                    if (isSelected) {
                        ImGui.setItemDefaultFocus()
                    }
                }
            }
            ImGui.endCombo()
        } else {
            if (wasOpen) {
                searchBuf.set("")
                when {
                    isDeckA -> comboWasOpenA = false
                    isDeckB -> comboWasOpenB = false
                    isDeckBG -> comboWasOpenBG = false
                    else -> comboWasOpenPV = false
                }
            }
        }
        itemTooltip(
            if (activePreset != null) "Active preset: $activePreset$dirtyMarker\nClick to search and select presets."
            else "Select a preset for $deckLabel."
        )

        ImGui.sameLine(0f, gap)

        // 3. Eject Button [ EJECT ]
        val iconBtnW = headerH
        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.45f, 0.20f, 0.20f, 1f))
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.EJECT}##perf_eject_$tag", iconBtnW, headerH)) {
                UIManager.triggerDeckEject(deck, isDeckA = isDeckA, isDeckPV = isDeckPV)
            }
        }
        ImGui.popStyleColor(2)
        itemTooltip("Eject current preset from $deckLabel and reset to defaults.")

        // 4. Randomize Die Button [ DICES ]
        if (session.uiTheme.randomizationEnabled) {
            ImGui.sameLine(0f, gap)
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.20f, 0.16f, 0.24f, 0.90f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.35f, 0.22f, 0.42f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.DICES}##perf_rand_$tag", iconBtnW, headerH)) {
                    ParametersUndo.pushUndoState(parametersState, mixer)
                    when {
                        isDeckA -> mixer.randomizeDeckA()
                        isDeckB -> mixer.randomizeDeckB()
                        isDeckBG -> mixer.randomizeDeckBG()
                        else -> mixer.randomizeDeckPV()
                    }
                }
            }
            ImGui.popStyleColor(2)
            itemTooltip("Randomize $deckLabel modulators & base values.\nClick to randomize with undo support.")
        }

        ImGui.sameLine(0f, gap * 2f)

        // 5. PlayQueue / BG Queue navigation (or preview indicator for PV)
        val navBtnW = (headerH * 0.9f).coerceAtLeast(22f)
        if (isDeckA || isDeckB) {
            val q = session.playQueueManager.queue
            val qIdx = session.playQueueManager.activeIndex
            val qCountStr = if (q.isNotEmpty() && qIdx in q.indices) "${qIdx + 1}/${q.size}" else if (q.isNotEmpty()) "-/${q.size}" else "--"

            val qPrevKey = "Global/queuePrev"
            val qPrevOscKey = "Mixer/queuePrev"
            val isMidiLearnQPrev = session.parametersState.isMidiTargetLearning(qPrevKey)
            val isOscLearnQPrev = OscLearnState.isTargetLearning(qPrevOscKey)
            val qPrevMidiMapping = session.midiMappingManager.getMappingForParameter(qPrevKey)
            val qPrevMidiText = qPrevMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

            val qPrevX = ImGui.getCursorScreenPosX()
            val qPrevY = ImGui.getCursorScreenPosY()
            if (ImGui.button("<##perf_q_prev_$tag", navBtnW, headerH)) {
                session.playQueueManager.triggerPrevious(mixer)
            }
            if (isMidiLearnQPrev) {
                dl.addRect(qPrevX - 1f, qPrevY - 1f, qPrevX + navBtnW + 1f, qPrevY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
            }
            if (ImGui.beginPopupContextItem("perf_q_prev_ctx_$tag")) {
                ImGui.textDisabled("PlayQueue Prev (<)")
                ImGui.separator()
                if (ImGui.menuItem("Trigger Previous")) {
                    session.playQueueManager.triggerPrevious(mixer)
                }
                ImGui.separator()
                if (isMidiLearnQPrev) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                        session.parametersState.midiLearnTarget = null
                    }
                } else {
                    if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Queue Prev)")) {
                        session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(qPrevKey))
                    }
                }
                if (qPrevMidiMapping != null) {
                    if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                        session.midiMappingManager.removeMapping(qPrevKey)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                if (isOscLearnQPrev) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                        OscLearnState.cancelLearn()
                    }
                } else {
                    if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Queue Prev)")) {
                        OscLearnState.startLearn(qPrevOscKey, 0f, 1f, "PlayQueue Prev")
                    }
                }
                ImGui.endPopup()
            }
            itemTooltip("Advance to previous item in PlayQueue.$qPrevMidiText\nRight-click for MIDI/OSC Learn.")

            ImGui.sameLine(0f, 2f)

            val qTextW = (availW * 0.10f).coerceIn(36f, 54f)
            val qCurX = ImGui.getCursorScreenPosX()
            val qCurY = ImGui.getCursorScreenPosY()
            dl.addRectFilled(qCurX, qCurY, qCurX + qTextW, qCurY + headerH, genBgCol, 3f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val sz = ImGui.calcTextSize(qCountStr)
                dl.addText(qCurX + (qTextW - sz.x) * 0.5f, qCurY + (headerH - sz.y) * 0.5f, genTextCol, qCountStr)
            }
            ImGui.invisibleButton("##perf_q_idx_$tag", qTextW, headerH)
            itemTooltip("PlayQueue status: $qCountStr")

            ImGui.sameLine(0f, 2f)

            val qNextKey = "Global/queueNext"
            val qNextOscKey = "Mixer/queueNext"
            val isMidiLearnQNext = session.parametersState.isMidiTargetLearning(qNextKey)
            val isOscLearnQNext = OscLearnState.isTargetLearning(qNextOscKey)
            val qNextMidiMapping = session.midiMappingManager.getMappingForParameter(qNextKey)
            val qNextMidiText = qNextMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

            val qNextX = ImGui.getCursorScreenPosX()
            val qNextY = ImGui.getCursorScreenPosY()
            if (ImGui.button(">##perf_q_next_$tag", navBtnW, headerH)) {
                session.playQueueManager.triggerNext(mixer)
            }
            if (isMidiLearnQNext) {
                dl.addRect(qNextX - 1f, qNextY - 1f, qNextX + navBtnW + 1f, qNextY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
            }
            if (ImGui.beginPopupContextItem("perf_q_next_ctx_$tag")) {
                ImGui.textDisabled("PlayQueue Next (>)")
                ImGui.separator()
                if (ImGui.menuItem("Trigger Next")) {
                    session.playQueueManager.triggerNext(mixer)
                }
                ImGui.separator()
                if (isMidiLearnQNext) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                        session.parametersState.midiLearnTarget = null
                    }
                } else {
                    if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Queue Next)")) {
                        session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(qNextKey))
                    }
                }
                if (qNextMidiMapping != null) {
                    if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                        session.midiMappingManager.removeMapping(qNextKey)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                if (isOscLearnQNext) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                        OscLearnState.cancelLearn()
                    }
                } else {
                    if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Queue Next)")) {
                        OscLearnState.startLearn(qNextOscKey, 0f, 1f, "PlayQueue Next")
                    }
                }
                ImGui.endPopup()
            }
            itemTooltip("Advance to next item in PlayQueue.$qNextMidiText\nRight-click for MIDI/OSC Learn.")
        } else if (isDeckBG) {
            val bgQ = session.bgQueueManager.queue
            val bgQIdx = session.bgQueueManager.activeIndex
            val bgQCountStr = if (bgQ.isNotEmpty() && bgQIdx in bgQ.indices) "${bgQIdx + 1}/${bgQ.size}" else if (bgQ.isNotEmpty()) "-/${bgQ.size}" else "--"

            val bgPrevKey = "Global/bgQueuePrev"
            val bgPrevOscKey = "Mixer/bgQueuePrev"
            val isMidiLearnBgPrev = session.parametersState.isMidiTargetLearning(bgPrevKey)
            val isOscLearnBgPrev = OscLearnState.isTargetLearning(bgPrevOscKey)
            val bgPrevMidiMapping = session.midiMappingManager.getMappingForParameter(bgPrevKey)
            val bgPrevMidiText = bgPrevMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

            val bgPrevX = ImGui.getCursorScreenPosX()
            val bgPrevY = ImGui.getCursorScreenPosY()
            if (ImGui.button("<##perf_bg_prev", navBtnW, headerH)) {
                session.bgQueueManager.triggerPrevious(mixer)
            }
            if (isMidiLearnBgPrev) {
                dl.addRect(bgPrevX - 1f, bgPrevY - 1f, bgPrevX + navBtnW + 1f, bgPrevY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
            }
            if (ImGui.beginPopupContextItem("perf_bg_prev_ctx")) {
                ImGui.textDisabled("BG Queue Prev (<)")
                ImGui.separator()
                if (ImGui.menuItem("Trigger Previous")) {
                    session.bgQueueManager.triggerPrevious(mixer)
                }
                ImGui.separator()
                if (isMidiLearnBgPrev) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                        session.parametersState.midiLearnTarget = null
                    }
                } else {
                    if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (BG Queue Prev)")) {
                        session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(bgPrevKey))
                    }
                }
                if (bgPrevMidiMapping != null) {
                    if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                        session.midiMappingManager.removeMapping(bgPrevKey)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                if (isOscLearnBgPrev) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                        OscLearnState.cancelLearn()
                    }
                } else {
                    if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (BG Queue Prev)")) {
                        OscLearnState.startLearn(bgPrevOscKey, 0f, 1f, "BG Queue Prev")
                    }
                }
                ImGui.endPopup()
            }
            itemTooltip("Advance to previous item in BG Queue.$bgPrevMidiText\nRight-click for MIDI/OSC Learn.")

            ImGui.sameLine(0f, 2f)

            val bgQTextW = (availW * 0.10f).coerceIn(36f, 54f)
            val bgCurX = ImGui.getCursorScreenPosX()
            val bgCurY = ImGui.getCursorScreenPosY()
            dl.addRectFilled(bgCurX, bgCurY, bgCurX + bgQTextW, bgCurY + headerH, genBgCol, 3f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val sz = ImGui.calcTextSize(bgQCountStr)
                dl.addText(bgCurX + (bgQTextW - sz.x) * 0.5f, bgCurY + (headerH - sz.y) * 0.5f, genTextCol, bgQCountStr)
            }
            ImGui.invisibleButton("##perf_bg_idx", bgQTextW, headerH)
            itemTooltip("BG Queue status: $bgQCountStr")

            ImGui.sameLine(0f, 2f)

            val bgNextKey = "Global/bgQueueNext"
            val bgNextOscKey = "Mixer/bgQueueNext"
            val isMidiLearnBgNext = session.parametersState.isMidiTargetLearning(bgNextKey)
            val isOscLearnBgNext = OscLearnState.isTargetLearning(bgNextOscKey)
            val bgNextMidiMapping = session.midiMappingManager.getMappingForParameter(bgNextKey)
            val bgNextMidiText = bgNextMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

            val bgNextX = ImGui.getCursorScreenPosX()
            val bgNextY = ImGui.getCursorScreenPosY()
            if (ImGui.button(">##perf_bg_next", navBtnW, headerH)) {
                session.bgQueueManager.triggerNext(mixer)
            }
            if (isMidiLearnBgNext) {
                dl.addRect(bgNextX - 1f, bgNextY - 1f, bgNextX + navBtnW + 1f, bgNextY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
            }
            if (ImGui.beginPopupContextItem("perf_bg_next_ctx")) {
                ImGui.textDisabled("BG Queue Next (>)")
                ImGui.separator()
                if (ImGui.menuItem("Trigger Next")) {
                    session.bgQueueManager.triggerNext(mixer)
                }
                ImGui.separator()
                if (isMidiLearnBgNext) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                        session.parametersState.midiLearnTarget = null
                    }
                } else {
                    if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (BG Queue Next)")) {
                        session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(bgNextKey))
                    }
                }
                if (bgNextMidiMapping != null) {
                    if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                        session.midiMappingManager.removeMapping(bgNextKey)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                if (isOscLearnBgNext) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                        OscLearnState.cancelLearn()
                    }
                } else {
                    if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (BG Queue Next)")) {
                        OscLearnState.startLearn(bgNextOscKey, 0f, 1f, "BG Queue Next")
                    }
                }
                ImGui.endPopup()
            }
            itemTooltip("Advance to next item in BG Queue.$bgNextMidiText\nRight-click for MIDI/OSC Learn.")
        } else {
            // Deck PV indicator / focus button
            val pvBadgeW = (navBtnW * 2f + 40f).coerceIn(50f, 90f)
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.12f, 0.22f, 0.18f, 0.85f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.18f, 0.32f, 0.25f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                if (ImGui.button("PREVIEW##perf_pv_badge", pvBadgeW, headerH)) {
                    parametersState.activeTopTab = "Deck PV"
                }
            }
            ImGui.popStyleColor(2)
            itemTooltip("Deck PV (Preview Deck)\nClick to focus in Parameters panel.")
        }

        ImGui.sameLine(0f, gap * 2f)

        // 6. FX Routing toggles [FX1][FX2]
        // deck.fxRouting.baseValue: 0 = Off, 1 = FX1, 2 = FX2
        val currentRouting = kotlin.math.round(deck.fxRouting.baseValue).toInt().coerceIn(0, 2)
        val fxBtnW = ((availW - (ImGui.getCursorScreenPosX() - (boxX1 + pad)) - gap) * 0.5f).coerceIn(28f, 55f)
        val fxRouteParamKey = "$deckLabel/View/FxRouting"
        val isMidiLearnFx = session.parametersState.isMidiTargetLearning(fxRouteParamKey)
        val isOscLearnFx = OscLearnState.isTargetLearning(fxRouteParamKey)
        val fxMidiMapping = session.midiMappingManager.getMappingForParameter(fxRouteParamKey)
        val fxMidiText = fxMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val fx1X = ImGui.getCursorScreenPosX()
        val fx1Y = ImGui.getCursorScreenPosY()
        // [FX1]
        val isFx1 = currentRouting == 1
        ImGui.pushStyleColor(ImGuiCol.Button, if (isFx1) ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
        if (ImGui.button("FX1##perf_route_fx1_$tag", fxBtnW, headerH)) {
            deck.fxRouting.baseValue = if (isFx1) 0f else 1f
        }
        ImGui.popStyleColor()
        if (isMidiLearnFx) {
            dl.addRect(fx1X - 1f, fx1Y - 1f, fx1X + fxBtnW + 1f, fx1Y + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_route_fx1_ctx_$tag")) {
            drawFxRoutingContextMenu(session, deck, deckLabel, fxRouteParamKey, isMidiLearnFx, isOscLearnFx, fxMidiMapping)
            ImGui.endPopup()
        }
        itemTooltip((if (isFx1) "Routed to FX Bank 1 (Click to turn off)" else "Route $deckLabel to FX Bank 1") + "$fxMidiText\nRight-click for FX Routing & MIDI/OSC Learn.")

        ImGui.sameLine(0f, gap)

        val fx2X = ImGui.getCursorScreenPosX()
        val fx2Y = ImGui.getCursorScreenPosY()
        // [FX2]
        val isFx2 = currentRouting == 2
        ImGui.pushStyleColor(ImGuiCol.Button, if (isFx2) ImGui.colorConvertFloat4ToU32(0.10f, 0.72f, 0.52f, 1f) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
        if (ImGui.button("FX2##perf_route_fx2_$tag", fxBtnW, headerH)) {
            deck.fxRouting.baseValue = if (isFx2) 0f else 2f
        }
        ImGui.popStyleColor()
        if (isMidiLearnFx) {
            dl.addRect(fx2X - 1f, fx2Y - 1f, fx2X + fxBtnW + 1f, fx2Y + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_route_fx2_ctx_$tag")) {
            drawFxRoutingContextMenu(session, deck, deckLabel, fxRouteParamKey, isMidiLearnFx, isOscLearnFx, fxMidiMapping)
            ImGui.endPopup()
        }
        itemTooltip((if (isFx2) "Routed to FX Bank 2 (Click to turn off)" else "Route $deckLabel to FX Bank 2") + "$fxMidiText\nRight-click for FX Routing & MIDI/OSC Learn.")

        ImGui.endGroup()
    }

    private fun drawFxRoutingContextMenu(
        session: llm.slop.liquidlsd.SessionContext,
        deck: Deck,
        deckLabel: String,
        fxRouteParamKey: String,
        isMidiLearnFx: Boolean,
        isOscLearnFx: Boolean,
        fxMidiMapping: llm.slop.liquidlsd.midi.MidiControlMapping?
    ) {
        ImGui.textDisabled("$deckLabel FX Routing")
        ImGui.separator()
        if (ImGui.menuItem("Off (Bypass FX)", "", deck.fxRouting.baseValue == 0f)) {
            deck.fxRouting.baseValue = 0f
        }
        if (ImGui.menuItem("Route to FX Bank 1", "", deck.fxRouting.baseValue == 1f)) {
            deck.fxRouting.baseValue = 1f
        }
        if (ImGui.menuItem("Route to FX Bank 2", "", deck.fxRouting.baseValue == 2f)) {
            deck.fxRouting.baseValue = 2f
        }
        ImGui.separator()
        if (isMidiLearnFx) {
            if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                session.parametersState.midiLearnTarget = null
            }
        } else {
            if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI ($deckLabel FX Route)")) {
                session.parametersState.startMidiLearn(
                    MidiLearnTarget.BaseValueSlider(
                        paramKey = fxRouteParamKey,
                        label = "$deckLabel FX Route",
                        param = deck.fxRouting,
                        min = 0f,
                        max = 2f
                    )
                )
            }
        }
        if (fxMidiMapping != null) {
            if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                session.midiMappingManager.removeMapping(fxRouteParamKey)
                session.midiMappingManager.saveActiveProfile()
            }
        }
        if (isOscLearnFx) {
            if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                OscLearnState.cancelLearn()
            }
        } else {
            if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC ($deckLabel FX Route)")) {
                OscLearnState.startLearn(fxRouteParamKey, 0f, 2f, "$deckLabel FX Route")
            }
        }
        val oscAddress = OscMappingManager.getMappings().entries.find { it.value.parameterPath == fxRouteParamKey }?.key
        if (oscAddress != null) {
            if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping ($oscAddress)")) {
                OscMappingManager.removeMapping(oscAddress)
                OscMappingManager.saveActiveProfile()
            }
        }
    }

    /**
     * Drawn in place of the plain group title for LIVE_CONSOLE's Master / Transitions row.
     * Provides Deck A snap badge, interactive crossfader slider, Deck B snap badge, Auto-fade button,
     * transition picker popup button, and TransitionQueue prev/status/next navigation.
     */
    private fun drawMasterTransitionsHeaderControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        boxX1: Float,
        boxX2: Float,
        headerY: Float,
        headerH: Float
    ) {
        val pad = 6f
        val gap = 4f
        val availW = (boxX2 - boxX1 - pad * 2f).coerceAtLeast(1f)
        val dl = ImGui.getWindowDrawList()
        val centerY = headerY + headerH * 0.5f

        ImGui.setCursorScreenPos(boxX1 + pad, headerY)
        ImGui.beginGroup()

        // 1. Deck A Snap Badge [ A ]
        val badgeW = (headerH * 1.05f).coerceIn(24f, 32f)
        val badgeAX = boxX1 + pad
        val badgeAY = headerY
        val rgbA = BrowserDeckButtons.colorA()
        val colorA = ImGui.colorConvertFloat4ToU32(rgbA[0], rgbA[1], rgbA[2], 1f)
        val snapAKey = "Global/snapDeckA"
        val isMidiLearnSnapA = session.parametersState.isMidiTargetLearning(snapAKey)
        val snapAMapping = session.midiMappingManager.getMappingForParameter(snapAKey)
        val snapAMidiText = snapAMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        dl.addRectFilled(badgeAX, badgeAY, badgeAX + badgeW, badgeAY + headerH, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
        val badgeBorderColorA = if (isMidiLearnSnapA) ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f) else colorA
        dl.addRect(badgeAX, badgeAY, badgeAX + badgeW, badgeAY + headerH, badgeBorderColorA, 4f, 0, if (isMidiLearnSnapA) 2f else 1.5f)

        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            val textSz = ImGui.calcTextSize("A")
            dl.addText(badgeAX + (badgeW - textSz.x) * 0.5f, badgeAY + (headerH - textSz.y) * 0.5f, colorA, "A")
        }
        ImGui.invisibleButton("##perf_crossfade_deck_a", badgeW, headerH)
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
        }
        if (ImGui.isItemClicked(0)) {
            mixer.onCrossfadeManualTakeover()
            mixer.crossfade.set(-1.0f)
        }
        if (ImGui.beginPopupContextItem("perf_snap_a_ctx")) {
            ImGui.textDisabled("Snap Deck A")
            ImGui.separator()
            if (ImGui.menuItem("Snap Crossfader to Deck A")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(-1.0f)
            }
            ImGui.separator()
            if (isMidiLearnSnapA) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Snap Deck A)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(snapAKey))
                }
            }
            if (snapAMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(snapAKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Deck A (Click to snap crossfader to Deck A)$snapAMidiText\nRight-click for MIDI Learn.")

        ImGui.sameLine(0f, gap)

        // Calculate layout allocations for right-side elements first:
        // Transition Queue nav: Prev (24px) + Count (~45px) + Next (24px) + gaps
        val navBtnW = (headerH * 0.9f).coerceIn(22f, 28f)
        val qTextW = 46f
        val qNavTotalW = navBtnW * 2f + qTextW + 4f

        // Transition Picker button
        val transBtnW = (availW * 0.22f).coerceIn(100f, 200f)

        // Auto-Fade button
        val autoBtnW = (availW * 0.10f).coerceIn(48f, 75f)

        // Fade Speed widget (drag/badge)
        val speedBtnW = (availW * 0.08f).coerceIn(44f, 65f)

        // Deck B badge width
        val badgeBW = badgeW

        // Crossfader slider takes whatever remaining width is available between Deck A and Deck B
        val fixedRightW = gap + badgeBW + gap * 2f + autoBtnW + gap + speedBtnW + gap + transBtnW + gap + qNavTotalW
        val crossfaderW = (availW - badgeW - fixedRightW).coerceAtLeast(50f)

        // 2. Crossfader Slider Track
        val lineStartX = badgeAX + badgeW + gap
        val lineEndX = lineStartX + crossfaderW
        val lineWidth = crossfaderW

        val trackPadX = 2f
        val trackW = lineWidth + trackPadX * 2f
        ImGui.setCursorScreenPos(lineStartX - trackPadX, headerY)
        ImGui.invisibleButton("##perf_crossfader_track", trackW, headerH)

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

        val isTrackHovered = ImGui.isItemHovered()
        val isTrackActive = ImGui.isItemActive()
        val paramKey = "Mixer/crossfade"
        val isTarget = parametersState.midiLearnTarget?.let {
            it is MidiLearnTarget.BaseValueSlider && it.paramKey == paramKey
        } ?: false
        val isMidiLearnXfader = parametersState.isMidiTargetLearning(paramKey)
        val isOscLearnXfader = OscLearnState.isTargetLearning(paramKey)
        val xfaderMidiMapping = session.midiMappingManager.getMappingForParameter(paramKey)

        if (ImGui.beginPopupContextItem("perf_xfader_ctx")) {
            ImGui.textDisabled("Crossfader (Mixer/crossfade)")
            ImGui.separator()
            if (ImGui.menuItem("Reset to Center (0.0)")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(0.0f)
            }
            if (ImGui.menuItem("Snap to Deck A (-1.0)")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(-1.0f)
            }
            if (ImGui.menuItem("Snap to Deck B (+1.0)")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(1.0f)
            }
            ImGui.separator()
            if (isMidiLearnXfader) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Crossfader)")) {
                    parametersState.startMidiLearn(
                        MidiLearnTarget.BaseValueSlider(
                            paramKey = paramKey,
                            label = "Crossfader",
                            param = mixer.crossfade,
                            min = -1.0f,
                            max = 1.0f
                        )
                    )
                }
            }
            if (xfaderMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(paramKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnXfader) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Crossfader)")) {
                    OscLearnState.startLearn(
                        parameterPath = paramKey,
                        minVal = -1.0f,
                        maxVal = 1.0f,
                        displayLabel = "Crossfader"
                    )
                }
            }
            val oscAddress = OscMappingManager.getMappings().entries.find { it.value.parameterPath == paramKey }?.key
            if (oscAddress != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping ($oscAddress)")) {
                    OscMappingManager.removeMapping(oscAddress)
                    OscMappingManager.saveActiveProfile()
                }
            }
            ImGui.endPopup()
        }

        if (isTrackActive) {
            mixer.onCrossfadeManualTakeover()
            val mouseX = ImGui.getIO().mousePos.x
            val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
            val newVal = -1.0f + pct * 2.0f
            mixer.crossfade.set(newVal)
        }

        val io = ImGui.getIO()
        if (isTrackHovered || isTrackActive) {
            if (io.mouseWheel != 0f) {
                mixer.onCrossfadeManualTakeover()
                val shift = io.keyShift
                val ctrl = io.keyCtrl
                val delta = if (ctrl && shift) 0.1f else if (shift) 0.02f else 0.05f
                val newVal = (mixer.crossfade.baseValue + io.mouseWheel * delta).coerceIn(-1.0f, 1.0f)
                mixer.crossfade.set(newVal)
                io.mouseWheel = 0f
            }
            if (ImGui.isMouseClicked(2) || ImGui.isItemClicked(2)) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(0.0f)
            }
        }

        if (isTrackHovered && session.uiTheme.tooltipsEnabled) {
            val mapping = session.midiMappingManager.getMappingForParameter(paramKey)
            val midiText = mapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""
            itemTooltip("Crossfader$midiText\nDrag or scroll to blend. Middle-click to center.\nRight-click for MIDI/OSC Learn.")
        }

        // Render crossfader visual tracks & ticks
        val rgbB = BrowserDeckButtons.colorB()
        val colorB = ImGui.colorConvertFloat4ToU32(rgbB[0], rgbB[1], rgbB[2], 1f)
        val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f)
        dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)

        val markColFaint = ImGui.colorConvertFloat4ToU32(0.65f, 0.65f, 0.65f, 0.28f)
        val markColCenter = ImGui.colorConvertFloat4ToU32(0.85f, 0.85f, 0.85f, 0.45f)
        val markColEnds = ImGui.colorConvertFloat4ToU32(0.70f, 0.70f, 0.70f, 0.35f)

        // Ends (-1.0, +1.0)
        dl.addLine(lineStartX, centerY - 6f, lineStartX, centerY + 6f, markColEnds, 1.5f)
        dl.addLine(lineEndX, centerY - 6f, lineEndX, centerY + 6f, markColEnds, 1.5f)

        // Midway points (-0.5, +0.5)
        val midLeftX = lineStartX + lineWidth * 0.25f
        val midRightX = lineStartX + lineWidth * 0.75f
        dl.addLine(midLeftX, centerY - 5f, midLeftX, centerY + 5f, markColFaint, 1f)
        dl.addLine(midRightX, centerY - 5f, midRightX, centerY + 5f, markColFaint, 1f)

        // Middle (0.0)
        val centerX = lineStartX + lineWidth * 0.50f
        dl.addLine(centerX, centerY - 8f, centerX, centerY + 8f, markColCenter, 1.5f)

        // Active bipolar colored bar
        val valPct = ((mixer.crossfade.baseValue - (-1f)) / 2f).coerceIn(0f, 1f)
        val valHandleX = lineStartX + valPct * lineWidth
        val barColor = if (mixer.crossfade.baseValue < 0f) colorA else colorB
        if (kotlin.math.abs(valHandleX - centerX) > 0.5f) {
            dl.addLine(centerX, centerY, valHandleX, centerY, barColor, 3f)
        }

        // Handle
        val handleW = 6f
        val handleH = 16f
        val handleBgCol = if (isTrackActive) ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f) else ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f)
        val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
        dl.addRectFilled(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
        dl.addRect(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

        // Hover / Active border
        if (isTarget) {
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, ImGui.colorConvertFloat4ToU32(0f, 0.8f, 1f, 1f), 4f, 0, 1.5f)
        } else if (isTrackHovered || isTrackActive) {
            val borderCol = if (isTrackActive) ImGui.colorConvertFloat4ToU32(0.0f, 0.85f, 1.0f, 1.0f) else ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f)
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, borderCol, 4f, 0, 1.5f)
        }

        // Dynamic modulated value indicator (Amber Gold dot)
        val hasModulators = mixer.crossfade.modulators.any { !it.bypassed }
        if (hasModulators || mixer.isAutoFading) {
            val livePct = ((mixer.crossfade.value - (-1f)) / 2f).coerceIn(0f, 1f)
            val liveX = lineStartX + livePct * lineWidth
            val dotR = 4f
            val curDotCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 1.0f)
            dl.addCircleFilled(liveX, centerY, dotR, curDotCol)
            dl.addCircle(liveX, centerY, dotR + 0.5f, ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f), 12, 1.0f)
        }

        ImGui.sameLine(0f, gap)

        // 3. Deck B Snap Badge [ B ]
        val badgeBX = lineEndX + gap
        val badgeBY = headerY
        val snapBKey = "Global/snapDeckB"
        val isMidiLearnSnapB = session.parametersState.isMidiTargetLearning(snapBKey)
        val snapBMapping = session.midiMappingManager.getMappingForParameter(snapBKey)
        val snapBMidiText = snapBMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val badgeBorderColorB = if (isMidiLearnSnapB) ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f) else colorB
        dl.addRectFilled(badgeBX, badgeBY, badgeBX + badgeBW, badgeBY + headerH, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
        dl.addRect(badgeBX, badgeBY, badgeBX + badgeBW, badgeBY + headerH, badgeBorderColorB, 4f, 0, if (isMidiLearnSnapB) 2f else 1.5f)

        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            val textSz = ImGui.calcTextSize("B")
            dl.addText(badgeBX + (badgeBW - textSz.x) * 0.5f, badgeBY + (headerH - textSz.y) * 0.5f, colorB, "B")
        }
        ImGui.invisibleButton("##perf_crossfade_deck_b", badgeBW, headerH)
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
        }
        if (ImGui.isItemClicked(0)) {
            mixer.onCrossfadeManualTakeover()
            mixer.crossfade.set(1.0f)
        }
        if (ImGui.beginPopupContextItem("perf_snap_b_ctx")) {
            ImGui.textDisabled("Snap Deck B")
            ImGui.separator()
            if (ImGui.menuItem("Snap Crossfader to Deck B")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(1.0f)
            }
            ImGui.separator()
            if (isMidiLearnSnapB) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Snap Deck B)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(snapBKey))
                }
            }
            if (snapBMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(snapBKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Deck B (Click to snap crossfader to Deck B)$snapBMidiText\nRight-click for MIDI Learn.")

        ImGui.sameLine(0f, gap * 2f)

        // 4. Auto-Fade Button [ AUTO ]
        val autoFadeKey = "Global/autoFade"
        val isMidiLearnAutoFade = session.parametersState.isMidiTargetLearning(autoFadeKey)
        val autoFadeMapping = session.midiMappingManager.getMappingForParameter(autoFadeKey)
        val autoFadeMidiText = autoFadeMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val autoX = ImGui.getCursorScreenPosX()
        val autoY = ImGui.getCursorScreenPosY()
        val isAuto = mixer.isAutoFading
        if (isAuto) {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.9f, 0.6f, 0.1f, 0.9f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(1.0f, 0.7f, 0.2f, 1.0f))
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.24f, 0.28f, 0.35f, 1f))
        }
        val autoLabel = if (isAuto) "FADING" else "AUTO"
        if (ImGui.button("$autoLabel##perf_autofade_btn", autoBtnW, headerH)) {
            if (mixer.isAutoFading) {
                mixer.onCrossfadeManualTakeover()
            } else {
                val targetIsA = mixer.crossfade.baseValue > 0.0f
                mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
                mixer.isAutoFading = true
                mixer.muteCrossfadeNonMidiCv()
            }
        }
        ImGui.popStyleColor(2)
        if (isMidiLearnAutoFade) {
            dl.addRect(autoX - 1f, autoY - 1f, autoX + autoBtnW + 1f, autoY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_autofade_ctx")) {
            ImGui.textDisabled("Crossfader Auto-Fade")
            ImGui.separator()
            if (ImGui.menuItem(if (mixer.isAutoFading) "Stop Auto-Fade" else "Start Auto-Fade")) {
                if (mixer.isAutoFading) {
                    mixer.onCrossfadeManualTakeover()
                } else {
                    val targetIsA = mixer.crossfade.baseValue > 0.0f
                    mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
                    mixer.isAutoFading = true
                    mixer.muteCrossfadeNonMidiCv()
                }
            }
            ImGui.separator()
            if (isMidiLearnAutoFade) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Auto-Fade)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(autoFadeKey))
                }
            }
            if (autoFadeMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(autoFadeKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Auto-fade between Deck A and Deck B over ${String.format(java.util.Locale.US, "%.1f", mixer.xfadeSpeed.value)}s.$autoFadeMidiText\nClick while fading to stop. Right-click for MIDI Learn.")

        ImGui.sameLine(0f, gap)

        // 5. Fade Speed Widget [ N.Ns ]
        val xfadeSpeedParamKey = "Mixer/xfadeSpeed"
        val isMidiLearnSpeed = session.parametersState.isMidiTargetLearning(xfadeSpeedParamKey)
        val isOscLearnSpeed = OscLearnState.isTargetLearning(xfadeSpeedParamKey)
        val speedMidiMapping = session.midiMappingManager.getMappingForParameter(xfadeSpeedParamKey)
        val speedMidiText = speedMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val speedX = ImGui.getCursorScreenPosX()
        val speedY = ImGui.getCursorScreenPosY()
        val speedStr = "${String.format(java.util.Locale.US, "%.1f", mixer.xfadeSpeed.baseValue)}s"

        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.90f))
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.20f, 0.24f, 0.32f, 1f))
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.button("$speedStr##perf_speed_badge", speedBtnW, headerH)
        }
        ImGui.popStyleColor(2)

        val isSpeedHovered = ImGui.isItemHovered()
        val isSpeedActive = ImGui.isItemActive()
        if (isSpeedActive) {
            val dragDelta = ImGui.getIO().mouseDelta.x - ImGui.getIO().mouseDelta.y
            if (dragDelta != 0f) {
                val step = if (ImGui.getIO().keyShift) 0.02f else 0.1f
                mixer.xfadeSpeed.baseValue = (mixer.xfadeSpeed.baseValue + dragDelta * step).coerceIn(0.1f, 30.0f)
            }
        }
        if (isSpeedHovered && ImGui.getIO().mouseWheel != 0f) {
            val step = if (ImGui.getIO().keyShift) 0.05f else 0.2f
            mixer.xfadeSpeed.baseValue = (mixer.xfadeSpeed.baseValue + ImGui.getIO().mouseWheel * step).coerceIn(0.1f, 30.0f)
            ImGui.getIO().mouseWheel = 0f
        }

        if (isMidiLearnSpeed) {
            dl.addRect(speedX - 1f, speedY - 1f, speedX + speedBtnW + 1f, speedY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_speed_ctx")) {
            ImGui.textDisabled("Auto-Fade Duration ($speedStr)")
            ImGui.separator()
            listOf(0.5f, 1.0f, 2.0f, 4.0f, 8.0f).forEach { s ->
                if (ImGui.menuItem("${s}s", "", kotlin.math.abs(mixer.xfadeSpeed.baseValue - s) < 0.05f)) {
                    mixer.xfadeSpeed.baseValue = s
                }
            }
            ImGui.separator()
            if (isMidiLearnSpeed) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Fade Speed)")) {
                    session.parametersState.startMidiLearn(
                        MidiLearnTarget.BaseValueSlider(
                            paramKey = xfadeSpeedParamKey,
                            label = "Fade Speed",
                            param = mixer.xfadeSpeed,
                            min = 0.1f,
                            max = 15.0f
                        )
                    )
                }
            }
            if (speedMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(xfadeSpeedParamKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnSpeed) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Fade Speed)")) {
                    OscLearnState.startLearn(xfadeSpeedParamKey, 0.1f, 15.0f, "Fade Speed")
                }
            }
            val oscAddress = OscMappingManager.getMappings().entries.find { it.value.parameterPath == xfadeSpeedParamKey }?.key
            if (oscAddress != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping ($oscAddress)")) {
                    OscMappingManager.removeMapping(oscAddress)
                    OscMappingManager.saveActiveProfile()
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Auto-fade duration: $speedStr$speedMidiText\nDrag or scroll to adjust speed.\nRight-click for quick presets & MIDI/OSC Learn.")

        ImGui.sameLine(0f, gap)

        // 6. Transition Picker Button [ Settings Icon + Name * ]
        val transName = mixer.transitionFilter?.displayName ?: "Default Blend"
        val isTransModified = mixer.transitionFilter?.let { filter ->
            filter.dryWet.baseValue != 1.0f ||
                filter.parameters.any { (name, param) ->
                    val defaultVal = filter.header.INPUTS.find { it.NAME == name }?.DEFAULT?.toString()?.toFloatOrNull() ?: 0.0f
                    kotlin.math.abs(param.baseValue - defaultVal) > 0.001f || param.modulators.any { !it.bypassed }
                }
        } ?: false
        val modBadge = if (isTransModified) " *" else ""

        if (ImGui.button("${Icons.SETTINGS} $transName$modBadge##perf_trans_picker_btn", transBtnW, headerH)) {
            ShaderPickerPopup.show("Select Mixer Transition", ShaderPickerPopup.PickerType.MIXER_TRANSITION) { id ->
                mixer.setTransition(id)
            }
        }
        itemTooltip("Select ISF transition shader or blend mode.\nActive: $transName$modBadge")

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

        ImGui.sameLine(0f, gap)

        // 6. Transition Queue Navigation [ < ] [ N/Total ] [ > ]
        val transQ = TransitionQueueManager.queue
        val transQIdx = TransitionQueueManager.activeIndex
        val transCountStr = if (transQ.isNotEmpty() && transQIdx in transQ.indices) "${transQIdx + 1}/${transQ.size}" else if (transQ.isNotEmpty()) "-/${transQ.size}" else "--"

        val transQPrevKey = "Global/transQueuePrev"
        val transQPrevOscKey = "Mixer/transQueuePrev"
        val isMidiLearnTransQPrev = session.parametersState.isMidiTargetLearning(transQPrevKey)
        val isOscLearnTransQPrev = OscLearnState.isTargetLearning(transQPrevOscKey)
        val transQPrevMidiMapping = session.midiMappingManager.getMappingForParameter(transQPrevKey)
        val transQPrevMidiText = transQPrevMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val transPrevX = ImGui.getCursorScreenPosX()
        val transPrevY = ImGui.getCursorScreenPosY()
        if (ImGui.button("<##perf_trans_q_prev", navBtnW, headerH)) {
            TransitionQueueManager.advancePrevious(mixer)
        }
        if (isMidiLearnTransQPrev) {
            dl.addRect(transPrevX - 1f, transPrevY - 1f, transPrevX + navBtnW + 1f, transPrevY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_trans_q_prev_ctx")) {
            ImGui.textDisabled("Transition Queue Prev (<)")
            ImGui.separator()
            if (ImGui.menuItem("Trigger Previous")) {
                TransitionQueueManager.advancePrevious(mixer)
            }
            ImGui.separator()
            if (isMidiLearnTransQPrev) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Trans Queue Prev)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(transQPrevKey))
                }
            }
            if (transQPrevMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(transQPrevKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnTransQPrev) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Trans Queue Prev)")) {
                    OscLearnState.startLearn(transQPrevOscKey, 0f, 1f, "Trans Queue Prev")
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Advance to previous transition in Transition Queue.$transQPrevMidiText\nRight-click for MIDI/OSC Learn.")

        ImGui.sameLine(0f, 2f)

        val transCurX = ImGui.getCursorScreenPosX()
        val transCurY = ImGui.getCursorScreenPosY()
        val genBgCol = ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.85f)
        val genTextCol = ImGui.colorConvertFloat4ToU32(0.80f, 0.85f, 0.95f, 1f)
        dl.addRectFilled(transCurX, transCurY, transCurX + qTextW, transCurY + headerH, genBgCol, 3f)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val sz = ImGui.calcTextSize(transCountStr)
            dl.addText(transCurX + (qTextW - sz.x) * 0.5f, transCurY + (headerH - sz.y) * 0.5f, genTextCol, transCountStr)
        }
        ImGui.invisibleButton("##perf_trans_q_idx", qTextW, headerH)
        itemTooltip("Transition Queue status: $transCountStr")

        ImGui.sameLine(0f, 2f)

        val transQNextKey = "Global/transQueueNext"
        val transQNextOscKey = "Mixer/transQueueNext"
        val isMidiLearnTransQNext = session.parametersState.isMidiTargetLearning(transQNextKey)
        val isOscLearnTransQNext = OscLearnState.isTargetLearning(transQNextOscKey)
        val transQNextMidiMapping = session.midiMappingManager.getMappingForParameter(transQNextKey)
        val transQNextMidiText = transQNextMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val transNextX = ImGui.getCursorScreenPosX()
        val transNextY = ImGui.getCursorScreenPosY()
        if (ImGui.button(">##perf_trans_q_next", navBtnW, headerH)) {
            TransitionQueueManager.advanceNext(mixer)
        }
        if (isMidiLearnTransQNext) {
            dl.addRect(transNextX - 1f, transNextY - 1f, transNextX + navBtnW + 1f, transNextY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_trans_q_next_ctx")) {
            ImGui.textDisabled("Transition Queue Next (>)")
            ImGui.separator()
            if (ImGui.menuItem("Trigger Next")) {
                TransitionQueueManager.advanceNext(mixer)
            }
            ImGui.separator()
            if (isMidiLearnTransQNext) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Trans Queue Next)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(transQNextKey))
                }
            }
            if (transQNextMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(transQNextKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnTransQNext) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Trans Queue Next)")) {
                    OscLearnState.startLearn(transQNextOscKey, 0f, 1f, "Trans Queue Next")
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Advance to next transition in Transition Queue.$transQNextMidiText\nRight-click for MIDI/OSC Learn.")

        ImGui.endGroup()
    }
}

