package llm.slop.liquidlsd.rendering

import com.sun.jna.Pointer
import llm.slop.liquidlsd.rendering.pipewire.PipeWireLibrary
import llm.slop.liquidlsd.rendering.pipewire.PwParamChanged
import llm.slop.liquidlsd.rendering.pipewire.PwStreamEvents
import llm.slop.liquidlsd.rendering.pipewire.SpaBufferLayout
import llm.slop.liquidlsd.rendering.pipewire.SpaPod
import mu.KotlinLogging
import org.lwjgl.opengl.GL11.*
import llm.slop.liquidlsd.rendering.FBO

private val logger = KotlinLogging.logger {}

/**
 * Interface for consuming external textures via Spout/Syphon.
 */
interface TextureReceiver {
    val isSupported: Boolean
    val currentWidth: Int
    val currentHeight: Int
    
    fun start(serverName: String): Boolean
    fun update(): Int // Returns the received OpenGL Texture ID, or 0 if none
    fun stop()
}

/**
 * Null receiver used when unsupported or offline.
 */
class NullTextureReceiver : TextureReceiver {
    override val isSupported = true
    override val currentWidth = 1920
    override val currentHeight = 1080
    override fun start(serverName: String): Boolean = false
    override fun update(): Int = 0
    override fun stop() {}
}

class SpoutReceiverImpl : TextureReceiver {
    override val isSupported: Boolean
        get() = System.getProperty("os.name").lowercase().contains("win")

    private var spoutPtr: com.sun.jna.Pointer? = null
    private var spoutLib: SpoutLibrary? = null
    private var active = false
    
    override var currentWidth = 1920
        private set
    override var currentHeight = 1080
        private set

    override fun start(serverName: String): Boolean {
        if (!isSupported) return false
        if (active || spoutPtr != null || localTextureId != 0) {
            stop()
        }
        try {
            val nativesPath = java.io.File("library/natives").absolutePath
            System.setProperty("jna.library.path", "${System.getProperty("jna.library.path") ?: ""}${java.io.File.pathSeparator}$nativesPath")
            
            spoutLib = com.sun.jna.Native.load("SpoutLibrary", SpoutLibrary::class.java)
            spoutPtr = spoutLib?.CreateSpout()
            if (spoutPtr == null) return false
            
            val nameBytes = serverName.toByteArray(Charsets.UTF_8).let { it.copyOf(it.size + 1) }
            val w = intArrayOf(0)
            val h = intArrayOf(0)
            
            // Try to create receiver
            val success = spoutLib?.CreateReceiver(spoutPtr!!, nameBytes, w, h, false) == true
            if (success) {
                currentWidth = w[0]
                currentHeight = h[0]
                active = true
                logger.info { "Connected to Spout sender: $serverName" }
                return true
            }
            return false
        } catch (e: Exception) {
            logger.error(e) { "Failed to start Spout receiver" }
            return false
        }
    }

    private var localTextureId = 0
    // Pre-allocated reusable buffers — avoids ByteArray/IntArray allocation on every render frame
    private val recvNameBuf = ByteArray(256)
    private val recvWBuf    = IntArray(1)
    private val recvHBuf    = IntArray(1)

    override fun update(): Int {
        if (!active || spoutPtr == null) return 0
        
        // Ensure local texture exists and matches size
        if (localTextureId == 0 || currentWidth <= 0 || currentHeight <= 0) {
            // Allocate initial dummy texture if size is known
            if (currentWidth > 0 && currentHeight > 0) {
                localTextureId = glGenTextures()
                glBindTexture(GL_TEXTURE_2D, localTextureId)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, currentWidth, currentHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as java.nio.ByteBuffer?)
                glBindTexture(GL_TEXTURE_2D, 0)
            }
        }
        
        recvNameBuf.fill(0)
        recvWBuf[0] = currentWidth
        recvHBuf[0] = currentHeight
        
        val success = spoutLib?.ReceiveTexture(spoutPtr!!, recvNameBuf, recvWBuf, recvHBuf, localTextureId, GL_TEXTURE_2D, false, 0) == true
        if (success) {
            // Re-allocate if size changed
            if (recvWBuf[0] != currentWidth || recvHBuf[0] != currentHeight) {
                currentWidth = recvWBuf[0]
                currentHeight = recvHBuf[0]
                if (localTextureId != 0) {
                    glDeleteTextures(localTextureId)
                }
                localTextureId = glGenTextures()
                glBindTexture(GL_TEXTURE_2D, localTextureId)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, currentWidth, currentHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as java.nio.ByteBuffer?)
                glBindTexture(GL_TEXTURE_2D, 0)
                // Need to call receive again since it failed to map due to size mismatch? 
                // Or Spout handled it and next frame it will be fine.
            }
            return localTextureId
        }
        return 0
    }

    override fun stop() {
        if (active && spoutPtr != null) {
            spoutLib?.ReleaseReceiver(spoutPtr!!)
            spoutLib?.ReleaseSpout(spoutPtr!!)
            spoutPtr = null
            active = false
        }
        if (localTextureId != 0) {
            glDeleteTextures(localTextureId)
            localTextureId = 0
        }
    }
}

