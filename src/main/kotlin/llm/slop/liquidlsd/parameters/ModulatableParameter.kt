package llm.slop.liquidlsd.parameters

import llm.slop.liquidlsd.cv.CvHistoryBuffer
import llm.slop.liquidlsd.cv.evaluateModulator
import llm.slop.liquidlsd.cv.CVRegistry
import java.util.concurrent.CopyOnWriteArrayList

enum class MeterType {
    MONOPOLAR, BIPOLAR, ENDLESS, DISCRETE
}

/**
 * A parameter that can be modulated by a base value and multiple CV sources.
 * Keeps a sliding history of its evaluated values.
 */
class ModulatableParameter(
    var baseValue: Float = 0.0f,
    val historySize: Int = 600,
    val minClamp: Float = 0.0f,
    val maxClamp: Float = 1.0f,
    var randomizeBase: Boolean = false,
    val meterType: MeterType = if (minClamp < 0f) MeterType.BIPOLAR else MeterType.MONOPOLAR,
    val explicitIsAngle: Boolean = false
) {
    val isAngle: Boolean
        get() = explicitIsAngle || (minClamp in -3.15f..-3.13f && maxClamp in 3.13f..3.15f)
    val modulators = CopyOnWriteArrayList<CvModulator>()
    val history = CvHistoryBuffer(historySize)

    val defaultValue: Float = baseValue
    var baseMin: Float = baseValue
    var baseMax: Float = baseValue

    var scopeTimebase: ScopeTimebase = ScopeTimebase.AUTO

    /**
     * Resolves the effective timebase duration and division interval.
     * In AUTO mode, derives the timebase from the first active LFO modulator's period,
     * or uses [defaultWhenNoLfo] (default: 1s for reactive transient visualization).
     */
    fun resolveEffectiveTimebase(defaultWhenNoLfo: ScopeTimebase = ScopeTimebase.ONE_SEC): Pair<Float, Float> {
        if (scopeTimebase != ScopeTimebase.AUTO) {
            return Pair(scopeTimebase.durationSec, scopeTimebase.divSec)
        }
        val firstLfo = modulators.firstOrNull { !it.bypassed && (it.sourceId == "lfo" || it.sourceId == "beatPhase" || it.sourceId == "sampleAndHold") }
        if (firstLfo == null) {
            return Pair(defaultWhenNoLfo.durationSec, defaultWhenNoLfo.divSec)
        }
        val periodSec = when (firstLfo.sourceId) {
            "beatPhase", "sampleAndHold" -> firstLfo.subdivision * (60.0f / 120.0f)
            "lfo" -> {
                when (firstLfo.genUnit) {
                    GenUnit.TIME -> firstLfo.subdivision
                    GenUnit.BEAT -> firstLfo.subdivision * (60.0f / 120.0f)
                    GenUnit.FRAME -> firstLfo.subdivision / 60.0f
                }
            }
            else -> 10.0f
        }
        val targetWindow = periodSec * 2.0f
        val matchingTier = when {
            targetWindow <= 2.0f -> ScopeTimebase.ONE_SEC
            targetWindow <= 25.0f -> ScopeTimebase.TEN_SEC
            targetWindow <= 250.0f -> ScopeTimebase.HUNDRED_SEC
            targetWindow <= 2000.0f -> ScopeTimebase.FIFTEEN_MIN
            targetWindow <= 25000.0f -> ScopeTimebase.TWO_POINT_FIVE_HOURS
            else -> ScopeTimebase.TWENTY_FOUR_HOURS
        }
        return Pair(matchingTier.durationSec, matchingTier.divSec)
    }

    @Volatile
    var modulatorFilter: ((CvModulator) -> Boolean)? = null

    @Deprecated("Use global MIDI mapping profiles instead")
    var mappedMidiId: String? = null
    @Deprecated("Use global MIDI mapping profiles instead")
    var midiMapMin: Float = 0f
    @Deprecated("Use global MIDI mapping profiles instead")
    var midiMapMax: Float = 1f

    var value: Float = baseValue
        internal set

    fun reset() {
        baseValue = defaultValue
        baseMin = defaultValue
        baseMax = defaultValue
        randomizeBase = false
        @Suppress("DEPRECATION")
        mappedMidiId = null
        @Suppress("DEPRECATION")
        midiMapMin = 0f
        @Suppress("DEPRECATION")
        midiMapMax = 1f
        modulators.clear()
    }

    /**
     * Randomizes the static baseValue within the [baseMin, baseMax] range.
     */
    fun randomizeBaseValue(random: kotlin.random.Random = kotlin.random.Random.Default) {
        if (!randomizeBase) return
        baseValue = if (baseMin == baseMax) baseMin else random.nextFloat() * (baseMax - baseMin) + baseMin
    }

    /**
     * Calculates the final value by combining the base value with all active modulators.
     * Called once per frame prior to rendering.
     */
    fun evaluate(): Float {
        var hasActive = false
        val size = modulators.size
        for (i in 0 until size) {
            val mod = modulators[i]
            val isAllowed = modulatorFilter?.invoke(mod) ?: true
            if (isAllowed && !mod.bypassed && (CVRegistry.exists(mod.sourceId) || mod.sourceId.startsWith("midi_cc_"))) {
                hasActive = true
                break
            }
        }

        if (!hasActive) {
            value = baseValue.coerceIn(minClamp, maxClamp)
            history.add(value)
            return value
        }

        var result = baseValue

        for (i in 0 until size) {
            val mod = modulators[i]
            val isAllowed = modulatorFilter?.invoke(mod) ?: true
            if (!isAllowed || mod.bypassed || !(CVRegistry.exists(mod.sourceId) || mod.sourceId.startsWith("midi_cc_"))) {
                continue
            }
            val finalCv = evaluateModulator(mod)
            val isBipolar = minClamp < 0f
            // Bipolar:    rawModAmount = (rawCV * depth) + dc      → symmetric around 0
            // Monopolar:  rawModAmount = ((rawCV+1)/2 * depth) + dc → maps [−1,1] to [0,depth]+dc
            val rawModAmount = if (isBipolar) {
                finalCv * mod.depth + mod.dcOffset
            } else {
                ((finalCv + 1f) / 2f) * mod.depth + mod.dcOffset
            }
            
            val scalar = if (mod.operator == ModulationOperator.ADD) {
                if (isBipolar) (maxClamp - minClamp) / 2.0f else (maxClamp - minClamp)
            } else 1.0f
            val modAmount = rawModAmount * scalar

            result = when (mod.operator) {
                ModulationOperator.ADD -> result + modAmount
                ModulationOperator.MUL -> result * (1.0f + modAmount)
                ModulationOperator.SCALE -> result * (1.0f - mod.depth + modAmount)
            }
        }

        // Clamp the final parameter output to configured clamp range
        value = result.coerceIn(minClamp, maxClamp)
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

    /**
     * Creates a deep clone of this parameter.
     */
    fun clone(): ModulatableParameter {
        val copy = ModulatableParameter(
            baseValue = this.baseValue,
            historySize = this.historySize,
            minClamp = this.minClamp,
            maxClamp = this.maxClamp,
            randomizeBase = this.randomizeBase,
            meterType = this.meterType,
            explicitIsAngle = this.explicitIsAngle
        )
        copy.baseMin = this.baseMin
        copy.baseMax = this.baseMax
        copy.modulatorFilter = this.modulatorFilter
        copy.modulators.addAll(this.modulators.map { it.copy(id = java.util.UUID.randomUUID().toString()) })
        @Suppress("DEPRECATION")
        copy.mappedMidiId = this.mappedMidiId
        @Suppress("DEPRECATION")
        copy.midiMapMin = this.midiMapMin
        @Suppress("DEPRECATION")
        copy.midiMapMax = this.midiMapMax
        return copy
    }
}
