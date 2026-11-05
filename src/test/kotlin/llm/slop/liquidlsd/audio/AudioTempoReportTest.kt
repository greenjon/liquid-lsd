package llm.slop.liquidlsd.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * A measuring tool, not a regression test: how the tracker's tempo compares with a list of known or tagged tempos.
 * Skipped unless `LSD_TEMPO_LIST` names a file of `group<TAB>bpm<TAB>path` lines. The report goes to `LSD_TEMPO_REPORT`.
 * Tags from analysis software are a second opinion, not the truth; compare two versions of the tracker on one list.
 */
class AudioTempoReportTest {
    private val seconds = 60
    private val sr = 44100f
    private val block = 512

    private fun decode(path: String): FloatArray? {
        val p = ProcessBuilder("ffmpeg", "-v", "error", "-ss", "30", "-t", seconds.toString(), "-i", path, "-ac", "1", "-ar", sr.toInt().toString(), "-f", "f32le", "-")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val bytes = p.inputStream.readBytes()
        p.waitFor()
        if (bytes.size < 4 * sr.toInt() * 20) return null
        val out = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
        return out
    }

    private fun track(audio: FloatArray): Pair<Int, Double> {
        val detector = BeatDetector()
        val hopper = BeatHopper(detector)
        val low = BiquadFilter(BiquadFilter.Type.LOWPASS, sr, 150f)
        val mid = BiquadFilter(BiquadFilter.Type.BANDPASS, sr, 1000f)
        val high = BiquadFilter(BiquadFilter.Type.HIGHPASS, sr, 5000f)
        val a = FloatArray(block); val b = FloatArray(block); val m = FloatArray(block); val h = FloatArray(block)
        val votes = HashMap<Int, Int>()
        var locked = 0L
        var total = 0L
        var i = 0
        while (i + block <= audio.size) {
            for (k in 0 until block) { val x = audio[i + k]; a[k] = x; b[k] = low.process(x); m[k] = mid.process(x); h[k] = high.process(x) }
            val bpm = hopper.process(a, b, m, h, block, sr)
            if (i > sr * 15) {
                total++
                if (detector.isTempoLocked) { locked++; votes.merge(Math.round(bpm), 1, Int::plus) }
            }
            i += block
        }
        return (votes.maxByOrNull { it.value }?.key ?: 0) to (locked.toDouble() / maxOf(1L, total))
    }

    private fun near(found: Int, want: Double, ratio: Double) = abs(found - want * ratio) <= want * ratio * 0.04

    @Test
    fun report() {
        val list = System.getenv("LSD_TEMPO_LIST")
        assumeTrue(list != null, "LSD_TEMPO_LIST not set")
        val rows = File(list!!).readLines().filter { it.isNotBlank() }.map { it.split("\t") }
        data class R(val group: String, val tag: Double, val found: Int, val locked: Double, val path: String)
        val results = java.util.concurrent.ConcurrentLinkedQueue<R>()
        val pool = Executors.newFixedThreadPool(8)
        for (row in rows) pool.submit {
            val audio = decode(row[2]) ?: return@submit
            val (found, locked) = track(audio)
            results += R(row[0], row[1].toDouble(), found, locked, row[2])
        }
        pool.shutdown()
        pool.awaitTermination(9, TimeUnit.MINUTES)
        val out = StringBuilder()
        val groups = results.groupBy { it.group }.toSortedMap() + ("ALL" to results.toList())
        for ((g, rs) in groups) {
            val n = rs.size.coerceAtLeast(1).toDouble()
            val exact = rs.count { near(it.found, it.tag, 1.0) } / n
            val oct = rs.count { near(it.found, it.tag, 1.0) || near(it.found, it.tag, 2.0) || near(it.found, it.tag, 0.5) } / n
            val triple = rs.count { r -> listOf(1.5, 2.0 / 3, 4.0 / 3, 0.75, 3.0, 1.0 / 3).any { near(r.found, r.tag, it) } && !near(r.found, r.tag, 1.0) } / n
            val floor = rs.count { it.found in 1..46 } / n
            out.append(String.format(Locale.ROOT, "%-10s n %3d  exact %2.0f%%  with octaves %2.0f%%  3:2 family %2.0f%%  at floor %2.0f%%  locked %2.0f%%\n",
                g, rs.size, exact * 100, oct * 100, triple * 100, floor * 100, rs.map { it.locked }.average() * 100))
        }
        out.append("\nDisagreements:\n")
        results.filter { !near(it.found, it.tag, 1.0) && !near(it.found, it.tag, 2.0) && !near(it.found, it.tag, 0.5) }.sortedBy { it.group }.forEach {
            out.append(String.format(Locale.ROOT, "%-10s tag %6.1f found %3d ratio %.2f  %s\n", it.group, it.tag, it.found, it.found / it.tag, File(it.path).name.take(50)))
        }
        File(System.getenv("LSD_TEMPO_REPORT") ?: "build/tempo-report.txt").writeText(out.toString())
    }
}
