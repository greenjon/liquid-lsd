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
import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging
import java.io.File

object PresetListPanel {
    private val logger = KotlinLogging.logger {}
    val searchBuffer = ImString(256)
    var selectedAsset: AssetItem? = null
    var shouldFocusSearch: Boolean = false
    var filteredPresets: List<AssetItem> = emptyList()

    fun draw(session: SessionContext, mixer: Mixer, presetState: PresetGridState) {
        val btnSize = ImGui.getFrameHeight()

        // Title Bar: "Presets" on the left, [+] button on the right
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Presets")
        }
        ImGui.sameLine()
        val rightX = ImGui.getWindowContentRegionMaxX() - btnSize
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

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
                UIManager.newPresetSafely(mixer, mixer.deckA)
                presetState.activeTopTab = "Deck A"
            }
            if (ImGui.menuItem("Deck B")) {
                UIManager.newPresetSafely(mixer, mixer.deckB)
                presetState.activeTopTab = "Deck B"
            }
            if (ImGui.menuItem("Deck BG")) {
                UIManager.newPresetSafely(mixer, mixer.deckBG)
                presetState.activeTopTab = "Deck BG"
            }
            if (ImGui.menuItem("Deck PV")) {
                UIManager.newPresetSafely(mixer, mixer.deckPV)
                presetState.activeTopTab = "Deck PV"
            }
            ImGui.endPopup()
        }

        ImGui.separator()
        ImGui.spacing()

        // Search Filter Bar (Full width)
        val searchWidth = ImGui.getContentRegionAvailX()
        ImGui.setNextItemWidth(searchWidth)
        if (shouldFocusSearch) {
            ImGui.setKeyboardFocusHere()
            shouldFocusSearch = false
        }
        ImGui.inputTextWithHint("##presetSearch", "Search presets & tags... (Ctrl+F)", searchBuffer)
        if (ImGui.isItemActive()) {
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.Escape))) {
                searchBuffer.set("")
                LibraryPanel.shouldReclaimFocus = true
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Type to filter presets by name or tags.\nPress Esc while searching to clear.")
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
        filteredPresets = filtered

        if (filtered.isEmpty()) {
            ImGui.textDisabled(if (query.isEmpty()) "No presets found" else "No matching presets")
            return
        }

        filtered.forEachIndexed { index, asset ->
            ImGui.pushID(index)

            val label = asset.displayName
            val isSelected = selectedAsset?.path == asset.path

            val popupId = "preset_context_menu_$index"

            if (isSelected && LibraryPanel.shouldReclaimFocus) {
                ImGui.setKeyboardFocusHere()
            }
            if (isSelected && LibraryPanel.shouldScrollToSelection) {
                ImGui.setScrollHereY(0.5f)
            }

            if (ImGui.selectable(label, isSelected)) {
                LibraryPanel.selectPreset(asset, session, mixer)
            }
            val isRowHovered = ImGui.isItemHovered()
            if (ImGui.isItemClicked(1)) {
                ImGui.openPopup(popupId)
            }

            val io = ImGui.getIO()
            if (ImGui.isItemFocused() && !isSelected && !io.wantTextInput) {
                LibraryPanel.selectPreset(asset, session, mixer)
            }

            // Double-click: Load the preset to the inactive deck (>0% crossfader).
            if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                val targetIsA = mixer.crossfade.value > 0.0f
                val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB
                logger.info { "Loading preset ${asset.name} to inactive deck ${if (targetIsA) "A" else "B"}" }
                UIManager.loadDeckPresetSafely(mixer, targetDeck, File(asset.path))
            }

            // Drag source: drag a preset
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
                ImGui.text(asset.name)
                ImGui.endDragDropSource()
            }

            BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "preset_$index")

            // Context menu (triggered by right-click or more button)
            if (ImGui.beginPopup(popupId)) {
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
