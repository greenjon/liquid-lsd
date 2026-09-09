package llm.slop.liquidlsd.rendering.isf

import io.mockk.mockk
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.rendering.Shader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ISFFilterTest {

    @Test
    fun `test ISF header parsing and parameter creation`() {
        val source = """
            /*{
                "DESCRIPTION": "Test Filter",
                "INPUTS": [
                    { "NAME": "inputImage", "TYPE": "image" },
                    { "NAME": "intensity", "TYPE": "float", "MIN": 0.0, "MAX": 1.0, "DEFAULT": 0.5 }
                ]
            }*/
            void main() { gl_FragColor = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord) * intensity; }
        """.trimIndent()

        val header = ISFParser.parseHeader(source)
        assertNotNull(header)
        assertEquals("Test Filter", header?.DESCRIPTION)

        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("test", "Test", header!!, shader)

        assertEquals(1, filter.parameters.size)
        val intensityParam = filter.parameters["intensity"]
        assertNotNull(intensityParam)
        assertEquals(0.5f, intensityParam?.baseValue)
        assertEquals(0.0f, intensityParam?.minClamp)
        assertEquals(1.0f, intensityParam?.maxClamp)
    }

    @Test
    fun `test ISFFilter parameter registration`() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "speed", TYPE = "float", DEFAULT = kotlinx.serialization.json.JsonPrimitive(0.5f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("fx1", "FX 1", header, shader)
        
        val paths = filter.getParameterPaths("A/FX1")
        assertTrue(paths.any { it.first == "A/FX1/DryWet" })
        assertTrue(paths.any { it.first == "A/FX1/speed" })
    }

    @Test
    fun `test FXSlotDto creation`() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "intensity", TYPE = "float", DEFAULT = kotlinx.serialization.json.JsonPrimitive(1.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("invert", "Invert", header, shader)
        
        val fxDto = FXSlotDto(
            filterId = filter.id,
            enabled = filter.enabled,
            dryWet = filter.dryWet.toDto(),
            parameters = filter.parameters.mapValues { it.value.toDto() }
        )
        
        assertEquals("invert", fxDto.filterId)
        assertEquals(1.0f, fxDto.dryWet.baseValue)
        assertTrue(fxDto.parameters.containsKey("intensity"))
    }

    @Test
    fun `test ISFFilter clone and reset`() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "speed", TYPE = "float", DEFAULT = kotlinx.serialization.json.JsonPrimitive(0.5f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("fx1", "FX 1", header, shader)
        filter.dryWet.baseValue = 0.8f
        filter.parameters["speed"]?.baseValue = 0.9f
        filter.enabled = false

        val clone = filter.clone()
        assertEquals(filter.id, clone.id)
        assertEquals(false, clone.enabled)
        assertEquals(0.8f, clone.dryWet.baseValue)
        assertEquals(0.9f, clone.parameters["speed"]?.baseValue)

        clone.reset()
        assertEquals(true, clone.enabled)
        assertEquals(1.0f, clone.dryWet.baseValue)
        assertEquals(0.5f, clone.parameters["speed"]?.baseValue)
    }
}
