package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.ui.UITheme

/**
 * Decks that the output can't show cost less. PV only feeds its monitor, so it renders at half the output size.
 * With [UITheme.offAirHalfRate] on, an off-air deck (one the master can't show at the moment) renders every other
 * frame. Its monitor runs at half rate then, and frame-stepped simulations and feedback trails advance at half
 * speed until the deck comes on air. A deck sent out over Spout / Syphon / PipeWire always renders in full.
 */
object DeckRenderBudget {
    private var frame = 0L

    /** Call once per rendered frame, before asking [shouldRender]. */
    fun nextFrame() {
        frame++
    }

    fun isStreamed(slot: DeckSlot): Boolean =
        UITheme.settings.videoOutputConfigs[endpointOf(slot)]?.isEnabled == true

    fun endpointOf(slot: DeckSlot): VideoOutputEndpoint = when (slot) {
        DeckSlot.A -> VideoOutputEndpoint.DECK_A
        DeckSlot.B -> VideoOutputEndpoint.DECK_B
        DeckSlot.BG -> VideoOutputEndpoint.DECK_BG
        DeckSlot.PV -> VideoOutputEndpoint.DECK_PV
    }

    /** PV's render size for an output of [width] x [height]: half each way, or full when PV is streamed. */
    fun pvSize(width: Int, height: Int, streamed: Boolean): Pair<Int, Int> =
        if (streamed) width to height else (width / 2).coerceAtLeast(1) to (height / 2).coerceAtLeast(1)

    /** True when the master output can't show [slot] right now: PV always; A or B faded fully across or at level 0; BG at level 0; all of them at master level 0. */
    fun isOffAir(slot: DeckSlot, mixer: Mixer): Boolean {
        if (slot == DeckSlot.PV || mixer.masterLevel.value <= 0f) return true
        val progress = (mixer.crossfade.value + 1f) * 0.5f
        return when (slot) {
            DeckSlot.A -> progress >= 1f || mixer.levelA.value <= 0f
            DeckSlot.B -> progress <= 0f || mixer.levelB.value <= 0f
            DeckSlot.BG -> mixer.levelBG.value <= 0f
            DeckSlot.PV -> true
        }
    }

    /** Whether [slot] renders this frame. Off-air decks take turns (A and BG on one frame, B and PV on the next) so the load stays even. */
    fun shouldRender(slot: DeckSlot, mixer: Mixer): Boolean =
        !UITheme.offAirHalfRate || isStreamed(slot) || !isOffAir(slot, mixer) || (frame + slot.index) % 2 == 0L
}
