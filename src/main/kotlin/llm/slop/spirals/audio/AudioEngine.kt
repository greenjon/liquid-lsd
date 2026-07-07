package llm.slop.spirals.audio

import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.cv.CvHistoryBuffer
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.log10
import mu.KotlinLogging

enum class SignalState { SILENT, ACTIVE }

enum class BeatDetectionMode { STFT_COMB, AUTOCORRELATION, PLL }
enum class AudioTarget { UNFILTERED, LOW, MID, HIGH }

data class BeatDetectionSettings(
    var mode: BeatDetectionMode = BeatDetectionMode.AUTOCORRELATION,
    var target: AudioTarget = AudioTarget.LOW,
    var windowSize: Int = 2048,
    var hopSize: Int = 512,
    var bpmSearchFloor: Int = 40,
    var bpmSearchCeiling: Int = 140,
    var bpmGridResolution: Float = 1.0f,
    var analysisWindowLength: Float = 3.0f,
    var pllAdaptationRate: Float = 0.1f
) {
    companion object {
        fun highAccuracy() = BeatDetectionSettings(
            mode = BeatDetectionMode.STFT_COMB,
            target = AudioTarget.UNFILTERED,
            windowSize = 4096,
            hopSize = 256,
            bpmSearchFloor = 40,
            bpmSearchCeiling = 240,
            bpmGridResolution = 0.1f,
            analysisWindowLength = 5.0f,
            pllAdaptationRate = 0.1f
        )
        fun balanced() = BeatDetectionSettings(
            mode = BeatDetectionMode.AUTOCORRELATION,
            target = AudioTarget.LOW,
            windowSize = 2048,
            hopSize = 512,
            bpmSearchFloor = 40,
            bpmSearchCeiling = 240,
            bpmGridResolution = 1.0f,
            analysisWindowLength = 3.0f,
            pllAdaptationRate = 0.1f
        )
        fun eco() = BeatDetectionSettings(
            mode = BeatDetectionMode.PLL,
            target = AudioTarget.LOW,
            windowSize = 1024,
            hopSize = 512,
            bpmSearchFloor = 40,
            bpmSearchCeiling = 240,
            bpmGridResolution = 2.0f,
            analysisWindowLength = 1.5f,
            pllAdaptationRate = 0.1f
        )
    }
}

class BeatDetector {
    @Volatile
    var settings = BeatDetectionSettings.balanced()
        private set
    
    private class AnalysisSnapshot {
        var fps: Float = 0f
        var bgHistoryIndex: Int = 0
        var bgHistoryCount: Int = 0
    }

    private val snapshot1 = AnalysisSnapshot()
    private val snapshot2 = AnalysisSnapshot()
    @Volatile
    private var pendingSnapshot = snapshot1
    
    // Pre-allocated circular buffer to store envelopes without allocating memory in real-time thread.
    // 8192 blocks of history is enough for ~8s of audio at normal JACK buffer sizes.
    private val maxEnvelopeBlocks = 8192
    private val historyBuffer = FloatArray(maxEnvelopeBlocks)
    private var historyIndex = 0
    private var historyCount = 0
    
    // PLL State
    private var pllPhase = 0.0f
    private var pllPeriod = 0.0f 
    @Volatile
    private var currentBpm = 120.0f

