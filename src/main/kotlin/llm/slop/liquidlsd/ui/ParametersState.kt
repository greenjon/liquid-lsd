package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.midi.MidiLearnSink
import llm.slop.liquidlsd.midi.MidiLearnTarget
import llm.slop.liquidlsd.midi.ParameterCellId
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Holds transient UI state for the Parameters and Properties panels.
 */
class ParametersState : MidiLearnSink {
    /** The cell the user has clicked on (null = nothing selected). */
    var selectedCell: ParameterCellId? = null

    /** The parameter object that backs the selected cell. */
    var selectedParam: ModulatableParameter? = null

    /** Tracks the height of subgroup panels for background drawing. */
    val subgroupHeight = mutableMapOf<String, Float>()

    /**
     * The one expanded FX slot per chain (accordion), keyed by chain prefix; value is the 0-based slot index.
     * Absent = default to the first filled slot; [NO_SLOT_EXPANDED] = every slot collapsed.
     */
    val fxExpandedSlot = mutableMapOf<String, Int>()

    /** Chain prefixes whose SUPER KNOB & METAKNOBS section (Super Knob + Metaknob rows) is collapsed in the Edit FX tab. */
    val fxMacroCollapsed = mutableSetOf<String>()

    companion object {
        const val NO_SLOT_EXPANDED = -1

        /** Every value [activeMixerSubTab] can hold: MST side tab (LEVELS, CTRL) then OUT side tab (FX, TRANS). */
        val MIXER_SUB_TABS: List<String> = listOf("LEVELS", "CTRL", "FX", "TRANS")

        /** True for the MST side tab's sections (Master levels and Clock rows); false for the OUT side tab's (Transitions and Master FX). */
        fun isMstSubTab(tab: String): Boolean = tab == "LEVELS" || tab == "CTRL"
    }

    // -- Modular Rack disclosure state (docs/user_guide/macros_and_rack.md) --------------------

    /** The two disclosure tiers a [llm.slop.liquidlsd.ui.rack.RackUnit] can be in. */
    enum class DisclosureLevel { COLLAPSED, DEEP_EDIT }

    /** Per rack-module ("DECK_A", "DECK_B", "MASTER", etc.) disclosure tier. Absent = COLLAPSED. */
    val rackModuleDisclosure = mutableMapOf<String, DisclosureLevel>()

    init {
        // UITheme's init block loads preferences the first time it's touched, which SessionContext
        // guarantees happens before ParametersState is constructed (uiTheme is declared first) --
        // so UITheme.rackExpandedModules is already hydrated here. Only DEEP_EDIT is ever
        // persisted (see persistRackExpandedModules), and no Learn session survives a restart, so
        // this can never resurrect a mid-Learn-pinned state. Deep Edit is solo-only, so at most one
        // module is restored (older preference files could hold several from the removed MULTI mode).
        for ((moduleId, levelName) in UITheme.rackExpandedModules) {
            // "FX" was the removed LIVE CONSOLE focused-FX row's module id.
            if (moduleId == "FX") continue
            val level = runCatching { DisclosureLevel.valueOf(levelName) }.getOrNull()
            if (level != null && level != DisclosureLevel.COLLAPSED) {
                rackModuleDisclosure[moduleId] = level
                break
            }
        }
    }

    /** Persists the current Bay/Deep-Edit disclosure state so it survives an app restart. */
    private fun persistRackExpandedModules() {
        UITheme.rackExpandedModules = rackModuleDisclosure
            .filterValues { it != DisclosureLevel.COLLAPSED }
            .mapValues { it.value.name }
        AppPreferencesStore.savePreferences()
    }

    /** Per rack-module: that module's own Tier-3 Deep Edit selected cell (analogue of [selectedCell]). */
    val rackSelectedCell = mutableMapOf<String, ParameterCellId?>()

    fun disclosureFor(moduleId: String): DisclosureLevel = rackModuleDisclosure[moduleId] ?: DisclosureLevel.COLLAPSED

    /**
     * Maps a Rack Unit's moduleId to the [MacroEngine] bank id(s) it owns. Deck and Master rows
     * switch their knobs between banks (SRC / FX, MIX / FX) under one canonical moduleId, so those
     * own every bank they can show; any other module's moduleId is its own bank id.
     */
    private val rackModuleBankIds: Map<String, List<String>> = mapOf(
        MacroEngine.DECK_A to listOf(MacroEngine.DECK_A, MacroEngine.DECK_A_FX),
        MacroEngine.DECK_B to listOf(MacroEngine.DECK_B, MacroEngine.DECK_B_FX),
        MacroEngine.DECK_BG to listOf(MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX),
        MacroEngine.DECK_PV to listOf(MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX),
        MacroEngine.MASTER to listOf(MacroEngine.MASTER, MacroEngine.MASTER_FX, MacroEngine.TRANS)
    )

