package llm.slop.liquidlsd.ui

import mu.KotlinLogging
import java.io.File
import java.time.LocalDateTime

/**
 * Manages playlist file operations (loading, saving, editing).
 * Playlists are stored as simple text files with one preset path per line.
 */
object PlaylistManager {
    private val logger = KotlinLogging.logger {}
    
    /**
     * Represents a playlist in memory.
     */
    data class Playlist(
        val name: String,
        val filePath: String,
        val presets: MutableList<String> = mutableListOf()
    ) {
        val isDirty: Boolean
            get() = originalPresets != presets
        
        private var originalPresets: List<String> = presets.toList()
        
        fun markClean() {
            originalPresets = presets.toList()
        }
        
        /**
         * Validates all preset references and returns list of missing presets.
         */
        fun validatePresets(): List<String> {
            return presets.filter { !resolvePreset(it).exists() }
        }
    }

    /**
     * Resolves a preset path, checking absolute and relative locations.
     */
    fun resolvePreset(path: String): File {
        val f = File(path)
        if (f.exists()) return f
        
        // Try relative to presets root
        val relative = File(FileSystemManager.getPresetsRoot(), path)
        if (relative.exists()) return relative
        
        // Try with extension if missing
        if (f.extension.isEmpty()) {
            val possible = listOf("$path.lsd")
            for (p in possible) {
                val pf = File(p)
                if (pf.exists()) return pf
                val pr = File(FileSystemManager.getPresetsRoot(), p)
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
            val presets = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toMutableList()
            
            val playlist = Playlist(
                name = file.nameWithoutExtension,
                filePath = file.absolutePath,
                presets = presets
            )
            playlist.markClean()
            
            logger.info { "Loaded playlist: ${file.name} with ${presets.size} presets" }
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
                appendLine("# Liquid LSD Playlist: ${playlist.name}")
                appendLine("# Generated: ${LocalDateTime.now()}")
                appendLine()
                playlist.presets.forEach { preset ->
                    appendLine(preset)
                }
            }
            
            file.writeText(content)
            playlist.markClean()
            
            logger.info { "Saved playlist: ${playlist.name} with ${playlist.presets.size} presets" }
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
                presets = mutableListOf()
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
     * Inserts a preset at a specific index in the playlist.
     */
    fun insertPreset(playlist: Playlist, presetPath: String, index: Int): Result<Unit> {
        return try {
            if (index < 0 || index > playlist.presets.size) {
                return Result.failure(IllegalArgumentException("Invalid index: $index"))
            }
            
            val relativePath = try {
                val file = File(presetPath)
                val root = FileSystemManager.getPresetsRoot().canonicalFile
                if (file.canonicalPath.startsWith(root.path)) {
                    file.canonicalFile.relativeTo(root).path
                } else {
                    presetPath
                }
            } catch (e: Exception) {
                presetPath
            }
            
            playlist.presets.add(index, relativePath)
            logger.info { "Inserted preset at index $index: $relativePath" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to insert preset" }
            Result.failure(e)
        }
    }
    
    /**
     * Removes a preset at a specific index from the playlist.
     */
    fun removePreset(playlist: Playlist, index: Int): Result<Unit> {
        return try {
            if (index < 0 || index >= playlist.presets.size) {
                return Result.failure(IllegalArgumentException("Invalid index: $index"))
            }
            
            val removed = playlist.presets.removeAt(index)
            logger.debug { "Removed preset at index $index: $removed" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove preset" }
            Result.failure(e)
        }
    }
    
    /**
     * Moves a preset from one index to another within the playlist.
     */
    fun movePreset(playlist: Playlist, fromIndex: Int, toIndex: Int): Result<Unit> {
        return try {
            if (fromIndex < 0 || fromIndex >= playlist.presets.size) {
                return Result.failure(IllegalArgumentException("Invalid from index: $fromIndex"))
            }
            if (toIndex < 0 || toIndex >= playlist.presets.size) {
                return Result.failure(IllegalArgumentException("Invalid to index: $toIndex"))
            }
            
            val preset = playlist.presets.removeAt(fromIndex)
            playlist.presets.add(toIndex, preset)
            logger.debug { "Moved preset from $fromIndex to $toIndex" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to move preset" }
            Result.failure(e)
        }
    }
    
    /**
     * Unpacks a playlist and inserts all its presets at the specified index.
     * This is the "flat unpacking" operation for drag-and-drop.
     */
    fun unpackPlaylistInto(targetPlaylist: Playlist, sourcePlaylistPath: String, insertIndex: Int): Result<Unit> {
        return try {
            val sourceFile = File(sourcePlaylistPath)
            val sourcePlaylist = loadPlaylist(sourceFile).getOrThrow()
            
            if (insertIndex < 0 || insertIndex > targetPlaylist.presets.size) {
                return Result.failure(IllegalArgumentException("Invalid insert index: $insertIndex"))
            }
            
            targetPlaylist.presets.addAll(insertIndex, sourcePlaylist.presets)
            logger.info { "Unpacked ${sourcePlaylist.presets.size} presets from ${sourceFile.name} into ${targetPlaylist.name}" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to unpack playlist" }
            Result.failure(e)
        }
    }
    
    /**
     * Relinks a missing preset to a new path.
     */
    fun relinkPreset(playlist: Playlist, index: Int, newPath: String): Result<Unit> {
        return try {
            if (index < 0 || index >= playlist.presets.size) {
                return Result.failure(IllegalArgumentException("Invalid index: $index"))
            }
            
            val file = File(newPath)
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("New preset file does not exist"))
            }
            
            val relativePath = try {
                val root = FileSystemManager.getPresetsRoot().canonicalFile
                if (file.canonicalPath.startsWith(root.path)) {
                    file.canonicalFile.relativeTo(root).path
                } else {
                    newPath
                }
            } catch (e: Exception) {
                newPath
            }
            
            playlist.presets[index] = relativePath
            logger.info { "Relinked preset at index $index to $relativePath" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to relink preset" }
            Result.failure(e)
        }
    }

    /**
     * Scans every playlist on disk and replaces [oldAbsPath] with [newAbsPath] wherever it appears.
     * Paths stored in playlists may be relative to the presets root, so both absolute and relative
     * forms of the old path are matched. Only playlist files that actually contained the old path
     * are rewritten.
     */
    fun updatePresetPathInAllPlaylists(oldAbsPath: String, newAbsPath: String) {
        val presetsRoot = FileSystemManager.getPresetsRoot().canonicalFile
        val playlistsRoot = FileSystemManager.getPlaylistsRoot()

        fun toRelative(abs: String): String? = try {
            val f = File(abs).canonicalFile
            if (f.path.startsWith(presetsRoot.path)) f.relativeTo(presetsRoot).path else null
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
                    logger.info { "Updated preset path in playlist ${playlistFile.name}: $oldAbsPath -> $newAbsPath" }
                }
            }
    }
}
