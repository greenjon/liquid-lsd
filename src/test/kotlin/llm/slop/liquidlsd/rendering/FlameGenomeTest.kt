package llm.slop.liquidlsd.rendering

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlameGenomeTest {
    private val params get() = FlameGenome.buildParameters()

    /** `value` is the per-frame evaluated result, so evaluate after editing base values (the source's update() does this). */
    private fun pack(p: Map<String, llm.slop.liquidlsd.parameters.ModulatableParameter>, out: FloatArray): Int {
        p.values.forEach { it.evaluate() }
        return FlameGenome.pack(p, out)
    }

    @Test fun namesAreUniqueAfterSpaceStrippingAndMacroSafe() {
        val names = params.keys
        assertEquals(names.size, names.map { it.replace(" ", "") }.toSet().size)
        assertTrue(names.none { '/' in it })
    }

    @Test fun variationIdsAreDiscreteAndExcludedFromRandomize() {
        val p = params
        val ids = p.filterKeys { it.endsWith(" Id") }
        assertEquals((FlameGenome.MAX_XF + 1) * FlameGenome.SLOTS, ids.size)
        ids.values.forEach {
            assertEquals(FlameGenome.VARIATIONS.size, it.steps)
            assertTrue(it.isRandomizeDisabled)
        }
    }

    @Test fun starterPacksThreeXformsAndIdentityFinal() {
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        val n = pack(params, out)
        assertEquals(3, n)
        assertTrue(FlameGenome.finalIsIdentity(out))
    }

    @Test fun zeroWeightXformsAreCompactedAndEmptyGenomeFallsBackToIdentity() {
        val p = params
        p["X1 Weight"]!!.baseValue = 0f
        p["X3 Weight"]!!.baseValue = 0f
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        assertEquals(1, pack(p, out))
        assertEquals(p["X2 Color"]!!.baseValue, out[1])   // X2 moved to index 0

        listOf("X1", "X2", "X3").forEach { p["$it Weight"]!!.baseValue = 0f }
        assertEquals(1, pack(p, out))
        assertEquals(1f, out[0]); assertEquals(1f, out[4]); assertEquals(1f, out[7])
    }

    @Test fun finalXformDetectsEdits() {
        val p = params
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        p["XF PostX"]!!.baseValue = 0.3f
        pack(p, out)
        assertFalse(FlameGenome.finalIsIdentity(out))
    }

    @Test fun warpScalesOnlyNonlinearSlots() {
        val p = params
        p["Warp"]!!.baseValue = 2f
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        pack(p, out)
        // X1: V1 sinusoidal 0.7 -> 1.4; V3 is linear weight 0 (unchanged)
        assertEquals(1.4f, out[16 + 1], 1e-5f)
        assertEquals(0f, out[16 + 8 + 1], 1e-6f)
    }
}
