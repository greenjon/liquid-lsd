package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Mode changes content, never geometry: the pinned deck halves (`deck.<tag>.src` / `.fx`) and Master
 * `master.mix` / `master.fx` rows, the resolver's column layout across modes, plus assorted resolver edges.
 */
class PerfLayoutPinnedRowsTest {
    private val pinnedIds = PerfRows.DECK_TAGS.flatMap { listOf("deck.$it.src", "deck.$it.fx") } + listOf("master.mix", "master.fx")

    @Test
    fun pinnedRowsAreIgnoredByTheSharedDeckAndMasterToggles() {
        val state = ParametersState().apply { activeDeckASubTab = "FX"; activeMixerSubTab = "FX" }
        val ctx = PerformanceUiContext().apply {
            for (t in PerfRows.DECK_TAGS) deckRowMode[t] = "FX"
            masterRowMode = "FX"
        }
        for (id in pinnedIds) {
            val row = PerfRows.CATALOG.getValue(id)
            assertTrue(row.pinnedMode != null, id)
            assertSame(row, PerfRows.withDeckRowMode(row, ctx, state), "$id retargeted by a shared toggle")
        }
    }

    @Test
    fun pinnedHalvesCoverTheirOwnBankAndHaveTheSameHeaderLayoutAsTheToggleRow() {
        for (tag in PerfRows.DECK_TAGS) {
            val toggle = PerfRows.CATALOG.getValue("deck.$tag.srcfx")
            val src = PerfRows.CATALOG.getValue("deck.$tag.src")
            val fx = PerfRows.CATALOG.getValue("deck.$tag.fx")
            assertEquals(toggle.bankId, src.bankId)
            assertNotEquals(src.bankId, fx.bankId)
            assertEquals(listOf(toggle.knobOffset, toggle.hasExtraHeader, toggle.canExpand), listOf(src.knobOffset, src.hasExtraHeader, src.canExpand))
            assertEquals(listOf(toggle.knobOffset, toggle.hasExtraHeader, toggle.canExpand), listOf(fx.knobOffset, fx.hasExtraHeader, fx.canExpand))
            assertEquals("SRC", src.pinnedMode)
            assertEquals("FX", fx.pinnedMode)
        }
        val mix = PerfRows.CATALOG.getValue("master.mix")
        val mfx = PerfRows.CATALOG.getValue("master.fx")
        assertEquals(PerfRows.CATALOG.getValue("master").hasExtraHeader, mix.hasExtraHeader)
        assertEquals(mix.hasExtraHeader, mfx.hasExtraHeader)
        assertEquals("MIX", mix.pinnedMode)
        assertEquals("FX", mfx.pinnedMode)
    }

    @Test
    fun theToggleRowSwitchesBankButKeepsItsPlacementAttributes() {
        val ctx = PerformanceUiContext().apply { deckRowMode["A"] = "FX"; masterRowMode = "FX" }
        val state = ParametersState()
        for (id in listOf("deck.A.srcfx", "master")) {
            val base = PerfRows.CATALOG.getValue(id)
            val fx = PerfRows.withDeckRowMode(base, ctx, state)
            assertNotEquals(base.bankId, fx.bankId)
            assertEquals(base.knobOffset, fx.knobOffset)
            assertEquals(base.hasExtraHeader, fx.hasExtraHeader)
            assertEquals(base.canExpand, fx.canExpand)
            assertTrue(base.accent.contentEquals(fx.accent))
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

    @Test
    fun formatValueHandlesNegativesAndRounding() {
        assertEquals("-2", PerfKnobResolver.formatValue(-2f))
        assertEquals("-0.50", PerfKnobResolver.formatValue(-0.5f))
        assertEquals("0.13", PerfKnobResolver.formatValue(0.126f))
        assertEquals("0", PerfKnobResolver.formatValue(0f))
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
