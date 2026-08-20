package llm.slop.liquidlsd.cv

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.LfoSpeedMode
import llm.slop.liquidlsd.parameters.calculateWaveform
import llm.slop.liquidlsd.parameters.calculateAdvancedLFO
import llm.slop.liquidlsd.parameters.GenUnit
import llm.slop.liquidlsd.parameters.Waveform

private fun randomFloatFromSeed(seed: Long): Float {
    var x = seed
    x = x xor (x ushr 33)
    x *= -4906477898972856333L
    x = x xor (x ushr 33)
    x *= -4265267296055433173L
    x = x xor (x ushr 33)
    val bits = (x ushr 40).toInt() and 0xffffff
    return (bits.toFloat() / 16777216.0f) * 2.0f - 1.0f
}

private fun calculateRandomWaveform(
    positivePhase: Double,
    morph: Float,
    hold: Float,
    previousValue: Float,
    currentValue: Float
): Float {
    val safeHold = hold.coerceIn(0.0f, 0.99f)
    val slideDuration = 1.0f - safeHold
    val tSlide = if (positivePhase < slideDuration) {
        (positivePhase / slideDuration).toFloat().coerceIn(0f, 1f)
    } else {
        1.0f
    }
    val k = 1.5f + (15.0f - 1.5f) * morph
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
    return previousValue + (currentValue - previousValue) * t
}

fun evaluateModulator(modulator: CvModulator): Float = evaluateModulatorAtOffset(modulator, 0.0)

