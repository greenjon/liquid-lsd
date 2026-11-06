package llm.slop.liquidlsd.audio

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.test.Test
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Where the beat count lands against known beats. Decodes real audio, so it does nothing unless LSD_PHASE_LIST names a
 * list from scripts/mixxx_beat_grids.py (bpm, first beat, path); LSD_PHASE_NOBEAT, one path per line, adds how much the
 * lock moves the clock on audio with no beat. Writes to LSD_PHASE_REPORT.
 */
class AudioPhaseReportTest {
    private val sr = 44100f
    private val block = 512

    private class Clock(val name: String) {
        var beats = 0.0; var prev = 0.0; var on = 0L; var off = 0L; var count = 0L; var hist = IntArray(21)
        var moved = 0.0; var jumps = 0L
    }

    private class Run(val path: String, val secs: Double, val clocks: List<Clock>, val locked: Double)

    private fun score(c: Clock, t: Double, bpm: Double, truthBpm: Double, first: Double) {
        if (truthBpm <= 0 || t < WARMUP_SEC) return
        // A count running at about twice the grid's tempo: its off-grid beats are the grid's halves, which is fine.
        val grid = if (abs(bpm / truthBpm - 2) < 0.1) truthBpm * 2 else truthBpm
        val period = 60 / grid
        val pos = (t - first) / period
        val errSec = (pos - round(pos)) * period
        c.count++
        if (abs(errSec) <= WINDOW_SEC) c.on++
        val half = pos + 0.5
        if (abs((half - round(half)) * period) <= WINDOW_SEC) c.off++
        c.hist[((errSec * 1000 + 105) / 10).toInt().coerceIn(0, 20)]++
    }

    private fun run(path: String, truthBpm: Double, first: Double): Run? {
        val p = ProcessBuilder("ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", "44100", "-f", "f32le", "-")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val d = DataInputStream(p.inputStream.buffered(1 shl 20))
        val det = BeatDetector(); val hop = BeatHopper(det); val lock = BeatPhaseLock(inline = true)
        val low = BiquadFilter(BiquadFilter.Type.LOWPASS, sr, 150f)
        val mid = BiquadFilter(BiquadFilter.Type.BANDPASS, sr, 1000f)
        val high = BiquadFilter(BiquadFilter.Type.HIGHPASS, sr, 5000f)
        val a = FloatArray(block); val b = FloatArray(block); val m = FloatArray(block); val h = FloatArray(block)
        val raw = ByteArray(block * 4); val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val today = Clock("today"); val tracker = Clock("tracker phase"); val locked = Clock("phase lock")
        var samples = 0L; var slew = 0.0; var bpm = 120f; var lockedN = 0L; var n = 0L; var prevNco = 0.0
        while (true) {
            try { d.readFully(raw) } catch (_: EOFException) { break }
            bb.rewind()
            for (k in 0 until block) { val x = bb.getFloat(); a[k] = x; b[k] = low.process(x); m[k] = mid.process(x); h[k] = high.process(x) }
            bpm = hop.process(a, b, m, h, block, sr)
            lock.process(a, block, sr, bpm)
            val dt = block / sr.toDouble()
            val t = (samples + block) / sr.toDouble()
            val db = dt * bpm / 60
            for (c in listOf(today, locked)) {
                c.prev = c.beats
                c.beats += db
            }
            val active = slew * 0.1; locked.beats += active; slew -= active
            val correction = lock.steer(locked.beats, det.isTempoLocked)
            if (correction != 0.0) {
                locked.moved += abs(correction)
                if (abs(correction) > BeatPhaseLock.JUMP_BEATS) { locked.beats += correction; locked.jumps++ } else slew += correction
            }
            for (c in listOf(today, locked)) {
                if (floor(c.beats) != floor(c.prev)) {
                    val over = (c.beats - floor(c.beats)) / (c.beats - c.prev).coerceAtLeast(1e-9) * dt
                    score(c, t - over.coerceIn(0.0, dt), bpm.toDouble(), truthBpm, first)
                }
            }
            val nco = det.engine.getPhase((samples + block) / sr.toDouble())
            if (nco < prevNco && prevNco - nco > 0.5) score(tracker, t, bpm.toDouble(), truthBpm, first)
            prevNco = nco
            samples += block
            if (samples > sr * WARMUP_SEC) { n++; if (lock.isLocked) lockedN++ }
        }
        p.waitFor(5, TimeUnit.SECONDS)
        if (samples < sr * 30) return null
        return Run(path, samples / sr.toDouble(), listOf(today, tracker, locked), lockedN.toDouble() / maxOf(1L, n))
    }

