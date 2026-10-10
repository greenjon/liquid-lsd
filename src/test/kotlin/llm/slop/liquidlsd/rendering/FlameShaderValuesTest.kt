package llm.slop.liquidlsd.rendering

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the real GLSL `vari()` of the deterministic variations of ids 49-97 on spec test pairs, on a hidden GL 3.3
 * context (skipped where none can be made). [FlameVariationsTest] only checks a Kotlin mirror; this is the check that
 * the shader itself says the same. Single precision, so the tolerance is looser than the mirror's.
 */
class FlameShaderValuesTest {
    private class Case(val name: String, val x: Double, val y: Double, val ex: Double, val ey: Double, val p: List<Double> = emptyList(), val w: Double = 1.0)

    private val pi = PI
    private fun c(name: String, x: Double, y: Double, ex: Double, ey: Double, vararg p: Double, w: Double = 1.0) = Case(name, x, y, ex, ey, p.toList(), w)

    private val cases = listOf(
        c("disc2", 1.0, 0.0, 0.0, 0.5, 0.0, 0.0), c("disc2", 0.5, 0.5, 0.0, -0.25, 1.0, 0.0), c("disc2", 1.0, 0.0, -1.0, 0.5, 0.0, pi),
        c("disc2", 1.0, 0.0, -4.14159, 0.5, 0.0, 3 * pi),
        c("bent2", -3.0, 4.0, -6.0, 4.0, 2.0, 0.5), c("bent2", -1.0, -2.0, -2.0, -1.0, 2.0, 0.5),
        c("bipolar", 0.0, 0.0, 0.0, 1.0, 0.0), c("bipolar", 0.0, 1.0, 0.0, 0.5, 0.0), c("bipolar", 0.5, 0.0, 0.34970, 1.0, 0.0),
        c("bipolar", 1.0, 1.0, 0.25615, 0.35242, 0.0), c("bipolar", 0.0, 1.0, 0.0, 0.0, 0.5),
        c("butterfly", 2.0, 1.0, 1.30294, 1.30294), c("butterfly", -1.0, -1.0, -0.58269, -1.16538), c("butterfly", 1.0, 0.0, 0.0, 0.0),
        c("cell", 0.5, 0.5, 0.5, -0.5, 1.0), c("cell", 1.5, 0.5, 2.5, -0.5, 1.0), c("cell", -0.5, 0.5, 1.5, -0.5, 1.0),
        c("cell", 3.0, -1.0, 5.0, -3.0, 2.0), c("cell", -0.2, 0.7, 0.8, -1.2, 0.5),
        c("cpow", 1.0, 2.0, 1.0, 2.0, 1.0, 0.0, 1.0), c("cpow", -3.0, 4.0, -3.0, 4.0, 1.0, 0.0, 1.0),
        c("curve", 0.0, 0.0, 1.0, 2.0, 1.0, 2.0, 1.0, 1.0), c("curve", 1.0, 0.0, 2.0, 0.73576, 1.0, 2.0, 1.0, 1.0),
        c("curve", 0.5, 1.0, 2.05760, 0.63212, 2.0, -1.0, 2.0, 0.5),
        c("edisc", 0.0, 0.0, 0.21686, 0.0), c("edisc", 1.0, 1.0, 0.07944, 0.06866), c("edisc", 1.0, -1.0, 0.07944, -0.06866),
        c("elliptic", 0.0, 1.0, 0.0, 0.45941), c("elliptic", 2.0, 1.0, 0.67701, 0.78342), c("elliptic", 2.0, -1.0, 0.67701, -0.78342),
        c("elliptic", 2.0, 0.0, 1.0, -0.69940),
        c("escher", 0.0, 1.0, 0.32240, 0.32240, pi / 2), c("escher", Math.E, 0.0, 1.44689, 0.79044, pi / 2), c("escher", 0.0, 1.0, 1.0, 0.0, pi),
        c("escher", -3.0, 4.0, -3.0, 4.0, 0.0),
        c("foci", 0.0, pi / 2, 0.0, 1.0), c("foci", 1.0, 0.0, 2.16395, 0.0), c("foci", -1.0, 1.0, -1.17195, 0.83914),
        c("loonie", 2.0, 0.0, 2.0, 0.0), c("loonie", 0.6, 0.0, 0.8, 0.0), c("loonie", 1.0, 0.0, 3.46410, 0.0, w = 2.0), c("loonie", 3.0, 0.0, 6.0, 0.0, w = 2.0),
        c("modulus", 1.5, 0.0, -0.5, 0.0, 1.0, 1.0), c("modulus", -1.5, 0.0, 0.5, 0.0, 1.0, 1.0), c("modulus", 1.2, -5.0, 0.2, -1.0, 0.5, 2.0),
        c("oscope", 0.0, 0.5, 0.0, -0.5, 1.0, pi, 1.0, 0.0), c("oscope", 0.0, 2.5, 0.0, 2.5, 1.0, pi, 1.0, 0.0),
        c("oscope", 0.3, 0.1, 0.3, -0.1, 0.0, 0.5, 2.0, 1.0),
        c("polar2", 1.0, 0.0, 0.5, 0.0), c("polar2", 0.0, Math.exp(pi), 0.0, 1.0), c("polar2", -1.0, -1.0, -0.75, 0.11032),
        c("popcorn2", pi / 4, 0.0, 0.78540, 0.42074, 0.5, 0.5, 1.0), c("popcorn2", 0.2, 0.3, 0.51600, 0.40258, 0.5, 0.25, 2.0),
        c("scry", 1.0, 0.0, 0.5, 0.0), c("scry", 0.0, 2.0, 0.0, 0.2), c("scry", 1.0, 0.0, 0.66667, 0.0, w = 2.0),
        c("separation", 0.0, 0.0, -1.0, -1.0, 1.0, 0.0, 1.0, 0.0), c("separation", 1.0, 1.0, 1.41421, 1.41421, 1.0, 0.0, 1.0, 0.0),
        c("separation", 2.0, -2.0, 1.23607, -1.0, 1.0, 0.5, 0.0, 0.5), c("separation", -1.0, -2.0, -1.0, -2.0, 0.0, 0.0, 0.0, 0.0),
        c("split", 1.0, 2.0, 1.0, -2.0, 1.0, 1.0), c("split", 1.5, -2.5, 1.5, -2.5, 0.0, 0.0),
        c("splits", 0.0, 0.0, 1.0, 2.0, 1.0, 2.0), c("splits", 3.0, 4.0, 4.0, 6.0, 1.0, 2.0), c("splits", -3.0, -4.0, -4.0, -6.0, 1.0, 2.0),
        c("stripes", 0.2, 1.0, 0.16, 1.04, 0.2, 1.0), c("stripes", 0.3, 2.0, 0.0, 2.0, 1.0, 0.0),
        c("wedge", 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0), c("wedge", 2.0, 0.0, 2.5, 0.0, 0.0, 0.5, 1.0, 0.0),
        c("wedge", 1.0, 0.0, 0.54030, 0.84147, 0.0, 0.0, 1.0, 1.0), c("wedge", 0.0, 2.0, -1.41421, 1.41421, pi / 2, 0.0, 2.0, 0.0),
        c("wedge_julia", 0.0, 2.0, -1.41421, 1.41421, 1.0, 1.0, 2.0, pi / 2),
        c("wedge_sph", 2.0, 0.0, 0.5, 0.0, 0.0, 0.0, 1.0, 0.0), c("wedge_sph", 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0),
        c("wedge_sph", 2.0, 0.0, 1.0, 0.0, 0.0, 0.5, 1.0, 0.0),
        c("whorl", 0.0, 2.0, 0.0, 2.0, 0.0, 0.0), c("whorl", 0.5, 0.0, -0.20807, 0.45465, 1.0, 2.0), c("whorl", 2.0, 0.0, -0.83229, -1.81859, 1.0, 2.0),
        c("waves2", 0.0, 0.5, 1.0, 0.5, 1.0, 2.0, pi, pi / 2),
        c("exp", 1.0, pi / 3, 1.35914, 2.35410), c("log", Math.E, 0.0, 1.0, 0.0), c("log", 0.0, 1.0, 0.0, 1.57080), c("log", -1.0, 0.0, 0.0, 3.14159),
        c("sin", 1.0, 0.5, 0.94886, 0.28155), c("cos", 1.0, 0.5, 0.60926, -0.43849), c("tan", 1.0, 0.5, 0.80688, 1.04283),
        c("sec", 1.0, 0.5, 1.08127, 0.77819), c("csc", 1.0, 0.5, 0.96861, -0.28741), c("cot", 1.0, 0.5, 0.46411, -0.59983),
        c("sinh", 1.0, 0.5, 1.03134, 0.73979), c("cosh", 1.0, 0.5, 1.35418, 0.56342), c("tanh", 0.5, 0.5, 0.56408, 0.40390),
        c("sech", 0.5, 0.5, 0.94998, -0.23983), c("csch", 0.5, 0.5, 0.91207, -1.07823), c("coth", 0.5, 0.5, 1.17195, 0.83914),
        c("auger", pi / 2, 0.0, 1.57080, 0.25, 1.0, 0.5, 0.0, 1.0), c("auger", 0.3, 0.4, 0.3, 0.65409, 2.0, 0.5, 0.0, 1.0),
        c("auger", 1.0, 2.0, 1.68197, 3.05184, 1.0, 0.5, 1.0, 1.0),
        c("flux", 0.0, 1.0, 1.41421, 1.41421, 0.0), c("flux", 1.0, 1.0, 3.14461, 5.08808, 0.0, w = 2.0)
    )

