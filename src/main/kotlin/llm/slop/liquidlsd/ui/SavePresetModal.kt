package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import java.io.File

/**
 * Stateful singleton that renders a clean ImGui popup modal for saving, renaming,
 * editing metadata, or cloning deck presets, FX slot presets, and FX chains.
 * Accepts a preset name and comma-separated tags with universal overwrite protection.
 */
object SavePresetModal {

    private const val BUFFER_SIZE = 128

    private var titleText = "Save Preset As"
    private var confirmButtonLabel = "Save"
    private var originalPath: String? = null
    private var targetDirectory: File? = null
    private var folderRoot: File? = null

    /** The folder the preset is saved in: read it in the save callback, since a Folder dropdown ([request]'s `folderRoot`) can change it. */
    val directory: File get() = targetDirectory ?: FileSystemManager.getPresetsRoot()
    private var fileExtension: String = "lsd"

    private val nameBuffer = ImString(BUFFER_SIZE)
    private val tagsBuffer = ImString(BUFFER_SIZE)

    private var showOverwriteWarning = false
    private var lastCheckedName = ""

    private var onSaveCallback: ((name: String, tags: List<String>) -> Unit)? = null
    private var pendingOpen = false

    /**
     * Request opening the Preset Metadata modal.
     *
     * @param title Header title for the popup modal.
     * @param confirmLabel Text label for the confirmation button.
     * @param defaultName Initial name to populate in the text field.
     * @param defaultTags Initial list of tags to populate in the tags text field.
     * @param originalPath Optional path of the current preset file being edited/renamed.
     * @param targetDir Directory where the preset will be saved (defaults to presets root).
     * @param extension Extension for the preset file (defaults to "lsd").
     * @param folderRoot When set, the modal shows a Folder dropdown of this section root and its folders, starting at [targetDir].
     * @param onSave Callback executed when the user confirms saving.
     */
    fun request(
        title: String = "Save Preset As",
        confirmLabel: String = "Save",
        defaultName: String = "",
        defaultTags: List<String> = emptyList(),
        originalPath: String? = null,
        targetDir: File? = null,
        extension: String = "lsd",
        folderRoot: File? = null,
        onSave: (name: String, tags: List<String>) -> Unit
    ) {
        titleText = title
        confirmButtonLabel = confirmLabel
        this.originalPath = originalPath
        this.targetDirectory = targetDir
        this.fileExtension = extension
        this.folderRoot = folderRoot

        nameBuffer.set(defaultName)
        tagsBuffer.set(defaultTags.joinToString(", "))
        onSaveCallback = onSave

        showOverwriteWarning = false
        lastCheckedName = defaultName.removeSuffix(".$extension").removeSuffix(".lsd").trim()
        pendingOpen = true
    }

    /** "Folder:" and a dropdown of [root] and its folders; picking one changes [directory] (and the overwrite check). */
    private fun drawFolderCombo(root: File) {
        val current = directory
        fun labelOf(dir: File): String =
            if (dir.absoluteFile == root.absoluteFile) "(top level)" else dir.relativeTo(root).path.replace('\\', '/')
        ImGui.text("Folder:")
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
        if (ImGui.beginCombo("##savePresetFolder", labelOf(current))) {
            for (folder in listOf("") + llm.slop.liquidlsd.ui.browser.LibraryFolders.dirsUnder(root)) {
                val dir = llm.slop.liquidlsd.ui.browser.LibraryFolders.dirOf(root, folder)
                if (ImGui.selectable(labelOf(dir), dir.absoluteFile == current.absoluteFile)) {
                    targetDirectory = dir
                    showOverwriteWarning = false
                }
            }
            ImGui.endCombo()
        }
        ImGui.popItemWidth()
        itemTooltip("Where the file is saved. Starts in the folder of what you loaded, else the folder selected in the Library.")
    }

    /** Call every frame from the main render loop. */
    fun draw(session: llm.slop.liquidlsd.SessionContext) {
        val modalId = "$titleText###save_preset_modal"
        if (pendingOpen) {
            ImGui.openPopup(modalId)
            pendingOpen = false
        }

        val io = ImGui.getIO()
        val displayW = io.displaySizeX
        val saveW = 420f.coerceAtMost((displayW - 48f).coerceAtLeast(260f))

        ImGui.setNextWindowSize(saveW, 0f, ImGuiCond.Appearing)
        ImGui.setNextWindowPos(
            io.displaySizeX * 0.5f,
            io.displaySizeY * 0.5f,
            ImGuiCond.Appearing,
            0.5f, 0.5f
        )

        val flags = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoMove
        if (!ImGui.beginPopupModal(modalId, flags)) return

        ImGui.text("Preset Name:")
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
        val nameChanged = ImGui.inputText("##savePresetName", nameBuffer)
        ImGui.popItemWidth()

        val currentName = nameBuffer.get().removeSuffix(".$fileExtension").removeSuffix(".lsd").trim()
        if (nameChanged && currentName != lastCheckedName) {
            showOverwriteWarning = false
            lastCheckedName = currentName
        }

        folderRoot?.let { root ->
            ImGui.spacing()
            drawFolderCombo(root)
        }

        ImGui.spacing()
        ImGui.text("Tags (comma-separated):")
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
        ImGui.inputText("##savePresetTags", tagsBuffer)
        ImGui.popItemWidth()
        ImGui.textDisabled("e.g. ambient, geo, strobe")

        if (showOverwriteWarning) {
            ImGui.spacing()
            ImGui.pushStyleColor(ImGuiCol.Text, TangoPalette.TEXT_WARN.u32()) // Warning Amber
            ImGui.textWrapped("${Icons.ALERT} File '$currentName.$fileExtension' already exists. Overwrite?")
            ImGui.popStyleColor()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        val btnLabel = if (showOverwriteWarning) "Overwrite" else confirmButtonLabel
        val btnW = 100f

        if (ButtonChrome.button("$btnLabel##confirmSavePreset", btnW, 0f)) {
            if (currentName.isNotEmpty()) {
                val dir = targetDirectory ?: FileSystemManager.getPresetsRoot()
                val targetFile = File(dir, "$currentName.$fileExtension")

                val isSameAsOriginal = originalPath?.let { orig ->
                    try {
                        File(orig).canonicalPath == targetFile.canonicalPath
                    } catch (e: Exception) {
                        false
                    }
                } ?: false

                if (targetFile.exists() && !isSameAsOriginal && !showOverwriteWarning) {
                    showOverwriteWarning = true
                    lastCheckedName = currentName
                } else {
                    val tags = tagsBuffer.get()
                        .split(",")
                        .map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                    onSaveCallback?.invoke(currentName, tags)
                    ImGui.closeCurrentPopup()
                }
            }
        }
        ImGui.sameLine()
        if (ButtonChrome.button("Cancel##cancelSavePreset", btnW, 0f)) {
            ImGui.closeCurrentPopup()
        }

        ImGui.endPopup()
    }
}
