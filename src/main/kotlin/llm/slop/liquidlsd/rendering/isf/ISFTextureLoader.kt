package llm.slop.liquidlsd.rendering.isf

import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Thread-0 OpenGL texture loading helper for ISF IMPORTED assets (LUTs, noise maps, static textures).
 */
object ISFTextureLoader {

    /**
     * Loads an image file from disk into a 2D OpenGL texture.
     * Must be called strictly on the OpenGL render thread (Thread 0).
     *
     * @param file Image file to load.
     * @return OpenGL texture ID (or 0 on failure).
     */
    fun loadTexture(file: File): Int {
        if (!file.exists() || !file.isFile || !file.canRead()) {
            logger.warn { "Imported asset file does not exist or cannot be read: ${file.absolutePath}" }
            return 0
        }

        return try {
            MemoryStack.stackPush().use { stack ->
                val w = stack.mallocInt(1)
                val h = stack.mallocInt(1)
                val comp = stack.mallocInt(1)

                val pixels = stbi_load(file.absolutePath, w, h, comp, 4)
                if (pixels == null) {
                    logger.warn { "Failed to decode image file via STBImage: ${file.absolutePath}, reason: ${stbi_failure_reason()}" }
                    return@use 0
                }

                try {
                    val texId = glGenTextures()
                    glBindTexture(GL_TEXTURE_2D, texId)

                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)

                    glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_RGBA8,
                        w.get(0),
                        h.get(0),
                        0,
                        GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        pixels
                    )

                    glBindTexture(GL_TEXTURE_2D, 0)
                    logger.debug { "Loaded ISF imported texture '${file.name}' (${w.get(0)}x${h.get(0)}) -> texId=$texId" }
                    texId
                } finally {
                    stbi_image_free(pixels)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Exception while loading texture from ${file.absolutePath}" }
            0
        }
    }

    /**
     * Disposes of an OpenGL texture.
     * Must be called strictly on Thread 0.
     */
    fun disposeTexture(texId: Int) {
        if (texId > 0) {
            glDeleteTextures(texId)
        }
    }
}
