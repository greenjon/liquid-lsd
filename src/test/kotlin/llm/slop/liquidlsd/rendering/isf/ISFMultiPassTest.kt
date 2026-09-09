package llm.slop.liquidlsd.rendering.isf

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
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
    fun `test Deck parameter path registration with Dual FX slots`() {
        val mockSource = mockk<VisualSource>(relaxed = true)
        val shader1 = mockk<Shader>(relaxed = true)
        val shader2 = mockk<Shader>(relaxed = true)

        val header1 = ISFParser.parseHeader("""
            /*{
                "DESCRIPTION": "Slot 1 Filter",
                "INPUTS": [
                    { "NAME": "inputImage", "TYPE": "image" },
                    { "NAME": "intensity", "TYPE": "float", "DEFAULT": 0.5 }
                ]
            }*/
        """.trimIndent())!!

        val header2 = ISFParser.parseHeader("""
            /*{
                "DESCRIPTION": "Slot 2 Filter",
                "INPUTS": [
                    { "NAME": "inputImage", "TYPE": "image" },
                    { "NAME": "blurRadius", "TYPE": "float", "DEFAULT": 1.0 }
                ]
            }*/
        """.trimIndent())!!

        val fx1 = ISFFilter("slot1_filter", "Slot 1 Filter", header1, shader1)
        val fx2 = ISFFilter("slot2_filter", "Slot 2 Filter", header2, shader2)

        val deck = mockk<Deck>(relaxed = true)
        every { deck.source } returns mockSource
        every { deck.fxSlot1 } returns fx1
        every { deck.fxSlot2 } returns fx2
        every { deck.getParameterPaths(any()) } answers { callOriginal() }

        val paths = deck.getParameterPaths("Deck A").map { it.first }

        assertTrue(paths.contains("Deck A/FX1/DryWet"))
        assertTrue(paths.contains("Deck A/FX1/intensity"))
        assertTrue(paths.contains("Deck A/FX2/DryWet"))
        assertTrue(paths.contains("Deck A/FX2/blurRadius"))
    }

    @Test
    fun `test DeckPresetDto Dual FX serialization round trip`() {
        val mockSource = mockk<VisualSource>(relaxed = true)
        val shader1 = mockk<Shader>(relaxed = true)
        val shader2 = mockk<Shader>(relaxed = true)

        val header1 = ISFParser.parseHeader("""
            /*{
                "DESCRIPTION": "Slot 1 Filter",
                "INPUTS": [
                    { "NAME": "inputImage", "TYPE": "image" },
                    { "NAME": "intensity", "TYPE": "float", "DEFAULT": 0.5 }
                ]
            }*/
        """.trimIndent())!!

        val header2 = ISFParser.parseHeader("""
            /*{
                "DESCRIPTION": "Slot 2 Filter",
                "INPUTS": [
                    { "NAME": "inputImage", "TYPE": "image" },
                    { "NAME": "blurRadius", "TYPE": "float", "DEFAULT": 1.0 }
                ]
            }*/
        """.trimIndent())!!

        val fx1 = ISFFilter("slot1_filter", "Slot 1 Filter", header1, shader1)
        val fx2 = ISFFilter("slot2_filter", "Slot 2 Filter", header2, shader2)

        fx1.dryWet.baseValue = 0.7f
        fx1.parameters["intensity"]?.baseValue = 0.9f
        fx2.dryWet.baseValue = 0.4f
        fx2.parameters["blurRadius"]?.baseValue = 2.5f

        val deck = mockk<Deck>(relaxed = true)
        every { deck.source } returns mockSource
        every { deck.fxSlot1 } returns fx1
        every { deck.fxSlot2 } returns fx2
        every { deck.fbDecay } returns ModulatableParameter(0.5f)
        every { deck.fbGain } returns ModulatableParameter(1.0f)
        every { deck.fbZoom } returns ModulatableParameter(0.0f)
        every { deck.fbRotate } returns ModulatableParameter(0.0f)
        every { deck.fbHueShift } returns ModulatableParameter(0.0f)
        every { deck.fbBlur } returns ModulatableParameter(0.0f)
        every { deck.fbChroma } returns ModulatableParameter(0.0f)
        every { deck.fbMode } returns ModulatableParameter(0.0f)
        every { deck.fbKaleido } returns ModulatableParameter(1.0f)

        val dto = deck.toDto("Test Preset")

        assertNotNull(dto.fxSlot1)
        assertEquals("slot1_filter", dto.fxSlot1?.filterId)
        assertEquals(0.7f, dto.fxSlot1?.dryWet?.baseValue)
        assertEquals(0.9f, dto.fxSlot1?.parameters?.get("intensity")?.baseValue)

        assertNotNull(dto.fxSlot2)
        assertEquals("slot2_filter", dto.fxSlot2?.filterId)
        assertEquals(0.4f, dto.fxSlot2?.dryWet?.baseValue)
        assertEquals(2.5f, dto.fxSlot2?.parameters?.get("blurRadius")?.baseValue)
    }
}