    /**
     * True if [moduleId] owns the macro knob currently armed for [MacroLearnState] Learn --
     * such a module is exempt from auto-solo collapse (see Learn-mode pinning in the rack plan).
     */
    fun isLearnPinned(moduleId: String): Boolean {
        val controlId = MacroLearnState.activeSession?.controlId ?: return false
        val (bankId, _) = MacroEngine.findBankForControl(controlId) ?: return false
        val ownedBankIds = rackModuleBankIds[moduleId] ?: listOf(moduleId)
        return bankId in ownedBankIds
    }

    /** The [activeTopTab] a Deep Edit rack module shows ("Deck A" for deckA, "Mixer" for master), or null. */
    fun topTabForDeepEditModule(moduleId: String): String? = when (moduleId) {
        MacroEngine.DECK_A -> "Deck A"
        MacroEngine.DECK_B -> "Deck B"
        MacroEngine.DECK_BG -> "Deck BG"
        MacroEngine.DECK_PV -> "Deck PV"
        MacroEngine.MASTER -> "Mixer"
        else -> null
    }

    /** Inverse of [topTabForDeepEditModule]. */
    fun deepEditModuleForTopTab(topTab: String): String? = when (topTab) {
        "Deck A" -> MacroEngine.DECK_A
        "Deck B" -> MacroEngine.DECK_B
        "Deck BG" -> MacroEngine.DECK_BG
        "Deck PV" -> MacroEngine.DECK_PV
        "Mixer" -> MacroEngine.MASTER
        else -> null
    }

    /**
     * Sets [moduleId]'s disclosure tier. Deep Edit is solo: when [level] is not COLLAPSED, every
     * other module is collapsed -- except one currently pinned open by an active Learn. Opening a
     * Deep Edit also focuses it ([activeTopTab]), and drops a FULL
     * Library back to HALF so the Edit view (which hides the Library) is actually on screen.
     */
    fun setDisclosure(moduleId: String, level: DisclosureLevel) {
        if (level != DisclosureLevel.COLLAPSED) { focusedPair = null; libraryReturn = null }
        rackModuleDisclosure[moduleId] = level
        // Closing a row's Edit bay ends its dock binding too (it would otherwise reappear bound in Perform).
        if (level == DisclosureLevel.COLLAPSED && dockSelection?.moduleId == moduleId) dockSelection = null
        if (level == DisclosureLevel.COLLAPSED) endEditIfClosed()
        if (level != DisclosureLevel.COLLAPSED) {
            topTabForDeepEditModule(moduleId)?.let { activeTopTab = it }
            for (key in rackModuleDisclosure.keys.toList()) {
                if (key != moduleId && !isLearnPinned(key)) {
                    rackModuleDisclosure[key] = DisclosureLevel.COLLAPSED
                }
            }
            if (UITheme.libraryMode == UITheme.LibraryMode.FULL) UITheme.libraryMode = UITheme.LibraryMode.HALF
        }
        persistRackExpandedModules()
    }

    /**
     * Collapses every rack module to Tier 1, except one currently pinned open by an active Learn.
     * Once no Edit bay remains open the dock binding ends with it, so every way of leaving Edit
     * (Esc, EDIT gear, Space, Ctrl+F, menu, controller) lands in the same unbound Perform/Library state.
     */
    fun collapseAllRackModules() {
        for (key in rackModuleDisclosure.keys.toList()) {
            if (!isLearnPinned(key)) {
                rackModuleDisclosure[key] = DisclosureLevel.COLLAPSED
            }
        }
        endEditIfClosed()
        persistRackExpandedModules()
    }

    /** When no module is expanded any more, drops the dock binding (unless the pair view owns it). */
    private fun endEditIfClosed() {
        if (anyRackModuleExpanded() || focusedPair != null) return
        dockSelection = null
    }

    /**
     * Leaves Edit and the Pair focus view, as a Perform page tab click does: the tab asks for that page's rows, which both
     * views replace. Clicking the active page's tab therefore closes Edit too. A Learn-pinned module stays open.
     */
    fun leaveEditForPageChange() {
        collapseAllRackModules()
        leavePair()
        libraryReturn = null
    }

    /** The module whose Edit bay is open, or null. */
    fun expandedRackModuleId(): String? = rackModuleDisclosure.entries.firstOrNull { it.value != DisclosureLevel.COLLAPSED }?.key

