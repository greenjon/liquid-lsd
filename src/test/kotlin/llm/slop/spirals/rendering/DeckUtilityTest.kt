package llm.slop.spirals.rendering

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import llm.slop.spirals.models.DeckPatchDto
import llm.slop.spirals.models.applyDto
import llm.slop.spirals.models.toDto
import llm.slop.spirals.patches.PatchManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeckUtilityTest {

    @BeforeTest
    fun setup() {
        mockkStatic("llm.slop.spirals.models.PatchModelsKt")
        PatchManager.activePresetA = null
        PatchManager.activePresetB = null
        PatchManager.activePresetC = null
        PatchManager.cachedDtoA = null
        PatchManager.cachedDtoB = null
        PatchManager.cachedDtoC = null
    }

    @Test
    fun testCopyDeck() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>()
        val deckB = mockk<Deck>()
        
        every { mixer.deckA } returns deckA
        every { mixer.deckB } returns deckB
        every { mixer.deckC } returns mockk()

        val dtoA = mockk<DeckPatchDto>()
        every { dtoA.name } returns "Patch A"
        every { deckA.toDto(any(), any()) } returns dtoA
        every { deckB.applyDto(any()) } returns Unit

        PatchManager.copyDeck(mixer, deckA, deckB)

        verify { deckA.toDto(any()) }
        verify { deckB.applyDto(dtoA) }
        assertEquals("Patch A", PatchManager.activePresetB)
    }

    @Test
    fun testMoveDeck() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>()
        val deckB = mockk<Deck>()
        
        every { mixer.deckA } returns deckA
        every { mixer.deckB } returns deckB
        every { mixer.deckC } returns mockk()

        val dtoA = mockk<DeckPatchDto>()
        every { dtoA.name } returns "Patch A"
        every { deckA.toDto(any(), any()) } returns dtoA
        every { deckB.applyDto(any()) } returns Unit
        every { deckA.reset() } returns Unit

        PatchManager.moveDeck(mixer, deckA, deckB)

        verify { deckA.toDto(any()) }
        verify { deckB.applyDto(dtoA) }
        verify { deckA.reset() }
        assertEquals("Patch A", PatchManager.activePresetB)
        assertNull(PatchManager.activePresetA)
    }

    @Test
    fun testSwapDecks() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>()
        val deckB = mockk<Deck>()
        
        every { mixer.deckA } returns deckA
        every { mixer.deckB } returns deckB
        every { mixer.deckC } returns mockk()

        val dtoA = mockk<DeckPatchDto>()
        every { dtoA.name } returns "Patch A"
        val dtoB = mockk<DeckPatchDto>()
        every { dtoB.name } returns "Patch B"

        every { deckA.toDto(any(), any()) } returns dtoA
        every { deckB.toDto(any(), any()) } returns dtoB
        every { deckA.applyDto(any()) } returns Unit
        every { deckB.applyDto(any()) } returns Unit

        PatchManager.swapDecks(mixer, deckA, deckB)

        verify { deckA.applyDto(dtoB) }
        verify { deckB.applyDto(dtoA) }
        assertEquals("Patch B", PatchManager.activePresetA)
        assertEquals("Patch A", PatchManager.activePresetB)
    }
}
