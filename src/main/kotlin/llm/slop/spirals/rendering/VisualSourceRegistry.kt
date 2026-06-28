package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.MeterType
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
        val stream = Shader::class.java.classLoader.getResourceAsStream("shaders/mandelbulb.vert")
            ?: throw RuntimeException("Vertex shader resource not found: shaders/mandelbulb.vert")
        stream.bufferedReader().use { it.readText() }
    }
    
    private val errorFragmentShaderSource = """
        #version 330 core
        out vec4 FragColor;
        in vec2 uv;
        void main() {
            vec2 p = mod(uv * 10.0, 1.0);
            float c = (p.x < 0.5 ^^ p.y < 0.5) ? 1.0 : 0.0;
            FragColor = vec4(c, 0.0, 0.0, 1.0);
        }
    """.trimIndent()

    fun loadAll() {
        availableSources.clear()
        
        val sourcesDir = File("presets/sources")
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

            try {
                val metaText = metaFile.readText()
                val meta = json.decodeFromString<SourceMeta>(metaText)
                val fragText = shaderFile.readText()
                
                var shader: Shader
                try {
                    shader = Shader(vertexShaderSource, fragText)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to compile custom shader for '${meta.name}'. Using error fallback." }
                    shader = Shader(vertexShaderSource, errorFragmentShaderSource)
                }

                val parameters = LinkedHashMap<String, ModulatableParameter>()
                for (pMeta in meta.parameters) {
                    val meterType = try {
                        MeterType.valueOf(pMeta.type)
                    } catch (e: Exception) {
                        MeterType.MONOPOLAR
                    }
                    parameters[pMeta.name] = ModulatableParameter(
                        baseValue = pMeta.default,
                        minClamp = pMeta.min,
                        maxClamp = pMeta.max,
                        meterType = meterType
                    )
                }

                val dynamicSource = DynamicVisualSource(
                    id = meta.id,
                    displayName = meta.name,
                    shader = shader,
                    parameters = parameters
                )
                availableSources.add(dynamicSource)
                logger.info { "Loaded dynamic visual source: ${meta.name} (${meta.id})" }
                
            } catch (e: Exception) {
                logger.error(e) { "Failed to load visual source from '${folder.name}'" }
            }
        }
    }
}
