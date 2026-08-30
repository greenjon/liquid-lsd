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
}
