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
    private val session = FakeLinkBackend(bpm = 128.0)

    @BeforeTest
    fun setUp() {
        AudioEngine.clockSource = ClockSource.MANUAL
        AudioEngine.manualBpm = 120f
        LinkSyncManager.reset()
        AbletonLinkEngine.installBackend(session)
    }

    @AfterTest
    fun tearDown() {
        AudioEngine.clockSource = ClockSource.MANUAL
        LinkSyncManager.reset()
        AbletonLinkEngine.shutdown()
    }

    @Test
    fun `a missing library leaves Link off rather than faking a session`() {
        val noOp = NoOpLinkBackend()
        assertTrue(noOp.init(125.0))
        noOp.setEnabled(true)
        assertFalse(noOp.isEnabled())
        assertEquals(0, noOp.getNumPeers())
    }

    @Test
    fun `the role comes from the clock source`() {
        assertEquals(LinkRole.OFF, LinkSyncManager.role)
        AudioEngine.setLinkEnabled(true)
        assertEquals(LinkRole.LEAD, LinkSyncManager.role)
        AudioEngine.clockSource = ClockSource.AUDIO_TRACKER
        assertEquals(LinkRole.LEAD, LinkSyncManager.role)
        AudioEngine.clockSource = ClockSource.LINK
        assertEquals(LinkRole.FOLLOW, LinkSyncManager.role)
    }

    @Test
    fun `choosing the LINK source joins the session`() {
        AudioEngine.clockSource = ClockSource.LINK
        assertTrue(AbletonLinkEngine.isEnabled)
        assertTrue(session.sessionOn)
    }

    @Test
    fun `turning Link off leaves the LINK source`() {
        AudioEngine.clockSource = ClockSource.LINK
        AudioEngine.setLinkEnabled(false)
        assertEquals(ClockSource.MANUAL, AudioEngine.clockSource)
        assertFalse(AbletonLinkEngine.isEnabled)
    }

    @Test
    fun `leaving the LINK source keeps the session, which we then lead`() {
        AudioEngine.clockSource = ClockSource.LINK
        AudioEngine.clockSource = ClockSource.MANUAL
        assertTrue(AbletonLinkEngine.isEnabled)
        assertEquals(LinkRole.LEAD, LinkSyncManager.role)
    }

    @Test
    fun `following runs the clock from the session and writes nothing`() {
        AudioEngine.clockSource = ClockSource.LINK
        session.advance(10.0)
        LinkSyncManager.tick()
        assertEquals(128.0, AbletonLinkEngine.latestTempo, 1e-9)
        assertEquals(session.getBeatAtTime(session.nowUs, 4.0), AbletonLinkEngine.latestBeat, 1e-9)
        assertEquals(session.getBeatAtTime(session.nowUs, 4.0), CVRegistry.getSynchronizedTotalBeats(), 0.05)
        assertTrue(session.tempoWrites.isEmpty())
        assertTrue(session.forces.isEmpty())
    }

    @Test
    fun `following tracks a peer's tempo change`() {
        AudioEngine.clockSource = ClockSource.LINK
        session.peerSetsTempo(90.0)
        LinkSyncManager.tick()
        assertEquals(90.0, AbletonLinkEngine.latestTempo, 1e-9)
        assertEquals(90f, AudioEngine.getEstimatedBpm(), "the BPM readout, with no audio thread running")
        assertEquals(90f, AudioEngine.manualBpm)
        assertTrue(session.tempoWrites.isEmpty())
    }

    @Test
    fun `an unlocked tracker does not lead`() {
        AudioEngine.clockSource = ClockSource.AUDIO_TRACKER
        AudioEngine.setLinkEnabled(true)
        repeat(30) { LinkSyncManager.tick() }
        assertTrue(session.tempoWrites.isEmpty())
        assertTrue(session.forces.isEmpty())
        assertFalse(LinkSyncManager.isPublishing)
    }

    @Test
    fun `leading by hand sends the manual tempo`() {
        AudioEngine.setLinkEnabled(true)
        AudioEngine.setBpmDirectly(133f)
        LinkSyncManager.tick()
        assertEquals(133.0, session.bpm, 133.0 * LinkLeader.MAX_NUDGE + 0.01, "the tempo, plus at most the phase nudge")
        assertTrue(LinkSyncManager.isPublishing)
        assertEquals(133f, AudioEngine.manualBpm)
    }

    @Test
    fun `following, the tempo controls change the session`() {
        AudioEngine.clockSource = ClockSource.LINK
        AudioEngine.nudgeTempo(2f)
        assertEquals(130.0, session.bpm, 0.01)
        AudioEngine.halveTempo()
        assertEquals(65.0, session.bpm, 0.01)
        AudioEngine.doubleTempo()
        assertEquals(130.0, session.bpm, 0.01)
    }

    @Test
    fun `following, SNAP and TAP only ask the session`() {
        AudioEngine.clockSource = ClockSource.LINK
        LinkSyncManager.tick()
        AudioEngine.resyncDownbeat()
        assertEquals(1, session.requests.size)
        AudioEngine.registerTap(100f)
        assertEquals(2, session.requests.size)
        assertEquals(100.0, session.bpm, 0.01)
        assertTrue(session.forces.isEmpty())
    }

    @Test
    fun `leading, SNAP moves the app's own clock and not through a request`() {
        AudioEngine.setLinkEnabled(true)
        AudioEngine.resyncDownbeat()
        assertTrue(session.requests.isEmpty())
    }

    @Test
    fun `shutdown leaves the session`() {
        AudioEngine.setLinkEnabled(true)
        AbletonLinkEngine.shutdown()
        assertTrue(session.closed)
        assertFalse(AbletonLinkEngine.isEnabled)
    }

    @Test
    fun `active tempo reads the app's clock`() {
        val formatted = LinkSyncManager.formattedActiveBpm
        assertTrue(formatted.contains("."))
        assertEquals(String.format(java.util.Locale.US, "%.1f", LinkSyncManager.activeBpm), formatted)
        assertTrue(LinkSyncManager.confidence in 0.0f..1.0f)
        assertTrue(LinkSyncManager.confidencePercent in 0..100)
    }
}
