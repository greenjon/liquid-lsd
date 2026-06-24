package llm.slop.spirals.parameters

import llm.slop.spirals.cv.CvHistoryBuffer
import llm.slop.spirals.cv.evaluateModulator
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A parameter that can be modulated by a base value and multiple CV sources.
 * Keeps a sliding history of its evaluated values.
 */
class ModulatableParameter(
    var baseValue: Float = 0.0f,
    val historySize: Int = 200,
    val minClamp: Float = 0.0f,
    val maxClamp: Float = 1.0f,
    var randomizeBase: Boolean = false
) {
    val modulators = CopyOnWriteArrayList<CvModulator>()
    val history = CvHistoryBuffer(historySize)

    val defaultValue: Float = baseValue
    var baseMin: Float = baseValue
    var baseMax: Float = baseValue

    var mappedMidiId: String? = null
    var midiMapMin: Float = 0f
    var midiMapMax: Float = 1f

    var value: Float = baseValue
        private set

    fun reset() {
        baseValue = defaultValue
        baseMin = defaultValue
        baseMax = defaultValue
        randomizeBase = false
        mappedMidiId = null
        midiMapMin = 0f
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
        mappedMidiId?.let { id ->
            val midiVal = llm.slop.spirals.cv.CVRegistry.get(id)
            baseValue = midiMapMin + midiVal * (midiMapMax - midiMapMin)
        }

        val activeMods = modulators.filter { !it.bypassed }
        if (activeMods.isEmpty()) {
            value = baseValue.coerceIn(minClamp, maxClamp)
            history.add(value)
            return value
        }

        var result = baseValue

        for (mod in activeMods) {
            val finalCv = evaluateModulator(mod)
            val modAmount = finalCv * mod.amplitude + mod.dcOffset

            result = when (mod.operator) {
                ModulationOperator.ADD -> result + modAmount
                ModulationOperator.MUL -> result * (1.0f + modAmount)
                ModulationOperator.SCALE -> result * (1.0f - mod.amplitude + modAmount)
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
}
