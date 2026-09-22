package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File

class UIThemeTest {

    // --- UI Theme Preferences & Scaling ---

    @Test
    fun testPreferencesSaveAndLoadRoundTrip() {
        val preferencesFile = File("lsd-preferences.properties")
        val backupFile = File("lsd-preferences.properties.bak")
        val legacySettingsFile = File("lsd-settings.properties")
        val legacyBackupFile = File("lsd-settings.properties.bak")

        var hadPrefBackup = false
        if (preferencesFile.exists()) {
            preferencesFile.copyTo(backupFile, overwrite = true)
            hadPrefBackup = true
            preferencesFile.delete()
        }

        var hadLegacyBackup = false
        if (legacySettingsFile.exists()) {
            legacySettingsFile.copyTo(legacyBackupFile, overwrite = true)
            hadLegacyBackup = true
            legacySettingsFile.delete()
        }

        try {
            UITheme.presetNameScalePercent = 110
            UITheme.showMidiCol = false
            UITheme.showLfoCol = true
            UITheme.showAudioCol = false
            UITheme.cleanModeEnabled = true
            UITheme.backgroundVideoEnabled = true
            UITheme.tooltipsEnabled = false
            UITheme.maxFps = 60
            UITheme.renderResolutionPreset = UITheme.ResolutionPreset.CUSTOM
            UITheme.customRenderWidth = 1600
            UITheme.customRenderHeight = 1200
            UITheme.outputScaleMode = UITheme.OutputScaleMode.FILL
            UITheme.preferencesWidth = 750f
            UITheme.preferencesHeight = 600f

            // Save to disk
            AppPreferencesStore.savePreferences()
            assertTrue(preferencesFile.exists(), "Preferences file should be written")

            // Reset values to defaults in memory
            UITheme.presetNameScalePercent = 100
            UITheme.showMidiCol = true
            UITheme.showLfoCol = false
            UITheme.showAudioCol = true
            UITheme.cleanModeEnabled = false
            UITheme.backgroundVideoEnabled = false
            UITheme.tooltipsEnabled = true
            UITheme.maxFps = 30
            UITheme.renderResolutionPreset = UITheme.ResolutionPreset.RES_1080P
            UITheme.customRenderWidth = 1920
            UITheme.customRenderHeight = 1080
            UITheme.outputScaleMode = UITheme.OutputScaleMode.FIT
            UITheme.preferencesWidth = 640f
            UITheme.preferencesHeight = 520f

            // Reload preferences
            AppPreferencesStore.loadPreferences()

            // Assert restored values match what was saved
            assertEquals(110, UITheme.presetNameScalePercent)
            assertEquals(14.25f, UITheme.baseSize)
            assertFalse(UITheme.showMidiCol)
            assertTrue(UITheme.showLfoCol)
            assertFalse(UITheme.showAudioCol)
            assertTrue(UITheme.cleanModeEnabled)
            assertTrue(UITheme.backgroundVideoEnabled)
            assertFalse(UITheme.tooltipsEnabled)
            assertEquals(60, UITheme.maxFps)
            assertEquals(UITheme.ResolutionPreset.CUSTOM, UITheme.renderResolutionPreset)
            assertEquals(1600, UITheme.customRenderWidth)
            assertEquals(1200, UITheme.customRenderHeight)
            assertEquals(UITheme.OutputScaleMode.FILL, UITheme.outputScaleMode)
            assertEquals(1600, UITheme.renderWidth)
            assertEquals(1200, UITheme.renderHeight)
            assertEquals(750f, UITheme.preferencesWidth)
            assertEquals(600f, UITheme.preferencesHeight)

        } finally {
            if (hadPrefBackup && backupFile.exists()) {
                backupFile.copyTo(preferencesFile, overwrite = true)
                backupFile.delete()
            } else {
                preferencesFile.delete()
            }

            if (hadLegacyBackup && legacyBackupFile.exists()) {
                legacyBackupFile.copyTo(legacySettingsFile, overwrite = true)
                legacyBackupFile.delete()
            } else {
                legacySettingsFile.delete()
            }

            AppPreferencesStore.loadPreferences()
        }
    }

