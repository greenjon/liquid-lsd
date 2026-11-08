package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.ui.browser.LibraryFolders

/** The live check under a name field whose text becomes a file name (Save As, Rename, new and exported playlists). */
object NameCheck {
    /**
     * Shows, under the field just drawn, why [name] can't be saved ([LibraryFolders.nameProblem]); true when it can.
     * An empty field draws nothing and returns false: the dialogs already ignore an empty name. Call it right after the
     * field, inside a modal that uses [ModalKeys.focusOnOpen].
     */
    fun ok(name: String): Boolean {
        if (name.isBlank()) return false
        val problem = LibraryFolders.nameProblem(name) ?: return true
        // Enter takes the keyboard off the field; the dialog won't close, so hand it back for the fix.
        if (ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false)) ModalKeys.refocusField()
        ImGui.pushStyleColor(ImGuiCol.Text, TangoPalette.TEXT_WARN.u32())
        ImGui.textUnformatted(problem)
        ImGui.popStyleColor()
        return false
    }
}
