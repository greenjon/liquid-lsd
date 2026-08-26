package llm.slop.liquidlsd.audio

/**
 * Represents an audio input device (Java Sound TargetDataLine mixer or JACK port).
 */
data class AudioInputDevice(
    val id: String,
    val name: String,
    val description: String,
    val isJack: Boolean = false,
    val isDefault: Boolean = false
) {
    override fun toString(): String = name
}
