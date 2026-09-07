package llm.slop.liquidlsd.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TapTempoControllerTest {

    @Test
    fun testTapTempoCadenceCalculation() {
        val controller = TapTempoController(AudioEngine)
        controller.reset()

        var timeNs = 1_000_000_000L
        // First tap: should align phase, no BPM yet
        val firstBpm = controller.tap(timeNs)
        assertNull(firstBpm)
        assertEquals(1, controller.getActiveTapCount(timeNs))

        // Second tap 500ms later (120 BPM)
        timeNs += 500_000_000L
        val secondBpm = controller.tap(timeNs)
        assertNotNull(secondBpm)
        assertEquals(120.0f, secondBpm)
        assertEquals(2, controller.getActiveTapCount(timeNs))

        // Third tap 500ms later
        timeNs += 500_000_000L
        val thirdBpm = controller.tap(timeNs)
        assertNotNull(thirdBpm)
        assertEquals(120.0f, thirdBpm)
        assertEquals(3, controller.getActiveTapCount(timeNs))
    }

    @Test
    fun testTapTempoTimeoutReset() {
        val controller = TapTempoController(AudioEngine)
        controller.reset()

        var timeNs = 10_000_000_000L
        controller.tap(timeNs)
        timeNs += 500_000_000L
        val bpm = controller.tap(timeNs)
        assertEquals(120.0f, bpm)

        // Wait 2.5 seconds (exceeds 2.0s timeout)
        timeNs += 2_500_000_000L
        val timeoutBpm = controller.tap(timeNs)
        assertNull(timeoutBpm, "Tap after timeout should reset cadence and return null BPM")
        assertEquals(1, controller.getActiveTapCount(timeNs))

        // Next tap 400ms later (150 BPM)
        timeNs += 400_000_000L
        val newCadenceBpm = controller.tap(timeNs)
        assertEquals(150.0f, newCadenceBpm)
    }

    @Test
    fun testTapTempoClamping() {
        AudioEngine.beatDetector.engine.bpmFloor = 40f
        AudioEngine.beatDetector.engine.bpmCeiling = 200f
        val controller = TapTempoController(AudioEngine)
        controller.reset()

        var timeNs = 20_000_000_000L
        controller.tap(timeNs)
        // 100ms interval = 600 BPM, should clamp to ceiling (200 BPM)
        timeNs += 100_000_000L
        val highBpm = controller.tap(timeNs)
        assertEquals(200.0f, highBpm)

        controller.reset()
        timeNs += 3_000_000_000L
        controller.tap(timeNs)
        // 1800ms interval = 33.3 BPM, should clamp to floor (40 BPM)
        timeNs += 1_800_000_000L
        val lowBpm = controller.tap(timeNs)
        assertEquals(40.0f, lowBpm)
    }

    @Test
    fun testFlashIntensityDecay() {
        val controller = TapTempoController(AudioEngine)
        controller.reset()

        val tapTimeNs = 50_000_000_000L
        controller.tap(tapTimeNs)

        // Instantaneously at tap
        val flash0 = controller.getFlashIntensity(tapTimeNs)
        assertEquals(1.0f, flash0)

        // 150ms later (halfway through 300ms duration)
        val flashHalf = controller.getFlashIntensity(tapTimeNs + 150_000_000L)
        assertTrue(flashHalf in 0.45f..0.55f, "Flash should be ~0.5 at 150ms")

        // 350ms later (fully decayed)
        val flashDone = controller.getFlashIntensity(tapTimeNs + 350_000_000L)
        assertEquals(0.0f, flashDone)
    }
}
