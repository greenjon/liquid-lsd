package llm.slop.liquidlsd.ui

import imgui.ImGui
import java.lang.management.ManagementFactory

/**
 * Collects lightweight per-frame performance statistics for display in the menu bar.
 *
 * All reads are zero-allocation and safe to call from the main render thread every frame.
 *
 * - FPS / frame time : sourced from ImGui's own rolling average (updated by ImGui internally).
 * - CPU %            : process CPU load via OperatingSystemMXBean (JDK, all platforms). Cached
 *                      and updated once per second to prevent flickering and reduce query overhead.
 * - BPM              : sourced from AudioEngine when the audio engine is active.
 *
 * GPU utilization is intentionally omitted — there is no portable, zero-dependency API for
 * GPU % across Linux/Windows/macOS + Intel/AMD/Apple Silicon without a native library.
 */
object PerformanceStats {

    // OperatingSystemMXBean is available on every JVM platform.
    // The com.sun.management sub-interface exposes processCpuLoad(); we access it via the
    // standard javax.management cast so the code still compiles cleanly on all vendors.
    private val osMxBean = ManagementFactory.getOperatingSystemMXBean()
    private val sunOsMxBean: com.sun.management.OperatingSystemMXBean? =
        osMxBean as? com.sun.management.OperatingSystemMXBean

    private var lastCpuUpdateTime = 0L
    private var cachedProcessCpuFraction = 0.0
    private var cachedSystemCpuFraction = 0.0

    // Update CPU utilization once per second to make it readable and reduce query overhead
    private const val UPDATE_INTERVAL_MS = 1000L

    private fun updateCpuIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCpuUpdateTime >= UPDATE_INTERVAL_MS) {
            cachedProcessCpuFraction = sunOsMxBean?.processCpuLoad ?: -1.0
            cachedSystemCpuFraction = sunOsMxBean?.cpuLoad ?: -1.0
            lastCpuUpdateTime = now
        }
    }

    /** Frames per second as a rolling average maintained by ImGui (no extra bookkeeping). */
    val fps: Float
        get() = ImGui.getIO().framerate

    val frameTimeNanos = java.util.concurrent.atomic.AtomicLong(0L)

    /** Frame time in milliseconds (from GL timer query), useful as a GPU-load proxy. */
    val frameTimeMs: Float
        get() = frameTimeNanos.get() / 1_000_000f

    /** Audio callback time in milliseconds. */
    val audioCallbackMs: Float
        get() = llm.slop.liquidlsd.audio.AudioEngine.getCallbackLatencyNanos() / 1_000_000f

    /**
     * Fraction [0..1] of CPU time consumed by this JVM process, or -1 if unavailable.
     * Returns a value ≥ 0 on Linux, Windows, and macOS (Intel + ARM) when running on
     * a JDK that exposes com.sun.management.OperatingSystemMXBean.
     */
    val processCpuFraction: Double
        get() {
            updateCpuIfNeeded()
            return cachedProcessCpuFraction
        }

    /**
     * Fraction [0..1] of total system CPU utilisation, or -1 if unavailable.
     */
    val systemCpuFraction: Double
        get() {
            updateCpuIfNeeded()
            return cachedSystemCpuFraction
        }

    /** Current BPM estimate from the audio engine (120 when not active). */
    val bpm: Float
        get() = llm.slop.liquidlsd.audio.AudioEngine.getEstimatedBpm()
}
