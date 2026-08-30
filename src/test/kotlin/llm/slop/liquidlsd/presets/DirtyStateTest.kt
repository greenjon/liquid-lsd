package llm.slop.liquidlsd.presets

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.toDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirtyStateTest {

    @BeforeTest
    fun setup() {
        mockkStatic("llm.slop.liquidlsd.models.PresetModelsKt")
        PresetManager.activePresetA = null
        PresetManager.activePresetB = null
        PresetManager.activePresetPV = null
        PresetManager.cachedDtoA = null
        PresetManager.cachedDtoB = null
        PresetManager.cachedDtoPV = null
    }

    @Test
    fun testDeckDirtyState() {
        val mixer = mockk<Mixer>()
        val deck = mockk<Deck>()
        
        every { mixer.deckA } returns deck
        every { mixer.deckB } returns mockk()

        // Initial state should not be dirty because cachedDto is null
        assertFalse(PresetManager.isDeckDirty(deck, mixer))
        
        // "Load" a preset by setting cachedDto
        val initialDto = mockk<DeckPresetDto>()
        every { initialDto.name } returns "TestPreset"
        PresetManager.cachedDtoA = initialDto
        PresetManager.activePresetA = "TestPreset"
        
        // Mock toDto to return something EQUAL to initialDto
        every { deck.toDto(any(), any()) } returns initialDto
        
        // Now it should NOT be dirty
        assertFalse(PresetManager.isDeckDirty(deck, mixer))
        
        // Change a parameter (mock toDto to return something different)
        val modifiedDto = mockk<DeckPresetDto>()
        every { modifiedDto.name } returns "TestPreset"
        every { deck.toDto(any(), any()) } returns modifiedDto
        
        // Now it SHOULD be dirty
        assertTrue(PresetManager.isDeckDirty(deck, mixer))
    }

    @Test
    fun testRangeDirtyState() {
        // Static parameter (randomizeBase = false): baseValue edits MUST trigger dirty state
        val staticInitial = llm.slop.liquidlsd.models.ParameterDto(0.5f, 0.1f, 0.9f, false, emptyList())
        val staticOther = llm.slop.liquidlsd.models.ParameterDto(0.7f, 0.1f, 0.9f, false, emptyList())
        kotlin.test.assertNotEquals(staticInitial, staticOther)

        // Randomized parameter (randomizeBase = true): baseValue differences are ignored
        val randomInitial = llm.slop.liquidlsd.models.ParameterDto(0.5f, 0.1f, 0.9f, true, emptyList())
        val randomOther = llm.slop.liquidlsd.models.ParameterDto(0.7f, 0.1f, 0.9f, true, emptyList())
        assertEquals(randomInitial, randomOther)
        
        val mixer = mockk<Mixer>()
        val deck = mockk<Deck>()
        every { mixer.deckA } returns deck
        every { mixer.deckB } returns mockk()

        val globalAlpha = llm.slop.liquidlsd.models.ParameterDto(1f, 0f, 1f, false, emptyList())

        // Use real DeckPresetDto objects with static parameters
        val cachedDeckDto = DeckPresetDto(
            name = "Test",
            visualSourceType = "Mandala",
            parameters = mapOf("Lobes" to staticInitial),
            feedbackParameters = emptyMap(),
            globalAlpha = globalAlpha
        )
        
        PresetManager.cachedDtoA = cachedDeckDto
        PresetManager.activePresetA = "Test"

        val currentDeckDto = DeckPresetDto(
            name = "Test",
            visualSourceType = "Mandala",
            parameters = mapOf("Lobes" to staticOther),
            feedbackParameters = emptyMap(),
            globalAlpha = globalAlpha
        )

        every { deck.toDto(any(), any()) } returns currentDeckDto

        // Should BE dirty because slider baseValue changed on a static parameter
        assertTrue(PresetManager.isDeckDirty(deck, mixer))
    }
}
