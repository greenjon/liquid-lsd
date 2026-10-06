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
    val canExpand: Boolean = true,
    /** Non-null for a deck row (`SRC` / `FX`) or a Master row (`MIX` / `FX`): the one half it shows. */
    val pinnedMode: String? = null
)

/**
 * Which rows the Performance matrix shows for a page, or, while a module is open in the Edit bay, the row for the
 * half its tab shows. Pure (no drawing), so the panel and hardware controllers resolve the same rows.
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

    /** Deck tags in strip order; catalog ids are `deck.<tag>.src` / `.fx`. */
    val DECK_TAGS: List<String> = DECK_ROW_BANKS.map { it.tag }

    /** The deck whose source or FX-chain bank is [bankId], or null. */
    private fun deckForBank(bankId: String): DeckRowBanks? =
        DECK_ROW_BANKS.firstOrNull { it.srcBankId == bankId || it.fxBankId == bankId }

    /**
     * Every row a page can place, by stable id (persisted in page files): per deck `deck.<tag>.src` and `.fx`,
     * Master `master.mix` and `master.fx`, Transitions and Clock & Global. Every deck and Master row
     * shows one half, so a row's meaning never depends on a mode.
     */
    val CATALOG: Map<String, RowDescriptor> = linkedMapOf<String, RowDescriptor>().apply {
        for (d in DECK_ROW_BANKS) {
            put("deck.${d.tag}.src", RowDescriptor(d.srcBankId, 0, d.accent, "DECK ${d.tag}", hasExtraHeader = true, pinnedMode = "SRC"))
            put("deck.${d.tag}.fx", RowDescriptor(d.fxBankId, 0, d.accent, "DECK ${d.tag} (FX)", hasExtraHeader = true, pinnedMode = "FX"))
        }
        put("master.mix", RowDescriptor(MacroEngine.MASTER, 0, PerformanceColors.COLOR_MASTER, "MASTER", hasExtraHeader = true, pinnedMode = "MIX"))
        put("master.fx", RowDescriptor(MacroEngine.MASTER_FX, 0, PerformanceColors.COLOR_MASTER, "MASTER (FX)", hasExtraHeader = true, pinnedMode = "FX"))
        put("trans",  RowDescriptor(MacroEngine.TRANS,    0, PerformanceColors.COLOR_TRANS,  "TRANSITIONS", hasExtraHeader = true))
        put("global", RowDescriptor(MacroEngine.GLOBAL,   0, PerformanceColors.COLOR_GLOBAL, "CLOCK & GLOBAL", hasExtraHeader = true, canExpand = false))
    }

    /** [page]'s rows, resolved from the catalog (unknown ids are skipped). */
    fun rowsForPage(page: PerfPageDef): List<RowDescriptor> = page.rows.mapNotNull { CATALOG[it.row] }

    /**
     * The catalog row that Deep Edit on [moduleId] should show: the first placement that covers the module and
     * its [half] (`SRC`/`FX` for a deck, `MIX`/`FX` for Master), scanning the active page top to bottom and then
     * the following pages, wrapping from last to first.
     * Null when no page places it (the caller falls back to [rowDescriptorForModule]).
     */
    fun catalogRowForModule(moduleId: String, half: String, pages: List<PerfPageDef>, activePageId: String): RowDescriptor? {
        if (pages.isEmpty()) return null
        val start = pages.indexOfFirst { it.id == activePageId }.coerceAtLeast(0)
        for (offset in pages.indices) {
            val page = pages[(start + offset) % pages.size]
            for (placement in page.rows) {
                val row = CATALOG[placement.row] ?: continue
                val deck = deckForBank(row.bankId)
                val covers = when {
                    deck != null -> moduleId == deck.srcBankId || moduleId == deck.fxBankId
                    row.bankId == MacroEngine.MASTER || row.bankId == MacroEngine.MASTER_FX ->
                        moduleId == MacroEngine.MASTER || moduleId == MacroEngine.MASTER_FX || moduleId == "Mixer"
                    else -> false
                }
                if (covers && row.pinnedMode == half) return row
            }
        }
        return null
    }

    /** `FX` when the Edit bay's tab for [moduleId] is the FX half of its deck / Master, else `SRC` (decks) or `MIX` (Master). */
    private fun halfFor(moduleId: String, ctx: PerformanceUiContext, parametersState: ParametersState): String? {
        deckForBank(moduleId)?.let {
            return if (ctx.isDeckBayFx(it.tag, parametersState)) "FX" else "SRC"
        }
        if (moduleId == MacroEngine.MASTER || moduleId == MacroEngine.MASTER_FX || moduleId == "Mixer") {
            if (parametersState.activeMixerSubTab == "TRANS") return null // Transitions has no page variant; use the fallback
            return if (ctx.isMasterBayFx(parametersState)) "FX" else "MIX"
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
        deckForBank(moduleId)?.let { deck ->
            return if (ctx.isDeckBayFx(deck.tag, parametersState))
                RowDescriptor(deck.fxBankId, 0, deck.accent, "DECK ${deck.tag} (FX)", hasExtraHeader = true, pinnedMode = "FX")
            else RowDescriptor(deck.srcBankId, 0, deck.accent, "DECK ${deck.tag}", hasExtraHeader = true, pinnedMode = "SRC")
        }
        return when (moduleId) {
            MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.MASTER_FX, "Mixer" -> {
                when {
                    parametersState.activeMixerSubTab == "TRANS" -> RowDescriptor(MacroEngine.TRANS, 0, PerformanceColors.COLOR_TRANS, "TRANSITIONS", hasExtraHeader = true)
                    ctx.isMasterBayFx(parametersState) -> RowDescriptor(MacroEngine.MASTER_FX, 0, PerformanceColors.COLOR_MASTER, "MASTER (FX)", hasExtraHeader = true, pinnedMode = "FX")
                    else -> RowDescriptor(MacroEngine.MASTER, 0, PerformanceColors.COLOR_MASTER, "MASTER", hasExtraHeader = true, pinnedMode = "MIX")
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
        if (expandedModuleIds.isEmpty()) return rowsForPage(page)

        // Show the active macro row for each expanded module
        return expandedModuleIds.map { id ->
            val half = halfFor(id, ctx, parametersState)
            half?.let { catalogRowForModule(id, it, pages, page.id) } ?: rowDescriptorForModule(id, ctx, parametersState, labelFor)
        }
    }

    /** True when [moduleId]'s Deep Edit row is named by the caller's `labelFor` (so its result can't be cached by state alone). */
    private fun usesLabel(moduleId: String): Boolean =
        deckForBank(moduleId) == null &&
            moduleId != MacroEngine.MASTER && moduleId != MacroEngine.TRANS && moduleId != MacroEngine.MASTER_FX && moduleId != "Mixer"

    /**
     * Memoises [visibleRowsForPage] for one caller (the matrix panel, a hardware surface). The result is a pure
     * function of the page and page list (compared by reference: the store hands out a new list on reload), the
     * four deck Edit-bay [SRC|FX] tabs, the Master [MIX|FX] tab, the Mixer sub-tab and the ordered set of expanded
     * Deep Edit modules, so those are compared each call (no allocation) and the rows are rebuilt only on a change.
     * Deep Edit on a module named by `labelFor` is never cached because the label can change without any of the above.
     * Not thread-safe; give each thread/caller its own instance.
     */
    class RowsCache {
        private var page: PerfPageDef? = null
        private var pages: List<PerfPageDef>? = null
        private var fxMask = -1
        private var mixerSub: String? = null
        private var expanded = arrayOfNulls<String>(4)
        private var expandedCount = -1
        private var scratch = arrayOfNulls<String>(4)
        private var result: List<RowDescriptor> = emptyList()

        fun rows(
            page: PerfPageDef,
            ctx: PerformanceUiContext,
            parametersState: ParametersState,
            labelFor: (String) -> String,
            pages: List<PerfPageDef> = listOf(page)
        ): List<RowDescriptor> {
            var mask = 0
            for (i in DECK_ROW_BANKS.indices) if (ctx.isDeckBayFx(DECK_ROW_BANKS[i].tag, parametersState)) mask = mask or (1 shl i)
            if (ctx.isMasterBayFx(parametersState)) mask = mask or (1 shl DECK_ROW_BANKS.size)

            var n = 0
            var uncacheable = false
            for ((id, level) in parametersState.rackModuleDisclosure) {
                if (level == ParametersState.DisclosureLevel.COLLAPSED) continue
                if (n == scratch.size) scratch = scratch.copyOf(n * 2)
                scratch[n++] = id
                if (usesLabel(id)) uncacheable = true
            }
            if (uncacheable) return visibleRowsForPage(page, ctx, parametersState, labelFor, pages)

            var same = this.page === page && this.pages === pages && fxMask == mask &&
                mixerSub == parametersState.activeMixerSubTab && expandedCount == n
            if (same) for (i in 0 until n) if (expanded[i] != scratch[i]) { same = false; break }
            if (same) return result

            result = visibleRowsForPage(page, ctx, parametersState, labelFor, pages)
            this.page = page; this.pages = pages; fxMask = mask
            mixerSub = parametersState.activeMixerSubTab
            val t = expanded; expanded = scratch; scratch = t
            expandedCount = n
            return result
        }
    }
}

/**
 * Remembers one built string per call site so a per-frame tooltip/label is only rebuilt when the values it
 * is made from change. Keys compare with `==`; the [build] lambda is inline, so a hit allocates nothing.
 */
internal class TipCache {
    private var k1: Any? = null
    private var k2: Any? = null
    private var k3: Any? = null
    private var text: String? = null

    inline fun get(a: Any?, b: Any? = null, c: Any? = null, build: () -> String): String {
        val cached = lookup(a, b, c)
        if (cached != null) return cached
        return store(a, b, c, build())
    }

    fun lookup(a: Any?, b: Any?, c: Any?): String? = if (text != null && k1 == a && k2 == b && k3 == c) text else null

    fun store(a: Any?, b: Any?, c: Any?, value: String): String {
        k1 = a; k2 = b; k3 = c; text = value
        return value
    }
}