    /** True if any rack module is currently above Tier 1 (used by the Esc priority stack). */
    fun anyRackModuleExpanded(): Boolean = rackModuleDisclosure.values.any { it != DisclosureLevel.COLLAPSED }

    // -- Pair focus view ---------------------------------------------------------------------

    /**
     * The pair a controller's knobs 1-8 play in the Library view (the page's rows aren't on screen there): the last deck (or Master)
     * a send knob loaded, starting on PV, where previews go. A deck tag or [PerfRows.PAIR_MASTER]; not persisted.
     */
    var libraryLivePair: String = "PV"

    /**
     * The focused pair's tag (a deck tag or [PerfRows.PAIR_MASTER]), or null. The pair view shows the
     * pair's two rows over the Browse dock; it is mutually exclusive with a Params Edit module and, unlike that module, is not persisted.
     */
    var focusedPair: String? = null
        private set(value) {
            // A dirty-deck answer belongs to one bind session: leaving or switching the pair asks again.
            val before = bindSessionTag
            field = value
            if (bindSessionTag != before) dirtyAnsweredTag = null
            if (value == null) chainView = null
        }

    /** Chain view's control row (row 4): the source (or Transitions) row to play, or the load row while browsing from the controller. */
    enum class ChainViewState { PLAY, BROWSE }

    /**
     * Non-null while the focused pair is shown as Chain view ([PerfRows.chainRowsFor]): row 4 per [ChainViewState], rows 1-3 the
     * pair's FX slots. Kept across [focusPair] (stepping decks stays in Chain view); cleared with the pair.
     */
    var chainView: ChainViewState? = null
        private set

    /** Shows [tag]'s pair as Chain view, in the play state, with the dock on its FX slot 1 (the slots are what Chain view is for). */
    fun enterChainView(tag: String) {
        val pair = PerfRows.pairFor(tag) ?: return
        focusPair(tag)
        chainView = ChainViewState.PLAY
        if ((dockSelection?.target as? BrowseTarget.FxChain)?.slotIndex == null) openBrowse(pair.moduleId, BrowseTarget.FxChain(0))
    }

    /** Chain view on or off for [tag]: from Perform, the plain pair view or another pair it enters; in it, back to Perform. */
    fun toggleChainView(tag: String) {
        if (chainView != null && focusedPair == tag) leavePair() else enterChainView(tag)
    }

    /**
     * Chain view's play / browse toggle (the control row only; the slot rows never move). Browsing keeps the dock on an FX slot, since the
     * load row loads into slots.
     */
    fun toggleChainBrowse() {
        val state = chainView ?: return
        chainView = if (state == ChainViewState.PLAY) ChainViewState.BROWSE else ChainViewState.PLAY
        if (chainView == ChainViewState.BROWSE && (dockSelection?.target as? BrowseTarget.FxChain)?.slotIndex == null) {
            val pair = focusedPair?.let { PerfRows.pairFor(it) } ?: return
            openBrowse(pair.moduleId, BrowseTarget.FxChain(0))
        }
    }

    /** Focuses [tag]'s pair. Closes any Params Edit module (the two focus kinds never coexist) and drops a FULL Library to HALF. */
    fun focusPair(tag: String) {
        if (PerfRows.pairFor(tag) == null) return
        libraryReturn = null
        if (anyRackModuleExpanded()) collapseAllRackModules()
        if (focusedPair != tag && dockSelection?.let { pairTagFor(it) } != tag) dockSelection = null
        focusedPair = tag
        if (UITheme.libraryMode == UITheme.LibraryMode.FULL) UITheme.libraryMode = UITheme.LibraryMode.HALF
    }

    /** The pair a dock selection belongs to: Transition is the Master pair's, everything else its row's module ([PerfRows.pairForModule]). */
    private fun pairTagFor(selection: DockSelection): String? =
        if (selection.target is BrowseTarget.Transition) PerfRows.PAIR_MASTER else PerfRows.pairForModule(selection.moduleId)?.tag

    /**
     * What the pair view's Browse is bound to: the slot last touched on one of [pair]'s rows, else its default half: a deck's source,
     * and for the Master pair the half Edit's OUT tab last showed ([lastMixerChainTab]: the Master FX chain until the transition is touched).
     */
    internal fun pairBrowseTarget(pair: PerfRows.PairDef): BrowseTarget {
        dockSelection?.takeIf { pairTagFor(it) == pair.tag }?.let { return it.target }
        return when (pair.tag) {
            PerfRows.PAIR_MASTER -> if (lastMixerChainTab == "TRANS") BrowseTarget.Transition else BrowseTarget.FxChain(null)
            else -> BrowseTarget.Gen
        }
    }

