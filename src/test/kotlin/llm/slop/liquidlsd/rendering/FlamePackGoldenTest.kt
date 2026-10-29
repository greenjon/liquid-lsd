package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The allocation-free [FlameGenome.pack] must write exactly what the previous implementation wrote. [Legacy] is that
 * implementation verbatim (per-call [FlameUnits.real] by name, local lambdas), compared bit for bit on random genomes,
 * with and without a morph, including slots whose variation id differs between the ends.
 */
class FlamePackGoldenTest {
    private class Lookup(val a: Map<ModulatableParameter, Float>, val b: Map<ModulatableParameter, Float>, override val t: Float) : FlameMorphLookup {
        override fun a(p: ModulatableParameter) = a[p] ?: p.baseValue
        override fun b(p: ModulatableParameter) = b[p] ?: p.baseValue
    }

    private fun genome(seed: Int): LinkedHashMap<String, ModulatableParameter> {
        val p = FlameGenome.buildParameters()
        FlameSampler.random(p, Random(seed)).forEach { (k, v) -> p[k]?.baseValue = v }
        val r = Random(seed * 31 + 7)
        // the handles and the tone go through their unit maps too
        for (name in listOf("Spin", "Breathe", "Warp", "Drift")) p[name]!!.let { it.baseValue = it.minClamp + r.nextFloat() * (it.maxClamp - it.minClamp) }
        p.values.forEach { it.evaluate() }
        return p
    }

    private fun assertSame(expected: FloatArray, actual: FloatArray, what: String) {
        for (i in expected.indices) assertEquals(expected[i].toRawBits(), actual[i].toRawBits(), "$what: float $i (${expected[i]} vs ${actual[i]})")
    }

    @Test fun packWithoutMorphIsBitIdentical() {
        for (seed in 0 until 24) {
            val p = genome(seed)
            val old = FloatArray(FlameGenome.PACKED_FLOATS); val new = FloatArray(FlameGenome.PACKED_FLOATS)
            assertEquals(Legacy.pack(p, old), FlameGenome.pack(p, new))
            assertSame(old, new, "seed $seed")
        }
        // the starter and the empty genome (identity fallback)
        val starter = FlameGenome.buildParameters().also { m -> m.values.forEach { it.evaluate() } }
        val o1 = FloatArray(FlameGenome.PACKED_FLOATS); val n1 = FloatArray(FlameGenome.PACKED_FLOATS)
        assertEquals(Legacy.pack(starter, o1), FlameGenome.pack(starter, n1)); assertSame(o1, n1, "starter")
        for (i in 0 until FlameGenome.MAX_XF) starter[FlameGenome.weightKey(i)]!!.let { it.baseValue = 0f; it.evaluate() }
        assertEquals(Legacy.pack(starter, o1), FlameGenome.pack(starter, n1)); assertSame(o1, n1, "empty")
    }

    @Test fun packWhileMorphingIsBitIdentical() {
        var mismatched = 0
        for (seed in 0 until 24) {
            val p = genome(seed)
            val a = FlameSampler.random(p, Random(1000 + seed)); val b = FlameSampler.random(p, Random(2000 + seed))
            val am = p.entries.filter { it.key in a }.associate { it.value to a[it.key]!! }
            val bm = p.entries.filter { it.key in b }.associate { it.value to b[it.key]!! }
            for (t in floatArrayOf(0f, 0.3f, 0.5f, 0.85f, 1f)) {
                val look = Lookup(am, bm, t)
                val old = FloatArray(FlameGenome.PACKED_FLOATS); val new = FloatArray(FlameGenome.PACKED_FLOATS)
                assertEquals(Legacy.pack(p, old, look), FlameGenome.pack(p, new, look))
                assertSame(old, new, "seed $seed t $t")
                for (x in 0 until FlameGenome.MAX_XF) for (j in 0 until FlameGenome.SLOTS) {
                    val so = x * FlameGenome.VEC4_PER_XF * 4 + 16 + j * FlameGenome.VEC4_PER_SLOT * 4
                    if (new[so] != new[so + 2] && new[so + 3] != 0f) mismatched++
                }
            }
        }
        assertTrue(mismatched > 0, "the cross-weighted (two-id) slot path was exercised")
    }

