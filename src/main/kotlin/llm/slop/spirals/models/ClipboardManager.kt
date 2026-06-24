package llm.slop.spirals.models

import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.ModulationOperator
import llm.slop.spirals.rendering.Mixer

object ClipboardManager {
    var cellClipboard: CellClipboardData? = null
    var rowClipboard: RowClipboardData? = null

    private val GENERATORS = listOf("beatPhase", "lfo", "sampleAndHold")

    fun mapModulatorToDestination(dto: ModulatorDto, destCvId: String): ModulatorDto {
        val isSourceGenerator = dto.sourceId in GENERATORS
        val isDestGenerator = destCvId in GENERATORS
        
        var mapped = dto.copy(sourceId = destCvId)
        
        if (!isSourceGenerator && isDestGenerator) {
            // Generate default speed/waveform properties
            mapped = mapped.copy(
                waveform = "SINE",
                subdivision = 1.0f,
                subdivisionMin = 1.0f,
                subdivisionMax = 1.0f,
                lfoSpeedMode = "FAST",
                phaseOffset = 0.0f,
                phaseOffsetMin = 0.0f,
                phaseOffsetMax = 0.0f,
                slope = 0.5f,
                slopeMin = 0.5f,
                slopeMax = 0.5f
            )
        }
        
        return mapped
    }

    fun applyCellClipboard(param: ModulatableParameter, destCvId: String, data: CellClipboardData) {
        val mappedMods = data.modulators.map { mapModulatorToDestination(it, destCvId) }
        
        // Remove existing modulators for this CV ID and append the new ones
        param.modulators.removeIf { it.sourceId == destCvId }
        param.modulators.addAll(mappedMods.map { it.toDomain() })
    }

    fun findParameterByKey(mixer: Mixer, key: String): ModulatableParameter? {
        val parts = key.split("/")
        if (parts.isEmpty()) return null
        return when (parts[0]) {
            "Mixer" -> {
                if (parts.size > 1) {
                    when (parts[1]) {
                        "crossfade" -> mixer.crossfade
                        "masterAlpha" -> mixer.masterAlpha
                        "bloom" -> mixer.bloom
                        else -> null
                    }
                } else null
            }
            "Deck A", "Deck B" -> {
                val deck = if (parts[0] == "Deck A") mixer.deckA else mixer.deckB
                if (parts.size > 1) {
                    when (parts[1]) {
                        "Geometry" -> {
                            if (parts.size > 2) deck.source.parameters[parts[2]] else null
                        }
                        "Color" -> {
                            if (parts.size > 2) {
                                if (parts[2] == "Gain") deck.source.globalAlpha
                                else deck.source.parameters[parts[2]]
                            } else null
                        }
                        "FB" -> {
                            if (parts.size > 2) {
                                when (parts[2]) {
                                    "Decay" -> deck.fbDecay
                                    "Gain" -> deck.fbGain
                                    "Zoom" -> deck.fbZoom
                                    "Rotate" -> deck.fbRotate
                                    "HueShift" -> deck.fbHueShift
                                    "Blur" -> deck.fbBlur
                                    "Chroma" -> deck.fbChroma
                                    "Mode" -> deck.fbMode
                                    else -> null
                                }
                            } else null
                        }
                        else -> null
                    }
                } else null
            }
            else -> null
        }
    }

    fun normalizeValue(value: Float, srcMin: Float, srcMax: Float, destMin: Float, destMax: Float): Float {
        if (srcMin == srcMax) return value.coerceIn(destMin, destMax)
        val pct = (value - srcMin) / (srcMax - srcMin)
        return (destMin + pct * (destMax - destMin)).coerceIn(destMin, destMax)
    }

    fun applyRowClipboard(destParam: ModulatableParameter, data: RowClipboardData, mixer: Mixer) {
        val srcParam = findParameterByKey(mixer, data.sourceParamKey)
        val srcMin = srcParam?.minClamp ?: 0f
        val srcMax = srcParam?.maxClamp ?: 1f
        
        val destMin = destParam.minClamp
        val destMax = destParam.maxClamp
        
        val srcRange = srcMax - srcMin
        val destRange = destMax - destMin
        
        fun scaleVal(v: Float) = normalizeValue(v, srcMin, srcMax, destMin, destMax)
        
        val newBaseValue = scaleVal(data.parameter.baseValue)
        val newBaseMin = scaleVal(data.parameter.baseMin)
        val newBaseMax = scaleVal(data.parameter.baseMax)
        
        val ampScale = if (srcRange != 0f) destRange / srcRange else 1f
        
        val mappedMods = data.parameter.modulators.map { modDto ->
            val amplitudeVal = if (modDto.operator == "ADD") modDto.amplitude * ampScale else modDto.amplitude
            val amplitudeMinVal = if (modDto.operator == "ADD") modDto.amplitudeMin * ampScale else modDto.amplitudeMin
            val amplitudeMaxVal = if (modDto.operator == "ADD") modDto.amplitudeMax * ampScale else modDto.amplitudeMax
            
            val dcOffsetVal = if (modDto.operator == "ADD") modDto.dcOffset * ampScale else modDto.dcOffset
            val dcOffsetMinVal = if (modDto.operator == "ADD") modDto.dcOffsetMin * ampScale else modDto.dcOffsetMin
            val dcOffsetMaxVal = if (modDto.operator == "ADD") modDto.dcOffsetMax * ampScale else modDto.dcOffsetMax

            modDto.copy(
                amplitude = amplitudeVal.coerceIn(0f, 1f),
                amplitudeMin = amplitudeMinVal.coerceIn(0f, 1f),
                amplitudeMax = amplitudeMaxVal.coerceIn(0f, 1f),
                dcOffset = dcOffsetVal.coerceIn(-1f, 1f),
                dcOffsetMin = dcOffsetMinVal.coerceIn(-1f, 1f),
                dcOffsetMax = dcOffsetMaxVal.coerceIn(-1f, 1f)
            ).toDomain()
        }
        
        destParam.baseValue = newBaseValue
        destParam.baseMin = newBaseMin
        destParam.baseMax = newBaseMax
        destParam.randomizeBase = data.parameter.randomizeBase
        
        destParam.modulators.clear()
        destParam.modulators.addAll(mappedMods)
    }
}

data class CellClipboardData(
    val sourceParamKey: String,
    val sourceCvId: String,
    val modulators: List<ModulatorDto>
)

data class RowClipboardData(
    val sourceParamKey: String,
    val parameter: ParameterDto
)
