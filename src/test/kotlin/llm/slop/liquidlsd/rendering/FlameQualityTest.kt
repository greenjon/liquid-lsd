package llm.slop.liquidlsd.rendering

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlameQualityTest {
    private val count = FlameProbe.SIDE * FlameProbe.SIDE   // the probe's own point count: the fog test depends on it
    private val age = FlameProbe.ITERATIONS.toFloat()

    private fun cloud(age: Float = this.age, point: (Random) -> Pair<Float, Float>): FloatArray {
        val r = Random(1)
        val s = FloatArray(count * 4)
        for (i in 0 until count) {
            val (x, y) = point(r)
            s[i * 4] = x; s[i * 4 + 1] = y; s[i * 4 + 3] = age
        }
        return s
    }

    private fun verdict(s: FloatArray) = FlameQuality.analyse(s, count, FlameProbe.ITERATIONS).verdict

    @Test fun filledBlobWithStructureIsOk() {
        // a ring plus a dense core: spread unevenly over a 2-D area
        val s = cloud { r ->
            val a = r.nextFloat() * 6.2832f
            if (r.nextFloat() < 0.5f) 0.5f * r.nextFloat() * cos(a) to 0.5f * r.nextFloat() * sin(a)
            else (1f + 0.1f * r.nextFloat()) * cos(a) to (1f + 0.1f * r.nextFloat()) * sin(a)
        }
        assertEquals(FlameQuality.Verdict.OK, verdict(s))
    }

    @Test fun pointLineAndFogAreRejected() {
        assertEquals(FlameQuality.Verdict.COLLAPSED, verdict(cloud { r -> 0.3f + 0.001f * r.nextFloat() to 0.2f + 0.001f * r.nextFloat() }))
        assertEquals(FlameQuality.Verdict.LINE, verdict(cloud { r -> val t = r.nextFloat() * 2f - 1f; t to 0.5f * t }))
        assertEquals(FlameQuality.Verdict.LINE, verdict(cloud { r -> (r.nextFloat() * 2f - 1f) to 0.001f * r.nextFloat() }))
        assertEquals(FlameQuality.Verdict.FOG, verdict(cloud { r -> (r.nextFloat() * 2f - 1f) to (r.nextFloat() * 2f - 1f) }))
    }

    @Test fun reseededOrNonFinitePointsMeanDivergence() {
        assertEquals(FlameQuality.Verdict.DIVERGED, verdict(cloud(age = 3f) { r -> r.nextFloat() to r.nextFloat() }))
        assertEquals(FlameQuality.Verdict.DIVERGED, verdict(cloud { _ -> Float.NaN to 0f }))
    }

    @Test fun hugeAttractorIsExploded() {
        assertEquals(FlameQuality.Verdict.EXPLODED, verdict(cloud { r -> (r.nextFloat() - 0.5f) * 500f to (r.nextFloat() - 0.5f) * 500f * r.nextFloat() }))
    }

    @Test fun framingCentresAndFitsTheAttractor() {
        val s = cloud { r -> val a = r.nextFloat() * 6.2832f; val k = 0.2f + 0.8f * r.nextFloat(); (1.5f + 2f * k * cos(a)) to (-1f + 2f * k * sin(a)) }
        val r = FlameQuality.analyse(s, count, FlameProbe.ITERATIONS)
        assertEquals(1.5f, r.centerX, 0.15f)
        assertEquals(-1f, r.centerY, 0.15f)
        // the ~4-unit-tall attractor fills about 85% of the unit-half-height view
        assertEquals(0.85f / 2f, r.zoom, 0.08f)
        assertTrue(r.ok)
    }
}
