package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Renderer
import mu.KotlinLogging

/**
 * Executes the normalled top-down signal flow across an ordered list of [RackUnit] devices.
 *
 * Signal routing rules:
 * 1. Default normalled flow: Output of Unit N feeds Input of Unit N+1.
 * 2. Bypass passthrough: If a unit is bypassed or dryWet == 0, its shader pass is skipped
 *    and its input texture pointer is passed directly to the next stage (zero GPU overhead).
 * 3. Solo override: If any unit has [RackUnit.isSoloed] == true, the pipeline captures that
 *    unit's output and routes it as the rack's primary output.
 */
class RackPipeline(
    var width: Int = 1920,
    var height: Int = 1080,
    val allocateGlBuffers: Boolean = true
) {
    private val logger = KotlinLogging.logger {}

    // Ping-pong intermediate framebuffers for stage chaining
    var stageFboA: FBO? = if (allocateGlBuffers) FBO(width, height) else null
    var stageFboB: FBO? = if (allocateGlBuffers) FBO(width, height) else null

    var lastOutputTexture: Int = 0
        private set

    init {
        stageFboA?.clear(0f, 0f, 0f, 0f)
        stageFboB?.clear(0f, 0f, 0f, 0f)
    }

    fun resize(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight) return
        width = newWidth
        height = newHeight
        if (allocateGlBuffers) {
            stageFboA?.dispose()
            stageFboB?.dispose()
            stageFboA = FBO(width, height)
            stageFboB = FBO(width, height)
            stageFboA?.clear(0f, 0f, 0f, 0f)
            stageFboB?.clear(0f, 0f, 0f, 0f)
            logger.info { "Resized RackPipeline FBOs to ${width}x${height}" }
        }
    }

    /**
     * Evaluates the rack chain top-to-bottom.
     * Zero-allocation frame loop.
     */
    fun process(units: List<RackUnit>, renderer: Renderer?): Int {
        if (units.isEmpty()) {
            lastOutputTexture = 0
            return 0
        }

        var currentTexture = 0
        var soloTexture = 0
        var hasSolo = false
        var pingPongIndex = 0

        for (i in units.indices) {
            val unit = units[i]
            if (!unit.isPowered) {
                unit.lastOutputTexture = 0
                continue
            }

            val targetFBO = if (pingPongIndex % 2 == 0) stageFboA else stageFboB

            val stageOut = if (unit.isBypassed) {
                currentTexture
            } else {
                val out = unit.process(currentTexture, targetFBO, width, height, renderer)
                if (out != currentTexture) {
                    pingPongIndex++
                }
                out
            }

            unit.lastOutputTexture = stageOut
            currentTexture = stageOut

            if (unit.isSoloed) {
                soloTexture = stageOut
                hasSolo = true
            }
        }

        lastOutputTexture = if (hasSolo) soloTexture else currentTexture
        return lastOutputTexture
    }

    fun dispose() {
        stageFboA?.dispose()
        stageFboB?.dispose()
    }
}
