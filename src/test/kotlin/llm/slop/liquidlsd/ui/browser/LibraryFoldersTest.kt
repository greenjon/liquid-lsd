package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LibraryFoldersTest {
    private fun preset(dir: File, name: String) = File(dir, "$name.lsd").apply { parentFile.mkdirs(); writeText("{}") }

    @Test
    fun `folder names reject paths and hidden names`() {
        assertNull(LibraryFolders.nameProblem("garden"))
        assertNotNull(LibraryFolders.nameProblem(""))
        assertNotNull(LibraryFolders.nameProblem("a/b"))
        assertNotNull(LibraryFolders.nameProblem("..\\up"))
        assertNotNull(LibraryFolders.nameProblem(".trash"))
    }

    @Test
    fun `new folders are listed even when empty, hidden folders are not`(@TempDir root: File) {
        assertTrue(LibraryFolders.createFolder(root, "", "garden").isSuccess)
        assertTrue(LibraryFolders.createFolder(root, "garden", "moss").isSuccess)
        File(root, ".trash").mkdirs()
        assertTrue(LibraryFolders.createFolder(root, "", "garden").isFailure, "an existing folder is refused")
        assertEquals(listOf("garden", "garden/moss"), LibraryFolders.dirsUnder(root))
    }

    @Test
    fun `renaming a folder maps every saved file inside to its new path`(@TempDir root: File) {
        val a = preset(File(root, "garden"), "a")
        val b = preset(File(root, "garden/moss"), "b")
        File(root, "garden/a.png").writeText("png")

        val moved = LibraryFolders.renameFolder(root, "garden", "yard").getOrThrow()
        assertEquals(mapOf(a to File(root, "yard/a.lsd"), b to File(root, "yard/moss/b.lsd")), moved)
        assertTrue(File(root, "yard/moss/b.lsd").isFile)
        assertTrue(File(root, "yard/a.png").isFile, "sidecars go with the folder")
        assertFalse(File(root, "garden").exists())
    }

    @Test
    fun `a folder can't move into itself`(@TempDir root: File) {
        preset(File(root, "garden/moss"), "b")
        assertTrue(LibraryFolders.moveFolder(root, "garden", "garden/moss").isFailure)
        val moved = LibraryFolders.moveFolder(root, "garden/moss", "").getOrThrow()
        assertEquals(File(root, "moss/b.lsd"), moved.values.single())
    }

    @Test
    fun `moving files reports clashes, or keeps both with a suffix`(@TempDir root: File) {
        val a = preset(root, "a")
        val b = preset(root, "b")
        val dest = File(root, "garden")
        preset(dest, "a")

        val plan = LibraryFolders.planMove(listOf(a, b), dest, keepBoth = false)
        assertEquals(listOf(a), plan.conflicts)
        assertEquals(mapOf(b to File(dest, "b.lsd")), plan.moves)

        val both = LibraryFolders.planMove(listOf(a, b), dest, keepBoth = true)
        assertEquals(mapOf(a to File(dest, "a_2.lsd"), b to File(dest, "b.lsd")), both.moves)

        val done = LibraryFolders.moveFiles(root, both.moves)
        assertEquals(2, done.size)
        assertTrue(File(dest, "a_2.lsd").isFile && File(dest, "a.lsd").isFile && !a.exists())
        // Moving files that are already there is a no-op.
        assertTrue(LibraryFolders.planMove(listOf(File(dest, "b.lsd")), dest, keepBoth = false).moves.isEmpty())
    }

    @Test
    fun `files never move out of the section`(@TempDir dir: File) {
        val root = File(dir, "presets")
        val a = preset(root, "a")
        assertTrue(LibraryFolders.moveFiles(root, mapOf(a to File(dir, "elsewhere/a.lsd"))).isEmpty())
        assertTrue(a.isFile)
    }

    @Test
    fun `deleting a folder returns the saved files that went`(@TempDir root: File) {
        val a = preset(File(root, "garden"), "a")
        assertEquals(listOf(a), LibraryFolders.deleteFolder(root, "garden").getOrThrow())
        assertFalse(File(root, "garden").exists())
        assertTrue(LibraryFolders.deleteFolder(root, "").isFailure, "the section itself is never deleted")
    }

    @Test
    fun `a saved file's section follows its extension`() {
        assertEquals(BrowseSection.SINGLE, LibraryFolders.sectionOf(BrowseKind.FX, File("x.lsdfx")))
        assertEquals(BrowseSection.CHAIN, LibraryFolders.sectionOf(BrowseKind.FX, File("x.lsdfxchain")))
        assertEquals(BrowseSection.SAVED, LibraryFolders.sectionOf(BrowseKind.TRANS, File("x.lsdtrans")))
        assertNull(LibraryFolders.sectionOf(BrowseKind.SRC, File("x.lsdfx")))
    }

    @Test
    fun `the catalog tree shows empty folders`() {
        val entries = listOf(BrowseEntry(AssetItem("/lib/a.lsd", "a", AssetType.PRESET), BrowseSection.SAVED, "/lib/a.lsd", "garden"))
        val c = BrowseCatalog(BrowseKind.SRC, entries, folders = mapOf(BrowseSection.SAVED to listOf("garden", "empty", "empty/inner")))
        val saved = c.tree().map { "${" ".repeat(it.depth)}${it.label}(${it.count})" }
        assertEquals(
            listOf("All(1)", "Stock sources(0)", "Saved presets(1)", " empty(0)", "  inner(0)", " garden(1)", "External video(0)", "Tags(0)", "Playlists(0)"),
            saved
        )
    }

    @Test
    fun `impact names the playlists and queues`() {
        assertEquals("", LibraryRefs.Impact(emptyList(), emptyList()).describe())
        assertEquals("used in the playlist 'set'", LibraryRefs.Impact(listOf("set"), emptyList()).describe())
        assertEquals("used in 2 playlists and the A/B Queue", LibraryRefs.Impact(listOf("a", "b"), listOf("A/B Queue")).describe())
        assertEquals(
            "used in the playlist 'a', the A/B Queue and the BG Queue",
            LibraryRefs.Impact(listOf("a"), listOf("A/B Queue", "BG Queue")).describe()
        )
    }

    @Test
    fun `playlists nest under their folders, folders first`() {
        val c = BrowseCatalog(
            BrowseKind.SRC, emptyList(),
            playlists = listOf(
                BrowsePlaylist("loose", "/pl/loose.lsdplay", listOf("a", "b")),
                BrowsePlaylist("friday", "/pl/gigs/friday.lsdplay", listOf("a"), folder = "gigs"),
                BrowsePlaylist("old", "/pl/gigs/2025/old.lsdplay", listOf("a", "b", "c"), folder = "gigs/2025")
            ),
            playlistFolders = listOf("gigs", "gigs/2025", "empty")
        )
        val labels = c.tree().map { "${" ".repeat(it.depth)}${it.label}(${it.count})" }
        assertEquals(
            listOf("Playlists(6)", " empty(0)", " gigs(4)", "  2025(3)", "   old(3)", "  friday(1)", " loose(2)"),
            labels.dropWhile { !it.startsWith("Playlists") }
        )
        assertFalse(c.tree().first { it.scope is BrowseScope.PlaylistFolder }.selectable)
    }

    @Test
    fun `missing playlist items list their positions and same-named candidates`() {
        val a = BrowseEntry(AssetItem("/lib/presets/yard/a.lsd", "a", AssetType.PRESET), BrowseSection.SAVED, "/lib/presets/yard/a.lsd", "yard")
        val c = BrowseCatalog(
            BrowseKind.SRC, listOf(a),
            playlists = listOf(BrowsePlaylist("set", "/pl/set.lsdplay", listOf("yard/a.lsd", "/lib/presets/garden/a.lsd", "gone/zz.lsd")))
        )
        val scope = BrowseScope.Playlist("/pl/set.lsdplay")
        assertEquals(listOf(1 to "/lib/presets/garden/a.lsd", 2 to "gone/zz.lsd"), c.missingItems(scope))
        assertEquals(listOf(a), c.candidatesFor("/lib/presets/garden/a.lsd"))
        assertTrue(c.candidatesFor("gone/zz.lsd").isEmpty())
    }

    @Test
    fun `playlist files sort into the playlists section`() {
        assertEquals(BrowseSection.PLAYLISTS, LibraryFolders.sectionOf(BrowseKind.SRC, File("x.lsdplay")))
        assertEquals(BrowseSection.PLAYLISTS, LibraryFolders.sectionOf(BrowseKind.FX, File("x.lsdfxplay")))
        assertTrue(LibraryRefs.isLibraryFile(File("x.lsdtransplay")))
    }
}
