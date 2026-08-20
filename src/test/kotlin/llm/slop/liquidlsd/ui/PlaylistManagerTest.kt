package llm.slop.liquidlsd.ui

import java.io.File
import kotlin.io.path.createTempDirectory
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.ParameterDto
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistManagerTest {

    @AfterTest
    fun tearDown() {
        FileSystemManager.clearScanCache()
    }

    @Test
    fun testCreateAndAutoSaveOperations() {
        val tempDir = createTempDirectory().toFile()
        val playlistResult = PlaylistManager.createPlaylist("test_playlist", tempDir)
        assertTrue(playlistResult.isSuccess)
        val playlist = playlistResult.getOrThrow()

        // Test insertPreset with default autoSave=true
        PlaylistManager.insertPreset(playlist, "preset1.lsd", 0)
        assertEquals(1, playlist.presets.size)
        assertEquals("preset1.lsd", playlist.presets[0])
        assertFalse(playlist.isDirty, "Playlist should be clean after auto-save on insert")

        // Reload from disk to verify file persistence
        val reloaded1 = PlaylistManager.loadPlaylist(File(playlist.filePath)).getOrThrow()
        assertEquals(listOf("preset1.lsd"), reloaded1.presets)

        // Test insertPreset at end
        PlaylistManager.insertPreset(playlist, "preset2.lsd", 1)
        assertEquals(listOf("preset1.lsd", "preset2.lsd"), playlist.presets)
        assertFalse(playlist.isDirty)

        val reloaded2 = PlaylistManager.loadPlaylist(File(playlist.filePath)).getOrThrow()
        assertEquals(listOf("preset1.lsd", "preset2.lsd"), reloaded2.presets)

        // Test movePreset
        PlaylistManager.movePreset(playlist, 0, 1)
        assertEquals(listOf("preset2.lsd", "preset1.lsd"), playlist.presets)
        assertFalse(playlist.isDirty)

        val reloaded3 = PlaylistManager.loadPlaylist(File(playlist.filePath)).getOrThrow()
        assertEquals(listOf("preset2.lsd", "preset1.lsd"), reloaded3.presets)

        // Test removePreset
        PlaylistManager.removePreset(playlist, 0)
        assertEquals(listOf("preset1.lsd"), playlist.presets)
        assertFalse(playlist.isDirty)

        val reloaded4 = PlaylistManager.loadPlaylist(File(playlist.filePath)).getOrThrow()
        assertEquals(listOf("preset1.lsd"), reloaded4.presets)
    }

    @Test
    fun testPresetTagExtraction() {
        val tempDir = createTempDirectory().toFile()
        val dto = DeckPresetDto(
            name = "tag_test",
            tags = listOf("hypnotic", "ambient", "fast"),
            visualSourceType = "Mandala",
            parameters = emptyMap(),
            feedbackParameters = emptyMap(),
            globalAlpha = ParameterDto(1f, 0f, 1f, false, emptyList())
        )
        val jsonStr = kotlinx.serialization.json.Json.encodeToString(DeckPresetDto.serializer(), dto)
        val presetFile = File(tempDir, "tag_test.lsd").apply {
            writeText(jsonStr)
        }

        val tags = FileSystemManager.getPresetTags(presetFile)
        assertEquals(listOf("hypnotic", "ambient", "fast"), tags)
    }
}
