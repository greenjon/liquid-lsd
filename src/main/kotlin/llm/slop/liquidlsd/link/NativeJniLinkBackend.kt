package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.utils.NativeLibraryLoader
import mu.KotlinLogging

/**
 * Ableton Link through the bundled `link_jni` library (src/main/cpp/link_jni).
 * Not realtime-safe: never call this from the audio thread.
 */
class NativeJniLinkBackend : LinkBackend {
    private val logger = KotlinLogging.logger {}
    override val name: String = "Native (ableton::Link)"

    private var handle: Long = 0L

    private object NativeLinkBindings {
        external fun nInit(initialBpm: Double): Long
        external fun nFree(handle: Long)
        external fun nSetEnabled(handle: Long, enabled: Boolean)
        external fun nIsEnabled(handle: Long): Boolean
        external fun nGetNumPeers(handle: Long): Int
        external fun nClockMicros(handle: Long): Long
        external fun nGetTempo(handle: Long): Double
        external fun nSetTempo(handle: Long, bpm: Double, timeUs: Long)
        external fun nGetBeatAtTime(handle: Long, timeUs: Long, quantum: Double): Double
        external fun nRequestBeatAtTime(handle: Long, beat: Double, timeUs: Long, quantum: Double)
        external fun nForceBeatAtTime(handle: Long, beat: Double, timeUs: Long, quantum: Double)
    }

    override fun init(initialBpm: Double): Boolean {
        if (handle != 0L) return true
        if (!NativeLibraryLoader.loadLibrary("link_jni")) {
            logger.warn { "Could not load the 'link_jni' native library; Ableton Link is unavailable." }
            return false
        }
        return try {
            handle = NativeLinkBindings.nInit(initialBpm)
            handle != 0L
        } catch (e: Throwable) {
            logger.warn(e) { "Could not create the native Link instance" }
            false
        }
    }

    override fun close() {
        if (handle != 0L) {
            val h = handle
            handle = 0L
            NativeLinkBindings.nFree(h)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        if (handle != 0L) NativeLinkBindings.nSetEnabled(handle, enabled)
    }

    override fun isEnabled(): Boolean = handle != 0L && NativeLinkBindings.nIsEnabled(handle)

    override fun getNumPeers(): Int = if (handle != 0L) NativeLinkBindings.nGetNumPeers(handle) else 0

    override fun clockMicros(): Long = if (handle != 0L) NativeLinkBindings.nClockMicros(handle) else 0L

    override fun getTempo(): Double = if (handle != 0L) NativeLinkBindings.nGetTempo(handle) else 120.0

    override fun setTempo(bpm: Double) {
        if (handle != 0L) NativeLinkBindings.nSetTempo(handle, bpm.coerceIn(20.0, 999.0), clockMicros())
    }

    override fun getBeatAtTime(timeUs: Long, quantum: Double): Double =
        if (handle != 0L) NativeLinkBindings.nGetBeatAtTime(handle, timeUs, quantum) else 0.0

    override fun requestBeatAtTime(beat: Double, timeUs: Long, quantum: Double) {
        if (handle != 0L) NativeLinkBindings.nRequestBeatAtTime(handle, beat, timeUs, quantum)
    }

    override fun forceBeatAtTime(beat: Double, timeUs: Long, quantum: Double) {
        if (handle != 0L) NativeLinkBindings.nForceBeatAtTime(handle, beat, timeUs, quantum)
    }
}
