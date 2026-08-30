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
        val btnSize = ImGui.getFrameHeight()
        val spacing = ImGui.getStyle().getItemSpacingX()
        val searchWidth = (ImGui.getContentRegionAvailX() - (btnSize + spacing)).coerceAtLeast(60f)

        // Search Filter Bar
        ImGui.setNextItemWidth(searchWidth)
        ImGui.inputTextWithHint("##presetSearch", "Search presets & tags...", searchBuffer)
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Type to filter presets by name or tags.")
        }

        ImGui.sameLine()

        // [ + ] Create New Preset dropdown button
        if (ImGui.button("${Icons.PLUS}##preset_new_preset", btnSize, btnSize)) {
            ImGui.openPopup("create_new_preset_popup")
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Create new preset on a deck...")
        }

        if (ImGui.beginPopup("create_new_preset_popup")) {
            ImGui.textDisabled("Create new preset on:")
            ImGui.separator()
            if (ImGui.menuItem("Deck A")) {
                UIManager.triggerDeckEject(mixer.deckA, isDeckA = true, isDeckC = false)
                presetState.activeTopTab = "Deck A"
            }
            if (ImGui.menuItem("Deck B")) {
                UIManager.triggerDeckEject(mixer.deckB, isDeckA = false, isDeckC = false)
                presetState.activeTopTab = "Deck B"
            }
            if (ImGui.menuItem("Deck BG")) {
                UIManager.triggerDeckEject(mixer.deckBG, isDeckA = false, isDeckC = false)
                presetState.activeTopTab = "Deck BG"
            }
            if (ImGui.menuItem("Deck PV")) {
                UIManager.triggerDeckEject(mixer.deckPV, isDeckA = false, isDeckC = true)
                presetState.activeTopTab = "Deck PV"
            }
            ImGui.endPopup()
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
            val isSelected = selectedAsset?.path == asset.path

            if (ImGui.selectable(label, isSelected)) {
                LibraryPanel.selectPreset(asset, session, mixer)
            }

            val io = ImGui.getIO()
            if (ImGui.isItemFocused() && !isSelected && !io.wantTextInput) {
                LibraryPanel.selectPreset(asset, session, mixer)
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
        val io = ImGui.getIO()
        val selected = selectedAsset
        if (selected != null && !io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper) {
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Delete), false) ||
                ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Backspace), false)) {
                BrowserPopupHandler.deleteTarget = selected
                BrowserPopupHandler.pendingOpenDeletePopup = true
            }
        }
    }
}
