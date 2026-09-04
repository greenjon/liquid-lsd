package llm.slop.liquidlsd.parameters

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.evaluateModulatorAtOffset
import llm.slop.liquidlsd.models.ModulatorDto
import llm.slop.liquidlsd.models.toDomain
import llm.slop.liquidlsd.models.toDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepSequencerEvaluatorTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testSequencerStepSelection() {
        CVRegistry.setRenderFrameCount(0L)

        val steps = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f)
        val mod = CvModulator(
            sourceId = "seq",
            genUnit = GenUnit.FRAME,
            subdivision = 10.0f, // 10 frames per step
            seqStepCount = 8,
            seqSteps = steps,
            seqHold = 1.0f, // Instant step jumps
            depth = 1.0f
        )

        // Frame 0: Step 0 -> 0.1
        CVRegistry.setRenderFrameCount(0L)
        assertEquals(0.1f, evaluateModulatorAtOffset(mod, 0.0), 0.001f)

        // Frame 5: Still Step 0 (mid-step with hold=1.0) -> 0.1
        CVRegistry.setRenderFrameCount(5L)
        assertEquals(0.1f, evaluateModulatorAtOffset(mod, 0.0), 0.001f)

        // Frame 10: Step 1 -> 0.2
        CVRegistry.setRenderFrameCount(10L)
        assertEquals(0.2f, evaluateModulatorAtOffset(mod, 0.0), 0.001f)

        // Frame 75: Step 7 -> 0.8
        CVRegistry.setRenderFrameCount(75L)
        assertEquals(0.8f, evaluateModulatorAtOffset(mod, 0.0), 0.001f)

        // Frame 80: Looped back to Step 0 -> 0.1
        CVRegistry.setRenderFrameCount(80L)
        assertEquals(0.1f, evaluateModulatorAtOffset(mod, 0.0), 0.001f)
    }

    @Test
    fun testSequencerHoldAndGlide() {
        val steps = listOf(0.0f, 1.0f)
        val modGlide = CvModulator(
            sourceId = "seq",
            genUnit = GenUnit.FRAME,
            subdivision = 10.0f, // 10 frames per step
            seqStepCount = 2,
            seqSteps = steps,
            seqHold = 0.0f, // Continuous glide over entire step
            seqCurveSmooth = false, // Linear
            depth = 1.0f
        )

        // Frame 0: Start of step 0 -> 0.0
        CVRegistry.setRenderFrameCount(0L)
        assertEquals(0.0f, evaluateModulatorAtOffset(modGlide, 0.0), 0.001f)

        // Frame 5: Mid-glide to step 1 (0.0 -> 1.0) -> 0.5
        CVRegistry.setRenderFrameCount(5L)
        assertEquals(0.5f, evaluateModulatorAtOffset(modGlide, 0.0), 0.001f)

        // Test smooth cosine curve at mid-point (cos(pi/2) = 0 -> 0.5 - 0.5*0 = 0.5)
        val modSmooth = modGlide.copy(seqCurveSmooth = true)
        assertEquals(0.5f, evaluateModulatorAtOffset(modSmooth, 0.0), 0.001f)

        // Test at 25% through step with smooth curve:
        // linear = 0.25, smooth = 0.5 - 0.5 * cos(0.25 * pi) ~ 0.5 - 0.3535 ~ 0.1464
        CVRegistry.setRenderFrameCount(2L) // 2.5 frames would be 0.25, at frame 2 it is 0.2
        val smoothVal = evaluateModulatorAtOffset(modSmooth, 0.0)
        val linearVal = evaluateModulatorAtOffset(modGlide, 0.0)
        assertTrue(smoothVal < linearVal, "Smooth S-curve starts slower than linear ramp: $smoothVal < $linearVal")

        // Test Glide & Hold (Hold = 50%)
        val modHold50 = modGlide.copy(seqHold = 0.5f)
        // Frame 0..5 (first 50%): held flat at 0.0
        CVRegistry.setRenderFrameCount(2L)
        assertEquals(0.0f, evaluateModulatorAtOffset(modHold50, 0.0), 0.001f)

        // Frame 7.5 (midpoint of glide between 5.0 and 10.0): should be halfway (0.5)
        // At frame 7: progress in glide is (0.7 - 0.5) / 0.5 = 0.4
        CVRegistry.setRenderFrameCount(7L)
        assertEquals(0.4f, evaluateModulatorAtOffset(modHold50, 0.0), 0.001f)
    }

    @Test
    fun testSequencerModulatableParameterIntegration() {
        // Monopolar parameter [0.0 .. 1.0]
        val paramMono = ModulatableParameter(baseValue = 0.0f, minClamp = 0.0f, maxClamp = 1.0f)
        CVRegistry.setRenderFrameCount(0L)

        val mod = CvModulator(
            sourceId = "seq",
            genUnit = GenUnit.FRAME,
            subdivision = 10.0f,
            seqStepCount = 4,
            seqSteps = listOf(0.25f, 0.75f, 0.5f, 1.0f),
            seqHold = 1.0f,
            depth = 1.0f,
            operator = ModulationOperator.ADD
        )
        paramMono.modulators.add(mod)

        // Step 0: 0.25
        CVRegistry.setRenderFrameCount(0L)
        assertEquals(0.25f, paramMono.evaluate(), 0.001f)

        // Step 1: 0.75
        CVRegistry.setRenderFrameCount(10L)
        assertEquals(0.75f, paramMono.evaluate(), 0.001f)

        // Bipolar parameter [-180.0 .. 180.0]
        val paramBi = ModulatableParameter(baseValue = 0.0f, minClamp = -180.0f, maxClamp = 180.0f)
        val modBi = CvModulator(
            sourceId = "seq",
            genUnit = GenUnit.FRAME,
            subdivision = 10.0f,
            seqStepCount = 4,
            seqSteps = listOf(-0.5f, 1.0f, -1.0f, 0.0f),
            seqHold = 1.0f,
            depth = 1.0f,
            operator = ModulationOperator.ADD
        )
        paramBi.modulators.add(modBi)

        // Step 0: -0.5 * 180 = -90°
        CVRegistry.setRenderFrameCount(0L)
        assertEquals(-90.0f, paramBi.evaluate(), 0.001f)

        // Step 1: 1.0 * 180 = 180°
        CVRegistry.setRenderFrameCount(10L)
        assertEquals(180.0f, paramBi.evaluate(), 0.001f)

        // Step 2: -1.0 * 180 = -180°
        CVRegistry.setRenderFrameCount(20L)
        assertEquals(-180.0f, paramBi.evaluate(), 0.001f)
    }

    @Test
    fun testSequencerSerializationRoundTrip() {
        val original = CvModulator(
            sourceId = "seq",
            genUnit = GenUnit.BEAT,
            subdivision = 0.5f,
            seqStepCount = 16,
            seqSteps = List(32) { i -> (i / 32f) },
            seqHold = 0.75f,
            seqCurveSmooth = true,
            depth = 0.8f
        )

        val dto = original.toDto()
        val jsonStr = json.encodeToString(ModulatorDto.serializer(), dto)
        val decodedDto = json.decodeFromString(ModulatorDto.serializer(), jsonStr)
        val restored = decodedDto.toDomain()

        assertEquals(original.sourceId, restored.sourceId)
        assertEquals(original.genUnit, restored.genUnit)
        assertEquals(original.subdivision, restored.subdivision)
        assertEquals(original.seqStepCount, restored.seqStepCount)
        assertEquals(original.seqSteps, restored.seqSteps)
        assertEquals(original.seqHold, restored.seqHold)
        assertEquals(original.seqCurveSmooth, restored.seqCurveSmooth)
        assertEquals(original.depth, restored.depth)
    }

    @Test
    fun testBackwardCompatibilityWithoutSequencerFields() {
        // Old JSON without seqSteps, seqStepCount, seqHold, seqCurveSmooth
        val oldJson = """
            {
                "sourceId": "seq",
                "operator": "ADD",
                "depth": 0.5,
                "bypassed": false,
                "waveform": "SINE",
                "subdivision": 1.0,
                "phaseOffset": 0.0,
                "slope": 0.5,
                "lfoSpeedMode": "FAST",
                "genUnit": "BEAT",
                "modGenUnit": "TIME"
            }
        """.trimIndent()

        val decodedDto = json.decodeFromString(ModulatorDto.serializer(), oldJson)
        val restored = decodedDto.toDomain()

        assertEquals("seq", restored.sourceId)
        assertEquals(16, restored.seqStepCount)
        assertEquals(1.0f, restored.seqHold)
        assertEquals(false, restored.seqCurveSmooth)
        assertEquals(32, restored.seqSteps.size)
    }
}
