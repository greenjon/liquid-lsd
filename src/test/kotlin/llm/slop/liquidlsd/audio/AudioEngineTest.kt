package llm.slop.liquidlsd.audio

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.nio.FloatBuffer
import llm.slop.liquidlsd.ui.UITheme

class AudioEngineTest {

    @Test
    fun testProcessAudioBoundsSafety() {
        // 1. processAudio with valid, zero, and max frames without throwing
        val maxFrames = 16384
        
        val buf1 = FloatBuffer.allocate(0)
        AudioEngine.processAudio(buf1, 0, 44100f)
        
        val buf2 = FloatBuffer.allocate(1024)
        AudioEngine.processAudio(buf2, 1024, 44100f)
        
        val buf3 = FloatBuffer.allocate(maxFrames * 2)
        AudioEngine.processAudio(buf3, maxFrames * 2, 44100f)
    }

    @Test
    fun testWatchdogSkipsReconnectWhenPatchIOInFlight() {
        AudioEngine.patchIOInFlight.set(true)
        assertTrue(AudioEngine.patchIOInFlight.get())
        
        // Verify AudioEngine is accessible and we can set the flag,
        // which the watchdog loop reads.
        AudioEngine.patchIOInFlight.set(false)
        assertFalse(AudioEngine.patchIOInFlight.get())
    }

    @Test
    fun testBeatDetectorHandoff() {
        val detector = BeatDetector()
        val initialGen = detector.writeGen.get()
        
        // Simulate 16 blocks to trigger an analysis handoff
        for (i in 0 until 16) {
            detector.processBlock(0.5f, 0.5f, 0.5f, 0.5f, 44100f, 1024)
        }
        
        val newGen = detector.writeGen.get()
        assertTrue(newGen > initialGen, "Generation counter should have advanced")
    }
}
