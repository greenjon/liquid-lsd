package llm.slop.spirals.audio

import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.cv.CvHistoryBuffer
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * Orchestrates the audio capture client and runs the real-time DSP analysis pipeline.
 * Separates audio into bands, detects transients/onsets, estimates BPM, and updates CVRegistry.
 */
object AudioEngine {
    private var jackClient: JackClient? = null
    
    // DSP filters
    private var lastSampleRate = 44100f
    private val lowPass = BiquadFilter(BiquadFilter.Type.LOWPASS, lastSampleRate, 150f)
    private val midPass = BiquadFilter(BiquadFilter.Type.BANDPASS, lastSampleRate, 1000f)
    private val highPass = BiquadFilter(BiquadFilter.Type.HIGHPASS, lastSampleRate, 5000f)
    
    private val extractor = AmplitudeExtractor()

    // Pre-allocated buffer for oscilloscope rendering of raw input samples
    val rawHistory = CvHistoryBuffer(1024)

    // Temporary processing buffers to avoid allocation on the audio thread
    private var lowBuffer = FloatArray(1024)
    private var midBuffer = FloatArray(1024)
    private var highBuffer = FloatArray(1024)

    // Beat/BPM flywheel state
    private var lastCallbackTime = System.nanoTime()
    private var totalBeats = 0.0
    @Volatile private var estimatedBpm = 120f

    fun getEstimatedBpm(): Float = estimatedBpm
    fun isActive(): Boolean = jackClient != null

    // BPM Sync/Transient detection state
    private var lastBeatTime = System.nanoTime()
    private val beatIntervals = mutableListOf<Long>()
    private val maxIntervals = 8
    private val minIntervalNs = 300_000_000L  // 200 BPM max
    private val maxIntervalNs = 1_500_000_000L // 40 BPM min

    private var prevBass = 0f
    private var prevMid = 0f
    private var prevHigh = 0f
    private var accentLevel = 0f
    private var beatThreshold = 0.5f
    private var lastOnsetNormalized = 0f

    /**
     * Starts the Audio Engine and JACK client connection.
     */
    fun start() {
        jackClient = JackClient("spirals-desktop") { buffer, nframes, sampleRate ->
            processAudio(buffer, nframes, sampleRate)
        }
        jackClient?.start()
    }

    /**
     * Processes a new block of audio samples from JACK. Runs on the real-time audio thread.
     */
    private fun processAudio(buffer: FloatBuffer, nframes: Int, sampleRate: Float) {
        val currentTime = System.nanoTime()

        // 1. Dynamic sample rate adjustment
        if (sampleRate != lastSampleRate) {
            lowPass.sampleRate = sampleRate
            lowPass.updateCoefficients()
            midPass.sampleRate = sampleRate
            midPass.updateCoefficients()
            highPass.sampleRate = sampleRate
            highPass.updateCoefficients()
            lastSampleRate = sampleRate
        }

        // 2. Adjust temp buffer sizes if necessary (rare, only on buffer size changes)
        if (lowBuffer.size < nframes) {
            lowBuffer = FloatArray(nframes)
            midBuffer = FloatArray(nframes)
            highBuffer = FloatArray(nframes)
        }

        // 3. Process samples through the filter banks and update raw history
        val startPos = buffer.position()
        for (i in 0 until nframes) {
            val sample = buffer.get(startPos + i)
            rawHistory.add(sample)
            lowBuffer[i] = lowPass.process(sample)
            midBuffer[i] = midPass.process(sample)
            highBuffer[i] = highPass.process(sample)
        }

        // 4. Calculate RMS amplitudes
        val amp = extractor.calculateRms(buffer, nframes)
        val bass = extractor.calculateRms(lowBuffer, nframes)
        val mid = extractor.calculateRms(midBuffer, nframes)
        val high = extractor.calculateRms(highBuffer, nframes)

        // 5. Calculate Spectral Flux (transient/onset detection)
        val bassFlux = max(0f, bass - prevBass)
        val midFlux = max(0f, mid - prevMid)
        val highFlux = max(0f, high - prevHigh)
        val onsetRaw = (bassFlux * 1.0f) + (midFlux * 0.6f) + (highFlux * 0.3f)

        prevBass = bass
        prevMid = mid
        prevHigh = high

        val onsetNormalized = (onsetRaw / 0.05f).coerceIn(0f, 2f)
        if (onsetNormalized > accentLevel) {
            accentLevel = onsetNormalized
        } else {
            accentLevel *= 0.88f // decay
        }

        // 6. BPM Estimation and Beat Phase Sync
        if (onsetNormalized > beatThreshold && lastOnsetNormalized <= beatThreshold) {
            val interval = currentTime - lastBeatTime
            if (interval in minIntervalNs..maxIntervalNs) {
                beatIntervals.add(interval)
                if (beatIntervals.size > maxIntervals) {
                    beatIntervals.removeAt(0)
                }
                val medianInterval = beatIntervals.sorted()[beatIntervals.size / 2]
                estimatedBpm = 60_000_000_000f / medianInterval

                // Phase alignment on strong transient
                if (onsetNormalized > 1.4f) {
                    val currentPhase = totalBeats % 1.0
                    val isNearBeat = currentPhase < 0.1 || currentPhase > 0.9
                    if (!isNearBeat) {
                        // Align totalBeats to nearest beat boundary
                        totalBeats = Math.round(totalBeats).toDouble()
                    }
                }
            }
            lastBeatTime = currentTime
        }

        // Adapt threshold dynamically
        beatThreshold = (beatThreshold * 0.95f) + (onsetNormalized * 0.05f)
        beatThreshold = beatThreshold.coerceAtLeast(0.1f)

        lastOnsetNormalized = onsetNormalized

        // 7. Tick the flywheel
        val deltaTimeSec = (currentTime - lastCallbackTime) / 1_000_000_000.0
        lastCallbackTime = currentTime
        val beatDelta = deltaTimeSec * (estimatedBpm / 60.0)
        totalBeats += beatDelta

        // 8. Update central CV Registry
        CVRegistry.updateBeatAnchor(totalBeats, estimatedBpm, currentTime)
        CVRegistry.updatePushedValue("amp", (amp / 0.1f).coerceIn(0f, 2f))
        CVRegistry.updatePushedValue("bass", (bass / 0.1f).coerceIn(0f, 2f))
        CVRegistry.updatePushedValue("mid", (mid / 0.1f).coerceIn(0f, 2f))
        CVRegistry.updatePushedValue("high", (high / 0.1f).coerceIn(0f, 2f))
        CVRegistry.updatePushedValue("bassFlux", (bassFlux / 0.05f).coerceIn(0f, 2f))
        CVRegistry.updatePushedValue("onset", onsetNormalized)
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
