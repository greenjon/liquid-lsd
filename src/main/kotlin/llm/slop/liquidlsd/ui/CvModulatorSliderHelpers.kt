package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.parameters.CvModulator

/**
 * Standard three-callback bundle for a [CvModulator] field that is exposed as a
 * [CustomRangeSlider].  Pass the returned object directly as named arguments.
 *
 * Every slider operating on a [CvModulator] field follows the same boilerplate:
 *   - onRandomizableChanged: if enabling, keep existing range or expand by [defaultOffset]
 *     on each side; if disabling, collapse range to the current value.
 *   - onRangeChanged: clamp the active value into [safeMin, safeMax] and store all three.
 *   - onValueChanged: set value and collapse min/max to that same value.
 *
 * @param getValue   Reads the current live value from the modulator.
 * @param getMin     Reads the current range minimum.
 * @param getMax     Reads the current range maximum.
 * @param minLimit   Hard floor for the slider (used to clamp the default offset expansion).
 * @param maxLimit   Hard ceiling for the slider (used to clamp the default offset expansion).
 * @param defaultOffset  How far to expand the range on each side when enabling randomization
 *                       and the range is currently collapsed (min == max). Defaults to 0.1.
 * @param copyWithRandomize  Returns a new [CvModulator] copy with randomize flag and range set.
 * @param copyWithRange  Returns a new [CvModulator] copy with the new range (and clamped active value).
 * @param copyWithValue  Returns a new [CvModulator] copy with value and min/max all set to [newVal].
 * @param randomizeNow   Returns a new [CvModulator] with the active value drawn from the range.
 * @param onReplace  Commits the updated [CvModulator] to the caller.
 */
data class CvSliderCallbacks(
    val onRandomizableChanged: (Boolean) -> Unit,
    val onRandomizeNow: () -> Unit,
    val onRangeChanged: (Float, Float) -> Unit,
    val onValueChanged: (Float) -> Unit,
)

fun cvModulatorSlider(
    existing: CvModulator,
    getValue: CvModulator.() -> Float,
    getMin: CvModulator.() -> Float,
    getMax: CvModulator.() -> Float,
    minLimit: Float,
    maxLimit: Float,
    defaultOffset: Float = 0.1f,
    copyWithRandomize: CvModulator.(enabled: Boolean, newMin: Float, newMax: Float) -> CvModulator,
    copyWithRange: CvModulator.(safeMin: Float, safeMax: Float, clampedValue: Float) -> CvModulator,
    copyWithValue: CvModulator.(newVal: Float) -> CvModulator,
    randomizeNow: CvModulator.() -> CvModulator,
    onReplace: (CvModulator) -> Unit,
): CvSliderCallbacks {
    return CvSliderCallbacks(
        onRandomizableChanged = { checked ->
            if (checked) {
                val rMin = existing.getMin()
                val rMax = existing.getMax()
                val (nextMin, nextMax) = if (rMin == rMax) {
                    Pair(
                        (existing.getValue() - defaultOffset).coerceAtLeast(minLimit),
                        (existing.getValue() + defaultOffset).coerceAtMost(maxLimit),
                    )
                } else {
                    Pair(rMin, rMax)
                }
                onReplace(existing.copyWithRandomize(true, nextMin, nextMax))
            } else {
                val v = existing.getValue()
                onReplace(existing.copyWithRandomize(false, v, v))
            }
        },
        onRandomizeNow = {
            onReplace(existing.randomizeNow())
        },
        onRangeChanged = { nextMin, nextMax ->
            val safeMin = minOf(nextMin, nextMax)
            val safeMax = maxOf(nextMin, nextMax)
            val clamped = existing.getValue().coerceIn(safeMin, safeMax)
            onReplace(existing.copyWithRange(safeMin, safeMax, clamped))
        },
        onValueChanged = { newVal ->
            onReplace(existing.copyWithValue(newVal))
        },
    )
}
