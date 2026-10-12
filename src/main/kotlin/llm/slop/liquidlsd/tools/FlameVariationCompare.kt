package llm.slop.liquidlsd.tools

import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.FlameEngine
import llm.slop.liquidlsd.rendering.FlameImporter
import llm.slop.liquidlsd.rendering.FlameVariations
import llm.slop.liquidlsd.rendering.Shader
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Compares our shader's variation with an observed table (`tools/blackbox/extract_points.py`): runs the real GLSL
 * `vari()` at each observed input point and reports how far it lands from the observed output. Deterministic
 * variations only; a random one is compared by statistics instead.
 *
 *   ./gradlew compareVariation -Pargs="secant2 build/blackbox/secant2/table.csv [--p 1,0,0,0] [--w 1]"
 */
object FlameVariationCompare {
    private const val HARNESS_HEAD = "#version 330 core\nout vec4 o;\nuniform vec4 uIn;   // x, y, weight, id\nuniform vec4 uP;\nuniform vec2 uT;  \n"
    private const val HARNESS_MAIN = "\nvoid main() { gRng = 1u; o = vec4(vari(int(uIn.w + 0.5), uIn.xy, uIn.z, uP, vec4(1.0, 0.0, 0.0, 1.0), uT), 0.0, 1.0); }\n"
    private const val VERT = "#version 330 core\nvoid main() { vec2 q = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(q * 2.0 - 1.0, 0.0, 1.0); }"
    private const val TOLERANCE = 0.02   // two probe pixels (1 pixel = 0.01 units), allowing for centroid and half-pixel error

    private class Job(val name: String, val table: File, val params: List<Float>?, val weight: Float) {
        /** Translation of the probe's final xform, read from probes.json beside the table (zero when absent). */
        val translate: Pair<Float, Float> = File(table.absoluteFile.parentFile, "probes.json").takeIf { it.isFile }
            ?.let { Regex("\"translate\": \\[([^,\\]]+), ([^\\]]+)\\]").find(it.readText()) }
            ?.let { it.groupValues[1].trim().toFloat() to it.groupValues[2].trim().toFloat() } ?: (0f to 0f)
    }

    private fun idOf(name: String) = FlameImporter.variationIdForFileName(name) ?: FlameVariations.NAMES.indexOf(name).takeIf { it >= 0 && FlameVariations.ALL[it].supported }
        ?: error("no supported variation called $name")

