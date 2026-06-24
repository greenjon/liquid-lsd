package llm.slop.spirals.cv

import kotlin.math.floor
import kotlin.random.Random

/**
 * CV source representing a classic analog Sample & Hold module.
 * Generates a new random value on every integer beat transition.
 */
class SampleAndHold(override val id: String = "sampleAndHold") : CVSource {
    private var lastBeat = -1
    private var _value = 0f
    override val value: Float get() = _value

    override fun update(totalBeats: Double, elapsedSeconds: Double) {
        val currentBeat = floor(totalBeats).toInt()
        if (currentBeat != lastBeat) {
            // Seed deterministically based on the current beat index
            val rng = Random(currentBeat)
            _value = rng.nextFloat() * 2.0f - 1.0f
            lastBeat = currentBeat
        }
    }
}
