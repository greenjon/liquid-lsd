package llm.slop.liquidlsd.audio

import llm.slop.liquidlsd.ui.AppPreferencesStore
import llm.slop.liquidlsd.ui.UITheme
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioEnginePreferencesTest {

    private val preferencesFile = File("lsd-preferences.properties")
    private val legacySettingsFile = File("lsd-settings.properties")
    private var originalPrefBackup: String? = null
    private var originalSettingsBackup: String? = null

    @BeforeEach
    fun setUp() {
        if (preferencesFile.exists()) {
            originalPrefBackup = preferencesFile.readText()
            preferencesFile.delete()
        }
        if (legacySettingsFile.exists()) {
            originalSettingsBackup = legacySettingsFile.readText()
            legacySettingsFile.delete()
        }
    }

    @AfterEach
    fun tearDown() {
        if (originalPrefBackup != null) {
            preferencesFile.writeText(originalPrefBackup!!)
        } else if (preferencesFile.exists()) {
            preferencesFile.delete()
        }

        if (originalSettingsBackup != null) {
            legacySettingsFile.writeText(originalSettingsBackup!!)
        } else if (legacySettingsFile.exists()) {
            legacySettingsFile.delete()
        }

        AppPreferencesStore.loadPreferences()
    }

    @Test
    fun testAudioEnginePreferencesSaveAndLoad() {
        // Configure specific custom audio settings
        UITheme.audioEngineEnabled = false
        AudioEngine.backendMode = AudioEngine.AudioBackendMode.JAVASOUND_ONLY
        AudioEngine.selectedDeviceName = "Custom Test Mic"
        AudioEngine.channelRouting = AudioChannelRouting.RIGHT_ONLY
        AudioEngine.inputGain = 3.5f
        AudioEngine.isBpmLocked = false
        AudioEngine.manualBpm = 145.5f
        AudioEngine.beatDetector.applyPreset(
            BeatDetectionSettings(
                target = AudioTarget.HIGH,
                bpmSearchFloor = 60,
                bpmSearchCeiling = 180,
                transitionWeightAlpha = 95.0f,
                trackingInertiaBpmPerBeat = 2.8f
            )
        )

        // Save to file
        AppPreferencesStore.savePreferences()
        assertTrue(preferencesFile.exists(), "Preferences file should have been created")

        // Reset to different defaults
        UITheme.audioEngineEnabled = true
        AudioEngine.backendMode = AudioEngine.AudioBackendMode.AUTO
        AudioEngine.channelRouting = AudioChannelRouting.MIX
        AudioEngine.selectedDeviceName = null
        AudioEngine.inputGain = 1.0f
        AudioEngine.isBpmLocked = true
        AudioEngine.manualBpm = 120.0f
        AudioEngine.beatDetector.applyPreset(BeatDetectionSettings.highAccuracy())

        // Load preferences
        AppPreferencesStore.loadPreferences()

        // Verify all preferences were restored correctly
        assertEquals(false, UITheme.audioEngineEnabled)
        assertEquals(AudioEngine.AudioBackendMode.JAVASOUND_ONLY, AudioEngine.backendMode)
        assertEquals(AudioChannelRouting.RIGHT_ONLY, AudioEngine.channelRouting)
        assertEquals("Custom Test Mic", AudioEngine.selectedDeviceName)
        assertEquals(3.5f, AudioEngine.inputGain, 0.001f)
        assertEquals(false, AudioEngine.isBpmLocked)
        assertEquals(145.5f, AudioEngine.manualBpm, 0.001f)
        assertEquals(AudioTarget.HIGH, AudioEngine.beatDetector.settings.target)
        assertEquals(60, AudioEngine.beatDetector.settings.bpmSearchFloor)
        assertEquals(180, AudioEngine.beatDetector.settings.bpmSearchCeiling)
        assertEquals(95.0f, AudioEngine.beatDetector.settings.transitionWeightAlpha, 0.001f)
        assertEquals(2.8f, AudioEngine.beatDetector.settings.trackingInertiaBpmPerBeat, 0.001f)
    }

    @Test
    fun testPreservesExistingExternalPropertiesWhenSaving() {
        // Pre-populate preferences file with broadcast and custom properties
        preferencesFile.writeText("broadcastServerUrl=wss://example.com/live\nbroadcastAutoConnect=true\n")

        AppPreferencesStore.savePreferences()

        val savedContent = preferencesFile.readText()
        assertTrue(savedContent.contains("broadcastServerUrl=wss\\://example.com/live") || savedContent.contains("broadcastServerUrl=wss://example.com/live"), "Existing broadcast URL should be preserved")
        assertTrue(savedContent.contains("broadcastAutoConnect=true"), "Existing broadcast auto-connect should be preserved")
        assertTrue(savedContent.contains("audioBackend="), "New audio properties should be appended")
    }

    @Test
    fun testManualBpmWhenAudioEngineDisabled() {
        UITheme.audioEngineEnabled = false
        AudioEngine.stop()

        val initialBeats = llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats()
        AudioEngine.manualBpm = 135.0f
        AudioEngine.setBpmDirectly(135.0f)

        assertEquals(135.0f, AudioEngine.getEstimatedBpm())
        assertEquals(135.0f, llm.slop.liquidlsd.cv.CVRegistry.get("bpm"))
        assertEquals(135.0f, llm.slop.liquidlsd.ui.PerformanceStats.bpm)

        // Beat count must maintain monotonic forward continuity across manual BPM changes
        val afterBeats = llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats()
        assertTrue(afterBeats >= initialBeats, "Beat count should advance monotonically after manual BPM change")

        AppPreferencesStore.savePreferences()

        // Reset
        AudioEngine.manualBpm = 120.0f
        AudioEngine.setBpmDirectly(120.0f)

        AppPreferencesStore.loadPreferences()

        assertEquals(false, UITheme.audioEngineEnabled)
        assertEquals(135.0f, AudioEngine.manualBpm, 0.001f)
        assertEquals(135.0f, AudioEngine.getEstimatedBpm())
    }
}
