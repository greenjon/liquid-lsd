package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.ClockSource
import llm.slop.liquidlsd.cv.CVRegistry
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The app's side of scripts/link_peer_check.py, which runs another Link client in its own process and compares
 * the two logs. Skips unless that script started it.
 */
class LinkExternalPeerReportTest {
    private lateinit var log: java.io.PrintWriter
    private lateinit var poke: File
    private var stage = ""

    private fun run(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            LinkSyncManager.tick()
            val beats = CVRegistry.getSynchronizedTotalBeats()
            log.println("$stage ${System.currentTimeMillis() / 1000.0} $beats ${AudioEngine.getEstimatedBpm()}")
            Thread.sleep(8)
        }
    }

    /** Knocks the app's own beat away from the session's, as a new track or a SNAP would. */
    private fun shiftBeat(beats: Double) {
        CVRegistry.alignBeatPhase(CVRegistry.getSynchronizedTotalBeats() + beats, AudioEngine.manualBpm, System.nanoTime())
    }

    /** Asks the peer to set this tempo. */
    private fun peerSets(bpm: Double) {
        val tmp = File(poke.path + ".tmp")
        tmp.writeText(bpm.toString())
        tmp.renameTo(poke)
    }

    @Test
    fun report() {
        val logPath = System.getenv("LSD_LINK_APP_LOG")
        assumeTrue(logPath != null, "LSD_LINK_APP_LOG not set")
        poke = File(System.getenv("LSD_LINK_POKE"))
        log = File(logPath!!).printWriter()

        val backend = NativeJniLinkBackend()
        assertTrue(backend.init(120.0), "no link_jni library")
        AudioEngine.clockSource = ClockSource.MANUAL
        AudioEngine.setBeatsPerBarAndLinkQuantum(4)
        LinkSyncManager.reset()
        AbletonLinkEngine.installBackend(backend)
        AudioEngine.clockSource = ClockSource.LINK
        try {
            val deadline = System.currentTimeMillis() + 15_000
            while (backend.getNumPeers() < 1 && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertTrue(backend.getNumPeers() >= 1, "never found the peer")

            stage = "settle"; run(2000)
            stage = "follow"; run(8000)
            peerSets(100.0)
            stage = "follow-change"; run(1000)
            stage = "follow-after-change"; run(5000)

            AudioEngine.manualBpm = 126f
            AudioEngine.clockSource = ClockSource.MANUAL
            AudioEngine.setBpmDirectly(126f)
            stage = "lead-settling"; run(10_000)
            stage = "lead"; run(8000)
            peerSets(150.0)
            stage = "lead-putback"; run(3000)
            stage = "lead-after-putback"; run(6000)

            shiftBeat(0.15)
            stage = "slide-first-500ms"; run(500)
            stage = "slide-to-8s"; run(7500)
            stage = "slid"; run(3000)

            shiftBeat(1.0)
            stage = "large-before-force"; run(1500)
            stage = "large-around-force"; run(1500)
            stage = "large-forced"; run(4000)
        } finally {
            log.close()
            AudioEngine.clockSource = ClockSource.MANUAL
            LinkSyncManager.reset()
            AbletonLinkEngine.shutdown()
        }
    }
}
