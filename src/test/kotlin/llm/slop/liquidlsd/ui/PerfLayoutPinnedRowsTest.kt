package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Mode changes content, never geometry: the deck halves (`deck.<tag>.src` / `.fx`) and Master
 * `master.mix` / `master.fx` rows, the resolver's column layout across modes, plus assorted resolver edges.
 */
class PerfLayoutPinnedRowsTest {
    private val pinnedIds = PerfRows.DECK_TAGS.flatMap { listOf("deck.$it.src", "deck.$it.fx") } + listOf("master.mix", "master.fx")

    @Test
    fun everyDeckAndMasterRowShowsOneHalfAndIgnoresTheEditBayTabs() {
        val state = ParametersState().apply {
            activeDeckASubTab = "FX"; activeDeckBSubTab = "FX"; activeDeckBGSubTab = "FX"; activeDeckPVSubTab = "FX"; activeMixerSubTab = "FX"
        }
        assertEquals(pinnedIds.toSet(), PerfRows.CATALOG.filterValues { it.pinnedMode != null }.keys)
        val page = PerfPageDef("t", "T", rows = pinnedIds.take(PerfPageDef.ROWS).map(::RowPlacement))
        assertEquals(page.rows.map { PerfRows.CATALOG.getValue(it.row) }, PerfRows.rowsForPage(page))
        assertTrue(PerfRows.CATALOG.keys.none { it.endsWith(".srcfx") || it == "master" }, "toggle rows are gone")
        assertTrue(state.activeMixerSubTab == "FX")
    }

    @Test
    fun pinnedHalvesCoverTheirOwnBankAndHaveTheSameHeaderLayout() {
        for (tag in PerfRows.DECK_TAGS) {
            val src = PerfRows.CATALOG.getValue("deck.$tag.src")
            val fx = PerfRows.CATALOG.getValue("deck.$tag.fx")
            assertNotEquals(src.bankId, fx.bankId)
            assertEquals(listOf(src.knobOffset, src.hasExtraHeader, src.canExpand), listOf(fx.knobOffset, fx.hasExtraHeader, fx.canExpand))
            assertEquals("SRC", src.pinnedMode)
            assertEquals("FX", fx.pinnedMode)
        }
        val mix = PerfRows.CATALOG.getValue("master.mix")
        val mfx = PerfRows.CATALOG.getValue("master.fx")
        assertEquals(mix.hasExtraHeader, mfx.hasExtraHeader)
        assertEquals("MIX", mix.pinnedMode)
        assertEquals("FX", mfx.pinnedMode)
    }

    @Test
    fun theEditBayTabPicksTheHalfButKeepsThePlacementAttributes() {
        val ctx = PerformanceUiContext()
        val state = ParametersState()
        for ((moduleId, tag) in listOf(MacroEngine.DECK_A to "A", MacroEngine.MASTER to null)) {
            fun row() = PerfRows.rowDescriptorForModule(moduleId, ctx, state) { it }
            state.setDeckSubTab("Deck A", "SRC"); state.activeMixerSubTab = "CTRL"
            val src = row()
            if (tag != null) state.setDeckSubTab("Deck $tag", "FX") else state.activeMixerSubTab = "FX"
            val fx = row()
            assertNotEquals(src.bankId, fx.bankId)
            assertEquals(if (tag != null) "SRC" else "MIX", src.pinnedMode)
            assertEquals("FX", fx.pinnedMode)
            assertEquals(src.knobOffset, fx.knobOffset)
            assertEquals(src.hasExtraHeader, fx.hasExtraHeader)
            assertEquals(src.canExpand, fx.canExpand)
            assertTrue(src.accent.contentEquals(fx.accent))
        }
    }

    @Test
    fun resolverPlacesFourKnobsInColumnsZeroToThreeInEveryMode() {
        val bank = MacroBank()
        val params = (1..6).map { "p$it" to ModulatableParameter(baseValue = it.toFloat()) }
        val modes = listOf(
            null,
            FxRowState(null, 0, emptyList()),
            FxRowState(0, 0, emptyList()),          // focused empty slot
            FxRowState(2, 0, params),
            FxRowState(2, 1, params),
            FxRowState(2, 9, params)                // page past the end: all blank
        )
        for (mode in modes) {
            val specs = PerfKnobResolver.resolve(bank, 0, mode)
            assertEquals(listOf(0, 1, 2, 3), specs.map { it.col }, "mode=$mode")
            assertEquals(listOf(0, 1, 2, 3), specs.map { it.knobIndex }, "mode=$mode")
        }
    }

    @Test
    fun resolverOmitsColumnsPastTheBankSize() {
        val bank = MacroBank()
        val offset = bank.knobs.size - 2
        val specs = PerfKnobResolver.resolve(bank, offset, null)
        assertEquals(listOf(0, 1), specs.map { it.col })
        assertTrue(PerfKnobResolver.resolve(bank, bank.knobs.size, null).isEmpty())
    }

    @Test
    fun focusedParamCellCarriesTheParameterMeterType() {
        val p = ModulatableParameter(baseValue = 0.5f)
        val spec = PerfKnobResolver.resolve(MacroBank(), 0, FxRowState(0, 0, listOf("x" to p)))[1]
        assertEquals(p.meterType, spec.meterType)
        assertEquals(UnderKnob.ParamCell("x", p), spec.under)
    }

    // --- Copy As New Page: stable ids ---

    @Test
    fun copyingAPinnedPageGivesAStableIdThatSurvivesReload() {
        val dir = createTempDirectory().toFile()
        val store = PerfPageStore(dir)
        val ab = store.get("ab")!!
        val id = PerfPageDef.idFromName("  My AB / Mix! ")
        assertEquals("my-ab-mix", id)
        assertEquals(emptyList(), store.saveUser(ab.copy(id = id, name = "My AB / Mix!")))
        store.reload()
        assertEquals(PerfPageStore.Source.USER, store.sourceOf(id))
        assertEquals(ab.rows, store.get(id)!!.rows)
        assertEquals(PerfPageStore.Source.BUILT_IN, store.sourceOf("ab"))
        // The same name always maps to the same id, so a second copy is detected as taken.
        assertEquals(id, PerfPageDef.idFromName("my ab mix"))
        assertTrue(store.get(PerfPageDef.idFromName("my ab mix")) != null)
        // Renaming the page later does not change its id.
        store.saveUser(store.get(id)!!.copy(name = "Renamed"))
        assertEquals("Renamed", store.get(id)!!.name)
        assertEquals(1, store.all().count { it.id == id })
    }
}
