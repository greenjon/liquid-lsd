package llm.slop.liquidlsd.audio

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownbeatDetectorTest {

    /**
     * Feeds a steady 120 BPM groove in blocks of 1/20 beat. The first block of every beat carries an onset;
     * the beat at [accentPosition] of the bar (in the unshifted count) carries [accent] times as much.
     * Applies the detector's shifts the way the audio engine does and returns the running total.
     */
    private class Groove(
        val detector: DownbeatDetector,
        val beatsPerBar: Int = 4,
        var accentPosition: Int = 2,
        var accent: Float = 1.6f,
        val noise: Float = 0.05f,
        seed: Int = 1
    ) {
        private val random = Random(seed)
        private var step = 0L
        var jump = 0
        var shifted = 0
        var shifts = 0
        private val perBeat = 20

        fun bars(n: Int) {
            repeat(n * beatsPerBar * perBeat) {
                val beatIndex = step / perBeat
                val first = step % perBeat == 0L
                val position = Math.floorMod(beatIndex, beatsPerBar.toLong()).toInt()
                val gain = (if (position == accentPosition) accent else 1f) * (1f + noise * (random.nextFloat() - 0.5f))
                val hit = if (first) gain else 0f
                val tail = if (first) 0f else 0.1f * gain
                step++
                val beatsEnd = step.toDouble() / perBeat + shifted + jump
                val shift = detector.process(beatsEnd, 1.0 / perBeat, hit * 0.05f + tail * 0.01f, hit * 0.1f, 0.2f * gain)
                if (shift != 0) { shifted += shift; shifts++ }
            }
        }

        /** Where the accented beat sits in the shifted count: 0 means the detector put it on the bar line. */
        fun accentedPosition(): Int = Math.floorMod(accentPosition + shifted, beatsPerBar)
    }

    @Test
    fun movesTheBarLineOntoTheAccentedBeat() {
        val detector = DownbeatDetector()
        val groove = Groove(detector)
        groove.bars(12)
        assertEquals(0, groove.accentedPosition())
        assertTrue(detector.confidence > 0.5f, "confidence ${detector.confidence}")
    }

    @Test
    fun settlesAndStaysPut() {
        val detector = DownbeatDetector()
        val groove = Groove(detector)
        groove.bars(12)
        val settled = groove.shifts
        groove.bars(60)
        assertEquals(settled, groove.shifts)
        assertEquals(0, groove.accentedPosition())
    }

    @Test
    fun alreadyAlignedNeedsNoShift() {
        val detector = DownbeatDetector()
        val groove = Groove(detector, accentPosition = 0)
        groove.bars(20)
        assertEquals(0, groove.shifts)
    }

    @Test
    fun worksForOtherBarLengths() {
        for (bpb in intArrayOf(3, 6, 7)) {
            val detector = DownbeatDetector().also { it.beatsPerBar = bpb }
            val groove = Groove(detector, beatsPerBar = bpb, accentPosition = bpb - 1)
            groove.bars(16)
            assertEquals(0, groove.accentedPosition(), "beats per bar $bpb")
        }
    }

    @Test
    fun aFlatGrooveNeverMovesTheBarLine() {
        val detector = DownbeatDetector()
        val groove = Groove(detector, accent = 1f, noise = 0.1f)
        groove.bars(100)
        assertEquals(0, groove.shifts)
        assertTrue(detector.confidence < 0.5f, "confidence ${detector.confidence}")
    }

    @Test
    fun noAnswerBeforeAFewBarsHaveBeenHeard() {
        val detector = DownbeatDetector()
        val groove = Groove(detector)
        groove.bars(3)
        assertEquals(0, groove.shifts)
    }

    @Test
    fun aManualClaimIsLeftAloneForSixteenBars() {
        val detector = DownbeatDetector()
        val groove = Groove(detector, accentPosition = 0)
        groove.bars(8)
        detector.claimDownbeat()
        groove.accentPosition = 2   // the music now disagrees with the claim
        groove.bars(DownbeatDetector.LOCK_BARS - 1)
        assertEquals(0, groove.shifts)
        assertTrue(detector.isLocked)
    }

    @Test
    fun aClearContraryAccentWinsOnceTheClaimLockEnds() {
        val detector = DownbeatDetector()
        val groove = Groove(detector, accentPosition = 0)
        groove.bars(8)
        detector.claimDownbeat()
        groove.accentPosition = 2
        groove.bars(DownbeatDetector.LOCK_BARS + 24)
        assertEquals(0, groove.accentedPosition())
    }

    @Test
    fun aJumpInTheBeatCountDropsWhatWasGathered() {
        val detector = DownbeatDetector()
        val groove = Groove(detector)
        groove.bars(3)
        groove.jump += 7 // a resync moved the count
        groove.bars(3)
        assertEquals(0, groove.shifts) // neither half is enough to decide on its own
    }

    @Test
    fun shiftToBarLineTakesTheShorterWayAndForwardOnATie() {
        assertEquals(0, DownbeatDetector.shiftToBarLine(0, 4))
        assertEquals(-1, DownbeatDetector.shiftToBarLine(1, 4))
        assertEquals(2, DownbeatDetector.shiftToBarLine(2, 4))
        assertEquals(1, DownbeatDetector.shiftToBarLine(3, 4))
        assertEquals(-1, DownbeatDetector.shiftToBarLine(1, 3))
        assertEquals(1, DownbeatDetector.shiftToBarLine(2, 3))
        assertEquals(1, DownbeatDetector.shiftToBarLine(-1, 4))
        for (bpb in 2..8) for (p in 0 until bpb) {
            assertEquals(0, Math.floorMod(p + DownbeatDetector.shiftToBarLine(p, bpb), bpb), "p=$p bpb=$bpb")
        }
    }
}
