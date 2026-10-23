package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.parameters.ModulatableParameter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhaseIntegratorsTest {

    @Test
    fun `a rate change late in a set moves the phase only by the new slope`() {
        val speed = ModulatableParameter(0.5f)
        val phases = PhaseIntegrators(mapOf("SpinPhase" to listOf("Speed")), mapOf("Speed" to speed))
        var t = 3600.0
        phases.advance(t)
        assertEquals(1800.0, phases.value("SpinPhase"), 1e-9)   // where TIME * Speed was
        repeat(60) { t += 1.0 / 60; phases.advance(t) }
        assertEquals(1800.5, phases.value("SpinPhase"), 1e-9)

        // TIME * Speed would jump by 3601 x 0.05 = 180 here; the integrated phase moves by one frame's worth.
        speed.value = 0.55f
        val before = phases.value("SpinPhase")
        t += 1.0 / 60; phases.advance(t)
        assertEquals(0.55 / 60, phases.value("SpinPhase") - before, 1e-6)
    }

    @Test
    fun `the rate is the product of the listed inputs`() {
        val speed = ModulatableParameter(0.5f)
        val shear = ModulatableParameter(-0.4f)
        val phases = PhaseIntegrators(mapOf("ShearPhase" to listOf("Speed", "Shear")), mapOf("Speed" to speed, "Shear" to shear))
        phases.advance(10.0)
        phases.advance(10.2)
        assertEquals(0.5 * -0.4 * 10.2, phases.value("ShearPhase"), 1e-6)
    }

    @Test
    fun `a long gap counts as one capped step`() {
        val phases = PhaseIntegrators(mapOf("P" to listOf("Speed")), mapOf("Speed" to ModulatableParameter(1f)))
        phases.advance(1.0)
        phases.advance(31.0)
        assertEquals(1.0 + PhaseIntegrators.MAX_STEP_SEC, phases.value("P"), 1e-9)
    }

    @Test
    fun `the parser declares phase uniforms`() {
        val source = """
            /*{ "INPUTS": [ { "NAME": "Speed", "TYPE": "float" } ], "LSD_PHASES": { "SpinPhase": ["Speed"] } }*/
            void main() { gl_FragColor = vec4(SpinPhase); }
        """.trimIndent()
        val header = ISFParser.parseHeader(source)!!
        assertEquals(listOf("Speed"), header.LSD_PHASES["SpinPhase"])
        assertTrue(ISFParser.buildGLSLFragmentShader(source, header).contains("uniform float SpinPhase;"))
    }
}
