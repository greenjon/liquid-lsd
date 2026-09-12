package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.midi.MidiEngine
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ModulationOperator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferencesDefaultsTest {

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

        UITheme.loadPreferences()
    }

    @Test
    fun testAppPreferencesDefaultValues() {
        val defaultPreferences = AppPreferences()
        assertFalse(defaultPreferences.sequencerEnabled, "Sequencer should be disabled by default")
        assertFalse(defaultPreferences.randomizationEnabled, "Randomization should be disabled by default")
        assertFalse(defaultPreferences.midiEnabled, "MIDI should be disabled by default")
        assertTrue(defaultPreferences.checkUpdatesOnStartup, "Update check on startup should be enabled by default")
    }

    @Test
    fun testPreferencesSaveAndLoadRoundTrip() {
        // Explicitly set non-default values
        UITheme.sequencerEnabled = true
        UITheme.randomizationEnabled = true
        UITheme.midiEnabled = true

        UITheme.savePreferences()
        assertTrue(preferencesFile.exists(), "Preferences file should be written")

        val savedProps = preferencesFile.readText()
        assertTrue(savedProps.contains("sequencerEnabled=true"))
        assertTrue(savedProps.contains("randomizationEnabled=true"))
        assertTrue(savedProps.contains("midiEnabled=true"))

        // Reset to false in memory
        UITheme.sequencerEnabled = false
        UITheme.randomizationEnabled = false
        UITheme.midiEnabled = false

        // Reload from file
        UITheme.loadPreferences()

        // Verify loaded as true
        assertTrue(UITheme.sequencerEnabled)
        assertTrue(UITheme.randomizationEnabled)
        assertTrue(UITheme.midiEnabled)

        // Now save as false
        UITheme.sequencerEnabled = false
        UITheme.randomizationEnabled = false
        UITheme.midiEnabled = false
        UITheme.savePreferences()

        UITheme.loadPreferences()
        assertFalse(UITheme.sequencerEnabled)
        assertFalse(UITheme.randomizationEnabled)
        assertFalse(UITheme.midiEnabled)
    }

    @Test
    fun testFallbackLoadingFromLegacySettings() {
        // Ensure no preferences file exists, but legacy settings file does
        if (preferencesFile.exists()) preferencesFile.delete()
        legacySettingsFile.writeText(
            """
            sequencerEnabled=true
            randomizationEnabled=true
            midiEnabled=true
            """.trimIndent()
        )

        UITheme.sequencerEnabled = false
        UITheme.randomizationEnabled = false
        UITheme.midiEnabled = false

        UITheme.loadPreferences()

        assertTrue(UITheme.sequencerEnabled, "Should load sequencerEnabled from legacy settings")
        assertTrue(UITheme.randomizationEnabled, "Should load randomizationEnabled from legacy settings")
        assertTrue(UITheme.midiEnabled, "Should load midiEnabled from legacy settings")
    }

    @Test
    fun testMidiDisabledSuppressesInputsAndModulation() {
        UITheme.midiEnabled = false

        // CC value access returns 0.0f
        assertEquals(0.0f, MidiEngine.getCcValue(0, 10))
        assertEquals(0.0f, CVRegistry.get("midi_cc_0_10"))
        assertEquals(0, MidiEngine.getActiveDeviceCount())

        // Parameter modulation with MIDI CC source is skipped
        val param = ModulatableParameter(baseValue = 0.7f, minClamp = 0.0f, maxClamp = 1.0f)
        val mod = CvModulator(
            sourceId = "midi_cc_0_10",
            depth = 1.0f,
            dcOffset = 0.5f,
            operator = ModulationOperator.ADD
        )
        param.modulators.add(mod)

        assertEquals(0.7f, param.evaluate(), 0.001f)
    }

    @Test
    fun testSequencerDisabledSuppressesModulation() {
        UITheme.sequencerEnabled = false

        // CVRegistry returns 0.0f
        assertEquals(0.0f, CVRegistry.get("seq"), 0.001f)

        // Parameter modulation with seq source is skipped
        val param = ModulatableParameter(baseValue = 0.35f, minClamp = 0.0f, maxClamp = 1.0f)
        val mod = CvModulator(
            sourceId = "seq",
            seqSteps = listOf(0.9f),
            depth = 1.0f,
            operator = ModulationOperator.ADD
        )
        param.modulators.add(mod)

        assertEquals(0.35f, param.evaluate(), 0.001f)
    }
}
