package llm.slop.liquidlsd.utils

import org.lwjgl.glfw.GLFW

/**
 * Centralized time source for real-time rendering and deterministic offline export.
 *
 * During live execution, delegates to system/GLFW clocks.
 * During offline rendering (OfflineRenderStudio), provides deterministic simulated time
 * and frame delta to ensure perfect audio/visual synchronization.
 */
object TimeSource {

    @Volatile
    var isSimulated: Boolean = false
        private set

    @Volatile
    private var simulatedTimeSec: Double = 0.0

    @Volatile
    private var simulatedDeltaTimeSec: Double = 1.0 / 60.0

    /**
     * Current time in seconds.
     * Returns simulated timeline time during offline export, or GLFW time during live rendering.
     */
    fun getTimeSec(): Double {
        return if (isSimulated) simulatedTimeSec else GLFW.glfwGetTime()
    }

    /**
     * Current time in nanoseconds.
     * Returns simulated nanoseconds during offline export, or system nanoseconds during live rendering.
     */
    fun getTimeNanos(): Long {
        return if (isSimulated) (simulatedTimeSec * 1_000_000_000.0).toLong() else System.nanoTime()
    }

    /**
     * Wall-clock epoch milliseconds (DATE / iDate uniforms). Simulated runs get a fixed epoch plus
     * the simulated time so renders do not depend on the day they were made.
     */
    fun getEpochMillis(): Long {
        return if (isSimulated) SIMULATED_EPOCH_MS + (simulatedTimeSec * 1000.0).toLong() else System.currentTimeMillis()
    }

    /** 2026-01-01T12:00:00Z */
    private const val SIMULATED_EPOCH_MS = 1_767_268_800_000L

    /**
     * Current frame delta time in seconds.
     */
    fun getDeltaTimeSec(): Double {
        return if (isSimulated) simulatedDeltaTimeSec else (1.0 / 60.0)
    }

    /**
     * Enables simulated time for deterministic offline rendering.
     */
    fun setSimulatedTime(timeSec: Double, dtSec: Double = 1.0 / 60.0) {
        simulatedTimeSec = timeSec
        simulatedDeltaTimeSec = dtSec
        isSimulated = true
    }

    /**
     * Clears simulated time and restores live wall-clock operation.
     */
    fun clearSimulatedTime() {
        isSimulated = false
        simulatedTimeSec = 0.0
    }
}
