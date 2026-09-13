package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiSelectableFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTableColumnFlags
import imgui.type.ImString
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry
import llm.slop.liquidlsd.SessionContext

/**
 * High-performance, keyboard-searchable modal popup for selecting Visual Sources and ISF Filters.
 *
 * Designed to handle 300+ items with zero-allocation per-frame filtering.
 * Supports category pill filtering and instant fuzzy search.
 */
object ShaderPickerPopup {
    private const val POPUP_ID = "Shader Picker###shader_picker_popup"
    
    enum class PickerType { SOURCE, FX_SLOT_1, FX_SLOT_2, MIXER_TRANSITION }
    
    private var pendingOpen = false
    private var pickerType = PickerType.SOURCE
    private var onSelect: ((String?) -> Unit)? = null
    private var title = "Select Shader"
    
    private val searchBuf = ImString(64)
    private var selectedCategory = "All"
    
    enum class ViewMode { FOLDERS, FLAT }
    private var viewMode = ViewMode.FOLDERS

    // Internal cache to avoid allocations in draw()
    private val filteredItems = mutableListOf<ShaderItem>()
    private val folderGroups = mutableMapOf<String, MutableList<ShaderItem>>()
    private val categories = mutableListOf<String>()

    data class ShaderItem(
        val id: String,
        val displayName: String,
        val categories: List<String>,
        val type: String,
        val isExternal: Boolean = false,
        val folderPath: String = ""
    ) {
        // Pre-joined at construction time — zero allocation when the table row renders
        val categoriesLabel: String = (if (folderPath.isNotBlank() && !categories.contains(folderPath)) listOf(folderPath) + categories else categories).joinToString(", ")
    }

    /**
     * Request the picker to open.
     */
    fun show(title: String, type: PickerType, callback: (String?) -> Unit) {
        this.title = title
        this.pickerType = type
        this.onSelect = callback
        this.pendingOpen = true
        this.searchBuf.set("")
        this.selectedCategory = when(type) {
            PickerType.FX_SLOT_1 -> "Color Adjustment"
            PickerType.FX_SLOT_2 -> "Distortion"
            PickerType.MIXER_TRANSITION -> "Transitions"
            else -> "All"
        }
        updateItems()
    }

    /**
     * Re-calculates the filtered list based on search buffer and selected category.
     */
    private fun updateItems() {
        filteredItems.clear()
        folderGroups.clear()
        val tempCats = mutableSetOf<String>()
        tempCats.add("All")
        
        val searchText = searchBuf.get().lowercase()
        
        if (pickerType == PickerType.SOURCE) {
            // ── Dynamic External Video Feeds ──
            tempCats.add("External Sources")
            val externalServers = llm.slop.liquidlsd.rendering.ExternalVideoDiscovery.availableServers.value
            if (externalServers.isNotEmpty()) {
                externalServers.forEach { srv ->
                    val matchesSearch = srv.lowercase().contains(searchText) || "external".contains(searchText) || "video".contains(searchText)
                    val matchesCategory = selectedCategory == "All" || selectedCategory == "External Sources"
                    if (matchesSearch && matchesCategory) {
                        filteredItems.add(
                            ShaderItem(
                                id = "ext_video:$srv",
                                displayName = srv,
                                categories = listOf("External Sources"),
                                type = "Live Video",
                                isExternal = true
                            )
                        )
                    }
                }
            } else {
                val fallbackName = "External Video (No streams active)"
                val matchesSearch = fallbackName.lowercase().contains(searchText) || "external".contains(searchText)
                val matchesCategory = selectedCategory == "All" || selectedCategory == "External Sources"
                if (matchesSearch && matchesCategory) {
                    filteredItems.add(
                        ShaderItem(
                            id = "ext_video:",
                            displayName = fallbackName,
                            categories = listOf("External Sources"),
                            type = "Live Video",
                            isExternal = true
                        )
                    )
                }
            }

            // ── Static Visual Sources ──
            VisualSourceRegistry.availableSources.forEach { source ->
                if (source is llm.slop.liquidlsd.rendering.ExternalVideoSource) {
                    // Handled above as dynamic external items
                    return@forEach
                }

                source.categories.forEach { tempCats.add(it) }
                if (source.folderPath.isNotBlank()) {
                    tempCats.add(source.folderPath)
                }
                
                val matchesSearch = source.displayName.lowercase().contains(searchText) ||
                    source.id.lowercase().contains(searchText) ||
                    source.folderPath.lowercase().contains(searchText)
                val matchesCategory = selectedCategory == "All" ||
                    source.categories.contains(selectedCategory) ||
                    source.folderPath == selectedCategory
                
                if (matchesSearch && matchesCategory) {
                    filteredItems.add(
                        ShaderItem(
                            id = source.id,
                            displayName = source.displayName,
                            categories = source.categories,
                            type = "Source",
                            folderPath = source.folderPath
                        )
                    )
                }
            }
        } else if (pickerType == PickerType.MIXER_TRANSITION) {
            ISFTransitionRegistry.availableTransitions.forEach { transition ->
                transition.categories.forEach { tempCats.add(it) }
                if (transition.folderPath.isNotBlank()) {
                    tempCats.add(transition.folderPath)
                }

                val matchesSearch = transition.displayName.lowercase().contains(searchText) ||
                    transition.id.lowercase().contains(searchText) ||
                    transition.folderPath.lowercase().contains(searchText)
                val matchesCategory = selectedCategory == "All" ||
                    transition.categories.contains(selectedCategory) ||
                    transition.folderPath == selectedCategory

                if (matchesSearch && matchesCategory) {
                    filteredItems.add(
                        ShaderItem(
                            id = transition.id,
                            displayName = transition.displayName,
                            categories = transition.categories,
                            type = "Transition",
                            folderPath = transition.folderPath
                        )
                    )
                }
            }
        } else {
            ISFFilterRegistry.availableFilters.forEach { filter ->
                filter.categories.forEach { tempCats.add(it) }
                if (filter.folderPath.isNotBlank()) {
                    tempCats.add(filter.folderPath)
                }

                val matchesSearch = filter.displayName.lowercase().contains(searchText) ||
                    filter.id.lowercase().contains(searchText) ||
                    filter.folderPath.lowercase().contains(searchText)
                val matchesCategory = selectedCategory == "All" ||
                    filter.categories.contains(selectedCategory) ||
                    filter.folderPath == selectedCategory

                if (matchesSearch && matchesCategory) {
                    filteredItems.add(
                        ShaderItem(
                            id = filter.id,
                            displayName = filter.displayName,
                            categories = filter.categories,
                            type = "Filter",
                            folderPath = filter.folderPath
                        )
                    )
                }
            }
        }
        
        categories.clear()
        categories.addAll(tempCats.sorted())
        val allIdx = categories.indexOf("All")
        if (allIdx > 0) {
            categories.removeAt(allIdx)
            categories.add(0, "All")
        }
        val extIdx = categories.indexOf("External Sources")
        if (extIdx > 1) {
            categories.removeAt(extIdx)
            categories.add(1, "External Sources")
        }
        
        filteredItems.sortBy { it.displayName.lowercase() }

        // Rebuild folder groups cache
        filteredItems.forEach { item ->
            folderGroups.getOrPut(item.folderPath) { mutableListOf() }.add(item)
        }
    }

