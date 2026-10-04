package llm.slop.liquidlsd.ui

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import llm.slop.liquidlsd.ui.browser.ApplyTarget
import llm.slop.liquidlsd.ui.browser.BrowseKind
import llm.slop.liquidlsd.ui.browser.BrowseScope
import llm.slop.liquidlsd.ui.browser.BrowserPane
import llm.slop.liquidlsd.ui.browser.PresetListPanel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Controller navigation contexts (Library FULL / picker / Perform+Edit), the Esc-stack priorities of
 * [BackNavigation], and the last-touched-knob picker routing. Intended behavior: DECISIONS.md "MIDI phase 3".
 */
class NavigationSurfaceTest {
    private lateinit var session: SessionContext
    private val ctx = PerformanceUiContext()
    private val mixer = mockk<Mixer>(relaxed = true)
    private val deckAChain = FxChain("Deck A FX")
    private val state get() = session.parametersState
    private val bankIds = listOf(
        MacroEngine.DECK_A, MacroEngine.DECK_B, MacroEngine.DECK_BG, MacroEngine.DECK_PV,
        MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.FX_SENDS, MacroEngine.GLOBAL
    ) + FxMacroSync.FX_BANK_IDS
    private var savedPage = ""
    private var savedMode = UITheme.LibraryMode.HALF
    private var savedExpanded: Map<String, String> = emptyMap()

    @BeforeTest
    fun setUp() {
        session = SessionContext()
        clock = nextEpoch.also { nextEpoch += 1_000_000L } // later than any picker stamp left by an earlier test
        UiClock.nowMs = { clock }
        savedPage = UITheme.performancePageId
        savedMode = UITheme.libraryMode
        savedExpanded = UITheme.rackExpandedModules
        UITheme.performancePageId = "decks"
        UITheme.libraryMode = UITheme.LibraryMode.HALF
        state.rackModuleDisclosure.clear()
        for (id in bankIds) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
        every { mixer.deckA.fxChain } returns deckAChain
        PreferencesPanel.close()
        MacroLearnState.cancelLearn()
        PerformSurface.lastTouchedKnob = null
    }

    private var clock = 0L

    private companion object {
        var nextEpoch = 1_000_000L
    }

    @AfterTest
    fun tearDown() {
        UiClock.nowMs = { System.currentTimeMillis() }
        MacroLearnState.cancelLearn()
        PreferencesPanel.close()
        PerformSurface.lastTouchedKnob = null
        UITheme.performancePageId = savedPage
        UITheme.libraryMode = savedMode
        UITheme.rackExpandedModules = savedExpanded
        for (id in bankIds) MacroEngine.unregisterBank(id)
    }

    private fun nav() = NavigationSurface(session, state, mixer, ctx)

    private fun glow(): ISFFilter {
        val inputs = mutableListOf(ISFInput(NAME = "inputImage", TYPE = "image"))
        return ISFFilter("glow", "glow", ISFHeader(INPUTS = inputs), mockk<Shader>(relaxed = true))
    }

    // --- BackNavigation priorities ---

    @Test
    fun backWithNothingOpenDoesNothing() {
        assertFalse(BackNavigation.back(state, mixer))
    }

    @Test
    fun backPrefersLearnOverPreferencesOverFocusOverRack() {
        deckAChain.slots[0] = glow()
        FxMacroSync.focusSlot(MacroEngine.DECK_A_FX, mixer, 0)
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        PreferencesPanel.open()
        MacroLearnState.startLearn("deckA.k1")

        assertTrue(BackNavigation.back(state, mixer))
        assertFalse(MacroLearnState.isLearning())
        assertTrue(PreferencesPanel.isOpen)

        assertTrue(BackNavigation.back(state, mixer))
        assertFalse(PreferencesPanel.isOpen)
        assertEquals(0, deckAChain.focusedSlot)

        assertTrue(BackNavigation.back(state, mixer))
        assertNull(deckAChain.focusedSlot)
        assertTrue(state.anyRackModuleExpanded())

        assertTrue(BackNavigation.back(state, mixer))
        assertFalse(state.anyRackModuleExpanded())
        assertFalse(BackNavigation.back(state, mixer))
    }

