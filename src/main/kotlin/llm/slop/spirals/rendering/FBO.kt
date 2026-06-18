package llm.slop.spirals.rendering

import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*

private val logger = KotlinLogging.logger {}

/**
 * Framebuffer Object wrapper for off-screen rendering.
 * 
 * Currently supports a single color attachment.
 * Future: Can be extended to support multiple color attachments and depth buffers.
 */
class FBO(val width: Int, val height: Int) {
    val framebufferId: Int
    val texture: Int
    
    private var isDisposed = false

    init {
        // Generate framebuffer
        framebufferId = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferId)

        // Create color texture attachment
        texture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, texture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0)
        
        // Set texture parameters (no mipmaps, linear filtering)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

        // Attach texture to framebuffer
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)

        // Check framebuffer completeness
        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            val errorMsg = when (status) {
                GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> "Incomplete attachment"
                GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> "Missing attachment"
                GL_FRAMEBUFFER_UNSUPPORTED -> "Unsupported framebuffer format"
                else -> "Unknown error (0x${status.toString(16)})"
            }
            throw RuntimeException("Framebuffer is not complete: $errorMsg")
        }

        // Unbind
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glBindTexture(GL_TEXTURE_2D, 0)

        logger.debug { "Created FBO ${framebufferId} (${width}x${height}), texture: $texture" }
    }

    /**
     * Bind this FBO for rendering (subsequent draw calls will render to this FBO)
     */
    fun bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferId)
        glViewport(0, 0, width, height)
    }

    /**
     * Unbind this FBO (return to default framebuffer)
     */
    fun unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    /**
     * Clear the FBO's color buffer
     */
    fun clear(r: Float = 0f, g: Float = 0f, b: Float = 0f, a: Float = 1f) {
        bind()
        glClearColor(r, g, b, a)
        glClear(GL_COLOR_BUFFER_BIT)
        unbind()
    }

    /**
     * Clean up OpenGL resources
     */
    fun dispose() {
        if (!isDisposed) {
            glDeleteTextures(texture)
            glDeleteFramebuffers(framebufferId)
            isDisposed = true
            logger.debug { "Disposed FBO $framebufferId" }
        }
    }

    protected fun finalize() {
        if (!isDisposed) {
            logger.warn { "FBO $framebufferId was not disposed properly!" }
        }
    }
}
