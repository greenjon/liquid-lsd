package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Identifies a single cell in the Preset Grid.
 * @param paramKey   Fully-qualified parameter key, e.g. "Mixer/crossfade" or "Deck A/Geometry/L1"
 * @param cvSourceId The CV source column, e.g. "beatPhase", "amp", "lfo"
 */
data class PresetCellId(val paramKey: String, val cvSourceId: String)

sealed class MidiLearnTarget {
    data class GridCell(val cellId: PresetCellId, val param: ModulatableParameter) : MidiLearnTarget()
    data class BaseValueSlider(val paramKey: String, val label: String, val param: ModulatableParameter, val min: Float, val max: Float) : MidiLearnTarget()
}

/**
 * Holds transient UI state for the Preset Grid and Cell Config panel.
 */
class PresetGridState {
    /** The cell the user has clicked on (null = nothing selected). */
    var selectedCell: PresetCellId? = null

    /** The parameter object that backs the selected cell. */
    var selectedParam: ModulatableParameter? = null

    /** Tracks the height of subgroup panels for background drawing. */
    val subgroupHeight = mutableMapOf<String, Float>()

    /** History stack for undo support. */
    private val undoStack = mutableListOf<PresetGridUndoSnapshot>()
    private val maxUndoDepth = 30

    fun pushUndoState(snapshot: PresetGridUndoSnapshot) {
        undoStack.add(snapshot)
        if (undoStack.size > maxUndoDepth) {
            undoStack.removeAt(0)
        }
    }

    fun popUndoState(): PresetGridUndoSnapshot? {
        return if (undoStack.isNotEmpty()) undoStack.removeLast() else null
    }

    /** MIDI Learn mode toggle and active learn target */
    var isMidiLearnMode: Boolean = false
    var midiLearnTarget: MidiLearnTarget? = null

    var activeTopTab: String = "Deck A"
    var activeDeckASubTab: String = "Mandala"
    var activeDeckBSubTab: String = "Mandala"
    var activeDeckBGSubTab: String = "Mandala"
    var activeDeckPVSubTab: String = "Mandala"

    fun setDeckSubTab(deckLabel: String, tab: String) {
        when (deckLabel) {
            "Deck A" -> activeDeckASubTab = tab
            "Deck B" -> activeDeckBSubTab = tab
            "Deck BG" -> activeDeckBGSubTab = tab
            "Deck PV" -> activeDeckPVSubTab = tab
        }
    }

    fun select(cellId: PresetCellId, param: ModulatableParameter) {
        selectedCell = cellId
        selectedParam = param
    }

    fun clearSelection() {
        selectedCell = null
        selectedParam = null
    }
}


data class PresetGridUndoSnapshot(
    val modulatorsByParamKey: Map<String, List<CvModulator>>
)




