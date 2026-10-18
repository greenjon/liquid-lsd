package llm.slop.liquidlsd.audio

import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpectrumAnalyzerTest {
    private val sr = 48000f

    private fun sine(freq: Float, n: Int, amp: Float = 1f) =
        FloatArray(n) { (amp * sin(2.0 * PI * freq * it / sr)).toFloat() }

    private fun run(a: SpectrumAnalyzer, x: FloatArray, block: Int = 512): FloatArray {
        var i = 0
        val b = FloatArray(block)
        while (i + block <= x.size) {
            System.arraycopy(x, i, b, 0, block); a.process(b, block, sr); i += block
        }
        return FloatArray(SpectrumAnalyzer.BINS).also { a.copyTo(it) }
    }

    @Test
    fun sinePeaksInExpectedBin() {
        val bin = 100
        val freq = bin * sr / SpectrumAnalyzer.SIZE
        val out = run(SpectrumAnalyzer(), sine(freq, 8192))
        val peak = out.indices.maxByOrNull { out[it] }!!
        assertEquals(bin, peak)
        assertTrue(out[peak] > 0.95f, "full-scale sine should read ~1.0, was ${out[peak]}")
        assertTrue(out[bin + 20] < 0.2f)
    }

    @Test
    fun silenceIsZero() {
        val out = run(SpectrumAnalyzer(), FloatArray(8192))
        assertEquals(0f, out.max())
    }

    @Test
    fun releaseDecaysAfterSignalStops() {
        val a = SpectrumAnalyzer()
        run(a, sine(2000f, 8192))
        val out = run(a, FloatArray(48000))
        assertTrue(out.max() < 0.01f)
    }

    @Test
    fun deterministicForFixedInput() {
        val x = sine(1234f, 8192, 0.5f)
        assertContentEquals(run(SpectrumAnalyzer(), x), run(SpectrumAnalyzer(), x))
    }

    @Test
    fun engineFeedsTextureFft() {
        AudioEngine.stop()
        AudioEngine.resetForOfflineFeed(120f, 0L)
        val x = sine(1000f, 512, 0.5f)
        val buf = FloatBuffer.wrap(x)
        repeat(20) { buf.rewind(); AudioEngine.processAudio(buf, 512, sr, it * 10_000_000L) }
        val fft = FloatArray(512); val wave = FloatArray(512)
        AudioEngine.copyAudioTextureData(fft, wave)
        assertTrue(fft.max() > 0.5f, "FFT row must be live, max=${fft.max()}")
        AudioEngine.stop()
    }
}
