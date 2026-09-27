package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiSelectableFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTableColumnFlags
import imgui.type.ImString
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry
import llm.slop.liquidlsd.rendering.isf.ISFVisualSource
import llm.slop.liquidlsd.SessionContext

/**
 * High-performance, keyboard-searchable content for selecting Visual Sources, ISF Filters,
 * saved single-FX files, and Mixer Transitions.
 *
 * Designed to handle 300+ items with zero-allocation per-frame filtering.
 * Supports category pill filtering and instant fuzzy search.
 *
 * Drawn inline in the Performance row bay's Browse content (see [PerformanceBrowseBay]), not as a
 * modal popup -- selecting a row applies it immediately and leaves the list open, so rapidly trying
 * several generators/effects in a row (Ctrl+Z undoes any of them) doesn't require reopening
 * anything. [ensureInline]/[ensureInlineFx] (re)configure which items are shown; call them every
 * frame with a stable `contextKey` -- state (search text, category filters) only resets when that
 * key changes, so redrawing each frame doesn't clobber what the user typed.
 */
object ShaderPickerPopup {
    enum class PickerType { SOURCE, FX_SLOT_1, FX_SLOT_2, FX_SLOT_3, MIXER_TRANSITION }

    /** What the user picked for an FX slot: a stock ISF filter, a saved single-FX file, or nothing. */
    sealed class FxPick {
        data class Stock(val filterId: String) : FxPick()
        data class Saved(val file: java.io.File) : FxPick()
        object None : FxPick()
    }

    const val CATEGORY_FAVORITES = "\u2605 Favorites"
    const val CATEGORY_SAVED = "Saved FX"
    private const val SAVED_PREFIX = "saved:"

    private val isFxPicker: Boolean
        get() = pickerType == PickerType.FX_SLOT_1 || pickerType == PickerType.FX_SLOT_2 || pickerType == PickerType.FX_SLOT_3

    /** Identifies which Browse target is currently configured, so [ensureInline] knows when to reset. */
    private var activeContextKey: String? = null
    private var pickerType = PickerType.SOURCE
    private var onSelect: ((String?) -> Unit)? = null
    private var title = "Select Shader"

    private val searchBuf = ImString(64)
    /** Active category pill filters, OR-combined. "All" is exclusive with every other entry. */
    private var selectedCategories: MutableSet<String> = mutableSetOf("All")

    enum class ViewMode { FOLDERS, FLAT }
    private var viewMode = ViewMode.FOLDERS

    // Internal cache to avoid allocations in drawInline()
    private val filteredItems = mutableListOf<ShaderItem>()
    private val folderGroups = mutableMapOf<String, MutableList<ShaderItem>>()
    private val categories = mutableListOf<String>()

    data class ShaderItem(
        val id: String,
        val displayName: String,
        val categories: List<String>,
        val type: String,
        val isExternal: Boolean = false,
        val folderPath: String = "",
        /** The ISF shader's own DESCRIPTION field, shown as a hover tooltip on the row -- this is
         * where the free-form documentation text belongs, not the (now short) displayName. */
        val description: String = ""
    ) {
        // Pre-joined at construction time — zero allocation when the table row renders
        val categoriesLabel: String = (if (folderPath.isNotBlank() && !categories.contains(folderPath)) listOf(folderPath) + categories else categories).joinToString(", ")
    }

    /** Resets search/category/results state for a freshly-selected [type]. */
    private fun resetForType(type: PickerType) {
        pickerType = type
        searchBuf.set("")
        selectedCategories = mutableSetOf(
            when (type) {
                PickerType.FX_SLOT_1 -> "Color Adjustment"
                PickerType.FX_SLOT_2 -> "Distortion"
                PickerType.FX_SLOT_3 -> "All"
                PickerType.MIXER_TRANSITION -> "Transitions"
                else -> "All"
            }
        )
        updateItems()
    }

