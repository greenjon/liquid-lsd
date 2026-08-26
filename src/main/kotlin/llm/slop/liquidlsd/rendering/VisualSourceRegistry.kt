package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.MeterType
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

    fun loadAll() {
        disposeAll()
        
        val sourcesDir = File("library/sources")
        if (!sourcesDir.exists()) {
            sourcesDir.mkdirs()
        }

        val folders = sourcesDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        
        for (folder in folders) {
            val metaFile = File(folder, "meta.json")
            val shaderFile = File(folder, "shader.frag")

            if (!metaFile.exists() || !shaderFile.exists()) {
                logger.warn { "Skipping source folder '${folder.name}': Missing meta.json or shader.frag" }
                continue
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
                        ownsShader = true
                    )
                } else if (meta.id == "icosahedron") {
                    Icosahedron(
                        id = meta.id,
                        displayName = meta.name,
                        shader = shader,
                        parameters = parameters,
                        hasFeedback = meta.feedback,
                        ownsShader = true
                    )

                } else {
                    DynamicVisualSource(
                        id = meta.id,
                        displayName = meta.name,
                        shader = shader,
                        parameters = parameters,
                        hasFeedback = meta.feedback,
                        ownsShader = true // Master instance owns the shader
                    )
                }
                availableSources.add(dynamicSource)
                logger.info { "Loaded dynamic visual source: ${meta.name} (${meta.id})" }
                
            } catch (e: Exception) {
                logger.error(e) { "Failed to load visual source from '${folder.name}'" }
            }
        }
    }
}
