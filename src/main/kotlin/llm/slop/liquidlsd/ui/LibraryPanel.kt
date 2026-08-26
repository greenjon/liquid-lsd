package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.browser.BrowserPopupHandler
import llm.slop.liquidlsd.ui.browser.PlaylistEditorPanel
import llm.slop.liquidlsd.ui.browser.PresetListPanel
import llm.slop.liquidlsd.ui.browser.QueueActionsPanel
import mu.KotlinLogging
import java.io.File

object LibraryPanel {
    private val logger = KotlinLogging.logger {}

    var selectedPlaylistFile: File? = null
    internal var activePlaylistData: PlaylistManager.Playlist? = null

    private var lastKnownSignature: String = ""
    private var lastAutoRefreshTimeMs: Long = 0L

    fun getOrLoadPlaylist(file: File): PlaylistManager.Playlist? {
        val current = activePlaylistData
        if (current != null && current.filePath == file.absolutePath) {
            return current
        }
        PlaylistManager.loadPlaylist(file).onSuccess { playlist ->
            activePlaylistData = playlist
            return playlist
        }
        return null
    }

    private fun checkAutoRefresh() {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshTimeMs > 500L) {
            val root = FileSystemManager.getPresetsRoot()
            val sig = FileSystemManager.getRecursiveDirectorySignature(root)
            if (sig != lastKnownSignature) {
                refreshAssets()
            }
        }
    }

    fun refreshAssets() {
        val root = FileSystemManager.getPresetsRoot()
        lastKnownSignature = FileSystemManager.getRecursiveDirectorySignature(root)
        lastAutoRefreshTimeMs = System.currentTimeMillis()
        FileSystemManager.scanAllPresets()
        FileSystemManager.scanAllPlaylists()
    }

    fun draw(session: SessionContext, width: Float, height: Float, mixer: Mixer, presetState: PresetGridState) {
        checkAutoRefresh()
        val safeW = width.coerceAtLeast(80f)
        val colWidth = ((safeW - 24f) * 0.25f).coerceAtLeast(20f)

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), 6f)
        if (ImGui.beginMenuBar()) {
            UITheme.LibraryMode.entries.forEachIndexed { index, mode ->
                val active = session.uiTheme.libraryMode == mode
                val icon = when (mode) {
                    UITheme.LibraryMode.FULL -> Icons.LAYOUT_FULL
                    UITheme.LibraryMode.HALF -> Icons.LAYOUT_HALF
                    UITheme.LibraryMode.HIDE -> Icons.LAYOUT_HIDE
                }

                if (index > 0) {
                    ImGui.sameLine(0f, 6f)
                }

                // Transparent button background style
                ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1f, 1f, 1f, 0.1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 0.2f)

                // Text color: bright white for active, dimmed for inactive
                if (active) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 1f, 1f, 1f, 1.0f)
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f)
                }

                if (ImGui.button("$icon##mode_${mode.name}")) {
                    session.uiTheme.libraryMode = mode
                    session.uiTheme.saveSettings()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    val modeDesc = when (mode) {
                        UITheme.LibraryMode.FULL -> "Switch library height to Full size."
                        UITheme.LibraryMode.HALF -> "Switch library height to Half size."
                        UITheme.LibraryMode.HIDE -> "Hide the library."
                    }
                    ImGui.setTooltip(modeDesc)
                }
                ImGui.popStyleColor(4)
            }

            ImGui.endMenuBar()
        }
        ImGui.popStyleVar()

        if (session.uiTheme.libraryMode == UITheme.LibraryMode.HIDE) return

        val contentH = (ImGui.getContentRegionAvailY() - 5f).coerceAtLeast(1f)

        // Column 1: Presets Library
        ImGui.beginChild("LibraryPresetsList", colWidth, contentH, true)
        PresetListPanel.draw(session, mixer, presetState)
        ImGui.endChild()
        ImGui.sameLine()

        // Column 2: Playlist Editor
        ImGui.beginChild("LibraryPlaylistEditor", colWidth, contentH, true)
        PlaylistEditorPanel.draw(session, mixer)
        ImGui.endChild()
        ImGui.sameLine()

        // Column 3: Play Queue (A/B)
        ImGui.beginChild("LibraryQueue", colWidth, contentH, true)
        QueueActionsPanel.draw(session, mixer)
        ImGui.endChild()
        ImGui.sameLine()

        // Column 4: Background Queue (BG)
        val lastColWidth = (ImGui.getContentRegionAvailX() - 4f).coerceAtLeast(colWidth)
        ImGui.beginChild("LibraryBgQueue", lastColWidth, contentH, true)
        llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.draw(session, mixer)
        ImGui.endChild()

        // Popups
        if (BrowserPopupHandler.pendingOpenRenamePopup) {
            ImGui.openPopup("RenameAssetPopup")
            BrowserPopupHandler.pendingOpenRenamePopup = false
        }
        if (BrowserPopupHandler.pendingOpenDeletePopup) {
            ImGui.openPopup("ConfirmDeleteAssetPopup")
            BrowserPopupHandler.pendingOpenDeletePopup = false
        }
        BrowserPopupHandler.drawRenameAssetPopup()
        BrowserPopupHandler.drawDeleteAssetConfirmationPopup()
        BrowserPopupHandler.drawNewPlaylistPopup()
        BrowserPopupHandler.drawExportQueuePopup(session)
    }

    fun getSelectedAsset(): AssetItem? = PresetListPanel.selectedAsset
}
