package llm.slop.liquidlsd.ui

import java.io.File
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.presets.DeckChange
import llm.slop.liquidlsd.presets.DeckOps
import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Manages deck preset workflows: saving, loading, ejecting, copying/moving/swapping,
 * dirty-state confirmation routing, and ImGui file dialogs.
 */
class DeckPresetController(
    private val session: SessionContext,
    private val popupManager: PopupManager
) {
    fun saveDeckPreset(mixer: Mixer, name: String, deck: Deck, isDeckA: Boolean, tags: List<String>? = null) {
        if (deck.source is llm.slop.liquidlsd.rendering.ExternalVideoSource) return
        val cleanName = name.removeSuffix(".lsd").trim()
        if (cleanName.isBlank()) return

        val slot = DeckSlot.of(deck, mixer)
        val resolvedTags = tags ?: slot?.let { session.presetManager.cachedDto(it)?.tags } ?: emptyList()

        val dto = deck.toDto(cleanName, resolvedTags)
        slot?.let { session.presetManager.setActive(it, cleanName, dto) }
        val file = File("library/presets/$cleanName.lsd")
        val deckIndex = slot?.index ?: -1
        session.presetRepository.saveDeckPresetAsync(file, deck, cleanName, resolvedTags, deckIndex)
    }

    fun handleUtilityAction(mixer: Mixer, mode: Int, from: Deck, to: Deck) {
        val toSlot = DeckSlot.of(to, mixer) ?: return
        val fromSlot = DeckSlot.of(from, mixer) ?: return
        DeckOps.request(toSlot, when (mode) {
            0 -> DeckChange.MoveFrom(fromSlot)
            1 -> DeckChange.CopyFrom(fromSlot)
            else -> DeckChange.SwapWith(fromSlot)
        })
    }

    fun handleSaveDeck(mixer: Mixer, deck: Deck, isDeckA: Boolean, isSaveAs: Boolean) {
        if (deck.source is llm.slop.liquidlsd.rendering.ExternalVideoSource) return
        val slot = DeckSlot.of(deck, mixer)
        val activeName = slot?.let { session.presetManager.activePreset(it) }
        if (activeName != null && !isSaveAs) {
            saveDeckPreset(mixer, activeName, deck, isDeckA)
        } else {
            val cached = slot?.let { session.presetManager.cachedDto(it) }
            val defaultName = if (activeName != null && isSaveAs) {
                generateUniqueCopyName(activeName)
            } else {
                activeName ?: ""
            }
            val defaultTags = cached?.tags ?: emptyList()
            SavePresetModal.request(
                title = "Save Preset As",
                confirmLabel = "Save",
                defaultName = defaultName,
                defaultTags = defaultTags,
                originalPath = activeName?.let { "library/presets/$it.lsd" }
            ) { name, tags ->
                saveDeckPreset(mixer, name, deck, isDeckA, tags)
            }
        }
    }

    fun handleEjectDeck(mixer: Mixer, deck: Deck, isDeckA: Boolean = false, isDeckPV: Boolean = false) {
        DeckSlot.of(deck, mixer)?.let { DeckOps.request(it, DeckChange.Eject) }
    }

    fun loadDeckPresetSafely(mixer: Mixer, deck: Deck, file: File) {
        DeckSlot.of(deck, mixer)?.let { DeckOps.request(it, DeckChange.Preset(file)) }
    }

    fun newPresetSafely(mixer: Mixer, deck: Deck) {
        DeckSlot.of(deck, mixer)?.let { DeckOps.request(it, DeckChange.Eject) }
    }

    /** [state] and [deckLabel] are unused now: [DeckOps] resets the selection and SRC sub-tab itself. */
    fun changeVisualSourceSafely(
        mixer: Mixer,
        deck: Deck,
        deckLabel: String,
        newSource: llm.slop.liquidlsd.rendering.VisualSource,
        state: ParametersState
    ) {
        DeckSlot.of(deck, mixer)?.let { DeckOps.request(it, DeckChange.Source(newSource)) }
    }

    fun generateUniqueCopyName(baseName: String): String {
        val presetsDir = FileSystemManager.getPresetsRoot()
        val cleanBase = baseName.removeSuffix(".lsd").trim()
        val candidate1 = "${cleanBase}_copy"
        if (!File(presetsDir, "$candidate1.lsd").exists()) return candidate1

        var idx = 2
        while (true) {
            val candidate = "${cleanBase}_copy$idx"
            if (!File(presetsDir, "$candidate.lsd").exists()) return candidate
            idx++
        }
    }

    fun triggerDeckDragDrop(file: File, deck: Deck, isDeckA: Boolean, mixer: Mixer) {
        loadDeckPresetSafely(mixer, deck, file)
    }
}
