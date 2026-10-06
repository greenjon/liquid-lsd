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
        MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.GLOBAL
    ) + FxMacroSync.FX_BANK_IDS
    private var savedPage = PerfPageDef.DEFAULT_ID

    @BeforeTest
    fun setUp() {
        // The tab is persisted between runs (the real app leaves it wherever you last were), so pin it.
        savedPage = UITheme.performancePageId
        UITheme.performancePageId = "ab"
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
        state.rackModuleDisclosure.clear() // leave no expanded rows behind for other tests
        for (id in bankIds) MacroEngine.unregisterBank(id)
    }

    private fun surface() = PerformSurface(UITheme, ctx, state, mixer)

    private fun page(pageId: String = "ab") = PerformPages.resolve(pageId, ctx, state, mixer)

    private fun filter(id: String, params: List<String>): ISFFilter {
        val inputs = mutableListOf(ISFInput(NAME = "inputImage", TYPE = "image"))
        for (name in params) {
            inputs += ISFInput(NAME = name, TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(2.0f), DEFAULT = JsonPrimitive(1.0f))
        }
        return ISFFilter(id, id, ISFHeader(INPUTS = inputs), mockk<Shader>(relaxed = true))
    }

    // --- Page resolution ---

    @Test
    fun abTabIsFourRowsOfFourKnobsRowMajor() {
        val p = page()
        assertEquals(16, p.knobs.size)
        assertEquals(listOf(MacroEngine.DECK_A, MacroEngine.DECK_A_FX, MacroEngine.DECK_B, MacroEngine.DECK_B_FX),
            listOf(0, 4, 8, 12).map { p.knobs[it]?.bankId })
        assertEquals((0 until 4).toList(), (0 until 4).map { p.knobs[it]?.spec?.knobIndex })
        assertEquals((0 until 16).map { it % 4 }, p.knobs.map { it?.spec?.col })
    }

    @Test
    fun mixerTabIsMasterMixMasterFxTransitionsAndAKnoblessClockRow() {
        val p = page("mixer")
        assertEquals(listOf(MacroEngine.MASTER, MacroEngine.MASTER_FX, MacroEngine.TRANS, null),
            listOf(0, 4, 8, 12).map { p.knobs[it]?.bankId })
        assertTrue((12..15).all { p.knobs[it] == null })
    }

    @Test
    fun anFxRowShowsTheChainSlotsAndIgnoresTheEditBayTab() {
        every { mixer.deckB.fxChain } returns FxChain("Deck B FX")
        state.setDeckSubTab("Deck B", "FX")   // the bay's tab must not retarget Perform rows
        val p = page()
        assertEquals(MacroEngine.DECK_B, p.knobs[8]?.bankId)
        assertEquals(MacroEngine.DECK_B_FX, p.knobs[12]?.bankId)
        assertTrue(p.knobs[13]?.spec?.under is UnderKnob.SlotCell)
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
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)

        assertTrue(deckAChain.slots[1]!!.enabled)
        surface().primary(6)                       // knob 3 = slot 2 (index 1)
        FxOps.drainOnGlThread(mixer)
        assertFalse(deckAChain.slots[1]!!.enabled)
        surface().primary(6)
        FxOps.drainOnGlThread(mixer)
        assertTrue(deckAChain.slots[1]!!.enabled)
    }

    @Test
    fun shiftedTapFocusesTheSlotThenLeavesFocusFromKnobOne() {
        deckAChain.slots[0] = filter("glow", listOf("a", "b", "c", "d"))
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)
        assertNull(deckAChain.focusedSlot)

        surface().secondary(5)                     // knob 2 = slot 1
        assertEquals(0, deckAChain.focusedSlot)
        assertTrue(page().knobs[6]?.spec?.under is UnderKnob.ParamCell)

        surface().secondary(6)                     // on a parameter: next page (4 params = 2 pages)
        assertEquals(1, deckAChain.focusParamPage)

        surface().secondary(4)                     // knob 1 in focus mode: leave focus
        assertNull(deckAChain.focusedSlot)
    }

    @Test
    fun shiftedTapOnKnobOneTogglesChainLinking() {
        deckAChain.slots[0] = filter("glow", listOf("a"))
        deckAChain.setAllSlotsLinked(false)
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)

        surface().secondary(4)
        assertTrue(deckAChain.areAllSlotsLinked())
        assertNull(deckAChain.focusedSlot)

        surface().secondary(4)
        assertTrue(deckAChain.areAllSlotsUnlinked())
    }

    @Test
    fun toggleChainLinkTargetsTheFxChainOfTheTouchedRowAndWorksInFocusMode() {
        deckAChain.slots[0] = filter("glow", listOf("a", "b", "c", "d"))
        deckAChain.setAllSlotsLinked(false)
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)

        surface().turn(4, 0.01f)                    // touch the Deck A FX row
        surface().toggleChainLink()
        assertTrue(deckAChain.areAllSlotsLinked())

        surface().secondary(5)                      // focus slot 1: Shift+Tap on knob 1 would no longer toggle
        assertEquals(0, deckAChain.focusedSlot)
        surface().toggleChainLink()
        assertTrue(deckAChain.areAllSlotsUnlinked())
    }

    @Test
    fun tapOnAFocusedParameterResetsItToDefaultAndMovesTheKnob() {
        val glow = filter("glow", listOf("intensity", "radius", "threshold"))
        deckAChain.slots[0] = glow
        FxMacroSync.focusSlot(MacroEngine.DECK_A_FX, mixer, 0)

        val param = glow.parameters["intensity"]!!
        param.baseValue = 2.0f
        val control = page().knobs[5]!!.control
        control.value = 1f

        surface().primary(5)
        assertEquals(param.defaultValue, param.baseValue)
        assertEquals(0.5f, control.value, 1e-6f)   // default 1.0 in [0, 2]
    }

    @Test
    fun showPageSelectsTheMatrixTabAndIgnoresUnknownPages() {
        surface().showPage("perform.mixer")
        assertEquals("mixer", UITheme.performancePageId)
        surface().showPage("perform.ab")
        assertEquals("ab", UITheme.performancePageId)
        surface().showPage("mixer") // a bare page id works too
        assertEquals("mixer", UITheme.performancePageId)
        surface().showPage("nonsense")
        assertEquals("mixer", UITheme.performancePageId)
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
        val deckB = lights[8]!!
        assertEquals(PerformanceColors.COLOR_DECK_B.toList(), listOf(deckB.r, deckB.g, deckB.b))
    }

    @Test
    fun anEmptyOrBypassedSlotGoesDarkButKeepsItsRing() {
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)
        assertFalse(surface().knobLights()[5]!!.lit, "slot 1 is empty")

        deckAChain.slots[0] = filter("glow", listOf("intensity"))
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)
        assertTrue(surface().knobLights()[5]!!.lit)

        surface().primary(5)
        FxOps.drainOnGlThread(mixer)
        val bypassed = surface().knobLights()[5]!!
        assertFalse(bypassed.lit)
        assertEquals(page().knobs[5]!!.control.value, bypassed.value)
    }

    @Test
    fun blankParameterPositionsOnAFocusedPageAreDark() {
        deckAChain.slots[0] = filter("glow", listOf("intensity"))      // one parameter: knobs 3 and 4 are blank
        FxMacroSync.focusSlot(MacroEngine.DECK_A_FX, mixer, 0)
        val lights = surface().knobLights()
        assertTrue(lights[4]!!.lit, "the focused slot's Metaknob")
        assertTrue(lights[5]!!.lit, "the parameter")
        assertFalse(lights[6]!!.lit)
        assertFalse(lights[7]!!.lit)
    }

    @Test
    fun masterRowsUseHueColoursOnTheHardware() {
        val lights = surface().let { UITheme.performancePageId = "mixer"; it.knobLights() }
        fun rgb(i: Int) = lights[i]!!.let { listOf(it.r, it.g, it.b) }
        assertEquals(PerformanceColors.LED_MASTER.toList(), rgb(0))
        assertEquals(PerformanceColors.COLOR_TRANS.toList(), rgb(8))
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
            val values = PerfRows.rowsForPage(page).map(::hue)
            assertEquals(4, values.distinct().size, "${page.id} LEDs: $values")
        }
    }

    private fun pageOf(vararg rows: String) = PerfPageDef("t", "T", rows = rows.map(::RowPlacement))

    @Test
    fun rowsOnAPageIgnoreTheEditBayTabs() {
        val page = pageOf("deck.A.src", "deck.A.fx", "master.mix", "master.fx")
        fun banks() = PerfRows.rowsForPage(page).map { it.bankId }
        val expected = listOf(MacroEngine.DECK_A, MacroEngine.DECK_A_FX, MacroEngine.MASTER, MacroEngine.MASTER_FX)
        assertEquals(expected, banks())
        state.setDeckSubTab("Deck A", "FX"); state.activeMixerSubTab = "FX"
        try {
            assertEquals(expected, banks())
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
            pageOf("master.mix", "master.fx", "trans", "global"),
        )
        for (page in candidatePages) {
            val values = PerfRows.rowsForPage(page).map { row ->
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
        val ab = pages.first { it.id == "ab" }
        val first = cache.rows(ab, ctx, state, { it }, pages)
        assertEquals(uncachedRows(ab, pages), first)
        assertTrue(first === cache.rows(ab, ctx, state, { it }, pages), "unchanged inputs reuse the list")

        // Opening Deck A in the Edit bay shows the row for the bay's tab: SRC, then FX.
        state.rackModuleDisclosure[MacroEngine.DECK_A] = ParametersState.DisclosureLevel.DEEP_EDIT
        state.setDeckSubTab("Deck A", "SRC")
        val src = cache.rows(ab, ctx, state, { it }, pages)
        assertEquals(MacroEngine.DECK_A, src.single().bankId)
        state.setDeckSubTab("Deck A", "FX")
        val fx = cache.rows(ab, ctx, state, { it }, pages)
        assertTrue(fx !== src)
        assertEquals(uncachedRows(ab, pages), fx)
        assertEquals(MacroEngine.DECK_A_FX, fx.single().bankId)
        state.setDeckSubTab("Deck A", "SRC")
        state.rackModuleDisclosure[MacroEngine.DECK_A] = ParametersState.DisclosureLevel.COLLAPSED
        assertEquals(uncachedRows(ab, pages), cache.rows(ab, ctx, state, { it }, pages))

        val master = pages.first { it.id == "mixer" }
        state.activeMixerSubTab = "FX"
        assertEquals(uncachedRows(master, pages), cache.rows(master, ctx, state, { it }, pages))
        state.activeMixerSubTab = "CTRL"
        assertEquals(uncachedRows(master, pages), cache.rows(master, ctx, state, { it }, pages))
    }

    @Test
    fun rowsCacheFollowsTheStoreList() {
        val cache = PerfRows.RowsCache()
        val one = PerfPageDef("t1", "T1", rows = List(PerfPageDef.ROWS) { RowPlacement(if (it % 2 == 0) "deck.A.src" else "deck.A.fx") })
        val a = cache.rows(one, ctx, state, { it }, listOf(one))
        assertEquals(uncachedRows(one, listOf(one)), a)
        // A reloaded store hands out a new list; the cache must not keep serving the old page's rows.
        val edited = one.copy(rows = List(PerfPageDef.ROWS) { RowPlacement("deck.B.src") })
        assertEquals(MacroEngine.DECK_B, cache.rows(edited, ctx, state, { it }, listOf(edited))[0].bankId)
    }

    @Test
    fun rowsCacheFollowsEditAndTheMixerSubTab() {
        val cache = PerfRows.RowsCache()
        val pages = PerfPageStore.default.all()
        val ab = pages.first { it.id == "ab" }
        assertEquals(4, cache.rows(ab, ctx, state, { it }, pages).size)
        state.rackModuleDisclosure[MacroEngine.DECK_B] = ParametersState.DisclosureLevel.DEEP_EDIT
        val open = cache.rows(ab, ctx, state, { it }, pages)
        assertEquals(uncachedRows(ab, pages), open)
        assertEquals(1, open.size)
        state.rackModuleDisclosure[MacroEngine.DECK_B] = ParametersState.DisclosureLevel.COLLAPSED
        assertEquals(4, cache.rows(ab, ctx, state, { it }, pages).size)

        state.rackModuleDisclosure["Mixer"] = ParametersState.DisclosureLevel.DEEP_EDIT
        val saved = state.activeMixerSubTab
        try {
            for (tab in listOf("TRANS", "FX", "CTRL")) {
                state.activeMixerSubTab = tab
                assertEquals(uncachedRows(ab, pages), cache.rows(ab, ctx, state, { it }, pages), tab)
            }
        } finally {
            state.activeMixerSubTab = saved
        }
    }

    @Test
    fun surfaceRetargetsKnobsWhenTheEditBayTabFlipsDespiteTheCache() {
        state.rackModuleDisclosure[MacroEngine.DECK_A] = ParametersState.DisclosureLevel.DEEP_EDIT
        state.setDeckSubTab("Deck A", "SRC")
        val s = surface()
        assertEquals(MacroEngine.DECK_A, page().knobs[0]!!.bankId)
        s.turn(0, 0.1f)
        state.setDeckSubTab("Deck A", "FX")
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)
        assertEquals(MacroEngine.DECK_A_FX, page().knobs[0]!!.bankId)
        val fxControl = page().knobs[0]!!.control
        val before = fxControl.value
        s.turn(0, 0.2f)
        assertEquals((before + 0.2f).coerceIn(0f, 1f), fxControl.value)
    }
}
