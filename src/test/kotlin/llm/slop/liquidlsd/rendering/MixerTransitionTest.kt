package llm.slop.liquidlsd.rendering

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFInput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MixerTransitionTest {

    @Test
    fun `test mixer transition parameter routing`() {
        val crossfade = llm.slop.liquidlsd.parameters.ModulatableParameter(-1.0f)
        val mode = llm.slop.liquidlsd.parameters.ModulatableParameter(4.0f)
        val masterAlpha = llm.slop.liquidlsd.parameters.ModulatableParameter(1.0f)

        val header = ISFHeader(
            INPUTS = listOf(
                ISFInput(NAME = "startImage", TYPE = "image"),
                ISFInput(NAME = "endImage", TYPE = "image"),
                ISFInput(NAME = "progress", TYPE = "float"),
                ISFInput(NAME = "softness", TYPE = "float", DEFAULT = kotlinx.serialization.json.JsonPrimitive(0.1f))
            )
        )
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("wipe_horizontal", "Horizontal Wipe", header, shader)

        val mixer = mockk<Mixer>(relaxed = true)
        io.mockk.every { mixer.crossfade } returns crossfade
        io.mockk.every { mixer.mode } returns mode
        io.mockk.every { mixer.masterAlpha } returns masterAlpha
        io.mockk.every { mixer.transitionFilter } returns filter

        io.mockk.every { mixer.getParameterPaths("Mixer") } answers {
            val list = mutableListOf<Pair<String, llm.slop.liquidlsd.parameters.ModulatableParameter>>()
            list.add("Mixer/crossfade" to crossfade)
            list.add("Mixer/mode" to mode)
            list.add("Mixer/masterAlpha" to masterAlpha)
            filter.let { f -> list.addAll(f.getParameterPaths("Mixer/Transition")) }
            list
        }

        val paths = mixer.getParameterPaths("Mixer")
        assertTrue(paths.any { it.first == "Mixer/crossfade" })
        assertTrue(paths.any { it.first == "Mixer/mode" })
        assertTrue(paths.any { it.first == "Mixer/Transition/softness" }, "Transition parameter 'softness' should be registered")
    }

    @Test
    fun `test mixer transition clear resets to default non-ISF`() {
        var transitionFilter: ISFFilter? = mockk(relaxed = true)
        val mixer = mockk<Mixer>(relaxed = true)
        io.mockk.every { mixer.transitionFilter } answers { transitionFilter }
        io.mockk.every { mixer.setTransition(null) } answers { transitionFilter = null }

        mixer.setTransition(null)
        assertNull(mixer.transitionFilter)
    }
}
