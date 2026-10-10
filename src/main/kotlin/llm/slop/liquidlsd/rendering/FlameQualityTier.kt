package llm.slop.liquidlsd.rendering

/**
 * Per-machine Fractal Flame cost settings. Not part of a preset: a flame made on a fast GPU must still play on a slow one.
 * [points] is the ceiling the auto-governor works under; [passes] and [kernel] are the splat passes per frame and the sprite size.
 */
enum class FlameQualityTier(val label: String, val points: Float, val passes: Int, val kernel: Float) {
    LOW("Low", 0.5f, 2, 3f),
    MEDIUM("Medium", 1f, 4, 3f),
    HIGH("High", 1f, 8, 3f);

    /** Exposure factor that keeps a flame's brightness the same as at [REFERENCE_PASSES]: density accumulates once per pass. */
    val passExposure: Float get() = REFERENCE_PASSES.toFloat() / passes

    companion object {
        const val REFERENCE_PASSES = 4
    }
}
