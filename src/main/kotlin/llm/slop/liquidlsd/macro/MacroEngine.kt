package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Runs the Macro Controls evaluation pipeline once per frame, prior to any deck's
 * update()/evaluate() (see proposal §3.2). Mirrors the resolved-binding-cache pattern used by
 * [llm.slop.liquidlsd.midi.MidiMappingManager] (see its `resolvedBindings`/`bindingsDirty`,
 * `rebuildResolvedBindings()`, and `update()`): bindings are re-resolved against live
 * [ModulatableParameter]/[CvModulator] instances only when dirty, and the hot per-frame loop
 * walks a plain `Array` with an indexed for-loop to stay allocation-free.
 *
 * Holds one [MacroBank] per scope: the `null` key is the global, session-scoped Column 3 bank;
 * non-null keys are Rack unit instance ids (see proposal §6 / modular_video_rack_proposal.md).
 */
object MacroEngine {
    private val lock = Any()

    private val banks = LinkedHashMap<String?, MacroBank>().apply {
        put(null, MacroBank())
    }

    /** Registers (or replaces) the bank for [unitInstanceId] (null = global bank) and invalidates the resolved cache. */
    fun registerBank(unitInstanceId: String?, bank: MacroBank) {
        synchronized(lock) {
            banks[unitInstanceId] = bank
        }
        invalidate()
    }

    /** Removes the bank for [unitInstanceId] (e.g. when a rack unit is removed from the bay) and invalidates the resolved cache. */
    fun unregisterBank(unitInstanceId: String?) {
        synchronized(lock) {
            banks.remove(unitInstanceId)
        }
        invalidate()
    }

    /** Returns the global, session-scoped bank, recreating an empty one if it was ever unregistered. */
    fun globalBank(): MacroBank {
        synchronized(lock) {
            return banks.getOrPut(null) { MacroBank() }
        }
    }

    @Volatile
    private var bindingsDirty = true

    /**
     * Marks the resolved-binding cache dirty so it is rebuilt on the next [tick].
     *
     * Known Phase 1 limitation: [registerBank] calls this automatically, but mutating the
     * `bindings` list of a [MacroControl] that belongs to an *already-registered* bank (e.g. the
     * Learn Mode UI in a later phase appending a new [MacroBinding]) does **not** automatically
     * invalidate the cache — there is no change-detection on the mutable list. Callers that mutate
     * bindings in place must call [invalidate] themselves afterwards.
     */
    fun invalidate() {
        bindingsDirty = true
    }

    private class ResolvedBinding(
        val control: MacroControl,
        val binding: MacroBinding,
        val param: ModulatableParameter,
        // Only set (non-null) for MODULATOR_PROPERTY bindings.
        val modulator: CvModulator?
    )

    @Volatile
    private var resolvedBindings: Array<ResolvedBinding> = emptyArray()

    private fun rebuildResolvedBindings(mixer: Mixer) {
        val list = ArrayList<ResolvedBinding>()
        val banksSnapshot = synchronized(lock) { ArrayList(banks.values) }
        for (bank in banksSnapshot) {
            resolveControls(bank.knobs, mixer, list)
            resolveControls(bank.switches, mixer, list)
        }
        resolvedBindings = list.toTypedArray()
        bindingsDirty = false
    }

    private fun resolveControls(controls: List<MacroControl>, mixer: Mixer, out: MutableList<ResolvedBinding>) {
        for (control in controls) {
            for (binding in control.bindings) {
                if (!binding.enabled) continue
                val param = ParameterResolver.findParameterByPath(mixer, binding.parameterId) ?: continue
                val modulator: CvModulator? = if (binding.targetType == MacroTargetType.MODULATOR_PROPERTY) {
                    param.modulators.getOrNull(binding.modulatorIndex) ?: continue
                } else {
                    null
                }
                out.add(ResolvedBinding(control, binding, param, modulator))
            }
        }
    }

    /**
     * Rebuilds the resolved-binding cache if dirty, then evaluates every resolved binding and
     * applies its mapped value to its target field. Must run before any deck's
     * update()/evaluate() so macro-driven base values and modulator properties are in place
     * before CV evaluation reads them for this frame.
     */
    fun tick(mixer: Mixer) {
        if (bindingsDirty) {
            rebuildResolvedBindings(mixer)
        }

        val bindings = resolvedBindings
        for (i in 0 until bindings.size) {
            val rb = bindings[i]
            val mapped = MacroCurve.mapToRange(rb.control.value, rb.binding)
            when (rb.binding.targetType) {
                MacroTargetType.PARAM_BASE_VALUE -> rb.param.baseValue = mapped
                MacroTargetType.MODULATOR_PROPERTY -> {
                    val mod = rb.modulator
                    if (mod != null) {
                        applyModulatorProperty(mod, rb.binding.propertyName, mapped)
                    }
                }
            }
        }

        // Consume any armed one-shot TRIGGER resets, producing the "1-frame pulse" (proposal §3.4).
        val banksSnapshot = synchronized(lock) { ArrayList(banks.values) }
        for (bank in banksSnapshot) {
            val switches = bank.switches
            for (i in switches.indices) {
                switches[i].consumeTriggerReset()
            }
        }
    }

    /** Mutates the matching `var` field on [mod]. Unrecognized property names are a silent no-op — never throw. */
    private fun applyModulatorProperty(mod: CvModulator, propertyName: String, value: Float) {
        when (propertyName) {
            "depth" -> mod.depth = value
            "subdivision" -> mod.subdivision = value
            "phaseOffset" -> mod.phaseOffset = value
            "slope" -> mod.slope = value
            "morph" -> mod.morph = value
            "hold" -> mod.hold = value
            "dcOffset" -> mod.dcOffset = value
            "attackMs" -> mod.attackMs = value
            "decayMs" -> mod.decayMs = value
            "modSubdivision" -> mod.modSubdivision = value
            "modPhaseOffset" -> mod.modPhaseOffset = value
            "modSlope" -> mod.modSlope = value
            "modMorph" -> mod.modMorph = value
            "modHold" -> mod.modHold = value
            "generatorModDepth" -> mod.generatorModDepth = value
            "seqHold" -> mod.seqHold = value
            else -> {}
        }
    }

    /**
     * Looks up enabled bindings from the resolved cache matching [unitInstanceId] and
     * [parameterId] exactly, plus [modulatorIndex]/[propertyName] when non-null. Intended for
     * later-phase UI to answer "is this slider/property locked by a macro?" (see proposal §3.3) —
     * not otherwise used by this phase's engine loop.
     */
    fun findBindingsTargeting(
        unitInstanceId: String?,
        parameterId: String,
        modulatorIndex: Int? = null,
        propertyName: String? = null
    ): List<MacroBinding> {
        val bindings = resolvedBindings
        val result = ArrayList<MacroBinding>()
        for (i in bindings.indices) {
            val b = bindings[i].binding
            if (b.unitInstanceId != unitInstanceId) continue
            if (b.parameterId != parameterId) continue
            if (modulatorIndex != null && b.modulatorIndex != modulatorIndex) continue
            if (propertyName != null && b.propertyName != propertyName) continue
            result.add(b)
        }
        return result
    }
}
