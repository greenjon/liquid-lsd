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
 * Displays up to 16 knobs arranged in rows of 4 columns across 4 tabs ([LIVE QUAD],
 * [MASTER & FX], [LIVE CONSOLE], and [ALL FX]), mapped to canonical [MacroEngine] banks
 * according to the active layout tab. Knob drag adjusts the underlying
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
        LIVE_QUAD("LIVE QUAD", "One row per deck (Deck A / Deck B / Deck BG / Deck PV), knobs 1-4 each."),
        MASTER_AND_FX("MASTER & FX", "Master (composite alphas + crossfader), Transitions (picker + queue), FX wet/dry, and Deck PV."),
        LIVE_CONSOLE("LIVE CONSOLE", "Deck A / Deck B / Deck BG / focused FX -- a single 4x4 surface for live shows."),
        ALL_FX("ALL FX", "4 rows of FX macros: Deck A, Deck B, Deck BG, and Master FX (16 knobs total).")
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
        val hasExtraHeader: Boolean = false,
        /** When false, the modular rack disclosure chevron and collapse controls are omitted. */
        val canExpand: Boolean = true
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

        /** Uniform height of every row-side control (buttons, badges, preset combo) left/right of the knobs. */
        private const val CTRL_H = 24f

        /**
         * Floor on grid row height. Below this the knobs get too small to use, so instead of
         * shrinking further the grid keeps this height and its child region scrolls vertically.
         * Meant to match the row height with the Library dock at half height (~56px knobs).
         */
        private const val MIN_ROW_H = 96f

        private val TAB_ROWS: Array<List<RowDescriptor>> = arrayOf(
            // LIVE QUAD: one row per deck (knobs 0–3 each: Deck A, Deck B, Deck BG, Deck PV)
            listOf(
                RowDescriptor(MacroEngine.DECK_A,  0, COLOR_DECK_A,  "DECK A",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_B,  0, COLOR_DECK_B,  "DECK B",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_BG, 0, COLOR_DECK_BG, "DECK BG", hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_PV, 0, COLOR_DECK_PV, "DECK PV", hasExtraHeader = true),
            ),
            // MASTER & FX: Master (composite alphas + crossfader/crossfader-time), Transitions
            // (transition picker + queue nav), FX Wet/Dry, Deck PV (1 row of 4 knobs each, except
            // Master/Transitions which also reserve header space -- see drawMatrix).
            listOf(
                RowDescriptor(MacroEngine.MASTER,    0, COLOR_MASTER, "MASTER", hasExtraHeader = true),
                RowDescriptor(MacroEngine.TRANS,     0, COLOR_TRANS,  "TRANSITIONS", hasExtraHeader = true),
                RowDescriptor(MacroEngine.FX_SENDS,  0, COLOR_FX,     "FX WET/DRY", hasExtraHeader = true, canExpand = false),
                RowDescriptor(MacroEngine.DECK_PV,   0, COLOR_DECK_PV, "DECK PV", hasExtraHeader = true),
            ),
            // LIVE CONSOLE: Deck A, Deck B, Deck BG, focused FX target (A, B, BG, PV, MST)
            listOf(
                RowDescriptor(MacroEngine.DECK_A,    0, COLOR_DECK_A,  "DECK A",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_B,    0, COLOR_DECK_B,  "DECK B",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_BG,   0, COLOR_DECK_BG, "DECK BG", hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_A_FX, 0, COLOR_FX,      "FX",      hasExtraHeader = true),
            ),
            // ALL FX: Deck A FX, Deck B FX, Deck BG FX, Deck PV FX, Master FX (1 row each, knobs 0–3)
            listOf(
                RowDescriptor(MacroEngine.DECK_A_FX,  0, COLOR_DECK_A,  "DECK A FX",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_B_FX,  0, COLOR_DECK_B,  "DECK B FX",  hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_BG_FX, 0, COLOR_DECK_BG, "DECK BG FX", hasExtraHeader = true),
                RowDescriptor(MacroEngine.DECK_PV_FX, 0, COLOR_DECK_PV, "DECK PV FX", hasExtraHeader = true),
                RowDescriptor(MacroEngine.MASTER_FX,  0, COLOR_MASTER,  "MASTER FX",  hasExtraHeader = true),
            ),
        )
    }

    /** LIVE_CONSOLE: which target ("A", "B", "BG", "PV", "MST") Row 3 is currently focused on. */
    private var focusedFxTarget: String = "A"

    /** Per-deck row mode: "SRC" (visual source generator macros) or "FX" (deck FX chain macros). */
    private val deckRowMode = mutableMapOf<String, String>()

    private fun targetBankIdFor(target: String): String = when (target) {
        "A" -> MacroEngine.DECK_A_FX
        "B" -> MacroEngine.DECK_B_FX
        "BG" -> MacroEngine.DECK_BG_FX
        "PV" -> MacroEngine.DECK_PV_FX
        else -> MacroEngine.MASTER_FX
    }

    private fun targetAccentFor(target: String): FloatArray = when (target) {
        "A" -> COLOR_DECK_A
        "B" -> COLOR_DECK_B
        "BG" -> COLOR_DECK_BG
        "PV" -> COLOR_DECK_PV
        else -> COLOR_MASTER
    }

    private fun targetDisplayName(target: String): String = when (target) {
        "A" -> "Deck A"
        "B" -> "Deck B"
        "BG" -> "Deck BG"
        "PV" -> "Deck PV"
        else -> "Master"
    }

    private fun resolveFxChain(mixer: Mixer, bankId: String): llm.slop.liquidlsd.rendering.FxChain = when (bankId) {
        MacroEngine.DECK_A_FX -> mixer.deckA.fxChain
        MacroEngine.DECK_B_FX -> mixer.deckB.fxChain
        MacroEngine.DECK_BG_FX -> mixer.deckBG.fxChain
        MacroEngine.DECK_PV_FX -> mixer.deckPV.fxChain
        MacroEngine.MASTER_FX -> mixer.masterFxBank.activeChain
        MacroEngine.FX_BANK_1 -> mixer.fxBank1.activeChain
        MacroEngine.FX_BANK_2 -> mixer.fxBank2.activeChain
        else -> mixer.deckA.fxChain
    }

    private fun resolveFxChainLabel(bankId: String): String = when (bankId) {
        MacroEngine.DECK_A_FX -> "Deck A"
        MacroEngine.DECK_B_FX -> "Deck B"
        MacroEngine.DECK_BG_FX -> "Deck BG"
        MacroEngine.DECK_PV_FX -> "Deck PV"
        MacroEngine.MASTER_FX -> "Master"
        MacroEngine.FX_BANK_1 -> "FX1"
        MacroEngine.FX_BANK_2 -> "FX2"
        else -> "Deck A"
    }

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
            drawRackBay(session, mixer, parametersState, bayH)
        }
    }

    /** Maps sub-modules (DECK_A_FX, MASTER_FX, TRANS, etc.) to their canonical primary module id (DECK_A, MASTER, etc.). */
    private fun canonicalModuleId(moduleId: String): String = when (moduleId) {
        MacroEngine.DECK_A, MacroEngine.DECK_A_FX -> MacroEngine.DECK_A
        MacroEngine.DECK_B, MacroEngine.DECK_B_FX -> MacroEngine.DECK_B
        MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX -> MacroEngine.DECK_BG
        MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX -> MacroEngine.DECK_PV
        MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.MASTER_FX, "Mixer" -> MacroEngine.MASTER
        else -> moduleId
    }

    /** Resolves the active 4-knob row descriptor for a specific module id based on the current subtab/mode. */
    private fun rowDescriptorForModule(moduleId: String, parametersState: ParametersState): RowDescriptor {
        return when (moduleId) {
            MacroEngine.DECK_A, MacroEngine.DECK_A_FX -> {
                val isFx = deckRowMode["A"] == "FX" || parametersState.activeDeckASubTab == "FX"
                if (isFx) {
                    RowDescriptor(MacroEngine.DECK_A_FX, 0, COLOR_DECK_A, "DECK A (FX)", hasExtraHeader = true)
                } else {
                    RowDescriptor(MacroEngine.DECK_A, 0, COLOR_DECK_A, "DECK A", hasExtraHeader = true)
                }
            }
            MacroEngine.DECK_B, MacroEngine.DECK_B_FX -> {
                val isFx = deckRowMode["B"] == "FX" || parametersState.activeDeckBSubTab == "FX"
                if (isFx) {
                    RowDescriptor(MacroEngine.DECK_B_FX, 0, COLOR_DECK_B, "DECK B (FX)", hasExtraHeader = true)
                } else {
                    RowDescriptor(MacroEngine.DECK_B, 0, COLOR_DECK_B, "DECK B", hasExtraHeader = true)
                }
            }
            MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX -> {
                val isFx = deckRowMode["BG"] == "FX" || parametersState.activeDeckBGSubTab == "FX"
                if (isFx) {
                    RowDescriptor(MacroEngine.DECK_BG_FX, 0, COLOR_DECK_BG, "DECK BG (FX)", hasExtraHeader = true)
                } else {
                    RowDescriptor(MacroEngine.DECK_BG, 0, COLOR_DECK_BG, "DECK BG", hasExtraHeader = true)
                }
            }
            MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX -> {
                val isFx = deckRowMode["PV"] == "FX" || parametersState.activeDeckPVSubTab == "FX"
                if (isFx) {
                    RowDescriptor(MacroEngine.DECK_PV_FX, 0, COLOR_DECK_PV, "DECK PV (FX)", hasExtraHeader = true)
                } else {
                    RowDescriptor(MacroEngine.DECK_PV, 0, COLOR_DECK_PV, "DECK PV", hasExtraHeader = true)
                }
            }
            MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.MASTER_FX, "Mixer" -> {
                when (parametersState.activeMixerSubTab) {
                    "FX" -> RowDescriptor(MacroEngine.MASTER_FX, 0, COLOR_MASTER, "MASTER FX", hasExtraHeader = true)
                    "TRANS" -> RowDescriptor(MacroEngine.TRANS, 0, COLOR_TRANS, "TRANSITIONS", hasExtraHeader = true)
                    else -> RowDescriptor(MacroEngine.MASTER, 0, COLOR_MASTER, "MASTER", hasExtraHeader = true)
                }
            }
            "FX" -> {
                val targetBankId = targetBankIdFor(focusedFxTarget)
                val targetAccent = targetAccentFor(focusedFxTarget)
                RowDescriptor(targetBankId, 0, targetAccent, "FX: ${targetDisplayName(focusedFxTarget)}", hasExtraHeader = true)
            }
            else -> RowDescriptor(moduleId, 0, COLOR_MASTER, rackModuleDisplayLabel(moduleId), hasExtraHeader = true)
        }
    }

    /** This tab's rows with the LIVE_CONSOLE FX row's bankId substituted for whichever target is currently focused, plus per-deck [SRC|FX] toggles. */
    private fun substitutedRowsForTab(tabIdx: Int, parametersState: ParametersState? = null): List<RowDescriptor> {
        val templateRows = TAB_ROWS[tabIdx]
        return templateRows.map { row ->
            when {
                tabIdx == Tab.LIVE_CONSOLE.ordinal && row.hasExtraHeader && (row.groupLabel.startsWith("FX") || row.bankId == MacroEngine.FX_BANK_1 || row.bankId == MacroEngine.DECK_A_FX) -> {
                    val targetBankId = targetBankIdFor(focusedFxTarget)
                    val targetAccent = targetAccentFor(focusedFxTarget)
                    row.copy(bankId = targetBankId, accent = targetAccent, groupLabel = "FX: ${targetDisplayName(focusedFxTarget)}")
                }
                row.bankId == MacroEngine.DECK_A && (deckRowMode["A"] == "FX" || parametersState?.activeDeckASubTab == "FX") -> {
                    row.copy(bankId = MacroEngine.DECK_A_FX, groupLabel = "DECK A (FX)")
                }
                row.bankId == MacroEngine.DECK_B && (deckRowMode["B"] == "FX" || parametersState?.activeDeckBSubTab == "FX") -> {
                    row.copy(bankId = MacroEngine.DECK_B_FX, groupLabel = "DECK B (FX)")
                }
                row.bankId == MacroEngine.DECK_BG && (deckRowMode["BG"] == "FX" || parametersState?.activeDeckBGSubTab == "FX") -> {
                    row.copy(bankId = MacroEngine.DECK_BG_FX, groupLabel = "DECK BG (FX)")
                }
                row.bankId == MacroEngine.DECK_PV && (deckRowMode["PV"] == "FX" || parametersState?.activeDeckPVSubTab == "FX") -> {
                    row.copy(bankId = MacroEngine.DECK_PV_FX, groupLabel = "DECK PV (FX)")
                }
                else -> row
            }
        }
    }

    /** Same moduleId a row's chevron uses (see the chevron-drawing block in [drawMatrix]). */
    private fun rowModuleId(row: RowDescriptor): String {
        val isFxRow = row.bankId in listOf(
            MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2, MacroEngine.MASTER_FX,
            MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX
        )
        return if (isFxRow && row.hasExtraHeader && row.groupLabel.startsWith("FX")) "FX" else row.bankId
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
        // LIVE_CONSOLE's FX row bankId is dynamic (whichever bank is focused), not baked into the
        // static table -- substituted in visibleRowsForTab rather than forking a separate
        // row-list per bank. Also hides every still-collapsed row while some module is expanded --
        // see the comment on visibleRowsForTab.
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
        val hasFxRow = rows.any {
            it.bankId in listOf(MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2, MacroEngine.MASTER_FX, MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX) ||
            it.groupLabel.startsWith("FX") ||
            tabIdx == Tab.ALL_FX.ordinal
        }

        val deckComboW = (gridW * 0.13f).coerceIn(100f, 150f)
        val deckLeftW = 74f + 4f + 28f + 1f + 28f + 4f + deckComboW + 4f + 24f + (if (session.uiTheme.randomizationEnabled) 28f else 0f) + 4f + 82f
        val deckRightW = 108f
        val fxLeftW = 268f
        val fxRightW = 130f

        val maxLeftW = if (hasDeckRows && hasFxRow) maxOf(deckLeftW, fxLeftW)
                       else if (hasDeckRows) deckLeftW
                       else if (hasFxRow) fxLeftW
                       else 0f
        val maxRightW = if (hasDeckRows && hasFxRow) maxOf(deckRightW, fxRightW)
                        else if (hasFxRow) fxRightW
                        else if (hasDeckRows) deckRightW
                        else 0f

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
            val isDeck = rows[group.startRow].bankId in listOf(MacroEngine.DECK_A, MacroEngine.DECK_B, MacroEngine.DECK_BG, MacroEngine.DECK_PV) || rows[group.startRow].groupLabel.startsWith("DECK")
            val isFx = rows[group.startRow].bankId in listOf(MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2, MacroEngine.MASTER_FX, MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX) || rows[group.startRow].groupLabel.startsWith("FX") || tabIdx == Tab.ALL_FX.ordinal
            val hasTopBar = rows[group.startRow].hasExtraHeader && !isDeck && !isFx
            val extraHeaderH = if (hasTopBar) EXTRA_HEADER_H + boxLabelGap else 0f
            // Title is placed to the right above UI elements, not above the central knob column.
            // Only extraHeaderH (e.g. Master/Transitions header bar) takes vertical space across the whole row.
            val contentH = groupH - boxMarginY * 2f - boxPad * 2f - extraHeaderH
            val subRowH = contentH / group.rowCount
            val knobAreaH = if (hasSubLabel) subRowH - subLabelH - subLabelGap else subRowH
            diamByHeight = minOf(diamByHeight, (knobAreaH - textBelowH).coerceAtLeast(8f))
        }
        val diameter = minOf(diamByWidth, diamByHeight).coerceIn(8f, 100f)

        val targetColW = if (maxLeftW > 0f) (diameter + 24f).coerceIn(72f, 96f) else (diameter + 28f).coerceIn(80f, 130f)
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

            val isConsoleFxRow = descriptor.hasExtraHeader && descriptor.groupLabel.startsWith("FX:")
            val isAllFxTab = tabIdx == Tab.ALL_FX.ordinal

            val isDeckA = (descriptor.bankId == MacroEngine.DECK_A || descriptor.bankId == MacroEngine.DECK_A_FX) && !isConsoleFxRow && !isAllFxTab
            val isDeckB = (descriptor.bankId == MacroEngine.DECK_B || descriptor.bankId == MacroEngine.DECK_B_FX) && !isConsoleFxRow && !isAllFxTab
            val isDeckBG = (descriptor.bankId == MacroEngine.DECK_BG || descriptor.bankId == MacroEngine.DECK_BG_FX) && !isConsoleFxRow && !isAllFxTab
            val isDeckPV = (descriptor.bankId == MacroEngine.DECK_PV || descriptor.bankId == MacroEngine.DECK_PV_FX) && !isConsoleFxRow && !isAllFxTab
            val isDeckRow = isDeckA || isDeckB || isDeckBG || isDeckPV
            val isFxChainRow = !isDeckRow && (isConsoleFxRow || isAllFxTab || descriptor.bankId == MacroEngine.MASTER_FX || descriptor.bankId in listOf(MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2))

            val isTransRow = descriptor.bankId == MacroEngine.TRANS
            val isMasterRow = descriptor.bankId == MacroEngine.MASTER
            val displayLabel = descriptor.groupLabel

            val rawModuleId = rowModuleId(descriptor)
            val canonicalId = canonicalModuleId(rawModuleId)
            val isModuleExpanded = parametersState.disclosureFor(canonicalId) != ParametersState.DisclosureLevel.COLLAPSED ||
                                   parametersState.disclosureFor(rawModuleId) != ParametersState.DisclosureLevel.COLLAPSED
            val activeModuleId = if (parametersState.disclosureFor(canonicalId) != ParametersState.DisclosureLevel.COLLAPSED) canonicalId else rawModuleId
            val moduleId = activeModuleId
            val chevronSize = groupLabelH.coerceIn(16f, 22f)

            // The Master and Transitions rows each reserve a header-controls bar at the top of the
            // box (crossfader/crossfader-time on Master, transition picker/queue nav on
            // Transitions), so their titles stay there too; every other row's title sits to the
            // left of its knobs, top-aligned with them, so its Y is derived from the same knob-top
            // geometry the row loop computes below.
            val isSpecialHeaderRow = descriptor.hasExtraHeader && (isTransRow || isMasterRow)
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
                                session.presetRepository.loadFxChainAsync(file).thenAccept { chainDto ->
                                    targetDeck.fxChain.applyFxChain(chainDto)
                                    val bankId = when {
                                        isDeckA -> MacroEngine.DECK_A_FX
                                        isDeckB -> MacroEngine.DECK_B_FX
                                        isDeckBG -> MacroEngine.DECK_BG_FX
                                        else -> MacroEngine.DECK_PV_FX
                                    }
                                    llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(bankId, "Deck $dropTag FX", targetDeck.fxChain)
                                }
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
                } else if (isFxChainRow) {
                    ImGui.setCursorScreenPos(boxX1, boxTopY)
                    ImGui.setNextItemAllowOverlap()
                    ImGui.invisibleButton("##perf_fx_drop_${group.startRow}_${descriptor.bankId}", boxX2 - boxX1, dropAreaH.coerceAtLeast(1f))
                    applyDragScroll()
                    if (ImGui.beginDragDropTarget()) {
                        val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                        if (payload != null) {
                            val file = java.io.File(payload)
                            if (file.exists() && file.extension.lowercase() == "lsdfxchain") {
                                val chain = resolveFxChain(mixer, descriptor.bankId)
                                session.presetRepository.loadFxChainAsync(file).thenAccept { chainDto ->
                                    chain.applyFxChain(chainDto)
                                    val chainLabel = resolveFxChainLabel(descriptor.bankId)
                                    when (descriptor.bankId) {
                                        MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX ->
                                            llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(descriptor.bankId, chainLabel, chain)
                                        MacroEngine.MASTER_FX ->
                                            llm.slop.liquidlsd.macro.FxMacroSync.syncChain(descriptor.bankId, chainLabel, chain, mixer.masterFxBank.activeChainIndex)
                                        else ->
                                            llm.slop.liquidlsd.macro.FxMacroSync.syncChain(descriptor.bankId, chainLabel, chain, 0)
                                    }
                                }
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
                drawMasterHeaderControls(session, mixer, parametersState, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
                afterTitleY + EXTRA_HEADER_H + boxLabelGap
            } else if (descriptor.hasExtraHeader && isTransRow) {
                drawTransitionsHeaderControls(session, mixer, boxX1, boxX2, afterTitleY, EXTRA_HEADER_H)
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

                val knobAreaCenterY = knobAreaTopY + (subBottomY - knobAreaTopY) / 2f
                val knobTopYCentered = knobAreaCenterY - diameter / 2f - textBelowH / 2f
                // Halfway between top-anchored and fully centered -- halves the dead space above the
                // knob (most visible when a module is expanded and its row fills the whole panel height)
                // without pushing the Val/Learn content below it past the box.
                val knobTopY = (knobAreaTopY + knobTopYCentered) / 2f
                val knobCenterY = knobTopY + diameter / 2f
                val ctrlH = CTRL_H
                // Bottom-aligned with the knob face, so short rows push the controls down into the
                // space beside the knob caption rather than up into the row title; never above the
                // title (first sub-row only -- that's where the title sits) or below the box.
                val ctrlMinY = if (k == 0 && !isSpecialHeaderRow) titleY + groupLabelH + 2f else subTopY
                val ctrlY = (knobTopY + diameter - ctrlH)
                    .coerceAtMost(subBottomY - ctrlH)
                    .coerceAtLeast(ctrlMinY)

                if (descriptor.hasExtraHeader) {
                    when {
                        isDeckA -> {
                            drawDeckRowLeftControls(session, mixer, parametersState, "Deck A", mixer.deckA, boxX1 + pad, ctrlY, ctrlH, deckComboW)
                            drawDeckRowRightControls(session, "Deck A", mixer.deckA, boxX2 - pad - deckRightW, ctrlY, ctrlH)
                        }
                        isDeckB -> {
                            drawDeckRowLeftControls(session, mixer, parametersState, "Deck B", mixer.deckB, boxX1 + pad, ctrlY, ctrlH, deckComboW)
                            drawDeckRowRightControls(session, "Deck B", mixer.deckB, boxX2 - pad - deckRightW, ctrlY, ctrlH)
                        }
                        isDeckBG -> {
                            drawDeckRowLeftControls(session, mixer, parametersState, "Deck BG", mixer.deckBG, boxX1 + pad, ctrlY, ctrlH, deckComboW)
                            drawDeckRowRightControls(session, "Deck BG", mixer.deckBG, boxX2 - pad - deckRightW, ctrlY, ctrlH)
                        }
                        isDeckPV -> {
                            drawDeckRowLeftControls(session, mixer, parametersState, "Deck PV", mixer.deckPV, boxX1 + pad, ctrlY, ctrlH, deckComboW)
                            drawDeckRowRightControls(session, "Deck PV", mixer.deckPV, boxX2 - pad - deckRightW, ctrlY, ctrlH)
                        }
                        descriptor.bankId == MacroEngine.FX_SENDS -> {
                            drawFxSendsLeftControls(session, boxX1 + pad, ctrlY, ctrlH)
                            drawFxSendsRightControls(session, mixer, boxX2 - pad - fxRightW, ctrlY, ctrlH)
                        }
                        isConsoleFxRow -> {
                            drawFxRowLeftControls(session, mixer, parametersState, boxX1 + pad, ctrlY, ctrlH)
                            drawFxRowRightControls(session, mixer, boxX2 - pad - fxRightW, ctrlY, ctrlH, targetBankId = descriptor.bankId)
                        }
                        isAllFxTab -> {
                            drawAllFxRowLeftControls(session, mixer, descriptor.bankId, boxX1 + pad, ctrlY, ctrlH)
                            drawFxRowRightControls(session, mixer, boxX2 - pad - fxRightW, ctrlY, ctrlH, targetBankId = descriptor.bankId)
                        }
                    }
                }

                // 4 knobs for this row.
                for (col in 0 until 4) {
                    val knobIdx = row.knobOffset + col
                    val control = bank.knobs.getOrNull(knobIdx) ?: continue

                    val cellCenterX = knobClusterStartX + col * knobColW + knobColW / 2f

                    val isFxBankId = row.bankId in listOf(
                        MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX,
                        MacroEngine.MASTER_FX, MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2
                    )
                    // Clickable link icon for FX slots (any FX row or Deck row in FX mode, cols 1..3)
                    if (descriptor.hasExtraHeader && (isFxChainRow || isFxBankId) && col in 1..3) {
                        val slotIdx = col - 1
                        val chain = resolveFxChain(mixer, row.bankId)
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
                                val chainLabel = resolveFxChainLabel(row.bankId)
                                when (row.bankId) {
                                    MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX ->
                                        llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(row.bankId, chainLabel, chain)
                                    MacroEngine.MASTER_FX ->
                                        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(row.bankId, chainLabel, chain, mixer.masterFxBank.activeChainIndex)
                                    else ->
                                        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(row.bankId, chainLabel, chain, 0)
                                }
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
                                        navigateMacroPanelTo(parametersState, row.bankId)
                                        session.uiTheme.column3Mode = UITheme.Column3Mode.MACROS
                                    }
                                    ImGui.popStyleColor()
                                    itemTooltip("Arm Learn Mode and open the Mixer panel's Macros tab. Then click any parameter slider or modulator property in Column 1 or 2.")
                                } else {
                                    ImGui.textDisabled("Max 4")
                                }
                            }
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

    /**
     * Points [ParametersState.activeTopTab] (and Mixer sub-tab) at whichever tab [MacroPanel]
     * reads to display [bankId], mirroring [MacroPanel.activeBankId]'s reverse mapping -- so
     * arming Learn on a Performance-panel knob and having [UITheme.Column3Mode.MACROS] pop open
     * lands on the *same* bank's knobs rather than whatever tab Columns 1/2 last had focused.
     * FX_SENDS has no dedicated Macros-tab destination, so it's left as a no-op (Macros still
     * opens, just without a matching tab switch).
     */
    private fun navigateMacroPanelTo(parametersState: ParametersState, bankId: String) {
        when (bankId) {
            MacroEngine.DECK_A -> parametersState.activeTopTab = "Deck A"
            MacroEngine.DECK_B -> parametersState.activeTopTab = "Deck B"
            MacroEngine.DECK_BG -> parametersState.activeTopTab = "Deck BG"
            MacroEngine.DECK_PV -> parametersState.activeTopTab = "Deck PV"
            MacroEngine.DECK_A_FX -> {
                parametersState.activeTopTab = "Deck A"
                parametersState.setDeckSubTab("Deck A", "FX")
            }
            MacroEngine.DECK_B_FX -> {
                parametersState.activeTopTab = "Deck B"
                parametersState.setDeckSubTab("Deck B", "FX")
            }
            MacroEngine.DECK_BG_FX -> {
                parametersState.activeTopTab = "Deck BG"
                parametersState.setDeckSubTab("Deck BG", "FX")
            }
            MacroEngine.DECK_PV_FX -> {
                parametersState.activeTopTab = "Deck PV"
                parametersState.setDeckSubTab("Deck PV", "FX")
            }
            MacroEngine.FX_BANK_1 -> parametersState.activeTopTab = "FX1"
            MacroEngine.FX_BANK_2 -> parametersState.activeTopTab = "FX2"
            MacroEngine.MASTER_FX -> parametersState.activeTopTab = "MFX"
            MacroEngine.MASTER -> {
                parametersState.activeTopTab = "Mixer"
                parametersState.activeMixerSubTab = "CTRL"
            }
            MacroEngine.TRANS -> {
                parametersState.activeTopTab = "Mixer"
                parametersState.activeMixerSubTab = "TRANS"
            }
        }
    }

    // -- Modular Rack Deep Edit (Tier 2) -------------------------------------------------------

    /** Friendly title for a rack module id shown in the Deep-Edit region's header line. */
    private fun rackModuleDisplayLabel(moduleId: String): String = when (moduleId) {
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
        "FX" -> "FX: ${targetDisplayName(focusedFxTarget)}"
        else -> moduleId
    }

    /**
     * Scrollable region beneath the Tier-1 grid showing every module currently above COLLAPSED
     * (in Solo mode this is at most one). Never touches [focusedFxBankId] or re-runs
     * [llm.slop.liquidlsd.macro.FxMacroSync] -- expand/collapse is strictly a display detail.
     */
    private fun drawRackBay(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, parametersState: ParametersState, bayH: Float) {
        val expandedModules = parametersState.rackModuleDisclosure.entries
            .filter { it.value != ParametersState.DisclosureLevel.COLLAPSED && it.key != MacroEngine.FX_SENDS }
            .map { it.key }
        if (expandedModules.isEmpty()) return

        if (ImGui.beginChild("##rack_bay_area", 0f, bayH, true)) {
            for ((idx, moduleId) in expandedModules.withIndex()) {
                if (idx > 0) {
                    ImGui.spacing(); ImGui.separator(); ImGui.spacing()
                }
                drawRackBayModule(session, mixer, parametersState, moduleId)
            }
        }
        ImGui.endChild()
    }

    /**
     * The Deep Edit tier: the full parameter/CV editor, reusing
     * [ParametersTabs.drawDeckGroupContent]/[ParametersTabs.drawFxBankGroupContent] and
     * [PropertiesPanel.draw] verbatim (see [drawRackDeepEdit]) -- same reachable bindings as
     * Classic mode, no regressions. Macro target binding (arm Learn, inspect/edit bindings) lives
     * on the Mixer panel's MACROS tab ([MacroPanel]), not here -- pressing Learn on a Tier-1 knob
     * jumps there automatically (see [navigateMacroPanelTo]).
     */
    private fun drawRackBayModule(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, parametersState: ParametersState, moduleId: String) {
        val label = rackModuleDisplayLabel(moduleId)

        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.textColored(0.75f, 0.85f, 1f, 1f, "$label — DEEP EDIT")
        }
        ImGui.sameLine()
        if (ImGui.smallButton("Collapse##rack_bay_collapse_$moduleId")) {
            parametersState.setDisclosure(moduleId, ParametersState.DisclosureLevel.COLLAPSED)
        }
        ImGui.spacing()

        drawRackDeepEdit(session, mixer, parametersState, moduleId)
    }

    private fun deckLabelForModuleId(moduleId: String): String? = when (moduleId) {
        MacroEngine.DECK_A, MacroEngine.DECK_A_FX -> "Deck A"
        MacroEngine.DECK_B, MacroEngine.DECK_B_FX -> "Deck B"
        MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX -> "Deck BG"
        MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX -> "Deck PV"
        "FX" -> when (focusedFxTarget) {
            "A" -> "Deck A"
            "B" -> "Deck B"
            "BG" -> "Deck BG"
            "PV" -> "Deck PV"
            else -> null
        }
        else -> null
    }

    private fun deckForLabel(mixer: Mixer, deckLabel: String): Deck = when (deckLabel) {
        "Deck A" -> mixer.deckA
        "Deck B" -> mixer.deckB
        "Deck BG" -> mixer.deckBG
        else -> mixer.deckPV
    }

    /** CV columns visible in the rack Deep Edit grid -- mirrors ParametersPanel's own (private) getCvColumns. */
    private fun rackCvColumns(session: llm.slop.liquidlsd.SessionContext): List<String> {
        val cols = mutableListOf<String>()
        if (session.uiTheme.showLfoCol) cols.add("lfo")
        if (session.uiTheme.sequencerEnabled) cols.add("seq")
        if (session.uiTheme.audioEngineEnabled) cols.add("audio")
        return cols
    }

    /** VAL (+ optional MIDI) + CV columns, in display order -- mirrors ParametersPanel's own (private) getVisibleColumns. */
    private fun rackVisibleColumns(session: llm.slop.liquidlsd.SessionContext): List<String> {
        val cols = mutableListOf("value")
        if (session.uiTheme.midiEnabled) cols.add("midi")
        cols.addAll(rackCvColumns(session))
        return cols
    }

    private fun rackColumnOffset(session: llm.slop.liquidlsd.SessionContext, colId: String, metrics: GridMetrics): Float {
        val visible = rackVisibleColumns(session)
        val targetId = if (colId == "final") "value" else colId
        val index = visible.indexOf(targetId)
        if (index < 0) return 0f
        return index * (metrics.cell + metrics.cellPad)
    }

    /**
     * Tier 3 (Deep Edit): the full parameter/CV editor, laid out side-by-side like Classic mode's
     * Columns 1 & 2 -- reusing [ParametersPanel.drawColumnHeaders] (VAL/MIDI/LFO/SEQ/AUD headers,
     * which also draws the SRC/View/CTRL/TRANS Section Tabs) + [ParametersTabs.drawDeckGroupContent]
     * (decks), [ParametersTabs.drawFxBankGroupContent] (FX banks), or [ParametersTabs.drawMixerGroupContent]
     * (Transitions/Master) for the left-hand row grid, and [PropertiesPanel.draw] verbatim on the
     * right for the per-parameter CV detail (LFO/MIDI/SEQ/AUD) of whichever cell is selected --
     * identical reachable bindings to Classic mode, per the rack plan's Tier 3 requirement.
     *
     * [ParametersState.activeTopTab] and [ParametersState.selectedCell]/[ParametersState.selectedParam]
     * are shared globals (Classic mode uses them too), so they're saved, temporarily pointed at this
     * module's own state ([ParametersState.rackSelectedCell]), and restored afterward -- this keeps
     * two simultaneously open Deep Edits (Multi mode) from fighting over one shared selection, and
     * keeps Classic mode's own selection undisturbed by Rack mode edits.
     */
    private fun drawRackDeepEdit(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, parametersState: ParametersState, moduleId: String) {
        val deckLabel = deckLabelForModuleId(moduleId)
        val fxBank = when {
            moduleId == "FX" && focusedFxTarget == "MST" -> mixer.masterFxBank
            moduleId == MacroEngine.MASTER_FX -> mixer.masterFxBank
            moduleId in listOf(MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2) -> resolveFxBank(mixer, moduleId)
            else -> null
        }
        if (deckLabel != null && (moduleId.endsWith("_fx") || moduleId == "FX")) {
            parametersState.setDeckSubTab(deckLabel, "FX")
        }
        // Transitions and Master both live under the Parameters panel's "Mixer" top tab
        // (CTRL: crossfade/master level/queue nav/tap tempo; FX: master FX; TRANS: transition shader + dry/wet +
        // its own parameters) -- reuse that set of subtabs verbatim rather than reimplementing.
        val isMixerModule = moduleId == MacroEngine.TRANS || moduleId == MacroEngine.MASTER || moduleId == MacroEngine.MASTER_FX || moduleId == "Mixer"

        if (deckLabel == null && fxBank == null && !isMixerModule) {
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                ImGui.textDisabled("Deep Edit isn't available for this module yet -- use Classic mode (F4) for full control.")
            }
            return
        }

        val deck = deckLabel?.let { deckForLabel(mixer, it) }
        if (deck != null && deck.isEmpty) {
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                ImGui.textDisabled("$deckLabel is empty -- load a preset or source above to edit its parameters.")
            }
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
        var nextTopTab = parametersState.activeTopTab

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
        val labelColW = 160f
        val lastCol = rackVisibleColumns(session).last()
        val maxGridW = rackColumnOffset(session, lastCol, metrics) + metrics.cell + metrics.cellPad * 0.5f + ParametersPanel.getKebabWidth(session)
        val paramsW = labelColW + maxGridW + 24f

        val totalAvailW = ImGui.getContentRegionAvailX()
        val gap = 8f
        val propsW = (totalAvailW - sideTabWidth - gap - paramsW - gap).coerceAtLeast(280f)
        val headerH = ParametersPanel.calculateHeaderHeight(session)

        // 1. Left column: 5-channel side tabs (MIX, A, B, BG, PV)
        if (ImGui.beginChild("##rack_deep_side_tabs_$moduleId", sideTabWidth, 0f, false)) {
            ParametersTabs.drawPerformanceDeepEditSideTabs(session, parametersState, mixer, topOffset = headerH) { targetSection ->
                nextTopTab = targetSection
                parametersState.activeTopTab = targetSection
                when (targetSection) {
                    "Mixer" -> parametersState.setDisclosure(MacroEngine.MASTER, ParametersState.DisclosureLevel.DEEP_EDIT)
                    "Deck A" -> parametersState.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
                    "Deck B" -> parametersState.setDisclosure(MacroEngine.DECK_B, ParametersState.DisclosureLevel.DEEP_EDIT)
                    "Deck BG" -> parametersState.setDisclosure(MacroEngine.DECK_BG, ParametersState.DisclosureLevel.DEEP_EDIT)
                    "Deck PV" -> parametersState.setDisclosure(MacroEngine.DECK_PV, ParametersState.DisclosureLevel.DEEP_EDIT)
                }
            }
        }
        ImGui.endChild()

        ImGui.sameLine(0f, gap)

        // 2. Middle column: Parameter grid
        if (ImGui.beginChild("##rack_deep_params_$moduleId", paramsW, 0f, false)) {
            val gridStartX = ImGui.getCursorScreenPosX()

            ParametersPanel.drawColumnHeaders(session, labelColW, parametersState, mixer, metrics, headerH)

            if (deck != null) {
                ParametersTabs.drawDeckGroupContent(session, deckLabel, deck, parametersState, labelColW, mixer, gridStartX, cvColumnsFn, columnOffsetFn, colorFn, onPushUndo)
            } else if (fxBank != null) {
                ParametersTabs.drawFxBankGroupContent(session, fxBank.label, fxBank, parametersState, labelColW, mixer, gridStartX, cvColumnsFn, columnOffsetFn, colorFn, onPushUndo)
            } else if (isMixerModule) {
                ParametersTabs.drawMixerGroupContent(session, mixer, parametersState, labelColW, gridStartX, cvColumnsFn, columnOffsetFn, colorFn, onPushUndo)
            }

            ImGui.dummy(0f, 0f)
        }
        ImGui.endChild()

        ImGui.sameLine(0f, gap)

        // 3. Right column: Properties detail
        if (ImGui.beginChild("##rack_deep_props_$moduleId", propsW, 0f, true)) {
            PropertiesPanel.draw(session, parametersState, mixer)
        }
        ImGui.endChild()

        parametersState.rackSelectedCell[moduleId] = parametersState.selectedCell
        parametersState.selectedCell = savedCell
        parametersState.selectedParam = savedParam
        parametersState.activeTopTab = nextTopTab
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

    private fun drawAllFxRowLeftControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        bankId: String,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val gap = 4f
        val chain = resolveFxChain(mixer, bankId)
        val chainLabel = resolveFxChainLabel(bankId)

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        // 1. Badge with target name
        val badgeW = 76f
        val accent = targetAccentFor(when (bankId) {
            MacroEngine.DECK_A_FX -> "A"
            MacroEngine.DECK_B_FX -> "B"
            MacroEngine.DECK_BG_FX -> "BG"
            MacroEngine.DECK_PV_FX -> "PV"
            else -> "MST"
        })
        val bgCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.20f)
        val borderCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.85f)
        val textCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.95f)

        val curX = ImGui.getCursorScreenPosX()
        val curY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(curX, curY, curX + badgeW, curY + ctrlH, bgCol, 4f)
        dl.addRect(curX, curY, curX + badgeW, curY + ctrlH, borderCol, 4f, 0, 1.5f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val textSz = ImGui.calcTextSize(chainLabel)
            val tx = curX + (badgeW - textSz.x) * 0.5f
            val ty = curY + (ctrlH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(curX + 2f), ty, textCol, chainLabel)
        }
        ImGui.invisibleButton("##perf_allfx_badge_$bankId", badgeW, ctrlH)
        itemTooltip("Target: $chainLabel FX Chain")

        ImGui.sameLine(0f, gap)

        // 2. Preset dropdown button [ Chain Preset v ]
        val chainBtnW = 100f
        if (ImGui.button("Preset ${Icons.CHEVRON_DOWN}##perf_allfx_preset_$bankId", chainBtnW, ctrlH)) {
            ImGui.openPopup("perf_allfx_chain_popup_$bankId")
        }
        itemTooltip("Load a saved 3-slot FX chain (.lsdfxchain) for $chainLabel.")

        if (ImGui.beginPopup("perf_allfx_chain_popup_$bankId")) {
            ImGui.textDisabled("$chainLabel FX Chains")
            ImGui.separator()
            val chains = FileSystemManager.scanAllFxChains()
            if (chains.isEmpty()) {
                ImGui.textDisabled("No saved FX chains found")
            } else {
                for (asset in chains) {
                    if (ImGui.selectable("${asset.name}##perf_allfx_item_${bankId}_${asset.path.hashCode()}")) {
                        val file = File(asset.path)
                        session.presetRepository.loadFxChainAsync(file).thenAccept { dto ->
                            chain.applyFxChain(dto)
                            when (bankId) {
                                MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX ->
                                    llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(bankId, chainLabel, chain)
                                MacroEngine.MASTER_FX ->
                                    llm.slop.liquidlsd.macro.FxMacroSync.syncChain(bankId, chainLabel, chain, mixer.masterFxBank.activeChainIndex)
                                else ->
                                    llm.slop.liquidlsd.macro.FxMacroSync.syncChain(bankId, chainLabel, chain, 0)
                            }
                        }
                    }
                }
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.TRASH} Clear Chain")) {
                (0 until FxChain.SLOT_COUNT).forEach { chain.clearFxSlot(it) }
                when (bankId) {
                    MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX ->
                        llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(bankId, chainLabel, chain)
                    MacroEngine.MASTER_FX ->
                        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(bankId, chainLabel, chain, mixer.masterFxBank.activeChainIndex)
                    else ->
                        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(bankId, chainLabel, chain, 0)
                }
            }
            ImGui.endPopup()
        }

        ImGui.endGroup()
    }

    private fun drawFxSendsLeftControls(
        session: llm.slop.liquidlsd.SessionContext,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val badgeW = 76f
        val accent = COLOR_FX
        val bgCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.20f)
        val borderCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.85f)
        val textCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.95f)

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(startX, startY, startX + badgeW, startY + ctrlH, bgCol, 4f)
        dl.addRect(startX, startY, startX + badgeW, startY + ctrlH, borderCol, 4f, 0, 1.5f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val label = "FX SENDS"
            val textSz = ImGui.calcTextSize(label)
            val tx = startX + (badgeW - textSz.x) * 0.5f
            val ty = startY + (ctrlH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(startX + 2f), ty, textCol, label)
        }
        ImGui.invisibleButton("##perf_fxsends_badge", badgeW, ctrlH)
        itemTooltip("Master FX Wet/Dry Send Levels")

        ImGui.endGroup()
    }

    private fun drawFxSendsRightControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val resyncW = 58f

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        if (ImGui.button("Resync##perf_fxsends_resync", resyncW, ctrlH)) {
            val bank = MacroEngine.getBank(MacroEngine.FX_SENDS)
            bank?.knobs?.forEach { knob ->
                knob.value = 1.0f
            }
        }
        itemTooltip("Reset all FX Wet/Dry Send knobs to 100%.")

        ImGui.endGroup()
    }

    private fun drawFxRowLeftControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val gap = 3f

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        // Target switcher [ A ] [ B ] [ BG ] [ PV ] [ MST ]
        val targets = listOf("A", "B", "BG", "PV", "MST")
        val targetBtnW = 28f
        for ((i, target) in targets.withIndex()) {
            if (i > 0) ImGui.sameLine(0f, gap)
            val isActive = target == focusedFxTarget
            val accent = targetAccentFor(target)
            val activeCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.90f)
            val inactiveCol = ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f)
            ImGui.pushStyleColor(ImGuiCol.Button, if (isActive) activeCol else inactiveCol)
            if (ImGui.button("$target##perf_fx_target_$target", targetBtnW, ctrlH)) {
                focusedFxTarget = target
                val bankId = targetBankIdFor(target)
                val chain = resolveFxChain(mixer, bankId)
                val chainLabel = resolveFxChainLabel(bankId)
                when (bankId) {
                    MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX ->
                        llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(bankId, chainLabel, chain)
                    MacroEngine.MASTER_FX ->
                        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(bankId, chainLabel, chain, mixer.masterFxBank.activeChainIndex)
                    else ->
                        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(bankId, chainLabel, chain, 0)
                }
            }
            ImGui.popStyleColor()
        }
        itemTooltip("Focus Row 3 FX knobs on Deck A, B, BG, PV, or Master FX.")

        ImGui.sameLine(0f, 6f)

        // Preset dropdown button [ Chain Preset v ]
        val chainBtnW = 100f
        val currentBankId = targetBankIdFor(focusedFxTarget)
        val currentChain = resolveFxChain(mixer, currentBankId)
        val currentLabel = resolveFxChainLabel(currentBankId)
        if (ImGui.button("Preset ${Icons.CHEVRON_DOWN}##perf_fx_chain_preset", chainBtnW, ctrlH)) {
            ImGui.openPopup("perf_fx_chain_popup")
        }
        itemTooltip("Load a saved 3-slot FX chain (.lsdfxchain) for $currentLabel.")

        if (ImGui.beginPopup("perf_fx_chain_popup")) {
            ImGui.textDisabled("$currentLabel FX Chains")
            ImGui.separator()
            val chains = FileSystemManager.scanAllFxChains()
            if (chains.isEmpty()) {
                ImGui.textDisabled("No saved FX chains found")
            } else {
                for (asset in chains) {
                    if (ImGui.selectable("${asset.name}##perf_fx_item_${asset.path.hashCode()}")) {
                        val file = File(asset.path)
                        session.presetRepository.loadFxChainAsync(file).thenAccept { dto ->
                            currentChain.applyFxChain(dto)
                            when (currentBankId) {
                                MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX ->
                                    llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(currentBankId, currentLabel, currentChain)
                                MacroEngine.MASTER_FX ->
                                    llm.slop.liquidlsd.macro.FxMacroSync.syncChain(currentBankId, currentLabel, currentChain, mixer.masterFxBank.activeChainIndex)
                                else ->
                                    llm.slop.liquidlsd.macro.FxMacroSync.syncChain(currentBankId, currentLabel, currentChain, 0)
                            }
                        }
                    }
                }
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.TRASH} Clear Chain")) {
                (0 until FxChain.SLOT_COUNT).forEach { currentChain.clearFxSlot(it) }
                when (currentBankId) {
                    MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX ->
                        llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(currentBankId, currentLabel, currentChain)
                    MacroEngine.MASTER_FX ->
                        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(currentBankId, currentLabel, currentChain, mixer.masterFxBank.activeChainIndex)
                    else ->
                        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(currentBankId, currentLabel, currentChain, 0)
                }
            }
            ImGui.endPopup()
        }

        ImGui.endGroup()
    }

    private fun drawFxRowRightControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        startX: Float,
        startY: Float,
        ctrlH: Float,
        targetBankId: String = targetBankIdFor(focusedFxTarget)
    ) {
        val chain = resolveFxChain(mixer, targetBankId)
        val gap = 4f
        val bypassW = 68f
        val resyncW = 58f

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        // Chain-level bypass
        val isBypassed = !chain.enabled
        ImGui.pushStyleColor(ImGuiCol.Button, if (isBypassed) ImGui.colorConvertFloat4ToU32(0.6f, 0.15f, 0.15f, 1f) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
        val targetLabel = resolveFxChainLabel(targetBankId)
        if (ImGui.button((if (isBypassed) "BYPASS" else "FX ON") + "##perf_fx_bypass_$targetBankId", bypassW, ctrlH)) {
            chain.enabled = !chain.enabled
        }
        ImGui.popStyleColor()
        itemTooltip("Hard-bypasses the entire $targetLabel FX chain.")

        ImGui.sameLine(0f, gap)

        // Resync button
        if (ImGui.button("Resync##perf_fx_resync_$targetBankId", resyncW, ctrlH)) {
            when (targetBankId) {
                MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX ->
                    llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(targetBankId, targetLabel, chain, forceResync = true)
                MacroEngine.MASTER_FX ->
                    llm.slop.liquidlsd.macro.FxMacroSync.syncChain(targetBankId, targetLabel, chain, mixer.masterFxBank.activeChainIndex, forceResync = true)
                else ->
                    llm.slop.liquidlsd.macro.FxMacroSync.syncChain(targetBankId, targetLabel, chain, 0, forceResync = true)
            }
        }
        itemTooltip("Reset these 4 knobs to the Super Knob + 3 Metaknobs smart default.")

        ImGui.endGroup()
    }

    /**
     * Controls to the left of the knobs for Deck rows (Deck A, B, BG, PV).
     * Provides quick generator badge, preset selector combo, eject button, randomize die button,
     * and play queue / bg queue navigation.
     */
    private fun drawDeckRowLeftControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        deckLabel: String,
        deck: Deck,
        startX: Float,
        startY: Float,
        ctrlH: Float,
        comboW: Float
    ) {
        val gap = 4f
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

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        // 1. Generator badge
        val genBadgeW = 74f
        val genName = deck.source.displayName
        val genBorderCol = ImGui.colorConvertFloat4ToU32(0.35f, 0.40f, 0.50f, 0.70f)
        val genBgCol = ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.85f)
        val genTextCol = ImGui.colorConvertFloat4ToU32(0.80f, 0.85f, 0.95f, 1f)
        val curX = ImGui.getCursorScreenPosX()
        val curY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(curX, curY, curX + genBadgeW, curY + ctrlH, genBgCol, 4f)
        dl.addRect(curX, curY, curX + genBadgeW, curY + ctrlH, genBorderCol, 4f, 0, 1f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val textSz = ImGui.calcTextSize(genName)
            val tx = curX + (genBadgeW - textSz.x) * 0.5f
            val ty = curY + (ctrlH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(curX + 4f), ty, genTextCol, genName)
        }
        ImGui.invisibleButton("##perf_gen_badge_$tag", genBadgeW, ctrlH)
        itemTooltip("Generator: $genName ($deckLabel)")

        ImGui.sameLine(0f, gap)

        // 1b. [SRC | FX] mode toggle
        val activeSubTab = parametersState.getActiveDeckSubTabByTag(tag)
        val currentMode = if (activeSubTab == "FX") "FX" else deckRowMode.getOrDefault(tag, "SRC")
        val modeBtnW = 28f
        val isSrc = currentMode == "SRC"
        ImGui.pushStyleColor(ImGuiCol.Button, if (isSrc) ImGui.colorConvertFloat4ToU32(0.20f, 0.45f, 0.70f, 1f) else ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.7f))
        if (ImGui.button("SRC##perf_mode_src_$tag", modeBtnW, ctrlH)) {
            deckRowMode[tag] = "SRC"
            parametersState.setDeckSubTab(deckLabel, "SRC")
        }
        ImGui.popStyleColor()
        itemTooltip("Switch $deckLabel 4-knob row to Visual Generator controls.")

        ImGui.sameLine(0f, 1f)

        val isFx = currentMode == "FX"
        ImGui.pushStyleColor(ImGuiCol.Button, if (isFx) ImGui.colorConvertFloat4ToU32(0.80f, 0.40f, 0.15f, 1f) else ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.7f))
        if (ImGui.button("FX##perf_mode_fx_$tag", modeBtnW, ctrlH)) {
            deckRowMode[tag] = "FX"
            parametersState.setDeckSubTab(deckLabel, "FX")
            val chainLabel = "$deckLabel FX"
            val bankId = targetBankIdFor(tag)
            llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(bankId, chainLabel, deck.fxChain)
        }
        ImGui.popStyleColor()
        itemTooltip("Switch $deckLabel 4-knob row to dedicated Deck FX controls (Super Knob + 3 Metaknobs).")

        ImGui.sameLine(0f, gap)

        // 2. Preset dropdown combo
        if (isFx) {
            val chainBtnW = comboW
            val deckBankId = targetBankIdFor(tag)
            val deckChain = deck.fxChain
            val deckChainLabel = "$deckLabel FX"
            if (ImGui.button("Preset ${Icons.CHEVRON_DOWN}##perf_deck_fx_preset_$tag", chainBtnW, ctrlH)) {
                ImGui.openPopup("perf_deck_fx_chain_popup_$tag")
            }
            itemTooltip("Load a saved 3-slot FX chain (.lsdfxchain) for $deckChainLabel.")

            if (ImGui.beginPopup("perf_deck_fx_chain_popup_$tag")) {
                ImGui.textDisabled("$deckChainLabel Chains")
                ImGui.separator()
                val chains = FileSystemManager.scanAllFxChains()
                if (chains.isEmpty()) {
                    ImGui.textDisabled("No saved FX chains found")
                } else {
                    for (asset in chains) {
                        if (ImGui.selectable("${asset.name}##perf_deck_fx_item_${tag}_${asset.path.hashCode()}")) {
                            val file = File(asset.path)
                            session.presetRepository.loadFxChainAsync(file).thenAccept { dto ->
                                deckChain.applyFxChain(dto)
                                llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(deckBankId, deckChainLabel, deckChain)
                            }
                        }
                    }
                }
                ImGui.separator()
                if (ImGui.menuItem("${Icons.TRASH} Clear Chain")) {
                    (0 until FxChain.SLOT_COUNT).forEach { deckChain.clearFxSlot(it) }
                    llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(deckBankId, deckChainLabel, deckChain)
                }
                ImGui.endPopup()
            }
        } else {
            val activePreset = when {
                isDeckA -> session.presetManager.activePresetA
                isDeckB -> session.presetManager.activePresetB
                isDeckBG -> session.presetManager.activePresetBG
                else -> session.presetManager.activePresetPV
            }
            val isDirty = session.presetManager.isDeckDirty(deck, mixer)
            val dirtyMarker = if (isDirty) " *" else ""
            val presetDisplay = (activePreset ?: "Default") + dirtyMarker

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
            // Combos size their height from font + frame padding rather than taking one explicitly, so
            // pad them out to ctrlH to match the buttons beside them. Popped before the popup body.
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), ((ctrlH - ImGui.getFontSize()) / 2f).coerceAtLeast(0f))
            val isComboOpen = ImGui.beginCombo("##perf_preset_combo_$tag", presetDisplay)
            ImGui.popStyleVar()
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
        }

        ImGui.sameLine(0f, gap)

        // 3. Eject Button [ EJECT ]
        val iconBtnW = ctrlH
        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.45f, 0.20f, 0.20f, 1f))
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.EJECT}##perf_eject_$tag", iconBtnW, ctrlH)) {
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
                if (ImGui.button("${Icons.DICES}##perf_rand_$tag", iconBtnW, ctrlH)) {
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

        ImGui.sameLine(0f, gap)

        // 5. PlayQueue / BG Queue navigation (or preview indicator for PV)
        val navBtnW = (ctrlH * 0.85f).coerceAtLeast(20f)
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
            if (ImGui.button("<##perf_q_prev_$tag", navBtnW, ctrlH)) {
                session.playQueueManager.triggerPrevious(mixer)
            }
            if (isMidiLearnQPrev) {
                dl.addRect(qPrevX - 1f, qPrevY - 1f, qPrevX + navBtnW + 1f, qPrevY + ctrlH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
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

            val qTextW = 38f
            val qCurX = ImGui.getCursorScreenPosX()
            val qCurY = ImGui.getCursorScreenPosY()
            dl.addRectFilled(qCurX, qCurY, qCurX + qTextW, qCurY + ctrlH, genBgCol, 3f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val sz = ImGui.calcTextSize(qCountStr)
                dl.addText(qCurX + (qTextW - sz.x) * 0.5f, qCurY + (ctrlH - sz.y) * 0.5f, genTextCol, qCountStr)
            }
            ImGui.invisibleButton("##perf_q_idx_$tag", qTextW, ctrlH)
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
            if (ImGui.button(">##perf_q_next_$tag", navBtnW, ctrlH)) {
                session.playQueueManager.triggerNext(mixer)
            }
            if (isMidiLearnQNext) {
                dl.addRect(qNextX - 1f, qNextY - 1f, qNextX + navBtnW + 1f, qNextY + ctrlH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
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
            if (ImGui.button("<##perf_bg_prev", navBtnW, ctrlH)) {
                session.bgQueueManager.triggerPrevious(mixer)
            }
            if (isMidiLearnBgPrev) {
                dl.addRect(bgPrevX - 1f, bgPrevY - 1f, bgPrevX + navBtnW + 1f, bgPrevY + ctrlH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
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

            val bgQTextW = 38f
            val bgCurX = ImGui.getCursorScreenPosX()
            val bgCurY = ImGui.getCursorScreenPosY()
            dl.addRectFilled(bgCurX, bgCurY, bgCurX + bgQTextW, bgCurY + ctrlH, genBgCol, 3f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val sz = ImGui.calcTextSize(bgQCountStr)
                dl.addText(bgCurX + (bgQTextW - sz.x) * 0.5f, bgCurY + (ctrlH - sz.y) * 0.5f, genTextCol, bgQCountStr)
            }
            ImGui.invisibleButton("##perf_bg_idx", bgQTextW, ctrlH)
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
            if (ImGui.button(">##perf_bg_next", navBtnW, ctrlH)) {
                session.bgQueueManager.triggerNext(mixer)
            }
            if (isMidiLearnBgNext) {
                dl.addRect(bgNextX - 1f, bgNextY - 1f, bgNextX + navBtnW + 1f, bgNextY + ctrlH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
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
            val pvBadgeW = 60f
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.12f, 0.22f, 0.18f, 0.85f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.18f, 0.32f, 0.25f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                if (ImGui.button("PREVIEW##perf_pv_badge", pvBadgeW, ctrlH)) {
                    parametersState.activeTopTab = "Deck PV"
                }
            }
            ImGui.popStyleColor(2)
            itemTooltip("Deck PV (Preview Deck)\nClick to focus in Parameters panel.")
        }

        ImGui.endGroup()
    }

    /**
     * FX routing buttons [FX1][FX2] placed to the right of the knobs for Deck rows.
     */
    private fun drawDeckRowRightControls(
        session: llm.slop.liquidlsd.SessionContext,
        deckLabel: String,
        deck: Deck,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val gap = 4f
        val isDeckA = deckLabel.endsWith("A")
        val isDeckB = deckLabel.endsWith("B")
        val isDeckBG = deckLabel.endsWith("BG")
        val tag = when {
            isDeckA -> "A"
            isDeckB -> "B"
            isDeckBG -> "BG"
            else -> "PV"
        }

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        val isBypassed = !deck.fxChain.enabled
        ImGui.pushStyleColor(ImGuiCol.Button, if (isBypassed) ImGui.colorConvertFloat4ToU32(0.6f, 0.15f, 0.15f, 1f) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
        if (ImGui.button((if (isBypassed) "BYPASS" else "FX ON") + "##perf_deck_fx_bypass_$tag", 56f, ctrlH)) {
            deck.fxChain.enabled = !deck.fxChain.enabled
        }
        ImGui.popStyleColor()
        itemTooltip("Bypass/enable $deckLabel FX chain.")

        ImGui.sameLine(0f, gap)

        if (ImGui.button("Resync##perf_deck_fx_resync_$tag", 48f, ctrlH)) {
            val bankId = targetBankIdFor(tag)
            llm.slop.liquidlsd.macro.FxMacroSync.syncDeckFx(bankId, "$deckLabel FX", deck.fxChain, forceResync = true)
        }
        itemTooltip("Reset $deckLabel FX knobs to Super Knob + 3 Metaknobs smart default.")

        ImGui.endGroup()
    }

    /**
     * Drawn in place of the plain group title for the Master row (MASTER & FX / LIVE_CONSOLE).
     * Provides Deck A snap badge, interactive crossfader slider, Deck B snap badge, Auto-fade
     * button, and the crossfader-time (Fade Speed) badge.
     */
    private fun drawMasterHeaderControls(
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
        // Auto-Fade button
        val autoBtnW = (availW * 0.10f).coerceIn(48f, 75f)

        // Fade Speed widget (drag/badge)
        val speedBtnW = (availW * 0.08f).coerceIn(44f, 65f)

        // Deck B badge width
        val badgeBW = badgeW

        // Crossfader slider takes whatever remaining width is available between Deck A and Deck B
        val fixedRightW = gap + badgeBW + gap * 2f + autoBtnW + gap + speedBtnW
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

        ImGui.endGroup()
    }

    /**
     * Drawn in place of the plain group title for the Transitions row (MASTER & FX). Provides a
     * transition picker popup button displaying the active transition with modified indicator
     * (`*`), and TransitionQueue prev/status/next navigation.
     */
    private fun drawTransitionsHeaderControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        boxX1: Float,
        boxX2: Float,
        headerY: Float,
        headerH: Float
    ) {
        val pad = 6f
        val gap = 4f
        val availW = (boxX2 - boxX1 - pad * 2f).coerceAtLeast(1f)
        val dl = ImGui.getWindowDrawList()

        ImGui.setCursorScreenPos(boxX1 + pad, headerY)
        ImGui.beginGroup()

        // Transition Queue nav: Prev (24px) + Count (~45px) + Next (24px) + gaps
        val navBtnW = (headerH * 0.9f).coerceIn(22f, 28f)
        val qTextW = 46f

        // Transition Picker button
        val transBtnW = (availW * 0.35f).coerceIn(140f, 320f)

        // 1. Transition Picker Button [ Settings Icon + Name * ]
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

        // 2. Transition Queue Navigation [ < ] [ N/Total ] [ > ]
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

        // 3. Randomize Die Button [ DICES ]
        if (session.uiTheme.randomizationEnabled) {
            ImGui.sameLine(0f, gap)
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.20f, 0.16f, 0.24f, 0.90f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.35f, 0.22f, 0.42f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.DICES}##perf_trans_rand", navBtnW, headerH)) {
                    ParametersUndo.pushUndoState(session.parametersState, mixer)
                    mixer.transitionFilter?.let { filter ->
                        filter.parameters.values.forEach { param ->
                            val min = param.minClamp
                            val max = param.maxClamp
                            param.baseValue = (min + Math.random().toFloat() * (max - min)).coerceIn(min, max)
                        }
                    }
                }
            }
            ImGui.popStyleColor(2)
            itemTooltip("Randomize transition parameters.\nClick to randomize with undo support.")
        }

        ImGui.endGroup()
    }
}