    private var blocksSinceLastAnalysis = 0
    @Volatile
    private var isCalculating = false
    private val bgHistoryBuffer = FloatArray(maxEnvelopeBlocks)
    private val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "BeatDetector-Analysis").apply { isDaemon = true }
    }
    private val analysisTask = AnalysisTask()
    
    fun applyPreset(preset: BeatDetectionSettings) {
        this.settings = preset.copy()
    }
    
    fun processBlock(
        unfilteredAmp: Float,
        lowAmp: Float,
        midAmp: Float,
        highAmp: Float,
        sampleRate: Float,
        nframes: Int
    ): Float {
        // High-performance section: No allocations, no blocking calls!
        val targetAmp = when (settings.target) {
            AudioTarget.UNFILTERED -> unfilteredAmp
            AudioTarget.LOW -> lowAmp
            AudioTarget.MID -> midAmp
            AudioTarget.HIGH -> highAmp
        }
        
        // Push current target block amplitude into history
        historyBuffer[historyIndex] = targetAmp
        historyIndex = (historyIndex + 1) % maxEnvelopeBlocks
        if (historyCount < maxEnvelopeBlocks) {
            historyCount++
        }
        
        val fps = sampleRate / nframes.coerceAtLeast(1)
        val floorBpm = settings.bpmSearchFloor.toFloat()
        val ceilBpm = settings.bpmSearchCeiling.toFloat()
        val mode = settings.mode
        
        if (mode == BeatDetectionMode.PLL) {
            val expectedPeriod = fps * (60.0f / currentBpm)
            if (pllPeriod == 0.0f) pllPeriod = expectedPeriod
            
            pllPhase += 1.0f
            if (pllPhase >= pllPeriod) {
                pllPhase -= pllPeriod
            }
            
            val prevTarget = historyBuffer[(historyIndex - 2 + maxEnvelopeBlocks) % maxEnvelopeBlocks]
            if (targetAmp > prevTarget && targetAmp > 0.05f) { 
                var error = pllPhase / pllPeriod
                if (error > 0.5f) error -= 1.0f
                
                pllPhase -= error * pllPeriod * settings.pllAdaptationRate
                pllPeriod -= error * pllPeriod * (settings.pllAdaptationRate * 0.1f)
                
                val minPeriod = fps * (60.0f / ceilBpm)
                val maxPeriod = fps * (60.0f / floorBpm)
                pllPeriod = pllPeriod.coerceIn(minPeriod, maxPeriod)
            }
            
            currentBpm = 60.0f / (pllPeriod / fps)
        } else {
            blocksSinceLastAnalysis++
            if (blocksSinceLastAnalysis >= 16 && !isCalculating) {
                blocksSinceLastAnalysis = 0
                isCalculating = true
                System.arraycopy(historyBuffer, 0, bgHistoryBuffer, 0, maxEnvelopeBlocks)
                val nextSnapshot = if (pendingSnapshot === snapshot1) snapshot2 else snapshot1
                nextSnapshot.fps = fps
                nextSnapshot.bgHistoryIndex = historyIndex
                nextSnapshot.bgHistoryCount = historyCount
                pendingSnapshot = nextSnapshot
                analysisExecutor.execute(analysisTask)
            }
        }
        
        return currentBpm.coerceIn(floorBpm, ceilBpm)
    }

    private inner class AnalysisTask : Runnable {
        override fun run() {
            try {
                val snap = pendingSnapshot
                val fps = snap.fps
                val bgHistoryIndex = snap.bgHistoryIndex
                val bgHistoryCount = snap.bgHistoryCount

                val localSettings = settings
                val mode = localSettings.mode
                val floorBpm = localSettings.bpmSearchFloor.toFloat()
                val ceilBpm = localSettings.bpmSearchCeiling.toFloat()
                val res = localSettings.bpmGridResolution
                val winLen = localSettings.analysisWindowLength

                var bestBpm = currentBpm
                if (mode == BeatDetectionMode.STFT_COMB) {
                    // Comb Filter Bank logic on envelope onset
                    var maxEnergy = 0.0f
                    var bpm = floorBpm
                    
                    while (bpm <= ceilBpm) {
                        val delayInBlocks = ((60.0f / bpm) * fps).toInt().coerceAtLeast(1)
                        var energy = 0.0f
                        val numPeriods = 4
                        
                        for (i in 0 until numPeriods) {
                            val idx = (bgHistoryIndex - 1 - i * delayInBlocks + maxEnvelopeBlocks * 10) % maxEnvelopeBlocks
                            energy += bgHistoryBuffer[idx]
                        }
                        
                        if (energy > maxEnergy) {
                            maxEnergy = energy
                            bestBpm = bpm
                        }
                        bpm += res
                    }
                    currentBpm = currentBpm * 0.9f + bestBpm * 0.1f
                } else if (mode == BeatDetectionMode.AUTOCORRELATION) {
                    var maxAc = 0.0f
                    var bestDelay = 0
                    
                    val maxDelayBlocks = ((60.0f / floorBpm) * fps).toInt().coerceAtMost(maxEnvelopeBlocks / 2)
                    val minDelayBlocks = ((60.0f / ceilBpm) * fps).toInt().coerceAtLeast(1)
                    
                    for (delay in minDelayBlocks..maxDelayBlocks) {
                        var ac = 0.0f
                        val N = (winLen * fps).toInt().coerceAtMost(maxEnvelopeBlocks - delay)
                        
                        for (i in 0 until N) {
                            val idx1 = (bgHistoryIndex - 1 - i + maxEnvelopeBlocks * 2) % maxEnvelopeBlocks
                            val idx2 = (bgHistoryIndex - 1 - i - delay + maxEnvelopeBlocks * 2) % maxEnvelopeBlocks
                            ac += bgHistoryBuffer[idx1] * bgHistoryBuffer[idx2]
                        }
                        
                        if (ac > maxAc) {
                            maxAc = ac
                            bestDelay = delay
                        }
                    }
                    
                    if (bestDelay > 0) {
                        val calcBpm = 60.0f / (bestDelay / fps)
                        currentBpm = currentBpm * 0.95f + calcBpm * 0.05f
                    }
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                isCalculating = false
            }
        }
    }
}

