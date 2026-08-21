package llm.slop.liquidlsd.notes

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.DeckPresetDto
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Manages three tiers of user notes:
 *
 * 1. **Global source notes** — one note per visual source engine (e.g. "Mandala", "Dynamic Spiral"),
 *    persisted to `~/.liquid-lsd/source-notes.json`. Survives preset loads and app restarts.
 *
 * 2. **Preset notes** — one free-form note per loaded preset, stored in `DeckPresetDto.presetNotes`.
 *    Synced from the DTO on preset load and back into the DTO on save.
 *
 * 3. **Parameter notes** — one note per (deckLabel, paramKey) pair, stored in
 *    `DeckPresetDto.paramNotes`. Same sync lifecycle as preset notes.
 *
 * In-memory maps for preset/param notes are keyed by deckLabel ("Deck A", "Deck B", "Deck C").
 */
object NotesManager {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val appDataDir: File by lazy {
        File(System.getProperty("user.home"), ".liquid-lsd").also { it.mkdirs() }
    }

    private val sourceNotesFile: File get() = File(appDataDir, "source-notes.json")

    // ── Global source notes (key = sourceId) ──────────────────────────────────

    private val sourceNotes: MutableMap<String, String> = mutableMapOf()
    private var sourceNotesLoaded = false

    fun loadSourceNotes() {
        if (!sourceNotesFile.exists()) {
            sourceNotesLoaded = true
            return
        }
        try {
            val map = json.decodeFromString<Map<String, String>>(sourceNotesFile.readText())
            sourceNotes.clear()
            sourceNotes.putAll(map)
            logger.info { "Loaded ${sourceNotes.size} source notes from ${sourceNotesFile.path}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load source-notes.json" }
        }
        sourceNotesLoaded = true
    }

    fun getSourceNote(sourceId: String): String {
        if (!sourceNotesLoaded) loadSourceNotes()
        return sourceNotes[sourceId] ?: ""
    }

    fun setSourceNote(sourceId: String, text: String) {
        if (text.isBlank()) {
            sourceNotes.remove(sourceId)
        } else {
            sourceNotes[sourceId] = text.trim()
        }
        saveSourceNotes()
    }

    private fun saveSourceNotes() {
        try {
            sourceNotesFile.writeText(json.encodeToString(sourceNotes.toMap()))
        } catch (e: Exception) {
            logger.error(e) { "Failed to save source-notes.json" }
        }
    }

    // ── Per-deck preset and parameter notes (in-memory; synced to/from DTO) ────

    /** Preset note per deck label. */
    private val presetNotes: MutableMap<String, String> = mutableMapOf()

    /** Parameter notes per deck label, inner map keyed by paramKey. */
    private val paramNotes: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    fun getPresetNote(deckLabel: String): String = presetNotes[deckLabel] ?: ""

    fun setPresetNote(deckLabel: String, text: String) {
        if (text.isBlank()) presetNotes.remove(deckLabel)
        else presetNotes[deckLabel] = text.trim()
    }


    fun getParamNote(deckLabel: String, paramKey: String): String =
        paramNotes[deckLabel]?.get(paramKey) ?: ""

    fun setParamNote(deckLabel: String, paramKey: String, text: String) {
        if (text.isBlank()) {
            paramNotes[deckLabel]?.remove(paramKey)
        } else {
            paramNotes.getOrPut(deckLabel) { mutableMapOf() }[paramKey] = text.trim()
        }
    }

    /**
     * Populates in-memory preset/param notes from a freshly loaded [DeckPresetDto].
     * Call this after loading a preset from disk.
     */
    fun syncFromDto(deckLabel: String, dto: DeckPresetDto) {
        if (dto.presetNotes.isBlank()) presetNotes.remove(deckLabel)
        else presetNotes[deckLabel] = dto.presetNotes

        if (dto.paramNotes.isEmpty()) {
            paramNotes.remove(deckLabel)
        } else {
            paramNotes[deckLabel] = dto.paramNotes.toMutableMap()
        }
    }

    /**
     * Returns a copy of [dto] with the current in-memory preset/param notes applied.
     * Call this before saving a preset to disk.
     */
    fun syncToDto(deckLabel: String, dto: DeckPresetDto): DeckPresetDto =
        dto.copy(
            presetNotes = presetNotes[deckLabel] ?: "",
            paramNotes = paramNotes[deckLabel]?.toMap() ?: emptyMap()
        )

    /** Clears in-memory notes for a deck (e.g. when the deck is reset / new preset). */
    fun clearDeckNotes(deckLabel: String) {
        presetNotes.remove(deckLabel)
        paramNotes.remove(deckLabel)
    }
}
