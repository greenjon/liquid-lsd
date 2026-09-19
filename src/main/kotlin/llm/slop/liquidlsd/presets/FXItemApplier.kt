package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.rendering.Deck
import mu.KotlinLogging
import java.io.File

/**
 * Resolves a playlist/queue FX item (a .lsdfx or .lsdfxchain file) and applies it
 * to a deck deterministically — always all 4 slots, never "first vacant slot" —
 * so a playlist/queue step reproduces the exact same FX state every time it's
 * loaded, the same guarantee a saved chain already provides via Deck.applyFxChain.
 */
object FXItemApplier {
    private val logger = KotlinLogging.logger {}

    fun apply(session: SessionContext, file: File, deck: Deck) {
        when (file.extension.lowercase()) {
            "lsdfxchain" -> {
                session.presetRepository.loadFxChainAsync(file).thenAccept { chainDto ->
                    deck.applyFxChain(chainDto)
                }
            }
            "lsdfx" -> {
                session.presetRepository.loadFxPresetAsync(file).thenAccept { presetDto ->
                    deck.applyFxChain(FXChainDto(name = presetDto.name, tags = presetDto.tags, slots = listOf(presetDto.slot, null, null, null)))
                }
            }
            else -> {
                logger.warn { "Unrecognized FX item extension for ${file.name}, ignoring" }
            }
        }
    }
}
