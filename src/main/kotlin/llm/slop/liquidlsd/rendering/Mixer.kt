package llm.slop.liquidlsd.rendering

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

    // Master FX: one post-crossfader chain of 3 serial slots, same model as each deck's fxChain
    val masterFxChain = FxChain("Master FX")

    val masterFxSlots: Array<ISFFilter?>
        get() = masterFxChain.slots

    // Ping-pong pair for the Master FX chain's slots, plus its dry/wet-blended output:
    var masterFxPingFBO = FBO(width, height)
    var masterFxPongFBO = FBO(width, height)
    var masterFxOutFBO = FBO(width, height)

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
        masterFxOutFBO.dispose()

        masterFxPingFBO = FBO(width, height)
        masterFxPongFBO = FBO(width, height)
        masterFxOutFBO = FBO(width, height)

        masterFxPingFBO.clear(0f, 0f, 0f, 0f)
        masterFxPongFBO.clear(0f, 0f, 0f, 0f)
        masterFxOutFBO.clear(0f, 0f, 0f, 0f)

        deckA.resize(newWidth, newHeight)
        deckB.resize(newWidth, newHeight)
        deckBG.resize(newWidth, newHeight)
        deckPV.resize(newWidth, newHeight)
    }

    fun toMasterFxSlotDto(slotIndex: Int): FXSlotDto? = masterFxChain.toFxSlotDto(slotIndex)

    fun applyMasterFxSlot(slotIndex: Int, dto: FXSlotDto) = masterFxChain.applyFxSlot(slotIndex, dto)

    fun clearMasterFxSlot(slotIndex: Int) = masterFxChain.clearFxSlot(slotIndex)

    fun applyMasterFxChain(dto: FXChainDto) = masterFxChain.applyFxChain(dto)

    fun toMasterFxChainDto(name: String, tags: List<String> = emptyList()): FXChainDto =
        masterFxChain.toFxChainDto(name, tags)

    // Blend parameters
    val crossfade = ModulatableParameter(-1.0f, minClamp = -1.0f, maxClamp = 1.0f, meterType = MeterType.BIPOLAR) // -1.0 = Deck A, 1.0 = Deck B
    val xfadeSpeed = ModulatableParameter(5.0f, minClamp = 0.1f, maxClamp = 30.0f)
 
    init {
        setTransition("linear_crossfade")
        loadDefaultFxChains()
    }

    private val chainJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * Loads the starter FX chains: "Subtle Optical Warmth" on Master FX, and a distinct bundled
     * chain on each deck. Reads from the user's library first, falling back to the bundled copy.
     */
    fun loadDefaultFxChains() {
        fun loadChain(fileName: String): FXChainDto? {
            val file = java.io.File(llm.slop.liquidlsd.ui.FileSystemManager.getFxChainsRoot(), fileName)
            if (file.exists()) {
                return try {
                    chainJson.decodeFromString<FXChainDto>(file.readText())
                } catch (e: Exception) {
                    null
                }
            }
            val stream = Mixer::class.java.classLoader.getResourceAsStream("default_fx_chains/$fileName")
            if (stream != null) {
                return try {
                    val text = stream.bufferedReader().use { it.readText() }
                    chainJson.decodeFromString<FXChainDto>(text)
                } catch (e: Exception) {
                    null
                }
            }
            return null
        }

        loadChain("subtle_optical_warmth.lsdfxchain")?.let { masterFxChain.applyFxChain(it) }
        loadChain("liquid_mercury.lsdfxchain")?.let { deckA.fxChain.applyFxChain(it) }
        loadChain("liquid_chrome_dimension.lsdfxchain")?.let { deckB.fxChain.applyFxChain(it) }
        loadChain("hyperspace_trip.lsdfxchain")?.let { deckBG.fxChain.applyFxChain(it) }
        loadChain("prismatic_crystal_kaleidoscope.lsdfxchain")?.let { deckPV.fxChain.applyFxChain(it) }

        llm.slop.liquidlsd.macro.FxMacroSync.syncAll(this)
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
        /** Parameter-path prefix for [masterFxChain], the Master counterpart of "Deck A/FX". */
        const val MASTER_FX_PREFIX = "Master/FX"
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
        if (masterFxChain.enabled) {
            list.add(masterFxChain.dryWet)
            masterFxChain.slots.forEach { fx ->
                if (fx != null && fx.enabled) {
                    list.add(fx.dryWet)
                    list.addAll(fx.parameters.values)
                }
            }
        }
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

        // Master FX paths use the literal "Master/FX" prefix, mirroring each deck's "Deck X/FX" --
        // not $prefix, unlike everything else here, which is genuinely Mixer-owned.
        list.addAll(masterFxChain.getParameterPaths(MASTER_FX_PREFIX))

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
        masterFxChain.update()

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
        masterFxOutFBO.dispose()
        masterFxChain.dispose()
        transitionFilter?.dispose()
    }
}
