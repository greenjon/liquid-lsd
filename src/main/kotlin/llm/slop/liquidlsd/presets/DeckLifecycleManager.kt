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

    fun clearDeckActivePreset(deck: Deck, mixer: Mixer) {
        when {
            deck === mixer.deckA -> { PresetManager.cachedDtoA = null; PresetManager.activePresetA = null }
            deck === mixer.deckB -> { PresetManager.cachedDtoB = null; PresetManager.activePresetB = null }
            deck === mixer.deckBG -> { PresetManager.cachedDtoBG = null; PresetManager.activePresetBG = null }
            deck === mixer.deckPV -> { PresetManager.cachedDtoPV = null; PresetManager.activePresetPV = null }
        }
    }

    fun copyDeck(mixer: Mixer, from: Deck, to: Deck) {
        if (from.isEmpty) {
            to.applyDto(PresetManager.emptyDeckDto(to, mixer))
            when {
                to === mixer.deckA -> { PresetManager.cachedDtoA = null; PresetManager.activePresetA = null }
                to === mixer.deckB -> { PresetManager.cachedDtoB = null; PresetManager.activePresetB = null }
                to === mixer.deckBG -> { PresetManager.cachedDtoBG = null; PresetManager.activePresetBG = null }
                to === mixer.deckPV -> { PresetManager.cachedDtoPV = null; PresetManager.activePresetPV = null }
            }
            return
        }
        val fromDto = when {
            from === mixer.deckA -> PresetManager.cachedDtoA?.let { from.toDto(it.name) } ?: from.toDto("Deck A")
            from === mixer.deckB -> PresetManager.cachedDtoB?.let { from.toDto(it.name) } ?: from.toDto("Deck B")
            from === mixer.deckBG -> PresetManager.cachedDtoBG?.let { from.toDto(it.name) } ?: from.toDto("Deck BG")
            from === mixer.deckPV -> PresetManager.cachedDtoPV?.let { from.toDto(it.name) } ?: from.toDto("Deck PV")
            else -> return
        }

        to.applyDto(fromDto)

        when {
            to === mixer.deckA -> { PresetManager.cachedDtoA = fromDto; PresetManager.activePresetA = fromDto.name }
            to === mixer.deckB -> { PresetManager.cachedDtoB = fromDto; PresetManager.activePresetB = fromDto.name }
            to === mixer.deckBG -> { PresetManager.cachedDtoBG = fromDto; PresetManager.activePresetBG = fromDto.name }
            to === mixer.deckPV -> { PresetManager.cachedDtoPV = fromDto; PresetManager.activePresetPV = fromDto.name }
        }
    }

    fun moveDeck(mixer: Mixer, from: Deck, to: Deck) {
        copyDeck(mixer, from, to)
        from.applyDto(PresetManager.emptyDeckDto(from, mixer))
        when {
            from === mixer.deckA -> { PresetManager.cachedDtoA = null; PresetManager.activePresetA = null }
            from === mixer.deckB -> { PresetManager.cachedDtoB = null; PresetManager.activePresetB = null }
            from === mixer.deckBG -> { PresetManager.cachedDtoBG = null; PresetManager.activePresetBG = null }
            from === mixer.deckPV -> { PresetManager.cachedDtoPV = null; PresetManager.activePresetPV = null }
        }
    }

    fun swapDecks(mixer: Mixer, deck1: Deck, deck2: Deck) {
        val dto1 = when {
            deck1 === mixer.deckA -> PresetManager.cachedDtoA?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck A")
            deck1 === mixer.deckB -> PresetManager.cachedDtoB?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck B")
            deck1 === mixer.deckBG -> PresetManager.cachedDtoBG?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck BG")
            deck1 === mixer.deckPV -> PresetManager.cachedDtoPV?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck PV")
            else -> return
        }
        val dto2 = when {
            deck2 === mixer.deckA -> PresetManager.cachedDtoA?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck A")
            deck2 === mixer.deckB -> PresetManager.cachedDtoB?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck B")
            deck2 === mixer.deckBG -> PresetManager.cachedDtoBG?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck BG")
            deck2 === mixer.deckPV -> PresetManager.cachedDtoPV?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck PV")
            else -> return
        }

        deck1.applyDto(dto2)
        deck2.applyDto(dto1)

        val oldDto1 = dto1
        val oldDto2 = dto2

        when {
            deck1 === mixer.deckA -> { PresetManager.cachedDtoA = oldDto2; PresetManager.activePresetA = oldDto2.name }
            deck1 === mixer.deckB -> { PresetManager.cachedDtoB = oldDto2; PresetManager.activePresetB = oldDto2.name }
            deck1 === mixer.deckBG -> { PresetManager.cachedDtoBG = oldDto2; PresetManager.activePresetBG = oldDto2.name }
            deck1 === mixer.deckPV -> { PresetManager.cachedDtoPV = oldDto2; PresetManager.activePresetPV = oldDto2.name }
        }
        when {
            deck2 === mixer.deckA -> { PresetManager.cachedDtoA = oldDto1; PresetManager.activePresetA = oldDto1.name }
            deck2 === mixer.deckB -> { PresetManager.cachedDtoB = oldDto1; PresetManager.activePresetB = oldDto1.name }
            deck2 === mixer.deckBG -> { PresetManager.cachedDtoBG = oldDto1; PresetManager.activePresetBG = oldDto1.name }
            deck2 === mixer.deckPV -> { PresetManager.cachedDtoPV = oldDto1; PresetManager.activePresetPV = oldDto1.name }
        }
    }
}
