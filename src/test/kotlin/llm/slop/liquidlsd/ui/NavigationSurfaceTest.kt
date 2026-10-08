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
import llm.slop.liquidlsd.ui.browser.BrowserDock
import llm.slop.liquidlsd.ui.browser.BrowserPane
import llm.slop.liquidlsd.control.SendTarget
import llm.slop.liquidlsd.ui.browser.FXBrowserPanel
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
        MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.GLOBAL
    ) + FxMacroSync.FX_BANK_IDS
    private var savedPage = ""
    private var savedMode = UITheme.LibraryMode.HALF
    private var savedViewMode = LibraryPanel.viewMode
    private var savedExpanded: Map<String, String> = emptyMap()

    @BeforeTest
    fun setUp() {
        session = SessionContext()
        savedPage = UITheme.performancePageId
        savedMode = UITheme.libraryMode
        savedViewMode = LibraryPanel.viewMode
        savedExpanded = UITheme.rackExpandedModules
        UITheme.performancePageId = "ab"
        UITheme.libraryMode = UITheme.LibraryMode.HALF
        state.rackModuleDisclosure.clear()
        nav() // syncs the surface's pair sampling to this fresh session (it keeps state across instances)
        for (id in bankIds) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
        every { mixer.deckA.fxChain } returns deckAChain
        PreferencesPanel.close()
        MacroLearnState.cancelLearn()
        PerformSurface.lastTouchedKnob = null
    }

    @AfterTest
    fun tearDown() {
        MacroLearnState.cancelLearn()
        PreferencesPanel.close()
        PerformSurface.lastTouchedKnob = null
        UITheme.performancePageId = savedPage
        UITheme.libraryMode = savedMode
        LibraryNavigation.setViewMode(savedViewMode)
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
        state.openParams(MacroEngine.DECK_A)
        assertFalse(nav().browsing) // Edit view without a picker list
    }

    @Test
    fun sendKnobsAreLiveOnlyForTheListCursorInLibraryFullOrThePicker() {
        val saved = LibraryPanel.viewMode
        val savedSource = LibraryPanel.activeSelectionSource
        val savedPreset = PresetListPanel.selectedAsset
        val savedFx = FXBrowserPanel.selectedAsset
        try {
            LibraryPanel.viewMode = LibraryPanel.LibraryViewMode.PRESETS
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            PresetListPanel.selectedAsset = AssetItem("stock-source://plasma", "Plasma", AssetType.SOURCE_STOCK)
            assertTrue(nav().sendTargets.isEmpty()) // not in Library FULL
            UITheme.libraryMode = UITheme.LibraryMode.FULL
            assertEquals(setOf(SendTarget.A, SendTarget.B, SendTarget.BG, SendTarget.PV), nav().sendTargets) // a source never goes to master
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.TREE
            assertTrue(nav().sendTargets.isEmpty())
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            LibraryPanel.viewMode = LibraryPanel.LibraryViewMode.FX
            FXBrowserPanel.selectedAsset = AssetItem("stock-fx://glow", "Glow", AssetType.FX_STOCK)
            assertEquals(SendTarget.entries.toSet(), nav().sendTargets)
            LibraryPanel.viewMode = LibraryPanel.LibraryViewMode.TRANS
            assertTrue(nav().sendTargets.isEmpty())
        } finally {
            LibraryPanel.viewMode = saved
            LibraryPanel.activeSelectionSource = savedSource
            PresetListPanel.selectedAsset = savedPreset
            FXBrowserPanel.selectedAsset = savedFx
        }
    }

    @Test
    fun sendingFromLibraryFullLoadsWithoutLeavingTheLibrary() {
        val saved = LibraryPanel.viewMode
        val savedSource = LibraryPanel.activeSelectionSource
        val savedFx = FXBrowserPanel.selectedAsset
        try {
            UITheme.libraryMode = UITheme.LibraryMode.FULL
            LibraryPanel.viewMode = LibraryPanel.LibraryViewMode.FX
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            FXBrowserPanel.selectedAsset = AssetItem("stock-fx://glow", "Glow", AssetType.FX_STOCK)
            every { mixer.masterFxChain } returns FxChain("Master FX")
            nav().browseSend(SendTarget.MASTER)
            assertFalse(state.anyRackModuleExpanded())
            assertEquals(UITheme.LibraryMode.FULL, UITheme.libraryMode)
        } finally {
            LibraryPanel.viewMode = saved
            LibraryPanel.activeSelectionSource = savedSource
            FXBrowserPanel.selectedAsset = savedFx
        }
    }

    @Test
    fun sendingFromThePickerRetargetsTheBayToTheTargetRow() {
        val saved = LibraryPanel.viewMode
        val savedSource = LibraryPanel.activeSelectionSource
        val savedFx = FXBrowserPanel.selectedAsset
        try {
            state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
            hostPane(mutableListOf(), mutableListOf())
            LibraryPanel.viewMode = LibraryPanel.LibraryViewMode.FX
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            FXBrowserPanel.selectedAsset = AssetItem("stock-fx://glow", "Glow", AssetType.FX_STOCK)
            every { mixer.masterFxChain } returns FxChain("Master FX")
            nav().browseSend(SendTarget.MASTER)
            assertEquals(PerfRows.PAIR_MASTER, state.focusedPair)
            assertEquals("FX", state.activeMixerSubTab)
        } finally {
            unhostPane()
            LibraryPanel.viewMode = saved
            LibraryPanel.activeSelectionSource = savedSource
            FXBrowserPanel.selectedAsset = savedFx
        }
    }

    // --- Unified pane hosted in the Edit bay ---

    private fun hostPane(applied: MutableList<String>, cleared: MutableList<String>) {
        BrowserPane.noteHosting(
            ApplyTarget(
                kind = BrowseKind.SRC, contextKey = "gen/Deck A", defaultScope = BrowseScope.All,
                accepts = { true }, isApplied = { false }, apply = { applied += it.path }, clear = { cleared += "x" }
            )
        )
    }

    private fun unhostPane() {
        BrowserPane.noteHosting(null)
        LibraryPanel.activeSelectionSource = null
        PresetListPanel.selectedAsset = null
    }

    @Test
    fun backDropsTheDockSelectionBeforeAnythingElse() {
        state.selectGen(MacroEngine.DECK_A, "Deck A")
        assertTrue(nav().back())
        assertEquals(null, state.dockSelection)
        assertFalse(nav().back())
    }

    @Test
    fun leavingThePairViewDropsTheDockSelection() {
        state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
        assertTrue(nav().back()) // one back leaves the pair view and drops the selection
        assertEquals(null, state.focusedPair)
        assertFalse(state.anyRackModuleExpanded())
        assertEquals(null, state.dockSelection)
    }

    @Test
    fun aHostedPaneMakesEditViewBrowsingAndAppliesTheCursorRowOnTap() {
        val applied = mutableListOf<String>()
        try {
            state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
            hostPane(applied, mutableListOf())
            assertTrue(nav().browsing)
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            PresetListPanel.selectedAsset = AssetItem("stock-source://plasma", "Plasma", AssetType.SOURCE_STOCK)
            nav().browseAccept(false)
            assertEquals(listOf("stock-source://plasma"), applied)
            nav().browseAccept(true) // shift + tap does nothing in the bay
            assertEquals(1, applied.size)
            state.openParams(MacroEngine.DECK_A)
            assertFalse(nav().browsing)
        } finally {
            unhostPane()
        }
    }

    @Test
    fun browseStepMovesTheHostedPaneCursor() {
        val a = AssetItem("/x/a.lsdpreset", "a", AssetType.PRESET)
        val b = AssetItem("/x/b.lsdpreset", "b", AssetType.PRESET)
        try {
            state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
            hostPane(mutableListOf(), mutableListOf())
            PresetListPanel.filteredPresets = listOf(a, b)
            PresetListPanel.selectedAsset = a
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            nav().browseStep(1)
            assertEquals(b, PresetListPanel.selectedAsset)
            assertEquals(1f, nav().browsePosition) // last row of two
            nav().browseStep(-1)
            assertEquals(a, PresetListPanel.selectedAsset)
            assertEquals(0f, nav().browsePosition)
        } finally {
            PresetListPanel.filteredPresets = emptyList()
            unhostPane()
        }
    }

    @Test
    fun browseStepMovesTreeCursorWhenActiveSelectionSourceIsTree() {
        try {
            state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
            hostPane(mutableListOf(), mutableListOf())
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.TREE
            val initial = BrowserPane.treeCursorOf(BrowseKind.SRC)
            assertEquals(BrowseScope.All, initial)
            nav().browseStep(1)
            val stepped = BrowserPane.treeCursorOf(BrowseKind.SRC)
            assertEquals(BrowseScope.Favorites, stepped)
            nav().browseStep(-1)
            assertEquals(BrowseScope.All, BrowserPane.treeCursorOf(BrowseKind.SRC))
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
            hostPane(mutableListOf(), mutableListOf())
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
    fun shiftedPerformButtonsTogglesEditModeAndPairFocus() {
        assertFalse(state.anyRackModuleExpanded())
        nav().button(0, true) // Shift + Left-Top: toggle Edit mode
        assertTrue(state.anyRackModuleExpanded())
        nav().button(0, true) // Shift + Left-Top again: collapse Edit mode
        assertFalse(state.anyRackModuleExpanded())

        nav().button(1, true) // Shift + Right-Middle opens the Library on the FX tab
        assertEquals(UITheme.LibraryMode.FULL, UITheme.libraryMode)
        assertEquals(LibraryPanel.LibraryViewMode.FX, LibraryPanel.viewMode)
        nav().button(0, false)
        assertEquals(UITheme.LibraryMode.HALF, UITheme.libraryMode)

        assertNull(state.focusedPair)
        nav().button(2, true) // No knob touched yet: nothing to focus
        assertNull(state.focusedPair)
        PerformSurface.lastTouchedKnob = 0 // Deck A SRC row
        nav().button(2, true) // Shift + Right-Bottom: focus Pair view
        assertEquals("A", state.focusedPair)
        nav().button(0, false) // Left-Top (Back) in Pair view: leaves Pair focus
        assertNull(state.focusedPair)
    }

    @Test
    fun pickerLeftTopIsBackAndShiftedLeftTopIsNot() {
        try {
            state.openGenBrowse(MacroEngine.DECK_A, "Deck A")
            hostPane(mutableListOf(), mutableListOf())
            nav().button(0, true)
            assertEquals("A", state.focusedPair)
            nav().button(0, false)
            assertEquals(null, state.focusedPair)
        } finally {
            unhostPane()
        }
    }

    // --- Last-touched-knob picker routing ---

    @Test
    fun pickerButtonWithNoTouchedKnobDoesNothing() {
        nav().button(2, false)
        assertFalse(state.anyRackModuleExpanded())
    }

    @Test
    fun pickerOpensTheSourceListOfALastTouchedSrcRow() {
        PerformSurface.lastTouchedKnob = 9 // Deck B SRC row (row 3 of the A/B page)
        nav().button(2, false)
        assertEquals(ParametersState.BrowseTarget.Gen, state.dockSelection?.target)
        assertEquals("B", state.focusedPair)
        assertEquals("SRC", state.activeDeckBSubTab)
    }

    @Test
    fun pickerOpensTheSlotUnderAnFxRowKnobAndTheChainListForKnobOne() {
        every { mixer.deckB.fxChain } returns FxChain("Deck B FX")
        PerformSurface.lastTouchedKnob = 14 // Deck B's FX row (row 3 of the A/B page), col 2 = slot index 1
        nav().button(2, false)
        assertEquals(ParametersState.BrowseTarget.FxChain(1), state.dockSelection?.target)
        assertEquals("FX", state.activeDeckBSubTab)

        state.leavePair()
        PerformSurface.lastTouchedKnob = 12 // col 0 in group mode = the chain list
        nav().button(2, false)
        assertEquals(ParametersState.BrowseTarget.FxChain(null), state.dockSelection?.target)
    }

    @Test
    fun pickerOpensTheTransitionListForTheTransitionsRow() {
        UITheme.performancePageId = "mixer"
        PerformSurface.lastTouchedKnob = 8 // Transitions row (row 3 of MIXER)
        nav().button(2, false)
        assertEquals(ParametersState.BrowseTarget.Transition, state.dockSelection?.target)
        assertEquals("TRANS", state.activeMixerSubTab)
    }

    @Test
    fun pickerIgnoresRowsWithoutAPicker() {
        UITheme.performancePageId = "mixer"
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
