package llm.slop.liquidlsd.rendering

import kotlin.math.pow

/**
 * Holds a flame deck's GPU time near a budget by scaling how many points it iterates and draws (cost is roughly
 * linear in points). Fed the measured GPU milliseconds of each finished frame; [scale] (0.05..1) multiplies the
 * quality tier's point count, so the tier stays the ceiling. Frame times on integrated GPUs swing several-fold at
 * identical settings, so it works on a smoothed time and moves in small proportional steps, down faster than up.
 */
class FlameGovernor {
    var scale = 1f
        private set
    private var ema = 0f
    private var samples = 0

    /** Call with each GPU time sample; [budgetMs] <= 0 switches the governor off and restores full quality. */
    fun feed(ms: Float, budgetMs: Float) {
        if (budgetMs <= 0f) { scale = 1f; ema = 0f; samples = 0; return }
        ema = if (samples == 0) ms else ema + (ms - ema) * SMOOTHING
        if (++samples % STEP_EVERY != 0) return
        scale = when {
            ema > budgetMs * HIGH -> scale * (budgetMs / ema).pow(0.7f).coerceIn(0.7f, 0.98f)
            ema < budgetMs * LOW -> scale * 1.06f
            else -> scale
        }.coerceIn(MIN_SCALE, 1f)
    }

    companion object {
        const val MIN_SCALE = 0.05f
        private const val SMOOTHING = 0.1f
        private const val STEP_EVERY = 10
        private const val HIGH = 1.1f   // over budget by this factor: shed points
        private const val LOW = 0.75f   // under budget by this factor: win points back
    }
}
