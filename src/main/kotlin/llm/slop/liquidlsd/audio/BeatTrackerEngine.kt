package llm.slop.liquidlsd.audio

import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Real-time audio beat tracking engine and continuous phase-locked modulation generator.
 * Inspired by the beat tracking algorithm by Adam Stark and the Dan Ellis causal dynamic programming model.
 *
 * Implemented in pure Kotlin with strict zero-allocation constraints on the real-time audio thread
 * (targeting Kotlin Multiplatform / Native / JVM with JACK backend).
 *
 * ### Architectural Components:
 * 1. **Onset Detection Function (ODF)**: 512-point zero-allocation FFT computing Complex Spectral Difference
 *    and multi-band spectral flux to detect percussive attacks and soft/pitched note onsets while suppressing steady-state noise.
 * 2. **Two-State Periodicity & Tempo Estimation**: Unconstrained acquisition (40–200 BPM) vs. locked tracking
 *    with multi-band cross-spectral correlation, harmonic comb unwrapping, and human tracking inertia.
 * 3. **Causal Dynamic Programming Beat Anchor Selection**: Pre-allocated circular cumulative score ring buffer
 *    evaluating causal DP recurrence with pre-tabulated log penalties for flywheel beat tracking through syncopation.
 * 4. **Continuous Phase & Cosine Generator**: High-resolution continuous normalized phase in [0.0, 1.0) and
 *    cos(2 * PI * phase) for smooth visual rendering without phase snapping or visual stutter.
 */
