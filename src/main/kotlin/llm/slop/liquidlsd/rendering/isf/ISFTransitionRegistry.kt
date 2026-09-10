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
                        registerTransitionFromSource(id, displayName, source)
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to load user transition: ${file.path}" }
                    }
                }
        }
    }

    private fun registerTransitionFromSource(id: String, displayName: String, source: String) {
        val header = ISFParser.parseHeader(source) ?: return

        try {
            val glsl = ISFParser.buildGLSLFragmentShader(source, header)
            val blitVert = ISFTransitionRegistry::class.java.classLoader.getResourceAsStream("shaders/blit.vert")
                ?.bufferedReader()?.use { it.readText() } ?: throw RuntimeException("blit.vert not found")

            val shader = Shader(blitVert, glsl)
            val filter = ISFFilter(
                id = id,
                displayName = displayName,
                header = header,
                shader = shader,
                ownsShader = true,
                categories = header.CATEGORIES.takeIf { !it.isNullOrEmpty() } ?: listOf("Transitions")
            )
            transitions[id] = filter
            logger.debug { "Registered ISF transition: $id ($displayName)" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to compile ISF transition $id" }
        }
    }

    fun createTransition(id: String): ISFFilter? {
        return transitions[id]?.clone()
    }
}

private fun String.capitalize(): String = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
