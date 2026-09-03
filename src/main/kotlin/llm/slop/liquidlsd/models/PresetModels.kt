package llm.slop.liquidlsd.models

import kotlinx.serialization.Serializable
import llm.slop.liquidlsd.parameters.*
import llm.slop.liquidlsd.rendering.*

@Serializable
data class ModulatorDto(
    val sourceId: String,
    val operator: String, // "ADD" or "MUL"
    val depth: Float,
    val bypassed: Boolean = false,
    val waveform: String = "SINE",
    val subdivision: Float = 1.0f,
    val phaseOffset: Float = 0.0f,
    val slope: Float = 0.5f,
    val lfoSpeedMode: String = "FAST",
    val genUnit: String = "TIME",
    val modGenUnit: String = "TIME",
    
    // Randomization bounds
    val depthMin: Float,
    val depthMax: Float,
    val subdivisionMin: Float,
    val subdivisionMax: Float,
    val phaseOffsetMin: Float,
    val phaseOffsetMax: Float,
    val slopeMin: Float,
    val slopeMax: Float,
    val randomizeDepth: Boolean = false,
    val randomizeSubdivision: Boolean = false,
    val randomizePhaseOffset: Boolean = false,
    val randomizeSlope: Boolean = false,

    // Advanced LFO fields
    val morph: Float = 0.0f,
    val morphMin: Float = 0.0f,
    val morphMax: Float = 0.0f,
    val randomizeMorph: Boolean = false,
    val hold: Float = 0.0f,
    val holdMin: Float = 0.0f,
    val holdMax: Float = 0.0f,
    val randomizeHold: Boolean = false,

    // DC Offset fields
    val dcOffset: Float = 0.0f,
    val dcOffsetMin: Float = 0.0f,
    val dcOffsetMax: Float = 0.0f,
    val randomizeDcOffset: Boolean = false,

    // Audio envelope follower fields
    val followerMode: String = "RAW",
    val attackMs: Float = 0.0f,
    val decayMs: Float = 0.0f,
    val attackMsMin: Float = 0.0f,
    val attackMsMax: Float = 0.0f,
    val decayMsMin: Float = 0.0f,
    val decayMsMax: Float = 0.0f,
    val randomizeAttackMs: Boolean = false,
    val randomizeDecayMs: Boolean = false,

    val id: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModulatorDto) return false
        
        if (sourceId != other.sourceId) return false
        if (operator != other.operator) return false
        if (bypassed != other.bypassed) return false
        if (waveform != other.waveform) return false
        if (lfoSpeedMode != other.lfoSpeedMode) return false
        
        if (depthMin != other.depthMin) return false
        if (depthMax != other.depthMax) return false
        if (subdivisionMin != other.subdivisionMin) return false
        if (subdivisionMax != other.subdivisionMax) return false
        if (phaseOffsetMin != other.phaseOffsetMin) return false
        if (phaseOffsetMax != other.phaseOffsetMax) return false
        if (slopeMin != other.slopeMin) return false
        if (slopeMax != other.slopeMax) return false
        if (dcOffsetMin != other.dcOffsetMin) return false
        if (dcOffsetMax != other.dcOffsetMax) return false
        
        if (randomizeDepth != other.randomizeDepth) return false
        if (randomizeSubdivision != other.randomizeSubdivision) return false
        if (randomizePhaseOffset != other.randomizePhaseOffset) return false
        if (randomizeSlope != other.randomizeSlope) return false
        if (randomizeDcOffset != other.randomizeDcOffset) return false
        
        // Exclude instantaneous values from equality check if they are subject to active randomization
        val isDepthRandom = (randomizeDepth && depthMin != depthMax) || depthMin != other.depthMin || depthMax != other.depthMax
        if (!isDepthRandom && depth != other.depth) return false
        
        val isSubRandom = (randomizeSubdivision && subdivisionMin != subdivisionMax) || subdivisionMin != other.subdivisionMin || subdivisionMax != other.subdivisionMax
        if (!isSubRandom && subdivision != other.subdivision) return false
        
        val isPhaseRandom = (randomizePhaseOffset && phaseOffsetMin != phaseOffsetMax) || phaseOffsetMin != other.phaseOffsetMin || phaseOffsetMax != other.phaseOffsetMax
        if (!isPhaseRandom && phaseOffset != other.phaseOffset) return false
        
        val isSlopeRandom = (randomizeSlope && slopeMin != slopeMax) || slopeMin != other.slopeMin || slopeMax != other.slopeMax
        if (!isSlopeRandom && slope != other.slope) return false
        
        val isDcRandom = (randomizeDcOffset && dcOffsetMin != dcOffsetMax) || dcOffsetMin != other.dcOffsetMin || dcOffsetMax != other.dcOffsetMax
        if (!isDcRandom && dcOffset != other.dcOffset) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourceId.hashCode()
        result = 31 * result + operator.hashCode()
        result = 31 * result + bypassed.hashCode()
        result = 31 * result + waveform.hashCode()
        result = 31 * result + lfoSpeedMode.hashCode()
        
        result = 31 * result + depthMin.hashCode()
        result = 31 * result + depthMax.hashCode()
        result = 31 * result + subdivisionMin.hashCode()
        result = 31 * result + subdivisionMax.hashCode()
        result = 31 * result + phaseOffsetMin.hashCode()
        result = 31 * result + phaseOffsetMax.hashCode()
        result = 31 * result + slopeMin.hashCode()
        result = 31 * result + slopeMax.hashCode()
        result = 31 * result + dcOffsetMin.hashCode()
        result = 31 * result + dcOffsetMax.hashCode()

        result = 31 * result + randomizeDepth.hashCode()
        result = 31 * result + randomizeSubdivision.hashCode()
        result = 31 * result + randomizePhaseOffset.hashCode()
        result = 31 * result + randomizeSlope.hashCode()
        result = 31 * result + randomizeDcOffset.hashCode()

        val isDepthRandom = randomizeDepth && depthMin != depthMax
        if (!isDepthRandom) result = 31 * result + depth.hashCode()
        
        val isSubRandom = randomizeSubdivision && subdivisionMin != subdivisionMax
        if (!isSubRandom) result = 31 * result + subdivision.hashCode()
        
        val isPhaseRandom = randomizePhaseOffset && phaseOffsetMin != phaseOffsetMax
        if (!isPhaseRandom) result = 31 * result + phaseOffset.hashCode()
        
        val isSlopeRandom = randomizeSlope && slopeMin != slopeMax
        if (!isSlopeRandom) result = 31 * result + slope.hashCode()
        
        val isDcRandom = randomizeDcOffset && dcOffsetMin != dcOffsetMax
        if (!isDcRandom) result = 31 * result + dcOffset.hashCode()
        
        return result
    }
}

