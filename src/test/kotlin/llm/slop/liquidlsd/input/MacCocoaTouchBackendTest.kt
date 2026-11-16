package llm.slop.liquidlsd.input

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The macOS backend is a stub with no Cocoa touch hook, so it must not claim to be READY (audit L4-5). */
class MacCocoaTouchBackendTest {
    private fun <T> withOsName(name: String, block: () -> T): T {
        val old = System.getProperty("os.name")
        System.setProperty("os.name", name)
        try { return block() } finally { System.setProperty("os.name", old) }
    }

    @Test
    fun reportsNotSupportedOnMacOs() = withOsName("Mac OS X") {
        val backend = MacCocoaTouchBackend(0L, ConcurrentLinkedQueue())
        assertEquals(TouchBackendState.DISABLED, backend.state)
        assertFalse(backend.start())
        assertEquals(TouchBackendState.DISABLED, backend.state)
    }
}