    /**
     * Configures the picker to show [type]'s items with [title]/[callback], for [drawInline] to
     * render this frame. Cheap to call every frame: search text and category filters only reset
     * when [contextKey] differs from the last call (a new Browse target), not on every redraw.
     */
    fun ensureInline(contextKey: String, title: String, type: PickerType, callback: (String?) -> Unit) {
        this.title = title
        this.onSelect = callback
        if (activeContextKey == contextKey) return
        activeContextKey = contextKey
        resetForType(type)
    }

    /**
     * [ensureInline] for an FX slot: [slotIndex] (0-based) picks the FX_SLOT_N category defaults,
     * and opens on the user's favorites when there are any (the extra "Saved FX" category lists
     * saved single-FX files, .lsdfx).
     */
    fun ensureInlineFx(contextKey: String, title: String, slotIndex: Int, callback: (FxPick) -> Unit) {
        val type = when (slotIndex) {
            0 -> PickerType.FX_SLOT_1
            1 -> PickerType.FX_SLOT_2
            else -> PickerType.FX_SLOT_3
        }
        this.title = title
        this.onSelect = { id ->
            callback(
                when {
                    id == null -> FxPick.None
                    id.startsWith(SAVED_PREFIX) -> FxPick.Saved(java.io.File(id.removePrefix(SAVED_PREFIX)))
                    else -> FxPick.Stock(id)
                }
            )
        }
        if (activeContextKey == contextKey) return
        activeContextKey = contextKey
        resetForType(type)
        if (llm.slop.liquidlsd.presets.FxShortlist.favorites().isNotEmpty()) {
            selectedCategories = mutableSetOf(CATEGORY_FAVORITES)
            updateItems()
        }
    }

    /**
     * Whether an item belongs to at least one active category pill (OR match). "All" or an empty
     * selection means no filtering. [isFavorite] only applies to FX filters.
     */
    private fun matchesSelectedCategories(itemCategories: List<String>, folderPath: String, isFavorite: Boolean = false): Boolean {
        if (selectedCategories.isEmpty() || selectedCategories.contains("All")) return true
        return selectedCategories.any { cat ->
            (cat == CATEGORY_FAVORITES && isFavorite) || itemCategories.contains(cat) || folderPath == cat
        }
    }

