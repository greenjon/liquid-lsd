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
    fun testLoadPreferencesAppliesMidiProfile() {
        val preferencesFile = File("lsd-preferences.properties")
        val prefBackupFile = File("lsd-preferences.properties.bak")
        val settingsFile = File("lsd-settings.properties")
        val settingsBackupFile = File("lsd-settings.properties.bak")

        var hadPrefBackup = false
        if (preferencesFile.exists()) {
            preferencesFile.copyTo(prefBackupFile, overwrite = true)
            hadPrefBackup = true
            preferencesFile.delete()
        }

        var hadSettingsBackup = false
        if (settingsFile.exists()) {
            settingsFile.copyTo(settingsBackupFile, overwrite = true)
            hadSettingsBackup = true
            settingsFile.delete()
        }

        try {
            preferencesFile.writeText("activeMidiProfile=test_profile\n")
            llm.slop.liquidlsd.ui.UITheme.loadPreferences()

            MidiMappingManager.loadProfile(llm.slop.liquidlsd.ui.UITheme.activeMidiProfile)

            assertEquals("test_profile", MidiMappingManager.activeProfileName)
        } finally {
            if (hadPrefBackup && prefBackupFile.exists()) {
                prefBackupFile.copyTo(preferencesFile, overwrite = true)
                prefBackupFile.delete()
            } else {
                preferencesFile.delete()
            }

            if (hadSettingsBackup && settingsBackupFile.exists()) {
                settingsBackupFile.copyTo(settingsFile, overwrite = true)
                settingsBackupFile.delete()
            } else {
                settingsFile.delete()
            }

            llm.slop.liquidlsd.ui.UITheme.loadPreferences()
        }
    }
}
