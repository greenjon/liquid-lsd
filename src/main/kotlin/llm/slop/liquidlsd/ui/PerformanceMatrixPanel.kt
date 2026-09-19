package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Performance Mode 4×4 Macro Knob Matrix (see docs/user_guide/performance_mode.md).
 *
 * Displays 16 knobs arranged as 4 rows × 4 columns, mapped to per-deck [MacroEngine] banks
 * according to the active layout tab. Knob drag adjusts the underlying
 * [llm.slop.liquidlsd.macro.MacroControl.value] directly, and right-click arms hardware MIDI
 * Learn for that knob (the pulsing cyan ring shows an armed knob; a repeat right-click cancels).
 * There is no parameter-bind Learn or binding inspector here -- that editing (labels, bindings,
 * curves, ranges) stays in Classic mode's Column 3 MACROS tab.
 *
 * Active tab is persisted via [UITheme.performanceMatrixTab] / [AppPreferences.performanceMatrixTab].
 *
 * Knob sizing: `diameter = min(availW/4 − pad, availH/4 − labelH − pad)` so all 16 knobs and
 * their labels always fit on screen regardless of window aspect ratio.
 *
 * Each row of 4 knobs is enclosed in a rounded, accent-colored group box with a large centered
 * label (e.g. "DECK A") above it, so the current grouping is obvious at a glance.
 */
class PerformanceMatrixPanel {

    // -- Tab definitions ----------------------------------------------------------

    private enum class Tab(val label: String) {
        LIVE_QUAD("LIVE QUAD"),
        DUAL_DECKS("DUAL DECKS"),
        PREP_AND_BG("PREP & BG"),
        MASTER_AND_FX("MASTER & FX")
    }

    /**
     * Describes one row of 4 knobs: which bank to pull from, which 4-knob offset within that
     * bank (0 = knobs 0–3, 4 = knobs 4–7), and the RGB accent color for the row.
     */
    private data class RowDescriptor(
        val bankId: String,
        val knobOffset: Int,
        val accent: FloatArray
    )

    // Canonical deck colors matching BrowserDeckButtons.
    companion object {
        private val COLOR_DECK_A   = floatArrayOf(0.2f,  0.4f,  0.8f)
        private val COLOR_DECK_B   = floatArrayOf(0.8f,  0.4f,  0.2f)
        private val COLOR_DECK_BG  = floatArrayOf(0.85f, 0.65f, 0.2f)
        private val COLOR_DECK_PV  = floatArrayOf(0.2f,  0.7f,  0.5f)
        private val COLOR_TRANS    = floatArrayOf(0.7f,  0.4f,  0.9f)
        private val COLOR_MASTER   = floatArrayOf(0.9f,  0.25f, 0.35f)

        private val TAB_ROWS: Array<List<RowDescriptor>> = arrayOf(
            // LIVE QUAD: one row per deck (knobs 0–3 each)
            listOf(
                RowDescriptor(MacroEngine.DECK_A,  0, COLOR_DECK_A),
                RowDescriptor(MacroEngine.DECK_B,  0, COLOR_DECK_B),
                RowDescriptor(MacroEngine.DECK_BG, 0, COLOR_DECK_BG),
                RowDescriptor(MacroEngine.TRANS,   0, COLOR_TRANS),
            ),
            // DUAL DECKS: Deck A full (0–3, 4–7), Deck B full (0–3, 4–7)
            listOf(
                RowDescriptor(MacroEngine.DECK_A, 0, COLOR_DECK_A),
                RowDescriptor(MacroEngine.DECK_A, 4, COLOR_DECK_A),
                RowDescriptor(MacroEngine.DECK_B, 0, COLOR_DECK_B),
                RowDescriptor(MacroEngine.DECK_B, 4, COLOR_DECK_B),
            ),
            // PREP & BG: Deck PV full, Deck BG full
            listOf(
                RowDescriptor(MacroEngine.DECK_PV, 0, COLOR_DECK_PV),
                RowDescriptor(MacroEngine.DECK_PV, 4, COLOR_DECK_PV),
                RowDescriptor(MacroEngine.DECK_BG, 0, COLOR_DECK_BG),
                RowDescriptor(MacroEngine.DECK_BG, 4, COLOR_DECK_BG),
            ),
            // MASTER & FX: Transitions full, Master full
            listOf(
                RowDescriptor(MacroEngine.TRANS,  0, COLOR_TRANS),
                RowDescriptor(MacroEngine.TRANS,  4, COLOR_TRANS),
                RowDescriptor(MacroEngine.MASTER, 0, COLOR_MASTER),
                RowDescriptor(MacroEngine.MASTER, 4, COLOR_MASTER),
            ),
        )

        private val TAB_ROW_LABELS: Array<List<String>> = arrayOf(
            listOf("DECK A", "DECK B", "DECK BG", "TRANSITIONS"),
            listOf("DECK A  1-4", "DECK A  5-8", "DECK B  1-4", "DECK B  5-8"),
            listOf("DECK PV  1-4", "DECK PV  5-8", "DECK BG  1-4", "DECK BG  5-8"),
            listOf("TRANS  1-4", "TRANS  5-8", "MASTER  1-4", "MASTER  5-8"),
        )
    }

