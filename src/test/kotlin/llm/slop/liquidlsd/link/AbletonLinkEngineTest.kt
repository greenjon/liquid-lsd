package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.ClockSource
import llm.slop.liquidlsd.cv.CVRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbletonLinkEngineTest {

    @BeforeTest
    fun setUp() {
        LinkSyncManager.reset()
        AbletonLinkEngine.init(120.0)
        AbletonLinkEngine.setEnabled(false)
    }

    @AfterTest
    fun tearDown() {
        LinkSyncManager.reset()
        AbletonLinkEngine.setEnabled(false)
        AudioEngine.clockSource = ClockSource.MANUAL
    }

    // --- Ableton Link Engine & Fallback ---

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

        // Enable Ableton Link in MANUAL mode
        AudioEngine.clockSource = ClockSource.MANUAL
        AbletonLinkEngine.setEnabled(true)
        AbletonLinkEngine.setTempo(124.0)

        AbletonLinkEngine.updateClockAnchor(System.nanoTime())
        val beatsLink = CVRegistry.getSynchronizedTotalBeats()
        assertTrue(beatsLink >= 0.0)

        // Adjust manual BPM directly
        AudioEngine.setBpmDirectly(130f)
        assertEquals(130f, AudioEngine.manualBpm)

        val beatsManual = CVRegistry.getSynchronizedTotalBeats()
        assertTrue(beatsManual >= 0.0)

        // Test downbeat resync
        AudioEngine.resyncDownbeat()
        val beatsResynced = CVRegistry.getSynchronizedTotalBeats()
        assertTrue(beatsResynced >= 0.0)

        // Test halve and double tempo
        AudioEngine.halveTempo()
        assertEquals(65f, AudioEngine.manualBpm)
        AudioEngine.doubleTempo()
        assertEquals(130f, AudioEngine.manualBpm)

        // Reset back to default
        AbletonLinkEngine.setEnabled(false)
        AudioEngine.clockSource = ClockSource.MANUAL
    }

    // --- Link Sync Manager Operations ---

    @Test
    fun testDefaultState() {
        assertFalse(LinkSyncManager.isLinked)
        assertEquals(0, LinkSyncManager.peersCount)
    }

    @Test
    fun testIsLinkedWhenEnabled() {
        AbletonLinkEngine.setEnabled(true)
        assertTrue(LinkSyncManager.isLinked)
        assertEquals(AbletonLinkEngine.getTempo(), LinkSyncManager.activeBpm, 0.1)
    }

    @Test
    fun testPublishTempoCommittedWhenEnabled() {
        AbletonLinkEngine.setEnabled(true)

        // Publish sustained tempo events (4 samples to pass hysteresis filter)
        repeat(4) {
            LinkSyncManager.publishTempoCommitted(126.5)
        }
        assertEquals(126.5, AbletonLinkEngine.getTempo(), 0.5)
    }

    @Test
    fun testPublishTempoCommittedSuppressedWhenDisabled() {
        AbletonLinkEngine.setEnabled(false)
        val initialBpm = AbletonLinkEngine.getTempo()

        repeat(4) {
            LinkSyncManager.publishTempoCommitted(140.0)
        }
        assertEquals(initialBpm, AbletonLinkEngine.getTempo(), 0.1)
    }

    @Test
    fun testFormattedActiveBpmAndConfidence() {
        val bpm = LinkSyncManager.activeBpm
        val formatted = LinkSyncManager.formattedActiveBpm
        assertTrue(formatted.contains("."))
        assertEquals(String.format(java.util.Locale.US, "%.1f", bpm), formatted)

        val conf = LinkSyncManager.confidence
        val confPct = LinkSyncManager.confidencePercent
        assertTrue(conf in 0.0f..1.0f)
        assertTrue(confPct in 0..100)
    }
}
