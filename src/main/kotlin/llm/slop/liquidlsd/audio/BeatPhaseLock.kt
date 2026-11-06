package llm.slop.liquidlsd.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Keeps the beat count on the music's beats. [BeatTrackerEngine] says how fast the beat is; this says where it is.
 * Every 250 ms a comb at the tracker's tempo runs over the last 8 s of an onset envelope, and the offset with the most
 * onset energy is the time since the last beat. Readings only steer the clock at a tempo that has held for about 5 s,
 * when the best offset stands well clear of the average one and two readings in a row agree, so talk and ambient music
 * leave it alone. Same method as the web TV's `BeatTracker`/`BeatClock` (web/beattrack.js).
 *
 * The audio thread calls [process] and [steer], which never allocate or wait. The comb is too slow for the audio
 * callback, so it runs on a thread of its own ([startWorker]); offline callers set `inline` and it runs inside [process].
 */
class BeatPhaseLock(private val inline: Boolean = false) {
    private class Reading(val seq: Long, val beatSample: Double, val bpm: Double, val confidence: Double)

    // Envelope, written by the audio thread only. The worker reads WINDOW hops of a longer ring, so it stays clear of the writer.
    private val ring = FloatArray(RING)
    @Volatile private var hopsWritten = 0L
    private var hopSize = 441
    @Volatile private var hopSamples = 441
    @Volatile private var rate = 44100.0
    private var alpha = 0.0
    private var lp1 = 0.0
    private var lp2 = 0.0
    private var low = 0.0
    private var full = 0.0
    private var inHop = 0
    private var prevLow = 0.0
    private var prevFull = 0.0
    private var samples = 0L

    /** The tracker's tempo, for the comb. */
    @Volatile private var tempo = 0f
    @Volatile private var latest: Reading? = null
    private var readingSeq = 0L

    // Comb scratch, used by whichever single thread runs the analysis.
    private val raw = FloatArray(WINDOW)
    private val x = FloatArray(WINDOW)
    private var combBest = 0
    private var combScore = 0.0
    private var combContrast = 0.0

    // Steering state, audio thread only.
    private var lastSeq = -1L
    private var heldBpm = 0.0
    private var heldReadings = 0
    private var hasPending = false
    private var pendingFrac = 0.0
    private var pendingAt = 0L
    private var jumpReadings = 0
    private var offset = 0.0
    @Volatile private var claimRequested = false
    private var claimUntil = -1L
    private var awaitingClaim = false

    /** True while confident readings are steering the clock. */
    @Volatile var isLocked = false
        private set
    private var lastSteerSample = Long.MIN_VALUE

    @Volatile private var worker: Thread? = null

    /** Feeds a block of the full mix, and tells the comb the tracker's tempo. */
    fun process(mix: FloatArray, frames: Int, sampleRate: Float, bpm: Float) {
        tempo = bpm
        if (rate != sampleRate.toDouble() || alpha == 0.0) {
            rate = sampleRate.toDouble()
            hopSize = max(1, round(sampleRate * HOP_SEC).toInt())
            hopSamples = hopSize
            alpha = 1 - exp(-2 * Math.PI * LOW_HZ / sampleRate)
        }
        for (i in 0 until frames) {
            val v = mix[i].toDouble()
            lp1 += alpha * (v - lp1)
            lp2 += alpha * (lp1 - lp2)
            low += lp2 * lp2
            full += v * v
            if (++inHop >= hopSize) {
                val l = ln(1 + 1000 * low / hopSize)
                val f = ln(1 + 1000 * full / hopSize)
                val written = hopsWritten
                ring[(written % RING).toInt()] = (2 * max(0.0, l - prevLow) + max(0.0, f - prevFull)).toFloat()
                hopsWritten = written + 1
                prevLow = l
                prevFull = f
                low = 0.0
                full = 0.0
                inHop = 0
                if (inline && (written + 1) % READING_HOPS == 0L) analyse()
            }
        }
        samples += frames
    }

