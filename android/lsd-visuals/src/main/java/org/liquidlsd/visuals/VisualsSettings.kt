package org.liquidlsd.visuals

/** How hard the picture may work. Auto lets the web app's GPU probe and adaptive resolution decide. */
enum class VisualQuality(val js: String) { AUTO("auto"), LOW("low"), HIGH("high") }

/** Calmer motion: no flashes or hard scenes. Auto follows the system's "remove animations" setting. */
enum class ReducedMotion(val js: String) { AUTO("auto"), ON("on"), OFF("off") }

data class VisualsSettings(
    val quality: VisualQuality = VisualQuality.AUTO,
    /** How strongly the picture follows the music: 0.25 .. 2, 1 is the web TV's default (it levels the audio itself). */
    val reactivity: Float = DEFAULT_REACTIVITY,
    val reducedMotion: ReducedMotion = ReducedMotion.AUTO,
    /** Follow a live broadcast from the Liquid LSD desktop app (needs the network). */
    val live: Boolean = true,
    /** Milliseconds the picture is held back so it lines up with what is heard (the audio sink buffers ahead). */
    val syncMs: Int = DEFAULT_SYNC_MS,
    /** Fetch new scenes and playlists from the Liquid LSD server in the background (small downloads). */
    val contentUpdates: Boolean = true,
) {
    companion object {
        const val DEFAULT_REACTIVITY = 1f
        const val MIN_REACTIVITY = 0.25f
        const val MAX_REACTIVITY = 2f
        const val DEFAULT_SYNC_MS = 250
        const val MAX_SYNC_MS = 800
    }
}
