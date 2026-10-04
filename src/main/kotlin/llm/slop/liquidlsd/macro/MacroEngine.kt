package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ModulatorPropertyAccessor
import llm.slop.liquidlsd.parameters.ParameterResolver
import mu.KotlinLogging
import llm.slop.liquidlsd.rendering.Deck
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
 * Holds one [MacroBank] per scope, keyed by a canonical bank id. The canonical ids
 * ([CANONICAL_BANK_IDS]) are always-resident banks that the Performance Mode 4×4 Matrix (rows,
 * Edit-row macro strips) and the hardware controller read and write directly. [GLOBAL] is the one bank not scoped to a deck or
 * section: its knobs may bind to any parameter. Registration and persistence is handled entirely by
 * [llm.slop.liquidlsd.presets.SessionSerializer]. Other keys are still supported generically for
 * anything that registers its own bank.
 */
object MacroEngine {
    private val logger = KotlinLogging.logger {}
    private val lock = Any()

    const val DECK_A = "deckA"
    const val DECK_B = "deckB"
    const val DECK_BG = "deckBG"
    const val DECK_PV = "deckPV"
    const val DECK_A_FX = "deckA_fx"
    const val DECK_B_FX = "deckB_fx"
    const val DECK_BG_FX = "deckBG_fx"
    const val DECK_PV_FX = "deckPV_fx"
    const val TRANS = "masterTransition"
    const val MASTER = "master"
    // FX_SENDS holds one knob per deck's fxSendLevel (A/B/BG/PV); it has no natural path prefix to
    // auto-route quick-bind into (each knob targets a different deck), so it's reachable only via
    // the knob-first Learn flow. MASTER_FX is the Master FX chain's row, auto-bound by FxMacroSync
    // to "Master/FX/..." exactly like the deck FX banks.
    const val FX_SENDS = "fxSends"
    const val MASTER_FX = "masterFx"
    // GLOBAL: free, non-section-scoped knobs (see MacroLearnState.sectionFor) -- one knob can drive
    // parameters on several decks at once. Currently 0 knobs: the Clock row's 4 knobs were removed for v1.0
    // and a configurable "global knobs" row is planned for v1.1. The bank stays registered so that work (and
    // old sessions/OSC/MIDI mappings, which just find no knobs) needs no migration.
    const val GLOBAL = "global"

    /** The always-resident per-deck/mixer/FX-bank bank ids, in display order. */
    val CANONICAL_BANK_IDS = listOf(
        DECK_A, DECK_B, DECK_BG, DECK_PV,
        DECK_A_FX, DECK_B_FX, DECK_BG_FX, DECK_PV_FX,
        TRANS, MASTER, FX_SENDS, MASTER_FX, GLOBAL
    )

    /** The canonical bank id for [deck] on [mixer], or null if not recognized. */
    fun deckBankIdFor(deck: Deck, mixer: Mixer): String? = when {
        deck === mixer.deckA -> DECK_A
        deck === mixer.deckB -> DECK_B
        deck === mixer.deckBG -> DECK_BG
        deck === mixer.deckPV -> DECK_PV
        else -> null
    }

    /**
     * Knob count for a freshly auto-vivified bank. All canonical banks ([CANONICAL_BANK_IDS]) hold 4 knobs,
     * conforming to the 4-column performance grid, except [GLOBAL], which is empty for now (see [GLOBAL]).
     */
    fun defaultKnobCountFor(bankId: String?): Int = if (bankId == GLOBAL) 0 else 4

