package llm.slop.liquidlsd.ui

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure math in [MacroKnobWidget]: [MacroKnobWidget.applyDragDelta] and
 * [MacroKnobWidget.valueToAngleRadians]. These require no ImGui context, unlike
 * [MacroKnobWidget.draw] which needs a real render loop to verify (see Phase 2 manual
 * verification notes).
 */
class MacroKnobWidgetTest {

    // -- applyDragDelta ------------------------------------------------------------------

    @Test
    fun testDraggingUpIncreasesValue() {
        val result = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaYPixels = -50f, pixelsForFullSweep = 200f)
        assertTrue(result > 0.5f, "Dragging up (negative Y delta) should increase the value, got $result")
    }

    @Test
    fun testDraggingDownDecreasesValue() {
        val result = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaYPixels = 50f, pixelsForFullSweep = 200f)
        assertTrue(result < 0.5f, "Dragging down (positive Y delta) should decrease the value, got $result")
    }

    @Test
    fun testDragResultIsProportionalToPixelsForFullSweep() {
        // 50px of a 200px full sweep is a quarter of the range.
        val result = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaYPixels = -50f, pixelsForFullSweep = 200f)
        assertEquals(0.75f, result, 1e-5f)
    }

    @Test
    fun testDragResultClampsAtZero() {
        val result = MacroKnobWidget.applyDragDelta(currentValue = 0.1f, dragDeltaYPixels = 500f, pixelsForFullSweep = 200f)
        assertEquals(0f, result, 1e-6f)
    }

    @Test
    fun testDragResultClampsAtOne() {
        val result = MacroKnobWidget.applyDragDelta(currentValue = 0.9f, dragDeltaYPixels = -500f, pixelsForFullSweep = 200f)
        assertEquals(1f, result, 1e-6f)
    }

    @Test
    fun testDraggingFarBeyondSweepSaturatesRatherThanWrapping() {
        val resultUp = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaYPixels = -1_000_000f, pixelsForFullSweep = 200f)
        assertEquals(1f, resultUp, 1e-6f)
        val resultDown = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaYPixels = 1_000_000f, pixelsForFullSweep = 200f)
        assertEquals(0f, resultDown, 1e-6f)
    }

    @Test
    fun testZeroDragDeltaLeavesValueUnchanged() {
        val result = MacroKnobWidget.applyDragDelta(currentValue = 0.37f, dragDeltaYPixels = 0f, pixelsForFullSweep = 200f)
        assertEquals(0.37f, result, 1e-6f)
    }

    @Test
    fun testDraggingRightIncreasesValue() {
        val result = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaXPixels = 50f, dragDeltaYPixels = 0f, pixelsForFullSweep = 200f)
        assertTrue(result > 0.5f, "Dragging right (positive X delta) should increase the value, got $result")
        assertEquals(0.75f, result, 1e-5f)
    }

    @Test
    fun testDraggingLeftDecreasesValue() {
        val result = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaXPixels = -50f, dragDeltaYPixels = 0f, pixelsForFullSweep = 200f)
        assertTrue(result < 0.5f, "Dragging left (negative X delta) should decrease the value, got $result")
        assertEquals(0.25f, result, 1e-5f)
    }

    @Test
    fun testCombinedDiagonalDrag() {
        // Dragging right (+25px) and up (-25px) yields a net +50px change
        val result = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaXPixels = 25f, dragDeltaYPixels = -25f, pixelsForFullSweep = 200f)
        assertEquals(0.75f, result, 1e-5f)
    }

    @Test
    fun testHorizontalDragResultClampsAtZeroAndOne() {
        val clampedZero = MacroKnobWidget.applyDragDelta(currentValue = 0.2f, dragDeltaXPixels = -500f, dragDeltaYPixels = 0f, pixelsForFullSweep = 200f)
        assertEquals(0f, clampedZero, 1e-6f)

        val clampedOne = MacroKnobWidget.applyDragDelta(currentValue = 0.8f, dragDeltaXPixels = 500f, dragDeltaYPixels = 0f, pixelsForFullSweep = 200f)
        assertEquals(1f, clampedOne, 1e-6f)
    }

    // -- valueToAngleRadians ---------------------------------------------------------------

    @Test
    fun testValueZeroMapsToMinus135Degrees() {
        val expected = -135f * (PI.toFloat() / 180f)
        assertEquals(expected, MacroKnobWidget.valueToAngleRadians(0f), 1e-5f)
    }

    @Test
    fun testValueOneMapsToPlus135Degrees() {
        val expected = 135f * (PI.toFloat() / 180f)
        assertEquals(expected, MacroKnobWidget.valueToAngleRadians(1f), 1e-5f)
    }

    @Test
    fun testValueHalfMapsToZeroDegrees() {
        assertEquals(0f, MacroKnobWidget.valueToAngleRadians(0.5f), 1e-5f)
    }

    @Test
    fun testAngleMappingIsMonotonicallyIncreasingAcrossRange() {
        val samples = (0..20).map { it / 20f }
        val angles = samples.map { MacroKnobWidget.valueToAngleRadians(it) }
        for (i in 1 until angles.size) {
            assertTrue(angles[i] > angles[i - 1], "Angle should strictly increase with value: ${angles[i - 1]} -> ${angles[i]}")
        }
    }

    @Test
    fun testAngleMappingClampsValuesOutsideZeroToOne() {
        assertEquals(MacroKnobWidget.valueToAngleRadians(0f), MacroKnobWidget.valueToAngleRadians(-1f), 1e-5f)
        assertEquals(MacroKnobWidget.valueToAngleRadians(1f), MacroKnobWidget.valueToAngleRadians(2f), 1e-5f)
    }
}
