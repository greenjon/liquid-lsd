package llm.slop.spirals.rendering

import mu.KotlinLogging
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import org.lwjgl.opengl.GL43.GL_DEBUG_SEVERITY_NOTIFICATION
import org.lwjgl.opengl.GL43.glDebugMessageCallback
import org.lwjgl.opengl.GLDebugMessageCallback
import org.lwjgl.system.MemoryUtil

private val logger = KotlinLogging.logger {}

object GLDebug {
    private var debugCallback: GLDebugMessageCallback? = null

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
            logger.trace { "✓ No GL errors in: $context" }
        }
    }

    /**
     * Setup GL debug message callback if supported by context
     */
    fun setupDebugCallback() {
        try {
            if (!GL.getCapabilities().GL_KHR_debug && !GL.getCapabilities().OpenGL43) {
                logger.info { "OpenGL Debug Message Callback not supported by this context" }
                return
            }

            debugCallback = GLDebugMessageCallback.create { source, type, id, severity, length, message, _ ->
                if (severity == GL_DEBUG_SEVERITY_NOTIFICATION) {
                    return@create
                }

                val text = MemoryUtil.memUTF8(message, length)
                logger.warn {
                    "OpenGL debug message: id=0x${id.toString(16)}, source=$source, type=$type, severity=$severity, message=$text"
                }
            }
            glDebugMessageCallback(debugCallback, 0L)
            logger.info { "OpenGL Debug Message Callback enabled successfully" }
        } catch (e: Exception) {
            logger.warn { "Failed to enable OpenGL Debug Message Callback: ${e.message}" }
        }
    }

    fun disposeDebugCallback() {
        debugCallback?.free()
        debugCallback = null
    }
}
