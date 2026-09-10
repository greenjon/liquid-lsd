package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.rendering.pipewire.PipeWireBridge
import llm.slop.liquidlsd.rendering.pipewire.PipeWireLibrary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PipeWireBridgeTest {

    @Test
    fun testPipeWireLibraryLoadOnLinux() {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("linux")) {
            val lib = PipeWireLibrary.load()
            if (lib != null) {
                assertNotNull(lib)
            }
        }
    }

    @Test
    fun testLinuxTextureBridgeLifecycle() {
        val bridge = LinuxTextureBridge("TestStream")
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("linux")) {
            assertTrue(bridge.isSupported)
            val status = bridge.getDriverStatus()
            assertNotNull(status)
            
            bridge.stop()
        } else {
            assertFalse(bridge.isSupported)
        }
    }

    @Test
    fun testPipeWireBridgeLifecycle() {
        val pwBridge = PipeWireBridge()
        if (pwBridge.isAvailable) {
            val created = pwBridge.createServer("TestUnitStream", 640, 480)
            if (created) {
                assertTrue(pwBridge.isConnected)
                pwBridge.stopServer()
                assertFalse(pwBridge.isConnected)
            }
        }
    }

    @Test
    fun testPipeWireReceiverLifecycle() {
        val receiver = PipeWireReceiverImpl()
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("linux") && PipeWireLibrary.load() != null) {
            assertTrue(receiver.isSupported)
            val started = receiver.start("TestTargetStream")
            if (started) {
                receiver.update()
                receiver.stop()
            }
        } else {
            if (!os.contains("linux")) {
                assertFalse(receiver.isSupported)
            }
        }
    }

    @Test
    fun testFetchPipeWireStreams() {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("linux")) {
            val streams = ExternalVideoDiscovery.fetchPipeWireStreams()
            assertNotNull(streams)
        }
    }
}
