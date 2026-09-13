package llm.slop.liquidlsd.midi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MidiEngineTest {

    @Test
    fun testMidiMessageTypeEnum() {
        assertEquals(MidiMessageType.CC, MidiMessageType.valueOf("CC"))
        assertEquals(MidiMessageType.NOTE, MidiMessageType.valueOf("NOTE"))
        assertEquals(MidiMessageType.PITCH_BEND, MidiMessageType.valueOf("PITCH_BEND"))
    }

    @Test
    fun testMidiEventCreationAndStorage() {
        val event = MidiEvent(
            channel = 0,
            type = MidiMessageType.NOTE,
            index = 60,
            rawValue = 100,
            normalizedValue = 100f / 127f
        )
        assertEquals(0, event.channel)
        assertEquals(MidiMessageType.NOTE, event.type)
        assertEquals(60, event.index)
        assertEquals(100, event.rawValue)
        assertTrue(event.normalizedValue > 0.78f && event.normalizedValue < 0.79f)
    }

    @Test
    fun testNormalizedValueAccessors() {
        // Test normalized value helper for CC
        llm.slop.liquidlsd.ui.UITheme.midiEnabled = true
        val valCc = MidiEngine.getNormalizedValue(0, MidiMessageType.CC, 10)
        assertEquals(0.0f, valCc)

        val valNote = MidiEngine.getNormalizedValue(0, MidiMessageType.NOTE, 60)
        assertEquals(0.0f, valNote)

        val valPb = MidiEngine.getNormalizedValue(0, MidiMessageType.PITCH_BEND, 0)
        assertEquals(0.0f, valPb)
    }
}
