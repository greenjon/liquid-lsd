package llm.slop.liquidlsd.export

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * High-performance asynchronous GPU-to-CPU framebuffer readback using a ring of Pixel Buffer Objects (PBOs).
 * Avoids blocking the OpenGL render pipeline.
 */
class PboReadbackPipeline(
    val width: Int,
    val height: Int,
    private val pboCount: Int = 3
) {
    val bufferSizeBytes: Int = width * height * 4 // RGBA 8-bit
    private val pbos = IntArray(pboCount)
    private var pboIndex = 0
    private var framesQueued = 0
    private var isInitialized = false
    private var isDisposed = false

    // Preallocated CPU buffer for vertical flipping
    private var flippedBuffer: ByteBuffer = MemoryUtil.memAlloc(bufferSizeBytes)

    init {
        initPbos()
    }

    private fun initPbos() {
        for (i in 0 until pboCount) {
            pbos[i] = glGenBuffers()
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pbos[i])
            glBufferData(GL_PIXEL_PACK_BUFFER, bufferSizeBytes.toLong(), GL_STREAM_READ)
        }
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
        isInitialized = true
    }

    /**
     * Triggers an asynchronous DMA readback from the currently bound FBO.
     * Returns a ByteBuffer with the flipped (top-to-bottom) RGBA frame from (pboCount - 1) frames ago,
     * or null during initial pipeline warmup.
     */
    fun readFrameAsync(sourceFboId: Int, destination: ByteBuffer? = null): ByteBuffer? {
        if (isDisposed) return null

        try {
            glBindFramebuffer(GL_FRAMEBUFFER, sourceFboId)

            // 1. Trigger asynchronous DMA transfer from GPU FBO into current PBO
            val writePbo = pbos[pboIndex]
            glBindBuffer(GL_PIXEL_PACK_BUFFER, writePbo)
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
        } finally {
            // Restore default framebuffer to prevent corrupting subsequent screen blits
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
        }

        framesQueued++

        // 2. Advance index and select next PBO to read previously completed frame
        val readIndex = (pboIndex + 1) % pboCount
        pboIndex = (pboIndex + 1) % pboCount

        // Skip returning uninitialized frames during ring buffer warmup
        if (framesQueued < pboCount) {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
            return null
        }

        val readPbo = pbos[readIndex]
        glBindBuffer(GL_PIXEL_PACK_BUFFER, readPbo)
        val mappedBuffer = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, bufferSizeBytes.toLong(), GL_MAP_READ_BIT)

        var result: ByteBuffer? = null
        if (mappedBuffer != null) {
            val target = destination ?: flippedBuffer
            if (mappedBuffer.isDirect && target.isDirect) {
                MemoryUtil.memCopy(MemoryUtil.memAddress(mappedBuffer), MemoryUtil.memAddress(target), bufferSizeBytes.toLong())
            } else {
                mappedBuffer.rewind()
                target.rewind()
                target.put(mappedBuffer)
                mappedBuffer.rewind()
                target.rewind()
            }
            glUnmapBuffer(GL_PIXEL_PACK_BUFFER)
            result = target
        }

        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
        return result
    }

    /**
     * Synchronously reads the currently bound framebuffer into a direct ByteBuffer.
     * Useful for offline / single-frame captures.
     */
    fun readFrameSync(sourceFboId: Int): ByteBuffer {
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, sourceFboId)
            flippedBuffer.rewind()
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, flippedBuffer)
            flippedBuffer.rewind()
            return flippedBuffer
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
        }
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        for (pbo in pbos) {
            if (pbo != 0) {
                glDeleteBuffers(pbo)
            }
        }
        MemoryUtil.memFree(flippedBuffer)
    }

    companion object {
        /**
         * Flips an RGBA image vertically in memory using high-performance native memory copying.
         */
        fun flipVertical(src: ByteBuffer, dst: ByteBuffer, width: Int, height: Int) {
            val stride = width * 4L
            src.rewind()
            dst.rewind()

            if (src.isDirect && dst.isDirect) {
                val srcAddr = MemoryUtil.memAddress(src)
                val dstAddr = MemoryUtil.memAddress(dst)
                for (row in 0 until height) {
                    val srcOffset = (height - 1 - row) * stride
                    val dstOffset = row * stride
                    MemoryUtil.memCopy(srcAddr + srcOffset, dstAddr + dstOffset, stride)
                }
            } else {
                val rowSize = (width * 4)
                val rowBytes = ByteArray(rowSize)
                for (row in 0 until height) {
                    val srcOffset = (height - 1 - row) * rowSize
                    val dstOffset = row * rowSize
                    src.position(srcOffset)
                    src.get(rowBytes)
                    dst.position(dstOffset)
                    dst.put(rowBytes)
                }
            }
            dst.rewind()
            src.rewind()
        }
    }
}
