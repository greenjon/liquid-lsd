package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Genome-aware "next state" for the flame source: Mutate(A), the drift of a lineage. Generic per-parameter
 * randomize draws every value uniformly, which gives mush and divergent genomes; this perturbs the state being
 * left instead, keeping xforms contractive. [random] builds a fresh genome; Breed comes later (plan phase 5). Both
 * only propose: FlameSource runs every candidate through the quality gate ([FlameQuality]) before using it.
 */
object FlameSampler {
    /** Pick odds per variation id (index = id): favours the well-behaved ones, rare for the exotic, noisy or unstable ones. 0 = never picked (pre_blur draws nothing without a regular variation beside it).  */
    private val POOL: FloatArray = run {
        val odds = mapOf(
            "linear" to 3f, "sinusoidal" to 3f, "spherical" to 2f, "swirl" to 3f, "horseshoe" to 1f, "polar" to 1f,
            "handkerchief" to 1.5f, "heart" to 1f, "disc" to 1f, "spiral" to 1f, "hyperbolic" to 1f, "diamond" to 1f,
            "ex" to 1f, "julia" to 1.5f, "bent" to 1f, "waves" to 1f, "fisheye" to 0.7f, "popcorn" to 1f,
            "exponential" to 0.5f, "power" to 0.7f, "cosine" to 0.7f, "rings" to 1f, "fan" to 1f, "blob" to 1f,
            "pdj" to 1f, "fan2" to 0.7f, "rings2" to 0.7f, "eyefish" to 0.7f, "bubble" to 1f, "cylinder" to 1f,
            "perspective" to 0.7f, "juliaN" to 1f, "juliaScope" to 1f, "curl" to 1f, "rectangles" to 0.5f,
            "ngon" to 0.7f, "pie" to 0.4f, "radialBlur" to 0.4f, "tangent" to 0.3f, "cross" to 0.3f,
            "arch" to 0.3f, "square" to 0.3f, "noise" to 0.2f, "blur" to 0.2f, "gaussian" to 0.2f,
            "rays" to 0.2f, "blade" to 0.3f, "secant2" to 0.2f, "twintrian" to 0.2f,
            "boarders" to 0.3f, "butterfly" to 0.5f, "edisc" to 0.4f, "elliptic" to 0.5f, "foci" to 0.4f, "loonie" to 0.4f, "pre_blur" to 0f,
            "polar2" to 0.5f, "disc2" to 0.4f, "flower" to 0.3f, "conic" to 0.2f, "parabola" to 0.3f, "bent2" to 0.4f,
            "bipolar" to 0.4f, "cell" to 0.3f, "cpow" to 0.3f, "curve" to 0.5f, "escher" to 0.5f, "modulus" to 0.1f,
            "oscope" to 0.3f, "popcorn2" to 0.5f, "separation" to 0.4f, "split" to 0.2f, "splits" to 0.4f,
            "stripes" to 0.4f, "wedge" to 0.5f, "wedge_julia" to 0.4f, "wedge_sph" to 0.4f, "whorl" to 0.3f,
            "waves2" to 0.6f, "auger" to 0.4f, "flux" to 0.3f, "scry" to 0.2f, "exp" to 0.3f, "log" to 0.3f, "sin" to 0.5f, "cos" to 0.5f, "tan" to 0.3f, "sec" to 0.2f, "csc" to 0.2f,
            "cot" to 0.2f, "sinh" to 0.3f, "cosh" to 0.3f, "tanh" to 0.3f, "sech" to 0.2f, "csch" to 0.2f, "coth" to 0.2f, "hemisphere" to 0.6f
        )
        FloatArray(FlameVariations.count()) {
            val v = FlameVariations.ALL[it]
            if (!v.supported) 0f else odds[v.name] ?: error("no pool odds for ${v.name}")
        }
    }

    /** Contractivity guard: |pre scale| stays below this so the attractor does not blow up. */
    private const val MAX_PRE_SCALE = 0.95f

