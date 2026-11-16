package llm.slop.liquidlsd.input

import mu.KotlinLogging
import java.util.concurrent.ConcurrentLinkedQueue

private val logger = KotlinLogging.logger {}

/**
 * macOS Cocoa multi-touch trackpad backend: a stub. Nothing installs the Cocoa touch hook yet, so
 * [onNativeCocoaTouch] is never called; the backend reports [TouchBackendState.DISABLED] ("not supported on
 * macOS") rather than READY, so Preferences does not offer a console that would receive no touches.
 * When a real hook exists it would take indirect touch events from the GLFW window's NSView and dispatch
 * normalized (0.0..1.0) coordinates into the thread-safe event queue.
 */
class MacCocoaTouchBackend(
    private val windowHandle: Long,
    private val eventQueue: ConcurrentLinkedQueue<TouchConsoleEvent>
) : TouchStripBackend {

    override var state: TouchBackendState = TouchBackendState.NO_DEVICE
        private set

    private var isStarted = false

    init {
        // Not READY even on macOS until start() installs a real Cocoa touch hook (see the class comment).
        val os = System.getProperty("os.name", "").lowercase()
        state = if (os.contains("mac")) TouchBackendState.DISABLED else TouchBackendState.NO_DEVICE
    }

    override fun start(): Boolean {
        if (state != TouchBackendState.READY) return false
        if (isStarted) return true

        try {
            // LWJGL provides glfwGetCocoaWindow on macOS
            // org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow(windowHandle)
            logger.info { "Initializing macOS Cocoa indirect trackpad touch listener on window $windowHandle" }
            // Cocoa touch hook setup
            isStarted = true
            return true
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to initialize macOS Cocoa touch backend" }
            state = TouchBackendState.NO_DEVICE
            return false
        }
    }

    override fun setGrabbed(grabbed: Boolean) {
        // macOS handles indirect touches without mouse cursor hijacking when acceptsTouchEvents is true
    }

    override fun stop() {
        isStarted = false
    }

    /**
     * Called from the native Cocoa touch listener to dispatch indirect touch events.
     */
    fun onNativeCocoaTouch(touchId: Long, phase: Int, normX: Float, normY: Float) {
        // Cocoa phases: 0 = Began, 1 = Moved, 2 = Stationary, 3 = Ended, 4 = Cancelled
        when (phase) {
            0 -> eventQueue.add(TouchConsoleEvent.TouchDown(touchId, normX, normY))
            1 -> eventQueue.add(TouchConsoleEvent.TouchMove(touchId, normX, normY))
            3, 4 -> eventQueue.add(TouchConsoleEvent.TouchUp(touchId))
        }
    }
}
