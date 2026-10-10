package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Genome-aware "next state" for the flame source: Mutate(A), the drift of a lineage. Generic per-parameter
 * randomize draws every value uniformly, which gives mush and divergent genomes; this perturbs the state being
 * left instead, keeping xforms contractive. Random and Breed come later (plan phase 5).
 */
object FlameSampler {
    /** Pick odds per variation id (index = id): favours the well-behaved ones, rare for the exotic ones. */
    private val POOL = floatArrayOf(3f, 3f, 2f, 3f, 1f, 1f, 1.5f, 1f, 1f, 1f, 1f, 1f)

    /** Contractivity guard: |pre scale| stays below this so the attractor does not blow up. */
    private const val MAX_PRE_SCALE = 0.95f

    private fun gaussian(r: Random): Float {
        val u = r.nextFloat().coerceAtLeast(1e-6f)
        return sqrt(-2f * ln(u)) * cos(2f * PI.toFloat() * r.nextFloat())
    }

    private fun pickVariation(r: Random): Int {
        var x = r.nextFloat() * POOL.sum()
        for (i in POOL.indices) { x -= POOL[i]; if (x < 0f) return i }
        return 0
    }

    private fun wrapAngle(a: Float): Float {
        val twoPi = 2f * PI.toFloat()
        var x = a
        while (x < -PI.toFloat()) x += twoPi
        while (x > PI.toFloat()) x -= twoPi
        return x
    }

    /**
     * @param params the flame's parameters (ranges and the morph flag are read from them)
     * @param from value of a parameter in the state being left (A)
     * @param strength 0..1, the Mutation parameter: jitter size and the odds of id swaps / xform birth / death
     * @return new values for the morph-enabled parameters that were changed (variation ids as whole numbers)
     */
    fun mutate(
        params: Map<String, ModulatableParameter>,
        from: (String) -> Float,
        strength: Float,
        random: Random
    ): Map<String, Float> {
        val s = strength.coerceIn(0f, 1f)
        val out = HashMap<String, Float>()

        // 1. gaussian jitter on continuous xform parameters (final xform and post-affine stay as they are)
        for ((name, p) in params) {
            if (!p.randomizeBase || p.steps != null) continue
            if (name.startsWith("XF") || " Post" in name) continue
            val range = p.maxClamp - p.minClamp
            var v = from(name) + gaussian(random) * s * 0.2f * range
            v = if (p.isAngle) wrapAngle(v) else v.coerceIn(p.minClamp, p.maxClamp)
            if (name.contains(" PreScale")) v = v.coerceIn(-MAX_PRE_SCALE, MAX_PRE_SCALE)
            out[name] = v
        }

        fun cur(name: String) = out[name] ?: from(name)

        // 2. occasional variation swap in an audible slot
        for (x in 0 until FlameGenome.MAX_XF) {
            for (j in 0 until FlameGenome.SLOTS) {
                if (cur(FlameGenome.slotWeightKey(x, j)) > 0.05f && random.nextFloat() < 0.5f * s) {
                    out[FlameGenome.slotIdKey(x, j)] = pickVariation(random).toFloat()
                } else {
                    out[FlameGenome.slotIdKey(x, j)] = from(FlameGenome.slotIdKey(x, j)).toInt().toFloat()
                }
            }
        }

        // 3. occasional xform birth (empty one) or death (when more than two are active)
        if (random.nextFloat() < 0.3f * s) {
            val x = random.nextInt(FlameGenome.MAX_XF)
            val active = (0 until FlameGenome.MAX_XF).count { cur(FlameGenome.weightKey(it)) > 0.05f }
            if (cur(FlameGenome.weightKey(x)) < 0.05f) birth(x, out, random)
            else if (active > 2) out[FlameGenome.weightKey(x)] = 0f
        }
        return out
    }

    private fun birth(x: Int, out: MutableMap<String, Float>, r: Random) {
        val n = FlameGenome.xfParamNames(x)   // weight, color, speed, preRot, preSx, preSy, preShear, preX, preY, ids, weights
        val sc = 0.4f + 0.3f * r.nextFloat()
        val flip = if (r.nextFloat() < 0.2f) -1f else 1f
        out[n[0]] = 0.4f + 0.6f * r.nextFloat()
        out[n[1]] = r.nextFloat()
        out[n[3]] = (r.nextFloat() * 2f - 1f) * PI.toFloat()
        out[n[4]] = sc * flip
        out[n[5]] = sc
        out[n[6]] = 0f
        out[n[7]] = (r.nextFloat() * 2f - 1f) * 0.6f
        out[n[8]] = (r.nextFloat() * 2f - 1f) * 0.6f
        for (j in 0 until FlameGenome.SLOTS) {
            out[FlameGenome.slotIdKey(x, j)] = (if (j == 0) pickVariation(r) else 0).toFloat()
            out[FlameGenome.slotWeightKey(x, j)] = if (j == 0) 1f else 0f
        }
    }
}
