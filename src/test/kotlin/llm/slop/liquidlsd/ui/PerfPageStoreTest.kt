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
    fun builtInPagesAreAllValidAndInTwisterBankOrder() {
        val pages = store().all()
        assertEquals(listOf("ab", "bgpv", "mixer", "master"), pages.take(4).map { it.id })
        assertEquals(listOf("master.mix", "trans", "wetdry", "global"), pages[3].rows.map { it.row })
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
        File(dir, "a.json").writeText("""{"id":"ab","name":"MY AB","rows":[{"row":"master.mix"},{"row":"trans"},{"row":"wetdry"},{"row":"global"}]}""")
        File(dir, "b.json").writeText("""{"id":"extra","name":"EXTRA","rows":[{"row":"deck.A.src"},{"row":"deck.A.fx"},{"row":"deck.B.src"},{"row":"global"}]}""")
        val pages = store(dir).all()
        assertEquals(listOf("ab", "bgpv", "mixer", "master", "extra"), pages.map { it.id })
        assertEquals("MY AB", pages[0].name)
    }

    @Test
    fun invalidUserPagesAreSkipped() {
        val dir = createTempDirectory().toFile()
        File(dir, "short.json").writeText("""{"id":"short","name":"S","rows":[{"row":"master.mix"}]}""")
        File(dir, "unknown.json").writeText("""{"id":"unk","name":"U","rows":[{"row":"x"},{"row":"master.mix"},{"row":"trans"},{"row":"global"}]}""")
        // Pages saved before the SRC/FX toggle rows were removed name rows that no longer exist: skipped.
        File(dir, "old.json").writeText("""{"id":"old","name":"O","rows":[{"row":"deck.A.srcfx"},{"row":"master"},{"row":"trans"},{"row":"global"}]}""")
        File(dir, "garbage.json").writeText("not json")
        val s = store(dir)
        assertEquals(listOf("ab", "bgpv", "mixer", "master"), s.all().map { it.id })
        assertNull(s.get("short"))
        assertNull(s.get("old"))
    }

    @Test
    fun focusLookupScansActivePageThenFollowingPagesWrapping() {
        val pages = store().all()
        assertEquals(PerfRows.CATALOG["deck.A.src"], PerfRows.catalogRowForModule("deckA", "SRC", pages, "ab"))
        // From MIXER the scan wraps past MASTER to A/B and finds the pinned SRC row.
        assertEquals(PerfRows.CATALOG["deck.A.src"], PerfRows.catalogRowForModule("deckA", "SRC", pages, "mixer"))
        // Deck BG from the last page wraps all the way round to BG/PV.
        assertEquals(PerfRows.CATALOG["deck.BG.src"], PerfRows.catalogRowForModule("deckBG", "SRC", pages.filter { it.id in setOf("mixer", "bgpv") }, "mixer"))
        assertEquals(PerfRows.CATALOG["master.mix"], PerfRows.catalogRowForModule("Mixer", "MIX", pages, "ab"))
    }

    @Test
    fun focusLookupMatchesTheTargetHalfAndSkipsOtherPinnedRows() {
        fun page(id: String, vararg rows: String) = PerfPageDef(id, id, rows = rows.map(::RowPlacement))
        val pages = listOf(
            page("p1", "deck.A.fx", "deck.A.src", "master.fx", "master.mix"),
            page("p2", "deck.A.fx", "trans", "wetdry", "global"),
        )
        assertEquals(PerfRows.CATALOG["deck.A.src"], PerfRows.catalogRowForModule("deckA", "SRC", pages, "p1"))
        assertEquals(PerfRows.CATALOG["deck.A.fx"], PerfRows.catalogRowForModule(MacroEngine.DECK_A_FX, "FX", pages, "p1"))
        assertEquals(PerfRows.CATALOG["master.mix"], PerfRows.catalogRowForModule("Mixer", "MIX", pages, "p1"))
        // Active page p2 first: its FX row wins for the FX half; the SRC half is found on the next page.
        assertEquals(PerfRows.CATALOG["deck.A.fx"], PerfRows.catalogRowForModule("deckA", "FX", pages, "p2"))
        assertEquals(PerfRows.CATALOG["deck.A.src"], PerfRows.catalogRowForModule("deckA", "SRC", pages, "p2"))
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
        assertEquals(listOf("ab", "bgpv", "mixer", "master"), store.all().map { it.id }.take(4))
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

    @Test
    fun tabsKeepFullWidthUpToFiveThenShrinkWithinTheSameStrip() {
        assertEquals(68f, PerfTabStrip.tabWidth(1))
        assertEquals(68f, PerfTabStrip.tabWidth(5))
        val strip = { n: Int -> n * PerfTabStrip.tabWidth(n) + (n - 1) * PerfTabStrip.GAP }
        assertTrue(PerfTabStrip.tabWidth(7) < 68f)
        assertTrue(strip(7) <= strip(5) + 0.01f)
        assertTrue(PerfTabStrip.tabWidth(40) >= 36f)
    }

    @Test
    fun hidingPagesKeepsOneTabAndMovesTheActivePage() {
        val savedHidden = UITheme.hiddenPerformPages
        val savedActive = UITheme.performancePageId
        try {
            UITheme.hiddenPerformPages = emptySet()
            UITheme.performancePageId = "ab"
            val all = PerfPageStore.default.all().map { it.id }

            assertTrue(UITheme.setPerformPageHidden("ab", true))
            assertTrue("ab" !in UITheme.visiblePerformPages().map { it.id })
            assertEquals(all.first { it != "ab" }, UITheme.performancePageId)

            for (id in all.filter { it != "ab" }.dropLast(1)) assertTrue(UITheme.setPerformPageHidden(id, true))
            assertEquals(1, UITheme.visiblePerformPages().size)
            assertTrue(!UITheme.setPerformPageHidden(UITheme.visiblePerformPages().single().id, true))

            assertTrue(UITheme.setPerformPageHidden("ab", false))
            assertTrue("ab" in UITheme.visiblePerformPages().map { it.id })
        } finally {
            UITheme.hiddenPerformPages = savedHidden
            UITheme.performancePageId = savedActive
        }
    }

    @Test
    fun aHiddenPageThatBecomesActiveGetsATemporaryTab() {
        val savedHidden = UITheme.hiddenPerformPages
        val savedActive = UITheme.performancePageId
        try {
            UITheme.hiddenPerformPages = emptySet()
            UITheme.performancePageId = "ab"
            assertTrue(UITheme.setPerformPageHidden("bgpv", true))
            assertTrue("bgpv" !in UITheme.visiblePerformPages().map { it.id })

            UITheme.performancePageId = "bgpv" // a controller bank selects the hidden page
            assertTrue("bgpv" in UITheme.visiblePerformPages().map { it.id })

            UITheme.performancePageId = "ab"
            assertTrue("bgpv" !in UITheme.visiblePerformPages().map { it.id })
        } finally {
            UITheme.hiddenPerformPages = savedHidden
            UITheme.performancePageId = savedActive
        }
    }

    @Test
    fun idFromNameAndIndependentCopy() {
        assertEquals("my-page-2", PerfPageDef.idFromName("  My Page #2! "))
        assertEquals("", PerfPageDef.idFromName("!!!"))
        val store = store()
        val ab = store.get("ab")!!
        assertEquals(emptyList(), store.saveUser(ab.copy(id = "my-ab", name = "My AB")))
        assertEquals(PerfPageStore.Source.USER, store.sourceOf("my-ab"))
        assertEquals(PerfPageStore.Source.BUILT_IN, store.sourceOf("ab"))
        assertEquals(ab.rows, store.get("my-ab")!!.rows)
    }

    private val goodPage = """{"id":"%s","name":"N","rows":[{"row":"master.mix"},{"row":"trans"},{"row":"wetdry"},{"row":"global"}]}"""

    @Test
    fun unreadableUserFileIsRejectedWithoutThrowing() {
        val dir = createTempDirectory().toFile()
        File(dir, "bad.json").mkdir()
        val s = store(dir)
        assertEquals(listOf("ab", "bgpv", "mixer", "master"), s.all().map { it.id })
        assertEquals(listOf("bad.json"), s.rejected().map { it.file.name })
    }

    @Test
    fun duplicateIdsFirstFileByNameWinsAndLaterIsRejected() {
        val dir = createTempDirectory().toFile()
        File(dir, "b.json").writeText(goodPage.format("dup").replace("\"N\"", "\"SECOND\""))
        File(dir, "a.json").writeText(goodPage.format("dup").replace("\"N\"", "\"FIRST\""))
        val s = store(dir)
        assertEquals("FIRST", s.get("dup")!!.name)
        val rej = s.rejected().single()
        assertEquals("b.json", rej.file.name)
        assertTrue(rej.problems.single().contains("duplicate id"))
    }

    @Test
    fun saveReplacesContentAtomicallyAndLeavesNoTempFile() {
        val dir = createTempDirectory().toFile()
        val s = store(dir)
        assertEquals(emptyList(), s.saveUser(PerfPageDef("mine", "First", rows = rows)))
        assertEquals(emptyList(), s.saveUser(PerfPageDef("mine", "Second", rows = rows)))
        assertEquals("Second", s.get("mine")!!.name)
        assertEquals(listOf("mine.json"), dir.list()!!.toList())
    }

    @Test
    fun saveUserRejectsUnsafeIds() {
        val dir = createTempDirectory().toFile()
        val s = store(dir)
        for (id in listOf("../evil", "a/b", "", "Up", "-x")) assertTrue(s.saveUser(PerfPageDef(id, "X", rows = rows)).isNotEmpty(), id)
        assertEquals(0, dir.list()!!.size)
    }
}
