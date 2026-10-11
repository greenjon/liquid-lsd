package llm.slop.liquidlsd.rendering

/**
 * Per-machine Fractal Flame cost settings. Not part of a preset: a flame made on a fast GPU must still play on a slow one.
 * [points] is the ceiling the auto-governor works under; [passes] is the number of splat passes per frame. The sprite size is a look setting (the `Softness` parameter).
 */
enum class FlameQualityTier(val label: String, val points: Float, val passes: Int) {
    LOW("Low", 0.5f, 2),
    MEDIUM("Medium", 1f, 4),
    HIGH("High", 1f, 8);

    /** Exposure factor that keeps a flame's brightness the same as at [REFERENCE_PASSES]: density accumulates once per pass. */
    val passExposure: Float get() = REFERENCE_PASSES.toFloat() / passes

    companion object {
        const val REFERENCE_PASSES = 4
    }
}
