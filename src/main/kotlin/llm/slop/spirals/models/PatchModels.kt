package llm.slop.spirals.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import llm.slop.spirals.parameters.*
import llm.slop.spirals.rendering.*

@Serializable
data class ModulatorDto(
    val sourceId: String,
    val operator: String, // "ADD" or "MUL"
    @SerialName("weight") val amplitude: Float,
    val bypassed: Boolean = false,
    val waveform: String = "SINE",
    val subdivision: Float = 1.0f,
    val phaseOffset: Float = 0.0f,
    val slope: Float = 0.5f,
    val lfoSpeedMode: String = "FAST",
    
    // Randomization bounds
    @SerialName("weightMin") val amplitudeMin: Float,
    @SerialName("weightMax") val amplitudeMax: Float,
    val subdivisionMin: Float,
    val subdivisionMax: Float,
    val phaseOffsetMin: Float,
    val phaseOffsetMax: Float,
    val slopeMin: Float,
    val slopeMax: Float,
    @SerialName("randomizeWeight") val randomizeAmplitude: Boolean = false,
    val randomizeSubdivision: Boolean = false,
    val randomizePhaseOffset: Boolean = false,
    val randomizeSlope: Boolean = false,

    // DC Offset fields
    val dcOffset: Float = 0.0f,
    val dcOffsetMin: Float = 0.0f,
    val dcOffsetMax: Float = 0.0f,
    val randomizeDcOffset: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModulatorDto) return false
        
        if (sourceId != other.sourceId) return false
        if (operator != other.operator) return false
        if (bypassed != other.bypassed) return false
        if (waveform != other.waveform) return false
        if (lfoSpeedMode != other.lfoSpeedMode) return false
        
        if (amplitudeMin != other.amplitudeMin) return false
        if (amplitudeMax != other.amplitudeMax) return false
        if (subdivisionMin != other.subdivisionMin) return false
        if (subdivisionMax != other.subdivisionMax) return false
        if (phaseOffsetMin != other.phaseOffsetMin) return false
        if (phaseOffsetMax != other.phaseOffsetMax) return false
        if (slopeMin != other.slopeMin) return false
        if (slopeMax != other.slopeMax) return false
        if (dcOffsetMin != other.dcOffsetMin) return false
        if (dcOffsetMax != other.dcOffsetMax) return false
        
        if (randomizeAmplitude != other.randomizeAmplitude) return false
        if (randomizeSubdivision != other.randomizeSubdivision) return false
        if (randomizePhaseOffset != other.randomizePhaseOffset) return false
        if (randomizeSlope != other.randomizeSlope) return false
        if (randomizeDcOffset != other.randomizeDcOffset) return false
        
        // Exclude instantaneous values from equality check if they are subject to randomization
        if (!randomizeAmplitude && amplitude != other.amplitude) return false
        if (!randomizeSubdivision && subdivision != other.subdivision) return false
        if (!randomizePhaseOffset && phaseOffset != other.phaseOffset) return false
        if (!randomizeSlope && slope != other.slope) return false
        if (!randomizeDcOffset && dcOffset != other.dcOffset) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourceId.hashCode()
        result = 31 * result + operator.hashCode()
        result = 31 * result + bypassed.hashCode()
        result = 31 * result + waveform.hashCode()
        result = 31 * result + lfoSpeedMode.hashCode()
        
        result = 31 * result + amplitudeMin.hashCode()
        result = 31 * result + amplitudeMax.hashCode()
        result = 31 * result + subdivisionMin.hashCode()
        result = 31 * result + subdivisionMax.hashCode()
        result = 31 * result + phaseOffsetMin.hashCode()
        result = 31 * result + phaseOffsetMax.hashCode()
        result = 31 * result + slopeMin.hashCode()
        result = 31 * result + slopeMax.hashCode()
        result = 31 * result + dcOffsetMin.hashCode()
        result = 31 * result + dcOffsetMax.hashCode()

        result = 31 * result + randomizeAmplitude.hashCode()
        result = 31 * result + randomizeSubdivision.hashCode()
        result = 31 * result + randomizePhaseOffset.hashCode()
        result = 31 * result + randomizeSlope.hashCode()
        result = 31 * result + randomizeDcOffset.hashCode()

        if (!randomizeAmplitude) result = 31 * result + amplitude.hashCode()
        if (!randomizeSubdivision) result = 31 * result + subdivision.hashCode()
        if (!randomizePhaseOffset) result = 31 * result + phaseOffset.hashCode()
        if (!randomizeSlope) result = 31 * result + slope.hashCode()
        if (!randomizeDcOffset) result = 31 * result + dcOffset.hashCode()
        
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

        // Exclude instantaneous baseValue from equality check if it is subject to randomization
        if (!randomizeBase && baseValue != other.baseValue) return false

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

        if (!randomizeBase) result = 31 * result + baseValue.hashCode()

        return result
    }
}

