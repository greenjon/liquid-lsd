package llm.slop.liquidlsd.rendering

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D

private val logger = KotlinLogging.logger {}

/**
 * Cross-platform interface for zero-copy / low-latency GPU texture sharing
 * to external VJ & streaming software (Resolume Arena, OBS Studio, TouchDesigner, MadMapper).
 */
interface TextureStreamer {
    val identifier: String
    val isSupported: Boolean
    fun start(width: Int, height: Int): Boolean
    fun update(textureId: Int, width: Int, height: Int)
    fun stop()
}

/**
 * Null implementation used when live texture output is disabled or unsupported.
 */
class NullTextureStreamer(override val identifier: String) : TextureStreamer {
    override val isSupported = true
    override fun start(width: Int, height: Int): Boolean = true
    override fun update(textureId: Int, width: Int, height: Int) {}
    override fun stop() {}
}

/**
 * Spout2 texture sender implementation for Windows.
 */
class SpoutStreamer(override val identifier: String) : TextureStreamer {
    override val isSupported: Boolean
        get() = System.getProperty("os.name").lowercase().contains("win")

    private var spout: SpoutApi? = null

    override fun start(width: Int, height: Int): Boolean {
        if (!isSupported) return false
        val api = SpoutApi.create() ?: run {
            logger.warn { "SpoutLibrary.dll not available; Spout sender '$identifier' is off." }
            return false
        }
        return try {
            api.setSenderName(identifier.take(255))
            spout = api
            logger.info { "Initialized Spout Sender '$identifier' (${width}x${height})" }
            true
        } catch (e: Throwable) {
            logger.error(e) { "Failed to start Spout sender '$identifier'" }
            api.release()
            false
        }
    }

    override fun update(textureId: Int, width: Int, height: Int) {
        val api = spout ?: return
        // The sender is created on the first send. GL textures are bottom-up; invert gives Spout's top-down order.
        val hostFbo = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING)
        api.sendTexture(textureId, GL_TEXTURE_2D, width, height, true, hostFbo)
    }

    override fun stop() {
        val api = spout ?: return
        logger.info { "Closing Spout Sender '$identifier'" }
        try { api.releaseSender() } finally { api.release() }
        spout = null
    }
}

/**
 * Syphon texture server implementation for macOS.
 */
class SyphonStreamer(override val identifier: String) : TextureStreamer {
    override val isSupported: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")

    private var active = false
    private var serverPtr: Pointer? = null
    private val bridge = SyphonBridge()

    override fun start(width: Int, height: Int): Boolean {
        if (!isSupported) return false
        if (!bridge.isAvailable()) {
            logger.warn { "Syphon bridge not available. Frame sharing disabled." }
            return false
        }
        logger.info { "Initializing Syphon Server '$identifier' (${width}x${height})" }
        serverPtr = bridge.createServer(identifier)
        active = serverPtr != null
        return active
    }

    override fun update(textureId: Int, width: Int, height: Int) {
        if (!active || serverPtr == null) return
        bridge.publishTexture(serverPtr!!, textureId, width, height)
    }

    override fun stop() {
        if (active && serverPtr != null) {
            logger.info { "Closing Syphon Server '$identifier'" }
            bridge.stopServer(serverPtr!!)
            serverPtr = null
            active = false
        }
    }
}

/**
 * Linux texture sharing bridge (PipeWire / DMA-BUF / Shared Memory).
 */
class LinuxTextureBridge(override val identifier: String) : TextureStreamer {
    override val isSupported: Boolean
        get() = System.getProperty("os.name").lowercase().contains("linux")

    private val bridge = llm.slop.liquidlsd.rendering.pipewire.PipeWireBridge()
    private var pboPipeline: llm.slop.liquidlsd.export.PboReadbackPipeline? = null
    private var active = false

    override fun start(width: Int, height: Int): Boolean {
        if (!isSupported) return false
        if (!bridge.isAvailable) {
            logger.warn { "PipeWire library not available on system. Video sharing disabled for '$identifier'." }
            return false
        }

        logger.info { "Initializing Linux PipeWire Texture Bridge '$identifier' (${width}x${height})" }
        active = bridge.createServer(identifier, width, height)
        if (active) {
            pboPipeline = llm.slop.liquidlsd.export.PboReadbackPipeline(width, height)
        }
        return active
    }

