package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BrowseCatalogTest {
    private fun stock(id: String, name: String = id, folder: String = "", cats: List<String> = emptyList()) =
        BrowseEntry(AssetItem("stock://$id", name, AssetType.FX_STOCK), BrowseSection.STOCK, id, folder, cats)

    private fun saved(path: String, section: BrowseSection = BrowseSection.SAVED, folder: String = "", tags: List<String> = emptyList()): BrowseEntry {
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        return BrowseEntry(AssetItem(path, name, AssetType.PRESET, tags = tags), section, path, folder)
    }

    private fun labels(c: BrowseCatalog) = c.tree().map { "${" ".repeat(it.depth)}${it.label}(${it.count})" }

    @Test
    fun `tree has All, Favorites only with a store, sections and playlists`() {
        val entries = listOf(stock("blur"), saved("/lib/presets/a.lsd"))
        val withFavs = BrowseCatalog(BrowseKind.SRC, entries, favorites = emptySet())
        assertEquals(
            listOf("All(2)", "Favorites(0)", "Stock sources(1)", "Saved presets(1)", "External video(0)", "Playlists(0)"),
            labels(withFavs)
        )
        val noFavs = BrowseCatalog(BrowseKind.SRC, entries)
        assertEquals(listOf("All(2)", "Stock sources(1)", "Saved presets(1)", "External video(0)", "Playlists(0)"), labels(noFavs))
    }

    @Test
    fun `FX tree splits saved into single and chains`() {
        val c = BrowseCatalog(BrowseKind.FX, listOf(stock("blur"), saved("/x/a.lsdfx", BrowseSection.SINGLE), saved("/x/b.lsdfxchain", BrowseSection.CHAIN)))
        assertEquals(listOf("All(3)", "Stock filters(1)", "Saved single FX(1)", "Saved chains(1)", "Playlists(0)"), labels(c))
    }

    @Test
    fun `tree counts follow a hosting target's filter`() {
        val c = BrowseCatalog(
            BrowseKind.FX,
            listOf(stock("blur"), saved("/x/a.lsdfx", BrowseSection.SINGLE), saved("/x/b.lsdfxchain", BrowseSection.CHAIN)),
            listOf(BrowsePlaylist("mix", "/pl/mix.lsdfxplay", listOf("blur", "/x/b.lsdfxchain")))
        )
        // A chain-only target: stock and single rows vanish from the counts, the playlist counts its chain item only.
        val chainsOnly = c.tree { it.section == BrowseSection.CHAIN }.map { "${it.label}(${it.count})" }
        assertEquals(listOf("All(1)", "Stock filters(0)", "Saved single FX(0)", "Saved chains(1)", "Playlists(1)", "mix(1)"), chainsOnly)
    }

    @Test
    fun `subfolders nest, count cumulatively and filter by prefix`() {
        val c = BrowseCatalog(
            BrowseKind.SRC,
            listOf(
                saved("/p/top.lsd"),
                saved("/p/rave/a.lsd", folder = "rave"),
                saved("/p/rave/deep/b.lsd", folder = "rave/deep"),
                saved("/p/chill/c.lsd", folder = "chill")
            )
        )
        assertEquals(
            listOf("All(4)", "Stock sources(0)", "Saved presets(4)", " chill(1)", " rave(2)", "  deep(1)", "External video(0)", "Playlists(0)"),
            labels(c)
        )
        val rave = c.rows(BrowseScope.Folder(BrowseSection.SAVED, "rave")).map { it.asset.name }
        assertEquals(listOf("a", "b"), rave)
        assertEquals(listOf("b"), c.rows(BrowseScope.Folder(BrowseSection.SAVED, "rave/deep")).map { it.asset.name })
        // "rave" must not match a sibling folder that merely starts with the same letters.
        val c2 = BrowseCatalog(BrowseKind.SRC, listOf(saved("/p/raven/x.lsd", folder = "raven")))
        assertTrue(c2.rows(BrowseScope.Folder(BrowseSection.SAVED, "rave")).isEmpty())
    }

    @Test
    fun `favorites scope returns only starred keys`() {
        val c = BrowseCatalog(BrowseKind.FX, listOf(stock("blur"), stock("glow"), saved("/x/a.lsdfx", BrowseSection.SINGLE)), favorites = setOf("glow"))
        assertEquals(listOf("glow"), c.rows(BrowseScope.Favorites).map { it.key })
        assertEquals(1, c.tree().first { it.scope == BrowseScope.Favorites }.count)
    }

    @Test
    fun `search uses the shared token rule across name, id, folder, categories and tags`() {
        val c = BrowseCatalog(
            BrowseKind.FX,
            listOf(
                stock("fx.blur.gauss", name = "Gaussian", folder = "blurs", cats = listOf("Soften")),
                saved("/x/neon.lsdfx", BrowseSection.SINGLE, tags = listOf("glow"))
            )
        )
        assertEquals(1, c.rows(BrowseScope.All, "gauss").size)      // id
        assertEquals(1, c.rows(BrowseScope.All, "blurs soft").size) // folder + category, both tokens must hit
        assertEquals(1, c.rows(BrowseScope.All, "GLOW").size)       // tag, case-insensitive
        assertTrue(c.rows(BrowseScope.All, "gauss glow").isEmpty()) // every token must match one item
        assertEquals(2, c.rows(BrowseScope.All, "  ").size)
    }

    @Test
    fun `playlist scope keeps playlist order and resolves bare, relative and absolute tokens`() {
        val entries = listOf(
            saved("/lib/presets/one.lsd"),
            saved("/lib/presets/sub/two.lsd", folder = "sub"),
            saved("/lib/presets/three.lsd")
        )
        val pl = BrowsePlaylist("rave", "/lib/playlists/rave.lsdplay", listOf("three", "sub/two.lsd", "/lib/presets/one.lsd", "gone"))
        val c = BrowseCatalog(BrowseKind.SRC, entries, listOf(pl))
        val scope = BrowseScope.Playlist(pl.path)
        assertEquals(listOf("three", "two", "one"), c.rows(scope).map { it.asset.name })
        assertEquals(1, c.missing(scope))
        assertEquals(listOf("three", "two", "one", null), c.playlistSlots(scope).map { it?.asset?.name })
        assertTrue(c.playlistSlots(BrowseScope.All).isEmpty())
        assertEquals(0, c.missing(BrowseScope.All))
        assertTrue(c.isReorderable(scope))
        assertFalse(c.isReorderable(BrowseScope.All))
        assertEquals(listOf("two"), c.rows(scope, "two").map { it.asset.name })
        // Playlist rows appear as children of the Playlists header.
        assertEquals(listOf("Playlists(1)", " rave(4)"), labels(c).takeLast(2))
    }

    @Test
    fun `transition playlists resolve stock ids`() {
        val t = BrowseEntry(AssetItem("stock://wipe", "Wipe", AssetType.TRANSITION_STOCK), BrowseSection.STOCK, "wipe")
        val pl = BrowsePlaylist("mix", "/p/mix.lsdtransplay", listOf("wipe"))
        val c = BrowseCatalog(BrowseKind.TRANS, listOf(t), listOf(pl))
        assertEquals(listOf("wipe"), c.rows(BrowseScope.Playlist(pl.path)).map { it.key })
    }

    @Test
    fun `stock rows are read-only for source and FX playlists but not for transitions`() {
        val s = stock("blur")
        assertFalse(BrowseCatalog(BrowseKind.SRC, listOf(s)).canAddToPlaylist(s))
        assertFalse(BrowseCatalog(BrowseKind.FX, listOf(s)).canAddToPlaylist(s))
        assertTrue(BrowseCatalog(BrowseKind.TRANS, listOf(s)).canAddToPlaylist(s))
        val saved = saved("/p/a.lsd")
        assertTrue(BrowseCatalog(BrowseKind.SRC, listOf(saved)).canAddToPlaylist(saved))
    }

    @Test
    fun `PlaylistsHeader is not selectable and has no rows`() {
        val c = BrowseCatalog(BrowseKind.SRC, listOf(saved("/p/a.lsd")))
        val header = c.tree().first { it.scope == BrowseScope.PlaylistsHeader }
        assertFalse(header.selectable)
        assertTrue(c.rows(BrowseScope.PlaylistsHeader).isEmpty())
    }

    @Test
    fun `folderOf derives the relative folder below a root`() {
        val root = File("/lib/presets")
        assertEquals("", BrowseCatalog.folderOf(File("/lib/presets/a.lsd"), root))
        assertEquals("rave", BrowseCatalog.folderOf(File("/lib/presets/rave/a.lsd"), root))
        assertEquals("rave/deep", BrowseCatalog.folderOf(File("/lib/presets/rave/deep/a.lsd"), root))
        assertEquals("", BrowseCatalog.folderOf(File("/elsewhere/a.lsd"), root))
    }

    private fun cursorFixture(): Pair<List<BrowseNode>, BrowseCatalog> {
        val c = BrowseCatalog(
            BrowseKind.SRC,
            listOf(stock("blur"), saved("/lib/a/x.lsd", folder = "a"), saved("/lib/a/b/y.lsd", folder = "a/b")),
            listOf(BrowsePlaylist("set", "/p/set.lsdplaylist", emptyList()))
        )
        return c.tree() to c
    }

    @Test
    fun `visible selectable scopes skip the playlists header and anything under a collapsed folder`() {
        val (tree, _) = cursorFixture()
        val saved = BrowseScope.Folder(BrowseSection.SAVED)
        val all = visibleSelectableScopes(tree, emptySet())
        assertEquals(
            listOf(
                BrowseScope.All, BrowseScope.Folder(BrowseSection.STOCK), saved, BrowseScope.Folder(BrowseSection.SAVED, "a"),
                BrowseScope.Folder(BrowseSection.SAVED, "a/b"), BrowseScope.Folder(BrowseSection.LIVE), BrowseScope.Playlist("/p/set.lsdplaylist")
            ),
            all
        )
        assertEquals(
            listOf(BrowseScope.All, BrowseScope.Folder(BrowseSection.STOCK), saved, BrowseScope.Folder(BrowseSection.LIVE), BrowseScope.Playlist("/p/set.lsdplaylist")),
            visibleSelectableScopes(tree, setOf(saved))
        )
    }

    @Test
    fun `tree cursor steps clamp at the ends and restart when the current row is gone`() {
        val (tree, _) = cursorFixture()
        val visible = visibleSelectableScopes(tree, emptySet())
        assertEquals(visible[1], stepTreeCursor(visible, visible[0], 1))
        assertEquals(visible.first(), stepTreeCursor(visible, visible.first(), -1))
        assertEquals(visible.last(), stepTreeCursor(visible, visible.last(), 1))
        assertEquals(visible.first(), stepTreeCursor(visible, null, 1))
        assertEquals(visible.last(), stepTreeCursor(visible, BrowseScope.Playlist("/gone"), -1))
        assertEquals(null, stepTreeCursor(emptyList(), null, 1))
    }

    @Test
    fun `live external rows list under their section but never go into playlists or favorites`() {
        val live = BrowseEntry(AssetItem("ext-video://OBS", "OBS", AssetType.SOURCE_EXTERNAL), BrowseSection.LIVE, "ext-video://OBS")
        val c = BrowseCatalog(BrowseKind.SRC, listOf(stock("blur"), live), favorites = emptySet())
        assertEquals(listOf("OBS"), c.rows(BrowseScope.Folder(BrowseSection.LIVE)).map { it.asset.name })
        assertEquals(1, c.tree().first { it.scope == BrowseScope.Folder(BrowseSection.LIVE) }.count)
        assertFalse(c.canAddToPlaylist(live))
    }
}
