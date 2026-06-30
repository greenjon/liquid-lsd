package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import mu.KotlinLogging
import java.io.File

/**
 * A fully ImGui-native file browser rendered as a modal popup.
 *
 * Replaces all `java.awt.FileDialog` calls so the OpenGL context is never
 * interrupted by a native OS window.
 *
 * Usage (one instance per call-site, or share a single instance and call
 * [open] with the desired mode before each use):
 *
 * ```kotlin
 * // Trigger (once, outside the popup):
 * fileBrowser.open(ImGuiFileBrowser.Mode.LOAD, startDir = File("presets/global"))
 *
 * // Draw every frame (inside your render function):
 * fileBrowser.draw { file ->
 *     // called once when the user confirms a selection
 *     doSomethingWith(file)
 * }
 * ```
 *
 * Thread safety: all `java.io.File` calls happen on the render (main) thread,
 * which is correct — they must never run on the JACK audio thread.
 */
class ImGuiFileBrowser(private val id: String = "##fileBrowser") {

    private val logger = KotlinLogging.logger {}

    enum class Mode { LOAD, SAVE }

    // ── State ─────────────────────────────────────────────────────────────────

    internal var mode: Mode = Mode.LOAD
    private var currentDir: File = File("presets/global").canonicalFile
    private var selectedFile: File? = null
    private val filenameInput = ImString(128)
    private val filterExt: String = ".json"

    /** Set to true for one frame to trigger `ImGui.openPopup`. */
    private var pendingOpen = false

    /** Cached directory listing, refreshed whenever [currentDir] changes. */
    private var listing: List<File> = emptyList()
    private var listingDir: File? = null   // which dir the listing was built for

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Schedule the browser to open on the next [draw] call.
     *
     * @param mode       LOAD or SAVE
     * @param startDir   Directory to open initially (defaults to `presets/global`)
     * @param initialName Pre-populate the filename input (useful for Save As)
     */
    fun open(
        mode: Mode,
        startDir: File = File("presets/global").canonicalFile,
        initialName: String = ""
    ) {
        this.mode = mode
        currentDir = startDir.canonicalFile
        selectedFile = null
        filenameInput.set(initialName)
        listingDir = null   // force refresh
        pendingOpen = true
    }

    /**
     * Must be called every frame from the render thread.
     * Draws the modal popup when open; calls [onConfirm] with the chosen [File]
     * when the user clicks Save/Open.
     *
     * @param onConfirm Invoked (on the render thread) with the confirmed file.
     */
    fun draw(onConfirm: (File) -> Unit) {
        if (pendingOpen) {
            ImGui.openPopup(popupId())
            pendingOpen = false
        }

        val title = if (mode == Mode.SAVE) "Save Project###$id" else "Load Project###$id"

        ImGui.setNextWindowSize(600f, 420f, imgui.flag.ImGuiCond.Appearing)
        ImGui.setNextWindowPos(
            ImGui.getIO().displaySizeX * 0.5f,
            ImGui.getIO().displaySizeY * 0.5f,
            imgui.flag.ImGuiCond.Appearing,
            0.5f, 0.5f
        )

        val flags = ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove
        if (!ImGui.beginPopupModal(title, flags)) return

        refreshListingIfNeeded()

        drawBreadcrumb()
        ImGui.separator()
        drawFileList()
        ImGui.separator()
        drawFilenameRow()
        ImGui.spacing()
        drawButtons(onConfirm)

        ImGui.endPopup()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun popupId() = if (mode == Mode.SAVE) "Save Project###$id" else "Load Project###$id"

    private fun refreshListingIfNeeded() {
        if (listingDir == currentDir) return
        listingDir = currentDir
        listing = buildListing(currentDir)
    }

    /**
     * Returns a sorted list: parent-dir entry first (if applicable), then
     * subdirectories, then `.json` files.
     */
    private fun buildListing(dir: File): List<File> {
        val entries = dir.listFiles() ?: return emptyList()
        val dirs  = entries.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val files = entries.filter { it.isFile && it.name.endsWith(filterExt) }
                           .sortedBy { it.name.lowercase() }
        return dirs + files
    }

    /** Clickable breadcrumb: each path segment navigates up to that directory. */
    private fun drawBreadcrumb() {
        ImGui.pushID("breadcrumb")
        val parts = mutableListOf<File>()
        var cur: File? = currentDir
        while (cur != null) {
            parts.add(0, cur)
            cur = cur.parentFile
        }

        // Only show the last 4 segments to avoid overflow
        val visible = if (parts.size > 4) parts.takeLast(4) else parts
        val showEllipsis = parts.size > 4

        if (showEllipsis) {
            ImGui.textDisabled("...")
            ImGui.sameLine()
        }

        visible.forEachIndexed { i, segment ->
            val name = segment.name.ifEmpty { segment.path } // root on Linux is ""
            if (ImGui.button(name)) {
                navigateTo(segment)
            }
            if (i < visible.lastIndex) {
                ImGui.sameLine()
                ImGui.textDisabled("/")
                ImGui.sameLine()
            }
        }
        ImGui.popID()
    }

    /** Scrollable list of directories and .json files. */
    private fun drawFileList() {
        val listH = 240f
        ImGui.beginChild("##fileList", ImGui.getContentRegionAvailX(), listH, false)

        // ".." entry to go up
        val parent = currentDir.parentFile
        if (parent != null) {
            if (ImGui.selectable("[..]", false)) {
                navigateTo(parent)
            }
        }

        for (entry in listing) {
            val isDir = entry.isDirectory
            val label = if (isDir) "[${entry.name}]" else entry.name
            val isSelected = selectedFile == entry

            if (ImGui.selectable(label, isSelected)) {
                if (isDir) {
                    navigateTo(entry)
                } else {
                    selectedFile = entry
                    filenameInput.set(entry.nameWithoutExtension)
                }
            }
            // Double-click on a file confirms immediately
            if (!isDir && ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                selectedFile = entry
                filenameInput.set(entry.nameWithoutExtension)
                // We can't call onConfirm here (no closure), so we just pre-select;
                // the confirm button logic below will fire on the same frame via
                // the flag approach — but double-click confirm is handled in drawButtons.
                pendingDoubleClickConfirm = true
            }
        }

        ImGui.endChild()
    }

    private var pendingDoubleClickConfirm = false

    /** Filename text input at the bottom. */
    private fun drawFilenameRow() {
        ImGui.text("Filename:")
        ImGui.sameLine()
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
        ImGui.inputText("##filename", filenameInput)
        ImGui.popItemWidth()
    }

    /** Save/Open and Cancel buttons. */
    private fun drawButtons(onConfirm: (File) -> Unit) {
        val confirmLabel = if (mode == Mode.SAVE) "Save" else "Open"
        val btnW = 90f

        val doConfirm = ImGui.button(confirmLabel, btnW, 0f) || pendingDoubleClickConfirm
        pendingDoubleClickConfirm = false

        if (doConfirm) {
            val name = filenameInput.get().trim()
            if (name.isNotEmpty()) {
                val target = File(currentDir, if (name.endsWith(filterExt)) name else "$name$filterExt")
                logger.info { "FileBrowser confirmed: ${target.absolutePath}" }
                onConfirm(target)
                ImGui.closeCurrentPopup()
            }
        }

        ImGui.sameLine()
        if (ImGui.button("Cancel", btnW, 0f)) {
            ImGui.closeCurrentPopup()
        }
    }

    private fun navigateTo(dir: File) {
        currentDir = dir.canonicalFile
        listingDir = null   // force refresh next frame
        selectedFile = null
    }
}