@Serializable
data class DeckPatchDto(
    val version: Int = 1,
    val name: String,
    val visualSourceType: String, // e.g., "Mandala"
    val recipe: MandalaRecipeDto, // For restoring recipe structure
    val parameters: Map<String, ParameterDto>, // Visual source params
    val feedbackParameters: Map<String, ParameterDto>, // Feedback chain params
    val globalAlpha: ParameterDto,
    val globalScale: ParameterDto? = null
)

@Serializable
data class MandalaRecipeDto(
    val a: Int,
    val b: Int,
    val c: Int,
    val d: Int
)

@Serializable
data class GlobalPatchDto(
    val version: Int = 1,
    val name: String,
    val crossfade: ParameterDto,
    val masterAlpha: ParameterDto,
    val blendMode: Float,
    val deckA: DeckPatchDto,
    val deckB: DeckPatchDto,
    val bloom: ParameterDto? = null
)

// --- Extension Converters ---

fun CvModulator.toDto(): ModulatorDto = ModulatorDto(
    sourceId = sourceId,
    operator = operator.name,
    amplitude = amplitude,
    bypassed = bypassed,
    waveform = waveform.name,
    subdivision = subdivision,
    phaseOffset = phaseOffset,
    slope = slope,
    lfoSpeedMode = lfoSpeedMode.name,
    amplitudeMin = amplitudeMin,
    amplitudeMax = amplitudeMax,
    subdivisionMin = subdivisionMin,
    subdivisionMax = subdivisionMax,
    phaseOffsetMin = phaseOffsetMin,
    phaseOffsetMax = phaseOffsetMax,
    slopeMin = slopeMin,
    slopeMax = slopeMax,
    randomizeAmplitude = randomizeAmplitude,
    randomizeSubdivision = randomizeSubdivision,
    randomizePhaseOffset = randomizePhaseOffset,
    randomizeSlope = randomizeSlope,
    dcOffset = dcOffset,
    dcOffsetMin = dcOffsetMin,
    dcOffsetMax = dcOffsetMax,
    randomizeDcOffset = randomizeDcOffset
)

fun ModulatorDto.toDomain(): CvModulator = CvModulator(
    sourceId = sourceId,
    operator = ModulationOperator.valueOf(operator),
    amplitude = amplitude,
    bypassed = bypassed,
    waveform = Waveform.valueOf(waveform),
    subdivision = subdivision,
    phaseOffset = phaseOffset,
    slope = slope,
    lfoSpeedMode = LfoSpeedMode.valueOf(lfoSpeedMode),
    amplitudeMin = amplitudeMin,
    amplitudeMax = amplitudeMax,
    subdivisionMin = subdivisionMin,
    subdivisionMax = subdivisionMax,
    phaseOffsetMin = phaseOffsetMin,
    phaseOffsetMax = phaseOffsetMax,
    slopeMin = slopeMin,
    slopeMax = slopeMax,
    randomizeAmplitude = randomizeAmplitude,
    randomizeSubdivision = randomizeSubdivision,
    randomizePhaseOffset = randomizePhaseOffset,
    randomizeSlope = randomizeSlope,
    dcOffset = dcOffset,
    dcOffsetMin = dcOffsetMin,
    dcOffsetMax = dcOffsetMax,
    randomizeDcOffset = randomizeDcOffset
)

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
}

fun MandalaRatio.toDto(): MandalaRecipeDto = MandalaRecipeDto(a, b, c, d)

