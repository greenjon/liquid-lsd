package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiKey
import io.mockk.mockk
import io.mockk.verify
import io.mockk.every
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import org.lwjgl.glfw.GLFW.GLFW_KEY_S
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PresetGridKeyboardTest {

    private lateinit var mixer: Mixer
    private lateinit var deckA: Deck
    private lateinit var deckB: Deck
    private lateinit var deckPresetController: DeckPresetController
    @BeforeTest
    fun setup() {
        ImGui.createContext()
        ImGui.getIO().fonts.build()

        mixer = mockk(relaxed = true)
        deckA = mockk(relaxed = true)
        deckB = mockk(relaxed = true)

        every { mixer.deckA } returns deckA
        every { mixer.deckB } returns deckB
        every { deckA.isEmpty } returns false
        every { deckB.isEmpty } returns false

        deckPresetController = mockk(relaxed = true)
    }

    @AfterTest
    fun teardown() {
        try {
            ImGui.destroyContext()
        } catch (ignored: Throwable) {}
    }

    private fun simulateFrameWithKeys(key: Int, ctrl: Boolean, shift: Boolean, block: () -> Unit) {
        val io = ImGui.getIO()
        io.setDisplaySize(800f, 600f)
        io.setDeltaTime(1f / 60f)
        io.keyCtrl = ctrl
        io.keyShift = shift
        io.setKeysDown(key, true)
        ImGui.newFrame()

        try {
            block()
        } finally {
            ImGui.render()
            io.setKeysDown(key, false)
            io.keyCtrl = false
            io.keyShift = false
        }
    }

    @Test
    fun testCtrlSSavesActiveDeckPreset() {
        val state = PresetGridState()
        state.activeTopTab = "Deck A"

        simulateFrameWithKeys(GLFW_KEY_S, ctrl = true, shift = false) {
            PresetGridKeyboard.handleKeyboardShortcuts(
                state = state,
                mixer = mixer,
                deckPresetController = deckPresetController,
                onPushUndo = { _, _ -> },
                onPerformUndo = { _, _ -> }
            )
        }

        verify(exactly = 1) {
            deckPresetController.handleSaveDeck(mixer, deckA, isDeckA = true, isSaveAs = false)
        }
    }

    @Test
    fun testShiftCtrlSCallsSaveAsForActiveDeck() {
        val state = PresetGridState()
        state.activeTopTab = "Deck B"

        simulateFrameWithKeys(GLFW_KEY_S, ctrl = true, shift = true) {
            PresetGridKeyboard.handleKeyboardShortcuts(
                state = state,
                mixer = mixer,
                deckPresetController = deckPresetController,
                onPushUndo = { _, _ -> },
                onPerformUndo = { _, _ -> }
            )
        }

        verify(exactly = 1) {
            deckPresetController.handleSaveDeck(mixer, deckB, isDeckA = false, isSaveAs = true)
        }
    }

    @Test
    fun testCtrlSIgnoredWhenMixerIsActive() {
        val state = PresetGridState()
        state.activeTopTab = "Mixer"

        simulateFrameWithKeys(GLFW_KEY_S, ctrl = true, shift = false) {
            PresetGridKeyboard.handleKeyboardShortcuts(
                state = state,
                mixer = mixer,
                deckPresetController = deckPresetController,
                onPushUndo = { _, _ -> },
                onPerformUndo = { _, _ -> }
            )
        }

        verify(exactly = 0) {
            deckPresetController.handleSaveDeck(any(), any(), any(), any())
        }
    }

    @Test
    fun testCtrlSIgnoredWhenDeckIsEmpty() {
        val state = PresetGridState()
        state.activeTopTab = "Deck A"
        every { deckA.isEmpty } returns true

        simulateFrameWithKeys(GLFW_KEY_S, ctrl = true, shift = false) {
            PresetGridKeyboard.handleKeyboardShortcuts(
                state = state,
                mixer = mixer,
                deckPresetController = deckPresetController,
                onPushUndo = { _, _ -> },
                onPerformUndo = { _, _ -> }
            )
        }

        verify(exactly = 0) {
            deckPresetController.handleSaveDeck(any(), any(), any(), any())
        }
    }
}
