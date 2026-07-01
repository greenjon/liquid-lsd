package llm.slop.spirals.parameters

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CvModulator(
    val sourceId: String,
    val operator: ModulationOperator = ModulationOperator.ADD,
    val amplitude: Float = 0.0f,
    val bypassed: Boolean = false,
    // Beat synchronization/shape settings
    val waveform: Waveform = Waveform.SINE,
    val subdivision: Float = 1.0f,
    val phaseOffset: Float = 0.0f,
    val slope: Float = 0.5f,
    // LFO speed bounds setting
    val lfoSpeedMode: LfoSpeedMode = LfoSpeedMode.FAST,
    val genUnit: GenUnit = GenUnit.TIME,

    // Range fields
    val amplitudeMin: Float = amplitude,
    val amplitudeMax: Float = amplitude,
    val subdivisionMin: Float = subdivision,
    val subdivisionMax: Float = subdivision,
    val phaseOffsetMin: Float = phaseOffset,
    val phaseOffsetMax: Float = phaseOffset,
    val slopeMin: Float = slope,
    val slopeMax: Float = slope,

    val randomizeAmplitude: Boolean = false,
    val randomizeSubdivision: Boolean = false,
    val randomizePhaseOffset: Boolean = false,
    val randomizeSlope: Boolean = false,

    // Advanced LFO fields
    val morph: Float = 0.0f,
    val morphMin: Float = morph,
    val morphMax: Float = morph,
    val randomizeMorph: Boolean = false,

    val hold: Float = 0.0f,
    val holdMin: Float = hold,
    val holdMax: Float = hold,
    val randomizeHold: Boolean = false,

    // DC Offset fields
    val dcOffset: Float = 0.0f,
    val dcOffsetMin: Float = dcOffset,
    val dcOffsetMax: Float = dcOffset,
    val randomizeDcOffset: Boolean = false,

    // Modulator LFO (LFO 2) fields for Gen 1/Gen 2
    val modWaveform: Waveform = Waveform.SINE,
    val modSubdivision: Float = 1.0f,
    val modPhaseOffset: Float = 0.0f,
    val modSlope: Float = 0.5f,
    val modGenUnit: GenUnit = GenUnit.TIME,
    val generatorModMode: GeneratorModMode = GeneratorModMode.NONE,
    val generatorModDepth: Float = 0.0f,

    val modSubdivisionMin: Float = modSubdivision,
    val modSubdivisionMax: Float = modSubdivision,
    val modPhaseOffsetMin: Float = modPhaseOffset,
    val modPhaseOffsetMax: Float = modPhaseOffset,
    val modSlopeMin: Float = modSlope,
    val modSlopeMax: Float = modSlope,

    val randomizeModSubdivision: Boolean = false,
    val randomizeModPhaseOffset: Boolean = false,
    val randomizeModSlope: Boolean = false,

    // Modulator LFO (LFO 2) Advanced fields
    val modMorph: Float = 0.0f,
    val modMorphMin: Float = modMorph,
    val modMorphMax: Float = modMorph,
    val randomizeModMorph: Boolean = false,

    val modHold: Float = 0.0f,
    val modHoldMin: Float = modHold,
    val modHoldMax: Float = modHold,
    val randomizeModHold: Boolean = false,

    val generatorModDepthMin: Float = generatorModDepth,
    val generatorModDepthMax: Float = generatorModDepth,
    val randomizeGeneratorModDepth: Boolean = false,

    val id: String = UUID.randomUUID().toString()
) {
    private fun isDiscreteSubdivision(): Boolean {
        return sourceId == "beatPhase" || sourceId == "sampleAndHold" || 
               ((sourceId == "gen1" || sourceId == "gen2") && genUnit == GenUnit.BEAT)
    }

    fun randomizeActiveValues(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        val newAmplitude = if (randomizeAmplitude) {
            if (amplitudeMin == amplitudeMax) amplitudeMin else random.nextFloat() * (amplitudeMax - amplitudeMin) + amplitudeMin
        } else amplitude

        val newDcOffset = if (randomizeDcOffset) {
            if (dcOffsetMin == dcOffsetMax) dcOffsetMin else random.nextFloat() * (dcOffsetMax - dcOffsetMin) + dcOffsetMin
        } else dcOffset

        val newPhase = if (randomizePhaseOffset) {
            if (phaseOffsetMin == phaseOffsetMax) phaseOffsetMin else random.nextFloat() * (phaseOffsetMax - phaseOffsetMin) + phaseOffsetMin
        } else phaseOffset

        val newSlope = if (randomizeSlope) {
            if (slopeMin == slopeMax) slopeMin else random.nextFloat() * (slopeMax - slopeMin) + slopeMin
        } else slope

        val newMorph = if (randomizeMorph) {
            if (morphMin == morphMax) morphMin else random.nextFloat() * (morphMax - morphMin) + morphMin
        } else morph

        val newHold = if (randomizeHold) {
            if (holdMin == holdMax) holdMin else random.nextFloat() * (holdMax - holdMin) + holdMin
        } else hold

        val newSubdiv = if (randomizeSubdivision) {
            if (subdivisionMin == subdivisionMax) {
                subdivisionMin
            } else {
                if (isDiscreteSubdivision()) {
                    val options = floatArrayOf(0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f)
                    val valid = options.filter { it in subdivisionMin..subdivisionMax }
                    if (valid.isNotEmpty()) valid.random(random) else subdivisionMin
                } else {
                    // LFO Speed is continuous. 0.1s to 10s.
                    random.nextFloat() * (subdivisionMax - subdivisionMin) + subdivisionMin
                }
            }
        } else subdivision

        val newModPhase = if (randomizeModPhaseOffset) {
            if (modPhaseOffsetMin == modPhaseOffsetMax) modPhaseOffsetMin else random.nextFloat() * (modPhaseOffsetMax - modPhaseOffsetMin) + modPhaseOffsetMin
        } else modPhaseOffset

        val newModSlope = if (randomizeModSlope) {
            if (modSlopeMin == modSlopeMax) modSlopeMin else random.nextFloat() * (modSlopeMax - modSlopeMin) + modSlopeMin
        } else modSlope

        val newModMorph = if (randomizeModMorph) {
            if (modMorphMin == modMorphMax) modMorphMin else random.nextFloat() * (modMorphMax - modMorphMin) + modMorphMin
        } else modMorph

        val newModHold = if (randomizeModHold) {
            if (modHoldMin == modHoldMax) modHoldMin else random.nextFloat() * (modHoldMax - modHoldMin) + modHoldMin
        } else modHold

        val newModSubdiv = if (randomizeModSubdivision) {
            if (modSubdivisionMin == modSubdivisionMax) {
                modSubdivisionMin
            } else {
                if (modGenUnit == GenUnit.BEAT) {
                    val options = floatArrayOf(0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f)
                    val valid = options.filter { it in modSubdivisionMin..modSubdivisionMax }
                    if (valid.isNotEmpty()) valid.random(random) else modSubdivisionMin
                } else {
                    random.nextFloat() * (modSubdivisionMax - modSubdivisionMin) + modSubdivisionMin
                }
            }
        } else modSubdivision

        val newModDepth = if (randomizeGeneratorModDepth) {
            if (generatorModDepthMin == generatorModDepthMax) generatorModDepthMin else random.nextFloat() * (generatorModDepthMax - generatorModDepthMin) + generatorModDepthMin
        } else generatorModDepth

        return this.copy(
            amplitude = newAmplitude,
            dcOffset = newDcOffset,
            subdivision = newSubdiv,
            phaseOffset = newPhase,
            slope = newSlope,
            morph = newMorph,
            hold = newHold,
            modPhaseOffset = newModPhase,
            modSlope = newModSlope,
            modMorph = newModMorph,
            modHold = newModHold,
            modSubdivision = newModSubdiv,
            generatorModDepth = newModDepth
        )
    }

    fun randomizeAmplitude(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeAmplitude) return this
        val newAmplitude = if (amplitudeMin == amplitudeMax) amplitudeMin else random.nextFloat() * (amplitudeMax - amplitudeMin) + amplitudeMin
        return this.copy(amplitude = newAmplitude)
    }

    fun randomizeDcOffset(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeDcOffset) return this
        val newDcOffset = if (dcOffsetMin == dcOffsetMax) dcOffsetMin else random.nextFloat() * (dcOffsetMax - dcOffsetMin) + dcOffsetMin
        return this.copy(dcOffset = newDcOffset)
    }

    fun randomizeSubdivision(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeSubdivision) return this
        val newSubdiv = if (subdivisionMin == subdivisionMax) {
            subdivisionMin
        } else {
            if (isDiscreteSubdivision()) {
                val options = floatArrayOf(0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f)
                val valid = options.filter { it in subdivisionMin..subdivisionMax }
                if (valid.isNotEmpty()) valid.random(random) else subdivisionMin
            } else {
                random.nextFloat() * (subdivisionMax - subdivisionMin) + subdivisionMin
            }
        }
        return this.copy(subdivision = newSubdiv)
    }

    fun randomizePhaseOffset(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizePhaseOffset) return this
        val newPhase = if (phaseOffsetMin == phaseOffsetMax) phaseOffsetMin else random.nextFloat() * (phaseOffsetMax - phaseOffsetMin) + phaseOffsetMin
        return this.copy(phaseOffset = newPhase)
    }

    fun randomizeSlope(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeSlope) return this
        val newSlope = if (slopeMin == slopeMax) slopeMin else random.nextFloat() * (slopeMax - slopeMin) + slopeMin
        return this.copy(slope = newSlope)
    }

    fun randomizeMorph(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeMorph) return this
        val newMorph = if (morphMin == morphMax) morphMin else random.nextFloat() * (morphMax - morphMin) + morphMin
        return this.copy(morph = newMorph)
    }

    fun randomizeHold(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeHold) return this
        val newHold = if (holdMin == holdMax) holdMin else random.nextFloat() * (holdMax - holdMin) + holdMin
        return this.copy(hold = newHold)
    }

    fun randomizeModSubdivision(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeModSubdivision) return this
        val newSubdiv = if (modSubdivisionMin == modSubdivisionMax) {
            modSubdivisionMin
        } else {
            if (modGenUnit == GenUnit.BEAT) {
                val options = floatArrayOf(0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f)
                val valid = options.filter { it in modSubdivisionMin..modSubdivisionMax }
                if (valid.isNotEmpty()) valid.random(random) else modSubdivisionMin
            } else {
                random.nextFloat() * (modSubdivisionMax - modSubdivisionMin) + modSubdivisionMin
            }
        }
        return this.copy(modSubdivision = newSubdiv)
    }

    fun randomizeModPhaseOffset(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeModPhaseOffset) return this
        val newPhase = if (modPhaseOffsetMin == modPhaseOffsetMax) modPhaseOffsetMin else random.nextFloat() * (modPhaseOffsetMax - modPhaseOffsetMin) + modPhaseOffsetMin
        return this.copy(modPhaseOffset = newPhase)
    }

    fun randomizeModSlope(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeModSlope) return this
        val newSlope = if (modSlopeMin == modSlopeMax) modSlopeMin else random.nextFloat() * (modSlopeMax - modSlopeMin) + modSlopeMin
        return this.copy(modSlope = newSlope)
    }

    fun randomizeModMorph(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeModMorph) return this
        val newMorph = if (modMorphMin == modMorphMax) modMorphMin else random.nextFloat() * (modMorphMax - modMorphMin) + modMorphMin
        return this.copy(modMorph = newMorph)
    }

    fun randomizeModHold(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeModHold) return this
        val newHold = if (modHoldMin == modHoldMax) modHoldMin else random.nextFloat() * (modHoldMax - modHoldMin) + modHoldMin
        return this.copy(modHold = newHold)
    }

    fun randomizeGeneratorModDepth(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        if (!randomizeGeneratorModDepth) return this
        val newDepth = if (generatorModDepthMin == generatorModDepthMax) generatorModDepthMin else random.nextFloat() * (generatorModDepthMax - generatorModDepthMin) + generatorModDepthMin
        return this.copy(generatorModDepth = newDepth)
    }
}
