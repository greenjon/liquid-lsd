package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rack.ui.RackChassisRenderer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RackUnitTest {

    @Test
    fun testRackUnitCreationAndDefaults() {
        val unit = GenericRackUnit(
            id = "test_unit_1",
            label = "Test Synth",
            unitType = RackUnitType.GENERATOR,
            heightU = 2
        )

        assertEquals("test_unit_1", unit.id)
        assertEquals("Test Synth", unit.label)
        assertEquals(RackUnitType.GENERATOR, unit.unitType)
        assertEquals(2, unit.heightU)
        assertFalse(unit.isCollapsed)
        assertTrue(unit.isPowered)
        assertFalse(unit.isBypassed)
        assertFalse(unit.isSoloed)
    }

    @Test
    fun testHeightQuantization() {
        val u1Height = RackChassisRenderer.calculateUnitHeight(1, isCollapsed = false)
        val u2Height = RackChassisRenderer.calculateUnitHeight(2, isCollapsed = false)
        val u3Height = RackChassisRenderer.calculateUnitHeight(3, isCollapsed = false)
        val collapsedHeight = RackChassisRenderer.calculateUnitHeight(2, isCollapsed = true)

        assertEquals(RackChassisRenderer.U_HEIGHT_PX, u1Height)
        assertEquals(RackChassisRenderer.U_HEIGHT_PX * 2f, u2Height)
        assertEquals(RackChassisRenderer.U_HEIGHT_PX * 3f, u3Height)
        assertEquals(RackChassisRenderer.SPINE_HEIGHT_PX, collapsedHeight)
    }

    @Test
    fun testBypassPassthrough() {
        var processed = false
        val unit = GenericRackUnit(
            id = "bypass_test",
            label = "Test FX",
            unitType = RackUnitType.PROCESSOR,
            onProcess = { input ->
                processed = true
                input + 100
            }
        )

        val inputTex = 42

        // Normal active execution
        val activeOut = unit.process(inputTex, null, 1920, 1080, null)
        assertTrue(processed)
        assertEquals(142, activeOut)

        // Bypassed execution
        processed = false
        unit.isBypassed = true
        val bypassedOut = unit.process(inputTex, null, 1920, 1080, null)
        assertFalse(processed)
        assertEquals(inputTex, bypassedOut)
    }

    @Test
    fun testPowerOffPassthrough() {
        var processed = false
        val unit = GenericRackUnit(
            id = "power_test",
            label = "Test FX",
            unitType = RackUnitType.PROCESSOR,
            onProcess = { input ->
                processed = true
                input + 50
            }
        )

        unit.isPowered = false
        val out = unit.process(10, null, 1920, 1080, null)
        assertFalse(processed)
        assertEquals(10, out)
    }
}
