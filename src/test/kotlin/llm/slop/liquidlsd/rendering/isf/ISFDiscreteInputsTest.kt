package llm.slop.liquidlsd.rendering.isf

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Shader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ISFDiscreteInputsTest {

    private val source = """
        /*{
            "DESCRIPTION": "Discrete",
            "INPUTS": [
                { "NAME": "inputImage", "TYPE": "image" },
                { "NAME": "amount", "TYPE": "float", "MIN": 0.0, "MAX": 1.0, "DEFAULT": 0.5 },
                { "NAME": "mode", "TYPE": "long", "VALUES": [0, 1, 2, 3], "LABELS": ["A", "B", "C", "D"], "DEFAULT": 1 },
                { "NAME": "flag", "TYPE": "bool", "DEFAULT": true },
                { "NAME": "count", "TYPE": "long", "MIN": 2, "MAX": 5, "DEFAULT": 3 },
                { "NAME": "segments", "TYPE": "float", "MIN": 2.0, "MAX": 24.0, "STEP": 1, "DEFAULT": 6.0 },
                { "NAME": "lines", "TYPE": "float", "MIN": 50.0, "MAX": 800.0, "STEP": 10, "DEFAULT": 200.0 },
                { "NAME": "badStep", "TYPE": "float", "MIN": 0.0, "MAX": 1.0, "STEP": 0.3, "DEFAULT": 0.0 },
                { "NAME": "badLabels", "TYPE": "long", "VALUES": [0, 1], "LABELS": ["only"], "DEFAULT": 0 }
            ]
        }*/
        void main() { gl_FragColor = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord); }
    """.trimIndent()

    @Test
    fun `filter long and bool inputs get steps and labels`() {
        val header = ISFParser.parseHeader(source)!!
        val p = ISFFilter("t", "T", header, mockk<Shader>(relaxed = true)).parameters
        assertNull(p["amount"]!!.steps)
        assertEquals(4, p["mode"]!!.steps)
        assertEquals(listOf("A", "B", "C", "D"), p["mode"]!!.labels)
        assertEquals(2, p["flag"]!!.steps)
        assertEquals(4, p["count"]!!.steps)
        assertEquals(2f, p["count"]!!.minClamp)
        assertEquals(5f, p["count"]!!.maxClamp)
        assertNull(p["badLabels"]!!.labels)
    }

    @Test
    fun `visual source long input honors VALUES and integer default`() {
        val header = ISFParser.parseHeader(source)!!
        val p = ISFVisualSource.createParameters(header)
        val mode = p["mode"]!!
        assertEquals(0f, mode.minClamp)
        assertEquals(3f, mode.maxClamp)
        assertEquals(1f, mode.baseValue)
        assertEquals(4, mode.steps)
        assertEquals(listOf("A", "B", "C", "D"), mode.labels)
        assertEquals(2, p["flag"]!!.steps)
        assertNull(p["amount"]!!.steps)
    }

    @Test
    fun `evaluated long value is always a whole step`() {
        val header = ISFParser.parseHeader(source)!!
        val mode = ISFVisualSource.createParameters(header)["mode"]!!
        mode.baseValue = 0.99f
        assertEquals(1f, mode.evaluate())
    }

    @Test
    fun `STEP on a float input yields steps in both constructors and bad STEP is ignored`() {
        val header = ISFParser.parseHeader(source)!!
        for (p in listOf(ISFFilter("t", "T", header, mockk<Shader>(relaxed = true)).parameters, ISFVisualSource.createParameters(header))) {
            assertEquals(23, p["segments"]!!.steps)
            assertEquals(76, p["lines"]!!.steps)
            assertNull(p["badStep"]!!.steps)
            assertEquals(7f, p["segments"]!!.also { it.baseValue = 6.6f }.evaluate())
        }
    }
}