    @Test fun packAllocatesNothingPerFrame() {
        val bean = java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean ?: return
        if (!bean.isThreadAllocatedMemorySupported) return
        val p = genome(3)
        val look = Lookup(p.values.associateWith { it.baseValue }, p.values.associateWith { it.baseValue * 0.5f }, 0.4f)
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        val tid = Thread.currentThread().id
        fun measure(pack: (FlameMorphLookup?) -> Unit): Long {
            repeat(2000) { pack(null); pack(look) }   // warm up (class init, JIT)
            val before = bean.getThreadAllocatedBytes(tid)
            repeat(500) { pack(null); pack(look) }
            return bean.getThreadAllocatedBytes(tid) - before
        }
        // A MockK test earlier in the same JVM instruments ModulatableParameter for good, and its advice allocates on
        // every `value` read; then there is nothing to measure here (run this class alone to check).
        val probe = p.getValue("Zoom")
        var sink = 0f
        if (measure { sink += probe.value } > 1024) return
        val bytes = measure { FlameGenome.pack(p, out, it) }
        // the old pack allocated a few kilobytes per call (regex matches, bound references, lambdas, boxed Floats)
        assertTrue(bytes < 4096, "1000 packs allocated $bytes bytes ($sink)")
    }

    /** The pack as it was before it was made allocation-free (2026-10-29). Do not "fix" this copy. */
    private object Legacy {
        const val COLOR_SPEED = 0.6f

        private fun matrix(rot: Float, sx: Float, sy: Float, shear: Float, out: FloatArray, o: Int) {
            val c = cos(rot); val s = sin(rot)
            out[o] = c * sx; out[o + 1] = c * shear - s * sy; out[o + 2] = s * sx; out[o + 3] = s * shear + c * sy
        }

        fun pack(params: Map<String, ModulatableParameter>, out: FloatArray, morph: FlameMorphLookup? = null): Int {
            fun v(name: String, def: Float) = params[name]?.let { FlameUnits.real(name, it.value) } ?: def
            val spin = v("Spin", 0f); val breathe = v("Breathe", 1f); val warp = v("Warp", 1f); val drift = v("Drift", 0f)
            var n = 0
            for (i in 0 until FlameGenome.MAX_XF) {
                val k = FlameGenome.keysFor(i)
                val w = v(k.weight, 0f)
                if (w <= 1e-4f) continue
                packXf(params, k, out, n * FlameGenome.VEC4_PER_XF * 4, w, spin * (if (n % 2 == 0) 1f else -1f) * (1f + 0.3f * n), breathe, warp, drift, morph)
                n++
            }
            if (n == 0) {
                java.util.Arrays.fill(out, 0, FlameGenome.VEC4_PER_XF * 4, 0f)
                out[0] = 1f; out[4] = 1f; out[7] = 1f; out[12] = 1f; out[15] = 1f
                out[16 + 1] = 1f
                n = 1
            }
            packXf(params, FlameGenome.keysFor(FlameGenome.FINAL_INDEX), out, FlameGenome.FINAL_INDEX * FlameGenome.VEC4_PER_XF * 4, 1f, 0f, 1f, 1f, 0f, morph)
            return n
        }

