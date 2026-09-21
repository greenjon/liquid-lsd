package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.rendering.isf.ISFParser
import llm.slop.liquidlsd.rendering.isf.ISFVisualSource
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

object VisualSourceRegistry {
    val availableSources = mutableListOf<VisualSource>()
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val vertexShaderSource: String by lazy {
        val stream = Shader::class.java.classLoader.getResourceAsStream("shaders/blit.vert")
            ?: throw RuntimeException("Vertex shader resource not found: shaders/blit.vert")
        stream.bufferedReader().use { it.readText() }
    }
    
    private val errorFragmentShaderSource = """
        #version 330 core
        out vec4 FragColor;
        in vec2 vTexCoord;
        void main() {
            vec2 p = mod(vTexCoord * 10.0, 1.0);
            // ^^ (logical XOR) is GLSL 4.0+ only; use step+inequality for 3.30 compatibility.
            float c = (step(0.5, p.x) != step(0.5, p.y)) ? 1.0 : 0.0;
            FragColor = vec4(c, 0.0, 0.0, 1.0);
        }
    """.trimIndent()

    /**
     * Disposes of all loaded master sources, deleting their shaders and FBOs.
     */
    fun disposeAll() {
        for (source in availableSources) {
            try {
                source.dispose()
            } catch (e: Exception) {
                logger.error(e) { "Error disposing visual source: ${source.displayName}" }
            }
        }
        availableSources.clear()
    }

    val DEFAULT_SOURCE_IDS = listOf(
        "mandala",
        "dynamic_spiral",
        "icosa_h3",
        "domain_warp_fluid",
        "gyroid_hyperspace",
        "celestial_engine",
        "hyper_slice",
        "chladni_cymatics"
    )

    internal fun ensureDefaultSources(sourcesDir: File) {
        val anyMissing = DEFAULT_SOURCE_IDS.any { id ->
            val folder = File(sourcesDir, id)
            if (id == "mandala") {
                !File(folder, "meta.json").exists() || !File(folder, "shader.frag").exists()
            } else {
                !File(folder, "$id.fs").exists()
            }
        }
        if (!anyMissing) {
            return
        }

        logger.info { "Default sources missing or incomplete in ${sourcesDir.path}; extracting bundled defaults..." }
        for (sourceId in DEFAULT_SOURCE_IDS) {
            val targetFolder = File(sourcesDir, sourceId)
            val sourceFiles = if (sourceId == "mandala") {
                listOf("meta.json", "shader.frag", "shader.vert")
            } else {
                listOf("$sourceId.fs")
            }
            var extractedAny = false
            for (filename in sourceFiles) {
                val targetFile = File(targetFolder, filename)
                if (targetFile.exists()) continue

                val resourcePath = "default_sources/$sourceId/$filename"
                val stream = VisualSourceRegistry::class.java.classLoader.getResourceAsStream(resourcePath)
                    ?: continue
                if (!targetFolder.exists()) {
                    targetFolder.mkdirs()
                }
                stream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                extractedAny = true
            }
            if (extractedAny) {
                logger.info { "Extracted bundled visual source: $sourceId" }
            }
        }
    }

    fun loadBundledSources(defaultSourcesDir: File = File("library/sources")) {
        if (!defaultSourcesDir.exists()) {
            defaultSourcesDir.mkdirs()
        }
        ensureDefaultSources(defaultSourcesDir)

        // Load bundled source folders from library/sources
        val folders = defaultSourcesDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        for (folder in folders) {
            if (availableSources.none { it.id == folder.name }) {
                loadFromFolder(folder)
            }
        }

        // Register static native sources (ensure single instance)
        if (availableSources.none { it is ExternalVideoSource }) {
            availableSources.add(ExternalVideoSource())
        }
    }

