package llm.slop.liquidlsd.rendering

/** The deck the crossfader is on (FX goes here: audible now). At exactly 0 it is Deck A. */
val Mixer.liveDeck: Deck get() = if (crossfade.value <= 0.0f) deckA else deckB

/** The deck the crossfader is moving away from (presets and sources go here: the next look). At exactly 0 it is Deck B. */
val Mixer.inactiveDeck: Deck get() = if (crossfade.value > 0.0f) deckA else deckB
