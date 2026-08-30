package llm.slop.liquidlsd.audio

import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.CvHistoryBuffer
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.log10
import mu.KotlinLogging

enum class SignalState { SILENT, ACTIVE }

enum class BeatDetectionMode { AUTOCORRELATION, ENERGY_DIFFERENCE, RESONATOR }
enum class AudioTarget { UNFILTERED, LOW, MID, HIGH }

data class BeatDetectionSettings(
    var mode: BeatDetectionMode = BeatDetectionMode.AUTOCORRELATION,
    var target: AudioTarget = AudioTarget.LOW,
    var bpmSearchFloor: Int = 40,
    var bpmSearchCeiling: Int = 200,
    var bpmGridResolution: Float = 0.5f,
    var analysisWindowLength: Float = 4.0f,
    var energyThreshold: Float = 1.5f,
    var pllAdaptationRate: Float = 0.2f,
    var biquadQ: Float = 3.0f
) {
    companion object {
        fun highAccuracy() = BeatDetectionSettings(mode = BeatDetectionMode.AUTOCORRELATION, bpmGridResolution = 0.5f, pllAdaptationRate = 0.25f)
        fun balanced() = BeatDetectionSettings(mode = BeatDetectionMode.AUTOCORRELATION, bpmGridResolution = 1.0f, pllAdaptationRate = 0.20f)
        fun eco() = BeatDetectionSettings(mode = BeatDetectionMode.RESONATOR, biquadQ = 5.0f)
    }
}

class BeatDetector {
    @Volatile
    var settings = BeatDetectionSettings.highAccuracy()
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

    // Pre-allocated ring buffers for multi-band onset history (zero allocation)
    private val onsetHistory = FloatArray(2048)
    private val bassHistory = FloatArray(2048)
    private val midHistory = FloatArray(2048)
    private val highHistory = FloatArray(2048)
    private var historyCount = 0
    private var historyIndex = 0

    // Dynamic peak thresholding
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

