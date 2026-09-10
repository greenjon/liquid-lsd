package llm.slop.liquidlsd.rendering.pipewire

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

    companion object {
        const val PW_DIRECTION_INPUT = 0
        const val PW_DIRECTION_OUTPUT = 1

        const val PW_STREAM_FLAG_NONE = 0
        const val PW_STREAM_FLAG_AUTOCONNECT = (1 shl 0)
        const val PW_STREAM_FLAG_MAP_BUFFERS = (1 shl 3)
        const val PW_STREAM_FLAG_ALLOC_BUFFERS = (1 shl 5)

        const val SPA_MEDIA_TYPE_video = 2
        const val SPA_MEDIA_SUBTYPE_raw = 1
        const val SPA_VIDEO_FORMAT_RGBA = 11
        const val SPA_VIDEO_FORMAT_BGRA = 12

        const val SPA_DATA_MemFd = 2
        const val SPA_DATA_DmaBuf = 3

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

/**
 * SPA Data Buffer mapping structure
 */
@Structure.FieldOrder("type", "flags", "fd", "mapoffset", "maxsize", "datasize", "data", "chunk")
open class SpaData : Structure {
    constructor() : super()
    constructor(p: Pointer?) : super(p)

    @JvmField var type: Int = 0
    @JvmField var flags: Int = 0
    @JvmField var fd: Long = -1
    @JvmField var mapoffset: Int = 0
    @JvmField var maxsize: Int = 0
    @JvmField var datasize: Int = 0
    @JvmField var data: Pointer? = null
    @JvmField var chunk: Pointer? = null
}

/**
 * PipeWire Buffer wrapper structure
 */
@Structure.FieldOrder("id", "datasize", "datas")
open class PipeWireBufferStruct : Structure {
    constructor() : super()
    constructor(p: Pointer?) : super(p)

    @JvmField var id: Int = 0
    @JvmField var datasize: Int = 0
    @JvmField var datas: Pointer? = null
}
