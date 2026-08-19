package llm.slop.liquidlsd.parameters

import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.Mixer
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertTrue

class ParameterResolverTest {

    @Test
    fun testDelegatedParameterPaths() {
        val mixer = mockk<Mixer>()
        val param = ModulatableParameter(0.5f)
        
        every { mixer.getParameterPaths("Mixer") } returns listOf(Pair("Mixer/crossfade", param))
        
        val paths = ParameterResolver.getAllParameterPaths(mixer)
        assertTrue(paths.size == 1, "Should return delegated parameters")
        assertTrue(paths[0].first == "Mixer/crossfade", "Path should match")
        
        val found = ParameterResolver.findParameterByPath(mixer, "Mixer/crossfade")
        assertTrue(found == param, "Should find the correct parameter by path")
    }
}
