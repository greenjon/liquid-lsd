package llm.slop.liquidlsd.audio

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
    fun testAutocorrelationMode() {
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
    fun testEnergyDifferenceMode() {
        val detector = BeatDetector()
        val settings = BeatDetectionSettings.balanced().apply { mode = BeatDetectionMode.ENERGY_DIFFERENCE }
        detector.applyPreset(settings)
        
        val sampleRate = 44100f
        val nframes = 512
        val blocksPerBeat = ((60.0f / 120.0f) * (sampleRate / nframes)).toInt()

        var bpmEstimate = 120.0f
        for (i in 0..200) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 1.0f else 0.01f
            bpmEstimate = detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }

        assertTrue(bpmEstimate >= 40f && bpmEstimate <= 240f, "BPM estimate should remain within valid search range")
    }

    @Test
    fun testResonatorMode() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.eco())
        
        val sampleRate = 44100f
        val nframes = 512
        val blocksPerBeat = 43

        for (i in 0..256) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 0.8f else 0.02f
            detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }
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
        assertEquals(-1.0, detector.pendingPhaseNudge, "Phase nudges should be suppressed under low signal")
    }

    @Test
    fun testStabilityGatingBeforeTempoLock() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.highAccuracy())
        val sampleRate = 44100f
        val nframes = 512
        val blocksPerBeat = ((60.0f / 130.0f) * (sampleRate / nframes)).toInt()

        // Feed first 0.5s of audio (approx 43 blocks)
        for (i in 0..43) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 1.0f else 0.01f
            detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }

        assertTrue(detector.isTargetLevelSufficient, "Target level should be sufficient with strong onsets")
        // Within the first 0.5s (less than 1.5s required lock time), isTempoLocked should still be false
        assertTrue(!detector.isTempoLocked, "Tempo should not be locked before stability duration threshold")

        // Feed remaining blocks up to 3.0s (approx 260 blocks total)
        var finalBpm = 120.0f
        for (i in 44..260) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 1.0f else 0.01f
            finalBpm = detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }

        val expectedBpm = 60.0f / (blocksPerBeat * nframes.toFloat() / sampleRate)
        assertTrue(detector.isTempoLocked, "Tempo should become locked after sufficient stable duration")
        assertTrue(kotlin.math.abs(finalBpm - expectedBpm) < 2.0f, "Final BPM should converge to expected BPM $expectedBpm (actual: $finalBpm)")
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
        assertTrue(kotlin.math.abs(lockedBpm - 135.0f) < 2.0f)

        // Drop audio to silence for 4 seconds (~350 blocks)
        var droppedBpm = lockedBpm
        for (i in 0..350) {
            droppedBpm = detector.processBlock(0.0001f, 0.0001f, 0.0001f, 0.0001f, sampleRate, nframes, 0.0f)
        }

        assertTrue(!detector.isTargetLevelSufficient, "Target level should become insufficient")
        assertTrue(!detector.isTempoLocked, "Tempo lock should be released")
        assertEquals(120.0f, droppedBpm, 0.5f, "BPM should gracefully return towards 120 BPM on signal loss (actual: $droppedBpm)")
    }
}
