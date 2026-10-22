package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiKey

/**
 * Keyboard rules shared by the small confirm modals (Rename, Delete, New Playlist, Export, unsaved deck): the text field has the
 * keyboard when the modal opens, Enter confirms and Esc cancels. Call these inside the modal's begin/end.
 */
object ModalKeys {
    /** Frame on which a modal took Enter, so list shortcuts drawn later in the same frame leave it alone. */
    private var enterFrame = -1

    /** Last frame a modal that answers Esc itself was drawn; the global Esc (back) stands down meanwhile. */
    private var escOwnerFrame = -10

    /**
     * Call every frame inside the modal: Enter / Esc answer it. Returns the key's answer (true confirm, false cancel) or null.
     * The global Esc ([NavigationSurface.back]) is skipped while such a modal is up, so Esc only closes the modal.
     */
    fun answer(): Boolean? {
        escOwnerFrame = ImGui.getFrameCount()
        // The key that opened the modal (Enter on a list row, say) must not answer it in the same frame.
        if (ImGui.isWindowAppearing()) return null
        return when {
            confirmPressed() -> true
            cancelPressed() -> false
            else -> null
        }
    }

    /** Whether a modal that answers Esc was on screen this frame or the last. */
    fun ownsEscape(): Boolean = ImGui.getFrameCount() - escOwnerFrame <= 1

    /** Call right before the modal's text field: gives it the keyboard on the modal's first frame. */
    fun focusOnOpen() {
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere()
    }

    /** True on the frame Enter (or keypad Enter) is pressed. */
    fun confirmPressed(): Boolean {
        val pressed = ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false)
        if (pressed) enterFrame = ImGui.getFrameCount()
        return pressed
    }

    /** True on the frame Esc is pressed. */
    fun cancelPressed(): Boolean = ImGui.isKeyPressed(ImGuiKey.Escape, false)

    /** Whether a modal already answered this frame's Enter. */
    fun enterTakenThisFrame(): Boolean = enterFrame == ImGui.getFrameCount()
}