    /** Leaves the pair view (back to Perform). */
    fun leavePair() {
        focusedPair = null
        dockSelection = null
    }

    // -- Bound in place (the Perform dock) ---------------------------------------------------

    /**
     * True while the Perform view's own (HALF) dock is bound to a slot of a row on the current page: a dock selection with no pair
     * focused and no Edit module open. The rows stay where they are; only the dock gains the target chip (see [openBrowse]).
     */
    val boundInPlace: Boolean get() = dockSelection != null && focusedPair == null && !anyRackModuleExpanded()

    /**
     * Whether the current Perform page shows the row behind [bankId] (a deck's source or FX bank, `MASTER`, `MASTER_FX`, `TRANS`).
     * Decides between binding in place and the pair view. Replaceable for tests.
     */
    internal var pageHasRow: (String) -> Boolean = { bankId ->
        val pages = PerfPageStore.default.all()
        val page = pages.firstOrNull { it.id == UITheme.performancePageId } ?: pages.first()
        page.rows.any { PerfRows.CATALOG[it.row]?.bankId == bankId }
    }

    /** The row a selection's slot sits on: a deck's source row or FX row, Master's FX row, the Transitions row. */
    private fun rowBankFor(selection: DockSelection): String? = when (selection.target) {
        is BrowseTarget.Gen -> selection.moduleId
        is BrowseTarget.Transition -> MacroEngine.TRANS
        // A pair's second row is its FX row (a deck's chain, Master FX).
        is BrowseTarget.FxChain -> PerfRows.pairForModule(selection.moduleId)?.rowIds?.getOrNull(1)?.let { PerfRows.CATALOG[it]?.bankId }
    }

    /** True when [selection] binds the Perform dock in place: Perform (no pair, no Edit module) with the slot's row on the current page. */
    private fun bindsInPlace(selection: DockSelection): Boolean =
        focusedPair == null && !anyRackModuleExpanded() && rowBankFor(selection)?.let(pageHasRow) == true

    /** True when the in-place binding's row has left the current page (a page edit), so the binding should end. */
    fun boundRowLeftPage(): Boolean = boundInPlace && dockSelection?.let(::bindsInPlace) == false

    /**
     * The Library went FULL (Space, the maximize button, the menu, the controller): it never shows a bound row, so an in-place binding
     * ends here, remembered so Esc (or Space back to HALF) returns to it ([restoreLibraryReturn]). A pair view simply ends: the routes
     * into FULL already remembered it ([LibraryPanel.show]).
     */
    fun libraryWentFull() {
        if (focusedPair != null) leavePair()
        if (boundInPlace) {
            rememberLibraryReturn()
            dockSelection = null
        }
    }

    /**
     * A shifted send-knob tap while a dock is bound (`NavSurface.browseSwitch`): moves the binding to [tag]'s pair (a deck's source,
     * or the half last touched there; Master's FX chain or transition), in place when its row is on the page, else in the pair view.
     */
    fun switchBrowseTo(tag: String) {
        val pair = PerfRows.pairFor(tag) ?: return
        if (focusedPair != null) { focusPair(tag); return }
        openBrowse(pair.moduleId, pairBrowseTarget(pair))
    }

    // -- Dirty-deck prompt, once per bind session ---------------------------------------------

    /** The deck tag whose unsaved-changes prompt was answered (Save or Discard) in the current bind session, or null. */
    private var dirtyAnsweredTag: String? = null

    /** The pair a bind session is about: the focused pair, or the pair of the slot the Perform dock is bound to in place. Null when unbound. */
    private val bindSessionTag: String?
        get() = focusedPair ?: dockSelection?.takeIf { !anyRackModuleExpanded() }?.let { pairTagFor(it) }

    /**
     * True when a load to deck [deckTag] may skip the unsaved-changes prompt: the prompt was already answered for that deck in
     * this bind session (the pair view, or the Perform dock bound to that deck), so trying several sources in a row asks once.
     * Ctrl+Z still undoes each load.
     */
    fun skipsDirtyPrompt(deckTag: String): Boolean = dirtyAnsweredTag == deckTag && bindSessionTag == deckTag

    /** Records that the prompt for [deckTag] was answered with Save or Discard; only counts while a bind session is on that deck. */
    fun noteDirtyPromptAnswered(deckTag: String) {
        if (bindSessionTag == deckTag) dirtyAnsweredTag = deckTag
    }

    // -- Way back from the Library ------------------------------------------------------------

    /** Where Esc returns to after the Library replaced a pair view, an Edit module or the Perform dock's in-place binding; see [rememberLibraryReturn]. */
    sealed class LibraryReturn {
        data class Pair(val tag: String, val selection: DockSelection?) : LibraryReturn()
        data class Edit(val moduleId: String, val selection: DockSelection?) : LibraryReturn()
        data class Bound(val selection: DockSelection) : LibraryReturn()
    }

