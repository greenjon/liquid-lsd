package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ParameterResolver

object ParametersUndo {
    fun createUndoSnapshot(mixer: Mixer): ParametersUndoSnapshot {
        val mods = mutableMapOf<String, List<CvModulator>>()
        ParameterResolver.getAllParameterPaths(mixer).forEach { (path, p) ->
            mods[path] = p.modulators.map { it.copy() }
        }
        return ParametersUndoSnapshot(mods)
    }

    fun pushUndoState(state: ParametersState, mixer: Mixer) {
        state.pushUndoState(createUndoSnapshot(mixer))
    }

    fun performUndo(state: ParametersState, mixer: Mixer) {
        val snapshot = state.popUndoState() ?: return
        ParameterResolver.getAllParameterPaths(mixer).forEach { (path, p) ->
            snapshot.modulatorsByParamKey[path]?.let { savedMods ->
                p.modulators.clear()
                p.modulators.addAll(savedMods.map { it.copy() })
            }
        }
    }
}


