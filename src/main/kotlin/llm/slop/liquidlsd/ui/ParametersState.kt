package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Identifies a single cell in the Deep Edit parameter grid.
 * @param paramKey   Fully-qualified parameter key, e.g. "Mixer/crossfade" or "Deck A/Geometry/L1"
 * @param cvSourceId The CV source column, e.g. "beatPhase", "amp", "lfo"
 */
data class ParameterCellId(val paramKey: String, val cvSourceId: String)

sealed class MidiLearnTarget {
    data class GridCell(val cellId: ParameterCellId, val param: ModulatableParameter) : MidiLearnTarget()
    data class BaseValueSlider(val paramKey: String, val label: String, val param: ModulatableParameter? = null, val min: Float, val max: Float) : MidiLearnTarget()
    data class ModulatorProperty(val fullPath: String, val label: String, val min: Float, val max: Float) : MidiLearnTarget()
    data class GlobalAction(val actionKey: String) : MidiLearnTarget()
    data class MacroTarget(val macroPath: String, val label: String) : MidiLearnTarget()
}

/**
 * Holds transient UI state for the Parameters and Properties panels.
 */
class ParametersState {
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

    /** Per rack-module: which macro knob is selected -- drives the Tier-1 grid highlight and inline Learn button. */
    val selectedRackMacroId = mutableMapOf<String, String?>()

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
     * Deep Edit also focuses it ([activeTopTab]), so the MACROS panel follows it, and drops a FULL
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
    var midiLearnStartTimeMs: Long = 0L
    var midiLearnTarget: MidiLearnTarget? = null
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

    /**
     * Column 3 MACROS panel's GLB tab. The Global bank has no Deep Edit section, so rather than a
     * fake [activeTopTab] (which Deep Edit reads) this pins GLB to the tab/sub-tab that was active
     * when GLB was picked; any later navigation (Deep Edit, side rail, another MACROS tab) changes
     * that key and so drops back to the normal tab. See [isGlobalMacrosShown].
     */
    private var globalMacrosAnchor: String? = null

    private fun macroNavKey(): String = "$activeTopTab/${getActiveSubTab(activeTopTab)}"

    fun showGlobalMacros() { globalMacrosAnchor = macroNavKey() }

    fun hideGlobalMacros() { globalMacrosAnchor = null }

    fun isGlobalMacrosShown(): Boolean {
        val anchor = globalMacrosAnchor ?: return false
        if (anchor == macroNavKey()) return true
        globalMacrosAnchor = null
        return false
    }
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
    val modulatorsByParamKey: Map<String, List<CvModulator>>
)




