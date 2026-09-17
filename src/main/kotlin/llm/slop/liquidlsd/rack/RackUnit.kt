package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Renderer
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
 * Merged Generator + FX Unit wrapping a full [Deck] chain -- the source generator plus up to
 * [Deck.FX_SLOT_COUNT] ISF FX slots -- as a single rack unit, per the §2.7 unit-consolidation
 * decision in `modular_video_rack_proposal.md`. Replaces the previous one-unit-per-pipeline-stage
 * split (one generator unit plus one FX unit per occupied slot): [getNamedParameters] flattens
 * the generator's own parameters (unprefixed) together with each occupied FX slot's
 * parameters (prefixed `"FX1/…"`..`"FX4/…"`), so any of this unit's macro knobs can target the
 * generator or any occupied FX slot -- including one knob driving both simultaneously, since
 * [MacroBinding] already supports multiple bindings per control.
 */
class DeckRackUnit(
    val deck: Deck,
    label: String,
    id: String = UUID.randomUUID().toString().take(8),
    heightU: Int = 3,
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(
    id = id,
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
        for (i in deck.fxSlots.indices) {
            val fx = deck.fxSlots[i] ?: continue
            map["FX${i + 1}/dryWet"] = fx.dryWet
            for ((name, p) in fx.parameters) {
                map["FX${i + 1}/$name"] = p
            }
        }
        return map
    }

    override fun getParameters(): List<ModulatableParameter> = getNamedParameters().values.toList()

    // No [update] override: [Deck.update] already calls `source.update()` and ticks every FX
    // slot's filter every frame from the main loop (see `Main.kt`), unconditionally of workspace
    // mode. Re-ticking here would double-advance any per-frame state (e.g. persistent-history ISF
    // filters) whenever Rack mode is visible -- inherits [BaseRackUnit]'s no-op default instead.

    /**
     * Reads the deck's already-rendered output -- the last active FX slot's texture, or the clean
     * generator texture if none are active -- via [Deck.getOutputTexture] rather than re-invoking
     * rendering. Every frame's main loop already renders this deck's full generator+FX chain
     * before the Rack UI draws (see `Main.kt` / `RackManager.process`), so re-rendering here would
     * both double the GPU cost and double-advance any per-frame state (e.g. persistent-history ISF
     * filters); this unit is a read-only monitor of that already-computed frame.
     */
    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int = deck.getOutputTexture()
}

/**
 * Transition Unit wrapping [Mixer] crossfader & blend filters.
 */
class MixerTransitionUnit(
    val mixer: Mixer,
    label: String = "Master Crossfader & Transition",
    id: String = UUID.randomUUID().toString().take(8),
    heightU: Int = 2,
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(
    id = id,
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

    // No [update] override: `Main.kt`'s main loop already calls [Mixer.update] unconditionally of
    // workspace mode every frame. Re-invoking it here would double-tick the Mixer (and everything
    // it drives, e.g. [llm.slop.liquidlsd.presets.BgQueueManager]'s dip-to-black timer) whenever
    // Rack mode is visible -- inherits [BaseRackUnit]'s no-op default instead.

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
 * Always-present "Queue & Staging" master unit (§ Question 1 decision in
 * `modular_video_rack_proposal.md`) -- a rack-native *view* onto the existing
 * [llm.slop.liquidlsd.presets.PlayQueueManager], [llm.slop.liquidlsd.presets.BgQueueManager], and
 * [llm.slop.liquidlsd.presets.TransitionQueueManager] singletons, not a new queue data model.
 * Carries no signal-processing role of its own: [process] passes its input texture straight
 * through unchanged, and [update] is intentionally a no-op -- all three managers are already
 * driven by their existing call paths (transport button presses, [Mixer.update] for the BG
 * dip-to-black state machine) regardless of which workspace is currently visible.
 */
class QueueStagingRackUnit(
    val mixer: Mixer,
    label: String = "Queue & Staging",
    id: String = UUID.randomUUID().toString().take(8),
    heightU: Int = 3,
    macroBank: MacroBank = MacroBank()
) : BaseRackUnit(
    id = id,
    label = label,
    unitType = RackUnitType.UTILITY,
    heightU = heightU,
    macroBank = macroBank
) {
    override fun getNamedParameters(): Map<String, ModulatableParameter> = emptyMap()
    override fun getParameters(): List<ModulatableParameter> = emptyList()

    override fun process(
        inputTexture: Int,
        outputFBO: FBO?,
        width: Int,
        height: Int,
        renderer: Renderer?
    ): Int = inputTexture
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
