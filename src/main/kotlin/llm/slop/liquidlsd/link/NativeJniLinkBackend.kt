package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.utils.NativeLibraryLoader
import mu.KotlinLogging

/**
 * Ableton Link backend using native C++ JNI bindings (`link_jni`).
 */
class NativeJniLinkBackend : LinkBackend {
    private val logger = KotlinLogging.logger {}
    override val name: String = "Native JNI (ableton::Link)"

    private var nativeHandle: Long = 0L
    @Volatile private var initialized = false

    private object NativeLinkBindings {
        @JNIClass
        external fun nInit(initialBpm: Double): Long
        external fun nFree(handle: Long)
        external fun nSetEnabled(handle: Long, enabled: Boolean)
        external fun nIsEnabled(handle: Long): Boolean
        external fun nGetNumPeers(handle: Long): Int
        external fun nGetTempo(handle: Long): Double
        external fun nSetTempo(handle: Long, bpm: Double, timeUs: Long)
        external fun nGetBeatAtTime(handle: Long, timeUs: Long, quantum: Double): Double
        external fun nGetPhaseAtTime(handle: Long, timeUs: Long, quantum: Double): Double
        external fun nRequestBeatAtTime(handle: Long, beat: Double, timeUs: Long, quantum: Double)
        external fun nSetStartStopSyncEnabled(handle: Long, enabled: Boolean)
        external fun nIsStartStopSyncEnabled(handle: Long): Boolean
        external fun nIsPlaying(handle: Long): Boolean
        external fun nSetIsPlaying(handle: Long, isPlaying: Boolean, timeUs: Long)
    }

    annotation class JNIClass

    override fun init(initialBpm: Double): Boolean {
        if (initialized) return true

        if (!NativeLibraryLoader.loadLibrary("link_jni")) {
            logger.warn { "NativeJniLinkBackend: Failed to load 'link_jni' native library." }
            return false
        }

        try {
            nativeHandle = NativeLinkBindings.nInit(initialBpm)
            if (nativeHandle != 0L) {
                initialized = true
                logger.info { "NativeJniLinkBackend: Successfully initialized native ableton::Link (handle=0x${nativeHandle.toString(16)})" }
                return true
            }
        } catch (e: Throwable) {
            logger.warn(e) { "NativeJniLinkBackend: Exception initializing native Link instance" }
        }

        return false
    }

    override fun close() {
        if (initialized && nativeHandle != 0L) {
            try {
                NativeLinkBindings.nFree(nativeHandle)
            } catch (e: Throwable) {
                logger.warn(e) { "Error freeing native Link handle" }
            }
            nativeHandle = 0L
            initialized = false
        }
    }

    override fun setEnabled(enabled: Boolean) {
        if (!initialized || nativeHandle == 0L) return
        try {
            NativeLinkBindings.nSetEnabled(nativeHandle, enabled)
        } catch (e: Throwable) {
            logger.warn(e) { "Error in nSetEnabled" }
        }
    }

    override fun isEnabled(): Boolean {
        if (!initialized || nativeHandle == 0L) return false
        return try {
            NativeLinkBindings.nIsEnabled(nativeHandle)
        } catch (e: Throwable) {
            false
        }
    }

    override fun getNumPeers(): Int {
        if (!initialized || nativeHandle == 0L) return 0
        return try {
            NativeLinkBindings.nGetNumPeers(nativeHandle)
        } catch (e: Throwable) {
            0
        }
    }

    override fun getTempo(): Double {
        if (!initialized || nativeHandle == 0L) return 120.0
        return try {
            NativeLinkBindings.nGetTempo(nativeHandle)
        } catch (e: Throwable) {
            120.0
        }
    }

    override fun setTempo(bpm: Double) {
        if (!initialized || nativeHandle == 0L) return
        val nowUs = System.nanoTime() / 1000
        try {
            NativeLinkBindings.nSetTempo(nativeHandle, bpm.coerceIn(20.0, 300.0), nowUs)
        } catch (e: Throwable) {
            logger.warn(e) { "Error setting tempo on native Link" }
        }
    }

    override fun getBeatAtTime(timeUs: Long, quantum: Double): Double {
        if (!initialized || nativeHandle == 0L) return 0.0
        return try {
            NativeLinkBindings.nGetBeatAtTime(nativeHandle, timeUs, quantum)
        } catch (e: Throwable) {
            0.0
        }
    }

    override fun getPhaseAtTime(timeUs: Long, quantum: Double): Double {
        if (!initialized || nativeHandle == 0L) return 0.0
        return try {
            NativeLinkBindings.nGetPhaseAtTime(nativeHandle, timeUs, quantum)
        } catch (e: Throwable) {
            0.0
        }
    }

    override fun isConnected(): Boolean = initialized && nativeHandle != 0L && isEnabled()

    override fun requestBeatAtTime(beat: Double, timeUs: Long, quantum: Double) {
        if (!initialized || nativeHandle == 0L) return
        val nowUs = if (timeUs > 0) timeUs else (System.nanoTime() / 1000)
        try {
            NativeLinkBindings.nRequestBeatAtTime(nativeHandle, beat, nowUs, quantum)
        } catch (e: Throwable) {
            logger.warn(e) { "Error in requestBeatAtTime" }
        }
    }

    override fun setStartStopSyncEnabled(enabled: Boolean) {
        if (!initialized || nativeHandle == 0L) return
        try {
            NativeLinkBindings.nSetStartStopSyncEnabled(nativeHandle, enabled)
        } catch (e: Throwable) {
            logger.warn(e) { "Error setting start/stop sync" }
        }
    }

    override fun isStartStopSyncEnabled(): Boolean {
        if (!initialized || nativeHandle == 0L) return false
        return try {
            NativeLinkBindings.nIsStartStopSyncEnabled(nativeHandle)
        } catch (e: Throwable) {
            false
        }
    }

    override fun isPlaying(): Boolean {
        if (!initialized || nativeHandle == 0L) return false
        return try {
            NativeLinkBindings.nIsPlaying(nativeHandle)
        } catch (e: Throwable) {
            false
        }
    }

    override fun setIsPlaying(isPlaying: Boolean) {
        if (!initialized || nativeHandle == 0L) return
        val nowUs = System.nanoTime() / 1000
        try {
            NativeLinkBindings.nSetIsPlaying(nativeHandle, isPlaying, nowUs)
        } catch (e: Throwable) {
            logger.warn(e) { "Error setting playing state" }
        }
    }
}
