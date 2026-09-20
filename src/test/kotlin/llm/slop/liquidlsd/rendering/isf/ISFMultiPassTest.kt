package llm.slop.liquidlsd.rendering.isf

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.VisualSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ISFMultiPassTest {

    @Test
    fun `test multi-pass header parsing and GLSL sampler injection`() {
        val source = """
            /*{
                "DESCRIPTION": "Multi-pass Test",
                "INPUTS": [
                    { "NAME": "inputImage", "TYPE": "image" }
                ],
                "PASSES": [
                    { "TARGET": "pass0", "WIDTH": "${'$'}WIDTH/2.0", "HEIGHT": "${'$'}HEIGHT/2.0" },
                    { "TARGET": "historyBuffer", "PERSISTENT": true },
                    {}
                ]
            }*/
            void main() {
                if (PASSINDEX == 0) {
                    gl_FragColor = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);
                } else if (PASSINDEX == 1) {
                    gl_FragColor = IMG_NORM_PIXEL(pass0, isf_FragNormCoord);
                } else {
                    gl_FragColor = IMG_NORM_PIXEL(historyBuffer, isf_FragNormCoord);
                }
            }
        """.trimIndent()

        val header = ISFParser.parseHeader(source)
        assertNotNull(header)
        assertEquals(3, header?.PASSES?.size)
        assertEquals("pass0", header?.PASSES?.get(0)?.TARGET)
        assertEquals(true, header?.PASSES?.get(1)?.PERSISTENT)

        val glsl = ISFParser.buildGLSLFragmentShader(source, header!!)
        assertTrue(glsl.contains("uniform sampler2D pass0;"), "GLSL should contain sampler declaration for pass0")
        assertTrue(glsl.contains("uniform sampler2D historyBuffer;"), "GLSL should contain sampler declaration for historyBuffer")
    }

    @Test
    fun `test ISFVisualSource multi-pass passBindings and parameters`() {
        val source = """
            /*{
                "DESCRIPTION": "Reaction Diffusion Generator",
                "PASSES": [
                    { "TARGET": "bufferA", "PERSISTENT": true, "FLOAT": true },
                    { "TARGET": "bufferB", "FLOAT": true },
                    {}
                ]
            }*/
            void main() {
                gl_FragColor = vec4(1.0);
            }
        """.trimIndent()

        val header = ISFParser.parseHeader(source)!!
        assertEquals(3, header.PASSES.size)
        val shader = mockk<Shader>(relaxed = true)
        val isfSource = ISFVisualSource(
            id = "rd_gen",
            displayName = "Reaction Diffusion",
            shader = shader,
            header = header,
            parameters = LinkedHashMap()
        )

        assertNotNull(isfSource)
        assertEquals("rd_gen", isfSource.id)
        assertEquals(3, isfSource.header.PASSES.size)
        assertEquals("bufferA", isfSource.header.PASSES[0].TARGET)
        assertTrue(isfSource.header.PASSES[0].PERSISTENT)
        assertTrue(isfSource.header.PASSES[0].FLOAT)
    }
}
