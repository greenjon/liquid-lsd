package llm.slop.liquidlsd.ui

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




