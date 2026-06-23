package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.ModulationOperator
import llm.slop.spirals.parameters.Waveform
import kotlin.math.round

/**
 * Generates a randomized mandala configuration from a [RandomSet] template.
 * Ported from Android RandomSetGenerator; Android-specific dependencies removed.
 *
 * All randomization is deterministic given a [kotlin.random.Random] seed,
 * making patches reproducible when needed.
 */
object RecipeRandomizer {

    fun apply(rset: RandomSet, mandala: Mandala, random: kotlin.random.Random = kotlin.random.Random.Default) {
        // 1. Select a recipe
        mandala.recipe = selectRecipe(rset, random)

        // 2. Auto hue sweep
        if (rset.autoHueSweep) {
            val options = mandala.getSymmetricHueCycles(mandala.recipe.petals)
            val randomIdx = random.nextInt(options.size)
            val valForIdx = if (options.size > 1) randomIdx.toFloat() / (options.size - 1).toFloat() else 0.0f
            mandala.parameters["Hue Sweep"]?.let {
                it.baseValue = valForIdx
                it.baseMin = valForIdx
                it.baseMax = valForIdx
                it.modulators.clear()
            }
        }

        // 3. Arm lengths
        if (rset.linkArms) {
            listOf("L1","L2","L3","L4").forEach { applyArm(it, rset.l1Constraints, mandala, random) }
        } else {
            applyArm("L1", rset.l1Constraints, mandala, random)
            applyArm("L2", rset.l2Constraints, mandala, random)
            applyArm("L3", rset.l3Constraints, mandala, random)
            applyArm("L4", rset.l4Constraints, mandala, random)
        }

        // 4. Rotation
        applyRotation(rset.rotationConstraints, mandala, random)

        // 5. Hue offset
        applyHueOffset(rset.hueOffsetConstraints, mandala, random)
    }

    // ── Recipe selection ──────────────────────────────────────────────────────

    private fun selectRecipe(rset: RandomSet, random: kotlin.random.Random): MandalaRatio {
        val all = MandalaLibrary.MandalaRatios
        val filtered = when (rset.recipeFilter) {
            RecipeFilter.ALL           -> all
            RecipeFilter.PETALS_EXACT  -> all.filter { it.petals == (rset.petalCount ?: 5) }
            RecipeFilter.PETALS_RANGE  -> all.filter { it.petals in (rset.petalMin ?: 3)..(rset.petalMax ?: 9) }
            RecipeFilter.SPECIFIC_IDS  -> rset.specificRecipeIds?.let { ids -> all.filter { it.id in ids } } ?: all
        }
        return (if (filtered.isEmpty()) all else filtered).random(random)
    }

    // ── Parameter helpers ─────────────────────────────────────────────────────

    private fun applyArm(name: String, c: ArmConstraints?, mandala: Mandala, random: kotlin.random.Random) {
        val param = mandala.parameters[name] ?: return
        val constraints = c ?: ArmConstraints()
        param.baseValue = random.nextInt(constraints.baseLengthMin, constraints.baseLengthMax + 1) / 100f
        param.modulators.clear()

        val sources = buildList {
            if (constraints.enableBeat)   add("beatPhase")
            if (constraints.enableLfo)    add("lfo")
            if (constraints.enableRandom) add("sampleAndHold")
        }
        if (sources.isEmpty()) return

        val sourceId = sources.random(random)
        val weight   = random.nextInt(constraints.weightMin, constraints.weightMax + 1) / 100f
        val phase    = normalizedPhase(constraints.phaseMin, constraints.phaseMax, random)
        val waveform = if (sourceId == "sampleAndHold") Waveform.SINE else pickWaveform(constraints, random)

        val subdivision = when (sourceId) {
            "beatPhase", "sampleAndHold" -> pickBeatDiv(constraints.beatDivMin, constraints.beatDivMax, random)
            else -> random.nextInt(constraints.lfoTimeMin.toInt(), constraints.lfoTimeMax.toInt() + 1).toFloat()
        }
        val slope = if (sourceId == "sampleAndHold")
            random.nextFloat() * (constraints.randomGlideMax - constraints.randomGlideMin) + constraints.randomGlideMin
        else 0.5f

        param.modulators.add(CvModulator(sourceId, ModulationOperator.ADD, weight, false, waveform, subdivision, phase, slope))
    }

