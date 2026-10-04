package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.AssetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplyTargetTest {
    @Test
    fun fxSlotsTakeSingleEffectsAndTheChainTargetTakesChainsOnly() {
        assertTrue(ApplyTarget.acceptsFxSlot(AssetType.FX_STOCK))
        assertTrue(ApplyTarget.acceptsFxSlot(AssetType.FX_PRESET))
        assertFalse(ApplyTarget.acceptsFxSlot(AssetType.FX_CHAIN))
        assertTrue(ApplyTarget.acceptsFxChain(AssetType.FX_CHAIN))
        assertFalse(ApplyTarget.acceptsFxChain(AssetType.FX_STOCK))
        assertFalse(ApplyTarget.acceptsFxChain(AssetType.FX_PRESET))
    }

    @Test
    fun sourceAndTransitionTargetsTakeStockAndSavedRows() {
        assertTrue(ApplyTarget.acceptsSource(AssetType.SOURCE_STOCK))
        assertTrue(ApplyTarget.acceptsSource(AssetType.PRESET))
        assertFalse(ApplyTarget.acceptsSource(AssetType.PLAYLIST))
        assertTrue(ApplyTarget.acceptsTransition(AssetType.TRANSITION_STOCK))
        assertTrue(ApplyTarget.acceptsTransition(AssetType.TRANSITION_PRESET))
        assertFalse(ApplyTarget.acceptsTransition(AssetType.TRANSITION_PLAYLIST))
    }

    @Test
    fun fxTargetsStartInChainsOrStock() {
        assertEquals(BrowseScope.Folder(BrowseSection.CHAIN), ApplyTarget.defaultFxScope(null))
        assertEquals(BrowseScope.Folder(BrowseSection.STOCK), ApplyTarget.defaultFxScope(0))
    }

    @Test
    fun scopeMemoryRestoresEachContextsOwnScope() {
        val memory = ScopeMemory()
        val all = BrowseScope.All
        val stock = BrowseScope.Folder(BrowseSection.STOCK)
        val chains = BrowseScope.Folder(BrowseSection.CHAIN)
        // Library first: nothing changes.
        assertEquals(all to false, memory.enter(BrowseKind.FX, ScopeMemory.LIBRARY, all, all))
        // Entering a slot target starts at its default and reports the move.
        assertEquals(stock to true, memory.enter(BrowseKind.FX, "fxslot/1/0", all, stock))
        // Staying put changes nothing, even if the user moved the scope meanwhile.
        assertEquals(chains to false, memory.enter(BrowseKind.FX, "fxslot/1/0", chains, stock))
        // Another target starts at its own default; coming back restores the stashed scope.
        assertEquals(chains to true, memory.enter(BrowseKind.FX, "chain/1", chains, chains))
        assertEquals(chains to true, memory.enter(BrowseKind.FX, "fxslot/1/0", chains, stock))
        // Back in the Library the scope it left is restored.
        assertEquals(all to true, memory.enter(BrowseKind.FX, ScopeMemory.LIBRARY, chains, all))
    }

    @Test
    fun scopeMemoryKeepsKindsApart() {
        val memory = ScopeMemory()
        memory.enter(BrowseKind.SRC, ScopeMemory.LIBRARY, BrowseScope.All, BrowseScope.All)
        assertEquals(BrowseScope.All to false, memory.enter(BrowseKind.FX, ScopeMemory.LIBRARY, BrowseScope.All, BrowseScope.All))
    }
}