fun Deck.toDto(name: String): DeckPatchDto {
    val mandala = source as Mandala
    val recipeDto = mandala.recipe.toDto()
    
    val paramsMap = mandala.parameters.mapValues { it.value.toDto() }
    
    val feedbackParamsMap = mapOf(
        "fbDecay" to fbDecay.toDto(),
        "fbGain" to fbGain.toDto(),
        "fbZoom" to fbZoom.toDto(),
        "fbRotate" to fbRotate.toDto(),
        "fbHueShift" to fbHueShift.toDto(),
        "fbBlur" to fbBlur.toDto(),
        "fbChroma" to fbChroma.toDto(),
        "fbMode" to fbMode.toDto()
    )
    
    return DeckPatchDto(
        name = name,
        visualSourceType = "Mandala",
        recipe = recipeDto,
        parameters = paramsMap,
        feedbackParameters = feedbackParamsMap,
        globalAlpha = source.globalAlpha.toDto(),
        globalScale = ParameterDto(1.0f, 0.0f, 1.0f, false, emptyList())
    )
}

fun Deck.applyDto(dto: DeckPatchDto) {
    val mandala = source as Mandala
    
    // Recreate or lookup recipe
    val recipe = MandalaLibrary.MandalaRatios.firstOrNull {
        it.a == dto.recipe.a && it.b == dto.recipe.b &&
        it.c == dto.recipe.c && it.d == dto.recipe.d
    } ?: MandalaRatio(
        id = "custom_${dto.recipe.a}_${dto.recipe.b}_${dto.recipe.c}_${dto.recipe.d}",
        a = dto.recipe.a,
        b = dto.recipe.b,
        c = dto.recipe.c,
        d = dto.recipe.d
    )
    mandala.recipe = recipe
    
    // Apply visual source parameters
    for ((key, paramDto) in dto.parameters) {
        mandala.parameters[key]?.applyDto(paramDto)
    }

    // Legacy patch fallback: sync parameter values to recipe if they weren't in the saved patch
    if (!dto.parameters.containsKey("Lobes")) {
        mandala.parameters["Lobes"]?.set(recipe.petals.toFloat())
    }
    if (!dto.parameters.containsKey("Recipe Select")) {
        val list = MandalaLibrary.recipesByPetals[recipe.petals] ?: emptyList()
        val idx = list.indexOfFirst { it.a == recipe.a && it.b == recipe.b && it.c == recipe.c && it.d == recipe.d }.coerceAtLeast(0)
        val pct = if (list.size > 1) idx.toFloat() / (list.size - 1).toFloat() else 0.0f
        mandala.parameters["Recipe Select"]?.set(pct)
    }
    
    // Apply feedback parameters
    dto.feedbackParameters["fbDecay"]?.let { fbDecay.applyDto(it) }
    dto.feedbackParameters["fbGain"]?.let { fbGain.applyDto(it) }
    dto.feedbackParameters["fbZoom"]?.let { fbZoom.applyDto(it) }
    dto.feedbackParameters["fbRotate"]?.let { fbRotate.applyDto(it) }
    dto.feedbackParameters["fbHueShift"]?.let { fbHueShift.applyDto(it) }
    dto.feedbackParameters["fbBlur"]?.let { fbBlur.applyDto(it) }
    dto.feedbackParameters["fbChroma"]?.let { fbChroma.applyDto(it) }
    dto.feedbackParameters["fbMode"]?.let { fbMode.applyDto(it) }
    
    // Apply global parameters
    source.globalAlpha.applyDto(dto.globalAlpha)
}

fun Mixer.toDto(name: String): GlobalPatchDto = GlobalPatchDto(
    name = name,
    crossfade = crossfade.toDto(),
    masterAlpha = masterAlpha.toDto(),
    blendMode = mode.baseValue,
    deckA = deckA.toDto("Deck A"),
    deckB = deckB.toDto("Deck B"),
    bloom = bloom.toDto()
)

fun Mixer.applyDto(dto: GlobalPatchDto) {
    crossfade.applyDto(dto.crossfade)
    masterAlpha.applyDto(dto.masterAlpha)
    mode.set(dto.blendMode)
    deckA.applyDto(dto.deckA)
    deckB.applyDto(dto.deckB)
    dto.bloom?.let { bloom.applyDto(it) }
}
