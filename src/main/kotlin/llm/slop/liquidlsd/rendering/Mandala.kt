package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryUtil
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
    id: String,
    displayName: String,
    shader: Shader,
    parameters: LinkedHashMap<String, ModulatableParameter>,
    hasFeedback: Boolean = false,
    ownsShader: Boolean = false,
    recipe: MandalaRatio,
    override val categories: List<String> = listOf("Generator", "Geometric")
) : DynamicVisualSource(id, displayName, shader, parameters, hasFeedback = hasFeedback, ownsShader = ownsShader, categories = categories) {

    var recipe: MandalaRatio = recipe
        set(value) {
            val oldPetals = field.petals
            field = value
            if (oldPetals != value.petals) {
                updateDefaultHueSweep()
            }
        }

    var vao: Int = 0
        private set
    var vbo: Int = 0
        private set

    init {
        val initialList = MandalaLibrary.recipesByPetals[recipe.petals] ?: emptyList()
        val initialIdx = initialList.indexOfFirst { it.a == recipe.a && it.b == recipe.b && it.c == recipe.c && it.d == recipe.d }.coerceAtLeast(0)
        val initialPct = if (initialList.size > 1) initialIdx.toFloat() / (initialList.size - 1).toFloat() else 0.0f
        parameters["Recipe Select"]?.set(initialPct)
        updateDefaultHueSweep()
        if (ownsShader) {
            initGeometry()
        }
    }

    private fun initGeometry() {
        val buf = MemoryUtil.memAllocFloat(expansionBuffer.size)
        buf.put(expansionBuffer).flip()
        try {
            vao = glGenVertexArrays()
            vbo = glGenBuffers()
            glBindVertexArray(vao)
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW)
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
            glEnableVertexAttribArray(0)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindVertexArray(0)
        } finally {
            MemoryUtil.memFree(buf)
        }
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

    fun getSymmetricHueCycles(petals: Int): List<Int> = symmetricHueCycles(petals)

    var minR: Float = 0f
        private set
    var maxR: Float = 1f
        private set

    override fun update() {
        super.update()

        // 1+2. Resolve the closest valid lobe count, then the recipe selected within it
        val targetLobes = parameters["Lobes"]?.value?.roundToInt() ?: 3
        val targetRecipe = pickRecipe(targetLobes, parameters["Recipe Select"]?.value ?: 0.0f)
        if (targetRecipe != null && targetRecipe != recipe) {
            recipe = targetRecipe
        }

        // With sum-of-lengths normalization, the max possible reach is TARGET_RADIUS
        val l1 = abs(parameters["L1"]?.value ?: 0f)
        val l2 = abs(parameters["L2"]?.value ?: 0f)
        val l3 = abs(parameters["L3"]?.value ?: 0f)
        val l4 = abs(parameters["L4"]?.value ?: 0f)
        val sumL = l1 + l2 + l3 + l4

        maxR = if (sumL > 1e-5f) TARGET_RADIUS else 0.001f
        minR = 0f // Stable base for depth/brightness effect
    }

    /** The hue-cycle count the shader receives as `uHueSweep` (the Hue Sweep knob picks from the recipe's symmetric options). */
    private fun hueSweepCycles(): Float = hueSweepCycles(recipe.petals, parameters["Hue Sweep"]?.value ?: 0f)

    override fun setupUniforms(shader: Shader) {
        val p = parameters

        val normArms = computeNormalizedArmLengths(
            p["L1"]?.value ?: 0f, p["L2"]?.value ?: 0f,
            p["L3"]?.value ?: 0f, p["L4"]?.value ?: 0f
        )
        shader.setUniform("uL1", normArms[0])
        shader.setUniform("uL2", normArms[1])
        shader.setUniform("uL3", normArms[2])
        shader.setUniform("uL4", normArms[3])

        shader.setUniform("uA", recipe.a.toFloat())
        shader.setUniform("uB", recipe.b.toFloat())
        shader.setUniform("uC", recipe.c.toFloat())
        shader.setUniform("uD", recipe.d.toFloat())

        shader.setUniform("uThickness",      (p["Thickness"]?.value ?: 0.5f)  * 0.035f)

        shader.setUniform("uHueOffset", p["Hue Offset"]?.value ?: 0f)
        shader.setUniform("uHueSweep",  hueSweepCycles())
        shader.setUniform("uDepth",     p["Depth"]?.value ?: 0.35f)
        shader.setUniform("uMaxR",      maxR)
    }

    /**
     * The shader-ready uniform values for the current recipe and parameters, keyed by uniform
     * name. Allocates, so it is for the web broadcast (which sends them verbatim so the browser
     * needs neither the recipe table nor this maths), not the render loop.
     */
    fun uniformSnapshot(): Map<String, Float> {
        val p = parameters
        val arms = computeNormalizedArmLengths(
            p["L1"]?.value ?: 0f, p["L2"]?.value ?: 0f,
            p["L3"]?.value ?: 0f, p["L4"]?.value ?: 0f
        )
        return linkedMapOf(
            "uL1" to arms[0], "uL2" to arms[1], "uL3" to arms[2], "uL4" to arms[3],
            "uA" to recipe.a.toFloat(), "uB" to recipe.b.toFloat(),
            "uC" to recipe.c.toFloat(), "uD" to recipe.d.toFloat(),
            "uThickness" to (p["Thickness"]?.value ?: 0.5f) * 0.035f,
            "uHueOffset" to (p["Hue Offset"]?.value ?: 0f),
            "uHueSweep" to hueSweepCycles(),
            "uDepth" to (p["Depth"]?.value ?: 0.35f),
            "uMaxR" to maxR
        )
    }

    override fun drawTopology() {
        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, (POINTS + 1) * 2)
        glBindVertexArray(0)
    }

    override fun clone(): Mandala {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        this.parameters.forEach { (name, param) ->
            clonedParams[name] = param.clone()
        }
        val copy = Mandala(
            id = this.id,
            displayName = this.displayName,
            shader = this.shader,
            parameters = clonedParams,
            hasFeedback = this.hasFeedback,
            ownsShader = false,
            recipe = this.recipe,
            categories = this.categories
        )
        copy.vao = this.vao
        copy.vbo = this.vbo
        copy.globalAlpha.set(this.globalAlpha.baseValue)
        copy.globalAlpha.randomizeBase = this.globalAlpha.randomizeBase
        copy.globalAlpha.baseMin = this.globalAlpha.baseMin
        copy.globalAlpha.baseMax = this.globalAlpha.baseMax
        copy.globalAlpha.modulators.clear()
        copy.globalAlpha.modulators.addAll(this.globalAlpha.modulators)
        return copy
    }

    override fun dispose() {
        super.dispose()
        if (ownsShader && vao != 0) {
            glDeleteBuffers(vbo)
            glDeleteVertexArrays(vao)
            vao = 0
            vbo = 0
        }
    }

    companion object {
        /** The petal count in the recipe library closest to [target] (ties go to the smaller). */
        fun closestPetals(target: Int): Int {
            val keys = MandalaLibrary.uniquePetals
            if (keys.isEmpty()) return 3
            return keys.minByOrNull { abs(it - target) } ?: 3
        }

        /**
         * The recipe the Lobes and Recipe Select knobs choose: the closest petal count, then
         * [select] (0..1) mapped across that count's recipes. Null if the library is empty.
         * Mirrored by web/mandala.js; web/tools/mandala_vectors.json pins the two together.
         */
        fun pickRecipe(lobes: Int, select: Float): MandalaRatio? {
            val recipes = MandalaLibrary.recipesByPetals[closestPetals(lobes)] ?: return null
            if (recipes.isEmpty()) return null
            val index = (select * (recipes.size - 1)).roundToInt().coerceIn(0, recipes.size - 1)
            return recipes[index]
        }

        /** Hue-cycle counts a recipe with [petals] offers: its divisors plus 1x to 4x the petal count. */
        fun symmetricHueCycles(petals: Int): List<Int> {
            val p = petals.coerceAtLeast(1)
            return symmetricHueCyclesCache.getOrPut(p) {
                val options = mutableSetOf<Int>()
                for (i in 1..p) {
                    if (p % i == 0) {
                        options.add(i)
                    }
                }
                for (i in 1..4) {
                    options.add(p * i)
                }
                options.sorted()
            }
        }

        /** The `uHueSweep` value the Hue Sweep knob ([rawSweep], 0..1) picks from [symmetricHueCycles]. */
        fun hueSweepCycles(petals: Int, rawSweep: Float): Float {
            val options = symmetricHueCycles(petals)
            val idx = if (options.size > 1)
                (rawSweep * (options.size - 1)).roundToInt().coerceIn(0, options.size - 1) else 0
            return options[idx].toFloat()
        }

        private val symmetricHueCyclesCache = java.util.concurrent.ConcurrentHashMap<Int, List<Int>>()
        const val POINTS = 2048
        const val TARGET_RADIUS = 2.0f

        /**
         * Normalizes 4 arm lengths using Sum-of-Lengths (Option 1):
         * scale = targetRadius / sum(|Li|)
         * Guarantees that the theoretical maximum reach of the pen exactly touches targetRadius.
         * If sum is <= 1e-5, returns all zeros to avoid division by zero or amplifying noise.
         */
        fun computeNormalizedArmLengths(
            l1: Float,
            l2: Float,
            l3: Float,
            l4: Float,
            targetRadius: Float = TARGET_RADIUS
        ): FloatArray {
            val sumL = abs(l1) + abs(l2) + abs(l3) + abs(l4)
            val scale = if (sumL > 1e-5f) targetRadius / sumL else 0.0f
            return floatArrayOf(l1 * scale, l2 * scale, l3 * scale, l4 * scale)
        }

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
