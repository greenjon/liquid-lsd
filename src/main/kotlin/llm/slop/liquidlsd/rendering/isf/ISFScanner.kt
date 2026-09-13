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

        val format = ISFParser.detectFormat(rawSource)
        val header = try {
            ISFParser.parseHeader(rawSource)
        } catch (e: Exception) {
            logger.warn(e) { "Encountered malformed or truncated ISF header in ${file.name}; skipping header parsing." }
            null
        } ?: ISFParser.createDefaultHeader(file.nameWithoutExtension.replace("_", " ").capitalize(), format)

        val filenameId = file.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val jsonName = header.DESCRIPTION
        val displayName = jsonName?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension.replace("_", " ").capitalize()
        val id = filenameId

        // Determine relative folder hierarchy from scanned root
        val directoryFile = File(directoryPath)
        val relFolder = try {
            file.relativeToOrNull(directoryFile)?.parent?.replace('\\', '/') ?: ""
        } catch (_: Exception) {
            ""
        }
        val folderSegments = if (relFolder.isNotBlank()) relFolder.split("/").filter { it.isNotBlank() } else emptyList()

        // Determine category and categories list
        val headerCategories = header.CATEGORIES ?: emptyList()
        val allCategories = (headerCategories + folderSegments + (if (relFolder.isNotBlank()) listOf(relFolder) else emptyList()))
            .filter { it.isNotBlank() }
            .distinct()

        val category = when {
            headerCategories.isNotEmpty() -> headerCategories.first()
            folderSegments.isNotEmpty() -> folderSegments.last().replace("_", " ").capitalize()
            else -> file.parentFile?.name?.replace("_", " ")?.capitalize() ?: "General"
        }

        // Determine asset type via JSON INPUTS image count:
        // 0 image inputs = Generator (visual source)
        // 1 image input  = Filter (FX)
        // 2+ image inputs = Transition
        val imageInputs = header.INPUTS.filter { it.TYPE.equals("image", ignoreCase = true) }
        val assetType = when (imageInputs.size) {
            0 -> ISFAssetType.GENERATOR
            1 -> ISFAssetType.FILTER
            else -> ISFAssetType.TRANSITION
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
            sourceDirectoryPath = directoryPath,
            folderPath = relFolder,
            categories = allCategories
        )
    }
}

private fun String.capitalize(): String = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
