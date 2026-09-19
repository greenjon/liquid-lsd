package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Manages the live volatile FX Queue for Deck A / Deck B.
 * Advancing applies deterministically to the crossfader-active deck (A or B) via FXItemApplier.
 */
object FXQueueManager : FxQueueEngine(queueLabel = "FX queue", savePrefix = "AutoFX") {

    override fun getTargetDeck(mixer: Mixer): Deck {
        return if (mixer.crossfade.value <= 0.0f) mixer.deckA else mixer.deckB
    }

    override fun deckLabel(mixer: Mixer): String {
        return if (getTargetDeck(mixer) === mixer.deckA) "A" else "B"
    }

    override fun activePresetName(mixer: Mixer): String? {
        return if (getTargetDeck(mixer) === mixer.deckA) PresetManager.activePresetA else PresetManager.activePresetB
    }
}
