package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine

/**
 * Describes one row of 4 knobs: which bank to pull from, which 4-knob offset within that
 * bank (0 = knobs 0–3, 4 = knobs 4–7), and the RGB accent color for the row. Each row is
 * drawn in its own box (see [PerformanceMatrixPanel]).
 */
internal data class RowDescriptor(
    val bankId: String,
    val knobOffset: Int,
    val accent: FloatArray,
    val groupLabel: String,
    /** True for rows that draw a title badge and side controls (deck/Master mode pills, chain header, bypass, Transitions/Clock lines). */
    val hasExtraHeader: Boolean = false,
    /** When false, the modular rack disclosure chevron and collapse controls are omitted. */
    val canExpand: Boolean = true
)

/**
 * Which rows the Performance matrix shows for a tab, with the per-deck [SRC|FX] and Master [MIX|FX]
 * toggles applied. Pure (no drawing), so the panel and hardware controllers resolve the same rows.
 */
internal object PerfRows {
    /** Index of the DECKS tab in [TAB_ROWS] / [PerformanceMatrixPanel.Tab]. */
    const val TAB_DECKS = 0
    /** Index of the MASTER tab. */
    const val TAB_MASTER = 1

    /** A deck row's tag, source-macro bank, FX-chain bank and accent. */
    private data class DeckRowBanks(val tag: String, val srcBankId: String, val fxBankId: String, val accent: FloatArray)

    private val DECK_ROW_BANKS = listOf(
        DeckRowBanks("A",  MacroEngine.DECK_A,  MacroEngine.DECK_A_FX,  PerformanceColors.COLOR_DECK_A),
        DeckRowBanks("B",  MacroEngine.DECK_B,  MacroEngine.DECK_B_FX,  PerformanceColors.COLOR_DECK_B),
        DeckRowBanks("BG", MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX, PerformanceColors.COLOR_DECK_BG),
        DeckRowBanks("PV", MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX, PerformanceColors.COLOR_DECK_PV),
    )

    val TAB_ROWS: Array<List<RowDescriptor>> = arrayOf(
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

    /** [row] with the per-deck [SRC|FX] or Master [MIX|FX] toggle applied (retargeted to the FX bank when on). */
    fun withDeckRowMode(row: RowDescriptor, ctx: PerformanceUiContext, parametersState: ParametersState?): RowDescriptor {
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
    fun substitutedRowsForTab(tabIdx: Int, ctx: PerformanceUiContext, parametersState: ParametersState? = null): List<RowDescriptor> =
        TAB_ROWS[tabIdx].map { withDeckRowMode(it, ctx, parametersState) }

    /**
     * Resolves the active 4-knob row descriptor for a specific module id based on the current subtab/mode.
     * [labelFor] names modules that aren't deck or Master rows (it needs the Deep Edit bay, so it's injected).
     */
    fun rowDescriptorForModule(
        moduleId: String,
        ctx: PerformanceUiContext,
        parametersState: ParametersState,
        labelFor: (String) -> String
    ): RowDescriptor {
        DECK_ROW_BANKS.firstOrNull { moduleId == it.srcBankId || moduleId == it.fxBankId }?.let { deck ->
            val template = RowDescriptor(deck.srcBankId, 0, deck.accent, "DECK ${deck.tag}", hasExtraHeader = true)
            return withDeckRowMode(template, ctx, parametersState)
        }
        return when (moduleId) {
            MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.MASTER_FX, "Mixer" -> {
                when {
                    parametersState.activeMixerSubTab == "TRANS" -> RowDescriptor(MacroEngine.TRANS, 0, PerformanceColors.COLOR_TRANS, "TRANSITIONS", hasExtraHeader = true)
                    ctx.isMasterRowFx(parametersState) -> RowDescriptor(MacroEngine.MASTER_FX, 0, PerformanceColors.COLOR_MASTER, "MASTER (FX)", hasExtraHeader = true)
                    else -> RowDescriptor(MacroEngine.MASTER, 0, PerformanceColors.COLOR_MASTER, "MASTER", hasExtraHeader = true)
                }
            }
            else -> RowDescriptor(moduleId, 0, PerformanceColors.COLOR_MASTER, labelFor(moduleId), hasExtraHeader = true)
        }
    }

    /**
     * When any module is in Deep Edit, returns the macro row(s) corresponding to the expanded module(s)
     * (reflecting active subtab e.g. SRC vs FX), decoupling the row from the matrix tab.
     * When all modules are collapsed, returns the current tab's 4 rows.
     */
    fun visibleRowsForTab(
        tabIdx: Int,
        ctx: PerformanceUiContext,
        parametersState: ParametersState,
        labelFor: (String) -> String
    ): List<RowDescriptor> {
        val expandedModuleIds = parametersState.rackModuleDisclosure
            .filterValues { it != ParametersState.DisclosureLevel.COLLAPSED }
            .keys
            .filter { it != MacroEngine.FX_SENDS }
        if (expandedModuleIds.isEmpty()) return substitutedRowsForTab(tabIdx, ctx, parametersState)

        // Show the active macro row for each expanded module
        return expandedModuleIds.map { rowDescriptorForModule(it, ctx, parametersState, labelFor) }
    }
}
