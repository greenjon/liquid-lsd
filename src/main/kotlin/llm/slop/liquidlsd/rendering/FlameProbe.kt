package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import org.lwjgl.opengl.GL33.*

/**
 * Runs a candidate genome through the engine's own iterate shader on a small hidden state texture, reads the points
 * back once, and lets [FlameQuality] judge them. Synchronous: ~40 trivial passes over 16k points plus a 256 KB
 * readback, so a sampler can try several candidates in the call that picks the next morph target. Needs the render
 * thread's GL context; GL state it touches is restored.
 *
 * The candidate is judged neutrally: only the morph-enabled parameters are taken from it, the performance handles
 * (Spin, Breathe, Warp, Drift) and the final xform stay at their defaults.
 */
internal class FlameProbe {
    private val n = SIDE
    private val stateA = FBO(n, n, GL_RGBA32F)
    private val stateB = FBO(n, n, GL_RGBA32F)
    private val iterate = Shader(FlameEngine.FULLSCREEN_VERT, FlameEngine.ITERATE_FRAG)
    private val scratch = FlameGenome.buildParameters()
    private val packed = FloatArray(FlameGenome.PACKED_FLOATS)
    private val points = FloatArray(n * n * 4)
    private var frame = 0

    /** @param value the candidate's value of a morph-enabled parameter, or null to keep the default */
    fun evaluate(value: (String) -> Float?): FlameQuality.Result {
        for ((name, p) in scratch) {
            p.baseValue = if (p.randomizeBase) value(name) ?: p.defaultValue else p.defaultValue
            p.evaluate()
        }
        val numXf = FlameGenome.pack(scratch, packed)

        val saved = GlState.save()
        try {
            val rnd = java.util.Random(frame.toLong() * 7919 + 17)
            for (i in 0 until n * n) {
                points[i * 4] = rnd.nextFloat() * 2f - 1f; points[i * 4 + 1] = rnd.nextFloat() * 2f - 1f
                points[i * 4 + 2] = rnd.nextFloat(); points[i * 4 + 3] = 0f
            }
            glBindTexture(GL_TEXTURE_2D, stateA.texture)
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, n, n, GL_RGBA, GL_FLOAT, points)

            glDisable(GL_BLEND)
            iterate.bind()
            iterate.setUniform("uState", 0)
            iterate.setUniform("uN", n)
            iterate.setUniform("uNumXf", numXf)
            iterate.setUniform4fv("uXf", packed)
            glActiveTexture(GL_TEXTURE0)
            var src = stateA; var dst = stateB
            for (i in 0 until ITERATIONS) {
                dst.bind()
                glBindTexture(GL_TEXTURE_2D, src.texture)
                iterate.setUniform("uFrame", frame++)
                Geometry.drawFullscreenQuad()
                val t = src; src = dst; dst = t
            }
            glBindFramebuffer(GL_READ_FRAMEBUFFER, src.framebufferId)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glReadPixels(0, 0, n, n, GL_RGBA, GL_FLOAT, points)
        } finally {
            GlState.restore(saved)
        }
        return FlameQuality.analyse(points, n * n, ITERATIONS)
    }

    /** The points of the last [evaluate] (x, y, colour, age per point), for debugging views. */
    internal fun lastPoints(): FloatArray = points

    fun dispose() {
        stateA.dispose(); stateB.dispose(); iterate.dispose()
    }

    /** The bits of GL state a probe run changes, so a sampler can run in the middle of a UI frame. */
    private class GlState(
        val draw: Int, val read: Int, val program: Int, val vao: Int, val activeTex: Int, val tex0: Int,
        val blend: Boolean, val viewport: IntArray
    ) {
        companion object {
            fun save(): GlState {
                val vp = IntArray(4); glGetIntegerv(GL_VIEWPORT, vp)
                val active = glGetInteger(GL_ACTIVE_TEXTURE)
                glActiveTexture(GL_TEXTURE0)
                val tex0 = glGetInteger(GL_TEXTURE_BINDING_2D)
                glActiveTexture(active)
                return GlState(
                    glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
                    glGetInteger(GL_CURRENT_PROGRAM), glGetInteger(GL_VERTEX_ARRAY_BINDING), active, tex0,
                    glIsEnabled(GL_BLEND), vp
                )
            }

            fun restore(s: GlState) {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, s.draw)
                glBindFramebuffer(GL_READ_FRAMEBUFFER, s.read)
                glViewport(s.viewport[0], s.viewport[1], s.viewport[2], s.viewport[3])
                glUseProgram(s.program)
                glBindVertexArray(s.vao)
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, s.tex0)
                glActiveTexture(s.activeTex)
                if (s.blend) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            }
        }
    }

    companion object {
        const val SIDE = 128
        /** Enough for a contractive map to have settled on its attractor (0.9^40 is under 2%). */
        const val ITERATIONS = 40
    }
}
