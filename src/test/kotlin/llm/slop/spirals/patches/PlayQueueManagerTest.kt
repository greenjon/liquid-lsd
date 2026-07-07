package llm.slop.spirals.patches

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import llm.slop.spirals.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File
import kotlin.io.path.createTempDirectory

class PlayQueueManagerTest {

    private val mixer = mockk<Mixer>(relaxed = true)

    @BeforeTest
    fun setUp() {
        mockkObject(PatchManager)
        every { PatchManager.isDeckDirty(any(), any()) } returns false
        every { PatchManager.loadDeckPresetAsync(any(), any()) } returns Unit
        PlayQueueManager.clearQueue()
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(PatchManager)
    }

    @Test
    fun testSequentialNoRepeatForward() {
        PlayQueueManager.isRepeatEnabled = false
        PlayQueueManager.isShuffleEnabled = false

        PlayQueueManager.appendToQueue(File("presets/patches/patch1.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch2.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch3.lsd"))

        assertEquals(-1, PlayQueueManager.activeIndex)

        // Trigger 1 -> Index 0
        PlayQueueManager.triggerNext(mixer)
        assertEquals(0, PlayQueueManager.activeIndex)

        // Trigger 2 -> Index 1
        PlayQueueManager.triggerNext(mixer)
        assertEquals(1, PlayQueueManager.activeIndex)

        // Trigger 3 -> Index 2
        PlayQueueManager.triggerNext(mixer)
        assertEquals(2, PlayQueueManager.activeIndex)

        // Trigger 4 -> End reached, stays at 2
        PlayQueueManager.triggerNext(mixer)
        assertEquals(2, PlayQueueManager.activeIndex)
    }

    @Test
    fun testSequentialRepeatForward() {
        PlayQueueManager.isRepeatEnabled = true
        PlayQueueManager.isShuffleEnabled = false

        PlayQueueManager.appendToQueue(File("presets/patches/patch1.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch2.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch3.lsd"))

        // Trigger 1 -> Index 0
        PlayQueueManager.triggerNext(mixer)
        assertEquals(0, PlayQueueManager.activeIndex)

        // Trigger 2 -> Index 1
        PlayQueueManager.triggerNext(mixer)
        assertEquals(1, PlayQueueManager.activeIndex)

        // Trigger 3 -> Index 2
        PlayQueueManager.triggerNext(mixer)
        assertEquals(2, PlayQueueManager.activeIndex)

        // Trigger 4 -> Wraps around to 0
        PlayQueueManager.triggerNext(mixer)
        assertEquals(0, PlayQueueManager.activeIndex)
    }

    @Test
    fun testSequentialRepeatBackward() {
        PlayQueueManager.isRepeatEnabled = true
        PlayQueueManager.isShuffleEnabled = false

        PlayQueueManager.appendToQueue(File("presets/patches/patch1.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch2.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch3.lsd"))

        // Set activeIndex to 0 initially
        PlayQueueManager.restoreSessionQueue(
            PlayQueueManager.queue,
            0,
            false,
            repeat = true,
            shuffle = false
        )
        assertEquals(0, PlayQueueManager.activeIndex)

        // Trigger previous -> wraps to Index 2 (last item)
        PlayQueueManager.triggerPrevious(mixer)
        assertEquals(2, PlayQueueManager.activeIndex)

        // Trigger previous -> Index 1
        PlayQueueManager.triggerPrevious(mixer)
        assertEquals(1, PlayQueueManager.activeIndex)
    }

    @Test
    fun testShuffleNoRepeatForward() {
        PlayQueueManager.isRepeatEnabled = false
        PlayQueueManager.isShuffleEnabled = true
        PlayQueueManager.initializeShuffle()

        PlayQueueManager.appendToQueue(File("presets/patches/patch1.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch2.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch3.lsd"))

        val visited = mutableSetOf<Int>()

        // Call triggerNext 3 times, checking that we visited every track exactly once
        for (i in 0 until 3) {
            PlayQueueManager.triggerNext(mixer)
            val active = PlayQueueManager.activeIndex
            assertTrue(active in 0..2)
            visited.add(active)
        }

        assertEquals(3, visited.size)

        // 4th trigger next -> no unplayed left, repeat is off, should stay on the last track
        val lastActive = PlayQueueManager.activeIndex
        PlayQueueManager.triggerNext(mixer)
        assertEquals(lastActive, PlayQueueManager.activeIndex)
    }

