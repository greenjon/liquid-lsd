package llm.slop.liquidlsd.rendering.pipewire

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * JNA Interface for libpipewire-0.3.so
 */
interface PipeWireLibrary : Library {
    fun pw_init(argc: Pointer?, argv: Pointer?)
    fun pw_deinit()
    
    fun pw_thread_loop_new(name: String?, props: Pointer?): Pointer?
    fun pw_thread_loop_start(loop: Pointer): Int
    fun pw_thread_loop_stop(loop: Pointer)
    fun pw_thread_loop_destroy(loop: Pointer)
    fun pw_thread_loop_get_loop(loop: Pointer): Pointer?
    fun pw_thread_loop_lock(loop: Pointer)
    fun pw_thread_loop_unlock(loop: Pointer)

    fun pw_context_new(main_loop: Pointer, props: Pointer?, user_data_size: Long): Pointer?
    fun pw_context_connect(context: Pointer, props: Pointer?, user_data_size: Long): Pointer?
    fun pw_context_destroy(context: Pointer)
    fun pw_core_disconnect(core: Pointer): Int

    fun pw_properties_new(key: String?, value: String?, vararg args: Any?): Pointer?
    fun pw_properties_set(props: Pointer, key: String, value: String)
    fun pw_properties_free(props: Pointer)

    fun pw_stream_new(core: Pointer, name: String, props: Pointer?): Pointer?
    fun pw_stream_connect(stream: Pointer, direction: Int, target_id: Int, flags: Int, params: Array<Pointer>?, n_params: Int): Int
    fun pw_stream_destroy(stream: Pointer)
    fun pw_stream_dequeue_buffer(stream: Pointer): Pointer?
    fun pw_stream_queue_buffer(stream: Pointer, buffer: Pointer): Int
    fun pw_stream_add_listener(stream: Pointer, listener: Pointer, events: PwStreamEvents, data: Pointer?)
    fun pw_stream_update_params(stream: Pointer, params: Array<Pointer>, n_params: Int): Int
    fun pw_stream_trigger_process(stream: Pointer): Int

    companion object {
        const val PW_DIRECTION_INPUT = 0
        const val PW_DIRECTION_OUTPUT = 1

        // pipewire/stream.h (1.6): enum pw_stream_flags
        const val PW_STREAM_FLAG_NONE = 0
        const val PW_STREAM_FLAG_AUTOCONNECT = (1 shl 0)
        const val PW_STREAM_FLAG_MAP_BUFFERS = (1 shl 2)
        const val PW_STREAM_FLAG_DRIVER = (1 shl 3)

        const val PW_VERSION_STREAM_EVENTS = 2

        private var instance: PipeWireLibrary? = null
        private var isLoaded = false

        fun load(): PipeWireLibrary? {
            if (isLoaded) return instance
            isLoaded = true

            if (!System.getProperty("os.name").lowercase().contains("linux")) {
                return null
            }

            val libraryNames = listOf("pipewire-0.3", "libpipewire-0.3.so.0", "libpipewire-0.3.so")
            for (libName in libraryNames) {
                try {
                    val lib = Native.load(libName, PipeWireLibrary::class.java)
                    lib.pw_init(null, null)
                    logger.info { "Successfully loaded native PipeWire library: $libName" }
                    instance = lib
                    return lib
                } catch (e: Throwable) {
                    logger.debug { "Could not load $libName: ${e.message}" }
                }
            }
            logger.warn { "PipeWire library (libpipewire-0.3) not found on system. Linux video output will use Null / Fallback streamer." }
            return null
        }
    }
}

/** `void (*param_changed)(void *data, uint32_t id, const struct spa_pod *param)` */
fun interface PwParamChanged : Callback {
    fun invoke(data: Pointer?, id: Int, param: Pointer?)
}

/** `void (*process)(void *data)` */
fun interface PwProcess : Callback {
    fun invoke(data: Pointer?)
}

/**
 * `struct pw_stream_events` (version 2). Unused callbacks stay null. Keep the instance referenced for the
 * stream's whole life: PipeWire holds a pointer to it.
 */
@Structure.FieldOrder(
    "version", "destroy", "state_changed", "control_info", "io_changed", "param_changed",
    "add_buffer", "remove_buffer", "process", "drained", "command", "trigger_done"
)
open class PwStreamEvents : Structure() {
    @JvmField var version: Int = PipeWireLibrary.PW_VERSION_STREAM_EVENTS
    @JvmField var destroy: Pointer? = null
    @JvmField var state_changed: Pointer? = null
    @JvmField var control_info: Pointer? = null
    @JvmField var io_changed: Pointer? = null
    @JvmField var param_changed: PwParamChanged? = null
    @JvmField var add_buffer: Pointer? = null
    @JvmField var remove_buffer: Pointer? = null
    @JvmField var process: PwProcess? = null
    @JvmField var drained: Pointer? = null
    @JvmField var command: Pointer? = null
    @JvmField var trigger_done: Pointer? = null
}

/**
 * Field offsets in `struct pw_buffer`, `struct spa_buffer`, `struct spa_data` and `struct spa_chunk`
 * (spa/buffer/buffer.h, 64-bit), read and written straight from native memory.
 */
object SpaBufferLayout {
    /** The first `spa_data` of a dequeued `pw_buffer`, or null. */
    fun firstData(pwBuffer: Pointer): Pointer? {
        val spaBuffer = pwBuffer.getPointer(0) ?: return null      // pw_buffer.buffer
        if (spaBuffer.getInt(4) < 1) return null                    // spa_buffer.n_datas
        return spaBuffer.getPointer(16)                              // spa_buffer.datas
    }
    fun maxSize(data: Pointer): Int = data.getInt(20)              // spa_data.maxsize
    fun mapped(data: Pointer): Pointer? = data.getPointer(24)      // spa_data.data
    fun chunk(data: Pointer): Pointer? = data.getPointer(32)       // spa_data.chunk
    // spa_chunk: offset, size, stride, flags
    fun chunkOffset(chunk: Pointer): Int = chunk.getInt(0)
    fun chunkSize(chunk: Pointer): Int = chunk.getInt(4)
    fun chunkStride(chunk: Pointer): Int = chunk.getInt(8)
    fun setChunk(chunk: Pointer, size: Int, stride: Int) {
        chunk.setInt(0, 0); chunk.setInt(4, size); chunk.setInt(8, stride); chunk.setInt(12, 0)
    }
}
