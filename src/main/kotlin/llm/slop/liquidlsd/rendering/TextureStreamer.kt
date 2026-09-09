package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D

private val logger = KotlinLogging.logger {}

/**
 * JNA Interface for Spout2 (Windows)
 */
interface SpoutLibrary : com.sun.jna.Library {
    fun CreateSpout(): com.sun.jna.Pointer
    fun ReleaseSpout(ptr: com.sun.jna.Pointer)
    fun SendTexture(ptr: com.sun.jna.Pointer, textureID: Int, textureTarget: Int, width: Int, height: Int, invert: Boolean, hostFBO: Int): Boolean
    fun ReleaseSender(ptr: com.sun.jna.Pointer)
    fun SetSenderName(ptr: com.sun.jna.Pointer, name: String): Boolean
}

/**
 * Objective-C / JNI Bridge for Syphon (macOS)
 */
class SyphonBridge {
    // In a real implementation, this would be a JNI wrapper like JSyphon
    // or use LWJGL's Objective-C bridge.
    
    fun createServer(name: String): Long = 0L
    fun stopServer(serverPtr: Long) {}
    fun publishTexture(serverPtr: Long, textureId: Int, target: Int, x: Int, y: Int, w: Int, h: Int, isFlipped: Boolean) {}
}

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

    private var active = false
    private var spoutPtr: com.sun.jna.Pointer? = null
    private var spoutLib: SpoutLibrary? = null

    override fun start(width: Int, height: Int): Boolean {
        if (!isSupported) return false
        try {
            val nativesPath = java.io.File("library/natives").absolutePath
            System.setProperty("jna.library.path", "${System.getProperty("jna.library.path") ?: ""}${java.io.File.pathSeparator}$nativesPath")
            
            spoutLib = com.sun.jna.Native.load("SpoutLibrary", SpoutLibrary::class.java)
            spoutPtr = spoutLib?.CreateSpout()
            spoutLib?.SetSenderName(spoutPtr!!, identifier)
            logger.info { "Initialized Spout Sender '$identifier' (${width}x${height})" }
            active = true
            return true
        } catch (e: Throwable) {
            logger.error(e) { "Failed to load SpoutLibrary.dll. Ensure it is in the system path or library/natives/" }
            return false
        }
    }

    override fun update(textureId: Int, width: Int, height: Int) {
        if (!active || spoutPtr == null) return
        spoutLib?.SendTexture(spoutPtr!!, textureId, GL_TEXTURE_2D, width, height, false, 0)
    }

    override fun stop() {
        if (active && spoutPtr != null) {
            logger.info { "Closing Spout Sender '$identifier'" }
            spoutLib?.ReleaseSender(spoutPtr!!)
            spoutLib?.ReleaseSpout(spoutPtr!!)
            spoutPtr = null
            active = false
        }
    }
}

/**
 * Syphon texture server implementation for macOS.
 */
class SyphonStreamer(override val identifier: String) : TextureStreamer {
    override val isSupported: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")

    private var active = false
    private var serverPtr: Long = 0
    private val bridge = SyphonBridge()

    override fun start(width: Int, height: Int): Boolean {
        if (!isSupported) return false
        logger.info { "Initializing Syphon Server '$identifier' (${width}x${height})" }
        serverPtr = bridge.createServer(identifier)
        active = true
        return true
    }

    override fun update(textureId: Int, width: Int, height: Int) {
        if (!active || serverPtr == 0L) return
        bridge.publishTexture(serverPtr, textureId, GL_TEXTURE_2D, 0, 0, width, height, false)
    }

    override fun stop() {
        if (active && serverPtr != 0L) {
            logger.info { "Closing Syphon Server '$identifier'" }
            bridge.stopServer(serverPtr)
            serverPtr = 0
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

    private var active = false

    override fun start(width: Int, height: Int): Boolean {
        if (!isSupported) return false
        logger.info { "Initializing Linux Texture Bridge '$identifier' (${width}x${height})" }
        active = true
        // TODO: PipeWire DMA-BUF initialization
        return true
    }

    override fun update(textureId: Int, width: Int, height: Int) {
        if (!active) return
    }

    override fun stop() {
        if (active) {
            logger.info { "Closing Linux Texture Bridge '$identifier'" }
            active = false
        }
    }
}

/**
 * Global manager for live texture streaming endpoints.
 */
object TextureStreamerManager {
    
    private val streamers = mutableMapOf<VideoOutputEndpoint, TextureStreamer>()
    private val rescalers = mutableMapOf<VideoOutputEndpoint, FBO>()
    
    private val osName = System.getProperty("os.name").lowercase()

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
            val name = config.customName.ifBlank { "LiquidLSD-${endpoint.name}" }
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
