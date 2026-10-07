package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import java.io.File

class MissingItemsPanel(private val fileBrowser: ImGuiFileBrowser = ImGuiFileBrowser("MissingItemsBrowser")) {
    private var browserOpenForItem: String? = null

    fun draw(session: llm.slop.liquidlsd.SessionContext) {
        val unresolved = session.presetManager.sessionState.unresolvedItems
        if (unresolved.isEmpty()) return

        // We want a modal overlay
        ImGui.openPopup("Missing Session Files")
        if (ImGui.beginPopupModal("Missing Session Files", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("The following files from the previous session could not be found:")
            ImGui.spacing()

            for (path in unresolved) {
                val isDescriptor = path.startsWith("Transition filter not found:") ||
                                   path.startsWith("Deck ") ||
                                   path.contains("filter not found:")
                ImGui.textUnformatted(path)
                if (!isDescriptor) {
                    ImGui.sameLine()
                    if (ButtonChrome.button("Locate...##$path")) {
                        browserOpenForItem = path
                        fileBrowser.open(ImGuiFileBrowser.Mode.LOAD, startDir = File("library"))
                    }
                }
            }

            ImGui.spacing()
            ImGui.separator()
            if (ButtonChrome.button("Dismiss All", 120f, 0f)) {
                session.presetManager.sessionState = session.presetManager.sessionState.copy(unresolvedItems = emptyList())
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }

        fileBrowser.draw { selectedFile ->
            val item = browserOpenForItem
            if (item != null) {
                // Remove the resolved item from the unresolved list
                val newUnresolved = session.presetManager.sessionState.unresolvedItems.filter { it != item }
                session.presetManager.sessionState = session.presetManager.sessionState.copy(unresolvedItems = newUnresolved)
                
                // Relinked files go back to the queue their asset type belongs to.
                if (isTransitionAsset(selectedFile)) TransitionQueueManager.appendToQueue(selectedFile)
                else session.playQueueManager.appendToQueue(selectedFile)
            }
            browserOpenForItem = null
        }
    }

    companion object {
        /** True for transition presets and transition playlists, which belong in the transition queue. */
        internal fun isTransitionAsset(file: File): Boolean =
            file.extension.equals("lsdtrans", ignoreCase = true) || file.extension.equals("lsdtransplay", ignoreCase = true)
    }
}