    private fun applyRotation(c: RotationConstraints?, mandala: Mandala, random: kotlin.random.Random) {
        val param = mandala.parameters["Rotation"] ?: return
        param.baseValue = 0f; param.modulators.clear()
        c ?: return

        val sources = buildList {
            if (c.enableBeat)   add("beatPhase")
            if (c.enableLfo)    add("lfo")
            if (c.enableRandom) add("sampleAndHold")
        }
        if (sources.isEmpty()) return

        val directions = buildList {
            if (c.enableClockwise)        add(0f)
            if (c.enableCounterClockwise) add(1f)
        }
        val slope   = if (directions.isNotEmpty()) directions.random(random) else 0f
        val sourceId = sources.random(random)
        val subdivision = when (sourceId) {
            "beatPhase", "sampleAndHold" -> pickBeatDiv(c.beatDivMin, c.beatDivMax, random)
            else -> random.nextInt(c.lfoTimeMin.toInt(), c.lfoTimeMax.toInt() + 1).toFloat()
        }
        val finalSlope = if (sourceId == "sampleAndHold")
            random.nextFloat() * (c.randomGlideMax - c.randomGlideMin) + c.randomGlideMin else slope

        param.modulators.add(CvModulator("beatPhase", ModulationOperator.ADD, 1.0f, false,
            Waveform.TRIANGLE, subdivision, random.nextFloat(), finalSlope))
    }

    private fun applyHueOffset(c: HueOffsetConstraints?, mandala: Mandala, random: kotlin.random.Random) {
        val param = mandala.parameters["Hue Offset"] ?: return
        param.baseValue = 0f; param.modulators.clear()
        c ?: return

        val sources = buildList {
            if (c.enableBeat)   add("beatPhase")
            if (c.enableLfo)    add("lfo")
            if (c.enableRandom) add("sampleAndHold")
        }
        if (sources.isEmpty()) return

        val directions = buildList {
            if (c.enableForward) add(1f)
            if (c.enableReverse) add(0f)
        }
        val slope    = if (directions.isNotEmpty()) directions.random(random) else 1f
        val sourceId = sources.random(random)
        val subdivision = when (sourceId) {
            "beatPhase", "sampleAndHold" -> pickBeatDiv(c.beatDivMin, c.beatDivMax, random)
            else -> random.nextInt(c.lfoTimeMin.toInt(), c.lfoTimeMax.toInt() + 1).toFloat()
        }
        val finalSlope = if (sourceId == "sampleAndHold")
            random.nextFloat() * (c.randomGlideMax - c.randomGlideMin) + c.randomGlideMin else slope

        param.modulators.add(CvModulator(sourceId, ModulationOperator.ADD, 1.0f, false,
            Waveform.TRIANGLE, subdivision, random.nextFloat(), finalSlope))
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun pickBeatDiv(min: Float, max: Float, random: kotlin.random.Random): Float {
        val valid = STANDARD_BEAT_VALUES.filter { it in min..max }
        return if (valid.isNotEmpty()) valid.random(random)
        else STANDARD_BEAT_VALUES.minByOrNull { kotlin.math.abs(it - min) } ?: 1f
    }

    private fun pickWaveform(c: ArmConstraints, random: kotlin.random.Random): Waveform {
        val allowed = buildList {
            if (c.allowSine)     add(Waveform.SINE)
            if (c.allowTriangle) add(Waveform.TRIANGLE)
            if (c.allowSquare)   add(Waveform.SQUARE)
        }
        return if (allowed.isNotEmpty()) allowed.random(random) else Waveform.SINE
    }

    private fun normalizedPhase(min: Float, max: Float, random: kotlin.random.Random): Float {
        val raw = if (max > min) random.nextFloat() * (max - min) + min else min
        return ((round(raw / 45.0) * 45.0) / 360.0).toFloat()
    }
}
