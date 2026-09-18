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
 * Holds one [MacroBank] per scope, keyed by unit instance id. The five canonical deck/mixer ids
 * ([DECK_A]/[DECK_B]/[DECK_BG]/[DECK_PV]/[TRANS]) are the resident banks Classic's Column 3
 * MACROS tabs and the Rack's per-deck faceplates both read and write directly -- there is no
 * separate "global" bank anymore. Other keys (e.g. a test's ad-hoc [llm.slop.liquidlsd.rack.GenericRackUnit]
 * id) are still supported generically for anything that registers its own bank.
 */
object MacroEngine {
    private val lock = Any()

    const val DECK_A = "deckA"
    const val DECK_B = "deckB"
    const val DECK_BG = "deckBG"
    const val DECK_PV = "deckPV"
    const val TRANS = "masterTransition"

    /** The five always-resident per-deck/mixer bank ids, in Classic-tab/rack-stack display order. */
    val CANONICAL_BANK_IDS = listOf(DECK_A, DECK_B, DECK_BG, DECK_PV, TRANS)

    private val banks = LinkedHashMap<String?, MacroBank>()

    /** Registers (or replaces) the bank for [unitInstanceId] and invalidates the resolved cache. */
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

    /** Returns the bank for [unitInstanceId], or null if not registered. */
    fun getBank(unitInstanceId: String?): MacroBank? {
        synchronized(lock) {
            return banks[unitInstanceId]
        }
    }

    /**
     * Returns the canonical bank whose deck prefix matches the first path segment of
     * [parameterPath] (e.g. "Deck A/fbZoom" -> [DECK_A], "Deck PV/..." -> [DECK_PV],
     * anything else including "Mixer/..." -> [TRANS]). Auto-registers an empty bank the first
     * time a given canonical id is requested, so callers never see a missing bank.
     */
    fun bankForParamPath(parameterPath: String): MacroBank {
        val key = canonicalIdForDeckLabel(parameterPath.substringBefore('/', parameterPath))
        synchronized(lock) {
            return banks.getOrPut(key) { MacroBank() }
        }
    }

    /** Maps a deck label (e.g. "Deck A", or a full path's leading segment) to its canonical bank id. */
    fun canonicalIdForDeckLabel(deckLabel: String): String = when (deckLabel) {
        "Deck A" -> DECK_A
        "Deck B" -> DECK_B
        "Deck BG" -> DECK_BG
        "Deck PV" -> DECK_PV
        else -> TRANS
    }

    /** Reverse lookup: the key a given bank instance is registered under, or null if unregistered. */
    fun keyForBank(bank: MacroBank): String? {
        synchronized(lock) {
            return banks.entries.find { it.value === bank }?.key
        }
    }

    /** Optional resolver for parameters scoped to a rack unit instance. */
    var unitParameterResolver: ((unitInstanceId: String, parameterId: String) -> ModulatableParameter?)? = null

    /** Finds which bank (and optional unitInstanceId) contains the given control ID. */
    fun findBankForControl(controlId: String): Pair<String?, MacroBank>? {
        synchronized(lock) {
            for ((unitId, bank) in banks) {
                if (bank.knobs.any { it.id == controlId } || bank.switches.any { it.id == controlId }) {
                    return Pair(unitId, bank)
                }
            }
        }
        return null
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
        val param: ModulatableParameter
    )

    @Volatile
    private var resolvedBindings: Array<ResolvedBinding> = emptyArray()

    // Snapshot of all registered banks, rebuilt only when [bindingsDirty] (i.e. only on
    // registerBank/unregisterBank/invalidate, not every frame). [tick] reuses this array to
    // consume trigger resets without re-synchronizing/re-copying `banks.values` every frame.
    // Empty until the first tick() rebuilds it (bindingsDirty starts true) -- see rebuildResolvedBindings.
    @Volatile
    private var banksSnapshot: Array<MacroBank> = emptyArray()

