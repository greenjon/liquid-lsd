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
    val genUnit: String = "TIME",
    
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
        val isAmpRandom = randomizeAmplitude || amplitudeMin != other.amplitudeMin || amplitudeMax != other.amplitudeMax || amplitudeMin != amplitudeMax
        if (!isAmpRandom && amplitude != other.amplitude) return false
        
        val isSubRandom = randomizeSubdivision || subdivisionMin != other.subdivisionMin || subdivisionMax != other.subdivisionMax || subdivisionMin != subdivisionMax
        if (!isSubRandom && subdivision != other.subdivision) return false
        
        val isPhaseRandom = randomizePhaseOffset || phaseOffsetMin != other.phaseOffsetMin || phaseOffsetMax != other.phaseOffsetMax || phaseOffsetMin != phaseOffsetMax
        if (!isPhaseRandom && phaseOffset != other.phaseOffset) return false
        
        val isSlopeRandom = randomizeSlope || slopeMin != other.slopeMin || slopeMax != other.slopeMax || slopeMin != slopeMax
        if (!isSlopeRandom && slope != other.slope) return false
        
        val isDcRandom = randomizeDcOffset || dcOffsetMin != other.dcOffsetMin || dcOffsetMax != other.dcOffsetMax || dcOffsetMin != dcOffsetMax
        if (!isDcRandom && dcOffset != other.dcOffset) return false

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

        val isAmpRandom = randomizeAmplitude || amplitudeMin != amplitudeMax
        if (!isAmpRandom) result = 31 * result + amplitude.hashCode()
        
        val isSubRandom = randomizeSubdivision || subdivisionMin != subdivisionMax
        if (!isSubRandom) result = 31 * result + subdivision.hashCode()
        
        val isPhaseRandom = randomizePhaseOffset || phaseOffsetMin != phaseOffsetMax
        if (!isPhaseRandom) result = 31 * result + phaseOffset.hashCode()
        
        val isSlopeRandom = randomizeSlope || slopeMin != slopeMax
        if (!isSlopeRandom) result = 31 * result + slope.hashCode()
        
        val isDcRandom = randomizeDcOffset || dcOffsetMin != dcOffsetMax
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

        // Exclude instantaneous baseValue from equality check if it is subject to randomization
        val isRandomized = randomizeBase || baseMin != other.baseMin || baseMax != other.baseMax || baseMin != baseMax
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

        val isRandomized = randomizeBase || baseMin != baseMax
        if (!isRandomized) result = 31 * result + baseValue.hashCode()

        return result
    }
}