class SyphonReceiverImpl : TextureReceiver {
    override val isSupported: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")

    private val bridge = SyphonBridge()
    private var clientPtr: com.sun.jna.Pointer? = null
    private var active = false
    private var lastTextureId = 0
    
    override var currentWidth = 1920
        private set
    override var currentHeight = 1080
        private set

    override fun start(serverName: String): Boolean {
        if (!isSupported) return false
        clientPtr = bridge.createClient(serverName)
        if (clientPtr != null) {
            active = true
            logger.info { "Connected to Syphon server: $serverName" }
            return true
        }
        return false
    }

    override fun update(): Int {
        if (!active || clientPtr == null) return 0
        
        if (bridge.hasNewFrame(clientPtr!!)) {
            val img = bridge.newFrameImage(clientPtr!!)
            if (img != null) {
                lastTextureId = bridge.textureNameForImage(img)
                val sz = bridge.textureSizeForImage(img)
                if (sz[0] > 0) {
                    currentWidth = sz[0]
                    currentHeight = sz[1]
                }
            }
        }
        return lastTextureId
    }

    override fun stop() {
        if (active && clientPtr != null) {
            bridge.stopClient(clientPtr!!)
            clientPtr = null
            active = false
            lastTextureId = 0
        }
    }
}

/**
 * Reads a PipeWire video stream (another app's `Video/Source`) into a GL texture. It offers raw RGBA, BGRA,
 * RGBx and BGRx at any size; PipeWire converts other formats for it. The agreed size and pixel order come from
 * `param_changed`, and each frame is uploaded with its own stride.
 */
class PipeWireReceiverImpl : TextureReceiver {
    override val isSupported: Boolean
        get() = System.getProperty("os.name").lowercase().contains("linux") && PipeWireLibrary.load() != null

    private val pw = PipeWireLibrary.load()
    private var threadLoop: Pointer? = null
    private var context: Pointer? = null
    private var core: Pointer? = null
    private var stream: Pointer? = null

    private var active = false
    private var localTextureId = 0
    private var texW = 0
    private var texH = 0
    private var texFormat = 0

    // PipeWire keeps pointers to these: they live as long as the stream.
    private var events: PwStreamEvents? = null
    private var hook: com.sun.jna.Memory? = null

    /** Set on the PipeWire thread when the format is agreed: width, height, SPA video format. */
    @Volatile private var agreed: SpaPod.VideoInfo? = null

    override val currentWidth: Int
        get() = agreed?.width ?: 1920
    override val currentHeight: Int
        get() = agreed?.height ?: 1080

    override fun start(serverName: String): Boolean {
        val lib = pw ?: return false
        if (!isSupported) return false
        try {
            threadLoop = lib.pw_thread_loop_new("PipeWireReceiver", null) ?: return false
            if (lib.pw_thread_loop_start(threadLoop!!) != 0) {
                stop()
                return false
            }

            lib.pw_thread_loop_lock(threadLoop!!)
            try {
                val loopPtr = lib.pw_thread_loop_get_loop(threadLoop!!) ?: return false
                context = lib.pw_context_new(loopPtr, null, 0) ?: return false
                core = lib.pw_context_connect(context!!, null, 0) ?: return false

                val props = lib.pw_properties_new(null, null)
                if (props != null) {
                    lib.pw_properties_set(props, "media.type", "Video")
                    lib.pw_properties_set(props, "media.category", "Capture")
                    lib.pw_properties_set(props, "media.role", "Production")
                    lib.pw_properties_set(props, "node.name", "LiquidLSD-Receiver")
                    lib.pw_properties_set(props, "node.description", "Liquid LSD PipeWire Receiver")
                    if (serverName.isNotBlank()) {
                        lib.pw_properties_set(props, "target.object", ExternalVideoDiscovery.pipeWireTarget(serverName))
                    }
                }

                stream = lib.pw_stream_new(core!!, "LiquidLSD-VideoIngest", props) ?: return false

                val ev = PwStreamEvents()
                ev.param_changed = PwParamChanged { _, id, param -> onParamChanged(id, param) }
                ev.write()
                events = ev
                hook = com.sun.jna.Memory(64).also { it.clear() }
                lib.pw_stream_add_listener(stream!!, hook!!, ev, null)

                val formats = intArrayOf(SpaPod.VIDEO_FORMAT_RGBA, SpaPod.VIDEO_FORMAT_BGRA, SpaPod.VIDEO_FORMAT_RGBX, SpaPod.VIDEO_FORMAT_BGRX)
                val format = SpaPod.videoFormat(SpaPod.PARAM_ENUM_FORMAT, formats, intArrayOf(1920, 1080), intArrayOf(1, 1), intArrayOf(8192, 8192), anyRate = true)
                val flags = PipeWireLibrary.PW_STREAM_FLAG_AUTOCONNECT or PipeWireLibrary.PW_STREAM_FLAG_MAP_BUFFERS
                val res = lib.pw_stream_connect(stream!!, PipeWireLibrary.PW_DIRECTION_INPUT, -1, flags, arrayOf(format.memory), 1)
                if (res < 0) {
                    logger.warn { "Failed to connect PipeWire receiver stream for target '$serverName' (code: $res)" }
                    return false
                }
                active = true
            } finally {
                lib.pw_thread_loop_unlock(threadLoop!!)
                if (!active) stop()
            }
            if (!active) return false
            logger.info { "Connected PipeWire Receiver to stream/target: $serverName" }
            return true
        } catch (e: Throwable) {
            logger.error(e) { "Failed to start PipeWire receiver for '$serverName'" }
            stop()
            return false
        }
    }

