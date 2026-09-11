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
    data class BaseValueSlider(val paramKey: String, val label: String, val param: ModulatableParameter, val min: Float, val max: Float) : MidiLearnTarget()
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

    /** MIDI Learn mode toggle and active learn target */
    var isMidiLearnMode: Boolean = false
    var midiLearnTarget: MidiLearnTarget? = null

    var activeTopTab: String = "Deck A"
    var activeDeckASubTab: String = "SRC"
    var activeDeckBSubTab: String = "SRC"
    var activeDeckBGSubTab: String = "SRC"
    var activeDeckPVSubTab: String = "SRC"

    fun setDeckSubTab(deckLabel: String, tab: String) {
        when (deckLabel) {
            "Deck A" -> activeDeckASubTab = tab
            "Deck B" -> activeDeckBSubTab = tab
            "Deck BG" -> activeDeckBGSubTab = tab
            "Deck PV" -> activeDeckPVSubTab = tab
        }
    }

    fun select(cellId: ParameterCellId, param: ModulatableParameter) {
        selectedCell = cellId
        selectedParam = param
    }

    fun clearSelection() {
        selectedCell = null
        selectedParam = null
    }
}


data class ParametersUndoSnapshot(
    val modulatorsByParamKey: Map<String, List<CvModulator>>
)




