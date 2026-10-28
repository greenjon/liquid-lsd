package llm.slop.liquidlsd.audio

import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioAgcTest {
    private val dt = 1.0 / 60

    /** A band level with a beat in it (kicks at ~2 Hz), so it looks like music to the section watch. */
    private fun beat(level: Float, t: Double) = level * (1f + 0.3f * sin(t * 2 * PI * 2.1).toFloat())

    private var t = 0.0

    /** Feeds [seconds] of all four bands at these levels; calls [each] after every block. */
    private fun feed(agc: AudioAgc, seconds: Double, amp: Float, bass: Float, mid: Float = amp, high: Float = amp, each: (AudioAgc) -> Unit = {}) {
        repeat((seconds / dt).toInt()) {
            t += dt
            agc.update(beat(amp, t), beat(bass, t), beat(mid, t), beat(high, t), dt)
            each(agc)
        }
    }

    private fun bassSignal(agc: AudioAgc, bass: Float) = min(1f, bass / agc.divisor(AudioAgc.BASS))

    @Test
    fun quietAndLoudFeedsNormaliseTheSame() {
        val quiet = AudioAgc().also { feed(it, 30.0, 0.02f, 0.03f) }
        val loud = AudioAgc().also { feed(it, 30.0, 0.2f, 0.3f) }
        assertEquals(bassSignal(quiet, 0.03f), bassSignal(loud, 0.3f), 1e-3f)
        assertEquals(1f / AudioAgc.SCALE[AudioAgc.BASS], bassSignal(loud, 0.3f), 0.02f)
    }

    @Test
    fun aNewFeedLevelsInAtOnce() {
        val agc = AudioAgc()
        feed(agc, 1.0, 0.1f, 0.15f)
        assertEquals(1f / AudioAgc.SCALE[AudioAgc.BASS], bassSignal(agc, 0.15f), 0.05f)
    }

    @Test
    fun aBreakdownStaysQuietAndTheDropHits() {
        val agc = AudioAgc()
        feed(agc, 40.0, 0.2f, 0.3f)
        val normal = bassSignal(agc, 0.3f)
        var latest = 0f
        feed(agc, 25.0, 0.1f, 0.03f) { latest = bassSignal(it, 0.03f) }
        assertEquals(SectionWatch.Section.BREAKDOWN, agc.watch.section)
        assertTrue(latest < 0.15f * normal, "bass at the end of the breakdown $latest, before it $normal")
        feed(agc, 5.0, 0.2f, 0.3f)
        assertEquals(1, agc.watch.drops)
        assertEquals(normal, bassSignal(agc, 0.3f), 0.05f)
    }

    @Test
    fun aQuieterSongBecomesTheNewNormal() {
        val agc = AudioAgc()
        feed(agc, 40.0, 0.2f, 0.3f)
        feed(agc, 70.0, 0.04f, 0.06f)
        assertEquals(SectionWatch.Section.NORMAL, agc.watch.section)
        assertEquals(1f / AudioAgc.SCALE[AudioAgc.BASS], bassSignal(agc, 0.06f), 0.05f)
    }

    @Test
    fun aLouderSongIsAdoptedWithinSeconds() {
        val agc = AudioAgc()
        feed(agc, 40.0, 0.03f, 0.05f)
        feed(agc, 7.0, 0.2f, 0.3f)
        assertEquals(1f / AudioAgc.SCALE[AudioAgc.BASS], bassSignal(agc, 0.3f), 0.08f)
    }

    @Test
    fun silenceStartsAfreshAndIsNotBoostedBeyondTheFloor() {
        val agc = AudioAgc()
        feed(agc, 40.0, 0.2f, 0.3f)
        feed(agc, 5.0, 0.0f, 0.0f)
        assertEquals(AudioAgc.FLOOR, agc.divisor(AudioAgc.BASS), 1e-6f)
        assertTrue(0.001f / agc.divisor(AudioAgc.BASS) < 0.1f)
        feed(agc, 1.0, 0.02f, 0.03f) // a quieter feed after the gap levels in at once
        assertEquals(1f / AudioAgc.SCALE[AudioAgc.BASS], bassSignal(agc, 0.03f), 0.05f)
    }

    @Test
    fun bandsAreIndependent() {
        val agc = AudioAgc()
        feed(agc, 10.0, 0.5f, 0.5f, 0.5f, high = 0f)
        assertEquals(AudioAgc.FLOOR, agc.divisor(AudioAgc.HIGH), 1e-6f)
    }
}
