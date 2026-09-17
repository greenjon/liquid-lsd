package llm.slop.liquidlsd.export

import mu.KotlinLogging
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

/**
 * Utility for capturing OpenGL framebuffers and writing crisp, deterministic PNG images.
 */
object ScreenshotCapture {

    /**
     * Flips image row buffer vertically (from OpenGL lower-left origin to standard top-left origin).
     */
    fun flipVertical(src: ByteBuffer, dst: ByteBuffer, width: Int, height: Int, channels: Int = 4) {
        val stride = width * channels
        val strideL = stride.toLong()

        src.rewind()
        dst.rewind()

        if (src.isDirect && dst.isDirect) {
            val srcAddr = MemoryUtil.memAddress(src)
            val dstAddr = MemoryUtil.memAddress(dst)
            for (row in 0 until height) {
                val srcOffset = (height - 1 - row) * strideL
                val dstOffset = row * strideL
                MemoryUtil.memCopy(srcAddr + srcOffset, dstAddr + dstOffset, strideL)
            }
        } else {
            val rowBytes = ByteArray(stride)
            for (row in 0 until height) {
                val srcOffset = (height - 1 - row) * stride
                val dstOffset = row * stride
                src.position(srcOffset)
                src.get(rowBytes)
                dst.position(dstOffset)
                dst.put(rowBytes)
            }
            src.rewind()
            dst.rewind()
        }
    }

    /**
     * Synchronously reads the specified framebuffer (or default 0) and writes it to a PNG file on disk.
     */
    fun captureFramebufferToPng(file: File, width: Int, height: Int, fboId: Int = 0): Boolean {
        if (width <= 0 || height <= 0) {
            logger.error { "Cannot capture screenshot with invalid dimensions: ${width}x${height}" }
            return false
        }

        try {
            file.parentFile?.mkdirs()

            val prevFbo = glGetInteger(GL_FRAMEBUFFER_BINDING)
            val prevPackAlignment = glGetInteger(GL_PACK_ALIGNMENT)

            val channels = 4 // RGBA
            val stride = width * channels
            val bufferSizeBytes = stride * height

            val rawBuffer = MemoryUtil.memAlloc(bufferSizeBytes)
            val flippedBuffer = MemoryUtil.memAlloc(bufferSizeBytes)

            try {
                glBindFramebuffer(GL_FRAMEBUFFER, fboId)
                glPixelStorei(GL_PACK_ALIGNMENT, 1)

                glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rawBuffer)

                flipVertical(rawBuffer, flippedBuffer, width, height, channels)

                val success = stbi_write_png(file.absolutePath, width, height, channels, flippedBuffer, stride)
                if (success) {
                    logger.info { "Successfully saved screenshot (${width}x${height}) to: ${file.absolutePath}" }
                } else {
                    logger.error { "STBImageWrite failed to save PNG screenshot to: ${file.absolutePath}" }
                }
                return success
            } finally {
                MemoryUtil.memFree(rawBuffer)
                MemoryUtil.memFree(flippedBuffer)
                glPixelStorei(GL_PACK_ALIGNMENT, prevPackAlignment)
                glBindFramebuffer(GL_FRAMEBUFFER, prevFbo)
            }
        } catch (t: Throwable) {
            logger.error(t) { "Failed to capture screenshot to ${file.absolutePath}" }
            return false
        }
    }
}
