package llm.slop.liquidlsd.rendering

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import kotlin.test.Test

/** Compiles and links the flame engine's shaders on a hidden GL 3.3 context; skipped where no context can be made. */
class FlameShaderCompileTest {
    @Test fun engineShadersCompileAndLink() {
        val hasGlfw = try { glfwInit() } catch (e: Throwable) { false }
        assumeTrue(hasGlfw, "GLFW unavailable; skipping")
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        val window = glfwCreateWindow(64, 64, "Flame shader compile test", 0L, 0L)
        assumeTrue(window != 0L, "No offscreen GL context; skipping")
        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            // Shader() throws with the driver's info log on a compile or link failure
            Shader(FlameEngine.FULLSCREEN_VERT, FlameEngine.ITERATE_FRAG).dispose()
            Shader(FlameEngine.SPLAT_VERT, FlameEngine.SPLAT_FRAG).dispose()
        } finally {
            glfwDestroyWindow(window)
        }
    }
}
