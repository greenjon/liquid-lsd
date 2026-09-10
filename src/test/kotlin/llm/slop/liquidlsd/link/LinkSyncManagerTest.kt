package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.ClockSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.*

class LinkSyncManagerTest {

    @BeforeTest
    fun setUp() {
        LinkSyncManager.reset()
        AbletonLinkEngine.init(120.0)
    }

    @AfterTest
    fun tearDown() {
        LinkSyncManager.reset()
    }

    @Test
    fun testDefaultState() {
        assertEquals(SyncMode.DISABLED, LinkSyncManager.currentMode)
        assertFalse(LinkSyncManager.isLinked)
        assertEquals(0, LinkSyncManager.peersCount)
    }

    @Test
    fun testTransitionToLinkFollower() {
        LinkSyncManager.setSyncMode(SyncMode.LINK_FOLLOWER)

        assertEquals(SyncMode.LINK_FOLLOWER, LinkSyncManager.currentMode)
        assertTrue(LinkSyncManager.isLinked)
        assertEquals(ClockSource.ABLETON_LINK, AudioEngine.clockSource)
    }

    @Test
    fun testTransitionToAudioBroadcast() {
        LinkSyncManager.setSyncMode(SyncMode.AUDIO_BROADCAST)

        assertEquals(SyncMode.AUDIO_BROADCAST, LinkSyncManager.currentMode)
        assertTrue(LinkSyncManager.isLinked)
        assertEquals(ClockSource.AUDIO_TRACKER, AudioEngine.clockSource)
    }

    @Test
    fun testTransitionToDisabled() {
        LinkSyncManager.setSyncMode(SyncMode.LINK_FOLLOWER)
        assertTrue(LinkSyncManager.isLinked)

        LinkSyncManager.setSyncMode(SyncMode.DISABLED)
        assertEquals(SyncMode.DISABLED, LinkSyncManager.currentMode)
        assertFalse(LinkSyncManager.isLinked)
        assertEquals(ClockSource.AUDIO_TRACKER, AudioEngine.clockSource)
    }

    @Test
    fun testToggleSyncMode() {
        assertEquals(SyncMode.DISABLED, LinkSyncManager.currentMode)

        assertEquals(SyncMode.LINK_FOLLOWER, LinkSyncManager.toggleSyncMode())
        assertEquals(SyncMode.LINK_FOLLOWER, LinkSyncManager.currentMode)

        assertEquals(SyncMode.AUDIO_BROADCAST, LinkSyncManager.toggleSyncMode())
        assertEquals(SyncMode.AUDIO_BROADCAST, LinkSyncManager.currentMode)

        assertEquals(SyncMode.DISABLED, LinkSyncManager.toggleSyncMode())
        assertEquals(SyncMode.DISABLED, LinkSyncManager.currentMode)
    }

    @Test
    fun testAudioTempoEventSinkDispatchingInBroadcastMode() {
        val committedBpm = AtomicReference<Double?>(null)
        val alignedBeat = AtomicReference<Double?>(null)
        val alignedTimestamp = AtomicLong(0L)

        val sink = object : AudioTempoEventSink {
            override fun onTempoCommitted(bpm: Double) {
                committedBpm.set(bpm)
            }

            override fun onBeatAligned(beatTime: Double, microsecondTimestamp: Long, quantum: Double) {
                alignedBeat.set(beatTime)
                alignedTimestamp.set(microsecondTimestamp)
            }
        }

        LinkSyncManager.registerEventSink(sink)
        LinkSyncManager.setSyncMode(SyncMode.AUDIO_BROADCAST)

        // Publish sustained tempo events in AUDIO_BROADCAST mode (4 samples to pass hysteresis filter)
        repeat(4) {
            LinkSyncManager.publishTempoCommitted(126.5)
        }
        assertNotNull(committedBpm.get())
        assertEquals(126.5, committedBpm.get()!!, 0.5)

        // Publish major phase alignment event (>= 0.5 beats error)
        val expectedBeat = AbletonLinkEngine.getBeatAtTime(5000000L, 4.0)
        val majorBeat = expectedBeat + 0.8
        LinkSyncManager.publishBeatAligned(majorBeat, 5000000L, 4.0)
        assertEquals(majorBeat, alignedBeat.get())
        assertEquals(5000000L, alignedTimestamp.get())

        // Test automatic timestamp resolution when 0L passed (with major phase error >= 0.5 beats)
        val nowUs = System.nanoTime() / 1000
        val currentBeat = AbletonLinkEngine.getBeatAtTime(nowUs, 4.0)
        val majorBeat2 = currentBeat + 0.7
        LinkSyncManager.publishBeatAligned(majorBeat2, 0L, 4.0)
        assertEquals(majorBeat2, alignedBeat.get())
        assertTrue(alignedTimestamp.get() > 0L)
    }

    @Test
    fun testEventSinkSuppressionInFollowerAndDisabledModes() {
        val eventReceived = AtomicBoolean(false)

        val sink = object : AudioTempoEventSink {
            override fun onTempoCommitted(bpm: Double) {
                eventReceived.set(true)
            }

            override fun onBeatAligned(beatTime: Double, microsecondTimestamp: Long, quantum: Double) {
                eventReceived.set(true)
            }
        }

        LinkSyncManager.registerEventSink(sink)

        // 1. LINK_FOLLOWER mode
        LinkSyncManager.setSyncMode(SyncMode.LINK_FOLLOWER)
        LinkSyncManager.publishTempoCommitted(128.0)
        LinkSyncManager.publishBeatAligned(8.0)
        assertFalse(eventReceived.get())

        // 2. DISABLED mode
        LinkSyncManager.setSyncMode(SyncMode.DISABLED)
        LinkSyncManager.publishTempoCommitted(128.0)
        LinkSyncManager.publishBeatAligned(8.0)
        assertFalse(eventReceived.get())
    }

    @Test
    fun testConcurrentModeTransitions() {
        val numThreads = 10
        val iterations = 50
        val latch = CountDownLatch(numThreads)

        val modes = arrayOf(SyncMode.DISABLED, SyncMode.LINK_FOLLOWER, SyncMode.AUDIO_BROADCAST)

        for (i in 0 until numThreads) {
            thread {
                try {
                    for (j in 0 until iterations) {
                        val mode = modes[(i + j) % modes.size]
                        LinkSyncManager.setSyncMode(mode)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val finalMode = LinkSyncManager.currentMode
        assertTrue(finalMode in modes)
    }

    @Test
    fun testObservableUiStateAndTransmission() {
        LinkSyncManager.setSyncMode(SyncMode.DISABLED)
        assertFalse(LinkSyncManager.isTransmitting)

        LinkSyncManager.setSyncMode(SyncMode.AUDIO_BROADCAST)
        AbletonLinkEngine.setEnabled(true)

        // Active BPM formatting
        val bpm = LinkSyncManager.activeBpm
        val formatted = LinkSyncManager.formattedActiveBpm
        assertTrue(formatted.contains("."))
        assertEquals(String.format(java.util.Locale.US, "%.1f", bpm), formatted)

        // Confidence metrics
        val conf = LinkSyncManager.confidence
        val confPct = LinkSyncManager.confidencePercent
        assertTrue(conf in 0.0f..1.0f)
        assertTrue(confPct in 0..100)

        // Transmission state propagation to AbletonLinkEngine
        repeat(4) {
            LinkSyncManager.publishTempoCommitted(125.0)
        }
        assertEquals(125.0, AbletonLinkEngine.getTempo(), 0.5)
    }
}
