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
        AudioEngine.clockSource = ClockSource.MANUAL
        AudioEngine.manualBpm = 120.0f
        AudioEngine.setBpmDirectly(120.0f)
    }

    @Test
    fun testNudgeAndHalveDoubleTempo() {
        AudioEngine.clockSource = ClockSource.MANUAL
        AudioEngine.setBpmDirectly(120.0f)
        assertEquals(120.0f, AudioEngine.manualBpm)

        AudioEngine.nudgeTempo(0.5f)
        assertEquals(120.5f, AudioEngine.manualBpm)

        AudioEngine.nudgeTempo(-1.0f)
        assertEquals(119.5f, AudioEngine.manualBpm)

        AudioEngine.setBpmDirectly(130.0f)
        AudioEngine.halveTempo()
        assertEquals(65.0f, AudioEngine.manualBpm)

        AudioEngine.doubleTempo()
        assertEquals(130.0f, AudioEngine.manualBpm)

        // Clamping check
        AudioEngine.setBpmDirectly(230.0f)
        AudioEngine.doubleTempo()
        assertEquals(240.0f, AudioEngine.manualBpm)

        // Reset back
        AudioEngine.setBpmDirectly(120.0f)
    }

    @Test
    fun testResyncDownbeat() {
        AudioEngine.clockSource = ClockSource.MANUAL
        AudioEngine.setBpmDirectly(120.0f)
        AudioEngine.resyncDownbeat()

        val beats = llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats()
        kotlin.test.assertTrue(beats >= 0.0)
    }

    @Test
    fun testClockSourceFromString() {
        assertEquals(ClockSource.MANUAL, ClockSource.fromString("MANUAL"))
        assertEquals(ClockSource.MANUAL, ClockSource.fromString("MANUAL_TAP"))
        assertEquals(ClockSource.MANUAL, ClockSource.fromString("ABLETON_LINK"))
        assertEquals(ClockSource.AUDIO_TRACKER, ClockSource.fromString("AUDIO_TRACKER"))
        assertEquals(ClockSource.AUDIO_TRACKER, ClockSource.fromString("UNKNOWN_VALUE"))
    }
}
