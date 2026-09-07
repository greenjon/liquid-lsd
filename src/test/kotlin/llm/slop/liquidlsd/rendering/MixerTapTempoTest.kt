package llm.slop.liquidlsd.rendering

import io.mockk.mockk
import io.mockk.every
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MixerTapTempoTest {

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