    /**
     * Re-calculates the filtered list based on search buffer and selected categories.
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
                    val matchesCategory = matchesSelectedCategories(listOf("External Sources"), "")
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
                val matchesCategory = matchesSelectedCategories(listOf("External Sources"), "")
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
                val matchesCategory = matchesSelectedCategories(source.categories, source.folderPath)

                if (matchesSearch && matchesCategory) {
                    filteredItems.add(
                        ShaderItem(
                            id = source.id,
                            displayName = source.displayName,
                            categories = source.categories,
                            type = "Source",
                            folderPath = source.folderPath,
                            description = (source as? ISFVisualSource)?.header?.DESCRIPTION ?: ""
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
                val matchesCategory = matchesSelectedCategories(transition.categories, transition.folderPath)

                if (matchesSearch && matchesCategory) {
                    filteredItems.add(
                        ShaderItem(
                            id = transition.id,
                            displayName = transition.displayName,
                            categories = transition.categories,
                            type = "Transition",
                            folderPath = transition.folderPath,
                            description = transition.header.DESCRIPTION ?: ""
                        )
                    )
                }
            }
        } else {
            // Stock ISF filters, tag/favorite-filtered.
            ISFFilterRegistry.availableFilters.forEach { filter ->
                filter.categories.forEach { tempCats.add(it) }
                if (filter.folderPath.isNotBlank()) {
                    tempCats.add(filter.folderPath)
                }

                val matchesSearch = filter.displayName.lowercase().contains(searchText) ||
                    filter.id.lowercase().contains(searchText) ||
                    filter.folderPath.lowercase().contains(searchText)
                val isFavorite = llm.slop.liquidlsd.presets.FxShortlist.isFavorite(filter.id)
                val matchesCategory = matchesSelectedCategories(filter.categories, filter.folderPath, isFavorite)

                if (matchesSearch && matchesCategory) {
                    filteredItems.add(
                        ShaderItem(
                            id = filter.id,
                            displayName = filter.displayName,
                            categories = filter.categories,
                            type = "Filter",
                            folderPath = filter.folderPath,
                            description = filter.header.DESCRIPTION ?: ""
                        )
                    )
                }
            }

            // Saved single-FX presets -- included only when that pill is explicitly active,
            // additive with whatever stock tag filters are also selected.
            if (selectedCategories.contains(CATEGORY_SAVED)) {
                FileSystemManager.scanAllFxPresets().forEach { asset ->
                    val matchesSearch = asset.name.lowercase().contains(searchText) ||
                        asset.tags.any { it.lowercase().contains(searchText) }
                    if (matchesSearch) {
                        filteredItems.add(
                            ShaderItem(
                                id = SAVED_PREFIX + asset.path,
                                displayName = asset.name,
                                categories = asset.tags,
                                type = "Saved FX"
                            )
                        )
                    }
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
        if (isFxPicker) {
            categories.add(1, CATEGORY_FAVORITES)
            categories.add(2, CATEGORY_SAVED)
        }
        
        // Integrated camera(s) first, then other live external feeds (Spout/Syphon/PipeWire),
        // then everything else -- alphabetical within each group.
        filteredItems.sortWith(
            compareBy(
                { item ->
                    when {
                        item.displayName.contains("camera", ignoreCase = true) -> 0
                        item.isExternal -> 1
                        else -> 2
                    }
                },
                { it.displayName.lowercase() }
            )
        )

        // Rebuild folder groups cache
        filteredItems.forEach { item ->
            folderGroups.getOrPut(item.folderPath) { mutableListOf() }.add(item)
        }
    }

    /** Result table has 2 columns normally (Name, Categories); FX pickers get a 3rd for the \u2605 favorite toggle. */
    private fun resultColumnCount() = if (isFxPicker) 3 else 2

    private fun setupResultColumns() {
        ImGui.tableSetupColumn("Display Name", ImGuiTableColumnFlags.WidthStretch, 0.3f)
        ImGui.tableSetupColumn("Categories", ImGuiTableColumnFlags.WidthStretch, if (isFxPicker) 0.6f else 0.7f)
        if (isFxPicker) {
            ImGui.tableSetupColumn("\u2605", ImGuiTableColumnFlags.WidthFixed, 34f)
        }
    }

