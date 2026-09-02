package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.PI

enum class BoundaryTarget {
    READY_FOR_ONE,
    READY_FOR_ZERO
}

data class ModulatorSnapshot(
    var depth: Float = 0f,
    var subdivision: Float = 1f,
    var phaseOffset: Float = 0f,
    var slope: Float = 0.5f,
    var morph: Float = 0f,
    var hold: Float = 0f,
    var dcOffset: Float = 0f,
    var modSubdivision: Float = 1f,
    var modPhaseOffset: Float = 0f,
    var modSlope: Float = 0.5f,
    var modMorph: Float = 0f,
    var modHold: Float = 0f,
    var generatorModDepth: Float = 0f,
    var attackMs: Float = 0f,
    var decayMs: Float = 0f
) {
    fun copyFrom(mod: CvModulator) {
        depth = mod.depth
        subdivision = mod.subdivision
        phaseOffset = mod.phaseOffset
        slope = mod.slope
        morph = mod.morph
        hold = mod.hold
        dcOffset = mod.dcOffset
        modSubdivision = mod.modSubdivision
        modPhaseOffset = mod.modPhaseOffset
        modSlope = mod.modSlope
        modMorph = mod.modMorph
        modHold = mod.modHold
        generatorModDepth = mod.generatorModDepth
        attackMs = mod.attackMs
        decayMs = mod.decayMs
    }

    fun copyFrom(other: ModulatorSnapshot) {
        depth = other.depth
        subdivision = other.subdivision
        phaseOffset = other.phaseOffset
        slope = other.slope
        morph = other.morph
        hold = other.hold
        dcOffset = other.dcOffset
        modSubdivision = other.modSubdivision
        modPhaseOffset = other.modPhaseOffset
        modSlope = other.modSlope
        modMorph = other.modMorph
        modHold = other.modHold
        generatorModDepth = other.generatorModDepth
        attackMs = other.attackMs
        decayMs = other.decayMs
    }
}

data class ParameterSnapshot(
    var baseValue: Float = 0f,
    val modulators: MutableList<ModulatorSnapshot> = mutableListOf()
)

class DeckMorphSnapshot {
    val parameters = mutableMapOf<ModulatableParameter, ParameterSnapshot>()

    fun clear() {
        parameters.clear()
    }
}

object MorphMath {
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun shortestPathLerpAngle(a: Float, b: Float, t: Float): Float {
        val twoPi = (2.0 * PI).toFloat()
        var diff = (b - a) % twoPi
        if (diff < -PI.toFloat()) diff += twoPi
        if (diff > PI.toFloat()) diff -= twoPi
        var result = a + diff * t
        while (result < -PI.toFloat()) result += twoPi
        while (result > PI.toFloat()) result -= twoPi
        return result
    }

    fun shortestPathLerpHue(a: Float, b: Float, t: Float): Float {
        var diff = (b - a) % 1f
        if (diff < -0.5f) diff += 1f
        if (diff > 0.5f) diff -= 1f
        var result = (a + diff * t) % 1f
        if (result < 0f) result += 1f
        return result
    }
}

/**
 * Manages the continuous morphing state between two snapshots (S0 and S1)
 * driven by a 0.0 .. 1.0 parameter value, with flip-flop boundary latching.
 */
