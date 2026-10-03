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
        assertEquals(listOf("decks", "master"), pages.map { it.id })
        assertEquals(listOf("deck.A.srcfx", "deck.B.srcfx", "deck.BG.srcfx", "deck.PV.srcfx"), pages[0].rows.map { it.row })
        assertEquals(listOf("master", "trans", "wetdry", "global"), pages[1].rows.map { it.row })
        assertTrue(pages.all { it.problems().isEmpty() })
    }

    @Test
    fun userPageOverridesBuiltInInPlaceAndNewPagesFollow() {
        val dir = createTempDirectory().toFile()
        File(dir, "a.json").writeText("""{"id":"decks","name":"MY DECKS","rows":[{"row":"master"},{"row":"trans"},{"row":"wetdry"},{"row":"global"}]}""")
        File(dir, "b.json").writeText("""{"id":"extra","name":"EXTRA","rows":[{"row":"deck.A.srcfx"},{"row":"deck.A.srcfx"},{"row":"deck.B.srcfx"},{"row":"global"}]}""")
        val pages = store(dir).all()
        assertEquals(listOf("decks", "master", "extra"), pages.map { it.id })
        assertEquals("MY DECKS", pages[0].name)
    }

    @Test
    fun invalidUserPagesAreSkipped() {
        val dir = createTempDirectory().toFile()
        File(dir, "short.json").writeText("""{"id":"short","name":"S","rows":[{"row":"master"}]}""")
        File(dir, "unknown.json").writeText("""{"id":"unk","name":"U","rows":[{"row":"x"},{"row":"master"},{"row":"trans"},{"row":"global"}]}""")
        File(dir, "garbage.json").writeText("not json")
        val s = store(dir)
        assertEquals(listOf("decks", "master"), s.all().map { it.id })
        assertNull(s.get("short"))
    }

    @Test
    fun focusLookupScansActivePageThenFollowingPagesWrapping() {
        val pages = store().all()
        // DECKS places every deck row; from MASTER the scan wraps to DECKS and finds the same catalog row.
        val row = PerfRows.catalogRowForModule("deckA", "SRC", pages, "master")
        assertEquals(PerfRows.CATALOG["deck.A.srcfx"], row)
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
}
