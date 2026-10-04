package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.cv.isAudioSource
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroTargetType
import llm.slop.liquidlsd.midi.ParameterCellId
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer

/** Shared "go to this binding's target" helper for the Edit-row macro strip's binding chips. */
object MacroBindingNav {
    /** Where a bound parameter is edited: the rack module that opens it in Deep Edit, plus the top tab and sub-tab. */
    internal data class NavTarget(val moduleId: String, val topTab: String, val subTab: String)

    /**
     * Maps a parameter path ("Deck A/fbZoom", "Deck A/FX/...", "Mixer/Transition/DryWet", "Master/FX/...",
     * "Mixer/crossfade") to where it's shown. Null for paths with no editor tab.
     */
    internal fun navTargetFor(parameterId: String): NavTarget? {
        val top = parameterId.substringBefore('/', missingDelimiterValue = "")
        val rest = parameterId.substringAfter('/', missingDelimiterValue = "")
        val deckModule = when (top) {
            "Deck A" -> MacroEngine.DECK_A
            "Deck B" -> MacroEngine.DECK_B
            "Deck BG" -> MacroEngine.DECK_BG
            "Deck PV" -> MacroEngine.DECK_PV
            else -> null
        }
        return when {
            deckModule != null -> NavTarget(deckModule, top, if (rest.startsWith("FX/")) "FX" else "SRC")
            top == "Master" && rest.startsWith("FX/") -> NavTarget(MacroEngine.MASTER, "Mixer", "FX")
            top == "Mixer" -> NavTarget(MacroEngine.MASTER, "Mixer", if (rest.startsWith("Transition/")) "TRANS" else "CTRL")
            else -> null
        }
    }

    /** Opens [nav]'s Deep Edit tab and selects [binding]'s target cell there. */
    internal fun navigateTo(binding: MacroBinding, nav: NavTarget, parametersState: ParametersState, mixer: Mixer) {
        parametersState.activeTopTab = nav.topTab
        parametersState.setDeckSubTab(nav.topTab, nav.subTab)
        val targetParam = ParameterResolver.findParameterByPath(mixer, binding.parameterId)
        if (targetParam != null) {
            val cvId = if (binding.targetType == MacroTargetType.MODULATOR_PROPERTY) {
                llm.slop.liquidlsd.parameters.ModulatorPropertyAccessor.findById(targetParam, binding.modulatorId)?.let { modulatorCvId(it.sourceId) } ?: "value"
            } else {
                "value"
            }
            val cell = ParameterCellId(binding.parameterId, cvId)
            parametersState.select(cell, targetParam)
            // Deep Edit keeps its own per-module selection (see PerformanceDeepEditBay.drawRackDeepEdit).
            parametersState.rackSelectedCell[nav.moduleId] = cell
        }
        parametersState.setDisclosure(nav.moduleId, ParametersState.DisclosureLevel.DEEP_EDIT)
    }

    /**
     * Maps a [llm.slop.liquidlsd.parameters.CvModulator.sourceId] to the Properties panel's
     * cvSourceId column key (see [ParametersRenderer.drawCvCell]): individual audio-reactive
     * bands share the single "audio" column/tab, MIDI CC modulators share "midi", and every
     * other source (e.g. "lfo", "seq") is used verbatim as its own column.
     */
    private fun modulatorCvId(sourceId: String): String = when {
        isAudioSource(sourceId)        -> "audio"
        sourceId.startsWith("midi_cc_") -> "midi"
        else                             -> sourceId
    }
}