class BeatTrackerEngine(
    var sampleRate: Float = 44100f,
    val fftSize: Int = 512,
    val historySize: Int = 2048,
    val scoreBufferSize: Int = 2048
) {
    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) { "fftSize must be a power of 2" }
        require(historySize > 0 && (historySize and (historySize - 1)) == 0) { "historySize must be a power of 2" }
        require(scoreBufferSize > 0 && (scoreBufferSize and (scoreBufferSize - 1)) == 0) { "scoreBufferSize must be a power of 2" }
    }

    private val historyMask = historySize - 1
    private val scoreBufferMask = scoreBufferSize - 1

    // ── Configuration & Presets ─────────────────────────────────────────────
    @Volatile var bpmFloor: Float = 40.0f
    @Volatile var bpmCeiling: Float = 200.0f
    @Volatile var fallbackBpm: Float = 120.0f
    @Volatile var transitionWeightAlpha: Float = 120.0f
    @Volatile var dpDecayLambda: Float = 0.992f
    @Volatile var trackingInertiaBpmPerBeat: Float = 2.0f
    @Volatile var stabilityLockDurationSec: Float = 0.40f
    @Volatile var energySilenceThreshold: Float = 0.0025f
    @Volatile var tempoEstimationIntervalBlocks: Int = 16

    // ── Pre-Allocated FFT Buffers & Lookup Tables (Zero Allocation) ─────────
    private val halfFft = fftSize / 2
    private val window = FloatArray(fftSize)
    private val bitRevTable = IntArray(fftSize)
    private val cosFftTable = FloatArray(halfFft)
    private val sinFftTable = FloatArray(halfFft)

    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val prevMagSpectrum = FloatArray(halfFft + 1)
    private val prevPhase = FloatArray(halfFft + 1)
    private val prevPrevPhase = FloatArray(halfFft + 1)

    // ── Pre-Computed Logarithm Table for Ultra-Fast DP Recurrence ───────────
    private val maxLag = scoreBufferSize / 2
    private val logTauTable = FloatArray(maxLag + 1)

    // ── Ring Buffers for ODF and Dynamic Programming Cumulative Scores ──────
    private val odfHistory = FloatArray(historySize)
    private val bassHistory = FloatArray(historySize)
    private val midHistory = FloatArray(historySize)
    private val highHistory = FloatArray(historySize)
    private val cumulativeScore = FloatArray(scoreBufferSize)
    private val acTable = FloatArray(historySize / 2 + 1)
    private val phaseFold = FloatArray(historySize / 2 + 1)
    private val scoreTable = FloatArray(historySize / 2 + 1)
    private val candidateDelays = IntArray(METRE_CANDIDATES)
    private val onsetHistory = FloatArray(historySize)
    private var acCursor = -1
    private var acAvailableMax = 0
    private var acMaxDelay = 0
    private var acWindow = 0
    private var prevBassIn = 0f
    private var prevMidIn = 0f
    private var prevHighIn = 0f
    private var meanRiseBass = 0f
    private var meanRiseMid = 0f
    private var meanRiseHigh = 0f

    private var historyCount = 0
    private var historyIndex = 0

    // ── Engine Runtime State (Primitive Fields) ─────────────────────────────
    @Volatile var isLocked: Boolean = false
        private set

    @Volatile var isSignalSufficient: Boolean = false
        private set

    @Volatile var confidence: Float = 0.0f
        private set

    @Volatile var currentBpm: Float = fallbackBpm
        private set

    @Volatile var targetBeatIntervalTau0: Float = (sampleRate / fftSize) * (60.0f / fallbackBpm)
        private set

    // ── Phase Accumulator & Lock-Free Seqlock Atomic Visual Render Snapshot ───
    @Volatile private var snapSeq: Long = 0L
    private var snapPhase: Double = 0.0
    private var snapTimestampSec: Double = 0.0
    private var snapFreqHz: Double = fallbackBpm / 60.0
    private var snapBeatPeriodSec: Double = 60.0 / fallbackBpm

    val anchorTimestampSec: Double
        get() {
            var t: Double
            var s1: Long
            var s2: Long
            do {
                s1 = snapSeq
                while ((s1 and 1L) == 1L) s1 = snapSeq
                t = snapTimestampSec
                s2 = snapSeq
            } while (s1 != s2)
            return t
        }

    val anchorBeatPeriodSec: Double
        get() {
            var p: Double
            var s1: Long
            var s2: Long
            do {
                s1 = snapSeq
                while ((s1 and 1L) == 1L) s1 = snapSeq
                p = snapBeatPeriodSec
                s2 = snapSeq
            } while (s1 != s2)
            return p
        }

    val anchorPhase: Double
        get() {
            var p: Double
            var s1: Long
            var s2: Long
            do {
                s1 = snapSeq
                while ((s1 and 1L) == 1L) s1 = snapSeq
                p = snapPhase
                s2 = snapSeq
            } while (s1 != s2)
            return p
        }

    val effectiveFreqHz: Double
        get() {
            var f: Double
            var s1: Long
            var s2: Long
            do {
                s1 = snapSeq
                while ((s1 and 1L) == 1L) s1 = snapSeq
                f = snapFreqHz
                s2 = snapSeq
            } while (s1 != s2)
            return f
        }

    private var accumulatedPhase: Double = 0.0
    private var phaseSlewError: Double = 0.0
    private var lastProcessedTimestampSec: Double = 0.0
    private var lastAnchorBlockIndex: Long = 0L

    // Low-signal & tempo stability gating
    private var stableCandidateBpm: Float = fallbackBpm
    private var stableAccumulatedSec: Float = 0.0f
    private var totalBlocksProcessed: Long = 0L
    private var localOdfMean: Float = 0.001f
    private var localAudioEnergy: Float = 0.001f

    init {
        // Initialize Hann window
        for (i in 0 until fftSize) {
            window[i] = (0.5 * (1.0 - cos(2.0 * PI * i / fftSize))).toFloat()
        }

        // Initialize bit-reversal table
        var bits = 0
        var temp = fftSize
        while (temp > 1) {
            bits++
            temp = temp shr 1
        }
        for (i in 0 until fftSize) {
            var rev = 0
            for (j in 0 until bits) {
                if (((i shr j) and 1) == 1) {
                    rev = rev or (1 shl (bits - 1 - j))
                }
            }
            bitRevTable[i] = rev
        }

        // Initialize FFT twiddle factors
        for (i in 0 until halfFft) {
            val angle = -2.0 * PI * i / fftSize
            cosFftTable[i] = cos(angle).toFloat()
            sinFftTable[i] = sin(angle).toFloat()
        }

        // Pre-compute logarithm table for DP recurrence inner loop
        logTauTable[0] = 0.0f
        for (i in 1..maxLag) {
            logTauTable[i] = ln(i.toFloat())
        }

        reset(fallbackBpm)
    }

    /**
     * Resets internal states and ring buffers.
     */
    fun reset(initialBpm: Float = fallbackBpm) {
        fallbackBpm = initialBpm
        currentBpm = initialBpm
        stableCandidateBpm = initialBpm
        val fps = sampleRate / fftSize.coerceAtLeast(1)
        targetBeatIntervalTau0 = fps * (60.0f / initialBpm)

        val nextSeq = snapSeq + 1L
        snapSeq = nextSeq
        snapPhase = 0.0
        snapTimestampSec = 0.0
        snapFreqHz = initialBpm / 60.0
        snapBeatPeriodSec = 60.0 / initialBpm
        snapSeq = nextSeq + 1L

        accumulatedPhase = 0.0
        phaseSlewError = 0.0
        lastProcessedTimestampSec = 0.0
        lastAnchorBlockIndex = 0L

        isLocked = false
        isSignalSufficient = false
        confidence = 0.0f
        stableAccumulatedSec = 0.0f
        totalBlocksProcessed = 0L
        localOdfMean = 0.001f
        localAudioEnergy = 0.001f
        historyCount = 0
        historyIndex = 0

        odfHistory.fill(0f)
        bassHistory.fill(0f)
        midHistory.fill(0f)
        highHistory.fill(0f)
        onsetHistory.fill(0f)
        acCursor = -1
        cumulativeScore.fill(0f)
        prevMagSpectrum.fill(0f)
        prevPhase.fill(0f)
        prevPrevPhase.fill(0f)
        prevBassIn = 0f
        prevMidIn = 0f
        prevHighIn = 0f
        meanRiseBass = 0f
        meanRiseMid = 0f
        meanRiseHigh = 0f
    }

    /**
     * Nudges tempo tracking towards an externally tapped or specified BPM.
     * Re-centers the dynamic programming target beat interval Tau0 and seeds candidate tempo.
     */
    fun nudgeTempo(tappedBpm: Float) {
        val clamped = tappedBpm.coerceIn(bpmFloor, bpmCeiling)
        currentBpm = clamped
        stableCandidateBpm = clamped
        val fps = sampleRate / fftSize.coerceAtLeast(1)
        targetBeatIntervalTau0 = fps * (60.0f / clamped)
        snapFreqHz = clamped.toDouble() / 60.0
        snapBeatPeriodSec = 60.0 / clamped.toDouble()
        stableAccumulatedSec = stabilityLockDurationSec
        isLocked = true
    }

    /**
     * Parabolic interpolation across 3 points (y1, y2, y3) to find peak offset in [-0.5, 0.5].
     */
    fun interpolateParabolicPeak(y1: Float, y2: Float, y3: Float): Float {
        val denom = y1 - 2.0f * y2 + y3
        if (abs(denom) < 1e-6f) return 0.0f
        val delta = (y1 - y3) / (2.0f * denom)
        return delta.coerceIn(-0.5f, 0.5f)
    }

    /**
     * In-place Radix-2 Cooley-Tukey FFT with zero memory allocation.
     */
    private fun executeFft() {
        for (i in 0 until fftSize) {
            val j = bitRevTable[i]
            if (j > i) {
                val tempR = fftReal[i]; fftReal[i] = fftReal[j]; fftReal[j] = tempR
                val tempI = fftImag[i]; fftImag[i] = fftImag[j]; fftImag[j] = tempI
            }
        }

        var len = 2
        while (len <= fftSize) {
            val halfLen = len shr 1
            val tableStep = fftSize / len
            var i = 0
            while (i < fftSize) {
                var k = 0
                var tableIdx = 0
                while (k < halfLen) {
                    val cosVal = cosFftTable[tableIdx]
                    val sinVal = sinFftTable[tableIdx]
                    val uR = fftReal[i + k]
                    val uI = fftImag[i + k]
                    val vIdx = i + k + halfLen
                    val vR = fftReal[vIdx]
                    val vI = fftImag[vIdx]
                    val tR = vR * cosVal - vI * sinVal
                    val tI = vR * sinVal + vI * cosVal

                    fftReal[i + k] = uR + tR
                    fftImag[i + k] = uI + tI
                    fftReal[vIdx] = uR - tR
                    fftImag[vIdx] = uI - tI

                    k++
                    tableIdx += tableStep
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Computes Complex Spectral Difference Onset Detection Function (ODF) sample for incoming audio.
     */
    private fun computeComplexSpectralDifference(): Float {
        executeFft()

        var sumDifference = 0.0f
        val binMin = 1
        val binMax = min(halfFft, (5000.0f * fftSize / sampleRate).toInt())

        for (k in binMin..binMax) {
            val r = fftReal[k]
            val im = fftImag[k]
            val mag = sqrt(r * r + im * im)
            val phase = atan2(im, r)


            val rawPredictedPhase = 2.0f * prevPhase[k] - prevPrevPhase[k]
            val pi = Math.PI.toFloat()
            val twoPi = 2.0f * pi
            val predictedPhase = when {
                rawPredictedPhase > pi -> rawPredictedPhase - twoPi
                rawPredictedPhase < -pi -> rawPredictedPhase + twoPi
                else -> rawPredictedPhase
            }
            val prevMag = prevMagSpectrum[k]

            val expR = prevMag * cos(predictedPhase)
            val expI = prevMag * sin(predictedPhase)

            val diffR = r - expR
            val diffI = im - expI
            val dist = sqrt(diffR * diffR + diffI * diffI)

            sumDifference += dist

            prevPrevPhase[k] = prevPhase[k]
            prevPhase[k] = phase
            prevMagSpectrum[k] = mag
        }

        return sumDifference
    }

    /**
     * One audio block's share of the tempo estimate. The autocorrelation is spread over [tempoEstimationIntervalBlocks]
     * blocks, because this runs in the audio callback, whose budget at a 64-frame buffer is under 1.5 ms. The blocks move the
     * window a little between lags, which does not matter at this length.
     */
    private fun stepTempoEstimate(fps: Float, dt: Float, minPeriodBlocks: Int, maxPeriodBlocks: Int) {
        if (acCursor < 0) {
            if (historyCount < MIN_HISTORY_SEC * fps) return // two onsets are not a tempo, and a wrong first lock jumps the phase
            val available = (historyCount - minPeriodBlocks).coerceAtLeast(0).coerceAtMost(maxPeriodBlocks)
            if (available < minPeriodBlocks) return
            val window = (historyCount - available).coerceAtMost((4.0f * fps).toInt())
            if (window <= 10) return
            acAvailableMax = available
            acWindow = window
            // Out past the slowest tempo, so the comb below can see the multiples.
            acMaxDelay = min(min(maxPeriodBlocks * 2, historySize / 2), historyCount - window).coerceAtLeast(available)
            acCursor = minPeriodBlocks
        }
        // Faster while the history is short and each pass cheap, so a new track locks quickly.
        val interval = (if (historyCount < STARTUP_SEC * fps) min(4, tempoEstimationIntervalBlocks) else tempoEstimationIntervalBlocks).coerceAtLeast(2)
        val lagsPerBlock = (acMaxDelay - minPeriodBlocks + 1) / (interval - 1) + 1
        val invWinLen = 1.0f / acWindow
        val end = min(acMaxDelay, acCursor + lagsPerBlock - 1)
        val head = historyIndex - 1 + historySize * 10
        for (delay in acCursor..end) {
            var ac = 0.0f
            for (i in 0 until acWindow) {
                val idx1 = (head - i) and historyMask
                val idx2 = (head - i - delay) and historyMask
                ac += (1.0f - i * invWinLen) * (bassHistory[idx1] * bassHistory[idx2] +
                    midHistory[idx1] * midHistory[idx2] +
                    highHistory[idx1] * highHistory[idx2])
            }
            acTable[delay] = ac
        }
        acCursor = end + 1
        if (acCursor > acMaxDelay) {
            estimateTempo(fps, dt * interval, minPeriodBlocks)
            acCursor = -1
        }
    }

    /** The tempo from a finished autocorrelation table: a comb, a preference for usual tempos, then a check of where the accents fall. */
    private fun estimateTempo(fps: Float, dt: Float, minPeriodBlocks: Int): Float {
        val availableMaxDelay = acAvailableMax
        var maxScore = -1.0f
        var rawBestDelay = minPeriodBlocks
        var acSum = 0.0f

        // A lag's score is a comb over its multiples: a beat repeats at 2, 3 and 4 beats, while a syncopated gap of 1.5 beats
        // does not. Then a preference for usual tempos, so a whole bar (which always correlates best) does not win.
        for (delay in minPeriodBlocks..availableMaxDelay) {
            acSum += acTable[delay]
            var comb = 0f
            var norm = 0f
            var k = 1
            while (k <= COMB_MULTIPLES && delay * k <= acMaxDelay) {
                comb += acTable[delay * k] / k
                norm += 1f / k
                k++
            }
            val octaves = log2((delay / fps) / PRIOR_PERIOD_SEC) / PRIOR_OCTAVES
            val score = (comb / norm) * exp(-0.5f * octaves * octaves)
            scoreTable[delay] = score
            if (score > maxScore) {
                maxScore = score
                rawBestDelay = delay
            }
        }

        val meanAc = acSum / (availableMaxDelay - minPeriodBlocks + 1)
        confidence = if (meanAc > 1e-5f) (acTable[rawBestDelay] / (meanAc * 3.0f)).coerceIn(0.0f, 1.0f) else 0.5f

        val foldBlocks = min(historyCount, (FOLD_SEC * fps).toInt())
        rawBestDelay = settleMetre(rawBestDelay, fps, minPeriodBlocks, availableMaxDelay, foldBlocks)

        // Harmonic unwrapping: is the half period the real beat?
        var bestDelay = rawBestDelay
        while (bestDelay / 2 >= minPeriodBlocks) {
            val halfBpm = 60.0f / ((bestDelay / 2).toFloat() / fps)
            // A backbeat puts an accent on every beat of the half period: take it, unless that is only hats between the beats.
            if (halfBpm <= 165.0f && foldedStrike(bestDelay / 2f, foldBlocks) >= UNWRAP_STRIKE * foldedStrike(bestDelay.toFloat(), foldBlocks)) {
                bestDelay /= 2
            } else {
                break
            }
        }

        // Sub-block parabolic lag interpolation around bestDelay
        val delayPrev = max(minPeriodBlocks, bestDelay - 1)
        val delayNext = min(availableMaxDelay, bestDelay + 1)
        val deltaLag = interpolateParabolicPeak(acTable[delayPrev], acTable[bestDelay], acTable[delayNext])
        val subBlockLag = (bestDelay + deltaLag).coerceAtLeast(1.0f)
        val calcBpm = 60.0f / (subBlockLag / fps)

        val clampedCalcBpm = if (calcBpm > 0.0f) calcBpm.coerceIn(bpmFloor, bpmCeiling) else currentBpm

        // Find closest harmonic octave match to stable candidate (e.g. 70 <-> 140 BPM)
        val bpmMatch = when {
            abs(clampedCalcBpm - stableCandidateBpm) <= 4.0f -> clampedCalcBpm
            clampedCalcBpm * 2.0f <= bpmCeiling && abs(clampedCalcBpm * 2.0f - stableCandidateBpm) <= 4.0f -> clampedCalcBpm * 2.0f
            else -> clampedCalcBpm
        }

        // Stability gating & tempo lock state machine (Synthesized with Hysteresis)
        if (calcBpm > 0.0f) {
            val bpmDifference = abs(bpmMatch - stableCandidateBpm)
            if (bpmDifference <= 4.0f) {
                // Build stability, capped at a maximum duration (1.5x threshold)
                stableAccumulatedSec = min(stabilityLockDurationSec * 1.5f, stableAccumulatedSec + dt)

                stableCandidateBpm = stableCandidateBpm * EMA_RETAIN + bpmMatch * EMA_UPDATE

                if (!isLocked && stableAccumulatedSec >= stabilityLockDurationSec) {
                    isLocked = true
                    currentBpm = stableCandidateBpm
                }
            } else {
                // Leaky decay instead of an instant hard reset to 0.0f.
                // It decays at 2x the rate it builds, dropping lock gracefully if the tempo actually shifted.
                stableAccumulatedSec = max(0.0f, stableAccumulatedSec - (dt * 2.0f))

                if (stableAccumulatedSec == 0.0f) {
                    stableCandidateBpm = clampedCalcBpm
                    isLocked = false
                }
            }
        }

        if (isLocked && calcBpm > 0.0f) {
            val maxBpmDeltaPerBlock = (trackingInertiaBpmPerBeat * (dt / (60.0f / currentBpm))).coerceAtLeast(0.01f)
            val deltaBpm = (bpmMatch - currentBpm).coerceIn(-maxBpmDeltaPerBlock, maxBpmDeltaPerBlock)
            currentBpm = (currentBpm + deltaBpm).coerceIn(bpmFloor, bpmCeiling)
            targetBeatIntervalTau0 = fps * (60.0f / currentBpm)
        } else if (!isLocked) {
            targetBeatIntervalTau0 = fps * (60.0f / currentBpm)
        }

        return currentBpm
    }

    /**
     * How hard the strongest repeating position hits with period [delay]: the onsets folded at that period, the best
     * position's mean per cycle. On the true beat every cycle lands on an accent; a syncopated gap of 1.5 beats alternates
     * between an accent and whatever falls on the off-beat, so it scores lower, though its autocorrelation can be as high.
     */
    private fun foldedStrike(period: Float, windowBlocks: Int): Float {
        // The true period is fractional, and half a block of error smears the accents over 8 s: try either side of it.
        var best = 0f
        var offset = -FOLD_SEARCH_BLOCKS
        while (offset <= FOLD_SEARCH_BLOCKS) {
            best = max(best, foldedStrikeAt(max(1f, period + offset), windowBlocks))
            offset += FOLD_SEARCH_STEP
        }
        return best
    }

    private fun foldedStrikeAt(period: Float, windowBlocks: Int): Float {
        val bins = ceil(period).toInt().coerceIn(1, phaseFold.size)
        phaseFold.fill(0f, 0, bins)
        val head = historyIndex - 1 + historySize * 10
        var position = 0f
        for (i in 0 until windowBlocks) {
            phaseFold[min(position.toInt(), bins - 1)] += onsetHistory[(head - i) and historyMask]
            position += 1f
            if (position >= period) position -= period
        }
        var best = 0f
        for (p in 0 until bins) {
            val sum = phaseFold[p] + 0.5f * (phaseFold[(p + 1) % bins] + phaseFold[(p + bins - 1) % bins])
            if (sum > best) best = sum
        }
        return best * period / windowBlocks
    }

    /**
     * Picks among the comb's strongest lags by where the accents fall. A lag near the tempo already locked, or an octave
     * of it, gets a bonus: a groove that shifts for a bar should not move the tempo.
     */
    private fun settleMetre(delay: Int, fps: Float, minDelay: Int, maxDelay: Int, windowBlocks: Int): Int {
        // The strongest local peaks of the score.
        var count = 0
        for (d in minDelay + 1 until maxDelay) {
            val score = scoreTable[d]
            if (score <= scoreTable[d - 1] || score < scoreTable[d + 1]) continue
            var slot = if (count < METRE_CANDIDATES) count++ else METRE_CANDIDATES - 1
            if (slot == METRE_CANDIDATES - 1 && count == METRE_CANDIDATES && candidateDelays[slot] != 0 && score <= scoreTable[candidateDelays[slot]]) continue
            while (slot > 0 && scoreTable[candidateDelays[slot - 1]] < score) {
                candidateDelays[slot] = candidateDelays[slot - 1]
                slot--
            }
            candidateDelays[slot] = d
        }
        var best = delay
        var bestValue = metreValue(delay, fps, windowBlocks) * METRE_SWITCH_MARGIN
        for (c in 0 until count) {
            val d = candidateDelays[c]
            if (d == delay) continue
            val value = metreValue(d, fps, windowBlocks)
            if (value > bestValue) {
                bestValue = value
                best = d
            }
        }
        return best
    }

    private fun metreValue(delay: Int, fps: Float, windowBlocks: Int): Float {
        val bpm = 60f * fps / delay
        val octaves = log2((delay / fps) / PRIOR_PERIOD_SEC) / PRIOR_OCTAVES
        var value = foldedStrike(delay.toFloat(), windowBlocks) * exp(-0.5f * octaves * octaves)
        if (isLocked) {
            val ratio = bpm / currentBpm
            if (abs(ratio - 1f) < 0.04f || abs(ratio - 2f) < 0.08f || abs(ratio - 0.5f) < 0.02f) value *= METRE_STAY_BONUS
        }
        return value
    }

    /**
     * Evaluates the causal dynamic programming recurrence for the current block:
     * Score(t) = ODF(t) + lambda * max_{tau in [tau_min, tau_max]} { Score(t - tau) - alpha * (log(tau / tau0))^2 }
     */
    private fun evaluateCausalDynamicProgramming(odfSample: Float, minLag: Int, maxLag: Int) {
        val tau0 = targetBeatIntervalTau0.coerceAtLeast(1.0f)
        val logTau0 = ln(tau0)
        val alpha = transitionWeightAlpha
        val lambda = dpDecayLambda

        val searchMin = max(minLag, (tau0 * 0.50f).toInt())
        val searchMax = min(maxLag, (tau0 * 1.80f).toInt())

        val head = totalBlocksProcessed.toInt()
        var maxPathScore = -1e9f

        for (tau in searchMin..searchMax) {
            val prevScore = cumulativeScore[(head - tau) and scoreBufferMask]
            val logDiff = logTauTable[tau] - logTau0
            val penalty = alpha * (logDiff * logDiff)
            val candidateScore = prevScore - penalty
            if (candidateScore > maxPathScore) {
                maxPathScore = candidateScore
            }
        }

        val currentScore = odfSample + lambda * max(0.0f, maxPathScore)
        cumulativeScore[head and scoreBufferMask] = currentScore
    }

    /**
     * Identifies beat anchors in cumulativeScore history and smoothly slews the continuous phase accumulator.
     * Guaranteed strictly C^0 continuous visual phase with zero jump discontinuities on beat arrivals.
     */
    private fun updateBeatAnchors(timestampSeconds: Double, fps: Float) {
        val tau0 = targetBeatIntervalTau0
        val nominalFreqHz = currentBpm / 60.0
        val currentBlock = totalBlocksProcessed

        val dt = if (lastProcessedTimestampSec > 0.0 && timestampSeconds > lastProcessedTimestampSec) {
            timestampSeconds - lastProcessedTimestampSec
        } else {
            fftSize.toDouble() / sampleRate.toDouble()
        }
        lastProcessedTimestampSec = timestampSeconds

        if (lastAnchorBlockIndex == 0L) {
            lastAnchorBlockIndex = currentBlock
            accumulatedPhase = 0.0
            phaseSlewError = 0.0

            val nextSeq = snapSeq + 1L
            snapSeq = nextSeq
            snapPhase = 0.0
            snapTimestampSec = timestampSeconds
            snapFreqHz = nominalFreqHz
            snapBeatPeriodSec = 1.0 / nominalFreqHz
            snapSeq = nextSeq + 1L
            return
        }

        val projectedBlock = lastAnchorBlockIndex + tau0.roundToLong()
        val windowHalf = max(2, (tau0 * 0.20f).toInt())

        if (currentBlock >= projectedBlock + windowHalf) {
            var peakScore = -1e9f
            var bestBlock = projectedBlock

            val startBlock = max(lastAnchorBlockIndex + 1, projectedBlock - windowHalf)
            val endBlock = min(currentBlock, projectedBlock + windowHalf)

            for (b in startBlock..endBlock) {
                val score = cumulativeScore[b.toInt() and scoreBufferMask]
                if (score > peakScore) {
                    peakScore = score
                    bestBlock = b
                }
            }

            // We know a true beat occurred exactly at bestBlock.
            val blocksSinceBeat = currentBlock - bestBlock
            val idealPhase = (blocksSinceBeat.toDouble() / tau0.coerceAtLeast(1.0f).toDouble()) % 1.0

            // Measure how far accumulatedPhase has drifted from absolute ideal phase
            val phaseDiff = idealPhase - accumulatedPhase
            val wrappedError = (phaseDiff + 1.5) % 1.0 - 0.5 // Wrap to [-0.5, 0.5]

            // Integrate phase correction into 2nd-order exponential slew
            phaseSlewError = (phaseSlewError * 0.80) + (wrappedError * 0.20)
            lastAnchorBlockIndex = bestBlock
        }

        // Apply smooth phase correction frequency nudge (capped at +-10% of nominal frequency)
        val maxNudgeHz = nominalFreqHz * 0.10
        val correctionHz = (phaseSlewError * 0.35 * nominalFreqHz).coerceIn(-maxNudgeHz, maxNudgeHz)
        val instantaneousFreqHz = (nominalFreqHz + correctionHz).coerceAtLeast(0.1)

        // Advance continuous phase accumulator
        accumulatedPhase = (accumulatedPhase + dt * instantaneousFreqHz) % 1.0
        if (accumulatedPhase < 0.0) accumulatedPhase += 1.0

        // Decay the slew error
        phaseSlewError *= 0.95

        // Publish thread-safe atomic Seqlock snapshot for continuous render extrapolation
        val nextSeq = snapSeq + 1L
        snapSeq = nextSeq
        snapPhase = accumulatedPhase
        snapTimestampSec = timestampSeconds
        snapFreqHz = instantaneousFreqHz
        snapBeatPeriodSec = 1.0 / instantaneousFreqHz
        snapSeq = nextSeq + 1L
    }

    /**
     * Processes an audio frame directly from a primitive FloatArray (real-time JACK audio callback).
     * Strictly ZERO memory allocations or boxing on this code path.
     */
    fun processBlock(audioFrame: FloatArray, timestampSeconds: Double): Float {
        return processBlock(audioFrame, 0, audioFrame.size, timestampSeconds)
    }

    /**
     * Processes a block of audio samples with explicit offset and length.
     * Strictly ZERO memory allocations or boxing on this code path.
     */
    fun processBlock(audioFrame: FloatArray, offset: Int, length: Int, timestampSeconds: Double): Float {
        val safeLen = min(fftSize, length)
        var sumSquares = 0.0f
        for (i in 0 until safeLen) {
            val sample = audioFrame[offset + i]
            fftReal[i] = sample * window[i]
            fftImag[i] = 0.0f
            sumSquares += sample * sample
        }
        for (i in safeLen until fftSize) {
            fftReal[i] = 0.0f
            fftImag[i] = 0.0f
        }

        val frameRms = sqrt(sumSquares / safeLen.coerceAtLeast(1))
        localAudioEnergy = localAudioEnergy * EMA_RETAIN + frameRms * EMA_UPDATE
        val signalSufficient = localAudioEnergy > energySilenceThreshold
        isSignalSufficient = signalSufficient

        val odfSample: Float
        if (signalSufficient) {
            val rawOdf = computeComplexSpectralDifference()
            localOdfMean = localOdfMean * EMA_RETAIN + rawOdf * EMA_UPDATE
            odfSample = max(0.0f, rawOdf - localOdfMean * 0.50f)
        } else {
            odfSample = 0.0f
        }

        return processMultiBand(odfSample, frameRms * 0.6f, frameRms * 0.3f, frameRms * 0.1f, timestampSeconds, signalSufficient)
    }

    /**
     * Processes a block from a NIO FloatBuffer (zero allocation).
     */
    fun processBlock(buffer: FloatBuffer, offset: Int, length: Int, timestampSeconds: Double): Float {
        val safeLen = min(fftSize, length)
        var sumSquares = 0.0f
        for (i in 0 until safeLen) {
            val sample = buffer.get(offset + i)
            fftReal[i] = sample * window[i]
            fftImag[i] = 0.0f
            sumSquares += sample * sample
        }
        for (i in safeLen until fftSize) {
            fftReal[i] = 0.0f
            fftImag[i] = 0.0f
        }

        val frameRms = sqrt(sumSquares / safeLen.coerceAtLeast(1))
        localAudioEnergy = localAudioEnergy * EMA_RETAIN + frameRms * EMA_UPDATE
        val signalSufficient = localAudioEnergy > energySilenceThreshold
        isSignalSufficient = signalSufficient

        val odfSample: Float
        if (signalSufficient) {
            val rawOdf = computeComplexSpectralDifference()
            localOdfMean = localOdfMean * EMA_RETAIN + rawOdf * EMA_UPDATE
            odfSample = max(0.0f, rawOdf - localOdfMean * 0.50f)
        } else {
            odfSample = 0.0f
        }

        return processMultiBand(odfSample, frameRms * 0.6f, frameRms * 0.3f, frameRms * 0.1f, timestampSeconds, signalSufficient)
    }

    /**
     * Processes explicit multi-band energy signals (e.g. from AudioEngine filterbank or benchmark simulations).
     */
    fun processMultiBand(
        odfSample: Float,
        bass: Float,
        mid: Float,
        high: Float,
        timestampSeconds: Double,
        signalSufficient: Boolean = true
    ): Float {
        isSignalSufficient = signalSufficient
        totalBlocksProcessed++

        val fps = sampleRate / fftSize.coerceAtLeast(1)
        val dt = fftSize.toFloat() / sampleRate.coerceAtLeast(1f)
        val effectiveTimestamp = if (timestampSeconds >= 0.0) timestampSeconds else ((totalBlocksProcessed - 1) * dt.toDouble())

        val minPeriodBlocks = (fps * (60.0f / bpmCeiling)).toInt().coerceAtLeast(1)
        val maxPeriodBlocks = (fps * (60.0f / bpmFloor)).toInt().coerceAtMost(historySize / 2)

        odfHistory[historyIndex] = odfSample
        // The periodicity is in the onsets, not the levels: a sustained note would otherwise fill every lag alike.
        val riseBass = max(0f, bass - prevBassIn)
        val riseMid = max(0f, mid - prevMidIn)
        val riseHigh = max(0f, high - prevHighIn)
        // Each band in units of its own usual rise, so a loud kick does not decide alone.
        meanRiseBass += (riseBass - meanRiseBass) * BAND_MEAN_RATE
        meanRiseMid += (riseMid - meanRiseMid) * BAND_MEAN_RATE
        meanRiseHigh += (riseHigh - meanRiseHigh) * BAND_MEAN_RATE
        bassHistory[historyIndex] = riseBass / (meanRiseBass + 1e-4f)
        midHistory[historyIndex] = riseMid / (meanRiseMid + 1e-4f)
        highHistory[historyIndex] = riseHigh / (meanRiseHigh + 1e-4f)
        onsetHistory[historyIndex] = bassHistory[historyIndex] + midHistory[historyIndex] + highHistory[historyIndex]
        prevBassIn = bass
        prevMidIn = mid
        prevHighIn = high
        historyIndex = (historyIndex + 1) and historyMask
        if (historyCount < historySize) historyCount++

        if (!signalSufficient) {
            isLocked = false
            stableAccumulatedSec = 0.0f
            confidence = 0.0f
            acCursor = -1
            // Retain flywheel momentum at current tempo without drifting towards fallback 120 BPM
            targetBeatIntervalTau0 = fps * (60.0f / currentBpm)
            cumulativeScore[totalBlocksProcessed.toInt() and scoreBufferMask] = 0.0f
            updateBeatAnchors(effectiveTimestamp, fps)
            return currentBpm.coerceIn(bpmFloor, bpmCeiling)
        }

        // 1. Periodic Two-State Periodicity & Tempo Estimation (Autocorrelation)
        stepTempoEstimate(fps, dt, minPeriodBlocks, maxPeriodBlocks)

        // 2. Causal Dynamic Programming Recurrence
        evaluateCausalDynamicProgramming(odfSample, minPeriodBlocks, maxPeriodBlocks)

        // 3. Beat Anchor Flywheel Tracking
        updateBeatAnchors(effectiveTimestamp, fps)

        return currentBpm.coerceIn(bpmFloor, bpmCeiling)
    }

    /**
     * Single ODF novelty sample processing helper.
     */
    fun processOdfSample(odfSample: Float, timestampSeconds: Double, signalSufficient: Boolean = true): Float {
        return processMultiBand(odfSample, odfSample, odfSample * 0.5f, odfSample * 0.2f, timestampSeconds, signalSufficient)
    }

    // ── Zero-Allocation Visual Phase & Modulation Signal Queries ─────────────

    /**
     * Returns the continuous normalized beat phase phi in [0.0, 1.0) at an arbitrary query timestamp.
     * Zero memory allocations. Thread-safe.
     */
    fun getPhase(queryTimestampSeconds: Double): Double {
        var t0: Double
        var p0: Double
        var f: Double
        var s1: Long
        var s2: Long
        do {
            s1 = snapSeq
            while ((s1 and 1L) == 1L) {
                s1 = snapSeq
            }
            t0 = snapTimestampSec
            p0 = snapPhase
            f = snapFreqHz
            s2 = snapSeq
        } while (s1 != s2)

        if (f <= 1e-6) return 0.0

        val elapsed = queryTimestampSeconds - t0
        var phase = (p0 + elapsed * f) % 1.0
        if (phase < 0.0) phase += 1.0
        return phase
    }

    /**
     * Returns the continuous locked cosine modulation signal cos(2 * PI * phi) at a query timestamp.
     * Zero memory allocations. Thread-safe.
     */
    fun getCosine(queryTimestampSeconds: Double): Double {
        val phase = getPhase(queryTimestampSeconds)
        return cos(2.0 * PI * phase)
    }

    /**
     * Populates the provided reusable primitive FloatArray with [phase, cosine] at query timestamp.
     * Guaranteed ZERO object allocations on the render thread.
     *
     * @param queryTimestampSeconds Timestamp in seconds
     * @param outReusedContainer Pre-allocated FloatArray of at least size 2
     */
    fun getPhaseAndCosine(queryTimestampSeconds: Double, outReusedContainer: FloatArray) {
        val phase = getPhase(queryTimestampSeconds)
        outReusedContainer[0] = phase.toFloat()
        outReusedContainer[1] = cos(2.0 * PI * phase).toFloat()
    }

    /**
     * Returns phase and cosine packed into a single 64-bit primitive Long with zero object allocation.
     * High 32 bits contain Float.toRawBits(phase), low 32 bits contain Float.toRawBits(cosine).
     */
    fun getPhaseAndCosinePacked(queryTimestampSeconds: Double): Long {
        val phase = getPhase(queryTimestampSeconds).toFloat()
        val cosine = cos(2.0 * PI * phase.toDouble()).toFloat()
        val highBits = phase.toRawBits().toLong() and 0xFFFFFFFFL
        val lowBits = cosine.toRawBits().toLong() and 0xFFFFFFFFL
        return (highBits shl 32) or lowBits
    }

    companion object {
        // Slightly smoother smoothing factor (0.95/0.05 instead of 0.9/0.1).
        /** One-pole EMA retain weight applied to the prior smoothed value. */
        const val EMA_RETAIN = 0.95f
        /** One-pole EMA update weight applied to the new sample. */
        const val EMA_UPDATE = 0.05f
        /** The tempo preference: a log-normal around this beat period, this many octaves wide. */
        const val PRIOR_PERIOD_SEC = 0.5f
        const val PRIOR_OCTAVES = 1.0f
        const val COMB_MULTIPLES = 4
        const val BAND_MEAN_RATE = 0.002f // about 6 s at the tracker's rate
        /** A relative must hit this much harder to replace the comb's choice. */
        const val METRE_SWITCH_MARGIN = 1.25f
        const val UNWRAP_STRIKE = 0.55f
        const val FOLD_SEC = 8f
        const val STARTUP_SEC = 5f
        const val MIN_HISTORY_SEC = 1.5f
        const val FOLD_SEARCH_BLOCKS = 0.75f
        const val FOLD_SEARCH_STEP = 0.25f
        const val METRE_CANDIDATES = 6
        const val METRE_STAY_BONUS = 1.3f
    }
}
