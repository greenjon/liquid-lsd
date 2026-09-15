package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiComboFlags
import imgui.flag.ImGuiKey
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import mu.KotlinLogging
import java.io.File

object StockTransitionListPanel {
    private val logger = KotlinLogging.logger {}
    val searchBuffer = ImString(256)
    var selectedCategory: String = "All"
    var selectedTransitionId: String? = null
    var shouldFocusSearch: Boolean = false
    var filteredTransitions: List<ISFFilter> = emptyList()

    private var lastQuery: String = ""
    private var lastCategory: String = ""
    private var lastAllTransitions: List<ISFFilter>? = null
    private var cachedFiltered: List<ISFFilter> = emptyList()

    fun draw(session: SessionContext, mixer: Mixer) {
        val allTransitions = ISFTransitionRegistry.availableTransitions

        // Title Bar: "Stock Shaders" on the left, total count badge on the right
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Stock Shaders")
        }
        ImGui.sameLine()
        val countText = "[${allTransitions.size}]"
        val countW = ImGui.calcTextSize(countText).x
        val rightX = (ImGui.getWindowContentRegionMaxX() - countW).coerceAtLeast(ImGui.getCursorPosX())
        ImGui.setCursorPosX(rightX)
        ImGui.textDisabled(countText)

        ImGui.separator()
        ImGui.spacing()

        // Category Filter Combo & Search Bar
        val availableCategories = mutableListOf("All")
        val catsFromRegistry = allTransitions.flatMap { it.categories }.distinct().sorted()
        availableCategories.addAll(catsFromRegistry)

        val comboWidth = ImGui.getContentRegionAvailX()
        ImGui.setNextItemWidth(comboWidth)
        if (ImGui.beginCombo("##stockTransCategory", "Category: $selectedCategory", ImGuiComboFlags.None)) {
            availableCategories.forEach { cat ->
                val isSelected = selectedCategory == cat
                if (ImGui.selectable(cat, isSelected)) {
                    selectedCategory = cat
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        itemTooltip("Filter stock shaders by category.")

        ImGui.spacing()

        val searchWidth = ImGui.getContentRegionAvailX()
        ImGui.setNextItemWidth(searchWidth)
        if (shouldFocusSearch) {
            ImGui.setKeyboardFocusHere()
            shouldFocusSearch = false
        }
        ImGui.inputTextWithHint("##stockTransSearch", "Search shaders & folders...", searchBuffer)
        if (ImGui.isItemActive()) {
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                searchBuffer.set("")
                LibraryPanel.shouldReclaimFocus = true
            }
        }
        itemTooltip("Type to search stock transitions by name, category, or folder.")

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##stock_transitions_scroll", 0f, 0f, false)) {
            val query = searchBuffer.get().trim().lowercase()

            val filtered = if (allTransitions === lastAllTransitions && query == lastQuery && selectedCategory == lastCategory) {
                cachedFiltered
            } else {
                lastQuery = query
                lastCategory = selectedCategory
                lastAllTransitions = allTransitions
                val res = allTransitions.filter { trans ->
                    val matchesCategory = selectedCategory == "All" || trans.categories.contains(selectedCategory)
                    val matchesQuery = query.isEmpty() ||
                        trans.displayName.lowercase().contains(query) ||
                        trans.id.lowercase().contains(query) ||
                        trans.folderPath.lowercase().contains(query) ||
                        trans.categories.any { it.lowercase().contains(query) }
                    matchesCategory && matchesQuery
                }
                cachedFiltered = res
                res
            }
            filteredTransitions = filtered

            if (filtered.isEmpty()) {
                ImGui.textDisabled(if (query.isEmpty()) "No shaders found" else "No matching shaders")
            } else {
                val btnW = 20f
                val isPanelFocused = ImGui.isWindowFocused()

                filtered.forEachIndexed { index, trans ->
                    val isSelected = selectedTransitionId == trans.id
                    val popupId = "stock_trans_context_$index"

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

                    val folderLabel = if (trans.folderPath.isNotBlank()) " [${trans.folderPath}]" else ""
                    val label = "${Icons.ACTIVITY} ${trans.displayName}$folderLabel##stock_trans_$index"

                    session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
                        ImGui.selectable(label, isSelected, 0, itemW, rowHeight)
                    }
                    val isRowHovered = ImGui.isItemHovered()

                    if (ImGui.isItemClicked(0)) {
                        LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.STOCK_TRANSITIONS
                        selectedTransitionId = trans.id
                    }

                    // Double-click: apply transition directly to mixer
                    if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                        logger.info { "Applying stock transition ${trans.displayName} (${trans.id}) to mixer" }
                        mixer.setTransition(trans.id)
                    }

                    // Drag source for stock transition item
                    if (ImGui.beginDragDropSource()) {
                        val payloadToken = trans.baseDir?.let { File(it, "${trans.id}.fs").absolutePath }
                            ?: "isf_trans:${trans.id}"
                        ImGui.setDragDropPayload("ASSET_ITEM", payloadToken as Any)
                        ImGui.textUnformatted(trans.displayName)
                        ImGui.endDragDropSource()
                    }

                    if (isRowHovered && trans.categories.isNotEmpty()) {
                        itemTooltip("${trans.displayName}\nCategories: ${trans.categories.joinToString(", ")}")
                    }

                    ImGui.sameLine(0f, 0f)
                    BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "stock_trans_$index", btnW)

                    // Context menu
                    if (ImGui.beginPopup(popupId)) {
                        if (ImGui.menuItem("Apply to Mixer")) {
                            mixer.setTransition(trans.id)
                        }
                        if (ImGui.menuItem("Add to Live Queue")) {
                            val fileToken = trans.baseDir?.let { File(it, "${trans.id}.fs") } ?: File(trans.id)
                            TransitionQueueManager.appendToQueue(fileToken)
                        }
                        val activePlFile = LibraryPanel.selectedTransitionPlaylistFile
                        if (activePlFile != null) {
                            if (ImGui.menuItem("Add to '${activePlFile.nameWithoutExtension}' Playlist")) {
                                appendToActiveTransitionPlaylist(activePlFile, trans.id)
                            }
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

    private fun appendToActiveTransitionPlaylist(playlistFile: File, transitionId: String) {
        if (!playlistFile.exists()) return
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true }
            val dto = json.decodeFromString<llm.slop.liquidlsd.models.TransitionPlaylistDto>(playlistFile.readText())
            val updated = dto.copy(items = dto.items + transitionId)
            playlistFile.writeText(json.encodeToString(llm.slop.liquidlsd.models.TransitionPlaylistDto.serializer(), updated))
            LibraryPanel.refreshAssets()
        } catch (e: Exception) {
            logger.error(e) { "Failed to append item $transitionId to transition playlist ${playlistFile.name}" }
        }
    }
}
