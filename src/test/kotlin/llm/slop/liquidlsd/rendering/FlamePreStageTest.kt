package llm.slop.liquidlsd.rendering

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The real GLSL `applyXf` on a hidden GL context (skipped where none can be made): `pre_blur` (id 67, observed with
 * JWildfire 9.00: the point plus a random direction at a bell-shaped distance, sigma about 0.73 w) and the pre stage, which
 * runs before the xform's other variations whatever the slot order.
 */
class FlamePreStageTest {
    private val side = 128
    private val harness = """#version 330 core
out vec4 o;
uniform vec2 uIn;
""" + FlameEngine.COMMON + """
void main() {
    gRng = uint(gl_FragCoord.y) * 977u + uint(gl_FragCoord.x) + 1u;
    for (int i = 0; i < 4; i++) rnd();
    o = vec4(applyXf(0, uIn), 0.0, 1.0);
}
"""
    private val vert = "#version 330 core\nvoid main() { vec2 q = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(q * 2.0 - 1.0, 0.0, 1.0); }"
    private val sinusoidal = FlameVariations.NAMES.indexOf("sinusoidal")
    private val preBlur = FlameVariations.NAMES.indexOf("pre_blur")

    /** One identity xform whose slots are the given (id, weight) pairs, in order. */
    private fun genome(vararg slots: Pair<Int, Float>): FloatArray {
        val g = FloatArray((FlameGenome.MAX_XF + 1) * FlameGenome.VEC4_PER_XF * 4)
        g[4] = 1f; g[7] = 1f      // pre matrix (a, b, d, e)
        g[12] = 1f; g[15] = 1f    // post matrix
        slots.forEachIndexed { j, (id, w) -> g[16 + j * FlameGenome.VEC4_PER_SLOT * 4] = id.toFloat(); g[17 + j * FlameGenome.VEC4_PER_SLOT * 4] = w }
        return g
    }

    private fun sample(g: FloatArray, x: Float, y: Float): List<Pair<Double, Double>> {
        val hasGlfw = try { glfwInit() } catch (e: Throwable) { false }
        assumeTrue(hasGlfw, "GLFW unavailable; skipping")
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE); glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        val window = glfwCreateWindow(64, 64, "Flame pre-stage test", 0L, 0L)
        assumeTrue(window != 0L, "No offscreen GL context; skipping")
        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            val shader = Shader(vert, harness)
            val fbo = FBO(side, side, GL_RGBA32F)
            val vao = glGenVertexArrays()
            fbo.bind(); shader.bind()
            glViewport(0, 0, side, side)
            shader.setUniform4fv("uXf", g)
            shader.setUniform("uIn", x, y)
            glBindVertexArray(vao); glDrawArrays(GL_TRIANGLES, 0, 3)
            val out = BufferUtils.createFloatBuffer(side * side * 4)
            glReadPixels(0, 0, side, side, GL_RGBA, GL_FLOAT, out)
            val pts = List(side * side) { out.get(it * 4).toDouble() to out.get(it * 4 + 1).toDouble() }
            glBindVertexArray(0); glDeleteVertexArrays(vao); fbo.dispose(); shader.dispose()
            return pts
        } finally {
            glfwDestroyWindow(window)
        }
    }

    @Test fun preBlurCloudMatchesTheObservedDistribution() {
        // observed share of hits within radius r of the point, weight 1 (JWildfire 9.00, 100 renders of one dot)
        val observed = listOf(0.25 to 0.263, 0.5 to 0.507, 1.0 to 0.843, 1.5 to 0.972)
        val pts = sample(genome(preBlur to 1f, 0 to 1f), 1f, 0.5f)
        for ((r, share) in observed) {
            val got = pts.count { hypot(it.first - 1.0, it.second - 0.5) <= r } / pts.size.toDouble()
            assertTrue(abs(got - share) < 0.04, "share within $r: ours $got, observed $share")
        }
        assertTrue(abs(pts.map { it.first }.average() - 1.0) < 0.02 && abs(pts.map { it.second }.average() - 0.5) < 0.02, "the cloud is centred on the point")
        val w = sample(genome(preBlur to 0.5f, 0 to 1f), 1f, 0.5f)   // scales with the weight: half the weight, half the radius
        val got = w.count { hypot(it.first - 1.0, it.second - 0.5) <= 0.25 } / w.size.toDouble()
        assertTrue(abs(got - 0.507) < 0.04, "weight 0.5 share within 0.25: $got")
    }

    @Test fun preVariationsRunBeforeTheOthersWhateverTheSlotOrder() {
        // sin(p + blur) stays inside [-1, 1]; blur applied after the sine would not
        for (order in listOf(arrayOf(sinusoidal to 1f, preBlur to 1f), arrayOf(preBlur to 1f, sinusoidal to 1f))) {
            val pts = sample(genome(*order), 0.5f, 0f)
            assertTrue(pts.all { abs(it.first) <= 1.0001 && abs(it.second) <= 1.0001 }, "blur ran after the sine in slot order ${order.map { it.first }}")
            assertTrue(pts.any { abs(it.second) > 0.2 }, "blur had no effect")
        }
    }
}
