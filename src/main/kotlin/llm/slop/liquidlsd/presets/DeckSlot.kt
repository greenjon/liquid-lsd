package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer

/**
 * The four decks a preset or source can be loaded into. Replaces the hand-written
 * `deck === mixer.deckA -> ... deck === mixer.deckPV` chains that mapped a deck to its per-deck
 * preset bookkeeping, label and macro bank.
 */
enum class DeckSlot(val label: String, val bankId: String, val index: Int) {
    A("Deck A", MacroEngine.DECK_A, 0),
    B("Deck B", MacroEngine.DECK_B, 1),
    BG("Deck BG", MacroEngine.DECK_BG, 2),
    PV("Deck PV", MacroEngine.DECK_PV, 3);

    fun deck(mixer: Mixer): Deck = when (this) {
        A -> mixer.deckA
        B -> mixer.deckB
        BG -> mixer.deckBG
        PV -> mixer.deckPV
    }

    companion object {
        fun of(deck: Deck, mixer: Mixer): DeckSlot? = when {
            deck === mixer.deckA -> A
            deck === mixer.deckB -> B
            deck === mixer.deckBG -> BG
            deck === mixer.deckPV -> PV
            else -> null
        }

        /** Maps the legacy `isDeckA / isDeckBG / isDeckPV` flag triple (neither set = Deck B). */
        fun ofFlags(isDeckA: Boolean, isDeckBG: Boolean, isDeckPV: Boolean): DeckSlot = when {
            isDeckA -> A
            isDeckBG -> BG
            isDeckPV -> PV
            else -> B
        }
    }
}
