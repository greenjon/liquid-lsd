package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import org.junit.jupiter.api.io.TempDir
import java.io.File
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
    fun aTapAppliesToTheBoundTargetAndIsANoOpUnbound(@TempDir dir: File) {
        BrowseRecents.resetForTest(BrowseKind.SRC, File(dir, "src_recent.json"))
        val applied = mutableListOf<String>()
        DockActions.tap(null, asset)
        assertTrue(applied.isEmpty())
        DockActions.tap(target(applied), asset)
        assertEquals(listOf("/x/a.lsdpreset"), applied)
        // An apply is remembered for the Recent folder; the unbound tap (a plain selection) is not.
        assertEquals(listOf("/x/a.lsdpreset"), BrowseRecents.keys(BrowseKind.SRC))
    }

    @Test
    fun listManagementIsLibraryOnly() {
        assertTrue(DockActions.canManageList(null))
        assertFalse(DockActions.canManageList(target(mutableListOf())))
    }
}
