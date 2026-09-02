package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.MeterType

import llm.slop.liquidlsd.parameters.ParameterOwner

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

    // The master FBO where the blended result is rendered
    var masterFBO = FBO(width, height)

    fun resize(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight) return
        width = newWidth
        height = newHeight
        masterFBO.dispose()
        masterFBO = FBO(width, height)
        masterFBO.clear(0f, 0f, 0f, 0f)
        deckA.resize(newWidth, newHeight)
        deckB.resize(newWidth, newHeight)
        deckBG.resize(newWidth, newHeight)
        deckPV.resize(newWidth, newHeight)
    }

    // Blend parameters
    val crossfade = ModulatableParameter(-1.0f, minClamp = -1.0f, maxClamp = 1.0f, meterType = MeterType.BIPOLAR) // -1.0 = Deck A, 1.0 = Deck B
    val mode = ModulatableParameter(4.0f, minClamp = 0.0f, maxClamp = 4.0f).apply { // 0 = ADD, 1 = SCREEN, 2 = MULT, 3 = MAX, 4 = XFADE
        modulatorFilter = { false }
    }
    val masterAlpha = ModulatableParameter(1.0f) // Master output gain
    val bloom = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val xfadeSpeed = ModulatableParameter(5.0f, minClamp = 0.1f, maxClamp = 30.0f)

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

    val randDeckA = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val randDeckB = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val randDeckBG = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val randDeckPV = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val randAll = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)

    private var prevQueuePrevVal = 0.0f
    private var prevQueueNextVal = 0.0f
    private var prevBgQueuePrevVal = 0.0f
    private var prevBgQueueNextVal = 0.0f
    private var lastUpdateTimeNs: Long = System.nanoTime()

    fun getAllMixerRandomizableParameters(): List<ModulatableParameter> {
        val list = mutableListOf<ModulatableParameter>()
        list.addAll(deckA.getAllRandomizableParameters())
        list.addAll(deckB.getAllRandomizableParameters())
        list.addAll(deckBG.getAllRandomizableParameters())
        list.addAll(deckPV.getAllRandomizableParameters())
        list.add(crossfade)
        list.add(masterAlpha)
        return list
    }

    val morphControllerAll = DeckMorphController(::getAllMixerRandomizableParameters)

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        list.add("$prefix/crossfade" to crossfade)
        list.add("$prefix/mode" to mode)
        list.add("$prefix/masterAlpha" to masterAlpha)
        list.add("$prefix/bloom" to bloom)
        list.add("$prefix/xfadeSpeed" to xfadeSpeed)
        list.add("$prefix/queuePrev" to queuePrev)
        list.add("$prefix/queueNext" to queueNext)
        list.add("$prefix/bgQueuePrev" to bgQueuePrev)
        list.add("$prefix/bgQueueNext" to bgQueueNext)
        list.add("$prefix/randDeckA" to randDeckA)
        list.add("$prefix/randDeckB" to randDeckB)
        list.add("$prefix/randDeckBG" to randDeckBG)
        list.add("$prefix/randDeckPV" to randDeckPV)
        list.add("$prefix/randAll" to randAll)

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
        listOf(crossfade, masterAlpha).forEach { param ->
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
        mode.evaluate()
        masterAlpha.evaluate()
        bloom.evaluate()
        xfadeSpeed.evaluate()
        queuePrev.evaluate()
        queueNext.evaluate()
        bgQueuePrev.evaluate()
        bgQueueNext.evaluate()
        randDeckA.evaluate()
        randDeckB.evaluate()
        randDeckBG.evaluate()
        randDeckPV.evaluate()
        randAll.evaluate()

        // Continuous random morphing evaluation
        val isModA = randDeckA.modulators.any { !it.bypassed } || randDeckA.value > 0.0001f
        if (isModA) {
            deckA.morphController.update(randDeckA.value)
        }
        val isModB = randDeckB.modulators.any { !it.bypassed } || randDeckB.value > 0.0001f
        if (isModB) {
            deckB.morphController.update(randDeckB.value)
        }
        val isModBG = randDeckBG.modulators.any { !it.bypassed } || randDeckBG.value > 0.0001f
        if (isModBG) {
            deckBG.morphController.update(randDeckBG.value)
        }
        val isModPV = randDeckPV.modulators.any { !it.bypassed } || randDeckPV.value > 0.0001f
        if (isModPV) {
            deckPV.morphController.update(randDeckPV.value)
        }
        val isModAll = randAll.modulators.any { !it.bypassed } || randAll.value > 0.0001f
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
     * Synchronizes current queue trigger parameter values into edge-detection trackers.
     * Prevents false 0->1 trigger edge detection on startup / session load.
     */
    fun syncQueueTriggerPrevValues() {
        queueNext.evaluate()
        queuePrev.evaluate()
        bgQueueNext.evaluate()
        bgQueuePrev.evaluate()
        prevQueueNextVal = queueNext.value
        prevQueuePrevVal = queuePrev.value
        prevBgQueueNextVal = bgQueueNext.value
        prevBgQueuePrevVal = bgQueuePrev.value
    }

    /**
     * Disposes the master FBO.
     */
    fun dispose() {
        masterFBO.dispose()
    }
}
