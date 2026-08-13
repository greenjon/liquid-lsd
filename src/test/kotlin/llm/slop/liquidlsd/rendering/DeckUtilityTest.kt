package llm.slop.liquidlsd.rendering

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.presets.PresetManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeckUtilityTest {

    @BeforeTest
    fun setup() {
        mockkStatic("llm.slop.liquidlsd.models.PresetModelsKt")
        PresetManager.activePresetA = null
        PresetManager.activePresetB = null
        PresetManager.activePresetC = null
        PresetManager.cachedDtoA = null
        PresetManager.cachedDtoB = null
        PresetManager.cachedDtoC = null
    }

    @Test
    fun testCopyDeck() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>()
        val deckB = mockk<Deck>()
        
        every { mixer.deckA } returns deckA
        every { mixer.deckB } returns deckB
        every { mixer.deckC } returns mockk()

        every { deckA.isEmpty } returns false
        every { deckB.isEmpty } returns false
        
        val dtoA = mockk<DeckPresetDto>()
        every { dtoA.name } returns "Preset A"
        every { deckA.toDto(any(), any()) } returns dtoA
        every { deckB.applyDto(any()) } returns Unit

        PresetManager.copyDeck(mixer, deckA, deckB)

        verify { deckA.toDto(any()) }
        verify { deckB.applyDto(dtoA) }
        assertEquals("Preset A", PresetManager.activePresetB)
    }

    @Test
    fun testMoveDeck() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>()
        val deckB = mockk<Deck>()
        
        every { mixer.deckA } returns deckA
        every { mixer.deckB } returns deckB
        every { mixer.deckC } returns mockk()

        every { deckA.isEmpty } returns false
        every { deckB.isEmpty } returns false

        val dtoA = mockk<DeckPresetDto>()
        every { dtoA.name } returns "Preset A"
        every { deckA.toDto(any(), any()) } returns dtoA

        // emptyDeckDto calls dtoA.copy(isEmpty = true, visualSourceType = any()); return a distinct sentinel
        val emptyDto = mockk<DeckPresetDto>()
        every { dtoA.copy(isEmpty = true, visualSourceType = any()) } returns emptyDto

        every { deckB.applyDto(any()) } returns Unit
        // deckA.applyDto is called once with the empty DTO to clear the source deck
        every { deckA.applyDto(any()) } returns Unit

        PresetManager.moveDeck(mixer, deckA, deckB)

        verify { deckA.toDto(any()) }
        verify { deckB.applyDto(dtoA) }
        verify { deckA.applyDto(emptyDto) }
        assertEquals("Preset A", PresetManager.activePresetB)
        assertNull(PresetManager.activePresetA)
    }

    @Test
    fun testSwapDecks() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>()
        val deckB = mockk<Deck>()
        
        every { mixer.deckA } returns deckA
        every { mixer.deckB } returns deckB
        every { mixer.deckC } returns mockk()

        val dtoA = mockk<DeckPresetDto>()
        every { dtoA.name } returns "Preset A"
        val dtoB = mockk<DeckPresetDto>()
        every { dtoB.name } returns "Preset B"

        every { deckA.toDto(any(), any()) } returns dtoA
        every { deckB.toDto(any(), any()) } returns dtoB
        every { deckA.applyDto(any()) } returns Unit
        every { deckB.applyDto(any()) } returns Unit

        PresetManager.swapDecks(mixer, deckA, deckB)

        verify { deckA.applyDto(dtoB) }
        verify { deckB.applyDto(dtoA) }
        assertEquals("Preset B", PresetManager.activePresetA)
        assertEquals("Preset A", PresetManager.activePresetB)
    }
}
