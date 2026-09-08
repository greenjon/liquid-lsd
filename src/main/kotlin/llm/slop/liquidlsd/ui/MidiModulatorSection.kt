package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Dedicated UI section for MIDI CC modulators (midi_cc_<channel>_<cc>).
 * Renders the mapped channel/CC readout, DC Offset, and Depth controls.
 */
object MidiModulatorSection {

    fun draw(
        session: SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        // Channel & CC info readout
        val parts = existing.sourceId.removePrefix("midi_cc_").split('_')
        if (parts.size >= 2) {
            val ch = parts[0].toIntOrNull() ?: 0
            val cc = parts[1].toIntOrNull() ?: 0
            val liveVal = llm.slop.liquidlsd.midi.MidiEngine.getCcValue(ch, cc)

            session.uiTheme.caption("Assigned MIDI Target: Channel ${ch + 1}, CC $cc (Live: ${"%.2f".format(liveVal)})")
            ImGui.spacing()
        }

        // 1. DC Offset Slider
        val dcOffsetCbs = cvModulatorSlider(
            existing = existing,
            getValue = { dcOffset }, getMin = { dcOffsetMin }, getMax = { dcOffsetMax },
            minLimit = -1f, maxLimit = 1f,
            copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeDcOffset = enabled, dcOffsetMin = nMin, dcOffsetMax = nMax) },
            copyWithRange   = { sMin, sMax, v -> copy(dcOffsetMin = sMin, dcOffsetMax = sMax, dcOffset = v) },
            copyWithValue   = { v -> copy(dcOffset = v, dcOffsetMin = v, dcOffsetMax = v) },
            randomizeNow    = { randomizeDcOffset() },
            onReplace = onReplace,
        )
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = existing.id,
            label = "DC Offset",
            themeColor = themeColor,
            currentValue = existing.dcOffset,
            currentMin = existing.dcOffsetMin,
            currentMax = existing.dcOffsetMax,
            minLimit = -1f, maxLimit = 1f, defaultValue = 0f,
            isRandomizable = existing.randomizeDcOffset,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = dcOffsetCbs.onRandomizableChanged,
            onRandomizeNow        = dcOffsetCbs.onRandomizeNow,
            onRangeChanged        = dcOffsetCbs.onRangeChanged,
            onValueChanged        = dcOffsetCbs.onValueChanged,
        )
        ImGui.spacing()

        // 2. Depth Slider
        val depthCbs = cvModulatorSlider(
            existing = existing,
            getValue = { depth }, getMin = { depthMin }, getMax = { depthMax },
            minLimit = 0f, maxLimit = 1f,
            copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeDepth = enabled, depthMin = nMin, depthMax = nMax) },
            copyWithRange   = { sMin, sMax, v -> copy(depthMin = sMin, depthMax = sMax, depth = v) },
            copyWithValue   = { v -> copy(depth = v, depthMin = v, depthMax = v) },
            randomizeNow    = { randomizeDepth() },
            onReplace = onReplace,
        )
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = existing.id,
            label = "Depth",
            themeColor = themeColor,
            currentValue = existing.depth,
            currentMin = existing.depthMin,
            currentMax = existing.depthMax,
            minLimit = 0f, maxLimit = 1f, defaultValue = 1f,
            isRandomizable = existing.randomizeDepth,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = depthCbs.onRandomizableChanged,
            onRandomizeNow        = depthCbs.onRandomizeNow,
            onRangeChanged        = depthCbs.onRangeChanged,
            onValueChanged        = depthCbs.onValueChanged,
        )
        ImGui.spacing()
    }
}
