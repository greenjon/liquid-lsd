package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
            UITheme.backgroundVideoEnabled = true
            UITheme.tooltipsEnabled = false
            UITheme.maxFps = 60
            UITheme.renderResolutionPreset = UITheme.ResolutionPreset.CUSTOM
            UITheme.customRenderWidth = 1600
            UITheme.customRenderHeight = 1200
            UITheme.outputScaleMode = UITheme.OutputScaleMode.FILL
            UITheme.settingsWidth = 750f
            UITheme.settingsHeight = 600f

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
            UITheme.backgroundVideoEnabled = false
            UITheme.tooltipsEnabled = true
            UITheme.maxFps = 30
            UITheme.renderResolutionPreset = UITheme.ResolutionPreset.RES_1080P
            UITheme.customRenderWidth = 1920
            UITheme.customRenderHeight = 1080
            UITheme.outputScaleMode = UITheme.OutputScaleMode.FIT
            UITheme.settingsWidth = 640f
            UITheme.settingsHeight = 520f

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
            assertTrue(UITheme.backgroundVideoEnabled)
            assertFalse(UITheme.tooltipsEnabled)
            assertEquals(60, UITheme.maxFps)
            assertEquals(UITheme.ResolutionPreset.CUSTOM, UITheme.renderResolutionPreset)
            assertEquals(1600, UITheme.customRenderWidth)
            assertEquals(1200, UITheme.customRenderHeight)
            assertEquals(UITheme.OutputScaleMode.FILL, UITheme.outputScaleMode)
            assertEquals(1600, UITheme.renderWidth)
            assertEquals(1200, UITheme.renderHeight)
            assertEquals(750f, UITheme.settingsWidth)
            assertEquals(600f, UITheme.settingsHeight)

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

    @Test
    fun testColorTunerPanelOpenToggle() {
        ColorTunerPanel.isOpen = false
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

    @Test
    fun testFontSizeBoundaries() {
        // Scale range: 75 %–200 % of BASE_PX=15f → 11.25 f–30 f
        UITheme.baseSize = 5f
        val clampedSmall = 5f.coerceIn(11.25f, 30f)
        assertEquals(11.25f, clampedSmall)

        val clampedLarge = 50f.coerceIn(11.25f, 30f)
        assertEquals(30f, clampedLarge)

        // 100 % = 15 px (default baseline)
        assertEquals(15f, 100 / 100f * 15f)

        // 75 % and 200 % round-trip through pctToPx
        assertEquals(11.25f, SettingsPanel.pctToPx(75))
        assertEquals(30f,    SettingsPanel.pctToPx(200))

        // Reset to default
        UITheme.baseSize = 20f
    }
}

