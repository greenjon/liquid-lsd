package llm.slop.spirals.ui

import imgui.ImGui
import java.io.File
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.patches.PatchManager
import mu.KotlinLogging

class ProjectManager(
    private val onTriggerConfirmPopup: () -> Unit,
    private val onTriggerExit: () -> Unit,
    private val getMixer: () -> Mixer?
) {
    private val logger = KotlinLogging.logger {}

    var currentGlobalPatchFile: File? = null
    var pendingSetlistFile: File? = null
    
    enum class PendingProjectAction {
        NONE, NEW, LOAD, LOAD_SETLIST, EXIT
    }
    
    var pendingProjectAction = PendingProjectAction.NONE

    private val fileBrowser = ImGuiFileBrowser("globalFileBrowser")
    private var lastLoadDir: File = File("presets/global").canonicalFile

    fun performNewProject(mixer: Mixer) {
        PatchManager.resetToDefault(mixer)
        currentGlobalPatchFile = null
    }

    fun loadGlobalPatchWithDialog() {
        pendingProjectAction = PendingProjectAction.LOAD
        val lastDir = currentGlobalPatchFile?.parentFile ?: File("presets/global").canonicalFile
        fileBrowser.open(ImGuiFileBrowser.Mode.LOAD, startDir = lastDir)
    }

    fun saveGlobalPatch(mixer: Mixer, forceAs: Boolean): Boolean {
        if (!forceAs && currentGlobalPatchFile != null) {
            val file = currentGlobalPatchFile!!
            PatchManager.saveGlobalPatchAsync(file, mixer, file.nameWithoutExtension)
            return true
        }
        val initialName = currentGlobalPatchFile?.nameWithoutExtension ?: "project"
        fileBrowser.open(
            ImGuiFileBrowser.Mode.SAVE,
            startDir = File("presets/global").canonicalFile,
            initialName = initialName
        )
        return false
    }

    fun performLoadProject() {
        loadGlobalPatchWithDialog()
    }

    fun performLoadFromSetlist(file: File) {
        currentGlobalPatchFile = file
        PatchManager.loadGlobalPatchAsync(file)
    }

    fun advanceSetlist(delta: Int) {
        val mixer = getMixer() ?: return
        val targetFile = SetlistPanel.getFileOffset(currentGlobalPatchFile, delta) ?: return
        if (targetFile.canonicalPath == currentGlobalPatchFile?.canonicalPath) return

        logger.info { "Advancing setlist by $delta to file: ${targetFile.name}" }

        val isDirty = PatchManager.isGlobalPatchDirty(mixer)
        if (!isDirty) {
            performLoadFromSetlist(targetFile)
            return
        }

        when (UITheme.setlistTransitionBehavior) {
            UITheme.SetlistTransitionBehavior.PROMPT -> {
                pendingSetlistFile = targetFile
                pendingProjectAction = PendingProjectAction.LOAD_SETLIST
                onTriggerConfirmPopup()
            }
            UITheme.SetlistTransitionBehavior.AUTO_DISCARD -> {
                performLoadFromSetlist(targetFile)
            }
            UITheme.SetlistTransitionBehavior.AUTO_SAVE -> {
                val currentFile = currentGlobalPatchFile
                if (currentFile == null) {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyMMdd-HH.mm.ss")
                    val filename = "New-${java.time.LocalDateTime.now().format(formatter)}.json"
                    val dir = File("presets/global")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, filename)
                    logger.info { "Autosaving untitled patch as $filename" }
                    PatchManager.saveGlobalPatchAsync(file, mixer, file.nameWithoutExtension)
                    currentGlobalPatchFile = file
                } else {
                    logger.info { "Autosaving current patch: ${currentFile.name}" }
                    PatchManager.saveGlobalPatchAsync(currentFile, mixer, currentFile.nameWithoutExtension)
                }
                performLoadFromSetlist(targetFile)
            }
        }
    }

    fun drawGlobalFileBrowser(mixer: Mixer) {
        fileBrowser.draw { file ->
            val name = file.nameWithoutExtension
            when (pendingProjectAction) {
                PendingProjectAction.LOAD -> {
                    if (fileBrowser.mode == ImGuiFileBrowser.Mode.SAVE) {
                        PatchManager.saveGlobalPatchAsync(file, mixer, name)
                        currentGlobalPatchFile = file
                        loadGlobalPatchWithDialog()
                    } else {
                        lastLoadDir = file.parentFile?.canonicalFile ?: lastLoadDir
                        currentGlobalPatchFile = file
                        PatchManager.loadGlobalPatchAsync(file)
                        pendingProjectAction = PendingProjectAction.NONE
                    }
                }
                PendingProjectAction.NEW -> {
                    currentGlobalPatchFile = file
                    PatchManager.saveGlobalPatchAsync(file, mixer, name)
                    performNewProject(mixer)
                    pendingProjectAction = PendingProjectAction.NONE
                }
                PendingProjectAction.LOAD_SETLIST -> {
                    currentGlobalPatchFile = file
                    PatchManager.saveGlobalPatchAsync(file, mixer, name)
                    pendingSetlistFile?.let { performLoadFromSetlist(it) }
                    pendingProjectAction = PendingProjectAction.NONE
                }
                PendingProjectAction.NONE -> {
                    currentGlobalPatchFile = file
                    PatchManager.saveGlobalPatchAsync(file, mixer, name)
                }
                PendingProjectAction.EXIT -> {
                    currentGlobalPatchFile = file
                    PatchManager.saveGlobalPatchAsync(file, mixer, name)
                    pendingProjectAction = PendingProjectAction.NONE
                    onTriggerExit()
                }
            }
        }
    }
}
