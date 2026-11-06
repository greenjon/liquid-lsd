package llm.slop.liquidlsd.link

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkLeaderTest {
    private val session = FakeLinkBackend()
    private val leader = LinkLeader { session }
    private var now = 0.0
    private var localBeats = 0.0

    /** Runs [seconds] of frames with the app's clock at [bpm]. */
    private fun run(
        seconds: Double,
        bpm: Double,
        steady: Boolean = false,
        tempoConfident: Boolean = true,
        phaseConfident: Boolean = true
    ) {
        val dt = 1.0 / 60.0
        repeat((seconds / dt).toInt()) {
            now += dt
            localBeats += dt * bpm / 60.0
            session.advance(dt)
            leader.tick(now, localBeats, bpm, steady, tempoConfident, phaseConfident, 4, 4.0)
        }
    }

    @Test
    fun `an unconfident tracker writes nothing to the session`() {
        session.bpm = 128.0
        localBeats = 1.3
        run(10.0, 90.0, tempoConfident = false, phaseConfident = false)
        assertTrue(session.tempoWrites.isEmpty())
        assertTrue(session.forces.isEmpty())
        assertFalse(leader.isPublishing)
    }

    @Test
    fun `the first confident tempo goes out`() {
        run(0.5, 137.0)
        assertEquals(137.0, session.bpm, 0.5)
        assertTrue(leader.isPublishing)
    }

    @Test
    fun `tracker wobble under the step stays off the network`() {
        run(1.0, 128.0)
        val writes = session.tempoWrites.size
        var wobble = 0
        repeat(40) {
            run(0.25, 128.0 + if (wobble++ % 2 == 0) 0.3 else -0.3)
        }
        assertEquals(writes, session.tempoWrites.size)
    }

    @Test
    fun `a tracked tempo change waits about four beats`() {
        run(1.0, 120.0)
        run(1.0, 140.0) // 2.3 beats
        assertEquals(120.0, session.bpm, 2.5, "a brief wrong reading must not reach the peers")
        run(1.5, 140.0)
        assertEquals(140.0, session.bpm, 3.0)
    }

    @Test
    fun `a brief wrong reading never goes out`() {
        run(1.0, 174.0)
        run(0.5, 116.0) // a 2:3 flicker
        run(5.0, 174.0)
        assertTrue(session.tempoWrites.all { abs(it - 174.0) < 4.0 }, "wrote ${session.tempoWrites}")
    }

    @Test
    fun `a hand-set tempo goes out straight away`() {
        run(1.0, 120.0, steady = true)
        run(0.3, 90.0, steady = true)
        assertEquals(90.0, session.bpm, 2.0)
    }

    @Test
    fun `a small phase error slides into place with no force`() {
        run(1.0, 120.0)
        localBeats += 0.2
        run(20.0, 120.0)
        assertTrue(session.forces.isEmpty())
        assertTrue(abs(session.phaseErrorTo(localBeats)) < 0.02, "still ${session.phaseErrorTo(localBeats)} off")
        assertEquals(120.0, session.bpm, 0.15, "the nudge must end")
    }

    @Test
    fun `the nudge never warps the session more than two percent`() {
        run(1.0, 120.0)
        localBeats += 0.24
        val before = session.tempoWrites.size
        run(5.0, 120.0)
        assertTrue(session.tempoWrites.drop(before).all { abs(it - 120.0) <= 120.0 * LinkLeader.MAX_NUDGE + 1e-9 })
    }

    @Test
    fun `a large phase error is forced only once it has held`() {
        run(1.0, 120.0)
        localBeats += 1.0 // a new bar line
        run(1.5, 120.0)
        assertTrue(session.forces.isEmpty(), "too soon")
        run(1.0, 120.0)
        assertEquals(1, session.forces.size)
        assertTrue(abs(session.phaseErrorTo(localBeats)) < 0.02)
    }

    @Test
    fun `forces are rate limited`() {
        run(1.0, 120.0)
        repeat(5) {
            localBeats += 0.7
            run(2.5, 120.0)
        }
        assertEquals(2, session.forces.size, "a new error every 2.5 s, but one force every 8 s at most")
    }

    @Test
    fun `alone in a session the beat moves at once`() {
        session.peers = 0
        run(0.1, 120.0)
        localBeats += 1.5
        run(0.1, 120.0)
        assertFalse(session.forces.isEmpty())
        assertTrue(abs(session.phaseErrorTo(localBeats)) < 0.02)
    }

    @Test
    fun `without a phase lock only the tempo goes out`() {
        localBeats = 1.4
        run(5.0, 133.0, phaseConfident = false)
        assertTrue(session.forces.isEmpty())
        assertEquals(133.0, session.bpm, 0.01)
    }

    @Test
    fun `a peer's tempo change is put back, without a fight`() {
        run(1.0, 120.0)
        session.peerSetsTempo(100.0)
        val writes = session.tempoWrites.size
        run(0.1, 120.0)
        session.peerSetsTempo(100.0)
        run(0.1, 120.0)
        assertTrue(session.tempoWrites.size - writes <= 1, "no more than one write a second to a contested session")
        run(1.5, 120.0)
        assertEquals(120.0, session.bpm, 1.0)
    }

    @Test
    fun `bars line up when the quantum is whole bars`() {
        val eight = LinkLeader { session }
        session.forceBeatAtTime(0.0, session.nowUs, 8.0)
        var t = 0.0
        val beats = 5.0 // beat 2 of a 4-beat bar, wherever the session is
        repeat(240) {
            t += 1.0 / 60.0
            session.advance(1.0 / 60.0)
            eight.tick(t, beats + t * 2.0, 120.0, false, true, true, 4, 8.0)
        }
        assertEquals(0.0, session.phaseErrorTo(beats + t * 2.0), 0.02)
        val mod4 = (session.getBeatAtTime(session.nowUs, 8.0) - (beats + t * 2.0)) % 4.0
        assertEquals(0.0, abs(mod4), 0.02)
    }

    @Test
    fun `reset forgets the tempo`() {
        run(1.0, 120.0)
        leader.reset()
        assertFalse(leader.isPublishing)
        run(0.3, 150.0)
        assertEquals(150.0, session.bpm, 3.1, "the first confident tempo after a reset goes straight out")
    }
}
