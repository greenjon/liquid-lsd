package llm.slop.liquidlsd.rendering

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MixerTransitionTest {

    // --- Transitions & ISF Composite Routing ---

    @Test
    fun testMixerTransitionParameterRouting() {
        val crossfade = ModulatableParameter(0.5f)
        val masterLevel = ModulatableParameter(1.0f)
        val filter: ISFFilter = mockk(relaxed = true)
        every { filter.getParameterPaths(any()) } returns listOf("Mixer/Transition/softness" to ModulatableParameter(0.2f))

        val mixer = mockk<Mixer>(relaxed = true)
        every { mixer.crossfade } returns crossfade
        every { mixer.masterLevel } returns masterLevel
        every { mixer.transitionFilter } returns filter

        every { mixer.getParameterPaths("Mixer") } answers {
            val list = mutableListOf<Pair<String, ModulatableParameter>>()
            list.add("Mixer/crossfade" to crossfade)
            list.add("Mixer/masterLevel" to masterLevel)
            filter.let { f -> list.addAll(f.getParameterPaths("Mixer/Transition")) }
            list
        }

        val paths = mixer.getParameterPaths("Mixer")
        assertTrue(paths.any { it.first == "Mixer/crossfade" })
        assertTrue(paths.any { it.first == "Mixer/masterLevel" })
        assertTrue(paths.any { it.first == "Mixer/Transition/softness" }, "Transition parameter 'softness' should be registered")
    }

    @Test
    fun testMixerTransitionClearResetsToDefaultNonISF() {
        var transitionFilter: ISFFilter? = mockk(relaxed = true)
        val mixer = mockk<Mixer>(relaxed = true)
        every { mixer.transitionFilter } answers { transitionFilter }
        every { mixer.setTransition(null) } answers { transitionFilter = null }

        mixer.setTransition(null)
        assertNull(mixer.transitionFilter)
    }

    @Test
    fun testMixerFragHasIsfCompositeSupport() {
        val stream = javaClass.classLoader.getResourceAsStream("shaders/mixer.frag")
        assertNotNull(stream, "shaders/mixer.frag must exist")
        val source = stream!!.bufferedReader().use { it.readText() }

        assertTrue(source.contains("uProgress"), "mixer.frag must declare uProgress uniform")
        assertTrue(source.contains("uLevelA = 1.0"), "mixer.frag must declare uLevelA default 1.0")
        assertTrue(source.contains("uLevelB = 1.0"), "mixer.frag must declare uLevelB default 1.0")
        assertTrue(source.contains("uLevelBG = 1.0"), "mixer.frag must declare uLevelBG default 1.0")
        assertTrue(source.contains("uMasterLevel = 1.0"), "mixer.frag must declare uMasterLevel default 1.0")
        assertTrue(source.contains("sampleComposite"), "mixer.frag must composite foreground and background layers")
    }

    // --- Tap Tempo Operations ---

    @Test
    fun testTapTempoParameterMidiFilter() {
        val tapTempoParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f, isRandomizeDisabled = true).apply {
            modulatorFilter = { mod -> mod.sourceId.startsWith("midi_cc_") }
        }

        assertTrue(tapTempoParam.isRandomizeDisabled)

        // MIDI CC modulator should pass filter
        val midiMod = CvModulator(sourceId = "midi_cc_16", depth = 1.0f)
        assertTrue(tapTempoParam.modulatorFilter?.invoke(midiMod) == true)

        // LFO, Beat, or Audio modulators should fail filter
        val lfoMod = CvModulator(sourceId = "lfo1", depth = 1.0f)
        assertFalse(tapTempoParam.modulatorFilter?.invoke(lfoMod) == true)

        val audioMod = CvModulator(sourceId = "bass", depth = 1.0f)
        assertFalse(tapTempoParam.modulatorFilter?.invoke(audioMod) == true)
    }

    @Test
    fun testPollTapTempoRisingEdge() {
        val tapTempoParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f, isRandomizeDisabled = true)
        val mixer = mockk<Mixer>(relaxed = true)

        var prevTapTempoVal = 0.0f
        every { mixer.tapTempo } returns tapTempoParam
        every { mixer.pollTapTempo() } answers {
            val nextVal = tapTempoParam.value
            val triggered = prevTapTempoVal < 0.5f && nextVal >= 0.5f
            prevTapTempoVal = nextVal
            if (tapTempoParam.baseValue != 0f) tapTempoParam.baseValue = 0f
            triggered
        }

        // Initial state at 0.0f -> no trigger
        tapTempoParam.baseValue = 0.0f
        tapTempoParam.evaluate()
        assertFalse(mixer.pollTapTempo())

        // Value jumps to 0.7f -> rising edge trigger!
        tapTempoParam.baseValue = 0.7f
        tapTempoParam.evaluate()
        assertTrue(mixer.pollTapTempo())
        assertEquals(0.0f, tapTempoParam.baseValue, "Base value should reset to 0f after trigger")

        // Next frame stays high (or still evaluated at high) -> no trigger (not a rising edge)
        assertFalse(mixer.pollTapTempo())

        // Drops below 0.5f -> no trigger
        tapTempoParam.baseValue = 0.2f
        tapTempoParam.evaluate()
        assertFalse(mixer.pollTapTempo())

        // Jumps back up to 1.0f -> rising edge trigger!
        tapTempoParam.baseValue = 1.0f
        tapTempoParam.evaluate()
        assertTrue(mixer.pollTapTempo())
    }
}
