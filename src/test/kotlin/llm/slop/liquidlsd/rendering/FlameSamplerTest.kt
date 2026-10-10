package llm.slop.liquidlsd.rendering

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlameSamplerTest {
    private val params = FlameGenome.buildParameters()
    private val from: (String) -> Float = { params[it]?.baseValue ?: 0f }

    @Test fun zeroStrengthChangesNothing() {
        val out = FlameSampler.mutate(params, from, 0f, Random(1))
        out.forEach { (name, v) -> assertEquals(from(name), v, 1e-5f, name) }
    }

    @Test fun resultsStayInRangeAndPreScalesStayContractive() {
        repeat(200) { seed ->
            val out = FlameSampler.mutate(params, from, 1f, Random(seed))
            out.forEach { (name, v) ->
                val p = params[name]!!
                assertTrue(v >= p.minClamp - 1e-5f && v <= p.maxClamp + 1e-5f, "$name=$v")
                if (" PreScale" in name) assertTrue(abs(v) <= 0.95f + 1e-5f, "$name=$v")
            }
        }
    }

    @Test fun finalAndPostAffineAreNeverMutated() {
        val out = FlameSampler.mutate(params, from, 1f, Random(7))
        assertTrue(out.keys.none { it.startsWith("XF") || " Post" in it })
    }

    @Test fun variationIdsAreWholeNumbersAndAtLeastTwoXformsStayActive() {
        repeat(300) { seed ->
            val out = FlameSampler.mutate(params, from, 1f, Random(seed))
            out.filterKeys { it.endsWith(" Id") }.values.forEach { assertEquals(it, it.toInt().toFloat()) }
            val active = (0 until FlameGenome.MAX_XF).count { (out[FlameGenome.weightKey(it)] ?: from(FlameGenome.weightKey(it))) > 0.05f }
            assertTrue(active >= 2)
        }
    }

    @Test fun highStrengthEventuallySwapsAVariationAndBirthsAnXform() {
        var swapped = false; var born = false
        for (seed in 0 until 300) {
            val out = FlameSampler.mutate(params, from, 1f, Random(seed))
            if (out.any { (k, v) -> k.endsWith(" Id") && v != from(k) && (params["${k.removeSuffix(" Id")} Weight"]!!.baseValue > 0.05f) }) swapped = true
            if ((3 until 6).any { (out[FlameGenome.weightKey(it)] ?: 0f) > 0.3f }) born = true
        }
        assertTrue(swapped && born)
    }

    // ---- random ----

    private fun activeXforms(out: Map<String, Float>) =
        (0 until FlameGenome.MAX_XF).count { (out[FlameGenome.weightKey(it)] ?: 0f) > 0.05f }

    @Test fun randomIsDeterministicPerSeedAndStaysInRange() {
        assertEquals(FlameSampler.random(params, Random(5)), FlameSampler.random(params, Random(5)))
        repeat(300) { seed ->
            FlameSampler.random(params, Random(seed)).forEach { (name, v) ->
                val p = params[name]!!
                assertTrue(v >= p.minClamp - 1e-5f && v <= p.maxClamp + 1e-5f, "$name=$v")
                if (" PreScale" in name) assertTrue(abs(v) <= 0.95f + 1e-5f, "$name=$v")
            }
        }
    }

    @Test fun randomPicksTwoToFiveXformsAndLeavesFinalAndGlobalsAlone() {
        val counts = HashSet<Int>()
        repeat(300) { seed ->
            val out = FlameSampler.random(params, Random(seed))
            counts += activeXforms(out)
            assertTrue(out.keys.none { it.startsWith("XF") })
            assertTrue(out.keys.all { it.startsWith("X") }, "only xform parameters: ${out.keys.filterNot { it.startsWith("X") }}")
        }
        assertEquals(setOf(2, 3, 4, 5), counts)
    }

    @Test fun randomSlotsAreDistinctValidAndNormalised() {
        repeat(300) { seed ->
            val out = FlameSampler.random(params, Random(seed))
            for (x in 0 until FlameGenome.MAX_XF) {
                if ((out[FlameGenome.weightKey(x)] ?: 0f) <= 0.05f) continue
                val used = (0 until FlameGenome.SLOTS).filter { (out[FlameGenome.slotWeightKey(x, it)] ?: 0f) > 0f }
                assertTrue(used.isNotEmpty() && used == (0 until used.size).toList(), "slots fill from the front")
                assertEquals(1f, used.sumOf { (out[FlameGenome.slotWeightKey(x, it)] ?: 0f).toDouble() }.toFloat(), 1e-4f)
                val ids = used.map { out[FlameGenome.slotIdKey(x, it)]!!.toInt() }
                assertEquals(ids.size, ids.toSet().size)
                ids.forEach { assertTrue(it in 0 until FlameVariations.count()) }
            }
        }
    }

    // ---- breed ----

    private val parentA: Map<String, Float> = params.mapValues { it.value.baseValue }
    private val parentB: Map<String, Float> = FlameSampler.random(params, Random(99)).let { r -> params.mapValues { r[it.key] ?: it.value.baseValue } }

    @Test fun breedWithoutMutationTakesWholeXformsFromOneParent() {
        repeat(100) { seed ->
            val child = FlameSampler.breed(params, { parentA[it]!! }, { parentB[it]!! }, 0f, Random(seed))
            for (x in 0 until FlameGenome.MAX_XF) {
                val names = params.keys.filter { it.startsWith("X${x + 1} ") && params[it]!!.randomizeBase }
                val fromA = names.all { child[it] == parentA[it] }
                val fromB = names.all { child[it] == parentB[it] }
                assertTrue(fromA || fromB, "X${x + 1} mixes parents (seed $seed)")
            }
        }
    }

    @Test fun breedUsesBothParentsKeepsTwoXformsAndStaysInRange() {
        var sawA = false; var sawB = false
        repeat(200) { seed ->
            val child = FlameSampler.breed(params, { parentA[it]!! }, { parentB[it]!! }, 0.5f, Random(seed))
            assertTrue(activeXforms(child) >= 2, "seed $seed")
            assertTrue(child.keys.none { it.startsWith("XF") })
            child.forEach { (name, v) ->
                val p = params[name]!!
                assertTrue(v >= p.minClamp - 1e-5f && v <= p.maxClamp + 1e-5f, "$name=$v")
            }
            if (child[FlameGenome.weightKey(0)] != parentB[FlameGenome.weightKey(0)]) sawA = true
            if (child[FlameGenome.weightKey(1)] != parentA[FlameGenome.weightKey(1)]) sawB = true
        }
        assertTrue(sawA && sawB)
    }

    @Test fun breedingIdenticalParentsAtZeroStrengthClones() {
        val child = FlameSampler.breed(params, { parentA[it]!! }, { parentA[it]!! }, 0f, Random(3))
        child.forEach { (n, v) -> assertEquals(parentA[n]!!, v, 1e-5f, n) }
    }
}
