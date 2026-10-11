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

    @Test fun variationIdsAreDiscreteAndTakePartInTheMorph() {
        val p = params
        val ids = p.filterKeys { it.endsWith(" Id") }
        assertEquals((FlameGenome.MAX_XF + 1) * FlameGenome.SLOTS, ids.size)
        ids.values.forEach {
            assertEquals(FlameGenome.VARIATIONS.size, it.steps)
            assertTrue(it.randomizeBase)
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
        p["Warp"]!!.baseValue = FlameUnits.stored("Warp", 2f)
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        pack(p, out)
        // X1: V1 sinusoidal 0.7 -> 1.4; V3 is linear weight 0 (unchanged)
        assertEquals(1.4f, out[16 + 1], 1e-5f)
        assertEquals(0f, out[16 + 2 * FlameGenome.VEC4_PER_SLOT * 4 + 1], 1e-6f)
    }

    private class Lookup(val a: Map<llm.slop.liquidlsd.parameters.ModulatableParameter, Float>,
                         val b: Map<llm.slop.liquidlsd.parameters.ModulatableParameter, Float>,
                         override val t: Float) : FlameMorphLookup {
        override fun a(p: llm.slop.liquidlsd.parameters.ModulatableParameter) = a[p] ?: p.baseValue
        override fun b(p: llm.slop.liquidlsd.parameters.ModulatableParameter) = b[p] ?: p.baseValue
    }

    @Test fun morphCrossWeightsVariationSlotsByT() {
        val p = params
        val id = p["X1 V1 Id"]!!; val w = p["X1 V1 Weight"]!!
        // A: sinusoidal(1) weight 0.7 (starter). B: spherical(2) weight 1.0
        val s = { r: Float -> FlameUnits.stored("X1 V1 Weight", r) }   // weights are stored normalised
        val look = { t: Float -> Lookup(mapOf(id to 1f, w to s(0.7f)), mapOf(id to 2f, w to s(1.0f)), t) }
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        w.baseValue = s(0.7f + (1.0f - 0.7f) * 0.25f)   // the controller lerps the base value; pack adds only the modulation on top
        p.values.forEach { it.evaluate() }
        FlameGenome.pack(p, out, look(0.25f))
        assertEquals(1f, out[16]); assertEquals(0.7f * 0.75f, out[17], 1e-5f)
        assertEquals(2f, out[18]); assertEquals(1.0f * 0.25f, out[19], 1e-5f)
        // same id at both ends collapses to one entry with the lerped weight
        w.baseValue = s(0.6f); w.evaluate()
        FlameGenome.pack(p, out, Lookup(mapOf(id to 1f, w to s(0.2f)), mapOf(id to 1f, w to s(1.0f)), 0.5f))
        assertEquals(1f, out[16]); assertEquals(0.6f, out[17], 1e-5f)
        assertEquals(0f, out[19])
    }
}

class FlameSlotParamPackingTest {
    @Test fun slotParametersArePackedInTheVariationsOwnUnits() {
        val p = FlameGenome.buildParameters()
        p["X1 V1 Id"]!!.baseValue = FlameVariations.NAMES.indexOf("ngon").toFloat()
        p["X1 V1 B"]!!.baseValue = 1f        // sides at its max
        p.values.forEach { it.evaluate() }
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        FlameGenome.pack(p, out)
        val so = 16   // X1 slot 0
        assertEquals(2f, out[so + 4], 1e-5f)       // power default
        assertEquals(32f, out[so + 5], 1e-5f)      // sides max
        assertEquals(0f, out[so + 8], 0f)          // B params empty outside a morph
    }
}
