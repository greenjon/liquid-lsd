package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.rendering.liveDeck
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Manages the live volatile FX Queue for Deck A / Deck B.
 * Advancing applies deterministically to the crossfader-active deck (A or B) via FXItemApplier.
 */
object FXQueueManager : FxQueueEngine(queueLabel = "FX queue") {

    override fun getTargetDeck(mixer: Mixer): Deck {
        return mixer.liveDeck
    }
}
