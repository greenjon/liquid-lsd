package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.ButtonChrome
import llm.slop.liquidlsd.ui.ModalKeys
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.ui.itemTooltip
import java.io.File

/**
 * The Library's tag dialogs: Add Tag (to the rows a menu covers), Rename Tag and Delete Tag (on every saved item of the kind
 * that carries it). The disk work is [LibraryTags]; [draw] runs once per frame with the browser's other popups.
 */
object TagPopups {
    private enum class Dialog { ADD, RENAME, DELETE }

    private var dialog: Dialog? = null
    private var pendingOpen = false
    private var kind = BrowseKind.SRC
    private var files: List<File> = emptyList()
    private var tag = ""
    private val buffer = ImString(128)

    private fun open(d: Dialog, k: BrowseKind, targets: List<File>, t: String) {
        dialog = d
        kind = k
        files = targets
        tag = t
        pendingOpen = true
    }

    fun requestAdd(kind: BrowseKind, targets: List<File>) {
        if (targets.isEmpty()) return
        buffer.set("")
        open(Dialog.ADD, kind, targets, "")
    }

    fun requestRename(kind: BrowseKind, tag: String) {
        buffer.set(tag)
        open(Dialog.RENAME, kind, taggedFiles(kind, tag), tag)
    }

    fun requestDelete(kind: BrowseKind, tag: String) = open(Dialog.DELETE, kind, taggedFiles(kind, tag), tag)

    /** The saved files of [kind] carrying [tag]. */
    fun taggedFiles(kind: BrowseKind, tag: String): List<File> = BrowseCatalogs.get(kind).rows(BrowseScope.Tag(tag)).map { File(it.asset.path) }

    fun draw(mixer: Mixer) {
        val d = dialog ?: return
        val id = when (d) {
            Dialog.ADD -> "Add Tag##LibraryAddTag"
            Dialog.RENAME -> "Rename Tag##LibraryRenameTag"
            Dialog.DELETE -> "Delete Tag##LibraryDeleteTag"
        }
        if (pendingOpen) {
            ImGui.openPopup(id)
            pendingOpen = false
        }
        if (!ImGui.beginPopupModal(id, ImGuiWindowFlags.AlwaysAutoResize)) return
        when (d) {
            Dialog.ADD -> drawAdd(mixer)
            Dialog.RENAME -> drawRename(mixer)
            Dialog.DELETE -> drawDelete(mixer)
        }
        ImGui.endPopup()
    }

    private fun close() {
        dialog = null
        ImGui.closeCurrentPopup()
    }

    private fun items(n: Int) = if (n == 1) "1 item" else "$n items"

    private fun drawAdd(mixer: Mixer) {
        ImGui.text("Add tags to ${items(files.size)} (comma-separated):")
        ModalKeys.focusOnOpen()
        ImGui.setNextItemWidth(320f)
        ImGui.inputText("##libraryAddTag", buffer)
        // The kind's existing tags, one click each, so crates are reused rather than misspelled.
        val existing = BrowseCatalogs.get(kind).tags()
        if (existing.isNotEmpty()) {
            ImGui.textDisabled("Existing tags:")
            val maxX = ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX()
            for ((i, t) in existing.withIndex()) {
                val w = ImGui.calcTextSize(t).x + ImGui.getStyle().framePaddingX * 2
                if (i > 0 && ImGui.getItemRectMaxX() + ImGui.getStyle().itemSpacingX + w < maxX) ImGui.sameLine()
                if (ImGui.smallButton("$t##tag_$i")) {
                    val typed = LibraryTags.parse(buffer.get())
                    if (t !in typed) buffer.set((typed + t).joinToString(", "))
                }
            }
            itemTooltip("Click a tag to add it to the field.")
        }
        ImGui.separator()
        val key = ModalKeys.answer()
        if (ButtonChrome.button("Add", 120f, 0f) || key == true) {
            val tags = LibraryTags.parse(buffer.get())
            if (tags.isNotEmpty()) {
                val n = LibraryTags.add(files, tags, mixer.fxChains)
                ToastOverlay.show("Tagged ${items(n)} ${tags.joinToString(", ")}")
            }
            close()
        }
        ImGui.sameLine()
        if (ButtonChrome.button("Cancel", 120f, 0f) || key == false) close()
    }

    private fun drawRename(mixer: Mixer) {
        ImGui.text("Rename tag '$tag' on ${items(files.size)} to:")
        ModalKeys.focusOnOpen()
        ImGui.setNextItemWidth(320f)
        ImGui.inputText("##libraryRenameTag", buffer)
        ImGui.separator()
        val key = ModalKeys.answer()
        if (ButtonChrome.button("Rename", 120f, 0f) || key == true) {
            val new = LibraryTags.normalize(buffer.get())
            if (new.isNotEmpty() && new != tag && ',' !in new) {
                LibraryTags.rename(files, tag, new, mixer.fxChains)
                BrowserPane.followTag(kind, tag, new)
            }
            close()
        }
        ImGui.sameLine()
        if (ButtonChrome.button("Cancel", 120f, 0f) || key == false) close()
    }

    private fun drawDelete(mixer: Mixer) {
        ImGui.text("Remove tag '$tag' from ${items(files.size)}?")
        ImGui.textDisabled("The items themselves stay.")
        ImGui.separator()
        val key = ModalKeys.answer()
        if (ButtonChrome.button("Remove", 120f, 0f) || key == true) {
            LibraryTags.remove(files, tag, mixer.fxChains)
            BrowserPane.followTag(kind, tag, null)
            close()
        }
        ImGui.sameLine()
        if (ButtonChrome.button("Cancel", 120f, 0f) || key == false) close()
    }
}
