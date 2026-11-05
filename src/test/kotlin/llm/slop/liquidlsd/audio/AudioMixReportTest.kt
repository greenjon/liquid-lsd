package llm.slop.liquidlsd.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * A measuring tool, not a regression test: runs real mixes and synthetic signals through the band filters and auto
 * gain, and reports what a modulator would see of each transient variant at 60 fps through a typical follower.
 * Skipped unless `LSD_AUDIO_MIXES` lists audio files, one per line. The report goes to `LSD_AUDIO_REPORT`.
 *
 * ```
 * LSD_AUDIO_MIXES="$(printf '%s\n' a.mp3 b.mp3)" LSD_AUDIO_REPORT=/tmp/r.txt ./gradlew test --tests '*AudioMixReportTest*'
 * ```
 */
class AudioMixReportTest {
    private val hopSec = 0.0058
    private val holdSec = 0.034
    private val fps = 60.0
    private val followAttack = 0.003
    private val followDecay = 0.120
    private val bandScale = 0.05f
    private val bands = arrayOf("bass", "mid", "himid", "high")
    private val agcBand = intArrayOf(AudioAgc.BASS, AudioAgc.MID, AudioAgc.HIMID, AudioAgc.HIGH)
    private val detBand = intArrayOf(TransientDetector.BASS, TransientDetector.MID, TransientDetector.HIMID, TransientDetector.HIGH)

    /** What a modulator sees: the value at each frame, through an attack/decay follower. */
    private class Reader(private val attack: Double, private val decay: Double, private val fps: Double) {
        private var v = 0.0
        private val hist = IntArray(1001)
        private var windowMax = 0.0
        private var frames = 0
        var count = 0L
        var onsets = 0L
        var sum = 0.0
        var duty = 0L
        var seen = 0L
        private var above = false

        fun frame(input: Float) {
            val dt = 1.0 / fps
            val tau = if (input > v) attack else decay
            v += (input - v) * (1.0 - exp(-dt / tau))
            windowMax = max(windowMax, v)
            sum += v; seen++
            if (v > 0.5) duty++
            if (!above && v > 0.35) { onsets++; above = true } else if (above && v < 0.2) above = false
            if (++frames >= 6) { // 100 ms
                hist[(windowMax * 1000).toInt().coerceIn(0, 1000)]++
                count++
                windowMax = 0.0
                frames = 0
            }
        }

        fun pct(p: Double): Double {
            var seen = 0L
            val want = (count * p).toLong()
            for (i in hist.indices) {
                seen += hist[i]
                if (seen > want) return i / 1000.0
            }
            return 1.0
        }
    }

    /** One way of turning a band into a transient signal. */
    private abstract inner class Variant(val name: String) {
        val reader = Reader(followAttack, followDecay, fps)
        abstract fun block(b: FloatArray, frames: Int, sr: Float, blockRms: Float, div: Float): Float
    }

    /** Today's signal: the rise in RMS since the last block. */
    private inner class OldFlux : Variant("old") {
        private var prev = 0f
        override fun block(b: FloatArray, frames: Int, sr: Float, blockRms: Float, div: Float): Float {
            val flux = max(0f, blockRms - prev)
            prev = blockRms
            return (flux / (bandScale * div / AudioAgc.FIXED_SCALE)).coerceIn(0f, 1f)
        }
    }

    /** The production detector at a trial scale. */
    private inner class Prod(name: String, val band: Int, val scale: Float) : Variant(name) {
        private val detector = TransientDetector()
        override fun block(b: FloatArray, frames: Int, sr: Float, blockRms: Float, div: Float): Float =
            (detector.process(band, b, frames, sr) / (scale * div / AudioAgc.FIXED_SCALE)).coerceIn(0f, 1f)
    }

    private fun variants(band: Int): List<Variant> = listOf(
        OldFlux(),
        Prod("new .100", band, .100f),
        Prod("new .125", band, .125f),
        Prod("new .167", band, .167f),
        Prod("new .250", band, .250f),
    )

