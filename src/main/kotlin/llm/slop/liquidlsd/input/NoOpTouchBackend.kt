package llm.slop.liquidlsd.input

/**
 * Fallback backend for systems without touchpad access or unsupported platforms.
 */
class NoOpTouchBackend(
    override val state: TouchBackendState = TouchBackendState.NO_DEVICE
) : TouchStripBackend {
    override fun start(): Boolean = false
    override fun setGrabbed(grabbed: Boolean) {}
    override fun stop() {}
}