    @JvmStatic fun main(args: Array<String>) {
        if (args[0] == "--list") {   // one line per supported variation: file name, random flag, parameter attributes at our defaults
            for (id in 0 until FlameVariations.count()) {
                val v = FlameVariations.ALL[id]
                if (!v.supported) continue
                val file = FlameImporter.fileNameFor(id)
                println("$file ${if (v.name in FlameVariations.RANDOM) "random" else "fixed"} " + v.params.joinToString(",") { "${file}_${it.label.lowercase()}=${it.default}" })
            }
            return
        }
        val jobs = ArrayList<Job>()
        if (args[0] == "--batch") {   // lines: name|table.csv|p1,p2,p3,p4 (parameters in our order, may be empty)
            for (line in File(args[1]).readLines().filter { it.isNotBlank() }) {
                val c = line.split("|")
                jobs += Job(c[0], File(c[1]), c.getOrNull(2)?.takeIf { it.isNotBlank() }?.split(",")?.map { it.toFloat() }, 1f)
            }
        } else {
            var weight = 1f
            var override: List<Float>? = null
            var i = 2
            while (i < args.size) {
                when (args[i]) { "--w" -> weight = args[++i].toFloat(); "--p" -> override = args[++i].split(",").map { it.toFloat() } }
                i++
            }
            jobs += Job(args[0], File(args[1]), override, weight)
        }

        check(glfwInit()) { "GLFW unavailable" }
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE); glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        val window = glfwCreateWindow(64, 64, "Variation compare", 0L, 0L)
        check(window != 0L) { "No offscreen GL context" }
        glfwMakeContextCurrent(window)
        GL.createCapabilities()
        try {
            val shader = Shader(VERT, HARNESS_HEAD + FlameEngine.COMMON + HARNESS_MAIN)
            val fbo = FBO(1, 1, GL_RGBA32F)
            val vao = glGenVertexArrays()
            val out = BufferUtils.createFloatBuffer(4)
            class Row(val set: String, val xi: Double, val yi: Double, val xo: Double, val yo: Double, val gx: Double, val gy: Double) { val err = hypot(xo - gx, yo - gy) }
            for (job in jobs) {
            val id = idOf(job.name)
            val params = job.params ?: FlameVariations.ALL[id].params.map { it.default }
            val weight = job.weight
            println("${FlameVariations.NAMES[id]} (id $id), parameters $params, weight $weight, translate ${job.translate}")
            val rows = ArrayList<Row>()
            var unseen = 0
            val predicted = ArrayList<DoubleArray>()   // our output at every input point of the table, for the set-level check below
            for (line in job.table.readLines().drop(1)) {
                val c = line.split(",")
                if (c.size < 4 || c[2].isBlank()) continue
                fbo.bind(); shader.bind()
                shader.setUniform("uT", job.translate.first, job.translate.second)
                shader.setUniform("uIn", c[2].toFloat() + job.translate.first, c[3].toFloat() + job.translate.second, weight, id.toFloat())
                val p = FloatArray(4) { params.getOrNull(it) ?: 0f }
                shader.setUniform("uP", p[0], p[1], p[2], p[3])
                glBindVertexArray(vao); glDrawArrays(GL_TRIANGLES, 0, 3)
                glReadPixels(0, 0, 1, 1, GL_RGBA, GL_FLOAT, out)
                predicted += doubleArrayOf(out.get(0).toDouble(), out.get(1).toDouble())
            }
            for (line in job.table.readLines().drop(1)) {
                val c = line.split(",")
                if (c[2].isBlank()) continue   // undecodable blob
                if (c[4].isBlank() || c.getOrElse(8) { "" }.isNotBlank()) { unseen++; continue }   // no clean dot for this point
                val xi = c[2].toDouble(); val yi = c[3].toDouble()
                fbo.bind(); shader.bind()
                shader.setUniform("uT", job.translate.first, job.translate.second)
                shader.setUniform("uIn", xi.toFloat() + job.translate.first, yi.toFloat() + job.translate.second, weight, id.toFloat())
                val p = FloatArray(4) { params.getOrNull(it) ?: 0f }
                shader.setUniform("uP", p[0], p[1], p[2], p[3])
                glBindVertexArray(vao); glDrawArrays(GL_TRIANGLES, 0, 3)
                glReadPixels(0, 0, 1, 1, GL_RGBA, GL_FLOAT, out)
                rows += Row(c[0], xi, yi, c[4].toDouble(), c[5].toDouble(), out.get(0).toDouble(), out.get(1).toDouble())
            }
            val bad = rows.count { it.err > TOLERANCE || !it.err.isFinite() }
            println("compared ${rows.size} observed points ($unseen without a clean dot): max error %.4f, mean %.4f, %d beyond %.3f".format(
                rows.filter { it.err.isFinite() }.maxOfOrNull { it.err } ?: Double.NaN, rows.filter { it.err.isFinite() }.map { it.err }.average(), bad, TOLERANCE))
            for (r in rows.sortedByDescending { if (it.err.isFinite()) it.err else 1e9 }.take(8).filter { it.err > TOLERANCE || !it.err.isFinite() })
                println("  %s (%.2f, %.2f): observed (%.4f, %.4f), ours (%.4f, %.4f)".format(r.set, r.xi, r.yi, r.xo, r.yo, r.gx, r.gy))
            // set level: a dot decoded to the wrong input (outputs that collide, or merge) still has to sit where some input of ours lands
            val unexplained = rows.count { r -> predicted.none { hypot(it[0] - r.xo, it[1] - r.yo) <= TOLERANCE } }
            val verdict = when {
                rows.isEmpty() -> "NO-DATA"
                bad <= maxOf(1, rows.size / 50) -> "MATCH"
                unexplained <= maxOf(1, rows.size / 50) -> "MATCH-AS-SET"
                else -> "DIFFERENT"
            }
            println("RESULT ${FlameVariations.NAMES[id]} $verdict ${rows.size} points, $bad beyond, $unexplained unexplained")
            }
            glBindVertexArray(0); glDeleteVertexArrays(vao); fbo.dispose(); shader.dispose()
        } finally {
            glfwDestroyWindow(window)
        }
    }
}