    // -- Draw ---------------------------------------------------------------------

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, parametersState: ParametersState) {
        val theme = session.uiTheme
        drawTabStrip(session, theme)
        ImGui.spacing()
        drawMatrix(session, theme, parametersState)
    }

    // -- Tab strip ----------------------------------------------------------------

    private fun drawTabStrip(session: llm.slop.liquidlsd.SessionContext, theme: UITheme) {
        val tabs = Tab.values()
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(1f)
        val gap = 4f
        val tabW = ((availW - gap * (tabs.size - 1)) / tabs.size).coerceAtLeast(1f)
        val tabH = 28f

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
            ImGui.popStyleColor(2)
        }
    }

    // -- 4x4 Knob Grid -----------------------------------------------------------

    private fun drawMatrix(session: llm.slop.liquidlsd.SessionContext, theme: UITheme, parametersState: ParametersState) {
        val tabIdx = theme.performanceMatrixTab.coerceIn(0, Tab.values().size - 1)
        val rows = TAB_ROWS[tabIdx]
        val rowLabels = TAB_ROW_LABELS[tabIdx]

        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(4f)
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)

        val gridW = availW

        // Height budget per row: group-box margin + box top border + large centered group label
        // (drawn just inside the box, under the top line) + knob diameter + per-knob caption.
        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        val groupLabelH = session.uiTheme.withFont(UITheme.FontLevel.H1) { ImGui.getTextLineHeight() }
        val boxMarginY = 3f   // gap between the row's edge and adjacent rows' boxes
        val boxLabelGap = 3f  // gap above and below the group label, inside the box
        val boxPad = 6f       // inner padding between the box border and the knobs it contains
        val rowPad = 8f
        val rowH = (availH / 4f).coerceAtLeast(1f)

        // Knob diameter: bounded by both column width and row height so all 16 always fit.
        val colW = gridW / 4f
        val diamByWidth  = (colW - rowPad).coerceAtLeast(8f)
        val reservedV = boxMarginY * 2f + boxLabelGap * 2f + groupLabelH + boxPad
        val diamByHeight = (rowH - reservedV - captionH).coerceAtLeast(8f)
        val diameter = minOf(diamByWidth, diamByHeight).coerceIn(8f, 120f)

        val gridStartX = ImGui.getCursorScreenPosX()
        val gridStartY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()

        for ((rowIdx, row) in rows.withIndex()) {
            val bank: MacroBank = MacroEngine.getBank(row.bankId) ?: MacroEngine.bankForParamPath(row.bankId)
            val rowTopY = gridStartY + rowIdx * rowH

            val boxTopY = rowTopY + boxMarginY
            val labelTopY = boxTopY + boxLabelGap
            val boxBottomY = rowTopY + rowH - boxMarginY
            val boxX1 = gridStartX + 2f
            val boxX2 = gridStartX + gridW - 2f

            // Rounded group box (faint fill + accent border) around the row's 4 knobs.
            val fillCol = ImGui.colorConvertFloat4ToU32(row.accent[0], row.accent[1], row.accent[2], 0.07f)
            val borderCol = ImGui.colorConvertFloat4ToU32(row.accent[0], row.accent[1], row.accent[2], 0.85f)
            dl.addRectFilled(boxX1, boxTopY, boxX2, boxBottomY, fillCol, 8f)
            dl.addRect(boxX1, boxTopY, boxX2, boxBottomY, borderCol, 8f, 0, 2f)

            // Large centered group label just under the box's top border. Pushes an explicit
            // nonzero size rather than going through UITheme.withFont(H1) (which passes 0f for
            // "native baked size") -- on this draw-list addText path, 0f renders H1 no bigger
            // than H3, so the size is requested explicitly to get the real 22px glyphs.
            run {
                val font = session.uiTheme.fontFor(UITheme.FontLevel.H1)
                val pushed = font != null && font.ptr != 0L
                if (pushed) ImGui.pushFont(font, UITheme.FONT_H1)
                val label = rowLabels[rowIdx]
                val textW = ImGui.calcTextSize(label).x
                val textX = gridStartX + (gridW - textW) / 2f
                dl.addText(textX, labelTopY, borderCol, label)
                if (pushed) ImGui.popFont()
            }

            val contentTopY = labelTopY + groupLabelH + boxLabelGap
            val contentBottomY = boxBottomY - boxPad
            val contentCenterY = contentTopY + (contentBottomY - contentTopY) / 2f
            val knobTopY = contentCenterY - diameter / 2f - captionH / 2f

            // 4 knobs for this row.
            for (col in 0 until 4) {
                val knobIdx = row.knobOffset + col
                val control = bank.knobs.getOrNull(knobIdx) ?: continue

                val cellCenterX = gridStartX + col * colW + colW / 2f

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
                    bindings = emptyList(),
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

        // Advance the ImGui cursor past the grid so the window scrollbar is correct.
        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), gridStartY + availH)
        ImGui.dummy(0f, 0f)
    }
}