    /** Builds a fresh, correctly-sized, blank-labeled or pre-bound default bank for [bankId]. */
    fun newBankFor(bankId: String?): MacroBank = when (bankId) {
        MASTER -> MacroBank(
            knobs = listOf(
                MacroControl(
                    label = "ALPHA A",
                    value = 1.0f,
                    bindings = mutableListOf(
                        MacroBinding(
                            parameterId = "Mixer/levelA",
                            targetType = MacroTargetType.PARAM_BASE_VALUE,
                            minVal = 0.0f,
                            maxVal = 1.0f
                        )
                    )
                ),
                MacroControl(
                    label = "ALPHA B",
                    value = 1.0f,
                    bindings = mutableListOf(
                        MacroBinding(
                            parameterId = "Mixer/levelB",
                            targetType = MacroTargetType.PARAM_BASE_VALUE,
                            minVal = 0.0f,
                            maxVal = 1.0f
                        )
                    )
                ),
                MacroControl(
                    label = "ALPHA BG",
                    value = 0.0f,
                    bindings = mutableListOf(
                        MacroBinding(
                            parameterId = "Mixer/levelBG",
                            targetType = MacroTargetType.PARAM_BASE_VALUE,
                            minVal = 0.0f,
                            maxVal = 1.0f
                        )
                    )
                ),
                MacroControl(
                    label = "MASTER",
                    value = 1.0f,
                    bindings = mutableListOf(
                        MacroBinding(
                            parameterId = "Mixer/masterLevel",
                            targetType = MacroTargetType.PARAM_BASE_VALUE,
                            minVal = 0.0f,
                            maxVal = 1.0f
                        )
                    )
                )
            )
        )
        FX_SENDS -> MacroBank(
            knobs = listOf(
                MacroControl(
                    label = "Deck A",
                    value = 1.0f,
                    bindings = mutableListOf(
                        MacroBinding(
                            parameterId = "Deck A/FXChain/DryWet",
                            targetType = MacroTargetType.PARAM_BASE_VALUE,
                            minVal = 0.0f,
                            maxVal = 1.0f
                        )
                    )
                ),
                MacroControl(
                    label = "Deck B",
                    value = 1.0f,
                    bindings = mutableListOf(
                        MacroBinding(
                            parameterId = "Deck B/FXChain/DryWet",
                            targetType = MacroTargetType.PARAM_BASE_VALUE,
                            minVal = 0.0f,
                            maxVal = 1.0f
                        )
                    )
                ),
                MacroControl(
                    label = "Deck BG",
                    value = 1.0f,
                    bindings = mutableListOf(
                        MacroBinding(
                            parameterId = "Deck BG/FXChain/DryWet",
                            targetType = MacroTargetType.PARAM_BASE_VALUE,
                            minVal = 0.0f,
                            maxVal = 1.0f
                        )
                    )
                ),
                MacroControl(
                    label = "Deck PV",
                    value = 1.0f,
                    bindings = mutableListOf(
                        MacroBinding(
                            parameterId = "Deck PV/FXChain/DryWet",
                            targetType = MacroTargetType.PARAM_BASE_VALUE,
                            minVal = 0.0f,
                            maxVal = 1.0f
                        )
                    )
                )
            )
        )
        else -> MacroBank(knobs = List(defaultKnobCountFor(bankId)) { MacroControl(label = "KNOB ${it + 1}") })
    }

    private val banks = LinkedHashMap<String?, MacroBank>()

    /**
     * Bumped whenever a bank is replaced or loaded wholesale (register/unregister, preset or default install),
     * as opposed to edited by hand. `ui/MacroUndoTracker` treats a bump as "not a user edit": it re-baselines
     * instead of recording an undo step.
     */
    @Volatile
    var bankReplaceEpoch: Long = 0L
        private set

    fun noteBankReplaced() { bankReplaceEpoch++ }

    /** Registers (or replaces) the bank for [unitInstanceId] and invalidates the resolved cache. */
    fun registerBank(unitInstanceId: String?, bank: MacroBank) {
        synchronized(lock) {
            banks[unitInstanceId] = bank
        }
        noteBankReplaced()
        invalidate()
    }

    /** Removes the bank for [unitInstanceId] and invalidates the resolved cache. */
    fun unregisterBank(unitInstanceId: String?) {
        synchronized(lock) {
            banks.remove(unitInstanceId)
        }
        noteBankReplaced()
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
        "Deck A", DECK_A -> DECK_A
        "Deck B", DECK_B -> DECK_B
        "Deck BG", DECK_BG -> DECK_BG
        "Deck PV", DECK_PV -> DECK_PV
        "Deck A FX", "Deck A/FX", DECK_A_FX -> DECK_A_FX
        "Deck B FX", "Deck B/FX", DECK_B_FX -> DECK_B_FX
        "Deck BG FX", "Deck BG/FX", DECK_BG_FX -> DECK_BG_FX
        "Deck PV FX", "Deck PV/FX", DECK_PV_FX -> DECK_PV_FX
        "Master", "MST", MASTER -> MASTER
        "TRANS", "Transition", TRANS -> TRANS
        "FX Sends", "FX_SENDS", FX_SENDS -> FX_SENDS
        "Master FX", "Master/FX", MASTER_FX -> MASTER_FX
        "Global", "GLB", GLOBAL -> GLOBAL
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
        baseBindingCache = java.util.concurrent.ConcurrentHashMap()
    }