    /**
     * Applies the newest reading to the clock, once. [totalBeats] is the beat count at the end of the block just given to
     * [process]. Returns the beats to add to it: small ones are a share of the error, to be slewed in; one over
     * [JUMP_BEATS] is a confirmed move to a new grid, always forward, to apply at once.
     */
    fun steer(totalBeats: Double, tempoLocked: Boolean): Double {
        if (lastSteerSample != Long.MIN_VALUE && samples - lastSteerSample > LOCK_HOLD_SEC * rate) isLocked = false
        if (claimRequested) {
            claimRequested = false
            claimUntil = samples + (CLAIM_SETTLE_SEC * rate).toLong()
            awaitingClaim = true
        }
        val r = latest ?: return 0.0
        if (r.seq == lastSeq) return 0.0
        lastSeq = r.seq

        if (heldBpm > 0 && abs(r.bpm / heldBpm - 1) < TEMPO_TOLERANCE) {
            heldReadings++
        } else {
            heldBpm = r.bpm
            heldReadings = 1
            if (!awaitingClaim) offset = 0.0
        }
        if (!tempoLocked || heldReadings < TEMPO_SETTLE || r.confidence < LOCK_CONFIDENCE) {
            hasPending = false
            return 0.0
        }
        val perSample = r.bpm / 60.0 / rate
        val frac = mod1((samples - r.beatSample) * perSample)
        val hadPending = hasPending
        val expected = pendingFrac + (samples - pendingAt) * perSample
        hasPending = true
        pendingFrac = frac
        pendingAt = samples
        if (!hadPending) return 0.0
        if (abs(wrapHalf(frac - expected)) > READING_AGREEMENT) {
            jumpReadings = 0
            return 0.0
        }
        isLocked = true
        lastSteerSample = samples
        if (awaitingClaim) {
            // A person aligned the beat by hand: follow the music from where they put it.
            if (samples < claimUntil) return 0.0
            offset = wrapHalf(totalBeats - frac)
            awaitingClaim = false
            jumpReadings = 0
            return 0.0
        }
        // A small error either way is slewed; a large one moves forward to the next beat, so the count never runs back.
        var err = wrapHalf(frac + offset - totalBeats)
        if (err < -JUMP_BEATS) err += 1.0
        if (err > JUMP_BEATS) {
            if (++jumpReadings < JUMP_CONFIRM) return 0.0
            jumpReadings = 0
            offset = 0.0 // the grid has moved on: a new track, so the hand alignment no longer applies
            return err
        }
        jumpReadings = 0
        return err * BLEND
    }

    /** A person has just aligned the beat (tap, SNAP): hold off, then keep their offset from the music's grid. */
    fun claimPhase() {
        claimRequested = true
    }

    /** The comb, over the envelope so far. Publishes a reading for [steer]. Runs on one thread at a time. */
    internal fun analyse() {
        val end = hopsWritten
        val n = min(end, WINDOW.toLong()).toInt()
        val bpm = tempo.toDouble()
        if (n < MIN_HOPS || bpm <= 1.0) return
        val hop = hopSamples
        val first = end - n
        for (i in 0 until n) raw[i] = ring[((first + i) % RING).toInt()]
        var energy = 0.0
        for (i in 0 until n) {
            var s = 0.0
            var c = 0
            for (j in max(0, i - AVERAGE_HALF)..min(n - 1, i + AVERAGE_HALF)) { s += raw[j]; c++ }
            x[i] = max(0.0, raw[i] - s / c).toFloat()
            energy += x[i] * x[i]
        }
        if (energy < 1e-9) return

        // The tracker's tempo is only good to a lag or so, which over 8 s smears the comb and drags the phase.
        val hopSec = hop / rate
        val base = 60 / bpm / hopSec
        var period = base
        comb(base, 0.0, n.toDouble(), 1.0, n)
        var bestFit = combScore
        for (k in -FINE_STEPS..FINE_STEPS) {
            val per = base * (1 + k * FINE_STEP)
            comb(per, 0.0, n.toDouble(), 1.0, n)
            if (combScore > bestFit) { bestFit = combScore; period = per }
        }
        comb(period, 0.0, n / 2.0, 1.0, n)
        val older = combBest
        comb(period, n / 2.0, n.toDouble(), 1.0, n)
        val newer = combBest
        comb(period, 0.0, n.toDouble(), COMB_DECAY, n)
        // A real beat puts the older and the newer half of the window on the same grid.
        val halvesAgree = abs(wrapHalf((older - newer) / period)) < HALVES_AGREEMENT
        val confidence = if (halvesAgree) ((combContrast - CONTRAST_FLOOR) / CONTRAST_RANGE).coerceIn(0.0, 1.0) else 0.0
        val lastHopEnd = end.toDouble() * hop
        latest = Reading(++readingSeq, lastHopEnd - combBest.toDouble() * hop, 60 / (period * hopSec), confidence)
    }

