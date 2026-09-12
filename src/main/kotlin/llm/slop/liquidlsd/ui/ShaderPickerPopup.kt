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
    
    // Internal cache to avoid allocations in draw()
    private val filteredItems = mutableListOf<ShaderItem>()
    private val categories = mutableListOf<String>()

    data class ShaderItem(
        val id: String,
        val displayName: String,
        val categories: List<String>,
        val type: String,
        val isExternal: Boolean = false
    ) {
        // Pre-joined at construction time — zero allocation when the table row renders
        val categoriesLabel: String = categories.joinToString(", ")
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
                
                val matchesSearch = source.displayName.lowercase().contains(searchText) || source.id.lowercase().contains(searchText)
                val matchesCategory = selectedCategory == "All" || source.categories.contains(selectedCategory)
                
                if (matchesSearch && matchesCategory) {
                    filteredItems.add(ShaderItem(source.id, source.displayName, source.categories, "Source"))
                }
            }
        } else if (pickerType == PickerType.MIXER_TRANSITION) {
            ISFTransitionRegistry.availableTransitions.forEach { transition ->
                transition.categories.forEach { tempCats.add(it) }

                val matchesSearch = transition.displayName.lowercase().contains(searchText) || transition.id.lowercase().contains(searchText)
                val matchesCategory = selectedCategory == "All" || transition.categories.contains(selectedCategory)

                if (matchesSearch && matchesCategory) {
                    filteredItems.add(ShaderItem(transition.id, transition.displayName, transition.categories, "Transition"))
                }
            }
        } else {
            ISFFilterRegistry.availableFilters.forEach { filter ->
                filter.categories.forEach { tempCats.add(it) }

                val matchesSearch = filter.displayName.lowercase().contains(searchText) || filter.id.lowercase().contains(searchText)
                val matchesCategory = selectedCategory == "All" || filter.categories.contains(selectedCategory)

                if (matchesSearch && matchesCategory) {
                    filteredItems.add(ShaderItem(filter.id, filter.displayName, filter.categories, "Filter"))
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
    }

    fun draw(session: SessionContext) {
        if (pendingOpen) {
            ImGui.openPopup(POPUP_ID)
            pendingOpen = false
            updateItems()
        }

        ImGui.setNextWindowSize(700f, 600f, ImGuiCond.Appearing)
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

            // ── Search Bar ──
            ImGui.setNextItemWidth(ImGui.getWindowWidth() - 200f)
            if (ImGui.inputTextWithHint("##search", "Search by name, ID or category...", searchBuf)) {
                updateItems()
            }
            ImGui.sameLine()
            if (ImGui.button("${Icons.TRASH} Detach / None", 170f, 0f)) {
                onSelect?.invoke(null)
                ImGui.closeCurrentPopup()
            }
            itemTooltip("Detach the current shader from this slot.")

            ImGui.spacing()

            // ── Category Pills Row ──
            ImGui.beginChild("##categories_pills", 0f, 40f, false, ImGuiWindowFlags.HorizontalScrollbar)
            // categories is only mutated by updateItems() which runs on this same ImGui thread —
            // no defensive copy needed (no ConcurrentModificationException risk).
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

            // ── Results Table ──
            val tableFlags = ImGuiTableFlags.ScrollY         or 
                             ImGuiTableFlags.BordersInnerV   or 
                             ImGuiTableFlags.RowBg          or 
                             ImGuiTableFlags.Resizable
                             
            if (ImGui.beginTable("##shader_results", 3, tableFlags)) {
                ImGui.tableSetupColumn("Display Name", ImGuiTableColumnFlags.WidthStretch, 0.6f)
                ImGui.tableSetupColumn("Categories", ImGuiTableColumnFlags.WidthStretch, 0.3f)
                ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, 80f)
                ImGui.tableHeadersRow()

                filteredItems.forEach { item ->
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
                ImGui.endTable()
            }

            ImGui.endPopup()
        }
    }
}
