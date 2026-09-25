package llm.slop.liquidlsd.presets

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroCurveType
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLinkMode
import llm.slop.liquidlsd.macro.MacroTargetType
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSource
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
class GeneratorDefaultsTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var originalStorageDir: File

    private class TestSource(
        override val id: String = "test_gen",
        override val displayName: String = "Test Gen",
        override val parameters: Map<String, ModulatableParameter> = emptyMap(),
        override val globalAlpha: ModulatableParameter = ModulatableParameter(1.0f)
    ) : VisualSource {
        override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> =
            parameters.map { (k, v) -> "$prefix/$k" to v } + ("$prefix/globalAlpha" to globalAlpha)

        override fun clone(): VisualSource = TestSource(
            id = id,
            displayName = displayName,
            parameters = parameters.mapValues { (_, p) ->
                ModulatableParameter(p.baseValue, minClamp = p.minClamp, maxClamp = p.maxClamp).apply {
                    mappedMidiId = p.mappedMidiId
                }
            },
            globalAlpha = ModulatableParameter(globalAlpha.baseValue, minClamp = globalAlpha.minClamp, maxClamp = globalAlpha.maxClamp)
        )
    }

    private fun assertEqualsWithTolerance(expected: Float, actual: Float, tolerance: Float = 0.001f, message: String? = null) {
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, message ?: "Expected $expected but was $actual within tolerance $tolerance")
    }

    @BeforeTest
    fun setUp() {
        originalStorageDir = GeneratorDefaults.storageDir
        GeneratorDefaults.storageDir = tempDir
        for (id in MacroEngine.CANONICAL_BANK_IDS) {
            MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
        }
    }

    @AfterTest
    fun tearDown() {
        GeneratorDefaults.storageDir = originalStorageDir
    }

    private fun mockDeck(source: VisualSource, label: String = "Deck A"): Deck {
        val deck = mockk<Deck>(relaxed = true)
        var curSource = source
        every { deck.source } answers { curSource }
        every { deck.source = any() } answers { curSource = firstArg() }
        every { deck.isEmpty } returns false
        every { deck.getParameterPaths(label) } answers {
            val list = mutableListOf<Pair<String, ModulatableParameter>>()
            curSource.parameters.forEach { (name, param) ->
                list.add("$label/$name" to param)
            }
            list.add("$label/globalAlpha" to curSource.globalAlpha)
            list
        }
        return deck
    }

    @Test
    fun testRoundTripSaveAndLoad() {
        val p1 = ModulatableParameter(0.75f, minClamp = 0f, maxClamp = 1f)
        val p2 = ModulatableParameter(15f, minClamp = 0f, maxClamp = 100f)
        val alpha = ModulatableParameter(0.88f)
        val source = TestSource(
            id = "synth_wave",
            parameters = mapOf("cutoff" to p1, "resonance" to p2),
            globalAlpha = alpha
        )

        val deck = mockDeck(source, "Deck A")

        // Populate Deck A bank with custom knobs
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        bank.knobs[0].label = "CUT"
        bank.knobs[0].value = 0.75f
        bank.knobs[0].bindings.clear()
        bank.knobs[0].bindings.add(
            MacroBinding(
                parameterId = "Deck A/cutoff",
                targetType = MacroTargetType.PARAM_BASE_VALUE,
                minVal = 0f,
                maxVal = 1f
            )
        )
        bank.knobs[1].label = "RES"
        bank.knobs[1].value = 0.15f
        bank.knobs[1].bindings.clear()
        bank.knobs[1].bindings.add(
            MacroBinding(
                parameterId = "Deck A/resonance",
                targetType = MacroTargetType.PARAM_BASE_VALUE,
                minVal = 0f,
                maxVal = 100f
            )
        )

        // Save default
        GeneratorDefaults.saveDefault(deck, MacroEngine.DECK_A)
        assertTrue(GeneratorDefaults.hasUserDefault("synth_wave"))

        // Mutate deck parameters and blank the bank
        p1.baseValue = 0.1f
        p2.baseValue = 50f
        alpha.baseValue = 0.2f
        bank.knobs[0].label = ""
        bank.knobs[0].value = 0f
        bank.knobs[0].bindings.clear()

        // Apply default back
        GeneratorDefaults.applyToDeck(deck, "Deck A", MacroEngine.DECK_A)

        assertEquals(0.75f, p1.baseValue)
        assertEquals(15f, p2.baseValue)
        assertEquals(0.88f, alpha.baseValue)

        val restoredBank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        assertEquals("CUT", restoredBank.knobs[0].label)
        assertEquals(0.75f, restoredBank.knobs[0].value)
        assertEquals(1, restoredBank.knobs[0].bindings.size)
        assertEquals("Deck A/cutoff", restoredBank.knobs[0].bindings[0].parameterId)

        assertEquals("RES", restoredBank.knobs[1].label)
        assertEquals(0.15f, restoredBank.knobs[1].value)
        assertEquals(1, restoredBank.knobs[1].bindings.size)
        assertEquals("Deck A/resonance", restoredBank.knobs[1].bindings[0].parameterId)
    }

    @Test
    fun testMidiMappingIdsStrippedOnSave() {
        val p = ModulatableParameter(0.5f).apply {
            mappedMidiId = "CH1_CC10"
            midiMapMin = 0.2f
            midiMapMax = 0.8f
        }
        val source = TestSource(id = "midi_strip_test", parameters = mapOf("param" to p))
        val deck = mockDeck(source)

        GeneratorDefaults.saveDefault(deck, MacroEngine.DECK_A)

        val file = File(tempDir, "midi_strip_test.json")
        assertTrue(file.exists())
        val dto = GeneratorDefaults.json.decodeFromString<llm.slop.liquidlsd.models.GeneratorDefaultDto>(file.readText())

        val pDto = dto.parameters["param"]
        assertNotNull(pDto)
        assertNull(pDto.mappedMidiId)
        assertEquals(0f, pDto.midiMapMin)
        assertEquals(1f, pDto.midiMapMax)
    }

    @Test
    fun testDefaultSavedOnDeckARemappedToDeckB() {
        val source = TestSource(
            id = "cross_deck_test",
            parameters = mapOf("speed" to ModulatableParameter(0.5f, minClamp = 0f, maxClamp = 1f))
        )
        val deckA = mockDeck(source, "Deck A")

        val bankA = MacroEngine.getBank(MacroEngine.DECK_A)!!
        bankA.knobs[0].label = "SPD"
        bankA.knobs[0].value = 0.5f
        bankA.knobs[0].bindings.clear()
        bankA.knobs[0].bindings.add(
            MacroBinding(
                parameterId = "Deck A/speed",
                targetType = MacroTargetType.PARAM_BASE_VALUE
            )
        )

        GeneratorDefaults.saveDefault(deckA, MacroEngine.DECK_A)

        // Apply on Deck B
        val deckB = mockDeck(source.clone(), "Deck B")
        GeneratorDefaults.applyToDeck(deckB, "Deck B", MacroEngine.DECK_B)

        val bankB = MacroEngine.getBank(MacroEngine.DECK_B)!!
        assertEquals("SPD", bankB.knobs[0].label)
        assertEquals(1, bankB.knobs[0].bindings.size)
        assertEquals("Deck B/speed", bankB.knobs[0].bindings[0].parameterId)
    }

    @Test
    fun testCuratedMappingsForStockSourcesResolve() {
        val stockInputNames = mapOf(
            "mandala" to listOf("Lobes", "Thickness", "Depth", "Hue Offset"),
            "dynamic_spiral" to listOf("Speed", "Scale", "WaveAmp", "Shear"),
            "icosa_h3" to listOf("Morph", "StellationBoost", "Zoom", "HueOffset"),
            "domain_warp_fluid" to listOf("WarpStrength", "Swirl", "Speed", "Zoom"),
            "gyroid_hyperspace" to listOf("FlightSpeed", "WallThickness", "Frequency", "CoreGlow"),
            "celestial_engine" to listOf("Speed", "Symmetries", "PhaseTwist", "Glow"),
            "hyper_slice" to listOf("SliceOffset", "RotateXW", "Morph", "Zoom"),
            "chladni_cymatics" to listOf("FrequencyM", "FrequencyN", "VibrationSpeed", "NodeSharpness")
        )

        for (sourceId in VisualSourceRegistry.DEFAULT_SOURCE_IDS) {
            val expectedNames = stockInputNames[sourceId]
            assertNotNull(expectedNames, "Missing stock inputs definition for $sourceId")

            val params = expectedNames.associateWith { ModulatableParameter(0.5f, minClamp = 0f, maxClamp = 1f) }
            val source = TestSource(id = sourceId, parameters = params)

            val resolved = GeneratorDefaults.resolve(source)
            assertEquals(sourceId, resolved.sourceId)

            val bank = resolved.macroBank
            assertNotNull(bank)
            assertEquals(4, bank.knobs.size)

            for (i in 0 until 4) {
                val knob = bank.knobs[i]
                assertTrue(knob.label.isNotBlank(), "Knob $i label should not be blank for $sourceId")
                assertEquals(1, knob.bindings.size, "Knob $i should have 1 binding for $sourceId")
                assertEquals("Deck/${expectedNames[i]}", knob.bindings[0].parameterId)
            }
        }
    }

    @Test
    fun testHeuristicSkipsSelectorsAndOrdersSemantics() {
        val params = linkedMapOf(
            "ColorMode" to ModulatableParameter(0f, minClamp = 0f, maxClamp = 3f),
            "WireframeMode" to ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f),
            "Speed" to ModulatableParameter(0.4f, minClamp = -1f, maxClamp = 1f),
            "Zoom" to ModulatableParameter(1f, minClamp = 0.1f, maxClamp = 5f),
            "Detail" to ModulatableParameter(3f, minClamp = 1f, maxClamp = 10f),
            "MiscParam" to ModulatableParameter(0.5f, minClamp = 0f, maxClamp = 1f)
        )
        val source = TestSource(id = "third_party_shader", parameters = params)

        val resolved = GeneratorDefaults.resolve(source)
        val bank = resolved.macroBank!!
        assertEquals(4, bank.knobs.size)

        val boundParams = bank.knobs.mapNotNull { it.bindings.firstOrNull()?.parameterId?.removePrefix("Deck/") }
        // Should skip ColorMode and WireframeMode
        assertFalse(boundParams.contains("ColorMode"))
        assertFalse(boundParams.contains("WireframeMode"))

        // Should include priority names first
        assertEquals("Speed", boundParams[0])
        assertEquals("Zoom", boundParams[1])
        assertEquals("Detail", boundParams[2])
        assertEquals("MiscParam", boundParams[3])

        // Speed should have exponential curve
        val speedBinding = bank.knobs[0].bindings[0]
        assertEquals(MacroCurveType.EXPONENTIAL, speedBinding.curve)
    }

    @Test
    fun testNoParameterJumpAfterApplyToDeckAndTick() {
        val lobesParam = ModulatableParameter(4.0f, minClamp = 3.0f, maxClamp = 26.0f)
        val thickParam = ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 1.0f)
        val depthParam = ModulatableParameter(0.35f, minClamp = 0.0f, maxClamp = 1.0f)
        val hueParam = ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f)

        val source = TestSource(
            id = "mandala",
            parameters = mapOf(
                "Lobes" to lobesParam,
                "Thickness" to thickParam,
                "Depth" to depthParam,
                "Hue Offset" to hueParam
            )
        )

        val deckA = mockDeck(source, "Deck A")
        val mixer = mockk<Mixer>(relaxed = true)
        every { mixer.deckA } returns deckA
        every { mixer.getParameterPaths("Mixer") } answers {
            deckA.getParameterPaths("Deck A")
        }

        GeneratorDefaults.applyToDeck(deckA, "Deck A", MacroEngine.DECK_A)

        // Evaluate macro engine tick
        MacroEngine.invalidate()
        MacroEngine.tick(mixer)

        assertEqualsWithTolerance(4.0f, lobesParam.baseValue, 0.001f, "Lobes value should not jump on load")
        assertEqualsWithTolerance(0.5f, thickParam.baseValue, 0.001f, "Thickness value should not jump on load")
        assertEqualsWithTolerance(0.35f, depthParam.baseValue, 0.001f, "Depth value should not jump on load")
        assertEqualsWithTolerance(0.0f, hueParam.baseValue, 0.001f, "Hue Offset value should not jump on load")
    }

    @Test
    fun testRegistryMasterSourcesRemainUnmutated() {
        val masterLobes = ModulatableParameter(4.0f, minClamp = 3.0f, maxClamp = 26.0f)
        val masterSource = TestSource(
            id = "mandala",
            parameters = mapOf("Lobes" to masterLobes)
        )
        VisualSourceRegistry.availableSources.clear()
        VisualSourceRegistry.availableSources.add(masterSource)

        val clonedInstance = masterSource.clone()
        val deck = mockDeck(clonedInstance, "Deck A")

        // Mutate deck source and apply defaults
        deck.source.parameters["Lobes"]?.baseValue = 12.0f
        GeneratorDefaults.applyToDeck(deck, "Deck A", MacroEngine.DECK_A)

        // Verify master source in registry was never mutated
        assertEquals(4.0f, masterLobes.baseValue, "Master registry source must remain clean and unmutated")

        VisualSourceRegistry.availableSources.clear()
    }
}
