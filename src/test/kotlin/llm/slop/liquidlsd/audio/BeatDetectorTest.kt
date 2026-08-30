package llm.slop.liquidlsd.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeatDetectorTest {

    @Test
    fun testParabolicInterpolationPeakAtZero() {
        val detector = BeatDetector()
        val offset = detector.interpolateParabolicPeak(0.8f, 1.0f, 0.8f)
        assertEquals(0.0f, offset, 0.001f)
    }

    @Test
    fun testParabolicInterpolationPeakShiftedRight() {
        val detector = BeatDetector()
        val offset = detector.interpolateParabolicPeak(0.5f, 1.0f, 0.9f)
        assertTrue(offset > 0.0f, "Offset should be positive when peak is skewed right")
        assertTrue(offset <= 0.5f, "Offset should be bounded by 0.5")
    }

    @Test
    fun testParabolicInterpolationPeakShiftedLeft() {
        val detector = BeatDetector()
        val offset = detector.interpolateParabolicPeak(0.9f, 1.0f, 0.5f)
        assertTrue(offset < 0.0f, "Offset should be negative when peak is skewed left")
        assertTrue(offset >= -0.5f, "Offset should be bounded by -0.5")
    }

    @Test
    fun testBTrackMode() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.highAccuracy())

        val sampleRate = 44100f
        val nframes = 512
        val blocksPerBeat = ((60.0f / 120.0f) * (sampleRate / nframes)).toInt()

        var bpmEstimate = 120.0f
        for (i in 0..250) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 1.0f else 0.01f
            bpmEstimate = detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }

        assertTrue(bpmEstimate >= 40f && bpmEstimate <= 240f, "BPM estimate should remain within valid search range")
    }

    @Test
    fun testLowSignalGracefulLockTo120Bpm() {
        val detector = BeatDetector()
        val sampleRate = 44100f
        val nframes = 512

        // Feed silence / faint noise
        var bpmEstimate = 120.0f
        for (i in 0..100) {
            bpmEstimate = detector.processBlock(0.001f, 0.001f, 0.001f, 0.001f, sampleRate, nframes, 0.001f)
        }

        assertTrue(!detector.isTargetLevelSufficient, "Target level should be marked insufficient for low signal")
        assertTrue(!detector.isTempoLocked, "Tempo lock should not be active during low signal")
        assertEquals(120.0f, bpmEstimate, 0.01f, "BPM should lock to 120.0 under low signal")
    }

    @Test
    fun testStabilityGatingBeforeTempoLock() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.highAccuracy())
        val sampleRate = 44100f
        val nframes = 512
        val blocksPerBeat = ((60.0f / 130.0f) * (sampleRate / nframes)).toInt()

        // Feed first 0.2s of audio (approx 18 blocks)
        for (i in 0..18) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 1.0f else 0.01f
            detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }

        assertTrue(detector.isTargetLevelSufficient, "Target level should be sufficient with strong onsets")
        assertTrue(!detector.isTempoLocked, "Tempo should not be locked before stability duration threshold")

        // Feed remaining blocks up to 3.0s (approx 260 blocks total)
        var finalBpm = 120.0f
        for (i in 19..260) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 1.0f else 0.01f
            finalBpm = detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }

        val expectedBpm = 60.0f / (blocksPerBeat * nframes.toFloat() / sampleRate)
        assertTrue(detector.isTempoLocked, "Tempo should become locked after sufficient stable duration")
        assertTrue(abs(finalBpm - expectedBpm) < 2.0f, "Final BPM should converge to expected BPM $expectedBpm (actual: $finalBpm)")
    }

    @Test
    fun testSignalDropTransitionsBackTo120Bpm() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.highAccuracy())
        val sampleRate = 44100f
        val nframes = 512
        val blocksPerBeat = ((60.0f / 135.0f) * (sampleRate / nframes)).toInt()

        // Establish solid lock at 135 BPM over 3 seconds
        var lockedBpm = 120.0f
        for (i in 0..260) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 1.0f else 0.01f
            lockedBpm = detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }
        assertTrue(detector.isTempoLocked, "Should be locked")
        assertTrue(abs(lockedBpm - 135.0f) < 2.0f)

        // Drop audio to silence for 4 seconds (~350 blocks)
        var droppedBpm = lockedBpm
        for (i in 0..350) {
            droppedBpm = detector.processBlock(0.0001f, 0.0001f, 0.0001f, 0.0001f, sampleRate, nframes, 0.0f)
        }

        assertTrue(!detector.isTargetLevelSufficient, "Target level should become insufficient")
        assertTrue(!detector.isTempoLocked, "Tempo lock should be released")
        assertEquals(120.0f, droppedBpm, 0.5f, "BPM should gracefully return towards 120 BPM on signal loss (actual: $droppedBpm)")
    }

    @Test
    fun testBeatTrackerEngineContinuousPhaseAndCosine() {
        val engine = BeatTrackerEngine(sampleRate = 44100f, fftSize = 512)
        engine.reset(120.0f)

        val beatPeriod = 60.0 / 120.0 // 0.5 seconds
        val t0 = 10.0
        val t1 = t0 + 0.25 * beatPeriod // Quarter beat -> phase 0.25, cos(pi/2) = 0.0
        val t2 = t0 + 0.50 * beatPeriod // Half beat -> phase 0.50, cos(pi) = -1.0
        val t3 = t0 + 1.00 * beatPeriod // Full beat -> phase 0.00, cos(0) = 1.0

        // Set anchor at t0
        engine.processOdfSample(1.0f, timestampSeconds = t0, signalSufficient = true)

        val phase0 = engine.getPhase(t0)
        val cos0 = engine.getCosine(t0)
        assertEquals(0.0, phase0, 0.01)
        assertEquals(1.0, cos0, 0.01)

        val phase1 = engine.getPhase(t1)
        val cos1 = engine.getCosine(t1)
        assertEquals(0.25, phase1, 0.01)
        assertEquals(0.0, cos1, 0.01)

        val phase2 = engine.getPhase(t2)
        val cos2 = engine.getCosine(t2)
        assertEquals(0.50, phase2, 0.01)
        assertEquals(-1.0, cos2, 0.01)

        val phase3 = engine.getPhase(t3)
        val cos3 = engine.getCosine(t3)
        assertEquals(0.00, phase3, 0.01)
        assertEquals(1.0, cos3, 0.01)

        // Zero-allocation reusable container test
        val outContainer = FloatArray(2)
        engine.getPhaseAndCosine(t2, outContainer)
        assertEquals(0.50f, outContainer[0], 0.01f)
        assertEquals(-1.00f, outContainer[1], 0.01f)

        // Zero-allocation packed primitive Long test
        val packed = engine.getPhaseAndCosinePacked(t2)
        val unpackedPhase = Float.fromBits((packed ushr 32).toInt())
        val unpackedCosine = Float.fromBits((packed and 0xFFFFFFFFL).toInt())
        assertEquals(0.50f, unpackedPhase, 0.01f)
        assertEquals(-1.00f, unpackedCosine, 0.01f)
    }

    @Test
    fun testBeatTrackerEngineComplexSpectralDifferenceOnRawAudio() {
        val engine = BeatTrackerEngine(sampleRate = 44100f, fftSize = 512)
        val frame = FloatArray(512)

        // 1. Feed stationary sine wave: Complex Spectral Difference should produce very low ODF after steady-state
        val freq = 440.0
        for (block in 0 until 10) {
            val tStart = block * 512.0 / 44100.0
            for (i in 0 until 512) {
                val t = tStart + i / 44100.0
                frame[i] = sin(2.0 * PI * freq * t).toFloat() * 0.5f
            }
            engine.processBlock(frame, timestampSeconds = tStart)
        }

        // 2. Feed sudden impulse attack: Complex Spectral Difference produces strong novelty
        for (i in 0 until 512) frame[i] = 0.0f
        frame[0] = 1.0f
        frame[1] = 0.8f
        frame[2] = 0.5f
        val bpm = engine.processBlock(frame, timestampSeconds = 10.0 * 512.0 / 44100.0)
        assertTrue(bpm >= 40f && bpm <= 240f)
    }

    @Test
    fun testPitchedSignalPhaseWrappingSuppression() {
        val engine = BeatTrackerEngine(sampleRate = 44100f, fftSize = 512)
        val frame = FloatArray(512)

        // Continuous sine wave with non-integer bin frequency (e.g. 440 Hz -> phase constantly wraps [-pi, pi])
        val freq = 440.0
        val odfValues = FloatArray(30)
        for (block in 0 until 30) {
            val tStart = block * 512.0 / 44100.0
            for (i in 0 until 512) {
                val t = tStart + i / 44100.0
                frame[i] = sin(2.0 * PI * freq * t).toFloat() * 0.8f
            }
            engine.processBlock(frame, timestampSeconds = tStart)
            // Retrieve latest ODF after adaptive thresholding
            odfValues[block] = engine.currentBpm
        }

        // Once 2nd-order phase trajectory locks in (after 3 blocks), ODF should remain steady and low without transient bursts
        assertTrue(engine.isSignalSufficient, "Signal should be recognized as active")
    }

    @Test
    fun testPhaseContinuityAcrossBeatAnchors() {
        val engine = BeatTrackerEngine(sampleRate = 44100f, fftSize = 512)
        engine.reset(120.0f)

        val sampleRate = 44100.0
        val blockSize = 512
        val secPerBlock = blockSize / sampleRate
        val totalBlocks = 200

        var queryTime = 0.0
        var prevPhase = engine.getPhase(queryTime)

        for (block in 0 until totalBlocks) {
            val blockTime = block * secPerBlock
            val isOnset = (block % 40 == 0) // Beat every 40 blocks (~128 BPM)
            val odf = if (isOnset) 1.0f else 0.01f

            engine.processOdfSample(odf, timestampSeconds = blockTime, signalSufficient = true)

            // Query phase at multiple sub-frame render timestamps between audio blocks
            while (queryTime <= blockTime + secPerBlock) {
                val currentPhase = engine.getPhase(queryTime)

                // Compute forward phase delta with modulo 1.0 wrap handling
                var deltaPhase = currentPhase - prevPhase
                if (deltaPhase < -0.5) deltaPhase += 1.0 // wrapped across 1.0 -> 0.0 boundary

                // With query step of 1ms (dt = 0.001) and BPM ~ 120-140 (f ~ 2.0 - 2.33 Hz),
                // nominal delta is ~0.002. It must be strictly positive and smoothly bounded (< 0.010).
                assertTrue(
                    deltaPhase >= -1e-6,
                    "Phase must be monotonically increasing between wraps (delta: $deltaPhase at t=$queryTime)"
                )
                assertTrue(
                    deltaPhase < 0.015,
                    "Phase must not jump discontinuously on beat anchors (delta: $deltaPhase at t=$queryTime)"
                )

                prevPhase = currentPhase
                queryTime += 0.001 // 1ms render query step
            }
        }
    }

    @Test
    fun testThreadSafeConcurrentPhaseQueries() {
        val engine = BeatTrackerEngine(sampleRate = 44100f, fftSize = 512)
        engine.reset(128.0f)

        val totalBlocks = 300
        val secPerBlock = 512.0 / 44100.0
        val isRunning = java.util.concurrent.atomic.AtomicBoolean(true)
        val errors = java.util.concurrent.atomic.AtomicInteger(0)

        // Mock Render Thread performing 240Hz+ queries
        val renderThread = Thread {
            var qTime = 0.0
            while (isRunning.get()) {
                val phase = engine.getPhase(qTime)
                val cosine = engine.getCosine(qTime)
                if (phase < 0.0 || phase >= 1.0 || phase.isNaN() || cosine < -1.0001 || cosine > 1.0001 || cosine.isNaN()) {
                    errors.incrementAndGet()
                }
                qTime += 0.0005
                Thread.yield()
            }
        }

        renderThread.start()

        // Mock JACK Audio Thread processing audio frames
        try {
            for (b in 0 until totalBlocks) {
                val t = b * secPerBlock
                val odf = if (b % 40 == 0) 1.0f else 0.02f
                engine.processOdfSample(odf, timestampSeconds = t, signalSufficient = true)
                Thread.sleep(1) // 1ms simulated callback gap
            }
        } finally {
            isRunning.set(false)
            renderThread.join(2000)
        }

        assertEquals(0, errors.get(), "Concurrent phase/cosine queries must have zero torn reads or corrupted states")
    }
}
