package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.rendering.Shader
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

object ISFTransitionRegistry {
    private val transitions = ConcurrentHashMap<String, ISFFilter>()
    private val bundledTransitions = listOf(
        "linear_crossfade",
        "wipe_horizontal",
        "wipe_vertical",
        "radial_wipe",
        "glitch_transition",
        "luma_wipe",
        "zoom_fade"
    )

    @Volatile
    private var cachedTransitions: List<ISFFilter> = emptyList()

    val availableTransitions: List<ISFFilter>
        get() = cachedTransitions

    fun hasTransition(id: String): Boolean = transitions.containsKey(id)

    private fun rebuildCache() {
        cachedTransitions = transitions.values.toList().sortedBy { it.displayName }
    }

    fun loadAll() {
        disposeAll()
        loadBundledTransitions()
        scanUserTransitions()
        rebuildCache()
    }

    fun disposeAll() {
        for (transition in transitions.values) {
            try {
                transition.dispose()
            } catch (e: Exception) {
                logger.error(e) { "Error disposing transition: ${transition.displayName}" }
            }
        }
        transitions.clear()
        rebuildCache()
    }

    private fun loadBundledTransitions() {
        for (name in bundledTransitions) {
            try {
                val path = "default_transitions/$name.fs"
                val stream = ISFTransitionRegistry::class.java.classLoader.getResourceAsStream(path)
                if (stream == null) {
                    logger.warn { "Bundled transition resource not found: $path" }
                    continue
                }
                val source = stream.bufferedReader().use { it.readText() }
                registerTransitionFromSource(name, name.replace("_", " ").capitalize(), source)
            } catch (e: Exception) {
                logger.error(e) { "Failed to load bundled transition: $name" }
            }
        }
    }

    private fun scanUserTransitions() {
        val defaultDir = File("library/transitions")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }


        val resolvedDirs = ISFDirectoryManager.getResolvedDirectories()
        val enabledDirs = resolvedDirs.filter { 
            it.config.isEnabled && 
            it.status == DirectoryStatus.ACTIVE &&
            it.config.path != "library/sources" &&
            it.config.path != "library/filters"
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
                        registerTransitionFromSource(id, displayName, source, file = file, directoryRoot = dir)
                    } catch (e: Exception) {
                        logger.warn { "Failed to load user transition '${file.path}': ${e.message?.lines()?.firstOrNull() ?: e.toString()}" }
                        logger.debug(e) { "Full stack trace for '${file.path}'" }
                    }
                }
        }
    }

    private fun registerTransitionFromSource(
        id: String,
        displayName: String,
        source: String,
        file: File? = null,
        directoryRoot: File? = null
    ) {
        val header = ISFParser.parseHeader(source) ?: return

        // Auto-detect role: Transitions require 2+ image inputs, or progress input with at least 1 image input
        val imageInputs = header.INPUTS.filter { it.TYPE.equals("image", ignoreCase = true) }
        val hasProgress = header.INPUTS.any { it.NAME.equals("progress", ignoreCase = true) }
        if (imageInputs.size < 2 && !(imageInputs.isNotEmpty() && hasProgress)) {
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
            .ifEmpty { listOf("Transitions") }

        val parsedDisplayName = (header.DESCRIPTION?.takeIf { it.isNotBlank() } ?: displayName).ifBlank { id }

        try {
            val glsl = ISFParser.buildGLSLFragmentShader(source, header)
            val blitVert = ISFTransitionRegistry::class.java.classLoader.getResourceAsStream("shaders/blit.vert")
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
            transitions[id] = filter
            logger.debug { "Registered ISF transition: $id ($parsedDisplayName)" }
        } catch (e: Exception) {
            logger.warn { "Failed to compile ISF transition '$id': ${e.message?.lines()?.firstOrNull() ?: e.toString()}" }
            logger.debug(e) { "Full compilation stack trace for ISF transition '$id'" }
        }
    }


    fun createTransition(id: String): ISFFilter? {
        return transitions[id]?.clone()
    }
}

private fun String.capitalize(): String = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
