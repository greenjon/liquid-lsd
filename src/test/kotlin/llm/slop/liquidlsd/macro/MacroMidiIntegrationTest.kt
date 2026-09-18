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

    @Test
    fun testMidiNoteMapsToMacroSwitch() {
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        val switch1 = bank.switches[0]
        switch1.switchBehavior = SwitchBehavior.TOGGLE
        switch1.value = 0.0f

        MidiMappingManager.addMapping(
            parameterPath = "Macro/deckA/switch_1",
            cc = 60,
            channel = 0,
            minVal = 0f,
            maxVal = 1f,
            messageType = MidiMessageType.NOTE,
            inputType = MidiInputType.BUTTON_NOTE
        )

        val mixer = mockk<Mixer>(relaxed = true)

        // Note On (press)
        val noteOn = MidiEvent(
            channel = 0,
            type = MidiMessageType.NOTE,
            index = 60,
            rawValue = 127,
            normalizedValue = 1.0f
        )
        MidiMappingManager.onMidiEvent(noteOn, mixer)
        assertEquals(1.0f, switch1.value)

        // Note Off (release)
        val noteOff = MidiEvent(
            channel = 0,
            type = MidiMessageType.NOTE,
            index = 60,
            rawValue = 0,
            normalizedValue = 0.0f
        )
        MidiMappingManager.onMidiEvent(noteOff, mixer)
        assertEquals(1.0f, switch1.value, "Toggle switch stays at 1 on release")

        // Second Note On (toggle off)
        MidiMappingManager.onMidiEvent(noteOn, mixer)
        assertEquals(0.0f, switch1.value, "Toggle switch toggles to 0 on second press")
    }
}
