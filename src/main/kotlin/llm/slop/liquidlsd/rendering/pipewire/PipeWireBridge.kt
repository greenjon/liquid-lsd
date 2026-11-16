package llm.slop.liquidlsd.rendering.pipewire

import com.sun.jna.Pointer
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

/**
 * A PipeWire video source on Linux: the app's output as a `Video/Source` node other apps (OBS, GStreamer,
 * another Liquid LSD) can read. Frames arrive as GL readbacks (RGBA, bottom row first) and are copied top row
 * first into PipeWire's shared memory buffers.
 *
 * Negotiation: [createServer] offers one raw RGBA format at the stream's size; when PipeWire settles the
 * format, `param_changed` answers with the buffer size and stride. The stream drives its own graph cycles:
 * each published frame is copied into a free buffer, queued, and a cycle triggered (`pw_stream_trigger_process`).
 * All stream calls after setup run with the thread loop locked.
 */
class PipeWireBridge {
    private val pw = PipeWireLibrary.load()

    private var threadLoop: Pointer? = null
    private var context: Pointer? = null
    private var core: Pointer? = null
    private var stream: Pointer? = null

    @Volatile private var active = false
    private var streamName = ""
    private var currentWidth = 0
    private var currentHeight = 0

    // PipeWire keeps pointers to these: they live as long as the stream.
    private var events: PwStreamEvents? = null
    private var hook: com.sun.jna.Memory? = null

    @Volatile private var negotiated = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var drainJob: Job? = null

    val isAvailable: Boolean
        get() = pw != null

    val isConnected: Boolean
        get() = active && stream != null

    /** Frames go through shared memory; GPU (DMA-BUF) sharing isn't implemented. */
    val isDmaBufSupported: Boolean
        get() = false

    /** True once a reader has connected and PipeWire has agreed a format and buffers. */
    val isNegotiated: Boolean
        get() = negotiated

    val width: Int get() = currentWidth
    val height: Int get() = currentHeight

    /**
     * Creates the video source node [name] at [width]x[height] and connects it.
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
            negotiated = false

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
            try {
                val loopPtr = lib.pw_thread_loop_get_loop(threadLoop!!) ?: run {
                    logger.warn { "Failed to get loop pointer from thread loop for '$name'" }
                    return false
                }
                context = lib.pw_context_new(loopPtr, null, 0) ?: run {
                    logger.warn { "Failed to create PipeWire context for stream '$name'" }
                    return false
                }
                core = lib.pw_context_connect(context!!, null, 0) ?: run {
                    logger.warn { "Failed to connect to PipeWire daemon for stream '$name'" }
                    return false
                }

                val props = lib.pw_properties_new(null, null)
                if (props != null) {
                    lib.pw_properties_set(props, "media.type", "Video")
                    lib.pw_properties_set(props, "media.category", "Source")
                    lib.pw_properties_set(props, "media.role", "Production")
                    lib.pw_properties_set(props, "media.class", "Video/Source")
                    lib.pw_properties_set(props, "node.name", name)
                    lib.pw_properties_set(props, "node.description", "Liquid LSD - $name")
                }

                stream = lib.pw_stream_new(core!!, name, props) ?: run {
                    logger.warn { "Failed to create PipeWire video stream '$name'" }
                    return false
                }

                val ev = PwStreamEvents()
                ev.param_changed = PwParamChanged { _, id, param -> onParamChanged(id, param) }
                ev.write()
                events = ev
                hook = com.sun.jna.Memory(64).also { it.clear() }
                lib.pw_stream_add_listener(stream!!, hook!!, ev, null)

                val format = SpaPod.videoFormat(SpaPod.PARAM_ENUM_FORMAT, intArrayOf(SpaPod.VIDEO_FORMAT_RGBA), intArrayOf(width, height))
                val flags = PipeWireLibrary.PW_STREAM_FLAG_AUTOCONNECT or
                        PipeWireLibrary.PW_STREAM_FLAG_MAP_BUFFERS or
                        PipeWireLibrary.PW_STREAM_FLAG_DRIVER
                val res = lib.pw_stream_connect(stream!!, PipeWireLibrary.PW_DIRECTION_OUTPUT, -1, flags, arrayOf(format.memory), 1)
                if (res < 0) {
                    logger.warn { "Failed to connect PipeWire video stream '$name' (error code: $res)" }
                    return false
                }
                active = true
            } finally {
                lib.pw_thread_loop_unlock(threadLoop!!)
                if (!active) stopServer()
            }
            if (!active) return false

            frames = FrameTripleBuffer(width * height * 4)
            drainJob = scope.launch {
                while (isActive && active) {
                    if (frames?.hasFresh() == true) drainPendingFrame()
                    delay(4)
                }
            }
            logger.info { "Initialized PipeWire Video Stream '$name' (${width}x${height} RGBA, shared memory)" }
            return true
        } catch (e: Throwable) {
            logger.error(e) { "Exception initializing PipeWire stream '$name'" }
            stopServer()
            return false
        }
    }

    /** On the PipeWire thread: once the format is fixed, say how big each buffer is. */
    private fun onParamChanged(id: Int, param: Pointer?) {
        if (id != SpaPod.PARAM_FORMAT || param == null) return
        val lib = pw ?: return
        val st = stream ?: return
        val stride = currentWidth * 4
        val buffers = SpaPod.buffers(stride * currentHeight, stride)
        lib.pw_stream_update_params(st, arrayOf(buffers.memory), 1)
        negotiated = true
        logger.info { "PipeWire stream '$streamName' format agreed: ${currentWidth}x$currentHeight RGBA" }
    }