    /** Set when the Library replaced a pair view, an Edit module or an in-place binding; cleared once used, or when one is opened another way. */
    var libraryReturn: LibraryReturn? = null
        private set

    /**
     * Called just before the Library (Space, Ctrl+F, `/`, the menu, the controller) closes the pair view or the Edit module:
     * remembers it, with the slot Browse was bound to, so Esc/back from the Library goes back there instead of to plain Perform.
     */
    fun rememberLibraryReturn() {
        val pair = focusedPair
        val module = expandedRackModuleId()
        val bound = dockSelection
        when {
            pair != null -> libraryReturn = LibraryReturn.Pair(pair, dockSelection)
            module != null -> libraryReturn = LibraryReturn.Edit(module, dockSelection)
            bound != null -> libraryReturn = LibraryReturn.Bound(bound)
        }
    }

    /** Re-opens the pair view or Edit module the Library replaced (see [rememberLibraryReturn]). Returns false when there is none. */
    fun restoreLibraryReturn(): Boolean {
        val back = libraryReturn ?: return false
        libraryReturn = null
        when (back) {
            is LibraryReturn.Pair -> {
                focusPair(back.tag)
                back.selection?.let { dockSelection = it; dockSelectionEpoch++ }
            }
            is LibraryReturn.Edit -> {
                openParams(back.moduleId)
                back.selection?.let { dockSelection = it }
            }
            // In place again when its row is still on the page (it drops a FULL Library to HALF), else the pair view.
            is LibraryReturn.Bound -> openBrowse(back.selection.moduleId, back.selection.target)
        }
        return true
    }

    /** Space or the maximize button taking a FULL Library back to HALF: re-binds the in-place binding FULL ended. False when there is none. */
    fun restoreBoundReturn(): Boolean = libraryReturn is LibraryReturn.Bound && restoreLibraryReturn()

    // -- Browse content (the unified BrowserPane is hosted here) -----------------------------

    /** What a pair view's Browse is showing. [FxChain.slotIndex] null means the whole-chain list. */
    sealed class BrowseTarget {
        object Gen : BrowseTarget()
        data class FxChain(val slotIndex: Int? = null) : BrowseTarget()
        object Transition : BrowseTarget()
    }

    /** The dock's one apply-target: which row ([moduleId]) and what on it ([target]) Browse is bound to. */
    data class DockSelection(val moduleId: String, val target: BrowseTarget)

    /**
     * Single dock-level selection; null means the plain Library. Binding happens in the Perform dock in place ([boundInPlace]) or in
     * the pair view; an Edit module keeps the one it was opened with, for its Browse button.
     */
    var dockSelection: DockSelection? = null
        private set(value) {
            val before = bindSessionTag
            field = value
            if (bindSessionTag != before) dirtyAnsweredTag = null
        }

    /**
     * Bumped by every explicit selection (row click, picker, send, the header's Browse button), so re-clicking the already-bound
     * slot still re-syncs a paused dock tab. A controller knob touch retargets without bumping it, so it never pulls the user off a tab.
     */
    var dockSelectionEpoch: Int = 0
        private set

    fun clearDockSelection() {
        dockSelection = null
    }

    /**
     * Binds the dock to [target] on [moduleId]. Every badge, slot and transition-name click, picker, send and monitor entry point goes
     * through here. In Perform (or a FULL Library, which drops to HALF) with the slot's row on the current page, the Perform dock binds
     * in place and nothing moves ([boundInPlace]); otherwise (the row is on another page, or a pair view or Edit module is up) the
     * row's pair is focused and the pair view shows the list under it. [retab] false (a knob touch) leaves the dock's tab alone unless
     * the target itself changed (see [dockSelectionEpoch]).
     */
    fun openBrowse(moduleId: String, target: BrowseTarget, retab: Boolean = true) {
        val selection = DockSelection(moduleId, target)
        val tag = pairTagFor(selection) ?: return
        if (bindsInPlace(selection)) {
            libraryReturn = null
            dockSelection = selection
            if (UITheme.libraryMode == UITheme.LibraryMode.FULL) UITheme.libraryMode = UITheme.LibraryMode.HALF
        } else {
            focusPair(tag)
            dockSelection = selection
        }
        if (retab) dockSelectionEpoch++
    }