    /** The chain the engine runs: band filters, AGC every block, one frame's read of each variant. */
    private inner class Chain(sr: Float, val blockFrames: Int) {
        val filters = arrayOf(
            BiquadFilter(BiquadFilter.Type.LOWPASS, sr, 150f),
            BiquadFilter(BiquadFilter.Type.BANDPASS, sr, 1000f),
            BiquadFilter(BiquadFilter.Type.BANDPASS, sr, 3200f, 1.05f),
            BiquadFilter(BiquadFilter.Type.HIGHPASS, sr, 5000f),
        )
        val agc = AudioAgc()
        val v = Array(4) { variants(detBand[it]) }
        val tracker = BeatDetector()
        val buf = Array(4) { FloatArray(blockFrames) }
        val levelHist = Array(4) { IntArray(1001) }
        val energyBand = Array(4) { IntArray(21) }
        val mono = FloatArray(blockFrames)
        val prevRms = FloatArray(4)
        val last = Array(4) { FloatArray(v[0].size) }
        var frameDebt = 0.0
        var seconds = 0.0
        var peakAbs = 0f
        var sumAmp = 0.0
        var silent = 0.0
        val sumRms = DoubleArray(4)
        val bpms = IntArray(301)
        var lockedBlocks = 0L
        var blocks = 0L
        var breakdowns = 0
        var section = SectionWatch.Section.NORMAL
        val energyHist = IntArray(21)

        fun feed(s: FloatArray, frames: Int, sr: Float) {
            var amp = 0f
            for (i in 0 until frames) {
                val x = s[i]
                mono[i] = x
                amp += x * x
                peakAbs = max(peakAbs, kotlin.math.abs(x))
                for (b in 0 until 4) buf[b][i] = filters[b].process(x)
            }
            amp = sqrt(amp / frames)
            val rms = FloatArray(4)
            for (b in 0 until 4) {
                var e = 0f
                for (i in 0 until frames) e += buf[b][i] * buf[b][i]
                rms[b] = sqrt(e / frames)
                sumRms[b] += rms[b].toDouble()
            }
            val dt = frames.toDouble() / sr
            val onset = max(0f, rms[0] - prevRms[0]) * 2f + max(0f, rms[1] - prevRms[1]) * 0.8f + max(0f, rms[3] - prevRms[3]) * 0.3f
            prevRms[0] = rms[0]; prevRms[1] = rms[1]; prevRms[2] = rms[2]; prevRms[3] = rms[3]
            val bpm = tracker.processBlock(amp, rms[0], rms[1], rms[3], sr, frames, onset)
            blocks++
            if (tracker.isTempoLocked) { lockedBlocks++; bpms[bpm.toInt().coerceIn(0, 300)]++ }
            agc.update(amp, rms[0], rms[1], rms[3], rms[2], dt)
            if (agc.watch.section != section) {
                if (agc.watch.section == SectionWatch.Section.BREAKDOWN) breakdowns++
                section = agc.watch.section
            }
            energyHist[((agc.watch.energy + 1f) * 10f).toInt().coerceIn(0, 20)]++
            sumAmp += amp.toDouble()
            if (amp < 0.003f) silent += dt
            for (b in 0 until 4) {
                val div = agc.divisor(agcBand[b])
                levelHist[b][((rms[b] / div).coerceIn(0f, 1f) * 1000).toInt()]++
                energyBand[b][((agc.energy(agcBand[b]) + 1f) * 10f).toInt().coerceIn(0, 20)]++
                for (k in v[b].indices) last[b][k] = v[b][k].block(buf[b], frames, sr, rms[b], div)
            }
            seconds += dt
            frameDebt += dt * fps
            while (frameDebt >= 1.0) {
                frameDebt -= 1.0
                for (b in 0 until 4) for (k in v[b].indices) v[b][k].reader.frame(last[b][k])
            }
        }
    }

    private fun analyse(name: String, sr: Float, blockFrames: Int, feed: (Chain) -> Unit, out: StringBuilder) {
        val c = Chain(sr, blockFrames)
        feed(c)
        synchronized(out) {
            out.append("\n=== $name  (sr ${sr.toInt()}, block $blockFrames, ${"%.0f".format(Locale.ROOT, c.seconds)} s)\n")
            val mean = c.sumAmp / max(1, c.blocks)
            val modes = c.bpms.withIndex().sortedByDescending { it.value }.take(4).filter { it.value > 0 }
                .joinToString(" ") { "${it.index}:${it.value * 100 / max(1L, c.lockedBlocks)}%" }
            val eTotal = max(1, c.energyHist.sum())
            val low = c.energyHist.take(7).sum() * 100 / eTotal
            val hi = c.energyHist.drop(14).sum() * 100 / eTotal
            out.append(String.format(Locale.ROOT, "peak %.2f  meanRMS %.3f  crest %.1f  silent %.0fs  bandRMS b/m/hm/h %.3f/%.3f/%.3f/%.3f  breakdowns %d  drops %d\n",
                c.peakAbs, mean, c.peakAbs / mean, c.silent, c.sumRms[0] / c.blocks, c.sumRms[1] / c.blocks, c.sumRms[2] / c.blocks, c.sumRms[3] / c.blocks, c.breakdowns, c.agc.watch.drops))
            out.append("tempo locked ${c.lockedBlocks * 100 / max(1L, c.blocks)}%  bpm $modes  energy low ${low}% high ${hi}%\n")
            for (b in 0 until 4) {
                val h = c.levelHist[b]
                val pl = { q: Double -> var acc = 0L; val want = (h.sumOf { it.toLong() } * q).toLong(); var r = 1.0; for (i in h.indices) { acc += h[i]; if (acc > want) { r = i / 1000.0; break } }; r }
                val eh = c.energyBand[b]
                val et = max(1, eh.sum())
                out.append(String.format(Locale.ROOT, "  %s level p50 %.2f p90 %.2f p99 %.2f   energy <-.3: %d%%  >+.3: %d%%\n", bands[b], pl(.5), pl(.9), pl(.99), eh.take(7).sum() * 100 / et, eh.drop(14).sum() * 100 / et))
            }
            for (b in 0 until 4) {
                out.append("  ${bands[b]}:   variant                p50   p90   p99  mean  >.5  hits/s\n")
                for (variant in c.v[b]) {
                    val r = variant.reader
                    out.append(String.format(Locale.ROOT, "    %-22s %.2f  %.2f  %.2f  %.2f  %2d%%  %.2f\n", variant.name, r.pct(0.5), r.pct(0.9), r.pct(0.99), r.sum / max(1L, r.seen), r.duty * 100 / max(1L, r.seen), r.onsets / max(1.0, c.seconds)))
                }
            }
        }
    }

