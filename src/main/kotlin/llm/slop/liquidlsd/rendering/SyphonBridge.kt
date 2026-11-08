package llm.slop.liquidlsd.rendering

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/** `CGLGetCurrentContext` from the OpenGL framework: Syphon servers and clients need the app's GL context. */
interface CglLibrary : Library {
    fun CGLGetCurrentContext(): Pointer?
}

/**
 * Syphon (macOS) through the Objective-C runtime, against the bundled Syphon.framework (headers in
 * `library/natives/Syphon.framework/Versions/A/Headers`).
 *
 * Every message goes through `objc_msgSend` called as a fixed-signature function (JNA [Function.invoke] with the exact
 * argument types). Calling it as a C variadic function is wrong on Apple Silicon, where variadic arguments go on the
 * stack, and plain JNA structures are passed by reference; NSRect and NSSize must go by value ([Structure.ByValue]).
 * Objects made with alloc/init or returned by a `new...` method are released here (no ARC).
 */
class SyphonBridge {
    @Structure.FieldOrder("width", "height")
    open class NSSize : Structure(), Structure.ByValue {
        @JvmField var width: Double = 0.0
        @JvmField var height: Double = 0.0
    }

    @Structure.FieldOrder("x", "y", "width", "height")
    open class NSRect : Structure(), Structure.ByValue {
        @JvmField var x: Double = 0.0
        @JvmField var y: Double = 0.0
        @JvmField var width: Double = 0.0
        @JvmField var height: Double = 0.0
    }

    private companion object {
        /** Set after the first failed framework lookup so later bridges skip the reload and stay quiet. */
        @Volatile var frameworkMissing = false
        @Volatile var syphon: NativeLibrary? = null
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        val objc: NativeLibrary by lazy { NativeLibrary.getInstance("objc") }
        val msgSend: Function by lazy { objc.getFunction("objc_msgSend") }
        val getClass: Function by lazy { objc.getFunction("objc_getClass") }
        val selRegister: Function by lazy { objc.getFunction("sel_registerName") }
        val cgl: CglLibrary by lazy { Native.load("OpenGL", CglLibrary::class.java) }
        val selectors = java.util.concurrent.ConcurrentHashMap<String, Pointer>()
    }

    private fun sel(name: String): Pointer = selectors.getOrPut(name) { selRegister.invokePointer(arrayOf(name)) }
    private fun cls(name: String): Pointer? = getClass.invokePointer(arrayOf(name))?.takeIf { Pointer.nativeValue(it) != 0L }
    private fun obj(recv: Pointer, selector: String, vararg args: Any?): Pointer? =
        msgSend.invokePointer(arrayOf(recv, sel(selector), *args))?.takeIf { Pointer.nativeValue(it) != 0L }
    private fun long(recv: Pointer, selector: String, vararg args: Any?): Long = msgSend.invokeLong(arrayOf(recv, sel(selector), *args))
    private fun int(recv: Pointer, selector: String): Int = msgSend.invokeInt(arrayOf(recv, sel(selector)))
    /** BOOL comes back in the low byte only. */
    private fun bool(recv: Pointer, selector: String): Boolean = (msgSend.invokeInt(arrayOf(recv, sel(selector))) and 0xFF) != 0
    private fun void(recv: Pointer, selector: String, vararg args: Any?) = msgSend.invokeVoid(arrayOf(recv, sel(selector), *args))

    private var serverClass: Pointer? = null
    private var clientClass: Pointer? = null
    private var directoryClass: Pointer? = null

    init {
        if (isMac && !frameworkMissing) {
            try {
                if (syphon == null) {
                    val natives = java.io.File("library/natives").absolutePath
                    val bundled = java.io.File(natives, "Syphon.framework/Versions/A/Syphon")
                    syphon = try {
                        NativeLibrary.getInstance("Syphon")
                    } catch (e: Throwable) {
                        if (bundled.exists()) NativeLibrary.getInstance(bundled.absolutePath) else throw e
                    }
                }
                serverClass = cls("SyphonServer")
                clientClass = cls("SyphonClient")
                directoryClass = cls("SyphonServerDirectory")
                if (serverClass == null) {
                    frameworkMissing = true
                    logger.warn { "SyphonServer class not found. Ensure Syphon.framework is in library/natives/ or /Library/Frameworks/" }
                }
            } catch (e: Throwable) {
                frameworkMissing = true
                logger.warn { "Syphon not available: ${e.message}" }
            }
        }
    }

    fun isAvailable() = serverClass != null