    /**
     * What the Edit header's Browse button binds for [moduleId]: the pair and half Edit is on (a deck's source or FX chain,
     * Master FX, the transition), keeping the FX slot last browsed there. Null on the MST side tab (Master levels and Clock), which has nothing to browse.
     */
    internal fun browseSelectionForEdit(moduleId: String): DockSelection? {
        val deckLabel = topTabForDeepEditModule(moduleId)?.takeIf { moduleId != MacroEngine.MASTER }
        val target = when {
            deckLabel != null -> if (getActiveSubTab(deckLabel) == "FX") BrowseTarget.FxChain(null) else BrowseTarget.Gen
            moduleId != MacroEngine.MASTER -> return null
            activeMixerSubTab == "FX" -> BrowseTarget.FxChain(null)
            activeMixerSubTab == "TRANS" -> BrowseTarget.Transition
            else -> return null // LEVELS / CTRL
        }
        val kept = dockSelection?.takeIf { it.moduleId == moduleId && it.target is BrowseTarget.FxChain && target is BrowseTarget.FxChain }
        return kept ?: DockSelection(moduleId, target)
    }

    /** The Edit header's Browse button: switches to the pair view on the pair and half [moduleId]'s Edit is on (see [browseSelectionForEdit]). */
    fun openBrowseFromEdit(moduleId: String) {
        val selection = browseSelectionForEdit(moduleId)
        if (selection != null) openBrowse(selection.moduleId, selection.target)
        else PerfRows.pairForModule(moduleId)?.takeIf { it.tag != PerfRows.PAIR_MASTER }?.let { focusPair(it.tag) }
    }

    /** A row-slot click: binds the dock to it, except that clicking the slot the Perform dock is already bound to in place unbinds it. */
    private fun selectDock(moduleId: String, target: BrowseTarget) {
        if (boundInPlace && dockSelection == DockSelection(moduleId, target)) clearDockSelection()
        else openBrowse(moduleId, target)
    }

    /** Row click on a deck's source badge: binds the dock to that deck's source. */
    fun selectGen(canonicalModuleId: String, deckLabel: String) {
        setDeckSubTab(deckLabel, "SRC")
        selectDock(canonicalModuleId, BrowseTarget.Gen)
    }

    /** Row click on an FX chain's name or one of its slots ([slotIndex] null = the whole chain; [deckLabel] null = Master). */
    fun selectFxChain(canonicalModuleId: String, deckLabel: String?, slotIndex: Int?) {
        if (deckLabel != null) setDeckSubTab(deckLabel, "FX") else activeMixerSubTab = "FX"
        selectDock(canonicalModuleId, BrowseTarget.FxChain(slotIndex))
    }

    /** Row click on the active transition's name. */
    fun selectTransition() {
        activeMixerSubTab = "TRANS"
        selectDock(MacroEngine.MASTER, BrowseTarget.Transition)
    }

    /**
     * Row Edit toggle: opens Params for [moduleId] as drawn by its row -- a deck's SRC row passes the deck id and its FX row
     * the `_fx` id -- landing on that half's tab rather than whichever the deck was last left on.
     */
    fun openParamsFromRow(moduleId: String) {
        val (deckLabel, tab) = when (moduleId) {
            MacroEngine.DECK_A -> "Deck A" to "SRC"
            MacroEngine.DECK_B -> "Deck B" to "SRC"
            MacroEngine.DECK_BG -> "Deck BG" to "SRC"
            MacroEngine.DECK_PV -> "Deck PV" to "SRC"
            MacroEngine.DECK_A_FX -> "Deck A" to "FX"
            MacroEngine.DECK_B_FX -> "Deck B" to "FX"
            MacroEngine.DECK_BG_FX -> "Deck BG" to "FX"
            MacroEngine.DECK_PV_FX -> "Deck PV" to "FX"
            else -> null to null
        }
        if (deckLabel != null && tab != null) setDeckSubTab(deckLabel, tab)
        // Always open the canonical module (never `deckA_fx`): the tab is just a sub-tab of it.
        val canonical = canonicalModuleForRow(moduleId)
        when (moduleId) {
            MacroEngine.MASTER_FX -> activeMixerSubTab = "FX"
            MacroEngine.TRANS -> activeMixerSubTab = "TRANS"
            // Master MST lands on the MST side tab's LEVELS (deck levels and alphas, master level), not CTRL's queue/tempo triggers.
            MacroEngine.MASTER -> activeMixerSubTab = "LEVELS"
        }
        openParams(canonical)
    }

