package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine
import java.io.File
import kotlin.test.*

/** Edit shows the open module's pair of rows (like Browse); the bay's tab picks the active half, not the rows. */
class EditPairTest {
    private val preferencesFile = File("lsd-preferences.properties")
    private val backupFile = File("lsd-preferences.properties.editpairtest.bak")
    private var hadBackup = false
    private val ctx = PerformanceUiContext()
    private val pages = PerfPageStore.default.all()
    private val page = pages.first()

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

    private fun banks(state: ParametersState) = PerfRows.visibleRowsForPage(page, ctx, state, { it }, pages).map { it.bankId }

    @Test
    fun aDeckOpensItsSrcAndFxRowsWhicheverTabIsActive() {
        val state = ParametersState()
        state.openParamsFromRow(MacroEngine.DECK_B_FX)
        assertEquals(listOf(MacroEngine.DECK_B, MacroEngine.DECK_B_FX), banks(state))
        state.setDeckSubTab("Deck B", "SRC")
        assertEquals(listOf(MacroEngine.DECK_B, MacroEngine.DECK_B_FX), banks(state))
    }

    @Test
    fun masterPairsMixWithFxOnCtrlAndTransitionsWithFxOtherwise() {
        val state = ParametersState()
        state.openParamsFromRow(MacroEngine.MASTER)
        assertEquals(listOf(MacroEngine.MASTER, MacroEngine.MASTER_FX), banks(state))
        state.activeMixerSubTab = "TRANS"
        assertEquals(listOf(MacroEngine.TRANS, MacroEngine.MASTER_FX), banks(state))
        state.activeMixerSubTab = "FX"
        assertEquals(listOf(MacroEngine.TRANS, MacroEngine.MASTER_FX), banks(state))
    }

    @Test
    fun theGearOfTheOtherRowMovesTheBayInsteadOfClosingIt() {
        val state = ParametersState()
        state.openParamsFromRow(MacroEngine.DECK_A)
        assertTrue(state.isRowEditActive(MacroEngine.DECK_A))
        assertFalse(state.isRowEditActive(MacroEngine.DECK_A_FX))

        state.toggleRowEdit(MacroEngine.DECK_A_FX)
        assertEquals("FX", state.activeDeckASubTab)
        assertTrue(state.isRowEditActive(MacroEngine.DECK_A_FX))
        assertFalse(state.isRowEditActive(MacroEngine.DECK_A))
        assertTrue(state.anyRackModuleExpanded())

        state.toggleRowEdit(MacroEngine.DECK_A_FX)
        assertFalse(state.anyRackModuleExpanded())
    }

    @Test
    fun masterRowsMapToTheirOwnMixerSubTab() {
        val state = ParametersState()
        state.openParamsFromRow(MacroEngine.TRANS)
        assertTrue(state.isRowEditActive(MacroEngine.TRANS))
        assertFalse(state.isRowEditActive(MacroEngine.MASTER_FX))
        state.toggleRowEdit(MacroEngine.MASTER_FX)
        assertEquals("FX", state.activeMixerSubTab)
        assertTrue(state.isRowEditActive(MacroEngine.MASTER_FX))
    }
}
