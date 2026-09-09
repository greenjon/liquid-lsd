package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.ClockSource
import llm.slop.liquidlsd.cv.CVRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbletonLinkEngineTest {

    @Test
    fun testNoOpBackendFallback() {
        val backend = NoOpLinkBackend()
        assertTrue(backend.init(125.0))
        assertEquals("Disabled / No-Op", backend.name)
        assertEquals(0, backend.getNumPeers())
        assertEquals(125.0, backend.getTempo())

        backend.setTempo(130.0)
        assertEquals(130.0, backend.getTempo())

        val nowUs = System.nanoTime() / 1000
        val beat1 = backend.getBeatAtTime(nowUs, 4.0)
        val phase1 = backend.getPhaseAtTime(nowUs, 4.0)
        assertTrue(beat1 >= 0.0)
        assertTrue(phase1 in 0.0..4.0)

        backend.close()
    }

    @Test
    fun testAbletonLinkEngineInitializationAndState() {
        AbletonLinkEngine.init(120.0)
        AbletonLinkEngine.quantum = 4.0
        assertEquals(4.0, AbletonLinkEngine.quantum)

        AbletonLinkEngine.setEnabled(true)
        assertTrue(AbletonLinkEngine.isEnabled)

        AbletonLinkEngine.setTempo(128.0)
        assertEquals(128.0, AbletonLinkEngine.getTempo(), 0.01)

        AbletonLinkEngine.setStartStopSyncEnabled(true)
        assertTrue(AbletonLinkEngine.isStartStopSyncEnabled())

        AbletonLinkEngine.setEnabled(false)
        assertFalse(AbletonLinkEngine.isEnabled)
    }

    @Test
    fun testClockSourceTransitions() {
        // Start in AUDIO_TRACKER mode
        AudioEngine.clockSource = ClockSource.AUDIO_TRACKER
        assertEquals(ClockSource.AUDIO_TRACKER, AudioEngine.clockSource)

        // Transition to ABLETON_LINK
        AudioEngine.clockSource = ClockSource.ABLETON_LINK
        AbletonLinkEngine.setEnabled(true)
        AbletonLinkEngine.setTempo(124.0)

        AbletonLinkEngine.updateClockAnchor(System.nanoTime())
        val beatsLink = CVRegistry.getSynchronizedTotalBeats()
        assertTrue(beatsLink >= 0.0)

        // Transition to MANUAL_TAP
        AudioEngine.clockSource = ClockSource.MANUAL_TAP
        AudioEngine.setBpmDirectly(130f)
        assertEquals(130f, AudioEngine.manualBpm)

        val beatsManual = CVRegistry.getSynchronizedTotalBeats()
        assertTrue(beatsManual >= 0.0)

        // Reset back to default
        AudioEngine.clockSource = ClockSource.AUDIO_TRACKER
    }
}
