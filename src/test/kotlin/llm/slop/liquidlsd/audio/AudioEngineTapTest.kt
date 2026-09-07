package llm.slop.liquidlsd.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioEngineTapTest {

    @Test
    fun testRegisterTapInLockedMode() {
        AudioEngine.isBpmLocked = true
        AudioEngine.manualBpm = 120.0f
        AudioEngine.setBpmDirectly(120.0f)

        AudioEngine.registerTap(135.0f, System.nanoTime())
        assertEquals(135.0f, AudioEngine.manualBpm)
        assertEquals(135.0f, AudioEngine.getEstimatedBpm())

        // Reset back to 120.0f
        AudioEngine.manualBpm = 120.0f
        AudioEngine.setBpmDirectly(120.0f)
    }

    @Test
    fun testRegisterTapInUnlockedMode() {
        AudioEngine.isBpmLocked = false
        AudioEngine.registerTap(142.0f, System.nanoTime())

        assertEquals(142.0f, AudioEngine.getEstimatedBpm())
        assertEquals(142.0f, AudioEngine.beatDetector.engine.currentBpm)
        assertEquals(0.0, AudioEngine.beatDetector.pendingPhaseNudge)

        // Reset back
        AudioEngine.isBpmLocked = true
        AudioEngine.manualBpm = 120.0f
        AudioEngine.setBpmDirectly(120.0f)
    }
}
