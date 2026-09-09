package llm.slop.liquidlsd.rendering

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
        
        val nameBytes = ByteArray(256)
        val w = intArrayOf(currentWidth)
        val h = intArrayOf(currentHeight)
        
        val success = spoutLib?.ReceiveTexture(spoutPtr!!, nameBytes, w, h, localTextureId, GL_TEXTURE_2D, false, 0) == true
        if (success) {
            // Re-allocate if size changed
            if (w[0] != currentWidth || h[0] != currentHeight) {
                currentWidth = w[0]
                currentHeight = h[0]
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
