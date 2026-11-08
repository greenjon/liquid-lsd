package llm.slop.liquidlsd.rendering

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/** The only plain C export of SpoutLibrary.dll: `extern "C" SPOUTHANDLE WINAPI GetSpout(VOID)`. */
interface SpoutDll : Library {
    fun GetSpout(): Pointer?
}

/**
 * One SpoutLibrary object (Spout2 2.007.017, the bundled `library/natives/SpoutLibrary.dll`). The DLL exports only
 * `GetSpout()`, which returns a C++ object; every other call goes through its vtable, with the object as the first
 * argument (Windows x64: `this` in RCX, the normal calling convention).
 *
 * Slot numbers come from `SPOUTLIBRARY` in SpoutLibrary.h 2.007.017, in MSVC's vtable order: overloads of one name sit
 * together at the first one's place, last declared first, so the two `GetName` overloads (declared 7th and 105th) put
 * entries 8 to 104 one slot later. Checked against the DLL itself: slots 8 and 24 (`GetName()` and `GetSenderName()`)
 * jump to the same function, slot 4 fixes up a byte-sized 5th argument (`SendTexture`'s `bInvert`), and the table
 * ends after slot 171 (`Release`). A different SpoutLibrary.dll needs these checked again.
 *
 * C++ `bool` comes back in AL only, so results are masked to the low byte.
 */
class SpoutApi internal constructor(private val obj: Pointer) {
    private val vtable: Pointer = obj.getPointer(0)
    private fun fn(slot: Int): Function = Function.getFunction(vtable.getPointer(slot * 8L))
    private fun bool(slot: Int, vararg args: Any): Boolean = (fn(slot).invokeInt(arrayOf(obj, *args)) and 0xFF) != 0
    private fun void(slot: Int, vararg args: Any) = fn(slot).invokeVoid(arrayOf(obj, *args))

    // Sender
    fun setSenderName(name: String) = void(0, name)
    fun releaseSender() = void(2, 0)
    /** [invert] true flips the GL texture (bottom-up) into the DirectX order Spout shares, as the SDK default does. */
    fun sendTexture(textureId: Int, target: Int, width: Int, height: Int, invert: Boolean, hostFbo: Int): Boolean =
        bool(4, textureId, target, width, height, if (invert) 1 else 0, hostFbo)

    // Receiver
    fun setReceiverName(name: String) = void(16, name)
    fun releaseReceiver() = void(18)
    fun receiveTexture(textureId: Int, target: Int, invert: Boolean, hostFbo: Int): Boolean =
        bool(19, textureId, target, if (invert) 1 else 0, hostFbo)
    /** True after [receiveTexture] when the sender appeared or changed size: reallocate the texture, then receive again. */
    fun isUpdated(): Boolean = bool(21)
    fun isConnected(): Boolean = bool(22)
    fun senderWidth(): Int = fn(25).invokeInt(arrayOf(obj))
    fun senderHeight(): Int = fn(26).invokeInt(arrayOf(obj))

    // Sender list
    fun senderCount(): Int = fn(111).invokeInt(arrayOf(obj))
    fun sender(index: Int, buf: ByteArray): Boolean = bool(112, index, buf, buf.size)

    /** Frees the object. Don't use this instance afterwards. */
    fun release() = void(171)

    companion object {
        @Volatile private var dll: SpoutDll? = null
        @Volatile private var failed = false

        val isAvailable: Boolean get() = load() != null

        private fun load(): SpoutDll? {
            if (failed) return null
            dll?.let { return it }
            return try {
                val natives = java.io.File("library/natives").absolutePath
                val path = System.getProperty("jna.library.path")
                if (path?.contains(natives) != true) {
                    System.setProperty("jna.library.path", listOfNotNull(path?.takeIf { it.isNotBlank() }, natives).joinToString(java.io.File.pathSeparator))
                }
                Native.load("SpoutLibrary", SpoutDll::class.java).also { dll = it }
            } catch (e: Throwable) {
                failed = true
                logger.warn { "SpoutLibrary.dll not loaded (${e.message}); Spout sharing is off." }
                null
            }
        }

        /** A new SpoutLibrary object, or null when the DLL isn't there (not Windows, or not installed). */
        fun create(): SpoutApi? {
            val p = load()?.GetSpout() ?: return null
            return if (Pointer.nativeValue(p) == 0L) null else SpoutApi(p)
        }
    }
}
