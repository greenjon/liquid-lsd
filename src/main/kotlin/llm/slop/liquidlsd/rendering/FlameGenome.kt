package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Parameter layout and shader packing for the fractal-flame source (.planning/flame-source-plan.md, "Genome as
 * parameters"). The genome is an ordinary parameter map built here instead of in meta.json, because the variation
 * ids are discrete and the map is large. Names are the preset keys: "X2 PreRot", "X2 V1 Id", "XF V2 Weight".
 * Unused xforms have weight 0; the final xform (XF) has no weight, colour or Spin/Breathe handling.
 */
object FlameGenome {
    const val MAX_XF = 6
    const val SLOTS = 3
    /** Index of the final xform in the packed array (after the [MAX_XF] regular slots). */
    const val FINAL_INDEX = MAX_XF
    /** vec4s per xform in the `uXf` uniform: info, preMatrix, translations, postMatrix, then [SLOTS] variation slots. */
    const val VEC4_PER_XF = 4 + SLOTS
    const val PACKED_FLOATS = (MAX_XF + 1) * VEC4_PER_XF * 4

    /** Variation names; the index is the id used by the GLSL `vari()` switch. Keep in step with [FlameEngine]. */
    val VARIATIONS = listOf(
        "linear", "sinusoidal", "spherical", "swirl", "horseshoe", "polar",
        "handkerchief", "heart", "disc", "spiral", "hyperbolic", "diamond"
    )

    private fun xfName(i: Int) = if (i == FINAL_INDEX) "XF" else "X${i + 1}"
    private fun slotKey(xf: Int, slot: Int, field: String) = "${xfName(xf)} V${slot + 1} $field"
    private fun key(xf: Int, field: String) = "${xfName(xf)} $field"

    /** Pre-built keys so the per-frame pack does not allocate strings. */
    private class XfKeys(i: Int) {
        val weight = key(i, "Weight"); val color = key(i, "Color"); val speed = key(i, "ColorSpeed")
        val preRot = key(i, "PreRot"); val preSx = key(i, "PreScaleX"); val preSy = key(i, "PreScaleY")
        val preShear = key(i, "PreShear"); val preX = key(i, "PreX"); val preY = key(i, "PreY")
        val postRot = key(i, "PostRot"); val postSx = key(i, "PostScaleX"); val postSy = key(i, "PostScaleY")
        val postShear = key(i, "PostShear"); val postX = key(i, "PostX"); val postY = key(i, "PostY")
        val slotId = Array(SLOTS) { slotKey(i, it, "Id") }
        val slotWeight = Array(SLOTS) { slotKey(i, it, "Weight") }
    }
    private val keys = Array(MAX_XF + 1) { XfKeys(it) }

    /** Starter flame: three xforms, two variations each. */
    private class Starter(
        val weight: Float, val color: Float, val rot: Float, val sx: Float, val sy: Float, val shear: Float,
        val tx: Float, val ty: Float, val v: List<Pair<Int, Float>>
    )

    private val STARTER = listOf(
        Starter(1.0f, 0.00f, 0.35f, 0.65f, 0.65f, 0.0f, 0.45f, 0.10f, listOf(1 to 0.7f, 3 to 0.5f)),
        Starter(1.0f, 0.45f, -1.0f, 0.60f, 0.60f, 0.0f, -0.35f, 0.40f, listOf(2 to 0.35f, 0 to 0.65f)),
        Starter(0.8f, 1.00f, 2.10f, 0.55f, 0.50f, 0.2f, 0.00f, -0.55f, listOf(4 to 0.4f, 1 to 0.6f))
    )

    private const val COLOR_SPEED = 0.6f

    private fun param(v: Float, min: Float, max: Float, angle: Boolean = false) =
        ModulatableParameter(baseValue = v, minClamp = min, maxClamp = max, explicitIsAngle = angle)

    /** Variation ids are structural: generic per-parameter randomize would produce mush (see the plan's sampler hook). */
    private fun idParam(v: Int) = ModulatableParameter(
        baseValue = v.toFloat(), minClamp = 0f, maxClamp = (VARIATIONS.size - 1).toFloat(),
        isRandomizeDisabled = true, steps = VARIATIONS.size, labels = VARIATIONS
    )

    private fun global(m: LinkedHashMap<String, ModulatableParameter>) {
        // Camera
        m["Zoom"] = param(0.45f, 0.05f, 4f)
        m["Rotation"] = param(0f, -PI.toFloat(), PI.toFloat(), angle = true)
        m["CenterX"] = param(0f, -2f, 2f)
        m["CenterY"] = param(0f, -2f, 2f)
        // Tone
        m["Exposure"] = param(2f, 0.01f, 100f)
        m["Brightness"] = param(1f, 0f, 4f)
        m["Gamma"] = param(2.2f, 1f, 4f)
        m["Vibrancy"] = param(0.7f, 0f, 1f)
        m["Toe"] = param(0.7f, 0f, 5f)
        m["Decay"] = param(0f, 0f, 0.98f)
        m["Sharpness"] = param(3f, 0.5f, 8f)
        m["Palette"] = param(0f, 0f, 1f)
        // Quality (device-level in the plan; parameters for now, to be moved with the governor)
        m["Points"] = param(1f, 0.05f, 1f)
        m["Passes"] = param(4f, 1f, 16f)
        m["Kernel"] = param(3f, 1f, 8f)
        // Performance handles that move every xform at once
        m["Spin"] = param(0f, -PI.toFloat(), PI.toFloat(), angle = true)
        m["Breathe"] = param(1f, 0.5f, 1.5f)
        m["Warp"] = param(1f, 0f, 3f)
        m["Drift"] = param(0f, -0.5f, 0.5f)
    }

    private fun xform(m: LinkedHashMap<String, ModulatableParameter>, i: Int) {
        val k = keys[i]
        val s = STARTER.getOrNull(i)
        val isFinal = i == FINAL_INDEX
        if (!isFinal) {
            m[k.weight] = param(s?.weight ?: 0f, 0f, 2f)
            m[k.color] = param(s?.color ?: 0f, 0f, 1f)
            m[k.speed] = param(COLOR_SPEED, 0f, 1f)
        }
        m[k.preRot] = param(s?.rot ?: 0f, -PI.toFloat(), PI.toFloat(), angle = true)
        m[k.preSx] = param(s?.sx ?: if (isFinal) 1f else 0.6f, -2f, 2f)
        m[k.preSy] = param(s?.sy ?: if (isFinal) 1f else 0.6f, -2f, 2f)
        m[k.preShear] = param(s?.shear ?: 0f, -1f, 1f)
        m[k.preX] = param(s?.tx ?: 0f, -2f, 2f)
        m[k.preY] = param(s?.ty ?: 0f, -2f, 2f)
        m[k.postRot] = param(0f, -PI.toFloat(), PI.toFloat(), angle = true)
        m[k.postSx] = param(1f, -2f, 2f)
        m[k.postSy] = param(1f, -2f, 2f)
        m[k.postShear] = param(0f, -1f, 1f)
        m[k.postX] = param(0f, -2f, 2f)
        m[k.postY] = param(0f, -2f, 2f)
        for (j in 0 until SLOTS) {
            val v = s?.v?.getOrNull(j)
            m[k.slotId[j]] = idParam(v?.first ?: 0)
            m[k.slotWeight[j]] = param(v?.second ?: if (j == 0) 1f else 0f, 0f, 2f)
        }
    }

    fun buildParameters(): LinkedHashMap<String, ModulatableParameter> {
        val m = LinkedHashMap<String, ModulatableParameter>()
        global(m)
        for (i in 0..MAX_XF) xform(m, i)
        return m
    }

    /** Row-major 2x2 for `R(rot) * [[sx, shear], [0, sy]]`, written to out[o..o+3] as (a, b, c, d). */
    private fun matrix(rot: Float, sx: Float, sy: Float, shear: Float, out: FloatArray, o: Int) {
        val c = cos(rot); val s = sin(rot)
        out[o] = c * sx; out[o + 1] = c * shear - s * sy; out[o + 2] = s * sx; out[o + 3] = s * shear + c * sy
    }

    /**
     * Packs the current (modulated) genome for the shader. Active xforms (weight > 0) are compacted to the front;
     * the final xform always goes at [FINAL_INDEX]. Spin / Breathe / Warp / Drift are applied here.
     * Layout per xform, 4 floats per vec4: [weight, colour, colourSpeed, 0], [pre a b c d],
     * [preX, preY, postX, postY], [post a b c d], then per slot [id, weight, 0, 0].
     * @return number of active xforms (>= 1; an empty genome becomes one identity xform)
     */
    fun pack(params: Map<String, ModulatableParameter>, out: FloatArray): Int {
        fun v(name: String, def: Float) = params[name]?.value ?: def
        val spin = v("Spin", 0f); val breathe = v("Breathe", 1f); val warp = v("Warp", 1f); val drift = v("Drift", 0f)
        var n = 0
        for (i in 0 until MAX_XF) {
            val k = keys[i]
            val w = v(k.weight, 0f)
            if (w <= 1e-4f) continue
            packXf(params, k, out, n * VEC4_PER_XF * 4, w, spin * (if (n % 2 == 0) 1f else -1f) * (1f + 0.3f * n), breathe, warp, drift)
            n++
        }
        if (n == 0) {
            java.util.Arrays.fill(out, 0, VEC4_PER_XF * 4, 0f)
            out[0] = 1f; out[4] = 1f; out[7] = 1f; out[12] = 1f; out[15] = 1f   // weight 1, identity pre and post
            out[VEC4_PER_XF * 4 - SLOTS * 4 + 1] = 1f                            // slot 0 = linear, weight 1
            n = 1
        }
        packXf(params, keys[FINAL_INDEX], out, FINAL_INDEX * VEC4_PER_XF * 4, 1f, 0f, 1f, 1f, 0f)
        return n
    }

    private fun packXf(
        params: Map<String, ModulatableParameter>, k: XfKeys, out: FloatArray, o: Int,
        weight: Float, spin: Float, breathe: Float, warp: Float, drift: Float
    ) {
        fun v(name: String, def: Float) = params[name]?.value ?: def
        out[o] = weight
        out[o + 1] = v(k.color, 0f)
        out[o + 2] = (v(k.speed, COLOR_SPEED) + drift).coerceIn(0f, 1f)
        out[o + 3] = 0f
        matrix(v(k.preRot, 0f) + spin, v(k.preSx, 1f) * breathe, v(k.preSy, 1f) * breathe, v(k.preShear, 0f), out, o + 4)
        out[o + 8] = v(k.preX, 0f); out[o + 9] = v(k.preY, 0f); out[o + 10] = v(k.postX, 0f); out[o + 11] = v(k.postY, 0f)
        matrix(v(k.postRot, 0f), v(k.postSx, 1f), v(k.postSy, 1f), v(k.postShear, 0f), out, o + 12)
        for (j in 0 until SLOTS) {
            val so = o + 16 + j * 4
            val id = v(k.slotId[j], 0f).toInt().coerceIn(0, VARIATIONS.size - 1)
            out[so] = id.toFloat()
            out[so + 1] = v(k.slotWeight[j], 0f) * (if (id == 0) 1f else warp)
            out[so + 2] = 0f; out[so + 3] = 0f
        }
    }

    /** True when the packed final xform is a no-op (identity affines, only a unit linear slot), so the splat pass can skip it. */
    fun finalIsIdentity(packed: FloatArray): Boolean {
        val o = FINAL_INDEX * VEC4_PER_XF * 4
        fun near(a: Float, b: Float) = abs(a - b) < 1e-5f
        val ident = floatArrayOf(1f, 0f, 0f, 1f)
        for (i in 0 until 4) if (!near(packed[o + 4 + i], ident[i]) || !near(packed[o + 12 + i], ident[i])) return false
        for (i in 8..11) if (!near(packed[o + i], 0f)) return false
        var linear = 0f
        for (j in 0 until SLOTS) {
            val so = o + 16 + j * 4
            if (near(packed[so + 1], 0f)) continue
            if (packed[so].toInt() != 0) return false
            linear += packed[so + 1]
        }
        if (!near(linear, 1f)) return false
        return true
    }
}