class DeckMorphController(
    private val paramsProvider: () -> List<ModulatableParameter>
) {
    val state0 = DeckMorphSnapshot()
    val state1 = DeckMorphSnapshot()

    var latchTarget: BoundaryTarget = BoundaryTarget.READY_FOR_ONE
        private set

    var isInitialized: Boolean = false
        private set

    /**
     * Initializes state0 and state1 from current parameter values.
     */
    fun initFromCurrentState() {
        state0.clear()
        state1.clear()
        val params = paramsProvider()
        for (param in params) {
            val snap0 = ParameterSnapshot(param.baseValue)
            val snap1 = ParameterSnapshot(param.baseValue)
            for (mod in param.modulators) {
                val modSnap0 = ModulatorSnapshot().apply { copyFrom(mod) }
                val modSnap1 = ModulatorSnapshot().apply { copyFrom(mod) }
                snap0.modulators.add(modSnap0)
                snap1.modulators.add(modSnap1)
            }
            state0.parameters[param] = snap0
            state1.parameters[param] = snap1
        }
        latchTarget = BoundaryTarget.READY_FOR_ONE
        isInitialized = true
    }

    /**
     * Samples a randomized state into the target snapshot buffer using constrained bounds.
     */
    fun sampleNewState(target: DeckMorphSnapshot, random: kotlin.random.Random = kotlin.random.Random.Default) {
        val params = paramsProvider()
        for (param in params) {
            var paramSnap = target.parameters[param]
            if (paramSnap == null) {
                paramSnap = ParameterSnapshot()
                target.parameters[param] = paramSnap
            }

            // Randomize base value if enabled
            val newBase = if (param.randomizeBase) {
                if (param.baseMin == param.baseMax) param.baseMin else random.nextFloat() * (param.baseMax - param.baseMin) + param.baseMin
            } else {
                param.baseValue
            }
            paramSnap.baseValue = newBase

            // Ensure modulator list size matches
            val modCount = param.modulators.size
            while (paramSnap.modulators.size < modCount) {
                paramSnap.modulators.add(ModulatorSnapshot())
            }
            while (paramSnap.modulators.size > modCount) {
                paramSnap.modulators.removeAt(paramSnap.modulators.size - 1)
            }

            // Randomize active modulator values
            for (i in 0 until modCount) {
                val mod = param.modulators[i]
                val modSnap = paramSnap.modulators[i]
                val randomized = mod.randomizeActiveValues(random)
                modSnap.copyFrom(randomized)
            }
        }
    }

    /**
     * Applies continuous morphing based on normalized parameter value [v] in [0.0, 1.0].
     * Handles boundary crossing latch updates and in-place interpolation.
     */
    fun update(vRaw: Float) {
        val v = vRaw.coerceIn(0f, 1f)

        if (!isInitialized) {
            initFromCurrentState()
            sampleNewState(state1)
        }

        // Boundary crossing state machine
        if (latchTarget == BoundaryTarget.READY_FOR_ONE && v >= 0.99f) {
            latchTarget = BoundaryTarget.READY_FOR_ZERO
            sampleNewState(state0)
        } else if (latchTarget == BoundaryTarget.READY_FOR_ZERO && v <= 0.01f) {
            latchTarget = BoundaryTarget.READY_FOR_ONE
            sampleNewState(state1)
        }

        // Apply interpolation
        val params = paramsProvider()
        for (param in params) {
            val snap0 = state0.parameters[param] ?: continue
            val snap1 = state1.parameters[param] ?: continue

            // 1. Interpolate base value
            val lerpedBase = when {
                param.isAngle -> MorphMath.shortestPathLerpAngle(snap0.baseValue, snap1.baseValue, v)
                param.meterType == MeterType.ENDLESS -> MorphMath.shortestPathLerpHue(snap0.baseValue, snap1.baseValue, v)
                else -> MorphMath.lerp(snap0.baseValue, snap1.baseValue, v)
            }
            param.baseValue = lerpedBase

            // 2. Interpolate modulator fields in-place
            val modCount = param.modulators.size
            val minSnapCount = minOf(snap0.modulators.size, snap1.modulators.size, modCount)
            for (i in 0 until minSnapCount) {
                val mod = param.modulators[i]
                val m0 = snap0.modulators[i]
                val m1 = snap1.modulators[i]

                mod.depth = MorphMath.lerp(m0.depth, m1.depth, v)
                mod.subdivision = MorphMath.lerp(m0.subdivision, m1.subdivision, v)
                mod.phaseOffset = MorphMath.lerp(m0.phaseOffset, m1.phaseOffset, v)
                mod.slope = MorphMath.lerp(m0.slope, m1.slope, v)
                mod.morph = MorphMath.lerp(m0.morph, m1.morph, v)
                mod.hold = MorphMath.lerp(m0.hold, m1.hold, v)
                mod.dcOffset = MorphMath.lerp(m0.dcOffset, m1.dcOffset, v)
                mod.modSubdivision = MorphMath.lerp(m0.modSubdivision, m1.modSubdivision, v)
                mod.modPhaseOffset = MorphMath.lerp(m0.modPhaseOffset, m1.modPhaseOffset, v)
                mod.modSlope = MorphMath.lerp(m0.modSlope, m1.modSlope, v)
                mod.modMorph = MorphMath.lerp(m0.modMorph, m1.modMorph, v)
                mod.modHold = MorphMath.lerp(m0.modHold, m1.modHold, v)
                mod.generatorModDepth = MorphMath.lerp(m0.generatorModDepth, m1.generatorModDepth, v)
                mod.attackMs = MorphMath.lerp(m0.attackMs, m1.attackMs, v)
                mod.decayMs = MorphMath.lerp(m0.decayMs, m1.decayMs, v)
            }
        }
    }

    /**
     * Forces an immediate randomization roll (e.g. on manual UI trigger).
     */
    fun forceRandomize(random: kotlin.random.Random = kotlin.random.Random.Default) {
        sampleNewState(state0, random)
        sampleNewState(state1, random)
        latchTarget = BoundaryTarget.READY_FOR_ONE
        isInitialized = true
        val params = paramsProvider()
        for (param in params) {
            val snap0 = state0.parameters[param] ?: continue
            param.baseValue = snap0.baseValue
            val minModCount = minOf(snap0.modulators.size, param.modulators.size)
            for (i in 0 until minModCount) {
                val mod = param.modulators[i]
                val m0 = snap0.modulators[i]
                mod.depth = m0.depth
                mod.subdivision = m0.subdivision
                mod.phaseOffset = m0.phaseOffset
                mod.slope = m0.slope
                mod.morph = m0.morph
                mod.hold = m0.hold
                mod.dcOffset = m0.dcOffset
                mod.modSubdivision = m0.modSubdivision
                mod.modPhaseOffset = m0.modPhaseOffset
                mod.modSlope = m0.modSlope
                mod.modMorph = m0.modMorph
                mod.modHold = m0.modHold
                mod.generatorModDepth = m0.generatorModDepth
                mod.attackMs = m0.attackMs
                mod.decayMs = m0.decayMs
            }
        }
    }
}
