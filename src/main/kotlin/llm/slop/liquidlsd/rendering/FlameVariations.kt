package llm.slop.liquidlsd.rendering

import kotlin.math.PI

/**
 * The variation catalogue of the fractal-flame source: names, ids (= index = the GLSL `vari()` case) and the
 * parametric variations' parameters. The set and the formulas follow Draves & Reckase, "The Fractal Flame Algorithm"
 * (Sept 2003, rev. Nov 2008), Appendix A; ids are the paper's variation numbers.
 *
 * Each slot has four parameters `A`..`D`, stored normalised 0..1. [real] maps one onto the chosen variation's own
 * range piecewise so that 0.5 is always that parameter's default, which means swapping the variation id never needs a
 * parameter reset and morphs between variations with different ranges stay sane.
 */
object FlameVariations {
    const val PARAMS = 4

    class Param(val label: String, val min: Float, val default: Float, val max: Float)

    class Variation(val name: String, val params: List<Param> = emptyList())

    private fun p(label: String, min: Float, def: Float, max: Float) = Param(label, min, def, max)
    private fun v(name: String, vararg params: Param) = Variation(name, params.toList())
    private val PIf = PI.toFloat()

    /** Index = paper variation number. Keep in step with the `case` labels in [FlameEngine]. */
    val ALL: List<Variation> = listOf(
        v("linear"), v("sinusoidal"), v("spherical"), v("swirl"), v("horseshoe"), v("polar"),
        v("handkerchief"), v("heart"), v("disc"), v("spiral"), v("hyperbolic"), v("diamond"),
        v("ex"), v("julia"), v("bent"),
        v("waves"), v("fisheye"), v("popcorn"), v("exponential"), v("power"), v("cosine"),
        v("rings"), v("fan"),
        v("blob", p("High", 0f, 1f, 2f), p("Low", 0f, 0.2f, 2f), p("Waves", 1f, 5f, 32f)),
        v("pdj", p("a", -3f, 1f, 3f), p("b", -3f, 1f, 3f), p("c", -3f, 1f, 3f), p("d", -3f, 1f, 3f)),
        v("fan2", p("X", 0.05f, 0.5f, 2f), p("Y", -2f, 0f, 2f)),
        v("rings2", p("Val", 0.005f, 0.3f, 2f)),
        v("eyefish"), v("bubble"), v("cylinder"),
        v("perspective", p("Angle", -1.5f, 0.6f, 1.5f), p("Dist", 0.5f, 2f, 4f)),
        v("noise"),
        v("juliaN", p("Power", -20f, 3f, 20f), p("Dist", -2f, 1f, 2f)),
        v("juliaScope", p("Power", -20f, 3f, 20f), p("Dist", -2f, 1f, 2f)),
        v("blur"), v("gaussian"),
        v("radialBlur", p("Angle", -2f, 0.5f, 2f)),
        v("pie", p("Slices", 1f, 6f, 32f), p("Rotation", -PIf, 0f, PIf), p("Thickness", 0f, 0.5f, 1f)),
        v("ngon", p("Power", 0.5f, 2f, 4f), p("Sides", 3f, 5f, 32f), p("Corners", 0f, 1f, 4f), p("Circle", 0f, 1f, 4f)),
        v("curl", p("c1", -2f, 1f, 2f), p("c2", -2f, 0f, 2f)),
        v("rectangles", p("X", 0.05f, 0.5f, 2f), p("Y", 0.05f, 0.5f, 2f)),
        v("arch"), v("tangent"), v("square"), v("rays"), v("blade"), v("secant"), v("twintrian"), v("cross")
    )

    val NAMES: List<String> = ALL.map { it.name }

    /** Variations whose result differs from call to call (they use the shader's random numbers), so they cannot be unit-checked pointwise. */
    val RANDOM: Set<String> = setOf(
        "julia", "noise", "juliaN", "juliaScope", "blur", "gaussian", "radialBlur", "pie",
        "arch", "square", "rays", "blade", "twintrian"
    )

    fun count() = ALL.size

    /** The parameter [slot] (0..3) of variation [id] in its own units, from the normalised stored value [n]; 0.5 maps to the default. */
    fun real(id: Int, slot: Int, n: Float): Float {
        val prm = ALL.getOrNull(id)?.params?.getOrNull(slot) ?: return 0f
        val x = n.coerceIn(0f, 1f)
        return if (x < 0.5f) prm.min + (prm.default - prm.min) * (x * 2f)
        else prm.default + (prm.max - prm.default) * ((x - 0.5f) * 2f)
    }

    /** Inverse of [real]: the normalised value that gives [value] (clamped to the range). */
    fun normalised(id: Int, slot: Int, value: Float): Float {
        val prm = ALL.getOrNull(id)?.params?.getOrNull(slot) ?: return 0.5f
        val x = value.coerceIn(prm.min, prm.max)
        return if (x < prm.default) 0.5f * (x - prm.min) / (prm.default - prm.min)
        else if (x == prm.default) 0.5f
        else 0.5f + 0.5f * (x - prm.default) / (prm.max - prm.default)
    }
}