@Serializable
data class ParameterDto(
    val baseValue: Float,
    val baseMin: Float,
    val baseMax: Float,
    val randomizeBase: Boolean,
    val modulators: List<ModulatorDto>,
    val mappedMidiId: String? = null,
    val midiMapMin: Float = 0f,
    val midiMapMax: Float = 1f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParameterDto) return false

        if (baseMin != other.baseMin) return false
        if (baseMax != other.baseMax) return false
        if (randomizeBase != other.randomizeBase) return false
        if (mappedMidiId != other.mappedMidiId) return false
        if (midiMapMin != other.midiMapMin) return false
        if (midiMapMax != other.midiMapMax) return false
        if (modulators != other.modulators) return false

        // Exclude instantaneous baseValue from equality check only if it is subject to active randomization
        val isRandomized = (randomizeBase && baseMin != baseMax) || baseMin != other.baseMin || baseMax != other.baseMax
        if (!isRandomized && baseValue != other.baseValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = baseMin.hashCode()
        result = 31 * result + baseMax.hashCode()
        result = 31 * result + randomizeBase.hashCode()
        result = 31 * result + (mappedMidiId?.hashCode() ?: 0)
        result = 31 * result + midiMapMin.hashCode()
        result = 31 * result + midiMapMax.hashCode()
        result = 31 * result + modulators.hashCode()

        val isRandomized = randomizeBase && baseMin != baseMax
        if (!isRandomized) result = 31 * result + baseValue.hashCode()
        return result
    }
}

@Serializable
data class DeckPresetDto(
    val version: Int = 1,
    val name: String,
    val tags: List<String> = emptyList(),
    val visualSourceType: String, // e.g., "Mandala" or "Mandelbulb"
    val recipe: MandalaRecipeDto? = null, // For restoring recipe structure (Mandala-only)
    val parameters: Map<String, ParameterDto>, // Visual source params
    val feedbackParameters: Map<String, ParameterDto>, // Feedback chain params
    val viewParameters: Map<String, ParameterDto> = emptyMap(), // 3D View chain params
    val globalAlpha: ParameterDto? = null,
    val isEmpty: Boolean = false,
    val presetNotes: String = "",             // User notes for this preset
    val paramNotes: Map<String, String> = emptyMap() // Per-parameter notes keyed by paramKey
)

