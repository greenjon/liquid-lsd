package llm.slop.liquidlsd.rendering

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SpoutLibrary.dll is Windows-only, so this builds a fake C++ object in native memory (a vtable of JNA callbacks) and
 * checks that each SpoutApi call lands on its slot with its arguments, and that C++ bool results are read from the
 * low byte only. The slot numbers themselves were checked against the real DLL (see SpoutApi).
 */
class SpoutApiTest {
    private val calls = mutableListOf<String>()

    interface Ptr0 : Callback { fun invoke(self: Pointer): Int }
    interface PtrStr : Callback { fun invoke(self: Pointer, s: String?): Int }
    interface PtrInt : Callback { fun invoke(self: Pointer, a: Int): Int }
    interface Send : Callback { fun invoke(self: Pointer, tex: Int, target: Int, w: Int, h: Int, invert: Int, fbo: Int): Int }
    interface Recv : Callback { fun invoke(self: Pointer, tex: Int, target: Int, invert: Int, fbo: Int): Int }
    interface GetSender : Callback { fun invoke(self: Pointer, index: Int, buf: Pointer, max: Int): Int }

    // Kept referenced for the test's life: native code holds these function pointers.
    private val keep = mutableListOf<Callback>()

    private fun fake(): SpoutApi {
        val vtable = Memory(8L * 172).also { it.clear() }
        fun put(slot: Int, cb: Callback) { keep += cb; vtable.setPointer(slot * 8L, CallbackReference.getFunctionPointer(cb)) }
        put(0, object : PtrStr { override fun invoke(self: Pointer, s: String?) = 0.also { calls += "setSenderName $s" } })
        put(2, object : PtrInt { override fun invoke(self: Pointer, a: Int) = 0.also { calls += "releaseSender $a" } })
        // A C++ bool true with garbage above AL: must read as true. 0x100 (AL = 0) must read as false.
        put(4, object : Send { override fun invoke(self: Pointer, tex: Int, target: Int, w: Int, h: Int, invert: Int, fbo: Int) = 0x7F01.also { calls += "send $tex $target $w $h $invert $fbo" } })
        put(16, object : PtrStr { override fun invoke(self: Pointer, s: String?) = 0.also { calls += "setReceiverName $s" } })
        put(18, object : Ptr0 { override fun invoke(self: Pointer) = 0.also { calls += "releaseReceiver" } })
        put(19, object : Recv { override fun invoke(self: Pointer, tex: Int, target: Int, invert: Int, fbo: Int) = 0x100.also { calls += "receive $tex $target $invert $fbo" } })
        put(21, object : Ptr0 { override fun invoke(self: Pointer) = 1.also { calls += "isUpdated" } })
        put(22, object : Ptr0 { override fun invoke(self: Pointer) = 1.also { calls += "isConnected" } })
        put(25, object : Ptr0 { override fun invoke(self: Pointer) = 1280.also { calls += "width" } })
        put(26, object : Ptr0 { override fun invoke(self: Pointer) = 720.also { calls += "height" } })
        put(111, object : Ptr0 { override fun invoke(self: Pointer) = 2.also { calls += "count" } })
        put(112, object : GetSender { override fun invoke(self: Pointer, index: Int, buf: Pointer, max: Int): Int { buf.setString(0, "Resolume $index"); calls += "sender $index $max"; return 1 } })
        put(171, object : Ptr0 { override fun invoke(self: Pointer) = 0.also { calls += "release" } })
        val obj = Memory(16).also { it.clear(); it.setPointer(0, vtable) }
        objs += obj; tables += vtable
        return SpoutApi(obj)
    }
    // Native memory for the fake object and its vtable, kept reachable while the test runs.
    private val objs = mutableListOf<Memory>()
    private val tables = mutableListOf<Memory>()

    @Test
    fun callsLandOnTheirSlots() {
        val api = fake()
        api.setSenderName("LiquidLSD-MASTER")
        assertTrue(api.sendTexture(7, 0x0DE1, 1920, 1080, true, 3))
        api.releaseSender()
        api.setReceiverName("Resolume")
        assertFalse(api.receiveTexture(9, 0x0DE1, false, 0), "AL = 0 is false even with bits above it")
        assertTrue(api.isUpdated()); assertTrue(api.isConnected())
        assertEquals(1280, api.senderWidth()); assertEquals(720, api.senderHeight())
        assertEquals(2, api.senderCount())
        val buf = ByteArray(256)
        assertTrue(api.sender(1, buf))
        assertEquals("Resolume 1", String(buf, 0, buf.indexOf(0), Charsets.UTF_8))
        api.releaseReceiver(); api.release()
        assertEquals(listOf(
            "setSenderName LiquidLSD-MASTER", "send 7 3553 1920 1080 1 3", "releaseSender 0", "setReceiverName Resolume",
            "receive 9 3553 0 0", "isUpdated", "isConnected", "width", "height", "count", "sender 1 256", "releaseReceiver", "release",
        ), calls)
    }
}
