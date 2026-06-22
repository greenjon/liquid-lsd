package llm.slop.spirals.ui

import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.ModulatableParameter

/**
 * Identifies a single cell in the Patch Grid.
 * @param paramKey   Fully-qualified parameter key, e.g. "Mixer/crossfade" or "Deck A/Geometry/L1"
 * @param cvSourceId The CV source column, e.g. "beatPhase", "amp", "lfo"
 */
data class PatchCellId(val paramKey: String, val cvSourceId: String)

sealed class MidiLearnTarget {
    data class GridCell(val cellId: PatchCellId, val param: ModulatableParameter) : MidiLearnTarget()
    data class BaseValueSlider(val label: String, val param: ModulatableParameter, val min: Float, val max: Float) : MidiLearnTarget()
}

/**
 * Holds transient UI state for the Patch Grid and Cell Config panel.
 */
class PatchGridState {
    /** The cell the user has clicked on (null = nothing selected). */
    var selectedCell: PatchCellId? = null

    /** The parameter object that backs the selected cell. */
    var selectedParam: ModulatableParameter? = null

    /** MIDI Learn mode toggle and active learn target */
    var isMidiLearnMode: Boolean = false
    var midiLearnTarget: MidiLearnTarget? = null

    /** Tracks which tree node groups are open (keyed by label). Default open. */
    val groupOpen = mutableMapOf<String, Boolean>().withDefault { true }

    fun select(cellId: PatchCellId, param: ModulatableParameter) {
        selectedCell = cellId
        selectedParam = param
    }

    fun clearSelection() {
        selectedCell = null
        selectedParam = null
    }
}
