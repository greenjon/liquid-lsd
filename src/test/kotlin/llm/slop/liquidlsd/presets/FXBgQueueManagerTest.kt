package llm.slop.liquidlsd.presets

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class FXBgQueueManagerTest {

    private val session = mockk<SessionContext>(relaxed = true)
    private val mixer = mockk<Mixer>(relaxed = true)

    @BeforeTest
    fun setUp() {
        mockkObject(PresetManager)
        every { PresetManager.isDeckDirty(any(), any()) } returns false
        FXBgQueueManager.clearQueue()
        FXBgQueueManager.isRepeatEnabled = false
        FXBgQueueManager.isShuffleEnabled = false
    }

    @AfterTest
    fun tearDown() {
        FXBgQueueManager.clearQueue()
        unmockkObject(PresetManager)
    }

    @Test
    fun testSequentialAdvanceRepeatOff() {
        val f1 = File("bg_fx1.lsdfx")
        val f2 = File("bg_fx2.lsdfx")
        FXBgQueueManager.appendToQueue(f1)
        FXBgQueueManager.appendToQueue(f2)

        assertEquals(2, FXBgQueueManager.queue.size)
        assertEquals(-1, FXBgQueueManager.activeIndex)

        FXBgQueueManager.advanceNext(session, mixer)
        assertEquals(0, FXBgQueueManager.activeIndex)

        FXBgQueueManager.advanceNext(session, mixer)
        assertEquals(1, FXBgQueueManager.activeIndex)

        // End of queue reached, should not advance past last index
        FXBgQueueManager.advanceNext(session, mixer)
        assertEquals(1, FXBgQueueManager.activeIndex)
    }

    @Test
    fun testSequentialAdvanceRepeatOn() {
        FXBgQueueManager.isRepeatEnabled = true
        val f1 = File("bg_fx1.lsdfx")
        val f2 = File("bg_fx2.lsdfx")
        FXBgQueueManager.appendToQueue(f1)
        FXBgQueueManager.appendToQueue(f2)

        FXBgQueueManager.advanceNext(session, mixer)
        assertEquals(0, FXBgQueueManager.activeIndex)

        FXBgQueueManager.advanceNext(session, mixer)
        assertEquals(1, FXBgQueueManager.activeIndex)

        // Wraps around to index 0
        FXBgQueueManager.advanceNext(session, mixer)
        assertEquals(0, FXBgQueueManager.activeIndex)
    }

    @Test
    fun testRemoveAndClear() {
        val f1 = File("bg_fx1.lsdfx")
        val f2 = File("bg_fx2.lsdfx")
        FXBgQueueManager.appendToQueue(f1)
        FXBgQueueManager.appendToQueue(f2)

        FXBgQueueManager.removeFromQueue(0)
        assertEquals(1, FXBgQueueManager.queue.size)
        assertEquals("bg_fx2.lsdfx", FXBgQueueManager.queue[0].name)

        FXBgQueueManager.clearQueue()
        assertEquals(0, FXBgQueueManager.queue.size)
        assertEquals(-1, FXBgQueueManager.activeIndex)
    }

    @Test
    fun testDirtyTargetDeckSkipsAdvance() {
        every { PresetManager.isDeckDirty(any(), any()) } returns true
        val originalBehavior = llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior
        llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior = llm.slop.liquidlsd.ui.UITheme.AutoVjDirtyBehavior.SKIP
        try {
            FXBgQueueManager.appendToQueue(File("bg_fx1.lsdfx"))
            FXBgQueueManager.appendToQueue(File("bg_fx2.lsdfx"))

            FXBgQueueManager.advanceNext(session, mixer)

            assertEquals(-1, FXBgQueueManager.activeIndex, "Dirty Deck BG with SKIP behavior must not advance")
        } finally {
            llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior = originalBehavior
        }
    }
}
