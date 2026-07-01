package llm.slop.spirals.audio

import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.cv.CvHistoryBuffer
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.log10
import mu.KotlinLogging

enum class SignalState { SILENT, ACTIVE }

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

    // DSP filters
    private var lastSampleRate = 44100f
    private val lowPass  = BiquadFilter(BiquadFilter.Type.LOWPASS,  lastSampleRate, 150f)
    private val midPass  = BiquadFilter(BiquadFilter.Type.BANDPASS, lastSampleRate, 1000f)
    private val highPass = BiquadFilter(BiquadFilter.Type.HIGHPASS, lastSampleRate, 5000f)

    private val extractor = AmplitudeExtractor()

    // Pre-allocated buffer for oscilloscope rendering of raw input samples
    val rawHistory = CvHistoryBuffer(1024)

    // Temporary processing buffers — resized only on JACK buffer-size change (rare)
    private var lowBuffer  = FloatArray(1024)
    private var midBuffer  = FloatArray(1024)
    private var highBuffer = FloatArray(1024)

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
        jackClient?.start()
    }

    /**
     * Attempts to reconnect to JACK if not currently active.
     * Safe to call from a background thread.
     */
    fun tryReconnect() {
        if (isActive()) return
        logger.info { "Watchdog attempting JACK reconnection..." }
        stop()
        start()
    }

    /**
     * Processes a new block of audio samples from JACK. Runs on the real-time audio thread.
     * ZERO ALLOCATIONS — all buffers are pre-allocated in [start] or at object init.
     */
    private fun processAudio(buffer: FloatBuffer, nframes: Int, sampleRate: Float) {
        val currentTime = System.nanoTime()
        totalSamplesProcessed += nframes

        // 1. Dynamic sample rate adjustment (rare)
        if (sampleRate != lastSampleRate) {
            lowPass.sampleRate  = sampleRate; lowPass.updateCoefficients()
            midPass.sampleRate  = sampleRate; midPass.updateCoefficients()
            highPass.sampleRate = sampleRate; highPass.updateCoefficients()
            lastSampleRate = sampleRate
        }

        // 2. Resize temp buffers only on JACK buffer-size change (rare)
        if (lowBuffer.size < nframes) {
            lowBuffer  = FloatArray(nframes)
            midBuffer  = FloatArray(nframes)
            highBuffer = FloatArray(nframes)
        }

        // 3. Filter bank + raw history
        val startPos = buffer.position()
        val gain = inputGain
        for (i in 0 until nframes) {
            val sample = buffer.get(startPos + i) * gain
            rawHistory.add(sample)
            lowBuffer[i]  = lowPass.process(sample)
            midBuffer[i]  = midPass.process(sample)
            highBuffer[i] = highPass.process(sample)
        }

        // 4. RMS amplitudes per band
        val amp  = extractor.calculateRms(buffer, nframes) * gain
        val bass = extractor.calculateRms(lowBuffer,  nframes)
        val mid  = extractor.calculateRms(midBuffer,  nframes)
        val high = extractor.calculateRms(highBuffer, nframes)

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
        val deltaTimeSec = nframes.toDouble() / sampleRate.toDouble()
        if (currentState != SignalState.SILENT) {
            totalBeats += deltaTimeSec * (estimatedBpm / 60.0)
        }

        // 8. Manual BPM lock override (always active as real-time estimation is removed)
        estimatedBpm = manualBpm

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
