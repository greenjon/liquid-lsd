package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.ClockSource
import llm.slop.liquidlsd.cv.CVRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A real Link session: two instances of the bundled library, finding each other over the network. Skips without
 * the library (scripts/build_link_native.sh) or where the network does not let the two meet.
 */
class LinkNativeSessionTest {
    private val peer = NativeJniLinkBackend()
    private val app = NativeJniLinkBackend()

    @BeforeEach
    fun meet() {
        assumeTrue(peer.init(120.0), "no link_jni library")
        assumeTrue(app.init(120.0), "no link_jni library")
        peer.setEnabled(true)
        AudioEngine.clockSource = ClockSource.MANUAL
        LinkSyncManager.reset()
        AbletonLinkEngine.installBackend(app)
        AudioEngine.setLinkEnabled(true)
        assumeTrue(waitFor(10_000) { app.getNumPeers() == 1 && peer.getNumPeers() == 1 }, "the instances did not find each other")
    }

    @AfterEach
    fun leave() {
        AudioEngine.clockSource = ClockSource.MANUAL
        LinkSyncManager.reset()
        AbletonLinkEngine.shutdown()
        peer.close()
        app.close()
    }

    private fun waitFor(ms: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    private fun wrap(value: Double, span: Double) = value - span * floor(value / span + 0.5)

    private fun peerBeat() = peer.getBeatAtTime(peer.clockMicros(), 4.0)

    /** Runs frames for [ms], as the render loop would. */
    private fun frames(ms: Long, each: () -> Unit = {}) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            LinkSyncManager.tick()
            each()
            Thread.sleep(8)
        }
    }

    @Test
    fun `following takes the peer's tempo and lands on its beat`() {
        AudioEngine.clockSource = ClockSource.LINK
        peer.setTempo(137.0)
        assertTrue(waitFor(3000) { abs(app.getTempo() - 137.0) < 0.01 }, "tempo did not arrive")

        frames(500)
        val worst = (0 until 100).maxOf {
            LinkSyncManager.tick()
            val error = abs(wrap(CVRegistry.getSynchronizedTotalBeats() - peerBeat(), 4.0))
            Thread.sleep(8)
            error
        }
        // The beat clock goes through System.nanoTime while the peer reads Link's clock. 137 BPM: 0.02 beat = 9 ms.
        assertTrue(worst < 0.02, "the app's beat was $worst beats from the peer's")
        assertEquals(137.0, peer.getTempo(), 0.01, "following must not write to the session")
    }

    @Test
    fun `leading sets the peer's tempo and slides its beat onto ours`() {
        AudioEngine.manualBpm = 126f
        AudioEngine.setBpmDirectly(126f)
        frames(500)
        assertTrue(waitFor(3000) { LinkSyncManager.tick(); abs(peer.getTempo() - 126.0) < 126.0 * LinkLeader.MAX_NUDGE + 0.01 }, "tempo did not reach the peer: ${peer.getTempo()}")

        // Past the force hold and the slide: the peer's whole bar, not only its beat, is on ours.
        frames(6000)
        val error = abs(wrap(CVRegistry.getSynchronizedTotalBeats() - peerBeat(), 4.0))
        assertTrue(error < 0.03, "the peer's bar was $error beats from ours")
        assertEquals(126.0, peer.getTempo(), 0.2, "the nudge should have finished")
    }

    @Test
    fun `leading puts back a tempo another peer changed`() {
        AudioEngine.setBpmDirectly(110f)
        frames(1000)
        peer.setTempo(150.0)
        assertTrue(waitFor(4000) { LinkSyncManager.tick(); abs(peer.getTempo() - 110.0) < 2.5 }, "peer stayed at ${peer.getTempo()}")
    }
}
