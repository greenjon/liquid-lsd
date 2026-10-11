package llm.slop.liquidlsd.rendering

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlameViabilityTest {
    private val params = FlameGenome.buildParameters()

    /** Stored values: the starter, with [over] (real units) laid on top. */
    private fun check(over: Map<String, Float>): FlameViability.Result =
        FlameViability.check { name -> over[name]?.let { FlameUnits.stored(name, it) } ?: params[name]!!.defaultValue }

    @Test fun starterIsViable() {
        assertTrue(check(emptyMap()).ok)
    }

    @Test fun randomGenomesMostlyPass() {
        val r = Random(7)
        val ok = (0 until 200).count { i ->
            val c = FlameSampler.random(params, r)
            FlameViability.check { c[it] ?: params[it]!!.defaultValue }.ok
        }
        assertTrue(ok > 150, "only $ok of 200 random genomes were viable")
    }

    @Test fun dominantXformIsRejected() {
        val k = FlameGenome.keysFor(0)
        val others = (1 until FlameGenome.MAX_XF).associate { FlameGenome.weightKey(it) to 0.01f }
        assertFalse(check(others + (k.weight to 1f)).ok)
    }

    @Test fun singleActiveXformIsRejected() {
        assertFalse(check((1 until FlameGenome.MAX_XF).associate { FlameGenome.weightKey(it) to 0f }).ok)
    }

    @Test fun flatAffinesAreRejected() {
        val flat = (0 until FlameGenome.MAX_XF).associate { FlameGenome.keysFor(it).preSy to 0.0f }
        assertFalse(check(flat).ok)
    }

    @Test fun singularValueOfKnownMatrices() {
        assertEquals(2f, FlameViability.largestSingular(2f, 0f, 1f), 1e-5f)
        assertEquals(1f, FlameViability.largestSingular(1f, 0f, 1f), 1e-5f)
        // [[1, 1], [0, 1]] has largest singular value (1 + sqrt 5) / 2
        assertEquals(1.618034f, FlameViability.largestSingular(1f, 1f, 1f), 1e-4f)
    }
}
