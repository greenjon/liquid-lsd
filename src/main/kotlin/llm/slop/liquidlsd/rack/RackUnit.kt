package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Geometry
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import org.lwjgl.opengl.GL33.*
import java.util.UUID

/**
 * Common contract for a modular device mounted inside the 19" Rack Bay.
 */
interface RackUnit {
    val id: String
    var label: String
    val unitType: RackUnitType
    var heightU: Int
    var isCollapsed: Boolean
    var isPowered: Boolean
    var isBypassed: Boolean
    var isSoloed: Boolean

    /** Returns all modulatable parameters exposed on this unit's faceplate. */
    fun getParameters(): List<ModulatableParameter>

    /** Updates any internal state or timers. */
    fun update()

    /**
     * Executes the unit's visual processing for a frame.
     *
     * @param inputTexture OpenGL texture handle passed from upstream.
     * @param outputFBO Dedicated render target FBO for this stage.
     * @param width Render width in pixels.
     * @param height Render height in pixels.
     * @param renderer Global renderer instance.
     * @return Resulting OpenGL texture ID of this stage.
     */
    fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int

    /** Releases any dedicated GPU resources held by this unit. */
    fun dispose() {}
}

/**
 * Base abstract class providing common rack unit state management.
 */
abstract class BaseRackUnit(
    override val id: String = UUID.randomUUID().toString().take(8),
    override var label: String,
    override val unitType: RackUnitType,
    override var heightU: Int = 2,
    override var isCollapsed: Boolean = false,
    override var isPowered: Boolean = true,
    override var isBypassed: Boolean = false,
    override var isSoloed: Boolean = false
) : RackUnit {
    override fun update() {}
}

/**
 * Generator Unit wrapping a [Deck] visual synthesizer (e.g. Deck A or Deck B).
 */
class DeckGeneratorUnit(
    val deck: Deck,
    val isDeckA: Boolean,
    label: String = if (isDeckA) "Deck A Generator" else "Deck B Generator",
    heightU: Int = 2
) : BaseRackUnit(
    id = if (isDeckA) "deck_a_gen" else "deck_b_gen",
    label = label,
    unitType = RackUnitType.GENERATOR,
    heightU = heightU
) {
    override fun getParameters(): List<ModulatableParameter> {
        val params = mutableListOf<ModulatableParameter>()
        params.addAll(deck.source.parameters.values)
        if (!deck.source.is3D) {
            params.add(deck.viewZoom)
            params.add(deck.viewRotateZ)
        }
        return params
    }

    override fun update() {
        deck.source.update()
    }

    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int {
        if (!isPowered || renderer == null) return 0
        val zoom = if (deck.source.is3D) 1.0f else deck.viewZoom.value
        val rotZ = if (deck.source.is3D) 0.0f else deck.viewRotateZ.value
        renderer.render(deck.source, deck.cleanFBO, zoom, rotZ)
        return deck.cleanFBO.texture
    }
}

/**
 * Processor Unit wrapping an [ISFFilter] effect slot.
 */
class ISFProcessorUnit(
    val filter: ISFFilter,
    val slotIndex: Int,
    label: String = filter.displayName,
    heightU: Int = 1
) : BaseRackUnit(
    id = "isf_${filter.id}_$slotIndex",
    label = label,
    unitType = RackUnitType.PROCESSOR,
    heightU = heightU
) {
    override fun getParameters(): List<ModulatableParameter> {
        val params = mutableListOf<ModulatableParameter>()
        params.add(filter.dryWet)
        params.addAll(filter.parameters.values)
        return params
    }

    override fun update() {
        filter.update()
    }

    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int {
        if (!isPowered || isBypassed || filter.dryWet.value <= 0f || outputFBO == null || renderer == null) {
            return inputTexture
        }

        outputFBO.bind()
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_BLEND)

        filter.render(inputTexture, width, height)

        val dryWet = filter.dryWet.value
        if (dryWet < 1.0f) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA)
            glBlendColor(0f, 0f, 0f, 1.0f - dryWet)

            renderer.blitShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, inputTexture)
            renderer.blitShader.setUniform("uTexture", 0)
            Geometry.drawFullscreenQuad()
            renderer.blitShader.unbind()
            glDisable(GL_BLEND)
        }

        outputFBO.unbind()
        return outputFBO.texture
    }
}

/**
 * Processor Unit wrapping a Deck's feedback stage.
 */
class FeedbackProcessorUnit(
    val deck: Deck,
    val isDeckA: Boolean,
    label: String = if (isDeckA) "Deck A Feedback" else "Deck B Feedback",
    heightU: Int = 2
) : BaseRackUnit(
    id = if (isDeckA) "deck_a_feedback" else "deck_b_feedback",
    label = label,
    unitType = RackUnitType.PROCESSOR,
    heightU = heightU
) {
    override fun getParameters(): List<ModulatableParameter> = listOf(
        deck.fbDecay,
        deck.fbGain,
        deck.fbZoom,
        deck.fbRotate,
        deck.fbHueShift,
        deck.fbBlur,
        deck.fbChroma,
        deck.fbMode,
        deck.fbKaleido
    )

    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int {
        if (!isPowered || isBypassed) return inputTexture
        // Return active deck feedback output texture
        return deck.getOutputTexture()
    }
}

/**
 * Transition Unit wrapping [Mixer] crossfader & blend filters.
 */
class MixerTransitionUnit(
    val mixer: Mixer,
    label: String = "Master Crossfader & Transition",
    heightU: Int = 2
) : BaseRackUnit(
    id = "master_transition",
    label = label,
    unitType = RackUnitType.TRANSITION,
    heightU = heightU
) {
    override fun getParameters(): List<ModulatableParameter> = listOf(
        mixer.crossfade,
        mixer.mode,
        mixer.masterAlpha,
        mixer.bloom
    )

    override fun update() {
        mixer.update()
    }

    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int {
        if (!isPowered || isBypassed || renderer == null) return inputTexture
        renderer.renderMixer(mixer)
        return mixer.masterFBO.texture
    }
}

/**
 * Generic mockable/configurable unit for testing and custom bridges.
 */
class GenericRackUnit(
    id: String = UUID.randomUUID().toString().take(8),
    label: String = "Generic Unit",
    unitType: RackUnitType = RackUnitType.UTILITY,
    heightU: Int = 1,
    var onProcess: ((inputTexture: Int) -> Int)? = null,
    val exposedParams: List<ModulatableParameter> = emptyList()
) : BaseRackUnit(id, label, unitType, heightU) {
    override fun getParameters(): List<ModulatableParameter> = exposedParams

    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int {
        if (!isPowered || isBypassed) return inputTexture
        return onProcess?.invoke(inputTexture) ?: inputTexture
    }
}
