package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ParameterResolver

object ParametersUndo {
    fun createUndoSnapshot(mixer: Mixer, restore: (() -> Unit)? = null): ParametersUndoSnapshot {
        val mods = mutableMapOf<String, List<CvModulator>>()
        ParameterResolver.getAllParameterPaths(mixer).forEach { (path, p) ->
            mods[path] = p.modulators.map { it.copy() }
        }
        return ParametersUndoSnapshot(mods, restore)
    }

    fun pushUndoState(state: ParametersState, mixer: Mixer, restore: (() -> Unit)? = null) {
        state.pushUndoState(createUndoSnapshot(mixer, restore))
    }

    /**
     * The undo step for a randomize: besides the modulators, puts back every base value and macro knob position the
     * dice may move. A genome source (Fractal Flame) keeps its parameters, since they mean nothing without the genome.
     */
    fun pushRandomizeUndo(state: ParametersState, mixer: Mixer) {
        val genomeParams = listOf(mixer.deckA, mixer.deckB, mixer.deckBG, mixer.deckPV)
            .filter { it.source is llm.slop.liquidlsd.rendering.GenomeMorph }
            .flatMapTo(HashSet()) { it.source.parameters.values }
        val bases = ParameterResolver.getAllParameterPaths(mixer)
            .filter { (_, p) -> p !in genomeParams }
            .associate { (_, p) -> p to p.baseValue }
        val knobs = llm.slop.liquidlsd.macro.MacroEngine.CANONICAL_BANK_IDS
            .mapNotNull { llm.slop.liquidlsd.macro.MacroEngine.getBank(it) }
            .flatMap { it.knobs }
            .associateWith { it.value }
        pushUndoState(state, mixer) {
            knobs.forEach { (knob, v) -> knob.value = v }
            bases.forEach { (p, v) -> p.baseValue = v }
        }
    }

    fun performUndo(state: ParametersState, mixer: Mixer) {
        val snapshot = state.popUndoState() ?: return
        snapshot.restore?.invoke()
        ParameterResolver.getAllParameterPaths(mixer).forEach { (path, p) ->
            snapshot.modulatorsByParamKey[path]?.let { savedMods ->
                p.modulators.clear()
                p.modulators.addAll(savedMods.map { it.copy() })
            }
        }
    }
}


