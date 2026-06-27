package llm.slop.spirals.rendering

import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

private val logger = KotlinLogging.logger {}

/**
 * Utility class for managing simple geometry (VAO/VBO).
 */
object Geometry {

    private var fullscreenQuadVAO: Int = 0
    private var secondaryFullscreenQuadVAO: Int = 0
    private var fullscreenQuadVBO: Int = 0
    private var isInitialized = false

    /**
     * Get or create a fullscreen quad VAO.
     * Vertex format: [x, y, u, v] (position + texcoord)
     *
     * Returns the VAO ID. Call glBindVertexArray() before drawing.
     */
    fun getFullscreenQuad(): Int {
        if (!isInitialized) {
            initializeFullscreenQuad()
        }
        return fullscreenQuadVAO
    }

    private fun initializeFullscreenQuad() {
        // Fullscreen quad vertices: position (x, y) + texcoord (u, v)
        // Two triangles forming a quad from -1 to 1 in NDC
        val vertices = floatArrayOf(
            // Triangle 1
            -1f, -1f,  0f, 0f,  // Bottom-left
             1f, -1f,  1f, 0f,  // Bottom-right
             1f,  1f,  1f, 1f,  // Top-right

            // Triangle 2
             1f,  1f,  1f, 1f,  // Top-right
            -1f,  1f,  0f, 1f,  // Top-left
            -1f, -1f,  0f, 0f   // Bottom-left
        )

        // Create buffer
        val buffer: FloatBuffer = MemoryUtil.memAllocFloat(vertices.size)
        buffer.put(vertices).flip()

        try {
            // Generate VAO and VBO
            fullscreenQuadVAO = glGenVertexArrays()
            fullscreenQuadVBO = glGenBuffers()

            glBindVertexArray(fullscreenQuadVAO)
            glBindBuffer(GL_ARRAY_BUFFER, fullscreenQuadVBO)
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)

            // Position attribute (location = 0)
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.SIZE_BYTES, 0)
            glEnableVertexAttribArray(0)

            // TexCoord attribute (location = 1)
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES.toLong())
            glEnableVertexAttribArray(1)

            // Unbind
            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindVertexArray(0)

            logger.debug { "Initialized fullscreen quad VAO: $fullscreenQuadVAO, VBO: $fullscreenQuadVBO" }
            isInitialized = true
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    /**
     * Draw the fullscreen quad (call after binding appropriate shader and textures)
     */
    fun drawFullscreenQuad() {
        val vao = getFullscreenQuad()
        logger.trace { "Drawing fullscreen quad with VAO: $vao" }
        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLES, 0, 6)
        glBindVertexArray(0)
    }

    /**
     * Draw the fullscreen quad in the secondary window context.
     * Manages its own VAO since VAOs are not shared between contexts.
     */
    fun drawSecondaryFullscreenQuad() {
        if (!isInitialized) {
            initializeFullscreenQuad()
        }
        if (secondaryFullscreenQuadVAO == 0) {
            secondaryFullscreenQuadVAO = glGenVertexArrays()
            glBindVertexArray(secondaryFullscreenQuadVAO)
            glBindBuffer(GL_ARRAY_BUFFER, fullscreenQuadVBO)

            glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.SIZE_BYTES, 0)
            glEnableVertexAttribArray(0)

            glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES.toLong())
            glEnableVertexAttribArray(1)

            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindVertexArray(0)
            logger.debug { "Initialized secondary fullscreen quad VAO: $secondaryFullscreenQuadVAO" }
        }
        glBindVertexArray(secondaryFullscreenQuadVAO)
        glDrawArrays(GL_TRIANGLES, 0, 6)
        glBindVertexArray(0)
    }

    /**
     * Resets the secondary VAO reference. Call this when the secondary window context is destroyed.
     */
    fun resetSecondaryContext() {
        secondaryFullscreenQuadVAO = 0
    }

    /**
     * Clean up OpenGL resources
     */
    fun dispose() {
        if (isInitialized) {
            glDeleteBuffers(fullscreenQuadVBO)
            glDeleteVertexArrays(fullscreenQuadVAO)
            if (secondaryFullscreenQuadVAO != 0) {
                // Secondary VAO is typically deleted when the secondary context is destroyed.
                // We reset the reference here.
                secondaryFullscreenQuadVAO = 0
            }
            isInitialized = false
            logger.debug { "Disposed fullscreen quad geometry" }
        }
    }
}
