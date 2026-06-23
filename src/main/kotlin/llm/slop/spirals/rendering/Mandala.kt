package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Data class representing the frequency ratios (recipe) for a 4-arm mandala.
 */
/**
 * Defines the frequency ratios and pre-computed structural metadata for a 4-arm Lissajous mandala.
 *
 * The four integers [a, b, c, d] are the angular frequency multipliers fed to the four arms of the
 * parametric curve.  The renderer maps them to shader uniforms omega1..omega4.
 *
 * Pre-computed metadata (computed offline, stored for filtering/display — not used by the shader):
 *
 * @param id              Unique stable identifier (hash string from generation tool).
 * @param a               Frequency of arm 1 — typically the largest, sets overall rotation speed.
 * @param b               Frequency of arm 2 — second harmonic.
 * @param c               Frequency of arm 3 — third harmonic.
 * @param d               Frequency of arm 4 — fourth harmonic (may be negative for counter-rotation).
 * @param petals          Number of distinct lobes/petals visible in the completed figure.
 *                        Used for auto-hue-sweep: hueSweep = petals / 9.
 * @param shapeRatio      Ratio of the dominant frequency to the next; proxy for visual "complexity".
 *                        Higher = more regular/symmetric shape.
 * @param multiplicityClass  1 = single-trace (one continuous stroke), 2 = two-stroke figure.
 * @param independentFreqCount  Number of independently contributing frequency components (2–6).
 *                        Higher values produce more intricate interference patterns.
 * @param twoFoldLikely   True when the figure has approximate 2-fold rotational symmetry.
 * @param hierarchyDepth  Depth of nested sub-structure (0 = simple, higher = fractal-like).
 * @param dominanceRatio  Similar to shapeRatio but normalised differently; used for sorting/filtering.
 * @param radialVariance  Variance of radial extent across the figure; higher = more "spiky" or
 *                        asymmetric silhouette.
 */
data class Mandala4Arm(
    val id: String,
    val a: Int,
    val b: Int,
    val c: Int,
    val d: Int,
    val petals: Int = 3,
    val shapeRatio: Float = 4.0f,
    val multiplicityClass: Int = 2,
    val independentFreqCount: Int = 3,
    val twoFoldLikely: Boolean = true,
    val hierarchyDepth: Int = 0,
    val dominanceRatio: Float = 4.0f,
    val radialVariance: Float = 10.8f
)

typealias MandalaRatio = Mandala4Arm

/**
 * Encapsulates the state and parameters of a Mandala.
 * Implements VisualSource for desktop rendering.
 */
class Mandala(
    recipe: MandalaRatio
) : VisualSource {

    var recipe: MandalaRatio = recipe
        set(value) {
            val oldPetals = field.petals
            field = value
            if (oldPetals != value.petals) {
                updateDefaultHueSweep()
            }
        }

    override val parameters = linkedMapOf(
        "L1" to ModulatableParameter(0.4f),
        "L2" to ModulatableParameter(0.3f),
        "L3" to ModulatableParameter(0.2f),
        "L4" to ModulatableParameter(0.1f),
        "Scale" to ModulatableParameter(0.125f),
        "Rotation" to ModulatableParameter(0.0f),
        "Thickness" to ModulatableParameter(0.5f),
        "Hue Offset" to ModulatableParameter(0.0f),
        "Hue Sweep" to ModulatableParameter(0.0f),
        "Depth" to ModulatableParameter(0.35f),
        "Lobes" to ModulatableParameter(recipe.petals.toFloat(), minClamp = 3.0f, maxClamp = 26.0f),
        "Recipe Select" to ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f)
    )

    init {
        val initialList = MandalaLibrary.recipesByPetals[recipe.petals] ?: emptyList()
        val initialIdx = initialList.indexOfFirst { it.a == recipe.a && it.b == recipe.b && it.c == recipe.c && it.d == recipe.d }.coerceAtLeast(0)
        val initialPct = if (initialList.size > 1) initialIdx.toFloat() / (initialList.size - 1).toFloat() else 0.0f
        parameters["Recipe Select"]?.set(initialPct)
        updateDefaultHueSweep()
    }

    private fun updateDefaultHueSweep() {
        val options = getSymmetricHueCycles(recipe.petals)
        val defaultIndex = options.indexOf(recipe.petals).coerceAtLeast(0)
        val defaultVal = if (options.size > 1) defaultIndex.toFloat() / (options.size - 1).toFloat() else 0.0f
        parameters["Hue Sweep"]?.let {
            it.baseValue = defaultVal
            it.baseMin = defaultVal
            it.baseMax = defaultVal
        }
    }

    fun getSymmetricHueCycles(petals: Int): List<Int> {
        val p = petals.coerceAtLeast(1)
        val options = mutableSetOf<Int>()
        for (i in 1..p) {
            if (p % i == 0) {
                options.add(i)
            }
        }
        for (i in 1..4) {
            options.add(p * i)
        }
        return options.sorted()
    }

    override val globalAlpha = ModulatableParameter(1.0f)
    override val globalScale = ModulatableParameter(1.0f)

    var minR: Float = 0f
        private set
    var maxR: Float = 1f
        private set

    override fun update() {
        super.update()

        // 1. Resolve closest valid lobes
        val targetLobes = parameters["Lobes"]?.value?.roundToInt() ?: 3
        val activeLobes = getClosestLobeCount(targetLobes)

        // 2. Resolve recipe selection
        val recipes = MandalaLibrary.recipesByPetals[activeLobes] ?: emptyList()
        if (recipes.isNotEmpty()) {
            val selectVal = parameters["Recipe Select"]?.value ?: 0.0f
            val recipeIndex = (selectVal * (recipes.size - 1)).roundToInt().coerceIn(0, recipes.size - 1)
            val targetRecipe = recipes[recipeIndex]
            if (targetRecipe != recipe) {
                recipe = targetRecipe
            }
        }

        // Calculate max possible reach of the arms to normalize distance in the shader
        val l1 = abs(parameters["L1"]?.value ?: 0f)
        val l2 = abs(parameters["L2"]?.value ?: 0f)
        val l3 = abs(parameters["L3"]?.value ?: 0f)
        val l4 = abs(parameters["L4"]?.value ?: 0f)

        maxR = max(0.001f, l1 + l2 + l3 + l4)
        minR = 0f // Stable base for depth/brightness effect
    }

    private fun getClosestLobeCount(target: Int): Int {
        val keys = MandalaLibrary.uniquePetals
        if (keys.isEmpty()) return 3
        return keys.minByOrNull { abs(it - target) } ?: 3
    }

    companion object {
        const val POINTS = 2048

        /**
         * Static buffer for GPU expansion containing [Phase, Side] pairs.
         * Used to generate a ribbon geometry of points along the parameterized curve.
         */
        val expansionBuffer: FloatArray by lazy {
            val buffer = FloatArray((POINTS + 1) * 2 * 2)
            for (i in 0..POINTS) {
                val phase = i.toFloat() / POINTS.toFloat()
                // Left vertex
                buffer[i * 4 + 0] = phase
                buffer[i * 4 + 1] = -1.0f
                // Right vertex
                buffer[i * 4 + 2] = phase
                buffer[i * 4 + 3] = 1.0f
            }
            buffer
        }
    }
}
