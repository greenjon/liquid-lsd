package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object ISFParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Regex to match /*{ ... }*/ at the beginning of the file
    private val headerRegex = Regex("""^/\*\s*(\{.*?\})\s*\*/""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Extracts the ISF JSON header from the provided shader source.
     */
    fun parseHeader(source: String): ISFHeader? {
        val match = headerRegex.find(source) ?: return null
        val jsonText = match.groupValues[1]
        
        return try {
            json.decodeFromString<ISFHeader>(jsonText)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse ISF header JSON" }
            null
        }
    }

    /**
     * Strips the ISF header from the source to leave valid GLSL.
     */
    fun stripHeader(source: String): String {
        return source.replaceFirst(headerRegex, "")
    }

    /**
     * Preprocesses an ISF fragment shader by stripping the header and injecting standard
     * uniforms, macros, and input uniform declarations so standard ISF shaders compile under GLSL 3.30.
     */
    fun buildGLSLFragmentShader(rawSource: String, header: ISFHeader): String {
        val stripped = stripHeader(rawSource)
        val sb = StringBuilder()

        // 1. Version header
        sb.append("#version 330 core\n\n")

        // 2. Vertex attribute inputs and Fragment output
        sb.append("// Inputs from blit.vert vertex shader\n")
        sb.append("in vec2 vTexCoord;\n")
        sb.append("out vec4 isf_FragColor;\n\n")

        // 3. ISF Standard Macro Definitions & Compatibility
        sb.append("// ISF Built-in Macros & Compatibility\n")
        sb.append("#define isf_FragNormCoord vTexCoord\n")
        sb.append("#define gl_FragColor isf_FragColor\n")
        sb.append("#define IMG_NORM_PIXEL(sampler, coord) texture(sampler, (coord))\n")
        sb.append("#define IMG_PIXEL(sampler, coord) texture(sampler, (coord) / RENDERSIZE)\n\n")

        // 4. ISF Standard Built-in Uniforms
        sb.append("// ISF Standard Uniforms\n")
        sb.append("uniform vec2 RENDERSIZE;\n")
        sb.append("uniform float TIME;\n")
        sb.append("uniform float TIMEDELTA;\n")
        sb.append("uniform int FRAMEINDEX;\n")
        sb.append("uniform vec4 DATE;\n")
        sb.append("uniform int PASSINDEX;\n")
        sb.append("uniform float uAlpha;\n\n")

        // 5. User Inputs as Uniforms (only if not already declared in shader code)
        sb.append("// ISF Input Uniforms\n")
        for (input in header.INPUTS) {
            val uniformDecl = when (input.TYPE.lowercase()) {
                "float" -> "uniform float ${input.NAME};"
                "bool" -> "uniform bool ${input.NAME};"
                "long" -> "uniform float ${input.NAME};"
                "color" -> "uniform vec4 ${input.NAME};"
                "point2d" -> "uniform vec2 ${input.NAME};"
                "image" -> "uniform sampler2D ${input.NAME};"
                else -> "uniform float ${input.NAME};"
            }
            // If the shader already explicitly wrote "uniform ... <NAME>", avoid duplicate declaration
            val declRegex = Regex("""\buniform\s+[A-Za-z0-9_]+\s+${Regex.escape(input.NAME)}\s*;""")
            if (!declRegex.containsMatchIn(stripped)) {
                sb.append(uniformDecl).append("\n")
            }
        }
        sb.append("\n")

        // 5.5 Pass Targets as Uniforms
        sb.append("// ISF Pass Targets\n")
        val passTargets = header.PASSES.mapNotNull { it.TARGET }
        for (target in passTargets) {
            val declRegex = Regex("""\buniform\s+sampler2D\s+${Regex.escape(target)}\s*;""")
            if (!declRegex.containsMatchIn(stripped)) {
                sb.append("uniform sampler2D $target;\n")
            }
        }
        sb.append("\n")

        // 6. Clean up stripped body (strip redundant #version directives and preexisting vTexCoord/out vec4)
        var body = stripped
        val versionDirectiveRegex = Regex("""^\s*#version\s+.*$""", RegexOption.MULTILINE)
        body = body.replace(versionDirectiveRegex, "")

        // If body has 'in vec2 vTexCoord;', remove it since we injected it
        body = body.replace(Regex("""^\s*in\s+vec2\s+vTexCoord\s*;""", RegexOption.MULTILINE), "")

        // If body defines an explicit out vec4 (e.g. out vec4 fragColor; or out vec4 FragColor;),
        // replace its name with isf_FragColor or alias it.
        val outFragMatch = Regex("""\bout\s+vec4\s+([A-Za-z0-9_]+)\s*;""").find(body)
        if (outFragMatch != null) {
            val userOutName = outFragMatch.groupValues[1]
            body = body.replace(outFragMatch.value, "#define $userOutName isf_FragColor")
        }

        sb.append(body)
        return sb.toString()
    }
}
