package llm.slop.liquidlsd.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tracker from real samples, through the band filters and [BeatHopper], on grooves whose tempo is known. */
class TempoSuiteTest {
    private fun noise(i: Long): Double {
        var x = i * 2654435761L + 12345L
        x = x xor (x shr 13)
        x *= 1274126177L
        x = x xor (x shr 16)
        return ((x and 0xffff).toDouble() / 32768.0) - 1.0
    }

    /** One bar of drums as steps per beat, each of kick, snare, hat listing the beat positions it plays on. */
    private class Groove(val name: String, val bpm: Double, val kicks: List<Double>, val snares: List<Double>, val hats: List<Double>, val barBeats: Double = 4.0)

    private fun render(g: Groove, sr: Float, seconds: Double): FloatArray {
        val out = FloatArray((seconds * sr).toInt())
        val beat = 60.0 / g.bpm
        val bars = (seconds / (beat * g.barBeats)).toInt() + 1
        fun add(at: Double, len: Double, f: (Double, Long) -> Double) {
            val start = (at * sr).toLong()
            val n = (len * sr).toInt()
            for (k in 0 until n) {
                val idx = start + k
                if (idx >= out.size) break
                out[idx.toInt()] += f(k / sr.toDouble(), idx).toFloat()
            }
        }
        for (bar in 0 until bars) {
            val t0 = bar * g.barBeats * beat
            for (b in g.kicks) add(t0 + b * beat, 0.25) { t, _ -> 0.7 * sin(2 * PI * (50 * t + 40 * (1 - exp(-t * 30)) / 30)) * exp(-t * 14) }
            for (b in g.snares) add(t0 + b * beat, 0.18) { t, i -> (0.35 * noise(i) + 0.25 * sin(2 * PI * 190 * t)) * exp(-t * 22) }
            for (b in g.hats) add(t0 + b * beat, 0.05) { t, i -> 0.15 * noise(i) * exp(-t * 120) }
        }
        return out
    }

    /** The tempo over the last third of the groove, as the tracker reports it, feeding blocks of [block] frames. */
    private fun track(audio: FloatArray, sr: Float, block: Int): Float {
        val detector = BeatDetector()
        val hopper = BeatHopper(detector)
        val low = BiquadFilter(BiquadFilter.Type.LOWPASS, sr, 150f)
        val mid = BiquadFilter(BiquadFilter.Type.BANDPASS, sr, 1000f)
        val high = BiquadFilter(BiquadFilter.Type.HIGHPASS, sr, 5000f)
        val a = FloatArray(block)
        val b = FloatArray(block)
        val m = FloatArray(block)
        val h = FloatArray(block)
        val votes = HashMap<Int, Int>()
        var i = 0
        while (i + block <= audio.size) {
            for (k in 0 until block) {
                val x = audio[i + k]
                a[k] = x
                b[k] = low.process(x)
                m[k] = mid.process(x)
                h[k] = high.process(x)
            }
            val bpm = hopper.process(a, b, m, h, block, sr)
            if (i > audio.size * 2 / 3) votes.merge(Math.round(bpm), 1, Int::plus)
            i += block
        }
        return votes.maxByOrNull { it.value }!!.key.toFloat()
    }

    private val sixteenths = (0 until 16).map { it * 0.25 }
    private val eighths = (0 until 8).map { it * 0.5 }
    private val grooves = listOf(
        Groove("house 124", 124.0, listOf(0.0, 1.0, 2.0, 3.0), listOf(1.0, 3.0), eighths.map { it + 0.0 }),
        Groove("techno 138", 138.0, listOf(0.0, 1.0, 2.0, 3.0), listOf(), sixteenths),
        Groove("rock 118", 118.0, listOf(0.0, 2.0, 2.5), listOf(1.0, 3.0), eighths),
        Groove("hip hop 92", 92.0, listOf(0.0, 0.75, 2.5), listOf(1.0, 3.0), sixteenths),
        Groove("slow 72", 72.0, listOf(0.0, 2.0), listOf(1.0, 3.0), eighths),
        Groove("dub 76", 76.0, listOf(2.0), listOf(2.0), listOf(0.5, 1.5, 2.5, 3.5)),
        Groove("two-step 174", 174.0, listOf(0.0, 2.5), listOf(1.0, 3.0), eighths),
    )


    /** Right at the tempo, or an octave off, which visuals tolerate. 3:2 and the like are real errors. */
    private fun right(found: Float, truth: Double): Boolean =
        listOf(0.5, 1.0, 2.0).any { abs(found - truth * it) <= truth * it * 0.04 }

    @Test
    fun tempoDoesNotDependOnTheBufferSize() {
        val sr = 44100f
        val audio = render(grooves[0], sr, 30.0)
        val at512 = track(audio, sr, 512)
        assertEquals(124f, at512, 2f, "at 512 frames")
        for (block in listOf(64, 128, 256, 1024, 2048, 480)) {
            val found = track(audio, sr, block)
            assertEquals(at512, found, 2f, "at $block frames")
        }
    }

    @Test
    fun tempoIsRightAt48k() {
        assertEquals(124f, track(render(grooves[0], 48000f, 30.0), 48000f, 256), 2f)
    }

    @Test
    fun aBufferOfTheGridSizeGivesExactlyWhatADirectCallDoes() {
        val sr = 44100f
        val audio = render(grooves[2], sr, 15.0)
        val direct = BeatDetector()
        val hopped = BeatDetector()
        val hopper = BeatHopper(hopped)
        val low = BiquadFilter(BiquadFilter.Type.LOWPASS, sr, 150f)
        val mid = BiquadFilter(BiquadFilter.Type.BANDPASS, sr, 1000f)
        val high = BiquadFilter(BiquadFilter.Type.HIGHPASS, sr, 5000f)
        val ex = AmplitudeExtractor()
        val a = FloatArray(512); val b = FloatArray(512); val m = FloatArray(512); val h = FloatArray(512)
        var pb = 0f; var pm = 0f; var ph = 0f
        var i = 0
        while (i + 512 <= audio.size) {
            for (k in 0 until 512) { val x = audio[i + k]; a[k] = x; b[k] = low.process(x); m[k] = mid.process(x); h[k] = high.process(x) }
            val rb = ex.calculateRms(b, 512); val rm = ex.calculateRms(m, 512); val rh = ex.calculateRms(h, 512)
            val onset = maxOf(0f, rb - pb) * 2f + maxOf(0f, rm - pm) * 0.8f + maxOf(0f, rh - ph) * 0.3f
            pb = rb; pm = rm; ph = rh
            val want = direct.processBlock(ex.calculateRms(a, 512), rb, rm, rh, sr, 512, onset)
            val got = hopper.process(a, b, m, h, 512, sr)
            assertEquals(want, got, 0.5f, "at sample $i")
            i += 512
        }
    }

    @Test
    fun knownGrooves() {
        val sr = 44100f
        val report = grooves.map { g ->
            val found = track(render(g, sr, 40.0), sr, 512)
            Triple(g, found, right(found, g.bpm))
        }
        println(report.joinToString("\n") { (g, f, ok) -> "tempo suite: ${g.name} -> $f ${if (ok) "ok" else "WRONG"}" })
        val wrong = report.filter { !it.third }
        assertTrue(wrong.isEmpty(), "wrong tempo: " + wrong.joinToString { "${it.first.name} -> ${it.second}" })
    }
}
