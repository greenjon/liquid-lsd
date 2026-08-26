package llm.slop.liquidlsd.export

import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Shader
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * High-precision Accumulation Buffer for temporal super-sampling / motion blur.
 * Accumulates N sub-frame passes per output frame to simulate physical camera shutter exposure.
 */
class AccumulationBuffer(
    val width: Int,
    val height: Int,
    val blitShader: Shader
) {
    // 16-bit floating point FBO for HDR accumulation without color banding
    val fbo = FBO(width, height, internalFormat = GL_RGBA16F)
    private var isDisposed = false

    fun clear() {
        fbo.bind()
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
    }

    /**
     * Accumulates a sub-frame from a source FBO with the given weight (1.0 / N).
     */
    fun accumulate(sourceFbo: FBO, weight: Float) {
        fbo.bind()
        glViewport(0, 0, width, height)

        glEnable(GL_BLEND)
        // Additive blend: dst.rgb = dst.rgb + src.rgb * weight, dst.a = dst.a + src.a * weight
        glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE)
        glBlendColor(1f, 1f, 1f, weight)

        blitShader.bind()
        blitShader.setUniform("src", 0)
        blitShader.setUniform("uResolution", width.toFloat(), height.toFloat())

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, sourceFbo.texture)

        // Draw full-screen quad via glDrawArrays with dummy VAO
        glBindVertexArray(0)
        glDrawArrays(GL_TRIANGLES, 0, 6)

        glDisable(GL_BLEND)
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        fbo.dispose()
    }
}
