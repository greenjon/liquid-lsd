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
)

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
)

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