    @Test
    fun testFallbackLoadingFromLegacySettings() {
        val preferencesFile = File("lsd-preferences.properties")
        val backupFile = File("lsd-preferences.properties.bak")
        val legacySettingsFile = File("lsd-settings.properties")
        val legacyBackupFile = File("lsd-settings.properties.bak")

        var hadPrefBackup = false
        if (preferencesFile.exists()) {
            preferencesFile.copyTo(backupFile, overwrite = true)
            hadPrefBackup = true
            preferencesFile.delete()
        }

        var hadLegacyBackup = false
        if (legacySettingsFile.exists()) {
            legacySettingsFile.copyTo(legacyBackupFile, overwrite = true)
            hadLegacyBackup = true
            legacySettingsFile.delete()
        }

        try {
            legacySettingsFile.writeText(
                """
                maxFps=60
                cleanModeEnabled=true
                settingsWidth=820.0
                settingsHeight=620.0
                """.trimIndent()
            )

            UITheme.maxFps = 30
            UITheme.cleanModeEnabled = false
            UITheme.preferencesWidth = 640f
            UITheme.preferencesHeight = 520f

            AppPreferencesStore.loadPreferences()

            assertEquals(60, UITheme.maxFps)
            assertTrue(UITheme.cleanModeEnabled)
            assertEquals(820f, UITheme.preferencesWidth)
            assertEquals(620f, UITheme.preferencesHeight)
        } finally {
            if (hadPrefBackup && backupFile.exists()) {
                backupFile.copyTo(preferencesFile, overwrite = true)
                backupFile.delete()
            } else {
                preferencesFile.delete()
            }

            if (hadLegacyBackup && legacyBackupFile.exists()) {
                legacyBackupFile.copyTo(legacySettingsFile, overwrite = true)
                legacyBackupFile.delete()
            } else {
                legacySettingsFile.delete()
            }

            AppPreferencesStore.loadPreferences()
        }
    }

    // --- Color Tuner & Palettes ---

    @Test
    fun testColorTunerPanelOpenToggle() {
        ColorTunerPanel.isOpen = false
        ColorTunerPanel.open()
        assertTrue(ColorTunerPanel.isOpen)
        ColorTunerPanel.close()
        assertFalse(ColorTunerPanel.isOpen)
        ColorTunerPanel.open()
        assertTrue(ColorTunerPanel.isOpen)
        ColorTunerPanel.toggle()
        assertFalse(ColorTunerPanel.isOpen)
        ColorTunerPanel.toggle()
        assertTrue(ColorTunerPanel.isOpen)
    }

    @Test
    fun testColorTunerSwatchHexCalculation() {
        val swatch = ColorTunerPanel.Swatch.fromHex("test", "Test", "#FF007F")
        assertEquals("#ff007f", swatch.hex)
        assertEquals(1.0f, swatch.r)
        assertEquals(0.0f, swatch.g)
    }

    @Test
    fun testSolarizedPaletteExactHexCodes() {
        val expected = mapOf(
            "base03" to "#002b36",
            "base02" to "#073642",
            "base01" to "#586e75",
            "base00" to "#657b83",
            "base0"  to "#839496",
            "base1"  to "#93a1a1",
            "base2"  to "#eee8d5",
            "base3"  to "#fdf6e3",
            "red"    to "#dc322f",
            "orange" to "#cb4b16",
            "yellow" to "#b58900",
            "green"  to "#859900",
            "cyan"   to "#2aa198",
            "blue"   to "#268bd2",
            "violet" to "#6c71c4",
            "magenta" to "#d33682"
        )
        val palette = ColorTunerPanel.PALETTES.find { it.theme == UITheme.Theme.DARK_SOLARIZED }
        assertNotNull(palette)
        assertEquals(16, palette.swatches.size)
        expected.forEach { (id, hex) ->
            val swatch = palette.swatches.find { it.id == id }
            assertNotNull(swatch, "Swatch $id must exist")
            assertEquals(hex, swatch.hex)
        }
    }

    @Test
    fun testLunarizedPaletteExactHexCodes() {
        val expected = mapOf(
            "base03" to "#360b00",
            "base02" to "#421307",
            "base01" to "#755f58",
            "base00" to "#836d65",
            "base0"  to "#968583",
            "base1"  to "#a19393",
            "base2"  to "#d5dbee",
            "base3"  to "#e3eafd",
            "cyan"   to "#23cdd0",
            "blue"   to "#34b4e9",
            "indigo" to "#4a76ff",
            "violet" to "#7a66ff",
            "red"    to "#d55e67",
            "orange" to "#d9742d",
            "yellow" to "#938e3b",
            "green"  to "#2cc97d"
        )
        val palette = ColorTunerPanel.PALETTES.find { it.theme == UITheme.Theme.DARK_LUNARIZED }
        assertNotNull(palette)
        assertEquals(16, palette.swatches.size)
        expected.forEach { (id, hex) ->
            val swatch = palette.swatches.find { it.id == id }
            assertNotNull(swatch, "Swatch $id must exist")
            assertEquals(hex, swatch.hex)
        }
    }