data class DetectionConfig(
    val silenceThresholdDb: Float = -40f,
    val silenceTimeoutMs: Long = 500_000_000L // 500ms in nanos
)

/**
 * Orchestrates the audio capture client and runs the real-time DSP analysis pipeline.
 * Separates audio into bands, computes onset-strength and accent envelopes,
 * and publishes them to CVRegistry.
 *
 * Keeps a sample-accurate beat flywheel that increments linearly based on manual BPM.
 */
object AudioEngine {
    private val logger = KotlinLogging.logger {}
    private var jackClient: JackClient? = null
    @Volatile
    private var automaticReconnectEnabled = true
    @Volatile
    var lastJackFailure: JackStartFailure? = null
        private set
    @Volatile
    var lastJackFailureMessage: String? = null
        private set

    // DSP filters
    private var lastSampleRate = 44100f
    private val lowPass  = BiquadFilter(BiquadFilter.Type.LOWPASS,  lastSampleRate, 150f)
    private val midPass  = BiquadFilter(BiquadFilter.Type.BANDPASS, lastSampleRate, 1000f)
    private val highPass = BiquadFilter(BiquadFilter.Type.HIGHPASS, lastSampleRate, 5000f)

    private val extractor = AmplitudeExtractor()
    val beatDetector = BeatDetector()

    // Pre-allocated buffer for oscilloscope rendering of raw input samples
    // KNOWN BENIGN DATA RACE: The index and buffer array in rawHistory are updated without
    // synchronization. Since this is strictly used for real-time oscilloscope visualization in the UI,
    // a minor data race is harmless and preferred over introducing locks or allocation.
    val rawHistory = CvHistoryBuffer(1024)

    // Temporary processing buffers — sized to standard maximum JACK limits to guarantee no allocations.
    private val lowBuffer  = FloatArray(16384)
    private val midBuffer  = FloatArray(16384)
    private val highBuffer = FloatArray(16384)

    // ── Flywheel state ──────────────────────────────────────────────────────
    private var totalSamplesProcessed = 0L
    private var totalBeats = 0.0
    @Volatile private var estimatedBpm = 120f
    @Volatile var inputGain = 1.0f

    // ── User controls ────────────────────────────────────────────────────────
    @Volatile var isBpmLocked = true // default to locked/manual now that real-time estimate is removed
    @Volatile var manualBpm = 120f

    // ── State machine ────────────────────────────────────────────────────────
    val config = DetectionConfig()
    @Volatile var currentState = SignalState.SILENT
    private var lastSignalTime = System.nanoTime()

    // ── Onset-strength tracking ──────────────────────────────────────────────
    private var prevBass = 0f
    private var prevMid  = 0f
    private var prevHigh = 0f
    private var accentLevel  = 0f
    private var localOnsetMean = 0f // fast adaptive mean for onset threshold

    fun getEstimatedBpm(): Float = estimatedBpm
    fun isActive(): Boolean = jackClient?.isConnected == true

    fun setBpmDirectly(bpm: Float) {
        estimatedBpm = bpm
        CVRegistry.updateBeatAnchor(totalBeats, bpm, System.nanoTime())
    }

    /**
     * Starts the Audio Engine and JACK client connection.
     */
    fun start() {
        automaticReconnectEnabled = true
        startClient()
    }

    private fun startClient() {
        // Reset flywheel
        totalSamplesProcessed = 0L
        totalBeats = 0.0
        estimatedBpm = manualBpm
        currentState = SignalState.SILENT
        lastSignalTime = System.nanoTime()

        // Reset onset trackers
        prevBass = 0f
        prevMid  = 0f
        prevHigh = 0f
        accentLevel = 0f
        localOnsetMean = 0f

        jackClient = JackClient("spirals-desktop") { buffer, nframes, sampleRate ->
            processAudio(buffer, nframes, sampleRate)
        }
        val started = jackClient?.start() == true
        lastJackFailure = jackClient?.lastStartFailure
        lastJackFailureMessage = jackClient?.lastStartFailureMessage
        if (!started && lastJackFailure == JackStartFailure.NATIVE_LIBRARY_MISSING) {
            automaticReconnectEnabled = false
            logger.warn { "Automatic JACK reconnect disabled until manual retry; native library is missing." }
        }
    }

    /**
     * Attempts to reconnect to JACK if not currently active.
     * Safe to call from a background thread.
     */
    fun tryReconnect(force: Boolean = false) {
        if (isActive()) return
        if (!force && !automaticReconnectEnabled) return
        if (force) {
            automaticReconnectEnabled = true
        }
        logger.info { "Watchdog attempting JACK reconnection..." }
        stop()
        startClient()
    }

