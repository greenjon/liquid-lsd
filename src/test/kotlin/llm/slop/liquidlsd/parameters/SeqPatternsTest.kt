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

    @Test fun glideHoldsThenSlides() {
        assertEquals(0.2f, SeqPatterns.glide(0.2f, 0.8f, 0.4f, 0.5f, false))
        assertEquals(0.5f, SeqPatterns.glide(0.2f, 0.8f, 0.75f, 0.5f, false), 1e-5f)
        assertEquals(0.8f, SeqPatterns.glide(0.2f, 0.8f, 1.0f, 0.5f, true), 1e-5f)
        assertEquals(0.2f, SeqPatterns.glide(0.2f, 0.8f, 0.99f, 1.0f, false)) // fully held: never glides
    }

    @Test fun snapUsesGivenGridAndFreeWhenZero() {
        assertEquals(0.25f, SeqPatterns.snap(0.27f, 0f, 1f, 0.25f))
        assertEquals(0.27f, SeqPatterns.snap(0.27f, 0f, 1f, 0f))
        assertEquals(0f, SeqPatterns.snap(-0.3f, 0f, 1f, 0.05f))
    }

    @Test fun euclidRotationShiftsHitsLater() {
        val plain = SeqPatterns.euclid(base, 8, 3).take(8)
        val rotated = SeqPatterns.euclid(base, 8, 3, 1).take(8)
        assertEquals(plain.takeLast(1) + plain.take(7), rotated)
    }

    @Test fun modulatorRandomizeOnlyRollsPatternWhenFlagged() {
        val steps = List(32) { 0.5f }
        val off = CvModulator(sourceId = "seq", seqSteps = steps).randomizeActiveValues(Random(3))
        assertEquals(steps, off.seqSteps)
        val on = CvModulator(sourceId = "seq", seqSteps = steps, seqStepCount = 8, randomizeSeqSteps = true)
            .randomizeActiveValues(Random(3), seqBipolar = false)
        assertTrue(on.seqSteps.take(8).any { it != 0.5f })
        assertEquals(0.5f, on.seqSteps[20]) // beyond the length is untouched
        assertTrue(on.seqSteps.all { it in 0f..1f })
    }
}
