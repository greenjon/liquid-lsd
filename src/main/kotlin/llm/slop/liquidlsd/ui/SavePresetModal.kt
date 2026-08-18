package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import java.io.File

/**
 * Stateful singleton that renders a clean ImGui popup modal for saving, renaming,
 * editing metadata, or cloning deck presets.
 * Accepts a preset name and comma-separated tags with universal overwrite protection.
 */
object SavePresetModal {

    private const val BUFFER_SIZE = 128

    private var titleText = "Save Preset As"
    private var confirmButtonLabel = "Save"
    private var originalPath: String? = null

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
     * @param originalPath Optional path of the current preset file being edited/renamed (to avoid self-overwrite warnings).
     * @param onSave Callback executed when the user confirms saving.
     */
    fun request(
        title: String = "Save Preset As",
        confirmLabel: String = "Save",
        defaultName: String = "",
        defaultTags: List<String> = emptyList(),
        originalPath: String? = null,
        onSave: (name: String, tags: List<String>) -> Unit
    ) {
        titleText = title
        confirmButtonLabel = confirmLabel
        this.originalPath = originalPath

        nameBuffer.set(defaultName)
        tagsBuffer.set(defaultTags.joinToString(", "))
        onSaveCallback = onSave

        showOverwriteWarning = false
        lastCheckedName = defaultName.removeSuffix(".lsd").trim()
        pendingOpen = true
    }

    /** Call every frame from the main render loop. */
    fun draw(session: llm.slop.liquidlsd.SessionContext) {
        val modalId = "$titleText###save_preset_modal"
        if (pendingOpen) {
            ImGui.openPopup(modalId)
            pendingOpen = false
        }

        val displayW = ImGui.getIO().displaySizeX
        val displayH = ImGui.getIO().displaySizeY
        val saveW = 420f.coerceAtMost((displayW - 48f).coerceAtLeast(260f))

        ImGui.setNextWindowSize(saveW, 0f, ImGuiCond.Appearing)
        ImGui.setNextWindowPos(
            displayW * 0.5f,
            displayH * 0.5f,
            ImGuiCond.Appearing,
            0.5f, 0.5f
        )

        val flags = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoMove
        if (!ImGui.beginPopupModal(modalId, flags)) return

        ImGui.text("Preset Name:")
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
        val nameChanged = ImGui.inputText("##savePresetName", nameBuffer)
        ImGui.popItemWidth()

        val currentName = nameBuffer.get().removeSuffix(".lsd").trim()
        if (nameChanged && currentName != lastCheckedName) {
            showOverwriteWarning = false
            lastCheckedName = currentName
        }

        ImGui.spacing()
        ImGui.text("Tags (comma-separated):")
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
        ImGui.inputText("##savePresetTags", tagsBuffer)
        ImGui.popItemWidth()
        ImGui.textDisabled("e.g. ambient, geo, strobe")

        if (showOverwriteWarning) {
            ImGui.spacing()
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.75f, 0.25f, 1.0f) // Warning Amber
            ImGui.textWrapped("\u26A0 File '$currentName.lsd' already exists. Overwrite?")
            ImGui.popStyleColor()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        val btnLabel = if (showOverwriteWarning) "Overwrite" else confirmButtonLabel
        val btnW = 100f

        if (ImGui.button("$btnLabel##confirmSavePreset", btnW, 0f)) {
            if (currentName.isNotEmpty()) {
                val presetsDir = FileSystemManager.getPresetsRoot()
                val targetFile = File(presetsDir, "$currentName.lsd")

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
        if (ImGui.button("Cancel##cancelSavePreset", btnW, 0f)) {
            ImGui.closeCurrentPopup()
        }

        ImGui.endPopup()
    }
}
