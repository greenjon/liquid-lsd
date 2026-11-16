package llm.slop.liquidlsd.rendering.pipewire

import org.lwjgl.system.MemoryUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The PipeWire output owns its frames: the GL thread's reused readback buffer is copied, never shared. */
class FrameTripleBufferTest {

    @Test
    fun consumerGetsACopyThatTheProducerNoLongerWrites() {
        val tb = FrameTripleBuffer(16)
        val src = MemoryUtil.memAlloc(16)
        try {
            assertNull(tb.takeFresh())
            for (i in 0 until 16) src.put(i, 1)
            tb.publish(src)
            val taken = tb.takeFresh()!!
            assertNotSame(src, taken)
            // The GL thread overwrites its buffer and publishes twice more; the frame the consumer holds stays intact.
            for (i in 0 until 16) src.put(i, 2)
            tb.publish(src)
            for (i in 0 until 16) src.put(i, 3)
            tb.publish(src)
            for (i in 0 until 16) assertEquals(1, taken.get(i).toInt())
            // The newest frame wins; nothing fresh after it is taken.
            assertTrue(tb.hasFresh())
            val next = tb.takeFresh()!!
            assertEquals(3, next.get(0).toInt())
            assertNull(tb.takeFresh())
        } finally {
            MemoryUtil.memFree(src)
            tb.free()
        }
    }
}
