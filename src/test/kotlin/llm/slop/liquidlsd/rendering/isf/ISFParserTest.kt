package llm.slop.liquidlsd.rendering.isf

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ISFParserTest {

    @Test
    fun testParseSimpleHeader() {
        val source = """
            /*{
                "DESCRIPTION": "Test shader",
                "INPUTS": [
                    {
                        "NAME": "level",
                        "TYPE": "float",
                        "DEFAULT": 0.5,
                        "MIN": 0.0,
                        "MAX": 1.0
                    }
                ]
            }*/
            void main() {
                gl_FragColor = vec4(level, level, level, 1.0);
            }
        """.trimIndent()

        val header = ISFParser.parseHeader(source)
        assertNotNull(header)
        assertEquals("Test shader", header.DESCRIPTION)
        assertEquals(1, header.INPUTS.size)
        assertEquals("level", header.INPUTS[0].NAME)
        assertEquals("float", header.INPUTS[0].TYPE)
        
        val stripped = ISFParser.stripHeader(source).trim()
        assert(!stripped.contains("/*{"))
        assert(stripped.startsWith("void main()"))
    }

    @Test
    fun testNoHeader() {
        val source = "void main() { gl_FragColor = vec4(1.0); }"
        val header = ISFParser.parseHeader(source)
        assertNull(header)
    }

    @Test
    fun testBuildGLSLFragmentShaderInjectsUniformsAndMacros() {
        val source = """
            /*{
                "DESCRIPTION": "Plasma ISF",
                "INPUTS": [
                    { "NAME": "speed", "TYPE": "float", "DEFAULT": 1.0 },
                    { "NAME": "tint", "TYPE": "color", "DEFAULT": [1.0, 0.5, 0.2, 1.0] },
                    { "NAME": "center", "TYPE": "point2D", "DEFAULT": [0.5, 0.5] }
                ]
            }*/
            void main() {
                vec2 p = isf_FragNormCoord - center;
                gl_FragColor = vec4(sin(TIME * speed) * tint.rgb, 1.0);
            }
        """.trimIndent()

        val header = ISFParser.parseHeader(source)
        assertNotNull(header)
        val glsl = ISFParser.buildGLSLFragmentShader(source, header)

        // Verifications
        assert(glsl.contains("#version 330 core"))
        assert(glsl.contains("in vec2 vTexCoord;"))
        assert(glsl.contains("out vec4 isf_FragColor;"))
        assert(glsl.contains("#define isf_FragNormCoord vTexCoord"))
        assert(glsl.contains("#define gl_FragColor isf_FragColor"))
        assert(glsl.contains("uniform float speed;"))
        assert(glsl.contains("uniform vec4 tint;"))
        assert(glsl.contains("uniform vec2 center;"))
        assert(glsl.contains("uniform float TIME;"))
        assert(glsl.contains("uniform vec2 RENDERSIZE;"))
        assert(glsl.contains("vec2 p = isf_FragNormCoord - center;"))
    }

    @Test
    fun testBuildGLSLFragmentShaderDeduplicatesStandardUniforms() {
        // Simulates shaders like colors/shader.frag that explicitly declare TIME, uAlpha, etc. in their GLSL body
        val source = """
            /*{
                "DESCRIPTION": "Colors with explicit uniforms",
                "INPUTS": [
                    { "NAME": "Speed", "TYPE": "float", "DEFAULT": 0.2 },
                    { "NAME": "uAlpha", "TYPE": "float", "DEFAULT": 1.0 }
                ]
            }*/
            #version 330 core
            in vec2 vTexCoord;
            out vec4 fragColor;

            uniform float Speed;
            uniform float TIME;
            uniform float uAlpha;
            uniform vec2 RENDERSIZE;

            void main() {
                fragColor = vec4(sin(TIME * Speed), 0.0, 0.0, uAlpha);
            }
        """.trimIndent()

        val header = ISFParser.parseHeader(source)
        assertNotNull(header)
        val glsl = ISFParser.buildGLSLFragmentShader(source, header)

        // Ensure TIME, uAlpha, and RENDERSIZE appear EXACTLY once in the preprocessed GLSL
        val timeOccurrences = Regex("""\buniform\s+float\s+TIME\s*;""").findAll(glsl).count()
        assertEquals(1, timeOccurrences, "uniform float TIME; should be declared exactly once")

        val uAlphaOccurrences = Regex("""\buniform\s+float\s+uAlpha\s*;""").findAll(glsl).count()
        assertEquals(1, uAlphaOccurrences, "uniform float uAlpha; should be declared exactly once")

        val renderSizeOccurrences = Regex("""\buniform\s+vec2\s+RENDERSIZE\s*;""").findAll(glsl).count()
        assertEquals(1, renderSizeOccurrences, "uniform vec2 RENDERSIZE; should be declared exactly once")

        val speedOccurrences = Regex("""\buniform\s+float\s+Speed\s*;""").findAll(glsl).count()
        assertEquals(1, speedOccurrences, "uniform float Speed; should be declared exactly once")

        // Ensure output mapping occurred
        assert(glsl.contains("#define fragColor isf_FragColor"))
        assert(glsl.contains("fragColor = vec4(sin(TIME * Speed), 0.0, 0.0, uAlpha);"))
    }

    @Test
    fun testDetectFormat() {
        val isf = "/*{ \"DESCRIPTION\": \"test\" }*/ void main() {}"
        assertEquals(ShaderFormat.ISF, ISFParser.detectFormat(isf))

        val isfWithComments = "// License header\n/*{ \"DESCRIPTION\": \"test\" }*/ void main() {}"
        assertEquals(ShaderFormat.ISF, ISFParser.detectFormat(isfWithComments))

        val shadertoy = "void mainImage(out vec4 fragColor, in vec2 fragCoord) { fragColor = vec4(1.0); }"
        assertEquals(ShaderFormat.SHADERTOY, ISFParser.detectFormat(shadertoy))

        val sandbox = "uniform vec2 u_resolution;\nvoid main() { gl_FragColor = vec4(1.0); }"
        assertEquals(ShaderFormat.GLSL_SANDBOX, ISFParser.detectFormat(sandbox))
    }

    @Test
    fun testShadertoyShimAndUniversalUniforms() {
        val source = """
            void mainImage(out vec4 fragColor, in vec2 fragCoord) {
                vec2 uv = fragCoord / iResolution.xy;
                fragColor = vec4(uv, 0.5 + 0.5 * sin(iTime), 1.0);
            }
        """.trimIndent()

        val glsl = ISFParser.buildGLSLFragmentShader(source)

        // Verifications
        assert(glsl.contains("#version 330 core"))
        assert(glsl.contains("uniform vec3 iResolution;"))
        assert(glsl.contains("uniform float iTime;"))
        assert(glsl.contains("uniform vec4 iMouse;"))
        assert(glsl.contains("uniform sampler2D iChannel0;"))
        assert(glsl.contains("uniform sampler2D audioFFT;"))
        assert(glsl.contains("#define texture2D texture"))
        assert(glsl.contains("#define textureCube texture"))

        // Shadertoy entry point shim appended
        assert(glsl.contains("void main() {"))
        assert(glsl.contains("mainImage(isf_FragColor, gl_FragCoord.xy);"))
    }

    @Test
    fun testGLSLSandboxPreprocessing() {
        val source = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            uniform vec2 resolution;
            uniform float time;
            void main() {
                vec2 p = gl_FragCoord.xy / resolution.xy;
                gl_FragColor = vec4(p, sin(time), 1.0);
            }
        """.trimIndent()

        val glsl = ISFParser.buildGLSLFragmentShader(source)

        assert(glsl.contains("#version 330 core"))
        assert(glsl.contains("uniform vec2 resolution;"))
        assert(glsl.contains("uniform float time;"))
        assert(glsl.contains("#define gl_FragColor isf_FragColor"))
        assert(!glsl.contains("precision mediump float;"))
    }

    @Test
    fun testExtendedISFMacros() {
        val source = """
            /*{
                "DESCRIPTION": "Macro test"
            }*/
            void main() {
                gl_FragColor = vec4(1.0);
            }
        """.trimIndent()

        val glsl = ISFParser.buildGLSLFragmentShader(source)

        assert(glsl.contains("#define IMG_THIS_PIXEL(sampler) texture(sampler, gl_FragCoord.xy / RENDERSIZE)"))
        assert(glsl.contains("#define IMG_THIS_NORM_PIXEL(sampler) texture(sampler, vTexCoord)"))
        assert(glsl.contains("#define IMG_SIZE(sampler) vec2(textureSize(sampler, 0))"))
    }
}

