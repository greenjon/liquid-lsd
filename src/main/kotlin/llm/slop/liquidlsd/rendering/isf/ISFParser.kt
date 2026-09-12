package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object ISFParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Regex to match /*{ ... }*/ anywhere near the start of the file (handling BOM, whitespace, license comments)
    private val headerRegex = Regex("""(?:\A|\uFEFF|\s|//[^\r\n]*[\r\n]+)*?/\*\s*(\{.*?\})\s*\*/""", RegexOption.DOT_MATCHES_ALL)

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
     * Detects the underlying format of the shader code.
     */
    fun detectFormat(source: String): ShaderFormat {
        if (headerRegex.find(source) != null) {
            return ShaderFormat.ISF
        }
        if (Regex("""\bvoid\s+mainImage\s*\(""").containsMatchIn(source)) {
            return ShaderFormat.SHADERTOY
        }
        return ShaderFormat.GLSL_SANDBOX
    }

    /**
     * Synthesizes a fallback ISFHeader for shaders that do not declare one explicitly (Shadertoy, GLSLSandbox).
     */
    fun createDefaultHeader(displayName: String, format: ShaderFormat): ISFHeader {
        val category = when (format) {
            ShaderFormat.SHADERTOY -> "Shadertoy"
            ShaderFormat.GLSL_SANDBOX -> "GLSL Sandbox"
            ShaderFormat.ISF -> "Generators"
        }
        return ISFHeader(
            DESCRIPTION = displayName,
            CATEGORIES = listOf(category, "Generators"),
            INPUTS = emptyList(),
            PASSES = emptyList()
        )
    }

    /**
     * Strips the ISF header from the source to leave valid GLSL.
     */
    fun stripHeader(source: String): String {
        return source.replaceFirst(headerRegex, "")
    }

    /**
     * Preprocesses any fragment shader (ISF, Shadertoy, or GLSLSandbox) by stripping any JSON header,
     * injecting universal standard uniforms, compatibility macros, legacy GLSL polyfills, and normalising
     * the entry point so foreign shaders compile reliably under GLSL 3.30 Core.
     */
    fun buildGLSLFragmentShader(
        rawSource: String,
        header: ISFHeader = parseHeader(rawSource) ?: createDefaultHeader("Shader", detectFormat(rawSource))
    ): String {
        val format = detectFormat(rawSource)
        val stripped = stripHeader(rawSource)
        val sb = StringBuilder()

        // 1. Version header
        sb.append("#version 330 core\n\n")

        // 2. Vertex attribute inputs and Fragment output
        sb.append("// Inputs from blit.vert vertex shader\n")
        sb.append("in vec2 vTexCoord;\n")
        sb.append("out vec4 isf_FragColor;\n\n")

        // 3. Legacy GLSL 1.20 / 3.30 Core Compatibility Polyfills
        sb.append("// Legacy GLSL Polyfills & Compatibility\n")
        sb.append("#define texture2D texture\n")
        sb.append("#define textureCube texture\n")
        sb.append("#define texture2DRect(s, c) texture(s, (c) / RENDERSIZE)\n")
        sb.append("#define texture2DProj textureProj\n")
        sb.append("#define gl_FragColor isf_FragColor\n\n")

        // 4. ISF Standard Macro Definitions & Compatibility
        sb.append("// ISF Built-in Macros & Compatibility\n")
        sb.append("#define isf_FragNormCoord vTexCoord\n")
        sb.append("#define IMG_NORM_PIXEL(sampler, coord) texture(sampler, (coord))\n")
        sb.append("#define IMG_PIXEL(sampler, coord) texture(sampler, (coord) / RENDERSIZE)\n")
        sb.append("#define IMG_THIS_PIXEL(sampler) texture(sampler, gl_FragCoord.xy / RENDERSIZE)\n")
        sb.append("#define IMG_THIS_NORM_PIXEL(sampler) texture(sampler, vTexCoord)\n")
        sb.append("#define IMG_SIZE(sampler) vec2(textureSize(sampler, 0))\n\n")

        // 5. Universal Standard Built-in Uniforms (ISF, Shadertoy, Book of Shaders / GLSLSandbox, Audio)
        sb.append("// Universal Standard Uniforms\n")
        val standardUniforms = listOf(
            // Resolution
            "vec2" to "RENDERSIZE",
            "vec3" to "iResolution",
            "vec2" to "u_resolution",
            "vec2" to "resolution",
            // Time & Clocks
            "float" to "TIME",
            "float" to "iTime",
            "float" to "u_time",
            "float" to "time",
            "float" to "TIMEDELTA",
            "float" to "iTimeDelta",
            "float" to "u_delta",
            // Frame Indices & Dates
            "int" to "FRAMEINDEX",
            "int" to "iFrame",
            "int" to "u_frame",
            "float" to "iFrameRate",
            "vec4" to "DATE",
            "vec4" to "iDate",
            "int" to "PASSINDEX",
            "float" to "uAlpha",
            // Mouse & Interaction
            "vec4" to "iMouse",
            "vec2" to "u_mouse",
            "vec2" to "mouse",
            // Audio Uniforms (VJ Standard)
            "float" to "audioVolume",
            "float" to "audioBass",
            "float" to "audioMid",
            "float" to "audioTreble",
            "sampler2D" to "audioFFT",
            "sampler2D" to "iChannel0",
            "sampler2D" to "iChannel1",
            "sampler2D" to "iChannel2",
            "sampler2D" to "iChannel3"
        )
        for ((type, name) in standardUniforms) {
            sb.append("uniform $type $name;\n")
        }
        sb.append("uniform vec3 iChannelResolution[4];\n")
        sb.append("uniform float iChannelTime[4];\n\n")

        // 6. User Inputs as Uniforms (only if not already declared in standard uniforms)
        sb.append("// ISF Input Uniforms\n")
        val standardNames = standardUniforms.map { it.second }.toSet()
        for (input in header.INPUTS) {
            if (input.NAME in standardNames) continue
            val uniformDecl = when (input.TYPE.lowercase()) {
                "float" -> "uniform float ${input.NAME};"
                "bool" -> "uniform bool ${input.NAME};"
                "long" -> "uniform float ${input.NAME};"
                "color" -> "uniform vec4 ${input.NAME};"
                "point2d" -> "uniform vec2 ${input.NAME};"
                "image" -> "uniform sampler2D ${input.NAME};"
                else -> "uniform float ${input.NAME};"
            }
            val declRegex = Regex("""\buniform\s+(?:[A-Za-z0-9_]+\s+)*${Regex.escape(input.NAME)}\s*;""")
            if (!declRegex.containsMatchIn(stripped)) {
                sb.append(uniformDecl).append("\n")
            }
        }
        sb.append("\n")

        // 7. Pass Targets as Uniforms
        sb.append("// ISF Pass Targets\n")
        val passTargets = header.PASSES.mapNotNull { it.TARGET }
        for (target in passTargets) {
            val declRegex = Regex("""\buniform\s+(?:[A-Za-z0-9_]+\s+)*${Regex.escape(target)}\s*;""")
            if (!declRegex.containsMatchIn(stripped)) {
                sb.append("uniform sampler2D $target;\n")
            }
        }
        sb.append("\n")

        // 8. Clean up stripped body (strip redundant #version directives, precision qualifiers, preexisting vTexCoord/out vec4, and duplicate standard uniforms)
        var body = stripped
        val versionDirectiveRegex = Regex("""^\s*#version\s+.*$""", RegexOption.MULTILINE)
        body = body.replace(versionDirectiveRegex, "")

        // Strip precision qualifiers (common in WebGL / Shadertoy)
        body = body.replace(Regex("""^\s*precision\s+(highp|mediump|lowp)\s+(float|int)\s*;""", RegexOption.MULTILINE), "")

        // If body has 'in vec2 vTexCoord;', remove it since we injected it
        body = body.replace(Regex("""^\s*in\s+vec2\s+vTexCoord\s*;""", RegexOption.MULTILINE), "")

        // Strip duplicate declarations of standard uniforms from body if already declared there
        for ((_, name) in standardUniforms) {
            val dupRegex = Regex("""\buniform\s+(?:[A-Za-z0-9_]+\s+)*${Regex.escape(name)}\s*(?:\[\s*\d*\s*\]\s*)?;""")
            body = body.replace(dupRegex, "")
        }
        body = body.replace(Regex("""\buniform\s+vec3\s+iChannelResolution\s*\[\s*\d*\s*\]\s*;"""), "")
        body = body.replace(Regex("""\buniform\s+float\s+iChannelTime\s*\[\s*\d*\s*\]\s*;"""), "")

        // If body defines an explicit out vec4 (e.g. out vec4 fragColor; or out vec4 FragColor;),
        // replace its name with isf_FragColor or alias it.
        val outFragMatch = Regex("""\bout\s+vec4\s+([A-Za-z0-9_]+)\s*;""").find(body)
        if (outFragMatch != null) {
            val userOutName = outFragMatch.groupValues[1]
            body = body.replace(outFragMatch.value, "#define $userOutName isf_FragColor")
        }

        sb.append(body)

        // 9. Entry point normalization for Shadertoy
        if (format == ShaderFormat.SHADERTOY && !Regex("""\bvoid\s+main\s*\(""").containsMatchIn(body)) {
            sb.append("\n\n// Shadertoy Entry Point Shim\n")
            sb.append("void main() {\n")
            sb.append("    mainImage(isf_FragColor, gl_FragCoord.xy);\n")
            sb.append("}\n")
        }

        return sb.toString()
    }
}
