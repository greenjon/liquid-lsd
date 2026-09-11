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
}
