package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.midi.ParameterCellId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.ExternalVideoSource
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

        PresetManager.setActive(DeckSlot.A, "test_preset_a", null)
    }

    @Test
    fun testExternalVideoDeckCannotBeSaved() {
        val extSource = mockk<ExternalVideoSource>(relaxed = true)
        every { deckA.source } returns extSource

        val initialActiveA = PresetManager.activePresetA
        controller.handleSaveDeck(mixer, deckA, isDeckA = true, isSaveAs = false)
        controller.saveDeckPreset(mixer, "ForbiddenExternalPreset", deckA, isDeckA = true)

        assertEquals(initialActiveA, PresetManager.activePresetA, "Active preset must not be updated for ExternalVideoSource")
        verify(exactly = 0) { popupManager.requestDeckConfirm(any(), any(), any()) }
    }

    @Test
    fun testExternalVideoDeckNeverDirty() {
        val extSource = mockk<ExternalVideoSource>(relaxed = true)
        every { deckA.source } returns extSource

        // Even with a cached DTO present, ExternalVideoSource should never report dirty
        PresetManager.setActive(DeckSlot.A, PresetManager.activePresetA, mockk(relaxed = true))
        assertFalse(session.presetManager.isDeckDirty(deckA, mixer), "ExternalVideoSource deck must never be dirty")
    }

}

class FreePresetNameTest {
    @Test
    fun keepsTheBaseNameWhenFreeAndNeverReturnsAnExistingOne() {
        val dir = java.nio.file.Files.createTempDirectory("presets").toFile()
        try {
            kotlin.test.assertEquals("Untitled_DeckA", DeckPresetController.freePresetName("Untitled_DeckA", dir))
            java.io.File(dir, "Untitled_DeckA.lsd").writeText("{}")
            kotlin.test.assertEquals("Untitled_DeckA_2", DeckPresetController.freePresetName("Untitled_DeckA", dir))
            java.io.File(dir, "Untitled_DeckA_2.lsd").writeText("{}")
            kotlin.test.assertEquals("Untitled_DeckA_3", DeckPresetController.freePresetName("Untitled_DeckA", dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
