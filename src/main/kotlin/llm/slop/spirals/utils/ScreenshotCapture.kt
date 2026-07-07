package llm.slop.spirals.utils

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT
import org.lwjgl.opengl.GL11.GL_RGB
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glPixelStorei
import org.lwjgl.opengl.GL11.glReadPixels
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import java.io.File

object ScreenshotCapture {
    fun writeFramebufferPng(file: File, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false

        file.parentFile?.mkdirs()

        val stride = width * 3
        val source = BufferUtils.createByteBuffer(stride * height)
        val flipped = BufferUtils.createByteBuffer(stride * height)
        val previousPackAlignment = glGetInteger(GL_PACK_ALIGNMENT)

        glPixelStorei(GL_PACK_ALIGNMENT, 1)
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, source)
        glPixelStorei(GL_PACK_ALIGNMENT, previousPackAlignment)

        for (y in 0 until height) {
            val sourceRow = (height - 1 - y) * stride
            val targetRow = y * stride
            for (x in 0 until stride) {
                flipped.put(targetRow + x, source.get(sourceRow + x))
            }
        }

        return stbi_write_png(file.absolutePath, width, height, 3, flipped, stride)
    }
}
