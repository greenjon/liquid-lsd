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
}
