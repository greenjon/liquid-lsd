package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLearnState
import java.io.File
import kotlin.test.*

/**
 * Covers the Modular Rack disclosure state machine in [ParametersState] (see
 * docs/user_guide/macros_and_rack.md): Solo-mode auto-collapse and the Learn-mode pinning
 * exception that keeps a module's Deep Edit open while one of its own macro knobs is armed for
 * [MacroLearnState] Learn.
 *
 * Note: [ParametersState.setDisclosure] and [ParametersState.collapseAllRackModules] take no
 * Mixer/FxBank parameter at all -- that is the compile-time guarantee behind the "accordion
 * disclosure never triggers FX bank refocus or re-runs FxMacroSync" rule, so it isn't re-verified
 * here as a runtime assertion.
 */
class RackDisclosureTest {

    // setDisclosure/collapseAllRackModules persist to the real lsd-preferences.properties file
    // (same pattern as UIThemeTest.testPreferencesSaveAndLoadRoundTrip) -- back it up and restore
    // it so running this suite doesn't clobber the developer's actual saved preferences.
    private val preferencesFile = File("lsd-preferences.properties")
    private val backupFile = File("lsd-preferences.properties.rackdisclosuretest.bak")
    private var hadBackup = false

    @BeforeTest
    fun setUp() {
        hadBackup = preferencesFile.exists()
        if (hadBackup) {
            preferencesFile.copyTo(backupFile, overwrite = true)
        }
        MacroLearnState.cancelLearn()
        MacroLearnState.clearStatus()
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
        MacroEngine.registerBank(MacroEngine.DECK_B, MacroBank())
        MacroEngine.registerBank(MacroEngine.FX_BANK_1, MacroBank())
        MacroEngine.registerBank(MacroEngine.FX_BANK_2, MacroBank())
        // UITheme is a singleton, so its in-memory rackExpandedModules survives across tests in
        // the same JVM -- reset it so each test's ParametersState() starts from a clean slate.
        UITheme.rackExpandedModules = emptyMap()
    }

    @AfterTest
    fun tearDown() {
        MacroLearnState.cancelLearn()
        MacroLearnState.clearStatus()
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
        MacroEngine.registerBank(MacroEngine.DECK_B, MacroBank())
        MacroEngine.registerBank(MacroEngine.FX_BANK_1, MacroBank())
        MacroEngine.registerBank(MacroEngine.FX_BANK_2, MacroBank())
        if (hadBackup) {
            backupFile.copyTo(preferencesFile, overwrite = true)
            backupFile.delete()
        } else {
            preferencesFile.delete()
        }
    }

