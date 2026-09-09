package llm.slop.liquidlsd.audio

/**
 * Supported timing and beat clock sync sources for Liquid LSD.
 */
enum class ClockSource(val displayName: String) {
    /**
     * Autonomous FFT onset detection and dynamic programming flywheel from real-time audio input.
     */
    AUDIO_TRACKER("BTrack Audio"),

    /**
     * Network-synchronized shared beat timeline, tempo, and quantum phase via Ableton Link.
     */
    ABLETON_LINK("Ableton Link"),

    /**
     * Fixed manual BPM and tap-tempo flywheel.
     */
    MANUAL_TAP("Manual Fixed")
}
