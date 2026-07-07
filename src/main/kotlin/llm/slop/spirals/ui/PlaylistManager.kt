package llm.slop.spirals.ui

import llm.slop.spirals.config.ProjectConfig
import mu.KotlinLogging
import java.io.File
import java.time.LocalDateTime

/**
 * Manages playlist file operations (loading, saving, editing).
 * Playlists are stored as simple text files with one patch path per line.
 */
object PlaylistManager {
    private val logger = KotlinLogging.logger {}
    
    /**
     * Represents a playlist in memory.
     */
    data class Playlist(
        val name: String,
        val filePath: String,
        val patches: MutableList<String> = mutableListOf()
    ) {
        val isDirty: Boolean
            get() = originalPatches != patches
        
        private var originalPatches: List<String> = patches.toList()
        
        fun markClean() {
            originalPatches = patches.toList()
        }
        
        /**
         * Validates all patch references and returns list of missing patches.
         */
        fun validatePatches(): List<String> {
            return patches.filter { !resolvePatch(it).exists() }
        }
    }

    /**
     * Resolves a patch path, checking absolute and relative locations.
     */
    fun resolvePatch(path: String): File {
        val f = File(path)
        if (f.exists()) return f
        
        // Try relative to patches root
        val relative = File(FileSystemManager.getPatchesRoot(), path)
        if (relative.exists()) return relative
        
        // Try with extension if missing
        if (f.extension.isEmpty()) {
            val possible = listOf("$path.lsd", "$path.patch", "$path.json")
            for (p in possible) {
                val pf = File(p)
                if (pf.exists()) return pf
                val pr = File(FileSystemManager.getPatchesRoot(), p)
                if (pr.exists()) return pr
            }
        }
        
        return f
    }
    
    /**
     * Loads a playlist from disk.
     */
    fun loadPlaylist(file: File): Result<Playlist> {
        return try {
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("Playlist file does not exist"))
            }
            
            val content = file.readText()
            val patches = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toMutableList()
            
            val playlist = Playlist(
                name = file.nameWithoutExtension,
                filePath = file.absolutePath,
                patches = patches
            )
            playlist.markClean()
            
