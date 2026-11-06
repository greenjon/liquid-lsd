package llm.slop.liquidlsd.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeatPhaseLockTest {
    private val sr = 44100f
    private val block = 512

    /** A decaying 60 Hz kick every beat, the first at [firstSec]. */
    private fun kicks(bpm: Double, firstSec: Double, startSample: Long, out: FloatArray) {
        val period = 60.0 / bpm
        for (i in out.indices) {
            val t = (startSample + i) / sr.toDouble() - firstSec
            val since = if (t < 0) -1.0 else t - period * kotlin.math.floor(t / period)
            out[i] = if (since < 0) 0f else (0.8 * exp(-since * 25) * sin(2 * PI * 60 * since)).toFloat()
        }
    }

    /** The clock as AudioEngine runs it: integrates tempo, slews small corrections, applies jumps. */
    private inner class Sim(val lock: BeatPhaseLock = BeatPhaseLock(inline = true)) {
        var beats = 0.0
        var slew = 0.0
        var samples = 0L
        var smallest = 0.0

        fun step(buf: FloatArray, bpm: Float, tempoLocked: Boolean = true) {
            lock.process(buf, block, sr, bpm)
            beats += block / sr.toDouble() * bpm / 60
            val active = slew * 0.1
            beats += active
            slew -= active
            val c = lock.steer(beats, tempoLocked)
            smallest = minOf(smallest, c)
            if (abs(c) > BeatPhaseLock.JUMP_BEATS) beats += c else slew += c
            samples += block
        }

        fun run(seconds: Double, bpm: Double, firstSec: Double, claimAt: Double = -1.0) {
            val buf = FloatArray(block)
            var claimed = false
            while (samples < seconds * sr) {
                if (claimAt >= 0 && !claimed && samples >= claimAt * sr) { lock.claimPhase(); claimed = true }
                kicks(bpm, firstSec, samples, buf)
                step(buf, bpm.toFloat())
            }
        }

        /** Distance from the kick grid, in milliseconds. */
        fun errorMs(bpm: Double, firstSec: Double): Double {
            val expected = (samples / sr.toDouble() - firstSec) * bpm / 60
            val d = beats - expected
            return (d - round(d)) * 60000 / bpm
        }
    }

    @Test
    fun clockLandsOnTheKicksAtAnyTempo() {
        for ((bpm, first) in listOf(96.0 to 0.31, 120.0 to 0.17, 128.0 to 0.05, 174.0 to 0.2)) {
            val sim = Sim()
            sim.run(30.0, bpm, first)
            assertTrue(sim.lock.isLocked, "locked at $bpm")
            assertTrue(abs(sim.errorMs(bpm, first)) < 25.0, "$bpm BPM is ${sim.errorMs(bpm, first)} ms off")
        }
    }

    @Test
    fun aSmallTempoErrorDoesNotDriftAway() {
        val sim = Sim()
        val buf = FloatArray(block)
        while (sim.samples < 120 * sr) {
            kicks(120.0, 0.1, sim.samples, buf)
            sim.step(buf, 120.3f) // 0.25% off: a free clock would be 0.6 beat out by now
        }
        val d = sim.beats - (sim.samples / sr.toDouble() - 0.1) * 120.3 / 60
        assertTrue(abs(d) > 0.1, "steering must have moved the clock")
        val onGrid = sim.beats - (sim.samples / sr.toDouble() - 0.1) * 2.0
        assertTrue(abs(onGrid - round(onGrid)) * 500 < 40.0, "off by ${abs(onGrid - round(onGrid)) * 500} ms")
    }

    @Test
    fun largeCorrectionsOnlyGoForward() {
        val sim = Sim()
        sim.run(30.0, 120.0, 0.27)
        assertTrue(sim.smallest >= -BeatPhaseLock.JUMP_BEATS, "moved back ${sim.smallest}")
    }

    @Test
    fun noiseBarelyMovesTheClock() {
        val rnd = Random(3)
        val sim = Sim()
        val buf = FloatArray(block)
        var start = 0.0
        while (sim.samples < 60 * sr) {
            for (i in buf.indices) buf[i] = (rnd.nextFloat() - 0.5f) * 0.4f
            sim.step(buf, 120f)
            if (sim.samples < 20 * sr) start = sim.beats
        }
        // A stray agreeing reading can nudge it, as on real speech, but it must never jump.
        val free = start + (sim.samples / sr.toDouble() - 20.0) * 2.0
        assertTrue(abs(sim.beats - free) < BeatPhaseLock.JUMP_BEATS, "noise moved the clock ${sim.beats - free} beats")
    }

    @Test
    fun anUnlockedTempoDoesNotSteer() {
        val sim = Sim()
        val buf = FloatArray(block)
        while (sim.samples < 30 * sr) {
            kicks(120.0, 0.27, sim.samples, buf)
            sim.step(buf, 120f, tempoLocked = false)
        }
        assertEquals(sim.samples / sr.toDouble() * 2.0, sim.beats, 1e-6)
        assertFalse(sim.lock.isLocked)
    }

    @Test
    fun aHandAlignmentIsKeptAgainstTheGrid() {
        val sim = Sim()
        sim.run(20.0, 120.0, 0.1)
        assertTrue(abs(sim.errorMs(120.0, 0.1)) < 25.0)
        // A person moves the beat: 0.2 beat ahead of the grid.
        sim.beats += 0.2
        sim.lock.claimPhase()
        sim.run(60.0, 120.0, 0.1)
        assertEquals(100.0, sim.errorMs(120.0, 0.1), 30.0)
    }

    @Test
    fun theWorkerThreadSteersTheClock() {
        val sim = Sim(BeatPhaseLock())
        val buf = FloatArray(block)
        sim.lock.startWorker()
        try {
            var blocks = 0
            while (sim.samples < 40 * sr) { // 40 s of audio in about 8 s
                kicks(120.0, 0.17, sim.samples, buf)
                sim.step(buf, 120f)
                if (++blocks % 86 == 0) Thread.sleep(200)
            }
        } finally {
            sim.lock.stopWorker()
        }
        assertTrue(sim.lock.isLocked)
        assertTrue(abs(sim.errorMs(120.0, 0.17)) < 30.0, "off by ${sim.errorMs(120.0, 0.17)} ms")
    }
}
