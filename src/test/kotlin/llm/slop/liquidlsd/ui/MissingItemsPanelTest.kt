package llm.slop.liquidlsd.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MissingItemsPanelTest {
    @Test
    fun transitionAssetsRelinkToTheTransitionQueue() {
        assertTrue(MissingItemsPanel.isTransitionAsset(File("wipe.lsdtrans")))
        assertTrue(MissingItemsPanel.isTransitionAsset(File("set.LSDTRANSPLAY")))
    }

    @Test
    fun presetsAndPlaylistsRelinkToThePlayQueue() {
        assertFalse(MissingItemsPanel.isTransitionAsset(File("look.lsd")))
        assertFalse(MissingItemsPanel.isTransitionAsset(File("show.lsdplay")))
        assertFalse(MissingItemsPanel.isTransitionAsset(File("noextension")))
    }
}
