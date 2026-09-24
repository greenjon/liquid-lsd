package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiKey
import io.mockk.mockk
import io.mockk.verify
import io.mockk.every
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ParametersKeyboardTest {

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
        if (ctrl) io.addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, true)
        if (shift) io.addKeyEvent(ImGuiKey.ImGuiMod_Shift, true)
        io.keyCtrl = ctrl
        io.keyShift = shift
        io.addKeyEvent(key, true)
        ImGui.newFrame()

        try {
            block()
        } finally {
            ImGui.render()
            io.addKeyEvent(key, false)
            if (ctrl) io.addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, false)
            if (shift) io.addKeyEvent(ImGuiKey.ImGuiMod_Shift, false)
            io.keyCtrl = false
            io.keyShift = false
        }
    }

    @Test
    fun testCtrlSSavesActiveDeckPreset() {
        val state = ParametersState()
        state.activeTopTab = "Deck A"

        simulateFrameWithKeys(ImGuiKey.S, ctrl = true, shift = false) {
            ParametersKeyboard.handleKeyboardShortcuts(
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
        val state = ParametersState()
        state.activeTopTab = "Deck B"

        simulateFrameWithKeys(ImGuiKey.S, ctrl = true, shift = true) {
            ParametersKeyboard.handleKeyboardShortcuts(
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
        val state = ParametersState()
        state.activeTopTab = "Mixer"

        simulateFrameWithKeys(ImGuiKey.S, ctrl = true, shift = false) {
            ParametersKeyboard.handleKeyboardShortcuts(
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
        val state = ParametersState()
        state.activeTopTab = "Deck A"
        every { deckA.isEmpty } returns true

        simulateFrameWithKeys(ImGuiKey.S, ctrl = true, shift = false) {
            ParametersKeyboard.handleKeyboardShortcuts(
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

    // -- Performance-mode gating (allowUndo / allowSave / allowCellEdits) --

    @Test
    fun testCtrlSIgnoredWhenSaveDisallowed() {
        val state = ParametersState()
        state.activeTopTab = "Deck A"

        simulateFrameWithKeys(ImGuiKey.S, ctrl = true, shift = false) {
            ParametersKeyboard.handleKeyboardShortcuts(
                state = state,
                mixer = mixer,
                deckPresetController = deckPresetController,
                onPushUndo = { _, _ -> },
                onPerformUndo = { _, _ -> },
                allowSave = false
            )
        }

        verify(exactly = 0) {
            deckPresetController.handleSaveDeck(any(), any(), any(), any())
        }
    }

    @Test
    fun testCtrlZStillUndoesWhenOnlyUndoAllowed() {
        val state = ParametersState()
        var undoCount = 0

        simulateFrameWithKeys(ImGuiKey.Z, ctrl = true, shift = false) {
            ParametersKeyboard.handleKeyboardShortcuts(
                state = state,
                mixer = mixer,
                deckPresetController = deckPresetController,
                onPushUndo = { _, _ -> },
                onPerformUndo = { _, _ -> undoCount++ },
                allowSave = false,
                allowCellEdits = false
            )
        }

        kotlin.test.assertEquals(1, undoCount)
    }

    private fun pressDeleteWithSelectedParam(allowCellEdits: Boolean): llm.slop.liquidlsd.parameters.ModulatableParameter {
        val param = mockk<llm.slop.liquidlsd.parameters.ModulatableParameter>(relaxed = true)
        val state = ParametersState()
        state.selectedParam = param
        simulateFrameWithKeys(ImGuiKey.Delete, ctrl = false, shift = false) {
            ParametersKeyboard.handleKeyboardShortcuts(
                state = state,
                mixer = mixer,
                deckPresetController = deckPresetController,
                onPushUndo = { _, _ -> },
                onPerformUndo = { _, _ -> },
                allowCellEdits = allowCellEdits
            )
        }
        return param
    }

    @Test
    fun testDeleteResetsSelectedParamWhenCellEditsAllowed() {
        val param = pressDeleteWithSelectedParam(allowCellEdits = true)
        verify(exactly = 1) { param.reset() }
    }

    @Test
    fun testDeleteIgnoredWhenCellEditsDisallowed() {
        val param = pressDeleteWithSelectedParam(allowCellEdits = false)
        verify(exactly = 0) { param.reset() }
    }
}
