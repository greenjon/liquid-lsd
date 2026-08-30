package llm.slop.liquidlsd.presets

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetDirtyLoadingTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @BeforeTest
    fun setup() {
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
    fun testApplyPresetSetsCleanCachedDto() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>(relaxed = true)
        every { mixer.deckA } returns deckA

        val morphParam = ModulatableParameter(0.0f)
        val stellationParam = ModulatableParameter(0.0f)
        val supportHParam = ModulatableParameter(0.0f)
        val globalAlphaParam = ModulatableParameter(1.0f)
        
        val dynSource = mockk<DynamicVisualSource>(relaxed = true)
        val sourceParams = linkedMapOf(
            "Morph" to morphParam,
            "Stellation" to stellationParam,
            "Support H" to supportHParam
        )
        every { dynSource.id } returns "icosa_dodeca"
        every { dynSource.parameters } returns sourceParams
        every { dynSource.globalAlpha } returns globalAlphaParam

        every { deckA.source } returns dynSource
        every { deckA.availableSources } returns mutableListOf(dynSource)

        val file = File("library/presets/icosa2.lsd")
        assertTrue(file.exists(), "icosa2.lsd must exist")
        val presetDto = json.decodeFromString<DeckPresetDto>(file.readText())

        // Load preset through queue
        PresetManager.deckAPresetQueue.offer(PresetManager.PendingDeckLoad(presetDto, isManual = true))
        PresetManager.applyPendingPresets(mixer)

        // Ensure active preset and cached DTO are updated
        assertEquals("icosa2", PresetManager.activePresetA)
        assertFalse(PresetManager.isDeckDirty(deckA, mixer), "Deck should NOT be dirty immediately after applying icosa2 preset")
    }

    @Test
    fun testMandelboxLoadsCleanly() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>(relaxed = true)
        every { mixer.deckA } returns deckA

        val dynSource = mockk<DynamicVisualSource>(relaxed = true)
        val sourceParams = linkedMapOf<String, ModulatableParameter>()
        every { dynSource.id } returns "chladni"
        every { dynSource.parameters } returns sourceParams
        every { dynSource.globalAlpha } returns ModulatableParameter(1.0f)

        every { deckA.source } returns dynSource
        every { deckA.availableSources } returns mutableListOf(dynSource)

        val file = File("library/presets/mandelbox.lsd")
        assertTrue(file.exists(), "mandelbox.lsd must exist")
        val presetDto = json.decodeFromString<DeckPresetDto>(file.readText())

        PresetManager.deckAPresetQueue.offer(PresetManager.PendingDeckLoad(presetDto, isManual = true))
        PresetManager.applyPendingPresets(mixer)

        assertEquals("mandelbox", PresetManager.activePresetA)
        assertFalse(PresetManager.isDeckDirty(deckA, mixer), "Deck should NOT be dirty immediately after applying mandelbox preset")
    }

    @Test
    fun testModifyingParameterTripsDirtyState() {
        val mixer = mockk<Mixer>()
        val deckA = mockk<Deck>(relaxed = true)
        every { mixer.deckA } returns deckA

        val morphParam = ModulatableParameter(0.0f)
        val dynSource = mockk<DynamicVisualSource>(relaxed = true)
        val sourceParams = linkedMapOf("Morph" to morphParam)
        every { dynSource.id } returns "icosa_dodeca"
        every { dynSource.parameters } returns sourceParams
        every { dynSource.globalAlpha } returns ModulatableParameter(1.0f)

        every { deckA.source } returns dynSource
        every { deckA.availableSources } returns mutableListOf(dynSource)

        val file = File("library/presets/icosa2.lsd")
        val presetDto = json.decodeFromString<DeckPresetDto>(file.readText())

        PresetManager.deckAPresetQueue.offer(PresetManager.PendingDeckLoad(presetDto, isManual = true))
        PresetManager.applyPendingPresets(mixer)

        assertFalse(PresetManager.isDeckDirty(deckA, mixer), "Deck should be clean initially")

        // Modify parameter
        morphParam.baseValue = 0.75f
        assertTrue(PresetManager.isDeckDirty(deckA, mixer), "Deck MUST be dirty after modifying a static baseValue")
    }

    @Test
    fun testSanitizePresetDtoFillsMissingAndStripsLegacy() {
        val rawLegacyDto = DeckPresetDto(
            name = "legacy_test",
            visualSourceType = "icosa_dodeca",
            parameters = mapOf(
                "Morph" to ParameterDto(0.5f, 0.0f, 1.0f, false, emptyList()),
                "ObsoleteKey" to ParameterDto(1.0f, 0.0f, 1.0f, false, emptyList())
            ),
            feedbackParameters = mapOf(
                "legacyFeedback" to ParameterDto(0.5f, 0.0f, 1.0f, false, emptyList())
            ),
            globalAlpha = null
        )

        val (sanitized, wasMigrated) = PresetManager.sanitizePresetDto(rawLegacyDto)
        assertTrue(wasMigrated, "Legacy preset must be flagged as migrated")
        assertFalse(sanitized.parameters.containsKey("ObsoleteKey"), "Obsolete keys must be removed")
        assertFalse(sanitized.feedbackParameters.containsKey("legacyFeedback"), "Legacy feedback keys must be removed")
        assertTrue(sanitized.parameters.containsKey("Stellation"), "Missing Stellation parameter must be populated")
        assertTrue(sanitized.parameters.containsKey("Support H"), "Missing Support H parameter must be populated")
        assertTrue(sanitized.feedbackParameters.containsKey("fbDecay"), "Missing fbDecay must be populated")
        assertTrue(sanitized.feedbackParameters.containsKey("fbKaleido"), "Missing fbKaleido must be populated")
        assertEquals(0.5f, sanitized.parameters["Morph"]?.baseValue, "Existing parameter values must be preserved")
        assertEquals(1.0f, sanitized.globalAlpha?.baseValue, "Default globalAlpha must be populated")
    }

    @Test
    fun testSanitizePresetDtoDoesNotModifyCleanPreset() {
        val file = File("library/presets/icosa2.lsd")
        val cleanDto = json.decodeFromString<DeckPresetDto>(file.readText())
        val (_, wasMigrated) = PresetManager.sanitizePresetDto(cleanDto)
        assertFalse(wasMigrated, "Clean preset should not trigger migration")
    }
}