    private fun streamFile(file: File, block: Int, feed: (FloatArray, Int, Float) -> Unit, sr: Float) {
        val p = ProcessBuilder("ffmpeg", "-v", "error", "-i", file.path, "-ac", "1", "-ar", sr.toInt().toString(), "-f", "f32le", "-")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val bytes = ByteArray(block * 4)
        val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val s = FloatArray(block)
        p.inputStream.buffered(1 shl 20).use { input ->
            while (true) {
                var got = 0
                while (got < bytes.size) {
                    val r = input.read(bytes, got, bytes.size - got)
                    if (r < 0) break
                    got += r
                }
                if (got < bytes.size) break
                fb.position(0); fb.get(s)
                feed(s, block, sr)
            }
        }
        p.waitFor()
    }

    private fun sampleRateOf(file: File): Float {
        val p = ProcessBuilder("ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=sample_rate", "-of", "csv=p=0", file.path).start()
        return p.inputStream.bufferedReader().readText().trim().trimEnd(',').toFloat()
    }

    @Test
    fun report() {
        val list = System.getenv("LSD_AUDIO_MIXES")?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        assumeTrue(list.isNotEmpty(), "LSD_AUDIO_MIXES not set")
        val out = StringBuilder()
        synthetic(out)
        val blocks = System.getenv("LSD_AUDIO_BLOCKS")?.split(",")?.map { it.trim().toInt() } ?: listOf(512)
        val threads = list.flatMap { path ->
            val file = File(path)
            val sr = sampleRateOf(file)
            blocks.map { block ->
                Thread {
                    analyse(file.name.take(40), sr, block, { c -> streamFile(file, block, c::feed, sr) }, out)
                }.apply { start() }
            }
        }
        threads.forEach { it.join() }
        File(System.getenv("LSD_AUDIO_REPORT") ?: "build/audio-mix-report.txt").writeText(out.toString())
    }

    /** Feeds a generated signal block by block. */
    private fun synth(name: String, seconds: Double, block: Int, out: StringBuilder, f: (Double) -> Double) {
        val sr = 44100f
        val s = FloatArray(block)
        analyse(name, sr, block, { c ->
            var i = 0L
            while (i < seconds * sr) {
                for (k in 0 until block) s[k] = f((i + k) / sr.toDouble()).toFloat()
                c.feed(s, block, sr)
                i += block
            }
        }, out)
    }

    private fun kick(t: Double, bpm: Double, gain: Double = 0.6): Double {
        val beat = 60.0 / bpm
        val at = t % beat
        return gain * sin(2 * PI * (50 * at + 40 * (1 - exp(-at * 30)) / 30)) * exp(-at * 12)
    }

    private fun hat(t: Double, every: Double): Double {
        val at = t % every
        val n = sin(t * 91234.567) * sin(t * 53211.9) // deterministic noise
        return 0.3 * n * exp(-at * 180)
    }

    private fun synthetic(out: StringBuilder) {
        for (block in listOf(64, 1024)) {
            synth("synth kick 128 bpm", 40.0, block, out) { t -> kick(t, 128.0) }
            synth("synth sustained 50 Hz", 40.0, block, out) { t -> 0.5 * sin(2 * PI * 50 * t) }
            synth("synth kick over bass", 40.0, block, out) { t -> kick(t, 128.0, 0.45) + 0.25 * sin(2 * PI * 50 * t) }
        }
        synth("synth hats 16ths + kick", 40.0, 512, out) { t -> kick(t, 128.0) + hat(t, 60.0 / 128 / 4) }
        synth("synth quiet kick -40 dB", 40.0, 512, out) { t -> 0.01 * kick(t, 128.0) }
    }
}
