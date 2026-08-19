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
    val deckC: Deck,
    val width: Int = 1920,
    val height: Int = 1080
) : ParameterOwner {
    // The master FBO where the blended result is rendered
    val masterFBO = FBO(width, height)

    // Blend parameters
    val crossfade = ModulatableParameter(-1.0f, minClamp = -1.0f, maxClamp = 1.0f, meterType = MeterType.BIPOLAR) // -1.0 = Deck A, 1.0 = Deck B
    val mode = ModulatableParameter(4.0f) // 0 = ADD, 1 = SCREEN, 2 = MULT, 3 = MAX, 4 = XFADE
    val masterAlpha = ModulatableParameter(1.0f) // Master output gain
    val bloom = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val xfadeSpeed = ModulatableParameter(5.0f, minClamp = 0.1f, maxClamp = 30.0f)

    @Volatile var targetCrossfade = -1.0f
    var isAutoFading = false

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
    val randDeckC = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val randAll = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)

    private var prevQueuePrevVal = 0.0f
    private var prevQueueNextVal = 0.0f
    private var prevRandDeckAVal = 0.0f
    private var prevRandDeckBVal = 0.0f
    private var prevRandDeckCVal = 0.0f
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
        list.add("$prefix/randDeckC" to randDeckC)
        list.add("$prefix/randAll" to randAll)

        list.addAll(deckA.getParameterPaths("Deck A"))
        list.addAll(deckB.getParameterPaths("Deck B"))
        list.addAll(deckC.getParameterPaths("Deck C"))

        return list
    }

    /**
     * Evaluates mixer parameters.
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

        crossfade.evaluate()
        mode.evaluate()
        masterAlpha.evaluate()
        bloom.evaluate()
        xfadeSpeed.evaluate()
        queuePrev.evaluate()
        queueNext.evaluate()
        randDeckA.evaluate()
        randDeckB.evaluate()
        randDeckC.evaluate()
        randAll.evaluate()

        val valA = randDeckA.value
        if (prevRandDeckAVal < 0.5f && valA >= 0.5f) {
            deckA.randomizeModulators()
        }
        prevRandDeckAVal = valA

        val valB = randDeckB.value
        if (prevRandDeckBVal < 0.5f && valB >= 0.5f) {
            deckB.randomizeModulators()
        }
        prevRandDeckBVal = valB

        val valC = randDeckC.value
        if (prevRandDeckCVal < 0.5f && valC >= 0.5f) {
            deckC.randomizeModulators()
        }
        prevRandDeckCVal = valC

        val valAll = randAll.value
        if (prevRandAllVal < 0.5f && valAll >= 0.5f) {
            deckA.randomizeModulators()
            deckB.randomizeModulators()
            deckC.randomizeModulators()
            listOf(crossfade, masterAlpha).forEach { param ->
                val randomized = param.modulators.map { it.randomizeActiveValues() }
                param.modulators.clear()
                param.modulators.addAll(randomized)
                param.randomizeBaseValue()
            }
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
