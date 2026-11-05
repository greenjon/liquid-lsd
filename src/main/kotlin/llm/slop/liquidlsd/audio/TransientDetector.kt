package llm.slop.liquidlsd.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Scores a band's rise in level against its recent rises, so what stands out passes and what is always there does not.
 * A sustained bass note ripples in an 11.6 ms window, but evenly, so its rises never stand above their own mean; a kick
 * stands far above it. Ported to `web/dsp.js` (TransientGate); `WebTransientVectorsTest` keeps the two identical.
 */
class TransientGate {
    private var meanRise = 0f

    /** The part of [rise] that counts as a hit, [dt] seconds after the last call. */
    fun step(rise: Float, dt: Float): Float {
        val contrast = rise / (meanRise + TransientDetector.EPS)
        val t = ((contrast - TransientDetector.C_LOW) / (TransientDetector.C_HIGH - TransientDetector.C_LOW)).coerceIn(0f, 1f)
        val gate = t * t * (3f - 2f * t)
        meanRise += (rise - meanRise) * min(1f, dt / TransientDetector.LOCAL_TAU)
        return gate * rise
    }

    fun reset() {
        meanRise = 0f
    }
}

/**
 * The hits in each band, behind the `audio_flux_*` signals. What it reports does not depend on the audio block size:
 * the level is measured on a fixed time grid inside the block. The value is held for [HOLD_SEC], because modulators read
 * once per render frame and the audio thread overwrites between reads, which would lose a short spike.
 *
 * The beat and downbeat detectors do not use this; they keep the plain block-to-block rise in level.
 * No allocation; audio thread only.
 */
class TransientDetector {
    private class Band {
        val gate = TransientGate()
        var sum = 0.0
        var count = 0
        var e1 = 0.0
        var e2 = 0.0
        var e3 = 0.0
        var held = 0f
        var age = 0f

        fun reset() {
            gate.reset()
            sum = 0.0
            count = 0
            e1 = 0.0
            e2 = 0.0
            e3 = 0.0
            held = 0f
            age = 0f
        }
    }

    private val bands = Array(BANDS) { Band() }

    /**
     * Feeds a block of [band] and returns its hit value (raw RMS units, before scaling): the largest value held during
     * the block, so a long block loses nothing either.
     */
    fun process(band: Int, samples: FloatArray, frames: Int, sampleRate: Float): Float {
        val b = bands[band]
        val hop = max(1, (sampleRate * HOP_SEC).toInt())
        val dt = hop / sampleRate
        var peak = b.held
        for (i in 0 until frames) {
            val s = samples[i]
            b.sum += (s * s).toDouble()
            if (++b.count >= hop) {
                val e0 = b.sum / hop
                // Two hops make the 11.6 ms level; the rise is against the two hops before them.
                val level = sqrt((e0 + b.e1) / 2.0).toFloat()
                val before = sqrt((b.e2 + b.e3) / 2.0).toFloat()
                b.e3 = b.e2
                b.e2 = b.e1
                b.e1 = e0
                b.sum = 0.0
                b.count = 0
                val hit = b.gate.step(max(0f, level - before), dt)
                b.age += dt
                if (hit >= b.held || b.age > HOLD_SEC) {
                    b.held = hit
                    b.age = 0f
                }
                peak = max(peak, b.held)
            }
        }
        return peak
    }

    fun reset() {
        for (b in bands) b.reset()
    }

    companion object {
        const val BASS = 0
        const val MID = 1
        const val HIMID = 2
        const val HIGH = 3
        const val BANDS = 4

        const val HOP_SEC = 0.0058f
        const val HOLD_SEC = 0.034f     // every 30 fps frame or faster sees each hit
        const val LOCAL_TAU = 1f        // s, the recent rises a hit is compared with
        const val C_LOW = 3f            // no hit at this many times the recent rise...
        const val C_HIGH = 8f           // ...a whole one at this many
        const val EPS = 1e-4f           // about -80 dBFS, so near silence has no contrast to speak of

        /**
         * A rise of this reads 1.0 at the fixed scale; auto gain stretches it like the band's level. Set on three long
         * mixes (2026-11-04, `AudioMixReportTest`) so hits stand out and accents have room above an ordinary hit.
         */
        const val SCALE = 0.167f
        /** The same for the weighted full-mix signal, [BASS_WEIGHT] etc. */
        const val AMP_SCALE = 0.333f
        const val BASS_WEIGHT = 2f
        const val MID_WEIGHT = 0.8f
        const val HIMID_WEIGHT = 0.5f
        const val HIGH_WEIGHT = 0.3f
    }
}
