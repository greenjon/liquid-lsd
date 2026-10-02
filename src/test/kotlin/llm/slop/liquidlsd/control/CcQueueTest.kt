package llm.slop.liquidlsd.control

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CcQueueTest {
    @Test
    fun changesComeBackOldestFirst() {
        val q = CcQueue()
        q.offer(0, 5, 10); q.offer(1, 5, 20); q.offer(0, 6, 30)
        assertEquals(Cc(0, 5, 10), q.poll())
        assertEquals(Cc(1, 5, 20), q.poll())
        assertEquals(Cc(0, 6, 30), q.poll())
        assertNull(q.poll())
    }

    @Test
    fun aNewerValueReplacesAnOlderOneAndKeepsItsPlaceInLine() {
        val q = CcQueue()
        q.offer(0, 1, 10); q.offer(0, 2, 20)
        q.offer(0, 1, 99)                               // knob 1 moved again before it was sent
        assertEquals(2, q.size)
        assertEquals(Cc(0, 1, 99), q.poll())
        assertEquals(Cc(0, 2, 20), q.poll())
    }

    @Test
    fun theSameCcOnAnotherChannelIsAnotherEntry() {
        val q = CcQueue()
        q.offer(0, 7, 1); q.offer(1, 7, 2)
        assertEquals(2, q.size)
    }

    @Test
    fun aSlowWriterNeverSeesMoreThanOneEntryPerTarget() {
        val q = CcQueue()
        repeat(1000) { q.offer(0, it % 8, it) }          // a long burst of ring updates for 8 knobs
        assertEquals(8, q.size)
        val drained = generateSequence { q.poll() }.toList()
        assertEquals((992..999).toList(), drained.map { it.value }.sorted(), "the latest value of each")
    }

    @Test
    fun takeWaitsForAnOfferAndTimesOutWhenThereIsNone() {
        val q = CcQueue()
        assertNull(q.take(20))
        val producer = thread { Thread.sleep(30); q.offer(2, 3, 4) }
        assertEquals(Cc(2, 3, 4), q.take(2000))
        producer.join()
    }
}
