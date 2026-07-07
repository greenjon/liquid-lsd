package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import kotlinx.serialization.json.Json
import llm.slop.spirals.config.ProjectConfig
import llm.slop.spirals.models.DeckPatchDto
import mu.KotlinLogging
import java.io.File

/**
 * Searchable, tag-filtered deck preset browser rendered as an ImGui popup.
 *
 * Replaces the flat `ImGui.combo` in the deck preset row.  The browser is
 * opened via [open] and drawn every frame via [draw].  When the user selects
 * a preset [onSelect] is called with the clean preset name (no dirty suffix).
 *
 * Tags are read from the `tags` field of each `DeckPatchDto` at scan time.
 * The scan is triggered once on [open] and again whenever the user clicks
 * the Refresh button inside the popup.
 *
 * Phase 2c: the Save-As modal inside this browser includes a Tags input so
 * the user can attach tags when saving a new preset.
 */
class DeckPresetBrowser(
    /** Unique suffix used to namespace ImGui IDs -- pass "A" or "B". */
    private val deckLabel: String
) {
    private val logger = KotlinLogging.logger {}

    private val json = Json { ignoreUnknownKeys = true }

    // -- Popup state -----------------------------------------------------------

    private var pendingOpen = false

    // -- Scan results ----------------------------------------------------------

    data class PresetEntry(
        val name: String,
        val tags: List<String>,
        val file: File
    )

    private var allPresets: List<PresetEntry> = emptyList()
    private var allTags: List<String> = emptyList()

    // -- Filter state ----------------------------------------------------------

    private val searchInput = ImString(64)
    private val activeTags = mutableSetOf<String>()

    // -- Save-As state (Phase 2c) ----------------------------------------------

    private var showSaveAs = false
    private val saveAsName = ImString(64)
    private val saveAsTags = ImString(128)   // comma-separated

    private companion object {
        private const val POPUP_W = 400f
        private const val POPUP_H = 800f
        private const val SAVE_AS_W = 360f
        private const val POPUP_MARGIN = 48f
    }

    // -- Public API ------------------------------------------------------------

    /** Schedule the browser to open on the next [draw] call. */
    fun open() {
        scanPresets()
        pendingOpen = true
    }

    /**
     * Draw the browser popup.  Must be called every frame from the render thread.
     *
     * @param activePresetName  The currently loaded preset name (no dirty suffix).
     * @param isDirty           Whether the deck has unsaved changes.
     * @param onSelect          Called with the chosen preset name when the user clicks a row.
     *                          Pass `null` to clear the active preset ("None").
     * @param onSaveAs          Called with (name, tags) when the user confirms Save As.
     */
    fun draw(
        activePresetName: String?,
        isDirty: Boolean,
        onSelect: (String?) -> Unit,
        onSaveAs: (name: String, tags: List<String>) -> Unit
    ) {
        val popupId = "Preset Browser - Deck $deckLabel###presetBrowser$deckLabel"

        if (pendingOpen) {
            ImGui.openPopup(popupId)
            pendingOpen = false
        }

        val displayW = ImGui.getIO().displaySizeX
        val displayH = ImGui.getIO().displaySizeY
        val popupW = POPUP_W.coerceAtMost((displayW - POPUP_MARGIN).coerceAtLeast(280f))
        val popupH = POPUP_H.coerceAtMost((displayH - POPUP_MARGIN).coerceAtLeast(360f))

        ImGui.setNextWindowSize(popupW, popupH, imgui.flag.ImGuiCond.Always)
        ImGui.setNextWindowPos(
            displayW * 0.5f,
            displayH * 0.5f,
            imgui.flag.ImGuiCond.Always,
            0.5f, 0.5f
        )

        val flags = ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove
        if (!ImGui.beginPopupModal(popupId, flags)) return

        drawSearchBar()
        ImGui.spacing()
        drawTagRow()
        ImGui.separator()
        drawPresetList(activePresetName, isDirty, onSelect)
        ImGui.separator()
        drawFooter(activePresetName, isDirty, onSelect, onSaveAs)

        // Save-As sub-modal (Phase 2c)
        drawSaveAsModal(onSaveAs)

        ImGui.endPopup()
    }

    // -- Private drawing helpers -----------------------------------------------

    private fun drawSearchBar() {
        ImGui.text("Search:")
        ImGui.sameLine()
        ImGui.pushItemWidth((ImGui.getContentRegionAvailX() - 70f).coerceAtLeast(80f))
        ImGui.inputText("##search$deckLabel", searchInput)
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Type to search presets by name.")
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        if (ImGui.button("Refresh")) {
            scanPresets()
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Re-scan presets directory.")
        }
    }

    private fun drawTagRow() {
        if (allTags.isEmpty()) {
            ImGui.textDisabled("(no tags)")
            return
        }
        ImGui.text("Tags:")
        ImGui.sameLine()
        for (tag in allTags) {
            val active = activeTags.contains(tag)
            if (active) {
                // Highlighted pill
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,       0.3f, 0.7f, 0.4f, 1.0f)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.4f, 0.8f, 0.5f, 1.0f)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  0.2f, 0.6f, 0.3f, 1.0f)
            }
            if (ImGui.button("[$tag]##tag_${deckLabel}_$tag")) {
                if (active) activeTags.remove(tag) else activeTags.add(tag)
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Toggle tag '$tag' filter. Active tags filter listed presets.")
            }
            if (active) ImGui.popStyleColor(3)
            ImGui.sameLine()
        }
        // Clear tags button
        if (activeTags.isNotEmpty()) {
            if (ImGui.button("X Clear##clearTags$deckLabel")) {
                activeTags.clear()
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Clear all active tag filters.")
            }
        }
    }

    private fun drawPresetList(
        activePresetName: String?,
        isDirty: Boolean,
        onSelect: (String?) -> Unit
    ) {
        val footerHeight = ImGui.getFrameHeightWithSpacing() + 15f
        val listH = (ImGui.getContentRegionAvailY() - footerHeight).coerceAtLeast(ImGui.getFrameHeightWithSpacing() * 3f)
        ImGui.beginChild("##presetList$deckLabel", ImGui.getContentRegionAvailX(), listH, false)

        val filtered = filteredPresets()

        // "None" entry at the top
        val noneSelected = activePresetName == null
        if (ImGui.selectable("None##none$deckLabel", noneSelected)) {
            onSelect(null)
            ImGui.closeCurrentPopup()
        }

        for (entry in filtered) {
            val isActive = entry.name == activePresetName
            val rowLabel = if (isActive && isDirty) "${entry.name} *" else entry.name
            val tagSuffix = if (entry.tags.isNotEmpty()) "  [${entry.tags.joinToString(", ")}]" else ""

            if (ImGui.selectable("$rowLabel$tagSuffix##preset_${deckLabel}_${entry.name}", isActive)) {
                onSelect(entry.name)
                ImGui.closeCurrentPopup()
            }
        }

        if (filtered.isEmpty() && allPresets.isNotEmpty()) {
            ImGui.textDisabled("No presets match the current filter.")
        } else if (allPresets.isEmpty()) {
            ImGui.textDisabled("No presets found in ${ProjectConfig.Paths.PATCHES_DIR}/")
        }

        ImGui.endChild()
    }

    private fun drawFooter(
        activePresetName: String?,
        isDirty: Boolean,
        onSelect: (String?) -> Unit,
        onSaveAs: (name: String, tags: List<String>) -> Unit
    ) {
        ImGui.spacing()

        // Save As button
        if (ImGui.button("Save As...##saveAs$deckLabel")) {
            saveAsName.set(activePresetName ?: "")
            // Pre-populate tags from the active preset if available
            val activeTags = allPresets.firstOrNull { it.name == activePresetName }?.tags ?: emptyList()
            saveAsTags.set(activeTags.joinToString(", "))
            showSaveAs = true
        }

        ImGui.sameLine()
        if (ImGui.button("Close##close$deckLabel")) {
            ImGui.closeCurrentPopup()
        }
    }

    /** Phase 2c: Save-As sub-modal with name + tags input. */
    private fun drawSaveAsModal(onSaveAs: (name: String, tags: List<String>) -> Unit) {
        val saveAsId = "Save Preset As###saveAsModal$deckLabel"

        if (showSaveAs) {
            ImGui.openPopup(saveAsId)
            showSaveAs = false
        }

        val saveAsW = SAVE_AS_W.coerceAtMost((ImGui.getIO().displaySizeX - POPUP_MARGIN).coerceAtLeast(260f))
        ImGui.setNextWindowSize(saveAsW, 0f, imgui.flag.ImGuiCond.Appearing)
        val flags = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoMove
        if (!ImGui.beginPopupModal(saveAsId, flags)) return

        ImGui.text("Name:")
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
        ImGui.inputText("##saveAsName$deckLabel", saveAsName)
        ImGui.popItemWidth()

        ImGui.spacing()
        ImGui.text("Tags (comma-separated):")
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
        ImGui.inputText("##saveAsTags$deckLabel", saveAsTags)
        ImGui.popItemWidth()
        ImGui.textDisabled("e.g.  ambient, geo, strobe")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        if (ImGui.button("Save##confirmSaveAs$deckLabel", 90f, 0f)) {
            val name = saveAsName.get().trim()
            if (name.isNotEmpty()) {
                val tags = saveAsTags.get()
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                onSaveAs(name, tags)
                // Rescan so the new preset appears immediately
                scanPresets()
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.sameLine()
        if (ImGui.button("Cancel##cancelSaveAs$deckLabel", 90f, 0f)) {
            ImGui.closeCurrentPopup()
        }

        ImGui.endPopup()
    }

    // -- Filtering -------------------------------------------------------------

    private fun filteredPresets(): List<PresetEntry> {
        val query = searchInput.get().trim().lowercase()
        return allPresets.filter { entry ->
            val nameMatch = query.isEmpty() || entry.name.lowercase().contains(query)
            val tagMatch  = activeTags.isEmpty() || activeTags.all { t -> entry.tags.contains(t) }
            nameMatch && tagMatch
        }
    }

    // -- Preset scanning -------------------------------------------------------

    /**
     * Reads all `.lsd` and `.json` files from the configured patches directory, deserialises just enough
     * to extract the `tags` field, and rebuilds [allPresets] and [allTags].
     *
     * This runs on the render (main) thread -- file I/O is acceptable here
     * because it only happens on explicit user action (open / refresh), not
     * every frame.
     */
    private fun scanPresets() {
        val dir = File(ProjectConfig.Paths.PATCHES_DIR)
        if (!dir.exists()) dir.mkdirs()

        val files = dir.listFiles { _, name -> name.endsWith(".lsd") || name.endsWith(".json") }
            ?.sortedBy { it.nameWithoutExtension.lowercase() }
            ?: emptyList()

        val entries = files.mapNotNull { file ->
            try {
                val content = file.readText()
                val dto = json.decodeFromString<DeckPatchDto>(content)
                PresetEntry(
                    name = file.nameWithoutExtension,
                    tags = dto.tags,
                    file = file
                )
            } catch (e: Exception) {
                logger.warn { "Could not parse preset ${file.name}: ${e.message}" }
                // Still include the file, just with no tags
                PresetEntry(name = file.nameWithoutExtension, tags = emptyList(), file = file)
            }
        }

        allPresets = entries
        allTags = entries.flatMap { it.tags }.distinct().sorted()
        logger.debug { "Scanned ${entries.size} deck presets, ${allTags.size} unique tags" }
    }
}
