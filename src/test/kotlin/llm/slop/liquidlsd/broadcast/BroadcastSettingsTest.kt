package llm.slop.liquidlsd.broadcast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BroadcastSettingsTest {

    @Test
    fun testDefaultSettings() {
        assertTrue(BroadcastSettings.serverUrl.isNotBlank())
        assertTrue(BroadcastSettings.targetFps in 5..60)
        assertEquals("lsd25", BroadcastSettings.token)
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
