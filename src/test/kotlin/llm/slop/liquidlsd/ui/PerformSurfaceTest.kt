package llm.slop.liquidlsd.ui

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
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
    private var savedTab = 0

    @BeforeTest
    fun setUp() {
        // The tab is persisted between runs (the real app leaves it wherever you last were), so pin it.
        savedTab = UITheme.performanceMatrixTab
        UITheme.performanceMatrixTab = PerfRows.TAB_DECKS
        // A new ParametersState restores the persisted Deep Edit disclosure, which other tests leave
        // expanded; clear the in-memory map (not setDisclosure, which would persist) to start in Perform view.
        state.rackModuleDisclosure.clear()
        for (id in bankIds) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
        every { mixer.deckA.fxChain } returns deckAChain
        for (i in 0 until FxChain.SLOT_COUNT) deckAChain.setSlotLinked(i, false)
    }

    @AfterTest
    fun tearDown() {
        UITheme.performanceMatrixTab = savedTab
        for (id in bankIds) MacroEngine.unregisterBank(id)
    }

    private fun surface() = PerformSurface(UITheme, ctx, state, mixer)

    private fun page(tab: Int = PerfRows.TAB_DECKS) = PerformPages.resolve(tab, ctx, state, mixer)

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
    fun masterTabIsMasterTransitionsFxSendsAndGlobal() {
        val p = page(PerfRows.TAB_MASTER)
        assertEquals(listOf(MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.FX_SENDS, MacroEngine.GLOBAL),
            listOf(0, 4, 8, 12).map { p.knobs[it]?.bankId })
    }

    @Test
    fun aRowInFxModeSwitchesItsKnobsToTheFxBank() {
        ctx.deckRowMode["B"] = "FX"
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
        ctx.deckRowMode["A"] = "FX"
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
        ctx.deckRowMode["A"] = "FX"
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
        ctx.deckRowMode["A"] = "FX"
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
        assertEquals(PerfRows.TAB_MASTER, UITheme.performanceMatrixTab)
        surface().showPage(PerformSurface.PAGE_DECKS)
        assertEquals(PerfRows.TAB_DECKS, UITheme.performanceMatrixTab)
        surface().showPage("nonsense")
        assertEquals(PerfRows.TAB_DECKS, UITheme.performanceMatrixTab)
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
        ctx.deckRowMode["A"] = "FX"
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
        ctx.deckRowMode["A"] = "FX"
        FxMacroSync.focusSlot(MacroEngine.DECK_A_FX, mixer, 0)
        val lights = surface().knobLights()
        assertTrue(lights[0]!!.lit, "the focused slot's Metaknob")
        assertTrue(lights[1]!!.lit, "the parameter")
        assertFalse(lights[2]!!.lit)
        assertFalse(lights[3]!!.lit)
    }

    @Test
    fun masterAndGlobalRowsUseHueColoursOnTheHardware() {
        val lights = surface().let { UITheme.performanceMatrixTab = PerfRows.TAB_MASTER; it.knobLights() }
        fun rgb(i: Int) = lights[i]!!.let { listOf(it.r, it.g, it.b) }
        assertEquals(PerformanceColors.LED_MASTER.toList(), rgb(0))
        assertEquals(PerformanceColors.COLOR_TRANS.toList(), rgb(4))
        assertEquals(PerformanceColors.COLOR_FX.toList(), rgb(8))
        assertEquals(PerformanceColors.LED_GLOBAL.toList(), rgb(12))
    }

    @Test
    fun everyRowCanBeShownOnAnRgbLedWithItsOwnColour() {
        val wheel = llm.slop.liquidlsd.control.HueWheel()
        val rows = PerfRows.TAB_ROWS.flatMap { it } +
            RowDescriptor(MacroEngine.MASTER_FX, 0, PerformanceColors.COLOR_MASTER, "MASTER (FX)")
        val values = rows.map { row ->
            val c = PerformPages.ledColor(row)
            wheel.valueFor(c[0], c[1], c[2]).also {
                assertTrue(it != wheel.off && it != wheel.white, "${row.groupLabel} needs a real hue, got $it")
            }
        }
        // The four rows that share a tab must be told apart: DECKS (first four) and MASTER (next four).
        assertEquals(4, values.take(4).distinct().size, "deck LEDs: $values")
        assertEquals(4, values.drop(4).take(4).distinct().size, "master-tab LEDs: $values")
    }
}
