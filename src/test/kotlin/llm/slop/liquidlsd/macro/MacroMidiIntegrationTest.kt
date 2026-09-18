package llm.slop.liquidlsd.macro

import io.mockk.mockk
import llm.slop.liquidlsd.midi.*
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.*

class MacroMidiIntegrationTest {

    @BeforeTest
    fun setUp() {
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
    }

    @AfterTest
    fun tearDown() {
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
    }

    @Test
    fun testMidiCcMapsToMacroKnob() {
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        val knob1 = bank.knobs[0]
        knob1.value = 0.0f

        MidiMappingManager.addMapping(
            parameterPath = "Macro/deckA/knob_1",
            cc = 21,
            channel = 0,
            minVal = 0f,
            maxVal = 1f,
            messageType = MidiMessageType.CC,
            inputType = MidiInputType.CONTINUOUS_CC
        )

        val mixer = mockk<Mixer>(relaxed = true)
        val event = MidiEvent(
            channel = 0,
            type = MidiMessageType.CC,
            index = 21,
            rawValue = 95,
            normalizedValue = 95f / 127f
        )

        MidiMappingManager.onMidiEvent(event, mixer)

        assertEquals(95f / 127f, knob1.value, absoluteTolerance = 1e-4f)
    }
}
