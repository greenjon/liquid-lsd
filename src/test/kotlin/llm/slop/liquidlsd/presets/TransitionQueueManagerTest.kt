package llm.slop.liquidlsd.presets

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class TransitionQueueManagerTest {

    private val mixer = mockk<Mixer>(relaxed = true)

    @BeforeTest
    fun setUp() {
        TransitionQueueManager.clearQueue()
        TransitionQueueManager.isAutoAdvanceEnabled = true
        TransitionQueueManager.isRepeatEnabled = false
        TransitionQueueManager.isShuffleEnabled = false
    }

    @AfterTest
    fun tearDown() {
        TransitionQueueManager.clearQueue()
    }

    @Test
    fun testSequentialAdvanceRepeatOff() {
        val f1 = File("linear_crossfade")
        val f2 = File("radial_wipe")
        val f3 = File("zoom_fade")
        TransitionQueueManager.appendToQueue(f1)
        TransitionQueueManager.appendToQueue(f2)
        TransitionQueueManager.appendToQueue(f3)

        assertEquals(3, TransitionQueueManager.queue.size)
        assertEquals(-1, TransitionQueueManager.activeIndex)

        TransitionQueueManager.advanceNext(mixer)
        assertEquals(0, TransitionQueueManager.activeIndex)

        TransitionQueueManager.advanceNext(mixer)
        assertEquals(1, TransitionQueueManager.activeIndex)

        TransitionQueueManager.advanceNext(mixer)
        assertEquals(2, TransitionQueueManager.activeIndex)

        // End of queue reached, should not advance past last index
        TransitionQueueManager.advanceNext(mixer)
        assertEquals(2, TransitionQueueManager.activeIndex)
    }

    @Test
    fun testSequentialAdvanceRepeatOn() {
        TransitionQueueManager.isRepeatEnabled = true
        val f1 = File("linear_crossfade")
        val f2 = File("radial_wipe")
        TransitionQueueManager.appendToQueue(f1)
        TransitionQueueManager.appendToQueue(f2)

        TransitionQueueManager.advanceNext(mixer)
        assertEquals(0, TransitionQueueManager.activeIndex)

        TransitionQueueManager.advanceNext(mixer)
        assertEquals(1, TransitionQueueManager.activeIndex)

        // Wraps around to index 0
        TransitionQueueManager.advanceNext(mixer)
        assertEquals(0, TransitionQueueManager.activeIndex)
    }

    @Test
    fun testShuffleModePlaysEveryItemOnceBeforeRecycling() {
        TransitionQueueManager.isShuffleEnabled = true
        TransitionQueueManager.isRepeatEnabled = true
        val files = (1..5).map { File("trans_$it") }
        files.forEach { TransitionQueueManager.appendToQueue(it) }

        val playedSet = mutableSetOf<Int>()
        repeat(5) {
            TransitionQueueManager.advanceNext(mixer)
            val idx = TransitionQueueManager.activeIndex
            assertTrue(idx in 0..4, "Active index $idx must be within range")
            playedSet.add(idx)
        }

        assertEquals(5, playedSet.size, "All 5 items should be played exactly once in shuffle cycle")

        // 6th advance should recycle and pick next item
        TransitionQueueManager.advanceNext(mixer)
        assertTrue(TransitionQueueManager.activeIndex in 0..4)
    }

    @Test
    fun testShuffleHistoryBackStepping() {
        TransitionQueueManager.isShuffleEnabled = true
        val files = (1..3).map { File("trans_$it") }
        files.forEach { TransitionQueueManager.appendToQueue(it) }

        TransitionQueueManager.advanceNext(mixer)
        val idx1 = TransitionQueueManager.activeIndex

        TransitionQueueManager.advanceNext(mixer)
        assertTrue(TransitionQueueManager.activeIndex in 0..2)

        // Back-step to idx1
        TransitionQueueManager.advancePrevious(mixer)
        assertEquals(idx1, TransitionQueueManager.activeIndex)
    }

    @Test
    fun testQueueMutationWhileShuffleActive() {
        TransitionQueueManager.isShuffleEnabled = true
        val f0 = File("trans_0")
        val f1 = File("trans_1")
        val f2 = File("trans_2")
        TransitionQueueManager.appendToQueue(f0)
        TransitionQueueManager.appendToQueue(f1)
        TransitionQueueManager.appendToQueue(f2)

        TransitionQueueManager.jumpToIndex(0, mixer)
        assertEquals(0, TransitionQueueManager.activeIndex)

        // Insert at index 0 should shift activeIndex from 0 to 1
        val newFile = File("trans_inserted")
        TransitionQueueManager.insertAt(0, newFile)
        assertEquals(4, TransitionQueueManager.queue.size)
        assertEquals(1, TransitionQueueManager.activeIndex)
        assertEquals(f0, TransitionQueueManager.queue[1])

        // Remove index 0
        TransitionQueueManager.removeFromQueue(0)
        assertEquals(3, TransitionQueueManager.queue.size)
        assertEquals(0, TransitionQueueManager.activeIndex)
        assertEquals(f0, TransitionQueueManager.queue[0])
    }

    @Test
    fun testAdvanceOnAutoFadeHook() {
        val f1 = File("linear_crossfade")
        val f2 = File("radial_wipe")
        TransitionQueueManager.appendToQueue(f1)
        TransitionQueueManager.appendToQueue(f2)

        // When auto-advance enabled:
        TransitionQueueManager.isAutoAdvanceEnabled = true
        TransitionQueueManager.advanceOnAutoFade(mixer)
        assertEquals(0, TransitionQueueManager.activeIndex)

        // When auto-advance disabled:
        TransitionQueueManager.isAutoAdvanceEnabled = false
        TransitionQueueManager.advanceOnAutoFade(mixer)
        assertEquals(0, TransitionQueueManager.activeIndex, "Active index should remain unchanged when auto-advance disabled")
    }
}
