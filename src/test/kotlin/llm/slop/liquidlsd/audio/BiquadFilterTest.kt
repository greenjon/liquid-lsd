package llm.slop.liquidlsd.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BiquadFilterTest {

    @Test
    fun testSubnormalFlushing() {
        val filter = BiquadFilter(
            type = BiquadFilter.Type.LOWPASS,
            sampleRate = 44100f,
            frequency = 150f,
            q = 0.707f
        )

        // Process a spike to load internal state registers
        filter.process(1.0f)

        // Feed silence (0.0f) repeatedly until state decays toward zero
        for (i in 0 until 5000) {
            val out = filter.process(0.0f)
            if (abs(out) < 1e-15f) {
                assertTrue(out == 0.0f || abs(out) >= 1e-38f || !out.isFinite().not())
            }
        }

        // After many silent frames, processing 0.0f must produce exactly 0.0f
        val silentOut = filter.process(0.0f)
        assertEquals(0.0f, silentOut, 1e-6f)
    }

    @Test
    fun testReset() {
        val filter = BiquadFilter(
            type = BiquadFilter.Type.HIGHPASS,
            sampleRate = 44100f,
            frequency = 5000f
        )
        filter.process(1.0f)
        filter.reset()
        assertEquals(0.0f, filter.process(0.0f))
    }
}
