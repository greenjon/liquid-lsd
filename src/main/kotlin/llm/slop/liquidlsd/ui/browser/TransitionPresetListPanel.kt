package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.SavePresetModal
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import mu.KotlinLogging
import java.io.File

object TransitionPresetListPanel {
    private val logger = KotlinLogging.logger {}
    val searchBuffer = ImString(256)
    var selectedAsset: AssetItem? = null
    var shouldFocusSearch: Boolean = false
    var filteredPresets: List<AssetItem> = emptyList()

    private var lastQuery: String = ""
    private var lastAllPresets: List<AssetItem>? = null
    private var cachedFiltered: List<AssetItem> = emptyList()

    fun draw(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        val btnSize = ImGui.getFrameHeight()

        // Title Bar: "Transition Presets" on the left, [+] button on the right
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Transition Presets")
        }
        ImGui.sameLine()
        val rightX = ImGui.getWindowContentRegionMaxX() - btnSize
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

        // [ + ] Save Active Transition as Preset button
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.PLUS}##trans_preset_new", btnSize, btnSize)) {
                val currentTrans = mixer.transitionFilter
                if (currentTrans != null) {
                    val slotDto = FXSlotDto(
                        filterId = currentTrans.id,
                        enabled = currentTrans.enabled,
                        dryWet = currentTrans.dryWet.toDto(),
                        parameters = currentTrans.parameters.mapValues { p -> p.value.toDto() }
                    )
                    SavePresetModal.request(
                        title = "Save Transition Preset As",
                        confirmLabel = "Save",
                        defaultName = currentTrans.displayName.lowercase().replace(" ", "_"),
                        targetDir = FileSystemManager.getTransitionsRoot(),
                        extension = "lsdtrans"
                    ) { name, tags ->
                        val file = File(FileSystemManager.getTransitionsRoot(), "$name.lsdtrans")
                        session.presetRepository.saveTransitionPresetAsync(file, name, slotDto, tags)
                        LibraryPanel.refreshAssets()
                    }
                } else {
                    logger.warn { "No active transition filter to save as preset" }
                }
            }
        }
        itemTooltip("Save current mixer transition as a preset (.lsdtrans)...")

        ImGui.separator()
        ImGui.spacing()

        // Search Filter Bar
        val searchWidth = ImGui.getContentRegionAvailX()
        ImGui.setNextItemWidth(searchWidth)
        if (shouldFocusSearch) {
            ImGui.setKeyboardFocusHere()
            shouldFocusSearch = false
        }
        ImGui.inputTextWithHint("##transPresetSearch", "Search transition presets & tags...", searchBuffer)
        if (ImGui.isItemActive()) {
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                searchBuffer.set("")
                LibraryPanel.shouldReclaimFocus = true
            }
        }
        itemTooltip("Type to filter transition presets (.lsdtrans) by name or tags.")

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##trans_presets_scroll", 0f, 0f, false)) {
            val allPresets = FileSystemManager.scanAllTransitionPresets()
            val query = searchBuffer.get().trim().lowercase()

            val filtered = if (allPresets === lastAllPresets && query == lastQuery) {
                cachedFiltered
            } else {
                lastQuery = query
                lastAllPresets = allPresets
                val res = if (query.isEmpty()) {
                    allPresets
                } else {
                    allPresets.filter { asset ->
                        asset.name.lowercase().contains(query) || asset.tags.any { it.lowercase().contains(query) }
                    }
                }
                cachedFiltered = res
                res
            }
            filteredPresets = filtered

            if (filtered.isEmpty()) {
                ImGui.textDisabled(if (query.isEmpty()) "No transition presets" else "No matching presets")
            } else {
                val btnW = 20f
                val isPanelFocused = ImGui.isWindowFocused()

                filtered.forEachIndexed { index, asset ->
                    val isSelected = selectedAsset?.path == asset.path
                    val popupId = "trans_preset_context_$index"

                    val rowHeight = (session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() } + 6f).coerceAtLeast(20f)
                    val itemW = (ImGui.getContentRegionAvailX() - btnW - 4f).coerceAtLeast(10f)

                    if (isSelected && LibraryPanel.shouldScrollToSelection) {
                        ImGui.setScrollHereY(0.5f)
                    }

                    if (isSelected) {
                        val highlightCol = ImGui.colorConvertFloat4ToU32(0.25f, 0.45f, 0.75f, 0.4f)
                        val pMinX = ImGui.getCursorScreenPosX()
                        val pMinY = ImGui.getCursorScreenPosY()
                        val pMaxX = pMinX + ImGui.getContentRegionAvailX()
                        val pMaxY = pMinY + rowHeight
                        ImGui.getWindowDrawList().addRectFilled(pMinX, pMinY, pMaxX, pMaxY, highlightCol)
                    }

                    val label = "${Icons.ACTIVITY} ${asset.displayName}##trans_preset_$index"

                    session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
                        ImGui.selectable(label, isSelected, 0, itemW, rowHeight)
                    }
                    val isRowHovered = ImGui.isItemHovered()

                    if (ImGui.isItemClicked(0)) {
                        LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.TRANSITION_PRESETS
                        selectedAsset = asset
                    }

                    // Double-click: apply preset to mixer
                    if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                        val file = File(asset.path)
                        session.presetRepository.loadTransitionPresetAsync(file).thenAccept { dto ->
                            mixer.applyTransitionPreset(dto)
                        }
                    }

                    // Drag source for transition preset
                    if (ImGui.beginDragDropSource()) {
                        ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
                        ImGui.textUnformatted(asset.name)
                        ImGui.endDragDropSource()
                    }

                    ImGui.sameLine(0f, 0f)
                    BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "trans_preset_$index", btnW)

                    // Context menu
                    if (ImGui.beginPopup(popupId)) {
                        val file = File(asset.path)
                        if (ImGui.menuItem("Apply to Mixer")) {
                            session.presetRepository.loadTransitionPresetAsync(file).thenAccept { dto ->
                                mixer.applyTransitionPreset(dto)
                            }
                        }
                        if (ImGui.menuItem("Add to Live Queue")) {
                            TransitionQueueManager.appendToQueue(file)
                        }
                        val activePlFile = LibraryPanel.selectedTransitionPlaylistFile
                        if (activePlFile != null) {
                            if (ImGui.menuItem("Add to '${activePlFile.nameWithoutExtension}' Playlist")) {
                                appendToActiveTransitionPlaylist(activePlFile, asset.path)
                            }
                        }
                        ImGui.separator()
                        if (ImGui.menuItem("Rename...")) {
                            BrowserPopupHandler.renameTarget = asset
                            BrowserPopupHandler.renameBuffer.set(asset.name)
                            BrowserPopupHandler.pendingOpenRenamePopup = true
                        }
                        if (ImGui.menuItem("Clone")) {
                            FileSystemManager.cloneFile(asset.path).onSuccess {
                                LibraryPanel.refreshAssets()
                            }
                        }
                        if (ImGui.menuItem("Delete...")) {
                            BrowserPopupHandler.deleteTarget = asset
                            BrowserPopupHandler.pendingOpenDeletePopup = true
                        }
                        ImGui.endPopup()
                    }
                }

                if (LibraryPanel.shouldReclaimFocus && isPanelFocused) {
                    ImGui.setKeyboardFocusHere(-1)
                }
            }
        }
        ImGui.endChild()
    }

    private fun appendToActiveTransitionPlaylist(playlistFile: File, presetPath: String) {
        if (!playlistFile.exists()) return
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true }
            val dto = json.decodeFromString<llm.slop.liquidlsd.models.TransitionPlaylistDto>(playlistFile.readText())
            val updated = dto.copy(items = dto.items + presetPath)
            playlistFile.writeText(json.encodeToString(llm.slop.liquidlsd.models.TransitionPlaylistDto.serializer(), updated))
            LibraryPanel.refreshAssets()
        } catch (e: Exception) {
            logger.error(e) { "Failed to append item $presetPath to transition playlist ${playlistFile.name}" }
        }
    }
}