        private fun packXf(
            params: Map<String, ModulatableParameter>, k: FlameGenome.XfKeys, out: FloatArray, o: Int,
            weight: Float, spin: Float, breathe: Float, warp: Float, drift: Float, morph: FlameMorphLookup?
        ) {
            fun v(name: String, def: Float) = params[name]?.let { FlameUnits.real(name, it.value) } ?: def
            out[o] = weight
            out[o + 1] = v(k.color, 0f)
            out[o + 2] = (v(k.speed, COLOR_SPEED) + drift).coerceIn(0f, 1f)
            out[o + 3] = 0f
            matrix(v(k.preRot, 0f) + spin, v(k.preSx, 1f) * breathe, v(k.preSy, 1f) * breathe, v(k.preShear, 0f), out, o + 4)
            out[o + 8] = v(k.preX, 0f); out[o + 9] = v(k.preY, 0f); out[o + 10] = v(k.postX, 0f); out[o + 11] = v(k.postY, 0f)
            matrix(v(k.postRot, 0f), v(k.postSx, 1f), v(k.postSy, 1f), v(k.postShear, 0f), out, o + 12)
            for (j in 0 until FlameGenome.SLOTS) packSlot(params, k, j, out, o + 16 + j * FlameGenome.VEC4_PER_SLOT * 4, warp, morph)
        }

        private fun warped(id: Int, w: Float, warp: Float) = w * (if (id == 0) 1f else warp)

        private fun packSlot(
            params: Map<String, ModulatableParameter>, k: FlameGenome.XfKeys, j: Int, out: FloatArray, so: Int,
            warp: Float, morph: FlameMorphLookup?
        ) {
            val idP = params[k.slotId[j]]
            val wP = params[k.slotWeight[j]]
            val maxId = FlameGenome.VARIATIONS.size - 1
            fun value(n: Int) = params[k.slotParam[j][n]]?.value ?: 0.5f
            fun writeParams(o: Int, id: Int, norm: (Int) -> Float) {
                for (n in 0 until FlameVariations.PARAMS) out[o + n] = FlameVariations.real(id, n, norm(n))
            }
            if (morph == null || idP == null || wP == null) {
                val id = (idP?.value ?: 0f).toInt().coerceIn(0, maxId)
                out[so] = id.toFloat(); out[so + 1] = warped(id, wP?.let { FlameUnits.real(k.slotWeight[j], it.value) } ?: 0f, warp)
                out[so + 2] = 0f; out[so + 3] = 0f
                writeParams(so + 4, id, ::value)
                java.util.Arrays.fill(out, so + 8, so + 12, 0f)
                return
            }
            val t = morph.t.coerceIn(0f, 1f)
            val idA = (morph.a(idP) + 0.5f).toInt().coerceIn(0, maxId)
            val idB = (morph.b(idP) + 0.5f).toInt().coerceIn(0, maxId)
            val wk = k.slotWeight[j]
            val wa = FlameUnits.real(wk, morph.a(wP)); val wb = FlameUnits.real(wk, morph.b(wP))
            val delta = FlameUnits.real(wk, wP.value) - (wa + (wb - wa) * t)
            val wA = (wa + delta) * (1f - t)
            val wB = (wb + delta) * t
            if (idA == idB) {
                out[so] = idA.toFloat(); out[so + 1] = warped(idA, wA + wB, warp)
                out[so + 2] = 0f; out[so + 3] = 0f
                writeParams(so + 4, idA, ::value)
                java.util.Arrays.fill(out, so + 8, so + 12, 0f)
            } else {
                out[so] = idA.toFloat(); out[so + 1] = warped(idA, wA, warp)
                out[so + 2] = idB.toFloat(); out[so + 3] = warped(idB, wB, warp)
                fun end(side: (ModulatableParameter) -> Float, n: Int): Float {
                    val pp = params[k.slotParam[j][n]] ?: return 0.5f
                    val pa = morph.a(pp); val pb = morph.b(pp)
                    return side(pp) + (pp.value - (pa + (pb - pa) * t))
                }
                writeParams(so + 4, idA) { end(morph::a, it) }
                writeParams(so + 8, idB) { end(morph::b, it) }
            }
        }
    }
}