fun evaluateModulatorAtOffset(modulator: CvModulator, timeOffsetSec: Double): Float {
    return when (modulator.sourceId) {
        "beatPhase" -> {
            val beats = CVRegistry.getSynchronizedTotalBeats() + timeOffsetSec * (120.0 / 60.0)
            val localPhase = ((beats / modulator.subdivision) + modulator.phaseOffset) % 1.0
            val positivePhase = if (localPhase < 0.0) localPhase + 1.0 else localPhase
            if (modulator.waveform == Waveform.RANDOM) {
                val cyclePosition = (beats / modulator.subdivision.toDouble().coerceAtLeast(0.01)) + modulator.phaseOffset
                val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                val previousCycle = currentCycle - 1
                val seed = modulator.subdivision.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.id.hashCode()
                val currentValue = randomFloatFromSeed((currentCycle + seed).toLong())
                val previousValue = randomFloatFromSeed((previousCycle + seed).toLong())
                calculateRandomWaveform(positivePhase, modulator.morph, modulator.hold, previousValue, currentValue)
            } else {
                calculateAdvancedLFO(positivePhase, modulator.morph, modulator.hold, modulator.slope)
            }
        }
        "sampleAndHold" -> {
            val beats = CVRegistry.getSynchronizedTotalBeats() + timeOffsetSec * (120.0 / 60.0)
            val subdivisionD = modulator.subdivision.toDouble().coerceAtLeast(0.01)
            
            val cyclePosition = (beats / subdivisionD) + modulator.phaseOffset
            val phase = cyclePosition % 1.0
            val positivePhase = if (phase < 0.0) phase + 1.0 else phase

            val currentCycle = kotlin.math.floor(cyclePosition).toInt()
            val previousCycle = currentCycle - 1

            val seed = subdivisionD.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.id.hashCode()

            val currentValue = randomFloatFromSeed((currentCycle + seed).toLong())
            val previousValue = randomFloatFromSeed((previousCycle + seed).toLong())

            calculateRandomWaveform(positivePhase, modulator.morph, modulator.hold, previousValue, currentValue)
        }
        "lfo" -> {
            // Full LFO / generator: time-based, beat-based, or frame-based clocking (GenUnit), with optional LFO2 modulation (AM/PM/ADD).
            val modVal = if (modulator.generatorModMode != llm.slop.liquidlsd.parameters.GeneratorModMode.NONE) {
                val cyclePosition: Double
                val seed: Int
                when (modulator.modGenUnit) {
                    GenUnit.TIME -> {
                        val seconds = CVRegistry.getElapsedRealtimeSec() + timeOffsetSec
                        val period = modulator.modSubdivision.toDouble().coerceAtLeast(0.001)
                        cyclePosition = (seconds / period) + modulator.modPhaseOffset
                        seed = period.hashCode() xor modulator.modPhaseOffset.hashCode() xor modulator.sourceId.hashCode() xor 999 xor modulator.id.hashCode()
                    }
                    GenUnit.BEAT -> {
                        val beats = CVRegistry.getSynchronizedTotalBeats() + timeOffsetSec * (120.0 / 60.0)
                        val subdivisionD = modulator.modSubdivision.toDouble().coerceAtLeast(0.01)
                        cyclePosition = (beats / subdivisionD) + modulator.modPhaseOffset
                        seed = subdivisionD.hashCode() xor modulator.modPhaseOffset.hashCode() xor modulator.sourceId.hashCode() xor 999 xor modulator.id.hashCode()
                    }
                    GenUnit.FRAME -> {
                        val fps = CVRegistry.getTargetFps().toDouble()
                        val frameCount = CVRegistry.getRenderFrameCount().toDouble() + timeOffsetSec * fps
                        val framePeriod = modulator.modSubdivision.toDouble().coerceAtLeast(1.0)
                        cyclePosition = (frameCount / framePeriod) + modulator.modPhaseOffset
                        seed = framePeriod.hashCode() xor modulator.modPhaseOffset.hashCode() xor modulator.sourceId.hashCode() xor 999 xor modulator.id.hashCode()
                    }
                }
                val phase = cyclePosition % 1.0
                val positivePhase = if (phase < 0.0) phase + 1.0 else phase
                if (modulator.modWaveform == Waveform.RANDOM) {
                    val currentCycle = kotlin.math.floor(cyclePosition).toInt()
                    val previousCycle = currentCycle - 1
                    val currentValue = randomFloatFromSeed((currentCycle + seed).toLong())
                    val previousValue = randomFloatFromSeed((previousCycle + seed).toLong())
                    calculateRandomWaveform(positivePhase, modulator.modMorph, modulator.modHold, previousValue, currentValue)
                } else {
                    calculateAdvancedLFO(positivePhase, modulator.modMorph, modulator.modHold, modulator.modSlope)
                }
            } else 0f

            // Apply PM (Phase Modulation) shift to LFO 1 (Carrier) phase calculation
            val pmShift = if (modulator.generatorModMode == llm.slop.liquidlsd.parameters.GeneratorModMode.PM) {
                modVal * modulator.generatorModDepth
            } else 0f

            val carrierCyclePosition: Double
            val carrierSeed: Int
            when (modulator.genUnit) {
                GenUnit.TIME -> {
                    val seconds = CVRegistry.getElapsedRealtimeSec() + timeOffsetSec
                    val period = modulator.subdivision.toDouble().coerceAtLeast(0.001)
                    carrierCyclePosition = (seconds / period) + modulator.phaseOffset + pmShift
                    carrierSeed = period.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.sourceId.hashCode() xor modulator.id.hashCode()
                }
                GenUnit.BEAT -> {
                    val beats = CVRegistry.getSynchronizedTotalBeats() + timeOffsetSec * (120.0 / 60.0)
                    val subdivisionD = modulator.subdivision.toDouble().coerceAtLeast(0.01)
                    carrierCyclePosition = (beats / subdivisionD) + modulator.phaseOffset + pmShift
                    carrierSeed = subdivisionD.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.sourceId.hashCode() xor modulator.id.hashCode()
                }
                GenUnit.FRAME -> {
                    val fps = CVRegistry.getTargetFps().toDouble()
                    val frameCount = CVRegistry.getRenderFrameCount().toDouble() + timeOffsetSec * fps
                    val framePeriod = modulator.subdivision.toDouble().coerceAtLeast(1.0)
                    carrierCyclePosition = (frameCount / framePeriod) + modulator.phaseOffset + pmShift
                    carrierSeed = framePeriod.hashCode() xor modulator.phaseOffset.hashCode() xor modulator.sourceId.hashCode() xor modulator.id.hashCode()
                }
            }
            val carrierPhase = carrierCyclePosition % 1.0
            val carrierPositivePhase = if (carrierPhase < 0.0) carrierPhase + 1.0 else carrierPhase
            val carrierVal = if (modulator.waveform == Waveform.RANDOM) {
                val currentCycle = kotlin.math.floor(carrierCyclePosition).toInt()
                val previousCycle = currentCycle - 1
                val currentValue = randomFloatFromSeed((currentCycle + carrierSeed).toLong())
                val previousValue = randomFloatFromSeed((previousCycle + carrierSeed).toLong())
                calculateRandomWaveform(carrierPositivePhase, modulator.morph, modulator.hold, previousValue, currentValue)
            } else {
                calculateAdvancedLFO(carrierPositivePhase, modulator.morph, modulator.hold, modulator.slope)
            }

            // Apply final modulation operator (AM, ADD, or NONE/PM)
            when (modulator.generatorModMode) {
                llm.slop.liquidlsd.parameters.GeneratorModMode.AM -> {
                    carrierVal * (1.0f + modVal * modulator.generatorModDepth)
                }
                llm.slop.liquidlsd.parameters.GeneratorModMode.ADD -> {
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

fun isCvSourceBipolar(sourceId: String): Boolean = when (sourceId) {
    "lfo", "beatSine", "beatPhase", "sampleAndHold" -> true
    else -> false
}

fun isAudioSource(sourceId: String): Boolean = when (sourceId) {
    "audio_amp", "audio_bass", "audio_mid", "audio_high" -> true
    else -> false
}

fun isTriggerSource(sourceId: String): Boolean = when (sourceId) {
    "trigger_onset", "trigger_accent" -> true
    else -> false
}

/**
 * Calculates combined effective modulator value with correct formula per parameter polarity:
 *   Bipolar source on Bipolar param:   modAmount = cv * depth + dc         → in [-1, 1]
 *   Bipolar source on Monopolar param: modAmount = ((cv+1)/2) * depth + dc → in [ 0, 1]
 *   Unipolar source (Audio/Trigger/MIDI): modAmount = cv * depth + dc       → in [ 0, 1] (silence is 0)
 *
 * Used by the O-scope in CellConfigPanel and PresetGrid knob indicators so displays match engine output.
 */
fun getCombinedEffectiveValue(mods: List<CvModulator>, isBipolar: Boolean, includeBypassed: Boolean = false): Float =
    getCombinedEffectiveValueAtOffset(mods, isBipolar, 0.0, includeBypassed)

fun getCombinedEffectiveValueAtOffset(mods: List<CvModulator>, isBipolar: Boolean, timeOffsetSec: Double, includeBypassed: Boolean = false): Float {
    if (mods.isEmpty()) return 0f

    var result = 0f
    var first = true
    for (mod in mods) {
        if (mod.bypassed && !includeBypassed) continue
        val cv = evaluateModulatorAtOffset(mod, timeOffsetSec)
        val isSourceBipolar = isCvSourceBipolar(mod.sourceId)
        val modAmount = if (isSourceBipolar) {
            if (isBipolar) {
                cv * mod.depth + mod.dcOffset
            } else {
                ((cv + 1f) / 2f) * mod.depth + mod.dcOffset
            }
        } else {
            // Unipolar source (Audio, Trigger, MIDI CC)
            cv * mod.depth + mod.dcOffset
        }
        if (first) {
            result = when (mod.operator) {
                llm.slop.liquidlsd.parameters.ModulationOperator.ADD -> modAmount
                llm.slop.liquidlsd.parameters.ModulationOperator.MUL -> modAmount
                llm.slop.liquidlsd.parameters.ModulationOperator.SCALE -> 1.0f - mod.depth + modAmount
            }
            first = false
        } else {
            result = when (mod.operator) {
                llm.slop.liquidlsd.parameters.ModulationOperator.ADD -> result + modAmount
                llm.slop.liquidlsd.parameters.ModulationOperator.MUL -> result * (1.0f + modAmount)
                llm.slop.liquidlsd.parameters.ModulationOperator.SCALE -> result * (1.0f - mod.depth + modAmount)
            }
        }
    }
    return if (isBipolar) result.coerceIn(-1f, 1f) else result.coerceIn(0f, 1f)
}

