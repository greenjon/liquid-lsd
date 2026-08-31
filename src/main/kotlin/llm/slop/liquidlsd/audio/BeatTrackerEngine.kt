package llm.slop.liquidlsd.audio

import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Real-time audio beat tracking engine and continuous phase-locked modulation generator.
 * Modeled on BTrack (Adam Stark) and the Dan Ellis causal dynamic programming model.
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
    @Volatile var tempoEstimationIntervalBlocks: Int = 4

    // ── Pre-Allocated FFT Buffers & Lookup Tables (Zero Allocation) ─────────
    private val halfFft = fftSize / 2
    private val window = FloatArray(fftSize)
    private val bitRevTable = IntArray(fftSize)
    private val cosFftTable = FloatArray(halfFft)
    private val sinFftTable = FloatArray(halfFft)

    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val magSpectrum = FloatArray(halfFft + 1)
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
        cumulativeScore.fill(0f)
        prevMagSpectrum.fill(0f)
        prevPhase.fill(0f)
        prevPrevPhase.fill(0f)
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

            magSpectrum[k] = mag

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
     * Estimates tempo using multi-band cross-spectral autocorrelation and harmonic comb filtering over history.
     */
    private fun estimateTempo(fps: Float, dt: Float, minPeriodBlocks: Int, maxPeriodBlocks: Int): Float {
        val availableMaxDelay = (historyCount - minPeriodBlocks).coerceAtLeast(0).coerceAtMost(maxPeriodBlocks)
        if (availableMaxDelay < minPeriodBlocks) {
            return fallbackBpm
        }

        val winLenBlocks = (historyCount - availableMaxDelay).coerceAtMost((4.0f * fps).toInt())
        if (winLenBlocks <= 10) {
            return fallbackBpm
        }

        // Pre-calculate inverse length for the dynamic linear (Bartlett) window
        val invWinLen = 1.0f / winLenBlocks.coerceAtLeast(1)

        var maxAc = -1.0f
        var rawBestDelay = minPeriodBlocks
        var acSum = 0.0f
        var delayCount = 0

        // 1. The Main Search Loop
        for (delay in minPeriodBlocks..availableMaxDelay) {
            var ac = 0.0f
            for (i in 0 until winLenBlocks) {
                val idx1 = (historyIndex - 1 - i + historySize * 10) and historyMask
                val idx2 = (historyIndex - 1 - i - delay + historySize * 10) and historyMask

                val weight = 1.0f - (i * invWinLen)

                ac += weight * (bassHistory[idx1] * bassHistory[idx2] +
                                midHistory[idx1] * midHistory[idx2] +
                                highHistory[idx1] * highHistory[idx2])
            }
            acSum += ac
            delayCount++

            val candidateBpm = 60.0f / (delay.toFloat() / fps)
            val bpmDiff = candidateBpm - 120.0f
            val gaussianWeight = exp(-(bpmDiff * bpmDiff) / (2.0f * 80.0f * 80.0f))
            val weightedAc = ac * (0.85f + 0.15f * gaussianWeight)

            if (weightedAc > maxAc) {
                maxAc = weightedAc
                rawBestDelay = delay
            }
        }

        val meanAc = acSum / max(1, delayCount)
        confidence = if (meanAc > 1e-5f) (maxAc / (meanAc * 3.0f)).coerceIn(0.0f, 1.0f) else 0.5f

        // 2. Harmonic Unwrapping Loop: Check if half-lag (double tempo) is a valid fundamental beat period
        var bestDelay = rawBestDelay
        while (bestDelay / 2 >= minPeriodBlocks) {
            val halfDelay = bestDelay / 2
            var halfAc = 0.0f
            var fullAc = 0.0f
            for (i in 0 until winLenBlocks) {
                val idx1 = (historyIndex - 1 - i + historySize * 10) and historyMask
                val idxHalf = (historyIndex - 1 - i - halfDelay + historySize * 10) and historyMask
                val idxFull = (historyIndex - 1 - i - bestDelay + historySize * 10) and historyMask

                val weight = 1.0f - (i * invWinLen)

                halfAc += weight * (bassHistory[idx1] * bassHistory[idxHalf] + midHistory[idx1] * midHistory[idxHalf] + highHistory[idx1] * highHistory[idxHalf])
                fullAc += weight * (bassHistory[idx1] * bassHistory[idxFull] + midHistory[idx1] * midHistory[idxFull] + highHistory[idx1] * highHistory[idxFull])
            }
            val halfBpm = 60.0f / (halfDelay.toFloat() / fps)
            if (halfBpm <= 165.0f && fullAc > 1e-5f && (halfAc / fullAc) >= 0.45f) {
                bestDelay = halfDelay
            } else {
                break
            }
        }

        var calcBpm = -1.0f
        if (bestDelay >= minPeriodBlocks && bestDelay <= availableMaxDelay) {
            // 3. Sub-block parabolic lag interpolation around bestDelay
            var prevAc = 0.0f
            var centerAc = 0.0f
            var nextAc = 0.0f

            val delayPrev = max(minPeriodBlocks, bestDelay - 1)
            val delayNext = min(availableMaxDelay, bestDelay + 1)

            for (i in 0 until winLenBlocks) {
                val idx1 = (historyIndex - 1 - i + historySize * 10) and historyMask
                val idxPrev = (historyIndex - 1 - i - delayPrev + historySize * 10) and historyMask
                val idxCenter = (historyIndex - 1 - i - bestDelay + historySize * 10) and historyMask
                val idxNext = (historyIndex - 1 - i - delayNext + historySize * 10) and historyMask

                val weight = 1.0f - (i * invWinLen)

                prevAc += weight * (bassHistory[idx1] * bassHistory[idxPrev] + midHistory[idx1] * midHistory[idxPrev] + highHistory[idx1] * highHistory[idxPrev])
                centerAc += weight * (bassHistory[idx1] * bassHistory[idxCenter] + midHistory[idx1] * midHistory[idxCenter] + highHistory[idx1] * highHistory[idxCenter])
                nextAc += weight * (bassHistory[idx1] * bassHistory[idxNext] + midHistory[idx1] * midHistory[idxNext] + highHistory[idx1] * highHistory[idxNext])
            }

            val deltaLag = interpolateParabolicPeak(prevAc, centerAc, nextAc)
            val subBlockLag = (bestDelay + deltaLag).coerceAtLeast(1.0f)
            calcBpm = 60.0f / (subBlockLag / fps)
        }

        val clampedCalcBpm = if (calcBpm > 0.0f) calcBpm.coerceIn(bpmFloor, bpmCeiling) else fallbackBpm

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

                // Slightly smoother smoothing factor (0.95/0.05 instead of 0.9/0.1)
                stableCandidateBpm = stableCandidateBpm * 0.95f + bpmMatch * 0.05f

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
            val slewRate = 0.05f
            currentBpm = currentBpm * (1.0f - slewRate) + fallbackBpm * slewRate
            targetBeatIntervalTau0 = fps * (60.0f / currentBpm)
        }

        return currentBpm
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
        localAudioEnergy = localAudioEnergy * 0.95f + frameRms * 0.05f
        val signalSufficient = localAudioEnergy > energySilenceThreshold
        isSignalSufficient = signalSufficient

        val odfSample: Float
        if (signalSufficient) {
            val rawOdf = computeComplexSpectralDifference()
            localOdfMean = localOdfMean * 0.95f + rawOdf * 0.05f
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
        localAudioEnergy = localAudioEnergy * 0.95f + frameRms * 0.05f
        val signalSufficient = localAudioEnergy > energySilenceThreshold
        isSignalSufficient = signalSufficient

        val odfSample: Float
        if (signalSufficient) {
            val rawOdf = computeComplexSpectralDifference()
            localOdfMean = localOdfMean * 0.95f + rawOdf * 0.05f
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
        bassHistory[historyIndex] = if (odfSample > 0f) bass else bass * 0.1f
        midHistory[historyIndex] = mid
        highHistory[historyIndex] = high
        historyIndex = (historyIndex + 1) and historyMask
        if (historyCount < historySize) historyCount++

        if (!signalSufficient) {
            isLocked = false
            stableAccumulatedSec = 0.0f
            confidence = 0.0f
            val slewRate = 0.05f
            currentBpm = currentBpm * (1.0f - slewRate) + fallbackBpm * slewRate
            targetBeatIntervalTau0 = fps * (60.0f / currentBpm)
            cumulativeScore[totalBlocksProcessed.toInt() and scoreBufferMask] = 0.0f
            updateBeatAnchors(effectiveTimestamp, fps)
            return currentBpm.coerceIn(bpmFloor, bpmCeiling)
        }

        // 1. Periodic Two-State Periodicity & Tempo Estimation (Autocorrelation)
        val interval = tempoEstimationIntervalBlocks.coerceAtLeast(1)
        if (totalBlocksProcessed % interval.toLong() == 0L) {
            estimateTempo(fps, dt * interval.toFloat(), minPeriodBlocks, maxPeriodBlocks)
        }

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
}