    fun scanUserSources() {
        val resolvedDirs = llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.getResolvedDirectories()
        val enabledDirs = resolvedDirs.filter { 
            it.config.isEnabled && 
            it.status == llm.slop.liquidlsd.rendering.isf.DirectoryStatus.ACTIVE &&
            it.config.path != "library/filters" &&
            it.config.path != "library/transitions"
        }

        for (resolved in enabledDirs) {
            val dir = File(resolved.expandedPath)
            if (!dir.exists() || !dir.isDirectory) continue

            // 1. Check legacy source folders with meta.json (immediate subfolders)
            val folders = dir.listFiles { file -> file.isDirectory } ?: emptyArray()
            for (folder in folders) {
                if (File(folder, "meta.json").exists() && availableSources.none { it.id == folder.name }) {
                    loadFromFolder(folder)
                }
            }

            // 2. Recursively scan all ISF shader files in this directory
            val files = dir.walkTopDown()
                .filter { it.isFile && (it.extension == "fs" || it.extension == "isf" || it.extension == "frag") }
                .toList()

            for (file in files) {
                val sourceId = file.nameWithoutExtension
                if (availableSources.none { it.id == sourceId }) {
                    try {
                        val source = loadFromISFFile(file, directoryRoot = dir)
                        if (source != null && availableSources.none { it.id == source.id }) {
                            availableSources.add(source)
                            logger.info { "Loaded standalone ISF visual source: ${source.displayName} (${source.id})" }
                        }
                    } catch (e: Exception) {
                        logger.warn { "Failed to load standalone ISF source '${file.name}': ${e.message?.lines()?.firstOrNull() ?: e.toString()}" }
                        logger.debug(e) { "Full stack trace for '${file.name}'" }
                    }
                }
            }
        }
    }

    private val pendingGlTasks = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()

    /**
     * Drains and executes pending OpenGL compilation tasks strictly on Thread 0.
     */
    fun processPendingGlTasks() {
        var task = pendingGlTasks.poll()
        while (task != null) {
            try {
                task.run()
            } catch (e: Exception) {
                logger.error(e) { "Error executing pending OpenGL task in VisualSourceRegistry" }
            }
            task = pendingGlTasks.poll()
        }
    }

    /**
     * Loads default bundled sources immediately and kicks off an asynchronous scan of directory sources.
     * If [async] is false (e.g. in tests or when synchronous reload is required), scans synchronously.
     */
    fun loadAll(async: Boolean = false) {
        val defaultSourcesDir = File("library/sources")
        loadBundledSources(defaultSourcesDir)

        llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.loadSettings()
        if (async) {
            llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibraryAsync(onComplete = {
                pendingGlTasks.offer(Runnable {
                    scanUserSources()
                })
            })
        } else {
            llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibrary()
            scanUserSources()
        }
    }

