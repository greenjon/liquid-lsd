package llm.slop.liquidlsd.audio

import llm.slop.liquidlsd.ui.UITheme
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioEngineSettingsTest {

    private val settingsFile = File("lsd-settings.properties")
    private var originalBackup: String? = null

    @BeforeEach
    fun setUp() {
        if (settingsFile.exists()) {
            originalBackup = settingsFile.readText()
            settingsFile.delete()
        }
    }

    @AfterEach
    fun tearDown() {
        if (originalBackup != null) {
            settingsFile.writeText(originalBackup!!)
        } else if (settingsFile.exists()) {
            settingsFile.delete()
        }
    }

    @Test
    fun testAudioEngineSettingsSaveAndLoad() {
        // Configure specific custom audio settings
        UITheme.audioEngineEnabled = false
        AudioEngine.backendMode = AudioEngine.AudioBackendMode.JAVASOUND_ONLY
        AudioEngine.selectedDeviceName = "Custom Test Mic"
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
        UITheme.saveSettings()
        assertTrue(settingsFile.exists(), "Settings file should have been created")

        // Reset to different defaults
        UITheme.audioEngineEnabled = true
        AudioEngine.backendMode = AudioEngine.AudioBackendMode.AUTO
        AudioEngine.selectedDeviceName = null
        AudioEngine.inputGain = 1.0f
        AudioEngine.isBpmLocked = true
        AudioEngine.manualBpm = 120.0f
        AudioEngine.beatDetector.applyPreset(BeatDetectionSettings.highAccuracy())

        // Load settings using reflection or recreating/re-invoking loadSettings
        val loadMethod = UITheme::class.java.getDeclaredMethod("loadSettings")
        loadMethod.isAccessible = true
        loadMethod.invoke(UITheme)

        // Verify all settings were restored correctly
        assertEquals(false, UITheme.audioEngineEnabled)
        assertEquals(AudioEngine.AudioBackendMode.JAVASOUND_ONLY, AudioEngine.backendMode)
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
        // Pre-populate settings file with broadcast and custom properties
        settingsFile.writeText("broadcastServerUrl=wss://example.com/live\nbroadcastAutoConnect=true\n")

        UITheme.saveSettings()

        val savedContent = settingsFile.readText()
        assertTrue(savedContent.contains("broadcastServerUrl=wss\\://example.com/live") || savedContent.contains("broadcastServerUrl=wss://example.com/live"), "Existing broadcast URL should be preserved")
        assertTrue(savedContent.contains("broadcastAutoConnect=true"), "Existing broadcast auto-connect should be preserved")
        assertTrue(savedContent.contains("audioBackend="), "New audio properties should be appended")
    }
}
