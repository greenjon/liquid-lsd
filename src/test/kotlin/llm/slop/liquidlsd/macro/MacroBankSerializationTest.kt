package llm.slop.liquidlsd.macro

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.SessionStateDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File
import kotlin.test.*

class MacroBankSerializationTest {

    @BeforeTest
    fun setUp() {
        MacroEngine.registerBank(null, MacroBank())
    }

    @AfterTest
    fun tearDown() {
        MacroEngine.registerBank(null, MacroBank())
    }

    @Test
    fun testMacroBankRoundTripJson() {
        val binding = MacroBinding(
            parameterId = "Deck A/fbZoom",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = -1f,
            maxVal = 1f,
            curve = MacroCurveType.EXPONENTIAL,
            inverted = true
        )
        val knob = MacroControl(label = "WARP", value = 0.65f, bindings = mutableListOf(binding))
        val bank = MacroBank(knobs = listOf(knob))

        val jsonString = MacroBankSerializer.json.encodeToString(bank)
        val decoded = MacroBankSerializer.json.decodeFromString<MacroBank>(jsonString)

        assertEquals(1, decoded.knobs.size)
        val decodedKnob = decoded.knobs[0]
        assertEquals("WARP", decodedKnob.label)
        assertEquals(0.65f, decodedKnob.value)
        assertEquals(1, decodedKnob.bindings.size)

        val decodedBinding = decodedKnob.bindings[0]
        assertEquals("Deck A/fbZoom", decodedBinding.parameterId)
        assertEquals(MacroTargetType.PARAM_BASE_VALUE, decodedBinding.targetType)
        assertEquals(-1f, decodedBinding.minVal)
        assertEquals(1f, decodedBinding.maxVal)
        assertEquals(MacroCurveType.EXPONENTIAL, decodedBinding.curve)
        assertTrue(decodedBinding.inverted)
    }

    @Test
    fun testDeckPrefixFilteringAndRestoration() {
        val globalBank = MacroBank()
        globalBank.knobs[0].label = "ZOOM"
        globalBank.knobs[0].bindings.add(
            MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE)
        )
        globalBank.knobs[0].bindings.add(
            MacroBinding(parameterId = "Deck B/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE)
        )

        // Filter for Deck A
        val deckABank = MacroBankSerializer.filterMacroBankForDeck(globalBank, "Deck A")
        assertNotNull(deckABank)
        assertEquals(1, deckABank.knobs[0].bindings.size)
        assertEquals("Deck A/fbZoom", deckABank.knobs[0].bindings[0].parameterId)

        // Filter for Deck B
        val deckBBank = MacroBankSerializer.filterMacroBankForDeck(globalBank, "Deck B")
        assertNotNull(deckBBank)
        assertEquals(1, deckBBank.knobs[0].bindings.size)
        assertEquals("Deck B/fbZoom", deckBBank.knobs[0].bindings[0].parameterId)

        // Filter for Deck BG (has none)
        val deckBGBank = MacroBankSerializer.filterMacroBankForDeck(globalBank, "Deck BG")
        assertNull(deckBGBank)

        // Test restoring Deck A bank onto Deck B (prefix remapping)
        val freshGlobalBank = MacroBank()
        MacroEngine.registerBank(null, freshGlobalBank)

        MacroBankSerializer.restoreMacroBankForDeck(deckABank, targetDeckLabel = "Deck B")
        val restoredBank = MacroEngine.globalBank()
        assertEquals(1, restoredBank.knobs[0].bindings.size)
        assertEquals("Deck B/fbZoom", restoredBank.knobs[0].bindings[0].parameterId)
    }

    @Test
    fun testStandaloneExportAndImportWithMissingParamSkipping() {
        val tempFile = File.createTempFile("test_macro_bank", ".knobpreset.json")
        tempFile.deleteOnExit()

        val binding1 = MacroBinding(parameterId = "Deck A/validParam", targetType = MacroTargetType.PARAM_BASE_VALUE)
        val binding2 = MacroBinding(parameterId = "Deck A/missingParam", targetType = MacroTargetType.PARAM_BASE_VALUE)
        val bank = MacroBank(knobs = listOf(MacroControl(label = "TEST", bindings = mutableListOf(binding1, binding2))))

        MacroBankSerializer.exportToFile(tempFile, bank)
        assertTrue(tempFile.exists())

        // Mock Mixer where only "Deck A/validParam" exists
        val mixer = mockk<Mixer>()
        val validParam = ModulatableParameter(0.5f)
        every { mixer.getParameterPaths("Mixer") } returns listOf("Deck A/validParam" to validParam)

        val (importedBank, skippedCount) = MacroBankSerializer.importFromFile(tempFile, mixer)

        assertEquals(1, skippedCount, "Should skip the 1 missing parameter binding")
        assertEquals(1, importedBank.knobs[0].bindings.size)
        assertEquals("Deck A/validParam", importedBank.knobs[0].bindings[0].parameterId)
    }
}
