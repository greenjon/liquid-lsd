package llm.slop.liquidlsd.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Display spectrum for the shader audio texture (`audioFFT` / `iChannel0` row 0).
 *
 * Independent of [BeatTrackerEngine]: beat detection never sees this FFT, so its results cannot change.
 * Each audio block pushes mono samples into a 1024-sample ring; the newest 1024 are Hann-windowed and
 * transformed, giving [BINS] linear-frequency bins spanning 0..sampleRate/2.
 *
 * Scaling: magnitude is normalised so a full-scale sine reads 1.0, converted to dB and mapped
 * [MIN_DB]..0 dB onto 0..1. Release is a one-pole decay ([RELEASE_SEC]); attack is instant.
 *
 * Zero allocation after construction. Written on the audio thread, read from the render thread via a
 * double buffer; no wall-clock or random input, so a fixed feed gives bit-identical output.
 */
class SpectrumAnalyzer {
    private val ring = FloatArray(SIZE)
    private var writePos = 0
    private val window = FloatArray(SIZE) { (0.5 * (1.0 - cos(2.0 * PI * it / SIZE))).toFloat() }
    private val re = FloatArray(SIZE)
    private val im = FloatArray(SIZE)
    private val cosT = FloatArray(SIZE / 2) { cos(-2.0 * PI * it / SIZE).toFloat() }
    private val sinT = FloatArray(SIZE / 2) { sin(-2.0 * PI * it / SIZE).toFloat() }
    private val bitRev = IntArray(SIZE).also { t ->
        val bits = Integer.numberOfTrailingZeros(SIZE)
        for (i in 0 until SIZE) t[i] = Integer.reverse(i) ushr (32 - bits)
    }
    private val smoothed = FloatArray(BINS)
    private val buffers = arrayOf(FloatArray(BINS), FloatArray(BINS))
    @Volatile private var published = 0

    /** Amplitude of a full-scale sine after Hann windowing: sum(window) / 2. */
    private val normGain = 2f / window.sum()

    fun reset() {
        ring.fill(0f); writePos = 0; smoothed.fill(0f)
        buffers[0].fill(0f); buffers[1].fill(0f); published = 0
    }

    /** Audio thread. Appends [frames] mono samples and refreshes the published spectrum. */
    fun process(samples: FloatArray, frames: Int, sampleRate: Float) {
        if (frames <= 0) return
        // Only the newest SIZE samples can matter.
        val start = if (frames > SIZE) frames - SIZE else 0
        for (i in start until frames) {
            ring[writePos] = samples[i]
            writePos = (writePos + 1) and (SIZE - 1)
        }
        for (i in 0 until SIZE) {
            re[bitRev[i]] = ring[(writePos + i) and (SIZE - 1)] * window[i]
            im[bitRev[i]] = 0f
        }
        fft()

        val decay = exp(-frames / (sampleRate.coerceAtLeast(1f) * RELEASE_SEC))
        val out = buffers[1 - published]
        for (k in 0 until BINS) {
            val mag = sqrt(re[k] * re[k] + im[k] * im[k]) * normGain
            val db = 20f * (ln(mag + 1e-9f) * LOG10_INV)
            val v = ((db - MIN_DB) / -MIN_DB).coerceIn(0f, 1f)
            smoothed[k] = max(v, smoothed[k] * decay)
            out[k] = smoothed[k]
        }
        published = 1 - published
    }

    /** Render thread. Copies up to [BINS] values into [target]. */
    fun copyTo(target: FloatArray) {
        System.arraycopy(buffers[published], 0, target, 0, minOf(target.size, BINS))
    }

    private fun fft() {
        var len = 2
        while (len <= SIZE) {
            val half = len shr 1
            val step = SIZE / len
            var i = 0
            while (i < SIZE) {
                var t = 0
                for (k in 0 until half) {
                    val c = cosT[t]; val s = sinT[t]
                    val a = i + k; val b = a + half
                    val tr = re[b] * c - im[b] * s
                    val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                    t += step
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        const val SIZE = 1024
        const val BINS = 512
        const val MIN_DB = -80f
        const val RELEASE_SEC = 0.08f
        private const val LOG10_INV = 0.43429448f
    }
}