    private val harness = """#version 330 core
out vec4 o;
uniform vec4 uIn;   // x, y, weight, id
uniform vec4 uP;
""" + FlameEngine.COMMON + """
void main() {
    gRng = 1u;
    o = vec4(vari(int(uIn.w + 0.5), uIn.xy, uIn.z, uP, vec4(0.0), vec2(0.0)), 0.0, 1.0);
}
"""
    private val vert = "#version 330 core\nvoid main() { vec2 q = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(q * 2.0 - 1.0, 0.0, 1.0); }"

    @Test fun shaderMatchesSpecPairs() {
        val hasGlfw = try { glfwInit() } catch (e: Throwable) { false }
        assumeTrue(hasGlfw, "GLFW unavailable; skipping")
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        val window = glfwCreateWindow(64, 64, "Flame shader values test", 0L, 0L)
        assumeTrue(window != 0L, "No offscreen GL context; skipping")
        val failures = ArrayList<String>()
        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            val shader = Shader(vert, harness)
            val fbo = FBO(1, 1, GL_RGBA32F)
            val vao = glGenVertexArrays()
            val out = BufferUtils.createFloatBuffer(4)
            for (k in cases) {
                val id = FlameVariations.NAMES.indexOf(k.name)
                assertTrue(id >= 0 && FlameVariations.ALL[id].supported, k.name)
                fbo.bind()
                shader.bind()
                shader.setUniform("uIn", k.x.toFloat(), k.y.toFloat(), k.w.toFloat(), id.toFloat())
                val pp = FloatArray(4) { k.p.getOrNull(it)?.toFloat() ?: 0f }
                shader.setUniform("uP", pp[0], pp[1], pp[2], pp[3])
                glBindVertexArray(vao)
                glDrawArrays(GL_TRIANGLES, 0, 3)
                glReadPixels(0, 0, 1, 1, GL_RGBA, GL_FLOAT, out)
                val gx = out.get(0).toDouble(); val gy = out.get(1).toDouble()
                fun ok(g: Double, e: Double) = abs(g - e) <= 5e-4 + 2e-4 * abs(e)
                if (!ok(gx, k.ex) || !ok(gy, k.ey)) failures += "${k.name}(${k.x}, ${k.y}) p=${k.p} w=${k.w}: shader ($gx, $gy), spec (${k.ex}, ${k.ey})"
            }
            glBindVertexArray(0)
            glDeleteVertexArrays(vao)
            fbo.dispose(); shader.dispose()
        } finally {
            glfwDestroyWindow(window)
        }
        assertEquals(emptyList<String>(), failures)
    }
}
