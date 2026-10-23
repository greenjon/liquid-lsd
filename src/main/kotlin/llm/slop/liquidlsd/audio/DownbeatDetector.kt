package llm.slop.liquidlsd.audio

import llm.slop.liquidlsd.cv.CVRegistry

/** Whether the audio decides where the bar starts. */
enum class DownbeatMode { OFF, AUTO }

/**
 * Finds which beat of the bar is beat 1, on top of a beat count that is already phase-locked to the music.
 *
 * The beat tracker knows where beats fall but not which one opens the bar. This watches the audio around each
 * beat (low-band onsets, overall onsets, low-band level), scores every position in the bar by how strongly the
 * music "lands" there compared with its usual level, and remembers the last few bars. When one position clearly and
 * repeatedly beats the one currently called beat 1, it asks the caller to renumber the beat count by a few whole
 * beats so that position falls on a multiple of [beatsPerBar]. Nothing else changes: the fractional beat phase is
 * untouched, so only bar-length LFOs and sequencers move, and only when the answer changes.
 *
 * Runs on the audio thread: no allocation, no locks, no logging. State is touched only from [process]; the UI
 * thread talks to it through volatile requests ([claimDownbeat]).
 */
class DownbeatDetector {

    @Volatile var beatsPerBar: Int = CVRegistry.DEFAULT_BEATS_PER_BAR

    /** 0..1: how sure we are that the current beat 1 is right. 0 until the detector has seen a few bars. */
    @Volatile var confidence: Float = 0f
        private set

    /** True while a manual claim is protecting the current answer from the detector. */
    @Volatile var isLocked: Boolean = false
        private set

    @Volatile private var claimRequested = false

    // Everything below belongs to the audio thread.
    private val score = FloatArray(CVRegistry.MAX_BEATS_PER_BAR)
    private val rotated = FloatArray(CVRegistry.MAX_BEATS_PER_BAR)
    private var lastBeatsPerBar = beatsPerBar
    private var bucket = NO_BUCKET
    private var kick = 0f
    private var onset = 0f
    private var levelSum = 0f
    private var levelCount = 0
    private var emaKick = 0f
    private var emaOnset = 0f
    private var emaLevel = 0f
    private var previousFeature = 0f
    private var beatsObserved = 0
    private var beatsSinceEvaluation = 0
    private var holdBars = 0
    private var lockBars = 0
    private var decided = false
    private var userClaimed = false

    /**
     * The user (or a tap) has just declared that the current beat is beat 1. The detector forgets what it has
     * learnt, keeps that answer for [LOCK_BARS] bars, and afterwards needs [CLAIM_MARGIN] evidence to overrule it.
     */
    fun claimDownbeat() {
        claimRequested = true
    }

    /** Forgets everything, including any claim. */
    fun reset() {
        clearScores()
        bucket = NO_BUCKET
        decided = false
        userClaimed = false
        lockBars = 0
        isLocked = false
        confidence = 0f
        claimRequested = false
    }

    /**
     * Feeds one audio block.
     *
     * @param beatsEnd the beat count at the end of the block
     * @param deltaBeats how many beats the block covered
     * @param bassFlux low-band onset strength of the block
     * @param onsetStrength overall onset strength of the block
     * @param bassLevel low-band level of the block
     * @return a whole number of beats to add to the beat count (usually 0)
     */
    fun process(beatsEnd: Double, deltaBeats: Double, bassFlux: Float, onsetStrength: Float, bassLevel: Float): Int {
        if (claimRequested) {
            claimRequested = false
            clearScores()
            bucket = NO_BUCKET
            decided = true
            userClaimed = true
            lockBars = LOCK_BARS
            isLocked = true
            confidence = 0f
        }
        val bpb = beatsPerBar.coerceIn(CVRegistry.MIN_BEATS_PER_BAR, CVRegistry.MAX_BEATS_PER_BAR)
        if (bpb != lastBeatsPerBar) {
            lastBeatsPerBar = bpb
            clearScores()
            bucket = NO_BUCKET
            decided = false
            userClaimed = false
            lockBars = 0
            isLocked = false
        }

        // Beat n owns the audio from a little before it to most of the way to the next, because the onset the
        // beat tracker locked to arrives at, or just after, the beat edge.
        val b = Math.floor(beatsEnd - deltaBeats * 0.5 + WINDOW_LEAD).toLong()
        if (bucket == NO_BUCKET) bucket = b
        var shift = 0
        if (b > bucket) {
            if (b == bucket + 1) {
                shift = finishBeat(bucket, bpb)
            } else {
                // The count jumped (a tap, a resync, lost lock): what we gathered no longer lines up.
                clearScores()
            }
            bucket = b + shift
            kick = 0f; onset = 0f; levelSum = 0f; levelCount = 0
        }
        kick += bassFlux
        onset += onsetStrength
        levelSum += bassLevel
        levelCount++
        return shift
    }