            logger.info { "Loaded playlist: ${file.name} with ${patches.size} patches" }
            Result.success(playlist)
        } catch (e: Exception) {
            logger.error(e) { "Failed to load playlist: ${file.name}" }
            Result.failure(e)
        }
    }
    
    /**
     * Saves a playlist to disk.
     */
    fun savePlaylist(playlist: Playlist): Result<Unit> {
        return try {
            val file = File(playlist.filePath)
            val content = buildString {
                appendLine("# ${ProjectConfig.App.PLAYLIST_FILE_COMMENT_PREFIX}: ${playlist.name}")
                appendLine("# Generated: ${LocalDateTime.now()}")
                appendLine()
                playlist.patches.forEach { patch ->
                    appendLine(patch)
                }
            }
            
            file.writeText(content)
            playlist.markClean()
            
            logger.info { "Saved playlist: ${playlist.name} with ${playlist.patches.size} patches" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to save playlist: ${playlist.name}" }
            Result.failure(e)
        }
    }
    
    /**
     * Creates a new empty playlist.
     */
    fun createPlaylist(name: String, directory: File): Result<Playlist> {
        return try {
            if (!directory.exists() || !directory.isDirectory) {
                return Result.failure(IllegalArgumentException("Invalid directory"))
            }
            
            val file = File(directory, "$name.lsdset")
            if (file.exists()) {
                return Result.failure(IllegalArgumentException("Playlist already exists"))
            }
            
            val playlist = Playlist(
                name = name,
                filePath = file.absolutePath,
                patches = mutableListOf()
            )
            
            savePlaylist(playlist)
            logger.info { "Created new playlist: $name" }
            Result.success(playlist)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create playlist: $name" }
            Result.failure(e)
        }
    }
    
    /**
     * Inserts a patch at a specific index in the playlist.
     */
    fun insertPatch(playlist: Playlist, patchPath: String, index: Int): Result<Unit> {
        return try {
            if (index < 0 || index > playlist.patches.size) {
                return Result.failure(IllegalArgumentException("Invalid index: $index"))
            }
            
            val relativePath = try {
                val file = File(patchPath)
                val root = FileSystemManager.getPatchesRoot().canonicalFile
                if (file.canonicalPath.startsWith(root.path)) {
                    file.canonicalFile.relativeTo(root).path
                } else {
                    patchPath
                }
            } catch (e: Exception) {
                patchPath
            }
            
            playlist.patches.add(index, relativePath)
            logger.info { "Inserted patch at index $index: $relativePath" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to insert patch" }
            Result.failure(e)
        }
    }
    
    /**
     * Removes a patch at a specific index from the playlist.
     */
    fun removePatch(playlist: Playlist, index: Int): Result<Unit> {
        return try {
            if (index < 0 || index >= playlist.patches.size) {
                return Result.failure(IllegalArgumentException("Invalid index: $index"))
            }
            
            val removed = playlist.patches.removeAt(index)
            logger.debug { "Removed patch at index $index: $removed" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove patch" }
            Result.failure(e)
        }
    }
    
    /**
     * Moves a patch from one index to another within the playlist.
     */
    fun movePatch(playlist: Playlist, fromIndex: Int, toIndex: Int): Result<Unit> {
        return try {
            if (fromIndex < 0 || fromIndex >= playlist.patches.size) {
                return Result.failure(IllegalArgumentException("Invalid from index: $fromIndex"))
            }
            if (toIndex < 0 || toIndex >= playlist.patches.size) {
                return Result.failure(IllegalArgumentException("Invalid to index: $toIndex"))
            }
            
            val patch = playlist.patches.removeAt(fromIndex)
            playlist.patches.add(toIndex, patch)
            logger.debug { "Moved patch from $fromIndex to $toIndex" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to move patch" }
            Result.failure(e)
        }
    }
    
    /**
     * Unpacks a playlist and inserts all its patches at the specified index.
     * This is the "flat unpacking" operation for drag-and-drop.
     */
    fun unpackPlaylistInto(targetPlaylist: Playlist, sourcePlaylistPath: String, insertIndex: Int): Result<Unit> {
        return try {
            val sourceFile = File(sourcePlaylistPath)
            val sourcePlaylist = loadPlaylist(sourceFile).getOrThrow()
            
            if (insertIndex < 0 || insertIndex > targetPlaylist.patches.size) {
                return Result.failure(IllegalArgumentException("Invalid insert index: $insertIndex"))
            }
            
            targetPlaylist.patches.addAll(insertIndex, sourcePlaylist.patches)
            logger.info { "Unpacked ${sourcePlaylist.patches.size} patches from ${sourceFile.name} into ${targetPlaylist.name}" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to unpack playlist" }
            Result.failure(e)
        }
    }
    
    /**
     * Relinks a missing patch to a new path.
     */
    fun relinkPatch(playlist: Playlist, index: Int, newPath: String): Result<Unit> {
        return try {
            if (index < 0 || index >= playlist.patches.size) {
                return Result.failure(IllegalArgumentException("Invalid index: $index"))
            }
            
            val file = File(newPath)
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("New patch file does not exist"))
            }
            
            val relativePath = try {
                val root = FileSystemManager.getPatchesRoot().canonicalFile
                if (file.canonicalPath.startsWith(root.path)) {
                    file.canonicalFile.relativeTo(root).path
                } else {
                    newPath
                }
            } catch (e: Exception) {
                newPath
            }
            
            playlist.patches[index] = relativePath
            logger.info { "Relinked patch at index $index to $relativePath" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to relink patch" }
            Result.failure(e)
        }
    }

    /**
     * Scans every playlist on disk and replaces [oldAbsPath] with [newAbsPath] wherever it appears.
     * Paths stored in playlists may be relative to the patches root, so both absolute and relative
     * forms of the old path are matched. Only playlist files that actually contained the old path
     * are rewritten.
     */
    fun updatePatchPathInAllPlaylists(oldAbsPath: String, newAbsPath: String) {
        val patchesRoot = FileSystemManager.getPatchesRoot().canonicalFile
        val playlistsRoot = FileSystemManager.getPlaylistsRoot()

        fun toRelative(abs: String): String? = try {
            val f = File(abs).canonicalFile
            if (f.path.startsWith(patchesRoot.path)) f.relativeTo(patchesRoot).path else null
        } catch (e: Exception) { null }

        val oldRel = toRelative(oldAbsPath)
        val newRel = toRelative(newAbsPath) ?: newAbsPath

        // All forms the old path might appear as inside a playlist file
        val oldCandidates = listOfNotNull(oldAbsPath, oldRel).toSet()

        playlistsRoot.walkTopDown()
            .filter { it.isFile && it.extension == "lsdset" }
            .forEach { playlistFile ->
                val lines = playlistFile.readLines()
                var changed = false
                val updated = lines.map { line ->
                    if (line.trim() in oldCandidates) {
                        changed = true
                        newRel
                    } else {
                        line
                    }
                }
                if (changed) {
                    playlistFile.writeText(updated.joinToString("\n") + "\n")
                    logger.info { "Updated patch path in playlist ${playlistFile.name}: $oldAbsPath -> $newAbsPath" }
                }
            }
    }
}
