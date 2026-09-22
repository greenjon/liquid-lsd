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

    @Test
    fun testMixerModeParameterPathAndModulatorFilter() {
        val mixer = mockk<Mixer>()
        val masterLevelParam = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
        every { mixer.getParameterPaths("Mixer") } returns listOf(
            Pair("Mixer/crossfade", ModulatableParameter(0.5f)),
            Pair("Mixer/masterLevel", masterLevelParam)
        )
        val paths = ParameterResolver.getAllParameterPaths(mixer)
        
        val masterLevelEntry = paths.find { it.first == "Mixer/masterLevel" }
        assertTrue(masterLevelEntry != null, "Mixer/masterLevel should be registered in parameter paths")
        assertTrue(masterLevelEntry.second === masterLevelParam, "Mixer/masterLevel path should map to masterLevelParam instance")

        val found = ParameterResolver.findParameterByPath(mixer, "Mixer/masterLevel")
        assertTrue(found === masterLevelParam, "ParameterResolver should find Mixer/masterLevel")
    }
}
