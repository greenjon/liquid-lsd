package llm.slop.liquidlsd.export

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

class ScreenshotCaptureTest {

    @Test
    fun testFlipVerticalDirectBuffers() {
        val width = 2
        val height = 2
        val channels = 4
        val size = width * height * channels

        val src = MemoryUtil.memAlloc(size)
        val dst = MemoryUtil.memAlloc(size)

        try {
            // Fill row 0 (top row in GL lower-left coordinate = bottom row visually)
            // Row 0: Red pixels (255, 0, 0, 255)
            for (i in 0 until 2) {
                src.put(255.toByte()).put(0.toByte()).put(0.toByte()).put(255.toByte())
            }
            // Row 1: Blue pixels (0, 0, 255, 255)
            for (i in 0 until 2) {
                src.put(0.toByte()).put(0.toByte()).put(255.toByte()).put(255.toByte())
            }

            ScreenshotCapture.flipVertical(src, dst, width, height, channels)

            // After flip, Row 0 in dst should contain Blue pixels (Row 1 of src)
            dst.rewind()
            assertEquals(0.toByte(), dst.get()) // R
            assertEquals(0.toByte(), dst.get()) // G
            assertEquals(255.toByte(), dst.get()) // B
            assertEquals(255.toByte(), dst.get()) // A

            // Row 1 in dst should contain Red pixels (Row 0 of src)
            dst.position(width * channels)
            assertEquals(255.toByte(), dst.get()) // R
            assertEquals(0.toByte(), dst.get()) // G
            assertEquals(0.toByte(), dst.get()) // B
            assertEquals(255.toByte(), dst.get()) // A
        } finally {
            MemoryUtil.memFree(src)
            MemoryUtil.memFree(dst)
        }
    }

    @Test
    fun testFlipVerticalHeapBuffers() {
        val width = 2
        val height = 2
        val channels = 4
        val size = width * height * channels

        val src = ByteBuffer.allocate(size)
        val dst = ByteBuffer.allocate(size)

        // Row 0: Red
        for (i in 0 until 2) {
            src.put(255.toByte()).put(0.toByte()).put(0.toByte()).put(255.toByte())
        }
        // Row 1: Green
        for (i in 0 until 2) {
            src.put(0.toByte()).put(255.toByte()).put(0.toByte()).put(255.toByte())
        }

        ScreenshotCapture.flipVertical(src, dst, width, height, channels)

        // After flip, Row 0 of dst should be Green
        dst.rewind()
        assertEquals(0.toByte(), dst.get())
        assertEquals(255.toByte(), dst.get())
        assertEquals(0.toByte(), dst.get())
        assertEquals(255.toByte(), dst.get())

        // Row 1 of dst should be Red
        dst.position(width * channels)
        assertEquals(255.toByte(), dst.get())
        assertEquals(0.toByte(), dst.get())
        assertEquals(0.toByte(), dst.get())
        assertEquals(255.toByte(), dst.get())
    }
}
