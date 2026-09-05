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

class SettingsDefaultsTest {

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
        val loadMethod = UITheme::class.java.getDeclaredMethod("loadSettings")
        loadMethod.isAccessible = true
        loadMethod.invoke(UITheme)
    }

    @Test
    fun testAppSettingsDefaultValues() {
        val defaultSettings = AppSettings()
        assertFalse(defaultSettings.sequencerEnabled, "Sequencer should be disabled by default")
        assertFalse(defaultSettings.randomizationEnabled, "Randomization should be disabled by default")
        assertFalse(defaultSettings.midiEnabled, "MIDI should be disabled by default")
    }

    @Test
    fun testSettingsSaveAndLoadRoundTrip() {
        // Explicitly set non-default values
        UITheme.sequencerEnabled = true
        UITheme.randomizationEnabled = true
        UITheme.midiEnabled = true

        UITheme.saveSettings()
        assertTrue(settingsFile.exists())

        val savedProps = settingsFile.readText()
        assertTrue(savedProps.contains("sequencerEnabled=true"))
        assertTrue(savedProps.contains("randomizationEnabled=true"))
        assertTrue(savedProps.contains("midiEnabled=true"))

        // Reset to false in memory
        UITheme.sequencerEnabled = false
        UITheme.randomizationEnabled = false
        UITheme.midiEnabled = false

        // Reload from file
        val loadMethod = UITheme::class.java.getDeclaredMethod("loadSettings")
        loadMethod.isAccessible = true
        loadMethod.invoke(UITheme)

        // Verify loaded as true
        assertTrue(UITheme.sequencerEnabled)
        assertTrue(UITheme.randomizationEnabled)
        assertTrue(UITheme.midiEnabled)

        // Now save as false
        UITheme.sequencerEnabled = false
        UITheme.randomizationEnabled = false
        UITheme.midiEnabled = false
        UITheme.saveSettings()

        loadMethod.invoke(UITheme)
        assertFalse(UITheme.sequencerEnabled)
        assertFalse(UITheme.randomizationEnabled)
        assertFalse(UITheme.midiEnabled)
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
