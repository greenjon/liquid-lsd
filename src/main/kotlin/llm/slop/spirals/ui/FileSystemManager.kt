package llm.slop.spirals.ui

import llm.slop.spirals.patches.PlaylistParser
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages file system operations for patches and playlists.
 * All operations are non-destructive by default and return success/failure status.
 */
object FileSystemManager {
    private val logger = KotlinLogging.logger {}
    
    private const val PATCHES_ROOT = "presets/patches"
    private const val PLAYLISTS_ROOT = "presets/playlists"
    
    /**
     * Scans a directory and returns all assets (patches, playlists, folders).
     */
    fun scanDirectory(directory: File): List<AssetItem> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }
        
        val items = mutableListOf<AssetItem>()
        
        directory.listFiles()?.forEach { file ->
            val ext = file.extension.lowercase()
            when {
                file.isDirectory -> {
                    items.add(AssetItem(
                        path = file.absolutePath,
                        name = file.name,
                        type = AssetType.FOLDER
                    ))
                }
                ext == "lsd" || ext == "patch" || ext == "json" -> {
                    items.add(AssetItem(
                        path = file.absolutePath,
                        name = file.nameWithoutExtension,
                        type = AssetType.PATCH,
                        isValid = validatePatchFile(file)
                    ))
                }
                ext == "lsdset" -> {
                    val validation = validatePlaylistFile(file)
                    items.add(AssetItem(
                        path = file.absolutePath,
                        name = file.nameWithoutExtension,
                        type = AssetType.PLAYLIST,
                        isValid = validation.first,
                        errorMessage = validation.second
                    ))
                }
            }
        }
        
        return items.sortedWith(compareBy({ it.type != AssetType.FOLDER }, { it.name }))
    }
    
    /**
     * Validates that a patch file exists and is readable.
     */
    private fun validatePatchFile(file: File): Boolean {
        return file.exists() && file.canRead() && file.length() > 0
    }
    
    /**
     * Validates a playlist file and checks if all referenced patches exist.
     * Returns (isValid, errorMessage).
     */
    private fun validatePlaylistFile(file: File): Pair<Boolean, String?> {
        if (!file.exists() || !file.canRead()) {
            return false to "File not readable"
        }
        
        try {
            val patches = PlaylistParser.parseFile(file)
            
            val missingPatches = patches.filter { path ->
                PlaylistParser.resolveItem(path, listOf(getPatchesRoot())) == null
            }
            
            if (missingPatches.isNotEmpty()) {
                return false to "Missing ${missingPatches.size} patch(es)"
            }
            
            return true to null
        } catch (e: Exception) {
            logger.error(e) { "Failed to validate playlist: ${file.name}" }
            return false to "Parse error"
        }
    }
    
    /**
     * Renames a file on disk.
     */
    fun renameFile(oldPath: String, newName: String): Result<String> {
        return try {
            val oldFile = File(oldPath)
            if (!oldFile.exists()) {
                return Result.failure(IllegalArgumentException("File does not exist: $oldPath"))
            }
            
            val newFile = File(oldFile.parent, "$newName.${oldFile.extension}")
            if (newFile.exists()) {
                return Result.failure(IllegalArgumentException("File already exists: ${newFile.name}"))
            }
            
            if (oldFile.renameTo(newFile)) {
                logger.info { "Renamed ${oldFile.name} to ${newFile.name}" }
                Result.success(newFile.absolutePath)
            } else {
                Result.failure(IllegalStateException("Failed to rename file"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error renaming file: $oldPath" }
            Result.failure(e)
        }
    }
    
    /**
     * Clones a file with a _copy suffix.
     */
    fun cloneFile(sourcePath: String): Result<String> {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return Result.failure(IllegalArgumentException("Source file does not exist"))
            }
            
            val baseName = sourceFile.nameWithoutExtension
            val extension = sourceFile.extension
            var copyIndex = 1
            var targetFile: File
            
            do {
                val copyName = if (copyIndex == 1) {
                    "${baseName}_copy.$extension"
                } else {
                    "${baseName}_copy$copyIndex.$extension"
                }
                targetFile = File(sourceFile.parent, copyName)
                copyIndex++
            } while (targetFile.exists())
            
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
            logger.info { "Cloned ${sourceFile.name} to ${targetFile.name}" }
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            logger.error(e) { "Error cloning file: $sourcePath" }
            Result.failure(e)
        }
    }
    
    /**
     * Moves a file to a target directory.
     */
    fun moveFile(sourcePath: String, targetDirectory: String): Result<String> {
        return try {
            val sourceFile = File(sourcePath)
            val targetDir = File(targetDirectory)
            
            if (!sourceFile.exists()) {
                return Result.failure(IllegalArgumentException("Source file does not exist"))
            }
            
            if (!targetDir.exists() || !targetDir.isDirectory) {
                return Result.failure(IllegalArgumentException("Target directory does not exist"))
            }
            
            val targetFile = File(targetDir, sourceFile.name)
            if (targetFile.exists()) {
                return Result.failure(IllegalArgumentException("File already exists in target directory"))
            }
            
            Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            logger.info { "Moved ${sourceFile.name} to ${targetDir.name}" }
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            logger.error(e) { "Error moving file: $sourcePath" }
            Result.failure(e)
        }
    }
    
    /**
     * Deletes a file from disk.
     */
    fun deleteFile(path: String): Result<Unit> {
        return try {
            val file = File(path)
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("File does not exist"))
            }
            
            if (file.delete()) {
                logger.info { "Deleted ${file.name}" }
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Failed to delete file"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting file: $path" }
            Result.failure(e)
        }
    }
    
    /**
     * Creates a new directory.
     */
    fun createDirectory(parentPath: String, name: String): Result<String> {
        return try {
            val parentDir = File(parentPath)
            if (!parentDir.exists() || !parentDir.isDirectory) {
                return Result.failure(IllegalArgumentException("Parent directory does not exist"))
            }
            
            val newDir = File(parentDir, name)
            if (newDir.exists()) {
                return Result.failure(IllegalArgumentException("Directory already exists"))
            }
            
            if (newDir.mkdir()) {
                logger.info { "Created directory: $name" }
                Result.success(newDir.absolutePath)
            } else {
                Result.failure(IllegalStateException("Failed to create directory"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error creating directory: $name" }
            Result.failure(e)
        }
    }
    
    /**
     * Gets the root directory for patches.
     */
    fun getPatchesRoot(): File {
        val root = File(PATCHES_ROOT)
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }
    
    /**
     * Gets the root directory for playlists.
     */
    fun getPlaylistsRoot(): File {
        val root = File(PLAYLISTS_ROOT)
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }
}
