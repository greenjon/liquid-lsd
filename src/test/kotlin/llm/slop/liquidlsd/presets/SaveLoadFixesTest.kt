package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSource
import llm.slop.liquidlsd.parameters.ModulatableParameter
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SaveLoadFixesTest {

    @Test
    fun testEmptyDeckDtoApplyResetsSourceToMandala() {
        val masterMandala = mockk<Mandala>(relaxed = true)
        every { masterMandala.id } returns "mandala"

        val deck = mockk<Deck>(relaxed = true)
        val sources = mutableListOf<VisualSource>(masterMandala)
        every { deck.availableSources } returns sources
        
        var assignedSource: VisualSource? = null
        every { deck.source = any() } answers { assignedSource = firstArg() }
        every { deck.source } answers { assignedSource ?: masterMandala }

        val emptyDto = DeckPresetDto(
            name = "Empty",
            visualSourceType = "kifs",
            parameters = emptyMap(),
            feedbackParameters = emptyMap(),
            globalAlpha = ParameterDto(1f, 0f, 1f, false, emptyList()),
            isEmpty = true
        )

        deck.applyDto(emptyDto)

        verify { deck.reset() }
        assertEquals("mandala", (deck.source as? Mandala)?.id)
    }

    @Test
    fun testParameterDtoEqualsDetectsBaseValueChangesWhenNotRandomized() {
        val param1 = ParameterDto(baseValue = 0.2f, baseMin = 0.0f, baseMax = 1.0f, randomizeBase = false, modulators = emptyList())
        val param2 = ParameterDto(baseValue = 0.8f, baseMin = 0.0f, baseMax = 1.0f, randomizeBase = false, modulators = emptyList())
        val param1Same = ParameterDto(baseValue = 0.2f, baseMin = 0.0f, baseMax = 1.0f, randomizeBase = false, modulators = emptyList())

        assertEquals(param1, param1Same)
        assertFalse(param1 == param2, "ParameterDto with different baseValue and randomizeBase=false must not be equal")
    }

    @Test
    fun testParameterDtoEqualsIgnoresBaseValueChangesWhenRandomized() {
        val param1 = ParameterDto(baseValue = 0.2f, baseMin = 0.0f, baseMax = 1.0f, randomizeBase = true, modulators = emptyList())
        val param2 = ParameterDto(baseValue = 0.8f, baseMin = 0.0f, baseMax = 1.0f, randomizeBase = true, modulators = emptyList())

        assertEquals(param1, param2, "ParameterDto with randomizeBase=true should ignore baseValue differences")
    }

    @Test
    fun testSyncQueueTriggerPrevValuesPreventsFalseTriggerOnStartup() {
        val mixer = mockk<Mixer>(relaxed = true)
        val queueNextParam = ModulatableParameter(0.8f, minClamp = 0f, maxClamp = 1f)
        val queuePrevParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val bgQueueNextParam = ModulatableParameter(0.8f, minClamp = 0f, maxClamp = 1f)
        val bgQueuePrevParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        
        every { mixer.queueNext } returns queueNextParam
        every { mixer.queuePrev } returns queuePrevParam
        every { mixer.bgQueueNext } returns bgQueueNextParam
        every { mixer.bgQueuePrev } returns bgQueuePrevParam
        every { mixer.syncQueueTriggerPrevValues() } answers { callOriginal() }
        every { mixer.pollQueueAdvance() } answers { callOriginal() }
        every { mixer.pollBgQueueAdvance() } answers { callOriginal() }

        mixer.syncQueueTriggerPrevValues()

        val delta = mixer.pollQueueAdvance()
        assertEquals(0, delta, "pollQueueAdvance must return 0 after syncQueueTriggerPrevValues on session load")
        assertEquals(0f, mixer.queueNext.baseValue, "baseValue should be reset to 0f after polling")

        val bgDelta = mixer.pollBgQueueAdvance()
        assertEquals(0, bgDelta, "pollBgQueueAdvance must return 0 after syncQueueTriggerPrevValues on session load")
        assertEquals(0f, mixer.bgQueueNext.baseValue, "baseValue should be reset to 0f after polling")
    }

    @Test
    fun testViewParametersSerializationBackwardCompatibility() {
        // Test that an older preset DTO without viewParameters defaults to emptyMap and deserializes cleanly
        val jsonStr = """
            {
                "version": 1,
                "name": "Legacy Preset",
                "visualSourceType": "chladni",
                "parameters": {},
                "feedbackParameters": {}
            }
        """.trimIndent()

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val dto = json.decodeFromString<DeckPresetDto>(jsonStr)
        assertEquals(0, dto.viewParameters.size, "viewParameters must default to emptyMap when not present in JSON")
    }
}
