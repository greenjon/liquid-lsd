package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
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
        // Search Filter Bar
        ImGui.inputTextWithHint("##presetSearch", "Search presets & tags...", searchBuffer)
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Type to filter presets by name or tags.")
        }

        ImGui.separator()
        ImGui.spacing()

        // Create new preset row
        drawCreateNewPresetRow(session, mixer, presetState)

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

        val btnSize = ImGui.getFrameHeight()

        filtered.forEachIndexed { index, asset ->
            ImGui.pushID(index)

            // Button A (Deck A — blue)
            BrowserDeckButtons.push(BrowserDeckButtons.colorA())
            if (ImGui.button("A##preview_a_$index", btnSize, btnSize)) {
                val targetDeck = mixer.deckA
                val isDirty = session.presetManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    logger.info { "Loading preset ${asset.name} to Deck A" }
                    session.presetManager.loadDeckPresetAsync(File(asset.path), isDeckA = true, isDeckC = false)
                } else {
                    UIManager.triggerDeckDragDrop(File(asset.path), targetDeck, true, mixer)
                }
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Load preset to Deck A.")
            BrowserDeckButtons.pop()

            ImGui.sameLine()

            // Button B (Deck B — orange)
            BrowserDeckButtons.push(BrowserDeckButtons.colorB())
            if (ImGui.button("B##preview_b_$index", btnSize, btnSize)) {
                val targetDeck = mixer.deckB
                val isDirty = session.presetManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    logger.info { "Loading preset ${asset.name} to Deck B" }
                    session.presetManager.loadDeckPresetAsync(File(asset.path), isDeckA = false, isDeckC = false)
                } else {
                    UIManager.triggerDeckDragDrop(File(asset.path), targetDeck, false, mixer)
                }
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Load preset to Deck B.")
            BrowserDeckButtons.pop()

            ImGui.sameLine()

            // Button C (Deck C — green)
            BrowserDeckButtons.push(BrowserDeckButtons.colorC())
            if (ImGui.button("C##preview_c_$index", btnSize, btnSize)) {
                val targetDeck = mixer.deckC
                val isDirty = session.presetManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    logger.info { "Previewing preset ${asset.name} on Deck C" }
                    session.presetManager.loadDeckPresetAsync(File(asset.path), isDeckA = false, isDeckC = true)
                } else {
                    UIManager.triggerDeckDragDrop(File(asset.path), targetDeck, false, mixer)
                }
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Preview preset on Deck C (Preview/C).")
            BrowserDeckButtons.pop()

            ImGui.sameLine()

            // Button Q (Queue — violet)
            BrowserDeckButtons.push(BrowserDeckButtons.colorQ())
            if (ImGui.button("Q##queue_q_$index", btnSize, btnSize)) {
                session.playQueueManager.appendToQueue(File(asset.path))
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Add preset to the end of the queue.")
            BrowserDeckButtons.pop()

            ImGui.sameLine()

            val label = asset.displayName
            val isSelected = selectedAsset == asset

            if (ImGui.selectable(label, isSelected)) {
                selectedAsset = asset
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
                val activePl = LibraryPanel.activePlaylistData
                if (activePl != null) {
                    if (ImGui.menuItem("Add to '${activePl.name}'")) {
                        PlaylistManager.insertPreset(activePl, asset.path, activePl.presets.size)
                    }
                    ImGui.separator()
                }

                if (ImGui.menuItem("Play now (and replace queue)")) {
                    session.playQueueManager.playNow(File(asset.path), mixer)
                }
                if (ImGui.menuItem("Insert into the queue after current")) {
                    session.playQueueManager.insertAfterCurrent(File(asset.path))
                }
                if (ImGui.menuItem("Add to the bottom of the queue")) {
                    session.playQueueManager.appendToQueue(File(asset.path))
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
    }

    private fun drawCreateNewPresetRow(session: SessionContext, mixer: Mixer, presetState: PresetGridState) {
        ImGui.pushID("create_new_preset_row")
        val btnSize = ImGui.getFrameHeight()

        // Button A (Deck A — blue)
        BrowserDeckButtons.push(BrowserDeckButtons.colorA())
        if (ImGui.button("A##new_preset_a", btnSize, btnSize)) {
            UIManager.triggerDeckEject(mixer.deckA, isDeckA = true, isDeckC = false)
            presetState.activeTopTab = "Deck A"
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Create new preset in Deck A (eject existing).")
        BrowserDeckButtons.pop()

        ImGui.sameLine()

        // Button B (Deck B — orange)
        BrowserDeckButtons.push(BrowserDeckButtons.colorB())
        if (ImGui.button("B##new_preset_b", btnSize, btnSize)) {
            UIManager.triggerDeckEject(mixer.deckB, isDeckA = false, isDeckC = false)
            presetState.activeTopTab = "Deck B"
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Create new preset in Deck B (eject existing).")
        BrowserDeckButtons.pop()

        ImGui.sameLine()

        // Button C (Deck C — green)
        BrowserDeckButtons.push(BrowserDeckButtons.colorC())
        if (ImGui.button("C##new_preset_c", btnSize, btnSize)) {
            UIManager.triggerDeckEject(mixer.deckC, isDeckA = false, isDeckC = true)
            presetState.activeTopTab = "Deck C"
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Create new preset in Deck C (eject existing).")
        BrowserDeckButtons.pop()

        ImGui.sameLine()
        ImGui.textDisabled("[Create new preset...]")

        ImGui.popID()
    }
}
