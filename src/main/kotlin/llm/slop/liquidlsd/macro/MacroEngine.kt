package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.FxBank
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Runs the Macro Controls evaluation pipeline once per frame, prior to any deck's
 * update()/evaluate() (see proposal §3.2). Mirrors the resolved-binding-cache pattern used by
 * [llm.slop.liquidlsd.midi.MidiMappingManager] (see its `resolvedBindings`/`bindingsDirty`,
 * `rebuildResolvedBindings()`, and `update()`): bindings are re-resolved against live
 * [ModulatableParameter]/[CvModulator] instances only when dirty, and the hot per-frame loop
 * walks a plain `Array` with an indexed for-loop to stay allocation-free.
 *
 * Holds one [MacroBank] per scope, keyed by a canonical bank id. The six canonical deck/mixer ids
 * ([DECK_A]/[DECK_B]/[DECK_BG]/[DECK_PV]/[TRANS]/[MASTER]) are always-resident banks that
 * Classic's Column 3 MACROS tab and the Performance Mode 4×4 Matrix both read and write directly —
 * there is no separate "global" bank. Registration and persistence is handled entirely by
 * [llm.slop.liquidlsd.presets.SessionSerializer]. Other keys are still supported generically for
 * anything that registers its own bank.
 */
object MacroEngine {
    private val lock = Any()

    const val DECK_A = "deckA"
    const val DECK_B = "deckB"
    const val DECK_BG = "deckBG"
    const val DECK_PV = "deckPV"
    const val TRANS = "masterTransition"
    const val MASTER = "master"
    const val FX_BANK_1 = "fxBank1"
    const val FX_BANK_2 = "fxBank2"
    // Blank 4-knob banks for the FX Performance page's remaining two rows: FX_SENDS holds one
    // knob per deck's fxSendLevel (A/B/BG/PV), MASTER_FX holds Mixer.masterFxSlots' 3 chain
    // knobs + wet/dry. Neither has a natural path prefix to auto-route quick-bind into (a send
    // knob's target deck varies per knob, and master FX already has its own "$prefix/FX..."
    // paths under "Master" -- see Mixer.getParameterPaths), so both stay reachable only via the
    // knob-first Learn flow (arm the knob, then click the target parameter row), same as any
    // other macro knob.
    const val FX_SENDS = "fxSends"
    const val MASTER_FX = "masterFx"

    /** The always-resident per-deck/mixer/FX-bank bank ids, in display order. */
    val CANONICAL_BANK_IDS = listOf(DECK_A, DECK_B, DECK_BG, DECK_PV, TRANS, MASTER, FX_BANK_1, FX_BANK_2, FX_SENDS, MASTER_FX)

    /**
     * Knob count for a freshly auto-vivified bank. Per-deck banks hold 4 generation-only knobs
     * now that FX macros live on the shared FX_BANK_1/FX_BANK_2 banks (see FxBank); those two FX
     * banks, plus FX_SENDS and MASTER_FX, also get 4. TRANS/MASTER and any non-canonical (e.g.
     * future Rack unit) id keep the original 8.
     */
    fun defaultKnobCountFor(bankId: String?): Int = when (bankId) {
        DECK_A, DECK_B, DECK_BG, DECK_PV, FX_BANK_1, FX_BANK_2, FX_SENDS, MASTER_FX -> 4
        else -> 8
    }

    /** Builds a fresh, correctly-sized, blank-labeled bank for [bankId]. */
    fun newBankFor(bankId: String?): MacroBank =
        MacroBank(knobs = List(defaultKnobCountFor(bankId)) { MacroControl(label = "KNOB ${it + 1}") })

    private val banks = LinkedHashMap<String?, MacroBank>()

    /** Registers (or replaces) the bank for [unitInstanceId] and invalidates the resolved cache. */
    fun registerBank(unitInstanceId: String?, bank: MacroBank) {
        synchronized(lock) {
            banks[unitInstanceId] = bank
        }
        invalidate()
    }

