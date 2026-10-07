package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import java.io.File
import kotlin.test.*

/** The pair focus view's state: the pair table, exclusivity with a Params Edit module, and the Esc unwind. */
class PairFocusTest {
    private val preferencesFile = File("lsd-preferences.properties")
    private val backupFile = File("lsd-preferences.properties.pairfocustest.bak")
    private var hadBackup = false

    @BeforeTest
    fun setUp() {
        hadBackup = preferencesFile.exists()
        if (hadBackup) preferencesFile.copyTo(backupFile, overwrite = true)
        UITheme.rackExpandedModules = emptyMap()
    }

    @AfterTest
    fun tearDown() {
        if (hadBackup) { backupFile.copyTo(preferencesFile, overwrite = true); backupFile.delete() } else preferencesFile.delete()
    }

    @Test
    fun pairTableCoversEveryDeckMasterAndXf() {
        assertEquals(listOf("A", "B", "BG", "PV", "MASTER", "XF"), PerfRows.PAIRS.map { it.tag })
        assertEquals(listOf("deck.A.src", "deck.A.fx"), PerfRows.pairFor("A")!!.rowIds)
        assertEquals(listOf("master.mix", "master.fx"), PerfRows.pairFor("MASTER")!!.rowIds)
        assertEquals(listOf("trans", "global"), PerfRows.pairFor("XF")!!.rowIds)
        assertTrue(PerfRows.PAIRS.all { p -> p.rowIds.all { it in PerfRows.CATALOG } })
        assertEquals("A", PerfRows.pairForBank(MacroEngine.DECK_A_FX)?.tag)
        assertEquals("XF", PerfRows.pairForBank(MacroEngine.GLOBAL)?.tag)
    }

    @Test
    fun focusPairAndParamsEditAreMutuallyExclusive() {
        val state = ParametersState()
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        state.focusPair("B")
        assertEquals("B", state.focusedPair)
        assertFalse(state.anyRackModuleExpanded())

        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertNull(state.focusedPair)
        assertTrue(state.anyRackModuleExpanded())
    }

    @Test
    fun unknownPairIsIgnored() {
        val state = ParametersState()
        state.focusPair("nope")
        assertNull(state.focusedPair)
    }

    @Test
    fun backLeavesThePairInOnePress() {
        val state = ParametersState()
        state.focusPair("MASTER")
        assertTrue(BackNavigation.back(state, null))
        assertNull(state.focusedPair)
        assertFalse(BackNavigation.back(state, null))
    }

    @Test
    fun rowsCacheFollowsPairSwitches() {
        val state = ParametersState()
        val ctx = PerformanceUiContext()
        val pages = PerfPageStore.default.all()
        val page = pages.first()
        val cache = PerfRows.RowsCache()
        val pagerows = cache.rows(page, ctx, state, { it }, pages)

        state.focusPair("A")
        val a = cache.rows(page, ctx, state, { it }, pages)
        assertEquals(listOf(MacroEngine.DECK_A, MacroEngine.DECK_A_FX), a.map { it.bankId })
        assertSame(a, cache.rows(page, ctx, state, { it }, pages))

        state.focusPair("XF")
        assertEquals(listOf(MacroEngine.TRANS, MacroEngine.GLOBAL), cache.rows(page, ctx, state, { it }, pages).map { it.bankId })

        state.leavePair()
        assertEquals(pagerows.map { it.bankId }, cache.rows(page, ctx, state, { it }, pages).map { it.bankId })
    }
}
