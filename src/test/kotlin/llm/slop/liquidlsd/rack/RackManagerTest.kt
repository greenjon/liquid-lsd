package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroControl
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
     * Mirrors the exact "snapshot live units before rebuild" step at the top of
     * [RackManager.populateFromSession] (real Deck/Mixer construction needs a live GL context, so
     * it can't be exercised end-to-end here -- see the class doc comment on that method). Verifies
     * the underlying contract that method relies on: a rack unit's live [RackUnit.macroBank]
     * (e.g. curated per-unit bindings from RackUnitMacroCuration) captured into
     * [RackManager.persistedUnitMacroBanks] keyed by its stable [RackUnit.id] round-trips back out
     * unchanged, which is what lets a rebuilt unit with the same id recover its curation instead
     * of falling back to hardcoded defaults.
     */
    @Test
    fun testPersistedUnitMacroBanksCapturesAndRestoresLiveCuration() {
        val manager = RackManager(allocateGlBuffers = false)
        assertTrue(manager.persistedUnitMacroBanks.isEmpty(), "No persisted state until a unit has actually been curated/captured")

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

        // Same snapshot expression populateFromSession runs before wiping `units` on a rebuild
        // (e.g. a RE-SYNC SESSION click).
        manager.persistedUnitMacroBanks = manager.persistedUnitMacroBanks + manager.units.associate { it.id to it.macroBank }

        val restored = manager.persistedUnitMacroBanks[RackManager.DECK_A_UNIT_ID]
        assertNotNull(restored)
        assertEquals("ZOOM", restored!!.knobs[0].label)
        val restoredBinding = restored.knobs[0].bindings.first()
        assertEquals(RackManager.DECK_A_UNIT_ID, restoredBinding.unitInstanceId)
        assertEquals("viewZoom", restoredBinding.parameterId)
        assertEquals(0.2f, restoredBinding.minVal)
        assertEquals(3.0f, restoredBinding.maxVal)
    }
}
