package llm.slop.liquidlsd.rack

/**
 * Classification of modular rack units within the 19" chassis.
 *
 * Each type defines a standardized functional role and distinctive visual theme
 * (badge label and accent color) for quick visual scanning on live stage setups.
 */
enum class RackUnitType(
    val displayName: String,
    val badgeLabel: String,
    val colorR: Float,
    val colorG: Float,
    val colorB: Float
) {
    /**
     * Synthesizes visuals from mathematical formulas, GLSL shaders, or external video input.
     * No internal video input required. (Neon Cyan)
     */
    GENERATOR("Generator Synth", "GEN", 0.15f, 0.85f, 0.95f),

    /**
     * Accepts upstream Video In and applies feedback, distortion, color grading, or ISF filter effects.
     * (Electric Violet / Purple)
     */
    PROCESSOR("Processor FX", "FX", 0.75f, 0.35f, 0.95f),

    /**
     * Blends or transitions multiple video streams (e.g. crossfader, dissolve, wipe).
     * (Amber Gold)
     */
    TRANSITION("Transition Mixer", "MIX", 0.95f, 0.75f, 0.15f),

    /**
     * Audio bridges, clock conductors, video splitters, or confidence monitors.
     * (Emerald Green)
     */
    UTILITY("Utility / Bridge", "UTIL", 0.25f, 0.85f, 0.45f)
}
