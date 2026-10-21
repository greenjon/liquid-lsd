package llm.slop.liquidlsd.parameters

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure pattern operations for the step sequencer editor. All functions take the full 32-slot
 * step list plus the active [count] and return a new 32-slot list; slots beyond [count] are
 * preserved so growing the pattern again brings the old steps back.
 */
object SeqPatterns {
    const val MAX_STEPS = 32
    const val SNAP = 0.05f

    enum class Shape(val label: String) {
        RAMP_UP("Ramp Up"),
        RAMP_DOWN("Ramp Down"),
        TRIANGLE("Triangle"),
        ALTERNATE("Alternate"),
        PULSE("Pulse (1 in 4)"),
        ALL_ON("All On")
    }

    fun padded(steps: List<Float>): MutableList<Float> {
        val out = steps.toMutableList()
        while (out.size < MAX_STEPS) out.add(0f)
        return out
    }

    /** Rounds [v] to the nearest multiple of [step] (no rounding when [step] <= 0) within lo..hi. */
    fun snap(v: Float, lo: Float, hi: Float, step: Float = SNAP): Float {
        val r = if (step > 0f) (v / step).roundToInt() * step else v
        return r.coerceIn(lo, hi).let { if (abs(it) < 1e-4f) 0f else it }
    }

    /**
     * Sequencer output inside a step: holds [cur] for the first [hold] of the step, then glides to [next]
     * (cosine-eased when [smooth]). [frac] is progress through the step, 0..1. Shared by the evaluator and
     * the editor's glide trace.
     */
    fun glide(cur: Float, next: Float, frac: Float, hold: Float, smooth: Boolean): Float {
        val h = hold.coerceIn(0f, 1f)
        if (h >= 0.999f || frac < h) return cur
        val t = ((frac - h) / (1.0f - h)).coerceIn(0f, 1f)
        val u = if (smooth) 0.5f - 0.5f * kotlin.math.cos(t * Math.PI.toFloat()) else t
        return cur + (next - cur) * u
    }

    private fun apply(steps: List<Float>, values: List<Float>): List<Float> {
        val out = padded(steps)
        for (i in values.indices) out[i] = values[i]
        return out
    }

    fun invert(steps: List<Float>, count: Int, bipolar: Boolean): List<Float> {
        val n = count.coerceIn(1, MAX_STEPS)
        val src = padded(steps)
        return apply(steps, (0 until n).map { i -> if (bipolar) -src[i] else 1f - src[i] })
    }

    /** dir > 0 moves every step one slot later (right), dir < 0 one slot earlier. */
    fun rotate(steps: List<Float>, count: Int, dir: Int): List<Float> {
        val n = count.coerceIn(1, MAX_STEPS)
        val src = padded(steps)
        return apply(steps, (0 until n).map { i -> src[Math.floorMod(i - dir, n)] })
    }

    fun randomize(
        steps: List<Float>, count: Int, bipolar: Boolean,
        random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng
    ): List<Float> {
        val n = count.coerceIn(1, MAX_STEPS)
        val lo = if (bipolar) -1f else 0f
        return apply(steps, (0 until n).map { snap(lo + random.nextFloat() * (1f - lo), lo, 1f) })
    }

    fun shape(steps: List<Float>, count: Int, bipolar: Boolean, shape: Shape): List<Float> {
        val n = count.coerceIn(1, MAX_STEPS)
        val lo = if (bipolar) -1f else 0f
        fun lerp(t: Float) = snap(lo + (1f - lo) * t, lo, 1f)
        val values = (0 until n).map { i ->
            val t = if (n == 1) 1f else i / (n - 1).toFloat()
            when (shape) {
                Shape.RAMP_UP -> lerp(t)
                Shape.RAMP_DOWN -> lerp(1f - t)
                Shape.TRIANGLE -> lerp(1f - abs(2f * t - 1f))
                Shape.ALTERNATE -> if (i % 2 == 0) 1f else lo
                Shape.PULSE -> if (i % 4 == 0) 1f else 0f
                Shape.ALL_ON -> 1f
            }
        }
        return apply(steps, values)
    }

    /** Euclidean rhythm: [hits] evenly distributed over [count] steps (hit = 1, rest = 0), rotated [rotation] steps later. */
    fun euclid(steps: List<Float>, count: Int, hits: Int, rotation: Int = 0): List<Float> {
        val n = count.coerceIn(1, MAX_STEPS)
        val h = hits.coerceIn(0, n)
        return apply(steps, (0 until n).map { i -> val j = Math.floorMod(i - rotation, n); if ((j * h) % n < h) 1f else 0f })
    }
}