    /** The rack module a row's Edit belongs to: a deck's FX row opens the deck, Master's FX and Transitions rows open Master. */
    fun canonicalModuleForRow(rowBankId: String): String = when (rowBankId) {
        MacroEngine.DECK_A_FX -> MacroEngine.DECK_A
        MacroEngine.DECK_B_FX -> MacroEngine.DECK_B
        MacroEngine.DECK_BG_FX -> MacroEngine.DECK_BG
        MacroEngine.DECK_PV_FX -> MacroEngine.DECK_PV
        MacroEngine.MASTER_FX, MacroEngine.TRANS -> MacroEngine.MASTER
        else -> rowBankId
    }

    /** True when the Edit bay is open on exactly the half that [rowBankId]'s row shows (its module is open and its tab is selected). */
    fun isRowEditActive(rowBankId: String): Boolean {
        if (disclosureFor(canonicalModuleForRow(rowBankId)) == DisclosureLevel.COLLAPSED) return false
        return when (rowBankId) {
            MacroEngine.DECK_A -> activeDeckASubTab != "FX"
            MacroEngine.DECK_B -> activeDeckBSubTab != "FX"
            MacroEngine.DECK_BG -> activeDeckBGSubTab != "FX"
            MacroEngine.DECK_PV -> activeDeckPVSubTab != "FX"
            MacroEngine.DECK_A_FX -> activeDeckASubTab == "FX"
            MacroEngine.DECK_B_FX -> activeDeckBSubTab == "FX"
            MacroEngine.DECK_BG_FX -> activeDeckBGSubTab == "FX"
            MacroEngine.DECK_PV_FX -> activeDeckPVSubTab == "FX"
            MacroEngine.MASTER -> isMstSubTab(activeMixerSubTab) // the MST side tab (LEVELS and CTRL both show its row)
            MacroEngine.MASTER_FX -> activeMixerSubTab == "FX"
            MacroEngine.TRANS -> activeMixerSubTab == "TRANS"
            else -> true
        }
    }

    /**
     * A row's Edit gear: closes Edit when the bay is on this row's half, otherwise opens it there. With both rows of a
     * pair on screen, the other row's gear moves the bay to that half instead of closing it.
     */
    fun toggleRowEdit(rowBankId: String) {
        if (isRowEditActive(rowBankId)) setDisclosure(canonicalModuleForRow(rowBankId), DisclosureLevel.COLLAPSED)
        else openParamsFromRow(rowBankId)
    }

    /** Flips an already-open module back to its Params (Deep Edit) content. */
    fun openParams(moduleId: String) {
        setDisclosure(moduleId, DisclosureLevel.DEEP_EDIT)
    }

    /**
     * A confidence or preview monitor click (or the Master badge): browses [moduleId]'s pair on its default half ([pairBrowseTarget]: the
     * half last touched, a deck's source, Master's FX chain) -- in place when that row is on the page, else in the pair view.
     */
    fun openFromMonitor(moduleId: String, deckLabel: String? = topTabForDeepEditModule(moduleId)) {
        if (deckLabel != null) activeTopTab = deckLabel
        val pair = PerfRows.pairForModule(moduleId) ?: return
        openBrowse(pair.moduleId, pairBrowseTarget(pair))
    }

    /**
     * Opens the Params Edit view for the pair [tag]'s half last touched (the bay header's Params button): a deck's
     * SRC or FX, or the Master pair's Master FX or transition (the OUT side tab).
     */
    fun openParamsForPair(tag: String) {
        val pair = PerfRows.pairFor(tag) ?: return
        val target = pairBrowseTarget(pair)
        val deckLabel = topTabForDeepEditModule(pair.moduleId)?.takeIf { pair.moduleId != MacroEngine.MASTER }
        when {
            target is BrowseTarget.Transition -> activeMixerSubTab = "TRANS"
            deckLabel != null -> setDeckSubTab(deckLabel, if (target is BrowseTarget.FxChain) "FX" else "SRC")
            else -> activeMixerSubTab = "FX"
        }
        openParams(pair.moduleId)
    }

    /** Opens [deckLabel]'s source Browse -- the deck row's source badge, or its empty-deck launchpad. */
    fun openGenBrowse(canonicalModuleId: String, deckLabel: String) {
        setDeckSubTab(deckLabel, "SRC")
        openBrowse(canonicalModuleId, BrowseTarget.Gen)
    }

    /**
     * Opens an FX chain's Browse -- [deckLabel] null means the Master FX chain. [slotIndex] null
     * opens the whole-chain (load a saved `.lsdfxchain`) view; otherwise that slot's effect picker.
     */
    fun openFxChainBrowse(canonicalModuleId: String, deckLabel: String?, slotIndex: Int?) {
        if (deckLabel != null) setDeckSubTab(deckLabel, "FX") else activeMixerSubTab = "FX"
        openBrowse(canonicalModuleId, BrowseTarget.FxChain(slotIndex))
    }

