package llm.slop.liquidlsd.rendering.isf

import io.mockk.mockk
import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.rendering.Shader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A bundled effect's parameter with a negative minimum must come out bipolar, so its knob ring is drawn from the centre. */
class BundledFilterMeterTest {
    @Test
    fun everyNegativeMinimumFilterParameterIsBipolar() {
        var checked = 0
        File("src/main/resources/default_filters").walkTopDown().filter { it.extension == "fs" }.forEach { f ->
            val header = ISFParser.parseHeader(f.readText()) ?: return@forEach
            val filter = ISFFilter(f.nameWithoutExtension, f.nameWithoutExtension, header, mockk<Shader>(relaxed = true))
            for ((name, p) in filter.parameters) {
                if (p.minClamp < 0f && p.steps == null) {
                    assertEquals(MeterType.BIPOLAR, p.meterType, "${f.name}: $name [${p.minClamp}, ${p.maxClamp}]")
                    checked++
                }
            }
        }
        assertTrue(checked > 10, "expected many bipolar bundled parameters, saw $checked")
    }
}