    /** Removes the bank for [unitInstanceId] and invalidates the resolved cache. */
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
            return banks.getOrPut(key) { newBankFor(key) }
        }
    }

    /** Maps a deck label (e.g. "Deck A", or a full path's leading segment) to its canonical bank id. */
    fun canonicalIdForDeckLabel(deckLabel: String): String = when (deckLabel) {
        "Deck A" -> DECK_A
        "Deck B" -> DECK_B
        "Deck BG" -> DECK_BG
        "Deck PV" -> DECK_PV
        "Master" -> MASTER
        "Bank 1", "FX1" -> FX_BANK_1
        "Bank 2", "FX2" -> FX_BANK_2
        "MFX" -> MASTER_FX
        else -> TRANS
    }

    /** Reverse lookup: the key a given bank instance is registered under, or null if unregistered. */
    fun keyForBank(bank: MacroBank): String? {
        synchronized(lock) {
            return banks.entries.find { it.value === bank }?.key
        }
    }


    /** Finds which bank (and optional unitInstanceId) contains the given control ID. */
    fun findBankForControl(controlId: String): Pair<String?, MacroBank>? {
        synchronized(lock) {
            for ((unitId, bank) in banks) {
                if (bank.knobs.any { it.id == controlId }) {
                    return Pair(unitId, bank)
                }
            }
        }
        return null
    }

    /**
     * Resolves the "Macro/&lt;bankId&gt;/knob_N" MIDI mapping path for [control] within [bank],
     * matching the format [llm.slop.liquidlsd.midi.MidiMappingManager.onMidiEvent] dispatches
     * against. Returns null if [bank] isn't currently registered or [control] isn't found in it.
     */
    fun midiPathFor(bank: MacroBank, control: MacroControl): String? {
        val bankId = keyForBank(bank) ?: return null
        val knobIdx = bank.knobs.indexOf(control)
        if (knobIdx < 0) return null
        return "Macro/$bankId/knob_${knobIdx + 1}"
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

    private fun rebuildResolvedBindings(mixer: Mixer) {
        val list = ArrayList<ResolvedBinding>()
        val snapshot = synchronized(lock) { banks.values.toTypedArray() }
        for (bank in snapshot) {
            resolveControls(bank.knobs, mixer, list)
        }
        resolvedBindings = list.toTypedArray()
        bindingsDirty = false
    }

    private fun resolveControls(controls: List<MacroControl>, mixer: Mixer, out: MutableList<ResolvedBinding>) {
        for (control in controls) {
            for (binding in control.bindings) {
                if (!binding.enabled) continue
                val param = ParameterResolver.findParameterByPath(mixer, binding.parameterId) ?: continue
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
            val mapped = MacroCurve.mapToRange(rb.control.value, rb.binding)
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
                        llm.slop.liquidlsd.parameters.ModulatorPropertyAccessor.set(mod, rb.binding.propertyName, mapped)
                    }
                }
            }
        }

        // Keep linked FX slot macro knobs visually in sync with their slot's Metaknob / Super Knob
        syncLinkedFxKnobValues(FX_BANK_1, mixer.fxBank1)
        syncLinkedFxKnobValues(FX_BANK_2, mixer.fxBank2)
        syncLinkedFxKnobValues(MASTER_FX, mixer.masterFxBank)
    }

    private fun syncLinkedFxKnobValues(bankId: String, fxBank: FxBank) {
        val macroBank = synchronized(lock) { banks[bankId] } ?: return
        val chain = fxBank.activeChain
        for (i in 0 until FxChain.SLOT_COUNT) {
            if (chain.slotSuperKnobLink.getOrNull(i) == true) {
                val knob = macroBank.knobs.getOrNull(i + 1) ?: continue
                val slot = chain.slots.getOrNull(i)
                knob.value = slot?.metaKnob?.baseValue ?: chain.superKnob.baseValue
            }
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
        // describes the binding's *target* scope, not which bank the knob itself lives in, so it
        // can't be used to pick a single bank to look in (a Deck A bank can perfectly well hold a
        // binding whose unitInstanceId is null, or vice versa).
        val allBanks = synchronized(lock) { banks.values.toList() }
        for (bank in allBanks) {
            val knobIdx = bank.knobs.indexOfFirst { it.bindings.contains(targetBinding) }
            if (knobIdx >= 0) {
                val ctrl = bank.knobs[knobIdx]
                val badge = "K${knobIdx + 1}"
                val name = if (ctrl.label.isNotBlank()) ctrl.label else "Knob ${knobIdx + 1}"
                return MacroBindingInfo(targetBinding, ctrl, index = knobIdx, badgeLabel = badge, controlName = name)
            }
        }

        return null
    }
}
