package llm.slop.liquidlsd.ui

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckPresetControllerTest {

    private lateinit var session: SessionContext
    private lateinit var popupManager: PopupManager
    private lateinit var controller: DeckPresetController
    private lateinit var mixer: Mixer
    private lateinit var deckA: Deck

    @BeforeTest
    fun setup() {
        session = SessionContext()
        popupManager = mockk(relaxed = true)
        controller = DeckPresetController(session, popupManager)
        mixer = mockk(relaxed = true)
        deckA = mockk(relaxed = true)
        every { mixer.deckA } returns deckA

        PresetManager.activePresetA = "test_preset_a"
        PresetManager.cachedDtoA = null
    }

    @Test
    fun testCleanDeckExecutesImmediately() {
        var actionExecuted = false
        controller.guardDeckTransition(mixer, deckA) {
            actionExecuted = true
        }

        assertTrue(actionExecuted, "Clean deck transition must execute immediately")
        verify(exactly = 0) { popupManager.requestDeckConfirm(any(), any(), any()) }
    }

    @Test
    fun testDirtyDeckAutoSaveExecutesImmediately() {
        session.uiTheme.autoVjDirtyBehavior = UITheme.AutoVjDirtyBehavior.AUTO_SAVE
        
        val cachedDto = DeckPresetDto(
            name = "old_name",
            visualSourceType = "mandala",
            parameters = emptyMap(),
            feedbackParameters = emptyMap()
        )
        PresetManager.cachedDtoA = cachedDto

        var actionExecuted = false
        controller.guardDeckTransition(mixer, deckA) {
            actionExecuted = true
        }

        assertTrue(actionExecuted, "AUTO_SAVE must execute transition callback immediately")
        verify(exactly = 0) { popupManager.requestDeckConfirm(any(), any(), any()) }
    }

    @Test
    fun testDirtyDeckAutoDiscardExecutesImmediately() {
        session.uiTheme.autoVjDirtyBehavior = UITheme.AutoVjDirtyBehavior.AUTO_DISCARD
        
        val cachedDto = DeckPresetDto(
            name = "old_name",
            visualSourceType = "mandala",
            parameters = emptyMap(),
            feedbackParameters = emptyMap()
        )
        PresetManager.cachedDtoA = cachedDto

        var actionExecuted = false
        controller.guardDeckTransition(mixer, deckA) {
            actionExecuted = true
        }

        assertTrue(actionExecuted, "AUTO_DISCARD must execute transition callback immediately")
        verify(exactly = 0) { popupManager.requestDeckConfirm(any(), any(), any()) }
    }

    @Test
    fun testDirtyDeckSkipPromptsUser() {
        session.uiTheme.autoVjDirtyBehavior = UITheme.AutoVjDirtyBehavior.SKIP
        
        val cachedDto = DeckPresetDto(
            name = "old_name",
            visualSourceType = "mandala",
            parameters = emptyMap(),
            feedbackParameters = emptyMap()
        )
        PresetManager.cachedDtoA = cachedDto

        var actionExecuted = false
        controller.guardDeckTransition(mixer, deckA) {
            actionExecuted = true
        }

        assertFalse(actionExecuted, "SKIP must not execute transition immediately; it must prompt user first")
        verify(exactly = 1) { popupManager.requestDeckConfirm(deckA, "Deck A", any()) }
    }

    @Test
    fun testChangeVisualSourcePromptsWhenPresetActive() {
        val state = PresetGridState()
        state.selectedCell = PresetCellId("Deck A/Geometry/L1", "lfo")
        state.selectedParam = mockk(relaxed = true)

        val oldSource = mockk<llm.slop.liquidlsd.rendering.VisualSource>(relaxed = true)
        every { oldSource.displayName } returns "OldSource"
        every { deckA.source } returns oldSource

        val newSource = mockk<llm.slop.liquidlsd.rendering.VisualSource>(relaxed = true)
        val newSourceClone = mockk<llm.slop.liquidlsd.rendering.VisualSource>(relaxed = true)
        every { newSource.displayName } returns "NewSource"
        every { newSource.clone() } returns newSourceClone

        PresetManager.activePresetA = "MyCoolPreset"
        PresetManager.cachedDtoA = mockk(relaxed = true)

        var confirmCallback: (() -> Unit)? = null
        every { popupManager.requestSourceChangeConfirm(deckA, "Deck A", "OldSource", "NewSource", any()) } answers {
            confirmCallback = lastArg()
        }

        controller.changeVisualSourceSafely(mixer, deckA, "Deck A", newSource, state)

        verify(exactly = 1) { popupManager.requestSourceChangeConfirm(deckA, "Deck A", "OldSource", "NewSource", any()) }
        
        // Before confirm is called, deck and preset remain unchanged
        assertEquals("MyCoolPreset", PresetManager.activePresetA)
        assertFalse(state.selectedCell == null)

        // Invoke confirmation
        confirmCallback!!.invoke()

        verify { deckA.source = newSourceClone }
        verify { deckA.isEmpty = false }
        assertFalse(PresetManager.activePresetA != null, "Active preset must be cleared after source switch")
        assertTrue(state.selectedCell == null, "Selected cell must be cleared after source switch")
        assertEquals("SRC", state.activeDeckASubTab, "Active sub-tab must update to SRC")
    }

    @Test
    fun testChangeVisualSourceDirectWhenCleanAndNoActivePreset() {
        val state = PresetGridState()
        val oldSource = mockk<llm.slop.liquidlsd.rendering.VisualSource>(relaxed = true)
        every { oldSource.displayName } returns "OldSource"
        every { deckA.source } returns oldSource

        val newSource = mockk<llm.slop.liquidlsd.rendering.VisualSource>(relaxed = true)
        val newSourceClone = mockk<llm.slop.liquidlsd.rendering.VisualSource>(relaxed = true)
        every { newSource.displayName } returns "NewSource"
        every { newSource.clone() } returns newSourceClone

        PresetManager.activePresetA = null
        PresetManager.cachedDtoA = null

        controller.changeVisualSourceSafely(mixer, deckA, "Deck A", newSource, state)

        verify(exactly = 0) { popupManager.requestSourceChangeConfirm(any(), any(), any(), any(), any()) }
        verify { deckA.source = newSourceClone }
        assertEquals("SRC", state.activeDeckASubTab)
    }
}
