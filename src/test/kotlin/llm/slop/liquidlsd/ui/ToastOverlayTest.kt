package llm.slop.liquidlsd.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ToastOverlayTest {
    @Test
    fun aToastIsActiveUntilItExpiresAndTheNewestReplacesTheOlder() {
        ToastOverlay.show("first", durationMs = 1_000L)
        assertEquals("first", ToastOverlay.active())
        ToastOverlay.show("second", durationMs = 1_000L)
        assertEquals("second", ToastOverlay.active())
        assertNull(ToastOverlay.active(System.currentTimeMillis() + 2_000L))
        assertNull(ToastOverlay.active())
    }
}
