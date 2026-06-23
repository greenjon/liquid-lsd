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
    val groupOpen = mutableMapOf<String, Boolean>().withDefault { !UITheme.autocollapseEnabled }

    /** Tracks which tree node groups need to be programmatically collapsed. */
    val groupNeedsCollapse = mutableMapOf<String, Boolean>().withDefault { false }

    /** Tracks which tree node groups need to be programmatically expanded. */
    val groupNeedsExpand = mutableMapOf<String, Boolean>().withDefault { false }

    init {
        applyAutocollapseSetting()
    }

    fun applyAutocollapseSetting() {
        val openState = !UITheme.autocollapseEnabled
        val groups = listOf("Mixer", "Deck A", "Deck B")
        val subgroups = listOf("Geometry", "Color", "Background", "Feedback")

        for (g in groups) {
            groupOpen[g] = openState
            if (openState) {
                groupNeedsExpand[g] = true
                groupNeedsCollapse[g] = false
            } else {
                groupNeedsCollapse[g] = true
                groupNeedsExpand[g] = false
            }
        }
        for (deck in listOf("Deck A", "Deck B")) {
            for (sub in subgroups) {
                val key = "$deck/$sub"
                groupOpen[key] = openState
                if (openState) {
                    groupNeedsExpand[key] = true
                    groupNeedsCollapse[key] = false
                } else {
                    groupNeedsCollapse[key] = true
                    groupNeedsExpand[key] = false
                }
            }
        }
    }

    fun select(cellId: PatchCellId, param: ModulatableParameter) {
        selectedCell = cellId
        selectedParam = param
    }

    fun clearSelection() {
        selectedCell = null
        selectedParam = null
    }
}
