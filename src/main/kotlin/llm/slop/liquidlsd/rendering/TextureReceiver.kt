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

    private var spout: SpoutApi? = null
    private var localTextureId = 0
    private var texW = 0
    private var texH = 0

    override var currentWidth = 1920
        private set
    override var currentHeight = 1080
        private set

    override fun start(serverName: String): Boolean {
        if (!isSupported) return false
        stop()
        val api = SpoutApi.create() ?: return false
        return try {
            api.setReceiverName(serverName)
            spout = api
            logger.info { "Spout receiver set to sender: $serverName" }
            true
        } catch (e: Throwable) {
            logger.error(e) { "Failed to start Spout receiver" }
            api.release()
            false
        }
    }

    private fun allocate(w: Int, h: Int) {
        if (localTextureId == 0) localTextureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, localTextureId)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as java.nio.ByteBuffer?)
        glBindTexture(GL_TEXTURE_2D, 0)
        texW = w; texH = h
    }

    /**
     * Spout 2.007's receive loop: receive into our texture; when the sender appears or changes size [SpoutApi.isUpdated]
     * says so, and the texture is reallocated to the sender's size before the next receive.
     */
    override fun update(): Int {
        val api = spout ?: return 0
        if (localTextureId == 0) allocate(currentWidth, currentHeight)
        val hostFbo = glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING)
        if (!api.receiveTexture(localTextureId, GL_TEXTURE_2D, false, hostFbo)) return 0
        if (api.isUpdated()) {
            currentWidth = api.senderWidth().coerceAtLeast(1)
            currentHeight = api.senderHeight().coerceAtLeast(1)
            if (currentWidth != texW || currentHeight != texH) allocate(currentWidth, currentHeight)
            return 0
        }
        return localTextureId
    }

    override fun stop() {
        spout?.let { api -> try { api.releaseReceiver() } finally { api.release() } }
        spout = null
        if (localTextureId != 0) {
            glDeleteTextures(localTextureId)
            localTextureId = 0
        }
        texW = 0; texH = 0
    }
}

class SyphonReceiverImpl : TextureReceiver {
    override val isSupported: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")

    private val bridge = SyphonBridge()
    private var clientPtr: com.sun.jna.Pointer? = null
    /** Syphon frames are GL_TEXTURE_RECTANGLE and live only while their image is held; each one is copied here. */
    private var copy: FBO? = null
    private var readFbo = 0

    override var currentWidth = 1920
        private set
    override var currentHeight = 1080
        private set

    override fun start(serverName: String): Boolean {
        if (!isSupported) return false
        stop()
        clientPtr = bridge.createClient(serverName) ?: return false
        logger.info { "Connected to Syphon server: $serverName" }
        return true
    }

    override fun update(): Int {
        val client = clientPtr ?: return 0
        if (bridge.hasNewFrame(client)) {
            val img = bridge.newFrameImage(client) ?: return copy?.texture ?: 0
            try {
                val (w, h) = bridge.textureSizeForImage(img).let { it[0] to it[1] }
                if (w > 0 && h > 0) {
                    currentWidth = w; currentHeight = h
                    blit(bridge.textureNameForImage(img), w, h)
                }
            } finally {
                bridge.releaseObject(img)
            }
        }
        return copy?.texture ?: 0
    }

    private fun blit(rectTex: Int, w: Int, h: Int) {
        val dst = copy?.takeIf { it.width == w && it.height == h } ?: FBO(w, h).also { copy?.dispose(); copy = it }
        if (readFbo == 0) readFbo = org.lwjgl.opengl.GL30.glGenFramebuffers()
        val prevRead = glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING)
        val prevDraw = glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, readFbo)
        org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL31.GL_TEXTURE_RECTANGLE, rectTex, 0)
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, dst.framebufferId)
        org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL_COLOR_BUFFER_BIT, GL_NEAREST)
        org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL31.GL_TEXTURE_RECTANGLE, 0, 0)
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, prevRead)
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, prevDraw)
    }

    override fun stop() {
        clientPtr?.let { bridge.stopClient(it) }
        clientPtr = null
        copy?.dispose(); copy = null
        if (readFbo != 0) { org.lwjgl.opengl.GL30.glDeleteFramebuffers(readFbo); readFbo = 0 }
    }
}

