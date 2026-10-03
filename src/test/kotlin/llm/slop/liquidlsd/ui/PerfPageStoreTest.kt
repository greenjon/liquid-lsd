package llm.slop.liquidlsd.ui

import java.io.File
import llm.slop.liquidlsd.macro.MacroEngine
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerfPageStoreTest {
    private fun store(dir: File = createTempDirectory().toFile()) = PerfPageStore(dir)

    @Test
    fun builtInPagesReproduceDecksAndMaster() {
        val pages = store().all()
        assertEquals(listOf("decks", "master", "ab", "bgpv", "mixer"), pages.take(5).map { it.id })
        assertEquals(listOf("deck.A.srcfx", "deck.B.srcfx", "deck.BG.srcfx", "deck.PV.srcfx"), pages[0].rows.map { it.row })
        assertEquals(listOf("master", "trans", "wetdry", "global"), pages[1].rows.map { it.row })
        assertTrue(pages.all { it.problems().isEmpty() })
    }

    @Test
    fun defaultTwisterPagesPinEachDeckHalf() {
        val pages = store().all().associateBy { it.id }
        assertEquals(listOf("deck.A.src", "deck.A.fx", "deck.B.src", "deck.B.fx"), pages.getValue("ab").rows.map { it.row })
        assertEquals(listOf("deck.BG.src", "deck.BG.fx", "deck.PV.src", "deck.PV.fx"), pages.getValue("bgpv").rows.map { it.row })
        assertEquals(listOf("master.mix", "master.fx", "trans", "wetdry"), pages.getValue("mixer").rows.map { it.row })
    }

    @Test
    fun userPageOverridesBuiltInInPlaceAndNewPagesFollow() {
        val dir = createTempDirectory().toFile()
        File(dir, "a.json").writeText("""{"id":"decks","name":"MY DECKS","rows":[{"row":"master"},{"row":"trans"},{"row":"wetdry"},{"row":"global"}]}""")
        File(dir, "b.json").writeText("""{"id":"extra","name":"EXTRA","rows":[{"row":"deck.A.srcfx"},{"row":"deck.A.srcfx"},{"row":"deck.B.srcfx"},{"row":"global"}]}""")
        val pages = store(dir).all()
        assertEquals(listOf("decks", "master", "ab", "bgpv", "mixer", "extra"), pages.map { it.id })
        assertEquals("MY DECKS", pages[0].name)
    }

    @Test
    fun invalidUserPagesAreSkipped() {
        val dir = createTempDirectory().toFile()
        File(dir, "short.json").writeText("""{"id":"short","name":"S","rows":[{"row":"master"}]}""")
        File(dir, "unknown.json").writeText("""{"id":"unk","name":"U","rows":[{"row":"x"},{"row":"master"},{"row":"trans"},{"row":"global"}]}""")
        File(dir, "garbage.json").writeText("not json")
        val s = store(dir)
        assertEquals(listOf("decks", "master", "ab", "bgpv", "mixer"), s.all().map { it.id })
        assertNull(s.get("short"))
    }

    @Test
    fun focusLookupScansActivePageThenFollowingPagesWrapping() {
        val pages = store().all()
        // From DECKS the toggle row wins; from MASTER the scan continues with A/B and finds the pinned SRC row.
        assertEquals(PerfRows.CATALOG["deck.A.srcfx"], PerfRows.catalogRowForModule("deckA", "SRC", pages, "decks"))
        assertEquals(PerfRows.CATALOG["deck.A.src"], PerfRows.catalogRowForModule("deckA", "SRC", pages, "master"))
        // Deck BG from the last page wraps all the way round to DECKS.
        assertEquals(PerfRows.CATALOG["deck.BG.srcfx"], PerfRows.catalogRowForModule("deckBG", "SRC", pages.filter { it.id in setOf("mixer", "decks") }, "mixer"))
        assertNull(PerfRows.catalogRowForModule("Mixer", "MIX", pages, "decks")?.takeIf { it.pinnedMode != null })
    }

    @Test
    fun focusLookupMatchesTheTargetHalfAndSkipsOtherPinnedRows() {
        fun page(id: String, vararg rows: String) = PerfPageDef(id, id, rows = rows.map(::RowPlacement))
        val pages = listOf(
            page("p1", "deck.A.fx", "deck.A.src", "master.fx", "master.mix"),
            page("p2", "deck.A.srcfx", "trans", "wetdry", "global"),
        )
        assertEquals(PerfRows.CATALOG["deck.A.src"], PerfRows.catalogRowForModule("deckA", "SRC", pages, "p1"))
        assertEquals(PerfRows.CATALOG["deck.A.fx"], PerfRows.catalogRowForModule(MacroEngine.DECK_A_FX, "FX", pages, "p1"))
        assertEquals(PerfRows.CATALOG["master.mix"], PerfRows.catalogRowForModule("Mixer", "MIX", pages, "p1"))
        // Active page p2 first: its toggle row covers either half.
        assertEquals(PerfRows.CATALOG["deck.A.srcfx"], PerfRows.catalogRowForModule("deckA", "FX", pages, "p2"))
        // Nothing places Deck B anywhere.
        assertNull(PerfRows.catalogRowForModule("deckB", "SRC", pages, "p1"))
    }

    @Test
    fun pagesMayUsePinnedRows() {
        val page = PerfPageDef("x", "X", rows = listOf("deck.A.src", "deck.A.fx", "master.mix", "master.fx").map(::RowPlacement))
        assertTrue(page.problems().isEmpty())
    }

    private val rows = listOf("deck.A.src", "deck.A.fx", "master.mix", "master.fx").map(::RowPlacement)

    @Test
    fun copyBuiltInThenDeleteReverts() {
        val dir = createTempDirectory().toFile()
        val store = store(dir)
        assertEquals(PerfPageStore.Source.BUILT_IN, store.sourceOf("ab"))
        assertNull(store.copyBuiltInToUser("ab"))
        assertTrue(File(dir, "ab.json").exists())
        assertEquals(PerfPageStore.Source.USER_OVERRIDE, store.sourceOf("ab"))
        assertEquals(listOf("decks", "master", "ab", "bgpv", "mixer"), store.all().map { it.id }.take(5))
        assertTrue(store.copyBuiltInToUser("ab") != null)
        assertTrue(store.copyBuiltInToUser("nope") != null)
        assertTrue(store.deleteUser("ab"))
        assertEquals(PerfPageStore.Source.BUILT_IN, store.sourceOf("ab"))
        assertTrue(!store.deleteUser("ab"))
    }

    @Test
    fun saveUserValidatesAndAddsNewPage() {
        val dir = createTempDirectory().toFile()
        val store = store(dir)
        assertEquals(emptyList(), store.saveUser(PerfPageDef("mine", "Mine", rows = rows)))
        assertEquals(PerfPageStore.Source.USER, store.sourceOf("mine"))
        assertEquals("mine", store.all().last().id)

        assertTrue(store.saveUser(PerfPageDef("mine", "Mine", rows = rows.take(3))).isNotEmpty())
        assertTrue(store.saveUser(PerfPageDef("Bad Id", "Bad", rows = rows)).isNotEmpty())
        assertTrue(!File(dir, "Bad Id.json").exists())
        assertEquals(4, store.get("mine")!!.rows.size)
    }

    @Test
    fun rejectedFilesAreReportedAfterReload() {
        val dir = createTempDirectory().toFile()
        val store = store(dir)
        File(dir, "garbage.json").writeText("{ nope")
        store.reload()
        assertEquals(listOf("garbage.json"), store.rejected().map { it.file.name })
    }
}
