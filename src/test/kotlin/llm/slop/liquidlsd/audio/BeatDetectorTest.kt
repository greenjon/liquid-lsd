package llm.slop.liquidlsd.audio

import kotlin.test.Test
import kotlin.test.assertTrue

class BeatDetectorTest {

    @Test
    fun testEnergyDifferenceMode() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.balanced())
        
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
        detector.applyPreset(BeatDetectionSettings.eco()) // Eco uses RESONATOR mode
        
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
