package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import java.io.File

/**
 * The unified Library browser: folder tree (about 25% of the width), name | info list (50%) and the queues (25%).
 * One pane serves every asset kind; today only [BrowseKind.SRC] is ported ([supports]) and the pane is opt-in via [enabled]
 * (the Library's "Unified" toggle) while it reaches parity with the classic four columns. See `.planning/unified-browser-pane-plan.md`.
 */
object BrowserPane {
    /** Beta switch, not persisted: the classic four-column Library is the default until the pane reaches parity. */
    var enabled = System.getProperty("lsd.unifiedBrowser") == "true"

    fun supports(kind: BrowseKind): Boolean = kind == BrowseKind.SRC

    private val scopes = HashMap<BrowseKind, BrowseScope>()
    private val searchBuffers = HashMap<BrowseKind, ImString>()

    private const val GAP = 6f
    private const val MIN_SIDE_W = 150f

    fun scopeOf(kind: BrowseKind): BrowseScope = scopes[kind] ?: BrowseScope.All

    fun draw(session: SessionContext, mixer: Mixer, parametersState: ParametersState, kind: BrowseKind) {
        val catalog = BrowseCatalogs.get(kind)
        val totalW = ImGui.getContentRegionAvailX().coerceAtLeast(3 * MIN_SIDE_W)
        val h = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
        val usable = totalW - 2 * GAP
        val sideW = (usable * 0.25f).coerceAtLeast(MIN_SIDE_W)
        val midW = (usable - 2 * sideW).coerceAtLeast(MIN_SIDE_W)
        val flags = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse

        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 6f, 6f)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, llm.slop.liquidlsd.ui.TangoPalette.PANEL_BG.u32())
        ImGui.pushStyleColor(ImGuiCol.Border, llm.slop.liquidlsd.ui.TangoPalette.PANEL_BORDER.u32())

        ImGui.beginChild("BrowserPaneTree", sideW, h, true, flags)
        drawTree(session, catalog, kind)
        ImGui.endChild()

        ImGui.sameLine(0f, GAP)
        ImGui.beginChild("BrowserPaneList", midW, h, true, flags)
        drawList(session, mixer, parametersState, catalog, kind)
        ImGui.endChild()

        ImGui.sameLine(0f, GAP)
        ImGui.beginChild("BrowserPaneQueues", sideW, h, true, flags)
        drawQueues(session, mixer, kind)
        ImGui.endChild()

        ImGui.popStyleColor(2)
        ImGui.popStyleVar(2)
    }

    private fun drawTree(session: SessionContext, catalog: BrowseCatalog, kind: BrowseKind) {
        session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.text("Folders") }
        ImGui.separator()
        if (!ImGui.beginChild("##browser_tree_scroll", 0f, 0f, false)) {
            ImGui.endChild()
            return
        }
        val current = scopeOf(kind)
        for ((i, node) in catalog.tree().withIndex()) {
            ImGui.pushID(i)
            val indent = node.depth * 12f
            if (indent > 0f) ImGui.indent(indent)
            if (!node.selectable) {
                ImGui.spacing()
                ImGui.textDisabled(node.label)
            } else if (ImGui.selectable("${node.label}##node", node.scope == current)) {
                select(kind, node.scope, catalog)
            }
            if (node.selectable || node.count > 0) {
                ImGui.sameLine()
                val countText = node.count.toString()
                ImGui.setCursorPosX(ImGui.getWindowContentRegionMaxX() - ImGui.calcTextSize(countText).x)
                ImGui.textDisabled(countText)
            }
            if (indent > 0f) ImGui.unindent(indent)
            ImGui.popID()
        }
        ImGui.endChild()
    }

    private fun select(kind: BrowseKind, scope: BrowseScope, catalog: BrowseCatalog) {
        scopes[kind] = scope
        // A playlist scope becomes the Library's active playlist, so "Add to '<playlist>'" in row menus targets it.
        if (scope is BrowseScope.Playlist && kind == BrowseKind.SRC) {
            val file = File(scope.path)
            LibraryPanel.selectedPlaylistFile = file
            LibraryPanel.getOrLoadPlaylist(file)
        }
    }

    private fun drawList(session: SessionContext, mixer: Mixer, parametersState: ParametersState, catalog: BrowseCatalog, kind: BrowseKind) {
        val scope = scopeOf(kind)
        val title = catalog.tree().firstOrNull { it.scope == scope }?.label ?: "All"
        session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.text(title) }
        ImGui.separator()

        val search = searchBuffers.getOrPut(kind) { ImString(SearchMatcher.BUFFER_SIZE) }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX())
        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            ImGui.inputTextWithHint("##browserPaneSearch", "Search name, tags, folder...", search)
        }
        if (ImGui.isItemActive() && ImGui.isKeyPressed(ImGuiKey.Escape)) {
            search.set("")
            LibraryPanel.shouldReclaimFocus = true
        }
        itemTooltip("Type to filter by name, id, folder or tags.\nPress Esc while searching to clear.")
        ImGui.separator()
        ImGui.spacing()

        val query = search.get().trim()
        val entries = catalog.rows(scope, query)
        val missing = catalog.missing(scope)
        val infoByPath = entries.associate { it.asset.path to it.info }

        if (ImGui.beginChild("##browser_list_scroll", 0f, 0f, false)) {
            PresetListPanel.filteredPresets = entries.map { it.asset }
            if (entries.isEmpty()) {
                ImGui.textDisabled(if (query.isEmpty()) "Nothing here yet" else "No matches")
            } else {
                PresetListPanel.drawRows(
                    session, mixer, parametersState, entries.map { it.asset },
                    favoriteKeys = BrowseFavorites.keys(kind),
                    infoFor = { infoByPath[it.path] ?: "" },
                    contextExtras = { asset ->
                        val entry = entries.firstOrNull { it.asset.path == asset.path }
                        if (entry != null) {
                            val fav = BrowseFavorites.isFavorite(kind, entry.key)
                            if (ImGui.menuItem(if (fav) "★ Remove from Favorites" else "☆ Add to Favorites")) {
                                BrowseFavorites.toggle(kind, entry.key)
                            }
                        }
                    }
                )
            }
            if (missing > 0) {
                ImGui.spacing()
                ImGui.textDisabled("$missing playlist item(s) not found")
            }
        }
        ImGui.endChild()
    }

    private fun drawQueues(session: SessionContext, mixer: Mixer, kind: BrowseKind) {
        val flags = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse
        val h = ImGui.getContentRegionAvailY()
        val w = ImGui.getContentRegionAvailX()
        val half = ((h - GAP) * 0.5f).coerceAtLeast(1f)
        ImGui.beginChild("BrowserPaneBgQueue", w, half, false, flags)
        BgQueueActionsPanel.draw(session, mixer)
        ImGui.endChild()
        ImGui.separator()
        ImGui.beginChild("BrowserPaneQueue", w, 0f, false, flags)
        QueueActionsPanel.draw(session, mixer)
        ImGui.endChild()
    }
}
