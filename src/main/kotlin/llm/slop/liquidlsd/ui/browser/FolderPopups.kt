package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.ButtonChrome
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.ModalKeys
import llm.slop.liquidlsd.ui.TangoPalette
import llm.slop.liquidlsd.ui.ToastOverlay
import java.io.File

/**
 * The Library's folder actions (New folder, Rename folder, Delete folder, moving rows and folders) and their dialogs. Each action
 * changes the disk through [LibraryFolders], then hands what moved or went to [LibraryRefs], and keeps the tree on the folder
 * the user was looking at. [draw] runs once per frame with the browser's other popups ([BrowserDock.drawPopups]).
 */
object FolderPopups {
    /** One folder of a saved section: [folder] is '/'-separated below the section's root, "" for the section itself. */
    data class Place(val kind: BrowseKind, val section: BrowseSection, val folder: String) {
        val root: File? get() = LibraryFolders.rootOf(kind, section)
        val label: String get() = if (folder.isEmpty()) BrowseCatalog.sectionLabel(kind, section) else folder.substringAfterLast('/')
    }

    private enum class Dialog { NEW, RENAME, DELETE, CONFLICT }

    private var dialog: Dialog? = null
    private var pendingOpen = false
    private var place: Place? = null
    private val nameBuffer = ImString(128)
    private var error: String? = null

    // Delete: what the folder holds, worked out once when the dialog opens.
    private var deleteCount = 0
    private var deleteImpact = ""

    // Conflict: a move waiting for Skip / Keep both.
    private var conflictFiles: List<File> = emptyList()
    private var conflictNames: List<String> = emptyList()

    private fun open(d: Dialog, at: Place) {
        dialog = d
        place = at
        error = null
        pendingOpen = true
    }

    fun requestNewFolder(at: Place) {
        nameBuffer.set("")
        open(Dialog.NEW, at)
    }

    fun requestRenameFolder(at: Place) {
        if (at.folder.isEmpty()) return
        nameBuffer.set(at.folder.substringAfterLast('/'))
        open(Dialog.RENAME, at)
    }

    fun requestDeleteFolder(at: Place) {
        val root = at.root ?: return
        if (at.folder.isEmpty()) return
        val files = LibraryFolders.savedFilesUnder(LibraryFolders.dirOf(root, at.folder))
        deleteCount = files.size
        deleteImpact = LibraryRefs.impact(files).describe()
        open(Dialog.DELETE, at)
    }

    /** Moves saved [files] into [dest]. A name clash at the destination asks first (Skip / Keep both). */
    fun moveFiles(files: List<File>, dest: Place, mixer: Mixer) {
        val root = dest.root ?: return
        val section = dest.section
        val mine = files.filter { LibraryFolders.sectionOf(dest.kind, it) == section && LibraryFolders.isInside(it, root) }
        if (mine.isEmpty()) return
        val plan = LibraryFolders.planMove(mine, LibraryFolders.dirOf(root, dest.folder), keepBoth = false)
        if (plan.conflicts.isEmpty()) {
            finishMove(dest, plan.moves, mixer)
            return
        }
        conflictFiles = mine
        conflictNames = plan.conflicts.map { it.nameWithoutExtension }
        open(Dialog.CONFLICT, dest)
    }

    /** Moves [folder] (a folder of [dest]'s section) into [dest]. */
    fun moveFolder(folder: Place, dest: Place, mixer: Mixer) {
        val root = dest.root ?: return
        if (folder.kind != dest.kind || folder.section != dest.section || folder.folder.isEmpty()) return
        if (folder.folder.substringBeforeLast('/', "") == dest.folder) return
        LibraryFolders.moveFolder(root, folder.folder, dest.folder)
            .onSuccess { moved ->
                LibraryRefs.relocate(moved, mixer.fxChains)
                val newFolder = if (dest.folder.isEmpty()) folder.folder.substringAfterLast('/') else "${dest.folder}/${folder.folder.substringAfterLast('/')}"
                BrowserPane.followFolder(folder.kind, folder.section, folder.folder, newFolder)
            }
            .onFailure { ToastOverlay.show("Could not move '${folder.label}': ${it.message}") }
    }

    private fun finishMove(dest: Place, moves: Map<File, File>, mixer: Mixer) {
        val root = dest.root ?: return
        val done = LibraryFolders.moveFiles(root, moves)
        if (done.isEmpty()) return
        LibraryRefs.relocate(done, mixer.fxChains)
        ToastOverlay.show("Moved ${done.size} item${if (done.size == 1) "" else "s"} to ${dest.label}")
    }