    // --- UI Layout & Component Elements ---

    @Test
    fun testColumn3ModeDefaultAndToggle() {
        // Check the real default via a fresh AppPreferences instance rather than the
        // live UITheme singleton: UITheme is a mutable global shared across every test
        // class in this JVM, so asserting on its ambient value is order-dependent on
        // whatever ran before it (and on any real lsd-preferences.properties a test
        // reloaded from disk).
        assertEquals(UITheme.Column3Mode.MIXER, AppPreferences().column3Mode)

        val original = UITheme.column3Mode
        try {
            UITheme.column3Mode = UITheme.Column3Mode.MACROS
            assertEquals(UITheme.Column3Mode.MACROS, UITheme.column3Mode)
            UITheme.column3Mode = UITheme.Column3Mode.MIXER
            assertEquals(UITheme.Column3Mode.MIXER, UITheme.column3Mode)
        } finally {
            UITheme.column3Mode = original
        }
    }
    fun testFontSizeBoundaries() {
        // Preset name scale range: 80%–120%
        UITheme.presetNameScalePercent = 50 // below min
        assertEquals(80, UITheme.presetNameScalePercent)

        UITheme.presetNameScalePercent = 250 // above max
        assertEquals(120, UITheme.presetNameScalePercent)

        // 100% default
        UITheme.presetNameScalePercent = 100
        assertEquals(100, UITheme.presetNameScalePercent)

        // 110% user scale
        UITheme.presetNameScalePercent = 110
        assertEquals(110, UITheme.presetNameScalePercent)

        // Snaps to multiples of 10
        UITheme.presetNameScalePercent = 104
        assertEquals(100, UITheme.presetNameScalePercent)
        UITheme.presetNameScalePercent = 106
        assertEquals(110, UITheme.presetNameScalePercent)

        // UITheme semantic font size constants
        assertEquals(12f, UITheme.FONT_CAPTION)
        assertEquals(14f, UITheme.FONT_BODY)
        assertEquals(14f, UITheme.FONT_CODE)
        assertEquals(15f, UITheme.FONT_H3)
        assertEquals(18f, UITheme.FONT_H2)
        assertEquals(22f, UITheme.FONT_H1)
        assertEquals(14.25f, UITheme.BASE_SIZE)
        assertEquals(14.25f, UITheme.baseSize)

        // Reset to default
        UITheme.presetNameScalePercent = 100
    }

    @Test
    fun testWaveShapeEnumEntries() {
        assertEquals(
            listOf("SINE", "RAMP_UP", "RAMP_DOWN", "TRIANGLE", "SQUARE", "RANDOM", "SQUARE_10", "SQUARE_90"),
            WaveShape.entries.map { it.name }
        )
    }

    @Test
    fun testPreferencesCategories() {
        val categories = PreferencesPanel.Category.values()
        val expectedOrder = listOf(
            PreferencesPanel.Category.GENERAL,
            PreferencesPanel.Category.SHADER_LOCATIONS,
            PreferencesPanel.Category.VIDEO_DISPLAY,
            PreferencesPanel.Category.AUDIO_ENGINE,
            PreferencesPanel.Category.TEMPO_SYNC,
            PreferencesPanel.Category.MIDI_CONTROLLER,
            PreferencesPanel.Category.OSC_CONTROLLER,
            PreferencesPanel.Category.SHORTCUTS,
            PreferencesPanel.Category.BROADCAST
        )
        assertEquals(expectedOrder, categories.toList())
        assertEquals("Keyboard Shortcuts", PreferencesPanel.Category.SHORTCUTS.label)
        assertEquals("Shader Locations", PreferencesPanel.Category.SHADER_LOCATIONS.label)
    }

    @Test
    fun testImGuiKeys() {
        val fields = imgui.flag.ImGuiKey::class.java.fields
        assertTrue(fields.isNotEmpty(), "ImGuiKey fields should not be empty")
    }
}
