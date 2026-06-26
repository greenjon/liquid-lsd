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

    // DC Offset fields
    val dcOffset: Float = 0.0f,
    val dcOffsetMin: Float = dcOffset,
    val dcOffsetMax: Float = dcOffset,
    val randomizeDcOffset: Boolean = false,

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

        return this.copy(
            amplitude = newAmplitude,
            dcOffset = newDcOffset,
            subdivision = newSubdiv,
            phaseOffset = newPhase,
            slope = newSlope
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
}
