package llm.slop.liquidlsd.input

/**
 * Low-latency touch event representation emitted by native backends
 * (Linux evdev or macOS Cocoa) into the thread-safe queue.
 */
sealed class TouchConsoleEvent {
    data class TouchDown(val id: Long, val x: Float, val y: Float) : TouchConsoleEvent()
    data class TouchMove(val id: Long, val x: Float, val y: Float) : TouchConsoleEvent()
    data class TouchUp(val id: Long) : TouchConsoleEvent()
    object TouchReset : TouchConsoleEvent()
}
