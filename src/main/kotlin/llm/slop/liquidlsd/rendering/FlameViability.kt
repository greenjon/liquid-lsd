package llm.slop.liquidlsd.rendering

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cheap numeric screen of a candidate genome before any pixel is drawn. It only rejects what no variation can rescue:
 * the variations fold space, so an expanding affine is fine on its own and is not judged here (the empirical
 * [FlameQuality] probe decides that). Pure maths on the stored (normalised) parameter values.
 */
object FlameViability {
    /** A xform share above this starves the others: rejected outright. Softer imbalance is only [Result.balance]. */
    const val MAX_SHARE = 0.97f
    /** Active-xform weight below this counts as unused (same threshold as the sampler). */
    private const val ACTIVE = 0.05f
    /** A pre-affine whose area scale is below this has flattened to a line or point. */
    private const val MIN_DET = 0.01f

    class Result(val ok: Boolean, val reason: String, val balance: Float, val activeXforms: Int) {
        override fun toString() = if (ok) "OK balance=%.2f".format(balance) else "REJECT $reason"
    }

    /** @param stored stored value of a genome parameter (xform parameters only are read) */
    fun check(stored: (String) -> Float): Result {
        fun real(name: String) = FlameUnits.real(name, stored(name))
        val weights = FloatArray(FlameGenome.MAX_XF) { real(FlameGenome.weightKey(it)) }
        val active = (0 until FlameGenome.MAX_XF).filter { weights[it] > ACTIVE }
        if (active.size < 2) return Result(false, "fewer than two active xforms", 0f, active.size)

        val total = active.sumOf { weights[it].toDouble() }.toFloat()
        val maxShare = active.maxOf { weights[it] } / total
        if (maxShare > MAX_SHARE) return Result(false, "one xform takes %.0f%% of the weight".format(maxShare * 100), 0f, active.size)

        var allFlat = true
        var allExpandingLinear = true
        for (x in active) {
            val k = FlameGenome.keysFor(x)
            val rot = real(k.preRot); val sx = real(k.preSx); val sy = real(k.preSy); val sh = real(k.preShear)
            if (!rot.isFinite() || !sx.isFinite() || !sy.isFinite() || !sh.isFinite()) return Result(false, "non-finite affine in X${x + 1}", 0f, active.size)
            if (abs(sx * sy) >= MIN_DET) allFlat = false
            // rotation does not change the singular values: those of the upper-triangular [[sx, sh], [0, sy]]
            val linearOnly = (0 until FlameGenome.SLOTS).all { j ->
                real(k.slotWeight[j]) <= 0.05f || real(k.slotId[j]).toInt() == 0
            }
            if (!(linearOnly && largestSingular(sx, sh, sy) > 1f)) allExpandingLinear = false
        }
        if (allFlat) return Result(false, "every pre-affine is singular", 0f, active.size)
        if (allExpandingLinear) return Result(false, "linear-only xforms all expand: it diverges", 0f, active.size)

        // balance 1 = even shares, falling to 0 at the reject limit; softly penalises a dominant xform
        val even = 1f / active.size
        val balance = ((MAX_SHARE - maxShare) / (MAX_SHARE - even)).coerceIn(0f, 1f)
        return Result(true, "", balance, active.size)
    }

    /** Largest singular value of [[a, b], [0, d]]. */
    internal fun largestSingular(a: Float, b: Float, d: Float): Float {
        val s = a * a + b * b + d * d
        val det = a * d
        val disc = sqrt(max(0f, s * s - 4f * det * det))
        return sqrt((s + disc) / 2f)
    }

    /** Weight-averaged log of the largest singular value: below 0 the maps contract on average. For reports only. */
    fun meanLogExpansion(stored: (String) -> Float): Float {
        var sum = 0f; var wsum = 0f
        for (x in 0 until FlameGenome.MAX_XF) {
            val k = FlameGenome.keysFor(x)
            val w = FlameUnits.real(k.weight, stored(k.weight))
            if (w <= ACTIVE) continue
            val sv = largestSingular(FlameUnits.real(k.preSx, stored(k.preSx)), FlameUnits.real(k.preShear, stored(k.preShear)), FlameUnits.real(k.preSy, stored(k.preSy)))
            sum += w * ln(max(sv, 1e-6f)); wsum += w
        }
        return if (wsum > 0f) sum / wsum else 0f
    }
}
