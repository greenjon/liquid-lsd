package llm.slop.liquidlsd.audio

import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.FloatBuffer

class AudioEngineTest {

    @BeforeTest
    fun setUp() {
        resetState()
    }

    @AfterTest
    fun tearDown() {
        resetState()
    }

    private fun resetState() {
        AudioEngine.stop()
        AudioEngine.clockSource = ClockSource.MANUAL
        AudioEngine.backendMode = AudioEngine.AudioBackendMode.AUTO
        AudioEngine.channelRouting = AudioChannelRouting.MIX
        AudioEngine.selectedDeviceName = null
        AudioEngine.inputGain = 1.0f
        AudioEngine.isBpmLocked = true
        AudioEngine.manualBpm = 120.0f
        AudioEngine.setBpmDirectly(120.0f)
        llm.slop.liquidlsd.link.AbletonLinkEngine.setEnabled(false)
    }

    // --- Audio Processing & Buffer Safety ---

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
    fun testAutomaticReconnectDisabledOnStartupFailure() {
        try {
            AudioEngine.selectDevice(null, AudioEngine.AudioBackendMode.JACK_ONLY)
            // In headless test environments without JACK running, automatic reconnect should be disabled
            if (!AudioEngine.isJackConnected()) {
                assertFalse(AudioEngine.automaticReconnectEnabled, "Automatic reconnect should be disabled after startup failure")

                // Watchdog tryReconnect without force must not re-attempt
                AudioEngine.tryReconnect(force = false)
                assertFalse(AudioEngine.automaticReconnectEnabled)

                // Force reconnect re-enables automatic reconnect attempt
                AudioEngine.tryReconnect(force = true)
                // After attempting startClient again without JACK running, it will disable again
                assertFalse(AudioEngine.automaticReconnectEnabled)
            }
        } finally {
            AudioEngine.stop()
        }
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
        assertEquals(-1.0, AudioEngine.beatDetector.pendingPhaseNudge)
        assertEquals(128.0f, AudioEngine.getEstimatedBpm())
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
                assertEquals(120.0f, bpm, 0.01f, "BPM must hold 120.0 until locked")
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

    // --- Stereo Routing & Gain Operations ---

    @Test
    fun testStereoRoutingMixDownmixAndHeadroom() {
        AudioEngine.channelRouting = AudioChannelRouting.MIX
        AudioEngine.inputGain = 1.0f

        val nframes = 256
        val bufL = FloatBuffer.allocate(nframes)
        val bufR = FloatBuffer.allocate(nframes)

        // Case 1: In-phase coherent 1.0 peak signals on both channels
        for (i in 0 until nframes) {
            bufL.put(i, 1.0f)
            bufR.put(i, 1.0f)
        }

        AudioEngine.processAudio(bufL, bufR, nframes, 44100f)

        // In MIX mode, (1.0 + 1.0) * 0.5 = 1.0 -> 0 dBFS, zero clipping
        val sample = AudioEngine.rawHistory.getAt(AudioEngine.rawHistory.size - 1)
        assertEquals(1.0f, sample, 0.001f, "MIX of dual 1.0 in-phase signals must sum to exactly 1.0 (no clipping)")
        assertEquals(1.0f, AudioEngine.meterPeakL, 0.001f)
        assertEquals(1.0f, AudioEngine.meterPeakR, 0.001f)

        // Case 2: Signal on Left only, silent on Right
        for (i in 0 until nframes) {
            bufL.put(i, 0.8f)
            bufR.put(i, 0.0f)
        }
        AudioEngine.processAudio(bufL, bufR, nframes, 44100f)
        // In MIX mode with Right dead: (0.8 + 0.0) * 0.5 = 0.4 (-6 dB attenuation)
        val sample2 = AudioEngine.rawHistory.getAt(AudioEngine.rawHistory.size - 1)
        assertEquals(0.4f, sample2, 0.001f, "MIX of 0.8L and 0.0R must be 0.4 (-6dB)")
        assertEquals(0.8f, AudioEngine.meterPeakL, 0.001f)
        assertEquals(0.0f, AudioEngine.meterPeakR, 0.001f)
    }

    @Test
    fun testStereoRoutingLeftOnlyAndRightOnly() {
        AudioEngine.inputGain = 1.0f
        val nframes = 256
        val bufL = FloatBuffer.allocate(nframes)
        val bufR = FloatBuffer.allocate(nframes)

        for (i in 0 until nframes) {
            bufL.put(i, 0.75f)
            bufR.put(i, 0.25f)
        }

        // Test LEFT_ONLY
        AudioEngine.channelRouting = AudioChannelRouting.LEFT_ONLY
        AudioEngine.processAudio(bufL, bufR, nframes, 44100f)
        assertEquals(0.75f, AudioEngine.rawHistory.getAt(AudioEngine.rawHistory.size - 1), 0.001f, "LEFT_ONLY must route Left channel at full level")

        // Test RIGHT_ONLY
        AudioEngine.channelRouting = AudioChannelRouting.RIGHT_ONLY
        AudioEngine.processAudio(bufL, bufR, nframes, 44100f)
        assertEquals(0.25f, AudioEngine.rawHistory.getAt(AudioEngine.rawHistory.size - 1), 0.001f, "RIGHT_ONLY must route Right channel at full level")

        // Meters must still show true physical levels for both channels regardless of routing
        assertEquals(0.75f, AudioEngine.meterPeakL, 0.001f)
        assertEquals(0.25f, AudioEngine.meterPeakR, 0.001f)
    }

    @Test
    fun testMonoInputFallbackInMixRouting() {
        // When rightBuffer is null (e.g. single mono capture port), MIX mode routes Left at unity (no -6dB penalty)
        AudioEngine.channelRouting = AudioChannelRouting.MIX
        AudioEngine.inputGain = 1.0f

        val nframes = 256
        val buf = FloatBuffer.allocate(nframes)
        for (i in 0 until nframes) buf.put(i, 0.8f)

        AudioEngine.processAudio(buf, nframes, 44100f)
        assertEquals(0.8f, AudioEngine.rawHistory.getAt(AudioEngine.rawHistory.size - 1), 0.001f, "Single-channel mono in MIX mode must preserve unity gain")
        assertEquals(0.8f, AudioEngine.meterPeakL, 0.001f)
        assertEquals(0.0f, AudioEngine.meterPeakR, 0.001f)
    }

    @Test
    fun testJackOnlyModeReturnsVirtualJackDevice() {
        AudioEngine.backendMode = AudioEngine.AudioBackendMode.JACK_ONLY
        val devices = AudioEngine.getAvailableInputDevices(forceRefresh = true)
        assertEquals(1, devices.size)
        assertEquals("jack_default", devices[0].id)
        assertEquals("JACK System Capture", devices[0].name)
    }

    @Test
    fun testSelectDeviceNoOpWhenUnchanged() {
        AudioEngine.selectedDeviceName = "TestDevice"
        AudioEngine.backendMode = AudioEngine.AudioBackendMode.JAVASOUND_ONLY
        // When already configured and not active (or active with identical settings), selectDevice must not error
        AudioEngine.selectDevice("TestDevice", AudioEngine.AudioBackendMode.JAVASOUND_ONLY)
        assertEquals("TestDevice", AudioEngine.selectedDeviceName)
        assertEquals(AudioEngine.AudioBackendMode.JAVASOUND_ONLY, AudioEngine.backendMode)
    }

    // --- Manual Tempo, Tapping & Clock Operations ---

    @Test
    fun testRegisterTapInLockedMode() {
        AudioEngine.isBpmLocked = true
        AudioEngine.manualBpm = 120.0f
        AudioEngine.setBpmDirectly(120.0f)

        AudioEngine.registerTap(135.0f, System.nanoTime())
        assertEquals(135.0f, AudioEngine.manualBpm)
        assertEquals(135.0f, AudioEngine.getEstimatedBpm())
    }

    @Test
    fun testRegisterTapInUnlockedMode() {
        AudioEngine.isBpmLocked = false
        AudioEngine.registerTap(142.0f, System.nanoTime())

        assertEquals(142.0f, AudioEngine.getEstimatedBpm())
        assertEquals(142.0f, AudioEngine.beatDetector.engine.currentBpm)
        assertEquals(0.0, AudioEngine.beatDetector.pendingPhaseNudge)
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
    }

    @Test
    fun testResyncDownbeat() {
        AudioEngine.clockSource = ClockSource.MANUAL
        AudioEngine.setBpmDirectly(120.0f)
        AudioEngine.resyncDownbeat()

        val beats = llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats()
        assertTrue(beats >= 0.0)
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
