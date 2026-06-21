package llm.slop.spirals.rendering

import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*

private val logger = KotlinLogging.logger {}

object GLDebug {
    /**
     * Check for OpenGL errors and log them
     */
    fun checkErrors(context: String = "") {
        var error = glGetError()
        var hasError = false
        while (error != GL_NO_ERROR) {
            hasError = true
            val errorString = when (error) {
                GL_INVALID_ENUM -> "GL_INVALID_ENUM"
                GL_INVALID_VALUE -> "GL_INVALID_VALUE"
                GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
                GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
                GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
                else -> "UNKNOWN_ERROR (0x${error.toString(16)})"
            }
            logger.error { "OpenGL Error in '$context': $errorString" }
            error = glGetError()
        }
        if (!hasError && context.isNotEmpty()) {
            logger.debug { "✓ No GL errors in: $context" }
        }
    }

    /**
     * Setup GL debug message callback if supported by context
     */
    fun setupDebugCallback() {
        try {
            org.lwjgl.opengl.GLUtil.setupDebugMessageCallback()
            logger.info { "OpenGL Debug Message Callback enabled successfully" }
        } catch (e: Exception) {
            logger.warn { "Failed to enable OpenGL Debug Message Callback: ${e.message}" }
        }
    }
}