    private fun nsString(s: String): Pointer? = cls("NSString")?.let { obj(it, "stringWithUTF8String:", s) }
    private fun utf8(nsString: Pointer?): String = nsString?.let { obj(it, "UTF8String") }?.getString(0, "UTF-8") ?: ""
    /** The value of an `extern NSString * const` key exported by the framework. */
    private fun key(name: String): Pointer? = try { syphon?.getGlobalVariableAddress(name)?.getPointer(0) } catch (e: Throwable) { null }

    // --- Server ---------------------------------------------------------------------------------

    /** `[[SyphonServer alloc] initWithName:context:options:]` on the current GL context. Call on the GL thread. */
    fun createServer(name: String): Pointer? {
        val cls = serverClass ?: return null
        val ctx = cgl.CGLGetCurrentContext() ?: return null
        val instance = obj(cls, "alloc") ?: return null
        return obj(instance, "initWithName:context:options:", nsString(name), ctx, null)
    }

    private val publishRect = NSRect()
    private val publishSize = NSSize()

    /** Publishes a GL_TEXTURE_2D of [w]x[h]. GL textures are bottom-up, which is what Syphon expects unflipped. */
    fun publishTexture(serverPtr: Pointer, textureId: Int, w: Int, h: Int) {
        publishRect.x = 0.0; publishRect.y = 0.0; publishRect.width = w.toDouble(); publishRect.height = h.toDouble()
        publishSize.width = w.toDouble(); publishSize.height = h.toDouble()
        void(serverPtr, "publishFrameTexture:textureTarget:imageRegion:textureDimensions:flipped:", textureId, GL_TEXTURE_2D_ID, publishRect, publishSize, 0.toByte())
    }

    fun stopServer(serverPtr: Pointer) {
        void(serverPtr, "stop")
        void(serverPtr, "release")
    }

    // --- Directory --------------------------------------------------------------------------------

    /** Server descriptions with the label the browser shows ("App - Name"). */
    private fun descriptions(): List<Pair<String, Pointer>> {
        val dirCls = directoryClass ?: return emptyList()
        val directory = obj(dirCls, "sharedDirectory") ?: return emptyList()
        val servers = obj(directory, "servers") ?: return emptyList()
        val appKey = key("SyphonServerDescriptionAppNameKey"); val nameKey = key("SyphonServerDescriptionNameKey")
        val out = mutableListOf<Pair<String, Pointer>>()
        for (i in 0 until long(servers, "count")) {
            val dict = obj(servers, "objectAtIndex:", i) ?: continue
            val app = appKey?.let { utf8(obj(dict, "objectForKey:", it)) } ?: ""
            val name = nameKey?.let { utf8(obj(dict, "objectForKey:", it)) } ?: ""
            val label = if (name.isNotEmpty()) "$app - $name" else app
            if (label.isNotEmpty()) out += label to dict
        }
        return out
    }

    /** Server labels; throws if a runtime call fails (the smoke test wants to know). */
    fun serverNames(): List<String> = descriptions().map { it.first }

    fun getAvailableServers(): List<String> = try { serverNames() } catch (e: Throwable) { emptyList() }

    // --- Client -----------------------------------------------------------------------------------

    /** `[[SyphonClient alloc] initWithServerDescription:context:options:newFrameHandler:nil]` on the current GL context. */
    fun createClient(serverName: String): Pointer? {
        val cls = clientClass ?: return null
        val ctx = cgl.CGLGetCurrentContext() ?: return null
        val dict = descriptions().firstOrNull { it.first == serverName }?.second ?: return null
        val instance = obj(cls, "alloc") ?: return null
        return obj(instance, "initWithServerDescription:context:options:newFrameHandler:", dict, ctx, null, null)
    }

    fun hasNewFrame(clientPtr: Pointer): Boolean = bool(clientPtr, "hasNewFrame")

    /** A retained SyphonImage (its texture is GL_TEXTURE_RECTANGLE); release it with [releaseObject]. */
    fun newFrameImage(clientPtr: Pointer): Pointer? = obj(clientPtr, "newFrameImage")

    fun textureNameForImage(imagePtr: Pointer): Int = int(imagePtr, "textureName")

    fun textureSizeForImage(imagePtr: Pointer): IntArray {
        val size = msgSend.invoke(NSSize::class.java, arrayOf(imagePtr, sel("textureSize"))) as NSSize
        return intArrayOf(size.width.toInt(), size.height.toInt())
    }

    fun releaseObject(p: Pointer) = void(p, "release")

    fun stopClient(clientPtr: Pointer) {
        void(clientPtr, "stop")
        void(clientPtr, "release")
    }
}

private const val GL_TEXTURE_2D_ID = 0x0DE1