@Serializable
data class MandalaRecipeDto(
    val a: Int,
    val b: Int,
    val c: Int,
    val d: Int,
    val id: String? = null
)

@Serializable
data class SessionStateDto(
    val version: Int = 5,
    val deckA: DeckPresetDto,
    val deckB: DeckPresetDto,
    val deckBG: DeckPresetDto? = null,
    val deckPV: DeckPresetDto? = null,
    val crossfade: ParameterDto,
    val masterAlpha: ParameterDto,
    val blendMode: Float,
    val queue: List<String>,
    val activeIndex: Int,
    val isAutoVJEnabled: Boolean,
    val bloom: ParameterDto? = null,
    val xfadeSpeed: ParameterDto? = null,
    val queueNext: ParameterDto? = null,
    val queuePrev: ParameterDto? = null,
    val bgQueueNext: ParameterDto? = null,
    val bgQueuePrev: ParameterDto? = null,
    val isRepeatEnabled: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val bgQueue: List<String> = emptyList(),
    val bgActiveIndex: Int = -1,
    val isAutoBGEnabled: Boolean = false,
    val isBgRepeatEnabled: Boolean = false,
    val isBgShuffleEnabled: Boolean = false
)

@Serializable
data class PlaylistDto(
    val version: Int = 1,
    val name: String,
    val items: List<String> // List of .lsd file names (relative to library/presets)
)

// --- Extension Converters ---

fun CvModulator.toDto(): ModulatorDto = ModulatorDto(
    sourceId = sourceId,
    operator = operator.name,
    depth = depth,
    bypassed = bypassed,
    waveform = waveform.name,
    subdivision = subdivision,
    phaseOffset = phaseOffset,
    slope = slope,
    lfoSpeedMode = lfoSpeedMode.name,
    genUnit = genUnit.name,
    modGenUnit = modGenUnit.name,
    depthMin = depthMin,
    depthMax = depthMax,
    subdivisionMin = subdivisionMin,
    subdivisionMax = subdivisionMax,
    phaseOffsetMin = phaseOffsetMin,
    phaseOffsetMax = phaseOffsetMax,
    slopeMin = slopeMin,
    slopeMax = slopeMax,
    randomizeDepth = randomizeDepth,
    randomizeSubdivision = randomizeSubdivision,
    randomizePhaseOffset = randomizePhaseOffset,
    randomizeSlope = randomizeSlope,
    morph = morph,
    morphMin = morphMin,
    morphMax = morphMax,
    randomizeMorph = randomizeMorph,
    hold = hold,
    holdMin = holdMin,
    holdMax = holdMax,
    randomizeHold = randomizeHold,
    dcOffset = dcOffset,
    dcOffsetMin = dcOffsetMin,
    dcOffsetMax = dcOffsetMax,
    randomizeDcOffset = randomizeDcOffset,
    followerMode = followerMode.name,
    attackMs = attackMs,
    decayMs = decayMs,
    attackMsMin = attackMsMin,
    attackMsMax = attackMsMax,
    decayMsMin = decayMsMin,
    decayMsMax = decayMsMax,
    randomizeAttackMs = randomizeAttackMs,
    randomizeDecayMs = randomizeDecayMs,
    id = id
)

