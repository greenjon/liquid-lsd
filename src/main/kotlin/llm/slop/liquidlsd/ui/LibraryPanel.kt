package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons
import llm.slop.liquidlsd.ui.browser.BrowserPopupHandler
import llm.slop.liquidlsd.ui.browser.PlaylistEditorPanel
import llm.slop.liquidlsd.ui.browser.PresetListPanel
import llm.slop.liquidlsd.ui.browser.QueueActionsPanel
import mu.KotlinLogging
import java.io.File

object LibraryPanel {
    private val logger = KotlinLogging.logger {}

    enum class SelectionSource {
        PRESETS,
        PLAYLIST,
        QUEUE_AB,
        QUEUE_BG
    }

    var activeSelectionSource: SelectionSource? = null
    var selectedPlaylistFile: File? = null
    internal var activePlaylistData: PlaylistManager.Playlist? = null

    private var lastKnownSignature: String = ""
    private var lastAutoRefreshTimeMs: Long = 0L

    fun getActiveSelectedFile(session: SessionContext): File? {
        return when (activeSelectionSource) {
            SelectionSource.PRESETS -> PresetListPanel.selectedAsset?.let { File(it.path) }
            SelectionSource.PLAYLIST -> PlaylistEditorPanel.getSelectedPresetFile()
            SelectionSource.QUEUE_AB -> {
                val idx = QueueActionsPanel.selectedIndex
                if (idx in session.playQueueManager.queue.indices) session.playQueueManager.queue[idx] else null
            }
            SelectionSource.QUEUE_BG -> {
                val idx = llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex
                if (idx in llm.slop.liquidlsd.presets.BgQueueManager.queue.indices) llm.slop.liquidlsd.presets.BgQueueManager.queue[idx] else null
            }
            null -> null
        }
    }

    fun selectPreset(asset: AssetItem?, session: SessionContext, mixer: Mixer) {
        PresetListPanel.selectedAsset = asset
        if (asset != null) {
            activeSelectionSource = SelectionSource.PRESETS
            PlaylistEditorPanel.selectedPresetIndex = -1
            QueueActionsPanel.selectedIndex = -1
            llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
            auditionIfLocked(File(asset.path), session, mixer)
        }
    }

    fun selectPlaylistPreset(index: Int, session: SessionContext, mixer: Mixer) {
        PlaylistEditorPanel.selectedPresetIndex = index
        if (index >= 0) {
            activeSelectionSource = SelectionSource.PLAYLIST
            PresetListPanel.selectedAsset = null
            QueueActionsPanel.selectedIndex = -1
            llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
            val file = PlaylistEditorPanel.getSelectedPresetFile()
            if (file != null) auditionIfLocked(file, session, mixer)
        }
    }

    fun selectQueueAb(index: Int, session: SessionContext, mixer: Mixer) {
        QueueActionsPanel.selectedIndex = index
        if (index >= 0) {
            activeSelectionSource = SelectionSource.QUEUE_AB
            PresetListPanel.selectedAsset = null
            PlaylistEditorPanel.selectedPresetIndex = -1
            llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
            val file = session.playQueueManager.queue.getOrNull(index)
            if (file != null) auditionIfLocked(file, session, mixer)
        }
    }

    fun selectQueueBg(index: Int, session: SessionContext, mixer: Mixer) {
        llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = index
        if (index >= 0) {
            activeSelectionSource = SelectionSource.QUEUE_BG
            PresetListPanel.selectedAsset = null
            PlaylistEditorPanel.selectedPresetIndex = -1
            QueueActionsPanel.selectedIndex = -1
            val file = llm.slop.liquidlsd.presets.BgQueueManager.queue.getOrNull(index)
            if (file != null) auditionIfLocked(file, session, mixer)
        }
    }

    fun clearAllSelection() {
        activeSelectionSource = null
        PresetListPanel.selectedAsset = null
        PlaylistEditorPanel.selectedPresetIndex = -1
        QueueActionsPanel.selectedIndex = -1
        llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
    }

    fun auditionIfLocked(file: File, session: SessionContext, mixer: Mixer) {
        if (llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.isAuditionLocked) {
            val target = llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.latchedDeckTarget ?: return
            BrowserDeckButtons.loadPresetToDeck(session, mixer, file, target.deckIndex)
        }
    }

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

            // Menu Bar Separator and Action Toolbar
            ImGui.sameLine(0f, 12f)
            val selectedFile = getActiveSelectedFile(session)
            llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.draw(
                session = session,
                mixer = mixer,
                presetState = presetState,
                selectedFile = selectedFile,
                source = activeSelectionSource
            )

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

        // Number key shortcuts: 1 -> Deck A, 2 -> Deck B, 3 -> Deck BG, 4 -> Deck PV
        val activeFile = getActiveSelectedFile(session)
        val io = ImGui.getIO()
        if (activeFile != null && activeFile.exists() && !io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper) {
            if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_1, false) || ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_1, false)) {
                BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 1)
            } else if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_2, false) || ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2, false)) {
                BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 2)
            } else if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_3, false) || ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_3, false)) {
                BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 3)
            } else if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_4, false) || ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4, false)) {
                BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 4)
            }
        }

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
