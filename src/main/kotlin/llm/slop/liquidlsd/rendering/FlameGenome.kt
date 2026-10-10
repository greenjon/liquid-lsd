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
    /** vec4s per variation slot: [idA, wA, idB, wB], A's four parameters, B's four parameters. */
    const val VEC4_PER_SLOT = 3
    /** vec4s per xform in the `uXf` uniform: info, preMatrix, translations, postMatrix, then [SLOTS] variation slots. */
    const val VEC4_PER_XF = 4 + SLOTS * VEC4_PER_SLOT
    const val PACKED_FLOATS = (MAX_XF + 1) * VEC4_PER_XF * 4

    /** Variation names; the index is the id used by the GLSL `vari()` switch. Keep in step with [FlameEngine]. */
    val VARIATIONS = FlameVariations.NAMES
    private val PARAM_LETTERS = listOf("A", "B", "C", "D")

    private fun xfName(i: Int) = if (i == FINAL_INDEX) "XF" else "X${i + 1}"
    private fun slotKey(xf: Int, slot: Int, field: String) = "${xfName(xf)} V${slot + 1} $field"
    private fun key(xf: Int, field: String) = "${xfName(xf)} $field"

    /** Pre-built keys so the per-frame pack does not allocate strings. */
    internal class XfKeys(i: Int) {
        val weight = key(i, "Weight"); val color = key(i, "Color"); val speed = key(i, "ColorSpeed")
        val preRot = key(i, "PreRot"); val preSx = key(i, "PreScaleX"); val preSy = key(i, "PreScaleY")
        val preShear = key(i, "PreShear"); val preX = key(i, "PreX"); val preY = key(i, "PreY")
        val postRot = key(i, "PostRot"); val postSx = key(i, "PostScaleX"); val postSy = key(i, "PostScaleY")
        val postShear = key(i, "PostShear"); val postX = key(i, "PostX"); val postY = key(i, "PostY")
        val slotId = Array(SLOTS) { slotKey(i, it, "Id") }
        val slotWeight = Array(SLOTS) { slotKey(i, it, "Weight") }
        val slotParam = Array(SLOTS) { j -> Array(FlameVariations.PARAMS) { slotKey(i, j, PARAM_LETTERS[it]) } }
    }
    private val keys = Array(MAX_XF + 1) { XfKeys(it) }

    /** Parameter names of xform [i] (0..MAX_XF-1, or [FINAL_INDEX]) for the sampler. */
    internal fun keysFor(xf: Int): XfKeys = keys[xf]
    internal fun slotIdKey(xf: Int, slot: Int) = keys[xf].slotId[slot]
    internal fun slotWeightKey(xf: Int, slot: Int) = keys[xf].slotWeight[slot]
    internal fun slotParamKey(xf: Int, slot: Int, n: Int) = keys[xf].slotParam[slot][n]
    internal fun weightKey(xf: Int) = keys[xf].weight
    internal fun xfParamNames(xf: Int): List<String> = with(keys[xf]) {
        listOf(weight, color, speed, preRot, preSx, preSy, preShear, preX, preY) + slotId.toList() + slotWeight.toList()
    }

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

    // Ranges wide enough for flames found in the wild (the importer clamps to them); generated genomes stay well inside.
    private const val SCALE_MAX = 4f        // pre/post scale and shear
    private const val MOVE_MAX = 10f        // pre/post translation
    private const val SLOT_WEIGHT_MAX = 4f  // variation weight, negative allowed

    /**
     * [morph] = takes part in deck morphing (randomizeBase): the morph controller keeps an A and a B value for it.
     * Its B value comes from [FlameSampler] (via [FlameSource.sampleMorphTarget]), not from the generic uniform draw
     * (baseMin == baseMax, so that draw is a no-op).
     */
    private fun param(v: Float, min: Float, max: Float, angle: Boolean = false, morph: Boolean = false) =
        ModulatableParameter(baseValue = v, minClamp = min, maxClamp = max, explicitIsAngle = angle, randomizeBase = morph)

    /** Variation ids: never lerped by the morph (the engine cross-weights A's and B's slots instead). */
    private fun idParam(v: Int) = ModulatableParameter(
        baseValue = v.toFloat(), minClamp = 0f, maxClamp = (VARIATIONS.size - 1).toFloat(),
        randomizeBase = true, steps = VARIATIONS.size, labels = FlameVariations.LABELS
    )

    private fun global(m: LinkedHashMap<String, ModulatableParameter>) {
        // Camera
        m["Zoom"] = param(0.45f, 0.01f, 32f)
        m["Rotation"] = param(0f, -PI.toFloat(), PI.toFloat(), angle = true)
        m["FlipY"] = ModulatableParameter(baseValue = 0f, minClamp = 0f, maxClamp = 1f, steps = 2, labels = listOf("Up", "Down"))   // image y axis: imported flames have it pointing down
        m["CenterX"] = param(0f, -16f, 16f)
        m["CenterY"] = param(0f, -16f, 16f)
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
        // Lineage: strength of the next-state mutation (read by the sampler when it picks the next morph target)
        m["Mutation"] = param(0.25f, 0f, 1f)
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
            m[k.weight] = param(s?.weight ?: 0f, 0f, 2f, morph = true)
            m[k.color] = param(s?.color ?: 0f, 0f, 1f, morph = true)
            m[k.speed] = param(COLOR_SPEED, 0f, 1f, morph = true)
        }
        m[k.preRot] = param(s?.rot ?: 0f, -PI.toFloat(), PI.toFloat(), angle = true, morph = true)
        m[k.preSx] = param(s?.sx ?: if (isFinal) 1f else 0.6f, -SCALE_MAX, SCALE_MAX, morph = true)
        m[k.preSy] = param(s?.sy ?: if (isFinal) 1f else 0.6f, -SCALE_MAX, SCALE_MAX, morph = true)
        m[k.preShear] = param(s?.shear ?: 0f, -SCALE_MAX, SCALE_MAX, morph = true)
        m[k.preX] = param(s?.tx ?: 0f, -MOVE_MAX, MOVE_MAX, morph = true)
        m[k.preY] = param(s?.ty ?: 0f, -MOVE_MAX, MOVE_MAX, morph = true)
        m[k.postRot] = param(0f, -PI.toFloat(), PI.toFloat(), angle = true, morph = true)
        m[k.postSx] = param(1f, -SCALE_MAX, SCALE_MAX, morph = true)
        m[k.postSy] = param(1f, -SCALE_MAX, SCALE_MAX, morph = true)
        m[k.postShear] = param(0f, -SCALE_MAX, SCALE_MAX, morph = true)
        m[k.postX] = param(0f, -MOVE_MAX, MOVE_MAX, morph = true)
        m[k.postY] = param(0f, -MOVE_MAX, MOVE_MAX, morph = true)
        for (j in 0 until SLOTS) {
            val v = s?.v?.getOrNull(j)
            m[k.slotId[j]] = idParam(v?.first ?: 0)
            m[k.slotWeight[j]] = param(v?.second ?: if (j == 0) 1f else 0f, -SLOT_WEIGHT_MAX, SLOT_WEIGHT_MAX, morph = true)
            // normalised variation parameters (see FlameVariations.real): 0.5 = the variation's default
            for (n in 0 until FlameVariations.PARAMS) m[k.slotParam[j][n]] = param(0.5f, 0f, 1f, morph = true)
        }
    }

    // ---- editor layout (names only; see StructuredEditor.kt) ------------------------------------------------------

    private val CAMERA = listOf("Zoom", "Rotation", "CenterX", "CenterY", "FlipY")
    private val TONE = listOf("Exposure", "Brightness", "Gamma", "Vibrancy", "Toe", "Decay", "Sharpness", "Palette")
    private val QUALITY = listOf("Points", "Passes", "Kernel")
    private val MODS = listOf("Spin", "Breathe", "Warp", "Drift", "Mutation")

    /** Xforms shown without being asked: the first [ALWAYS_SHOWN], plus any with weight > 0. */
    const val ALWAYS_SHOWN = 2

    fun tabId(xf: Int) = xfName(xf)

    /** Tab for xform [i]: weight/colour, pre and post affine (post collapsed), then the variation slots. */
    private fun xformTab(i: Int, params: Map<String, ModulatableParameter>): EditorTab {
        val k = keys[i]
        val isFinal = i == FINAL_INDEX
        val main = if (isFinal) emptyList() else listOf(k.weight, k.color, k.speed)
        val sections = ArrayList<EditorSection>()
        if (main.isNotEmpty()) sections += EditorSection("Weight / Colour", main)
        sections += EditorSection("Pre-affine", listOf(k.preRot, k.preSx, k.preSy, k.preShear, k.preX, k.preY))
        sections += EditorSection("Post-affine", listOf(k.postRot, k.postSx, k.postSy, k.postShear, k.postX, k.postY), collapsed = !isFinal)
        for (j in 0 until SLOTS) {
            val id = (params[k.slotId[j]]?.baseValue ?: 0f).toInt().coerceIn(0, VARIATIONS.size - 1)
            val used = FlameVariations.ALL[id].params
            val rows = listOf(k.slotId[j], k.slotWeight[j]) + used.indices.map { k.slotParam[j][it] }
            val labels = used.indices.associate { k.slotParam[j][it] to used[it].label }
            sections += EditorSection("Variation ${j + 1}", rows, rowLabels = labels)
        }
        return EditorTab(xfName(i), xfName(i), sections)
    }

    private fun isShown(params: Map<String, ModulatableParameter>, i: Int, keep: String?): Boolean =
        i < ALWAYS_SHOWN || xfName(i) == keep || (params[keys[i].weight]?.baseValue ?: 0f) > 1e-4f

    fun editorTabs(params: Map<String, ModulatableParameter>, keep: String?): List<EditorTab> {
        val tabs = ArrayList<EditorTab>()
        tabs += EditorTab("Look", "Look", listOf(
            EditorSection("Camera", CAMERA), EditorSection("Tone", TONE), EditorSection("Quality", QUALITY)
        ))
        tabs += EditorTab("Mods", "Mods", listOf(EditorSection("Handles", MODS)))
        for (i in 0 until MAX_XF) if (isShown(params, i, keep)) tabs += xformTab(i, params)
        tabs += xformTab(FINAL_INDEX, params)
        return tabs
    }

    /** First hidden xform, or -1 when all are shown. */
    fun firstHiddenXform(params: Map<String, ModulatableParameter>): Int =
        (0 until MAX_XF).firstOrNull { !isShown(params, it, null) } ?: -1

    /** Reveals xform [i] by giving it a small weight and a linear slot. Returns its tab id. */
    fun reveal(params: Map<String, ModulatableParameter>, i: Int): String {
        params[keys[i].weight]?.baseValue = 0.5f
        if ((params[keys[i].slotWeight[0]]?.baseValue ?: 0f) <= 0f) params[keys[i].slotWeight[0]]?.baseValue = 1f
        return xfName(i)
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
     * [preX, preY, postX, postY], [post a b c d], then per slot [idA, wA, idB, wB], [A's p1..p4], [B's p1..p4]
     * (parameters in the variation's own units, see [FlameVariations.real]).
     * @return number of active xforms (>= 1; an empty genome becomes one identity xform)
     */
    fun pack(params: Map<String, ModulatableParameter>, out: FloatArray, morph: FlameMorphLookup? = null): Int {
        fun v(name: String, def: Float) = params[name]?.value ?: def
        val spin = v("Spin", 0f); val breathe = v("Breathe", 1f); val warp = v("Warp", 1f); val drift = v("Drift", 0f)
        var n = 0
        for (i in 0 until MAX_XF) {
            val k = keys[i]
            val w = v(k.weight, 0f)
            if (w <= 1e-4f) continue
            packXf(params, k, out, n * VEC4_PER_XF * 4, w, spin * (if (n % 2 == 0) 1f else -1f) * (1f + 0.3f * n), breathe, warp, drift, morph)
            n++
        }
        if (n == 0) {
            java.util.Arrays.fill(out, 0, VEC4_PER_XF * 4, 0f)
            out[0] = 1f; out[4] = 1f; out[7] = 1f; out[12] = 1f; out[15] = 1f   // weight 1, identity pre and post
            out[16 + 1] = 1f                                                      // slot 0 = linear, weight 1
            n = 1
        }
        packXf(params, keys[FINAL_INDEX], out, FINAL_INDEX * VEC4_PER_XF * 4, 1f, 0f, 1f, 1f, 0f, morph)
        return n
    }

    private fun packXf(
        params: Map<String, ModulatableParameter>, k: XfKeys, out: FloatArray, o: Int,
        weight: Float, spin: Float, breathe: Float, warp: Float, drift: Float, morph: FlameMorphLookup?
    ) {
        fun v(name: String, def: Float) = params[name]?.value ?: def
        out[o] = weight
        out[o + 1] = v(k.color, 0f)
        out[o + 2] = (v(k.speed, COLOR_SPEED) + drift).coerceIn(0f, 1f)
        out[o + 3] = 0f
        matrix(v(k.preRot, 0f) + spin, v(k.preSx, 1f) * breathe, v(k.preSy, 1f) * breathe, v(k.preShear, 0f), out, o + 4)
        out[o + 8] = v(k.preX, 0f); out[o + 9] = v(k.preY, 0f); out[o + 10] = v(k.postX, 0f); out[o + 11] = v(k.postY, 0f)
        matrix(v(k.postRot, 0f), v(k.postSx, 1f), v(k.postSy, 1f), v(k.postShear, 0f), out, o + 12)
        for (j in 0 until SLOTS) packSlot(params, k, j, out, o + 16 + j * VEC4_PER_SLOT * 4, warp, morph)
    }

    private fun warped(id: Int, w: Float, warp: Float) = w * (if (id == 0) 1f else warp)

    /**
     * Slot vec4s = [idA, wA, idB, wB], [A's params], [B's params]. Without a morph, B is empty (weight 0). While morphing, A's slot is weighted
     * (1-t) and B's t, so a variation id swap cross-fades instead of sweeping through the ids in between. Weight
     * modulation (the evaluated value minus the plain A->B lerp) is added to both ends.
     */
    private fun packSlot(
        params: Map<String, ModulatableParameter>, k: XfKeys, j: Int, out: FloatArray, so: Int,
        warp: Float, morph: FlameMorphLookup?
    ) {
        val idP = params[k.slotId[j]]
        val wP = params[k.slotWeight[j]]
        val maxId = VARIATIONS.size - 1
        fun value(n: Int) = params[k.slotParam[j][n]]?.value ?: 0.5f
        fun writeParams(o: Int, id: Int, norm: (Int) -> Float) {
            for (n in 0 until FlameVariations.PARAMS) out[o + n] = FlameVariations.real(id, n, norm(n))
        }
        if (morph == null || idP == null || wP == null) {
            val id = (idP?.value ?: 0f).toInt().coerceIn(0, maxId)
            out[so] = id.toFloat(); out[so + 1] = warped(id, wP?.value ?: 0f, warp)
            out[so + 2] = 0f; out[so + 3] = 0f
            writeParams(so + 4, id, ::value)
            java.util.Arrays.fill(out, so + 8, so + 12, 0f)
            return
        }
        val t = morph.t.coerceIn(0f, 1f)
        val idA = (morph.a(idP) + 0.5f).toInt().coerceIn(0, maxId)
        val idB = (morph.b(idP) + 0.5f).toInt().coerceIn(0, maxId)
        val wa = morph.a(wP); val wb = morph.b(wP)
        val delta = wP.value - (wa + (wb - wa) * t)
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
            // each end keeps its own parameters; modulation (value minus the plain A->B lerp) rides on both
            fun end(side: (ModulatableParameter) -> Float, n: Int): Float {
                val pp = params[k.slotParam[j][n]] ?: return 0.5f
                val pa = morph.a(pp); val pb = morph.b(pp)
                return side(pp) + (pp.value - (pa + (pb - pa) * t))
            }
            writeParams(so + 4, idA) { end(morph::a, it) }
            writeParams(so + 8, idB) { end(morph::b, it) }
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
            val so = o + 16 + j * VEC4_PER_SLOT * 4
            if (!near(packed[so + 3], 0f)) return false
            if (near(packed[so + 1], 0f)) continue
            if (packed[so].toInt() != 0) return false
            linear += packed[so + 1]
        }
        if (!near(linear, 1f)) return false
        return true
    }
}

/** Read-only A/B view of the deck morph for [FlameGenome.pack]: raw base values at both ends and the position t. */
interface FlameMorphLookup {
    val t: Float
    fun a(p: ModulatableParameter): Float
    fun b(p: ModulatableParameter): Float
}