    // ── Lock-free frame hand-off from GL thread → PipeWire ─────────────────────────────────────
    // The GL thread copies each readback into a buffer the bridge owns; the drain coroutine copies the newest one
    // into a PipeWire buffer. Neither side waits on the other, and neither reads memory the other is writing.
    @Volatile private var frames: FrameTripleBuffer? = null

    /**
     * Called from the GL render thread once per frame with a direct buffer holding a GL readback (RGBA, bottom
     * row first) of the stream's size. Copies it (the caller reuses [buffer] next frame) and returns; the copy into
     * PipeWire happens on the drain coroutine.
     */
    fun publishFrameBuffer(buffer: ByteBuffer, width: Int, height: Int) {
        if (!active || stream == null || width != currentWidth || height != currentHeight) return
        frames?.publish(buffer)
    }

    /**
     * Copies the pending frame into a free PipeWire buffer, queues it and triggers a graph cycle.
     * Must NOT be called from the GL thread.
     */
    fun drainPendingFrame() {
        val lib = pw ?: return
        val st = stream ?: return
        val loop = threadLoop ?: return
        val buf = frames?.takeFresh() ?: return
        if (!negotiated) return

        lib.pw_thread_loop_lock(loop)
        try {
            val pwBuf = lib.pw_stream_dequeue_buffer(st) ?: return
            try {
                val data = SpaBufferLayout.firstData(pwBuf) ?: return
                val dest = SpaBufferLayout.mapped(data) ?: return
                val chunk = SpaBufferLayout.chunk(data) ?: return
                val stride = currentWidth * 4
                val bytes = stride * currentHeight
                if (!buf.isDirect || buf.capacity() < bytes || SpaBufferLayout.maxSize(data) < bytes) return
                // GL readback rows run bottom to top; video rows run top to bottom.
                val src = org.lwjgl.system.MemoryUtil.memAddress(buf, 0)
                val dst = Pointer.nativeValue(dest)
                for (row in 0 until currentHeight) {
                    org.lwjgl.system.MemoryUtil.memCopy(src + (currentHeight - 1 - row).toLong() * stride, dst + row.toLong() * stride, stride.toLong())
                }
                SpaBufferLayout.setChunk(chunk, bytes, stride)
            } finally {
                lib.pw_stream_queue_buffer(st, pwBuf)
            }
            lib.pw_stream_trigger_process(st)
        } catch (e: Throwable) {
            logger.debug { "Error draining pending frame to PipeWire stream '$streamName': ${e.message}" }
        } finally {
            lib.pw_thread_loop_unlock(loop)
        }
    }

    /**
     * Disconnects and destroys the PipeWire video stream and associated thread loop.
     */
    fun stopServer() {
        // Wait for the drain to finish: it may be copying out of [frames] or inside a stream call.
        drainJob?.let { job -> runBlocking { job.cancelAndJoin() } }
        drainJob = null

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
        negotiated = false
        frames?.free()
        frames = null
        events = null
        hook = null
        logger.info { "Closed PipeWire Video Stream '$streamName'" }
    }
}

/**
 * Three frame buffers handed between one producer (the GL thread) and one consumer (the drain): the producer
 * always has a buffer of its own to write, the consumer always reads a complete frame, and a frame the consumer
 * hasn't taken yet is replaced by a newer one. No locks, no allocation per frame.
 */
internal class FrameTripleBuffer(val sizeBytes: Int) {
    private val buffers = Array(3) { org.lwjgl.system.MemoryUtil.memAlloc(sizeBytes) }
    private var writeIndex = 0                       // producer only
    private var readIndex = 1                        // consumer only
    private val middle = java.util.concurrent.atomic.AtomicInteger(2)   // index, plus FRESH once published

    /** Producer: copies [src] (at least [sizeBytes], direct) in and makes it the newest frame. */
    fun publish(src: ByteBuffer) {
        if (!src.isDirect || src.capacity() < sizeBytes) return
        val dst = buffers[writeIndex]
        org.lwjgl.system.MemoryUtil.memCopy(org.lwjgl.system.MemoryUtil.memAddress(src, 0), org.lwjgl.system.MemoryUtil.memAddress(dst, 0), sizeBytes.toLong())
        writeIndex = middle.getAndSet(writeIndex or FRESH) and INDEX
    }

    fun hasFresh(): Boolean = middle.get() and FRESH != 0

    /** Consumer: the newest published frame, or null when nothing new arrived. Valid until the next call. */
    fun takeFresh(): ByteBuffer? {
        if (!hasFresh()) return null
        readIndex = middle.getAndSet(readIndex) and INDEX
        return buffers[readIndex]
    }

    /** Only once neither side uses it any more. */
    fun free() { for (b in buffers) org.lwjgl.system.MemoryUtil.memFree(b) }

    private companion object {
        const val INDEX = 3
        const val FRESH = 4
    }
}
