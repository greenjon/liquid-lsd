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
    private var originalUIThemePreferences: AppPreferences? = null

    @BeforeEach
    fun setUp() {
        // Snapshot in-memory UITheme state so this test class can never leak whatever
        // was on disk (a real dev's lsd-preferences.properties) into the global
        // UITheme singleton for other test classes sharing this JVM.
        originalUIThemePreferences = UITheme.preferences

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

        // loadPreferences() above may have just pulled whatever was in the restored
        // file (real, possibly non-default local dev settings) into UITheme -- undo
        // that so the singleton is back exactly where it was before this test ran.
        originalUIThemePreferences?.let { UITheme.preferences = it }
    }

    @Test
    fun testAppPreferencesDefaultValues() {
        val defaultPreferences = AppPreferences()
        assertFalse(defaultPreferences.sequencerEnabled, "Sequencer should be disabled by default")
        assertFalse(defaultPreferences.randomizationEnabled, "Randomization should be disabled by default")
        assertFalse(defaultPreferences.midiEnabled, "MIDI should be disabled by default")
        assertTrue(defaultPreferences.checkUpdatesOnStartup, "Update check on startup should be enabled by default")
        assertEquals(UITheme.WorkspaceMode.RACK, defaultPreferences.workspaceMode, "Workspace mode should be Performance (RACK) by default for new users")
    }

    @Test
    fun testPreferencesSaveAndLoadRoundTrip() {
        // Explicitly set non-default values
        UITheme.sequencerEnabled = true
        UITheme.randomizationEnabled = true
        UITheme.midiEnabled = true
        UITheme.workspaceMode = UITheme.WorkspaceMode.CLASSIC

        AppPreferencesStore.savePreferences()
        assertTrue(preferencesFile.exists(), "Preferences file should be written")

        val savedProps = preferencesFile.readText()
        assertTrue(savedProps.contains("sequencerEnabled=true"))
        assertTrue(savedProps.contains("randomizationEnabled=true"))
        assertTrue(savedProps.contains("midiEnabled=true"))
        assertTrue(savedProps.contains("workspaceMode=CLASSIC"))

        // Reset in memory
        UITheme.sequencerEnabled = false
        UITheme.randomizationEnabled = false
        UITheme.midiEnabled = false
        UITheme.workspaceMode = UITheme.WorkspaceMode.RACK

        // Reload from file
        AppPreferencesStore.loadPreferences()

        // Verify loaded from file
        assertTrue(UITheme.sequencerEnabled)
        assertTrue(UITheme.randomizationEnabled)
        assertTrue(UITheme.midiEnabled)
        assertEquals(UITheme.WorkspaceMode.CLASSIC, UITheme.workspaceMode)

        // Now save as alternate
        UITheme.sequencerEnabled = false
        UITheme.randomizationEnabled = false
        UITheme.midiEnabled = false
        UITheme.workspaceMode = UITheme.WorkspaceMode.RACK
        AppPreferencesStore.savePreferences()

        AppPreferencesStore.loadPreferences()
        assertFalse(UITheme.sequencerEnabled)
        assertFalse(UITheme.randomizationEnabled)
        assertFalse(UITheme.midiEnabled)
        assertEquals(UITheme.WorkspaceMode.RACK, UITheme.workspaceMode)
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
            workspaceMode=CLASSIC
            """.trimIndent()
        )

        UITheme.sequencerEnabled = false
        UITheme.randomizationEnabled = false
        UITheme.midiEnabled = false
        UITheme.workspaceMode = UITheme.WorkspaceMode.RACK

        AppPreferencesStore.loadPreferences()

        assertTrue(UITheme.sequencerEnabled, "Should load sequencerEnabled from legacy settings")
        assertTrue(UITheme.randomizationEnabled, "Should load randomizationEnabled from legacy settings")
        assertTrue(UITheme.midiEnabled, "Should load midiEnabled from legacy settings")
        assertEquals(UITheme.WorkspaceMode.CLASSIC, UITheme.workspaceMode, "Should load workspaceMode from legacy settings")
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
