package llm.slop.liquidlsd.audio

/**
 * Supported timing and beat clock sync sources for Liquid LSD.
 */
enum class ClockSource(val displayName: String) {
    /**
     * Fixed manual BPM, slider control, and tap-tempo flywheel.
     */
    MANUAL("Manual Fixed"),

    /**
     * Autonomous FFT onset detection and dynamic programming flywheel from real-time audio input.
     */
    AUDIO_TRACKER("Audio Beat Tracker");

    companion object {
        fun fromString(name: String): ClockSource = when (name.trim().uppercase()) {
            "MANUAL", "MANUAL_TAP" -> MANUAL
            "AUDIO_TRACKER" -> AUDIO_TRACKER
            "ABLETON_LINK" -> MANUAL
            else -> AUDIO_TRACKER
        }
    }
}

