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

    var shouldReclaimFocus: Boolean = false
    var shouldScrollToSelection: Boolean = false
    var isLibraryExpanding: Boolean = true

    fun cycleMode(session: SessionContext) {
        val current = session.uiTheme.libraryMode
        val next = when (current) {
            UITheme.LibraryMode.HIDE -> {
                isLibraryExpanding = true
                UITheme.LibraryMode.HALF
            }
            UITheme.LibraryMode.HALF -> {
                if (isLibraryExpanding) {
                    isLibraryExpanding = false
                    UITheme.LibraryMode.FULL
                } else {
                    isLibraryExpanding = true
                    UITheme.LibraryMode.HIDE
                }
            }
            UITheme.LibraryMode.FULL -> {
                isLibraryExpanding = false
                UITheme.LibraryMode.HALF
            }
        }
        session.uiTheme.libraryMode = next
        session.uiTheme.saveSettings()
    }

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

        if (ImGui.beginMenuBar()) {
            val menuBarH = ImGui.getFrameHeight()
            val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
            val btnW = (24f * fontScale).coerceIn(24f, 40f)
            val btnH = (menuBarH - 8f).coerceAtLeast(20f)
            val yOffset = ((menuBarH - btnH) * 0.5f).coerceAtLeast(0f)

            // Centered Action Toolbar
            val totalToolbarW = llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.TOOLBAR_WIDTH
            val windowBtnsW = (btnW * 2f) + 4f
            val targetCenterX = ((safeW - totalToolbarW) * 0.5f).coerceIn(8f, (safeW - totalToolbarW - windowBtnsW - 8f).coerceAtLeast(8f))

            ImGui.setCursorPosX(targetCenterX)
            ImGui.setCursorPosY(yOffset)

            val selectedFile = getActiveSelectedFile(session)
            llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.draw(
                session = session,
                mixer = mixer,
                presetState = presetState,
                selectedFile = selectedFile,
                source = activeSelectionSource,
                btnHeight = btnH
            )

            // Right-aligned Window Control Buttons (Minimize, Maximize / Restore)
            val rightX = (safeW - windowBtnsW - 8f).coerceAtLeast(ImGui.getCursorPosX() + 8f)
            ImGui.sameLine(0f, 0f)
            ImGui.setCursorPosX(rightX)
            ImGui.setCursorPosY(yOffset)

            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                // Minimize [-]
                val isHidden = session.uiTheme.libraryMode == UITheme.LibraryMode.HIDE
                if (ImGui.button("${Icons.MINUS}##lib_min", btnW, btnH)) {
                    if (isHidden) {
                        session.uiTheme.libraryMode = UITheme.LibraryMode.HALF
                        isLibraryExpanding = true
                        session.uiTheme.libraryRatio = session.uiTheme.lastCustomLibraryRatio.coerceIn(0.15f, 0.85f)
                    } else {
                        session.uiTheme.lastCustomLibraryRatio = session.uiTheme.libraryRatio
                        session.uiTheme.libraryMode = UITheme.LibraryMode.HIDE
                        isLibraryExpanding = true
                    }
                    session.uiTheme.saveSettings()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip(if (isHidden) "Restore Library" else "Minimize Library to bottom bar")
                }

                ImGui.sameLine(0f, 2f)

                // Maximize / Restore [□] / [❐]
                val isFull = session.uiTheme.libraryMode == UITheme.LibraryMode.FULL
                val maxIcon = if (isFull) Icons.COPY else Icons.SQUARE
                if (ImGui.button("$maxIcon##lib_max", btnW, btnH)) {
                    if (isFull) {
                        session.uiTheme.libraryMode = UITheme.LibraryMode.HALF
                        isLibraryExpanding = false
                        session.uiTheme.libraryRatio = session.uiTheme.lastCustomLibraryRatio.coerceIn(0.15f, 0.85f)
                    } else {
                        session.uiTheme.lastCustomLibraryRatio = session.uiTheme.libraryRatio
                        session.uiTheme.libraryMode = UITheme.LibraryMode.FULL
                        isLibraryExpanding = false
                    }
                    session.uiTheme.saveSettings()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip(if (isFull) "Restore Library (Half size)" else "Maximize Library (Full size)")
                }
            }

            ImGui.endMenuBar()
        }

        if (session.uiTheme.libraryMode == UITheme.LibraryMode.HIDE) return

        val contentH = (ImGui.getContentRegionAvailY() - 5f).coerceAtLeast(1f)
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(80f)
        val spacingX = ImGui.getStyle().getItemSpacingX()
        val totalSpacing = spacingX * 3f
        val colWidth = ((availW - totalSpacing) * 0.25f).coerceAtLeast(20f)
        val lastColWidth = (availW - colWidth * 3f - totalSpacing).coerceAtLeast(20f)

        // Column 1: Presets Library
        ImGui.beginChild("LibraryPresetsList", colWidth, contentH, true)
        ImGui.setScrollX(0f)
        PresetListPanel.draw(session, mixer, presetState)
        ImGui.endChild()
        ImGui.sameLine()

        // Column 2: Playlist Editor
        ImGui.beginChild("LibraryPlaylistEditor", colWidth, contentH, true)
        ImGui.setScrollX(0f)
        PlaylistEditorPanel.draw(session, mixer)
        ImGui.endChild()
        ImGui.sameLine()

        // Column 3: Play Queue (A/B)
        ImGui.beginChild("LibraryQueue", colWidth, contentH, true)
        ImGui.setScrollX(0f)
        QueueActionsPanel.draw(session, mixer)
        ImGui.endChild()
        ImGui.sameLine()

        // Column 4: Background Queue (BG)
        ImGui.beginChild("LibraryBgQueue", lastColWidth, contentH, true)
        ImGui.setScrollX(0f)
        llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.draw(session, mixer)
        ImGui.endChild()

        // Global library keyboard shortcuts: 1-4 (Decks), Q / Shift+Q (Queues), Up/Down (Navigation)
        val activeFile = getActiveSelectedFile(session)
        val io = ImGui.getIO()
        if (!io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper) {
            if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_1, false) || ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_1, false)) {
                if (activeFile != null && activeFile.exists()) {
                    BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 1)
                    shouldReclaimFocus = true
                }
            } else if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_2, false) || ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2, false)) {
                if (activeFile != null && activeFile.exists()) {
                    BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 2)
                    shouldReclaimFocus = true
                }
            } else if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_3, false) || ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_3, false)) {
                if (activeFile != null && activeFile.exists()) {
                    BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 3)
                    shouldReclaimFocus = true
                }
            } else if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_4, false) || ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4, false)) {
                if (activeFile != null && activeFile.exists()) {
                    BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 4)
                    shouldReclaimFocus = true
                }
            } else if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Q, false)) {
                if (activeFile != null && activeFile.exists()) {
                    if (io.keyShift) {
                        llm.slop.liquidlsd.presets.BgQueueManager.appendToQueue(activeFile)
                    } else {
                        session.playQueueManager.appendToQueue(activeFile)
                    }
                    shouldReclaimFocus = true
                }
            } else if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_UP, false)) {
                navigateSelection(-1, session, mixer)
            } else if (ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, false)) {
                navigateSelection(1, session, mixer)
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
        BrowserPopupHandler.drawExportBgQueuePopup()

        // Reset one-shot focus/scroll flags at end of frame
        shouldReclaimFocus = false
        shouldScrollToSelection = false
    }

    fun navigateSelection(delta: Int, session: SessionContext, mixer: Mixer) {
        when (activeSelectionSource) {
            SelectionSource.PRESETS -> {
                val list = PresetListPanel.filteredPresets
                if (list.isNotEmpty()) {
                    val currentIdx = list.indexOfFirst { it.path == PresetListPanel.selectedAsset?.path }
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else list.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, list.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        selectPreset(list[targetIdx], session, mixer)
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            SelectionSource.PLAYLIST -> {
                val playlist = activePlaylistData
                if (playlist != null && playlist.presets.isNotEmpty()) {
                    val currentIdx = PlaylistEditorPanel.selectedPresetIndex
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else playlist.presets.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, playlist.presets.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        selectPlaylistPreset(targetIdx, session, mixer)
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            SelectionSource.QUEUE_AB -> {
                val queue = session.playQueueManager.queue
                if (queue.isNotEmpty()) {
                    val currentIdx = QueueActionsPanel.selectedIndex
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else queue.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, queue.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        selectQueueAb(targetIdx, session, mixer)
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            SelectionSource.QUEUE_BG -> {
                val queue = llm.slop.liquidlsd.presets.BgQueueManager.queue
                if (queue.isNotEmpty()) {
                    val currentIdx = llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else queue.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, queue.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        selectQueueBg(targetIdx, session, mixer)
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            null -> {
                val list = PresetListPanel.filteredPresets
                if (list.isNotEmpty()) {
                    selectPreset(list.first(), session, mixer)
                    shouldScrollToSelection = true
                    shouldReclaimFocus = true
                }
            }
        }
    }

    fun getSelectedAsset(): AssetItem? = PresetListPanel.selectedAsset
}
