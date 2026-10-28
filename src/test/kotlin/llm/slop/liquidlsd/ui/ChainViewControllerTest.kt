package llm.slop.liquidlsd.ui

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.browser.BrowseCatalogs
import llm.slop.liquidlsd.ui.browser.BrowseKind
import llm.slop.liquidlsd.ui.browser.FXBrowserPanel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Chain view on a controller: the free gesture in and out, the side buttons, the 16 knobs in each state, and the load knobs' lights. */
class ChainViewControllerTest {
    private lateinit var session: SessionContext
    private val ctx = PerformanceUiContext()
    private val mixer = mockk<Mixer>(relaxed = true)
    private val deckAChain = FxChain("Deck A FX")
    private val state get() = session.parametersState
    private val bankIds = listOf(
        MacroEngine.DECK_A, MacroEngine.DECK_B, MacroEngine.DECK_BG, MacroEngine.DECK_PV,
        MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.GLOBAL
    ) + FxMacroSync.FX_BANK_IDS
    private var savedPage = ""
    private var savedMode = UITheme.LibraryMode.HALF
    private var savedAsset = FXBrowserPanel.selectedAsset

    @BeforeTest
    fun setUp() {
        session = SessionContext()
        savedPage = UITheme.performancePageId
        savedMode = UITheme.libraryMode
        savedAsset = FXBrowserPanel.selectedAsset
        UITheme.performancePageId = "ab"
        UITheme.libraryMode = UITheme.LibraryMode.HALF
        state.rackModuleDisclosure.clear()
        nav()
        for (id in bankIds) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
        every { mixer.deckA.fxChain } returns deckAChain
        for (i in 0 until FxChain.SLOT_COUNT) deckAChain.setSlotLinked(i, false)
        FxMacroSync.syncFor(MacroEngine.DECK_A_FX, mixer)
        PerformSurface.lastTouchedKnob = null
        val recentDir = kotlin.io.path.createTempDirectory("recent").toFile().apply { deleteOnExit() }
        BrowseKind.entries.forEach { llm.slop.liquidlsd.ui.browser.BrowseRecents.resetForTest(it, java.io.File(recentDir, "${it.name}.json").apply { deleteOnExit() }) }
    }

    @AfterTest
    fun tearDown() {
        PerformSurface.lastTouchedKnob = null
        UITheme.performancePageId = savedPage
        UITheme.libraryMode = savedMode
        FXBrowserPanel.selectedAsset = savedAsset
        for (id in bankIds + FxMacroSync.SLOT_BANK_IDS) MacroEngine.unregisterBank(id)
    }

    private fun nav() = NavigationSurface(session, state, mixer, ctx)
    private fun surface() = PerformSurface(UITheme, ctx, state, mixer, nav())
    private fun knobs() = PerformPages.resolve(UITheme.performancePageId, ctx, state, mixer).knobs

    @Test
    fun shiftTapOnASourceKnobTogglesChainViewForThatDeck() {
        surface().secondary(1)   // Deck A SRC row, knob 2, on the A/B page
        assertEquals("A", state.focusedPair)
        assertEquals(ParametersState.ChainViewState.PLAY, state.chainView)
        surface().secondary(2)   // in Chain view row 1 is FX slot 1: not the gesture
        assertEquals("A", state.focusedPair)
        surface().secondary(13)  // Deck A's SRC row is now row 4
        assertNull(state.focusedPair)
        assertNull(state.chainView)
        surface().secondary(5)   // an FX row's slot knob is not the gesture
        assertNull(state.chainView)
    }

    @Test
    fun playingNothingBrowsesAndAllSixteenKnobsAreTheFourRows() {
        state.enterChainView("A")
        assertFalse(nav().browsing)
        val k = knobs()
        assertEquals(listOf("deckA_fx_s1", "deckA_fx_s2", "deckA_fx_s3"), listOf(0, 4, 8).map { k[it]?.bankId })
        assertEquals(MacroEngine.DECK_A, k[12]?.bankId)
        assertTrue(k.all { it != null })
    }

    @Test
    fun loadingKeepsTheCursorOnKnobSixteenWithNoSendsAndTheSlotRowsLive() {
        state.enterChainView("A")
        nav().button(2, false)   // picker button: Load
        assertEquals(ParametersState.ChainViewState.BROWSE, state.chainView)
        val n = nav()
        assertTrue(n.browsing)
        assertEquals(15, n.browseKnob)
        assertFalse(n.isBrowseLive(13))
        assertTrue((0..11).all(n::isBrowseLive))
        assertTrue(n.sendTargets.isEmpty())
        assertTrue(n.switchTargets.isEmpty())
        assertTrue((12..15).all { knobs()[it] == null }, "the load row has no macro knobs")
        nav().button(2, false)
        assertEquals(ParametersState.ChainViewState.PLAY, state.chainView)
    }

    @Test
    fun aLoadKnobLoadsTheCursorItemIntoItsSlotAndTargetsIt() {
        state.enterChainView("A")
        state.toggleChainBrowse()
        FXBrowserPanel.selectedAsset = null
        assertTrue(nav().browseKnobTap(13, shifted = false), "the tap is the load knob's even with nothing to load")
        assertEquals(ParametersState.BrowseTarget.FxChain(0), state.dockSelection?.target, "nothing highlighted: nothing loaded, target unchanged")
        FXBrowserPanel.selectedAsset = AssetItem(BrowseCatalogs.STOCK_FX_PREFIX + "glow", "Glow", AssetType.FX_STOCK)
        assertTrue(nav().browseKnobTap(13, shifted = false))
        assertEquals(ParametersState.BrowseTarget.FxChain(1), state.dockSelection?.target)
        assertFalse(nav().browseKnobTap(15, shifted = false), "knob 16 is the cursor, not a load knob")
        assertFalse(nav().browseKnobTap(1, shifted = false), "knob 2 plays FX slot 1")
    }

    @Test
    fun backLeavesAndBankSteppingKeepsTheState() {
        state.enterChainView("A")
        state.toggleChainBrowse()
        assertTrue(surface().stepPair(+1))
        assertEquals("B", state.focusedPair)
        assertEquals(ParametersState.ChainViewState.BROWSE, state.chainView)
        nav().button(0, false)
        assertNull(state.focusedPair)
        assertNull(state.chainView)
    }

    @Test
    fun loadKnobsGlowInTheirSlotRowsColourAndKnobSixteenIsTheCursor() {
        state.enterChainView("A")
        state.toggleChainBrowse()
        FXBrowserPanel.selectedAsset = AssetItem(BrowseCatalogs.STOCK_FX_PREFIX + "glow", "Glow", AssetType.FX_STOCK)
        val lights = surface().knobLights()
        for (slot in 0 until 3) {
            val light = assertNotNull(lights[12 + slot])
            val c = PerformPages.ledColor(PerfRows.CATALOG.getValue("deck.A.fx${slot + 1}"))
            assertEquals(c.toList(), listOf(light.r, light.g, light.b))
            assertTrue(light.lit)
        }
        assertEquals(llm.slop.liquidlsd.parameters.MeterType.ENDLESS, lights[15]?.meterType)
        assertNotNull(lights[8], "knob 9 is FX3's Metaknob, not a send")
        FXBrowserPanel.selectedAsset = null
        assertFalse(surface().knobLights()[12]!!.lit, "dark while there's nothing to load")
    }
}
