package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXBankDto
import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.TransitionPresetDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.MeterType

import llm.slop.liquidlsd.parameters.ParameterOwner
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry

/**
 * Manages the blending of two Decks (Deck A and Deck B) into a master output FBO.
 * Provides controls for crossfade, master alpha, and blending mode.
 */
class Mixer(
    val deckA: Deck,
    val deckB: Deck,
    val deckBG: Deck,
    val deckPV: Deck,
    var width: Int = 1920,
    var height: Int = 1080
) : ParameterOwner {

    // The master FBO where the final output result is rendered
    var masterFBO = FBO(width, height)

    // FBO for intermediate transition rendering pass when an ISF transition is active
    var blendFBO = FBO(width, height)

    // Intermediate FBO for pre-FX composite output (Deck A + Deck B composited over Deck BG)
    var masterCompositeFBO = FBO(width, height)

    // Master FX bank (3 serial chains x 3 slots)
    val masterFxBank = FxBank("MFX")

    val masterFxWetDry: ModulatableParameter
        get() = masterFxBank.masterWetDry

    val masterFxSlots: Array<ISFFilter?>
        get() = masterFxBank.activeChain.slots

    // Ping-pong pair for Master FX's active chain, plus its dry/wet-blended output:
    var masterFxPingFBO = FBO(width, height)
    var masterFxPongFBO = FBO(width, height)
    var masterFxBankOutFBO = FBO(width, height)

    // The two shared FX banks decks route into (see FxBank). Default assignment mirrors the
    // previous FXQueueManager/FXBgQueueManager split (A/B share one queue, BG has its own) --
    // there's no user-facing bank-assignment toggle yet, so this is fixed for now.
    val fxBank1 = FxBank("FX1")
    val fxBank2 = FxBank("FX2")

    // Active ISF transition filter for crossfading
    var transitionFilter: ISFFilter? = null
    private var lastMode: Int = 4


    /**
     * Sets the active ISF transition filter.
     * Passing null or an empty string resets to the default linear crossfade transition.
     */
    fun setTransition(id: String?) {
        transitionFilter?.dispose()
        val transId = if (!id.isNullOrBlank()) id else "linear_crossfade"
        val filter = llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.createTransition(transId)
        transitionFilter = filter
    }

    /**
     * Applies a transition preset DTO to the active transition filter.
     */
    fun applyTransitionPreset(dto: TransitionPresetDto) {
        setTransition(dto.slot.filterId)
        transitionFilter?.let { trans ->
            trans.enabled = dto.slot.enabled
            trans.dryWet.applyDto(dto.slot.dryWet)
            for ((key, paramDto) in dto.slot.parameters) {
                trans.parameters[key]?.applyDto(paramDto)
            }
        }
    }

    fun resize(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight) return
        width = newWidth
        height = newHeight
        masterFBO.dispose()
        masterFBO = FBO(width, height)
        masterFBO.clear(0f, 0f, 0f, 0f)

        blendFBO.dispose()
        blendFBO = FBO(width, height)
        blendFBO.clear(0f, 0f, 0f, 0f)

        masterCompositeFBO.dispose()
        masterCompositeFBO = FBO(width, height)
        masterCompositeFBO.clear(0f, 0f, 0f, 0f)

        masterFxPingFBO.dispose()
        masterFxPongFBO.dispose()
        masterFxBankOutFBO.dispose()

        masterFxPingFBO = FBO(width, height)
        masterFxPongFBO = FBO(width, height)
        masterFxBankOutFBO = FBO(width, height)

        masterFxPingFBO.clear(0f, 0f, 0f, 0f)
        masterFxPongFBO.clear(0f, 0f, 0f, 0f)
        masterFxBankOutFBO.clear(0f, 0f, 0f, 0f)

        deckA.resize(newWidth, newHeight)
        deckB.resize(newWidth, newHeight)
        deckBG.resize(newWidth, newHeight)
        deckPV.resize(newWidth, newHeight)
    }

    fun toMasterFxSlotDto(slotIndex: Int): FXSlotDto? = masterFxBank.activeChain.toFxSlotDto(slotIndex)

    fun applyMasterFxSlot(slotIndex: Int, dto: FXSlotDto) = masterFxBank.activeChain.applyFxSlot(slotIndex, dto)

    fun clearMasterFxSlot(slotIndex: Int) = masterFxBank.activeChain.clearFxSlot(slotIndex)

    fun applyMasterFxChain(dto: FXChainDto) = masterFxBank.activeChain.applyFxChain(dto)

    fun toMasterFxChainDto(name: String, tags: List<String> = emptyList()): FXChainDto =
        masterFxBank.activeChain.toFxChainDto(name, tags)

    // Blend parameters
    val crossfade = ModulatableParameter(-1.0f, minClamp = -1.0f, maxClamp = 1.0f, meterType = MeterType.BIPOLAR) // -1.0 = Deck A, 1.0 = Deck B
    val xfadeSpeed = ModulatableParameter(5.0f, minClamp = 0.1f, maxClamp = 30.0f)
 
    init {
        setTransition("linear_crossfade")
        for (deck in listOf(deckA, deckB, deckBG, deckPV)) {
            deck.fxBank1 = fxBank1
            deck.fxBank2 = fxBank2
        }
        deckA.fxRouting.baseValue = 1.0f
        deckB.fxRouting.baseValue = 1.0f
        deckBG.fxRouting.baseValue = 2.0f
        deckPV.fxRouting.baseValue = 2.0f
        deckA.fxRoutingDefault = 1.0f
        deckB.fxRoutingDefault = 1.0f
        deckBG.fxRoutingDefault = 2.0f
        deckPV.fxRoutingDefault = 2.0f
        loadDefaultFxBanks()
    }

    private val bankJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * Loads the default starter FX banks for live performance:
     * - FX Bank 1: Psychedelic Warp and Flow
     * - FX Bank 2: Liquid Chrome and Prisms
     * - Master FX Bank: Club Master Finishers
     */
    fun loadDefaultFxBanks() {
        fun loadBank(fileName: String): FXBankDto? {
            val file = java.io.File(llm.slop.liquidlsd.ui.FileSystemManager.getFxBanksRoot(), fileName)
            if (file.exists()) {
                return try {
                    bankJson.decodeFromString<FXBankDto>(file.readText())
                } catch (e: Exception) {
                    null
                }
            }
            val stream = Mixer::class.java.classLoader.getResourceAsStream("default_fx_banks/$fileName")
            if (stream != null) {
                return try {
                    val text = stream.bufferedReader().use { it.readText() }
                    bankJson.decodeFromString<FXBankDto>(text)
                } catch (e: Exception) {
                    null
                }
            }
            return null
        }

        loadBank("psychedelic_warp_and_flow.lsdfxbank")?.let { fxBank1.applyFxBank(it) }
        loadBank("liquid_chrome_and_prisms.lsdfxbank")?.let { fxBank2.applyFxBank(it) }
        loadBank("club_master_finishers.lsdfxbank")?.let { masterFxBank.applyFxBank(it) }
    }

    // Channel level multiplier faders (0.0 to 1.0) -- modulatable so they're macro/CV-bindable
    // from the Performance panel, e.g. as the FX page's per-deck alpha row.
    val levelA = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    val levelB = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    val levelBG = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    val levelPV = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    val masterLevel = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)

    @Volatile var targetCrossfade = -1.0f
    var isAutoFading = false

    /**
     * Mutes all non-MIDI modulators on the crossfader (e.g. when Auto-VJ or auto-fading starts).
     */
    fun muteCrossfadeNonMidiCv() {
        val hasActiveNonMidiMods = crossfade.modulators.any { !it.sourceId.startsWith("midi_cc_") && !it.bypassed }
        if (hasActiveNonMidiMods) {
            val updated = crossfade.modulators.map { mod ->
                if (!mod.sourceId.startsWith("midi_cc_")) mod.copy(bypassed = true) else mod
            }
            crossfade.modulators.clear()
            crossfade.modulators.addAll(updated)
        }
    }

    /**
     * Called when the user manually interacts with the crossfader (via mouse or MIDI).
     * Disarms Auto-VJ, halts active auto-fade transitions, and mutes all non-MIDI CV modulators on crossfade.
     */
    fun onCrossfadeManualTakeover() {
        llm.slop.liquidlsd.presets.PlayQueueManager.isAutoVJEnabled = false
        isAutoFading = false
        muteCrossfadeNonMidiCv()
    }

    /**
     * Called when any CV modulator on the crossfader is unmuted or activated.
     * Snaps crossfade.baseValue to 0.0f (unbiased center) so modulation oscillates symmetrically between decks.
     */
    fun onCrossfadeCvUnmuted() {
        crossfade.baseValue = 0.0f
        if (!crossfade.randomizeBase) {
            crossfade.baseMin = 0.0f
            crossfade.baseMax = 0.0f
        }
        targetCrossfade = 0.0f
    }

    val queuePrev = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f).apply {
        modulatorFilter = { mod ->
            llm.slop.liquidlsd.presets.PlayQueueManager.isAutoVJEnabled || mod.sourceId.startsWith("midi_cc_")
        }
    }
    val queueNext = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f).apply {
        modulatorFilter = { mod ->
            llm.slop.liquidlsd.presets.PlayQueueManager.isAutoVJEnabled || mod.sourceId.startsWith("midi_cc_")
        }
    }

    val bgQueuePrev = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f).apply {
        modulatorFilter = { mod ->
            llm.slop.liquidlsd.presets.BgQueueManager.isAutoBGEnabled || mod.sourceId.startsWith("midi_cc_")
        }
    }
    val bgQueueNext = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f).apply {
        modulatorFilter = { mod ->
            llm.slop.liquidlsd.presets.BgQueueManager.isAutoBGEnabled || mod.sourceId.startsWith("midi_cc_")
        }
    }

    val transQueuePrev = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f).apply {
        modulatorFilter = { mod ->
            llm.slop.liquidlsd.presets.TransitionQueueManager.isAutoAdvanceEnabled || mod.sourceId.startsWith("midi_cc_")
        }
    }
    val transQueueNext = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f).apply {
        modulatorFilter = { mod ->
            llm.slop.liquidlsd.presets.TransitionQueueManager.isAutoAdvanceEnabled || mod.sourceId.startsWith("midi_cc_")
        }
    }

    val tapTempo = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f, isRandomizeDisabled = true).apply {
        modulatorFilter = { mod -> mod.sourceId.startsWith("midi_cc_") }
    }

    val randDeckA = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f, isRandomizeDisabled = true)
    val randDeckB = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f, isRandomizeDisabled = true)
    val randDeckBG = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f, isRandomizeDisabled = true)
    val randDeckPV = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f, isRandomizeDisabled = true)
    val randAll = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f, isRandomizeDisabled = true)

    companion object {
        const val MASTER_FX_SLOT_COUNT = 3
        const val FORBIDDEN_RANDOMIZE_TOOLTIP = "It is forbidden to randomize the randomizer. Chaos would ensue."
        val RANDOMIZER_PARAM_KEYS = setOf(
            "Mixer/randDeckA",
            "Mixer/randDeckB",
            "Mixer/randDeckBG",
            "Mixer/randDeckPV",
            "Mixer/randAll"
        )

        fun isRandomizerParameter(paramKey: String): Boolean = paramKey in RANDOMIZER_PARAM_KEYS
    }

    private var prevQueuePrevVal = 0.0f
    private var prevQueueNextVal = 0.0f
    private var prevBgQueuePrevVal = 0.0f
    private var prevBgQueueNextVal = 0.0f
    private var prevTransQueuePrevVal = 0.0f
    private var prevTransQueueNextVal = 0.0f
    private var prevTapTempoVal = 0.0f
    private var lastUpdateTimeNs: Long = System.nanoTime()

    fun getAllMixerRandomizableParameters(): List<ModulatableParameter> {
        val list = mutableListOf<ModulatableParameter>()
        list.addAll(deckA.getAllRandomizableParameters())
        list.addAll(deckB.getAllRandomizableParameters())
        list.addAll(deckBG.getAllRandomizableParameters())
        list.addAll(deckPV.getAllRandomizableParameters())
        masterFxBank.chains.forEach { chain ->
            if (chain.enabled) {
                list.add(chain.dryWet)
                chain.slots.forEach { fx ->
                    if (fx != null && fx.enabled) {
                        list.add(fx.dryWet)
                        list.addAll(fx.parameters.values)
                    }
                }
            }
        }
        list.add(masterFxWetDry)
        list.add(crossfade)
        list.add(masterLevel)
        return list
    }

    val morphControllerAll = DeckMorphController(::getAllMixerRandomizableParameters)

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        list.add("$prefix/crossfade" to crossfade)
        list.add("$prefix/levelA" to levelA)
        list.add("$prefix/levelB" to levelB)
        list.add("$prefix/levelBG" to levelBG)
        list.add("$prefix/levelPV" to levelPV)
        list.add("$prefix/masterLevel" to masterLevel)
        list.add("$prefix/xfadeSpeed" to xfadeSpeed)
        list.add("$prefix/queuePrev" to queuePrev)
        list.add("$prefix/queueNext" to queueNext)
        list.add("$prefix/bgQueuePrev" to bgQueuePrev)
        list.add("$prefix/bgQueueNext" to bgQueueNext)
        list.add("$prefix/transQueuePrev" to transQueuePrev)
        list.add("$prefix/transQueueNext" to transQueueNext)
        list.add("$prefix/tapTempo" to tapTempo)
        list.add("$prefix/randDeckA" to randDeckA)
        list.add("$prefix/randDeckB" to randDeckB)
        list.add("$prefix/randDeckBG" to randDeckBG)
        list.add("$prefix/randDeckPV" to randDeckPV)
        list.add("$prefix/randAll" to randAll)

        transitionFilter?.let { filter ->
            list.addAll(filter.getParameterPaths("$prefix/Transition"))
        }

        // Master FX paths use the literal "MFX" prefix (its own top-level Parameters tab and
        // macro bank), not $prefix -- unlike everything else here, which is genuinely Mixer-owned.
        list.addAll(masterFxBank.getParameterPaths("MFX"))

        list.addAll(fxBank1.getParameterPaths(fxBank1.label))
        list.addAll(fxBank2.getParameterPaths(fxBank2.label))

        list.addAll(deckA.getParameterPaths("Deck A"))
        list.addAll(deckB.getParameterPaths("Deck B"))
        list.addAll(deckBG.getParameterPaths("Deck BG"))
        list.addAll(deckPV.getParameterPaths("Deck PV"))

        return list
    }

    fun randomizeDeckA() {
        deckA.randomizeModulators()
    }

    fun randomizeDeckB() {
        deckB.randomizeModulators()
    }

    fun randomizeDeckBG() {
        deckBG.randomizeModulators()
    }

    fun randomizeDeckPV() {
        deckPV.randomizeModulators()
    }

    fun randomizeAll() {
        deckA.randomizeModulators()
        deckB.randomizeModulators()
        deckBG.randomizeModulators()
        deckPV.randomizeModulators()
        listOf(crossfade, masterLevel).forEach { param ->
            val randomized = param.modulators.map { it.randomizeActiveValues() }
            param.modulators.clear()
            param.modulators.addAll(randomized)
            param.randomizeBaseValue()
        }
        morphControllerAll.forceRandomize()
    }

    /**
     * Evaluates mixer parameters and background queue transitions.
     */
    fun update() {
        val deltaTime = if (llm.slop.liquidlsd.utils.TimeSource.isSimulated) {
            llm.slop.liquidlsd.utils.TimeSource.getDeltaTimeSec().toFloat()
        } else {
            val now = llm.slop.liquidlsd.utils.TimeSource.getTimeNanos()
            val dt = (now - lastUpdateTimeNs) / 1_000_000_000f
            lastUpdateTimeNs = now
            dt
        }

        if (isAutoFading) {
            val current = crossfade.baseValue
            if (kotlin.math.abs(current - targetCrossfade) < 0.001f) {
                crossfade.baseValue = targetCrossfade
                isAutoFading = false
            } else {
                val durationSec = xfadeSpeed.value.coerceAtLeast(0.1f)
                val step = 2.0f * deltaTime / durationSec
                if (current < targetCrossfade) {
                    crossfade.baseValue = (current + step).coerceAtMost(targetCrossfade)
                } else {
                    crossfade.baseValue = (current - step).coerceAtLeast(targetCrossfade)
                }
            }
        }

        // Update Background Queue transitions
        llm.slop.liquidlsd.presets.BgQueueManager.update(this, deltaTime)

        crossfade.evaluate()
        levelA.evaluate()
        levelB.evaluate()
        levelBG.evaluate()
        levelPV.evaluate()
        masterLevel.evaluate()
        xfadeSpeed.evaluate()
        queuePrev.evaluate()
        queueNext.evaluate()
        bgQueuePrev.evaluate()
        bgQueueNext.evaluate()
        transQueuePrev.evaluate()
        transQueueNext.evaluate()
        tapTempo.evaluate()
        randDeckA.evaluate()
        randDeckB.evaluate()
        randDeckBG.evaluate()
        randDeckPV.evaluate()
        randAll.evaluate()

        transitionFilter?.update()
        masterFxBank.update()
        fxBank1.update()
        fxBank2.update()

        // Continuous random morphing evaluation — zero-allocation check
        val isModA = randDeckA.hasActiveModulator() || randDeckA.value > 0.0001f
        if (isModA) {
            deckA.morphController.update(randDeckA.value)
        }
        val isModB = randDeckB.hasActiveModulator() || randDeckB.value > 0.0001f
        if (isModB) {
            deckB.morphController.update(randDeckB.value)
        }
        val isModBG = randDeckBG.hasActiveModulator() || randDeckBG.value > 0.0001f
        if (isModBG) {
            deckBG.morphController.update(randDeckBG.value)
        }
        val isModPV = randDeckPV.hasActiveModulator() || randDeckPV.value > 0.0001f
        if (isModPV) {
            deckPV.morphController.update(randDeckPV.value)
        }
        val isModAll = randAll.hasActiveModulator() || randAll.value > 0.0001f
        if (isModAll) {
            morphControllerAll.update(randAll.value)
        }
    }

    /**
     * Evaluates if either A/B queue parameter crossed the 0.5 threshold since the last frame.
     * Returns +1 if queueNext was triggered, -1 if queuePrev was triggered, or 0.
     */
    fun pollQueueAdvance(): Int {
        val nextVal = queueNext.value
        val prevVal = queuePrev.value

        var delta = 0
        if (prevQueueNextVal < 0.5f && nextVal >= 0.5f) {
            delta += 1
        }
        if (prevQueuePrevVal < 0.5f && prevVal >= 0.5f) {
            delta -= 1
        }

        prevQueueNextVal = nextVal
        prevQueuePrevVal = prevVal

        if (queueNext.baseValue != 0f) queueNext.baseValue = 0f
        if (queuePrev.baseValue != 0f) queuePrev.baseValue = 0f

        return delta
    }

    /**
     * Evaluates if either BG queue parameter crossed the 0.5 threshold since the last frame.
     * Returns +1 if bgQueueNext was triggered, -1 if bgQueuePrev was triggered, or 0.
     */
    fun pollBgQueueAdvance(): Int {
        val nextVal = bgQueueNext.value
        val prevVal = bgQueuePrev.value

        var delta = 0
        if (prevBgQueueNextVal < 0.5f && nextVal >= 0.5f) {
            delta += 1
        }
        if (prevBgQueuePrevVal < 0.5f && prevVal >= 0.5f) {
            delta -= 1
        }

        prevBgQueueNextVal = nextVal
        prevBgQueuePrevVal = prevVal

        if (bgQueueNext.baseValue != 0f) bgQueueNext.baseValue = 0f
        if (bgQueuePrev.baseValue != 0f) bgQueuePrev.baseValue = 0f

        return delta
    }

    /**
     * Evaluates if either Transition queue parameter crossed the 0.5 threshold since the last frame.
     * Returns +1 if transQueueNext was triggered, -1 if transQueuePrev was triggered, or 0.
     */
    fun pollTransQueueAdvance(): Int {
        val nextVal = transQueueNext.value
        val prevVal = transQueuePrev.value

        var delta = 0
        if (prevTransQueueNextVal < 0.5f && nextVal >= 0.5f) {
            delta += 1
        }
        if (prevTransQueuePrevVal < 0.5f && prevVal >= 0.5f) {
            delta -= 1
        }

        prevTransQueueNextVal = nextVal
        prevTransQueuePrevVal = prevVal

        if (transQueueNext.baseValue != 0f) transQueueNext.baseValue = 0f
        if (transQueuePrev.baseValue != 0f) transQueuePrev.baseValue = 0f

        return delta
    }

    /**
     * Evaluates if the tapTempo parameter crossed the 0.5 threshold on a rising edge since last frame.
     * Returns true if triggered.
     */
    fun pollTapTempo(): Boolean {
        val nextVal = tapTempo.value
        val triggered = prevTapTempoVal < 0.5f && nextVal >= 0.5f
        prevTapTempoVal = nextVal
        if (tapTempo.baseValue != 0f) tapTempo.baseValue = 0f
        return triggered
    }

    /**
     * Synchronizes current queue trigger parameter values into edge-detection trackers.
     * Prevents false 0->1 trigger edge detection on startup / session load.
     */
    fun syncQueueTriggerPrevValues() {
        queueNext.evaluate()
        queuePrev.evaluate()
        bgQueueNext.evaluate()
        bgQueuePrev.evaluate()
        transQueueNext.evaluate()
        transQueuePrev.evaluate()
        tapTempo.evaluate()
        prevQueueNextVal = queueNext.value
        prevQueuePrevVal = queuePrev.value
        prevBgQueueNextVal = bgQueueNext.value
        prevBgQueuePrevVal = bgQueuePrev.value
        prevTransQueueNextVal = transQueueNext.value
        prevTransQueuePrevVal = transQueuePrev.value
        prevTapTempoVal = tapTempo.value
    }

    /**
     * Disposes the master FBO, master composite FBO, FX slots, and transition filter resources.
     */
    fun dispose() {
        masterFBO.dispose()
        blendFBO.dispose()
        masterCompositeFBO.dispose()
        masterFxPingFBO.dispose()
        masterFxPongFBO.dispose()
        masterFxBankOutFBO.dispose()
        masterFxBank.dispose()
        fxBank1.dispose()
        fxBank2.dispose()
        transitionFilter?.dispose()
    }
}
