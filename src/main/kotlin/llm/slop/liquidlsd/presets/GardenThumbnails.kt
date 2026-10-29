package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.export.ScreenshotCapture
import llm.slop.liquidlsd.rendering.isf.ISFTextureLoader
import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.system.MemoryUtil
import java.io.File

/**
 * Small preview images for kept offspring: `<preset>.png` next to `<preset>.lsd`, written when the offspring is kept and
 * shown in the browser's row tooltip. [capture] and [textureFor] are GL-thread calls; the PNG is encoded and written on the preset IO thread.
 */
object GardenThumbnails {
    private val logger = KotlinLogging.logger {}
    const val WIDTH = 256
    const val HEIGHT = 144

    fun thumbnailFor(preset: File): File = File(preset.parentFile, preset.nameWithoutExtension + ".png")

    /**
     * Downscales [texture] (size [w]x[h]) into [WIDTH]x[HEIGHT] and reads it back (GL thread), then encodes and writes the
     * preset's thumbnail on [PresetManager.presetIoExecutor], so thread 0 never touches the disk. Returns true when the
     * readback worked and the write is queued; [PresetManager.awaitIo] waits for the file.
     */
    fun capture(texture: Int, w: Int, h: Int, preset: File): Boolean {
        if (texture == 0 || w <= 0 || h <= 0) return false
        val pixels = readBack(texture, w, h, preset) ?: return false
        val png = thumbnailFor(preset)
        PresetManager.presetIoExecutor.execute {
            val flipped = MemoryUtil.memAlloc(BYTES)
            try {
                png.parentFile?.mkdirs()
                ScreenshotCapture.flipVertical(pixels, flipped, WIDTH, HEIGHT, 4)
                if (!stbi_write_png(png.absolutePath, WIDTH, HEIGHT, 4, flipped, WIDTH * 4)) logger.warn { "Could not write the thumbnail ${png.path}" }
            } catch (t: Throwable) {
                logger.warn(t) { "Could not write the thumbnail ${png.path}" }
            } finally {
                MemoryUtil.memFree(flipped); MemoryUtil.memFree(pixels)
            }
        }
        return true
    }

    private const val BYTES = WIDTH * HEIGHT * 4

    /** The blitted, downscaled pixels (bottom row first) in a native buffer the caller frees, or null on a GL error. GL thread only. */
    private fun readBack(texture: Int, w: Int, h: Int, preset: File): java.nio.ByteBuffer? {
        val prevRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val prevDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val prevPack = glGetInteger(GL_PACK_ALIGNMENT)
        val prevViewport = IntArray(4).also { glGetIntegerv(GL_VIEWPORT, it) }
        val src = glGenFramebuffers()
        val dst = glGenFramebuffers()
        val dstTex = glGenTextures()
        val pixels = MemoryUtil.memAlloc(BYTES)
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, src)
            glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
            glBindTexture(GL_TEXTURE_2D, dstTex)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, dst)
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, dstTex, 0)
            glBlitFramebuffer(0, 0, w, h, 0, 0, WIDTH, HEIGHT, GL_COLOR_BUFFER_BIT, GL_LINEAR)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, dst)
            glPixelStorei(GL_PACK_ALIGNMENT, 1)
            glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
            return pixels
        } catch (t: Throwable) {
            logger.warn(t) { "Could not capture a thumbnail for ${preset.name}" }
            MemoryUtil.memFree(pixels)
            return null
        } finally {
            glPixelStorei(GL_PACK_ALIGNMENT, prevPack)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, prevRead)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, prevDraw)
            glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3])
            glBindTexture(GL_TEXTURE_2D, 0)
            glDeleteFramebuffers(src); glDeleteFramebuffers(dst); glDeleteTextures(dstTex)
        }
    }

    private class Loaded(val modified: Long, val texture: Int)
    private val textures = HashMap<String, Loaded>()

    /** GL texture of the thumbnail of the preset at [presetPath], or 0 when it has none. Cached; reloaded if the file changed. */
    fun textureFor(presetPath: String): Int {
        val png = thumbnailFor(File(presetPath))
        if (!png.isFile) return 0
        val modified = png.lastModified()
        val hit = textures[presetPath]
        if (hit != null && hit.modified == modified) return hit.texture
        hit?.let { ISFTextureLoader.disposeTexture(it.texture) }
        val tex = ISFTextureLoader.loadTexture(png)
        textures[presetPath] = Loaded(modified, tex)
        return tex
    }
}
