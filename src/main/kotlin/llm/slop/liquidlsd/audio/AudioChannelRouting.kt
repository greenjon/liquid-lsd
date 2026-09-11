package llm.slop.liquidlsd.audio

/**
 * Defines the channel routing strategy for incoming stereo audio streams.
 */
enum class AudioChannelRouting(val displayName: String) {
    MIX("Mix (L + R)"),
    LEFT_ONLY("Left Only"),
    RIGHT_ONLY("Right Only");

    companion object {
        fun fromString(name: String?): AudioChannelRouting {
            if (name == null) return MIX
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MIX
        }
    }
}
