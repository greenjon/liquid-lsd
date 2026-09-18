package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroTargetType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RackManagerTest {

    @Test
    fun testAddAndRemoveUnits() {
        val manager = RackManager(allocateGlBuffers = false)
        assertEquals(0, manager.units.size)

        val u1 = GenericRackUnit(id = "u1", label = "Unit 1")
        val u2 = GenericRackUnit(id = "u2", label = "Unit 2")

        manager.addUnit(u1)
        manager.addUnit(u2)
        assertEquals(2, manager.units.size)
        assertEquals("u1", manager.units[0].id)
        assertEquals("u2", manager.units[1].id)

        // Insert at index 0
        val u0 = GenericRackUnit(id = "u0", label = "Unit 0")
        manager.addUnit(u0, atIndex = 0)
        assertEquals(3, manager.units.size)
        assertEquals("u0", manager.units[0].id)
        assertEquals("u1", manager.units[1].id)

        // Remove unit
        val removed = manager.removeUnit("u1")
        assertTrue(removed)
        assertEquals(2, manager.units.size)
        assertEquals("u0", manager.units[0].id)
        assertEquals("u2", manager.units[1].id)

        assertFalse(manager.removeUnit("nonexistent"))
    }

    @Test
    fun testReordering() {
        val manager = RackManager(allocateGlBuffers = false)
        val u0 = GenericRackUnit(id = "u0", label = "Unit 0")
        val u1 = GenericRackUnit(id = "u1", label = "Unit 1")
        val u2 = GenericRackUnit(id = "u2", label = "Unit 2")

        manager.addUnit(u0)
        manager.addUnit(u1)
        manager.addUnit(u2)

        // Move down u0
        assertTrue(manager.moveDown(0))
        assertEquals("u1", manager.units[0].id)
        assertEquals("u0", manager.units[1].id)
        assertEquals("u2", manager.units[2].id)

        // Move up u2
        assertTrue(manager.moveUp(2))
        assertEquals("u1", manager.units[0].id)
        assertEquals("u2", manager.units[1].id)
        assertEquals("u0", manager.units[2].id)

        // Boundary checks
        assertFalse(manager.moveUp(0))
        assertFalse(manager.moveDown(2))
    }

    @Test
    fun testMasterControls() {
        val manager = RackManager(allocateGlBuffers = false)
        val u0 = GenericRackUnit(id = "u0", label = "Unit 0")
        val u1 = GenericRackUnit(id = "u1", label = "Unit 1")
        manager.addUnit(u0)
        manager.addUnit(u1)

        // Master fold
        assertFalse(manager.isMasterFolded)
        manager.toggleFoldAll()
        assertTrue(manager.isMasterFolded)
        assertTrue(u0.isCollapsed)
        assertTrue(u1.isCollapsed)

        manager.toggleFoldAll()
        assertFalse(manager.isMasterFolded)
        assertFalse(u0.isCollapsed)
        assertFalse(u1.isCollapsed)

        // Master bypass
        assertFalse(manager.isMasterBypassed)
        manager.toggleMasterBypass()
        assertTrue(manager.isMasterBypassed)
        assertTrue(u0.isBypassed)
        assertTrue(u1.isBypassed)

        // Clear solo
        u0.isSoloed = true
        manager.clearAllSolo()
        assertFalse(u0.isSoloed)
    }

    /**
     * [RackManager.populateFromSession] now sources each built-in unit's macroBank straight from
     * [MacroEngine.getBank] rather than a rack-owned shadow copy -- so a bank registered under a
     * unit's stable id (e.g. by [llm.slop.liquidlsd.presets.SessionSerializer.loadSession] before
     * the Rack workspace is ever drawn) is simply *the* bank that unit ends up wired to; there's no
     * separate capture/restore step left to test at the RackManager level (see [MacroEngine]'s own
     * tests for bank registration coverage).
     */
    @Test
    fun testAddUnitRegistersItsExistingMacroBankUnchanged() {
        val manager = RackManager(allocateGlBuffers = false)
        val curatedKnob = MacroControl(
            label = "ZOOM",
            bindings = mutableListOf(
                MacroBinding(
                    unitInstanceId = RackManager.DECK_A_UNIT_ID,
                    parameterId = "viewZoom",
                    targetType = MacroTargetType.PARAM_BASE_VALUE,
                    minVal = 0.2f,
                    maxVal = 3.0f
                )
            )
        )
        val curatedBank = MacroBank(knobs = listOf(curatedKnob) + List(7) { MacroControl(label = "KNOB ${it + 2}") })
        val deckAUnit = GenericRackUnit(id = RackManager.DECK_A_UNIT_ID, label = "Deck A", macroBank = curatedBank)

        manager.addUnit(deckAUnit)

        val registered = MacroEngine.getBank(RackManager.DECK_A_UNIT_ID)
        assertSame(curatedBank, registered)
        assertEquals("ZOOM", registered!!.knobs[0].label)
        val restoredBinding = registered.knobs[0].bindings.first()
        assertEquals(RackManager.DECK_A_UNIT_ID, restoredBinding.unitInstanceId)
        assertEquals("viewZoom", restoredBinding.parameterId)
        assertEquals(0.2f, restoredBinding.minVal)
        assertEquals(3.0f, restoredBinding.maxVal)

        manager.dispose()
    }
}
