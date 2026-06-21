package llm.slop.spirals.parameters

import kotlinx.serialization.Serializable
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.cv.CvHistoryBuffer
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
enum class ModulationOperator {
    ADD, MUL
}

@Serializable
enum class Waveform {
    SINE, TRIANGLE, SQUARE
}

@Serializable
enum class LfoSpeedMode {
    SLOW, MEDIUM, FAST
}

@Serializable
data class CvModulator(
    val sourceId: String,
    val operator: ModulationOperator = ModulationOperator.ADD,
    val weight: Float = 0.0f,
    val bypassed: Boolean = false,
    // Beat synchronization/shape settings
    val waveform: Waveform = Waveform.SINE,
    val subdivision: Float = 1.0f,
    val phaseOffset: Float = 0.0f,
    val slope: Float = 0.5f,
    // LFO speed bounds setting
    val lfoSpeedMode: LfoSpeedMode = LfoSpeedMode.FAST,

    // Range fields
    val weightMin: Float = weight,
    val weightMax: Float = weight,
    val subdivisionMin: Float = subdivision,
    val subdivisionMax: Float = subdivision,
    val phaseOffsetMin: Float = phaseOffset,
    val phaseOffsetMax: Float = phaseOffset,
    val slopeMin: Float = slope,
    val slopeMax: Float = slope
) {
    fun randomizeActiveValues(random: kotlin.random.Random = kotlin.random.Random.Default): CvModulator {
        val newWeight = if (weightMin == weightMax) weightMin else random.nextFloat() * (weightMax - weightMin) + weightMin
        val newPhase = if (phaseOffsetMin == phaseOffsetMax) phaseOffsetMin else random.nextFloat() * (phaseOffsetMax - phaseOffsetMin) + phaseOffsetMin
        val newSlope = if (slopeMin == slopeMax) slopeMin else random.nextFloat() * (slopeMax - slopeMin) + slopeMin

        val newSubdiv = if (subdivisionMin == subdivisionMax) {
            subdivisionMin
        } else {
            if (sourceId == "beatPhase" || sourceId == "sampleAndHold") {
                val options = floatArrayOf(0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f)
                val valid = options.filter { it in subdivisionMin..subdivisionMax }
                if (valid.isNotEmpty()) valid.random(random) else subdivisionMin
            } else {
                // LFO Speed is continuous. 0.1s to 10s.
                random.nextFloat() * (subdivisionMax - subdivisionMin) + subdivisionMin
            }
        }

        return this.copy(
            weight = newWeight,
            subdivision = newSubdiv,
            phaseOffset = newPhase,
            slope = newSlope
        )
    }

    fun evaluateValue(): Float {
        return when (sourceId) {
            "beatPhase" -> {
                val beats = CVRegistry.getSynchronizedTotalBeats()
                val localPhase = ((beats / subdivision) + phaseOffset) % 1.0
                val positivePhase = if (localPhase < 0.0) localPhase + 1.0 else localPhase
                calculateWaveform(waveform, positivePhase, slope)
            }
            "sampleAndHold" -> {
                val beats = CVRegistry.getSynchronizedTotalBeats()
                val subdivisionD = subdivision.toDouble().coerceAtLeast(0.01)
                
                val cyclePosition = (beats / subdivisionD) + phaseOffset
                val phase = cyclePosition % 1.0
                val positivePhase = if (phase < 0.0) phase + 1.0 else phase

                val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                val previousCycle = currentCycle - 1

                val seed = subdivisionD.hashCode() xor phaseOffset.hashCode()

                val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat()
                val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat()

                val glideAmount = if (positivePhase < slope) {
                    (positivePhase / slope).toFloat().coerceIn(0f, 1f)
                } else {
                    1.0f
                }

                previousValue + (currentValue - previousValue) * glideAmount
            }
            "lfo" -> {
                val seconds = CVRegistry.getElapsedRealtimeSec()
                val period = when (lfoSpeedMode) {
                    LfoSpeedMode.FAST -> subdivision * 10.0
                    LfoSpeedMode.MEDIUM -> subdivision * 900.0
                    LfoSpeedMode.SLOW -> subdivision * 86400.0
                }.coerceAtLeast(0.001)

                val localPhase = ((seconds / period) + phaseOffset) % 1.0
                val positivePhase = if (localPhase < 0.0) localPhase + 1.0 else localPhase
                calculateWaveform(waveform, positivePhase, slope)
            }
            else -> {
                CVRegistry.get(sourceId)
            }
        }
    }
}


/**
 * A parameter that can be modulated by a base value and multiple CV sources.
 * Keeps a sliding history of its evaluated values.
 */
class ModulatableParameter(
    var baseValue: Float = 0.0f,
    val historySize: Int = 200
) {
    val modulators = CopyOnWriteArrayList<CvModulator>()
    val history = CvHistoryBuffer(historySize)

    var baseMin: Float = baseValue
    var baseMax: Float = baseValue

    var value: Float = baseValue
        private set

    /**
     * Randomizes the static baseValue within the [baseMin, baseMax] range.
     */
    fun randomizeBaseValue(random: kotlin.random.Random = kotlin.random.Random.Default) {
        baseValue = if (baseMin == baseMax) baseMin else random.nextFloat() * (baseMax - baseMin) + baseMin
    }

    /**
     * Calculates the final value by combining the base value with all active modulators.
     * Called once per frame prior to rendering.
     */
    fun evaluate(): Float {
        val activeMods = modulators.filter { !it.bypassed }
        if (activeMods.isEmpty()) {
            value = baseValue.coerceIn(0f, 1f)
            history.add(value)
            return value
        }

        var result = baseValue

        for (mod in activeMods) {
            val finalCv = mod.evaluateValue()
            val modAmount = finalCv * mod.weight

            result = when (mod.operator) {
                ModulationOperator.ADD -> result + modAmount
                ModulationOperator.MUL -> result * (1.0f + modAmount)
            }
        }

        // Clamp the final parameter output to standard unit range [0.0, 1.0]
        value = result.coerceIn(0f, 1f)
        history.add(value)
        return value
    }

    /**
     * Directly updates the base value (e.g. from UI sliders).
     */
    fun set(newValue: Float) {
        baseValue = newValue
        baseMin = newValue
        baseMax = newValue
    }
}

fun calculateWaveform(waveform: Waveform, phase: Double, slope: Float): Float {
    return when (waveform) {
        Waveform.SINE -> (kotlin.math.sin(phase * 2.0 * Math.PI).toFloat() * 0.5f) + 0.5f
        Waveform.TRIANGLE -> {
            val s = slope.toDouble()
            if (s <= 0.001) (1.0 - phase).toFloat()
            else if (s >= 0.999) phase.toFloat()
            else if (phase < s) (phase / s).toFloat()
            else ((1.0 - phase) / (1.0 - s)).toFloat()
        }
        Waveform.SQUARE -> if (phase < slope) 1.0f else 0.0f
    }
}
