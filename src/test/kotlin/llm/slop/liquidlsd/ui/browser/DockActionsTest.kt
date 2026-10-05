package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockActionsTest {
    private val asset = AssetItem(path = "/x/a.lsdpreset", name = "a", type = AssetType.PRESET)

    private fun target(applied: MutableList<String>) = ApplyTarget(
        kind = BrowseKind.SRC, contextKey = "gen/Deck A", defaultScope = BrowseScope.All,
        accepts = { true }, isApplied = { false }, apply = { applied += it.path }
    )

    @Test
    fun aTapAppliesToTheBoundTargetAndIsANoOpUnbound() {
        val applied = mutableListOf<String>()
        DockActions.tap(null, asset)
        assertTrue(applied.isEmpty())
        DockActions.tap(target(applied), asset)
        assertEquals(listOf("/x/a.lsdpreset"), applied)
    }

    @Test
    fun listManagementIsLibraryOnly() {
        assertTrue(DockActions.canManageList(null))
        assertFalse(DockActions.canManageList(target(mutableListOf())))
    }
}