/**
 * Reads a PipeWire video stream (another app's `Video/Source`, or a camera) into a GL texture. It offers raw
 * RGBA, BGRA, RGBx, BGRx, YUY2 (what most webcams send) and GRAY8 (IR cameras) at any size and rate; PipeWire
 * doesn't convert video, so a source must send one of these. The agreed size and format come from
 * `param_changed`, and each frame is uploaded with its own stride. YUY2 goes through a small conversion pass.
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

                val formats = intArrayOf(SpaPod.VIDEO_FORMAT_RGBA, SpaPod.VIDEO_FORMAT_BGRA, SpaPod.VIDEO_FORMAT_RGBX, SpaPod.VIDEO_FORMAT_BGRX, SpaPod.VIDEO_FORMAT_YUY2, SpaPod.VIDEO_FORMAT_GRAY8)
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
        val stride = info.width * SpaPod.bytesPerPixel(info.format)
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
                    val rowBytes = info.width * SpaPod.bytesPerPixel(info.format)
                    val stride = SpaBufferLayout.chunkStride(chunk).let { if (it > 0) it else rowBytes }
                    val offset = SpaBufferLayout.chunkOffset(chunk).toLong() % SpaBufferLayout.maxSize(data).coerceAtLeast(1)
                    if (stride.toLong() * (info.height - 1) + rowBytes <= SpaBufferLayout.maxSize(data) - offset) {
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

    // YUY2: the packed frame (one RGBA texel = Y0 U Y1 V, half the width) and the converted RGBA picture.
    private var packedTex = 0
    private var yuvFbo: FBO? = null
    private var yuvShader: Shader? = null

    private fun upload(info: SpaPod.VideoInfo, address: Long, stride: Int) {
        val w = info.width; val h = info.height
        val yuy2 = info.format == SpaPod.VIDEO_FORMAT_YUY2
        val gray = info.format == SpaPod.VIDEO_FORMAT_GRAY8
        val texW = if (yuy2) (w + 1) / 2 else w
        if (!yuy2 && yuvFbo != null) { yuvFbo?.dispose(); yuvFbo = null; localTextureId = 0 }  // left YUY2: drop the converted picture
        val target = if (yuy2) { if (packedTex == 0) packedTex = glGenTextures(); packedTex } else {
            if (localTextureId == 0) localTextureId = glGenTextures(); localTextureId
        }
        glBindTexture(GL_TEXTURE_2D, target)
        if (this.texW != w || this.texH != h || texFormat != info.format) {
            val filter = if (yuy2) GL_NEAREST else GL_LINEAR
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
            if (gray) {
                glTexImage2D(GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30.GL_R8, w, h, 0, GL_RED, GL_UNSIGNED_BYTE, 0L)
                for (c in intArrayOf(org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_G, org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_B)) glTexParameteri(GL_TEXTURE_2D, c, GL_RED)
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texW, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
            }
            // RGBx / BGRx / GRAY8 carry no alpha: read it as opaque.
            val noAlpha = info.format == SpaPod.VIDEO_FORMAT_RGBX || info.format == SpaPod.VIDEO_FORMAT_BGRX || gray
            glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_A, if (noAlpha) org.lwjgl.opengl.GL11.GL_ONE else GL_ALPHA)
            if (yuy2) {
                yuvFbo?.dispose(); yuvFbo = FBO(w, h)
                if (localTextureId != 0) { glDeleteTextures(localTextureId); localTextureId = 0 }
            }
            this.texW = w; this.texH = h; texFormat = info.format
        }
        val bgr = info.format == SpaPod.VIDEO_FORMAT_BGRA || info.format == SpaPod.VIDEO_FORMAT_BGRX
        val glFormat = when { gray -> GL_RED; bgr -> org.lwjgl.opengl.GL12.GL_BGRA; else -> GL_RGBA }
        org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT, 1)
        // Video rows run top to bottom, GL textures bottom to top: upload each row into its mirrored place.
        for (row in 0 until h) {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, h - 1 - row, texW, 1, glFormat, GL_UNSIGNED_BYTE, address + row.toLong() * stride)
        }
        org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT, 4)
        glBindTexture(GL_TEXTURE_2D, 0)
        if (yuy2) convertYuy2(w, h)
    }

    /** Packed YUY2 -> RGBA (BT.601, limited range, as webcams send it) into [yuvFbo], restoring the caller's GL state. */
    private fun convertYuy2(w: Int, h: Int) {
        val fbo = yuvFbo ?: return
        val shader = yuvShader ?: Shader(YUY2_VERT, YUY2_FRAG).also { yuvShader = it }
        val prevFbo = glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING)
        val prevProgram = glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM)
        val vp = IntArray(4).also { glGetIntegerv(GL_VIEWPORT, it) }
        val blend = glIsEnabled(GL_BLEND)
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, fbo.framebufferId)
        glViewport(0, 0, w, h)
        glDisable(GL_BLEND)
        shader.bind()
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, packedTex)
        shader.setUniform("uPacked", 0)
        Geometry.drawFullscreenQuad()
        glBindTexture(GL_TEXTURE_2D, 0)
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, prevFbo)
        org.lwjgl.opengl.GL20.glUseProgram(prevProgram)
        glViewport(vp[0], vp[1], vp[2], vp[3])
        if (blend) glEnable(GL_BLEND)
        localTextureId = fbo.texture
    }

    private companion object {
        const val YUY2_VERT = """#version 330 core
layout(location = 0) in vec2 aPosition;
void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
"""
        const val YUY2_FRAG = """#version 330 core
uniform sampler2D uPacked;
out vec4 o;
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    vec4 t = texelFetch(uPacked, ivec2(p.x / 2, p.y), 0);   // Y0 U Y1 V
    float y = ((p.x & 1) == 0 ? t.r : t.b) - 16.0 / 255.0;
    float u = t.g - 0.5, v = t.a - 0.5;
    o = vec4(clamp(vec3(1.164 * y + 1.596 * v, 1.164 * y - 0.392 * u - 0.813 * v, 1.164 * y + 2.017 * u), 0.0, 1.0), 1.0);
}
"""
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

        if (yuvFbo != null) { yuvFbo?.dispose(); yuvFbo = null; localTextureId = 0 }
        if (localTextureId != 0) {
            glDeleteTextures(localTextureId)
            localTextureId = 0
        }
        if (packedTex != 0) { glDeleteTextures(packedTex); packedTex = 0 }
        yuvShader?.dispose(); yuvShader = null

        active = false
        agreed = null
        events = null
        hook = null
        texW = 0; texH = 0; texFormat = 0
        logger.info { "Stopped PipeWire Receiver" }
    }
}

