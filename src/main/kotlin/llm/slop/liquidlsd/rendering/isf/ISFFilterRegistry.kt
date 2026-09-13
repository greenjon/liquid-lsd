package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.rendering.Shader
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

object ISFFilterRegistry {
    private val filters = ConcurrentHashMap<String, ISFFilter>()
    private val bundledFilters = listOf("invert", "hue_shift", "posterize", "luma_key", "edge_detect", "bloom", "feedback_trails", "feedback", "3d_elevation", "glitch", "mirror")

    @Volatile
    private var cachedFilters: List<ISFFilter> = emptyList()

    val availableFilters: List<ISFFilter>
        get() = cachedFilters

    private fun rebuildCache() {
        cachedFilters = filters.values.toList().sortedBy { it.displayName }
    }

    fun loadAll() {
        disposeAll()
        loadBundledFilters()
        scanUserFilters()
        rebuildCache()
    }

    fun disposeAll() {
        for (filter in filters.values) {
            try {
                filter.dispose()
            } catch (e: Exception) {
                logger.error(e) { "Error disposing filter: ${filter.displayName}" }
            }
        }
        filters.clear()
        rebuildCache()
    }

    private fun loadBundledFilters() {
        for (name in bundledFilters) {
            try {
                val path = "default_filters/$name.fs"
                val stream = ISFFilterRegistry::class.java.classLoader.getResourceAsStream(path)
                if (stream == null) {
                    logger.warn { "Bundled filter resource not found: $path" }
                    continue
                }
                val source = stream.bufferedReader().use { it.readText() }
                registerFilterFromSource(name, name.replace("_", " ").capitalize(), source)
            } catch (e: Exception) {
                logger.error(e) { "Failed to load bundled filter: $name" }
            }
        }
    }

    private fun scanUserFilters() {
        val defaultDir = File("library/filters")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }

        val resolvedDirs = ISFDirectoryManager.getResolvedDirectories()
        val enabledDirs = resolvedDirs.filter { 
            it.config.isEnabled && 
            it.status == DirectoryStatus.ACTIVE &&
            it.config.path != "library/sources" &&
            it.config.path != "library/transitions"
        }

        for (resolved in enabledDirs) {
            val dir = File(resolved.expandedPath)
            if (!dir.exists() || !dir.isDirectory) continue

            dir.walkTopDown()
                .filter { it.isFile && (it.extension == "fs" || it.extension == "isf" || it.extension == "frag") }
                .forEach { file ->
                    try {
                        val source = file.readText()
                        val id = file.nameWithoutExtension
                        val displayName = id.replace("_", " ").capitalize().ifBlank { id }
                        registerFilterFromSource(id, displayName, source, file = file, directoryRoot = dir)
                    } catch (e: Exception) {
                        logger.warn { "Failed to load user filter '${file.path}': ${e.message?.lines()?.firstOrNull() ?: e.toString()}" }
                        logger.debug(e) { "Full stack trace for '${file.path}'" }
                    }
                }
        }
    }

    private fun registerFilterFromSource(
        id: String,
        displayName: String,
        source: String,
        file: File? = null,
        directoryRoot: File? = null
    ) {
        val header = ISFParser.parseHeader(source) ?: return

        // Auto-detect role: Filters require exactly 1 image input
        val imageInputs = header.INPUTS.filter { it.TYPE.equals("image", ignoreCase = true) }
        if (imageInputs.size != 1) {
            return
        }

        // Mutual exclusion: skip if this shader is explicitly marked with a progress input for transitions
        val hasProgress = header.INPUTS.any { it.NAME.equals("progress", ignoreCase = true) }
        if (hasProgress) {
            return
        }

        val relFolder = if (directoryRoot != null && file != null) {
            try {
                file.relativeToOrNull(directoryRoot)?.parent?.replace('\\', '/') ?: ""
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
        val folderSegments = if (relFolder.isNotBlank()) relFolder.split("/").filter { it.isNotBlank() } else emptyList()
        val headerCategories = header.CATEGORIES ?: emptyList()
        val allCategories = (headerCategories + folderSegments + (if (relFolder.isNotBlank()) listOf(relFolder) else emptyList()))
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf("Color Adjustment") }

        val parsedDisplayName = (header.DESCRIPTION?.takeIf { it.isNotBlank() } ?: displayName).ifBlank { id }

        try {
            val glsl = ISFParser.buildGLSLFragmentShader(source, header)
            val blitVert = ISFFilterRegistry::class.java.classLoader.getResourceAsStream("shaders/blit.vert")
                ?.bufferedReader()?.use { it.readText() } ?: throw RuntimeException("blit.vert not found")
            val pairedVert = file?.parentFile?.listFiles { f ->
                f.isFile && f.nameWithoutExtension == file.nameWithoutExtension && f.extension.lowercase() in setOf("vs", "vert")
            }?.firstOrNull()
            val vertSource = if (pairedVert != null) {
                ISFParser.buildGLSLVertexShader(pairedVert.readText(), header)
            } else {
                blitVert
            }
            
            val shader = Shader(vertSource, glsl)
            val filter = ISFFilter(
                id = id,
                displayName = parsedDisplayName,
                header = header,
                shader = shader,
                ownsShader = true,
                categories = allCategories,
                folderPath = relFolder,
                baseDir = file?.parentFile
            )
            filters[id] = filter
            logger.debug { "Registered ISF filter: $id ($parsedDisplayName)" }
        } catch (e: Exception) {
            logger.warn { "Failed to compile ISF filter '$id': ${e.message?.lines()?.firstOrNull() ?: e.toString()}" }
            logger.debug(e) { "Full compilation stack trace for ISF filter '$id'" }
        }
    }


    fun createFilter(id: String): ISFFilter? {
        return filters[id]?.clone()
    }
}

private fun String.capitalize(): String = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
