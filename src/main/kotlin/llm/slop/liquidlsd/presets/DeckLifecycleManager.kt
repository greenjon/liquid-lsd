package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Per-deck active-preset bookkeeping. Copy, move, swap, eject and source changes live in [DeckOps].
 */
object DeckLifecycleManager {

    fun clearDeckActivePreset(deck: Deck, mixer: Mixer) {
        DeckSlot.of(deck, mixer)?.let { PresetManager.clearActive(it) }
    }
}
