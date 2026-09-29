package llm.slop.liquidlsd.ui

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun testFineSweepMultiplierSlowsDragDelta() {
        val normalSweep = 200f
        val fineSweep = normalSweep * MacroKnobWidget.FINE_SWEEP_MULTIPLIER
        val normalResult = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaXPixels = 60f, dragDeltaYPixels = 0f, pixelsForFullSweep = normalSweep)
        val fineResult = MacroKnobWidget.applyDragDelta(currentValue = 0.5f, dragDeltaXPixels = 60f, dragDeltaYPixels = 0f, pixelsForFullSweep = fineSweep)

        assertEquals(0.80f, normalResult, 1e-5f)
        assertEquals(0.55f, fineResult, 1e-5f)
    }

    // -- valueToAngleRadians ---------------------------------------------------------------

    @Test
    fun testValueZeroMapsToMinus135Degrees() {
        val expected = -135f * (PI.toFloat() / 180f)
        assertEquals(expected, MacroKnobWidget.valueToAngleRadians(0f), 1e-5f)
        assertEquals(expected, MacroKnobWidget.valueToAngleRadians(0f, llm.slop.liquidlsd.parameters.MeterType.BIPOLAR), 1e-5f)
    }

    @Test
    fun testValueOneMapsToPlus135Degrees() {
        val expected = 135f * (PI.toFloat() / 180f)
        assertEquals(expected, MacroKnobWidget.valueToAngleRadians(1f), 1e-5f)
        assertEquals(expected, MacroKnobWidget.valueToAngleRadians(1f, llm.slop.liquidlsd.parameters.MeterType.BIPOLAR), 1e-5f)
    }

    @Test
    fun testValueHalfMapsToZeroDegrees() {
        assertEquals(0f, MacroKnobWidget.valueToAngleRadians(0.5f), 1e-5f)
        assertEquals(0f, MacroKnobWidget.valueToAngleRadians(0.5f, llm.slop.liquidlsd.parameters.MeterType.BIPOLAR), 1e-5f)
    }

    @Test
    fun testEndlessKnobAngleMapping() {
        // Endless 0 maps to knob PI (which in screen space is PI/2 = 6 o'clock straight down)
        val angle0 = MacroKnobWidget.valueToAngleRadians(0f, llm.slop.liquidlsd.parameters.MeterType.ENDLESS)
        assertEquals(PI.toFloat(), angle0, 1e-5f)
        assertEquals(PI.toFloat() / 2f, MacroKnobWidget.toScreenAngle(angle0), 1e-5f)

        // Endless 0.5 maps to knob 2*PI (which in screen space is 12 o'clock straight up, -PI/2 or 3*PI/2)
        val angleHalf = MacroKnobWidget.valueToAngleRadians(0.5f, llm.slop.liquidlsd.parameters.MeterType.ENDLESS)
        assertEquals(2f * PI.toFloat(), angleHalf, 1e-5f)
        val screenHalf = MacroKnobWidget.toScreenAngle(angleHalf)
        // sin(screenHalf) should be -1 (straight up) and cos should be 0
        assertEquals(0f, kotlin.math.cos(screenHalf), 1e-5f)
        assertEquals(-1f, kotlin.math.sin(screenHalf), 1e-5f)

        // Endless 1.0 maps to knob 3*PI (which in screen space is 6 o'clock straight down, same orientation as 0)
        val angleOne = MacroKnobWidget.valueToAngleRadians(1f, llm.slop.liquidlsd.parameters.MeterType.ENDLESS)
        assertEquals(3f * PI.toFloat(), angleOne, 1e-5f)
        val screenOne = MacroKnobWidget.toScreenAngle(angleOne)
        assertEquals(0f, kotlin.math.cos(screenOne), 1e-5f)
        assertEquals(1f, kotlin.math.sin(screenOne), 1e-5f)
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

    // -- formatDisplayValue ----------------------------------------------------------------

    @Test
    fun testFormatDisplayValueHandlesIntegersAndFloats() {
        assertEquals("5", MacroKnobWidget.formatDisplayValue(5.0f))
        assertEquals("0", MacroKnobWidget.formatDisplayValue(0.0f))
        assertEquals("-1", MacroKnobWidget.formatDisplayValue(-1.0f))
        assertEquals("0.25", MacroKnobWidget.formatDisplayValue(0.25f))
        assertEquals("1.50", MacroKnobWidget.formatDisplayValue(1.5f))
    }

    // -- Cursor Locking State & Lifecycle --------------------------------------------------

    @Test
    fun testDragLockDeadzoneConstantIsThreePixels() {
        assertEquals(3.0f, MacroKnobWidget.DRAG_LOCK_DEADZONE_PX, 1e-6f)
    }

    @Test
    fun testClearRequestsResetFlags() {
        MacroKnobWidget.wantsCursorLock = true
        MacroKnobWidget.wantsCursorRelease = true

        MacroKnobWidget.clearCursorLockRequest()
        assertFalse(MacroKnobWidget.wantsCursorLock)
        assertTrue(MacroKnobWidget.wantsCursorRelease)

        MacroKnobWidget.clearCursorReleaseRequest()
        assertFalse(MacroKnobWidget.wantsCursorRelease)
    }

    @Test
    fun testAbortDragWhenNotLockedCleansUpActiveKnobIdWithoutRequestingRelease() {
        MacroKnobWidget.activeKnobId = "test_knob_1"
        MacroKnobWidget.editingKnobId = "test_knob_1"
        MacroKnobWidget.isDragLocked = false
        MacroKnobWidget.wantsCursorLock = true
        MacroKnobWidget.wantsCursorRelease = false

        MacroKnobWidget.abortDrag()

        kotlin.test.assertNull(MacroKnobWidget.activeKnobId)
        kotlin.test.assertNull(MacroKnobWidget.editingKnobId)
        assertFalse(MacroKnobWidget.isDragLocked)
        assertFalse(MacroKnobWidget.wantsCursorLock)
        assertFalse(MacroKnobWidget.wantsCursorRelease)
    }

    @Test
    fun testAbortDragWhenLockedTriggersReleaseRequestAndClearsState() {
        MacroKnobWidget.activeKnobId = "test_knob_2"
        MacroKnobWidget.editingKnobId = "test_knob_2"
        MacroKnobWidget.isDragLocked = true
        MacroKnobWidget.lockOriginX = 120f
        MacroKnobWidget.lockOriginY = 340f
        MacroKnobWidget.wantsCursorLock = false
        MacroKnobWidget.wantsCursorRelease = false

        MacroKnobWidget.abortDrag()

        kotlin.test.assertNull(MacroKnobWidget.activeKnobId)
        kotlin.test.assertNull(MacroKnobWidget.editingKnobId)
        assertFalse(MacroKnobWidget.isDragLocked)
        assertFalse(MacroKnobWidget.wantsCursorLock)
        assertTrue(MacroKnobWidget.wantsCursorRelease)
        assertEquals(120f, MacroKnobWidget.lockOriginX)
        assertEquals(340f, MacroKnobWidget.lockOriginY)

        MacroKnobWidget.clearCursorReleaseRequest()
        assertFalse(MacroKnobWidget.wantsCursorRelease)
    }
}
