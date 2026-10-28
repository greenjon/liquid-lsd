package llm.slop.liquidlsd.parameters

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CvModulator(
    val sourceId: String,
    val operator: ModulationOperator = ModulationOperator.ADD,
    var depth: Float = if (sourceId == "seq") 1.0f else 0.0f,
    val bypassed: Boolean = false,
    // Beat synchronization/shape settings
    val waveform: Waveform = Waveform.SINE,
    var subdivision: Float = 1.0f,
    var phaseOffset: Float = 0.0f,
    var slope: Float = 0.5f,
    // LFO speed bounds setting
    val lfoSpeedMode: LfoSpeedMode = LfoSpeedMode.FAST,
    val genUnit: GenUnit = GenUnit.BEAT,

    // Range fields
    var depthMin: Float = depth,
    var depthMax: Float = depth,
    val subdivisionMin: Float = subdivision,
    val subdivisionMax: Float = subdivision,
    val phaseOffsetMin: Float = phaseOffset,
    val phaseOffsetMax: Float = phaseOffset,
    val slopeMin: Float = slope,
    val slopeMax: Float = slope,

    val randomizeDepth: Boolean = false,
    val randomizeSubdivision: Boolean = false,
    val randomizePhaseOffset: Boolean = false,
    val randomizeSlope: Boolean = false,

    // Advanced LFO fields
    var morph: Float = 0.0f,
    val morphMin: Float = morph,
    val morphMax: Float = morph,
    val randomizeMorph: Boolean = false,

    var hold: Float = 0.0f,
    val holdMin: Float = hold,
    val holdMax: Float = hold,
    val randomizeHold: Boolean = false,

    // DC Offset fields
    var dcOffset: Float = 0.0f,
    var dcOffsetMin: Float = dcOffset,
    var dcOffsetMax: Float = dcOffset,
    val randomizeDcOffset: Boolean = false,

    // Modulator LFO (LFO 2) fields for Gen 1/Gen 2
    val modWaveform: Waveform = Waveform.SINE,
    var modSubdivision: Float = 1.0f,
    var modPhaseOffset: Float = 0.0f,
    var modSlope: Float = 0.5f,
    val modGenUnit: GenUnit = GenUnit.BEAT,
    val generatorModMode: GeneratorModMode = GeneratorModMode.NONE,
    var generatorModDepth: Float = 1.0f,

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
    var modMorph: Float = 0.0f,
    val modMorphMin: Float = modMorph,
    val modMorphMax: Float = modMorph,
    val randomizeModMorph: Boolean = false,

    var modHold: Float = 0.0f,
    val modHoldMin: Float = modHold,
    val modHoldMax: Float = modHold,
    val randomizeModHold: Boolean = false,

    val generatorModDepthMin: Float = generatorModDepth,
    val generatorModDepthMax: Float = generatorModDepth,
    val randomizeGeneratorModDepth: Boolean = false,

    // Audio envelope follower fields
    val followerMode: AudioFollowerMode = AudioFollowerMode.RAW,
    var attackMs: Float = 0.0f,
    var decayMs: Float = 0.0f,
    val attackMsMin: Float = attackMs,
    val attackMsMax: Float = attackMs,
    val decayMsMin: Float = decayMs,
    val decayMsMax: Float = decayMs,
    val randomizeAttackMs: Boolean = false,
    val randomizeDecayMs: Boolean = false,

    // Step Sequencer fields
    var seqSteps: List<Float> = List(32) { 0.0f },
    val seqStepCount: Int = 16,
    var seqHold: Float = 1.0f,
    val seqHoldMin: Float = seqHold,
    val seqHoldMax: Float = seqHold,
    val randomizeSeqHold: Boolean = false,
    val seqCurveSmooth: Boolean = false,
    /** When true, global/deck randomization re-rolls the whole step pattern too. */
    val randomizeSeqSteps: Boolean = false,

    val lfoMinMaxMode: Boolean = false,

    val id: String = llm.slop.liquidlsd.utils.DeterministicRandom.uuid()
) {
    /**
     * Unipolar sources (audio, trigger, MIDI CC) swing 0..1, so their output spans dcOffset..dcOffset+depth;
     * bipolar sources, the sequencer and section energy swing -1..1 and span dcOffset±depth.
     */
    private val halfRange: Boolean
        get() = sourceId != "seq" && sourceId != "audio_energy" && !llm.slop.liquidlsd.cv.isCvSourceBipolar(sourceId)

    /** Modulator output before the operator scalar: cv * depth + dcOffset. */
    fun lfoShape(cv: Float): Float = cv * depth + dcOffset

    fun getLfoMin(): Float = if (halfRange) dcOffset else dcOffset - depth
    fun getLfoMax(): Float = dcOffset + depth

    /** Splits a min/max range into (depth, dcOffset) for this source's polarity. */
    private fun depthAndOffset(min: Float, max: Float): Pair<Float, Float> =
        if (halfRange) Pair(max - min, min) else Pair((max - min) / 2f, (max + min) / 2f)

    fun withLfoRange(min: Float, max: Float): CvModulator {
        val (d, o) = depthAndOffset(min, max)
        return this.copy(
            depth = d,
            dcOffset = o,
            depthMin = if (!lfoMinMaxMode) d else depthMin,
            depthMax = if (!lfoMinMaxMode) d else depthMax,
            dcOffsetMin = if (!lfoMinMaxMode) o else dcOffsetMin,
            dcOffsetMax = if (!lfoMinMaxMode) o else dcOffsetMax
        )
    }
    private fun isDiscreteSubdivision(): Boolean {
        return sourceId == "beatPhase" || sourceId == "sampleAndHold" || 
               (sourceId == "lfo" && genUnit == GenUnit.BEAT)
    }

    fun randomizeActiveValues(
        random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng,
        seqBipolar: Boolean = seqSteps.any { it < 0f }
    ): CvModulator {
        val (newDepth, newDcOffset) = if (lfoMinMaxMode) {
            val rMin = if (randomizeDcOffset) {
                if (dcOffsetMin == dcOffsetMax) dcOffsetMin else random.nextFloat() * (dcOffsetMax - dcOffsetMin) + dcOffsetMin
            } else getLfoMin()

            val rMax = if (randomizeDepth) {
                if (depthMin == depthMax) depthMin else random.nextFloat() * (depthMax - depthMin) + depthMin
            } else getLfoMax()

            depthAndOffset(rMin, rMax)
        } else {
            val d = if (randomizeDepth) {
                if (depthMin == depthMax) depthMin else random.nextFloat() * (depthMax - depthMin) + depthMin
            } else depth

            val o = if (randomizeDcOffset) {
                if (dcOffsetMin == dcOffsetMax) dcOffsetMin else random.nextFloat() * (dcOffsetMax - dcOffsetMin) + dcOffsetMin
            } else dcOffset
            Pair(d, o)
        }

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
                } else if (sourceId == "lfo" && genUnit == GenUnit.FRAME) {
                    val minI = subdivisionMin.toInt().coerceIn(1, MAX_FRAME_SUBDIVISION)
                    val maxI = subdivisionMax.toInt().coerceIn(minI, MAX_FRAME_SUBDIVISION)
                    if (minI == maxI) minI.toFloat() else random.nextInt(minI, maxI + 1).toFloat()
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
                } else if (modGenUnit == GenUnit.FRAME) {
                    val minI = modSubdivisionMin.toInt().coerceIn(1, MAX_FRAME_SUBDIVISION)
                    val maxI = modSubdivisionMax.toInt().coerceIn(minI, MAX_FRAME_SUBDIVISION)
                    if (minI == maxI) minI.toFloat() else random.nextInt(minI, maxI + 1).toFloat()
                } else {
                    random.nextFloat() * (modSubdivisionMax - modSubdivisionMin) + modSubdivisionMin
                }
            }
        } else modSubdivision

        val newModDepth = if (randomizeGeneratorModDepth) {
            if (generatorModDepthMin == generatorModDepthMax) generatorModDepthMin else random.nextFloat() * (generatorModDepthMax - generatorModDepthMin) + generatorModDepthMin
        } else generatorModDepth

        val newAttackMs = if (randomizeAttackMs) {
            if (attackMsMin == attackMsMax) attackMsMin else random.nextFloat() * (attackMsMax - attackMsMin) + attackMsMin
        } else attackMs

        val newDecayMs = if (randomizeDecayMs) {
            if (decayMsMin == decayMsMax) decayMsMin else random.nextFloat() * (decayMsMax - decayMsMin) + decayMsMin
        } else decayMs

        val newSeqHold = if (randomizeSeqHold) {
            if (seqHoldMin == seqHoldMax) seqHoldMin else random.nextFloat() * (seqHoldMax - seqHoldMin) + seqHoldMin
        } else seqHold

        val newSeqSteps = if (randomizeSeqSteps) SeqPatterns.randomize(seqSteps, seqStepCount, seqBipolar, random) else seqSteps

        return this.copy(
            depth = newDepth,
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
            generatorModDepth = newModDepth,
            attackMs = newAttackMs,
            decayMs = newDecayMs,
            seqHold = newSeqHold,
            seqSteps = newSeqSteps
        )
    }

    fun randomizeDepth(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeDepth) return this
        if (lfoMinMaxMode) {
            val rMax = if (depthMin == depthMax) depthMin else random.nextFloat() * (depthMax - depthMin) + depthMin
            val (d, o) = depthAndOffset(getLfoMin(), rMax)
            return this.copy(depth = d, dcOffset = o)
        }
        val newDepth = if (depthMin == depthMax) depthMin else random.nextFloat() * (depthMax - depthMin) + depthMin
        return this.copy(depth = newDepth)
    }

    fun randomizeDcOffset(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeDcOffset) return this
        if (lfoMinMaxMode) {
            val rMin = if (dcOffsetMin == dcOffsetMax) dcOffsetMin else random.nextFloat() * (dcOffsetMax - dcOffsetMin) + dcOffsetMin
            val (d, o) = depthAndOffset(rMin, getLfoMax())
            return this.copy(depth = d, dcOffset = o)
        }
        val newDcOffset = if (dcOffsetMin == dcOffsetMax) dcOffsetMin else random.nextFloat() * (dcOffsetMax - dcOffsetMin) + dcOffsetMin
        return this.copy(dcOffset = newDcOffset)
    }

    fun randomizeSeqHold(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeSeqHold) return this
        val newHold = if (seqHoldMin == seqHoldMax) seqHoldMin else random.nextFloat() * (seqHoldMax - seqHoldMin) + seqHoldMin
        return this.copy(seqHold = newHold)
    }

    fun randomizeSubdivision(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeSubdivision) return this
        val newSubdiv = if (subdivisionMin == subdivisionMax) {
            subdivisionMin
        } else {
            if (isDiscreteSubdivision()) {
                val options = floatArrayOf(0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f)
                val valid = options.filter { it in subdivisionMin..subdivisionMax }
                if (valid.isNotEmpty()) valid.random(random) else subdivisionMin
            } else if (sourceId == "lfo" && genUnit == GenUnit.FRAME) {
                val minI = subdivisionMin.toInt().coerceIn(1, MAX_FRAME_SUBDIVISION)
                val maxI = subdivisionMax.toInt().coerceIn(minI, MAX_FRAME_SUBDIVISION)
                if (minI == maxI) minI.toFloat() else random.nextInt(minI, maxI + 1).toFloat()
            } else {
                random.nextFloat() * (subdivisionMax - subdivisionMin) + subdivisionMin
            }
        }
        return this.copy(subdivision = newSubdiv)
    }

    fun randomizePhaseOffset(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizePhaseOffset) return this
        val newPhase = if (phaseOffsetMin == phaseOffsetMax) phaseOffsetMin else random.nextFloat() * (phaseOffsetMax - phaseOffsetMin) + phaseOffsetMin
        return this.copy(phaseOffset = newPhase)
    }

    fun randomizeSlope(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeSlope) return this
        val newSlope = if (slopeMin == slopeMax) slopeMin else random.nextFloat() * (slopeMax - slopeMin) + slopeMin
        return this.copy(slope = newSlope)
    }

    fun randomizeMorph(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeMorph) return this
        val newMorph = if (morphMin == morphMax) morphMin else random.nextFloat() * (morphMax - morphMin) + morphMin
        return this.copy(morph = newMorph)
    }

    fun randomizeHold(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeHold) return this
        val newHold = if (holdMin == holdMax) holdMin else random.nextFloat() * (holdMax - holdMin) + holdMin
        return this.copy(hold = newHold)
    }

    fun randomizeModSubdivision(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeModSubdivision) return this
        val newSubdiv = if (modSubdivisionMin == modSubdivisionMax) {
            modSubdivisionMin
        } else {
            if (modGenUnit == GenUnit.BEAT) {
                val options = floatArrayOf(0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f)
                val valid = options.filter { it in modSubdivisionMin..modSubdivisionMax }
                if (valid.isNotEmpty()) valid.random(random) else modSubdivisionMin
            } else if (modGenUnit == GenUnit.FRAME) {
                val minI = modSubdivisionMin.toInt().coerceIn(1, MAX_FRAME_SUBDIVISION)
                val maxI = modSubdivisionMax.toInt().coerceIn(minI, MAX_FRAME_SUBDIVISION)
                if (minI == maxI) minI.toFloat() else random.nextInt(minI, maxI + 1).toFloat()
            } else {
                random.nextFloat() * (modSubdivisionMax - modSubdivisionMin) + modSubdivisionMin
            }
        }
        return this.copy(modSubdivision = newSubdiv)
    }

    fun randomizeModPhaseOffset(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeModPhaseOffset) return this
        val newPhase = if (modPhaseOffsetMin == modPhaseOffsetMax) modPhaseOffsetMin else random.nextFloat() * (modPhaseOffsetMax - modPhaseOffsetMin) + modPhaseOffsetMin
        return this.copy(modPhaseOffset = newPhase)
    }

    fun randomizeModSlope(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeModSlope) return this
        val newSlope = if (modSlopeMin == modSlopeMax) modSlopeMin else random.nextFloat() * (modSlopeMax - modSlopeMin) + modSlopeMin
        return this.copy(modSlope = newSlope)
    }

    fun randomizeModMorph(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeModMorph) return this
        val newMorph = if (modMorphMin == modMorphMax) modMorphMin else random.nextFloat() * (modMorphMax - modMorphMin) + modMorphMin
        return this.copy(modMorph = newMorph)
    }

    fun randomizeModHold(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeModHold) return this
        val newHold = if (modHoldMin == modHoldMax) modHoldMin else random.nextFloat() * (modHoldMax - modHoldMin) + modHoldMin
        return this.copy(modHold = newHold)
    }

    fun randomizeGeneratorModDepth(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeGeneratorModDepth) return this
        val newDepth = if (generatorModDepthMin == generatorModDepthMax) generatorModDepthMin else random.nextFloat() * (generatorModDepthMax - generatorModDepthMin) + generatorModDepthMin
        return this.copy(generatorModDepth = newDepth)
    }

    fun randomizeAttackMs(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeAttackMs) return this
        val newAttack = if (attackMsMin == attackMsMax) attackMsMin else random.nextFloat() * (attackMsMax - attackMsMin) + attackMsMin
        return this.copy(attackMs = newAttack)
    }

    fun randomizeDecayMs(random: kotlin.random.Random = llm.slop.liquidlsd.utils.DeterministicRandom.rng): CvModulator {
        if (!randomizeDecayMs) return this
        val newDecay = if (decayMsMin == decayMsMax) decayMsMin else random.nextFloat() * (decayMsMax - decayMsMin) + decayMsMin
        return this.copy(decayMs = newDecay)
    }

    companion object {
        /** Upper bound (inclusive) for LFO/CV modulation frame-subdivision step counts (GenUnit.FRAME). */
        const val MAX_FRAME_SUBDIVISION = 10000
    }
}