    fun draw(mixer: Mixer) {
        val d = dialog ?: return
        val at = place ?: return
        val id = when (d) {
            Dialog.NEW -> "New Folder##LibraryNewFolder"
            Dialog.RENAME -> "Rename Folder##LibraryRenameFolder"
            Dialog.DELETE -> "Delete Folder##LibraryDeleteFolder"
            Dialog.CONFLICT -> "Move##LibraryMoveConflict"
        }
        if (pendingOpen) {
            ImGui.openPopup(id)
            pendingOpen = false
        }
        if (!ImGui.beginPopupModal(id, ImGuiWindowFlags.AlwaysAutoResize)) return
        when (d) {
            Dialog.NEW, Dialog.RENAME -> drawNameDialog(d, at, mixer)
            Dialog.DELETE -> drawDeleteDialog(at, mixer)
            Dialog.CONFLICT -> drawConflictDialog(at, mixer)
        }
        ImGui.endPopup()
    }

    private fun warn(text: String) {
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, TangoPalette.TEXT_WARN.u32())
        ImGui.textUnformatted(text)
        ImGui.popStyleColor()
    }

    private fun close() {
        dialog = null
        ImGui.closeCurrentPopup()
    }

    private fun drawNameDialog(d: Dialog, at: Place, mixer: Mixer) {
        ImGui.text(if (d == Dialog.NEW) "New folder in ${at.label}:" else "Rename folder '${at.label}' to:")
        ModalKeys.focusOnOpen()
        ImGui.setNextItemWidth(320f)
        ImGui.inputText("##libraryFolderName", nameBuffer)
        error?.let { warn(it) }
        val key = ModalKeys.answer()
        if (ButtonChrome.button(if (d == Dialog.NEW) "Create" else "Rename", 120f, 0f) || key == true) {
            val root = at.root ?: return close()
            val name = nameBuffer.get().trim()
            val result = if (d == Dialog.NEW) {
                LibraryFolders.createFolder(root, at.folder, name).map { dir ->
                    BrowserPane.followFolder(at.kind, at.section, null, (if (at.folder.isEmpty()) "" else at.folder + "/") + dir.name)
                }
            } else {
                LibraryFolders.renameFolder(root, at.folder, name).map { moved ->
                    LibraryRefs.relocate(moved, mixer.fxChains)
                    val parent = at.folder.substringBeforeLast('/', "")
                    BrowserPane.followFolder(at.kind, at.section, at.folder, if (parent.isEmpty()) name else "$parent/$name")
                }
            }
            result.onSuccess { close() }.onFailure { error = it.message }
        }
        ImGui.sameLine()
        if (ButtonChrome.button("Cancel", 120f, 0f) || key == false) close()
    }

    private fun drawDeleteDialog(at: Place, mixer: Mixer) {
        val what = when (deleteCount) {
            0 -> "It is empty."
            1 -> "It holds 1 item."
            else -> "It holds $deleteCount items."
        }
        ImGui.text("Delete folder '${at.label}' and everything in it?")
        ImGui.text(what + if (deleteImpact.isEmpty()) "" else " They are $deleteImpact.")
        ImGui.textDisabled(
            if (FileSystemManager.trashSupported()) "The folder goes to the system trash."
            else "This can't be undone."
        )
        error?.let { warn(it) }
        ImGui.separator()
        val key = ModalKeys.answer()
        if (ButtonChrome.button("Delete", 120f, 0f) || key == true) {
            val root = at.root ?: return close()
            LibraryFolders.deleteFolder(root, at.folder)
                .onSuccess { gone ->
                    LibraryRefs.forget(gone, mixer.fxChains)
                    BrowserPane.followFolder(at.kind, at.section, at.folder, null)
                    close()
                }
                .onFailure { error = it.message }
        }
        ImGui.sameLine()
        if (ButtonChrome.button("Cancel", 120f, 0f) || key == false) close()
    }

    private fun drawConflictDialog(at: Place, mixer: Mixer) {
        val n = conflictNames.size
        ImGui.text(if (n == 1) "'${conflictNames.first()}' is already in ${at.label}." else "$n items are already in ${at.label}:")
        if (n > 1) ImGui.textDisabled(conflictNames.take(4).joinToString(", ") + if (n > 4) " and ${n - 4} more" else "")
        ImGui.separator()
        val key = ModalKeys.answer()
        val root = at.root ?: return close()
        val dest = LibraryFolders.dirOf(root, at.folder)
        if (ButtonChrome.button("Keep both", 120f, 0f) || key == true) {
            finishMove(at, LibraryFolders.planMove(conflictFiles, dest, keepBoth = true).moves, mixer)
            close()
        }
        ImGui.sameLine()
        if (ButtonChrome.button("Skip", 120f, 0f)) {
            finishMove(at, LibraryFolders.planMove(conflictFiles, dest, keepBoth = false).moves, mixer)
            close()
        }
        ImGui.sameLine()
        if (ButtonChrome.button("Cancel", 120f, 0f) || key == false) close()
    }
}
