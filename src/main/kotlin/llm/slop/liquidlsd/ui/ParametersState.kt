package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Identifies a single cell in the Parameters panel matrix.
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

    /** Per rack-module ("DECK_A", "DECK_B", "FX", "MASTER", etc.) disclosure tier. Absent = COLLAPSED. */
    val rackModuleDisclosure = mutableMapOf<String, DisclosureLevel>()

    init {
        // UITheme's init block loads preferences the first time it's touched, which SessionContext
        // guarantees happens before ParametersState is constructed (uiTheme is declared first) --
        // so UITheme.rackExpandedModules is already hydrated here. Only DEEP_EDIT is ever
        // persisted (see persistRackExpandedModules), and no Learn session survives a restart, so
        // this can never resurrect a mid-Learn-pinned state.
        for ((moduleId, levelName) in UITheme.rackExpandedModules) {
            val level = runCatching { DisclosureLevel.valueOf(levelName) }.getOrNull()
            if (level != null && level != DisclosureLevel.COLLAPSED) {
                rackModuleDisclosure[moduleId] = level
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

    /** When true, opening one module's Deep Edit auto-collapses every other module (except a Learn-pinned one). Persisted via [UITheme.rackSoloMode]. */
    var rackSoloMode: Boolean
        get() = UITheme.rackSoloMode
        set(value) { UITheme.rackSoloMode = value }

    /** Per rack-module: which macro knob is selected -- drives the Tier-1 grid highlight and inline Learn button. */
    val selectedRackMacroId = mutableMapOf<String, String?>()

    /** Per rack-module: that module's own Tier-3 Deep Edit selected cell (analogue of [selectedCell]). */
    val rackSelectedCell = mutableMapOf<String, ParameterCellId?>()

    fun disclosureFor(moduleId: String): DisclosureLevel = rackModuleDisclosure[moduleId] ?: DisclosureLevel.COLLAPSED

    /**
     * Maps a Rack Unit's moduleId to the [MacroEngine] bank id(s) it owns. Every plain fixed
     * module (Deck A/B/BG/PV, Transitions, Master, FX Sends, Master FX) uses its own bank id as
     * its moduleId 1:1, so this table only needs an entry for "FX": LIVE_CONSOLE's focus-swappable
     * FX row keeps one stable moduleId ("FX") decoupled from whichever bank (FX1/FX2/MFX) is
     * currently focused, per the focus-swap decoupling rule -- accordion state must not reset or
     * duplicate itself when the user refocuses the row to a different bank.
     */
    private val rackModuleBankIds: Map<String, List<String>> = mapOf(
        "FX" to listOf(MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2, MacroEngine.MASTER_FX)
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

    /**
     * Sets [moduleId]'s disclosure tier. When [rackSoloMode] is on and [level] is not COLLAPSED,
     * every other module is collapsed too -- except one currently pinned open by an active Learn.
     */
    fun setDisclosure(moduleId: String, level: DisclosureLevel) {
        rackModuleDisclosure[moduleId] = level
        if (rackSoloMode && level != DisclosureLevel.COLLAPSED) {
            for (key in rackModuleDisclosure.keys.toList()) {
                if (key != moduleId && !isLearnPinned(key)) {
                    rackModuleDisclosure[key] = DisclosureLevel.COLLAPSED
                }
            }
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

    /**
     * The active chain is now a real, single live fact owned by [llm.slop.liquidlsd.rendering.FxBank]
     * (see [llm.slop.liquidlsd.rendering.FxBank.activeChainIndex]) rather than independent per-view
     * UI state -- these just read/write the bank directly so every view (Classic Mode, Performance
     * Console) shows and drives the same live chain.
     */
    fun getActiveChainIndex(bank: llm.slop.liquidlsd.rendering.FxBank): Int = bank.activeChainIndex

    fun setActiveChainIndex(bank: llm.slop.liquidlsd.rendering.FxBank, index: Int) {
        bank.activeChainIndex = index
        focusedFxSlot = null
        val bankId = llm.slop.liquidlsd.macro.MacroEngine.canonicalIdForDeckLabel(bank.label)
        llm.slop.liquidlsd.macro.FxMacroSync.sync(bankId, bank)
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




