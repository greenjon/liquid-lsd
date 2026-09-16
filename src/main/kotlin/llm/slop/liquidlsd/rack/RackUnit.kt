package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.rendering.isf.ISFFilter
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

    /** Returns all available patch jacks on the rear panel of this unit. */
    fun getRearPorts(): List<PatchPort>

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

    // Computed once from `id`/`unitType`, both fixed at construction -- avoids rebuilding this
    // list (and its PatchPort/fullId allocations) every frame RackRearChassisRenderer draws it.
    private val cachedRearPorts: List<PatchPort> by lazy {
        when (unitType) {
            RackUnitType.GENERATOR -> listOf(
                PatchPort(id, "video_out", "VIDEO OUT", PortDirection.OUTPUT),
                PatchPort(id, "cv_in", "CV MOD IN", PortDirection.INPUT, SignalType.CV)
            )
            RackUnitType.PROCESSOR -> listOf(
                PatchPort(id, "video_in", "VIDEO IN", PortDirection.INPUT),
                PatchPort(id, "mask_in", "MASK/SIDECHAIN IN", PortDirection.INPUT, SignalType.MASK),
                PatchPort(id, "cv_in", "CV MOD IN", PortDirection.INPUT, SignalType.CV),
                PatchPort(id, "video_out", "VIDEO OUT", PortDirection.OUTPUT)
            )
            RackUnitType.TRANSITION -> listOf(
                PatchPort(id, "video_in_a", "IN A", PortDirection.INPUT),
                PatchPort(id, "video_in_b", "IN B", PortDirection.INPUT),
                PatchPort(id, "mask_in", "MASK/SIDECHAIN IN", PortDirection.INPUT, SignalType.MASK),
                PatchPort(id, "cv_in", "CV MOD IN", PortDirection.INPUT, SignalType.CV),
                PatchPort(id, "video_out", "MASTER OUT", PortDirection.OUTPUT)
            )
            RackUnitType.UTILITY -> listOf(
                PatchPort(id, "video_in", "VIDEO IN", PortDirection.INPUT),
                PatchPort(id, "mask_in", "MASK/SIDECHAIN IN", PortDirection.INPUT, SignalType.MASK),
                PatchPort(id, "cv_in", "CV MOD IN", PortDirection.INPUT, SignalType.CV),
                PatchPort(id, "video_out", "VIDEO OUT", PortDirection.OUTPUT)
            )
        }
    }

    override fun getRearPorts(): List<PatchPort> = cachedRearPorts
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

    /**
     * Reads the deck's already-rendered clean source texture rather than re-invoking
     * [Renderer.render]. Every frame's main loop already calls [Renderer.renderDeck] for this
     * deck before the Rack UI draws (see `Main.kt` / `RackManager.process`), so re-rendering here
     * would both double the GPU cost and double-advance any per-frame state (e.g. persistent-
     * history ISF filters) -- this unit is a read-only monitor of that already-computed frame.
     */
    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int = deck.cleanFBO.texture
}

/**
 * Processor Unit wrapping an [ISFFilter] effect slot.
 */
class ISFProcessorUnit(
    val deck: Deck,
    val filter: ISFFilter,
    val slotIndex: Int,
    label: String = filter.displayName,
    heightU: Int = 1,
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(
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

    /**
     * Reads the deck's already-rendered per-slot FX texture (written this frame by
     * [Renderer.renderDeck]'s own chained FX loop) instead of re-invoking [ISFFilter.render].
     * Re-rendering the same filter instance a second time per frame would double its GPU cost
     * and, for filters with persistent per-frame history buffers (e.g. the modular feedback
     * filter), corrupt that history by advancing it twice per frame. Mirrors the same
     * enabled/dryWet fallback logic as [Deck.getOutputTexture].
     */
    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int {
        if (!filter.enabled || filter.dryWet.value <= 0f) return inputTexture
        val stageFbo = deck.fxFBOs.getOrNull(slotIndex) ?: return inputTexture
        return stageFbo.texture
    }
}

/**
 * Processor Unit wrapping a Deck's legacy feedback parameters ([Deck.fbGain], [Deck.fbDecay],
 * etc). These fields predate the 100% ISF pipeline migration (see ARCHITECTURE.md's "100% ISF
 * Pipeline & Modular Effects Engine" section) and are retained on [Deck] only for backward
 * preset/broadcast serialization -- they are no longer read by any shader. Feedback is now just
 * another ISF filter (`default_filters/feedback.fs`) that, when loaded into one of the deck's FX
 * slots, already appears in the rack as its own [ISFProcessorUnit] with real, live parameters.
 * This unit is therefore a true passthrough: curating its exposed params to a macro knob will
 * move the knob but produce no visible effect.
 */
class FeedbackProcessorUnit(
    val deck: Deck,
    val isDeckA: Boolean,
    label: String = if (isDeckA) "Deck A Feedback" else "Deck B Feedback",
    heightU: Int = 2,
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(
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
    ): Int = inputTexture
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

    /**
     * Reads the already-composited master output ([Mixer.masterFBO], written this frame by
     * [Renderer.renderMixer] in the main loop) rather than re-invoking it. The Mixer always
     * crossfades the live [Mixer.deckA]/[Mixer.deckB] state -- it isn't yet restructured to
     * accept externally patched inputs, so `inputTexture` (any cable plugged into IN A/IN B) is
     * not consumed here. Making that live would mean threading arbitrary external textures
     * through the single master-output path every workspace mode relies on, which is a bigger,
     * riskier change than this unit's read-only monitoring role.
     */
    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int = mixer.masterFBO.texture
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
