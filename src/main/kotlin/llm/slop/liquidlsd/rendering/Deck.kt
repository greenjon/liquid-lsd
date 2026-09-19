package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXPresetDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterOwner

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

    // ISF Filter Slots (chained in order: slot 0's output feeds slot 1's input, etc.)
    val fxSlots = arrayOfNulls<llm.slop.liquidlsd.rendering.isf.ISFFilter>(FX_SLOT_COUNT)
    var fxFBOs = Array(FX_SLOT_COUNT) { FBO(width, height) }

    // Master bypass/mix for the whole FX chain, independent of each slot's own enabled/dryWet.
    var fxChainEnabled: Boolean = true
    val fxChainDryWet = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    var fxChainOutFBO = FBO(width, height)

    fun resize(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight) return
        width = newWidth
        height = newHeight
        cleanFBO.dispose()
        fxFBOs.forEach { it.dispose() }
        fxChainOutFBO.dispose()
        cleanFBO = FBO(width, height)
        fxFBOs = Array(FX_SLOT_COUNT) { FBO(width, height) }
        fxChainOutFBO = FBO(width, height)
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxFBOs.forEach { it.clear(0f, 0f, 0f, 0f) }
        fxChainOutFBO.clear(0f, 0f, 0f, 0f)
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

    companion object {
        const val FX_SLOT_COUNT = 4
    }

    init {
        // Clear all FBOs at startup to prevent reading uninitialized GPU memory
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxFBOs.forEach { it.clear(0f, 0f, 0f, 0f) }

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
        fxSlots.forEach { it?.reset() }
        fxChainEnabled = true
        fxChainDryWet.reset()
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
        fxFBOs.forEach { it.clear(0f, 0f, 0f, 0f) }
        fxChainOutFBO.clear(0f, 0f, 0f, 0f)
        morphController.initFromCurrentState()
    }

    /**
     * Retrieves all randomizable parameters across the visual source, 3D view, and feedback system.
     */
    fun getAllRandomizableParameters(): List<ModulatableParameter> {
        val allParams = mutableListOf<ModulatableParameter>()
        allParams.addAll(this.source.parameters.values)
        allParams.add(this.source.globalAlpha)
        
        if (fxChainEnabled) {
            allParams.add(fxChainDryWet)
        }
        fxSlots.forEach { fx ->
            if (fx != null && fx.enabled) {
                allParams.add(fx.dryWet)
                allParams.addAll(fx.parameters.values)
            }
        }


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
    fun getOutputTexture(): Int {
        if (!fxChainEnabled || fxChainDryWet.value <= 0.0f) return cleanFBO.texture

        var wetTexture = cleanFBO.texture
        for (i in fxSlots.indices.reversed()) {
            val fx = fxSlots[i]
            if (fx != null && fx.enabled && fx.dryWet.value > 0.0f) {
                wetTexture = fxFBOs[i].texture
                break
            }
        }
        if (wetTexture == cleanFBO.texture) return cleanFBO.texture
        return if (fxChainDryWet.value < 1.0f) fxChainOutFBO.texture else wetTexture
    }

    /**
     * Updates the underlying visual source and evaluates view and feedback parameters.
     */
    fun update() {
        source.update()
        fxSlots.forEach { it?.update() }
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
        fxFBOs.forEach { it.dispose() }
        fxSlots.forEach { it?.dispose() }
        // Note: `source` is always one of the entries in `availableSources`, so the
        // forEach below already disposes it. Do NOT call source.dispose() here — that
        // would double-free the active source's GPU objects.
        availableSources.forEach { it.dispose() }
    }

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        // Add all source parameters first (Mandala or DynamicVisualSource)
        list.addAll(source.getParameterPaths(prefix))

        // Add FX parameters
        fxSlots.forEachIndexed { i, fx ->
            fx?.getParameterPaths("$prefix/FX${i + 1}")?.let { list.addAll(it) }
        }

        // Add Deck's View parameters
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

    fun toFxSlotDto(slotIndex: Int): FXSlotDto? {
        val fx = fxSlots.getOrNull(slotIndex) ?: return null
        if (fx.id.isEmpty()) return null
        return FXSlotDto(
            filterId = fx.id,
            enabled = fx.enabled,
            dryWet = fx.dryWet.toDto(),
            parameters = fx.parameters.mapValues { p -> p.value.toDto() }
        )
    }

    fun applyFxSlot(slotIndex: Int, dto: FXSlotDto) {
        if (slotIndex !in fxSlots.indices) return
        fxSlots[slotIndex]?.dispose()
        fxSlots[slotIndex] = null

        if (dto.filterId.isNotBlank()) {
            val filter = llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.createFilter(dto.filterId)
            if (filter != null) {
                filter.enabled = dto.enabled
                filter.dryWet.applyDto(dto.dryWet)
                for ((key, paramDto) in dto.parameters) {
                    filter.parameters[key]?.applyDto(paramDto)
                }
                fxSlots[slotIndex] = filter
            }
        }
    }

    fun clearFxSlot(slotIndex: Int) {
        if (slotIndex in fxSlots.indices) {
            fxSlots[slotIndex]?.dispose()
            fxSlots[slotIndex] = null
        }
    }

    fun applyFxChain(dto: FXChainDto) {
        for (i in fxSlots.indices) {
            clearFxSlot(i)
            val slotDto = dto.slots.getOrNull(i)
            if (slotDto != null && slotDto.filterId.isNotBlank()) {
                applyFxSlot(i, slotDto)
            }
        }
    }

    fun toFxChainDto(name: String, tags: List<String> = emptyList()): FXChainDto {
        val slotsList = (0 until FX_SLOT_COUNT).map { toFxSlotDto(it) }
        return FXChainDto(
            name = name,
            tags = tags,
            slots = slotsList
        )
    }
}
