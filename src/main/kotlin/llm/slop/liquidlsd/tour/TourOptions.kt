package llm.slop.liquidlsd.tour

/**
 * Render settings for a tour, from the command line (the script stays the same). The UI is always laid out in the
 * 1920x1080 window; [scale] only shrinks the frame (GPU blit) before readback, so widget targets never move.
 * Final renders keep the defaults; [DRAFT] is for iterating on a script.
 */
data class TourOptions(
    val fps: Int = 60,
    val scale: Float = 1f,
    /** "x264" (software, reproducible encoded output) or "nvenc" (NVIDIA hardware). */
    val encoder: String = "x264",
    /** Per-frame SHA-256 of the raw frame (<out>.hashes); costs about 5 ms a frame at 1080p. */
    val hashes: Boolean = true
) {
    companion object {
        val DRAFT = TourOptions(fps = 30, scale = 0.5f, encoder = "nvenc", hashes = false)
    }
}
