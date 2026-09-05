package llm.slop.liquidlsd.cv

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.LfoSpeedMode
import llm.slop.liquidlsd.parameters.calculateWaveform
import llm.slop.liquidlsd.parameters.calculateAdvancedLFO
import llm.slop.liquidlsd.parameters.GenUnit
import llm.slop.liquidlsd.parameters.Waveform
import llm.slop.liquidlsd.ui.UITheme

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
    val safeHold = hold.coerceIn(0.0f, 0.999f)
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
            val bpmVal = CVRegistry.get("bpm").toDouble().coerceIn(20.0, 300.0)
            val beats = CVRegistry.getSynchronizedTotalBeats() + timeOffsetSec * (bpmVal / 60.0)
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
                calculateAdvancedLFO(positivePhase, modulator.morph, modulator.hold, modulator.slope, modulator.waveform)
            }
        }
        "sampleAndHold" -> {
            val bpmVal = CVRegistry.get("bpm").toDouble().coerceIn(20.0, 300.0)
            val beats = CVRegistry.getSynchronizedTotalBeats() + timeOffsetSec * (bpmVal / 60.0)
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
                        val bpmVal = CVRegistry.get("bpm").toDouble().coerceIn(20.0, 300.0)
                        val beats = CVRegistry.getSynchronizedTotalBeats() + timeOffsetSec * (bpmVal / 60.0)
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
                    calculateAdvancedLFO(positivePhase, modulator.modMorph, modulator.modHold, modulator.modSlope, modulator.modWaveform)
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
                    val bpmVal = CVRegistry.get("bpm").toDouble().coerceIn(20.0, 300.0)
                    val beats = CVRegistry.getSynchronizedTotalBeats() + timeOffsetSec * (bpmVal / 60.0)
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
                calculateAdvancedLFO(carrierPositivePhase, modulator.morph, modulator.hold, modulator.slope, modulator.waveform)
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
        "seq" -> {
            if (!UITheme.sequencerEnabled) return 0.0f
            val stepCount = modulator.seqStepCount.coerceIn(1, 32)
            val steps = modulator.seqSteps
            val cyclePosition: Double = when (modulator.genUnit) {
                GenUnit.TIME -> {
                    val seconds = CVRegistry.getElapsedRealtimeSec() + timeOffsetSec
                    val period = modulator.subdivision.toDouble().coerceAtLeast(0.001)
                    (seconds / period) + modulator.phaseOffset
                }
                GenUnit.BEAT -> {
                    val bpmVal = CVRegistry.get("bpm").toDouble().coerceIn(20.0, 300.0)
                    val beats = CVRegistry.getSynchronizedTotalBeats() + timeOffsetSec * (bpmVal / 60.0)
                    val stepDiv = modulator.subdivision.toDouble().coerceAtLeast(0.001)
                    (beats / stepDiv) + modulator.phaseOffset
                }
                GenUnit.FRAME -> {
                    val fps = CVRegistry.getTargetFps().toDouble()
                    val frameCount = CVRegistry.getRenderFrameCount().toDouble() + timeOffsetSec * fps
                    val framePeriod = modulator.subdivision.toDouble().coerceAtLeast(1.0)
                    (frameCount / framePeriod) + modulator.phaseOffset
                }
            }

            val stepIndexRaw = kotlin.math.floor(cyclePosition).toLong()
            val curStep = Math.floorMod(stepIndexRaw, stepCount.toLong()).toInt()
            val nextStep = (curStep + 1) % stepCount

            val curVal = if (curStep < steps.size) steps[curStep] else 0f
            val nextVal = if (nextStep < steps.size) steps[nextStep] else 0f

            val stepFrac = (cyclePosition - kotlin.math.floor(cyclePosition)).toFloat().coerceIn(0f, 1f)
            val hold = modulator.seqHold.coerceIn(0f, 1f)

            if (hold >= 0.999f || stepFrac < hold) {
                curVal
            } else {
                val glideDuration = 1.0f - hold
                val t = ((stepFrac - hold) / glideDuration).coerceIn(0f, 1f)
                val u = if (modulator.seqCurveSmooth) {
                    0.5f - 0.5f * kotlin.math.cos(t * Math.PI.toFloat())
                } else {
                    t
                }
                curVal + (nextVal - curVal) * u
            }
        }
        else -> {
            val rawVal = CVRegistry.get(modulator.sourceId)
            if (isAudioSource(modulator.sourceId)) {
                if (modulator.followerMode == llm.slop.liquidlsd.parameters.AudioFollowerMode.RAW || 
                    (modulator.attackMs <= 0f && modulator.decayMs <= 0f)) {
                    rawVal
                } else {
                    AudioFollowerTracker.process(modulator.id, rawVal, modulator.attackMs, modulator.decayMs)
                }
            } else {
                rawVal
            }
        }
    }
}

object AudioFollowerTracker {
    private class FollowerState(
        @Volatile var value: Float = 0f,
        @Volatile var lastTimeNs: Long = 0L
    )

    private val states = java.util.concurrent.ConcurrentHashMap<String, FollowerState>()

    fun process(id: String, input: Float, attackMs: Float, decayMs: Float): Float {
        val now = llm.slop.liquidlsd.utils.TimeSource.getTimeNanos()
        val state = states.computeIfAbsent(id) { FollowerState(value = input, lastTimeNs = now) }

        val lastTime = state.lastTimeNs
        val dtSec = if (llm.slop.liquidlsd.utils.TimeSource.isSimulated) {
            llm.slop.liquidlsd.utils.TimeSource.getDeltaTimeSec()
        } else if (lastTime == 0L) {
            1.0 / CVRegistry.getTargetFps().toDouble()
        } else {
            ((now - lastTime) / 1_000_000_000.0).coerceIn(0.0001, 0.2)
        }
        state.lastTimeNs = now

        val attTau = (attackMs / 1000.0).coerceAtLeast(0.001)
        val decTau = (decayMs / 1000.0).coerceAtLeast(0.001)

        val curVal = state.value
        val nextVal = if (input > curVal) {
            if (attackMs <= 0.5f) {
                input
            } else {
                val alpha = (1.0 - kotlin.math.exp(-dtSec / attTau)).toFloat()
                curVal + alpha * (input - curVal)
            }
        } else {
            if (decayMs <= 0.5f) {
                input
            } else {
                val alpha = (1.0 - kotlin.math.exp(-dtSec / decTau)).toFloat()
                curVal + alpha * (input - curVal)
            }
        }
        state.value = nextVal
        return nextVal
    }

    fun reset() {
        states.clear()
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
        val modAmount = if (mod.sourceId == "seq") {
            cv * mod.depth + mod.dcOffset
        } else if (isSourceBipolar) {
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

