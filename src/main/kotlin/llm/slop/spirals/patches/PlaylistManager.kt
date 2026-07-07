package llm.slop.spirals.patches

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.spirals.config.ProjectConfig
import llm.slop.spirals.models.PlaylistDto
import mu.KotlinLogging
import java.io.File

object PlaylistManager {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    val activePlaylist = mutableListOf<File>()
    var currentPlaylistFile: File? = null
    var isDirty = false

    fun createNew() {
        activePlaylist.clear()
        currentPlaylistFile = null
        isDirty = false
    }

    fun loadPlaylist(file: File) {
        try {
            val content = file.readText()
            val lines = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            
            activePlaylist.clear()
            lines.forEach { itemName ->
                var deckFile = File(itemName)
                if (!deckFile.exists()) {
                    deckFile = File(ProjectConfig.Paths.PATCHES_DIR, itemName)
                }
                if (!deckFile.exists()) {
                    val possible = listOf(
                        itemName, "$itemName.lsd", "$itemName.json",
                        "${ProjectConfig.Paths.PATCHES_DIR}/$itemName.lsd",
                        "${ProjectConfig.Paths.PATCHES_DIR}/$itemName.json"
                    )
                    var found = false
                    for (p in possible) {
                        val f = File(p)
                        if (f.exists()) {
                            activePlaylist.add(f)
                            found = true
                            break
                        }
                    }
                    if (!found) logger.warn { "Playlist item not found: $itemName" }
                } else {
                    activePlaylist.add(deckFile)
                }
            }
            currentPlaylistFile = file
            isDirty = false
            logger.info { "Loaded playlist: ${file.name} (${activePlaylist.size} items)" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load playlist: ${file.absolutePath}" }
        }
    }

    fun savePlaylist(file: File) {
        try {
            val content = buildString {
                appendLine("# ${ProjectConfig.App.PLAYLIST_FILE_COMMENT_PREFIX}: ${file.nameWithoutExtension}")
                activePlaylist.forEach { patch ->
                    appendLine(patch.name)
                }
            }
            file.parentFile?.mkdirs()
            file.writeText(content)
            currentPlaylistFile = file
            isDirty = false
            logger.info { "Saved playlist: ${file.absolutePath}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save playlist: ${file.absolutePath}" }
        }
    }

    fun addToPlaylist(file: File) {
        activePlaylist.add(file)
        isDirty = true
    }

    fun removeFromPlaylist(index: Int) {
        if (index in activePlaylist.indices) {
            activePlaylist.removeAt(index)
            isDirty = true
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from in activePlaylist.indices && to in activePlaylist.indices) {
            val item = activePlaylist.removeAt(from)
            activePlaylist.add(to, item)
            isDirty = true
        }
    }

    fun initializeFromQueue(queue: List<File>) {
        activePlaylist.clear()
        activePlaylist.addAll(queue)
        currentPlaylistFile = null
        isDirty = true
    }

    fun pushToPlayQueue() {
        activePlaylist.forEach { file ->
            PlayQueueManager.appendToQueue(file)
        }
        logger.info { "Pushed ${activePlaylist.size} items to Play Queue" }
    }
}
