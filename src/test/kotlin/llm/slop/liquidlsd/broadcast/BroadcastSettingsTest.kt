package llm.slop.liquidlsd.broadcast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BroadcastSettingsTest {

    @Test
    fun testDefaultSettings() {
        val prevUrl = BroadcastSettings.serverUrl
        val prevToken = BroadcastSettings.token
        val prevAuto = BroadcastSettings.autoConnect
        val prevFps = BroadcastSettings.targetFps
        try {
            BroadcastSettings.resetDefaults()
            assertEquals("", BroadcastSettings.serverUrl)
            assertEquals("", BroadcastSettings.token)
            assertFalse(BroadcastSettings.isConfigured)
            assertTrue(BroadcastSettings.targetFps in 5..60)
        } finally {
            BroadcastSettings.serverUrl = prevUrl
            BroadcastSettings.token = prevToken
            BroadcastSettings.autoConnect = prevAuto
            BroadcastSettings.targetFps = prevFps
        }
    }

    @Test
    fun testIsConfigured() {
        try {
            BroadcastSettings.serverUrl = ""
            BroadcastSettings.token = ""
            assertFalse(BroadcastSettings.isConfigured)

            BroadcastSettings.serverUrl = "ws://127.0.0.1:9004"
            BroadcastSettings.token = ""
            assertFalse(BroadcastSettings.isConfigured)

            BroadcastSettings.serverUrl = "   "
            BroadcastSettings.token = "valid-token"
            assertFalse(BroadcastSettings.isConfigured)

            BroadcastSettings.serverUrl = ""
            BroadcastSettings.token = "valid-token"
            assertFalse(BroadcastSettings.isConfigured)

            BroadcastSettings.serverUrl = "ws://127.0.0.1:9004"
            BroadcastSettings.token = "valid-token"
            assertTrue(BroadcastSettings.isConfigured)
        } finally {
            BroadcastSettings.serverUrl = ""
            BroadcastSettings.token = ""
        }
    }

    @Test
    fun testTargetFpsClamping() {
        BroadcastSettings.targetFps = 100
        BroadcastSettings.targetFps = BroadcastSettings.targetFps.coerceIn(5, 60)
        assertEquals(60, BroadcastSettings.targetFps)

        BroadcastSettings.targetFps = 2
        BroadcastSettings.targetFps = BroadcastSettings.targetFps.coerceIn(5, 60)
        assertEquals(5, BroadcastSettings.targetFps)

        BroadcastSettings.targetFps = 25
    }
}