    @Test
    fun testShuffleRepeatForward() {
        PlayQueueManager.isRepeatEnabled = true
        PlayQueueManager.isShuffleEnabled = true
        PlayQueueManager.initializeShuffle()

        PlayQueueManager.appendToQueue(File("presets/patches/patch1.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch2.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch3.lsd"))

        val visitedCycle1 = mutableSetOf<Int>()
        for (i in 0 until 3) {
            PlayQueueManager.triggerNext(mixer)
            visitedCycle1.add(PlayQueueManager.activeIndex)
        }
        assertEquals(3, visitedCycle1.size)

        // With repeat engaged, 4th trigger should wrap around (clearing playedIndices) and continue playing
        PlayQueueManager.triggerNext(mixer)
        val indexAfterWrap = PlayQueueManager.activeIndex
        assertTrue(indexAfterWrap in 0..2)
    }

    @Test
    fun testShufflePreviousHistory() {
        PlayQueueManager.isRepeatEnabled = false
        PlayQueueManager.isShuffleEnabled = true
        PlayQueueManager.initializeShuffle()

        PlayQueueManager.appendToQueue(File("presets/patches/patch1.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch2.lsd"))
        PlayQueueManager.appendToQueue(File("presets/patches/patch3.lsd"))

        // Walk forward 3 tracks
        val history = mutableListOf<Int>()
        for (i in 0 until 3) {
            PlayQueueManager.triggerNext(mixer)
            history.add(PlayQueueManager.activeIndex)
        }

        // Walk backward 2 tracks and assert history matches exactly
        PlayQueueManager.triggerPrevious(mixer)
        assertEquals(history[1], PlayQueueManager.activeIndex)

        PlayQueueManager.triggerPrevious(mixer)
        assertEquals(history[0], PlayQueueManager.activeIndex)
    }

    @Test
    fun testIndexShiftingAndRemoval() {
        PlayQueueManager.isRepeatEnabled = false
        PlayQueueManager.isShuffleEnabled = true

        val f1 = File("presets/patches/patch1.lsd")
        val f2 = File("presets/patches/patch2.lsd")
        val f3 = File("presets/patches/patch3.lsd")

        PlayQueueManager.appendToQueue(f1)
        PlayQueueManager.appendToQueue(f2)

        PlayQueueManager.restoreSessionQueue(
            PlayQueueManager.queue,
            0,
            false,
            repeat = false,
            shuffle = true
        )
        // playedIndices has [0]
        assertTrue(0 in PlayQueueManager.playedIndices)

        // Insert f3 after current (at index 1), shifts original index 1 (f2) to index 2
        PlayQueueManager.insertAfterCurrent(f3)
        // activeIndex remains 0, playedIndices should still have [0]
        assertTrue(0 in PlayQueueManager.playedIndices)
        assertEquals(3, PlayQueueManager.queue.size)

        // Remove index 0
        PlayQueueManager.removeFromQueue(0)
        // should adjust activeIndex and shift playedIndices
        // activeIndex becomes -1, index 0 is removed, but others shift down by 1.
        assertFalse(0 in PlayQueueManager.playedIndices)
    }

    @Test
    fun testSharedPlaylistParserResolvesJsonItemsWithPatchExtensions() {
        val tempDir = createTempDirectory().toFile()
        val patchFile = File(tempDir, "testPatch.lsd").apply { writeText("{}") }
        val playlistContent = """
            {
              "version": 1,
              "name": "Test",
              "items": ["testPatch"]
            }
        """.trimIndent()

        val items = PlaylistParser.parseItems(playlistContent)
        val resolved = PlaylistParser.resolveItems(items, listOf(tempDir))

        assertEquals(listOf(patchFile.absoluteFile), resolved.map { it.absoluteFile })
    }
}
