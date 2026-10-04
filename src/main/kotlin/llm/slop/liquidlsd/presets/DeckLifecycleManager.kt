package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.models.*
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Deck lifecycle operations: clearing, copying, moving, and swapping the contents
 * (and associated active-preset bookkeeping) of decks within a [Mixer].
 *
 * These operations read/write [PresetManager]'s active-preset name and cached-DTO
 * state for each deck slot (A/B/BG/PV), since that bookkeeping travels alongside
 * the deck contents whenever a deck is cleared, copied, moved, or swapped.
 */
object DeckLifecycleManager {

    /** Captures [deck]'s active-preset name and cached DTO; the returned lambda puts them back (used to undo a source change). */
    fun captureActivePreset(deck: Deck, mixer: Mixer): () -> Unit {
        val slot = DeckSlot.of(deck, mixer) ?: return {}
        val name = PresetManager.activePreset(slot)
        val dto = PresetManager.cachedDto(slot)
        return { PresetManager.setActive(slot, name, dto) }
    }

    fun clearDeckActivePreset(deck: Deck, mixer: Mixer) {
        DeckSlot.of(deck, mixer)?.let { PresetManager.clearActive(it) }
    }

    /** The DTO describing [deck] as it is now, named after its active preset (or the slot label). */
    private fun currentDto(deck: Deck, mixer: Mixer): DeckPresetDto? {
        val slot = DeckSlot.of(deck, mixer) ?: return null
        val name = PresetManager.cachedDto(slot)?.name ?: slot.label
        return deck.toDto(name)
    }

    fun copyDeck(mixer: Mixer, from: Deck, to: Deck) {
        val toSlot = DeckSlot.of(to, mixer)
        if (from.isEmpty) {
            to.applyDto(PresetManager.emptyDeckDto(to, mixer))
            toSlot?.let { PresetManager.clearActive(it) }
            return
        }
        val fromDto = currentDto(from, mixer) ?: return
        to.applyDto(fromDto)
        toSlot?.let { PresetManager.setActive(it, fromDto.name, fromDto) }
    }

    fun moveDeck(mixer: Mixer, from: Deck, to: Deck) {
        copyDeck(mixer, from, to)
        from.applyDto(PresetManager.emptyDeckDto(from, mixer))
        DeckSlot.of(from, mixer)?.let { PresetManager.clearActive(it) }
    }

    fun swapDecks(mixer: Mixer, deck1: Deck, deck2: Deck) {
        val slot1 = DeckSlot.of(deck1, mixer) ?: return
        val slot2 = DeckSlot.of(deck2, mixer) ?: return
        val dto1 = currentDto(deck1, mixer) ?: return
        val dto2 = currentDto(deck2, mixer) ?: return

        deck1.applyDto(dto2)
        deck2.applyDto(dto1)

        PresetManager.setActive(slot1, dto2.name, dto2)
        PresetManager.setActive(slot2, dto1.name, dto1)
    }
}
