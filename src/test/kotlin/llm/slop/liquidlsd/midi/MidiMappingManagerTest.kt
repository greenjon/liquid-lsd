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

    @Test
    fun testRotaryDeltaDecoding() {
        // Binary offset: 64 is center
        assertEquals(1, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_BINARY_OFFSET, 65))
        assertEquals(3, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_BINARY_OFFSET, 67))
        assertEquals(-1, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_BINARY_OFFSET, 63))
        assertEquals(-4, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_BINARY_OFFSET, 60))

        // Signed bit: 1..63 positive, >64 negative with bit 6 set
        assertEquals(1, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_SIGNED_BIT, 1))
        assertEquals(5, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_SIGNED_BIT, 5))
        assertEquals(-1, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_SIGNED_BIT, 65))
        assertEquals(-3, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_SIGNED_BIT, 67))

        // Two's complement: 1..63 positive, 64..127 negative
        assertEquals(1, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_TWOS_COMP, 1))
        assertEquals(4, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_TWOS_COMP, 4))
        assertEquals(-1, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_TWOS_COMP, 127))
        assertEquals(-2, MidiMappingManager.decodeRotaryDelta(MidiInputType.ROTARY_TWOS_COMP, 126))
    }

    @Test
    fun testProfileBackwardCompatibility() {
        val legacyJson = """
            {
                "profileName": "legacy_profile",
                "mappings": {
                    "Mixer/crossfade": {
                        "cc": 10,
                        "channel": 0,
                        "minVal": -1.0,
                        "maxVal": 1.0
                    }
                }
            }
        """.trimIndent()

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<MidiMappingProfile>(legacyJson)
        assertEquals("legacy_profile", decoded.profileName)
        val mapping = decoded.mappings["Mixer/crossfade"]
        kotlin.test.assertNotNull(mapping)
        assertEquals(10, mapping.cc)
        assertEquals(0, mapping.channel)
        assertEquals(-1.0f, mapping.minVal)
        assertEquals(1.0f, mapping.maxVal)
        // Default fields
        assertEquals(MidiMessageType.CC, mapping.messageType)
        assertEquals(MidiInputType.CONTINUOUS_CC, mapping.inputType)
        assertEquals(TriggerMode.TOGGLE, mapping.triggerMode)
        assertEquals(TakeoverMode.IMMEDIATE, mapping.takeoverMode)
        assertEquals(false, mapping.inverted)
        assertEquals(0f, mapping.slewMs)
    }
}