    private fun renderTableRow(item: ShaderItem, session: SessionContext) {
        ImGui.tableNextRow()
        
        // Col 0: Name
        ImGui.tableSetColumnIndex(0)
        val itemLabel = if (item.isExternal) "${Icons.ACTIVITY}  ${item.displayName}" else item.displayName
        if (item.isExternal) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.85f, 0.45f, 1.0f)
        }
        if (ImGui.selectable(itemLabel, false, ImGuiSelectableFlags.SpanAllColumns or ImGuiSelectableFlags.AllowDoubleClick)) {
            onSelect?.invoke(item.id)
            ImGui.closeCurrentPopup()
        }
        if (item.isExternal) {
            ImGui.popStyleColor(1)
        }
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
             onSelect?.invoke(item.id)
             ImGui.closeCurrentPopup()
        }
        
        // Col 1: Categories (pre-joined at updateItems() time, zero allocation here)
        ImGui.tableSetColumnIndex(1)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (item.isExternal) {
                ImGui.textColored(0.2f, 0.85f, 0.45f, 0.9f, item.categoriesLabel)
            } else {
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, item.categoriesLabel)
            }
        }

        // Col 2: Action Button
        ImGui.tableSetColumnIndex(2)
        if (ImGui.button("Select##${item.id}", -1f, 0f)) {
            onSelect?.invoke(item.id)
            ImGui.closeCurrentPopup()
        }
    }

    fun draw(session: SessionContext) {
        if (pendingOpen) {
            ImGui.openPopup(POPUP_ID)
            pendingOpen = false
            updateItems()
        }

        ImGui.setNextWindowSize(720f, 620f, ImGuiCond.Appearing)
        val flags = ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse
        
        if (ImGui.beginPopupModal(POPUP_ID, flags)) {
            session.uiTheme.withFont(UITheme.FontLevel.H3) {
                ImGui.text("${Icons.SEARCH} $title")
            }
            ImGui.sameLine(ImGui.getWindowWidth() - 120f)
            if (ImGui.button("${Icons.X} Cancel", 100f, 0f)) {
                ImGui.closeCurrentPopup()
            }
            
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // ── Search Bar & View Mode Toggle ──
            ImGui.setNextItemWidth(ImGui.getWindowWidth() - 320f)
            if (ImGui.inputTextWithHint("##search", "Search by name, ID or folder...", searchBuf)) {
                updateItems()
            }
            ImGui.sameLine()
            val viewBtnLabel = if (viewMode == ViewMode.FOLDERS) "${Icons.FOLDER} Folders" else "${Icons.LAYOUT_FULL} Flat"
            if (ImGui.button(viewBtnLabel, 90f, 0f)) {
                viewMode = if (viewMode == ViewMode.FOLDERS) ViewMode.FLAT else ViewMode.FOLDERS
            }
            itemTooltip(if (viewMode == ViewMode.FOLDERS) "Switch to flat list view" else "Switch to folder hierarchy view")

            ImGui.sameLine()
            if (ImGui.button("${Icons.TRASH} Detach / None", 170f, 0f)) {
                onSelect?.invoke(null)
                ImGui.closeCurrentPopup()
            }
            itemTooltip("Detach the current shader from this slot.")

            ImGui.spacing()

            // ── Category Pills Row ──
            ImGui.beginChild("##categories_pills", 0f, 40f, false, ImGuiWindowFlags.HorizontalScrollbar)
            for (i in 0 until categories.size) {
                val cat = categories[i]
                val isSelected = cat == selectedCategory
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.8f, 1.0f)
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.25f, 0.55f, 0.85f, 1.0f)
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.15f, 0.45f, 0.75f, 1.0f)
                }
                
                if (ImGui.button(cat)) {
                    selectedCategory = cat
                    updateItems()
                }
                
                if (isSelected) {
                    ImGui.popStyleColor(3)
                }
                ImGui.sameLine()
            }
            ImGui.endChild()

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            val tableFlags = ImGuiTableFlags.ScrollY         or 
                             ImGuiTableFlags.BordersInnerV   or 
                             ImGuiTableFlags.RowBg          or 
                             ImGuiTableFlags.Resizable

            if (viewMode == ViewMode.FLAT) {
                // ── Flat List View ──
                if (ImGui.beginTable("##shader_results", 3, tableFlags)) {
                    ImGui.tableSetupColumn("Display Name", ImGuiTableColumnFlags.WidthStretch, 0.6f)
                    ImGui.tableSetupColumn("Categories", ImGuiTableColumnFlags.WidthStretch, 0.3f)
                    ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, 80f)
                    ImGui.tableHeadersRow()

                    for (i in 0 until filteredItems.size) {
                        renderTableRow(filteredItems[i], session)
                    }
                    ImGui.endTable()
                }
            } else {
                // ── Collapsible Folder Tree View ──
                val isSearching = searchBuf.get().isNotBlank()
                val treeNodeFlags = if (isSearching) imgui.flag.ImGuiTreeNodeFlags.DefaultOpen else 0
                val availY = ImGui.getContentRegionAvailY().coerceAtLeast(100f)

                if (ImGui.beginChild("##shader_folders_child", 0f, availY, false)) {
                    // 1. Folders with subpaths
                    for ((folder, items) in folderGroups) {
                        if (folder.isBlank()) continue
                        val headerLabel = "${Icons.FOLDER}  $folder (${items.size})###tree_$folder"
                        if (ImGui.treeNodeEx(headerLabel, treeNodeFlags)) {
                            if (ImGui.beginTable("##tbl_$folder", 3, ImGuiTableFlags.RowBg or ImGuiTableFlags.BordersInnerV)) {
                                ImGui.tableSetupColumn("Display Name", ImGuiTableColumnFlags.WidthStretch, 0.6f)
                                ImGui.tableSetupColumn("Categories", ImGuiTableColumnFlags.WidthStretch, 0.3f)
                                ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, 80f)
                                ImGui.tableHeadersRow()

                                for (i in 0 until items.size) {
                                    renderTableRow(items[i], session)
                                }
                                ImGui.endTable()
                            }
                            ImGui.treePop()
                        }
                    }

                    // 2. Root items (without a folder or top-level)
                    val rootItems = folderGroups[""] ?: emptyList()
                    if (rootItems.isNotEmpty()) {
                        val hasOtherFolders = folderGroups.keys.any { it.isNotBlank() }
                        if (hasOtherFolders) {
                            val headerLabel = "${Icons.FILE}  General / Root (${rootItems.size})###tree_root"
                            if (ImGui.treeNodeEx(headerLabel, treeNodeFlags)) {
                                if (ImGui.beginTable("##tbl_root", 3, ImGuiTableFlags.RowBg or ImGuiTableFlags.BordersInnerV)) {
                                    ImGui.tableSetupColumn("Display Name", ImGuiTableColumnFlags.WidthStretch, 0.6f)
                                    ImGui.tableSetupColumn("Categories", ImGuiTableColumnFlags.WidthStretch, 0.3f)
                                    ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, 80f)
                                    ImGui.tableHeadersRow()

                                    for (i in 0 until rootItems.size) {
                                        renderTableRow(rootItems[i], session)
                                    }
                                    ImGui.endTable()
                                }
                                ImGui.treePop()
                            }
                        } else {
                            if (ImGui.beginTable("##tbl_root_direct", 3, tableFlags)) {
                                ImGui.tableSetupColumn("Display Name", ImGuiTableColumnFlags.WidthStretch, 0.6f)
                                ImGui.tableSetupColumn("Categories", ImGuiTableColumnFlags.WidthStretch, 0.3f)
                                ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, 80f)
                                ImGui.tableHeadersRow()

                                for (i in 0 until rootItems.size) {
                                    renderTableRow(rootItems[i], session)
                                }
                                ImGui.endTable()
                            }
                        }
                    }
                }
                ImGui.endChild()
            }

            ImGui.endPopup()
        }
    }
}

