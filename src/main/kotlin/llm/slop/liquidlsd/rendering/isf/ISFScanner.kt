package llm.slop.liquidlsd.rendering.isf

import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Recursively scans directory sources and safely parses ISF shader headers and metadata.
 */
object ISFScanner {

    private val shaderExtensions = setOf("fs", "isf", "frag")
    private val vertexExtensions = setOf("vs", "vert")

    /**
     * Recursively scans the given directory for ISF shader assets.
     */
    fun scanDirectory(
        directory: File,
        sourceType: DirectorySourceType,
        directoryPath: String
    ): List<ISFAsset> {
        val assets = mutableListOf<ISFAsset>()
        if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
            return assets
        }

        val files = directory.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in shaderExtensions }
            .toList()

        for (shaderFile in files) {
            try {
                val asset = parseShaderFile(shaderFile, sourceType, directoryPath)
                if (asset != null) {
                    assets.add(asset)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse shader file: ${shaderFile.absolutePath}" }
            }
        }

        return assets
    }

    /**
     * Safely parses an individual ISF shader file, extracting header metadata and classifying its asset type.
     */
    fun parseShaderFile(
        file: File,
        sourceType: DirectorySourceType,
        directoryPath: String
    ): ISFAsset? {
        val rawSource = try {
            file.readText()
        } catch (e: Exception) {
            logger.error(e) { "Failed to read shader file: ${file.absolutePath}" }
            return null
        }

        val header = try {
            ISFParser.parseHeader(rawSource)
        } catch (e: Exception) {
            logger.warn(e) { "Encountered malformed or truncated ISF header in ${file.name}; skipping header parsing." }
            null
        }

        val filenameId = file.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val jsonName = header?.DESCRIPTION // or name if available in header
        val displayName = jsonName ?: file.nameWithoutExtension.replace("_", " ").capitalize()
        val id = filenameId

        // Determine category
        val categories = header?.CATEGORIES
        val parentFolder = file.parentFile?.name ?: "General"
        val category = if (!categories.isNullOrEmpty()) {
            categories.first()
        } else {
            parentFolder.replace("_", " ").capitalize()
        }

        // Determine asset type
        val lowerPath = file.absolutePath.lowercase()
        val lowerCategories = categories?.joinToString(" ") { it.lowercase() } ?: ""
        val assetType = when {
            lowerPath.contains("transition") || lowerCategories.contains("transition") -> ISFAssetType.TRANSITION
            lowerPath.contains("filter") || lowerPath.contains("effect") || lowerCategories.contains("filter") || lowerCategories.contains("effect") -> ISFAssetType.FILTER
            else -> ISFAssetType.GENERATOR
        }

        // Look for paired vertex shader (.vs or .vert)
        val parentDir = file.parentFile
        val baseName = file.nameWithoutExtension
        var vertexPath: String? = null
        if (parentDir != null && parentDir.exists()) {
            val pairedVert = parentDir.listFiles { f ->
                f.isFile && f.nameWithoutExtension == baseName && f.extension.lowercase() in vertexExtensions
            }?.firstOrNull()
            vertexPath = pairedVert?.absolutePath
        }

        return ISFAsset(
            id = id,
            displayName = displayName,
            sourcePath = file.absolutePath,
            vertexPath = vertexPath,
            category = category,
            type = assetType,
            sourceType = sourceType,
            sourceDirectoryPath = directoryPath
        )
    }
}

private fun String.capitalize(): String = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
