package llm.slop.liquidlsd.audio

import kotlin.math.PI

/**
 * Direct Form II Transposed Biquad Filter implementation.
 * Used for separating audio into frequency bands (bass, mid, high).
 */
class BiquadFilter(
    val type: Type,
    var sampleRate: Float,
    var frequency: Float,
    var q: Float = 0.707f
) {
    enum class Type { LOWPASS, HIGHPASS, BANDPASS }

    private var a0: Float = 0f
    private var a1: Float = 0f
    private var a2: Float = 0f
    private var b1: Float = 0f
    private var b2: Float = 0f

    private var z1: Float = 0f
    private var z2: Float = 0f

    init {
        updateCoefficients()
    }

    /**
     * Recalculates the filter coefficients. Must be called after updating sampleRate, frequency, or Q.
     */
    fun updateCoefficients() {
        val omega = (2.0 * PI * frequency / sampleRate).toFloat()
        val sn = Math.sin(omega.toDouble()).toFloat()
        val cs = Math.cos(omega.toDouble()).toFloat()
        val alpha = sn / (2.0f * q)

        when (type) {
            Type.LOWPASS -> {
                val norm = 1.0f / (1.0f + alpha)
                a0 = (1.0f - cs) * 0.5f * norm
                a1 = (1.0f - cs) * norm
                a2 = a0
                b1 = -2.0f * cs * norm
                b2 = (1.0f - alpha) * norm
            }
            Type.HIGHPASS -> {
                val norm = 1.0f / (1.0f + alpha)
                a0 = (1.0f + cs) * 0.5f * norm
                a1 = -(1.0f + cs) * norm
                a2 = a0
                b1 = -2.0f * cs * norm
                b2 = (1.0f - alpha) * norm
            }
            Type.BANDPASS -> {
                val norm = 1.0f / (1.0f + alpha)
                a0 = alpha * norm
                a1 = 0f
                a2 = -alpha * norm
                b1 = -2.0f * cs * norm
                b2 = (1.0f - alpha) * norm
            }
        }
    }

    /**
     * Processes a single input sample and returns the filtered output sample.
     */
    fun process(input: Float): Float {
        val output = a0 * input + z1
        z1 = a1 * input + z2 - b1 * output
        z2 = a2 * input - b2 * output

        // Flush subnormals to true zero to prevent CPU microcode stalls
        if (Math.abs(z1) < 1e-15f) z1 = 0.0f
        if (Math.abs(z2) < 1e-15f) z2 = 0.0f

        return output
    }

    /**
     * Resets the filter's state registers to zero.
     */
    fun reset() {
        z1 = 0f
        z2 = 0f
    }
}
