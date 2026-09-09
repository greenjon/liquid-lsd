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
    val availableSources = mutableListOf<DynamicVisualSource>()
    
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

    private val DEFAULT_SOURCE_IDS = listOf(
        "attractor_feedback",
        "chladni",
        "colors",
        "dynamic_spiral",
        "gyroid",
        "hyper_mesh",
        "hyper_slice",
        "icosa-v3",
        "icosa_dodeca",
        "icosahedron",
        "mandala"
    )

    internal fun ensureDefaultSources(sourcesDir: File) {
        val mandalaFolder = File(sourcesDir, "mandala")
        val mandalaMeta = File(mandalaFolder, "meta.json")
        val mandalaFrag = File(mandalaFolder, "shader.frag")
        if (mandalaMeta.exists() && mandalaFrag.exists()) {
            return
        }

        logger.info { "Default sources missing or incomplete in ${sourcesDir.path}; extracting bundled defaults..." }
        val sourceFiles = listOf("meta.json", "shader.frag", "shader.vert")
        for (sourceId in DEFAULT_SOURCE_IDS) {
            val targetFolder = File(sourcesDir, sourceId)
            var extractedAny = false
            for (filename in sourceFiles) {
                val resourcePath = "default_sources/$sourceId/$filename"
                val stream = VisualSourceRegistry::class.java.classLoader.getResourceAsStream(resourcePath)
                    ?: continue
                if (!targetFolder.exists()) {
                    targetFolder.mkdirs()
                }
                val targetFile = File(targetFolder, filename)
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

    fun loadAll() {
        disposeAll()
        
        val sourcesDir = File("library/sources")
        if (!sourcesDir.exists()) {
            sourcesDir.mkdirs()
        }

        ensureDefaultSources(sourcesDir)

        // Load folders
        val folders = sourcesDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        for (folder in folders) {
            loadFromFolder(folder)
        }

        // Load standalone ISF files
        val files = sourcesDir.listFiles { it.isFile && (it.extension == "fs" || it.extension == "isf" || it.extension == "frag") } ?: emptyArray()
        for (file in files) {
            try {
                val source = loadFromISFFile(file)
                if (source != null) {
                    availableSources.add(source)
                    logger.info { "Loaded standalone ISF visual source: ${source.displayName} (${source.id})" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load standalone ISF source: ${file.name}" }
            }
        }
    }

    private fun loadFromISFFile(file: File, overrideId: String? = null): ISFVisualSource? {
        val rawSource = file.readText()
        val header = ISFParser.parseHeader(rawSource) ?: return null
        val glslSource = ISFParser.buildGLSLFragmentShader(rawSource, header)
        
        val shader = try {
            Shader(vertexShaderSource, glslSource)
        } catch (e: Exception) {
            logger.error(e) { "Failed to compile ISF shader for '${file.name}'. Using error fallback." }
            Shader(vertexShaderSource, errorFragmentShaderSource)
        }

        val parameters = ISFVisualSource.createParameters(header)
        val sourceId = overrideId ?: file.nameWithoutExtension
        
        return ISFVisualSource(
            id = sourceId,
            displayName = header.DESCRIPTION ?: sourceId,
            shader = shader,
            header = header,
            parameters = parameters,
            ownsShader = true
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
                    recipe = initialRecipe
                )
            } else if (meta.id == "dynamic_spiral") {
                DynamicSpiral(
                    id = meta.id,
                    displayName = meta.name,
                    shader = shader,
                    parameters = parameters,
                    hasFeedback = meta.feedback,
                    ownsShader = true
                )
            } else if (meta.id == "hyper_mesh") {
                HyperMesh(
                    id = meta.id,
                    displayName = meta.name,
                    shader = shader,
                    parameters = parameters,
                    hasFeedback = meta.feedback,
                    ownsShader = true,
                    is3D = isSource3D
                )
            } else if (meta.id == "icosahedron") {
                Icosahedron(
                    id = meta.id,
                    displayName = meta.name,
                    shader = shader,
                    parameters = parameters,
                    hasFeedback = meta.feedback,
                    ownsShader = true,
                    is3D = isSource3D
                )
            } else {
                DynamicVisualSource(
                    id = meta.id,
                    displayName = meta.name,
                    shader = shader,
                    parameters = parameters,
                    hasFeedback = meta.feedback,
                    ownsShader = true, // Master instance owns the shader
                    is3D = isSource3D
                )
            }
            availableSources.add(dynamicSource)
            logger.info { "Loaded dynamic visual source: ${meta.name} (${meta.id})" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to load visual source from '${folder.name}'" }
        }
    }
}
