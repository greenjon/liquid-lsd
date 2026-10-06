package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString

/**
 * Perform page editor in Preferences > MIDI Controls: lists the pages in the Perform tab strip, copies a
 * built-in to an editable user file, creates new pages and edits the name and the four rows of user pages.
 * Edits are validated and saved at once by [PerfPageStore.saveUser]; the tab strip reads the store every frame.
 */
object PerformPagesPanel {
    private var message: String? = null
    private var problems: List<String> = emptyList()
    private val newName = ImString(32)
    private val nameFields = HashMap<String, ImString>()
    private val duplicateNames = HashMap<String, ImString>()

    private val catalogIds: List<String> by lazy { PerfRows.CATALOG.keys.toList() }

    /** Readable row label: the catalog id plus what it shows. */
    private fun rowLabel(id: String): String {
        val row = PerfRows.CATALOG[id] ?: return id
        val half = row.pinnedMode?.let { " ($it only)" } ?: ""
        return "$id  -  ${row.groupLabel}$half"
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext) {
        if (!ImGui.collapsingHeader("${Icons.SETTINGS} Perform Pages##perform_pages", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) return
        val theme = session.uiTheme
        val store = PerfPageStore.default
        theme.caption("Pages are the tabs of the Perform view; each shows four rows. A controller profile selects one per bank with perform.<id>. Built-in pages are read-only: copy one to edit it.")

        for (page in store.all()) {
            ImGui.pushID("page_${page.id}")
            val source = store.sourceOf(page.id)
            val label = when (source) {
                PerfPageStore.Source.BUILT_IN -> "built-in"
                PerfPageStore.Source.USER -> "user file"
                PerfPageStore.Source.USER_OVERRIDE -> "user file (overrides built-in)"
                null -> ""
            }
            ImGui.text(page.name)
            ImGui.sameLine()
            theme.caption("$label  |  controller name: perform.${page.id}")
            val shown = ImBoolean(page.id !in theme.hiddenPerformPages)
            if (ImGui.checkbox("Show in tab strip##show", shown)) {
                if (theme.setPerformPageHidden(page.id, !shown.get())) AppPreferencesStore.savePreferences()
                else message = "At least one page must stay in the tab strip"
            }
            itemTooltip("Hidden pages leave the tab strip, but a controller bank that selects perform.${page.id} still shows it.")
            if (source == PerfPageStore.Source.BUILT_IN) {
                for (placement in page.rows) theme.caption("  ${rowLabel(placement.row)}")
                if (ImGui.button("${Icons.COPY} Copy to User File##copy")) {
                    message = store.copyBuiltInToUser(page.id) ?: "Copied to library/perform_pages/${page.id}.json"
                }
                itemTooltip("Writes an editable copy to library/perform_pages/. It replaces the built-in page until you delete it.")
            } else {
                drawEditor(store, page)
                if (ImGui.button("${Icons.TRASH} Delete User File##delete")) {
                    nameFields.remove(page.id)
                    message = if (store.deleteUser(page.id)) "Deleted user page ${page.id}" else "Could not delete ${page.id}"
                }
                itemTooltip("Removes the user file. A built-in page with the same id becomes active again; a page of your own disappears from the strip.")
            }
            drawDuplicate(store, page, theme)
            ImGui.popID()
            ImGui.spacing()
        }

        for (rejected in store.rejected()) {
            theme.captionColored(0.95f, 0.35f, 0.3f, 1.0f, "${rejected.file.name} is not loaded:")
            for (problem in rejected.problems) theme.captionColored(0.95f, 0.35f, 0.3f, 1.0f, "  - $problem")
        }
        for (warning in store.warnings()) {
            theme.captionColored(0.95f, 0.75f, 0.2f, 1.0f, "${warning.file.name}:")
            for (problem in warning.problems) theme.captionColored(0.95f, 0.75f, 0.2f, 1.0f, "  - $problem")
        }
        for (problem in problems) theme.captionColored(0.95f, 0.35f, 0.3f, 1.0f, "  - $problem")

        ImGui.setNextItemWidth(160f)
        ImGui.inputTextWithHint("##new_page_name", "New page name", newName)
        ImGui.sameLine()
        val name = newName.get().trim()
        val id = PerfPageDef.idFromName(name)
        val canCreate = id.isNotEmpty() && store.get(id) == null
        if (!canCreate) ImGui.beginDisabled()
        if (ImGui.button("${Icons.PLUS} New Page##new_page")) {
            val page = PerfPageDef(id, name, rows = listOf("deck.A.src", "deck.A.fx", "deck.B.src", "deck.B.fx").map { RowPlacement(it) })
            problems = store.saveUser(page)
            message = if (problems.isEmpty()) "Created page $id" else null
            if (problems.isEmpty()) newName.set("")
        }
        if (!canCreate) ImGui.endDisabled()
        itemTooltip("Creates a page with Deck A and Deck B (source over FX) to start from, then choose each row.", allowWhenDisabled = true)
        ImGui.sameLine()
        if (ImGui.button("${Icons.REFRESH} Reload Pages##reload_pages")) {
            store.reload()
            nameFields.clear()
            message = "Reloaded perform pages"
        }
        itemTooltip("Re-reads library/perform_pages/*.json after you edit a file by hand.")
        message?.let { theme.caption(it) }
    }

    /** Copies [page]'s rows into a new user page whose id comes from the name typed here, leaving [page] alone. */
    private fun drawDuplicate(store: PerfPageStore, page: PerfPageDef, theme: UITheme) {
        val field = duplicateNames.getOrPut(page.id) { ImString(32) }
        ImGui.setNextItemWidth(160f)
        ImGui.inputTextWithHint("##dup_name", "Name for a copy", field)
        ImGui.sameLine()
        val name = field.get().trim()
        val id = PerfPageDef.idFromName(name)
        val taken = id.isNotEmpty() && store.get(id) != null
        if (id.isEmpty() || taken) ImGui.beginDisabled()
        if (ImGui.button("${Icons.COPY} Copy As New Page##dup")) {
            problems = store.saveUser(page.copy(id = id, name = name))
            message = if (problems.isEmpty()) "Created page $id (controller name perform.$id)" else null
            if (problems.isEmpty()) field.set("")
        }
        if (id.isEmpty() || taken) ImGui.endDisabled()
        itemTooltip(
            if (taken) "A page with the id '$id' already exists."
            else "Makes an independent page with its own controller name${if (id.isEmpty()) "" else " (perform.$id)"}, so this one stays as it is.",
            allowWhenDisabled = true
        )
    }

    private fun drawEditor(store: PerfPageStore, page: PerfPageDef) {
        val field = nameFields.getOrPut(page.id) { ImString(32).also { it.set(page.name) } }
        ImGui.setNextItemWidth(200f)
        ImGui.inputText("Name##name", field)
        if (ImGui.isItemDeactivatedAfterEdit()) save(store, page.copy(name = field.get().trim()))

        page.rows.forEachIndexed { i, placement ->
            val current = ImInt(catalogIds.indexOf(placement.row).coerceAtLeast(0))
            ImGui.setNextItemWidth(380f)
            val preview = rowLabel(placement.row)
            if (ImGui.beginCombo("Row ${i + 1}##row$i", preview)) {
                for ((idx, id) in catalogIds.withIndex()) {
                    if (ImGui.selectable(rowLabel(id), idx == current.get())) {
                        save(store, page.copy(rows = page.rows.toMutableList().also { it[i] = RowPlacement(id) }))
                    }
                }
                ImGui.endCombo()
            }
        }
    }

    private fun save(store: PerfPageStore, edited: PerfPageDef) {
        problems = store.saveUser(edited)
        message = null
    }
}
