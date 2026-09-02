package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Dedicated UI section for Trigger CV modulators (trigger_onset, trigger_accent).
 * Renders DC Offset and Depth controls.
 */
object TriggerModulatorSection {

    fun draw(
        session: SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        // 1. DC Offset Slider
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = existing.id,
            label = "DC Offset",
            themeColor = themeColor,
            currentValue = existing.dcOffset,
            currentMin = existing.dcOffsetMin,
            currentMax = existing.dcOffsetMax,
            minLimit = -1f,
            maxLimit = 1f,
            defaultValue = 0f,
            isRandomizable = existing.randomizeDcOffset,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.dcOffsetMin
                    val rMax = existing.dcOffsetMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.dcOffset - 0.1f).coerceAtLeast(-1f), (existing.dcOffset + 0.1f).coerceAtMost(1f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    onReplace(existing.copy(
                        randomizeDcOffset = true,
                        dcOffsetMin = nextMin,
                        dcOffsetMax = nextMax
                    ))
                } else {
                    onReplace(existing.copy(
                        randomizeDcOffset = false,
                        dcOffsetMin = existing.dcOffset,
                        dcOffsetMax = existing.dcOffset
                    ))
                }
            },
            onRandomizeNow = {
                onReplace(existing.randomizeDcOffset())
            },
            onRangeChanged = { nextMin, nextMax ->
                val safeMin = minOf(nextMin, nextMax)
                val safeMax = maxOf(nextMin, nextMax)
                val nextActive = existing.dcOffset.coerceIn(safeMin, safeMax)
                onReplace(existing.copy(
                    dcOffsetMin = safeMin,
                    dcOffsetMax = safeMax,
                    dcOffset = nextActive
                ))
            },
            onValueChanged = { newVal ->
                onReplace(existing.copy(
                    dcOffset = newVal,
                    dcOffsetMin = newVal,
                    dcOffsetMax = newVal
                ))
            }
        )
        ImGui.spacing()

        // 2. Depth Slider
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = existing.id,
            label = "Depth",
            themeColor = themeColor,
            currentValue = existing.depth,
            currentMin = existing.depthMin,
            currentMax = existing.depthMax,
            minLimit = 0f,
            maxLimit = 1f,
            defaultValue = 1f,
            isRandomizable = existing.randomizeDepth,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.depthMin
                    val rMax = existing.depthMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.depth - 0.1f).coerceAtLeast(0f), (existing.depth + 0.1f).coerceAtMost(1f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    onReplace(existing.copy(
                        randomizeDepth = true,
                        depthMin = nextMin,
                        depthMax = nextMax
                    ))
                } else {
                    onReplace(existing.copy(
                        randomizeDepth = false,
                        depthMin = existing.depth,
                        depthMax = existing.depth
                    ))
                }
            },
            onRandomizeNow = {
                onReplace(existing.randomizeDepth())
            },
            onRangeChanged = { nextMin, nextMax ->
                val safeMin = minOf(nextMin, nextMax)
                val safeMax = maxOf(nextMin, nextMax)
                val nextActive = existing.depth.coerceIn(safeMin, safeMax)
                onReplace(existing.copy(
                    depthMin = safeMin,
                    depthMax = safeMax,
                    depth = nextActive
                ))
            },
            onValueChanged = { newVal ->
                onReplace(existing.copy(
                    depth = newVal,
                    depthMin = newVal,
                    depthMax = newVal
                ))
            }
        )
        ImGui.spacing()
    }
}
