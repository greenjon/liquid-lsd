package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXPresetDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterOwner
import kotlin.math.roundToInt

/**
 * Represents a single visual rendering chain (Deck).
 * Manages its own offscreen Framebuffer Objects (FBOs) for ping-pong feedback effects,
 * as well as parameters that control the feedback loop.
 */
class Deck(
    initialSource: VisualSource,
    var width: Int = 1920,
    var height: Int = 1080,
    var isEmpty: Boolean = true
) : ParameterOwner {

    var source: VisualSource = initialSource
        set(value) {
            field = value
            if (value.is3D) {
                view3DMode.reset()
            }
            llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
            llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
        }

    // FBO for rendering the clean visual source output
    var cleanFBO = FBO(width, height)

    // Live references to both shared FxBanks this deck can route into (see FxBank -- a bank's 3
    // filter chains and their dry/wet are shared with any other deck routed to it; only this
    // deck's own send level, fxRouting, and its render-target FBOs below are deck-owned). Both
    // refs are set once by Mixer.init; which one is actually in use is resolved live below from
    // [fxRouting], so it can be modulated/switched per-frame.
    var fxBank1: FxBank? = null
    var fxBank2: FxBank? = null
    val fxSendLevel = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    val fxRouting = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 2.0f) // 0 = Off, 1 = FX1, 2 = FX2
    // This deck's own default FxRouting value, restored by reset() -- ModulatableParameter.reset()
    // reverts to the value baked in at construction, not whatever baseValue was set to afterward,
    // so Mixer.init's per-deck defaults (BG/PV default to FX2) need to be reapplied here too.
    var fxRoutingDefault: Float = 1.0f

    /** The bank this deck is actually routed to right now, resolved live from [fxRouting]. */
    val assignedFxBank: FxBank?
        get() = when (fxRouting.value.roundToInt().coerceIn(0, 2)) {
            1 -> fxBank1
            2 -> fxBank2
            else -> null
        }

    // Four-buffer ping-pong architecture:
    // Inner scratch pair for slot-to-slot progression within a chain:
    var fxPingFBO = FBO(width, height)
    var fxPongFBO = FBO(width, height)
    // Outer alternating pair carrying chain outputs forward:
    var fxChainOutFBO = FBO(width, height)
    var fxBankOutFBO = FBO(width, height)

    var activeOutputTexture: Int = cleanFBO.texture

    /** Read-only view of the assigned bank's filter slots, or 3 empty slots if unassigned. */
    val fxSlots: Array<llm.slop.liquidlsd.rendering.isf.ISFFilter?>
        get() = assignedFxBank?.slots ?: arrayOfNulls(FxBank.SLOT_COUNT)

    /** This deck's send level combined with its bank's shared master wet/dry, or 0 if unassigned/bypassed. */
    fun getEffectiveWet(bank: FxBank?): Float {
        val b = bank ?: return 0.0f
        if (!b.enabled) return 0.0f
        return fxSendLevel.value * b.masterWetDry.value
    }

    val fxEffectiveWet: Float
        get() = getEffectiveWet(assignedFxBank)

    fun resize(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight) return
        width = newWidth
        height = newHeight
        cleanFBO.dispose()
        fxPingFBO.dispose()
        fxPongFBO.dispose()
        fxChainOutFBO.dispose()
        fxBankOutFBO.dispose()

        cleanFBO = FBO(width, height)
        fxPingFBO = FBO(width, height)
        fxPongFBO = FBO(width, height)
        fxChainOutFBO = FBO(width, height)
        fxBankOutFBO = FBO(width, height)

        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxPingFBO.clear(0f, 0f, 0f, 0f)
        fxPongFBO.clear(0f, 0f, 0f, 0f)
        fxChainOutFBO.clear(0f, 0f, 0f, 0f)
        fxBankOutFBO.clear(0f, 0f, 0f, 0f)
        activeOutputTexture = cleanFBO.texture
        availableSources.forEach { src ->
            if (src is DynamicVisualSource) {
                src.fb1?.dispose()
                src.fb2?.dispose()
                src.fb1 = null
                src.fb2 = null
                src.fbIndex = 0
            }
        }
    }

    // Keep instances of all visual sources
    val availableSources = mutableListOf<VisualSource>()

    // 3D View parameters (universal for 2D visual sources)
    val view3DMode = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 4f) // 0 = 2D Flat, 1 = Tri-Axial (90°), 2 = Cube Cage, 3 = Hex-Planar (60°), 4 = Tetra Kaleido
    val viewZoom = ModulatableParameter(1.0f, minClamp = 0.1f, maxClamp = 5.0f)
    val viewRotateX = ModulatableParameter(0.0f, minClamp = -3.14159f, maxClamp = 3.14159f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS, explicitIsAngle = true)
    val viewRotateY = ModulatableParameter(0.0f, minClamp = -3.14159f, maxClamp = 3.14159f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS, explicitIsAngle = true)
    val viewRotateZ = ModulatableParameter(0.0f, minClamp = -3.14159f, maxClamp = 3.14159f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS, explicitIsAngle = true)
    val viewPersp = ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 1.0f)
    val viewDepthDim = ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 1.0f)
    val viewSeparation = ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f)
    val viewBlendMode = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f) // 1.0 = Additive, 0.0 = Alpha
    val viewRoundness = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f) // 1.0 = Circular Disc, 0.0 = Square Quad

    // Feedback parameters with custom clamp ranges
    val fbDecay = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val fbGain = ModulatableParameter(1.0f, minClamp = 0f, maxClamp = 2f)
    val fbZoom = ModulatableParameter(0.0f, minClamp = -1f, maxClamp = 1f) // negative is zoom out, positive is zoom in
    val fbRotate = ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS, explicitIsAngle = true) // in radians
    val fbHueShift = ModulatableParameter(0.0f, minClamp = -1f, maxClamp = 1f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS) // range 0..1
    val fbBlur = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f) // range 0..1
    val fbChroma = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val fbMode = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f) // 0 = Max, 1 = Difference
    val fbKaleido = ModulatableParameter(1.0f, minClamp = 1f, maxClamp = 12f)

    init {
        // Clear all FBOs at startup to prevent reading uninitialized GPU memory
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxPingFBO.clear(0f, 0f, 0f, 0f)
        fxPongFBO.clear(0f, 0f, 0f, 0f)
        fxChainOutFBO.clear(0f, 0f, 0f, 0f)
        fxBankOutFBO.clear(0f, 0f, 0f, 0f)

        val initialId = (initialSource as? DynamicVisualSource)?.id
        val registrySources = VisualSourceRegistry.availableSources
            .filter { it.id != initialId }
            .map { it.clone() }
        
        availableSources.add(initialSource.clone())
        availableSources.addAll(registrySources)
        this.source = availableSources.first()
    }

    fun reset() {
        isEmpty = true
        fxSendLevel.reset()
        fxRouting.reset()
        fxRouting.baseValue = fxRoutingDefault
        fxRouting.baseMin = fxRoutingDefault
        fxRouting.baseMax = fxRoutingDefault
        availableSources.forEach { src ->
            src.parameters.values.forEach { it.reset() }
            src.globalAlpha.reset()
            src.clear()
        }
        view3DMode.reset()
        viewZoom.reset()
        viewRotateX.reset()
        viewRotateY.reset()
        viewRotateZ.reset()
        viewPersp.reset()
        viewDepthDim.reset()
        viewSeparation.reset()
        viewBlendMode.reset()
        viewRoundness.reset()

        fbDecay.reset()
        fbGain.reset()
        fbZoom.reset()
        fbRotate.reset()
        fbHueShift.reset()
        fbBlur.reset()
        fbChroma.reset()
        fbMode.reset()
        fbKaleido.reset()
        source.clear()

        // Clear active FBOs
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxPingFBO.clear(0f, 0f, 0f, 0f)
        fxPongFBO.clear(0f, 0f, 0f, 0f)
        fxChainOutFBO.clear(0f, 0f, 0f, 0f)
        fxBankOutFBO.clear(0f, 0f, 0f, 0f)
        activeOutputTexture = cleanFBO.texture
        morphController.initFromCurrentState()
    }

    /**
     * Retrieves all randomizable parameters across the visual source, 3D view, and feedback system.
     */
    fun getAllRandomizableParameters(): List<ModulatableParameter> {
        val allParams = mutableListOf<ModulatableParameter>()
        allParams.addAll(this.source.parameters.values)
        allParams.add(this.source.globalAlpha)
        
        allParams.add(fxSendLevel)
        allParams.add(fxRouting)

        allParams.add(this.view3DMode)
        allParams.add(this.viewZoom)
        allParams.add(this.viewRotateX)
        allParams.add(this.viewRotateY)
        allParams.add(this.viewRotateZ)
        allParams.add(this.viewPersp)
        allParams.add(this.viewDepthDim)
        allParams.add(this.viewSeparation)
        allParams.add(this.viewBlendMode)
        allParams.add(this.viewRoundness)
        allParams.add(this.fbDecay)
        allParams.add(this.fbGain)
        allParams.add(this.fbZoom)
        allParams.add(this.fbRotate)
        allParams.add(this.fbHueShift)
        allParams.add(this.fbBlur)
        allParams.add(this.fbChroma)
        allParams.add(this.fbMode)
        allParams.add(this.fbKaleido)
        return allParams
    }

    val morphController = DeckMorphController(::getAllRandomizableParameters)

    /**
     * Retrieves the final output texture of the Deck (the active stage texture).
     */
    fun getOutputTexture(): Int = activeOutputTexture

    /**
     * Updates the underlying visual source and evaluates view and feedback parameters.
     */
    fun update() {
        source.update()
        fxRouting.evaluate()
        view3DMode.evaluate()
        viewZoom.evaluate()
        viewRotateX.evaluate()
        viewRotateY.evaluate()
        viewRotateZ.evaluate()
        viewPersp.evaluate()
        viewDepthDim.evaluate()
        viewSeparation.evaluate()
        viewBlendMode.evaluate()
        viewRoundness.evaluate()

        fbDecay.evaluate()
        fbGain.evaluate()
        fbZoom.evaluate()
        fbRotate.evaluate()
        fbHueShift.evaluate()
        fbBlur.evaluate()
        fbChroma.evaluate()
        fbMode.evaluate()
        fbKaleido.evaluate()
    }

    /**
     * Re-randomizes modulators and base values for all randomizable parameters in this Deck.
     */
    fun randomizeModulators() {
        morphController.forceRandomize()
    }

    /**
     * Disposes all FBOs associated with this Deck.
     */
    fun dispose() {
        cleanFBO.dispose()
        fxPingFBO.dispose()
        fxPongFBO.dispose()
        fxChainOutFBO.dispose()
        fxBankOutFBO.dispose()
        // Note: bank-owned filters are NOT disposed here -- they're
        // shared with any other deck routed to the same bank and outlive any one deck.
        // Note: `source` is always one of the entries in `availableSources`, so the
        // forEach below already disposes it. Do NOT call source.dispose() here — that
        // would double-free the active source's GPU objects.
        availableSources.forEach { it.dispose() }
    }

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        // Add all source parameters first (Mandala or DynamicVisualSource)
        list.addAll(source.getParameterPaths(prefix))

        // This deck's own send level into its assigned FxBank
        list.add("$prefix/FXChain/DryWet" to fxSendLevel)

        // Add Deck's View parameters
        list.add("$prefix/View/FxRouting" to fxRouting)
        list.add("$prefix/View/3DMode" to view3DMode)
        list.add("$prefix/View/Zoom" to viewZoom)
        list.add("$prefix/View/RotateX" to viewRotateX)
        list.add("$prefix/View/RotateY" to viewRotateY)
        list.add("$prefix/View/RotateZ" to viewRotateZ)
        list.add("$prefix/View/Persp" to viewPersp)
        list.add("$prefix/View/DepthDim" to viewDepthDim)
        list.add("$prefix/View/Separation" to viewSeparation)
        list.add("$prefix/View/BlendMode" to viewBlendMode)
        list.add("$prefix/View/Roundness" to viewRoundness)

        // Add Deck's own feedback parameters
        list.add("$prefix/FB/Decay" to fbDecay)
        list.add("$prefix/FB/Gain" to fbGain)
        list.add("$prefix/FB/Zoom" to fbZoom)
        list.add("$prefix/FB/Rotate" to fbRotate)
        list.add("$prefix/FB/HueShift" to fbHueShift)
        list.add("$prefix/FB/Blur" to fbBlur)
        list.add("$prefix/FB/Chroma" to fbChroma)
        list.add("$prefix/FB/Mode" to fbMode)
        list.add("$prefix/FB/Kaleido" to fbKaleido)
        
        return list
    }
}