@Serializable
data class DeckPatchDto(
    val version: Int = 1,
    val name: String,
    val tags: List<String> = emptyList(), // Phase 2 — tag list; defaults to empty for backward compat
    val visualSourceType: String, // e.g., "Mandala" or "Mandelbulb"
    val recipe: MandalaRecipeDto? = null, // For restoring recipe structure (Mandala-only)
    val parameters: Map<String, ParameterDto>, // Visual source params
    val feedbackParameters: Map<String, ParameterDto>, // Feedback chain params
    val globalAlpha: ParameterDto,
    val globalScale: ParameterDto? = null,
    val isEmpty: Boolean = false
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
data class GlobalPatchDto(
    val version: Int = 2,
    val name: String,
    val crossfade: ParameterDto,
    val masterAlpha: ParameterDto,
    val blendMode: Float,
    val deckA: DeckPatchDto,
    val deckB: DeckPatchDto,
    val bloom: ParameterDto? = null,
    val queueNext: ParameterDto? = null,
    val queuePrev: ParameterDto? = null
)

@Serializable
data class SessionStateDto(
    val version: Int = 4,
    val deckA: DeckPatchDto,
    val deckB: DeckPatchDto,
    val deckC: DeckPatchDto,
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
    val isRepeatEnabled: Boolean = false,
    val isShuffleEnabled: Boolean = false
)

@Serializable
data class PlaylistDto(
    val version: Int = 1,
    val name: String,
    val items: List<String> // List of .lsd file names (relative to presets/patches)
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
    genUnit = genUnit.name,
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
    id = id
)

fun ModulatorDto.toDomain(): CvModulator = CvModulator(
    // Migrate legacy source IDs from older patch versions
    sourceId = when (sourceId) { "gen1", "gen2" -> "lfo"; else -> sourceId },
    operator = ModulationOperator.valueOf(operator),
    amplitude = amplitude,
    bypassed = bypassed,
    waveform = Waveform.valueOf(waveform),
    subdivision = subdivision,
    phaseOffset = phaseOffset,
    slope = slope,
    lfoSpeedMode = LfoSpeedMode.valueOf(lfoSpeedMode),
    genUnit = GenUnit.valueOf(genUnit),
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

fun Deck.toDto(name: String, tags: List<String> = emptyList()): DeckPatchDto {
    val sourceName = if (source is llm.slop.spirals.rendering.DynamicVisualSource) (source as llm.slop.spirals.rendering.DynamicVisualSource).id else "Mandala"
    val recipeDto = if (source is Mandala) (source as Mandala).recipe.toDto() else null
    
    val paramsMap = source.parameters.mapValues { it.value.toDto() }
    
    val feedbackParamsMap = mapOf(
        "sourceSelect" to sourceSelect.toDto(),
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
        tags = tags,
        visualSourceType = sourceName,
        recipe = recipeDto,
        parameters = paramsMap,
        feedbackParameters = feedbackParamsMap,
        globalAlpha = source.globalAlpha.toDto(),
        globalScale = ParameterDto(1.0f, 0.0f, 1.0f, false, emptyList()),
        isEmpty = isEmpty
    )
}

fun Deck.applyDto(dto: DeckPatchDto) {
    this.isEmpty = dto.isEmpty
    dto.feedbackParameters["sourceSelect"]?.let { sourceSelect.applyDto(it) }
    this.lastSourceSelectBase = sourceSelect.baseValue
    
    // Select the active source dynamically based on sourceSelect parameter
    val size = availableSources.size
    val index = if (size > 0) (sourceSelect.value * size).toInt().coerceIn(0, size - 1) else 0
    source = if (size > 0) availableSources[index] else availableSources[0]
    
    if (source is Mandala) {
        val mandalaObj = source as Mandala
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
        
        // Apply visual source parameters
        for ((key, paramDto) in dto.parameters) {
            val mappedKey = when (key) {
                "Scale" -> "Zoom"
                "Rotation" -> "Rotate Z"
                "3D Yaw" -> "Rotate Y"
                "3D Pitch" -> "Rotate X"
                else -> key
            }
            mandalaObj.parameters[mappedKey]?.applyDto(paramDto)
        }

        // Legacy patch fallback: sync parameter values to recipe if they weren't in the saved patch
        if (!dto.parameters.containsKey("Lobes")) {
            mandalaObj.parameters["Lobes"]?.set(recipe.petals.toFloat())
        }
        if (!dto.parameters.containsKey("Recipe Select")) {
            val list = MandalaLibrary.recipesByPetals[recipe.petals] ?: emptyList()
            val idx = list.indexOfFirst { it.a == recipe.a && it.b == recipe.b && it.c == recipe.c && it.d == recipe.d }.coerceAtLeast(0)
            val pct = if (list.size > 1) idx.toFloat() / (list.size - 1).toFloat() else 0.0f
            mandalaObj.parameters["Recipe Select"]?.set(pct)
        }
    } else if (source is llm.slop.spirals.rendering.DynamicVisualSource) {
        val dynObj = source as llm.slop.spirals.rendering.DynamicVisualSource
        for ((key, paramDto) in dto.parameters) {
            dynObj.parameters[key]?.applyDto(paramDto)
        }
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
    bloom = bloom.toDto(),
    queueNext = queueNext.toDto(),
    queuePrev = queuePrev.toDto()
)

fun mapMonopolarToBipolar(dto: ParameterDto): ParameterDto {
    val mapVal: (Float) -> Float = { it * 2f - 1f }
    return dto.copy(
        baseValue = mapVal(dto.baseValue),
        baseMin = mapVal(dto.baseMin),
        baseMax = mapVal(dto.baseMax)
    )
}

fun Mixer.applyDto(dto: GlobalPatchDto) {
    val crossfadeDto = if (dto.version <= 1) mapMonopolarToBipolar(dto.crossfade) else dto.crossfade
    crossfade.applyDto(crossfadeDto)
    masterAlpha.applyDto(dto.masterAlpha)
    mode.set(dto.blendMode)
    deckA.applyDto(dto.deckA)
    deckB.applyDto(dto.deckB)
    dto.bloom?.let { bloom.applyDto(it) }
    dto.queueNext?.let { queueNext.applyDto(it) }
    dto.queuePrev?.let { queuePrev.applyDto(it) }
}
