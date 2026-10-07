package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.presets.DeckSlot

import llm.slop.liquidlsd.presets.DeckOps

import llm.slop.liquidlsd.presets.DeckChange

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.TangoPalette
import llm.slop.liquidlsd.ui.UITheme
import java.io.File

/**
 * Shared deck-button styling helpers used by [PresetListPanel] and the unified [BrowserPane].
 *
 * Each deck has a canonical RGBA accent colour, sourced from [TangoPalette] so no two decks (or
 * deck vs. status role) ever share a hue.
 */
internal object BrowserDeckButtons {

    // ── Deck accent colours (Tango Desktop Project hues; see TangoPalette) ──
    private val DECK_A   = TangoPalette.DECK_A.normal  // #F57900 orange
    private val DECK_B   = TangoPalette.DECK_B.normal  // #3465A4 sky blue
    private val DECK_BG  = TangoPalette.DECK_BG.normal // #73D216 chameleon green (matches Mixxx's own Deck 3)
    private val DECK_PV  = TangoPalette.DECK_PV.normal // #75507B plum

    fun colorA() = DECK_A
    fun colorB() = DECK_B
    fun colorBG() = DECK_BG
    fun colorPV() = DECK_PV

    /** Load a preset file into Deck A (1), B (2), BG (3), or PV (4) through [DeckOps] (dirty guard and undo). */
    fun loadPresetToDeck(session: SessionContext, mixer: Mixer, file: File, deckIndex: Int) {
        val slot = DeckSlot.entries.getOrNull(deckIndex - 1) ?: return
        DeckOps.request(slot, DeckChange.Preset(file))
    }
}