    private fun <T> pool(items: List<T>, f: (T) -> Run?): List<Run> {
        val q = ConcurrentLinkedQueue<Run>()
        val ex = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 2)
        for (i in items) ex.submit { try { f(i)?.let(q::add) } catch (e: Throwable) { println("$i: $e") } }
        ex.shutdown(); ex.awaitTermination(4, TimeUnit.HOURS)
        return q.toList()
    }

    @Test
    fun report() {
        val list = System.getenv("LSD_PHASE_LIST"); val noBeat = System.getenv("LSD_PHASE_NOBEAT")
        assumeTrue(list != null || noBeat != null)
        val o = StringBuilder()
        val pct = { v: Double -> String.format(Locale.ROOT, "%.1f%%", v * 100) }
        if (list != null) {
            val rows = File(list).readLines().filter { it.isNotBlank() }.map { it.split('\t') }
            val runs = pool(rows) { run(it[2], it[0].toDouble(), it[1].toDouble()) }
            o.append("Clock beats within ${(WINDOW_SEC * 1000).toInt()} ms of a Mixxx beat, and of the half-way point between beats. ${runs.size} tracks.\n")
            for (i in 0..2) {
                val cs = runs.map { it.clocks[i] }
                val total = cs.sumOf { it.count }.coerceAtLeast(1)
                val mean = cs.filter { it.count > 0 }.map { it.on.toDouble() / it.count }
                val hist = IntArray(21); for (c in cs) for (k in 0..20) hist[k] += c.hist[k]
                o.append(String.format(Locale.ROOT, "%-14s on %s  off-beat %s  tracks at 60%%+ on: %d of %d\n", cs[0].name, pct(cs.sumOf { it.on }.toDouble() / total), pct(cs.sumOf { it.off }.toDouble() / total), mean.count { it >= 0.6 }, mean.size))
                o.append("  error ms -100..+100, 10 ms bins: ").append(hist.joinToString(" ")).append('\n')
            }
            val l = runs.map { it.clocks[2] }; val mins = runs.sumOf { it.secs } / 60
            o.append("lock on ${pct(runs.sumOf { it.locked * it.secs } / runs.sumOf { it.secs })}, jumps/min ${String.format(Locale.ROOT, "%.2f", l.sumOf { it.jumps } / mins)}\n")
        }
        if (noBeat != null) {
            o.append("No-beat audio: how much the lock moves the clock.\n")
            for (r in pool(File(noBeat).readLines().filter { it.isNotBlank() }) { run(it, 0.0, 0.0) }) {
                val c = r.clocks[2]
                o.append(String.format(Locale.ROOT, "%-44s %4.0f min  lock on %s  jumps/min %.2f  beats moved/min %.3f\n", File(r.path).name.take(44), r.secs / 60, pct(r.locked), c.jumps / (r.secs / 60), c.moved / (r.secs / 60)))
            }
        }
        File(System.getenv("LSD_PHASE_REPORT") ?: "build/phase-report.txt").writeText(o.toString())
    }

    private companion object {
        const val WARMUP_SEC = 15.0
        const val WINDOW_SEC = 0.07
    }
}
