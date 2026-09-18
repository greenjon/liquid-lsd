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
 * according to the active layout tab. This panel is **read-only** from a macro-editing
 * perspective: knob drag adjusts the underlying [llm.slop.liquidlsd.macro.MacroControl.value]
 * directly, but there is no Learn Mode, no binding inspector, and no right-click action.
 * All macro editing (labels, bindings, curves, ranges) is done in Classic mode's Column 3
 * MACROS tab.
 *
 * Active tab is persisted via [UITheme.performanceMatrixTab] / [AppPreferences.performanceMatrixTab].
 *
 * Knob sizing: `diameter = min(availW/4 − pad, availH/4 − labelH − pad)` so all 16 knobs and
 * their labels always fit on screen regardless of window aspect ratio.
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

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        val theme = session.uiTheme
        drawTabStrip(session, theme)
        ImGui.spacing()
        drawMatrix(session, theme)
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

    private fun drawMatrix(session: llm.slop.liquidlsd.SessionContext, theme: UITheme) {
        val tabIdx = theme.performanceMatrixTab.coerceIn(0, Tab.values().size - 1)
        val rows = TAB_ROWS[tabIdx]
        val rowLabels = TAB_ROW_LABELS[tabIdx]

        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(4f)
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(4f)

        // Reserve a thin left-edge colored bar per row.
        val rowHeaderW = 6f
        val gridW = availW - rowHeaderW

        // Height budget per row: divide available height equally across 4 rows.
        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        val rowPad = 8f
        val rowH = (availH / 4f).coerceAtLeast(1f)

        // Knob diameter: bounded by both column width and row height so all 16 always fit.
        val colW = gridW / 4f
        val diamByWidth  = (colW - rowPad).coerceAtLeast(8f)
        val diamByHeight = (rowH - captionH - rowPad).coerceAtLeast(8f)
        val diameter = minOf(diamByWidth, diamByHeight).coerceIn(8f, 120f)

        val gridStartX = ImGui.getCursorScreenPosX() + rowHeaderW
        val gridStartY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()

        for ((rowIdx, row) in rows.withIndex()) {
            val bank: MacroBank = MacroEngine.getBank(row.bankId) ?: MacroEngine.bankForParamPath(row.bankId)
            val rowTopY = gridStartY + rowIdx * rowH
            val rowCenterY = rowTopY + rowH / 2f

            // Colored left-edge row header bar.
            val barX = ImGui.getCursorScreenPosX()
            val barCol = ImGui.colorConvertFloat4ToU32(row.accent[0], row.accent[1], row.accent[2], 0.75f)
            dl.addRectFilled(barX, rowTopY + 2f, barX + rowHeaderW - 1f, rowTopY + rowH - 2f, barCol, 2f)

            // Faint row label above the first knob.
            val labelCol = ImGui.colorConvertFloat4ToU32(row.accent[0], row.accent[1], row.accent[2], 0.55f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                dl.addText(gridStartX + 2f, rowTopY + 2f, labelCol, rowLabels[rowIdx])
            }

            // 4 knobs for this row.
            for (col in 0 until 4) {
                val knobIdx = row.knobOffset + col
                val control = bank.knobs.getOrNull(knobIdx) ?: continue

                val cellCenterX = gridStartX + col * colW + colW / 2f
                val knobTopY = rowCenterY - diameter / 2f - captionH / 2f

                ImGui.setCursorScreenPos(cellCenterX - diameter / 2f, knobTopY)

                MacroKnobWidget.draw(
                    session = session,
                    id = "perf_${tabIdx}_r${rowIdx}_c${col}",
                    label = control.label.ifEmpty { "K${knobIdx + 1}" },
                    value = control.value,
                    diameter = diameter,
                    defaultValue = 0.5f,
                    pixelsForFullSweep = 200f,
                    isSelected = false,
                    isLearning = false,
                    accentColor = row.accent,
                    bindings = emptyList(),
                    onSelect = {},
                    onToggleLearn = {},
                    onChanged = { newVal -> control.value = newVal }
                )
            }
        }

        // Advance the ImGui cursor past the grid so the window scrollbar is correct.
        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), gridStartY + availH)
        ImGui.dummy(0f, 0f)
    }
}
