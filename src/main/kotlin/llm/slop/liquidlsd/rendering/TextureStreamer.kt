package llm.slop.liquidlsd.rendering

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D

private val logger = KotlinLogging.logger {}

/**
 * JNA Interface for Spout2 (Windows)
 */
interface SpoutLibrary : Library {
    fun CreateSpout(): Pointer
    fun ReleaseSpout(ptr: Pointer)
    fun SendTexture(ptr: Pointer, textureID: Int, textureTarget: Int, width: Int, height: Int, invert: Boolean, hostFBO: Int): Boolean
    fun ReleaseSender(ptr: Pointer)
    fun SetSenderName(ptr: Pointer, name: String): Boolean
    
    // Receiver functions
    fun CreateReceiver(ptr: Pointer, name: ByteArray, width: IntArray, height: IntArray, bUseActive: Boolean): Boolean
    fun ReceiveTexture(ptr: Pointer, name: ByteArray, width: IntArray, height: IntArray, textureID: Int, textureTarget: Int, invert: Boolean, hostFBO: Int): Boolean
    fun ReleaseReceiver(ptr: Pointer)
    
    // Discovery functions
    fun GetSenderCount(ptr: Pointer): Int
    fun GetSender(ptr: Pointer, index: Int, sendername: ByteArray, maxsize: Int): Boolean
}

/**
 * Minimal Objective-C Runtime interface via JNA for Syphon (macOS)
 */
interface ObjCLibrary : Library {
    fun objc_getClass(name: String): Pointer
    fun sel_registerName(name: String): Pointer
    fun objc_msgSend(receiver: Pointer, selector: Pointer, vararg args: Any?): Pointer
    
    // For doubles/floats on x64, objc_msgSend_fpret might be needed, 
    // but for pointers/ints objc_msgSend is fine.
}

/**
 * Foundation / CoreFoundation for loading frameworks
 */
interface FoundationLibrary : Library {
    fun NSFullUserName(): Pointer // Dummy to ensure load
}

/**
 * Native Syphon Bridge using Objective-C Runtime (no JSyphon needed)
 */
class SyphonBridge {
    private val objc: ObjCLibrary by lazy { Native.load("objc", ObjCLibrary::class.java) }
    private var syphonServerClass: Pointer? = null
    private var syphonClientClass: Pointer? = null
    private var syphonServerDirectoryClass: Pointer? = null
    
