package llm.slop.liquidlsd.midi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.io.File
import kotlin.io.path.createTempDirectory

class MidiMappingManagerTest {

    @Test
    fun testSanitiseProfileNameRejectsPathTraversal() {
        assertFailsWith<IllegalArgumentException> { sanitiseProfileName("../external/profile") }
        assertFailsWith<IllegalArgumentException> { sanitiseProfileName(" ../ ") }
        assertFailsWith<IllegalArgumentException> { sanitiseProfileName("Live Set 01") }
        assertEquals("Live_Set_01", sanitiseProfileName("Live_Set_01"))
    }

    @Test
    fun testMidiProfileFileStaysUnderMidiDirectory() {
        val midiDir = createTempDirectory().toFile()
        // Wait, midiProfileFile now throws if it's invalid so this test might need adjustment
        val throws = runCatching { midiProfileFile(midiDir, "../outside") }.isFailure
        assertTrue(throws)
    }

    @Test
    fun testLoadSettingsAppliesMidiProfile() {
        val settingsFile = File("lsd-settings.properties")
        val backupFile = File("lsd-settings.properties.bak")
        var hadBackup = false
        if (settingsFile.exists()) {
            settingsFile.copyTo(backupFile, overwrite = true)
            hadBackup = true
        }

        try {
            settingsFile.writeText("activeMidiProfile=test_profile\n")
            // Use reflection to call private loadSettings if it is private
            val method = llm.slop.liquidlsd.ui.UITheme::class.java.getDeclaredMethod("loadSettings")
            method.isAccessible = true
            method.invoke(llm.slop.liquidlsd.ui.UITheme)

            MidiMappingManager.loadProfile(llm.slop.liquidlsd.ui.UITheme.activeMidiProfile)

            assertEquals("test_profile", MidiMappingManager.activeProfileName)
        } finally {
            if (hadBackup && backupFile.exists()) {
                backupFile.copyTo(settingsFile, overwrite = true)
                backupFile.delete()
            } else {
                settingsFile.delete()
            }
            val method = llm.slop.liquidlsd.ui.UITheme::class.java.getDeclaredMethod("loadSettings")
            method.isAccessible = true
            method.invoke(llm.slop.liquidlsd.ui.UITheme)
        }
    }
}