    @Test
    fun backCancelsEveryArmedLearnInOnePress() {
        MacroLearnState.startLearn("deckA.k1")
        llm.slop.liquidlsd.osc.OscLearnState.startLearn("Deck A/zoom")
        state.midiLearnTarget = llm.slop.liquidlsd.midi.MidiLearnTarget.GlobalAction("test")
        PreferencesPanel.open()

        assertTrue(BackNavigation.back(state, mixer))
        assertFalse(MacroLearnState.isLearning())
        assertFalse(llm.slop.liquidlsd.osc.OscLearnState.isLearning())
        assertNull(state.midiLearnTarget)
        assertTrue(PreferencesPanel.isOpen, "the Preferences window waits for the next press")
    }

    @Test
    fun backWithoutAMixerSkipsTheFocusStep() {
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertTrue(BackNavigation.back(state, null))
        assertFalse(state.anyRackModuleExpanded())
    }

    // --- Context detection ---

    @Test
    fun browsingIsTrueOnlyInLibraryFullView() {
        assertFalse(nav().browsing)
        UITheme.libraryMode = UITheme.LibraryMode.FULL
        assertTrue(nav().browsing)
        // Library FULL wins over a still-open Deep Edit module (Edit view is "expanded and not FULL").
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT) // setDisclosure itself drops FULL to HALF
        UITheme.libraryMode = UITheme.LibraryMode.FULL
        assertTrue(nav().browsing)
        UITheme.libraryMode = UITheme.LibraryMode.HALF
        clock += 350
        assertFalse(nav().browsing) // Edit view without a picker list
    }

    @Test
    fun browsingIsTrueInEditViewOnlyWhileAPickerListIsShowing() {
        state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
        clock += 350 // let any picker list another test published expire
        assertFalse(nav().browsing)
        ChainListBrowse.publish(emptyList(), {}, {})
        assertTrue(nav().browsing)
        clock += 350
        assertFalse(nav().browsing)
    }

    // --- Unified pane hosted in the Edit bay ---

    private fun hostPane(applied: MutableList<String>, cleared: MutableList<String>) {
        BrowserPane.enabled = true
        BrowserPane.noteHosting(
            ApplyTarget(
                kind = BrowseKind.SRC, contextKey = "gen/Deck A", defaultScope = BrowseScope.All,
                accepts = { true }, isApplied = { false }, apply = { applied += it.path }, clear = { cleared += "x" }
            )
        )
    }

    private fun unhostPane() {
        BrowserPane.noteHosting(null)
        BrowserPane.enabled = false
        LibraryPanel.activeSelectionSource = null
        PresetListPanel.selectedAsset = null
    }

    @Test
    fun aHostedPaneMakesEditViewBrowsingAndAppliesTheCursorRowOnTap() {
        val applied = mutableListOf<String>()
        try {
            state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
            clock += 350
            assertFalse(nav().browsing)
            hostPane(applied, mutableListOf())
            assertTrue(nav().browsing)
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            PresetListPanel.selectedAsset = AssetItem("stock-source://plasma", "Plasma", AssetType.SOURCE_STOCK)
            nav().browseAccept(false)
            assertEquals(listOf("stock-source://plasma"), applied)
            nav().browseAccept(true) // shift + tap does nothing in the bay
            assertEquals(1, applied.size)
            clock += 350
            assertFalse(nav().browsing)
        } finally {
            unhostPane()
        }
    }

    @Test
    fun hostedPaneButtonsStepPanesAndShiftRightBottomClears() {
        val cleared = mutableListOf<String>()
        try {
            state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
            hostPane(mutableListOf(), cleared)
            nav().button(2, false)
            assertTrue(cleared.isEmpty())
            nav().button(2, true)
            assertEquals(1, cleared.size)
        } finally {
            unhostPane()
        }
    }

    @Test
    fun tappingATreeRowInTheBayMovesTheCursorIntoTheList() {
        try {
            state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
            hostPane(mutableListOf(), mutableListOf())
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.TREE
            nav().browseAccept(false)
            assertEquals(LibraryPanel.SelectionSource.PRESETS, LibraryPanel.activeSelectionSource)
        } finally {
            unhostPane()
        }
    }

    // --- Library open / leave ---

    @Test
    fun rightTopInPerformOpensTheLibraryFull() {
        nav().button(1, false)
        assertEquals(UITheme.LibraryMode.FULL, UITheme.libraryMode)
    }

    @Test
    fun openingTheLibraryFromEditViewCollapsesDeepEditAndCancelsLearn() {
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        MacroLearnState.startLearn("deckA.k1")
        nav().button(1, false)
        assertFalse(state.anyRackModuleExpanded())
        assertFalse(MacroLearnState.isLearning())
        assertEquals(UITheme.LibraryMode.FULL, UITheme.libraryMode)
    }

    @Test
    fun leftTopInLibraryFullLeavesToHalf() {
        UITheme.libraryMode = UITheme.LibraryMode.FULL
        nav().button(0, false)
        assertEquals(UITheme.LibraryMode.HALF, UITheme.libraryMode)
    }

    @Test
    fun leftTopInLibraryFullUndoesBackStackBeforeLeaving() {
        UITheme.libraryMode = UITheme.LibraryMode.FULL
        PreferencesPanel.open()
        nav().button(0, false)
        assertFalse(PreferencesPanel.isOpen)
        assertEquals(UITheme.LibraryMode.FULL, UITheme.libraryMode)
        nav().button(0, false)
        assertEquals(UITheme.LibraryMode.HALF, UITheme.libraryMode)
    }

    private enum class Where { LIBRARY_FULL, LIBRARY_HALF, PICKER, NOTHING }

    private fun arrange(w: Where) {
        UITheme.libraryMode = if (w == Where.LIBRARY_FULL) UITheme.LibraryMode.FULL else UITheme.LibraryMode.HALF
        if (w == Where.PICKER) {
            state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
            ChainListBrowse.publish(emptyList(), {}, {})
        }
        PreferencesPanel.close()
        if (w == Where.LIBRARY_HALF) PreferencesPanel.open()
    }

    private fun snapshot() = Triple(UITheme.libraryMode, PreferencesPanel.isOpen, state.anyRackModuleExpanded())

    @Test
    fun escAndControllerBackProduceTheSameResultInEveryState() {
        for (w in Where.values()) {
            tearDown(); setUp()
            arrange(w)
            nav().button(0, false)
            val viaButton = snapshot()
            tearDown(); setUp()
            arrange(w)
            nav().back()
            assertEquals(viaButton, snapshot(), "state $w")
        }
        // Library FULL with nothing else open: both leave to HALF.
        tearDown(); setUp()
        UITheme.libraryMode = UITheme.LibraryMode.FULL
        assertTrue(nav().back())
        assertEquals(UITheme.LibraryMode.HALF, UITheme.libraryMode)
        // Nothing open: nothing happens.
        assertFalse(nav().back())
    }

    @Test
    fun escapeIsIgnoredWhileATextFieldHasFocus() {
        assertTrue(shouldHandleEscape(wantTextInput = false, escPressed = true))
        assertFalse(shouldHandleEscape(wantTextInput = true, escPressed = true))
        assertFalse(shouldHandleEscape(wantTextInput = false, escPressed = false))
    }

    @Test
    fun shiftedPerformButtonsDoNothing() {
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        nav().button(0, true)
        nav().button(1, true)
        nav().button(2, true)
        assertTrue(state.anyRackModuleExpanded())
        assertEquals(UITheme.LibraryMode.HALF, UITheme.libraryMode)
    }

    @Test
    fun pickerLeftTopIsBackAndShiftedLeftTopIsNot() {
        state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
        ChainListBrowse.publish(emptyList(), {}, {})
        nav().button(0, true)
        assertTrue(state.anyRackModuleExpanded())
        nav().button(0, false)
        assertFalse(state.anyRackModuleExpanded())
    }

    @Test
    fun pickerShiftedRightBottomClearsTheChain() {
        var cleared = 0
        state.openFxChainBrowse(MacroEngine.DECK_A, "Deck A", null)
        ChainListBrowse.publish(emptyList(), {}, { cleared++ })
        nav().button(2, false)
        assertEquals(0, cleared)   // destructive: needs shift
        nav().button(2, true)
        assertEquals(1, cleared)
    }

    @Test
    fun browseStepAndAcceptDriveTheChainListInThePicker() {
        val applied = ArrayList<String>()
        val items = listOf("a", "b").map { AssetItem(path = "/x/$it", name = it, type = AssetType.FX_CHAIN) }
        state.openFxChainBrowse(MacroEngine.DECK_A, "Deck A", null)
        ChainListBrowse.reset()
        ChainListBrowse.publish(items, { applied += it.name }, {})
        val n = nav()
        n.browseAccept(false)
        assertTrue(applied.isEmpty())          // stepping never applies; no cursor yet
        n.browseStep(2)
        n.browseAccept(true)                   // shifted accept does nothing in a picker
        assertTrue(applied.isEmpty())
        n.browseAccept(false)
        assertEquals(listOf("b"), applied)
    }

    // --- Last-touched-knob picker routing ---

    @Test
    fun pickerButtonWithNoTouchedKnobDoesNothing() {
        nav().button(2, false)
        assertFalse(state.anyRackModuleExpanded())
    }

    @Test
    fun pickerOpensTheSourceListOfALastTouchedSrcRow() {
        PerformSurface.lastTouchedKnob = 5 // Deck B row, SRC
        nav().button(2, false)
        assertEquals(ParametersState.BrowseTarget.Gen, state.browseTargetFor(MacroEngine.DECK_B))
        assertEquals(ParametersState.SectionMode.BROWSE, state.rackSectionMode)
        assertEquals("SRC", state.activeDeckBSubTab)
    }

    @Test
    fun pickerOpensTheSlotUnderAnFxRowKnobAndTheChainListForKnobOne() {
        state.setDeckSubTab("Deck B", "FX")
        every { mixer.deckB.fxChain } returns FxChain("Deck B FX")
        PerformSurface.lastTouchedKnob = 6 // col 2 = slot index 1
        nav().button(2, false)
        assertEquals(ParametersState.BrowseTarget.FxChain(1), state.browseTargetFor(MacroEngine.DECK_B))
        assertEquals("FX", state.activeDeckBSubTab)

        state.rackModuleDisclosure.clear()
        PerformSurface.lastTouchedKnob = 4 // col 0 in group mode = the chain list
        nav().button(2, false)
        assertEquals(ParametersState.BrowseTarget.FxChain(null), state.browseTargetFor(MacroEngine.DECK_B))
    }

    @Test
    fun pickerOpensTheTransitionListForTheTransitionsRow() {
        UITheme.performancePageId = "master"
        PerformSurface.lastTouchedKnob = 4 // Transitions row
        nav().button(2, false)
        assertEquals(ParametersState.BrowseTarget.Transition, state.browseTargetFor(MacroEngine.MASTER))
        assertEquals("TRANS", state.activeMixerSubTab)
    }

    @Test
    fun pickerIgnoresRowsWithoutAPicker() {
        UITheme.performancePageId = "master"
        PerformSurface.lastTouchedKnob = 12 // Clock & Global
        nav().button(2, false)
        assertFalse(state.anyRackModuleExpanded())
    }

    @Test
    fun turningAKnobRecordsItAsLastTouched() {
        val surface = PerformSurface(UITheme, ctx, state, mixer)
        surface.turn(7, 0.1f)
        assertEquals(7, PerformSurface.lastTouchedKnob)
        surface.primary(3)
        assertEquals(3, PerformSurface.lastTouchedKnob)
    }

    // --- dirty-deck modal ---

    private class FakePrompt(var pending: Boolean = true) : DeckConfirmPrompt {
        val answers = mutableListOf<DeckConfirmChoice>()
        override val deckConfirmPending get() = pending
        override fun answerDeckConfirm(choice: DeckConfirmChoice) { answers += choice }
    }

    private fun confirmNav(prompt: FakePrompt) = NavigationSurface(session, state, mixer, ctx, prompt)

    @Test
    fun deckConfirmModalTakesOverButtonsKnobAndBack() {
        val prompt = FakePrompt()
        val n = confirmNav(prompt)
        assertTrue(n.browsing)
        n.button(0, false)
        n.button(1, false)
        n.button(2, false)
        n.browseAccept(false)
        n.browseAccept(true)
        n.browseStep(3) // ignored
        assertTrue(n.back())
        assertEquals(
            listOf(
                DeckConfirmChoice.CANCEL, DeckConfirmChoice.SAVE, DeckConfirmChoice.DISCARD,
                DeckConfirmChoice.SAVE, DeckConfirmChoice.DISCARD, DeckConfirmChoice.CANCEL
            ),
            prompt.answers
        )
    }

    @Test
    fun deckConfirmIgnoresShiftedButtonsAndStepsAside() {
        val prompt = FakePrompt()
        confirmNav(prompt).button(1, true)
        assertTrue(prompt.answers.isEmpty())
        prompt.pending = false
        assertFalse(confirmNav(prompt).browsing)
        confirmNav(prompt).button(1, false) // normal Perform context again: opens the Library
        assertTrue(prompt.answers.isEmpty())
    }
}
