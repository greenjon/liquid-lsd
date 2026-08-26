package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.PlaylistManager
import llm.slop.liquidlsd.ui.PresetGridState
import llm.slop.liquidlsd.ui.UIManager
import mu.KotlinLogging
import java.io.File

object PresetListPanel {
    private val logger = KotlinLogging.logger {}
    val searchBuffer = ImString(256)
    var selectedAsset: AssetItem? = null

    fun draw(session: SessionContext, mixer: Mixer, presetState: PresetGridState) {
        // Top Action Toolbar
        BrowserActionToolbar.draw(
            session = session,
            mixer = mixer,
            presetState = presetState,
            getSelectedFile = {
                selectedAsset?.let { File(it.path) } ?: PlaylistEditorPanel.getSelectedPresetFile()
            }
        )

        ImGui.spacing()

        // Search Filter Bar
        ImGui.inputTextWithHint("##presetSearch", "Search presets & tags...", searchBuffer)
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Type to filter presets by name or tags.")
        }

        ImGui.separator()
        ImGui.spacing()

        // Flat list of all presets
        val allPresets = FileSystemManager.scanAllPresets()
        val query = searchBuffer.get().trim().lowercase()

        val filtered = if (query.isEmpty()) {
            allPresets
        } else {
            allPresets.filter { asset ->
                asset.name.lowercase().contains(query) ||
                    asset.tags.any { it.lowercase().contains(query) }
            }
        }

        if (filtered.isEmpty()) {
            ImGui.textDisabled(if (query.isEmpty()) "No presets found" else "No matching presets")
            return
        }

        filtered.forEachIndexed { index, asset ->
            ImGui.pushID(index)

            val label = asset.displayName
            val isSelected = selectedAsset == asset

            if (ImGui.selectable(label, isSelected)) {
                selectedAsset = asset
                PlaylistEditorPanel.selectedPresetIndex = -1
            }

            // Double-click: Load the preset to the inactive deck (>0% crossfader).
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                val targetIsA = mixer.crossfade.value > 0.0f
                val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB
                val isDirty = session.presetManager.isDeckDirty(targetDeck, mixer)

                if (!isDirty) {
                    logger.info { "Loading preset ${asset.name} to inactive deck ${if (targetIsA) "A" else "B"}" }
                    session.presetManager.loadDeckPresetAsync(File(asset.path), targetIsA)
                } else {
                    UIManager.triggerDeckDragDrop(File(asset.path), targetDeck, targetIsA, mixer)
                }
            }

            // Drag source: drag a preset
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
                ImGui.text(asset.name)
                ImGui.endDragDropSource()
            }

            // Right-click context menu
            if (ImGui.beginPopupContextItem("preset_context_menu_$index")) {
                if (ImGui.menuItem("Load to Deck A")) {
                    session.presetManager.loadDeckPresetAsync(File(asset.path), isDeckA = true)
                }
                if (ImGui.menuItem("Load to Deck B")) {
                    session.presetManager.loadDeckPresetAsync(File(asset.path), isDeckA = false, isDeckBG = false, isDeckPV = false)
                }
                if (ImGui.menuItem("Load to Deck BG")) {
                    session.presetManager.loadDeckPresetAsync(File(asset.path), isDeckBG = true)
                }
                if (ImGui.menuItem("Preview on Deck PV")) {
                    session.presetManager.loadDeckPresetAsync(File(asset.path), isDeckPV = true)
                }
                ImGui.separator()
                if (ImGui.menuItem("Add to A/B Queue")) {
                    session.playQueueManager.appendToQueue(File(asset.path))
                }
                if (ImGui.menuItem("Add to Background Queue")) {
                    llm.slop.liquidlsd.presets.BgQueueManager.appendToQueue(File(asset.path))
                }
                val activePl = LibraryPanel.activePlaylistData
                if (activePl != null) {
                    if (ImGui.menuItem("Add to '${activePl.name}'")) {
                        PlaylistManager.insertPreset(activePl, asset.path, activePl.presets.size)
                    }
                }
                ImGui.separator()
                if (asset.type == AssetType.PRESET) {
                    if (ImGui.menuItem("Rename / Edit Tags...")) {
                        BrowserPopupHandler.openRenamePresetModal(asset)
                    }
                    if (ImGui.menuItem("Duplicate Preset...")) {
                        BrowserPopupHandler.openDuplicatePresetModal(asset)
                    }
                } else {
                    if (ImGui.menuItem("Rename")) {
                        BrowserPopupHandler.renameTarget = asset
                        BrowserPopupHandler.renameBuffer.set(asset.name)
                        BrowserPopupHandler.pendingOpenRenamePopup = true
                    }
                    if (ImGui.menuItem("Clone")) {
                        FileSystemManager.cloneFile(asset.path).onSuccess {
                            LibraryPanel.refreshAssets()
                        }
                    }
                }
                if (ImGui.menuItem("Delete")) {
                    BrowserPopupHandler.deleteTarget = asset
                    BrowserPopupHandler.pendingOpenDeletePopup = true
                }
                ImGui.endPopup()
            }

            ImGui.popID()
        }

        // Keyboard shortcuts (Delete / Backspace deletes selected asset with confirmation)
        val selected = selectedAsset
        if (selected != null && !ImGui.getIO().wantTextInput) {
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Delete), false) ||
                ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Backspace), false)) {
                BrowserPopupHandler.deleteTarget = selected
                BrowserPopupHandler.pendingOpenDeletePopup = true
            }
        }
    }
}
