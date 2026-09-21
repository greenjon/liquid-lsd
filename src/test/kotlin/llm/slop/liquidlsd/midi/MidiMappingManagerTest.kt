package llm.slop.liquidlsd.midi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import java.io.File
import kotlin.io.path.createTempDirectory

class MidiMappingManagerTest {

    // --- MIDI Engine Events & Types ---

    @Test
    fun testMidiMessageTypeEnum() {
        assertEquals(MidiMessageType.CC, MidiMessageType.valueOf("CC"))
        assertEquals(MidiMessageType.NOTE, MidiMessageType.valueOf("NOTE"))
        assertEquals(MidiMessageType.PITCH_BEND, MidiMessageType.valueOf("PITCH_BEND"))
    }

    @Test
    fun testMidiEventCreationAndStorage() {
        val event = MidiEvent(
            channel = 0,
            type = MidiMessageType.NOTE,
            index = 60,
            rawValue = 100,
            normalizedValue = 100f / 127f
        )
        assertEquals(0, event.channel)
        assertEquals(MidiMessageType.NOTE, event.type)
        assertEquals(60, event.index)
        assertEquals(100, event.rawValue)
        assertTrue(event.normalizedValue > 0.78f && event.normalizedValue < 0.79f)
    }

    @Test
    fun testNormalizedValueAccessors() {
        llm.slop.liquidlsd.ui.UITheme.midiEnabled = true
        val valCc = MidiEngine.getNormalizedValue(0, MidiMessageType.CC, 10)
        assertEquals(0.0f, valCc)

        val valNote = MidiEngine.getNormalizedValue(0, MidiMessageType.NOTE, 60)
        assertEquals(0.0f, valNote)

        val valPb = MidiEngine.getNormalizedValue(0, MidiMessageType.PITCH_BEND, 0)
        assertEquals(0.0f, valPb)
    }

    // --- MIDI Profiles & Path Security ---

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
            llm.slop.liquidlsd.ui.AppPreferencesStore.loadPreferences()

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

            llm.slop.liquidlsd.ui.AppPreferencesStore.loadPreferences()
        }
    }

    // --- Rotary Encoders & Delta Decoding ---

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
        assertNotNull(mapping)
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

    @Test
    fun testGlobalSnapDeckActions() {
        val mixer = io.mockk.mockk<llm.slop.liquidlsd.rendering.Mixer>(relaxed = true)
        val state = llm.slop.liquidlsd.ui.ParametersState()

        MidiMappingManager.loadProfile("global_snap_test")
        try {
            MidiMappingManager.addMapping("Global/snapDeckA", cc = 50, channel = 0)
            MidiMappingManager.addMapping("Global/snapDeckB", cc = 51, channel = 0)

            // Trigger snapDeckA
            MidiEngine.receivedEvents.offer(
                MidiEvent(channel = 0, type = MidiMessageType.CC, index = 50, rawValue = 127, normalizedValue = 1.0f)
            )
            MidiMappingManager.processGlobalMidiEvents(
                midiEnabled = true,
                parametersState = state,
                mixer = mixer,
                onTapTempo = {}
            )

            io.mockk.verify { mixer.onCrossfadeManualTakeover() }
            io.mockk.verify { mixer.crossfade.set(-1.0f) }

            // Trigger snapDeckB
            MidiEngine.receivedEvents.offer(
                MidiEvent(channel = 0, type = MidiMessageType.CC, index = 51, rawValue = 127, normalizedValue = 1.0f)
            )
            MidiMappingManager.processGlobalMidiEvents(
                midiEnabled = true,
                parametersState = state,
                mixer = mixer,
                onTapTempo = {}
            )

            io.mockk.verify { mixer.crossfade.set(1.0f) }
        } finally {
            MidiMappingManager.clearAllMappings()
            MidiMappingManager.deleteProfile("global_snap_test")
            MidiMappingManager.loadProfile("default")
            io.mockk.unmockkAll()
        }
    }

    @Test
    fun testGlobalAutoFadeAction() {
        val mixer = io.mockk.mockk<llm.slop.liquidlsd.rendering.Mixer>(relaxed = true)
        io.mockk.every { mixer.isAutoFading } returns false
        io.mockk.every { mixer.crossfade.baseValue } returns 0.5f
        val state = llm.slop.liquidlsd.ui.ParametersState()

        MidiMappingManager.loadProfile("global_autofade_test")
        try {
            MidiMappingManager.addMapping("Global/autoFade", cc = 52, channel = 0)

            MidiEngine.receivedEvents.offer(
                MidiEvent(channel = 0, type = MidiMessageType.CC, index = 52, rawValue = 127, normalizedValue = 1.0f)
            )
            MidiMappingManager.processGlobalMidiEvents(
                midiEnabled = true,
                parametersState = state,
                mixer = mixer,
                onTapTempo = {}
            )

            io.mockk.verify { mixer.targetCrossfade = -1.0f }
            io.mockk.verify { mixer.isAutoFading = true }
            io.mockk.verify { mixer.muteCrossfadeNonMidiCv() }
        } finally {
            MidiMappingManager.clearAllMappings()
            MidiMappingManager.deleteProfile("global_autofade_test")
            MidiMappingManager.loadProfile("default")
            io.mockk.unmockkAll()
        }
    }
}