    /**
     * Processes a new block of audio samples from JACK. Runs on the real-time audio thread.
     * ZERO ALLOCATIONS — all buffers are pre-allocated in [start] or at object init.
     */
    private fun processAudio(buffer: FloatBuffer, nframes: Int, sampleRate: Float) {
        val currentTime = System.nanoTime()

        // Ensure nframes doesn't exceed our pre-allocated buffers
        val safeFrames = nframes.coerceAtMost(lowBuffer.size)
        totalSamplesProcessed += safeFrames

        // 1. Dynamic sample rate adjustment (rare)
        if (sampleRate != lastSampleRate) {
            lowPass.sampleRate  = sampleRate; lowPass.updateCoefficients()
            midPass.sampleRate  = sampleRate; midPass.updateCoefficients()
            highPass.sampleRate = sampleRate; highPass.updateCoefficients()
            lastSampleRate = sampleRate
        }

        // 2. Buffer bounds safety check (removed allocation branch to enforce zero allocations)
        // safeFrames handles bounds safety.

        // 3. Filter bank + raw history
        val startPos = buffer.position()
        val gain = inputGain
        for (i in 0 until safeFrames) {
            val sample = buffer.get(startPos + i) * gain
            rawHistory.add(sample)
            lowBuffer[i]  = lowPass.process(sample)
            midBuffer[i]  = midPass.process(sample)
            highBuffer[i] = highPass.process(sample)
        }

        // 4. RMS amplitudes per band
        val amp  = extractor.calculateRms(buffer, safeFrames) * gain
        val bass = extractor.calculateRms(lowBuffer,  safeFrames)
        val mid  = extractor.calculateRms(midBuffer,  safeFrames)
        val high = extractor.calculateRms(highBuffer, safeFrames)

        val autoBpm = beatDetector.processBlock(amp, bass, mid, high, sampleRate, safeFrames)

        // 5. Onset-strength function: half-wave rectified multi-band spectral flux
        //    Weights favour bass/kick (×2) over mid (×0.8) and high (×0.3)
        val bassFlux = max(0f, bass - prevBass)
        val midFlux  = max(0f, mid  - prevMid)
        val highFlux = max(0f, high - prevHigh)
        val onsetStrength = bassFlux * 2.0f + midFlux * 0.8f + highFlux * 0.3f

        prevBass = bass
        prevMid  = mid
        prevHigh = high

        // Fast adaptive local mean (τ ≈ 20 callbacks ≈ ~0.5 s) for onset thresholding
        localOnsetMean = localOnsetMean * 0.95f + onsetStrength * 0.05f

        // Accent envelope (peak-hold + decay) — published as CV
        if (onsetStrength > accentLevel) {
            accentLevel = onsetStrength
        } else {
            accentLevel *= 0.88f
        }

        // Normalized onset for CV output (0–2 range)
        val onsetNormalized = (onsetStrength / 0.05f).coerceIn(0f, 2f)

        // 6. Silence gate
        val currentRmsDb = 20f * log10(amp + 1e-6f)
        if (currentRmsDb < config.silenceThresholdDb) {
            if (currentTime - lastSignalTime > config.silenceTimeoutMs) {
                currentState = SignalState.SILENT
            }
        } else {
            lastSignalTime = currentTime
            if (currentState == SignalState.SILENT) {
                currentState = SignalState.ACTIVE
            }
        }

        // 7. Tick the flywheel (sample-accurate)
        val deltaTimeSec = safeFrames.toDouble() / sampleRate.toDouble()
        if (currentState != SignalState.SILENT) {
            totalBeats += deltaTimeSec * (estimatedBpm / 60.0)
        }

        // 8. Manual BPM lock override
        estimatedBpm = if (isBpmLocked) manualBpm else autoBpm

        // 9. Publish to CV Registry
        CVRegistry.updateBeatAnchor(totalBeats, estimatedBpm, currentTime)
        CVRegistry.updatePushedValue("amp",    (amp  / 0.1f).coerceIn(0f, 2f))
        CVRegistry.updatePushedValue("bass",   (bass / 0.1f).coerceIn(0f, 2f))
        CVRegistry.updatePushedValue("mid",    (mid  / 0.1f).coerceIn(0f, 2f))
        CVRegistry.updatePushedValue("high",   (high / 0.1f).coerceIn(0f, 2f))
        CVRegistry.updatePushedValue("onset",  onsetNormalized)
        CVRegistry.updatePushedValue("accent", accentLevel)
    }

    /**
     * Stops the Audio Engine and releases resources.
     */
    fun stop() {
        jackClient?.stop()
        jackClient = null
    }
}
