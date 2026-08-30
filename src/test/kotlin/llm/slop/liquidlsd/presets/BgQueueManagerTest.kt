package llm.slop.liquidlsd.presets

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File
import kotlin.io.path.createTempDirectory

class BgQueueManagerTest {

    private val mixer = mockk<Mixer>(relaxed = true)

    @BeforeTest
    fun setUp() {
        mockkObject(PresetManager)
        every { PresetManager.isDeckDirty(any(), any()) } returns false
        every { PresetManager.loadDeckPresetAsync(any(), any(), any(), any(), any()) } returns Unit
        BgQueueManager.clearQueue()
        BgQueueManager.isAutoBGEnabled = false
        BgQueueManager.isRepeatEnabled = false
        BgQueueManager.isShuffleEnabled = false
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(PresetManager)
    }

    @Test
    fun testAppendAndClear() {
        val f1 = File("library/presets/p1.lsd")
        val f2 = File("library/presets/p2.lsd")
        BgQueueManager.appendToQueue(f1)
        BgQueueManager.appendToQueue(f2)

        assertEquals(2, BgQueueManager.queue.size)
        assertEquals(f1, BgQueueManager.queue[0])
        assertEquals(f2, BgQueueManager.queue[1])

        BgQueueManager.clearQueue()
        assertEquals(0, BgQueueManager.queue.size)
        assertEquals(-1, BgQueueManager.activeIndex)
    }

    @Test
    fun testPlayNowEnablesAutoBgAndPlaysFirst() {
        BgQueueManager.isAutoBGEnabled = false
        val file = File("library/presets/bg1.lsd")

        BgQueueManager.playNow(file, mixer, withDipToBlack = false)

        assertTrue(BgQueueManager.isAutoBGEnabled, "playNow should enable Auto-BG")
        assertEquals(1, BgQueueManager.queue.size)
        assertEquals(0, BgQueueManager.activeIndex)
        assertEquals(file, BgQueueManager.queue[0])
    }

    @Test
    fun testPlayPlaylistNowEnablesAutoBgAndReplacesQueue() {
        BgQueueManager.isAutoBGEnabled = false
        val tempDir = createTempDirectory().toFile()
        val patch1 = File(tempDir, "bg1.lsd").apply { writeText("{}") }
        val patch2 = File(tempDir, "bg2.lsd").apply { writeText("{}") }
        val playlistFile = File(tempDir, "test_bg.json").apply {
            writeText("""{"version":1,"name":"TestBG","items":["${patch1.name}","${patch2.name}"]}""")
        }

        BgQueueManager.playPlaylistNow(playlistFile, mixer, withDipToBlack = false)

        assertTrue(BgQueueManager.isAutoBGEnabled, "playPlaylistNow should enable Auto-BG")
        assertEquals(2, BgQueueManager.queue.size)
        assertEquals(0, BgQueueManager.activeIndex)
        assertEquals(patch1.absolutePath, BgQueueManager.queue[0].absolutePath)
        assertEquals(patch2.absolutePath, BgQueueManager.queue[1].absolutePath)
    }

    @Test
    fun testInsertAfterCurrent() {
        val f1 = File("library/presets/p1.lsd")
        val f2 = File("library/presets/p2.lsd")
        val f3 = File("library/presets/p3.lsd")

        BgQueueManager.appendToQueue(f1)
        BgQueueManager.appendToQueue(f2)
        BgQueueManager.playIndex(0, mixer, withDipToBlack = false)
        assertEquals(0, BgQueueManager.activeIndex)

        BgQueueManager.insertAfterCurrent(f3)
        assertEquals(3, BgQueueManager.queue.size)
        assertEquals(f1, BgQueueManager.queue[0])
        assertEquals(f3, BgQueueManager.queue[1])
        assertEquals(f2, BgQueueManager.queue[2])
        assertEquals(0, BgQueueManager.activeIndex)
    }

