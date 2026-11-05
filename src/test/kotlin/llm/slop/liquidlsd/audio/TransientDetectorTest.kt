package llm.slop.liquidlsd.audio

import llm.slop.liquidlsd.cv.AudioSignals
import llm.slop.liquidlsd.parameters.CvModulator
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransientDetectorTest {
    private val sr = 44100f
    private val beatSec = 60.0 / 128

    private fun kick(t: Double, gain: Double = 0.6): Double {
        val at = t % beatSec
        return gain * sin(2 * PI * (50 * at + 40 * (1 - exp(-at * 30)) / 30)) * exp(-at * 12)
    }

    /** Runs [seconds] of [signal] through the bass filter and detector, giving [each] the time and value per block. */
    private fun run(seconds: Double, block: Int, signal: (Double) -> Double, each: (Double, Float) -> Unit) {
        val filter = BiquadFilter(BiquadFilter.Type.LOWPASS, sr, 150f)
        val detector = TransientDetector()
        val samples = FloatArray(block)
        var i = 0L
        while (i < seconds * sr) {
            for (k in 0 until block) samples[k] = filter.process(signal((i + k) / sr.toDouble()).toFloat().toDouble().toFloat())
            val value = detector.process(TransientDetector.BASS, samples, block, sr)
            i += block
            each(i / sr.toDouble(), value)
        }
    }

    /** The mean of each beat's peak, after the detector has settled. */
    private fun meanKickPeak(block: Int, signal: (Double) -> Double): Float {
        val peaks = HashMap<Int, Float>()
        run(20.0, block, signal) { t, v ->
            if (t > 5.0) {
                val beat = (t / beatSec).toInt()
                peaks[beat] = max(peaks[beat] ?: 0f, v)
            }
        }
        return peaks.values.average().toFloat()
    }

    @Test
    fun sustainedBassIsNotAHit() {
        for (block in listOf(64, 128, 512, 1024)) {
            var most = 0f
            run(10.0, block, { t -> 0.5 * sin(2 * PI * 50 * t) }) { t, v -> if (t > 3.0) most = max(most, v) }
            val kick = meanKickPeak(block) { t -> kick(t) }
            assertTrue(most < kick * 0.05f, "block $block: sustained note $most against a kick's $kick")
        }
    }

    @Test
    fun aKickReadsTheSameAtAnyBlockSize() {
        val peaks = listOf(64, 128, 512, 1024).associateWith { meanKickPeak(it) { t -> kick(t) } }
        val low = peaks.values.min()
        val high = peaks.values.max()
        assertTrue(low > 0f && high / low < 1.15f, "peaks by block size: $peaks")
    }

    @Test
    fun aKickOverSustainedBassStillStandsOut() {
        for (block in listOf(64, 512)) {
            var note = 0f
            run(10.0, block, { t -> 0.25 * sin(2 * PI * 50 * t) }) { t, v -> if (t > 3.0) note = max(note, v) }
            val both = meanKickPeak(block) { t -> kick(t, 0.45) + 0.25 * sin(2 * PI * 50 * t) }
            assertTrue(both > note * 5f + 0.01f, "block $block: kick over bass $both, the note alone $note")
        }
    }

    /** A modulator reads once per render frame; the audio thread must not overwrite a hit before a frame sees it. */
    @Test
    fun everyKickIsSeenByA30FpsReader() {
        for (block in listOf(64, 512, 1024)) {
            val frame = 1.0 / 30
            var next = 5.0
            var last = 0f
            val seen = HashSet<Int>()
            run(20.0, block, { t -> kick(t) }) { t, v ->
                last = v
                while (t >= next) {
                    if (last > 0.05f) seen += (next / beatSec).toInt()
                    next += frame
                }
            }
            val kicks = ((20.0 - 5.0) / beatSec).toInt()
            assertTrue(seen.size >= kicks - 1, "block $block: a 30 fps reader saw ${seen.size} of $kicks kicks")
        }
    }

    /** A kick track through the bass filter, auto gain and sizing, [each] getting the time and unclamped signal per block. */
    private fun sized(seconds: Double, gain: (Double) -> Double, each: (Double, Float, AudioAgc) -> Unit) {
        val agc = AudioAgc()
        val filter = BiquadFilter(BiquadFilter.Type.LOWPASS, sr, 150f)
        val detector = TransientDetector()
        val scale = HitScale()
        val block = 512
        val samples = FloatArray(block)
        val dt = block / sr.toDouble()
        var i = 0L
        while (i < seconds * sr) {
            var e = 0f
            for (k in 0 until block) {
                val t = (i + k) / sr.toDouble()
                val x = filter.process(kick(t, gain(t)).toFloat())
                samples[k] = x
                e += x * x
            }
            val rms = kotlin.math.sqrt(e / block)
            agc.update(rms, rms, rms * 0.1f, rms * 0.05f, rms * 0.05f, dt)
            val hit = detector.process(TransientDetector.BASS, samples, block, sr)
            val v = scale.signal(hit, TransientDetector.SCALE * agc.divisor(AudioAgc.BASS) / AudioAgc.FIXED_SCALE, dt.toFloat(), agc.watch.holding)
            i += block
            each(i / sr.toDouble(), v, agc)
        }
    }

    @Test
    fun aBreakdownStaysQuietAndTheDropLandsFull() {
        var before = 0f
        var inBreakdown = 0f
        var drop = 0f
        var section = SectionWatch.Section.NORMAL
        // A 6x quieter stretch from 35 s to 55 s, longer than the reference's decay would take to forget it.
        sized(62.0, { t -> if (t < 35.0 || t > 55.0) 0.6 else 0.1 }) { t, v, agc ->
            if (t in 25.0..35.0) before = max(before, v)
            if (t in 42.0..55.0) { inBreakdown = max(inBreakdown, v); section = agc.watch.section }
            if (t in 55.0..58.0) drop = max(drop, v)
        }
        assertEquals(SectionWatch.Section.BREAKDOWN, section, "the quiet stretch should be a breakdown")
        assertTrue(inBreakdown < before * 0.3f, "in the breakdown $inBreakdown, before it $before")
        assertTrue(drop > before * 0.7f, "the drop $drop against the music before it $before")
    }

    @Test
    fun quietAndLoudMusicUseTheRangeAlike() {
        // Fixed scales read these differently; both should land near full on their ordinary hits.
        fun typical(hitSize: Float, agcScale: Float): Float {
            val scale = HitScale()
            var last = 0f
            repeat(60 * 44) { i ->
                val hit = if (i % 40 < 3) hitSize else 0f // about 3 frames of hold, 1.5 hits a second at 60 steps a second
                val v = scale.signal(hit, agcScale, 1f / 60, false)
                if (hit > 0f) last = v
            }
            return last
        }
        val soft = typical(0.04f, 0.1f)
        val hard = typical(0.2f, 0.1f)
        assertTrue(soft > 0.9f && hard > 0.9f && abs(soft - hard) < 0.05f, "soft $soft, hard $hard")
    }

    @Test
    fun oneHugeHitDoesNotFlattenTheNextOnes() {
        val scale = HitScale()
        val dt = 512f / sr
        val stepsPerHit = (beatSec / dt).toInt()
        var first = -1f
        var after4s = -1f
        var step = 0
        val hugeAt = 200
        repeat(hugeAt * 2) { n ->
            val size = if (n == hugeAt) 0.6f else 0.2f
            var peak = 0f
            for (k in 0 until stepsPerHit) {
                val hit = if (k < 3) size else 0f
                peak = max(peak, scale.signal(hit, 0.1f, dt, false))
                step++
            }
            if (n == hugeAt + 1) first = peak
            if (n == hugeAt + (4.0 / beatSec).toInt()) after4s = peak
        }
        assertTrue(first > 0.4f, "the next hit reads $first")
        assertTrue(after4s > 0.7f, "4 s on it reads $after4s")
    }

    @Test
    fun aQuietBandIsNotLiftedPastTheCap() {
        val scale = HitScale()
        var v = 0f
        repeat(2000) { v = scale.signal(0.001f, 0.1f, 1f / 60, false) }
        assertEquals(0.001f / (0.1f / HitScale.MAX_BOOST), v, 1e-5f)
    }

    @Test
    fun silenceReadsZeroAndResetForgets() {
        val detector = TransientDetector()
        val samples = FloatArray(512)
        repeat(200) { assertEquals(0f, detector.process(TransientDetector.HIGH, samples, 512, sr)) }
        for (k in samples.indices) samples[k] = if (k % 2 == 0) 0.5f else -0.5f
        repeat(2) { detector.process(TransientDetector.HIGH, samples, 512, sr) }
        detector.reset()
        samples.fill(0f)
        assertEquals(0f, detector.process(TransientDetector.HIGH, samples, 512, sr))
    }

    @Test
    fun energiesAreBipolarAndOtherAudioSignalsAreNot() {
        for (id in AudioSignals.ENERGIES) {
            val m = CvModulator(sourceId = id, depth = 0.5f, dcOffset = 0f)
            assertEquals(-0.5f, m.getLfoMin(), "$id spans dc +/- depth")
            assertTrue(AudioSignals.isSection(id) && AudioSignals.isPushed(id), id)
        }
        for (id in AudioSignals.LEVELS + AudioSignals.TRANSIENTS) {
            assertEquals(0f, CvModulator(sourceId = id, depth = 0.5f, dcOffset = 0f).getLfoMin(), "$id only pushes upward")
            assertTrue(AudioSignals.isAudio(id) && !AudioSignals.isSection(id), id)
        }
        assertTrue(AudioSignals.isSection(AudioSignals.DROP) && !AudioSignals.isPushed(AudioSignals.DROP))
        (AudioSignals.LEVELS + AudioSignals.TRANSIENTS + AudioSignals.SECTIONS).forEach { assertTrue(AudioSignals.label(it) != null, it) }
    }

    /**
     * Golden vectors for `TransientGate` in web/dsp.js, replayed by `node --test web/tools/transient.test.mjs`.
     * Fails when the committed file is stale. Regenerate with `UPDATE_WEB_VECTORS=1`.
     */
    @Test
    fun webVectorsMatchDesktop() {
        val gate = TransientGate()
        val dt = 1f / 60
        val rows = (0 until 600).map { i ->
            val t = i * dt
            val ripple = 0.004f + 0.002f * sin(t * 40f)
            val hit = if (i % 28 == 14) 0.12f else 0f
            val rise = String.format(Locale.ROOT, "%.5f", ripple + hit).toFloat()
            "[$rise,${String.format(Locale.ROOT, "%.6g", gate.step(rise, dt))}]"
        }
        val scale = HitScale()
        val scaleRows = (0 until 600).map { i ->
            val hit = String.format(Locale.ROOT, "%.5f", if (i % 30 < 3) 0.1f + 0.05f * sin(i * 0.013f) else 0f).toFloat()
            val agcScale = String.format(Locale.ROOT, "%.5f", 0.08f + 0.02f * sin(i * 0.007f)).toFloat()
            val frozen = i in 250..330
            "[$hit,$agcScale,${if (frozen) 1 else 0},${String.format(Locale.ROOT, "%.6g", scale.signal(hit, agcScale, dt, frozen))}]"
        }
        val text = "{\n\"dt\": $dt,\n\"steps\": [\n${rows.joinToString(",\n")}\n],\n\"scale\": [\n${scaleRows.joinToString(",\n")}\n]\n}\n"
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "web/sync_manifest.json").exists()) dir = dir.parentFile
        val file = File(dir, "web/tools/transient_vectors.json")
        if (System.getenv("UPDATE_WEB_VECTORS") == "1" || !file.exists()) {
            file.writeText(text)
        } else {
            val num = Regex("-?[0-9.]+(e-?[0-9]+)?")
            val a = num.findAll(file.readText()).map { it.value.toDouble() }.toList()
            val b = num.findAll(text).map { it.value.toDouble() }.toList()
            assertTrue(a.size == b.size && a.indices.all { abs(a[it] - b[it]) <= 1e-5 * max(1.0, abs(a[it])) },
                "web/tools/transient_vectors.json is stale; regenerate with UPDATE_WEB_VECTORS=1")
        }
    }
}
