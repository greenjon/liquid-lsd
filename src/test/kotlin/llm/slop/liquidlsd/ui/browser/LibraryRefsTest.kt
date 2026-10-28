package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.ui.PlaylistManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class LibraryRefsTest {
    @Test
    fun `kind follows the saved file's extension`() {
        assertEquals(BrowseKind.SRC, LibraryRefs.kindOf(File("a.lsd")))
        assertEquals(BrowseKind.FX, LibraryRefs.kindOf(File("a.lsdfx")))
        assertEquals(BrowseKind.FX, LibraryRefs.kindOf(File("a.lsdfxchain")))
        assertEquals(BrowseKind.TRANS, LibraryRefs.kindOf(File("a.lsdtrans")))
        assertNull(LibraryRefs.kindOf(File("a.lsdplay")))
    }

    @Test
    fun `token playlists follow moved files, and bare names that still match are left alone`() {
        val items = listOf("/lib/fx/a.lsdfx", "sub/b.lsdfx", "c", "/lib/fx/other.lsdfx")
        val moves = mapOf(
            "/lib/fx/a.lsdfx" to "/lib/fx/dark/a.lsdfx",
            "/lib/fx/sub/b.lsdfx" to "/lib/fx/b2.lsdfx",
            "/lib/fx/c.lsdfx" to "/lib/fx/dark/c.lsdfx"
        )
        assertEquals(
            listOf("/lib/fx/dark/a.lsdfx", "/lib/fx/b2.lsdfx", "c", "/lib/fx/other.lsdfx"),
            PlaylistItems.relocate(items, moves)
        )
        // A rename changes the name, so even a bare token has to follow.
        assertEquals(listOf("/lib/fx/d.lsdfx"), PlaylistItems.relocate(listOf("c"), mapOf("/lib/fx/c.lsdfx" to "/lib/fx/d.lsdfx")))
    }

    @Test
    fun `preset playlists rewrite relative and absolute entries in one pass`(@TempDir dir: File) {
        val presets = File(dir, "presets").apply { mkdirs() }
        val playlists = File(dir, "playlists").apply { mkdirs() }
        val a = File(presets, "a.lsd")
        val b = File(presets, "b.lsd")
        val list = File(playlists, "set.lsdplay")
        list.writeText("a.lsd\n${b.absolutePath}\nkeep.lsd\n")

        PlaylistManager.updatePresetPathsInAllPlaylists(
            mapOf(a.absolutePath to File(presets, "garden/a.lsd").absolutePath, b.absolutePath to File(presets, "b2.lsd").absolutePath),
            presetsRoot = presets, playlistsRoot = playlists
        )
        assertEquals(listOf("garden/a.lsd", "b2.lsd", "keep.lsd"), list.readLines())
    }

    @Test
    fun `queues swap moved files in place`() {
        val queue = CopyOnWriteArrayList(listOf(File("/q/a.lsd"), File("/q/b.lsd"), File("/q/a.lsd")))
        LibraryRefs.relocateQueue(queue, mapOf(File("/q/a.lsd").absolutePath to File("/q/x/a.lsd").absolutePath))
        assertEquals(listOf(File("/q/x/a.lsd"), File("/q/b.lsd"), File("/q/x/a.lsd")), queue.toList())
    }

    @Test
    fun `favorites and recents follow moves and drop deleted files`(@TempDir dir: File) {
        BrowseFavorites.resetForTest(BrowseKind.SRC, File(dir, "fav.json"))
        BrowseRecents.resetForTest(BrowseKind.SRC, File(dir, "recent.json"))
        BrowseFavorites.toggle(BrowseKind.SRC, "/lib/a.lsd")
        BrowseFavorites.toggle(BrowseKind.SRC, "/lib/b.lsd")
        BrowseRecents.note(BrowseKind.SRC, "/lib/b.lsd")
        BrowseRecents.note(BrowseKind.SRC, "/lib/a.lsd")

        BrowseFavorites.relocate(BrowseKind.SRC, mapOf("/lib/a.lsd" to "/lib/g/a.lsd"))
        BrowseRecents.relocate(BrowseKind.SRC, mapOf("/lib/a.lsd" to "/lib/g/a.lsd"))
        assertEquals(listOf("/lib/g/a.lsd", "/lib/b.lsd"), BrowseFavorites.keys(BrowseKind.SRC).toList())
        assertEquals(listOf("/lib/g/a.lsd", "/lib/b.lsd"), BrowseRecents.keys(BrowseKind.SRC))

        BrowseFavorites.forget(BrowseKind.SRC, setOf("/lib/b.lsd"))
        BrowseRecents.forget(BrowseKind.SRC, setOf("/lib/b.lsd"))
        assertEquals(listOf("/lib/g/a.lsd"), BrowseFavorites.keys(BrowseKind.SRC).toList())
        assertEquals(listOf("/lib/g/a.lsd"), BrowseRecents.keys(BrowseKind.SRC))

        // And it was saved: a fresh load sees the moved key.
        BrowseRecents.resetForTest(BrowseKind.SRC, File(dir, "recent.json"))
        assertEquals(listOf("/lib/g/a.lsd"), BrowseRecents.keys(BrowseKind.SRC))
    }

    @Test
    fun `a deck follows its preset file, and a rename renames the deck's preset`() {
        val old = File("/lib/presets/moss.lsd")
        try {
            PresetManager.setActive(DeckSlot.A, "moss", null, old)
            PresetManager.setActive(DeckSlot.B, "other", null, File("/lib/presets/other.lsd"))

            PresetManager.relocateActive(old, File("/lib/presets/garden/moss.lsd"))
            assertEquals(File("/lib/presets/garden/moss.lsd"), PresetManager.activeFile(DeckSlot.A))
            assertEquals("moss", PresetManager.activePreset(DeckSlot.A))

            PresetManager.relocateActive(File("/lib/presets/garden/moss.lsd"), File("/lib/presets/garden/moss2.lsd"))
            assertEquals("moss2", PresetManager.activePreset(DeckSlot.A))
            // Save-back now targets the renamed file, not a fresh root-level moss.lsd.
            assertEquals(File("/lib/presets/garden/moss2.lsd"), PresetManager.saveFileFor(DeckSlot.A, "moss2"))
            assertEquals("other", PresetManager.activePreset(DeckSlot.B))

            PresetManager.forgetActive(File("/lib/presets/garden/moss2.lsd"))
            assertNull(PresetManager.activeFile(DeckSlot.A))
        } finally {
            DeckSlot.entries.forEach { PresetManager.clearActive(it) }
        }
    }
}