    /** Dims and shrinks the table header row so it reads as a label, not another data row. */
    private fun drawDimmedHeadersRow(session: SessionContext) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.55f, 0.58f, 1.0f)
            ImGui.tableHeadersRow()
            ImGui.popStyleColor()
        }
    }

    private fun renderTableRow(item: ShaderItem, session: SessionContext) {
        ImGui.tableNextRow()

        // Col 0: Name -- the whole row is the click target (single or double click selects it).
        ImGui.tableSetColumnIndex(0)
        val itemLabel = if (item.isExternal) "${Icons.ACTIVITY}  ${item.displayName}" else item.displayName
        if (item.isExternal) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.85f, 0.45f, 1.0f)
        }
        // Applies immediately and leaves the list open -- there's no popup to close, and trying
        // several picks in a row (each undoable with Ctrl+Z) is the point.
        if (selectableRow(itemLabel, false, flags = ImGuiSelectableFlags.AllowDoubleClick)) {
            onSelect?.invoke(item.id)
        }
        if (item.isExternal) {
            ImGui.popStyleColor(1)
        }
        if (item.description.isNotBlank()) {
            itemTooltip(item.description)
        }

        // Col 1: Categories (pre-joined at updateItems() time, zero allocation here).
        // Drawn in whatever font is already active (the popup-wide TOOLTIP font pushed in
        // draw()), so it reads at the same size as the Display Name column.
        ImGui.tableSetColumnIndex(1)
        if (item.isExternal) {
            ImGui.textColored(0.2f, 0.85f, 0.45f, 0.9f, item.categoriesLabel)
        } else {
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, item.categoriesLabel)
        }

        // Col 2: \u2605 favorite toggle -- FX pickers only
        if (isFxPicker) {
            ImGui.tableSetColumnIndex(2)
            if (item.type == "Filter") {
                val starred = llm.slop.liquidlsd.presets.FxShortlist.isFavorite(item.id)
                if (starred) ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.8f, 0.2f, 1.0f)
                if (ImGui.button("${if (starred) "\u2605" else "\u2606"}##star_${item.id}", -1f, 0f)) {
                    llm.slop.liquidlsd.presets.FxShortlist.toggle(item.id)
                    if (selectedCategories.contains(CATEGORY_FAVORITES)) updateItems()
                }
                if (starred) ImGui.popStyleColor()
                itemTooltip(if (starred) "Remove from the FX shortlist (the effects a slot's \u25c0 \u25b6 steps through)." else "Add to the FX shortlist (the effects a slot's \u25c0 \u25b6 steps through).")
            }
        }
    }

    /**
     * Draws the currently-configured picker (see [ensureInline]/[ensureInlineFx]) inline into
     * whatever region the caller has open -- no popup, no title bar, no Cancel. Leaving Browse
     * (or switching to a different target) is the caller's job, not this widget's.
     */
    fun drawInline(session: SessionContext) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled("${Icons.SEARCH} $title")
        }

        // ── Search Bar & View Mode Toggle ──
        ImGui.setNextItemWidth((ImGui.getContentRegionAvailX() - 300f).coerceAtLeast(120f))
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
        }
        itemTooltip("Detach the current shader from this slot.")

        ImGui.spacing()

        // ── Category Pills Row (multi-select, OR-combined; "All" is exclusive) ──
        ImGui.beginChild("##categories_pills", 0f, 48f, false, ImGuiWindowFlags.HorizontalScrollbar)
        for (i in 0 until categories.size) {
            val cat = categories[i]
            val isSelected = selectedCategories.contains(cat)
            if (isSelected) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.8f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.25f, 0.55f, 0.85f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.15f, 0.45f, 0.75f, 1.0f)
            }

            if (ImGui.button(cat)) {
                if (cat == "All") {
                    selectedCategories.clear()
                    selectedCategories.add("All")
                } else {
                    selectedCategories.remove("All")
                    if (isSelected) {
                        selectedCategories.remove(cat)
                        if (selectedCategories.isEmpty()) selectedCategories.add("All")
                    } else {
                        selectedCategories.add(cat)
                    }
                }
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
            if (ImGui.beginTable("##shader_results", resultColumnCount(), tableFlags)) {
                setupResultColumns()
                drawDimmedHeadersRow(session)

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
                        if (ImGui.beginTable("##tbl_$folder", resultColumnCount(), ImGuiTableFlags.RowBg or ImGuiTableFlags.BordersInnerV)) {
                            setupResultColumns()
                            drawDimmedHeadersRow(session)

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
                            if (ImGui.beginTable("##tbl_root", resultColumnCount(), ImGuiTableFlags.RowBg or ImGuiTableFlags.BordersInnerV)) {
                                setupResultColumns()
                                drawDimmedHeadersRow(session)

                                for (i in 0 until rootItems.size) {
                                    renderTableRow(rootItems[i], session)
                                }
                                ImGui.endTable()
                            }
                            ImGui.treePop()
                        }
                    } else {
                        if (ImGui.beginTable("##tbl_root_direct", resultColumnCount(), tableFlags)) {
                            setupResultColumns()
                            drawDimmedHeadersRow(session)

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
    }
}