    // paramKey -> base-value bindings targeting it. Replaced wholesale (never cleared in place) so a
    // reader racing an invalidate can only write into the discarded map.
    @Volatile
    private var baseBindingCache = java.util.concurrent.ConcurrentHashMap<String, List<MacroBindingInfo>>()

    /**
     * Cached, allocation-free-on-hit lookup of every macro binding that drives [paramKey]'s base value,
     * for per-cell-per-frame UI use (param grid arcs). Invalidated by [invalidate] and on rebuild.
     */
    fun baseBindingInfos(paramKey: String): List<MacroBindingInfo> {
        val cache = baseBindingCache
        return cache.getOrPut(paramKey) {
            findBindingInfos(null, paramKey).filter { it.binding.targetType == MacroTargetType.PARAM_BASE_VALUE }
        }
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
        baseBindingCache = java.util.concurrent.ConcurrentHashMap()
        bindingsDirty = false
    }

    /**
     * One-time upgrade of a pre-id binding: pins its saved list position to the modulator's stable
     * id (modulators without a saved id received one on load) and rewrites the control's binding.
     */
    private fun migrateLegacyModulatorIndex(control: MacroControl, binding: MacroBinding, param: ModulatableParameter): MacroBinding {
        val legacy = binding.modulatorIndex ?: return binding
        if (binding.modulatorId != null) return binding.copy(modulatorIndex = null).also { replaceBinding(control, binding, it) }
        val id = param.modulators.getOrNull(legacy)?.id
        val upgraded = binding.copy(modulatorId = id, modulatorIndex = null)
        replaceBinding(control, binding, upgraded)
        return upgraded
    }

    private fun replaceBinding(control: MacroControl, old: MacroBinding, new: MacroBinding) {
        val i = control.bindings.indexOf(old)
        if (i >= 0) control.bindings[i] = new
    }

    private fun resolveControls(controls: List<MacroControl>, mixer: Mixer, out: MutableList<ResolvedBinding>) {
        for (control in controls) {
            for (binding in control.bindings) {
                if (!binding.enabled) continue
                val param = ParameterResolver.findParameterByPath(mixer, binding.parameterId) ?: continue
                var resolved = binding
                if (binding.targetType == MacroTargetType.MODULATOR_PROPERTY) {
                    resolved = migrateLegacyModulatorIndex(control, binding, param)
                    if (ModulatorPropertyAccessor.findById(param, resolved.modulatorId) == null) {
                        logger.warn { "Macro binding on '${binding.parameterId}' targets missing modulator ${resolved.modulatorId}; skipped" }
                        continue
                    }
                }
                out.add(ResolvedBinding(control, resolved, param))
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
                    val mod = ModulatorPropertyAccessor.findById(rb.param, rb.binding.modulatorId)
                    if (mod != null) {
                        ModulatorPropertyAccessor.set(mod, rb.binding.propertyName, mapped)
                    }
                }
            }
        }

        // Keep linked FX slot macro knobs visually in sync with their slot's Metaknob / Super Knob
        syncLinkedFxChainKnobValues(DECK_A_FX, mixer.deckA.fxChain)
        syncLinkedFxChainKnobValues(DECK_B_FX, mixer.deckB.fxChain)
        syncLinkedFxChainKnobValues(DECK_BG_FX, mixer.deckBG.fxChain)
        syncLinkedFxChainKnobValues(DECK_PV_FX, mixer.deckPV.fxChain)
        syncLinkedFxChainKnobValues(MASTER_FX, mixer.masterFxChain)