    @Test
    fun soloModeCollapsesNonPinnedSiblingWhenAnotherModuleExpands() {
        val state = ParametersState()
        state.rackSoloMode = true

        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        state.setDisclosure(MacroEngine.DECK_B, ParametersState.DisclosureLevel.DEEP_EDIT)

        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))
    }

    @Test
    fun multiModeLeavesSiblingsExpanded() {
        val state = ParametersState()
        state.rackSoloMode = false

        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        state.setDisclosure(MacroEngine.DECK_B, ParametersState.DisclosureLevel.DEEP_EDIT)

        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))
    }

    @Test
    fun armedLearnOnAModulesOwnKnobPinsItOpenThroughSoloModeCollapse() {
        val state = ParametersState()
        state.rackSoloMode = true
        val deckAKnob = MacroEngine.getBank(MacroEngine.DECK_A)!!.knobs[0]
        MacroLearnState.startLearn(deckAKnob.id)

        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_A))

        // Expanding a sibling module in solo mode would normally collapse Deck A too -- but
        // Deck A is Learn-pinned (one of its own knobs is armed), so it must stay open.
        state.setDisclosure(MacroEngine.DECK_B, ParametersState.DisclosureLevel.DEEP_EDIT)

        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))
    }

    @Test
    fun collapseAllRackModulesLeavesALearnPinnedModuleOpen() {
        val state = ParametersState()
        val deckAKnob = MacroEngine.getBank(MacroEngine.DECK_A)!!.knobs[0]
        MacroLearnState.startLearn(deckAKnob.id)
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        state.setDisclosure(MacroEngine.DECK_B, ParametersState.DisclosureLevel.DEEP_EDIT)

        state.collapseAllRackModules()

        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_B))
    }

    @Test
    fun collapseAllRackModulesCollapsesEverythingWhenNoLearnIsArmed() {
        val state = ParametersState()
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        state.setDisclosure(MacroEngine.DECK_B, ParametersState.DisclosureLevel.DEEP_EDIT)

        state.collapseAllRackModules()

        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_B))
        assertFalse(state.anyRackModuleExpanded())
    }

    @Test
    fun fxCompositeModuleIsLearnPinnedWhenArmedKnobBelongsToAnyOfItsBanks() {
        val state = ParametersState()
        val fx2Knob = MacroEngine.getBank(MacroEngine.FX_BANK_2)!!.knobs[0]
        MacroLearnState.startLearn(fx2Knob.id)

        assertTrue(state.isLearnPinned("FX"))
    }

    @Test
    fun fxCompositeModuleStaysPinnedAcrossBankRefocus() {
        // The "FX" moduleId is a stable identity decoupled from whichever bank (FX1/FX2/MFX) is
        // currently focused -- Learn armed against FX1 still pins the "FX" module even though the
        // caller's own "focused bank" concept (owned by PerformanceMatrixPanel, not this state)
        // might display FX2 at the moment.
        val state = ParametersState()
        state.rackSoloMode = true
        val fx1Knob = MacroEngine.getBank(MacroEngine.FX_BANK_1)!!.knobs[0]
        MacroLearnState.startLearn(fx1Knob.id)

        state.setDisclosure("FX", ParametersState.DisclosureLevel.DEEP_EDIT)
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)

        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor("FX"))
    }

    @Test
    fun expandedModulesSurviveAcrossParametersStateInstancesViaPersistedPrefs() {
        val first = ParametersState()
        first.rackSoloMode = false
        first.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)

        // A fresh ParametersState (standing in for "app restarted") should pick up the persisted
        // disclosure from UITheme.rackExpandedModules, which setDisclosure just wrote.
        val second = ParametersState()
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, second.disclosureFor(MacroEngine.DECK_A))
    }

    @Test
    fun collapsedModulesAreNotPersisted() {
        val state = ParametersState()
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.COLLAPSED)

        assertFalse(UITheme.rackExpandedModules.containsKey(MacroEngine.DECK_A))
    }

    @Test
    fun mixerSubTabsSupportCenteredFx() {
        val state = ParametersState()
        state.activeTopTab = "Mixer"
        assertEquals("CTRL", state.activeMixerSubTab)

        state.setDeckSubTab("Mixer", "FX")
        assertEquals("FX", state.activeMixerSubTab)
        assertEquals("FX", state.getActiveSubTab("Mixer"))

        state.setDeckSubTab("Mixer", "TRANS")
        assertEquals("TRANS", state.activeMixerSubTab)
        assertEquals("TRANS", state.getActiveSubTab("Mixer"))
    }

    @Test
    fun deckSubTabsSupportCenteredFx() {
        val state = ParametersState()
        state.activeTopTab = "Deck A"
        assertEquals("SRC", state.activeDeckASubTab)

        state.setDeckSubTab("Deck A", "FX")
        assertEquals("FX", state.activeDeckASubTab)
        assertEquals("FX", state.getActiveDeckSubTabByTag("A"))
    }

    @Test
    fun deckSubTabsMergeViewIntoSrc() {
        val tabs = ParametersTabs.getDeckSubTabs(isEmpty = false)
        assertEquals(listOf("SRC", "FX"), tabs)
        assertFalse(tabs.contains("View"))
    }

    @Test
    fun deepEditSideRailNavigationSwitchesSectionInSoloMode() {
        val state = ParametersState()
        state.rackSoloMode = true

        // User expands Deck A
        state.activeTopTab = "Deck A"
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_A))

        // Clicking MIX on side rail collapses Deck A and expands Mixer
        state.activeTopTab = "Mixer"
        state.setDisclosure(MacroEngine.MASTER, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.MASTER))

        // Clicking Deck B collapses Mixer and expands Deck B
        state.activeTopTab = "Deck B"
        state.setDisclosure(MacroEngine.DECK_B, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.MASTER))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))
    }

    @Test
    fun confidenceMonitorClickOpensDeepEditInSoloMode() {
        val state = ParametersState()
        state.rackSoloMode = true

        // User clicks Deck A monitor in Column 3
        state.activeTopTab = "Deck A"
        state.setDisclosure(MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_A))

        // User clicks Deck B monitor: in Solo mode, Deck A collapses and Deck B expands
        state.activeTopTab = "Deck B"
        state.setDisclosure(MacroEngine.DECK_B, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_A))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.DECK_B))

        // User clicks Main Output monitor: Deck B collapses and Master expands
        state.activeTopTab = "Mixer"
        state.setDisclosure(MacroEngine.MASTER, ParametersState.DisclosureLevel.DEEP_EDIT)
        assertEquals(ParametersState.DisclosureLevel.COLLAPSED, state.disclosureFor(MacroEngine.DECK_B))
        assertEquals(ParametersState.DisclosureLevel.DEEP_EDIT, state.disclosureFor(MacroEngine.MASTER))
    }
}
