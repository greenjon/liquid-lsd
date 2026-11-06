package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.midi.MidiControlMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MidiMappingTextTest {
    @Test
    fun `a mapped control shows its CC and any channel but the first`() {
        assertEquals("CC 12", midiMappingText(MidiControlMapping(cc = 12)))
        assertEquals("Ch 2 CC 12", midiMappingText(MidiControlMapping(cc = 12, channel = 1)))
        assertEquals("CC 0", midiMappingText(MidiControlMapping(cc = 0)))
    }

    @Test
    fun `no mapping or no controller gives no MIDI line`() {
        assertNull(midiMappingText(null))
        assertNull(midiMappingText(MidiControlMapping(cc = -1)))
    }
}