    @Test
    fun testInsertPlaylistAfterCurrent() {
        val tempDir = createTempDirectory().toFile()
        val patch1 = File(tempDir, "p1.lsd").apply { writeText("{}") }
        val patch2 = File(tempDir, "p2.lsd").apply { writeText("{}") }
        val patch3 = File(tempDir, "p3.lsd").apply { writeText("{}") }
        val patch4 = File(tempDir, "p4.lsd").apply { writeText("{}") }

        val playlistFile = File(tempDir, "insert_pl.json").apply {
            writeText("""{"version":1,"name":"InsertPL","items":["${patch2.name}","${patch3.name}"]}""")
        }

        BgQueueManager.appendToQueue(patch1)
        BgQueueManager.appendToQueue(patch4)
        BgQueueManager.playIndex(0, mixer, withDipToBlack = false)

        BgQueueManager.insertPlaylistAfterCurrent(playlistFile)

        assertEquals(4, BgQueueManager.queue.size)
        assertEquals(patch1.absolutePath, BgQueueManager.queue[0].absolutePath)
        assertEquals(patch2.absolutePath, BgQueueManager.queue[1].absolutePath)
        assertEquals(patch3.absolutePath, BgQueueManager.queue[2].absolutePath)
        assertEquals(patch4.absolutePath, BgQueueManager.queue[3].absolutePath)
    }

    @Test
    fun testAppendPlaylistToQueue() {
        val tempDir = createTempDirectory().toFile()
        val patch1 = File(tempDir, "p1.lsd").apply { writeText("{}") }
        val patch2 = File(tempDir, "p2.lsd").apply { writeText("{}") }
        val playlistFile = File(tempDir, "append_pl.json").apply {
            writeText("""{"version":1,"name":"AppendPL","items":["${patch1.name}","${patch2.name}"]}""")
        }

        BgQueueManager.appendPlaylistToQueue(playlistFile)
        assertEquals(2, BgQueueManager.queue.size)
        assertEquals(patch1.absolutePath, BgQueueManager.queue[0].absolutePath)
        assertEquals(patch2.absolutePath, BgQueueManager.queue[1].absolutePath)
    }

    @Test
    fun testRemoveFileFromQueue() {
        val f1 = File("library/presets/p1.lsd")
        val f2 = File("library/presets/p2.lsd")
        val f3 = File("library/presets/p3.lsd")

        BgQueueManager.appendToQueue(f1)
        BgQueueManager.appendToQueue(f2)
        BgQueueManager.appendToQueue(f3)

        BgQueueManager.removeFileFromQueue(f2)
        assertEquals(2, BgQueueManager.queue.size)
        assertEquals(f1, BgQueueManager.queue[0])
        assertEquals(f3, BgQueueManager.queue[1])
    }

    @Test
    fun testDirtyDeckSkipBehavior() {
        val f1 = File("library/presets/p1.lsd")
        val f2 = File("library/presets/p2.lsd")
        BgQueueManager.appendToQueue(f1)
        BgQueueManager.appendToQueue(f2)

        every { PresetManager.isDeckDirty(any(), any()) } returns true
        llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior = llm.slop.liquidlsd.ui.UITheme.AutoVjDirtyBehavior.SKIP

        BgQueueManager.playIndex(0, mixer, withDipToBlack = false)
        assertEquals(-1, BgQueueManager.activeIndex, "Dirty deck with SKIP should not advance activeIndex")

        llm.slop.liquidlsd.ui.UITheme.autoVjDirtyBehavior = llm.slop.liquidlsd.ui.UITheme.AutoVjDirtyBehavior.AUTO_DISCARD
        BgQueueManager.playIndex(0, mixer, withDipToBlack = false)
        assertEquals(0, BgQueueManager.activeIndex, "AUTO_DISCARD should proceed with playback")
    }

    @Test
    fun testTriggerNextAndPrevious() {
        val f1 = File("library/presets/p1.lsd")
        val f2 = File("library/presets/p2.lsd")
        BgQueueManager.appendToQueue(f1)
        BgQueueManager.appendToQueue(f2)

        every { PresetManager.isDeckDirty(any(), any()) } returns false

        BgQueueManager.playIndex(0, mixer, withDipToBlack = false)
        assertEquals(0, BgQueueManager.activeIndex)

        BgQueueManager.triggerNext(mixer)
        assertEquals(1, BgQueueManager.activeIndex)

        BgQueueManager.triggerPrevious(mixer)
        assertEquals(0, BgQueueManager.activeIndex)
    }
}
