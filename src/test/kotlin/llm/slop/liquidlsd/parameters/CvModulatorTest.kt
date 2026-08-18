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
        // Monopolar parameter (0.0 .. 1.0) with bipolar LFO source
        val param = ModulatableParameter(baseValue = 0.5f, minClamp = 0.0f, maxClamp = 1.0f)

        // 1. ADD operator with LFO at peak (+1.0): rawModAmount = ((1 + 1)/2) * depth = depth
        // We set LFO subdivision such that at frame 30 phase is at peak (1.0)
        CVRegistry.setRenderFrameCount(30L)
        val modAdd = CvModulator(
            sourceId = "lfo",
            genUnit = GenUnit.FRAME,
            subdivision = 60f,
            waveform = Waveform.TRIANGLE,
            operator = ModulationOperator.ADD,
            depth = 0.4f,
            bypassed = false
        )
        param.modulators.add(modAdd)
        var evaluated = param.evaluate()
        // baseValue (0.5) + depth (0.4) * (1.0 - 0.0) = 0.9
        assertEquals(0.9f, evaluated, 0.001f)

        // 2. SCALE operator at peak: result = baseValue * (1 - depth + modAmount)
        // With depth = 0.4 and modAmount = 0.4: 1 - 0.4 + 0.4 = 1.0 -> result = 0.5 * 1.0 = 0.5
        param.modulators.clear()
        val modScale = CvModulator(
            sourceId = "lfo",
            genUnit = GenUnit.FRAME,
            subdivision = 60f,
            waveform = Waveform.TRIANGLE,
            operator = ModulationOperator.SCALE,
            depth = 0.4f,
            bypassed = false
        )
        param.modulators.add(modScale)
        evaluated = param.evaluate()
        assertEquals(0.5f, evaluated, 0.001f)

        // With rawCV at trough = -1.0 (frame 0) -> (( -1 + 1 ) / 2 ) * depth = 0.0:
        // result = baseValue * (1 - depth + 0) = 0.5 * (1 - 0.4) = 0.3
        CVRegistry.setRenderFrameCount(0L)
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
    fun testUnipolarAudioModulationEvaluation() {
        if (!CVRegistry.exists("audio_amp")) {
            CVRegistry.register(MutableCVSource("audio_amp", 0.0f))
        }
        CVRegistry.updatePushedValue("audio_amp", 0.0f) // Silence

        val param = ModulatableParameter(baseValue = 0.2f, minClamp = 0.0f, maxClamp = 1.0f)
        val audioMod = CvModulator(
            sourceId = "audio_amp",
            operator = ModulationOperator.ADD,
            depth = 0.8f,
            bypassed = false
        )
        param.modulators.add(audioMod)

        // At silence (cv = 0), baseline should NOT shift
        var evaluated = param.evaluate()
        assertEquals(0.2f, evaluated, 0.001f, "Silence should not add DC offset to the base value")

        // At peak audio (cv = 1.0), modulation adds depth * range = 0.8 * 1.0 = 0.8 -> 0.2 + 0.8 = 1.0
        CVRegistry.updatePushedValue("audio_amp", 1.0f)
        evaluated = param.evaluate()
        assertEquals(1.0f, evaluated, 0.001f, "Loud audio should produce full depth modulation")
    }

    @Test
    fun testPresetJsonSerialization() {
        val json = Json { ignoreUnknownKeys = true }

        val jsonStr = """
            {
                "sourceId": "lfo",
                "operator": "ADD",
                "depth": 0.65,
                "depthMin": 0.25,
                "depthMax": 0.75,
                "randomizeDepth": true,
                "subdivisionMin": 1.0,
                "subdivisionMax": 1.0,
                "phaseOffsetMin": 0.0,
                "phaseOffsetMax": 0.0,
                "slopeMin": 0.5,
                "slopeMax": 0.5
            }
        """.trimIndent()

        val dto = json.decodeFromString<ModulatorDto>(jsonStr)
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
        assertTrue(encoded.contains("\"depth\":0.65"), "Encoded JSON should contain 'depth' key")
        assertTrue(encoded.contains("\"depthMin\":0.25"), "Encoded JSON should contain 'depthMin' key")
        assertTrue(encoded.contains("\"depthMax\":0.75"), "Encoded JSON should contain 'depthMax' key")
        assertTrue(encoded.contains("\"randomizeDepth\":true"), "Encoded JSON should contain 'randomizeDepth' key")
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

    @Test
    fun testFrameSyncedLfoEvaluation() {
        CVRegistry.setRenderFrameCount(0L)
        val mod = CvModulator(
            sourceId = "lfo",
            genUnit = GenUnit.FRAME,
            subdivision = 60f, // 60-frame cycle
            waveform = Waveform.TRIANGLE,
            morph = 0f,
            hold = 0f,
            slope = 0.5f,
            phaseOffset = 0f,
            bypassed = false
        )

        // At frame 0: phase 0.0 -> bottom of triangle = -1.0
        val v0 = llm.slop.liquidlsd.cv.evaluateModulator(mod)
        assertEquals(-1.0f, v0, 0.001f)

        // At frame 15: phase 15/60 = 0.25 -> zero crossing = 0.0
        CVRegistry.setRenderFrameCount(15L)
        val v15 = llm.slop.liquidlsd.cv.evaluateModulator(mod)
        assertEquals(0.0f, v15, 0.001f)

        // At frame 30: phase 30/60 = 0.5 -> peak of triangle = 1.0
        CVRegistry.setRenderFrameCount(30L)
        val v30 = llm.slop.liquidlsd.cv.evaluateModulator(mod)
        assertEquals(1.0f, v30, 0.001f)

        // At frame 60: phase 60/60 = 1.0 (wrapped to 0.0) -> bottom of triangle = -1.0
        CVRegistry.setRenderFrameCount(60L)
        val v60 = llm.slop.liquidlsd.cv.evaluateModulator(mod)
        assertEquals(-1.0f, v60, 0.001f)
    }

    @Test
    fun testFrameSyncedLfoAt30FpsRunsAt1Hz() {
        CVRegistry.setTargetFps(30f)
        CVRegistry.setRenderFrameCount(0L)
        val mod = CvModulator(
            sourceId = "lfo",
            genUnit = GenUnit.FRAME,
            subdivision = 30f, // 30 frames at 30 FPS = 1.0 second period (1 Hz)
            waveform = Waveform.TRIANGLE,
            morph = 0f,
            hold = 0f,
            slope = 0.5f,
            phaseOffset = 0f,
            bypassed = false
        )

        // At t = 0.0s (offset 0.0): phase 0/30 = 0.0 -> bottom = -1.0
        val v0 = llm.slop.liquidlsd.cv.evaluateModulatorAtOffset(mod, 0.0)
        assertEquals(-1.0f, v0, 0.001f)

        // At t = 0.25s (quarter second = 7.5 frames): phase 7.5/30 = 0.25 -> zero crossing = 0.0
        val vQuarter = llm.slop.liquidlsd.cv.evaluateModulatorAtOffset(mod, 0.25)
        assertEquals(0.0f, vQuarter, 0.001f)

        // At t = 0.5s (half second = 15 frames): phase 15/30 = 0.5 -> peak = 1.0
        val vHalf = llm.slop.liquidlsd.cv.evaluateModulatorAtOffset(mod, 0.5)
        assertEquals(1.0f, vHalf, 0.001f)

        // At t = 1.0s (one full second = 30 frames): phase 30/30 = 1.0 (0.0) -> bottom = -1.0
        val vFull = llm.slop.liquidlsd.cv.evaluateModulatorAtOffset(mod, 1.0)
        assertEquals(-1.0f, vFull, 0.001f)
    }

    @Test
    fun testFrameSyncedLfoRandomizationDiscreteIntegers() {
        val mod = CvModulator(
            sourceId = "lfo",
            genUnit = GenUnit.FRAME,
            subdivision = 10f,
            subdivisionMin = 4f,
            subdivisionMax = 8f,
            randomizeSubdivision = true
        )
        val rng = Random(1234)
        for (i in 0..20) {
            val randomized = mod.randomizeSubdivision(rng)
            assertTrue(randomized.subdivision in 4f..8f, "Randomized frame subdivision must be in range [4, 8]")
            assertEquals(randomized.subdivision.toInt().toFloat(), randomized.subdivision, "Frame subdivision must be an integer value")
        }
    }

    @Test
    fun testFrameSyncedLfoJsonSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val mod = CvModulator(
            sourceId = "lfo",
            genUnit = GenUnit.FRAME,
            subdivision = 120f,
            subdivisionMin = 60f,
            subdivisionMax = 240f,
            randomizeSubdivision = true,
            modGenUnit = GenUnit.FRAME,
            modSubdivision = 30f
        )
        val dto = mod.toDto()
        assertEquals("FRAME", dto.genUnit)
        assertEquals(120f, dto.subdivision)

        val encoded = json.encodeToString(CvModulator.serializer(), mod)
        val decoded = json.decodeFromString<CvModulator>(encoded)

        assertEquals(GenUnit.FRAME, decoded.genUnit)
        assertEquals(GenUnit.FRAME, decoded.modGenUnit)
        assertEquals(120f, decoded.subdivision)
        assertEquals(60f, decoded.subdivisionMin)
        assertEquals(240f, decoded.subdivisionMax)
        assertTrue(decoded.randomizeSubdivision)
    }
}
