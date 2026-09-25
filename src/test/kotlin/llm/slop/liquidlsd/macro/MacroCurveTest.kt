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

    @Test
    fun testWindowFullIsIdentity() {
        for (v in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertEquals(v, MacroCurve.window(v, MacroLinkMode.FULL))
        }
    }

    @Test
    fun testWindowFirstHalfSweepsThenHolds() {
        assertEquals(0f, MacroCurve.window(0f, MacroLinkMode.FIRST_HALF))
        assertEquals(0.5f, MacroCurve.window(0.25f, MacroLinkMode.FIRST_HALF), absoluteTolerance = 1e-6f)
        assertEquals(1f, MacroCurve.window(0.5f, MacroLinkMode.FIRST_HALF), absoluteTolerance = 1e-6f)
        assertEquals(1f, MacroCurve.window(0.75f, MacroLinkMode.FIRST_HALF))
        assertEquals(1f, MacroCurve.window(1f, MacroLinkMode.FIRST_HALF))
    }

    @Test
    fun testWindowSecondHalfHoldsThenSweeps() {
        assertEquals(0f, MacroCurve.window(0f, MacroLinkMode.SECOND_HALF))
        assertEquals(0f, MacroCurve.window(0.25f, MacroLinkMode.SECOND_HALF))
        assertEquals(0f, MacroCurve.window(0.5f, MacroLinkMode.SECOND_HALF), absoluteTolerance = 1e-6f)
        assertEquals(0.5f, MacroCurve.window(0.75f, MacroLinkMode.SECOND_HALF), absoluteTolerance = 1e-6f)
        assertEquals(1f, MacroCurve.window(1f, MacroLinkMode.SECOND_HALF), absoluteTolerance = 1e-6f)
    }

    @Test
    fun testWindowTriangleReachesPeakAtCenter() {
        assertEquals(0f, MacroCurve.window(0f, MacroLinkMode.TRIANGLE))
        assertEquals(1f, MacroCurve.window(0.5f, MacroLinkMode.TRIANGLE), absoluteTolerance = 1e-6f)
        assertEquals(0f, MacroCurve.window(1f, MacroLinkMode.TRIANGLE), absoluteTolerance = 1e-6f)
        assertEquals(0.5f, MacroCurve.window(0.25f, MacroLinkMode.TRIANGLE), absoluteTolerance = 1e-6f)
        assertEquals(0.5f, MacroCurve.window(0.75f, MacroLinkMode.TRIANGLE), absoluteTolerance = 1e-6f)
    }

    @Test
    fun testWindowBipolarIsNeutralAtCenter() {
        assertEquals(1f, MacroCurve.window(0f, MacroLinkMode.BIPOLAR), absoluteTolerance = 1e-6f)
        assertEquals(0f, MacroCurve.window(0.5f, MacroLinkMode.BIPOLAR), absoluteTolerance = 1e-6f)
        assertEquals(1f, MacroCurve.window(1f, MacroLinkMode.BIPOLAR), absoluteTolerance = 1e-6f)
    }

    @Test
    fun testMapToRangeComposesWindowWithCurveAndInvert() {
        val firstHalf = MacroBinding(
            parameterId = "test",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = 0f,
            maxVal = 1f,
            curve = MacroCurveType.LINEAR,
            linkMode = MacroLinkMode.FIRST_HALF
        )
        assertEquals(1f, MacroCurve.mapToRange(0.5f, firstHalf), absoluteTolerance = 1e-6f)
        assertEquals(1f, MacroCurve.mapToRange(1f, firstHalf), absoluteTolerance = 1e-6f)

        val secondHalfInverted = firstHalf.copy(linkMode = MacroLinkMode.SECOND_HALF, inverted = true)
        assertEquals(1f, MacroCurve.mapToRange(0.25f, secondHalfInverted), absoluteTolerance = 1e-6f)
        assertEquals(0f, MacroCurve.mapToRange(1f, secondHalfInverted), absoluteTolerance = 1e-6f)

        val triangleExp = firstHalf.copy(linkMode = MacroLinkMode.TRIANGLE, curve = MacroCurveType.EXPONENTIAL)
        assertEquals(1f, MacroCurve.mapToRange(0.5f, triangleExp), absoluteTolerance = 1e-6f)
        assertEquals(0f, MacroCurve.mapToRange(0f, triangleExp), absoluteTolerance = 1e-6f)
        assertEquals(0f, MacroCurve.mapToRange(1f, triangleExp), absoluteTolerance = 1e-6f)
    }

    @Test
    fun testTwoBindingsOnOneKnobSplitZonesIndependently() {
        // Mirrors the FX/macro choreography use case: one knob driving two targets in split zones.
        val firstTarget = MacroBinding(
            parameterId = "targetA",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = 0f,
            maxVal = 1f,
            linkMode = MacroLinkMode.FIRST_HALF
        )
        val secondTarget = MacroBinding(
            parameterId = "targetB",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = 0f,
            maxVal = 1f,
            linkMode = MacroLinkMode.SECOND_HALF
        )

        // At knob=0.25: target A is mid-sweep, target B is still idle at 0.
        assertEquals(0.5f, MacroCurve.mapToRange(0.25f, firstTarget), absoluteTolerance = 1e-6f)
        assertEquals(0f, MacroCurve.mapToRange(0.25f, secondTarget), absoluteTolerance = 1e-6f)

        // At knob=0.75: target A has clamped at 1.0, target B is mid-sweep.
        assertEquals(1f, MacroCurve.mapToRange(0.75f, firstTarget), absoluteTolerance = 1e-6f)
        assertEquals(0.5f, MacroCurve.mapToRange(0.75f, secondTarget), absoluteTolerance = 1e-6f)
    }

    @Test
    fun testNaNHandlingSanitizesToZero() {
        assertEquals(0f, MacroCurve.window(Float.NaN, MacroLinkMode.FULL), absoluteTolerance = 1e-6f)
        assertEquals(0f, MacroCurve.shape(Float.NaN, MacroCurveType.LINEAR, 8), absoluteTolerance = 1e-6f)
        val binding = MacroBinding(
            parameterId = "test",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = 0f,
            maxVal = 10f
        )
        assertEquals(0f, MacroCurve.mapToRange(Float.NaN, binding), absoluteTolerance = 1e-6f)
    }

    @Test
    fun testInverseRoundTripForLinearExponentialAndLogarithmic() {
        val curves = listOf(MacroCurveType.LINEAR, MacroCurveType.EXPONENTIAL, MacroCurveType.LOGARITHMIC)
        val invertedFlags = listOf(false, true)
        val testValues = listOf(0.0f, 0.1f, 0.25f, 0.5f, 0.73f, 0.9f, 1.0f)

        for (curve in curves) {
            for (inverted in invertedFlags) {
                val binding = MacroBinding(
                    parameterId = "test",
                    targetType = MacroTargetType.PARAM_BASE_VALUE,
                    minVal = 2.0f,
                    maxVal = 10.0f,
                    curve = curve,
                    inverted = inverted,
                    linkMode = MacroLinkMode.FULL
                )
                for (v in testValues) {
                    val target = MacroCurve.mapToRange(v, binding)
                    val invertedV = MacroCurve.inverse(target, binding)
                    assertEquals(v, invertedV, absoluteTolerance = 1e-4f,
                        message = "Failed inverse round-trip for curve=$curve, inverted=$inverted, v=$v (target=$target, invertedV=$invertedV)")
                }
            }
        }
    }

    @Test
    fun testInverseWithInvertedRangeEndpoints() {
        val binding = MacroBinding(
            parameterId = "test",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = 100f,
            maxVal = 0f,
            curve = MacroCurveType.EXPONENTIAL,
            inverted = false,
            linkMode = MacroLinkMode.FULL
        )
        for (v in listOf(0f, 0.2f, 0.5f, 0.8f, 1f)) {
            val target = MacroCurve.mapToRange(v, binding)
            val invertedV = MacroCurve.inverse(target, binding)
            assertEquals(v, invertedV, absoluteTolerance = 1e-4f)
        }
    }

    private fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float, message: String? = null) {
        assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, message ?: "Expected $expected but was $actual (tolerance $absoluteTolerance)")
    }
}
