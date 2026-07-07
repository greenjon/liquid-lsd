package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.MeterType
import llm.slop.spirals.config.RuntimeConfig

/**
 * Manages the blending of two Decks (Deck A and Deck B) into a master output FBO.
 * Provides controls for crossfade, master alpha, and blending mode.
 */
class Mixer(
    val deckA: Deck,
    val deckB: Deck,
    val deckC: Deck,
    val width: Int = RuntimeConfig.Rendering.DEFAULT_WIDTH,
    val height: Int = RuntimeConfig.Rendering.DEFAULT_HEIGHT
) {
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
            llm.slop.spirals.patches.PlayQueueManager.isAutoVJEnabled || mod.sourceId.startsWith("midi_cc_")
        }
    }
    val queueNext = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f).apply {
        modulatorFilter = { mod ->
            llm.slop.spirals.patches.PlayQueueManager.isAutoVJEnabled || mod.sourceId.startsWith("midi_cc_")
        }
    }

    private var prevQueuePrevVal = 0.0f
    private var prevQueueNextVal = 0.0f
    private var lastUpdateTimeNs: Long = System.nanoTime()

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
        return delta
    }

    /**
     * Disposes the master FBO.
     */
    fun dispose() {
        masterFBO.dispose()
    }
}
