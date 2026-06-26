package llm.slop.spirals.cv

import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.LfoSpeedMode
import llm.slop.spirals.parameters.calculateWaveform
import llm.slop.spirals.parameters.GenUnit
import llm.slop.spirals.parameters.Waveform

fun evaluateModulator(modulator: CvModulator): Float {
    return when (modulator.sourceId) {
        "beatPhase" -> {
            val beats = CVRegistry.getSynchronizedTotalBeats()
            val localPhase = ((beats / modulator.subdivision) + modulator.phaseOffset) % 1.0
            val positivePhase = if (localPhase < 0.0) localPhase + 1.0 else localPhase
            calculateWaveform(modulator.waveform, positivePhase, modulator.slope)
        }
        "sampleAndHold" -> {
            val beats = CVRegistry.getSynchronizedTotalBeats()
            val subdivisionD = modulator.subdivision.toDouble().coerceAtLeast(0.01)
            
            val cyclePosition = (beats / subdivisionD) + modulator.phaseOffset
            val phase = cyclePosition % 1.0
            val positivePhase = if (phase < 0.0) phase + 1.0 else phase

            val currentCycle = kotlin.math.floor(cyclePosition).toInt()
            val previousCycle = currentCycle - 1

            val seed = subdivisionD.hashCode() xor modulator.phaseOffset.hashCode()

            val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
            val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f

            val glideAmount = if (positivePhase < modulator.slope) {
                (positivePhase / modulator.slope).toFloat().coerceIn(0f, 1f)
            } else {
                1.0f
            }

            previousValue + (currentValue - previousValue) * glideAmount
        }
        "lfo" -> {
            val seconds = CVRegistry.getElapsedRealtimeSec()
            val period = when (modulator.lfoSpeedMode) {
                LfoSpeedMode.FAST -> modulator.subdivision * 10.0
                LfoSpeedMode.MEDIUM -> modulator.subdivision * 900.0
                LfoSpeedMode.SLOW -> modulator.subdivision * 86400.0
            }.coerceAtLeast(0.001)

            val localPhase = ((seconds / period) + modulator.phaseOffset) % 1.0
            val positivePhase = if (localPhase < 0.0) localPhase + 1.0 else localPhase
            calculateWaveform(modulator.waveform, positivePhase, modulator.slope)
        }
        "gen1", "gen2" -> {
            if (modulator.genUnit == GenUnit.TIME) {
                // Time-based (LFO)
                val seconds = CVRegistry.getElapsedRealtimeSec()
                val period = when (modulator.lfoSpeedMode) {
                    LfoSpeedMode.FAST -> modulator.subdivision * 10.0
                    LfoSpeedMode.MEDIUM -> modulator.subdivision * 900.0
                    LfoSpeedMode.SLOW -> modulator.subdivision * 86400.0
                }.coerceAtLeast(0.001)

                val cyclePosition = (seconds / period) + modulator.phaseOffset
                if (modulator.waveform == Waveform.RANDOM) {
                    val phase = cyclePosition % 1.0
                    val positivePhase = if (phase < 0.0) phase + 1.0 else phase

                    val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                    val previousCycle = currentCycle - 1

                    val seed = period.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.sourceId.hashCode()

                    val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                    val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f

                    val glideAmount = if (positivePhase < modulator.slope) {
                        (positivePhase / modulator.slope).toFloat().coerceIn(0f, 1f)
                    } else {
                        1.0f
                    }

                    previousValue + (currentValue - previousValue) * glideAmount
                } else {
                    val phase = cyclePosition % 1.0
                    val positivePhase = if (phase < 0.0) phase + 1.0 else phase
                    calculateWaveform(modulator.waveform, positivePhase, modulator.slope)
                }
            } else {
                // Beat-based
                val beats = CVRegistry.getSynchronizedTotalBeats()
                val subdivisionD = modulator.subdivision.toDouble().coerceAtLeast(0.01)

                val cyclePosition = (beats / subdivisionD) + modulator.phaseOffset
                if (modulator.waveform == Waveform.RANDOM) {
                    val phase = cyclePosition % 1.0
                    val positivePhase = if (phase < 0.0) phase + 1.0 else phase

                    val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                    val previousCycle = currentCycle - 1

                    val seed = subdivisionD.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.sourceId.hashCode()

                    val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                    val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f

                    val glideAmount = if (positivePhase < modulator.slope) {
                        (positivePhase / modulator.slope).toFloat().coerceIn(0f, 1f)
                    } else {
                        1.0f
                    }

                    previousValue + (currentValue - previousValue) * glideAmount
                } else {
                    val phase = cyclePosition % 1.0
                    val positivePhase = if (phase < 0.0) phase + 1.0 else phase
                    calculateWaveform(modulator.waveform, positivePhase, modulator.slope)
                }
            }
        }
        else -> {
            CVRegistry.get(modulator.sourceId)
        }
    }
}

fun getCombinedModulatorValue(mods: List<CvModulator>): Float {
    if (mods.isEmpty()) return 0f
    
    var result = 0f
    var first = true
    for (mod in mods) {
        if (mod.bypassed) continue
        val cv = evaluateModulator(mod)
        val modAmount = cv * mod.amplitude + mod.dcOffset
        if (first) {
            result = when (mod.operator) {
                llm.slop.spirals.parameters.ModulationOperator.ADD -> modAmount
                llm.slop.spirals.parameters.ModulationOperator.MUL -> modAmount
                llm.slop.spirals.parameters.ModulationOperator.SCALE -> 1.0f - mod.amplitude + modAmount
            }
            first = false
        } else {
            result = when (mod.operator) {
                llm.slop.spirals.parameters.ModulationOperator.ADD -> result + modAmount
                llm.slop.spirals.parameters.ModulationOperator.MUL -> result * (1.0f + modAmount)
                llm.slop.spirals.parameters.ModulationOperator.SCALE -> result * (1.0f - mod.amplitude + modAmount)
            }
        }
    }
    return result.coerceIn(-1.0f, 1.0f)
}