    /** Widest range that scales a Mutate jitter (the parameter ranges used to top out here). */
    private const val JITTER_RANGE_CAP = 4f

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
    ): Map<String, Float> =
        mutateReal(params, { FlameUnits.real(it, from(it)) }, strength, random).mapValues { FlameUnits.stored(it.key, it.value) }

    /** [mutate] in the real units of [FlameUnits]: [from] and the result are not normalised. */
    private fun mutateReal(
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
            if (name.startsWith("XF") || " Post" in name || name in FlameGenome.MORPH_CAMERA) continue   // the camera is framed per candidate, not jittered
            // jitter scales with the range, capped so the wide ranges that admit imported flames do not make drift wild
            val lo = FlameUnits.realMin(name, p.minClamp); val hi = FlameUnits.realMax(name, p.maxClamp)
            val range = minOf(hi - lo, JITTER_RANGE_CAP)
            var v = from(name) + gaussian(random) * s * 0.2f * range
            v = if (p.isAngle) wrapAngle(v) else v.coerceIn(lo, hi)
            if (name.contains(" PreScale")) {   // keep contractive, but never shrink an already larger (imported) scale
                val limit = maxOf(MAX_PRE_SCALE, abs(from(name)))
                v = v.coerceIn(-limit, limit)
            }
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

    /**
     * Crossover of two parents: xform slot i comes whole from A or from B (never mixed per parameter, so each xform
     * stays coherent), at least two xforms stay active, then [mutate] jitters the child by [strength]. Final xform,
     * camera, tone and modifiers are not part of the genome proposal, as for [random].
     * @param a, b value of a parameter in each parent
     * @return values for every morph-enabled xform parameter (mutated ones included)
     */
    fun breed(
        params: Map<String, ModulatableParameter>,
        a: (String) -> Float,
        b: (String) -> Float,
        strength: Float,
        random: Random
    ): Map<String, Float> {
        val prefixes = Array(FlameGenome.MAX_XF) { "X${it + 1} " }
        val names = Array(FlameGenome.MAX_XF) { x -> params.filter { (n, p) -> p.randomizeBase && n.startsWith(prefixes[x]) }.keys.toList() }
        fun active(f: (String) -> Float, x: Int) = FlameUnits.real(FlameGenome.weightKey(x), f(FlameGenome.weightKey(x))) > 0.05f
        val fromB = BooleanArray(FlameGenome.MAX_XF) { random.nextBoolean() }
        fun childActive() = (0 until FlameGenome.MAX_XF).count { active(if (fromB[it]) b else a, it) }
        // too few active xforms: switch slots to whichever parent is active there until two are
        for (x in 0 until FlameGenome.MAX_XF) {
            if (childActive() >= 2) break
            val here = if (fromB[x]) b else a
            val other = if (fromB[x]) a else b
            if (!active(here, x) && active(other, x)) fromB[x] = !fromB[x]
        }
        val child = HashMap<String, Float>()
        for (x in 0 until FlameGenome.MAX_XF) for (n in names[x]) child[n] = (if (fromB[x]) b else a)(n)
        val mutated = mutate(params, { child[it] ?: a(it) }, strength, random)
        child.putAll(mutated)
        return child
    }

    /** Active xform count for [random]: 2..5, three being the most common. */
    private val COUNT_ODDS = floatArrayOf(0.25f, 0.4f, 0.25f, 0.1f)

    /**
     * A fresh genome: 2-5 active xforms with contractive pre-affines, mostly identity post-affines, 1-3 distinct
     * variations per xform from the weighted pool (slot weights sum to 1) and colours spread over the palette.
     * Unused xforms get weight 0 and are otherwise left alone. The final xform, camera and tone parameters are not
     * part of the genome proposal. Contractivity is only a bias here, since the variations can expand freely:
     * the quality gate decides.
     * @return values for the morph-enabled xform parameters (variation ids as whole numbers)
     */
    fun random(params: Map<String, ModulatableParameter>, random: Random): Map<String, Float> {
        val r = random
        val out = HashMap<String, Float>()
        fun range(lo: Float, hi: Float) = lo + (hi - lo) * r.nextFloat()
        fun set(name: String, v: Float) {
            val p = params[name] ?: return
            out[name] = if (p.isAngle) wrapAngle(v) else FlameUnits.stored(name, v.coerceIn(FlameUnits.realMin(name, p.minClamp), FlameUnits.realMax(name, p.maxClamp)))
        }

        var pick = r.nextFloat() * COUNT_ODDS.sum()
        var count = 2
        for (i in COUNT_ODDS.indices) { pick -= COUNT_ODDS[i]; if (pick < 0f) { count = i + 2; break } }
        val colourOffset = r.nextFloat()

        for (x in 0 until FlameGenome.MAX_XF) {
            if (x >= count) { set(FlameGenome.weightKey(x), 0f); continue }
            val k = FlameGenome.keysFor(x)
            set(k.weight, range(0.4f, 1f))
            set(k.color, (colourOffset + (x + range(0f, 0.5f)) / count) % 1f)
            set(k.speed, range(0.3f, 0.8f))

            val scale = range(0.35f, 0.85f)
            set(k.preRot, range(-PI.toFloat(), PI.toFloat()))
            set(k.preSx, (scale * range(0.85f, 1.15f)).coerceAtMost(MAX_PRE_SCALE) * (if (r.nextFloat() < 0.2f) -1f else 1f))
            set(k.preSy, (scale * range(0.85f, 1.15f)).coerceAtMost(MAX_PRE_SCALE))
            set(k.preShear, if (r.nextFloat() < 0.3f) range(-0.3f, 0.3f) else 0f)
            set(k.preX, range(-0.8f, 0.8f)); set(k.preY, range(-0.8f, 0.8f))

            val post = r.nextFloat() < 0.2f
            set(k.postRot, if (post) range(-1f, 1f) else 0f)
            set(k.postSx, 1f); set(k.postSy, 1f); set(k.postShear, 0f)
            set(k.postX, if (post) range(-0.3f, 0.3f) else 0f); set(k.postY, if (post) range(-0.3f, 0.3f) else 0f)

            val n = r.nextFloat().let { if (it < 0.45f) 1 else if (it < 0.85f) 2 else 3 }
            val ids = LinkedHashSet<Int>()
            while (ids.size < n) ids.add(pickVariation(r))
            val raw = FloatArray(n) { range(0.3f, 1f) }
            val sum = raw.sum()
            for (j in 0 until FlameGenome.SLOTS) {
                val id = ids.elementAtOrNull(j)
                set(k.slotId[j], (id ?: 0).toFloat())
                set(k.slotWeight[j], if (id != null) raw[j] / sum else 0f)
                for (q in 0 until FlameVariations.PARAMS) {
                    set(k.slotParam[j][q], if (id != null) (0.5f + gaussian(r) * 0.15f) else 0.5f)
                }
            }
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
