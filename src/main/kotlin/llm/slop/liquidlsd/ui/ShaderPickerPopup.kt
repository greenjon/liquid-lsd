package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiComboFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiSelectableFlags
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry
import llm.slop.liquidlsd.rendering.isf.ISFVisualSource
import llm.slop.liquidlsd.SessionContext

/**
 * High-performance, keyboard-searchable content for selecting Visual Sources (stock generator
 * types merged with saved deck presets -- a preset is just a generator with its parameter values
 * saved under a name), ISF Filters, saved single-FX files, and Mixer Transitions.
 *
 * Designed to handle 300+ items with zero-allocation per-frame filtering.
 * Supports multi-select category dropdown filtering and instant fuzzy search.
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

    /** What the user picked for a deck's generator: a stock source id (or "ext_video:..."),
     *  a saved deck preset file, or nothing. A preset is just a generator with its parameter
     *  values saved under a name, so both live in the same SOURCE picker list. */
    sealed class SourcePick {
        data class Id(val sourceId: String) : SourcePick()
        data class Saved(val file: java.io.File) : SourcePick()
        object None : SourcePick()
    }

    const val CATEGORY_FAVORITES = "\u2605 Favorites"
    const val CATEGORY_SAVED = "Saved FX"
    private const val SHOWING_WINDOW_MS = 300L
    private const val SAVED_PREFIX = "saved:"
    private const val SAVED_SOURCE_PREFIX = "preset:"

    private val isFxPicker: Boolean
        get() = pickerType == PickerType.FX_SLOT_1 || pickerType == PickerType.FX_SLOT_2 || pickerType == PickerType.FX_SLOT_3

    /** Identifies which Browse target is currently configured, so [ensureInline] knows when to reset. */
    private var activeContextKey: String? = null
    private var pickerType = PickerType.SOURCE
    private var onSelect: ((String?) -> Unit)? = null
    private var title = "Select Shader"

    private val searchBuf = ImString(64)
    /** Active category filters, OR-combined. "All" is exclusive with every other entry. */
    private var selectedCategories: MutableSet<String> = mutableSetOf("All")
    private val catCheckRef = ImBoolean()
    private var categoryPreviewText: String = "All Categories"
    private var categoryTooltipText: String = "All"

    enum class ViewMode { FOLDERS, FLAT }
    private var viewMode = ViewMode.FOLDERS

    /** Last-seen [FileSystemManager.scanAllPresets] result for the SOURCE picker, so [drawInline]
     *  can cheaply detect a rename/duplicate/delete from [drawPresetManageButton] (reference
     *  equality, same idiom [llm.slop.liquidlsd.ui.browser.PresetListPanel] uses) and refresh the
     *  list even though nothing was typed into search or the category dropdown. */
    private var lastSourcePresetsScan: List<AssetItem>? = null

    /** Controller cursor: the id of the highlighted row (null = none yet). Moves with [moveCursor], applies with [acceptCursor]. */
    private var cursorId: String? = null
    private var scrollToCursor = false
    /** The id of what the target currently uses (null = unknown), marked in the list; set by each ensureInline call. */
    private var appliedId: (() -> String?)? = null
    /** When [drawInline] last ran; lets a controller tell whether a picker is actually on screen. */
    @Volatile var lastDrawMs: Long = 0L
        private set

    val isShowing: Boolean get() = System.currentTimeMillis() - lastDrawMs < SHOWING_WINDOW_MS

    /**
     * Moves the cursor [steps] rows through the list in its flat order (clamped). Switches to the flat
     * view first, because the folder view hides the rows of collapsed groups.
     */
    fun moveCursor(steps: Int) {
        if (filteredItems.isEmpty()) return
        viewMode = ViewMode.FLAT
        val current = filteredItems.indexOfFirst { it.id == cursorId }
        val target = if (current < 0) (if (steps > 0) steps - 1 else filteredItems.size + steps) else current + steps
        cursorId = filteredItems[target.coerceIn(0, filteredItems.lastIndex)].id
        scrollToCursor = true
    }

    /** Applies the highlighted row, if any. Returns whether something was applied. */
    fun acceptCursor(): Boolean {
        val id = cursorId?.takeIf { id -> filteredItems.any { it.id == id } } ?: return false
        onSelect?.invoke(id)
        return true
    }

    /** Detaches / clears whatever the picker targets (the overflow menu's "Detach / None"). */
    fun detach() { onSelect?.invoke(null) }

    /** True when the current picker clears a target on [detach] (FX slots do; a source or a transition can't be unset). */
    val canDetach: Boolean get() = isFxPicker

    /** Steps the single active category through the dropdown's list, wrapping; the cursor restarts at the top. */
    fun stepCategory(delta: Int) {
        if (categories.isEmpty()) return
        val current = if (selectedCategories.size == 1) categories.indexOf(selectedCategories.first()).coerceAtLeast(0) else 0
        selectedCategories = mutableSetOf(categories[Math.floorMod(current + delta, categories.size)])
        cursorId = null
        updateItems()
    }

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
        cursorId = null
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
    fun ensureInline(contextKey: String, title: String, type: PickerType, applied: (() -> String?)? = null, callback: (String?) -> Unit) {
        this.title = title
        this.appliedId = applied
        this.onSelect = callback
        if (activeContextKey == contextKey) return
        activeContextKey = contextKey
        resetForType(type)
    }

    /**
     * [ensureInline] for a deck's generator: the SOURCE list merges stock [VisualSourceRegistry]
     * types with saved deck presets (`.lsd` files), so picking a row can mean either.
     */
    fun ensureInlineSource(contextKey: String, title: String, applied: (() -> String?)? = null, callback: (SourcePick) -> Unit) {
        this.title = title
        this.appliedId = applied
        this.onSelect = { id ->
            callback(
                when {
                    id == null -> SourcePick.None
                    id.startsWith(SAVED_SOURCE_PREFIX) -> SourcePick.Saved(java.io.File(id.removePrefix(SAVED_SOURCE_PREFIX)))
                    else -> SourcePick.Id(id)
                }
            )
        }
        if (activeContextKey == contextKey) return
        activeContextKey = contextKey
        resetForType(PickerType.SOURCE)
    }

    /**
     * [ensureInline] for an FX slot: [slotIndex] (0-based) picks the FX_SLOT_N category defaults,
     * and opens on the user's favorites when there are any (the extra "Saved FX" category lists
     * saved single-FX files, .lsdfx).
     */
    fun ensureInlineFx(contextKey: String, title: String, slotIndex: Int, applied: (() -> String?)? = null, callback: (FxPick) -> Unit) {
        val type = when (slotIndex) {
            0 -> PickerType.FX_SLOT_1
            1 -> PickerType.FX_SLOT_2
            else -> PickerType.FX_SLOT_3
        }
        this.title = title
        this.appliedId = applied
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
     * Whether an item belongs to at least one active category filter (OR match). "All" or an empty
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

            // ── Saved deck presets -- a preset is just a generator with its parameter values
            // saved under a name, so it belongs in the same list as the stock types above,
            // always shown (unlike FX's opt-in "Saved FX" category, matching the old preset picker's
            // always-visible list).
            FileSystemManager.scanAllPresets().forEach { asset ->
                asset.tags.forEach { tempCats.add(it) }
                val matchesSearch = asset.name.lowercase().contains(searchText) ||
                    asset.tags.any { it.lowercase().contains(searchText) }
                val matchesCategory = matchesSelectedCategories(asset.tags, "")
                if (matchesSearch && matchesCategory) {
                    filteredItems.add(
                        ShaderItem(
                            id = SAVED_SOURCE_PREFIX + asset.path,
                            displayName = asset.name,
                            categories = asset.tags,
                            type = "Preset"
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

            // Saved single-FX presets -- included only when that category is explicitly active,
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
        updateCategoryPreview()
    }

    private fun updateCategoryPreview() {
        categoryPreviewText = when {
            selectedCategories.isEmpty() || selectedCategories.contains("All") -> "All Categories"
            selectedCategories.size == 1 -> selectedCategories.first()
            selectedCategories.size == 2 -> {
                val joined = selectedCategories.joinToString(", ")
                if (joined.length <= 18) joined else "2 Categories"
            }
            else -> "${selectedCategories.size} Categories"
        }
        categoryTooltipText = if (selectedCategories.isEmpty() || selectedCategories.contains("All")) {
            "All"
        } else {
            selectedCategories.joinToString(", ")
        }
    }

    private fun toggleCategory(cat: String) {
        if (cat == "All") {
            selectedCategories.clear()
            selectedCategories.add("All")
        } else {
            selectedCategories.remove("All")
            if (selectedCategories.contains(cat)) {
                selectedCategories.remove(cat)
                if (selectedCategories.isEmpty()) selectedCategories.add("All")
            } else {
                selectedCategories.add(cat)
            }
        }
        updateItems()
    }

    private fun drawCategoryDropdown() {
        pushOpenDropdownPadding()
        if (ImGui.beginCombo("##category_filter", categoryPreviewText, ImGuiComboFlags.HeightLargest)) {
            pushOpenDropdownFont()

            for (i in 0 until categories.size) {
                val cat = categories[i]
                if (cat == "All") {
                    val isAll = selectedCategories.isEmpty() || selectedCategories.contains("All")
                    catCheckRef.set(isAll)
                    if (ImGui.checkbox("All Categories##cat_all", catCheckRef)) {
                        toggleCategory("All")
                    }
                    if (categories.size > 1) {
                        ImGui.separator()
                    }
                } else {
                    if (isFxPicker && i == 3 && categories.size > 3) {
                        ImGui.separator()
                    }
                    if (pickerType == PickerType.SOURCE && i == 2 && categories.size > 2) {
                        ImGui.separator()
                    }

                    val isSelected = selectedCategories.contains(cat)
                    catCheckRef.set(isSelected)
                    if (ImGui.checkbox("$cat##cat_$i", catCheckRef)) {
                        toggleCategory(cat)
                    }
                }
            }

            popOpenDropdownFont()
            ImGui.endCombo()
        }
        popOpenDropdownPadding()
    }

    /** SOURCE picker rows get a 3rd column too, for the saved-preset management ("...") button. */
    private val hasManageColumn: Boolean
        get() = isFxPicker || pickerType == PickerType.SOURCE

    /** Result table has 2 columns normally (Name, Categories); FX/SOURCE pickers get a 3rd. */
    private fun resultColumnCount() = if (hasManageColumn) 3 else 2

    private fun setupResultColumns() {
        ImGui.tableSetupColumn("Display Name", ImGuiTableColumnFlags.WidthStretch, 0.35f)
        ImGui.tableSetupColumn("Categories", ImGuiTableColumnFlags.WidthStretch, if (hasManageColumn) 0.55f else 0.65f)
        if (isFxPicker) {
            ImGui.tableSetupColumn("\u2605", ImGuiTableColumnFlags.WidthFixed, 36f)
        } else if (pickerType == PickerType.SOURCE) {
            ImGui.tableSetupColumn("", ImGuiTableColumnFlags.WidthFixed, 32f)
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
        val isApplied = item.id == appliedId?.invoke()
        val itemLabel = when {
            item.isExternal -> "${Icons.ACTIVITY}  ${item.displayName}"
            item.type == "Preset" -> "${Icons.DISC} ${item.displayName}"
            else -> item.displayName
        }.let { if (isApplied) "\u25cf $it" else it }
        if (item.isExternal) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.85f, 0.45f, 1.0f)
        }
        // Applies immediately and leaves the list open -- there's no popup to close, and trying
        // several picks in a row (each undoable with Ctrl+Z) is the point.
        // AllowOverlap: selectableRow spans all columns for its click/hover rect (SpanAllColumns),
        // which without this would swallow clicks on the ★ favorite / "..." manage button drawn
        // in column 2 afterward -- the later widget only wins hover if the row beneath it opts in.
        ImGui.setNextItemAllowOverlap()
        val isCursor = item.id == cursorId
        if (selectableRow(itemLabel, isCursor, flags = ImGuiSelectableFlags.AllowDoubleClick)) {
            cursorId = item.id
            onSelect?.invoke(item.id)
        }
        if (isCursor && scrollToCursor) {
            ImGui.setScrollHereY()
            scrollToCursor = false
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

        // Col 2: \u2605 favorite toggle for FX pickers; "..." preset management for saved
        // deck presets in the SOURCE picker (stock source rows leave this column blank).
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
        } else if (pickerType == PickerType.SOURCE && item.type == "Preset") {
            ImGui.tableSetColumnIndex(2)
            drawPresetManageButton(item)
        }
    }

    /** Rename/Duplicate/Delete for a saved deck preset row, reusing the same handlers the
     *  Library's Generators panel uses (see [llm.slop.liquidlsd.ui.browser.PresetListPanel]). */
    private fun drawPresetManageButton(item: ShaderItem) {
        val assetPath = item.id.removePrefix(SAVED_SOURCE_PREFIX)
        val popupId = "gen_preset_more_${item.id.hashCode()}"
        if (ImGui.smallButton("${Icons.MORE_VERTICAL}##more_${item.id}")) {
            ImGui.openPopup(popupId)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopup(popupId)) {
            pushOpenDropdownFont()
            val asset = AssetItem(path = assetPath, name = item.displayName, type = AssetType.PRESET, tags = item.categories)
            if (ImGui.menuItem("Rename / Edit Tags...")) {
                llm.slop.liquidlsd.ui.browser.BrowserPopupHandler.openRenamePresetModal(asset)
            }
            if (ImGui.menuItem("Duplicate...")) {
                llm.slop.liquidlsd.ui.browser.BrowserPopupHandler.openDuplicatePresetModal(asset)
            }
            ImGui.separator()
            if (ImGui.menuItem("Delete")) {
                llm.slop.liquidlsd.ui.browser.BrowserPopupHandler.deleteTarget = asset
                llm.slop.liquidlsd.ui.browser.BrowserPopupHandler.pendingOpenDeletePopup = true
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }

    /**
     * Draws the currently-configured picker (see [ensureInline]/[ensureInlineFx]) inline into
     * whatever region the caller has open -- no popup, no title bar, no Cancel. Leaving Browse
     * (or switching to a different target) is the caller's job, not this widget's.
     */
    fun drawInline(session: SessionContext) {
        lastDrawMs = System.currentTimeMillis()
        if (pickerType == PickerType.SOURCE) {
            val currentScan = FileSystemManager.scanAllPresets()
            if (currentScan !== lastSourcePresetsScan) {
                lastSourcePresetsScan = currentScan
                updateItems()
            }
        }

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled("${Icons.SEARCH} $title")
        }

        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            // ── Search Bar, Category Multi-Select Dropdown & Overflow Menu ──
            val availW = ImGui.getContentRegionAvailX()
            val moreBtnW = ImGui.getFrameHeight()
            val itemSpacingX = ImGui.getStyle().getItemSpacingX()
            val comboW = (availW * 0.38f).coerceIn(130f, 180f)
            val searchW = (availW - comboW - moreBtnW - itemSpacingX * 2).coerceAtLeast(80f)

            ImGui.setNextItemWidth(searchW)
            if (ImGui.inputTextWithHint("##search", "Search by name, ID or folder...", searchBuf)) {
                updateItems()
            }
            if (ImGui.isItemActive() && ImGui.isKeyPressed(ImGuiKey.Escape)) {
                searchBuf.set("")
                updateItems()
            }
            itemTooltip("Type to filter by name, ID, or folder.\nPress Esc to clear.")

            ImGui.sameLine()
            ImGui.setNextItemWidth(comboW)
            drawCategoryDropdown()
            itemTooltip("Filter by category (multi-select).\nActive: $categoryTooltipText")

            ImGui.sameLine()
            if (ImGui.button("${Icons.MORE_VERTICAL}##picker_more", moreBtnW, moreBtnW)) {
                ImGui.openPopup("picker_more_menu")
            }
            itemTooltip("View options and detach")
            pushOpenDropdownPadding()
            if (ImGui.beginPopup("picker_more_menu")) {
                pushOpenDropdownFont()
                val viewMenuLabel = if (viewMode == ViewMode.FOLDERS) "${Icons.LAYOUT_FULL} Flat list view" else "${Icons.FOLDER} Folder hierarchy view"
                if (ImGui.menuItem(viewMenuLabel)) {
                    viewMode = if (viewMode == ViewMode.FOLDERS) ViewMode.FLAT else ViewMode.FOLDERS
                }
                ImGui.separator()
                if (ImGui.menuItem("${Icons.TRASH} Detach / None")) {
                    onSelect?.invoke(null)
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()

            ImGui.separator()

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
}

