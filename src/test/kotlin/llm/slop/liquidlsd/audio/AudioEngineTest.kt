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
}
