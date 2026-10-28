package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.SmoothScroll
import llm.slop.liquidlsd.ui.ImGuiFileBrowser
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.ui.ButtonChrome
import imgui.ImGui
import imgui.ImGuiInputTextCallbackData
import imgui.callback.ImGuiInputTextCallback
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiPopupFlags
import llm.slop.liquidlsd.ui.shortcuts.ShortcutManager
import org.lwjgl.glfw.GLFW
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
import llm.slop.liquidlsd.ui.TangoPalette
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import java.io.File

/**
 * The unified Library browser: folder tree (25% of the width) and the name | info list (the rest; the info column is half the dock). The queues live on the Queues tab ([QueuesPane]).
 * One pane serves every asset kind (sources, FX, transitions). See `.planning/unified-browser-pane-plan.md`.
 */
object BrowserPane {
    private val scopes = HashMap<BrowseKind, BrowseScope>()
    private val searchBuffers = HashMap<BrowseKind, ImString>()

    /** Kinds whose search box takes keyboard focus on its next draw (Ctrl+F); consumed by [drawList]. */
    internal val searchFocusRequests = HashSet<BrowseKind>()

    fun focusSearch(kind: BrowseKind) { searchFocusRequests.add(kind) }

    /** Tree rows whose children are hidden, keyed by kind then by the node's scope. */
    private val collapsed = HashMap<BrowseKind, MutableSet<BrowseScope>>()

    /** The folded tree rows of [kind]; the Tags group starts folded. */
    private fun hiddenOf(kind: BrowseKind): MutableSet<BrowseScope> = collapsed.getOrPut(kind) { hashSetOf(BrowseScope.TagsHeader) }

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

    /** The controller's tree cursor per kind. Null means "on the selected scope"; stepping moves it, [acceptTree] selects it. */
    private val treeCursors = HashMap<BrowseKind, BrowseScope>()

    private fun visibleScopes(kind: BrowseKind): List<BrowseScope> =
        visibleSelectableScopes(treeOf(BrowseCatalogs.get(kind), kind), hiddenOf(kind))

    /** Where the tree cursor is now: the stepped-to row while it is still visible, else the selected scope. */
    fun treeCursorOf(kind: BrowseKind): BrowseScope = treeCursors[kind]?.takeIf { it in visibleScopes(kind) } ?: scopeOf(kind)

    /** Number of rows the tree cursor can visit. */
    fun treeSize(kind: BrowseKind): Int = visibleScopes(kind).size

    /** Row of the tree cursor among the visible rows, or -1. */
    fun treeIndex(kind: BrowseKind): Int = visibleScopes(kind).indexOf(treeCursorOf(kind))

    /** Moves the tree cursor [delta] rows without selecting anything. */
    fun stepTree(kind: BrowseKind, delta: Int) {
        val next = stepTreeCursor(visibleScopes(kind), treeCursorOf(kind), delta) ?: return
        treeCursors[kind] = next
    }

    /** Selects the scope under the tree cursor, which fills the list. */
    fun acceptTree(kind: BrowseKind) = select(kind, treeCursorOf(kind))

    private val scopeMemory = ScopeMemory()

    private var hostedTarget: ApplyTarget? = null

    /** The target the pane was last drawn bound to, else null (plain Library, or the dock is not on screen). The controller reads it to pick its context. */
    fun hosted(): ApplyTarget? = hostedTarget

    /** Applies the list row under the controller's cursor to the hosted target; false when there is none. */
    fun applyCursorRow(): Boolean {
        val target = hosted() ?: return false
        val asset = when (target.kind) {
            BrowseKind.SRC -> PresetListPanel.selectedAsset
            BrowseKind.FX -> FXBrowserPanel.selectedAsset
            BrowseKind.TRANS -> TransitionBrowserPanel.selectedAsset
        } ?: return false
        if (!target.accepts(asset)) return false
        DockActions.tap(target, asset)
        return true
    }

    /** The bound target's name for row menus ("Deck A · Source"), or null. */
    private var hostedLabel: String? = null

    /** First item of a row menu while bound: "Apply to <target>" above a separator, ahead of the Library's own load items. */
    internal fun applyMenuItem(target: ApplyTarget?, asset: AssetItem) {
        if (target == null || !target.accepts(asset)) return
        if (ImGui.menuItem("Apply to ${hostedLabel ?: "target"}")) DockActions.tap(target, asset)
        ImGui.separator()
    }

    /**
     * Draws the pane. [target] is null in the Library (double-click loads) and set when the pair view hosts it (a click applies to
     * the target and only rows it accepts are listed); [targetLabel] names it in row menus.
     */
    fun draw(session: SessionContext, mixer: Mixer, parametersState: ParametersState, kind: BrowseKind, target: ApplyTarget? = null, targetLabel: String? = null) {
        val catalog = BrowseCatalogs.get(kind)
        noteHosting(target)
        hostedLabel = targetLabel
        val (scope, moved) = scopeMemory.enter(kind, ScopeMemory.contextOf(target), scopeOf(kind), target?.defaultScope ?: BrowseScope.All)
        if (moved) {
            clearSelection(kind)
            treeCursors.remove(kind)
            searchBuffers[kind]?.set("")
            scopes[kind] = scope
        }
        val totalW = ImGui.getContentRegionAvailX().coerceAtLeast(2 * MIN_SIDE_W)
        val h = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
        val usable = totalW - GAP
        val sideW = (usable * 0.25f).coerceAtLeast(MIN_SIDE_W)
        val midW = (usable - sideW).coerceAtLeast(MIN_SIDE_W)
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
        drawList(session, mixer, parametersState, catalog, kind, target)
        ImGui.endChild()
        // The pane (tree or list) has the keyboard: a typed letter starts a search.
        if (ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)) typeToSearch(kind)

