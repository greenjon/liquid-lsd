package llm.slop.liquidlsd.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeatDetectorTest {

    @Test
    fun testParabolicInterpolationPeakAtZero() {
        val detector = BeatDetector()
        // Symmetric peak around y2: y1=0.8, y2=1.0, y3=0.8 -> peak at offset 0.0
        val offset = detector.interpolateParabolicPeak(0.8f, 1.0f, 0.8f)
        assertEquals(0.0f, offset, 0.001f)
    }

    @Test
    fun testParabolicInterpolationPeakShiftedRight() {
        val detector = BeatDetector()
        // Peak is skewed right: y1=0.5, y2=1.0, y3=0.9 -> offset > 0.0
        val offset = detector.interpolateParabolicPeak(0.5f, 1.0f, 0.9f)
        assertTrue(offset > 0.0f, "Offset should be positive when peak is skewed right")
        assertTrue(offset <= 0.5f, "Offset should be bounded by 0.5")
    }

    @Test
    fun testParabolicInterpolationPeakShiftedLeft() {
        val detector = BeatDetector()
        // Peak is skewed left: y1=0.9, y2=1.0, y3=0.5 -> offset < 0.0
        val offset = detector.interpolateParabolicPeak(0.9f, 1.0f, 0.5f)
        assertTrue(offset < 0.0f, "Offset should be negative when peak is skewed left")
        assertTrue(offset >= -0.5f, "Offset should be bounded by -0.5")
    }

    @Test
    fun testPllModeTransientPeakDetection() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.eco()) // Eco uses PLL mode
        
        val sampleRate = 44100f
        val nframes = 512
        val blocksPerBeat = ((60.0f / 120.0f) * (sampleRate / nframes)).toInt() // ~43 blocks per beat at 120 BPM

        var bpmEstimate = 120.0f
        // Simulate 200 blocks (approx 4.5 beats of 120 BPM audio)
        for (i in 0..200) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 1.0f else 0.01f
            bpmEstimate = detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }

        assertTrue(bpmEstimate >= 40f && bpmEstimate <= 240f, "BPM estimate should remain within valid search range")
    }

    @Test
    fun testSubBlockParabolicBpmEstimation() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.balanced()) // Balanced uses AUTOCORRELATION
        
        val sampleRate = 44100f
        val nframes = 512
        val blocksPerBeat = 43 // ~120.18 BPM

        // Simulate 256 blocks of history with beat pulses every 43 blocks
        for (i in 0..256) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 0.8f else 0.02f
            detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
        }

        assertTrue(detector.writeGen.get() > 0, "Analysis generation counter should advance")
    }
}