    private fun rebuildResolvedBindings(mixer: Mixer) {
        val list = ArrayList<ResolvedBinding>()
        val snapshot = synchronized(lock) { banks.values.toTypedArray() }
        banksSnapshot = snapshot
        for (bank in snapshot) {
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
                val param = if (binding.unitInstanceId != null) {
                    unitParameterResolver?.invoke(binding.unitInstanceId, binding.parameterId)
                        ?: ParameterResolver.findParameterByPath(mixer, binding.parameterId)
                } else {
                    ParameterResolver.findParameterByPath(mixer, binding.parameterId)
                } ?: continue
                if (binding.targetType == MacroTargetType.MODULATOR_PROPERTY &&
                    param.modulators.getOrNull(binding.modulatorIndex) == null) {
                    continue
                }
                out.add(ResolvedBinding(control, binding, param))
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
            val mapped = MacroCurve.mapToRange(effectiveValue(rb.control, rb.binding), rb.binding)
            when (rb.binding.targetType) {
                MacroTargetType.PARAM_BASE_VALUE -> rb.param.baseValue = mapped
                MacroTargetType.MODULATOR_PROPERTY -> {
                    // Re-fetched every tick rather than cached at rebuild time: UI edits to a
                    // modulator (any slider drag/waveform-preset click) replace the CvModulator
                    // instance at this index via `param.modulators[idx] = newMod.copy(...)`
                    // without invalidating this cache, so a cached reference would silently start
                    // mutating an orphaned object the moment the user touched any other control on
                    // the same modulator after binding it.
                    val mod = rb.param.modulators.getOrNull(rb.binding.modulatorIndex)
                    if (mod != null) {
                        applyModulatorProperty(mod, rb.binding.propertyName, mapped)
                    }
                }
            }
        }

        // Per-switch tail: update per-binding override state and consume control-level TRIGGER
        // resets. Reuses the cached snapshot from the last rebuild — no allocation every frame.
        val snapshot = banksSnapshot
        for (i in snapshot.indices) {
            val switches = snapshot[i].switches
            for (j in switches.indices) {
                val ctrl = switches[j]

                // Detect rising edge (rawPressValue went 0→1 this frame).
                val pressEdge = ctrl.rawPressValue >= 0.5f && ctrl.prevRawPressValue < 0.5f
                ctrl.prevRawPressValue = ctrl.rawPressValue

                // Drive per-binding override state machines on press edge.
                for (k in ctrl.bindings.indices) {
                    val b = ctrl.bindings[k]
                    when (b.switchBehaviorOverride) {
                        SwitchBehavior.TOGGLE  -> if (pressEdge) b.bindingLatchState = !b.bindingLatchState
                        SwitchBehavior.TRIGGER -> {
                            if (pressEdge) b.bindingPendingPulse = true
                            // Consume pulse now — effectiveValue() already read it this frame.
                            else b.bindingPendingPulse = false
                        }
                        else -> {}
                    }
                }

                // Existing control-level one-shot TRIGGER reset (unchanged).
                ctrl.consumeTriggerReset()
            }
        }
    }

    /**
     * Returns the normalized [0,1] input value for [binding] on [control].
     *
     * For knob controls, or switch bindings with no override ([MacroBinding.switchBehaviorOverride]
     * == null), this is simply [MacroControl.value] — fully backward-compatible.
     *
     * For switch bindings with an override the behavior is driven by the per-binding state:
     * - **TOGGLE**: returns 1f when [MacroBinding.bindingLatchState] is true, 0f otherwise.
     *   The state is flipped on each rising press edge by the tail loop in [tick].
     * - **MOMENTARY**: returns [MacroControl.rawPressValue] (1f while held, 0f on release).
     * - **TRIGGER**: returns 1f on the single frame that [MacroBinding.bindingPendingPulse] is
     *   armed (set on the rising edge by the tail loop); the tail loop consumes it on the next frame.
     */
    private fun effectiveValue(control: MacroControl, binding: MacroBinding): Float {
        val override = binding.switchBehaviorOverride
        if (!control.isSwitch || override == null) return control.value
        return when (override) {
            SwitchBehavior.TOGGLE    -> if (binding.bindingLatchState) 1f else 0f
            SwitchBehavior.MOMENTARY -> control.rawPressValue
            SwitchBehavior.TRIGGER   -> if (binding.bindingPendingPulse) 1f else 0f
        }
    }

