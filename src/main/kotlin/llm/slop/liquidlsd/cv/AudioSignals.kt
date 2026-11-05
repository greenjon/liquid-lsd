package llm.slop.liquidlsd.cv

/**
 * Every audio CV signal, in one place. Arrays that run across the bands share an order: full mix (where it exists),
 * bass, mid, hi-mid, high. The web TV keeps its own lists (`web/evaluator.js`, `web/renderer.js`); the IDs are saved in
 * presets, so an ID never changes meaning.
 *
 * - **Level:** the band's loudness, through auto gain.
 * - **Transient:** the band's hits (`audio/TransientDetector.kt`).
 * - **Section:** where the song is, not how hard it hits, so no envelope follower and no Audio Response.
 *   The energies are bipolar and pushed by the audio thread; `audio_drop` runs on the beat clock.
 */
object AudioSignals {
    val LEVELS = arrayOf("audio_amp", "audio_bass", "audio_mid", "audio_himid", "audio_high")
    val TRANSIENTS = arrayOf("audio_flux_amp", "audio_flux_bass", "audio_flux_mid", "audio_flux_himid", "audio_flux_high")
    const val ENERGY = "audio_energy"
    const val DROP = "audio_drop"
    val BAND_ENERGIES = arrayOf("audio_energy_bass", "audio_energy_mid", "audio_energy_himid", "audio_energy_high")
    val ENERGIES = arrayOf(ENERGY) + BAND_ENERGIES
    val SECTIONS = ENERGIES + DROP

    private val levels = LEVELS.toHashSet()
    private val transients = TRANSIENTS.toHashSet()
    private val energies = ENERGIES.toHashSet()
    private val sections = SECTIONS.toHashSet()

    fun isLevel(id: String) = id in levels
    fun isTransient(id: String) = id in transients
    fun isEnergy(id: String) = id in energies
    fun isSection(id: String) = id in sections
    fun isAudio(id: String) = id in levels || id in transients || id in sections

    /** Pushed by the audio thread, with their histories. */
    fun isPushed(id: String) = id in levels || id in transients || id in energies

    /** Title in modulator headers, or null for any other source. */
    fun label(id: String): String? = when (id) {
        "audio_amp" -> "Full Mix (RMS)"
        "audio_bass" -> "Low / Bass (RMS)"
        "audio_mid" -> "Mid (RMS)"
        "audio_himid" -> "Hi-Mid (RMS)"
        "audio_high" -> "High (RMS)"
        "audio_flux_amp" -> "Full Mix (Hits)"
        "audio_flux_bass" -> "Low / Kick (Hits)"
        "audio_flux_mid" -> "Mid / Snare (Hits)"
        "audio_flux_himid" -> "Hi-Mid / Crack (Hits)"
        "audio_flux_high" -> "High / Hat (Hits)"
        ENERGY -> "Energy (Section)"
        "audio_energy_bass" -> "Bass Energy (Section)"
        "audio_energy_mid" -> "Mid Energy (Section)"
        "audio_energy_himid" -> "Hi-Mid Energy (Section)"
        "audio_energy_high" -> "High Energy (Section)"
        DROP -> "Drop (Section)"
        else -> null
    }
}
