package llm.slop.liquidlsd.audio

import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.CvHistoryBuffer
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.log10
import mu.KotlinLogging

enum class SignalState { SILENT, ACTIVE }

enum class AudioTarget { UNFILTERED, LOW, MID, HIGH }

data class BeatDetectionSettings(
    var target: AudioTarget = AudioTarget.LOW,
    var bpmSearchFloor: Int = 40,
    var bpmSearchCeiling: Int = 200,
    var transitionWeightAlpha: Float = 120.0f,
    var trackingInertiaBpmPerBeat: Float = 2.0f
) {
    companion object {
        fun highAccuracy() = BeatDetectionSettings(target = AudioTarget.LOW, bpmSearchFloor = 40, bpmSearchCeiling = 200, transitionWeightAlpha = 120.0f, trackingInertiaBpmPerBeat = 2.0f)
        fun balanced() = BeatDetectionSettings(target = AudioTarget.LOW, bpmSearchFloor = 40, bpmSearchCeiling = 200, transitionWeightAlpha = 100.0f, trackingInertiaBpmPerBeat = 2.5f)
        fun eco() = BeatDetectionSettings(target = AudioTarget.LOW, bpmSearchFloor = 40, bpmSearchCeiling = 200, transitionWeightAlpha = 80.0f, trackingInertiaBpmPerBeat = 3.0f)
    }
}