    private fun finishBeat(beat: Long, bpb: Int): Int {
        val level = if (levelCount > 0) levelSum / levelCount else 0f
        val kickRel = relative(kick, emaKick)
        val onsetRel = relative(onset, emaOnset)
        val levelRel = relative(level, emaLevel)
        emaKick = if (emaKick == 0f) kick else emaKick + EMA_RATE * (kick - emaKick)
        emaOnset = if (emaOnset == 0f) onset else emaOnset + EMA_RATE * (onset - emaOnset)
        emaLevel = if (emaLevel == 0f) level else emaLevel + EMA_RATE * (level - emaLevel)

        val feature = W_KICK * kickRel + W_ONSET * onsetRel + W_LEVEL * levelRel
        val novelty = Math.max(0f, feature - previousFeature)
        previousFeature = feature

        val position = Math.floorMod(beat, bpb.toLong()).toInt()
        score[position] = score[position] * DECAY + feature + NOVELTY_WEIGHT * novelty
        beatsObserved++
        if (++beatsSinceEvaluation < bpb) return 0
        beatsSinceEvaluation = 0
        return evaluate(bpb)
    }

    /** [value] against its running average; the first beat has no average yet and counts as typical. */
    private fun relative(value: Float, average: Float): Float = if (average <= EPS) 1f else value / average

    private fun evaluate(bpb: Int): Int {
        if (lockBars > 0) {
            lockBars--
            if (lockBars == 0) isLocked = false
        }
        if (beatsObserved < WARMUP_BARS * bpb) return 0

        var best = 0
        for (i in 1 until bpb) if (score[i] > score[best]) best = i
        var second = -1
        for (i in 0 until bpb) if (i != best && (second < 0 || score[i] > score[second])) second = i
        val top = score[best]
        if (top <= EPS) return 0

        if (!decided) {
            val margin = (top - score[second]) / top
            confidence = (margin * CONFIDENCE_GAIN).coerceIn(0f, 1f)
            if (margin < SWITCH_MARGIN) return 0
            decided = true
            return adopt(best, bpb)
        }

        val current = score[0]
        confidence = ((current - score[if (best == 0) second else best]) / top * CONFIDENCE_GAIN).coerceIn(0f, 1f)
        if (best == 0 || lockBars > 0) {
            holdBars = 0
            return 0
        }
        val margin = (top - current) / top
        if (margin < (if (userClaimed) CLAIM_MARGIN else SWITCH_MARGIN)) {
            holdBars = 0
            return 0
        }
        // Make it win twice running before moving the bar line.
        if (++holdBars < HOLD_BARS) return 0
        holdBars = 0
        return adopt(best, bpb)
    }

    /** Makes position [best] the new position 0 and returns the whole-beat shift the caller must apply. */
    private fun adopt(best: Int, bpb: Int): Int {
        val shift = shiftToBarLine(best, bpb)
        for (i in 0 until bpb) rotated[Math.floorMod(i + shift, bpb)] = score[i]
        for (i in 0 until bpb) score[i] = rotated[i]
        return shift
    }

    private fun clearScores() {
        score.fill(0f)
        emaKick = 0f; emaOnset = 0f; emaLevel = 0f
        previousFeature = 0f
        kick = 0f; onset = 0f; levelSum = 0f; levelCount = 0
        beatsObserved = 0
        beatsSinceEvaluation = 0
        holdBars = 0
    }

    companion object {
        private const val NO_BUCKET = Long.MIN_VALUE
        private const val EPS = 1e-6f

        /** A beat's window starts this far before the beat edge, in beats. */
        private const val WINDOW_LEAD = 0.2

        /** How much of the previous bars each position's score keeps per bar (about the last five bars count). */
        private const val DECAY = 0.8f
        private const val EMA_RATE = 0.05f

        private const val W_KICK = 0.45f
        private const val W_ONSET = 0.35f
        private const val W_LEVEL = 0.20f
        private const val NOVELTY_WEIGHT = 0.5f

        /** Bars to watch before the first answer. */
        private const val WARMUP_BARS = 4

        /** How far ahead (relative to the leader) a position must be to take over. */
        private const val SWITCH_MARGIN = 0.15f

        /** The same, against an answer the user gave by hand. */
        private const val CLAIM_MARGIN = 0.30f
        private const val HOLD_BARS = 2

        /** Bars a manual claim is left alone. */
        const val LOCK_BARS = 16
        private const val CONFIDENCE_GAIN = 4f

        /**
         * The whole-beat shift that moves position [position] of the bar to position 0, taking the shorter way
         * (forward on a tie). Adding it to the beat count puts that beat on a multiple of [beatsPerBar].
         */
        fun shiftToBarLine(position: Int, beatsPerBar: Int): Int {
            val p = Math.floorMod(position, beatsPerBar)
            return if (p * 2 >= beatsPerBar && p != 0) beatsPerBar - p else -p
        }
    }
}