    /** For each offset (hops before the newest), the decayed sum of onsets one [period] apart, within positions [lo]..[hi]. */
    private fun comb(period: Double, lo: Double, hi: Double, decay: Double, n: Int) {
        val steps = ceil(period).toInt()
        var best = 0
        var bestS = -1.0
        var total = 0.0
        for (p in 0 until steps) {
            var s = 0.0
            var w = 1.0
            var pos = (n - 1 - p).toDouble()
            while (pos >= lo) {
                if (pos < hi) s += w * max(sample(pos, n), max(sample(pos - 1, n), sample(pos + 1, n)))
                pos -= period
                w *= decay
            }
            total += s
            if (s > bestS) { bestS = s; best = p }
        }
        combBest = best
        combScore = bestS
        combContrast = (bestS - total / steps) / (bestS + 1e-9)
    }

    private fun sample(pos: Double, n: Int): Double {
        if (pos < 0 || pos > n - 1) return 0.0
        val i = floor(pos).toInt()
        val f = pos - i
        return x[i] * (1 - f) + (if (i + 1 < n) x[i + 1].toDouble() else 0.0) * f
    }

    /** Runs the comb four times a second until [stopWorker]. Does nothing if it is already running. */
    fun startWorker() {
        if (inline || worker != null) return
        worker = Thread({
            var seen = -1L
            try {
                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep((READING_HOPS * HOP_SEC * 1000).toLong())
                    val now = hopsWritten
                    if (now != seen) { seen = now; analyse() }
                }
            } catch (_: InterruptedException) {
                // stopped
            }
        }, "beat-phase-lock").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    fun stopWorker() {
        worker?.interrupt()
        worker = null
    }

    /** Forgets the audio. Not while [process] or the worker could be running on another thread with old data that matters. */
    fun reset() {
        hopsWritten = 0L
        lp1 = 0.0; lp2 = 0.0; low = 0.0; full = 0.0
        inHop = 0
        prevLow = 0.0; prevFull = 0.0
        samples = 0L
        latest = null
        lastSeq = -1L
        heldBpm = 0.0
        heldReadings = 0
        hasPending = false
        jumpReadings = 0
        offset = 0.0
        claimRequested = false
        awaitingClaim = false
        isLocked = false
        lastSteerSample = Long.MIN_VALUE
    }

    private fun wrapHalf(v: Double) = v - round(v)
    private fun mod1(v: Double) = v - floor(v)

    companion object {
        private const val HOP_SEC = 0.01
        private const val LOW_HZ = 150.0
        private const val WINDOW = 800            // 8 s of envelope
        private const val RING = 1024
        private const val MIN_HOPS = 300          // 3 s before the first reading
        private const val READING_HOPS = 25       // a reading every 250 ms
        private const val AVERAGE_HALF = 7        // onsets are measured against their 150 ms average
        private const val FINE_STEPS = 20         // the comb's period is fine-tuned +-2%
        private const val FINE_STEP = 0.001
        private const val COMB_DECAY = 0.85       // weight of each older beat
        private const val HALVES_AGREEMENT = 0.1  // beats by which the window's two halves may differ
        private const val CONTRAST_FLOOR = 0.35   // the best offset's lead over the average one that reads as confidence 0
        private const val CONTRAST_RANGE = 0.3    // and the further lead that reads as confidence 1
        private const val LOCK_CONFIDENCE = 0.35  // below this a reading does not move the clock
        private const val TEMPO_TOLERANCE = 0.03
        private const val TEMPO_SETTLE = 20       // readings (about 5 s) a tempo must hold first; music holds, talk does not
        private const val READING_AGREEMENT = 0.1 // beats by which two readings in a row may differ
        private const val JUMP_CONFIRM = 8        // readings (about 2 s) that must agree before the clock jumps
        internal const val JUMP_BEATS = 0.25
        private const val BLEND = 0.25            // share of a small error taken from each reading
        private const val LOCK_HOLD_SEC = 4.0     // isLocked stays on this long after the last agreeing reading
        private const val CLAIM_SETTLE_SEC = 4.0  // after a hand alignment, how long before its offset is measured
    }
}
