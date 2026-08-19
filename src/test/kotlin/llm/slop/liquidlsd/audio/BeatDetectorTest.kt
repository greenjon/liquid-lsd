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
}
