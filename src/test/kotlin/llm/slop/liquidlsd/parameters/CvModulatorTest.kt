package llm.slop.liquidlsd.parameters

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.MutableCVSource
import llm.slop.liquidlsd.models.ModulatorDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.RowClipboardData
import llm.slop.liquidlsd.models.ClipboardManager
import llm.slop.liquidlsd.models.toDomain
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CvModulatorTest {

    @Test
    fun testDefaultValuesAndRandomizeDepth() {
        val mod = CvModulator(sourceId = "lfo")
        assertEquals(0.0f, mod.depth)
        assertEquals(0.0f, mod.depthMin)
        assertEquals(0.0f, mod.depthMax)
        assertFalse(mod.randomizeDepth)

        // If randomizeDepth is false, randomizeDepth() should be a no-op
        val unchanged = mod.randomizeDepth(Random(42))
        assertEquals(0.0f, unchanged.depth)

        // When enabled, randomizeDepth() samples between depthMin and depthMax
        val modRange = mod.copy(
            depth = 0.5f,
            depthMin = 0.2f,
            depthMax = 0.8f,
            randomizeDepth = true
        )
        val randomized = modRange.randomizeDepth(Random(42))
        assertTrue(randomized.depth in 0.2f..0.8f, "Randomized depth should be within [0.2, 0.8]")
    }

    @Test
    fun testModulatableParameterEvaluationWithDepth() {
        if (!CVRegistry.exists("test_cv")) {
            CVRegistry.register(MutableCVSource("test_cv", 1.0f))
        }
        CVRegistry.updatePushedValue("test_cv", 1.0f)

        // Monopolar parameter (0.0 .. 1.0)
        val param = ModulatableParameter(baseValue = 0.5f, minClamp = 0.0f, maxClamp = 1.0f)

        // 1. ADD operator: rawModAmount = ((1 + 1)/2) * depth + 0 = depth
        val modAdd = CvModulator(
            sourceId = "test_cv",
            operator = ModulationOperator.ADD,
            depth = 0.4f,
            bypassed = false
        )
        param.modulators.add(modAdd)
        var evaluated = param.evaluate()
        // baseValue (0.5) + depth (0.4) * (1.0 - 0.0) = 0.9
        assertEquals(0.9f, evaluated, 0.001f)

        // 2. SCALE operator: result = baseValue * (1 - depth + modAmount)
        // With depth = 0.4 and modAmount = 0.4: 1 - 0.4 + 0.4 = 1.0 -> result = 0.5 * 1.0 = 0.5
        param.modulators.clear()
        val modScale = CvModulator(
            sourceId = "test_cv",
            operator = ModulationOperator.SCALE,
            depth = 0.4f,
            bypassed = false
        )
        param.modulators.add(modScale)
        evaluated = param.evaluate()
        assertEquals(0.5f, evaluated, 0.001f)

        // With rawCV = -1.0 (so (( -1 + 1 ) / 2 ) * depth = 0.0):
        // result = baseValue * (1 - depth + 0) = 0.5 * (1 - 0.4) = 0.3
        CVRegistry.updatePushedValue("test_cv", -1.0f)
        evaluated = param.evaluate()
        assertEquals(0.3f, evaluated, 0.001f)
    }

    @Test
    fun testBipolarParameterEvaluationWithDepth() {
        if (!CVRegistry.exists("test_cv_bipolar")) {
            CVRegistry.register(MutableCVSource("test_cv_bipolar", 1.0f))
        }
        CVRegistry.updatePushedValue("test_cv_bipolar", 1.0f)

        // Bipolar parameter (-1.0 .. 1.0)
        val param = ModulatableParameter(baseValue = 0.0f, minClamp = -1.0f, maxClamp = 1.0f)

        // ADD operator: rawModAmount = cv * depth = 1.0 * 0.5 = 0.5
        // scalar = (max - min) / 2 = 2 / 2 = 1.0
        // result = 0.0 + 0.5 * 1.0 = 0.5
        val mod = CvModulator(
            sourceId = "test_cv_bipolar",
            operator = ModulationOperator.ADD,
            depth = 0.5f,
            bypassed = false
        )
        param.modulators.add(mod)
        val evaluated = param.evaluate()
        assertEquals(0.5f, evaluated, 0.001f)
    }

    @Test
    fun testPresetJsonSerializationCompatibility() {
        val json = Json { ignoreUnknownKeys = true }

        // Test deserializing legacy preset containing "weight", "weightMin", "weightMax", "randomizeWeight"
        val legacyJson = """
            {
                "sourceId": "lfo",
                "operator": "ADD",
                "weight": 0.65,
                "weightMin": 0.25,
                "weightMax": 0.75,
                "randomizeWeight": true,
                "subdivisionMin": 1.0,
                "subdivisionMax": 1.0,
                "phaseOffsetMin": 0.0,
                "phaseOffsetMax": 0.0,
                "slopeMin": 0.5,
                "slopeMax": 0.5
            }
        """.trimIndent()

        val dto = json.decodeFromString<ModulatorDto>(legacyJson)
        assertEquals(0.65f, dto.depth, 0.0001f)
        assertEquals(0.25f, dto.depthMin, 0.0001f)
        assertEquals(0.75f, dto.depthMax, 0.0001f)
        assertTrue(dto.randomizeDepth)

        val domain = dto.toDomain()
        assertEquals(0.65f, domain.depth, 0.0001f)
        assertEquals(0.25f, domain.depthMin, 0.0001f)
        assertEquals(0.75f, domain.depthMax, 0.0001f)
        assertTrue(domain.randomizeDepth)

        // Test round-trip back to DTO and verify serial names
        val backDto = domain.toDto()
        val encoded = json.encodeToString(ModulatorDto.serializer(), backDto)
        assertTrue(encoded.contains("\"weight\":0.65"), "Encoded JSON should contain 'weight' key")
        assertTrue(encoded.contains("\"weightMin\":0.25"), "Encoded JSON should contain 'weightMin' key")
        assertTrue(encoded.contains("\"weightMax\":0.75"), "Encoded JSON should contain 'weightMax' key")
        assertTrue(encoded.contains("\"randomizeWeight\":true"), "Encoded JSON should contain 'randomizeWeight' key")
    }

    @Test
    fun testClipboardManagerDepthScaling() {
        val sourceParam = ModulatableParameter(baseValue = 0.5f, minClamp = 0f, maxClamp = 1f)
        val mod = CvModulator(
            sourceId = "lfo",
            operator = ModulationOperator.ADD,
            depth = 0.4f,
            depthMin = 0.2f,
            depthMax = 0.6f
        )
        sourceParam.modulators.add(mod)

        val rowData = RowClipboardData("Deck A/Geometry/Zoom", sourceParam.toDto())

        val mixer = mockk<Mixer>()
        val deck = mockk<Deck>()
        every { mixer.deckA } returns deck
        every { deck.source } returns mockk {
            every { parameters } returns linkedMapOf("Zoom" to sourceParam)
        }

        // Paste into destination parameter with a range of 0..2 (destRange/srcRange = 2.0)
        val destParam = ModulatableParameter(baseValue = 0f, minClamp = 0f, maxClamp = 2f)
        ClipboardManager.applyRowClipboard(destParam, rowData, mixer)

        assertEquals(1, destParam.modulators.size)
        val pastedMod = destParam.modulators[0]
        // Range doubled: 0.4 * 2 = 0.8
        assertEquals(0.8f, pastedMod.depth, 0.001f)
        assertEquals(0.4f, pastedMod.depthMin, 0.001f)
        assertEquals(1.0f, pastedMod.depthMax.coerceAtMost(1f), 0.001f)
    }
}
