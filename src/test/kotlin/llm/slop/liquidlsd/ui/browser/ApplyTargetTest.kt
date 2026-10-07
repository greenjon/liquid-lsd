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

    private fun target(kind: BrowseKind, scope: BrowseScope) =
        ApplyTarget(kind, "ctx", scope, { true }, { false }, {})

    @Test
    fun targetsShareTheLibrarysScopeExceptTheWholeChainTarget() {
        val all = BrowseScope.All
        val slot = target(BrowseKind.FX, ApplyTarget.defaultFxScope(0))
        val chain = target(BrowseKind.FX, ApplyTarget.defaultFxScope(null))
        assertEquals(ScopeMemory.LIBRARY, ScopeMemory.contextOf(null))
        assertEquals(ScopeMemory.LIBRARY, ScopeMemory.contextOf(slot))
        assertEquals(ScopeMemory.LIBRARY, ScopeMemory.contextOf(target(BrowseKind.SRC, all)))
        assertEquals(ScopeMemory.CHAINS, ScopeMemory.contextOf(chain))
    }

    @Test
    fun scopeMemoryKeepsTheScopeAcrossSharedContextsAndRestoresTheChainBucket() {
        val memory = ScopeMemory()
        val all = BrowseScope.All
        val stock = BrowseScope.Folder(BrowseSection.STOCK)
        val chains = BrowseScope.Folder(BrowseSection.CHAIN)
        // Library, then a slot target: the same bucket, nothing moves.
        assertEquals(stock to false, memory.enter(BrowseKind.FX, ScopeMemory.LIBRARY, stock, all))
        // A whole-chain target has its own bucket and starts at its default...
        assertEquals(chains to true, memory.enter(BrowseKind.FX, ScopeMemory.CHAINS, stock, chains))
        // ...and coming back restores the scope it left.
        assertEquals(stock to true, memory.enter(BrowseKind.FX, ScopeMemory.LIBRARY, chains, all))
    }

    @Test
    fun scopeMemoryKeepsKindsApart() {
        val memory = ScopeMemory()
        memory.enter(BrowseKind.SRC, ScopeMemory.LIBRARY, BrowseScope.All, BrowseScope.All)
        assertEquals(BrowseScope.All to false, memory.enter(BrowseKind.FX, ScopeMemory.LIBRARY, BrowseScope.All, BrowseScope.All))
    }
}
