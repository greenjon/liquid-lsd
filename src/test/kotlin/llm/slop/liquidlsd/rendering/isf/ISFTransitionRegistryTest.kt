package llm.slop.liquidlsd.rendering.isf

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ISFTransitionRegistryTest {

    @Test
    fun `test bundled transition shaders parsing`() {
        val transitionNames = listOf(
            "linear_crossfade",
            "wipe_horizontal",
            "wipe_vertical",
            "radial_wipe",
            "glitch_transition",
            "luma_wipe",
            "zoom_fade"
        )

        for (name in transitionNames) {
            val path = "default_transitions/$name.fs"
            val stream = javaClass.classLoader.getResourceAsStream(path)
            assertNotNull(stream, "Bundled transition $path must exist")

            val source = stream!!.bufferedReader().use { it.readText() }
            val header = ISFParser.parseHeader(source)
            assertNotNull(header, "ISF header for $name should parse successfully")

            val inputs = header!!.INPUTS
            val imageInputs = inputs.filter { it.TYPE.lowercase() == "image" }
            assertEquals(2, imageInputs.size, "Transition $name should define 2 image inputs (startImage and endImage)")

            val hasProgress = inputs.any { it.NAME.equals("progress", ignoreCase = true) }
            assertTrue(hasProgress, "Transition $name should contain a 'progress' input")
        }
    }
}
