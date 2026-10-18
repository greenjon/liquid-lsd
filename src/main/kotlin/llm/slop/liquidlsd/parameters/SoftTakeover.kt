package llm.slop.liquidlsd.parameters

import kotlin.math.abs

/**
 * The pickup rule shared by MIDI/OSC mapping soft takeover and the FX Super Knob link: a moving source
 * "catches" a parameter once it crosses the parameter's current value or comes within [tolerance] of it.
 */
object SoftTakeover {

    /** Fraction of a parameter's range that counts as "close enough" to catch. */
    const val TOLERANCE_FRACTION = 0.04f

    /** [previous] is the source's last position, or null if it has not moved yet (a crossing then can't be detected). */
    fun caught(previous: Float?, source: Float, current: Float, tolerance: Float): Boolean {
        val crossed = previous != null && (previous - current) * (source - current) <= 0f
        return crossed || abs(source - current) <= tolerance
    }
}
