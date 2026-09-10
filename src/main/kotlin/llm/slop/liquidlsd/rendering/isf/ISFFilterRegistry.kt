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
        val enabledDirs = resolvedDirs.filter { it.config.isEnabled && it.status == DirectoryStatus.ACTIVE }

        for (resolved in enabledDirs) {
            val dir = File(resolved.expandedPath)
            if (!dir.exists() || !dir.isDirectory) continue

            dir.walkTopDown()
                .filter { it.isFile && (it.extension == "fs" || it.extension == "isf" || it.extension == "frag") }
                .forEach { file ->
                    try {
                        val source = file.readText()
                        val id = file.nameWithoutExtension
                        val displayName = id.replace("_", " ").capitalize()
                        registerFilterFromSource(id, displayName, source)
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to load user filter: ${file.path}" }
                    }
                }
        }
    }

    private fun registerFilterFromSource(id: String, displayName: String, source: String) {
        val header = ISFParser.parseHeader(source) ?: return

        // Mutual exclusion: skip if this shader is categorised as a transition or has a progress input
        val isTransitionCategory = header.CATEGORIES?.any { it.equals("Transitions", ignoreCase = true) || it.equals("Transition", ignoreCase = true) } == true
        val hasProgress = header.INPUTS.any { it.NAME.equals("progress", ignoreCase = true) }
        val inTransitionRegistry = ISFTransitionRegistry.hasTransition(id)
        if (isTransitionCategory || hasProgress || inTransitionRegistry) {
            logger.debug { "Skipping ISF transition '$id' from filter registry" }
            return
        }

        try {
            val glsl = ISFParser.buildGLSLFragmentShader(source, header)
            val blitVert = ISFFilterRegistry::class.java.classLoader.getResourceAsStream("shaders/blit.vert")
                ?.bufferedReader()?.use { it.readText() } ?: throw RuntimeException("blit.vert not found")
            
            val shader = Shader(blitVert, glsl)
            val filter = ISFFilter(id, displayName, header, shader)
            filters[id] = filter
            logger.debug { "Registered ISF filter: $id ($displayName)" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to compile ISF filter $id" }
        }
    }

    fun createFilter(id: String): ISFFilter? {
        return filters[id]?.clone()
    }
}

private fun String.capitalize(): String = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