    fun interpolateParabolicPeak(y1: Float, y2: Float, y3: Float): Float {
        val denom = y1 - 2.0f * y2 + y3
        if (kotlin.math.abs(denom) < 1e-6f) return 0.0f
        val delta = (y1 - y3) / (2.0f * denom)
        return delta.coerceIn(-0.5f, 0.5f)
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
            AudioTarget.LOW -> if (onsetStrength > 0f) onsetStrength else lowAmp
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
        val minPeriodBlocks = (fps * (60.0f / ceilBpm)).toInt().coerceAtLeast(1)
        val maxPeriodBlocks = (fps * (60.0f / floorBpm)).toInt().coerceAtMost(onsetHistory.size / 2)

        // Store target band & multi-band energies to history ring buffers (zero allocation)
        onsetHistory[historyIndex] = targetAmp
        bassHistory[historyIndex] = if (onsetStrength > 0f) lowAmp else lowAmp * 0.1f
        midHistory[historyIndex] = midAmp
        highHistory[historyIndex] = highAmp
        historyIndex = (historyIndex + 1) % onsetHistory.size
        if (historyCount < onsetHistory.size) historyCount++

        // Track target band energy to determine if sufficient signal exists
        localEnergyAverage = localEnergyAverage * 0.98f + targetAmp * 0.02f
        isTargetLevelSufficient = localEnergyAverage > 0.005f

        timeSinceLastBeatBlocks += 1.0f

        val mode = settings.mode

        if (mode == BeatDetectionMode.AUTOCORRELATION) {
            val availableMaxDelay = (historyCount - minPeriodBlocks).coerceAtLeast(0).coerceAtMost(maxPeriodBlocks)
            if (availableMaxDelay >= minPeriodBlocks) {
                var acSum = 0.0f
                var delayCount = 0

                val winLenBlocks = (historyCount - availableMaxDelay).coerceAtMost((settings.analysisWindowLength * fps).toInt())

                var maxAc = -1.0f
                var rawBestDelay = minPeriodBlocks

                for (delay in minPeriodBlocks..availableMaxDelay) {
                    var ac = 0.0f
                    for (i in 0 until winLenBlocks) {
                        val idx1 = (historyIndex - 1 - i + onsetHistory.size * 10) % onsetHistory.size
                        val idx2 = (historyIndex - 1 - i - delay + onsetHistory.size * 10) % onsetHistory.size
                        ac += bassHistory[idx1] * bassHistory[idx2] + midHistory[idx1] * midHistory[idx2] + highHistory[idx1] * highHistory[idx2]
                    }
                    acSum += ac
                    delayCount++

                    val candidateBpm = 60.0f / (delay / fps)
                    val bpmDiff = candidateBpm - 120.0f
                    val gaussianWeight = kotlin.math.exp(-(bpmDiff * bpmDiff) / (2.0f * 80.0f * 80.0f))
                    val weightedAc = ac * (0.85f + 0.15f * gaussianWeight)

                    if (weightedAc > maxAc) {
                        maxAc = weightedAc
                        rawBestDelay = delay
                    }
                }

                val meanAc = acSum / maxOf(1, delayCount)
                confidence = if (meanAc > 1e-5f) (maxAc / (meanAc * 3.0f)).coerceIn(0.0f, 1.0f) else 0.5f

                // Harmonic Unwrapping: Check if half-lag (double tempo) is a valid fundamental beat period
                var bestDelay = rawBestDelay
                while (bestDelay / 2 >= minPeriodBlocks) {
                    val halfDelay = bestDelay / 2
                    var halfAc = 0.0f
                    var fullAc = 0.0f
                    for (i in 0 until winLenBlocks) {
                        val idx1 = (historyIndex - 1 - i + onsetHistory.size * 10) % onsetHistory.size
                        val idxHalf = (historyIndex - 1 - i - halfDelay + onsetHistory.size * 10) % onsetHistory.size
                        val idxFull = (historyIndex - 1 - i - bestDelay + onsetHistory.size * 10) % onsetHistory.size
                        halfAc += bassHistory[idx1] * bassHistory[idxHalf] + midHistory[idx1] * midHistory[idxHalf] + highHistory[idx1] * highHistory[idxHalf]
                        fullAc += bassHistory[idx1] * bassHistory[idxFull] + midHistory[idx1] * midHistory[idxFull] + highHistory[idx1] * highHistory[idxFull]
                    }
                    val halfBpm = 60.0f / (halfDelay / fps)
                    if (halfBpm <= 165.0f && fullAc > 1e-5f && (halfAc / fullAc) >= 0.45f) {
                        bestDelay = halfDelay
                    } else {
                        break
                    }
                }

                if (bestDelay >= minPeriodBlocks && bestDelay <= availableMaxDelay) {
                    // Sub-block parabolic lag interpolation around bestDelay
                    var prevAc = 0.0f
                    var centerAc = 0.0f
                    var nextAc = 0.0f

                    val delayPrev = (bestDelay - 1).coerceAtLeast(minPeriodBlocks)
                    val delayNext = (bestDelay + 1).coerceAtMost(availableMaxDelay)

                    for (i in 0 until winLenBlocks) {
                        val idx1 = (historyIndex - 1 - i + onsetHistory.size * 10) % onsetHistory.size
                        val idxPrev = (historyIndex - 1 - i - delayPrev + onsetHistory.size * 10) % onsetHistory.size
                        val idxCenter = (historyIndex - 1 - i - bestDelay + onsetHistory.size * 10) % onsetHistory.size
                        val idxNext = (historyIndex - 1 - i - delayNext + onsetHistory.size * 10) % onsetHistory.size

                        prevAc += bassHistory[idx1] * bassHistory[idxPrev] + midHistory[idx1] * midHistory[idxPrev] + highHistory[idx1] * highHistory[idxPrev]
                        centerAc += bassHistory[idx1] * bassHistory[idxCenter] + midHistory[idx1] * midHistory[idxCenter] + highHistory[idx1] * highHistory[idxCenter]
                        nextAc += bassHistory[idx1] * bassHistory[idxNext] + midHistory[idx1] * midHistory[idxNext] + highHistory[idx1] * highHistory[idxNext]
                    }

                    val deltaLag = interpolateParabolicPeak(prevAc, centerAc, nextAc)
                    val subBlockLag = (bestDelay + deltaLag).coerceAtLeast(1.0f)
                    val calcBpm = 60.0f / (subBlockLag / fps)

                    // Adaptive PLL response: faster initial lock, smooth steady state
                    val adaptRate = if (historyCount < maxPeriodBlocks * 2) 0.45f else settings.pllAdaptationRate.coerceIn(0.01f, 0.5f)
                    currentBpm = currentBpm * (1.0f - adaptRate) + calcBpm.coerceIn(floorBpm, ceilBpm) * adaptRate

                    // Phase alignment calculation
                    val periodBlocks = subBlockLag.toInt().coerceAtLeast(1)
                    var maxPhaseImpulse = 0.0f
                    var bestPhaseShift = 0
                    for (shift in 0 until periodBlocks) {
                        var sum = 0.0f
                        var k = 0
                        while (true) {
                            val kBlocks = shift + k * periodBlocks
                            if (kBlocks >= historyCount) break
                            val idx = (historyIndex - 1 - kBlocks + onsetHistory.size * 10) % onsetHistory.size
                            sum += onsetHistory[idx]
                            k++
                        }
                        if (sum > maxPhaseImpulse) {
                            maxPhaseImpulse = sum
                            bestPhaseShift = shift
                        }
                    }
                    if (maxPhaseImpulse > 0.02f) {
                        pendingPhaseNudge = bestPhaseShift.toDouble() / periodBlocks.toDouble()
                    }
                }
            }
        } else if (mode == BeatDetectionMode.ENERGY_DIFFERENCE) {
            // Energy Difference + Dynamic Peak Sync
            if (targetAmp > localEnergyAverage * settings.energyThreshold && timeSinceLastBeatBlocks > minPeriodBlocks) {
                if (lastTargetAmp > targetAmp && lastTargetAmp > localEnergyAverage * settings.energyThreshold) {
                    pendingPhaseNudge = 0.0
                    
                    val measuredPeriodBlocks = timeSinceLastBeatBlocks - 1.0f
                    val calcBpm = 60.0f / (measuredPeriodBlocks / fps)
                    
                    currentBpm = currentBpm * (1f - settings.pllAdaptationRate) + calcBpm.coerceIn(floorBpm, ceilBpm) * settings.pllAdaptationRate
                    timeSinceLastBeatBlocks = 0f
                }
            }
        } else if (mode == BeatDetectionMode.RESONATOR) {
            // Biquad Resonator
            resonator.frequency = currentBpm / 60.0f
            resonator.q = settings.biquadQ
            resonator.updateCoefficients()
            
            val out = resonator.process(targetAmp - localEnergyAverage)
            
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
        estimatedBpm = manualBpm
        currentState = SignalState.SILENT
        lastSignalTime = System.nanoTime()

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

        // 9. Publish to CV Registry (normalized to unipolar 0.0..1.0 unit range)
        CVRegistry.updateBeatAnchor(totalBeats, estimatedBpm, currentTime)
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
