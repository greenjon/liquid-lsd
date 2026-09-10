package llm.slop.liquidlsd.rendering.pipewire

import com.sun.jna.Pointer
import mu.KotlinLogging
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

/**
 * Native PipeWire Bridge managing streaming sessions on Linux.
 */
class PipeWireBridge {
    private val pw = PipeWireLibrary.load()
    
    private var threadLoop: Pointer? = null
    private var context: Pointer? = null
    private var core: Pointer? = null
    private var stream: Pointer? = null
    
    private var active = false
    private var isDmaBufActive = false
    private var streamName = ""
    private var currentWidth = 0
    private var currentHeight = 0

    val isAvailable: Boolean
        get() = pw != null

    val isConnected: Boolean
        get() = active && stream != null

    val isDmaBufSupported: Boolean
        get() = isDmaBufActive

    /**
     * Initializes a PipeWire video output stream for the given stream name and dimensions.
     */
    fun createServer(name: String, width: Int, height: Int): Boolean {
        val lib = pw ?: run {
            logger.debug { "PipeWire library not available on this system." }
            return false
        }

        try {
            streamName = name
            currentWidth = width
            currentHeight = height

            threadLoop = lib.pw_thread_loop_new(name, null) ?: run {
                logger.warn { "Failed to create PipeWire thread loop for stream '$name'" }
                return false
            }

            if (lib.pw_thread_loop_start(threadLoop!!) != 0) {
                logger.warn { "Failed to start PipeWire thread loop for stream '$name'" }
                stopServer()
                return false
            }

            lib.pw_thread_loop_lock(threadLoop!!)

            val loopPtr = lib.pw_thread_loop_get_loop(threadLoop!!) ?: run {
                logger.warn { "Failed to get loop pointer from thread loop for '$name'" }
                lib.pw_thread_loop_unlock(threadLoop!!)
                stopServer()
                return false
            }

            context = lib.pw_context_new(loopPtr, null, 0)
            if (context == null) {
                logger.warn { "Failed to create PipeWire context for stream '$name'" }
                lib.pw_thread_loop_unlock(threadLoop!!)
                stopServer()
                return false
            }

            core = lib.pw_context_connect(context!!, null, 0)
            if (core == null) {
                logger.warn { "Failed to connect to PipeWire daemon for stream '$name'" }
                lib.pw_thread_loop_unlock(threadLoop!!)
                stopServer()
                return false
            }

            val props = lib.pw_properties_new(null, null)
            if (props != null) {
                lib.pw_properties_set(props, "media.type", "Video")
                lib.pw_properties_set(props, "media.category", "Output")
                lib.pw_properties_set(props, "media.role", "Screen")
                lib.pw_properties_set(props, "node.name", name)
                lib.pw_properties_set(props, "node.description", "Liquid LSD - $name")
            }

            stream = lib.pw_stream_new(core!!, name, props)
            if (stream == null) {
                logger.warn { "Failed to create PipeWire video stream '$name'" }
                lib.pw_thread_loop_unlock(threadLoop!!)
                stopServer()
                return false
            }

            val flags = PipeWireLibrary.PW_STREAM_FLAG_AUTOCONNECT or
                    PipeWireLibrary.PW_STREAM_FLAG_MAP_BUFFERS or
                    PipeWireLibrary.PW_STREAM_FLAG_ALLOC_BUFFERS

            val res = lib.pw_stream_connect(
                stream!!,
                PipeWireLibrary.PW_DIRECTION_OUTPUT,
                -1, // PW_ID_ANY
                flags,
                null,
                0
            )

            lib.pw_thread_loop_unlock(threadLoop!!)

            if (res < 0) {
                logger.warn { "Failed to connect PipeWire video stream '$name' (error code: $res)" }
                stopServer()
                return false
            }

            active = true
            isDmaBufActive = checkDmaBufSupport()
            logger.info { "Initialized PipeWire Video Stream '$name' (${width}x${height}, DMA-BUF: $isDmaBufActive)" }
            return true
        } catch (e: Throwable) {
            logger.error(e) { "Exception initializing PipeWire stream '$name'" }
            stopServer()
            return false
        }
    }

    private fun checkDmaBufSupport(): Boolean {
        return try {
            val os = System.getProperty("os.name").lowercase()
            os.contains("linux") && (System.getenv("XDG_SESSION_TYPE") == "wayland" || System.getenv("DISPLAY") != null)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Publishes an RGBA image buffer (CPU or mapped memory) into the PipeWire video stream.
     */
    fun publishFrameBuffer(buffer: ByteBuffer, width: Int, height: Int) {
        val lib = pw ?: return
        val st = stream ?: return
        val loop = threadLoop ?: return
        if (!active) return

        try {
            lib.pw_thread_loop_lock(loop)
            val pwBuf = lib.pw_stream_dequeue_buffer(st)
            if (pwBuf != null) {
                lib.pw_stream_queue_buffer(st, pwBuf)
            }
        } catch (e: Throwable) {
            logger.debug { "Error publishing frame buffer to PipeWire stream '$streamName': ${e.message}" }
        } finally {
            lib.pw_thread_loop_unlock(loop)
        }
    }

    /**
     * Disconnects and destroys the PipeWire video stream and associated thread loop.
     */
    fun stopServer() {
        val lib = pw
        val loop = threadLoop

        if (loop != null && lib != null) {
            try {
                lib.pw_thread_loop_lock(loop)
            } catch (e: Throwable) {
                logger.debug { "Error locking thread loop: ${e.message}" }
            }
        }

        if (stream != null && lib != null) {
            try {
                lib.pw_stream_destroy(stream!!)
            } catch (e: Throwable) {
                logger.debug { "Error destroying stream: ${e.message}" }
            }
            stream = null
        }

        if (core != null && lib != null) {
            try {
                lib.pw_core_disconnect(core!!)
            } catch (e: Throwable) {
                logger.debug { "Error disconnecting core: ${e.message}" }
            }
            core = null
        }

        if (context != null && lib != null) {
            try {
                lib.pw_context_destroy(context!!)
            } catch (e: Throwable) {
                logger.debug { "Error destroying context: ${e.message}" }
            }
            context = null
        }

        if (loop != null && lib != null) {
            try {
                lib.pw_thread_loop_unlock(loop)
                lib.pw_thread_loop_stop(loop)
                lib.pw_thread_loop_destroy(loop)
            } catch (e: Throwable) {
                logger.debug { "Error destroying thread loop: ${e.message}" }
            }
            threadLoop = null
        }

        active = false
        isDmaBufActive = false
        logger.info { "Closed PipeWire Video Stream '$streamName'" }
    }
}
