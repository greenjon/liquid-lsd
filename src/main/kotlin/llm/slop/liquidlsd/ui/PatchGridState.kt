package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Identifies a single cell in the Patch Grid.
 * @param paramKey   Fully-qualified parameter key, e.g. "Mixer/crossfade" or "Deck A/Geometry/L1"
 * @param cvSourceId The CV source column, e.g. "beatPhase", "amp", "lfo"
 */
data class PatchCellId(val paramKey: String, val cvSourceId: String)

sealed class MidiLearnTarget {
    data class GridCell(val cellId: PatchCellId, val param: ModulatableParameter) : MidiLearnTarget()
    data class BaseValueSlider(val paramKey: String, val label: String, val param: ModulatableParameter, val min: Float, val max: Float) : MidiLearnTarget()
}

/**
 * Holds transient UI state for the Patch Grid and Cell Config panel.
 */
class PatchGridState {
    /** The cell the user has clicked on (null = nothing selected). */
    var selectedCell: PatchCellId? = null

    /** The parameter object that backs the selected cell. */
    var selectedParam: ModulatableParameter? = null

    /** Tracks the height of subgroup panels for background drawing. */
    val subgroupHeight = mutableMapOf<String, Float>()

    /** History stack for undo support. */
    private val undoStack = mutableListOf<PatchGridUndoSnapshot>()
    private val maxUndoDepth = 30

    fun pushUndoState(snapshot: PatchGridUndoSnapshot) {
        undoStack.add(snapshot)
        if (undoStack.size > maxUndoDepth) {
            undoStack.removeAt(0)
        }
    }

    fun popUndoState(): PatchGridUndoSnapshot? {
        return if (undoStack.isNotEmpty()) undoStack.removeLast() else null
    }

    /** MIDI Learn mode toggle and active learn target */
    var isMidiLearnMode: Boolean = false
    var midiLearnTarget: MidiLearnTarget? = null

    var activeTopTab: String = "Deck A"
    var activeDeckASubTab: String = "Mandala"
    var activeDeckBSubTab: String = "Mandala"
    var activeDeckCSubTab: String = "Mandala"

    fun select(cellId: PatchCellId, param: ModulatableParameter) {
        selectedCell = cellId
        selectedParam = param
    }

    fun clearSelection() {
        selectedCell = null
        selectedParam = null
    }
}

data class PatchGridUndoSnapshot(
    val modulatorsByParamKey: Map<String, List<CvModulator>>
)

