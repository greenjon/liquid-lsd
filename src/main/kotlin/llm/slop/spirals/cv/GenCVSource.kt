package llm.slop.spirals.cv

import kotlin.math.sin

/**
 * CV source representing a unified generator source (Gen).
 * Ticks a default 0.25 Hz LFO for visual history/oscilloscope reference.
 */
class GenCVSource(override val id: String) : CVSource {
    private var _value = 0f
    override val value: Float get() = _value

    override fun update(totalBeats: Double, elapsedSeconds: Double) {
        val angle = elapsedSeconds * 2.0 * Math.PI * 0.25
        _value = sin(angle).toFloat()
    }
}
