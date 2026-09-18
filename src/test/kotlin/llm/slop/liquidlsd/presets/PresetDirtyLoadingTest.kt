package llm.slop.liquidlsd.presets

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSource
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresetDirtyLoadingTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @BeforeTest
    fun setup() {
        mockkStatic("llm.slop.liquidlsd.models.PresetModelsKt")
        PresetManager.activePresetA = null
        PresetManager.activePresetB = null
        PresetManager.activePresetBG = null
        PresetManager.activePresetPV = null
        PresetManager.cachedDtoA = null
        PresetManager.cachedDtoB = null
        PresetManager.cachedDtoBG = null
        PresetManager.cachedDtoPV = null
        PresetManager.deckAPresetQueue.clear()
        PresetManager.deckBPresetQueue.clear()
        PresetManager.deckBGPresetQueue.clear()
        PresetManager.deckPVPresetQueue.clear()
    }

    // --- Preset Decoding & Sanitization Tests ---

    @Test
    fun testAllPresetFilesDecodeSuccessfully() {
        val presetsDir = File("library/presets")
        val files = presetsDir.listFiles { f -> f.extension == "lsd" } ?: emptyArray()
        assertTrue(files.isNotEmpty(), "Preset library should not be empty")

        for (file in files) {
            val content = file.readText()
            val dto = json.decodeFromString<DeckPresetDto>(content)
            assertTrue(dto.visualSourceType.isNotBlank(), "Preset ${file.name} must have a valid visualSourceType")
        }
    }

    @Test
    fun testSanitizePresetDtoFillsMissingAndStripsLegacy() {
        val rawLegacyDto = DeckPresetDto(
            name = "legacy_test",
            visualSourceType = "mandala",
            parameters = mapOf(
                "Thickness" to ParameterDto(0.5f, 0.0f, 1.0f, false, emptyList()),
                "ObsoleteKey" to ParameterDto(1.0f, 0.0f, 1.0f, false, emptyList())
            ),
            feedbackParameters = mapOf(
                "legacyFeedback" to ParameterDto(0.5f, 0.0f, 1.0f, false, emptyList())
            ),
            globalAlpha = null
        )

        val (sanitized, wasMigrated) = PresetMigrator.sanitizePresetDto(rawLegacyDto)
        assertTrue(wasMigrated, "Legacy preset must be flagged as migrated")
        assertFalse(sanitized.parameters.containsKey("ObsoleteKey"), "Obsolete keys must be removed")
        assertFalse(sanitized.feedbackParameters.containsKey("legacyFeedback"), "Legacy feedback keys must be removed")
        assertTrue(sanitized.parameters.containsKey("L1"), "Missing L1 parameter must be populated")
        assertTrue(sanitized.parameters.containsKey("L2"), "Missing L2 parameter must be populated")
        assertTrue(sanitized.feedbackParameters.containsKey("fbDecay"), "Missing fbDecay must be populated")
        assertTrue(sanitized.feedbackParameters.containsKey("fbKaleido"), "Missing fbKaleido must be populated")
        assertEquals(0.5f, sanitized.parameters["Thickness"]?.baseValue, "Existing parameter values must be preserved")
        assertEquals(1.0f, sanitized.globalAlpha?.baseValue, "Default globalAlpha must be populated")
    }

    @Test
    fun testSanitizePresetDtoDoesNotModifyCleanPreset() {
        val file = File("library/presets/test_preset_a.lsd")
        val rawDto = json.decodeFromString<DeckPresetDto>(file.readText())
        val (cleanDto, _) = PresetMigrator.sanitizePresetDto(rawDto)
        val (_, wasMigrated) = PresetMigrator.sanitizePresetDto(cleanDto)
        assertFalse(wasMigrated, "Clean preset should not trigger migration")
    }

    @Test
    fun testViewParametersSerializationBackwardCompatibility() {
        val jsonStr = """
            {
                "version": 1,
                "name": "Legacy Preset",
                "visualSourceType": "chladni",
                "parameters": {},
                "feedbackParameters": {}
            }
        """.trimIndent()

        val dto = json.decodeFromString<DeckPresetDto>(jsonStr)
        assertEquals(0, dto.viewParameters.size, "viewParameters must default to emptyMap when not present in JSON")
    }

    // --- Dirty State & Modification Tracking Tests ---

    @Test
    fun testApplyPresetSetsCleanCachedDto() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>(relaxed = true)
        every { mixer.deckA } returns deckA

        val morphParam = ModulatableParameter(0.0f)
        val globalAlphaParam = ModulatableParameter(1.0f)

        val dynSource = mockk<DynamicVisualSource>(relaxed = true)
        val sourceParams = linkedMapOf("Petals" to morphParam)
        every { dynSource.id } returns "mandala"
        every { dynSource.parameters } returns sourceParams
        every { dynSource.globalAlpha } returns globalAlphaParam

        every { deckA.source } returns dynSource
        every { deckA.availableSources } returns mutableListOf(dynSource)

        val file = File("library/presets/test_preset_a.lsd")
        assertTrue(file.exists(), "test_preset_a.lsd must exist")
        val presetDto = json.decodeFromString<DeckPresetDto>(file.readText())

        // Load preset through queue
        PresetManager.deckAPresetQueue.offer(PresetManager.PendingDeckLoad(presetDto, isManual = true))
        PresetManager.applyPendingPresets(mixer)

        // Ensure active preset and cached DTO are updated cleanly
        assertEquals("test_preset_a", PresetManager.activePresetA)
        assertFalse(PresetManager.isDeckDirty(deckA, mixer), "Deck should NOT be dirty immediately after applying test_preset_a preset")
    }

    @Test
    fun testModifyingParameterTripsDirtyState() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>(relaxed = true)
        every { mixer.deckA } returns deckA

        val morphParam = ModulatableParameter(0.0f)
        val dynSource = mockk<DynamicVisualSource>(relaxed = true)
        val sourceParams = linkedMapOf("Petals" to morphParam)
        every { dynSource.id } returns "mandala"
        every { dynSource.parameters } returns sourceParams
        every { dynSource.globalAlpha } returns ModulatableParameter(0.0f)

        every { deckA.source } returns dynSource
        every { deckA.availableSources } returns mutableListOf(dynSource)

        val file = File("library/presets/test_preset_a.lsd")
        val presetDto = json.decodeFromString<DeckPresetDto>(file.readText())

        PresetManager.deckAPresetQueue.offer(PresetManager.PendingDeckLoad(presetDto, isManual = true))
        PresetManager.applyPendingPresets(mixer)

        assertFalse(PresetManager.isDeckDirty(deckA, mixer), "Deck should be clean initially")

        // Modify parameter
        morphParam.baseValue = 0.75f
        assertTrue(PresetManager.isDeckDirty(deckA, mixer), "Deck MUST be dirty after modifying a static baseValue")
    }

    @Test
    fun testRangeDirtyStateAndParameterEquals() {
        // Static parameter (randomizeBase = false): baseValue edits MUST trigger dirty state
        val staticInitial = ParameterDto(0.5f, 0.1f, 0.9f, false, emptyList())
        val staticOther = ParameterDto(0.7f, 0.1f, 0.9f, false, emptyList())
        assertNotEquals(staticInitial, staticOther)

        // Randomized parameter (randomizeBase = true): baseValue differences are ignored
        val randomInitial = ParameterDto(0.5f, 0.1f, 0.9f, true, emptyList())
        val randomOther = ParameterDto(0.7f, 0.1f, 0.9f, true, emptyList())
        assertEquals(randomInitial, randomOther)

        val mixer = mockk<Mixer>()
        val deck = mockk<Deck>()
        every { mixer.deckA } returns deck
        every { mixer.deckB } returns mockk()

        val globalAlpha = ParameterDto(1f, 0f, 1f, false, emptyList())

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

    // --- Deck State Operations (Empty / Reset / Sync) ---

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
    fun testStartEmptyResetsAllDecksAndActivePresets() {
        val mixer = mockk<Mixer>(relaxed = true)
        val deckA = mockk<Deck>(relaxed = true)
        val deckB = mockk<Deck>(relaxed = true)
        val deckBG = mockk<Deck>(relaxed = true)
        val deckPV = mockk<Deck>(relaxed = true)

        every { mixer.deckA } returns deckA
        every { mixer.deckB } returns deckB
        every { mixer.deckBG } returns deckBG
        every { mixer.deckPV } returns deckPV

        PresetManager.activePresetA = "SomePresetA"
        PresetManager.activePresetB = "SomePresetB"
        PresetManager.activePresetBG = "SomePresetBG"
        PresetManager.activePresetPV = "SomePresetPV"

        PresetManager.startEmpty(mixer)

        verify { deckA.reset() }
        verify { deckB.reset() }
        verify { deckBG.reset() }
        verify { deckPV.reset() }
        assertNull(PresetManager.activePresetA)
        assertNull(PresetManager.activePresetB)
        assertNull(PresetManager.activePresetBG)
        assertNull(PresetManager.activePresetPV)
    }

    @Test
    fun testSyncQueueTriggerPrevValuesPreventsFalseTriggerOnStartup() {
        val mixer = mockk<Mixer>(relaxed = true)
        val queueNextParam = ModulatableParameter(0.8f, minClamp = 0f, maxClamp = 1f)
        val queuePrevParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val bgQueueNextParam = ModulatableParameter(0.8f, minClamp = 0f, maxClamp = 1f)
        val bgQueuePrevParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val transQueueNextParam = ModulatableParameter(0.8f, minClamp = 0f, maxClamp = 1f)
        val transQueuePrevParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val tapTempoParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)

        every { mixer.queueNext } returns queueNextParam
        every { mixer.queuePrev } returns queuePrevParam
        every { mixer.bgQueueNext } returns bgQueueNextParam
        every { mixer.bgQueuePrev } returns bgQueuePrevParam
        every { mixer.transQueueNext } returns transQueueNextParam
        every { mixer.transQueuePrev } returns transQueuePrevParam
        every { mixer.tapTempo } returns tapTempoParam
        every { mixer.syncQueueTriggerPrevValues() } answers { callOriginal() }
        every { mixer.pollQueueAdvance() } answers { callOriginal() }
        every { mixer.pollBgQueueAdvance() } answers { callOriginal() }
        every { mixer.pollTransQueueAdvance() } answers { callOriginal() }

        mixer.syncQueueTriggerPrevValues()

        val delta = mixer.pollQueueAdvance()
        assertEquals(0, delta, "pollQueueAdvance must return 0 after syncQueueTriggerPrevValues on session load")
        assertEquals(0f, mixer.queueNext.baseValue, "baseValue should be reset to 0f after polling")

        val bgDelta = mixer.pollBgQueueAdvance()
        assertEquals(0, bgDelta, "pollBgQueueAdvance must return 0 after syncQueueTriggerPrevValues on session load")
        assertEquals(0f, mixer.bgQueueNext.baseValue, "baseValue should be reset to 0f after polling")

        val transDelta = mixer.pollTransQueueAdvance()
        assertEquals(0, transDelta, "pollTransQueueAdvance must return 0 after syncQueueTriggerPrevValues on session load")
        assertEquals(0f, mixer.transQueueNext.baseValue, "baseValue should be reset to 0f after polling")
    }
}
