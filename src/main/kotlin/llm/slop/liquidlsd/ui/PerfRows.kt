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
 * Which rows the Performance matrix shows for a page, with the per-deck [SRC|FX] and Master [MIX|FX]
 * toggles applied. Pure (no drawing), so the panel and hardware controllers resolve the same rows.
 */
internal object PerfRows {
    /** A deck row's tag, source-macro bank, FX-chain bank and accent. */
    private data class DeckRowBanks(val tag: String, val srcBankId: String, val fxBankId: String, val accent: FloatArray)

    private val DECK_ROW_BANKS = listOf(
        DeckRowBanks("A",  MacroEngine.DECK_A,  MacroEngine.DECK_A_FX,  PerformanceColors.COLOR_DECK_A),
        DeckRowBanks("B",  MacroEngine.DECK_B,  MacroEngine.DECK_B_FX,  PerformanceColors.COLOR_DECK_B),
        DeckRowBanks("BG", MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX, PerformanceColors.COLOR_DECK_BG),
        DeckRowBanks("PV", MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX, PerformanceColors.COLOR_DECK_PV),
    )

    /** Deck tags in strip order; catalog ids are `deck.<tag>.srcfx`. */
    val DECK_TAGS: List<String> get() = DECK_ROW_BANKS.map { it.tag }

    /**
     * Every row a page can place, by stable id (persisted in page files). Today's rows only:
     * the per-deck `srcfx` row (with its [SRC|FX] toggle), Master ([MIX|FX]), Transitions, FX Wet/Dry
     * and Clock & Global. Master's two halves and pinned SRC/FX deck rows join the catalog later.
     */
    val CATALOG: Map<String, RowDescriptor> = linkedMapOf<String, RowDescriptor>().apply {
        for (d in DECK_ROW_BANKS) {
            put("deck.${d.tag}.srcfx", RowDescriptor(d.srcBankId, 0, d.accent, "DECK ${d.tag}", hasExtraHeader = true))
        }
        put("master", RowDescriptor(MacroEngine.MASTER,   0, PerformanceColors.COLOR_MASTER, "MASTER", hasExtraHeader = true))
        put("trans",  RowDescriptor(MacroEngine.TRANS,    0, PerformanceColors.COLOR_TRANS,  "TRANSITIONS", hasExtraHeader = true))
        put("wetdry", RowDescriptor(MacroEngine.FX_SENDS, 0, PerformanceColors.COLOR_FX,     "FX WET/DRY", hasExtraHeader = true, canExpand = false))
        put("global", RowDescriptor(MacroEngine.GLOBAL,   0, PerformanceColors.COLOR_GLOBAL, "CLOCK & GLOBAL", hasExtraHeader = true, canExpand = false))
    }

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

    /** [page]'s rows with the per-deck [SRC|FX] and Master [MIX|FX] toggles applied. */
    fun substitutedRowsForPage(page: PerfPageDef, ctx: PerformanceUiContext, parametersState: ParametersState? = null): List<RowDescriptor> =
        page.rows.mapNotNull { CATALOG[it.row] }.map { withDeckRowMode(it, ctx, parametersState) }

    /**
     * The catalog row that Deep Edit on [moduleId] should show: the first placement that covers the module,
     * scanning the active page top to bottom and then the following pages, wrapping from last to first.
     * Null when no page places it (the caller falls back to [rowDescriptorForModule]).
     */
    fun catalogRowForModule(moduleId: String, pages: List<PerfPageDef>, activePageId: String): RowDescriptor? {
        if (pages.isEmpty()) return null
        val start = pages.indexOfFirst { it.id == activePageId }.coerceAtLeast(0)
        for (offset in pages.indices) {
            val page = pages[(start + offset) % pages.size]
            for (placement in page.rows) {
                val row = CATALOG[placement.row] ?: continue
                val deck = DECK_ROW_BANKS.firstOrNull { it.srcBankId == row.bankId }
                if (deck != null && (moduleId == deck.srcBankId || moduleId == deck.fxBankId)) return row
            }
        }
        return null
    }

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
     * (reflecting active subtab e.g. SRC vs FX), decoupling the row from the active page.
     * When all modules are collapsed, returns the active page's 4 rows.
     */
    fun visibleRowsForPage(
        page: PerfPageDef,
        ctx: PerformanceUiContext,
        parametersState: ParametersState,
        labelFor: (String) -> String,
        pages: List<PerfPageDef> = listOf(page)
    ): List<RowDescriptor> {
        val expandedModuleIds = parametersState.rackModuleDisclosure
            .filterValues { it != ParametersState.DisclosureLevel.COLLAPSED }
            .keys
            .filter { it != MacroEngine.FX_SENDS }
        if (expandedModuleIds.isEmpty()) return substitutedRowsForPage(page, ctx, parametersState)

        // Show the active macro row for each expanded module
        return expandedModuleIds.map { id ->
            val template = catalogRowForModule(id, pages, page.id)
            if (template != null) withDeckRowMode(template, ctx, parametersState)
            else rowDescriptorForModule(id, ctx, parametersState, labelFor)
        }
    }
}
