package llm.slop.liquidlsd.midi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MidiEngineQueueTest {
    private fun event(i: Int) = MidiEvent(0, MidiMessageType.CC, i % 128, 1, 1 / 127f, timestampMs = i.toLong())

    @Test
    fun queueIsBoundedAndDropsNewestWhenFull() {
        MidiEngine.clearEvents()
        val droppedBefore = MidiEngine.droppedEventCount
        repeat(MidiEngine.MAX_QUEUED_EVENTS + 10) { MidiEngine.enqueueEvent(event(it)) }
        assertEquals(10L, MidiEngine.droppedEventCount - droppedBefore)
        var n = 0
        var first: MidiEvent? = null
        while (true) { val e = MidiEngine.pollEvent() ?: break; if (n++ == 0) first = e }
        assertEquals(MidiEngine.MAX_QUEUED_EVENTS, n)
        assertEquals(0L, first!!.timestampMs)
        assertNull(MidiEngine.pollEvent())
        // Draining frees the capacity again.
        assertTrue(MidiEngine.enqueueEvent(event(1)))
        MidiEngine.clearEvents()
    }
}