    /** Mutates the matching `var` field on [mod]. Unrecognized property names are a silent no-op — never throw. */
    private fun applyModulatorProperty(mod: CvModulator, propertyName: String, value: Float) {
        when (propertyName) {
            "depth" -> mod.depth = value
            "lfoMin" -> { val max = mod.getLfoMax(); mod.dcOffset = (value + max) / 2f; mod.depth = (max - value) / 2f }
            "lfoMax" -> { val min = mod.getLfoMin(); mod.dcOffset = (min + value) / 2f; mod.depth = (value - min) / 2f }
            "subdivision" -> mod.subdivision = value
            "phaseOffset" -> mod.phaseOffset = value
            "slope" -> mod.slope = value
            "morph" -> mod.morph = value
            "hold" -> mod.hold = value
            "dcOffset" -> mod.dcOffset = value
            "dcOffsetMin" -> mod.dcOffsetMin = value
            "dcOffsetMax" -> mod.dcOffsetMax = value
            "depthMin" -> mod.depthMin = value
            "depthMax" -> mod.depthMax = value
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
     * [parameterId] exactly, plus [modulatorIndex]/[propertyName] when non-null. Used by the
     * Parameters/Properties panels to answer "is this slider/property locked by a macro?" (see
     * proposal §3.3). Called every frame for every rendered parameter row/modulator slot, so the
     * overwhelmingly common case (a field with no macro binding at all) must not allocate: only
     * materializes an [ArrayList] once an actual match is found, returning the shared
     * [emptyList] singleton otherwise.
     */
    fun findBindingsTargeting(
        unitInstanceId: String?,
        parameterId: String,
        modulatorIndex: Int? = null,
        propertyName: String? = null
    ): List<MacroBinding> {
        val bindings = resolvedBindings
        var result: ArrayList<MacroBinding>? = null
        for (i in bindings.indices) {
            val b = bindings[i].binding
            if (b.unitInstanceId != unitInstanceId) continue
            if (b.parameterId != parameterId) continue
            if (modulatorIndex != null && b.modulatorIndex != modulatorIndex) continue
            if (propertyName != null && b.propertyName != propertyName) continue
            (result ?: ArrayList<MacroBinding>(4).also { result = it }).add(b)
        }
        return result ?: emptyList()
    }

    /**
     * Finds the primary (first active) [MacroBindingInfo] targeting the specified parameter
     * (and optional modulator property), resolving the owning bank and control for UI rendering.
     * Returns null if not bound.
     */
    fun findPrimaryBindingInfo(
        unitInstanceId: String?,
        parameterId: String,
        modulatorIndex: Int? = null,
        propertyName: String? = null
    ): MacroBindingInfo? {
        val bindings = findBindingsTargeting(unitInstanceId, parameterId, modulatorIndex, propertyName)
        if (bindings.isEmpty()) return null
        val targetBinding = bindings.first()

        // Search every registered bank for the control owning this binding -- unitInstanceId
        // describes the binding's *target* scope, not which bank the knob/switch itself lives in,
        // so it can't be used to pick a single bank to look in (a Deck A bank can perfectly well
        // hold a binding whose unitInstanceId is null, or vice versa).
        val allBanks = synchronized(lock) { banks.values.toList() }
        for (bank in allBanks) {
            val knobIdx = bank.knobs.indexOfFirst { it.bindings.contains(targetBinding) }
            if (knobIdx >= 0) {
                val ctrl = bank.knobs[knobIdx]
                val badge = "K${knobIdx + 1}"
                val name = if (ctrl.label.isNotBlank()) ctrl.label else "Knob ${knobIdx + 1}"
                return MacroBindingInfo(targetBinding, ctrl, isKnob = true, index = knobIdx, badgeLabel = badge, controlName = name)
            }

            val swIdx = bank.switches.indexOfFirst { it.bindings.contains(targetBinding) }
            if (swIdx >= 0) {
                val ctrl = bank.switches[swIdx]
                val badge = "SW${swIdx + 1}"
                val name = if (ctrl.label.isNotBlank()) ctrl.label else "Switch ${swIdx + 1}"
                return MacroBindingInfo(targetBinding, ctrl, isKnob = false, index = swIdx, badgeLabel = badge, controlName = name)
            }
        }

        return null
    }
}
