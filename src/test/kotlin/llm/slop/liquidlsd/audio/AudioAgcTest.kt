package llm.slop.liquidlsd.audio

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioAgcTest {
    private val dt = 0.01

    private fun feed(agc: AudioAgc, rms: Float, seconds: Double) {
        repeat((seconds / dt).toInt()) { agc.update(AudioAgc.BASS, rms, dt) }
    }

    @Test
    fun disabledUsesFixedScale() {
        val agc = AudioAgc().apply { enabled = false }
        assertEquals(AudioAgc.FIXED_SCALE, agc.update(AudioAgc.BASS, 0.9f, dt))
        assertEquals(1f, agc.boost(AudioAgc.BASS))
    }

    @Test
    fun quietAndLoudInputsNormaliseToTheSamePeak() {
        val quiet = AudioAgc().also { feed(it, 0.05f, 1.0) }
        val loud = AudioAgc().also { feed(it, 0.5f, 1.0) }
        val q = 0.05f / quiet.divisor(AudioAgc.BASS)
        val l = 0.5f / loud.divisor(AudioAgc.BASS)
        assertEquals(q, l, 1e-4f)
        assertTrue(q > 1f / 0.85f - 1e-3f)
    }

    @Test
    fun silenceIsNotBoostedBeyondTheFloor() {
        val agc = AudioAgc().also { feed(it, 0.0f, 60.0) }
        assertEquals(AudioAgc.FLOOR, agc.divisor(AudioAgc.BASS), 1e-6f)
        assertTrue(0.001f / agc.divisor(AudioAgc.BASS) < 0.1f)
    }

    @Test
    fun recoversAfterLoudToQuiet() {
        val agc = AudioAgc().also { feed(it, 0.5f, 1.0) }
        val loudDiv = agc.divisor(AudioAgc.BASS)
        feed(agc, 0.05f, 40.0)
        assertTrue(agc.divisor(AudioAgc.BASS) < loudDiv / 3f)
    }

    @Test
    fun bandsAreIndependent() {
        val agc = AudioAgc()
        feed(agc, 0.5f, 1.0)
        assertEquals(AudioAgc.FLOOR, agc.divisor(AudioAgc.HIGH), 1e-6f)
    }
}