        broadcastChangedKnobsToOsc()
    }

    // Last value broadcast to OSC listeners per canonical "<bankId>/<knobIndex>" key, so any
    // write path (GUI drag, MIDI, FX-macro-sync above) reaches TouchOSC-style bidirectional
    // feedback uniformly without instrumenting every call site. Inbound OSC writes land in
    // the bank via MacroOscBridge.handleOscMessage before this runs, so they're already
    // reflected in the cache and won't re-broadcast themselves.
    private val lastBroadcastValues = HashMap<String, Float>()

    private fun broadcastChangedKnobsToOsc() {
        for (bankId in CANONICAL_BANK_IDS) {
            val bank = synchronized(lock) { banks[bankId] } ?: continue
            for (i in bank.knobs.indices) {
                val value = bank.knobs[i].value
                val cacheKey = "$bankId/$i"
                if (lastBroadcastValues[cacheKey] != value) {
                    lastBroadcastValues[cacheKey] = value
                    MacroOscBridge.broadcast(MacroOscBridge.getKnobAddress(bankId, i), value)
                }
            }
        }
    }

    private fun syncLinkedFxChainKnobValues(bankId: String, chain: FxChain) {
        val macroBank = synchronized(lock) { banks[bankId] } ?: return
        val focused = chain.focusedSlot
        if (focused != null) {
            // Focus mode: knob 0 is the focused slot's Metaknob; mirror it when that slot is linked.
            if (chain.slotSuperKnobLink.getOrNull(focused) == true) {
                macroBank.knobs.getOrNull(0)?.value = chain.slots.getOrNull(focused)?.metaKnob?.baseValue ?: chain.superKnob.baseValue
            }
            return
        }
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
     * [parameterId] exactly, plus [modulatorId]/[propertyName] when non-null. Used by the
     * Parameters/Properties panels to answer "is this slider/property locked by a macro?" (see
     * proposal §3.3). Called every frame for every rendered parameter row/modulator slot, so the
     * overwhelmingly common case (a field with no macro binding at all) must not allocate: only
     * materializes an [ArrayList] once an actual match is found, returning the shared
     * [emptyList] singleton otherwise.
     */
    fun findBindingsTargeting(
        unitInstanceId: String?,
        parameterId: String,
        modulatorId: String? = null,
        propertyName: String? = null
    ): List<MacroBinding> {
        val bindings = resolvedBindings
        var result: ArrayList<MacroBinding>? = null
        for (i in bindings.indices) {
            val b = bindings[i].binding
            if (b.unitInstanceId != unitInstanceId) continue
            if (b.parameterId != parameterId) continue
            if (modulatorId != null && b.modulatorId != modulatorId) continue
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
        modulatorId: String? = null,
        propertyName: String? = null
    ): MacroBindingInfo? =
        findBindingsTargeting(unitInstanceId, parameterId, modulatorId, propertyName)
            .firstOrNull()?.let { infoFor(it) }

    /** Registered bank id owning the control with [controlId], or null. */
    fun bankKeyOfControl(controlId: String): String? = synchronized(lock) {
        banks.entries.firstOrNull { (_, bank) -> bank.knobs.any { it.id == controlId } }?.key
    }

    /**
     * Every [MacroBindingInfo] targeting the specified parameter (and optional modulator property).
     * More than one macro knob can bind the same target, so editors list each one.
     */
    fun findBindingInfos(
        unitInstanceId: String?,
        parameterId: String,
        modulatorId: String? = null,
        propertyName: String? = null
    ): List<MacroBindingInfo> {
        val bindings = findBindingsTargeting(unitInstanceId, parameterId, modulatorId, propertyName)
        if (bindings.isEmpty()) return emptyList()
        return bindings.mapNotNull { infoFor(it) }
    }

    /**
     * Resolves the owning control for [binding]. Searches every registered bank -- unitInstanceId
     * describes the binding's *target* scope, not which bank the knob itself lives in, so it
     * can't be used to pick a single bank to look in.
     */
    private fun infoFor(binding: MacroBinding): MacroBindingInfo? {
        val allBanks = synchronized(lock) { banks.entries.toList() }
        for ((key, bank) in allBanks) {
            val knobIdx = bank.knobs.indexOfFirst { it.bindings.contains(binding) }
            if (knobIdx >= 0) {
                val ctrl = bank.knobs[knobIdx]
                val name = if (ctrl.label.isNotBlank()) ctrl.label else "Knob ${knobIdx + 1}"
                return MacroBindingInfo(binding, ctrl, index = knobIdx, badgeLabel = "K${knobIdx + 1}", controlName = name, bankKey = key)
            }
        }
        return null
    }
}
