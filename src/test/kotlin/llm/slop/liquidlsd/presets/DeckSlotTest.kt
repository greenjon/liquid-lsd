package llm.slop.liquidlsd.presets

import io.mockk.mockk
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DeckSlotTest {
    private val a = mockk<Deck>(relaxed = true)
    private val b = mockk<Deck>(relaxed = true)
    private val bg = mockk<Deck>(relaxed = true)
    private val pv = mockk<Deck>(relaxed = true)
    private val mixer = mockk<Mixer>(relaxed = true).also {
        io.mockk.every { it.deckA } returns a
        io.mockk.every { it.deckB } returns b
        io.mockk.every { it.deckBG } returns bg
        io.mockk.every { it.deckPV } returns pv
    }

    @AfterTest
    fun reset() = DeckSlot.values().forEach { PresetManager.clearActive(it); PresetManager.setMtime(it, null) }

    @Test
    fun ofMapsEachDeckAndRoundTripsThroughDeck() {
        for ((deck, slot) in listOf(a to DeckSlot.A, b to DeckSlot.B, bg to DeckSlot.BG, pv to DeckSlot.PV)) {
            assertEquals(slot, DeckSlot.of(deck, mixer))
            assertSame(deck, slot.deck(mixer))
        }
        assertNull(DeckSlot.of(mockk(relaxed = true), mixer))
    }

    @Test
    fun labelsAndBankIdsMatchTheCanonicalOnes() {
        for (slot in DeckSlot.values()) {
            assertEquals(MacroEngine.canonicalIdForDeckLabel(slot.label), slot.bankId)
        }
        assertEquals(listOf(0, 1, 2, 3), DeckSlot.values().map { it.index })
    }

    @Test
    fun ofFlagsDefaultsToDeckBLikeTheLegacyLoader() {
        assertEquals(DeckSlot.A, DeckSlot.ofFlags(true, false, false))
        assertEquals(DeckSlot.BG, DeckSlot.ofFlags(false, true, false))
        assertEquals(DeckSlot.PV, DeckSlot.ofFlags(false, false, true))
        assertEquals(DeckSlot.B, DeckSlot.ofFlags(false, false, false))
    }

    @Test
    fun accessorsReadAndWriteTheLegacyBackingFields() {
        val dto = io.mockk.mockk<DeckPresetDto>(relaxed = true)
        PresetManager.setActive(DeckSlot.BG, "x", dto)
        assertEquals("x", PresetManager.activePresetBG)
        assertSame(dto, PresetManager.cachedDtoBG)
        assertEquals("x", PresetManager.activePreset(DeckSlot.BG))
        assertNull(PresetManager.activePreset(DeckSlot.A))

        PresetManager.setMtime(DeckSlot.PV, 42L)
        assertEquals(42L, PresetManager.activePresetMtimePV)
        assertEquals(42L, PresetManager.mtime(DeckSlot.PV))

        PresetManager.clearActive(DeckSlot.BG)
        assertNull(PresetManager.activePresetBG)
        assertNull(PresetManager.cachedDtoBG)
    }
}
