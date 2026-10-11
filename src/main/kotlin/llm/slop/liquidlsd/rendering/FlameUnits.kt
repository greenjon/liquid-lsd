package llm.slop.liquidlsd.rendering

import kotlin.math.exp
import kotlin.math.ln

/**
 * Stored-to-working unit maps for the flame parameters. Every continuous flame parameter is stored (and so shown, see
 * `ValueFormat`) as 0..1 or -1..1, i.e. 0..100 / -100..100 in the UI, and angles as radians (-180..180); the maths
 * (engine, packing, sampler, importer) works in the real units and converts here. Discrete parameters (variation ids,
 * FlipY) and the already normalised variation parameters A..D are stored as they are.
 */
object FlameUnits {
    sealed class Mapping(val storedMin: Float, val storedMax: Float, val realMin: Float, val realMax: Float) {
        abstract fun real(stored: Float): Float
        abstract fun stored(real: Float): Float
        fun clampReal(r: Float) = r.coerceIn(realMin, realMax)
    }

    /** Straight line between the stored range and the real range. */
    class Linear(sMin: Float, sMax: Float, rMin: Float, rMax: Float) : Mapping(sMin, sMax, rMin, rMax) {
        override fun real(stored: Float) = realMin + (stored - storedMin) / (storedMax - storedMin) * (realMax - realMin)
        override fun stored(real: Float) = storedMin + (real - realMin) / (realMax - realMin) * (storedMax - storedMin)
    }

    /** 0..1 with 0.5 = [neutral]; exponential on both sides so equal steps are equal ratios (zoom-like values). */
    class Exponential(rMin: Float, private val neutral: Float, rMax: Float) : Mapping(0f, 1f, rMin, rMax) {
        private val lnLo = ln(rMin / neutral); private val lnHi = ln(rMax / neutral)
        override fun real(stored: Float): Float {
            val s = stored.coerceIn(0f, 1f)
            return if (s < 0.5f) neutral * exp(lnLo * (1f - 2f * s)) else neutral * exp(lnHi * (2f * s - 1f))
        }
        override fun stored(real: Float): Float {
            val r = real.coerceIn(realMin, realMax)
            return if (r < neutral) (1f - ln(r / neutral) / lnLo) / 2f else (1f + ln(r / neutral) / lnHi) / 2f
        }
    }

    /** 0..1 with 0.5 = [neutral], straight lines on each side (for values whose neutral sits off-centre). */
    class Neutral(rMin: Float, private val neutral: Float, rMax: Float) : Mapping(0f, 1f, rMin, rMax) {
        override fun real(stored: Float): Float {
            val s = stored.coerceIn(0f, 1f)
            return if (s < 0.5f) realMin + (neutral - realMin) * (s / 0.5f) else neutral + (realMax - neutral) * ((s - 0.5f) / 0.5f)
        }
        override fun stored(real: Float): Float {
            val r = real.coerceIn(realMin, realMax)
            return if (r < neutral) 0.5f * (r - realMin) / (neutral - realMin) else 0.5f + 0.5f * (r - neutral) / (realMax - neutral)
        }
    }

    private fun unit(rMin: Float, rMax: Float) = Linear(0f, 1f, rMin, rMax)
    private fun bipolar(rMax: Float) = Linear(-1f, 1f, -rMax, rMax)

    private val globals: Map<String, Mapping> = mapOf(
        "Zoom" to Exponential(0.01f, 1f, 32f),
        "Magnify" to Exponential(0.25f, 1f, 4f),
        "CenterX" to bipolar(16f), "CenterY" to bipolar(16f),
        "Exposure" to Exponential(0.01f, 1f, 100f),
        "Brightness" to unit(0f, 4f), "Gamma" to unit(1f, 4f), "Toe" to unit(0f, 5f), "Decay" to unit(0f, 0.98f),
        "Sharpness" to unit(0.5f, 8f), "Softness" to unit(1f, 10f),
        "Breathe" to Neutral(0.5f, 1f, 1.5f), "Warp" to Neutral(0f, 1f, 3f), "Drift" to bipolar(0.5f)
    )
    private val SCALE = bipolar(4f)        // pre/post scale and shear
    private val MOVE = bipolar(10f)        // pre/post translation
    private val XF_WEIGHT = unit(0f, 2f)
    private val SLOT_WEIGHT = bipolar(4f)

    private val xfField = Regex("^(?:X\\d|XF) (.+)$")
    private val slotWeight = Regex("^V\\d Weight$")

    /** The map for parameter [name], or null when it is stored in its own units (angles, 0..1 values, ids, A..D). */
    fun of(name: String): Mapping? {
        globals[name]?.let { return it }
        val field = xfField.find(name)?.groupValues?.get(1) ?: return null
        return when {
            field == "Weight" -> XF_WEIGHT
            slotWeight.matches(field) -> SLOT_WEIGHT
            field.startsWith("PreScale") || field.startsWith("PostScale") || field == "PreShear" || field == "PostShear" -> SCALE
            field in MOVES -> MOVE
            else -> null
        }
    }
    private val MOVES = setOf("PreX", "PreY", "PostX", "PostY")

    fun real(name: String, stored: Float): Float = of(name)?.real(stored) ?: stored
    fun stored(name: String, real: Float): Float = of(name)?.let { it.stored(it.clampReal(real)) } ?: real

    /** Real-unit range of [name], or [fallbackMin]..[fallbackMax] (the stored clamp) when it has no map. */
    fun realMin(name: String, fallback: Float) = of(name)?.realMin ?: fallback
    fun realMax(name: String, fallback: Float) = of(name)?.realMax ?: fallback

}
