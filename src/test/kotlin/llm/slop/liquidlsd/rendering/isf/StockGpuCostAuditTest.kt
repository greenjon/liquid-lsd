package llm.slop.liquidlsd.rendering.isf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Bundled shaders may declare LSD_COST (GPU cost, 1..3); the declared values must be in range and the known heavy raymarchers must carry 3. */
class StockGpuCostAuditTest {

    private fun shaders(): List<File> =
        listOf("default_filters", "default_sources", "default_transitions").flatMap { name ->
            val dir = File(checkNotNull(ISFFilter::class.java.classLoader.getResource(name)) { "$name missing" }.toURI())
            dir.walkTopDown().filter { it.extension == "fs" }.toList()
        }

    @Test
    fun `declared costs are within 1 to 3`() {
        for (file in shaders()) {
            val declared = ISFParser.parseHeader(file.readText())?.LSD_COST ?: continue
            assertTrue(declared in 1..3, "${file.name}: LSD_COST $declared out of range")
        }
    }

    @Test
    fun `heavy raymarchers are tagged heavy`() {
        val heavy = setOf(
            "icosa_h3", "hyper_slice", "gyroid_hyperspace",
            "mandelbox", "kifs", "mandelbulb", "pseudo_kleinian", "apollonian", "escape_time",
            "nebula_volume"
        )
        val found = shaders().filter { it.nameWithoutExtension in heavy }
        assertEquals(heavy, found.map { it.nameWithoutExtension }.toSet())
        for (file in found) assertEquals(3, ISFParser.parseHeader(file.readText())?.gpuCost, file.name)
    }

    @Test
    fun `undeclared and out-of-range costs clamp`() {
        assertEquals(0, ISFHeader().gpuCost)
        assertEquals(3, ISFHeader(LSD_COST = 9).gpuCost)
        assertEquals(0, ISFHeader(LSD_COST = -2).gpuCost)
    }
}
