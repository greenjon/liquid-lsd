package llm.slop.liquidlsd.presets

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import kotlin.io.path.createTempDirectory

class FXQueueManagerTest {

    private val session = mockk<SessionContext>(relaxed = true)
    private val mixer = mockk<Mixer>(relaxed = true)

    @BeforeTest
    fun setUp() {
        mockkObject(PresetManager)
        every { PresetManager.isDeckDirty(any(), any()) } returns false
        FXQueueManager.clearQueue()
        FXQueueManager.isRepeatEnabled = false
        FXQueueManager.isShuffleEnabled = false
    }

    @AfterTest
    fun tearDown() {
        FXQueueManager.clearQueue()
        unmockkObject(PresetManager)
    }

    @Test
    fun testSequentialAdvanceRepeatOff() {
        val f1 = File("fx1.lsdfx")
        val f2 = File("fx2.lsdfx")
        val f3 = File("fxchain.lsdfxchain")
        FXQueueManager.appendToQueue(f1)
        FXQueueManager.appendToQueue(f2)
        FXQueueManager.appendToQueue(f3)

        assertEquals(3, FXQueueManager.queue.size)
        assertEquals(-1, FXQueueManager.activeIndex)

        FXQueueManager.advanceNext(session, mixer)
        assertEquals(0, FXQueueManager.activeIndex)

        FXQueueManager.advanceNext(session, mixer)
        assertEquals(1, FXQueueManager.activeIndex)

        FXQueueManager.advanceNext(session, mixer)
        assertEquals(2, FXQueueManager.activeIndex)

        // End of queue reached, should not advance past last index
        FXQueueManager.advanceNext(session, mixer)
        assertEquals(2, FXQueueManager.activeIndex)
    }

    @Test
    fun testSequentialAdvanceRepeatOn() {
        FXQueueManager.isRepeatEnabled = true
        val f1 = File("fx1.lsdfx")
        val f2 = File("fx2.lsdfx")
        FXQueueManager.appendToQueue(f1)
        FXQueueManager.appendToQueue(f2)

        FXQueueManager.advanceNext(session, mixer)
        assertEquals(0, FXQueueManager.activeIndex)

        FXQueueManager.advanceNext(session, mixer)
        assertEquals(1, FXQueueManager.activeIndex)

        // Wraps around to index 0
        FXQueueManager.advanceNext(session, mixer)
        assertEquals(0, FXQueueManager.activeIndex)
    }

    @Test
    fun testShuffleModePlaysEveryItemOnceBeforeRecycling() {
        FXQueueManager.isShuffleEnabled = true
        FXQueueManager.isRepeatEnabled = true
        val files = (1..5).map { File("fx_$it.lsdfx") }
        files.forEach { FXQueueManager.appendToQueue(it) }

        val playedSet = mutableSetOf<Int>()
        repeat(5) {
            FXQueueManager.advanceNext(session, mixer)
            val idx = FXQueueManager.activeIndex
            assertTrue(idx in 0..4, "Active index $idx must be within range")
            playedSet.add(idx)
        }

        assertEquals(5, playedSet.size, "All 5 items should be played exactly once in shuffle cycle")

        // 6th advance should recycle and pick next item
        FXQueueManager.advanceNext(session, mixer)
        assertTrue(FXQueueManager.activeIndex in 0..4)
    }

    @Test
    fun testShuffleHistoryBackStepping() {
        FXQueueManager.isShuffleEnabled = true
        val files = (1..3).map { File("fx_$it.lsdfx") }
        files.forEach { FXQueueManager.appendToQueue(it) }

        FXQueueManager.advanceNext(session, mixer)
        val first = FXQueueManager.activeIndex

        FXQueueManager.advanceNext(session, mixer)
        val second = FXQueueManager.activeIndex

        // Step back should return to first
        FXQueueManager.advancePrevious(session, mixer)
        assertEquals(first, FXQueueManager.activeIndex)
    }

    @Test
    fun testReorderingQueueUpdatesIndicesCorrectly() {
        val f1 = File("fx_a.lsdfx")
        val f2 = File("fx_b.lsdfx")
        val f3 = File("fx_c.lsdfx")
        FXQueueManager.appendToQueue(f1)
        FXQueueManager.appendToQueue(f2)
        FXQueueManager.appendToQueue(f3)

        FXQueueManager.advanceNext(session, mixer) // active = 0 (fx_a)
        assertEquals(0, FXQueueManager.activeIndex)

        // Move active item from 0 to 2
        FXQueueManager.moveItem(0, 2)
        assertEquals(2, FXQueueManager.activeIndex)
        assertEquals("fx_a.lsdfx", FXQueueManager.queue[2].name)
        assertEquals("fx_b.lsdfx", FXQueueManager.queue[0].name)
    }

    @Test
    fun testTargetDeckSelection() {
        io.mockk.every { mixer.crossfade.value } returns -0.5f
        assertEquals(mixer.deckA, FXQueueManager.getTargetDeck(mixer))

        io.mockk.every { mixer.crossfade.value } returns 0.5f
        assertEquals(mixer.deckB, FXQueueManager.getTargetDeck(mixer))
    }

    @Test
    fun testDirtyTargetDeckSkipsAdvance() {
        io.mockk.every { PresetManager.isDeckDirty(any(), any()) } returns true
        val originalBehavior = llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior
        llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior = llm.slop.liquidlsd.ui.UITheme.AutoVjDirtyBehavior.SKIP
        try {
            FXQueueManager.appendToQueue(File("fx1.lsdfx"))
            FXQueueManager.appendToQueue(File("fx2.lsdfx"))

            FXQueueManager.advanceNext(session, mixer)

            assertEquals(-1, FXQueueManager.activeIndex, "Dirty target deck with SKIP behavior must not advance")
        } finally {
            llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior = originalBehavior
        }
    }

    @Test
    fun testDirtyTargetDeckWithDiscardStillAdvances() {
        io.mockk.every { PresetManager.isDeckDirty(any(), any()) } returns true
        val originalBehavior = llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior
        llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior = llm.slop.liquidlsd.ui.UITheme.AutoVjDirtyBehavior.AUTO_DISCARD
        try {
            FXQueueManager.appendToQueue(File("fx1.lsdfx"))

            FXQueueManager.advanceNext(session, mixer)

            assertEquals(0, FXQueueManager.activeIndex, "AUTO_DISCARD behavior must still advance past a dirty deck")
        } finally {
            llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior = originalBehavior
        }
    }

    @Test
    fun testParsePlaylistResolvesViaSharedPlaylistParserAndSkipsMissingItems() {
        val tempDir = createTempDirectory().toFile()
        val realItem = File(tempDir, "fx_real.lsdfx").apply { writeText("{}") }
        val playlistFile = File(tempDir, "fx.lsdfxplay").apply {
            writeText("""{"version":1,"name":"Test","items":["${realItem.name}","missing.lsdfx"]}""")
        }

        val resolved = FXQueueManager.parsePlaylist(playlistFile)

        assertEquals(listOf(realItem.absoluteFile), resolved.map { it.absoluteFile })
    }
}
