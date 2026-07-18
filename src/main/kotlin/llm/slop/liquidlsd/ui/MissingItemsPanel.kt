package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.patches.PatchManager
import llm.slop.liquidlsd.patches.PlayQueueManager
import java.io.File

class MissingItemsPanel(private val fileBrowser: ImGuiFileBrowser = ImGuiFileBrowser("MissingItemsBrowser")) {
    private var browserOpenForItem: String? = null

    fun draw() {
        val unresolved = PatchManager.sessionState.unresolvedItems
        if (unresolved.isEmpty()) return

        // We want a modal overlay
        ImGui.openPopup("Missing Session Files")
        if (ImGui.beginPopupModal("Missing Session Files", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("The following files from the previous session could not be found:")
            ImGui.spacing()

            for (path in unresolved) {
                ImGui.text(path)
                ImGui.sameLine()
                if (ImGui.button("Locate...##$path")) {
                    browserOpenForItem = path
                    fileBrowser.open(ImGuiFileBrowser.Mode.LOAD, startDir = File("presets"))
                }
            }

            ImGui.spacing()
            ImGui.separator()
            if (ImGui.button("Dismiss All", 120f, 0f)) {
                PatchManager.sessionState = PatchManager.sessionState.copy(unresolvedItems = emptyList())
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }

        fileBrowser.draw { selectedFile ->
            val item = browserOpenForItem
            if (item != null) {
                // Remove the resolved item from the unresolved list
                val newUnresolved = PatchManager.sessionState.unresolvedItems.filter { it != item }
                PatchManager.sessionState = PatchManager.sessionState.copy(unresolvedItems = newUnresolved)
                
                // Add the newly found file to the PlayQueueManager
                PlayQueueManager.appendToQueue(selectedFile)
            }
            browserOpenForItem = null
        }
    }
}
