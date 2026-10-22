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
    /** Non-null for a deck row (`SRC` / `FX`) or a Master row (`MST` / `FX`): the one half it shows. */
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

    /** The Master pair's tag: Transitions + Master FX, the two browsable rows that share a Twister page half (MIXER keys 1-8). */
    const val PAIR_MASTER = "MASTER"

    /** Deck tags in strip order; catalog ids are `deck.<tag>.src` / `.fx`. */
    val DECK_TAGS: List<String> = DECK_ROW_BANKS.map { it.tag }

    /** The deck whose source or FX-chain bank is [bankId], or null. */
    private fun deckForBank(bankId: String): DeckRowBanks? =
        DECK_ROW_BANKS.firstOrNull { it.srcBankId == bankId || it.fxBankId == bankId }

    /**
     * Every row a page can place, by stable id (persisted in page files): per deck `deck.<tag>.src` and `.fx`,
     * Master `master.mix` (pill MST) and `master.fx`, Transitions and Clock & Global. Every deck and Master row
     * shows one half, so a row's meaning never depends on a mode.
     */
    val CATALOG: Map<String, RowDescriptor> = linkedMapOf<String, RowDescriptor>().apply {
        for (d in DECK_ROW_BANKS) {
            put("deck.${d.tag}.src", RowDescriptor(d.srcBankId, 0, d.accent, "DECK ${d.tag}", hasExtraHeader = true, pinnedMode = "SRC"))
            put("deck.${d.tag}.fx", RowDescriptor(d.fxBankId, 0, d.accent, "DECK ${d.tag} (FX)", hasExtraHeader = true, pinnedMode = "FX"))
        }
        put("master.mix", RowDescriptor(MacroEngine.MASTER, 0, PerformanceColors.COLOR_MASTER, "MASTER", hasExtraHeader = true, pinnedMode = "MST"))
        put("master.fx", RowDescriptor(MacroEngine.MASTER_FX, 0, PerformanceColors.COLOR_MASTER, "MASTER (FX)", hasExtraHeader = true, pinnedMode = "FX"))
        put("trans",  RowDescriptor(MacroEngine.TRANS,    0, PerformanceColors.COLOR_TRANS,  "TRANSITIONS", hasExtraHeader = true))
        put("global", RowDescriptor(MacroEngine.GLOBAL,   0, PerformanceColors.COLOR_GLOBAL, "CLOCK & GLOBAL", hasExtraHeader = true, canExpand = false))
    }

    /** A focusable pair of rows: [tag] is what `ParametersState.focusedPair` holds, [moduleId] the canonical rack module the pair belongs to. */
    data class PairDef(val tag: String, val moduleId: String, val rowIds: List<String>)

    /**
     * The browsable pairs, each the two rows that share a half of a Twister page: a deck's SRC+FX, and Master's Transitions+Master FX
     * (the MIXER page's keys 1-8). The other MIXER half, Master MST + Clock, has nothing to browse, so it is no pair-view pair; it exists
     * only as Edit's pair on the MST side tab (see [editPairFor]).
     */
    val PAIRS: List<PairDef> = DECK_ROW_BANKS.map { PairDef(it.tag, it.srcBankId, listOf("deck.${it.tag}.src", "deck.${it.tag}.fx")) } +
        PairDef(PAIR_MASTER, MacroEngine.MASTER, listOf("trans", "master.fx"))

    fun pairFor(tag: String): PairDef? = PAIRS.firstOrNull { it.tag == tag }

    /** The pair whose row is [bankId] (a deck's source or FX bank, `MASTER_FX`, `TRANS`), or null (Master MST and Clock have no pair). */
    fun pairForBank(bankId: String): PairDef? = PAIRS.firstOrNull { pair -> pair.rowIds.any { CATALOG[it]?.bankId == bankId } }

    /**
     * The pair a rack module id belongs to: [pairForBank], except that the Master module (`MASTER`, which also owns the Transitions
     * and Master FX rows) is the Master pair. Dock selections and Edit modules name rows by module id, so they resolve through here.
     */
    fun pairForModule(moduleId: String): PairDef? =
        if (moduleId == MacroEngine.MASTER) pairFor(PAIR_MASTER) else pairForBank(moduleId)

    /** [pair]'s two rows, resolved from the catalog. */
    fun rowsForPair(pair: PairDef): List<RowDescriptor> = pair.rowIds.mapNotNull { CATALOG[it] }

    /** [page]'s rows, resolved from the catalog (unknown ids are skipped). */
    fun rowsForPage(page: PerfPageDef): List<RowDescriptor> = page.rows.mapNotNull { CATALOG[it.row] }

    /**
     * The two rows Edit shows for an open [moduleId], in the order the Twister page pairs them: a deck's SRC + FX, Master's
     * MST + Clock on the MST side tab (LEVELS/CTRL) and Transitions + Master FX on the OUT side tab (FX/TRANS). The bay's section
     * tab only picks which of the two the editor is on, so switching sections never changes the rows. Null for modules without a pair.
     */
    internal fun editPairFor(moduleId: String, parametersState: ParametersState): List<RowDescriptor>? {
        deckForBank(moduleId)?.let { return listOfNotNull(CATALOG["deck.${it.tag}.src"], CATALOG["deck.${it.tag}.fx"]) }
        if (moduleId == MacroEngine.MASTER || moduleId == MacroEngine.MASTER_FX || moduleId == "Mixer") {
            return if (ParametersState.isMstSubTab(parametersState.activeMixerSubTab)) listOfNotNull(CATALOG["master.mix"], CATALOG["global"])
            else listOfNotNull(CATALOG["trans"], CATALOG["master.fx"])
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
                    else -> RowDescriptor(MacroEngine.MASTER, 0, PerformanceColors.COLOR_MASTER, "MASTER", hasExtraHeader = true, pinnedMode = "MST")
                }
            }
            else -> RowDescriptor(moduleId, 0, PerformanceColors.COLOR_MASTER, labelFor(moduleId), hasExtraHeader = true)
        }
    }

    /**
     * When any module is in Deep Edit, returns the pair of macro rows for each expanded module
     * (see [editPairFor]), decoupling the rows from the active page.
     * When all modules are collapsed, returns the active page's 4 rows.
     */
    fun visibleRowsForPage(
        page: PerfPageDef,
        ctx: PerformanceUiContext,
        parametersState: ParametersState,
        labelFor: (String) -> String,
        pages: List<PerfPageDef> = listOf(page)
    ): List<RowDescriptor> {
        parametersState.focusedPair?.let { tag -> pairFor(tag)?.let { return rowsForPair(it) } }
        val expandedModuleIds = parametersState.rackModuleDisclosure
            .filterValues { it != ParametersState.DisclosureLevel.COLLAPSED }
            .keys
        if (expandedModuleIds.isEmpty()) return rowsForPage(page)

        // Show each expanded module's pair of rows (a module without one shows its single fallback row)
        return expandedModuleIds.flatMap { id ->
            editPairFor(id, parametersState) ?: listOf(rowDescriptorForModule(id, ctx, parametersState, labelFor))
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
        private var pairTag: String? = null
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
            parametersState.focusedPair?.let { tag ->
                // A pair's rows come from the catalog alone, so the tag is the whole key.
                if (pairTag != tag) {
                    result = visibleRowsForPage(page, ctx, parametersState, labelFor, pages)
                    pairTag = tag
                }
                return result
            }

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

            var same = pairTag == null && this.page === page && this.pages === pages && fxMask == mask &&
                mixerSub == parametersState.activeMixerSubTab && expandedCount == n
            if (same) for (i in 0 until n) if (expanded[i] != scratch[i]) { same = false; break }
            if (same) return result

            result = visibleRowsForPage(page, ctx, parametersState, labelFor, pages)
            this.page = page; this.pages = pages; fxMask = mask; pairTag = null
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