    override fun update(textureId: Int, width: Int, height: Int) {
        if (!active || !bridge.isConnected) return
        // A PipeWire format has one fixed size: a new output size means a new stream.
        if (width != bridge.width || height != bridge.height) {
            logger.info { "PipeWire stream '$identifier' size ${bridge.width}x${bridge.height} -> ${width}x$height: reconnecting" }
            bridge.stopServer()
            active = bridge.createServer(identifier, width, height)
            if (!active) return
        }

        var pipeline = pboPipeline
        if (pipeline == null || pipeline.width != width || pipeline.height != height) {
            pipeline?.dispose()
            pipeline = llm.slop.liquidlsd.export.PboReadbackPipeline(width, height)
            pboPipeline = pipeline
        }

        // Read the texture we were given, not the window: attach it to our own framebuffer first.
        if (readFbo == 0) readFbo = org.lwjgl.opengl.GL30.glGenFramebuffers()
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, readFbo)
        org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, textureId, 0)
        val frameBuffer = pipeline.readFrameAsync(readFbo)
        if (frameBuffer != null) {
            bridge.publishFrameBuffer(frameBuffer, width, height)
        }
    }

    private var readFbo = 0

    override fun stop() {
        if (active) {
            logger.info { "Closing Linux PipeWire Texture Bridge '$identifier'" }
            bridge.stopServer()
            pboPipeline?.dispose()
            pboPipeline = null
            active = false
            if (readFbo != 0) { org.lwjgl.opengl.GL30.glDeleteFramebuffers(readFbo); readFbo = 0 }
        }
    }

    fun getDriverStatus(): String = when {
        !bridge.isAvailable -> "PipeWire Unavailable"
        !active -> "Disabled"
        !bridge.isNegotiated -> "PipeWire (waiting for a reader)"
        else -> "PipeWire (shared memory)"
    }
}

/**
 * Global manager for live texture streaming endpoints.
 */
object TextureStreamerManager {
    
    private val streamers = mutableMapOf<VideoOutputEndpoint, TextureStreamer>()
    private val rescalers = mutableMapOf<VideoOutputEndpoint, FBO>()
    
    private val osName = System.getProperty("os.name").lowercase()

    fun getBackendName(): String = when {
        osName.contains("win") -> "Spout2 (Windows)"
        osName.contains("mac") -> "Syphon (macOS)"
        osName.contains("linux") -> {
            val lib = llm.slop.liquidlsd.rendering.pipewire.PipeWireLibrary.load()
            if (lib != null) "PipeWire 0.3 (Linux)" else "Linux Texture Bridge (Unavailable)"
        }
        else -> "None"
    }

    fun getStreamer(endpoint: VideoOutputEndpoint): TextureStreamer? = streamers[endpoint]

    fun update(endpoint: VideoOutputEndpoint, textureId: Int, width: Int, height: Int, renderer: Renderer) {
        val config = UITheme.settings.videoOutputConfigs[endpoint] ?: VideoOutputConfig()
        
        if (!config.isEnabled) {
            if (streamers.containsKey(endpoint)) {
                streamers.remove(endpoint)?.stop()
                rescalers.remove(endpoint)?.dispose()
            }
            return
        }

        val targetWidth = config.getEffectiveWidth(width)
        val targetHeight = config.getEffectiveHeight(height)
        
        val streamer = streamers.getOrPut(endpoint) {
            val name = config.customName.ifBlank { "LiquidLSD-${endpoint.name}" }.take(255)
            createStreamer(name).apply { start(targetWidth, targetHeight) }
        }

        var sourceTex = textureId
        if (targetWidth != width || targetHeight != height || config.scalingMode != UITheme.OutputScaleMode.STRETCH) {
            var fbo = rescalers[endpoint]
            if (fbo == null || fbo.width != targetWidth || fbo.height != targetHeight) {
                fbo?.dispose()
                fbo = FBO(targetWidth, targetHeight)
                rescalers[endpoint] = fbo
            }
            
            renderer.rescale(textureId, width, height, fbo, config.scalingMode)
            sourceTex = fbo.texture
        }

        streamer.update(sourceTex, targetWidth, targetHeight)
    }

    private fun createStreamer(name: String): TextureStreamer = when {
        osName.contains("win") -> SpoutStreamer(name)
        osName.contains("mac") -> SyphonStreamer(name)
        osName.contains("linux") -> LinuxTextureBridge(name)
        else -> NullTextureStreamer(name)
    }

    fun shutdown() {
        streamers.values.forEach { it.stop() }
        streamers.clear()
        rescalers.values.forEach { it.dispose() }
        rescalers.clear()
    }
}
