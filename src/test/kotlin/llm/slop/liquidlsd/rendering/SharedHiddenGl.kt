package llm.slop.liquidlsd.rendering

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.GL_NO_ERROR
import org.lwjgl.opengl.GL33.glGetError

/**
 * One hidden GL 3.3 context for every test class that draws through [Geometry], never destroyed: Geometry caches
 * its fullscreen-quad VAO, and VAOs are not shared between contexts, so a second context in the same JVM would
 * draw nothing. [use] makes it current (another class may have made its own current since) and drops stale errors.
 * Skips the test where no context can be made.
 */
object SharedHiddenGl {
    private var window = 0L

    fun use(body: () -> Unit) {
        if (window == 0L) {
            val hasGlfw = try { glfwInit() } catch (e: Throwable) { false }
            assumeTrue(hasGlfw, "GLFW unavailable; skipping")
            glfwDefaultWindowHints()
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
            window = glfwCreateWindow(64, 64, "Shared hidden GL test context", 0L, 0L)
            assumeTrue(window != 0L, "No offscreen GL context; skipping")
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
        }
        glfwMakeContextCurrent(window)
        GL.createCapabilities()
        @Suppress("ControlFlowWithEmptyBody") while (glGetError() != GL_NO_ERROR) {}
        body()
    }
}
