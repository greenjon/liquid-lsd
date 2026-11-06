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
    AUDIO_TRACKER("Audio Beat Tracker"),

    /**
     * The Ableton Link session: tempo and beat come from the other apps, and the tracker writes nothing.
     * With Link on and any other source, the app leads the session instead (see LinkSyncManager).
     */
    LINK("Ableton Link");

    companion object {
        fun fromString(name: String): ClockSource = when (name.trim().uppercase()) {
            "MANUAL", "MANUAL_TAP" -> MANUAL
            "AUDIO_TRACKER" -> AUDIO_TRACKER
            "LINK", "ABLETON_LINK" -> LINK
            else -> AUDIO_TRACKER
        }
    }
}

