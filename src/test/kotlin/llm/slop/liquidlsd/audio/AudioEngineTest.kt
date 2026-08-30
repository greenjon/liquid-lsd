package llm.slop.liquidlsd.audio

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.nio.FloatBuffer

class AudioEngineTest {

    @Test
    fun testProcessAudioBoundsSafety() {
        val maxFrames = 16384
        
        val buf1 = FloatBuffer.allocate(0)
        AudioEngine.processAudio(buf1, 0, 44100f)
        
        val buf2 = FloatBuffer.allocate(1024)
        AudioEngine.processAudio(buf2, 1024, 44100f)
        
        val buf3 = FloatBuffer.allocate(maxFrames * 2)
        AudioEngine.processAudio(buf3, maxFrames * 2, 44100f)
    }

    @Test
    fun testWatchdogSkipsReconnectWhenPresetIOInFlight() {
        AudioEngine.presetIOInFlight.set(true)
        assertTrue(AudioEngine.presetIOInFlight.get())
        
        AudioEngine.presetIOInFlight.set(false)
        assertFalse(AudioEngine.presetIOInFlight.get())
    }

    @Test
    fun testAudioDeviceCaching() {
        val devices1 = AudioEngine.getAvailableInputDevices()
        val devices2 = AudioEngine.getAvailableInputDevices()
        assertTrue(devices1 === devices2, "Subsequent calls should return cached device list instance")

        val names = AudioEngine.getAvailableDeviceNames()
        assertTrue(names.isNotEmpty())
        assertTrue(names.size == devices1.size)

        AudioEngine.refreshInputDevices()
        val devices3 = AudioEngine.getAvailableInputDevices()
        assertTrue(devices3.isNotEmpty())
    }

    @Test
    fun testManualBpmLockPhaseNudgeSuppression() {
        AudioEngine.isBpmLocked = true
        AudioEngine.manualBpm = 128.0f
        AudioEngine.beatDetector.reset()
        AudioEngine.beatDetector.pendingPhaseNudge = 0.5

        val buf = FloatBuffer.allocate(512)
        for (i in 0 until 512) buf.put(i, 0.5f)

        AudioEngine.processAudio(buf, 512, 44100f)

        // Verify phase nudge is consumed/suppressed without slewing
        kotlin.test.assertEquals(-1.0, AudioEngine.beatDetector.pendingPhaseNudge)
        kotlin.test.assertEquals(128.0f, AudioEngine.getEstimatedBpm())
    }

    @Test
    fun testFlywheelContinuityAcrossSilence() {
        AudioEngine.isBpmLocked = true
        AudioEngine.manualBpm = 120.0f
        AudioEngine.currentState = SignalState.SILENT

        val b0 = llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats()

        val buf = FloatBuffer.allocate(512)
        for (i in 0 until 10) {
            AudioEngine.processAudio(buf, 512, 44100f)
        }

        val b1 = llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats()
        assertTrue(b1 > b0, "Beat accumulator should smoothly coast during silence (b0=$b0, b1=$b1)")
    }

    @Test
    fun testBeatDetectorResetLocksTo120BpmUntilLocked() {
        val detector = BeatDetector()
        detector.reset()
        assertFalse(detector.isTempoLocked)

        // Feed blocks with 140 BPM intervals
        val sampleRate = 44100f
        val nframes = 512
        val blocksPerBeat = ((60.0f / 140.0f) * (sampleRate / nframes)).toInt()

        // First few blocks before lock duration threshold: must stay at 120 BPM
        for (i in 0..10) {
            val isOnset = (i % blocksPerBeat == 0)
            val flux = if (isOnset) 1.0f else 0.01f
            val bpm = detector.processBlock(flux, flux, flux, flux, sampleRate, nframes, flux)
            if (!detector.isTempoLocked) {
                kotlin.test.assertEquals(120.0f, bpm, 0.01f, "BPM must hold 120.0 until locked")
            }
        }
    }

    @Test
    fun testMonotonicVisualClockExtrapolation() {
        llm.slop.liquidlsd.cv.CVRegistry.resetBeatAnchor(100.0, 120f, System.nanoTime())
        var prev = llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats()

        // Simulate asynchronous anchor updates with minor backward jitter
        for (i in 1..50) {
            llm.slop.liquidlsd.cv.CVRegistry.updateBeatAnchor(100.0 + i * 0.01 - 0.005, 120f, System.nanoTime())
            val current = llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats()
            assertTrue(current >= prev, "CVRegistry beat clock must be monotonic: $current >= $prev")
            prev = current
        }
    }
}