        ImGui.popStyleColor(2)
        ImGui.popStyleVar(2)
    }

    /** Kinds whose search box just received a typed-ahead character: its caret goes to the end instead of selecting the text. */
    private val caretToEnd = HashSet<BrowseKind>()

    private val caretToEndCallback = object : ImGuiInputTextCallback() {
        override fun accept(data: ImGuiInputTextCallbackData) {
            data.cursorPos = data.bufTextLen
            data.selectionStart = data.bufTextLen
            data.selectionEnd = data.bufTextLen
        }
    }

    /**
     * Type-to-search: a letter or digit pressed while the pane has the keyboard (and no text field does) starts a search with it.
     * Keys bound to a shortcut (Q, Shift+Q, T, ...) keep their shortcut.
     */
    private fun typeToSearch(kind: BrowseKind) {
        val io = ImGui.getIO()
        if (io.wantTextInput || io.keyCtrl || io.keyAlt || io.keySuper) return
        if (ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId or ImGuiPopupFlags.AnyPopupLevel)) return
        val mods = if (io.keyShift) GLFW.GLFW_MOD_SHIFT else 0
        val typed = (0 until 26).firstNotNullOfOrNull { i ->
            if (!ImGui.isKeyPressed(ImGuiKey.A + i, false) || ShortcutManager.isBound(GLFW.GLFW_KEY_A + i, mods)) null
            else ('a' + i).let { if (io.keyShift) it.uppercaseChar() else it }
        } ?: if (io.keyShift) null else (0 until 10).firstNotNullOfOrNull { i ->
            if (!ImGui.isKeyPressed(ImGuiKey._0 + i, false) || ShortcutManager.isBound(GLFW.GLFW_KEY_0 + i, 0)) null else '0' + i
        }
        if (typed == null) return
        searchBuffers.getOrPut(kind) { ImString(SearchMatcher.BUFFER_SIZE) }.set(typed.toString())
        searchFocusRequests.add(kind)
        caretToEnd.add(kind)
    }

    /** Appends the playlist under the tree cursor to the BG ([bg]) or A/B queue of [kind] (the one queue for transitions). False when the cursor is not on a playlist. */
    fun enqueueCursorPlaylist(session: SessionContext, kind: BrowseKind, bg: Boolean): Boolean {
        val playlist = treeCursorOf(kind) as? BrowseScope.Playlist ?: return false
        val file = File(playlist.path)
        when (kind) {
            BrowseKind.SRC -> if (bg) BgQueueManager.appendPlaylistToQueue(file) else session.playQueueManager.appendPlaylistToQueue(file)
            BrowseKind.FX -> if (bg) FXBgQueueManager.appendToQueue(file) else FXQueueManager.appendToQueue(file)
            BrowseKind.TRANS -> TransitionQueueManager.appendToQueue(file)
        }
        return true
    }

    /** The tree of [catalog]; while hosted its counts only include the rows the target accepts. */
    private fun treeOf(catalog: BrowseCatalog, kind: BrowseKind): List<BrowseNode> {
        val target = hosted()?.takeIf { it.kind == kind } ?: return catalog.tree()
        return catalog.tree { target.accepts(it.asset) }
    }

    /** Records where the pane is hosted; a controller cursor left over from another host (or the Library) is dropped when that changes. */
    internal fun noteHosting(target: ApplyTarget?) {
        if (target?.contextKey != hostedTarget?.contextKey) LibraryPanel.activeSelectionSource = null
        hostedTarget = target
    }

    private fun drawTree(session: SessionContext, mixer: Mixer, catalog: BrowseCatalog, kind: BrowseKind) {
        val btnSize = ImGui.getFrameHeight()
        val manage = DockActions.canManageList(hosted())
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.text("Folders") }
        ImGui.sameLine()
        val buttons = if (manage) 2 else 1
        val rightX = ImGui.getWindowContentRegionMaxX() - (btnSize * buttons + ImGui.getStyle().itemSpacingX * (buttons - 1))
        ImGui.setCursorPosX(rightX.coerceAtLeast(ImGui.getCursorPosX()))
        if (manage) {
            val newIn = newFolderPlace(kind)
            ImGui.beginDisabled(newIn == null)
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ButtonChrome.button("${Icons.FOLDER_PLUS}##browserNewFolder", btnSize, btnSize) && newIn != null) FolderPopups.requestNewFolder(newIn)
            }
            ImGui.endDisabled()
            itemTooltip(
                if (newIn != null) "New folder: an empty folder in ${newIn.label}. Drag rows onto a folder to move them there."
                else "New folder: select Saved single FX or Saved chains (or one of their folders) first.",
                allowWhenDisabled = true
            )
            ImGui.sameLine()
        }
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ButtonChrome.button("${Icons.LIST_PLUS}##browserNewPlaylist", btnSize, btnSize)) {
                BrowserPopupHandler.pendingOpenNewPlaylistPopup = true
            }
        }
        itemTooltip(
            "New playlist: an empty " + when (kind) {
                BrowseKind.SRC -> "Sources"
                BrowseKind.FX -> "FX"
                BrowseKind.TRANS -> "Transitions"
            } + " playlist, listed under Playlists. Fill it by dragging rows onto it or with a row's \"Add to\" item."
        )
        ImGui.separator()
        if (!ImGui.beginChild("##browser_tree_scroll", 0f, 0f, false)) {
            ImGui.endChild()
            return
        }
        val tree = treeOf(catalog, kind)
        syncPlaylistSelection(kind, tree)
        val current = scopeOf(kind)
        val cursor = if (LibraryPanel.activeSelectionSource == LibraryPanel.SelectionSource.TREE) treeCursorOf(kind) else null
        val hidden = hiddenOf(kind)
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
            // Every row keeps a fold-arrow slot, so labels line up whether or not the row has children.
            val foldW = ImGui.getTextLineHeight()
            if (expandable) {
                val fx = ImGui.getCursorScreenPosX()
                val fy = ImGui.getCursorScreenPosY()
                if (ImGui.invisibleButton("##fold", foldW, ImGui.getTextLineHeight())) {
                    if (isCollapsed) hidden.remove(node.scope) else hidden.add(node.scope)
                }
                val arrowCol = if (ImGui.isItemHovered()) ImGui.getColorU32(ImGuiCol.Text) else ImGui.getColorU32(ImGuiCol.TextDisabled)
                val glyph = if (isCollapsed) Icons.FOLD_CLOSED else Icons.FOLD_OPEN
                val gw = ImGui.calcTextSize(glyph).x
                ImGui.getWindowDrawList().addText(fx + (foldW - gw) * 0.5f, fy, arrowCol, glyph)
                itemTooltip(if (isCollapsed) "Show ${node.label}'s folders." else "Hide ${node.label}'s folders.")
                ImGui.sameLine(0f, 2f)
            } else {
                ImGui.dummy(foldW, ImGui.getTextLineHeight())
                ImGui.sameLine(0f, 2f)
            }
            val label = if (node.scope == BrowseScope.Recent) "${Icons.HISTORY} ${node.label}" else node.label
            if (!node.selectable) {
                // The Playlists and Tags headers are headings, not scopes: plain text, not greyed like a disabled row.
                ImGui.textUnformatted(label)
                itemTooltip(
                    when (node.scope) {
                        BrowseScope.TagsHeader -> "Your tags, one row each: pick one to list every saved item with it. An item can have any number.\nTag rows with a row's Add Tag item, or drag rows onto a tag."
                        is BrowseScope.PlaylistFolder -> "A folder of playlists. Drag a playlist onto it to move it there; right-click for folder actions."
                        else -> "Your playlists. Pick one to list its items in order; right-click one for queue and file actions.\nRight-click here for New Folder; drag a playlist onto a folder to move it."
                    }
                )
            } else if (ImGui.selectable("$label##node", node.scope == current)) {
                select(kind, node.scope)
            }
            if (node.scope == BrowseScope.Recent) itemTooltip("The last ${BrowseRecents.LIMIT} items you loaded or applied, newest first.")
            val folderPlace = when (val sc = node.scope) {
                is BrowseScope.Folder -> placeOf(kind, sc)
                is BrowseScope.PlaylistFolder -> FolderPopups.Place(kind, BrowseSection.PLAYLISTS, sc.folder)
                BrowseScope.PlaylistsHeader -> FolderPopups.Place(kind, BrowseSection.PLAYLISTS, "")
                else -> null
            }
            if (folderPlace != null && manage) folderNode(mixer, folderPlace)
            (node.scope as? BrowseScope.Tag)?.let { if (manage) tagNode(mixer, kind, it.name) }
            if (node.scope == cursor) {
                // The controller's cursor: an outline, so it reads apart from the selected scope's fill.
                val min = ImGui.getItemRectMin()
                val max = ImGui.getItemRectMax()
                val dl = ImGui.getWindowDrawList()
                val boxMinX = (min.x + 1f).coerceAtLeast(ImGui.getWindowPosX() + ImGui.getStyle().windowPaddingX)
                val boxMaxX = (max.x - 1f).coerceAtMost(ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX())
                val boxMinY = min.y + 1f
                val boxMaxY = max.y - 1f
                dl.addRect(boxMinX, boxMinY, boxMaxX, boxMaxY, TangoPalette.u32(TangoPalette.SYNC.normal), 3f, 0, 1.5f)
                SmoothScroll.follow(LibraryPanel.shouldScrollToSelection)
            }
            val playlist = node.scope as? BrowseScope.Playlist
            if (playlist != null && manage && ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload(PAYLOAD_PLAYLIST, playlist.path as Any)
                ImGui.text(node.label)
                ImGui.endDragDropSource()
            }
            if (playlist != null) playlistContextMenu(session, mixer, kind, playlist)
            // Counts are items; an empty row shows none, the same rule for folders, playlists and the Playlists header.
            if (node.count > 0) {
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

    private const val PAYLOAD_FOLDER = "BROWSE_FOLDER"

    /** A playlist dragged by its tree row, to move it into a playlist folder. */
    private const val PAYLOAD_PLAYLIST = "BROWSE_PLAYLIST"

    /** The saved folder a tree scope stands for, or null for stock and live sections. */
    private fun placeOf(kind: BrowseKind, scope: BrowseScope.Folder): FolderPopups.Place? =
        if (LibraryFolders.rootOf(kind, scope.section) == null) null else FolderPopups.Place(kind, scope.section, scope.folder)

    /** Where the New folder button puts a folder: the selected saved folder, else the kind's one saved section (FX has two, so none). */
    private fun newFolderPlace(kind: BrowseKind): FolderPopups.Place? {
        (scopeOf(kind) as? BrowseScope.Folder)?.let { placeOf(kind, it) }?.let { return it }
        return if (kind == BrowseKind.FX) null else FolderPopups.Place(kind, BrowseSection.SAVED, "")
    }

    /** A saved section or folder row: rows and folders dropped on it move into it, a folder can be dragged, and it has a folder menu. */
    private fun folderNode(mixer: Mixer, place: FolderPopups.Place) {
        if (place.folder.isNotEmpty() && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(PAYLOAD_FOLDER, "${place.kind.name}|${place.section.name}|${place.folder}" as Any)
            ImGui.text("${Icons.FOLDER} ${place.label}")
            ImGui.endDragDropSource()
        }
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<String>("ASSET_ITEM")?.let { payload ->
                val files = payload.lines().map { it.trim() }.filter { it.isNotEmpty() }.map(::File)
                FolderPopups.moveFiles(files, place, mixer)
            }
            ImGui.acceptDragDropPayload<String>(PAYLOAD_PLAYLIST)?.let { path -> FolderPopups.moveFiles(listOf(File(path)), place, mixer) }
            ImGui.acceptDragDropPayload<String>(PAYLOAD_FOLDER)?.let { payload ->
                val (k, sec, folder) = payload.split('|', limit = 3)
                FolderPopups.moveFolder(FolderPopups.Place(BrowseKind.valueOf(k), BrowseSection.valueOf(sec), folder), place, mixer)
            }
            ImGui.endDragDropTarget()
        }
        if (!ImGui.beginPopupContextItem("folder_node_menu")) return
        pushOpenDropdownFont()
        if (ImGui.menuItem("New Folder...")) FolderPopups.requestNewFolder(place)
        if (place.folder.isNotEmpty()) {
            if (ImGui.menuItem("Rename Folder...")) FolderPopups.requestRenameFolder(place)
            if (ImGui.menuItem("Delete Folder...")) FolderPopups.requestDeleteFolder(place)
        }
        place.root?.let { root ->
            if (ImGui.menuItem("Reveal in File Manager")) {
                val dir = LibraryFolders.dirOf(root, place.folder)
                try { java.awt.Desktop.getDesktop().open(dir) } catch (e: Exception) { ToastOverlay.show("Could not open ${dir.name}") }
            }
        }
        popOpenDropdownFont()
        ImGui.endPopup()
    }

    /**
     * Keeps the tree on a folder that was just created, renamed, moved or deleted: [old] is the folder before (null for a new one) and
     * [new] after (null when it was deleted, which selects its parent). A selection inside a renamed folder follows it.
     */
    internal fun followFolder(kind: BrowseKind, section: BrowseSection, old: String?, new: String?) {
        fun moved(f: String): String? = when {
            old == null -> f
            f == old -> new
            f.startsWith("$old/") -> new?.let { it + f.removePrefix(old) }
            else -> f
        }
        val hidden = hiddenOf(kind)
        val renamed = hidden.map { s ->
            when {
                s is BrowseScope.Folder && s.section == section -> moved(s.folder)?.let { s.copy(folder = it) }
                s is BrowseScope.PlaylistFolder && section == BrowseSection.PLAYLISTS -> moved(s.folder)?.let { BrowseScope.PlaylistFolder(it) }
                else -> s
            }
        }
        hidden.clear()
        hidden.addAll(renamed.filterNotNull())
        if (section == BrowseSection.PLAYLISTS) {
            // Playlist folders aren't scopes; only their parents need opening so the folder is on screen.
            new?.split('/')?.runningReduce { acc, seg -> "$acc/$seg" }?.dropLast(1)?.forEach { hidden.remove(BrowseScope.PlaylistFolder(it)) }
            hidden.remove(BrowseScope.PlaylistsHeader)
            return
        }
        val current = scopeOf(kind)
        when {
            old == null && new != null -> select(kind, BrowseScope.Folder(section, new))
            current is BrowseScope.Folder && current.section == section -> {
                val f = moved(current.folder) ?: old!!.substringBeforeLast('/', "")
                if (f != current.folder) select(kind, BrowseScope.Folder(section, f))
            }
        }
        // A new or moved folder's parents open, so it is on screen.
        new?.split('/')?.runningReduce { acc, seg -> "$acc/$seg" }?.dropLast(1)?.forEach { hidden.remove(BrowseScope.Folder(section, it)) }
        hidden.remove(BrowseScope.Folder(section))
    }

    /** A tag row: rows dropped on it get the tag; its menu renames the tag or removes it from every item. */
    private fun tagNode(mixer: Mixer, kind: BrowseKind, tag: String) {
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<String>("ASSET_ITEM")?.let { payload ->
                val files = payload.lines().map { it.trim() }.filter { it.isNotEmpty() }.map(::File).filter { LibraryRefs.kindOf(it) == kind }
                val n = LibraryTags.add(files, listOf(tag), mixer.fxChains)
                if (n > 0) ToastOverlay.show("Tagged ${if (n == 1) "1 item" else "$n items"} $tag")
            }
            ImGui.endDragDropTarget()
        }
        if (!ImGui.beginPopupContextItem("tag_node_menu")) return
        pushOpenDropdownFont()
        if (ImGui.menuItem("Rename Tag...")) TagPopups.requestRename(kind, tag)
        if (ImGui.menuItem("Delete Tag...")) TagPopups.requestDelete(kind, tag)
        itemTooltip("Removes the tag from every item that has it. The items stay.")
        popOpenDropdownFont()
        ImGui.endPopup()
    }

    /** Keeps the tree on a tag that was just renamed ([new]) or deleted (null, which goes back to All). */
    internal fun followTag(kind: BrowseKind, old: String, new: String?) {
        if (scopeOf(kind) != BrowseScope.Tag(old)) return
        select(kind, new?.let { BrowseScope.Tag(it) } ?: BrowseScope.All)
    }

    /** The saved rows a row menu covers: the selection when [entry] is in it, else [entry] alone; stock and live rows are left out. */
    private fun savedRowsFor(kind: BrowseKind, entry: BrowseEntry, assets: List<AssetItem>): List<File> {
        val selected = selectedAssets(kind, assets)
        return (if (selected.any { it.path == entry.asset.path }) selected else listOf(entry.asset))
            .map { File(it.path) }.filter { LibraryRefs.kindOf(it) == kind }
    }

    /** Add Tag... and Remove Tag for the saved rows a row menu covers. */
    private fun tagMenu(mixer: Mixer, kind: BrowseKind, entry: BrowseEntry, assets: List<AssetItem>) {
        val rows = savedRowsFor(kind, entry, assets)
        if (rows.isEmpty()) return
        val n = if (rows.size == 1) "" else " (${rows.size})"
        if (ImGui.menuItem("Add Tag$n...")) TagPopups.requestAdd(kind, rows)
        val present = rows.flatMap { LibraryTags.read(it) }.map(LibraryTags::normalize).distinct().sorted()
        if (present.isNotEmpty() && ImGui.beginMenu("Remove Tag$n")) {
            for (t in present) if (ImGui.menuItem(t)) LibraryTags.remove(rows, t, mixer.fxChains)
            ImGui.endMenu()
        }
    }

    /** "Move to" for the saved rows a row menu covers: every folder of their section, top level first. */
    private fun moveToMenu(mixer: Mixer, kind: BrowseKind, entry: BrowseEntry, assets: List<AssetItem>) {
        val root = LibraryFolders.rootOf(kind, entry.section) ?: return
        val rows = savedRowsFor(kind, entry, assets).filter { LibraryFolders.sectionOf(kind, it) == entry.section }
        if (rows.isEmpty()) return
        val label = if (rows.size == 1) "Move to" else "Move ${rows.size} to"
        if (!ImGui.beginMenu(label)) return
        val folders = listOf("") + LibraryFolders.dirsUnder(root)
        for (f in folders) {
            val here = rows.all { it.absoluteFile.parentFile == LibraryFolders.dirOf(root, f).absoluteFile }
            val name = if (f.isEmpty()) "${BrowseCatalog.sectionLabel(kind, entry.section)} (top level)" else "    ".repeat(f.count { it == '/' }) + f.substringAfterLast('/')
            if (ImGui.menuItem("$name##move_$f", "", false, !here)) FolderPopups.moveFiles(rows, FolderPopups.Place(kind, entry.section, f), mixer)
        }
        ImGui.separator()
        if (ImGui.menuItem("New Folder...")) FolderPopups.requestNewFolder(FolderPopups.Place(kind, entry.section, entry.folder))
        ImGui.endMenu()
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
                if (ImGui.menuItem("Add All to A/B FX Queue")) FXQueueManager.appendToQueue(file)
                if (ImGui.menuItem("Add All to BG FX Queue")) FXBgQueueManager.appendToQueue(file)
            }
            BrowseKind.TRANS -> if (ImGui.menuItem("Load Playlist to A/B Queue")) TransitionQueueManager.appendToQueue(file)
        }
        ImGui.separator()
        val assetType = when (kind) {
            BrowseKind.SRC -> AssetType.PLAYLIST
            BrowseKind.FX -> AssetType.FX_PLAYLIST
            BrowseKind.TRANS -> AssetType.TRANSITION_PLAYLIST
        }
        val asset = AssetItem(path = file.absolutePath, name = file.nameWithoutExtension, type = assetType)
        if (ImGui.menuItem("Rename...")) BrowserPopupHandler.openRename(asset)
        if (ImGui.menuItem("Clone")) {
            FileSystemManager.cloneFile(file.absolutePath).onSuccess { newPath ->
                scopes[kind] = BrowseScope.Playlist(File(newPath).absolutePath)
                setSelectedPlaylistFile(kind, File(newPath))
            }
        }
        if (ImGui.menuItem("Delete...")) BrowserPopupHandler.openDeleteConfirmation(listOf(asset))
        if (ImGui.menuItem("Reveal in File Manager")) BrowserPopupHandler.revealInFileManager(file)
        popOpenDropdownFont()
        ImGui.endPopup()
    }

    /** Follows the Library's selected playlist file when something else changed it (a playlist just created) and drops a scope whose playlist is gone. */
    private fun syncPlaylistSelection(kind: BrowseKind, tree: List<BrowseNode>) {
        val external = selectedPlaylistFile(kind)?.absolutePath
        if (external != syncedPlaylist[kind]) {
            syncedPlaylist[kind] = external
            val target = tree.firstOrNull { (it.scope as? BrowseScope.Playlist)?.path == external }
            if (target != null) setScope(kind, target.scope)
        }
        val scope = scopes[kind]
        if (scope is BrowseScope.Playlist && tree.none { it.scope == scope }) scopes.remove(kind)
    }

    /** Changes the scope; the list selection is dropped when it moves, so a row of the previous scope cannot be accepted by accident. */
    private fun setScope(kind: BrowseKind, scope: BrowseScope) {
        if (scope != scopeOf(kind)) clearSelection(kind)
        scopes[kind] = scope
    }

    private fun select(kind: BrowseKind, scope: BrowseScope) {
        setScope(kind, scope)
        treeCursors.remove(kind)
        // A playlist scope becomes the Library's active playlist, so "Add to '<playlist>'" in row menus targets it.
        if (scope is BrowseScope.Playlist) {
            val file = File(scope.path)
            setSelectedPlaylistFile(kind, file)
            if (kind == BrowseKind.SRC) LibraryPanel.getOrLoadPlaylist(file)
        }
    }

    private fun drawList(session: SessionContext, mixer: Mixer, parametersState: ParametersState, catalog: BrowseCatalog, kind: BrowseKind, target: ApplyTarget?) {
        val scope = scopeOf(kind)
        val title = treeOf(catalog, kind).firstOrNull { it.scope == scope }?.label ?: "All"
        drawListHeader(session, mixer, parametersState, title, kind)
        ImGui.separator()

        val search = searchBuffers.getOrPut(kind) { ImString(SearchMatcher.BUFFER_SIZE) }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX())
        if (searchFocusRequests.remove(kind)) ImGui.setKeyboardFocusHere()
        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            // After a typed-ahead letter the field keeps the caret callback until it is active (focus lands a frame later).
            val moveCaret = kind in caretToEnd
            if (moveCaret) {
                ImGui.inputTextWithHint("##browserPaneSearch", "Search name, tags, folder...", search, ImGuiInputTextFlags.CallbackAlways, caretToEndCallback)
                if (ImGui.isItemActive()) caretToEnd.remove(kind)
            } else {
                ImGui.inputTextWithHint("##browserPaneSearch", "Search name, tags, folder...", search)
            }
            llm.slop.liquidlsd.tour.WidgetRegistry.note { "browser_search" }
        }
        if (ImGui.isItemActive() && ImGui.isKeyPressed(ImGuiKey.Escape)) {
            search.set("")
            LibraryPanel.shouldReclaimFocus = true
        }
        itemTooltip("Type to filter by name, id, folder or tags.\nTyping a letter while the list has the keyboard starts a search too.\nPress Esc while searching to clear.")
        ImGui.separator()
        ImGui.spacing()

        val query = search.get().trim()
        val entries = catalog.rows(scope, query).let { all -> if (target == null) all else all.filter { target.accepts(it.asset) } }
        val missing = catalog.missing(scope)
        val infoByPath = entries.associate { it.asset.path to it.info }
        val assets = entries.map { it.asset }
        // Reordering needs every playlist row visible, so it stays a Library feature.
        val playlistRows = if (DockActions.canManageList(target)) playlistRowsFor(catalog, scope, query) else null

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
                    if (entry != null && entry.section != BrowseSection.LIVE && !(kind == BrowseKind.FX && entry.section == BrowseSection.STOCK)) {
                        val fav = BrowseFavorites.isFavorite(kind, entry.key)
                        if (ImGui.menuItem(if (fav) "★ Remove from Favorites" else "☆ Add to Favorites")) {
                            BrowseFavorites.toggle(kind, entry.key)
                        }
                    }
                    if (playlistRows != null && ImGui.menuItem("Remove from playlist")) removeFromPlaylist(kind, playlistRows, assets)
                    if (entry != null && DockActions.canManageList(target)) {
                        tagMenu(mixer, kind, entry, assets)
                        if (playlistRows == null) moveToMenu(mixer, kind, entry, assets)
                    }
                }
                when (kind) {
                    BrowseKind.SRC -> PresetListPanel.drawRows(
                        session, mixer, parametersState, assets,
                        favoriteKeys = BrowseFavorites.keys(kind), infoFor = infoFor, contextExtras = extras, playlistRows = playlistRows, target = target
                    )
                    BrowseKind.FX -> FXBrowserPanel.drawRows(session, mixer, assets, infoFor, extras, playlistRows, target)
                    BrowseKind.TRANS -> TransitionBrowserPanel.drawRows(session, mixer, assets, infoFor, extras, playlistRows, target)
                }
            }
            if (missing > 0) {
                ImGui.spacing()
                if (playlistRows != null) drawMissing(mixer, catalog, kind, scope, playlistRows)
                else ImGui.textDisabled("$missing playlist item(s) not found")
            }
            // Ctrl/Cmd+A selects every listed row while the list has the keyboard (unless a shortcut claims it); a bound dock stays single.
            val keys = ImGui.getIO()
            val mod = if (keys.keySuper) GLFW.GLFW_MOD_SUPER else GLFW.GLFW_MOD_CONTROL
            if (target == null && !keys.wantTextInput && (keys.keyCtrl || keys.keySuper) && !keys.keyShift && !keys.keyAlt &&
                ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows) && ImGui.isKeyPressed(ImGuiKey.A, false) &&
                !ShortcutManager.isBound(GLFW.GLFW_KEY_A, mod)
            ) {
                selectionOf(kind).selectAll(assets)
                LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            }
        }
        ImGui.endChild()

        // The delete key (Preferences > Shortcuts, Delete by default; Backspace too): inside a playlist it removes the rows from the
        // playlist, elsewhere it deletes from the library with confirmation.
        val io = ImGui.getIO()
        val selected = selectedAssets(kind, assets)
        if (DockActions.canManageList(target) && selected.isNotEmpty() && !io.wantTextInput && DockActions.deleteKeyPressed()) {
            if (playlistRows != null) {
                removeFromPlaylist(kind, playlistRows, assets)
            } else {
                selected.filter { !isStock(it) }.takeIf { it.isNotEmpty() }?.let { BrowserPopupHandler.openDeleteConfirmation(it) }
            }
        }
    }

    private fun isStock(asset: AssetItem): Boolean =
        asset.type == AssetType.SOURCE_STOCK || asset.type == AssetType.SOURCE_EXTERNAL || asset.type == AssetType.FX_STOCK || asset.type == AssetType.TRANSITION_STOCK

    private fun selectionOf(kind: BrowseKind): MultiSelectionModel<AssetItem> = when (kind) {
        BrowseKind.SRC -> PresetListPanel.selection
        BrowseKind.FX -> FXBrowserPanel.selection
        BrowseKind.TRANS -> TransitionBrowserPanel.selection
    }

    /** The selected rows of the list, in list order (Ctrl/Shift+click multi-selects in every kind). */
    private fun selectedAssets(kind: BrowseKind, assets: List<AssetItem>): List<AssetItem> = selectionOf(kind).getSelectedInOrder(assets)

    private fun clearSelection(kind: BrowseKind) = selectionOf(kind).clear()

    /** Title row of the list with the per-kind "+" (and, for Sources, "..." maintenance) buttons. */
    private fun drawListHeader(session: SessionContext, mixer: Mixer, parametersState: ParametersState, title: String, kind: BrowseKind) {
        val btnSize = ImGui.getFrameHeight()
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.text(title) }
        ImGui.sameLine()
        val buttons = if (kind == BrowseKind.SRC) 2 else 1
        val rightX = ImGui.getWindowContentRegionMaxX() - (btnSize * buttons + ImGui.getStyle().itemSpacingX * (buttons - 1))
        if (rightX > ImGui.getCursorPosX()) ImGui.setCursorPosX(rightX)

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            // A new blank preset is made; FX and transitions are saved from what is playing, so they get the save icon.
            val icon = if (kind == BrowseKind.SRC) Icons.FILE_PLUS else Icons.SAVE
            if (ButtonChrome.button("$icon##browser_new", btnSize, btnSize)) {
                when (kind) {
                    BrowseKind.SRC -> ImGui.openPopup("browser_new_preset_popup")
                    BrowseKind.FX -> ImGui.openPopup("create_new_fx_popup")
                    BrowseKind.TRANS -> TransitionSave.requestSaveCurrent(session, mixer)
                }
            }
        }
        itemTooltip(
            when (kind) {
                BrowseKind.SRC -> "New preset: start a blank preset on a deck (pick which)."
                BrowseKind.FX -> "Save FX: save one FX slot, or a deck's whole 3-slot chain, as a new saved FX."
                BrowseKind.TRANS -> "Save transition: save the mixer's current transition as a new saved transition (.lsdtrans)."
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
            if (ButtonChrome.button("${Icons.MORE_VERTICAL}##browser_more", btnSize, btnSize)) ImGui.openPopup("browser_more_popup")
        }
        itemTooltip("More: restore the factory presets, or import a .flame file as Fractal Flame presets.")
        pushOpenDropdownPadding()
        if (ImGui.beginPopup("browser_more_popup")) {
            pushOpenDropdownFont()
            if (ImGui.menuItem("Restore Factory Presets")) FileSystemManager.restoreFactoryPresets()
            if (ImGui.menuItem("Import Flame...")) flameImportBrowser.open(ImGuiFileBrowser.Mode.LOAD, File(".").canonicalFile, "", listOf(".flame"))
            itemTooltip("Import a .flame genome (your own file) as Fractal Flame presets in library/presets/imported-flames/.\nVariations the generator does not have are skipped; the preset's notes list what was dropped.")
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        flameImportBrowser.draw { file -> importFlame(file) }
    }

    private val importLogger = mu.KotlinLogging.logger {}
    private val flameImportBrowser = ImGuiFileBrowser("##flameImportBrowser")

    private fun importFlame(file: File) {
        try {
            val outcome = llm.slop.liquidlsd.presets.FlameImportOps.importFile(file)
            ToastOverlay.show("Imported ${outcome.files.size} flame(s) to ${llm.slop.liquidlsd.presets.FlameImportOps.FOLDER}")
            outcome.notes.forEach { importLogger.info { "Flame import: $it" } }
        } catch (e: Exception) {
            importLogger.warn(e) { "Flame import failed for ${file.name}" }
            ToastOverlay.show("Could not import ${file.name}: ${e.message}")
        }
    }

    /** The missing item waiting for the Locate file picker: its kind and playlist token. */
    private var locating: Pair<BrowseKind, String>? = null
    private val locateBrowser = ImGuiFileBrowser("##locateMissingBrowser")

    /** Where a missing playlist item's file was: the token itself when absolute, else relative to the kind's saved folder. */
    private fun lostFile(kind: BrowseKind, token: String): File {
        val f = File(token)
        if (f.isAbsolute) return f
        val root = if (kind == BrowseKind.SRC) FileSystemManager.getPresetsRoot()
        else LibraryFolders.sectionOf(kind, f)?.let { LibraryFolders.rootOf(kind, it) } ?: File(".")
        return File(root, token)
    }

    /**
     * The playlist's missing items under the list, each with Find (files of the same name in the library), Locate... (pick the file)
     * and Remove. A found file is followed everywhere the lost path was used ([LibraryRefs.relocate]), not just in this playlist.
     */
    private fun drawMissing(mixer: Mixer, catalog: BrowseCatalog, kind: BrowseKind, scope: BrowseScope, rows: PlaylistRows) {
        val missing = catalog.missingItems(scope)
        if (missing.isEmpty()) return
        ImGui.pushStyleColor(ImGuiCol.Text, TangoPalette.TEXT_WARN.u32())
        ImGui.alignTextToFramePadding()
        ImGui.textUnformatted("${Icons.ALERT} ${missing.size} playlist item${if (missing.size == 1) "" else "s"} not found")
        ImGui.popStyleColor()
        val unique = missing.mapNotNull { (_, token) -> catalog.candidatesFor(token).singleOrNull()?.let { lostFile(kind, token) to File(it.key) } }.toMap()
        if (unique.isNotEmpty()) {
            ImGui.sameLine()
            if (ImGui.smallButton("Find all##missing_find_all")) {
                LibraryRefs.relocate(unique, mixer.fxChains)
                ToastOverlay.show("Found ${unique.size} of ${missing.size} missing item${if (missing.size == 1) "" else "s"}")
            }
            itemTooltip("Point every missing item that has exactly one file of the same name in the library at that file.")
        }
        for ((index, token) in missing) {
            ImGui.pushID("missing_$index")
            ImGui.textDisabled(token.replace('\\', '/').substringAfterLast('/'))
            itemTooltip(token)
            ImGui.sameLine()
            if (ImGui.smallButton("Find")) ImGui.openPopup("missing_find")
            itemTooltip("Files of the same name elsewhere in the library.")
            if (ImGui.beginPopup("missing_find")) {
                pushOpenDropdownFont()
                val candidates = catalog.candidatesFor(token)
                if (candidates.isEmpty()) ImGui.textDisabled("No file of that name in the library")
                for (c in candidates) {
                    val label = listOf(BrowseCatalog.sectionLabel(kind, c.section), c.folder).filter { it.isNotEmpty() }.joinToString(" / ") + " / " + c.asset.name
                    if (ImGui.menuItem(label)) LibraryRefs.relocate(mapOf(lostFile(kind, token) to File(c.key)), mixer.fxChains)
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            ImGui.sameLine()
            if (ImGui.smallButton("Locate...")) {
                locating = kind to token
                val start = lostFile(kind, token).parentFile?.takeIf { it.isDirectory } ?: File(".").canonicalFile
                val ext = token.substringAfterLast('.', "").takeIf { it.isNotEmpty() && '/' !in it }
                locateBrowser.open(ImGuiFileBrowser.Mode.LOAD, start, "", listOfNotNull(ext?.let { ".$it" }).ifEmpty { listOf(".lsd", ".lsdfx", ".lsdfxchain", ".lsdtrans") })
            }
            itemTooltip("Pick the file this item should play.")
            ImGui.sameLine()
            if (ImGui.smallButton("Remove")) rows.edit.remove(listOf(index))
            itemTooltip("Take this item out of the playlist.")
            ImGui.popID()
        }
        locateBrowser.draw { picked ->
            val (k, token) = locating ?: return@draw
            if (LibraryRefs.kindOf(picked) == k) LibraryRefs.relocate(mapOf(lostFile(k, token) to picked), mixer.fxChains)
            else ToastOverlay.show("${picked.name} isn't a saved item of this tab")
            locating = null
        }
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
}
