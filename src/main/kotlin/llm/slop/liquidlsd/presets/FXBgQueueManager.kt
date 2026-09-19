package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Manages the live volatile FX Queue for Background Deck (Deck BG).
 * Advancing applies deterministically to Deck BG via FXItemApplier.
 */
object FXBgQueueManager : FxQueueEngine(queueLabel = "BG FX queue", savePrefix = "AutoFX") {

    override fun getTargetDeck(mixer: Mixer): Deck = mixer.deckBG

    override fun deckLabel(mixer: Mixer): String = "BG"

    override fun activePresetName(mixer: Mixer): String? = PresetManager.activePresetBG
}
