package llm.slop.liquidlsd.input

enum class TouchBackendState {
    READY,
    PERMISSION_REQUIRED,
    NO_DEVICE,
    DISABLED
}

/**
 * Common abstraction for platform-specific multi-touch hardware backends.
 */
interface TouchStripBackend {
    val state: TouchBackendState

    /**
     * Attempts to initialize the backend and open the hardware touch device.
     * Returns true if ready, false otherwise.
     */
    fun start(): Boolean

    /**
     * Grabs or releases exclusive ownership of the hardware touchpad.
     * On Linux, this invokes ioctl(fd, EVIOCGRAB, 1/0) to prevent OS cursor drift.
     */
    fun setGrabbed(grabbed: Boolean)

    /**
     * Stops the backend and releases all system resources.
     */
    fun stop()
}
