package llm.slop.spirals.cv

import kotlin.math.sin

/**
 * CV source representing a time-based Low Frequency Oscillator (LFO).
 * Outputs a sine wave scaled to the [0.0, 1.0] range.
 */
class LFO(
    override val id: String = "lfo",
    var frequencyHz: Float = 0.25f // 4-second period default
) : CVSource {
    private var _value = 0f
    override val value: Float get() = _value

    override fun update(totalBeats: Double, elapsedSeconds: Double) {
        val angle = elapsedSeconds * 2.0 * Math.PI * frequencyHz
        _value = sin(angle).toFloat()
    }
}
