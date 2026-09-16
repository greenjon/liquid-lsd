package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.macro.MacroBank
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

    /** Dedicated Macro Bank owned by this unit instance. */
    val macroBank: MacroBank

    /** Whether the unit's macro curation drawer is currently unfolded. */
    var isMacroCurationOpen: Boolean

    /** Last processed OpenGL output texture ID produced by this unit (for micro-monitor and routing). */
    var lastOutputTexture: Int

    /** Returns all modulatable parameters exposed on this unit's faceplate. */
    fun getParameters(): List<ModulatableParameter>

    /** Returns all named modulatable parameters available for macro binding on this unit. */
    fun getNamedParameters(): Map<String, ModulatableParameter>

    /** Resolves a parameter on this unit by local name or path. */
    fun findParameter(paramId: String): ModulatableParameter? {
        return getNamedParameters()[paramId]
    }

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
    override var isSoloed: Boolean = false,
    override val macroBank: MacroBank = MacroBank(),
    override var isMacroCurationOpen: Boolean = false,
    override var lastOutputTexture: Int = 0
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
    heightU: Int = 2,
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(
    id = if (isDeckA) "deck_a_gen" else "deck_b_gen",
    label = label,
    unitType = RackUnitType.GENERATOR,
    heightU = heightU,
    macroBank = macroBank
) {
    override fun getNamedParameters(): Map<String, ModulatableParameter> {
        val map = LinkedHashMap<String, ModulatableParameter>()
        for ((name, p) in deck.source.parameters) {
            map[name] = p
        }
        if (!deck.source.is3D) {
            map["viewZoom"] = deck.viewZoom
            map["viewRotateZ"] = deck.viewRotateZ
            map["viewRotateX"] = deck.viewRotateX
            map["viewRotateY"] = deck.viewRotateY
            map["view3DMode"] = deck.view3DMode
        }
        return map
    }

    override fun getParameters(): List<ModulatableParameter> {
        return getNamedParameters().values.toList()
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
    heightU: Int = 1,
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(
    id = "isf_${filter.id}_$slotIndex",
    label = label,
    unitType = RackUnitType.PROCESSOR,
    heightU = heightU,
    macroBank = macroBank
) {
    override fun getNamedParameters(): Map<String, ModulatableParameter> {
        val map = LinkedHashMap<String, ModulatableParameter>()
        map["dryWet"] = filter.dryWet
        for ((name, p) in filter.parameters) {
            map[name] = p
        }
        return map
    }

    override fun getParameters(): List<ModulatableParameter> {
        return getNamedParameters().values.toList()
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
    heightU: Int = 2,
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(
    id = if (isDeckA) "deck_a_feedback" else "deck_b_feedback",
    label = label,
    unitType = RackUnitType.PROCESSOR,
    heightU = heightU,
    macroBank = macroBank
) {
    override fun getNamedParameters(): Map<String, ModulatableParameter> = linkedMapOf(
        "fbDecay" to deck.fbDecay,
        "fbGain" to deck.fbGain,
        "fbZoom" to deck.fbZoom,
        "fbRotate" to deck.fbRotate,
        "fbHueShift" to deck.fbHueShift,
        "fbBlur" to deck.fbBlur,
        "fbChroma" to deck.fbChroma,
        "fbMode" to deck.fbMode,
        "fbKaleido" to deck.fbKaleido
    )

    override fun getParameters(): List<ModulatableParameter> = getNamedParameters().values.toList()

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
    heightU: Int = 2,
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(
    id = "master_transition",
    label = label,
    unitType = RackUnitType.TRANSITION,
    heightU = heightU,
    macroBank = macroBank
) {
    override fun getNamedParameters(): Map<String, ModulatableParameter> = linkedMapOf(
        "crossfade" to mixer.crossfade,
        "mode" to mixer.mode,
        "masterAlpha" to mixer.masterAlpha,
        "bloom" to mixer.bloom
    )

    override fun getParameters(): List<ModulatableParameter> = getNamedParameters().values.toList()

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
    val namedParams: Map<String, ModulatableParameter> = emptyMap(),
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(id, label, unitType, heightU, macroBank = macroBank) {

    constructor(
        id: String,
        label: String,
        unitType: RackUnitType,
        heightU: Int,
        onProcess: ((inputTexture: Int) -> Int)?,
        exposedParams: List<ModulatableParameter>,
        macroBank: MacroBank = MacroBank()
    ) : this(
        id = id,
        label = label,
        unitType = unitType,
        heightU = heightU,
        onProcess = onProcess,
        namedParams = exposedParams.mapIndexed { idx, p -> "param_$idx" to p }.toMap(),
        macroBank = macroBank
    )

    override fun getNamedParameters(): Map<String, ModulatableParameter> = namedParams
    override fun getParameters(): List<ModulatableParameter> = namedParams.values.toList()

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
