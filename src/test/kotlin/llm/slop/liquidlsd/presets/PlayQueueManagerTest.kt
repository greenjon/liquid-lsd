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

class PlayQueueManagerTest {

    private val mixer = mockk<Mixer>(relaxed = true)

    @BeforeTest
    fun setUp() {
        mockkObject(PresetManager)
        every { PresetManager.isDeckDirty(any(), any()) } returns false
        every { PresetManager.loadDeckPresetAsync(any(), any(), any(), any()) } returns Unit
        PlayQueueManager.clearQueue()
        PlayQueueManager.isAutoVJEnabled = false
        PlayQueueManager.isRepeatEnabled = false
        PlayQueueManager.isShuffleEnabled = false
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(PresetManager)
    }

    @Test
    fun testManualLoadOnActiveDeckKeepsQueuePointer() {
        PlayQueueManager.appendToQueue(File("library/presets/p1.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/p2.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/p3.lsd"))

        // Crossfade at Deck A (-1.0)
        every { mixer.crossfade.value } returns -1.0f

        // First trigger -> loads p1 to Deck B, activeIndex = 0
        PlayQueueManager.triggerNext(mixer)
        assertEquals(0, PlayQueueManager.activeIndex)

        // Deck A is active. Manual load on Deck A.
        PlayQueueManager.notifyManualDeckLoaded(isDeckA = true, isDeckPV = false, mixer = mixer)
        assertFalse(PlayQueueManager.stagedDeckA, "Active deck should not be flagged as staged")
        assertEquals(0, PlayQueueManager.activeIndex, "Queue index must remain unchanged on manual load")

        // Next trigger -> loads p2 to Deck B, activeIndex = 1
        PlayQueueManager.triggerNext(mixer)
        assertEquals(1, PlayQueueManager.activeIndex)
    }

    @Test
    fun testManualLoadOnStandbyDeckJumpsTheLine() {
        PlayQueueManager.appendToQueue(File("library/presets/p1.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/p2.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/p3.lsd"))

        // Crossfade at Deck A (-1.0f) -> Deck B is standby
        every { mixer.crossfade.value } returns -1.0f

        // Queue is at index 0
        PlayQueueManager.triggerNext(mixer)
        assertEquals(0, PlayQueueManager.activeIndex)

        // User manually loads a preset into standby Deck B
        PlayQueueManager.notifyManualDeckLoaded(isDeckA = false, isDeckPV = false, mixer = mixer)
        assertTrue(PlayQueueManager.stagedDeckB, "Standby Deck B should be marked as staged")

        // Trigger Next -> should trigger crossfade to Deck B without advancing queue index or loading next file
        PlayQueueManager.triggerNext(mixer)
        assertFalse(PlayQueueManager.stagedDeckB, "Staged flag should be cleared after trigger")
        assertEquals(0, PlayQueueManager.activeIndex, "Active index must remain at 0 (p2 is saved for next transition)")
        io.mockk.verify { mixer.targetCrossfade = 1.0f }

        // Now crossfade reaches Deck B (+1.0f) -> Deck A is standby
        every { mixer.crossfade.value } returns 1.0f

        // Subsequent trigger -> normal queue advance loads p2 into Deck A
        PlayQueueManager.triggerNext(mixer)
        assertEquals(1, PlayQueueManager.activeIndex, "Queue resumes and advances to p2")
        io.mockk.verify { mixer.targetCrossfade = -1.0f }
    }

    @Test
    fun testManualLoadOnDeckPVDoesNotAffectAutoVJ() {
        PlayQueueManager.appendToQueue(File("library/presets/p1.lsd"))
        every { mixer.crossfade.value } returns -1.0f

        PlayQueueManager.notifyManualDeckLoaded(isDeckA = false, isDeckPV = true, mixer = mixer)
        assertFalse(PlayQueueManager.stagedDeckA)
        assertFalse(PlayQueueManager.stagedDeckB)
    }

    @Test
    fun testAutoVJArmedWhileManualPlayback() {
        PlayQueueManager.appendToQueue(File("library/presets/p1.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/p2.lsd"))

        // Manual playback with Deck A live (-1.0f), AutoVJ enabled, activeIndex is -1
        every { mixer.crossfade.value } returns -1.0f
        PlayQueueManager.isAutoVJEnabled = true
        assertEquals(-1, PlayQueueManager.activeIndex)

        // First trigger loads queue[0] to standby Deck B
        PlayQueueManager.triggerNext(mixer)
        assertEquals(0, PlayQueueManager.activeIndex)
        io.mockk.verify { mixer.targetCrossfade = 1.0f }
    }

    @Test
    fun testSequentialNoRepeatForward() {
        PlayQueueManager.isRepeatEnabled = false
        PlayQueueManager.isShuffleEnabled = false

        PlayQueueManager.appendToQueue(File("library/presets/preset1.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset2.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset3.lsd"))

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

        PlayQueueManager.appendToQueue(File("library/presets/preset1.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset2.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset3.lsd"))

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

        PlayQueueManager.appendToQueue(File("library/presets/preset1.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset2.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset3.lsd"))

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

        PlayQueueManager.appendToQueue(File("library/presets/preset1.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset2.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset3.lsd"))

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

        PlayQueueManager.appendToQueue(File("library/presets/preset1.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset2.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset3.lsd"))

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

        PlayQueueManager.appendToQueue(File("library/presets/preset1.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset2.lsd"))
        PlayQueueManager.appendToQueue(File("library/presets/preset3.lsd"))

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

        val f1 = File("library/presets/preset1.lsd")
        val f2 = File("library/presets/preset2.lsd")
        val f3 = File("library/presets/preset3.lsd")

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
    fun testSharedPlaylistParserResolvesJsonItemsWithPresetExtensions() {
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

    @Test
    fun testPlayNowEnablesAutoVjAndMutesCrossfadeCv() {
        PlayQueueManager.isAutoVJEnabled = false
        val file = File("library/presets/testPreset.lsd")
        
        PlayQueueManager.playNow(file, mixer)

        assertTrue(PlayQueueManager.isAutoVJEnabled, "playNow should enable Auto-VJ")
        io.mockk.verify { mixer.muteCrossfadeNonMidiCv() }
    }

    @Test
    fun testPlayPlaylistNowEnablesAutoVjAndMutesCrossfadeCv() {
        PlayQueueManager.isAutoVJEnabled = false
        val tempDir = createTempDirectory().toFile()
        val patch1 = File(tempDir, "p1.lsd").apply { writeText("{}") }
        val playlistFile = File(tempDir, "test.json").apply {
            writeText("""{"version":1,"name":"Test","items":["${patch1.name}"]}""")
        }

        PlayQueueManager.playPlaylistNow(playlistFile, mixer)

        assertTrue(PlayQueueManager.isAutoVJEnabled, "playPlaylistNow should enable Auto-VJ")
        io.mockk.verify(atLeast = 1) { mixer.muteCrossfadeNonMidiCv() }
    }
}
