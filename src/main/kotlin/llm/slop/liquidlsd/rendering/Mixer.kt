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
    // Backward compatibility alias for Deck PV
    val deckC: Deck get() = deckPV

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
    val mode = ModulatableParameter(4.0f) // 0 = ADD, 1 = SCREEN, 2 = MULT, 3 = MAX, 4 = XFADE
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

    val randDeckA = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val randDeckB = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val randDeckBG = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val randDeckPV = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val randDeckC: ModulatableParameter get() = randDeckPV
    val randAll = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)

    private var prevQueuePrevVal = 0.0f
    private var prevQueueNextVal = 0.0f
    private var prevRandDeckAVal = 0.0f
    private var prevRandDeckBVal = 0.0f
    private var prevRandDeckBGVal = 0.0f
    private var prevRandDeckPVVal = 0.0f
    private var prevRandAllVal = 0.0f
    private var lastUpdateTimeNs: Long = System.nanoTime()

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        list.add("$prefix/crossfade" to crossfade)
        list.add("$prefix/masterAlpha" to masterAlpha)
        list.add("$prefix/bloom" to bloom)
        list.add("$prefix/xfadeSpeed" to xfadeSpeed)
        list.add("$prefix/queuePrev" to queuePrev)
        list.add("$prefix/queueNext" to queueNext)
        list.add("$prefix/randDeckA" to randDeckA)
        list.add("$prefix/randDeckB" to randDeckB)
        list.add("$prefix/randDeckBG" to randDeckBG)
        list.add("$prefix/randDeckPV" to randDeckPV)
        list.add("$prefix/randDeckC" to randDeckPV)
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

    fun randomizeDeckC() {
        randomizeDeckPV()
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
    }

    /**
     * Evaluates mixer parameters and background queue transitions.
     */
    fun update() {
        val now = System.nanoTime()
        val deltaTime = (now - lastUpdateTimeNs) / 1_000_000_000f
        lastUpdateTimeNs = now

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
        randDeckA.evaluate()
        randDeckB.evaluate()
        randDeckBG.evaluate()
        randDeckPV.evaluate()
        randAll.evaluate()

        val valA = randDeckA.value
        if (prevRandDeckAVal < 0.5f && valA >= 0.5f) {
            randomizeDeckA()
        }
        prevRandDeckAVal = valA

        val valB = randDeckB.value
        if (prevRandDeckBVal < 0.5f && valB >= 0.5f) {
            randomizeDeckB()
        }
        prevRandDeckBVal = valB

        val valBG = randDeckBG.value
        if (prevRandDeckBGVal < 0.5f && valBG >= 0.5f) {
            randomizeDeckBG()
        }
        prevRandDeckBGVal = valBG

        val valPV = randDeckPV.value
        if (prevRandDeckPVVal < 0.5f && valPV >= 0.5f) {
            randomizeDeckPV()
        }
        prevRandDeckPVVal = valPV

        val valAll = randAll.value
        if (prevRandAllVal < 0.5f && valAll >= 0.5f) {
            randomizeAll()
        }
        prevRandAllVal = valAll
    }

    /**
     * Evaluates if either parameter crossed the 0.5 threshold since the last frame.
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
     * Synchronizes current queue trigger parameter values into edge-detection trackers.
     * Prevents false 0->1 trigger edge detection on startup / session load.
     */
    fun syncQueueTriggerPrevValues() {
        queueNext.evaluate()
        queuePrev.evaluate()
        prevQueueNextVal = queueNext.value
        prevQueuePrevVal = queuePrev.value
    }

    /**
     * Disposes the master FBO.
     */
    fun dispose() {
        masterFBO.dispose()
    }
}
