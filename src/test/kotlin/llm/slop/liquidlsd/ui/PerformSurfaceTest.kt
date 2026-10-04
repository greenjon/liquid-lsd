package llm.slop.liquidlsd.ui

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformSurfaceTest {
    private val ctx = PerformanceUiContext()
    private val state = ParametersState()
    private val mixer = mockk<Mixer>(relaxed = true)
    private val deckAChain = FxChain("Deck A FX")
    private val bankIds = listOf(
        MacroEngine.DECK_A, MacroEngine.DECK_B, MacroEngine.DECK_BG, MacroEngine.DECK_PV,
        MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.FX_SENDS, MacroEngine.GLOBAL
    ) + FxMacroSync.FX_BANK_IDS
    private var savedPage = PerfPageDef.DEFAULT_ID

    @BeforeTest
    fun setUp() {
        // The tab is persisted between runs (the real app leaves it wherever you last were), so pin it.
        savedPage = UITheme.performancePageId
        UITheme.performancePageId = "decks"
        // A new ParametersState restores the persisted Deep Edit disclosure, which other tests leave
        // expanded; clear the in-memory map (not setDisclosure, which would persist) to start in Perform view.
        state.rackModuleDisclosure.clear()
        for (id in bankIds) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
        every { mixer.deckA.fxChain } returns deckAChain
        for (i in 0 until FxChain.SLOT_COUNT) deckAChain.setSlotLinked(i, false)
    }

    @AfterTest
    fun tearDown() {
        UITheme.performancePageId = savedPage
        for (id in bankIds) MacroEngine.unregisterBank(id)
    }

    private fun surface() = PerformSurface(UITheme, ctx, state, mixer)

    private fun page(pageId: String = "decks") = PerformPages.resolve(pageId, ctx, state, mixer)

    private fun filter(id: String, params: List<String>): ISFFilter {
        val inputs = mutableListOf(ISFInput(NAME = "inputImage", TYPE = "image"))
        for (name in params) {
            inputs += ISFInput(NAME = name, TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(2.0f), DEFAULT = JsonPrimitive(1.0f))
        }
        return ISFFilter(id, id, ISFHeader(INPUTS = inputs), mockk<Shader>(relaxed = true))
    }

    // --- Page resolution ---

    @Test
    fun decksTabIsFourRowsOfFourKnobsRowMajor() {
        val p = page()
        assertEquals(16, p.knobs.size)
        assertEquals(listOf(MacroEngine.DECK_A, MacroEngine.DECK_B, MacroEngine.DECK_BG, MacroEngine.DECK_PV),
            listOf(0, 4, 8, 12).map { p.knobs[it]?.bankId })
        assertEquals((0 until 4).toList(), (0 until 4).map { p.knobs[it]?.spec?.knobIndex })
        assertEquals((0 until 16).map { it % 4 }, p.knobs.map { it?.spec?.col })
    }

    @Test
    fun masterTabIsMasterTransitionsFxSendsAndAKnoblessClockRow() {
        val p = page("master")
        assertEquals(listOf(MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.FX_SENDS, null),
            listOf(0, 4, 8, 12).map { p.knobs[it]?.bankId })
        assertTrue((12..15).all { p.knobs[it] == null })
    }

    @Test
    fun aRowInFxModeSwitchesItsKnobsToTheFxBank() {
        state.setDeckSubTab("Deck B", "FX")
        every { mixer.deckB.fxChain } returns FxChain("Deck B FX")
        val p = page()
        assertEquals(MacroEngine.DECK_A, p.knobs[0]?.bankId)
        assertEquals(MacroEngine.DECK_B_FX, p.knobs[4]?.bankId)
        assertTrue(p.knobs[5]?.spec?.under is UnderKnob.SlotCell)
    }

    // --- Gestures ---

    @Test
    fun turnMovesTheKnobAndClampsAtTheEnds() {
        val control = page().knobs[2]!!.control
        control.value = 0.5f
        surface().turn(2, 0.25f)
        assertEquals(0.75f, control.value, 1e-6f)
        surface().turn(2, 0.5f)
        assertEquals(1f, control.value)
        surface().turn(2, -3f)
        assertEquals(0f, control.value)
    }

    @Test
    fun turnOnAKnobThatDoesNotExistIsIgnored() {
        surface().turn(40, 0.5f)
        surface().primary(-1)
        surface().secondary(99)
    }

    @Test
    fun tapOnASourceKnobResetsItToTheMouseDefault() {
        val control = page().knobs[1]!!.control
        control.value = 0.9f
        surface().primary(1)
        assertEquals(PerformSurface.LABEL_KNOB_DEFAULT, control.value)
    }

    @Test
    fun tapOnAnFxSlotKnobTogglesThatSlotsBypass() {
        deckAChain.slots[1] = filter("glow", listOf("intensity"))
        state.setDeckSubTab("Deck A", "FX")
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)

        assertTrue(deckAChain.slots[1]!!.enabled)
        surface().primary(2)                       // knob 3 = slot 2 (index 1)
        FxOps.drainOnGlThread(mixer)
        assertFalse(deckAChain.slots[1]!!.enabled)
        surface().primary(2)
        FxOps.drainOnGlThread(mixer)
        assertTrue(deckAChain.slots[1]!!.enabled)
    }

    @Test
    fun shiftedTapFocusesTheSlotThenLeavesFocusFromKnobOne() {
        deckAChain.slots[0] = filter("glow", listOf("a", "b", "c", "d"))
        state.setDeckSubTab("Deck A", "FX")
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)
        assertNull(deckAChain.focusedSlot)

        surface().secondary(1)                     // knob 2 = slot 1
        assertEquals(0, deckAChain.focusedSlot)
        assertTrue(page().knobs[2]?.spec?.under is UnderKnob.ParamCell)

        surface().secondary(2)                     // on a parameter: next page (4 params = 2 pages)
        assertEquals(1, deckAChain.focusParamPage)

        surface().secondary(0)                     // knob 1 in focus mode: leave focus
        assertNull(deckAChain.focusedSlot)
    }

    @Test
    fun tapOnAFocusedParameterResetsItToDefaultAndMovesTheKnob() {
        val glow = filter("glow", listOf("intensity", "radius", "threshold"))
        deckAChain.slots[0] = glow
        state.setDeckSubTab("Deck A", "FX")
        FxMacroSync.focusSlot(MacroEngine.DECK_A_FX, mixer, 0)

        val param = glow.parameters["intensity"]!!
        param.baseValue = 2.0f
        val control = page().knobs[1]!!.control
        control.value = 1f

        surface().primary(1)
        assertEquals(param.defaultValue, param.baseValue)
        assertEquals(0.5f, control.value, 1e-6f)   // default 1.0 in [0, 2]
    }

    @Test
    fun showPageSelectsTheMatrixTabAndIgnoresUnknownPages() {
        surface().showPage(PerformSurface.PAGE_MASTER)
        assertEquals("master", UITheme.performancePageId)
        surface().showPage(PerformSurface.PAGE_DECKS)
        assertEquals("decks", UITheme.performancePageId)
        surface().showPage("master") // a bare page id works too
        assertEquals("master", UITheme.performancePageId)
        surface().showPage("nonsense")
        assertEquals("master", UITheme.performancePageId)
        assertNotNull(page())
    }

    // --- Lights (ring + LED feedback) ---

    @Test
    fun lightsCarryTheKnobValueAndTheRowAccent() {
        val a = page().knobs[0]!!
        a.control.value = 0.25f
        val lights = surface().knobLights()
        assertEquals(16, lights.size)
        val first = lights[0]!!
        assertEquals(0.25f, first.value)
        assertEquals(PerformanceColors.COLOR_DECK_A.toList(), listOf(first.r, first.g, first.b))
        assertTrue(first.lit)
        val deckB = lights[4]!!
        assertEquals(PerformanceColors.COLOR_DECK_B.toList(), listOf(deckB.r, deckB.g, deckB.b))
    }

    @Test
    fun anEmptyOrBypassedSlotGoesDarkButKeepsItsRing() {
        state.setDeckSubTab("Deck A", "FX")
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)
        assertFalse(surface().knobLights()[1]!!.lit, "slot 1 is empty")

        deckAChain.slots[0] = filter("glow", listOf("intensity"))
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)
        assertTrue(surface().knobLights()[1]!!.lit)

        surface().primary(1)
        FxOps.drainOnGlThread(mixer)
        val bypassed = surface().knobLights()[1]!!
        assertFalse(bypassed.lit)
        assertEquals(page().knobs[1]!!.control.value, bypassed.value)
    }

    @Test
    fun blankParameterPositionsOnAFocusedPageAreDark() {
        deckAChain.slots[0] = filter("glow", listOf("intensity"))      // one parameter: knobs 3 and 4 are blank
        state.setDeckSubTab("Deck A", "FX")
        FxMacroSync.focusSlot(MacroEngine.DECK_A_FX, mixer, 0)
        val lights = surface().knobLights()
        assertTrue(lights[0]!!.lit, "the focused slot's Metaknob")
        assertTrue(lights[1]!!.lit, "the parameter")
        assertFalse(lights[2]!!.lit)
        assertFalse(lights[3]!!.lit)
    }

    @Test
    fun masterRowsUseHueColoursOnTheHardware() {
        val lights = surface().let { UITheme.performancePageId = "master"; it.knobLights() }
        fun rgb(i: Int) = lights[i]!!.let { listOf(it.r, it.g, it.b) }
        assertEquals(PerformanceColors.LED_MASTER.toList(), rgb(0))
        assertEquals(PerformanceColors.COLOR_TRANS.toList(), rgb(4))
        assertEquals(PerformanceColors.COLOR_FX.toList(), rgb(8))
        assertNull(lights[12])  // the Clock row has no knobs
    }

    @Test
    fun everyRowCanBeShownOnAnRgbLedWithItsOwnColour() {
        val wheel = llm.slop.liquidlsd.control.HueWheel()
        val pages = PerfPageStore.default.all()
        fun hue(row: RowDescriptor): Int {
            val c = PerformPages.ledColor(row)
            return wheel.valueFor(c[0], c[1], c[2]).also {
                assertTrue(it != wheel.off && it != wheel.white, "${row.groupLabel} needs a real hue, got $it")
            }
        }
        hue(RowDescriptor(MacroEngine.MASTER_FX, 0, PerformanceColors.COLOR_MASTER, "MASTER (FX)"))
        // The four rows that share a page must be told apart.
        for (page in pages) {
            val values = PerfRows.substitutedRowsForPage(page, ctx).map(::hue)
            assertEquals(4, values.distinct().size, "${page.id} LEDs: $values")
        }
    }

    private fun pageOf(vararg rows: String) = PerfPageDef("t", "T", rows = rows.map(::RowPlacement))

    @Test
    fun pinnedRowsIgnoreTheSharedModeAndDoNotFlipNeighbours() {
        val pinned = pageOf("deck.A.src", "deck.A.fx", "master.mix", "master.fx")
        fun banks() = PerfRows.substitutedRowsForPage(pinned, ctx, state).map { it.bankId }
        val expected = listOf(MacroEngine.DECK_A, MacroEngine.DECK_A_FX, MacroEngine.MASTER, MacroEngine.MASTER_FX)
        assertEquals(expected, banks())
        state.setDeckSubTab("Deck A", "FX"); state.activeMixerSubTab = "FX"
        try {
            assertEquals(expected, banks())
            // The toggle row, by contrast, follows the shared mode.
            assertEquals(MacroEngine.DECK_A_FX,
                PerfRows.substitutedRowsForPage(pageOf("deck.A.srcfx", "trans", "wetdry", "global"), ctx, state).first().bankId)
        } finally {
            state.setDeckSubTab("Deck A", "SRC"); state.activeMixerSubTab = "CTRL"
        }
    }

    @Test
    fun pinnedFxRowsKeepTheirOwnHueOnTheHardware() {
        val wheel = llm.slop.liquidlsd.control.HueWheel()
        val candidatePages = listOf(
            pageOf("deck.A.src", "deck.A.fx", "deck.B.src", "deck.B.fx"),
            pageOf("deck.BG.src", "deck.BG.fx", "deck.PV.src", "deck.PV.fx"),
            pageOf("master.mix", "master.fx", "trans", "wetdry"),
        )
        for (page in candidatePages) {
            val values = PerfRows.substitutedRowsForPage(page, ctx).map { row ->
                val c = PerformPages.ledColor(row)
                wheel.valueFor(c[0], c[1], c[2]).also { assertTrue(it != wheel.off && it != wheel.white, "${row.groupLabel}: $it") }
            }
            // Every LED on a page needs its own hue, and neighbouring hues must be a few wheel steps apart to be told apart by eye.
            assertEquals(4, values.distinct().size, "${page.rows.map { it.row }}: $values")
            val sorted = values.sorted()
            assertTrue(sorted.zipWithNext().all { (a, b) -> b - a >= 4 }, "hues too close: $values")
        }
    }

    @Test
    fun knobLightsIncludesMeterTypeFromResolvedSpec() {
        val lights = surface().knobLights()
        assertNotNull(lights[0], "knob light 0 exists")
        assertEquals(MeterType.MONOPOLAR, lights[0]?.meterType)
    }

    // --- Row-resolution cache ---

    private fun uncachedRows(page: PerfPageDef, pages: List<PerfPageDef>) =
        PerfRows.visibleRowsForPage(page, ctx, state, { it }, pages)

    @Test
    fun rowsCacheReusesTheResultUntilAnInputChanges() {
        val cache = PerfRows.RowsCache()
        val pages = PerfPageStore.default.all()
        val decks = pages.first { it.id == "decks" }
        val first = cache.rows(decks, ctx, state, { it }, pages)
        assertEquals(uncachedRows(decks, pages), first)
        assertTrue(first === cache.rows(decks, ctx, state, { it }, pages), "unchanged inputs reuse the list")

        state.setDeckSubTab("Deck A", "FX")                       // SRC -> FX toggle
        val fx = cache.rows(decks, ctx, state, { it }, pages)
        assertTrue(fx !== first)
        assertEquals(uncachedRows(decks, pages), fx)
        assertEquals(MacroEngine.DECK_A_FX, fx[0].bankId)

        state.setDeckSubTab("Deck A", "SRC")
        assertEquals(uncachedRows(decks, pages), cache.rows(decks, ctx, state, { it }, pages))

        state.activeMixerSubTab = "FX"
        val master = pages.first { it.id == "master" }
        assertEquals(uncachedRows(master, pages), cache.rows(master, ctx, state, { it }, pages))
        state.activeMixerSubTab = "CTRL"
        assertEquals(uncachedRows(master, pages), cache.rows(master, ctx, state, { it }, pages))
    }

    @Test
    fun rowsCacheFollowsPinsAndTheStoreList() {
        val cache = PerfRows.RowsCache()
        val pinned = PerfPageDef("t1", "T1", rows = List(PerfPageDef.ROWS) { RowPlacement(if (it % 2 == 0) "deck.A.src" else "deck.A.fx") })
        val unpinned = pinned.copy(rows = List(PerfPageDef.ROWS) { RowPlacement("deck.A.srcfx") })
        val a = cache.rows(pinned, ctx, state, { it }, listOf(pinned))
        assertEquals(uncachedRows(pinned, listOf(pinned)), a)
        val b = cache.rows(unpinned, ctx, state, { it }, listOf(unpinned))
        assertEquals(uncachedRows(unpinned, listOf(unpinned)), b)
        assertTrue(a.map { it.pinnedMode } != b.map { it.pinnedMode })
        // A reloaded store hands out a new list; the cache must not keep serving the old page's rows.
        val edited = unpinned.copy(rows = List(PerfPageDef.ROWS) { RowPlacement("deck.B.srcfx") })
        assertEquals(MacroEngine.DECK_B, cache.rows(edited, ctx, state, { it }, listOf(edited))[0].bankId)
    }

    @Test
    fun rowsCacheFollowsDeepEditAndTheMixerSubTab() {
        val cache = PerfRows.RowsCache()
        val pages = PerfPageStore.default.all()
        val decks = pages.first { it.id == "decks" }
        assertEquals(4, cache.rows(decks, ctx, state, { it }, pages).size)
        state.rackModuleDisclosure[MacroEngine.DECK_B] = ParametersState.DisclosureLevel.DEEP_EDIT
        val open = cache.rows(decks, ctx, state, { it }, pages)
        assertEquals(uncachedRows(decks, pages), open)
        assertEquals(1, open.size)
        state.rackModuleDisclosure[MacroEngine.DECK_B] = ParametersState.DisclosureLevel.COLLAPSED
        assertEquals(4, cache.rows(decks, ctx, state, { it }, pages).size)

        state.rackModuleDisclosure["Mixer"] = ParametersState.DisclosureLevel.DEEP_EDIT
        val saved = state.activeMixerSubTab
        try {
            for (tab in listOf("TRANS", "FX", "CTRL")) {
                state.activeMixerSubTab = tab
                assertEquals(uncachedRows(decks, pages), cache.rows(decks, ctx, state, { it }, pages), tab)
            }
        } finally {
            state.activeMixerSubTab = saved
        }
    }

    @Test
    fun surfaceRetargetsKnobsAfterAModeFlipDespiteTheCache() {
        val s = surface()
        assertEquals(MacroEngine.DECK_A, s.knobLights().let { page().knobs[0]!!.bankId })
        s.turn(0, 0.1f)
        state.setDeckSubTab("Deck A", "FX")
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)
        val fxControl = page().knobs[0]!!.control
        val before = fxControl.value
        s.turn(0, 0.2f)
        assertEquals((before + 0.2f).coerceIn(0f, 1f), fxControl.value)
    }
}