class BeatDetector(
    val engine: BeatTrackerEngine = BeatTrackerEngine()
) {
    @Volatile
    var settings = BeatDetectionSettings.highAccuracy()
        private set

    val confidence: Float get() = engine.confidence

    @Volatile
    var pendingPhaseNudge: Double = -1.0

    val isTargetLevelSufficient: Boolean get() = engine.isSignalSufficient

    val isTempoLocked: Boolean get() = engine.isLocked

    val currentBpm: Float get() = engine.currentBpm

    private var localEnergyAverage = 0.001f

    fun applyPreset(preset: BeatDetectionSettings) {
        this.settings = preset.copy()
        engine.bpmFloor = preset.bpmSearchFloor.toFloat()
        engine.bpmCeiling = preset.bpmSearchCeiling.toFloat()
        engine.transitionWeightAlpha = preset.transitionWeightAlpha
        engine.trackingInertiaBpmPerBeat = preset.trackingInertiaBpmPerBeat
    }

    fun reset() {
        pendingPhaseNudge = -1.0
        localEnergyAverage = 0.001f
        engine.reset()
    }

    fun interpolateParabolicPeak(y1: Float, y2: Float, y3: Float): Float {
        return engine.interpolateParabolicPeak(y1, y2, y3)
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
        if (engine.sampleRate != sampleRate) {
            engine.sampleRate = sampleRate
        }
        engine.bpmFloor = settings.bpmSearchFloor.toFloat()
        engine.bpmCeiling = settings.bpmSearchCeiling.toFloat()
        engine.transitionWeightAlpha = settings.transitionWeightAlpha
        engine.trackingInertiaBpmPerBeat = settings.trackingInertiaBpmPerBeat
        val targetAmp = when (settings.target) {
            AudioTarget.UNFILTERED -> if (onsetStrength > 0f) onsetStrength else unfilteredAmp
            AudioTarget.LOW -> if (onsetStrength > 0f) onsetStrength else lowAmp
            AudioTarget.MID -> midAmp
            AudioTarget.HIGH -> highAmp
        }

        localEnergyAverage = localEnergyAverage * 0.985f + max(targetAmp, unfilteredAmp) * 0.015f
        val signalSufficient = localEnergyAverage > 0.003f
        val odfSample = if (onsetStrength > 0f) onsetStrength else targetAmp
        val timestampSec = -1.0 // Sentinel to let engine compute from (totalBlocksProcessed - 1) * dt

        return engine.processMultiBand(odfSample, lowAmp, midAmp, highAmp, timestampSec, signalSufficient)
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
    val beatTracker: BeatTrackerEngine get() = beatDetector.engine

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

    enum class AudioBackendMode { AUTO, JACK_ONLY, JAVASOUND_ONLY }

    @Volatile
    var backendMode: AudioBackendMode = AudioBackendMode.AUTO

    @Volatile
    var selectedDeviceName: String? = null

    @Volatile
    private var cachedInputDevices: List<AudioInputDevice> = emptyList()
    @Volatile
    private var cachedDeviceNames: Array<String> = emptyArray()

    fun getAvailableInputDevices(forceRefresh: Boolean = false): List<AudioInputDevice> {
        if (forceRefresh || cachedInputDevices.isEmpty()) {
            val devices = JavaSoundClient.getAvailableInputDevices()
            cachedInputDevices = devices
            cachedDeviceNames = devices.map { it.name }.toTypedArray()
        }
        return cachedInputDevices
    }

    fun getAvailableDeviceNames(): Array<String> {
        if (cachedDeviceNames.isEmpty()) {
            getAvailableInputDevices(forceRefresh = true)
        }
        return cachedDeviceNames
    }

    fun refreshInputDevices() {
        getAvailableInputDevices(forceRefresh = true)
    }

    fun selectDevice(deviceName: String?, backend: AudioBackendMode = backendMode) {
        selectedDeviceName = deviceName
        backendMode = backend
        stop()
        startClient()
    }

    private fun startClient() {
        // Reset flywheel
        totalSamplesProcessed = 0L
        totalBeats = 0.0
        phaseSlewBuffer = 0.0
        estimatedBpm = if (isBpmLocked) manualBpm else 120.0f
        currentState = SignalState.SILENT
        lastSignalTime = System.nanoTime()

        // Reset beat detector (locks to 120 BPM until new tempo is locked)
        beatDetector.reset()

        // Reset CV anchor
        CVRegistry.resetBeatAnchor(0.0, estimatedBpm, lastSignalTime)

        // Reset onset trackers
        prevBass = 0f
        prevMid  = 0f
        prevHigh = 0f
        accentLevel = 0f
        localOnsetMean = 0f

        var jackStarted = false
        if (backendMode != AudioBackendMode.JAVASOUND_ONLY) {
            jackClient = JackClient("lsd") { buffer, nframes, sampleRate ->
                processAudio(buffer, nframes, sampleRate)
            }
            jackStarted = jackClient?.start() == true
            lastJackFailure = jackClient?.lastStartFailure
            lastJackFailureMessage = jackClient?.lastStartFailureMessage

            if (jackStarted) {
                logger.info { "Successfully started JACK audio client." }
            } else {
                if (lastJackFailure == JackStartFailure.NATIVE_LIBRARY_MISSING) {
                    automaticReconnectEnabled = false
                    logger.warn { "Automatic JACK reconnect disabled until manual retry; native library is missing." }
                }
                jackClient = null
            }
        }

        if (!jackStarted && backendMode != AudioBackendMode.JACK_ONLY) {
            logger.info { "Starting Java Sound client (device: ${selectedDeviceName ?: "Default"})..." }
            javaSoundClient = JavaSoundClient(selectedDeviceName) { buffer, nframes, sampleRate ->
                processAudio(buffer, nframes, sampleRate)
            }
            val javaStarted = javaSoundClient?.start() == true
            if (!javaStarted) {
                logger.warn { "Java Sound fallback failed to start. Audio engine is inactive." }
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
    internal fun processAudio(buffer: FloatBuffer, nframes: Int, sampleRate: Float, timestampNanos: Long = System.nanoTime()) {
        val currentTime = timestampNanos

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

        // Tap live audio stream for real-time video recording (zero allocation)
        llm.slop.liquidlsd.export.RealtimeRecorder.pushAudioBlock(buffer, startPos, safeFrames, sampleRate, gain)

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

        // Normalized onset for CV output (0–1 range)
        val onsetNormalized = (onsetStrength / 0.1f).coerceIn(0f, 1f)

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
        if (!isBpmLocked) {
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
        } else {
            // Under manual BPM lock, suppress phase nudges and clear slew buffer
            beatDetector.pendingPhaseNudge = -1.0
            phaseSlewBuffer = 0.0
        }

        // 8. Determine effective BPM and advance totalBeats
        val effectiveBpm = if (isBpmLocked) manualBpm else autoBpm
        estimatedBpm = effectiveBpm

        val deltaTimeSec = safeFrames.toDouble() / sampleRate.toDouble()
        // Flywheel momentum: always advance totalBeats using effective BPM (smooth coasting even through silence)
        totalBeats += deltaTimeSec * (effectiveBpm / 60.0)

        // 9. Publish to CV Registry (normalized to unipolar 0.0..1.0 unit range)
        CVRegistry.updateBeatAnchor(totalBeats, effectiveBpm, currentTime)
        CVRegistry.updatePushedValue("amp",    (amp  / 0.25f).coerceIn(0f, 1f))
        CVRegistry.updatePushedValue("bass",   (bass / 0.25f).coerceIn(0f, 1f))
        CVRegistry.updatePushedValue("mid",    (mid  / 0.25f).coerceIn(0f, 1f))
        CVRegistry.updatePushedValue("high",   (high / 0.25f).coerceIn(0f, 1f))
        CVRegistry.updatePushedValue("onset",  onsetNormalized)
        CVRegistry.updatePushedValue("accent", accentLevel.coerceIn(0f, 1f))

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
