package llm.slop.liquidlsd.broadcast

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BroadcastPreferencesTest {

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
    }

    @Test
    fun testDefaultPreferences() {
        val prevUrl = BroadcastPreferences.serverUrl
        val prevToken = BroadcastPreferences.token
        val prevAuto = BroadcastPreferences.autoConnect
        val prevFps = BroadcastPreferences.targetFps
        try {
            BroadcastPreferences.resetDefaults()
            assertEquals("", BroadcastPreferences.serverUrl)
            assertEquals("", BroadcastPreferences.token)
            assertFalse(BroadcastPreferences.isConfigured)
            assertTrue(BroadcastPreferences.targetFps in 5..60)
        } finally {
            BroadcastPreferences.serverUrl = prevUrl
            BroadcastPreferences.token = prevToken
            BroadcastPreferences.autoConnect = prevAuto
            BroadcastPreferences.targetFps = prevFps
        }
    }

    @Test
    fun testIsConfigured() {
        try {
            BroadcastPreferences.serverUrl = ""
            BroadcastPreferences.token = ""
            assertFalse(BroadcastPreferences.isConfigured)

            BroadcastPreferences.serverUrl = "ws://127.0.0.1:9004"
            BroadcastPreferences.token = ""
            assertFalse(BroadcastPreferences.isConfigured)

            BroadcastPreferences.serverUrl = "   "
            BroadcastPreferences.token = "valid-token"
            assertFalse(BroadcastPreferences.isConfigured)

            BroadcastPreferences.serverUrl = ""
            BroadcastPreferences.token = "valid-token"
            assertFalse(BroadcastPreferences.isConfigured)

            BroadcastPreferences.serverUrl = "ws://127.0.0.1:9004"
            BroadcastPreferences.token = "valid-token"
            assertTrue(BroadcastPreferences.isConfigured)
        } finally {
            BroadcastPreferences.serverUrl = ""
            BroadcastPreferences.token = ""
        }
    }

    @Test
    fun testTargetFpsClamping() {
        BroadcastPreferences.targetFps = 100
        BroadcastPreferences.targetFps = BroadcastPreferences.targetFps.coerceIn(5, 60)
        assertEquals(60, BroadcastPreferences.targetFps)

        BroadcastPreferences.targetFps = 2
        BroadcastPreferences.targetFps = BroadcastPreferences.targetFps.coerceIn(5, 60)
        assertEquals(5, BroadcastPreferences.targetFps)

        BroadcastPreferences.targetFps = 25
    }

    @Test
    fun testSaveAndLoadPreferences() {
        val prevUrl = BroadcastPreferences.serverUrl
        val prevToken = BroadcastPreferences.token
        val prevAuto = BroadcastPreferences.autoConnect
        val prevFps = BroadcastPreferences.targetFps

        try {
            BroadcastPreferences.serverUrl = "wss://relay.example.com"
            BroadcastPreferences.token = "secret123"
            BroadcastPreferences.autoConnect = true
            BroadcastPreferences.targetFps = 45

            BroadcastPreferences.savePreferences()
            assertTrue(preferencesFile.exists(), "Preferences file should be written")

            BroadcastPreferences.resetDefaults()
            assertEquals("", BroadcastPreferences.serverUrl)

            BroadcastPreferences.loadPreferences()
            assertEquals("wss://relay.example.com", BroadcastPreferences.serverUrl)
            assertEquals("secret123", BroadcastPreferences.token)
            assertTrue(BroadcastPreferences.autoConnect)
            assertEquals(45, BroadcastPreferences.targetFps)
        } finally {
            BroadcastPreferences.serverUrl = prevUrl
            BroadcastPreferences.token = prevToken
            BroadcastPreferences.autoConnect = prevAuto
            BroadcastPreferences.targetFps = prevFps
        }
    }

    @Test
    fun testFallbackLoadingFromLegacySettings() {
        legacySettingsFile.writeText(
            """
            broadcastServerUrl=wss://legacy.example.com
            broadcastToken=legacy-tok
            broadcastAutoConnect=true
            broadcastTargetFps=30
            """.trimIndent()
        )

        BroadcastPreferences.resetDefaults()
        BroadcastPreferences.loadPreferences()

        assertEquals("wss://legacy.example.com", BroadcastPreferences.serverUrl)
        assertEquals("legacy-tok", BroadcastPreferences.token)
        assertTrue(BroadcastPreferences.autoConnect)
        assertEquals(30, BroadcastPreferences.targetFps)
    }
}
