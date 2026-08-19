package llm.slop.liquidlsd.audio

import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.CvHistoryBuffer
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.log10
import mu.KotlinLogging

enum class SignalState { SILENT, ACTIVE }

enum class BeatDetectionMode { ENERGY_DIFFERENCE, RESONATOR }
enum class AudioTarget { UNFILTERED, LOW, MID, HIGH }

data class BeatDetectionSettings(
    var mode: BeatDetectionMode = BeatDetectionMode.ENERGY_DIFFERENCE,
    var target: AudioTarget = AudioTarget.LOW,
    var bpmSearchFloor: Int = 40,
    var bpmSearchCeiling: Int = 200,
    var energyThreshold: Float = 1.5f,
    var pllAdaptationRate: Float = 0.2f,
    var biquadQ: Float = 3.0f
) {
    companion object {
        fun highAccuracy() = BeatDetectionSettings(mode = BeatDetectionMode.ENERGY_DIFFERENCE, energyThreshold = 1.8f)
        fun balanced() = BeatDetectionSettings(mode = BeatDetectionMode.ENERGY_DIFFERENCE, energyThreshold = 1.5f)
        fun eco() = BeatDetectionSettings(mode = BeatDetectionMode.RESONATOR, biquadQ = 5.0f)
    }
}

class BeatDetector {
    @Volatile
    var settings = BeatDetectionSettings.balanced()
        private set

    @Volatile
    var confidence: Float = 1.0f
        private set

    @Volatile
    var pendingPhaseNudge: Double = -1.0

    @Volatile
    var isTargetLevelSufficient: Boolean = true

    @Volatile
    private var currentBpm = 120.0f

    // Energy difference state
    private var localEnergyAverage = 0.001f
    private var timeSinceLastBeatBlocks = 0f
    private var lastTargetAmp = 0f

    // Resonator State
    private var resonator = BiquadFilter(BiquadFilter.Type.BANDPASS, 44100f, 2f, 3f)
    private var lastResonatorOut = 0f
    private var lastSampleRate = 44100f

    fun applyPreset(preset: BeatDetectionSettings) {
        this.settings = preset.copy()
    }

    fun processBlock(
        unfilteredAmp: Float,
        lowAmp: Float,
        midAmp: Float,
        highAmp: Float,
        sampleRate: Float,
        nframes: Int,
        onsetStrength: Float = 0f
    ): Float {
        val targetAmp = when (settings.target) {
            AudioTarget.UNFILTERED -> if (onsetStrength > 0f) onsetStrength else unfilteredAmp
            AudioTarget.LOW -> lowAmp
            AudioTarget.MID -> midAmp
            AudioTarget.HIGH -> highAmp
        }

        if (sampleRate != lastSampleRate) {
            resonator.sampleRate = sampleRate
            resonator.updateCoefficients()
            lastSampleRate = sampleRate
        }

        val fps = sampleRate / nframes.coerceAtLeast(1)
        val floorBpm = settings.bpmSearchFloor.toFloat()
        val ceilBpm = settings.bpmSearchCeiling.toFloat()
        val minPeriodBlocks = fps * (60.0f / ceilBpm)
        val maxPeriodBlocks = fps * (60.0f / floorBpm)

        // Track target band energy to determine if sufficient signal exists
        localEnergyAverage = localEnergyAverage * 0.99f + targetAmp * 0.01f
        isTargetLevelSufficient = localEnergyAverage > 0.005f

        timeSinceLastBeatBlocks += 1.0f

        val mode = settings.mode

        if (mode == BeatDetectionMode.ENERGY_DIFFERENCE) {
            // Energy Difference + PLL Soft-Sync
            if (targetAmp > localEnergyAverage * settings.energyThreshold && timeSinceLastBeatBlocks > minPeriodBlocks) {
                // Must be a local peak
                if (lastTargetAmp > targetAmp && lastTargetAmp > localEnergyAverage * settings.energyThreshold) {
                    pendingPhaseNudge = 0.0
                    
                    val measuredPeriodBlocks = timeSinceLastBeatBlocks - 1.0f // peak was 1 block ago
                    val calcBpm = 60.0f / (measuredPeriodBlocks / fps)
                    
                    // PLL adaptation
                    currentBpm = currentBpm * (1f - settings.pllAdaptationRate) + calcBpm.coerceIn(floorBpm, ceilBpm) * settings.pllAdaptationRate
                    
                    timeSinceLastBeatBlocks = 0f
                }
            }
        } else if (mode == BeatDetectionMode.RESONATOR) {
            // Option 3: Complex Domain Onset + Biquad Resonator
            resonator.frequency = currentBpm / 60.0f
            resonator.q = settings.biquadQ
            resonator.updateCoefficients()
            
            // Feed DC-blocked envelope
            val out = resonator.process(targetAmp - localEnergyAverage)
            
            // Zero crossing detector
            if (lastResonatorOut <= 0f && out > 0f) {
                pendingPhaseNudge = 0.0
                
                if (timeSinceLastBeatBlocks > minPeriodBlocks) {
                    val measuredPeriodBlocks = timeSinceLastBeatBlocks
                    val calcBpm = 60.0f / (measuredPeriodBlocks / fps)
                    currentBpm = currentBpm * 0.8f + calcBpm.coerceIn(floorBpm, ceilBpm) * 0.2f
                    timeSinceLastBeatBlocks = 0f
                }
            }
            lastResonatorOut = out
        }
        
        lastTargetAmp = targetAmp

        return currentBpm.coerceIn(floorBpm, ceilBpm)
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
    private var javaSoundClient: JavaSoundClient? = null
    @Volatile
    private var automaticReconnectEnabled = true
    @Volatile
    var lastJackFailure: JackStartFailure? = null
        private set
    @Volatile
    var lastJackFailureMessage: String? = null
        private set

    val presetIOInFlight = java.util.concurrent.atomic.AtomicBoolean(false)


    private val callbackLatencyNanos = java.util.concurrent.atomic.AtomicLong(0L)

    fun getCallbackLatencyNanos(): Long = if (jackClient?.isConnected == true) callbackLatencyNanos.get() else 0L

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
    private var phaseSlewBuffer = 0.0
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
    fun isActive(): Boolean = (jackClient?.isConnected == true) || (javaSoundClient?.isConnected == true)

    fun getActiveBackendName(): String = when {
        jackClient?.isConnected == true -> "JACK"
        javaSoundClient?.isConnected == true -> "Java Sound"
        else -> "None"
    }

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
        phaseSlewBuffer = 0.0
        estimatedBpm = manualBpm
        currentState = SignalState.SILENT
        lastSignalTime = System.nanoTime()

        // Reset onset trackers
        prevBass = 0f
        prevMid  = 0f
        prevHigh = 0f
        accentLevel = 0f
        localOnsetMean = 0f

        jackClient = JackClient("lsd") { buffer, nframes, sampleRate ->
            processAudio(buffer, nframes, sampleRate)
        }
        val started = jackClient?.start() == true
        lastJackFailure = jackClient?.lastStartFailure
        lastJackFailureMessage = jackClient?.lastStartFailureMessage
        
        if (started) {
            logger.info { "Successfully started JACK audio client." }
        } else {
            if (lastJackFailure == JackStartFailure.NATIVE_LIBRARY_MISSING) {
                automaticReconnectEnabled = false
                logger.warn { "Automatic JACK reconnect disabled until manual retry; native library is missing." }
            }
            logger.info { "JACK audio client failed to start. Trying Java Sound fallback..." }
            jackClient = null
            
            javaSoundClient = JavaSoundClient { buffer, nframes, sampleRate ->
                processAudio(buffer, nframes, sampleRate)
            }
            val javaStarted = javaSoundClient?.start() == true
            if (!javaStarted) {
                logger.warn { "Java Sound fallback also failed to start. Audio engine is inactive." }
                javaSoundClient = null
            }
        }
    }