    /** Opens the Master row's active-transition Browse. */
    fun openTransitionBrowse() {
        activeMixerSubTab = "TRANS"
        openBrowse(MacroEngine.MASTER, BrowseTarget.Transition)
    }

    /** History stack for undo support. */
    private val undoStack = mutableListOf<ParametersUndoSnapshot>()
    private val maxUndoDepth = 30

    fun pushUndoState(snapshot: ParametersUndoSnapshot) {
        undoStack.add(snapshot)
        if (undoStack.size > maxUndoDepth) {
            undoStack.removeAt(0)
        }
    }

    fun popUndoState(): ParametersUndoSnapshot? {
        return if (undoStack.isNotEmpty()) undoStack.removeLast() else null
    }

    /** Active MIDI Learn target and start timestamp */
    override var midiLearnStartTimeMs: Long = 0L
    override var midiLearnTarget: MidiLearnTarget? = null
        set(value) {
            field = value
            if (value != null) {
                midiLearnStartTimeMs = llm.slop.liquidlsd.utils.TimeSource.getEpochMillis()
            }
        }

    fun startMidiLearn(target: MidiLearnTarget) {
        midiLearnTarget = target
    }

    fun isMidiTargetLearning(fullPath: String): Boolean {
        val target = midiLearnTarget ?: return false
        if (llm.slop.liquidlsd.utils.TimeSource.getEpochMillis() - midiLearnStartTimeMs > 15000L) {
            midiLearnTarget = null
            return false
        }
        return when (target) {
            is MidiLearnTarget.ModulatorProperty -> target.fullPath == fullPath
            is MidiLearnTarget.BaseValueSlider -> target.paramKey == fullPath
            is MidiLearnTarget.MacroTarget -> target.macroPath == fullPath
            is MidiLearnTarget.GlobalAction -> target.actionKey == fullPath
            else -> false
        }
    }

    var activeTopTab: String = "Deck A"

    var activeDeckASubTab: String = "SRC"
    var activeDeckBSubTab: String = "SRC"
    var activeDeckBGSubTab: String = "SRC"
    var activeDeckPVSubTab: String = "SRC"
    /**
     * The Edit bay's Mixer section tab: `LEVELS` or `CTRL` on the MST side tab, `FX` or `TRANS` on the OUT side tab. Anything else
     * (an older saved value, a tour script) falls back to `LEVELS`, so the bay never lands on a section that does not exist.
     */
    var activeMixerSubTab: String = "LEVELS"
        set(value) {
            val tab = if (value in MIXER_SUB_TABS) value else "LEVELS"
            field = tab
            if (isMstSubTab(tab)) lastMstTab = tab else lastMixerChainTab = tab
        }

    /** The OUT side tab's section (FX or TRANS) it returns to after a visit to the MST side tab. */
    var lastMixerChainTab: String = "FX"
        private set

    /** The MST side tab's section (LEVELS or CTRL) it returns to after a visit to the OUT side tab. */
    var lastMstTab: String = "LEVELS"
        private set

    fun setDeckSubTab(deckLabel: String, tab: String) {
        when (deckLabel) {
            "Deck A" -> activeDeckASubTab = tab
            "Deck B" -> activeDeckBSubTab = tab
            "Deck BG" -> activeDeckBGSubTab = tab
            "Deck PV" -> activeDeckPVSubTab = tab
            "Mixer" -> activeMixerSubTab = tab
        }
    }

    fun getActiveSubTab(deckLabel: String): String = when (deckLabel) {
        "Deck A" -> activeDeckASubTab
        "Deck B" -> activeDeckBSubTab
        "Deck BG" -> activeDeckBGSubTab
        "Deck PV" -> activeDeckPVSubTab
        "Mixer" -> activeMixerSubTab
        else -> "SRC"
    }

    fun getActiveDeckSubTabByTag(tag: String): String = when (tag) {
        "A" -> activeDeckASubTab
        "B" -> activeDeckBSubTab
        "BG" -> activeDeckBGSubTab
        "PV" -> activeDeckPVSubTab
        else -> "SRC"
    }

    fun select(cellId: ParameterCellId, param: ModulatableParameter) {
        if (selectedCell != cellId) {
            midiLearnTarget = null
        }
        selectedCell = cellId
        selectedParam = param
    }

    fun clearSelection() {
        midiLearnTarget = null
        selectedCell = null
        selectedParam = null
    }
}


data class ParametersUndoSnapshot(
    val modulatorsByParamKey: Map<String, List<CvModulator>>,
    /** Extra state to put back on undo, run before the modulators are restored (e.g. a deck's source and macro bank). */
    val restore: (() -> Unit)? = null
)




