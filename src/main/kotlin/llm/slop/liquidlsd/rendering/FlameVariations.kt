package llm.slop.liquidlsd.rendering

import kotlin.math.PI

/**
 * The variation catalogue of the fractal-flame source: names, ids (= index = the GLSL `vari()` case) and the
 * parametric variations' parameters. The set and the formulas follow Draves & Reckase, "The Fractal Flame Algorithm"
 * (Sept 2003, rev. Nov 2008), Appendix A; ids are the paper's variation numbers (0-48)
 * and then the numbers of the clean-room spec in `.planning/flame-variations-spec.md` (49-98).
 *
 * Each slot has four parameters `A`..`D`, stored normalised 0..1. [real] maps one onto the chosen variation's own
 * range piecewise so that 0.5 is always that parameter's default, which means swapping the variation id never needs a
 * parameter reset and morphs between variations with different ranges stay sane.
 */
object FlameVariations {
    const val PARAMS = 4

    class Param(val label: String, val min: Float, val default: Float, val max: Float)

    /** [supported] false = a reserved id with no shader case: the importer reports its name as unsupported, the sampler never picks it. */
    class Variation(val name: String, val params: List<Param> = emptyList(), val supported: Boolean = true)

    private fun p(label: String, min: Float, def: Float, max: Float) = Param(label, min, def, max)
    private fun v(name: String, vararg params: Param) = Variation(name, params.toList())
    private fun na(name: String) = Variation(name, emptyList(), false)
    private val PIf = PI.toFloat()

    /**
     * Index = variation number. Keep in step with the `case` labels in [FlameEngine]. Ids the shader has no case for
     * stay in the table as [na] placeholders so that every later id keeps its number: 50, 65, 67 and 98 are skipped
     * for v1 (more parameters than a slot holds, or they alter the point instead of adding to the output).
     */
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
        v("arch"), v("tangent"), v("square"), v("rays"), v("blade"), v("secant2"), v("twintrian"), v("cross"),
        // 49-81 (the spec's order; 50 and 65 are skipped for v1)
        v("disc2", p("Rot", -0.5f, 0f, 0.5f), p("Twist", -2 * PIf, 0f, 2 * PIf)),
        na("super_shape"),
        v("flower", p("Petals", -4f, 0f, 4f), p("Holes", -1f, 0f, 1f)),
        v("conic", p("Eccentricity", 0f, 1f, 2f), p("Holes", -1f, 0f, 1f)),
        v("parabola", p("Height", -1.5f, 0f, 1.5f), p("Width", -1.5f, 0f, 1.5f)),
        v("bent2", p("X", -1.5f, 1f, 1.5f), p("Y", -1.5f, 1f, 1.5f)),
        v("bipolar", p("Shift", -1f, 0f, 1f)),
        v("boarders"), v("butterfly"),
        v("cell", p("Size", 0.5f, 1f, 2.5f)),
        v("cpow", p("R", 0f, 1f, 3f), p("I", -0.5f, 0f, 0.5f), p("Power", 0f, 1f, 6f)),
        v("curve", p("XAmp", -2.5f, 0f, 2.5f), p("YAmp", -2f, 0f, 2f), p("XLength", 0f, 1f, 3f), p("YLength", 0f, 1f, 3f)),
        v("edisc"), v("elliptic"),
        v("escher", p("Beta", -PIf, 0f, PIf)),
        v("foci"),
        na("lazysusan"),
        v("loonie"),
        na("pre_blur"),
        v("modulus", p("X", -1f, 0f, 1f), p("Y", -1f, 0f, 1f)),
        v("oscope", p("Separation", 0f, 1f, 2f), p("Frequency", 0f, PIf, 2 * PIf), p("Amplitude", 0f, 1f, 3f), p("Damping", -1f, 0f, 1f)),
        v("polar2"),
        v("popcorn2", p("X", -0.2f, 0f, 0.2f), p("Y", -0.2f, 0f, 0.2f), p("C", -5f, 0f, 5f)),
        v("scry"),
        v("separation", p("X", -2f, 0f, 2f), p("XInside", -1f, 0f, 1f), p("Y", -2f, 0f, 2f), p("YInside", -1f, 0f, 1f)),
        v("split", p("XSize", -2f, 0f, 2f), p("YSize", -2f, 0f, 2f)),
        v("splits", p("X", -1f, 0f, 1f), p("Y", -1f, 0f, 1f)),
        v("stripes", p("Space", -1f, 0f, 1f), p("Warp", -5f, 0f, 5f)),
        v("wedge", p("Angle", -PIf, 0f, PIf), p("Hole", -0.25f, 0f, 0.25f), p("Count", 0f, 1f, 5f), p("Swirl", -1f, 0f, 1f)),
        v("wedge_julia", p("Power", 0f, 1f, 6f), p("Dist", -2f, 0f, 2f), p("Count", 0f, 1f, 3f), p("Angle", -PIf, 0f, PIf)),
        v("wedge_sph", p("Angle", -PIf, 0f, PIf), p("Hole", -0.25f, 0f, 0.25f), p("Count", 0f, 1f, 5f), p("Swirl", -1f, 0f, 1f)),
        v("whorl", p("Inside", -1f, 0f, 1f), p("Outside", -1f, 0f, 1f)),
        v("waves2", p("ScaleX", -1.5f, 0f, 1.5f), p("ScaleY", -1.5f, 0f, 1.5f), p("FreqX", -4f, 0f, 4f), p("FreqY", -4f, 0f, 4f)),
        // 82-95: exp, log, trigonometric and hyperbolic
        v("exp"), v("log"), v("sin"), v("cos"), v("tan"), v("sec"), v("csc"), v("cot"),
        v("sinh"), v("cosh"), v("tanh"), v("sech"), v("csch"), v("coth"),
        // 96-98 (98 is skipped for v1)
        v("auger", p("Freq", 0f, 1f, 5f), p("Weight", 0f, 0.5f, 1f), p("Sym", -1f, 0f, 1f), p("Scale", 0f, 1f, 2f)),
        v("flux", p("Spread", -1f, 0f, 1f)),
        na("mobius")
    )

    val NAMES: List<String> = ALL.map { it.name }

    /** [NAMES] for the id parameter's step labels: reserved ids say so. */
    val LABELS: List<String> = ALL.map { if (it.supported) it.name else "${it.name} (unsupported)" }

    /** Variations whose result differs from call to call (they use the shader's random numbers), so they cannot be unit-checked pointwise. */
    val RANDOM: Set<String> = setOf(
        "julia", "noise", "juliaN", "juliaScope", "blur", "gaussian", "radialBlur", "pie",
        "arch", "square", "rays", "blade", "twintrian", "boarders", "flower", "conic", "parabola", "cpow", "wedge_julia"
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