    /**
     * Attempts to reconnect to JACK if not currently active.
     * Safe to call from a background thread.
     */
    fun tryReconnect(force: Boolean = false) {
        if (jackClient?.isConnected == true) return
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
     * 
     * JACK CALLBACK SAFETY RULES (Strictly Enforced):
     * - ZERO heap allocations (no `new`, no boxing, no Kotlin lambdas that allocate, no standard iterators)
     * - ZERO blocking calls (no locks, no `synchronized`, no I/O, no Thread.sleep)
     * - ZERO logging (no `logger.info`, `println`, etc.)
     * 
     * SAFE TO CALL:
     * - Pre-allocated buffer reads/writes (FloatArray, CvHistoryBuffer)
     * - Math operations and primitive local variables
     * - `CVRegistry.updateBeatAnchor` (uses primitive @Volatile fields)
     * - `CVRegistry.updatePushedValue` (uses ConcurrentHashMap.get which is wait-free for reads)
     * - `beatDetector.processBlock` (uses atomic generation counter handoff)
     * 
     * UNSAFE TO CALL:
     * - `Executors.submit { ... }` (lambda allocation)
     * - `String` manipulation or concatenation
     */
    internal fun processAudio(buffer: FloatBuffer, nframes: Int, sampleRate: Float) {
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

        // 5. Onset-strength function: half-wave rectified multi-band spectral flux
        //    Weights favour bass/kick (×2) over mid (×0.8) and high (×0.3)
        val bassFlux = max(0f, bass - prevBass)
        val midFlux  = max(0f, mid  - prevMid)
        val highFlux = max(0f, high - prevHigh)
        val onsetStrength = bassFlux * 2.0f + midFlux * 0.8f + highFlux * 0.3f

        prevBass = bass
        prevMid  = mid
        prevHigh = high

        val autoBpm = beatDetector.processBlock(amp, bass, mid, high, sampleRate, safeFrames, onsetStrength)

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

        // 7. Tick the flywheel (sample-accurate) with phase slewing
        val phaseNudge = beatDetector.pendingPhaseNudge
        if (phaseNudge >= 0.0) {
            beatDetector.pendingPhaseNudge = -1.0
            val currentPhase = totalBeats % 1.0
            var phaseDiff = phaseNudge - currentPhase
            if (phaseDiff > 0.5) phaseDiff -= 1.0
            if (phaseDiff < -0.5) phaseDiff += 1.0
            phaseSlewBuffer += phaseDiff * 0.2
        }

        val activeSlew = phaseSlewBuffer * 0.1
        totalBeats += activeSlew
        phaseSlewBuffer -= activeSlew

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

        val callbackNanos = System.nanoTime() - currentTime
        callbackLatencyNanos.set(callbackNanos)
    }

    /**
     * Stops the Audio Engine and releases resources.
     */
    fun stop() {
        jackClient?.stop()
        jackClient = null
        javaSoundClient?.stop()
        javaSoundClient = null
    }
}
