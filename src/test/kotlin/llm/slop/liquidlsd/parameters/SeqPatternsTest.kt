package llm.slop.liquidlsd.parameters

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeqPatternsTest {
    private val base = List(32) { (it % 5) * 0.25f }

    @Test fun invertUnipolarOnlyTouchesActiveSteps() {
        val out = SeqPatterns.invert(base, 4, bipolar = false)
        assertEquals(1f - base[1], out[1])
        assertEquals(base[4], out[4])
        assertEquals(32, out.size)
    }

    @Test fun invertBipolarNegates() {
        assertEquals(-base[2], SeqPatterns.invert(base, 8, bipolar = true)[2])
    }

    @Test fun rotateWrapsWithinActiveLength() {
        val steps = List(32) { it.toFloat() / 10f }
        val right = SeqPatterns.rotate(steps, 4, 1)
        assertEquals(listOf(steps[3], steps[0], steps[1], steps[2]), right.take(4))
        assertEquals(steps[4], right[4])
        assertEquals(listOf(steps[1], steps[2], steps[3], steps[0]), SeqPatterns.rotate(steps, 4, -1).take(4))
    }

    @Test fun randomizeStaysInRangeAndSnapped() {
        val uni = SeqPatterns.randomize(base, 16, false, Random(1)).take(16)
        assertTrue(uni.all { it in 0f..1f })
        val bi = SeqPatterns.randomize(base, 16, true, Random(1)).take(16)
        assertTrue(bi.all { it in -1f..1f } && bi.any { it < 0f })
    }

    @Test fun rampSpansRange() {
        val up = SeqPatterns.shape(base, 8, false, SeqPatterns.Shape.RAMP_UP)
        assertEquals(0f, up[0]); assertEquals(1f, up[7])
        val bi = SeqPatterns.shape(base, 8, true, SeqPatterns.Shape.RAMP_UP)
        assertEquals(-1f, bi[0]); assertEquals(1f, bi[7])
    }

    @Test fun euclidThreeOfEightIsTresillo() {
        val out = SeqPatterns.euclid(base, 8, 3).take(8)
        assertEquals(listOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f), out)
    }

    @Test fun euclidHitsClamped() {
        assertTrue(SeqPatterns.euclid(base, 4, 99).take(4).all { it == 1f })
        assertTrue(SeqPatterns.euclid(base, 4, 0).take(4).all { it == 0f })
    }

    @Test fun paddedFillsToThirtyTwo() {
        assertEquals(32, SeqPatterns.padded(emptyList()).size)
    }
}