    /** On the PipeWire thread: note the agreed format and say how big each buffer is. */
    private fun onParamChanged(id: Int, param: Pointer?) {
        if (id != SpaPod.PARAM_FORMAT || param == null) return
        val info = SpaPod.parseVideoFormat(param) ?: return
        agreed = info
        val stride = info.width * 4
        pw?.pw_stream_update_params(stream ?: return, arrayOf(SpaPod.buffers(stride * info.height, stride).memory), 1)
        logger.info { "PipeWire receiver format agreed: ${info.width}x${info.height} (SPA video format ${info.format})" }
    }

    override fun update(): Int {
        val lib = pw
        val loop = threadLoop
        if (!active || stream == null || loop == null || lib == null) return localTextureId
        val info = agreed ?: return localTextureId

        try {
            // Take the newest waiting frame; give older ones straight back.
            var newest: Pointer? = null
            lib.pw_thread_loop_lock(loop)
            try {
                while (true) {
                    val b = lib.pw_stream_dequeue_buffer(stream!!) ?: break
                    newest?.let { lib.pw_stream_queue_buffer(stream!!, it) }
                    newest = b
                }
            } finally {
                lib.pw_thread_loop_unlock(loop)
            }
            val pwBuf = newest ?: return localTextureId
            try {
                val data = SpaBufferLayout.firstData(pwBuf)
                val base = data?.let { SpaBufferLayout.mapped(it) }
                val chunk = data?.let { SpaBufferLayout.chunk(it) }
                if (base != null && chunk != null && SpaBufferLayout.chunkSize(chunk) > 0) {
                    val stride = SpaBufferLayout.chunkStride(chunk).let { if (it > 0) it else info.width * 4 }
                    val offset = SpaBufferLayout.chunkOffset(chunk).toLong() % SpaBufferLayout.maxSize(data).coerceAtLeast(1)
                    if (stride * (info.height - 1) + info.width * 4 <= SpaBufferLayout.maxSize(data) - offset) {
                        upload(info, Pointer.nativeValue(base) + offset, stride)
                    }
                }
            } finally {
                lib.pw_thread_loop_lock(loop)
                try { lib.pw_stream_queue_buffer(stream!!, pwBuf) } finally { lib.pw_thread_loop_unlock(loop) }
            }
        } catch (e: Throwable) {
            logger.debug { "Error receiving PipeWire buffer: ${e.message}" }
        }
        return localTextureId
    }

    private fun upload(info: SpaPod.VideoInfo, address: Long, stride: Int) {
        if (localTextureId == 0 || texW != info.width || texH != info.height || texFormat != info.format) {
            if (localTextureId == 0) localTextureId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, localTextureId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, info.width, info.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
            // RGBx / BGRx carry no alpha: read it as opaque.
            val noAlpha = info.format == SpaPod.VIDEO_FORMAT_RGBX || info.format == SpaPod.VIDEO_FORMAT_BGRX
            glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_A, if (noAlpha) org.lwjgl.opengl.GL11.GL_ONE else GL_ALPHA)
            texW = info.width; texH = info.height; texFormat = info.format
        } else {
            glBindTexture(GL_TEXTURE_2D, localTextureId)
        }
        val bgr = info.format == SpaPod.VIDEO_FORMAT_BGRA || info.format == SpaPod.VIDEO_FORMAT_BGRX
        // Video rows run top to bottom, GL textures bottom to top: upload each row into its mirrored place.
        val glFormat = if (bgr) org.lwjgl.opengl.GL12.GL_BGRA else GL_RGBA
        for (row in 0 until info.height) {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, info.height - 1 - row, info.width, 1, glFormat, GL_UNSIGNED_BYTE, address + row.toLong() * stride)
        }
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    override fun stop() {
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

        if (localTextureId != 0) {
            glDeleteTextures(localTextureId)
            localTextureId = 0
        }

        active = false
        agreed = null
        events = null
        hook = null
        texW = 0; texH = 0; texFormat = 0
        logger.info { "Stopped PipeWire Receiver" }
    }
}

