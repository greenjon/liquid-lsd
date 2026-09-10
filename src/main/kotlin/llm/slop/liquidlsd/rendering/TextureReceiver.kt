package llm.slop.liquidlsd.rendering

import com.sun.jna.Pointer
import llm.slop.liquidlsd.rendering.pipewire.PipeWireLibrary
import llm.slop.liquidlsd.rendering.pipewire.SpaData
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

    override var currentWidth = 1920
        private set
    override var currentHeight = 1080
        private set

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

            val loopPtr = lib.pw_thread_loop_get_loop(threadLoop!!) ?: run {
                lib.pw_thread_loop_unlock(threadLoop!!)
                stop()
                return false
            }

            context = lib.pw_context_new(loopPtr, null, 0) ?: run {
                lib.pw_thread_loop_unlock(threadLoop!!)
                stop()
                return false
            }

            core = lib.pw_context_connect(context!!, null, 0) ?: run {
                lib.pw_thread_loop_unlock(threadLoop!!)
                stop()
                return false
            }

            val props = lib.pw_properties_new(null, null)
            if (props != null) {
                lib.pw_properties_set(props, "media.type", "Video")
                lib.pw_properties_set(props, "media.category", "Capture")
                lib.pw_properties_set(props, "node.name", "LiquidLSD-Receiver")
                lib.pw_properties_set(props, "node.description", "Liquid LSD PipeWire Receiver")
                if (serverName.isNotBlank()) {
                    lib.pw_properties_set(props, "target.object", serverName)
                }
            }

            stream = lib.pw_stream_new(core!!, "LiquidLSD-VideoIngest", props) ?: run {
                lib.pw_thread_loop_unlock(threadLoop!!)
                stop()
                return false
            }

            val flags = PipeWireLibrary.PW_STREAM_FLAG_AUTOCONNECT or
                    PipeWireLibrary.PW_STREAM_FLAG_MAP_BUFFERS or
                    PipeWireLibrary.PW_STREAM_FLAG_ALLOC_BUFFERS

            val res = lib.pw_stream_connect(
                stream!!,
                PipeWireLibrary.PW_DIRECTION_INPUT,
                -1, // PW_ID_ANY
                flags,
                null,
                0
            )

            lib.pw_thread_loop_unlock(threadLoop!!)

            if (res < 0) {
                logger.warn { "Failed to connect PipeWire receiver stream for target '$serverName' (code: $res)" }
                stop()
                return false
            }

            active = true
            logger.info { "Connected PipeWire Receiver to stream/target: $serverName" }
            return true
        } catch (e: Throwable) {
            logger.error(e) { "Failed to start PipeWire receiver for '$serverName'" }
            stop()
            return false
        }
    }

    override fun update(): Int {
        val lib = pw
        if (!active || stream == null || threadLoop == null || lib == null) return 0

        var textureToReturn = localTextureId

        try {
            lib.pw_thread_loop_lock(threadLoop!!)
            val pwBufPtr = lib.pw_stream_dequeue_buffer(stream!!)
            if (pwBufPtr != null) {
                try {
                    val spaBufPtr = pwBufPtr.getPointer(0)
                    if (spaBufPtr != null) {
                        val datasPtr = spaBufPtr.getPointer(8)
                        if (datasPtr != null) {
                            val spaData = SpaData(datasPtr)
                            spaData.read()

                            val dataPtr = spaData.data
                            val dataSize = spaData.datasize.coerceAtLeast(spaData.maxsize)

                            if (dataPtr != null && dataSize > 0) {
                                if (localTextureId == 0) {
                                    localTextureId = glGenTextures()
                                    glBindTexture(GL_TEXTURE_2D, localTextureId)
                                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, currentWidth, currentHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as java.nio.ByteBuffer?)
                                    glBindTexture(GL_TEXTURE_2D, 0)
                                }

                                val byteBuffer = dataPtr.getByteBuffer(0, dataSize.toLong())
                                glBindTexture(GL_TEXTURE_2D, localTextureId)
                                if (dataSize >= currentWidth * currentHeight * 4) {
                                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, currentWidth, currentHeight, GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer)
                                } else {
                                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, currentWidth, currentHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer)
                                }
                                glBindTexture(GL_TEXTURE_2D, 0)
                                textureToReturn = localTextureId
                            }
                        }
                    }
                } finally {
                    lib.pw_stream_queue_buffer(stream!!, pwBufPtr)
                }
            }
        } catch (e: Throwable) {
            logger.debug { "Error receiving PipeWire buffer: ${e.message}" }
        } finally {
            lib.pw_thread_loop_unlock(threadLoop!!)
        }

        return textureToReturn
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
        logger.info { "Stopped PipeWire Receiver" }
    }
}