    init {
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            try {
                // 1. Add our natives folder to the search path
                val nativesPath = java.io.File("library/natives").absolutePath
                System.setProperty("jna.library.path", "${System.getProperty("jna.library.path") ?: ""}${java.io.File.pathSeparator}$nativesPath")

                // 2. Try to load the Syphon framework from common locations
                // JNA will look in /Library/Frameworks, ~/Library/Frameworks, and our jna.library.path
                try {
                    Native.load("Syphon", Library::class.java)
                } catch (e: Throwable) {
                    logger.debug { "Syphon framework not found in standard paths, attempting manual load from library/natives" }
                    val frameworkPath = java.io.File(nativesPath, "Syphon.framework/Versions/A/Syphon").absolutePath
                    if (java.io.File(frameworkPath).exists()) {
                        Native.load(frameworkPath, Library::class.java)
                    }
                }

                // 3. Resolve the classes
                syphonServerClass = objc.objc_getClass("SyphonServer")
                syphonClientClass = objc.objc_getClass("SyphonClient")
                syphonServerDirectoryClass = objc.objc_getClass("SyphonServerDirectory")
                
                if (syphonServerClass == null) {
                    logger.warn { "SyphonServer class not found. Ensure Syphon.framework is in library/natives/ or /Library/Frameworks/" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to initialize Syphon bridge" }
            }
        }
    }

    fun isAvailable() = syphonServerClass != null

    fun createServer(name: String): Pointer? {
        val cls = syphonServerClass ?: return null
        val selAlloc = objc.sel_registerName("alloc")
        val selInit = objc.sel_registerName("initWithName:options:handler:")
        
        val instance = objc.objc_msgSend(cls, selAlloc)
        // options and handler can be null (Pointer.NULL)
        return objc.objc_msgSend(instance, selInit, name, null, null)
    }

    fun stopServer(serverPtr: Pointer) {
        val selStop = objc.sel_registerName("stop")
        objc.objc_msgSend(serverPtr, selStop)
        // Note: in ARC environments we might need to release, 
        // but Syphon objects usually handle their own lifecycle via stop.
    }

    // Named JNA Structures for Syphon's publishFrameTexture — reused across frames, zero per-frame allocation.
    // NSSize and NSRect fields are Double (CGFloat = double on all modern Apple platforms).
    class NSSize : com.sun.jna.Structure() {
        @JvmField var width:  Double = 0.0
        @JvmField var height: Double = 0.0
        override fun getFieldOrder() = listOf("width", "height")
    }
    class NSRect : com.sun.jna.Structure() {
        @JvmField var x:      Double = 0.0
        @JvmField var y:      Double = 0.0
        @JvmField var width:  Double = 0.0
        @JvmField var height: Double = 0.0
        override fun getFieldOrder() = listOf("x", "y", "width", "height")
    }

    private val publishSize = NSSize()
    private val publishRect = NSRect()

    fun publishTexture(serverPtr: Pointer, textureId: Int, w: Int, h: Int) {
        val selPublish = objc.sel_registerName("publishFrameTexture:textureTarget:imageRegion:textureDimensions:flipped:")
        
        // Reuse pre-allocated structures; only update the dimension fields
        publishSize.width  = w.toDouble(); publishSize.height = h.toDouble()
        publishRect.x = 0.0; publishRect.y = 0.0; publishRect.width = w.toDouble(); publishRect.height = h.toDouble()

        // Texture target GL_TEXTURE_2D = 0x0DE1
        objc.objc_msgSend(serverPtr, selPublish, textureId, 0x0DE1, publishRect, publishSize, false)
    }

    // --- Client / Receiver ---
    
    fun getAvailableServers(): List<String> {
        val dirCls = syphonServerDirectoryClass ?: return emptyList()
        val selShared = objc.sel_registerName("sharedDirectory")
        val selServers = objc.sel_registerName("servers")
        val selCount = objc.sel_registerName("count")
        val selObjectAtIndex = objc.sel_registerName("objectAtIndex:")
        val selObjectForKey = objc.sel_registerName("objectForKey:")
        val selUTF8String = objc.sel_registerName("UTF8String")

        val nsStringCls = objc.objc_getClass("NSString")
        val selStringWithUTF8String = objc.sel_registerName("stringWithUTF8String:")

        val directory = objc.objc_msgSend(dirCls, selShared)
        val serversArray = objc.objc_msgSend(directory, selServers)
        
        val countPtr = objc.objc_msgSend(serversArray, selCount)
        val count = Pointer.nativeValue(countPtr).toInt()

        val results = mutableListOf<String>()
        val appNameKey = objc.objc_msgSend(nsStringCls, selStringWithUTF8String, "SyphonServerDescriptionAppNameKey")
        val nameKey = objc.objc_msgSend(nsStringCls, selStringWithUTF8String, "SyphonServerDescriptionNameKey")
        
        for (i in 0 until count) {
            // Need to pass index properly. JNA objc_msgSend with integer args might need a wrapper or primitive wrapper?
            // Actually, objectAtIndex: takes NSUInteger, so a long/int.
            val dict = objc.objc_msgSend(serversArray, selObjectAtIndex, i.toLong())
            
            val appNamePtr = objc.objc_msgSend(dict, selObjectForKey, appNameKey)
            val namePtr = objc.objc_msgSend(dict, selObjectForKey, nameKey)
            
            val appNameStrPtr = if (appNamePtr != null && Pointer.nativeValue(appNamePtr) != 0L) objc.objc_msgSend(appNamePtr, selUTF8String) else null
            val nameStrPtr = if (namePtr != null && Pointer.nativeValue(namePtr) != 0L) objc.objc_msgSend(namePtr, selUTF8String) else null
            
            val appName = appNameStrPtr?.getString(0, "UTF-8") ?: ""
            val name = nameStrPtr?.getString(0, "UTF-8") ?: ""
            
            val fullName = if (name.isNotEmpty()) "$appName - $name" else appName
            if (fullName.isNotEmpty()) {
                results.add(fullName)
            }
        }
        return results
    }

    fun createClient(serverName: String): Pointer? {
        val clientCls = syphonClientClass ?: return null
        val selAlloc = objc.sel_registerName("alloc")
        val selInit = objc.sel_registerName("initWithServerDescription:options:newFrameHandler:")
        
        // Find the matching server description dict
        val dirCls = syphonServerDirectoryClass ?: return null
        val selShared = objc.sel_registerName("sharedDirectory")
        val selServers = objc.sel_registerName("servers")
        val selCount = objc.sel_registerName("count")
        val selObjectAtIndex = objc.sel_registerName("objectAtIndex:")
        val selObjectForKey = objc.sel_registerName("objectForKey:")
        val selUTF8String = objc.sel_registerName("UTF8String")

        val nsStringCls = objc.objc_getClass("NSString")
        val selStringWithUTF8String = objc.sel_registerName("stringWithUTF8String:")

        val directory = objc.objc_msgSend(dirCls, selShared)
        val serversArray = objc.objc_msgSend(directory, selServers)
        
        val countPtr = objc.objc_msgSend(serversArray, selCount)
        val count = Pointer.nativeValue(countPtr).toInt()

        val appNameKey = objc.objc_msgSend(nsStringCls, selStringWithUTF8String, "SyphonServerDescriptionAppNameKey")
        val nameKey = objc.objc_msgSend(nsStringCls, selStringWithUTF8String, "SyphonServerDescriptionNameKey")
        
        var targetDict: Pointer? = null
        for (i in 0 until count) {
            val dict = objc.objc_msgSend(serversArray, selObjectAtIndex, i.toLong())
            val appNamePtr = objc.objc_msgSend(dict, selObjectForKey, appNameKey)
            val namePtr = objc.objc_msgSend(dict, selObjectForKey, nameKey)
            
            val appNameStrPtr = if (appNamePtr != null && Pointer.nativeValue(appNamePtr) != 0L) objc.objc_msgSend(appNamePtr, selUTF8String) else null
            val nameStrPtr = if (namePtr != null && Pointer.nativeValue(namePtr) != 0L) objc.objc_msgSend(namePtr, selUTF8String) else null
            
            val appName = appNameStrPtr?.getString(0, "UTF-8") ?: ""
            val name = nameStrPtr?.getString(0, "UTF-8") ?: ""
            val fullName = if (name.isNotEmpty()) "$appName - $name" else appName
            
            if (fullName == serverName) {
                targetDict = dict
                break
            }
        }
        
        if (targetDict == null) return null
        
        val instance = objc.objc_msgSend(clientCls, selAlloc)
        return objc.objc_msgSend(instance, selInit, targetDict, null, null)
    }

    fun hasNewFrame(clientPtr: Pointer): Boolean {
        val selHasNewFrame = objc.sel_registerName("hasNewFrame")
        val res = objc.objc_msgSend(clientPtr, selHasNewFrame)
        return res != null && Pointer.nativeValue(res) != 0L
    }

    fun newFrameImage(clientPtr: Pointer): Pointer? {
        val selNewFrameImage = objc.sel_registerName("newFrameImage")
        return objc.objc_msgSend(clientPtr, selNewFrameImage)
    }

    fun textureNameForImage(imagePtr: Pointer): Int {
        val selTextureName = objc.sel_registerName("textureName")
        val res = objc.objc_msgSend(imagePtr, selTextureName)
        return Pointer.nativeValue(res).toInt()
    }
    
    fun textureSizeForImage(imagePtr: Pointer): IntArray {
        val selTextureSize = objc.sel_registerName("textureSize")
        // textureSize returns NSSize struct. Since JNA objc_msgSend with struct return
        // can be tricky, we'll assume standard SyphonImage textureSize works.
        // Actually, returning a struct by value requires objc_msgSend_stret on some archs.
        // Let's skip size query if we can, or just try it:
        // Or we don't query size here and just let LiquidLSD query GL_TEXTURE_WIDTH
        return intArrayOf(0, 0) 
    }

    fun stopClient(clientPtr: Pointer) {
        val selStop = objc.sel_registerName("stop")
        objc.objc_msgSend(clientPtr, selStop)
    }
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

        var pipeline = pboPipeline
        if (pipeline == null || pipeline.width != width || pipeline.height != height) {
            pipeline?.dispose()
            pipeline = llm.slop.liquidlsd.export.PboReadbackPipeline(width, height)
            pboPipeline = pipeline
        }

        val frameBuffer = pipeline.readFrameAsync(0)
        if (frameBuffer != null) {
            bridge.publishFrameBuffer(frameBuffer, width, height)
        }
    }

    override fun stop() {
        if (active) {
            logger.info { "Closing Linux PipeWire Texture Bridge '$identifier'" }
            bridge.stopServer()
            pboPipeline?.dispose()
            pboPipeline = null
            active = false
        }
    }

    fun getDriverStatus(): String = when {
        !bridge.isAvailable -> "PipeWire Unavailable"
        !active -> "Disabled"
        bridge.isDmaBufSupported -> "PipeWire 0.3 (DMA-BUF Active)"
        else -> "PipeWire 0.3 (MemFd Fallback)"
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
