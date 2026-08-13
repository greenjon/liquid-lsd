package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ParameterResolver

object PresetGridUndo {
    fun createUndoSnapshot(mixer: Mixer): PresetGridUndoSnapshot {
        val mods = mutableMapOf<String, List<CvModulator>>()
        ParameterResolver.getAllParameterPaths(mixer).forEach { (path, p) ->
            mods[path] = p.modulators.map { it.copy() }
        }
        return PresetGridUndoSnapshot(mods)
    }

    fun pushUndoState(state: PresetGridState, mixer: Mixer) {
        state.pushUndoState(createUndoSnapshot(mixer))
    }

    fun performUndo(state: PresetGridState, mixer: Mixer) {
        val snapshot = state.popUndoState() ?: return
        ParameterResolver.getAllParameterPaths(mixer).forEach { (path, p) ->
            snapshot.modulatorsByParamKey[path]?.let { savedMods ->
                p.modulators.clear()
                p.modulators.addAll(savedMods.map { it.copy() })
            }
        }
    }
}