fun ModulatorDto.toDomain(): CvModulator = CvModulator(
    sourceId = sourceId,
    operator = ModulationOperator.valueOf(operator),
    depth = depth,
    bypassed = bypassed,
    waveform = Waveform.valueOf(waveform),
    subdivision = subdivision,
    phaseOffset = phaseOffset,
    slope = slope,
    lfoSpeedMode = LfoSpeedMode.valueOf(lfoSpeedMode),
    genUnit = GenUnit.valueOf(genUnit),
    modGenUnit = GenUnit.valueOf(modGenUnit),
    depthMin = depthMin,
    depthMax = depthMax,
    subdivisionMin = subdivisionMin,
    subdivisionMax = subdivisionMax,
    phaseOffsetMin = phaseOffsetMin,
    phaseOffsetMax = phaseOffsetMax,
    slopeMin = slopeMin,
    slopeMax = slopeMax,
    randomizeDepth = randomizeDepth,
    randomizeSubdivision = randomizeSubdivision,
    randomizePhaseOffset = randomizePhaseOffset,
    randomizeSlope = randomizeSlope,
    morph = morph,
    morphMin = morphMin,
    morphMax = morphMax,
    randomizeMorph = randomizeMorph,
    hold = hold,
    holdMin = holdMin,
    holdMax = holdMax,
    randomizeHold = randomizeHold,
    dcOffset = dcOffset,
    dcOffsetMin = dcOffsetMin,
    dcOffsetMax = dcOffsetMax,
    randomizeDcOffset = randomizeDcOffset,
    followerMode = try { AudioFollowerMode.valueOf(followerMode) } catch (_: Exception) { AudioFollowerMode.RAW },
    attackMs = attackMs,
    decayMs = decayMs,
    attackMsMin = attackMsMin,
    attackMsMax = attackMsMax,
    decayMsMin = decayMsMin,
    decayMsMax = decayMsMax,
    randomizeAttackMs = randomizeAttackMs,
    randomizeDecayMs = randomizeDecayMs,
    id = id ?: java.util.UUID.randomUUID().toString()
)

@Suppress("DEPRECATION")
fun ModulatableParameter.toDto(): ParameterDto = ParameterDto(
    baseValue = baseValue,
    baseMin = baseMin,
    baseMax = baseMax,
    randomizeBase = randomizeBase,
    modulators = modulators.map { it.toDto() },
    mappedMidiId = mappedMidiId,
    midiMapMin = midiMapMin,
    midiMapMax = midiMapMax
)

@Suppress("DEPRECATION")
fun ModulatableParameter.applyDto(dto: ParameterDto) {
    this.baseValue = dto.baseValue
    this.baseMin = dto.baseMin
    this.baseMax = dto.baseMax
    this.randomizeBase = dto.randomizeBase
    this.mappedMidiId = dto.mappedMidiId
    this.midiMapMin = dto.midiMapMin
    this.midiMapMax = dto.midiMapMax
    
    // Safety check for CopyOnWriteArrayList: clear and addAll
    this.modulators.clear()
    this.modulators.addAll(dto.modulators.map { it.toDomain() })
    this.value = dto.baseValue
}

fun MandalaRatio.toDto(): MandalaRecipeDto = MandalaRecipeDto(a, b, c, d, id)

fun Deck.toDto(name: String, tags: List<String> = emptyList()): DeckPresetDto {
    val sourceName = if (source is llm.slop.liquidlsd.rendering.DynamicVisualSource) (source as llm.slop.liquidlsd.rendering.DynamicVisualSource).id else "mandala"
    val recipeDto = if (source is Mandala) (source as Mandala).recipe.toDto() else null
    
    val paramsMap = source.parameters.mapValues { it.value.toDto() }
    
    val feedbackParamsMap = mapOf(
        "fbDecay" to fbDecay.toDto(),
        "fbGain" to fbGain.toDto(),
        "fbZoom" to fbZoom.toDto(),
        "fbRotate" to fbRotate.toDto(),
        "fbHueShift" to fbHueShift.toDto(),
        "fbBlur" to fbBlur.toDto(),
        "fbChroma" to fbChroma.toDto(),
        "fbMode" to fbMode.toDto(),
        "fbKaleido" to fbKaleido.toDto()
    )

    val viewParamsMap = mapOf(
        "view3DMode" to view3DMode.toDto(),
        "viewZoom" to viewZoom.toDto(),
        "viewRotateX" to viewRotateX.toDto(),
        "viewRotateY" to viewRotateY.toDto(),
        "viewRotateZ" to viewRotateZ.toDto(),
        "viewPersp" to viewPersp.toDto(),
        "viewDepthDim" to viewDepthDim.toDto(),
        "viewSeparation" to viewSeparation.toDto(),
        "viewBlendMode" to viewBlendMode.toDto()
    )
    
    return DeckPresetDto(
        name = name,
        tags = tags,
        visualSourceType = sourceName,
        recipe = recipeDto,
        parameters = paramsMap,
        feedbackParameters = feedbackParamsMap,
        viewParameters = viewParamsMap,
        globalAlpha = source.globalAlpha.toDto(),
        isEmpty = isEmpty
    )
}

