package llm.slop.liquidlsd.link

import kotlin.test.*

class LinkSyncManagerTest {

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
    }

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
