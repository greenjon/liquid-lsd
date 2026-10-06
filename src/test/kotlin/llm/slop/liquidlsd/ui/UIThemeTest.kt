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
            UITheme.outputViewEnabled = true
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
            UITheme.outputViewEnabled = false
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
            assertTrue(UITheme.outputViewEnabled)
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
                outputViewEnabled=true
                settingsWidth=820.0
                settingsHeight=620.0
                """.trimIndent()
            )

            UITheme.maxFps = 30
            UITheme.outputViewEnabled = false
            UITheme.preferencesWidth = 640f
            UITheme.preferencesHeight = 520f

            AppPreferencesStore.loadPreferences()

            assertEquals(60, UITheme.maxFps)
            assertTrue(UITheme.outputViewEnabled)
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

    // --- Tango Palette ---

    @Test
    fun testTangoPaletteExactHexCodes() {
        fun assertHex(rgb: FloatArray, hex: Int) {
            assertEquals(((hex shr 16) and 0xFF) / 255f, rgb[0])
            assertEquals(((hex shr 8) and 0xFF) / 255f, rgb[1])
            assertEquals((hex and 0xFF) / 255f, rgb[2])
        }
        assertHex(TangoPalette.ORANGE.normal, 0xF57900)
        assertHex(TangoPalette.SKY_BLUE.normal, 0x3465A4)
        assertHex(TangoPalette.CHOCOLATE.normal, 0xC17D11)
        assertHex(TangoPalette.PLUM.normal, 0x75507B)
        assertHex(TangoPalette.CHAMELEON.normal, 0x73D216)
        assertHex(TangoPalette.BUTTER.normal, 0xEDD400)
        assertHex(TangoPalette.SCARLET_RED.normal, 0xCC0000)
        assertHex(TangoPalette.ALUMINIUM_1.normal, 0xD3D7CF)
        assertHex(TangoPalette.ALUMINIUM_2.normal, 0x555753)
        assertHex(TangoPalette.SYNC_CYAN.normal, 0x06AFDF)
        assertHex(TangoPalette.SYNC_CYAN.bright, 0x34E2E2)
    }

    @Test
    fun testTangoSemanticRolesDefinedForBothThemes() {
        assertTrue(TangoPalette.ALL_ROLES.isNotEmpty())
        for (role in TangoPalette.ALL_ROLES) {
            assertEquals(4, role.dark.size)
            // A role is theme-independent, has explicit light RGBA, or follows an ImGui style slot.
            role.light?.let { assertEquals(4, it.size) }
            assertFalse(role.light != null && role.lightSlot >= 0, "role must not define both light RGBA and a style slot")
            (role.dark + (role.light ?: FloatArray(0))).forEach { assertTrue(it in 0f..1f) }
        }
    }

    // --- UI Layout & Component Elements ---

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
        assertEquals(18f, UITheme.FONT_TOOLTIP)
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
    fun testPreferencesPanelOpenCategory() {
        PreferencesPanel.open(PreferencesPanel.Category.TEMPO_SYNC)
        assertTrue(PreferencesPanel.isOpen, "PreferencesPanel should be open after open()")
        assertEquals(PreferencesPanel.Category.TEMPO_SYNC, PreferencesPanel.activeCategory)

        PreferencesPanel.open(PreferencesPanel.Category.AUDIO_ENGINE)
        assertEquals(PreferencesPanel.Category.AUDIO_ENGINE, PreferencesPanel.activeCategory)

        PreferencesPanel.close()
        assertFalse(PreferencesPanel.isOpen, "PreferencesPanel should be closed after close()")

        PreferencesPanel.toggle(PreferencesPanel.Category.GENERAL)
        assertTrue(PreferencesPanel.isOpen, "PreferencesPanel should be open after toggle from closed")
        assertEquals(PreferencesPanel.Category.GENERAL, PreferencesPanel.activeCategory)

        PreferencesPanel.toggle(PreferencesPanel.Category.GENERAL)
        assertFalse(PreferencesPanel.isOpen, "PreferencesPanel should be closed after toggle with same category")
    }

    @Test
    fun testImGuiKeys() {
        val fields = imgui.flag.ImGuiKey::class.java.fields
        assertTrue(fields.isNotEmpty(), "ImGuiKey fields should not be empty")
    }
}