fun Deck.applyDto(dto: DeckPresetDto) {
    if (dto.isEmpty) {
        reset()
        val defaultSource = availableSources.firstOrNull { (it as? llm.slop.liquidlsd.rendering.DynamicVisualSource)?.id == "mandala" } ?: availableSources.first()
        source = defaultSource
        return
    }
    this.isEmpty = false
    
    // Select the active source by visualSourceType id
    val matchedSource = availableSources.firstOrNull { src ->
        (src as? llm.slop.liquidlsd.rendering.DynamicVisualSource)?.id == dto.visualSourceType
    }
    if (matchedSource != null) {
        source = matchedSource
    }
    
    if (source is Mandala) {
        val mandalaObj = source as Mandala
        mandalaObj.parameters.values.forEach { it.reset() }
        val recipeDto = dto.recipe ?: MandalaRecipeDto(3, 3, 3, 3)
        // Recreate or lookup recipe
        val recipe = dto.recipe?.id?.let { savedId ->
            MandalaLibrary.MandalaRatios.firstOrNull { it.id == savedId }
        } ?: MandalaLibrary.MandalaRatios.firstOrNull {
            it.a == recipeDto.a && it.b == recipeDto.b &&
            it.c == recipeDto.c && it.d == recipeDto.d
        } ?: MandalaRatio(
            id = "custom_${recipeDto.a}_${recipeDto.b}_${recipeDto.c}_${recipeDto.d}",
            a = recipeDto.a, b = recipeDto.b, c = recipeDto.c, d = recipeDto.d
        )
        mandalaObj.recipe = recipe
        
        // Apply visual source parameters directly
        for ((key, paramDto) in dto.parameters) {
            mandalaObj.parameters[key]?.applyDto(paramDto)
        }
    } else if (source is llm.slop.liquidlsd.rendering.DynamicVisualSource) {
        val dynObj = source as llm.slop.liquidlsd.rendering.DynamicVisualSource
        dynObj.parameters.values.forEach { it.reset() }
        for ((key, paramDto) in dto.parameters) {
            dynObj.parameters[key]?.applyDto(paramDto)
        }
    }

    // Reset view parameters to baseline defaults before applying
    view3DMode.reset()
    viewZoom.reset()
    viewRotateX.reset()
    viewRotateY.reset()
    viewRotateZ.reset()
    viewPersp.reset()
    viewDepthDim.reset()
    viewSeparation.reset()
    viewBlendMode.reset()

    // Apply view parameters (if present in preset)
    dto.viewParameters["view3DMode"]?.let { view3DMode.applyDto(it) }
    dto.viewParameters["viewZoom"]?.let { viewZoom.applyDto(it) }
    dto.viewParameters["viewRotateX"]?.let { viewRotateX.applyDto(it) }
    dto.viewParameters["viewRotateY"]?.let { viewRotateY.applyDto(it) }
    dto.viewParameters["viewRotateZ"]?.let { viewRotateZ.applyDto(it) }
    dto.viewParameters["viewPersp"]?.let { viewPersp.applyDto(it) }
    dto.viewParameters["viewDepthDim"]?.let { viewDepthDim.applyDto(it) }
    dto.viewParameters["viewSeparation"]?.let { viewSeparation.applyDto(it) }
    dto.viewParameters["viewBlendMode"]?.let { viewBlendMode.applyDto(it) }
    
    // Reset feedback parameters to baseline defaults before applying
    fbDecay.reset()
    fbGain.reset()
    fbZoom.reset()
    fbRotate.reset()
    fbHueShift.reset()
    fbBlur.reset()
    fbChroma.reset()
    fbMode.reset()
    fbKaleido.reset()

    // Apply feedback parameters
    dto.feedbackParameters["fbDecay"]?.let { fbDecay.applyDto(it) }
    dto.feedbackParameters["fbGain"]?.let { fbGain.applyDto(it) }
    dto.feedbackParameters["fbZoom"]?.let { fbZoom.applyDto(it) }
    dto.feedbackParameters["fbRotate"]?.let { fbRotate.applyDto(it) }
    dto.feedbackParameters["fbHueShift"]?.let { fbHueShift.applyDto(it) }
    dto.feedbackParameters["fbBlur"]?.let { fbBlur.applyDto(it) }
    dto.feedbackParameters["fbChroma"]?.let { fbChroma.applyDto(it) }
    dto.feedbackParameters["fbMode"]?.let { fbMode.applyDto(it) }
    dto.feedbackParameters["fbKaleido"]?.let { fbKaleido.applyDto(it) }
    
    // Apply global parameters
    source.globalAlpha.reset()
    dto.globalAlpha?.let { source.globalAlpha.applyDto(it) }
}

