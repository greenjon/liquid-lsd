package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class UIThemeTest {

    @Test
    fun testSettingsSaveAndLoadRoundTrip() {
        val settingsFile = File("lsd-settings.properties")
        val backupFile = File("lsd-settings.properties.bak")
        
        // Backup existing settings file if present
        var hadBackup = false
        if (settingsFile.exists()) {
            settingsFile.copyTo(backupFile, overwrite = true)
            hadBackup = true
        }

        try {
            // Modify settings in memory
            UITheme.baseSize = 24.0f
            UITheme.showMidiCol = false
            UITheme.showLfoCol = true
            UITheme.showAudioCol = false
            UITheme.showTriggerCol = true
            UITheme.cleanModeEnabled = true
            UITheme.tooltipsEnabled = false
            UITheme.maxFps = 60

            // Save to disk
            UITheme.saveSettings()
            assertTrue(settingsFile.exists(), "Settings file should be written")

            // Reset values to defaults in memory
            UITheme.baseSize = 15.0f
            UITheme.showMidiCol = true
            UITheme.showLfoCol = false
            UITheme.showAudioCol = true
            UITheme.showTriggerCol = false
            UITheme.cleanModeEnabled = false
            UITheme.tooltipsEnabled = true
            UITheme.maxFps = 30

            // Reload via reflection
            val loadMethod = UITheme::class.java.getDeclaredMethod("loadSettings")
            loadMethod.isAccessible = true
            loadMethod.invoke(UITheme)

            // Assert restored values match what was saved
            assertEquals(24.0f, UITheme.baseSize)
            assertFalse(UITheme.showMidiCol)
            assertTrue(UITheme.showLfoCol)
            assertFalse(UITheme.showAudioCol)
            assertTrue(UITheme.showTriggerCol)
            assertTrue(UITheme.cleanModeEnabled)
            assertFalse(UITheme.tooltipsEnabled)
            assertEquals(60, UITheme.maxFps)

        } finally {
            // Restore original settings file if backed up, or delete test file
            if (hadBackup && backupFile.exists()) {
                backupFile.copyTo(settingsFile, overwrite = true)
                backupFile.delete()
            } else {
                settingsFile.delete()
            }
            val loadMethod = UITheme::class.java.getDeclaredMethod("loadSettings")
            loadMethod.isAccessible = true
            loadMethod.invoke(UITheme)
        }
    }
}
