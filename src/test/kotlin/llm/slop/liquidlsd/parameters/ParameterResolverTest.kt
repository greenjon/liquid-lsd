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
        val modeParam = ModulatableParameter(4.0f, minClamp = 0.0f, maxClamp = 4.0f).apply {
            modulatorFilter = { false }
        }
        every { mixer.getParameterPaths("Mixer") } returns listOf(
            Pair("Mixer/crossfade", ModulatableParameter(0.5f)),
            Pair("Mixer/mode", modeParam)
        )
        val paths = ParameterResolver.getAllParameterPaths(mixer)
        
        val modeEntry = paths.find { it.first == "Mixer/mode" }
        assertTrue(modeEntry != null, "Mixer/mode should be registered in parameter paths")
        assertTrue(modeEntry.second === modeParam, "Mixer/mode path should map to modeParam instance")

        val found = ParameterResolver.findParameterByPath(mixer, "Mixer/mode")
        assertTrue(found === modeParam, "ParameterResolver should find Mixer/mode")

        val dummyMod = CvModulator(sourceId = "lfo_1")
        assertTrue(modeParam.modulatorFilter?.invoke(dummyMod) == false, "Mixer mode must be non-modulatable")
    }
}
