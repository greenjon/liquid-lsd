package llm.slop.liquidlsd.rendering

import mu.KotlinLogging
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.GL_NO_ERROR
import org.lwjgl.opengl.GL11.glGetError
import org.lwjgl.opengl.GL43.*
import org.lwjgl.opengl.GLDebugMessageCallback
import org.lwjgl.opengl.KHRDebug
import org.lwjgl.system.MemoryUtil

private val logger = KotlinLogging.logger {}

/** OpenGL diagnostics: driver debug-message logging plus a manual glGetError checkpoint. */
object GLDebug {
    private var debugCallback: GLDebugMessageCallback? = null

    fun setupDebugCallback() {
        val caps = GL.getCapabilities()
        if (!caps.GL_KHR_debug && !caps.OpenGL43) {
            logger.info { "GL debug callback not supported by this context" }
            return
        }
        val callback = GLDebugMessageCallback.create { source, type, id, severity, length, message, _ ->
            if (severity == GL_DEBUG_SEVERITY_NOTIFICATION) return@create
            val text = MemoryUtil.memUTF8(message, length)
            logger.warn {
                "GL debug id=0x${Integer.toHexString(id)} source=0x${Integer.toHexString(source)} " +
                    "type=0x${Integer.toHexString(type)} severity=0x${Integer.toHexString(severity)}: $text"
            }
        }
        debugCallback = callback
        if (caps.OpenGL43) glDebugMessageCallback(callback, 0L) else KHRDebug.glDebugMessageCallback(callback, 0L)
    }

    /** Drains the GL error queue, logging each error with [label] as the checkpoint name. */
    fun checkErrors(label: String) {
        while (true) {
            val error = glGetError()
            if (error == GL_NO_ERROR) break
            logger.error { "GL error 0x${Integer.toHexString(error)} after: $label" }
        }
    }

    fun disposeDebugCallback() {
        debugCallback?.free()
        debugCallback = null
    }
}
