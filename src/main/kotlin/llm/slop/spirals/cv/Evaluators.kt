package llm.slop.spirals.cv

import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.LfoSpeedMode
import llm.slop.spirals.parameters.calculateWaveform
import llm.slop.spirals.parameters.calculateAdvancedLFO
import llm.slop.spirals.parameters.GenUnit
import llm.slop.spirals.parameters.Waveform

fun evaluateModulator(modulator: CvModulator): Float {
    return when (modulator.sourceId) {
        "beatPhase" -> {
            val beats = CVRegistry.getSynchronizedTotalBeats()
            val localPhase = ((beats / modulator.subdivision) + modulator.phaseOffset) % 1.0
            val positivePhase = if (localPhase < 0.0) localPhase + 1.0 else localPhase
            if (modulator.waveform == Waveform.RANDOM) {
                val cyclePosition = (beats / modulator.subdivision.toDouble().coerceAtLeast(0.01)) + modulator.phaseOffset
                val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                val previousCycle = currentCycle - 1
                val seed = modulator.subdivision.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.id.hashCode()
                val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                val safeHold = modulator.hold.coerceIn(0.0f, 0.99f)
                val slideDuration = 1.0f - safeHold
                val tSlide = if (positivePhase < slideDuration) {
                    (positivePhase / slideDuration).toFloat().coerceIn(0f, 1f)
                } else {
                    1.0f
                }
                val k = 1.5f + (15.0f - 1.5f) * modulator.morph
                val maxVal = kotlin.math.log(kotlin.math.cosh(k.toDouble()), Math.E).toFloat() / k
                val heldTri = tSlide * 2.0f - 1.0f
                val result = if (heldTri >= 0f) {
                    val u = 1.0f - heldTri
                    val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                    1.0f - (smoothedU / maxVal)
                } else {
                    val u = 1.0f + heldTri
                    val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                    -1.0f + (smoothedU / maxVal)
                }
                val t = (result + 1.0f) / 2.0f
                previousValue + (currentValue - previousValue) * t
            } else {
                calculateAdvancedLFO(positivePhase, modulator.morph, modulator.hold, modulator.slope)
            }
        }
        "sampleAndHold" -> {
            val beats = CVRegistry.getSynchronizedTotalBeats()
            val subdivisionD = modulator.subdivision.toDouble().coerceAtLeast(0.01)
            
            val cyclePosition = (beats / subdivisionD) + modulator.phaseOffset
            val phase = cyclePosition % 1.0
            val positivePhase = if (phase < 0.0) phase + 1.0 else phase

            val currentCycle = kotlin.math.floor(cyclePosition).toInt()
            val previousCycle = currentCycle - 1

            val seed = subdivisionD.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.id.hashCode()

            val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
            val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f

            val safeHold = modulator.hold.coerceIn(0.0f, 0.99f)
            val slideDuration = 1.0f - safeHold
            val tSlide = if (positivePhase < slideDuration) {
                (positivePhase / slideDuration).toFloat().coerceIn(0f, 1f)
            } else {
                1.0f
            }

            val k = 1.5f + (15.0f - 1.5f) * modulator.morph
            val maxVal = kotlin.math.log(kotlin.math.cosh(k.toDouble()), Math.E).toFloat() / k
            val heldTri = tSlide * 2.0f - 1.0f
            val result = if (heldTri >= 0f) {
                val u = 1.0f - heldTri
                val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                1.0f - (smoothedU / maxVal)
            } else {
                val u = 1.0f + heldTri
                val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                -1.0f + (smoothedU / maxVal)
            }
            val t = (result + 1.0f) / 2.0f

            previousValue + (currentValue - previousValue) * t
        }
        "lfo" -> {
            val seconds = CVRegistry.getElapsedRealtimeSec()
            val period = modulator.subdivision.toDouble().coerceAtLeast(0.001)

            val localPhase = ((seconds / period) + modulator.phaseOffset) % 1.0
            val positivePhase = if (localPhase < 0.0) localPhase + 1.0 else localPhase
            if (modulator.waveform == Waveform.RANDOM) {
                val cyclePosition = (seconds / period) + modulator.phaseOffset
                val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                val previousCycle = currentCycle - 1
                val seed = period.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.id.hashCode()
                val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                val safeHold = modulator.hold.coerceIn(0.0f, 0.99f)
                val slideDuration = 1.0f - safeHold
                val tSlide = if (positivePhase < slideDuration) {
                    (positivePhase / slideDuration).toFloat().coerceIn(0f, 1f)
                } else {
                    1.0f
                }
                val k = 1.5f + (15.0f - 1.5f) * modulator.morph
                val maxVal = kotlin.math.log(kotlin.math.cosh(k.toDouble()), Math.E).toFloat() / k
                val heldTri = tSlide * 2.0f - 1.0f
                val result = if (heldTri >= 0f) {
                    val u = 1.0f - heldTri
                    val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                    1.0f - (smoothedU / maxVal)
                } else {
                    val u = 1.0f + heldTri
                    val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                    -1.0f + (smoothedU / maxVal)
                }
                val t = (result + 1.0f) / 2.0f
                previousValue + (currentValue - previousValue) * t
            } else {
                calculateAdvancedLFO(positivePhase, modulator.morph, modulator.hold, modulator.slope)
            }
        }
        "gen1", "gen2" -> {
            // 1. Evaluate internal modulator LFO (LFO 2) if active
            val modVal = if (modulator.generatorModMode != llm.slop.spirals.parameters.GeneratorModMode.NONE) {
                if (modulator.modGenUnit == GenUnit.TIME) {
                    val seconds = CVRegistry.getElapsedRealtimeSec()
                    val period = modulator.modSubdivision.toDouble().coerceAtLeast(0.001)
                    val cyclePosition = (seconds / period) + modulator.modPhaseOffset
                    val phase = cyclePosition % 1.0
                    val positivePhase = if (phase < 0.0) phase + 1.0 else phase
                    if (modulator.modWaveform == Waveform.RANDOM) {
                        val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                        val previousCycle = currentCycle - 1
                        val seed = period.hashCode() xor modulator.modPhaseOffset.hashCode() xor modulator.sourceId.hashCode() xor 999 xor modulator.id.hashCode()
                        val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                        val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                        
                        val safeHold = modulator.modHold.coerceIn(0.0f, 0.99f)
                        val slideDuration = 1.0f - safeHold
                        val tSlide = if (positivePhase < slideDuration) {
                            (positivePhase / slideDuration).toFloat().coerceIn(0f, 1f)
                        } else {
                            1.0f
                        }
                        val k = 1.5f + (15.0f - 1.5f) * modulator.modMorph
                        val maxVal = kotlin.math.log(kotlin.math.cosh(k.toDouble()), Math.E).toFloat() / k
                        val heldTri = tSlide * 2.0f - 1.0f
                        val result = if (heldTri >= 0f) {
                            val u = 1.0f - heldTri
                            val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                            1.0f - (smoothedU / maxVal)
                        } else {
                            val u = 1.0f + heldTri
                            val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                            -1.0f + (smoothedU / maxVal)
                        }
                        val t = (result + 1.0f) / 2.0f
                        previousValue + (currentValue - previousValue) * t
                    } else {
                        calculateAdvancedLFO(positivePhase, modulator.modMorph, modulator.modHold, modulator.modSlope)
                    }
                } else {
                    val beats = CVRegistry.getSynchronizedTotalBeats()
                    val subdivisionD = modulator.modSubdivision.toDouble().coerceAtLeast(0.01)
                    val cyclePosition = (beats / subdivisionD) + modulator.modPhaseOffset
                    val phase = cyclePosition % 1.0
                    val positivePhase = if (phase < 0.0) phase + 1.0 else phase
                    if (modulator.modWaveform == Waveform.RANDOM) {
                        val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                        val previousCycle = currentCycle - 1
                        val seed = subdivisionD.hashCode() xor modulator.modPhaseOffset.hashCode() xor modulator.sourceId.hashCode() xor 999 xor modulator.id.hashCode()
                        val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                        val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                        
                        val safeHold = modulator.modHold.coerceIn(0.0f, 0.99f)
                        val slideDuration = 1.0f - safeHold
                        val tSlide = if (positivePhase < slideDuration) {
                            (positivePhase / slideDuration).toFloat().coerceIn(0f, 1f)
                        } else {
                            1.0f
                        }
                        val k = 1.5f + (15.0f - 1.5f) * modulator.modMorph
                        val maxVal = kotlin.math.log(kotlin.math.cosh(k.toDouble()), Math.E).toFloat() / k
                        val heldTri = tSlide * 2.0f - 1.0f
                        val result = if (heldTri >= 0f) {
                            val u = 1.0f - heldTri
                            val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                            1.0f - (smoothedU / maxVal)
                        } else {
                            val u = 1.0f + heldTri
                            val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                            -1.0f + (smoothedU / maxVal)
                        }
                        val t = (result + 1.0f) / 2.0f
                        previousValue + (currentValue - previousValue) * t
                    } else {
                        calculateAdvancedLFO(positivePhase, modulator.modMorph, modulator.modHold, modulator.modSlope)
                    }
                }
            } else 0f

            // Apply PM (Phase Modulation) shift to LFO 1 (Carrier) phase calculation
            val pmShift = if (modulator.generatorModMode == llm.slop.spirals.parameters.GeneratorModMode.PM) {
                modVal * modulator.generatorModDepth
            } else 0f

            val carrierVal = if (modulator.genUnit == GenUnit.TIME) {
                val seconds = CVRegistry.getElapsedRealtimeSec()
                val period = modulator.subdivision.toDouble().coerceAtLeast(0.001)
                val cyclePosition = (seconds / period) + modulator.phaseOffset + pmShift
                val phase = cyclePosition % 1.0
                val positivePhase = if (phase < 0.0) phase + 1.0 else phase
                if (modulator.waveform == Waveform.RANDOM) {
                    val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                    val previousCycle = currentCycle - 1
                    val seed = period.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.sourceId.hashCode() xor modulator.id.hashCode()
                    val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                    val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                    
                    val safeHold = modulator.hold.coerceIn(0.0f, 0.99f)
                    val slideDuration = 1.0f - safeHold
                    val tSlide = if (positivePhase < slideDuration) {
                        (positivePhase / slideDuration).toFloat().coerceIn(0f, 1f)
                    } else {
                        1.0f
                    }
                    val k = 1.5f + (15.0f - 1.5f) * modulator.morph
                    val maxVal = kotlin.math.log(kotlin.math.cosh(k.toDouble()), Math.E).toFloat() / k
                    val heldTri = tSlide * 2.0f - 1.0f
                    val result = if (heldTri >= 0f) {
                        val u = 1.0f - heldTri
                        val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                        1.0f - (smoothedU / maxVal)
                    } else {
                        val u = 1.0f + heldTri
                        val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                        -1.0f + (smoothedU / maxVal)
                    }
                    val t = (result + 1.0f) / 2.0f
                    previousValue + (currentValue - previousValue) * t
                } else {
                    calculateAdvancedLFO(positivePhase, modulator.morph, modulator.hold, modulator.slope)
                }
            } else {
                val beats = CVRegistry.getSynchronizedTotalBeats()
                val subdivisionD = modulator.subdivision.toDouble().coerceAtLeast(0.01)
                val cyclePosition = (beats / subdivisionD) + modulator.phaseOffset + pmShift
                val phase = cyclePosition % 1.0
                val positivePhase = if (phase < 0.0) phase + 1.0 else phase
                if (modulator.waveform == Waveform.RANDOM) {
                    val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                    val previousCycle = currentCycle - 1
                    val seed = subdivisionD.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.sourceId.hashCode() xor modulator.id.hashCode()
                    val currentValue = kotlin.random.Random((currentCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                    val previousValue = kotlin.random.Random((previousCycle + seed).toLong()).nextFloat() * 2.0f - 1.0f
                    
                    val safeHold = modulator.hold.coerceIn(0.0f, 0.99f)
                    val slideDuration = 1.0f - safeHold
                    val tSlide = if (positivePhase < slideDuration) {
                        (positivePhase / slideDuration).toFloat().coerceIn(0f, 1f)
                    } else {
                        1.0f
                    }
                    val k = 1.5f + (15.0f - 1.5f) * modulator.morph
                    val maxVal = kotlin.math.log(kotlin.math.cosh(k.toDouble()), Math.E).toFloat() / k
                    val heldTri = tSlide * 2.0f - 1.0f
                    val result = if (heldTri >= 0f) {
                        val u = 1.0f - heldTri
                        val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                        1.0f - (smoothedU / maxVal)
                    } else {
                        val u = 1.0f + heldTri
                        val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
                        -1.0f + (smoothedU / maxVal)
                    }
                    val t = (result + 1.0f) / 2.0f
                    previousValue + (currentValue - previousValue) * t
                } else {
                    calculateAdvancedLFO(positivePhase, modulator.morph, modulator.hold, modulator.slope)
                }
            }

            // Apply final modulation operator (AM, ADD, or NONE/PM)
            when (modulator.generatorModMode) {
                llm.slop.spirals.parameters.GeneratorModMode.AM -> {
                    carrierVal * (1.0f + modVal * modulator.generatorModDepth)
                }
                llm.slop.spirals.parameters.GeneratorModMode.ADD -> {
                    carrierVal + modVal * modulator.generatorModDepth
                }
                else -> {
                    carrierVal
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

/**
 * Like getCombinedModulatorValue, but applies the correct formula per parameter polarity:
 *   Bipolar  (isBipolar=true):  modAmount = cv * amplitude + dc         → result in [-1, 1]
 *   Monopolar (isBipolar=false): modAmount = ((cv+1)/2) * amplitude + dc → result in [ 0, 1]
 *
 * Used by the O-scope in CellConfigPanel so its display matches the engine output.
 */
fun getCombinedEffectiveValue(mods: List<CvModulator>, isBipolar: Boolean): Float {
    if (mods.isEmpty()) return 0f

    var result = 0f
    var first = true
    for (mod in mods) {
        if (mod.bypassed) continue
        val cv = evaluateModulator(mod)
        val modAmount = if (isBipolar) {
            cv * mod.amplitude + mod.dcOffset
        } else {
            ((cv + 1f) / 2f) * mod.amplitude + mod.dcOffset
        }
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
    return if (isBipolar) result.coerceIn(-1f, 1f) else result.coerceIn(0f, 1f)
}
