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

    /** Tracks which FX slot accordion sections are collapsed, keyed by "$deckLabel/FX$slotNum". */
    val fxSlotCollapsed = mutableMapOf<String, Boolean>()

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
     * switch their knobs between banks ([SRC|FX] / [MIX|FX]) under one canonical moduleId, so those
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
        rackModuleDisclosure[moduleId] = level
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

    /** Collapses every rack module to Tier 1, except one currently pinned open by an active Learn. */
    fun collapseAllRackModules() {
        for (key in rackModuleDisclosure.keys.toList()) {
            if (!isLearnPinned(key)) {
                rackModuleDisclosure[key] = DisclosureLevel.COLLAPSED
            }
        }
        persistRackExpandedModules()
    }

    /** True if any rack module is currently above Tier 1 (used by the Esc priority stack). */
    fun anyRackModuleExpanded(): Boolean = rackModuleDisclosure.values.any { it != DisclosureLevel.COLLAPSED }

    // -- Browse content (the unified BrowserPane is hosted here) -----------------------------

    /** Whether an open rack module's bay shows its Params (Deep Edit) or Browse content. */
    enum class SectionMode { PARAMS, BROWSE }

    /** What a module's Browse content is showing. [FxChain.slotIndex] null means the whole-chain list. */
    sealed class BrowseTarget {
        object Gen : BrowseTarget()
        data class FxChain(val slotIndex: Int? = null) : BrowseTarget()
        object Transition : BrowseTarget()
    }

    /** Active mode for the expanded rack bay (shared across modules, defaulting to PARAMS). */
    var rackSectionMode: SectionMode = SectionMode.PARAMS

    /** The dock's one apply-target: which row ([moduleId]) and what on it ([target]) Browse is bound to. */
    data class DockSelection(val moduleId: String, val target: BrowseTarget)

    /** Single dock-level selection; null means the plain Library. Survives a Browse<->Params toggle. */
    var dockSelection: DockSelection? = null
        private set

    fun clearDockSelection() {
        dockSelection = null
    }

    fun sectionModeFor(moduleId: String): SectionMode = rackSectionMode

    fun browseTargetFor(moduleId: String): BrowseTarget =
        dockSelection?.takeIf { it.moduleId == moduleId }?.target ?: BrowseTarget.Gen

    /** Opens [moduleId]'s Deep Edit (solo, same as [setDisclosure]) showing Browse content for [target]. */
    fun openBrowse(moduleId: String, target: BrowseTarget) {
        setDisclosure(moduleId, DisclosureLevel.DEEP_EDIT)
        rackSectionMode = SectionMode.BROWSE
        dockSelection = DockSelection(moduleId, target)
    }

    /**
     * Makes [target] on [moduleId] the dock's apply-target without expanding the row (Perform view). While a row is
     * already open in the Edit bay the same click also shows that bay's Browse tab, as [openBrowse] does.
     */
    private fun selectDock(moduleId: String, target: BrowseTarget) {
        if (anyRackModuleExpanded()) openBrowse(moduleId, target) else dockSelection = DockSelection(moduleId, target)
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

    /** Flips an already-open module back to its Params (Deep Edit) content. */
    fun openParams(moduleId: String) {
        setDisclosure(moduleId, DisclosureLevel.DEEP_EDIT)
        rackSectionMode = SectionMode.PARAMS
    }

    /**
     * Focuses [moduleId] (and [deckLabel]) from a confidence monitor or preview monitor click.
     *
     * Context-aware behavior:
     * - If the rack bay is currently collapsed, always opens the parameter Editor ([openParams]).
     * - If the rack bay is already open, preserves the active mode: stays in [SectionMode.PARAMS]
     *   if currently editing, or stays in [SectionMode.BROWSE] (carrying over the browse target type)
     *   if currently browsing.
     */
    fun openFromMonitor(moduleId: String, deckLabel: String? = topTabForDeepEditModule(moduleId)) {
        if (deckLabel != null) {
            activeTopTab = deckLabel
        }
        val currentlyExpanded = rackModuleDisclosure.entries.firstOrNull {
            it.value != DisclosureLevel.COLLAPSED
        }?.key

        if (currentlyExpanded == null) {
            // Bay was collapsed: always open the Editor (Deep Edit params)
            openParams(moduleId)
        } else {
            // Bay was already open: keep the active mode
            when (sectionModeFor(currentlyExpanded)) {
                SectionMode.PARAMS -> openParams(moduleId)
                SectionMode.BROWSE -> {
                    val currentTarget = browseTargetFor(currentlyExpanded)
                    if (deckLabel != null) {
                        when (currentTarget) {
                            is BrowseTarget.FxChain -> openFxChainBrowse(moduleId, deckLabel, currentTarget.slotIndex)
                            else -> openGenBrowse(moduleId, deckLabel)
                        }
                    } else if (moduleId == MacroEngine.MASTER) {
                        when (currentTarget) {
                            is BrowseTarget.FxChain -> openFxChainBrowse(moduleId, null, currentTarget.slotIndex)
                            else -> openTransitionBrowse()
                        }
                    } else {
                        openBrowse(moduleId, currentTarget)
                    }
                }
            }
        }
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
                midiLearnStartTimeMs = System.currentTimeMillis()
            }
        }

    fun startMidiLearn(target: MidiLearnTarget) {
        midiLearnTarget = target
    }

    fun isMidiTargetLearning(fullPath: String): Boolean {
        val target = midiLearnTarget ?: return false
        if (System.currentTimeMillis() - midiLearnStartTimeMs > 15000L) {
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
    var activeMixerSubTab: String = "CTRL"

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

    /** The (chainPrefix, slotIndex) currently drilled into by [FXChainMacroStrip]'s "Single FX Focus Mode", or null in group mode. */
    var focusedFxSlot: Pair<String, Int>? = null

    fun focusedSlotIndexFor(chainPrefix: String): Int? =
        focusedFxSlot?.takeIf { it.first == chainPrefix }?.second

    fun toggleFxFocus(chainPrefix: String, slotIndex: Int) {
        focusedFxSlot = if (focusedFxSlot == chainPrefix to slotIndex) null else chainPrefix to slotIndex
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




