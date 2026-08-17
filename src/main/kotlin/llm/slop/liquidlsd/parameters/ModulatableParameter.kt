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
    val historySize: Int = 200,
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
