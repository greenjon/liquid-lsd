package llm.slop.liquidlsd.macro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacroCurveTest {

    @Test
    fun testLinearIsIdentity() {
        for (v in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertEquals(v, MacroCurve.shape(v, MacroCurveType.LINEAR, 8))
        }
    }

    @Test
    fun testExponentialAndLogarithmicBracketLinearAtMidpoint() {
        val exp = MacroCurve.shape(0.5f, MacroCurveType.EXPONENTIAL, 8)
        val log = MacroCurve.shape(0.5f, MacroCurveType.LOGARITHMIC, 8)
        assertTrue(exp < 0.5f, "Exponential curve should be below linear at v=0.5, was $exp")
        assertTrue(log > 0.5f, "Logarithmic curve should be above linear at v=0.5, was $log")
    }

    @Test
    fun testExponentialAndLogarithmicEndpoints() {
        assertEquals(0f, MacroCurve.shape(0f, MacroCurveType.EXPONENTIAL, 8))
        assertEquals(1f, MacroCurve.shape(1f, MacroCurveType.EXPONENTIAL, 8))
        assertEquals(0f, MacroCurve.shape(0f, MacroCurveType.LOGARITHMIC, 8))
        assertEquals(1f, MacroCurve.shape(1f, MacroCurveType.LOGARITHMIC, 8))
    }

    @Test
    fun testSCurveMidpointAndSymmetry() {
        val mid = MacroCurve.shape(0.5f, MacroCurveType.S_CURVE, 8)
        assertEquals(0.5f, mid, absoluteTolerance = 1e-6f)

        for (x in listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 0.7f, 0.8f, 0.9f)) {
            val sx = MacroCurve.shape(x, MacroCurveType.S_CURVE, 8)
            val sInv = MacroCurve.shape(1f - x, MacroCurveType.S_CURVE, 8)
            assertEquals(1f - sx, sInv, absoluteTolerance = 1e-5f, message = "S_CURVE should be symmetric around 0.5 for x=$x")
        }
    }

    @Test
    fun testStepProducesExactlyStepCountDistinctValuesSweepingZeroToOne() {
        val stepCount = 4
        val samples = (0..1000).map { it / 1000f }
        val outputs = samples.map { MacroCurve.shape(it, MacroCurveType.STEP, stepCount) }.toSortedSet()
        val expected = setOf(0f, 1f / 3f, 2f / 3f, 1f)
        assertEquals(expected.size, outputs.size, "Expected exactly $stepCount distinct step values, got $outputs")
        for (e in expected) {
            assertTrue(outputs.any { kotlin.math.abs(it - e) < 1e-5f }, "Expected step value $e to be present in $outputs")
        }
        // v=1.0 exactly must land on the last step (not overflow past it).
        assertEquals(1f, MacroCurve.shape(1.0f, MacroCurveType.STEP, stepCount), absoluteTolerance = 1e-6f)
    }

    @Test
    fun testStepSingleStepAlwaysZero() {
        assertEquals(0f, MacroCurve.shape(0f, MacroCurveType.STEP, 1))
        assertEquals(0f, MacroCurve.shape(0.5f, MacroCurveType.STEP, 1))
        assertEquals(0f, MacroCurve.shape(1f, MacroCurveType.STEP, 1))
    }

    @Test
    fun testInvertedFlipsOutput() {
        val binding = MacroBinding(
            parameterId = "test",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = 0f,
            maxVal = 1f,
            curve = MacroCurveType.LINEAR,
            inverted = true
        )
        assertEquals(1f, MacroCurve.mapToRange(0f, binding))
        assertEquals(0f, MacroCurve.mapToRange(1f, binding))
        assertEquals(0.75f, MacroCurve.mapToRange(0.25f, binding), absoluteTolerance = 1e-6f)
    }

    @Test
    fun testMapToRangeHandlesInvertedRangeDistinctFromInvertedFlag() {
        // minVal > maxVal is an "inverted range" via the endpoints themselves, not the `inverted` flag.
        val invertedRange = MacroBinding(
            parameterId = "test",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = 10f,
            maxVal = 0f,
            curve = MacroCurveType.LINEAR,
            inverted = false
        )
        assertEquals(10f, MacroCurve.mapToRange(0f, invertedRange), absoluteTolerance = 1e-6f)
        assertEquals(0f, MacroCurve.mapToRange(1f, invertedRange), absoluteTolerance = 1e-6f)
        assertEquals(5f, MacroCurve.mapToRange(0.5f, invertedRange), absoluteTolerance = 1e-6f)

        // Combining an inverted range with the `inverted` flag should un-invert it (both flips compose).
        val invertedRangeAndFlag = invertedRange.copy(inverted = true)
        assertEquals(0f, MacroCurve.mapToRange(0f, invertedRangeAndFlag), absoluteTolerance = 1e-6f)
        assertEquals(10f, MacroCurve.mapToRange(1f, invertedRangeAndFlag), absoluteTolerance = 1e-6f)
    }

    @Test
    fun testMapToRangeScalesIntoArbitraryRange() {
        val binding = MacroBinding(
            parameterId = "test",
            targetType = MacroTargetType.MODULATOR_PROPERTY,
            propertyName = "subdivision",
            minVal = 0.1f,
            maxVal = 10.0f,
            curve = MacroCurveType.LINEAR
        )
        assertEquals(0.1f, MacroCurve.mapToRange(0f, binding), absoluteTolerance = 1e-6f)
        assertEquals(10.0f, MacroCurve.mapToRange(1f, binding), absoluteTolerance = 1e-6f)
        assertEquals(5.05f, MacroCurve.mapToRange(0.5f, binding), absoluteTolerance = 1e-5f)
    }

    private fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float, message: String? = null) {
        assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, message ?: "Expected $expected but was $actual (tolerance $absoluteTolerance)")
    }
}