    private fun loadFromISFFile(
        file: File,
        overrideId: String? = null,
        directoryRoot: File? = null
    ): ISFVisualSource? {
        val rawSource = file.readText()
        val format = ISFParser.detectFormat(rawSource)
        val header = ISFParser.parseHeader(rawSource)
            ?: ISFParser.createDefaultHeader(file.nameWithoutExtension.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, format)

        // Auto-detect role: Visual generator sources require exactly 0 image inputs
        val imageInputs = header.INPUTS.filter { it.TYPE.equals("image", ignoreCase = true) }
        if (imageInputs.isNotEmpty()) {
            return null
        }

        val glslSource = ISFParser.buildGLSLFragmentShader(rawSource, header)
        val pairedVert = file.parentFile?.listFiles { f ->
            f.isFile && f.nameWithoutExtension == file.nameWithoutExtension && f.extension.lowercase() in setOf("vs", "vert")
        }?.firstOrNull()
        val vertSource = if (pairedVert != null) {
            ISFParser.buildGLSLVertexShader(pairedVert.readText(), header)
        } else {
            vertexShaderSource
        }
        
        val shader = try {
            Shader(vertSource, glslSource)
        } catch (e: Exception) {
            logger.warn { "Failed to compile ISF shader for '${file.name}'. Using error fallback: ${e.message?.lines()?.firstOrNull() ?: e.toString()}" }
            logger.debug(e) { "Full compilation stack trace for '${file.name}'" }
            Shader(vertexShaderSource, errorFragmentShaderSource)
        }

        val parameters = ISFVisualSource.createParameters(header)
        val sourceId = overrideId ?: file.nameWithoutExtension

        val relFolder = if (directoryRoot != null) {
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
        
        // DESCRIPTION is free-form documentation text per the ISF spec (often a full sentence),
        // not a name -- the id-derived title below is always the right one to show.
        val parsedDisplayName = sourceId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        return ISFVisualSource(
            id = sourceId,
            displayName = parsedDisplayName.ifBlank { sourceId },
            shader = shader,
            header = header,
            parameters = parameters,
            ownsShader = true,
            categories = allCategories,
            folderPath = relFolder,
            baseDir = file.parentFile
        )
    }


    private fun loadFromFolder(folder: File) {
        val metaFile = File(folder, "meta.json")
        val shaderFile = File(folder, "shader.frag")

        // Check for ISF in folder if meta.json missing
        if (!metaFile.exists()) {
            val isfFile = folder.listFiles { it.isFile && (it.extension == "fs" || it.extension == "isf" || it.extension == "frag") }?.firstOrNull()
            if (isfFile != null) {
                val isfSource = loadFromISFFile(isfFile, overrideId = folder.name)
                if (isfSource != null) {
                    availableSources.add(isfSource)
                    logger.info { "Loaded ISF visual source from folder: ${isfSource.displayName} (${isfSource.id})" }
                    return
                }
            }
        }

        if (!metaFile.exists() || !shaderFile.exists()) {
            logger.warn { "Skipping source folder '${folder.name}': Missing meta.json or shader.frag" }
            return
        }

        val customVertFile = File(folder, "shader.vert")
        val vertSource = if (customVertFile.exists()) {
            customVertFile.readText()
        } else {
            vertexShaderSource
        }

        try {
            val metaText = metaFile.readText()
            val meta = json.decodeFromString<SourceMeta>(metaText)
            val fragText = shaderFile.readText()
            
            var shader: Shader
            try {
                shader = Shader(vertSource, fragText)
            } catch (e: Exception) {
                logger.error(e) { "Failed to compile custom shader for '${meta.name}'. Using error fallback." }
                shader = Shader(vertSource, errorFragmentShaderSource)
            }

            val parameters = LinkedHashMap<String, ModulatableParameter>()
            for (pMeta in meta.parameters) {
                val meterType = try {
                    MeterType.valueOf(pMeta.type)
                } catch (e: Exception) {
                    MeterType.MONOPOLAR
                }
                val param = ModulatableParameter(
                    baseValue = pMeta.default,
                    minClamp = pMeta.min,
                    maxClamp = pMeta.max,
                    meterType = meterType,
                    explicitIsAngle = pMeta.isAngle
                )
                if (pMeta.defaultMin != null && pMeta.defaultMax != null) {
                    param.baseMin = pMeta.defaultMin
                    param.baseMax = pMeta.defaultMax
                    param.randomizeBase = true
                }
                parameters[pMeta.name] = param
            }

            val isSource3D = meta.is3D || (parameters.containsKey("Rotate X") && parameters.containsKey("Rotate Y"))

            val dynamicSource = if (meta.id == "mandala") {
                val initialRecipe = MandalaLibrary.MandalaRatios.first()
                Mandala(
                    id = meta.id,
                    displayName = meta.name,
                    shader = shader,
                    parameters = parameters,
                    hasFeedback = meta.feedback,
                    ownsShader = true,
                    recipe = initialRecipe,
                    categories = meta.categories.takeIf { it.isNotEmpty() } ?: listOf("Generator", "Geometric")
                )
            } else {
                DynamicVisualSource(
                    id = meta.id,
                    displayName = meta.name,
                    shader = shader,
                    parameters = parameters,
                    hasFeedback = meta.feedback,
                    ownsShader = true, // Master instance owns the shader
                    is3D = isSource3D,
                    categories = meta.categories
                )
            }
            availableSources.add(dynamicSource)
            logger.info { "Loaded dynamic visual source: ${meta.name} (${meta.id})" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to load visual source from '${folder.name}'" }
        }
    }
}
