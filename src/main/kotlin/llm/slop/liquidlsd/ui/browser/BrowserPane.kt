package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.ui.TransitionSave
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.PlaylistManager
import llm.slop.liquidlsd.ui.UIManager
import llm.slop.liquidlsd.ui.popOpenDropdownFont
import llm.slop.liquidlsd.ui.popOpenDropdownPadding
import llm.slop.liquidlsd.ui.pushOpenDropdownFont
import llm.slop.liquidlsd.ui.pushOpenDropdownPadding
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

    fun supports(kind: BrowseKind): Boolean = true

    private val scopes = HashMap<BrowseKind, BrowseScope>()
    private val searchBuffers = HashMap<BrowseKind, ImString>()

    /** Tree rows whose children are hidden, keyed by kind then by the node's scope. */
    private val collapsed = HashMap<BrowseKind, MutableSet<BrowseScope>>()

    /** The playlist path last mirrored to/from the Library's selected playlist file of each kind, so outside changes (a new playlist) move the scope. */
    private val syncedPlaylist = HashMap<BrowseKind, String?>()

    private fun selectedPlaylistFile(kind: BrowseKind): File? = when (kind) {
        BrowseKind.SRC -> LibraryPanel.selectedPlaylistFile
        BrowseKind.FX -> LibraryPanel.selectedFxPlaylistFile
        BrowseKind.TRANS -> LibraryPanel.selectedTransitionPlaylistFile
    }

    private fun setSelectedPlaylistFile(kind: BrowseKind, file: File) {
        when (kind) {
            BrowseKind.SRC -> { LibraryPanel.selectedPlaylistFile = file; LibraryPanel.activePlaylistData = null }
            BrowseKind.FX -> LibraryPanel.selectedFxPlaylistFile = file
            BrowseKind.TRANS -> LibraryPanel.selectedTransitionPlaylistFile = file
        }
        syncedPlaylist[kind] = file.absolutePath
    }

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
        drawTree(session, mixer, catalog, kind)
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

    private fun drawTree(session: SessionContext, mixer: Mixer, catalog: BrowseCatalog, kind: BrowseKind) {
        val btnSize = ImGui.getFrameHeight()
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.text("Folders") }
        ImGui.sameLine()
        ImGui.setCursorPosX((ImGui.getWindowContentRegionMaxX() - btnSize).coerceAtLeast(ImGui.getCursorPosX()))
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.PLUS}##browserNewPlaylist", btnSize, btnSize)) {
                BrowserPopupHandler.pendingOpenNewPlaylistPopup = true
            }
        }
        itemTooltip("Create a new playlist.")
        ImGui.separator()
        if (!ImGui.beginChild("##browser_tree_scroll", 0f, 0f, false)) {
            ImGui.endChild()
            return
        }
        val tree = catalog.tree()
        syncPlaylistSelection(kind, tree)
        val current = scopeOf(kind)
        val hidden = collapsed.getOrPut(kind) { HashSet() }
        var hideBelow = Int.MAX_VALUE
        for ((i, node) in tree.withIndex()) {
            if (node.depth > hideBelow) continue
            hideBelow = Int.MAX_VALUE
            val expandable = tree.getOrNull(i + 1)?.let { it.depth > node.depth } == true
            val isCollapsed = expandable && node.scope in hidden
            if (isCollapsed) hideBelow = node.depth
            ImGui.pushID(i)
            val indent = node.depth * 12f
            if (indent > 0f) ImGui.indent(indent)
            if (expandable) {
                if (ImGui.smallButton(if (isCollapsed) "+##fold" else "-##fold")) {
                    if (isCollapsed) hidden.remove(node.scope) else hidden.add(node.scope)
                }
                ImGui.sameLine(0f, 4f)
            }
            if (!node.selectable) {
                ImGui.textDisabled(node.label)
            } else if (ImGui.selectable("${node.label}##node", node.scope == current)) {
                select(kind, node.scope)
            }
            val playlist = node.scope as? BrowseScope.Playlist
            if (playlist != null) playlistContextMenu(session, mixer, kind, playlist)
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

    /** Right-click menu of a playlist node: queue transport, rename, clone, delete. */
    private fun playlistContextMenu(session: SessionContext, mixer: Mixer, kind: BrowseKind, scope: BrowseScope.Playlist) {
        val file = File(scope.path)
        if (!ImGui.beginPopupContextItem("playlist_node_menu")) return
        pushOpenDropdownFont()
        when (kind) {
            BrowseKind.SRC -> {
                if (ImGui.menuItem("Play now in A/B Queue (and replace queue)")) session.playQueueManager.playPlaylistNow(file, mixer)
                if (ImGui.menuItem("Insert into A/B Queue after current")) session.playQueueManager.insertPlaylistAfterCurrent(file)
                if (ImGui.menuItem("Add to the bottom of A/B Queue")) session.playQueueManager.appendPlaylistToQueue(file)
                ImGui.separator()
                if (ImGui.menuItem("Play now in BG Queue (and replace queue)")) BgQueueManager.playPlaylistNow(file, mixer)
                if (ImGui.menuItem("Insert into BG Queue after current")) BgQueueManager.insertPlaylistAfterCurrent(file)
                if (ImGui.menuItem("Add to the bottom of BG Queue")) BgQueueManager.appendPlaylistToQueue(file)
            }
            BrowseKind.FX -> {
                if (ImGui.menuItem("Add All to Live FX Queue (A/B)")) FXQueueManager.appendToQueue(file)
                if (ImGui.menuItem("Add All to BG FX Queue")) FXBgQueueManager.appendToQueue(file)
            }
            BrowseKind.TRANS -> if (ImGui.menuItem("Load Playlist to Live Queue")) TransitionQueueManager.appendToQueue(file)
        }
        ImGui.separator()
        val assetType = when (kind) {
            BrowseKind.SRC -> AssetType.PLAYLIST
            BrowseKind.FX -> AssetType.FX_PLAYLIST
            BrowseKind.TRANS -> AssetType.TRANSITION_PLAYLIST
        }
        val asset = AssetItem(path = file.absolutePath, name = file.nameWithoutExtension, type = assetType)
        if (ImGui.menuItem("Rename...")) {
            BrowserPopupHandler.renameTarget = asset
            BrowserPopupHandler.renameBuffer.set(asset.name)
            BrowserPopupHandler.pendingOpenRenamePopup = true
        }
        if (ImGui.menuItem("Clone")) {
            FileSystemManager.cloneFile(file.absolutePath).onSuccess { newPath ->
                scopes[kind] = BrowseScope.Playlist(File(newPath).absolutePath)
                setSelectedPlaylistFile(kind, File(newPath))
            }
        }
        if (ImGui.menuItem("Delete")) {
            BrowserPopupHandler.deleteTarget = asset
            BrowserPopupHandler.pendingOpenDeletePopup = true
        }
        popOpenDropdownFont()
        ImGui.endPopup()
    }

    /** Follows the Library's selected playlist file when something else changed it (a playlist just created) and drops a scope whose playlist is gone. */
    private fun syncPlaylistSelection(kind: BrowseKind, tree: List<BrowseNode>) {
        val external = selectedPlaylistFile(kind)?.absolutePath
        if (external != syncedPlaylist[kind]) {
            syncedPlaylist[kind] = external
            val target = tree.firstOrNull { (it.scope as? BrowseScope.Playlist)?.path == external }
            if (target != null) scopes[kind] = target.scope
        }
        val scope = scopes[kind]
        if (scope is BrowseScope.Playlist && tree.none { it.scope == scope }) scopes.remove(kind)
    }

    private fun select(kind: BrowseKind, scope: BrowseScope) {
        scopes[kind] = scope
        // A playlist scope becomes the Library's active playlist, so "Add to '<playlist>'" in row menus targets it.
        if (scope is BrowseScope.Playlist) {
            val file = File(scope.path)
            setSelectedPlaylistFile(kind, file)
            if (kind == BrowseKind.SRC) LibraryPanel.getOrLoadPlaylist(file)
        }
    }

    private fun drawList(session: SessionContext, mixer: Mixer, parametersState: ParametersState, catalog: BrowseCatalog, kind: BrowseKind) {
        val scope = scopeOf(kind)
        val title = catalog.tree().firstOrNull { it.scope == scope }?.label ?: "All"
        drawListHeader(session, mixer, parametersState, title, kind)
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
        val assets = entries.map { it.asset }
        val playlistRows = playlistRowsFor(catalog, scope, query)

        if (ImGui.beginChild("##browser_list_scroll", 0f, 0f, false)) {
            when (kind) {
                BrowseKind.SRC -> PresetListPanel.filteredPresets = assets
                BrowseKind.FX -> FXBrowserPanel.filteredRows = assets
                BrowseKind.TRANS -> TransitionBrowserPanel.filteredRows = assets
            }
            if (entries.isEmpty()) {
                ImGui.textDisabled(
                    when {
                        query.isNotEmpty() -> "No matches"
                        scope is BrowseScope.Playlist -> "Playlist is empty. Drag items here or use an item's menu."
                        else -> "Nothing here yet"
                    }
                )
                playlistRows?.finish()
            } else {
                val infoFor = { asset: AssetItem -> infoByPath[asset.path] ?: "" }
                val extras = { asset: AssetItem ->
                    val entry = entries.firstOrNull { it.asset.path == asset.path }
                    // FX stock rows already carry a Favorites item in their own menu.
                    if (entry != null && kind != BrowseKind.FX) {
                        val fav = BrowseFavorites.isFavorite(kind, entry.key)
                        if (ImGui.menuItem(if (fav) "★ Remove from Favorites" else "☆ Add to Favorites")) {
                            BrowseFavorites.toggle(kind, entry.key)
                        }
                    }
                    if (playlistRows != null && ImGui.menuItem("Remove from playlist")) removeFromPlaylist(kind, playlistRows, assets)
                }
                when (kind) {
                    BrowseKind.SRC -> PresetListPanel.drawRows(
                        session, mixer, parametersState, assets,
                        favoriteKeys = BrowseFavorites.keys(kind), infoFor = infoFor, contextExtras = extras, playlistRows = playlistRows
                    )
                    BrowseKind.FX -> FXBrowserPanel.drawRows(session, mixer, assets, infoFor, extras, playlistRows)
                    BrowseKind.TRANS -> TransitionBrowserPanel.drawRows(session, mixer, assets, infoFor, extras, playlistRows)
                }
            }
            if (missing > 0) {
                ImGui.spacing()
                ImGui.textDisabled("$missing playlist item(s) not found")
            }
        }
        ImGui.endChild()

        // Delete / Backspace: inside a playlist it removes the rows from the playlist, elsewhere it deletes from the library with confirmation.
        val io = ImGui.getIO()
        val selected = selectedAssets(kind, assets)
        if (selected.isNotEmpty() && !io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper &&
            (ImGui.isKeyPressed(ImGuiKey.Delete, false) || ImGui.isKeyPressed(ImGuiKey.Backspace, false))
        ) {
            if (playlistRows != null) {
                removeFromPlaylist(kind, playlistRows, assets)
            } else {
                selected.filter { !isStock(it) }.takeIf { it.isNotEmpty() }?.let { BrowserPopupHandler.openDeleteConfirmation(it) }
            }
        }
    }

    private fun isStock(asset: AssetItem): Boolean =
        asset.type == AssetType.SOURCE_STOCK || asset.type == AssetType.FX_STOCK || asset.type == AssetType.TRANSITION_STOCK

    /** The selected rows of the list, in list order. SRC supports multi-select; FX and Transitions keep a single selected row. */
    private fun selectedAssets(kind: BrowseKind, assets: List<AssetItem>): List<AssetItem> = when (kind) {
        BrowseKind.SRC -> PresetListPanel.selection.getSelectedInOrder(assets)
        BrowseKind.FX -> assets.filter { it.path == FXBrowserPanel.selectedAsset?.path }
        BrowseKind.TRANS -> assets.filter { it.path == TransitionBrowserPanel.selectedAsset?.path }
    }

    private fun clearSelection(kind: BrowseKind) {
        when (kind) {
            BrowseKind.SRC -> PresetListPanel.selection.clear()
            BrowseKind.FX -> FXBrowserPanel.selectedAsset = null
            BrowseKind.TRANS -> TransitionBrowserPanel.selectedAsset = null
        }
    }

    /** Title row of the list with the per-kind "+" (and, for Sources, "..." maintenance) buttons, as in the classic columns. */
    private fun drawListHeader(session: SessionContext, mixer: Mixer, parametersState: ParametersState, title: String, kind: BrowseKind) {
        val btnSize = ImGui.getFrameHeight()
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.text(title) }
        ImGui.sameLine()
        val buttons = if (kind == BrowseKind.SRC) 2 else 1
        val rightX = ImGui.getWindowContentRegionMaxX() - (btnSize * buttons + ImGui.getStyle().itemSpacingX * (buttons - 1))
        if (rightX > ImGui.getCursorPosX()) ImGui.setCursorPosX(rightX)

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.PLUS}##browser_new", btnSize, btnSize)) {
                when (kind) {
                    BrowseKind.SRC -> ImGui.openPopup("browser_new_preset_popup")
                    BrowseKind.FX -> ImGui.openPopup("create_new_fx_popup")
                    BrowseKind.TRANS -> TransitionSave.requestSaveCurrent(session, mixer)
                }
            }
        }
        itemTooltip(
            when (kind) {
                BrowseKind.SRC -> "New blank preset on a deck..."
                BrowseKind.FX -> "Save FX slot or 3-slot chain from a deck..."
                BrowseKind.TRANS -> "Save current mixer transition as a preset (.lsdtrans)..."
            }
        )
        if (kind == BrowseKind.FX) {
            FXBrowserPanel.drawCreatePopup(session, mixer)
            FXBrowserPanel.drawOverwritePopup(session, mixer)
        }
        if (kind != BrowseKind.SRC) return

        pushOpenDropdownPadding()
        if (ImGui.beginPopup("browser_new_preset_popup")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("New blank preset on:")
            ImGui.separator()
            for ((label, deck) in listOf("Deck A" to mixer.deckA, "Deck B" to mixer.deckB, "Deck BG" to mixer.deckBG, "Deck PV" to mixer.deckPV)) {
                if (ImGui.menuItem(label)) {
                    UIManager.newPresetSafely(mixer, deck)
                    parametersState.activeTopTab = label
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()

        ImGui.sameLine()
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.MORE_VERTICAL}##browser_more", btnSize, btnSize)) ImGui.openPopup("browser_more_popup")
        }
        itemTooltip("More actions.")
        pushOpenDropdownPadding()
        if (ImGui.beginPopup("browser_more_popup")) {
            pushOpenDropdownFont()
            if (ImGui.menuItem("Restore Factory Presets")) FileSystemManager.restoreFactoryPresets()
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }

    /** Playlist editing context for the list, or null outside a playlist. Reordering is off while a search narrows the rows. */
    private fun playlistRowsFor(catalog: BrowseCatalog, scope: BrowseScope, query: String): PlaylistRows? {
        if (scope !is BrowseScope.Playlist) return null
        val file = File(scope.path)
        val edit: PlaylistEdit = when (catalog.kind) {
            BrowseKind.SRC -> LibraryPanel.getOrLoadPlaylist(file)?.let { PresetPlaylistEdit(it) }
            else -> TokenPlaylistEdit.open(catalog.kind, file)
        } ?: return null
        val slots = catalog.playlistSlots(scope)
        if (slots.size != edit.size) return null // file changed under us; the catalog catches up next frame
        val rowToIndex = if (query.isEmpty()) {
            slots.indices.filter { slots[it] != null }
        } else {
            val visible = catalog.rows(scope, query).map { it.key }.toSet()
            slots.indices.filter { slots[it]?.key in visible }
        }
        return PlaylistRows(edit, rowToIndex, reorderEnabled = query.isEmpty())
    }

    /** Removes the selected rows (the right-clicked row is selected first by the row renderer) from the playlist. */
    private fun removeFromPlaylist(kind: BrowseKind, rows: PlaylistRows, assets: List<AssetItem>) {
        val selected = selectedAssets(kind, assets).map { it.path }.toSet()
        rows.edit.remove(assets.indices.filter { assets[it].path in selected }.map { rows.indexOfRow(it) })
        clearSelection(kind)
    }

    private fun drawQueues(session: SessionContext, mixer: Mixer, kind: BrowseKind) {
        val flags = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse
        val h = ImGui.getContentRegionAvailY()
        val w = ImGui.getContentRegionAvailX()
        if (kind == BrowseKind.TRANS) {
            // Transitions have a single queue, so it gets the whole column.
            ImGui.beginChild("BrowserPaneQueue", w, h, false, flags)
            TransitionQueuePanel.draw(session, mixer)
            ImGui.endChild()
            return
        }
        val half = ((h - GAP) * 0.5f).coerceAtLeast(1f)
        ImGui.beginChild("BrowserPaneBgQueue", w, half, false, flags)
        if (kind == BrowseKind.FX) FXBgQueueActionsPanel.draw(session, mixer) else BgQueueActionsPanel.draw(session, mixer)
        ImGui.endChild()
        ImGui.separator()
        ImGui.beginChild("BrowserPaneQueue", w, 0f, false, flags)
        if (kind == BrowseKind.FX) FXQueueActionsPanel.draw(session, mixer) else QueueActionsPanel.draw(session, mixer)
        ImGui.endChild()
    }
}
