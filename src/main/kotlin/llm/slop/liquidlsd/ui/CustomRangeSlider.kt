package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.midi.MidiLearnTarget
import imgui.ImGui
import kotlin.math.roundToInt

object CustomRangeSlider {
    /** The dice button beside a slider (also [BeatDivisionSlider]): built once, so the hover path allocates nothing. */
    internal val RANDOM_RANGE_ON_TIP = buildControlTooltip {
        header = "Random range: on"
        leftClick = "turn it off (randomize leaves this value alone)"
        rightClick = "randomize now"
    }
    internal val RANDOM_RANGE_OFF_TIP = buildControlTooltip {
        header = "Random range: off"
        leftClick = "turn it on (randomize picks a value between Min and Max)"
        rightClick = "randomize now"
    }

    /**
     * Tooltip for a slider's name label (also [BeatDivisionSlider] and the Min/Max captions): what it is, the
     * armed MIDI/OSC Learn state, and its gestures. [defaultText] is the formatted default, null to leave it out;
     * [resettable] says middle-click resets it; [learnable] says the right-click menu offers MIDI/OSC Learn;
     * [about] is the control's explanation, shown under the header.
     */
    internal fun labelTooltip(
        label: String,
        defaultText: String?,
        bound: llm.slop.liquidlsd.macro.MacroBindingInfo?,
        isMacroLearning: Boolean,
        oscArmed: Boolean,
        midiArmed: Boolean,
        learnable: Boolean,
        resettable: Boolean,
        about: String? = null
    ) {
        showTooltip(buildControlTooltip {
            if (bound != null) {
                header = "$label [${bound.badgeLabel}]"
                description = "Controlled by ${bound.controlName}."
                leftClick = "edit the target"
            } else {
                header = if (defaultText != null) "$label (default $defaultText)" else label
                description = about
                status = when {
                    oscArmed -> "[OSC LEARN ARMED] Move a control on your OSC surface to map it."
                    midiArmed -> "[MIDI LEARN ARMED] Move a knob or fader on your MIDI controller to map it."
                    else -> null
                }
                if (isMacroLearning) leftClick = "add it as a target of the armed macro knob"
                if (resettable) middleClick = "reset to default"
                if (learnable && !oscArmed && !midiArmed) rightClick = "MIDI/OSC Learn"
            }
        })
    }

    /** Which part of a slider track the pointer is over, for [trackTooltip]. */
    internal enum class TrackSpot { NONE, MIN, MAX, CURRENT }

    /**
     * Tooltip for a slider track (also [BeatDivisionSlider]). [what] names the value ("value", "speed"); [isRange]
     * is true while the random range (two handles) is shown; [rangeScroll] says what scrolling does to the range;
     * [about] is the control's explanation.
     */
    internal fun trackTooltip(
        label: String,
        what: String,
        locked: llm.slop.liquidlsd.macro.MacroBindingInfo?,
        isRange: Boolean,
        spot: TrackSpot,
        rangeScroll: String,
        about: String? = null
    ) {
        showTooltip(when {
            locked != null -> buildControlTooltip {
                header = "$label: locked by ${locked.controlName} [${locked.badgeLabel}]"
                description = "Adjust the target in the Edit row or the Modulation column."
            }
            isRange && spot == TrackSpot.CURRENT -> "$label: current modulated $what"
            isRange -> buildControlTooltip {
                header = when (spot) {
                    TrackSpot.MIN -> "$label: minimum $what"
                    TrackSpot.MAX -> "$label: maximum $what"
                    else -> "$label: random range"
                }
                description = about
                drag = "move a handle"
                scroll = rangeScroll
                middleClick = "reset to default"
            }
            else -> buildControlTooltip {
                header = "$label: base $what"
                description = about
                drag = "adjust"
                scroll = "adjust"
                middleClick = "reset to default"
            }
        })
    }

    private val DEFAULT_PARSE: (String) -> Float? = { it.toFloatOrNull() }
    private var draggingMin = false
    private var draggingMax = false
    private var activeSliderLabel: String? = null
    private var clickMouseX = 0f
    /** Dragging the whole range of a slidable dual slider; the range at the start of the drag. */
    private var draggingBoth = false
    private var slideStartMin = 0f
    private var slideStartMax = 0f

    var isAnySliderHovered = false

    private val textBuffers = mutableMapOf<String, imgui.type.ImString>()
    private val textWidgetActive = mutableMapOf<String, Boolean>()
    private val textCallbacks = mutableMapOf<String, ReusableInputCallback>()

    private class ReusableInputCallback : imgui.callback.ImGuiInputTextCallback() {
        var currentValue: Float = 0f
        var minLimit: Float = 0f
        var maxLimit: Float = 0f
        var formatValue: (Float) -> String = { "%.3f".format(it) }
        var onChanged: (Float) -> Unit = {}

        override fun accept(data: imgui.ImGuiInputTextCallbackData) {
            val upPressed = data.eventKey == imgui.flag.ImGuiKey.UpArrow
            val downPressed = data.eventKey == imgui.flag.ImGuiKey.DownArrow
            if (upPressed || downPressed) {
                val io = ImGui.getIO()
                val shift = io.keyShift
                val ctrl = io.keyCtrl
                val delta = if (ctrl && shift) {
                    0.1f
                } else if (shift) {
                    0.01f
                } else {
                    0.001f
                }
                val dir = if (upPressed) 1f else -1f
                val nextValue = currentValue + (dir * delta)
                val clampedValue = nextValue.coerceIn(minLimit, maxLimit)
                
                val formatted = formatValue(clampedValue)
                data.deleteChars(0, data.buf.length)
                data.insertChars(0, formatted)
                data.cursorPos = formatted.length
                data.selectionStart = formatted.length
                data.selectionEnd = formatted.length
                data.setBufDirty(true)

                onChanged(clampedValue)
            }
        }
    }

    private fun drawTextInput(
        session: llm.slop.liquidlsd.SessionContext,
        key: String,
        currentValue: Float,
        minLimit: Float,
        maxLimit: Float,
        posX: Float,
        posY: Float,
        width: Float,
        defaultValue: Float? = null,
        onChanged: (Float) -> Unit,
        formatValue: (Float) -> String = ValueFormat.AUTO,
        parseValue: (String) -> Float? = DEFAULT_PARSE,
        readOnly: Boolean = false,
        macroBindingInfo: llm.slop.liquidlsd.macro.MacroBindingInfo? = null,
        displayScale: Float = 1f,
        tourKey: String? = null
    ) {
        val scale = displayScale
        val shown = ValueFormat.resolve(formatValue, scale)
        val parse: (String) -> Float? = if (scale != 1f && parseValue === DEFAULT_PARSE) { t -> t.toFloatOrNull()?.div(scale) } else parseValue
        val buffer = textBuffers.getOrPut(key) { imgui.type.ImString(shown(currentValue), 32) }
        val active = textWidgetActive.getOrDefault(key, false)
        if (!active) {
            buffer.set(shown(currentValue))
        }
        ImGui.setCursorScreenPos(posX, posY)
        ImGui.pushItemWidth(width)

        // Native callback to handle arrow keys
        var flags = imgui.flag.ImGuiInputTextFlags.CallbackHistory
        val isMacroBound = macroBindingInfo != null
        if (readOnly || isMacroBound) {
            flags = flags or imgui.flag.ImGuiInputTextFlags.ReadOnly
        }
        val callback = textCallbacks.getOrPut(key) { ReusableInputCallback() }
        callback.currentValue = currentValue
        callback.minLimit = minLimit
        callback.maxLimit = maxLimit
        callback.formatValue = shown
        callback.onChanged = onChanged

        val isBoundValue = isMacroBound
        if (isBoundValue) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, TangoPalette.u32(TangoPalette.SYNC.normal, 1.0f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, TangoPalette.u32(TangoPalette.SYNC.normal, 0.08f))
        }

        val inputChanged = ImGui.inputText("##input_$key", buffer, flags, callback)
        if (tourKey != null) llm.slop.liquidlsd.tour.WidgetRegistry.note { tourKey }

        if (isBoundValue) {
            ImGui.popStyleColor(2)
        }

        if (inputChanged && !isMacroBound) {
            val parsed = parse(buffer.get())
            if (parsed != null) {
                val clamped = parsed.coerceIn(minLimit, maxLimit)
                onChanged(clamped)
            }
        }
        val isItemFocused = ImGui.isItemActive()
        val isHovered = ImGui.isItemHovered()
        if (isItemFocused || isHovered || isBoundValue) {
            isAnySliderHovered = true
            val dl = ImGui.getWindowDrawList()
            val frameH = ImGui.getFrameHeight()
            val borderCol = when {
                isBoundValue -> TangoPalette.u32(TangoPalette.SYNC.normal, if (isHovered) 1.0f else 0.7f)
                isItemFocused -> TangoPalette.u32(TangoPalette.SYNC.normal, 1.0f) // Sync cyan for active focus
                else -> TangoPalette.u32(TangoPalette.ALERT.normal, 0.9f) // Alert amber for mouse hover
            }
            dl.addRect(posX - 1.5f, posY - 1.5f, posX + width + 1.5f, posY + frameH + 1.5f, borderCol, 3f, 0, if (isBoundValue) 1.5f else 2.0f)

            if (!isMacroBound) {
                val io = ImGui.getIO()
                if (io.mouseWheel != 0f) {
                    val shift = io.keyShift
                    val ctrl = io.keyCtrl
                    val deltaStep = if (ctrl && shift) 0.1f else if (shift) 0.01f else 0.001f
                    val nextVal = (currentValue + io.mouseWheel * deltaStep).coerceIn(minLimit, maxLimit)
                    onChanged(nextVal)
                    io.mouseWheel = 0f
                }
                if (ImGui.isMouseClicked(2) || ImGui.isItemClicked(2)) { // Middle click reset
                    val resetTarget = defaultValue ?: 0.0f.coerceIn(minLimit, maxLimit)
                    onChanged(resetTarget)
                }
            }
            val fieldType = when {
                isBoundValue -> "Controlled by ${macroBindingInfo!!.controlName} [${macroBindingInfo.badgeLabel}].\nManual edits are disabled while macro is active."
                key.endsWith("_min") -> "Minimum modulation boundary. Type, Up/Down, or Scroll to adjust. Middle-click to reset."
                key.endsWith("_max") -> "Maximum modulation boundary. Type, Up/Down, or Scroll to adjust. Middle-click to reset."
                key.endsWith("_value") -> "Base value. Type, Up/Down, or Scroll to adjust. Middle-click to reset."
                else -> "Type a precise numeric value. Up/Down or Scroll to adjust. Middle-click to reset."
            }
            showTooltip(fieldType)
        }
        textWidgetActive[key] = isItemFocused
        ImGui.popItemWidth()
    }

    private fun minMaxLock(paramKey: String?, modulatorId: String?, prop: String): llm.slop.liquidlsd.macro.MacroBindingInfo? =
        if (paramKey != null && modulatorId != null) llm.slop.liquidlsd.macro.MacroEngine.lockingBindingInfo(paramKey, modulatorId, prop) else null

    /**
     * The Min/Max range row every modulator shows (LFO, audio, sequencer, MIDI). Storage is Base (`dcOffset`)
     * and Amount (`depth`); Min/Max is how they are drawn. Dragging the bar between the handles, or Shift-dragging,
     * slides the whole range with its span locked (stopping at the track ends). The captions offer MIDI/OSC/macro
     * Learn for Min, Max, Base and Amount, whichever way the row is drawn (none without a [paramKey]).
     *
     * With the dice on, two roll rows replace it: the Min and Max ends ([RollSpace.ENDS]) or Base and Amount
     * ([RollSpace.BASE_AMOUNT]). Each row has a pin: a pinned row keeps its current value (and edits it) while
     * the other rolls.
     */
    fun drawModulatorRange(
        session: llm.slop.liquidlsd.SessionContext,
        label: String,
        mod: llm.slop.liquidlsd.parameters.CvModulator,
        unipolar: Boolean,
        isRandomizeDisabled: Boolean = false,
        themeColor: Int,
        onReplace: (llm.slop.liquidlsd.parameters.CvModulator) -> Unit,
        modulatorIndex: Int? = null,
        paramKey: String? = null,
        tooltip: String = "The lowest and highest the modulator takes this parameter, measured from its set value.\n" +
            "Drag between the handles (or Shift-drag) to slide the whole range; right-click Base or Amount above the track to learn those instead."
    ) {
        val idPrefix = mod.id
        val modulatorId = mod.id
        val minLimit = -1f
        val maxLimit = 1f
        val amountLimit = mod.amountLimit(unipolar)
        val curMin = mod.getLfoMin(unipolar)
        val curMax = mod.getLfoMax(unipolar)
        val rolling = mod.rollsRange
        val effectiveIsRandomizable = if (isRandomizeDisabled) false else (rolling && session.uiTheme.randomizationEnabled)
        val fontScale = 0.95f
        val buttonSize = ImGui.getFrameHeight()
        val w = ImGui.getContentRegionAvailX()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val formatValue = ValueFormat.AUTO
        val displayScale = ValueFormat.scaleFor(minLimit, maxLimit)

        ImGui.pushID("${idPrefix}_$label")

        val labelColW = 110f * fontScale
        val textBoxesStartX = startX + labelColW + 10f * fontScale
        val boxWidth = 44f * fontScale
        val boxSpacing = 8f
        val sliderStartX = textBoxesStartX + (boxWidth * 2f + boxSpacing) + 15f
        val lineEndX = maxOf(sliderStartX + 1f, startX + w - 10f)
        val lineWidth = (lineEndX - sliderStartX).coerceAtLeast(1f)

        ImGui.setCursorScreenPos(startX, startY + (buttonSize - session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() }) / 2f)
        session.uiTheme.body(label)
        itemTooltip(tooltip)

        if (session.uiTheme.randomizationEnabled) {
            ImGui.setCursorScreenPos(startX + labelColW - buttonSize, startY)
            if (isRandomizeDisabled) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, TangoPalette.TEXT_FAINT.u32())
                session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                    ButtonChrome.button("${Icons.DICES}##rand_disabled", buttonSize, buttonSize)
                }
                ImGui.popStyleColor()
                itemTooltip(llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP)
            } else {
                if (!effectiveIsRandomizable) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, TangoPalette.TEXT_DIM.u32())
                session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                    if (ButtonChrome.button("${Icons.DICES}##rand_toggle", buttonSize, buttonSize)) {
                        onReplace(mod.withRangeRolling(!effectiveIsRandomizable, unipolar))
                    }
                }
                if (!effectiveIsRandomizable) ImGui.popStyleColor()
                if (ImGui.isItemClicked(1)) {
                    val on = if (!effectiveIsRandomizable) mod.withRangeRolling(true, unipolar) else mod
                    onReplace(on.rollRange(unipolar = unipolar))
                }
                itemTooltip(if (effectiveIsRandomizable) RANDOM_RANGE_ON_TIP else RANDOM_RANGE_OFF_TIP)
            }
        }

        val isMacroBindable = paramKey != null
        val isMacroLearning = isMacroBindable && llm.slop.liquidlsd.macro.MacroLearnState.isLearning()
        val bind = { propertyName: String, lo: Float, hi: Float ->
            llm.slop.liquidlsd.macro.MacroLearnState.bindTarget(
                bank = llm.slop.liquidlsd.macro.MacroEngine.bankForParamPath(paramKey!!),
                targetType = llm.slop.liquidlsd.macro.MacroTargetType.MODULATOR_PROPERTY,
                parameterId = paramKey,
                modulatorId = modulatorId,
                propertyName = propertyName,
                minVal = lo,
                maxVal = hi
            )
        }
        fun caption(text: String, x: Float, y: Float, cw: Float, prop: String, lo: Float, hi: Float, about: String) =
            drawMinMaxBoundLabel(session, text, isMacroLearning, x, y, cw, paramKey, modulatorIndex, modulatorId, prop, lo, hi, about = about) { bind(prop, lo, hi) }

        if (!effectiveIsRandomizable) {
            val labelY = startY - 14f
            caption("Min", textBoxesStartX, labelY, boxWidth, "lfoMin", minLimit, maxLimit, "Where the swing bottoms out.")
            caption("Max", textBoxesStartX + boxWidth + boxSpacing, labelY, boxWidth, "lfoMax", minLimit, maxLimit, "Where the swing tops out.")
            if (paramKey != null) caption("Base", sliderStartX, labelY, 40f, "dcOffset", minLimit, maxLimit,
                if (unipolar) "Where the range sits with no signal (its bottom). Moving Base slides the whole range." else "The centre of the range. Moving Base slides the whole range.")
            if (paramKey != null) caption("Amount", sliderStartX + 48f, labelY, 56f, "depth", 0f, amountLimit,
                if (unipolar) "How far the range reaches up from Base." else "How far the range reaches either side of Base.")

            val minLock = minMaxLock(paramKey, modulatorId, "lfoMin") ?: minMaxLock(paramKey, modulatorId, "dcOffset")
            val maxLock = minMaxLock(paramKey, modulatorId, "lfoMax") ?: minMaxLock(paramKey, modulatorId, "depth")
            val setRange = { lo: Float, hi: Float -> onReplace(mod.withLfoRange(lo, hi, unipolar)) }
            drawTextInput(session, "${idPrefix}_min", curMin, minLimit, maxLimit, textBoxesStartX, startY, boxWidth, null, { setRange(it, maxOf(it, curMax)) }, formatValue, macroBindingInfo = minLock, displayScale = displayScale, tourKey = "range_${label}_min")
            drawTextInput(session, "${idPrefix}_max", curMax, minLimit, maxLimit, textBoxesStartX + boxWidth + boxSpacing, startY, boxWidth, null, { setRange(minOf(it, curMin), it) }, formatValue, macroBindingInfo = maxLock, displayScale = displayScale, tourKey = "range_${label}_max")
            renderInternalDualSlider(idPrefix + label + "_single", curMin, curMax, minLimit, maxLimit, sliderStartX, startY, lineWidth, themeColor, setRange, locked = minLock ?: maxLock, slidable = true)
        } else {
            val rowLabelH = 14f
            val row2Y = startY + buttonSize + 4f + rowLabelH
            val ends = mod.rollSpace == llm.slop.liquidlsd.parameters.RollSpace.ENDS

            // Roll-space switch under the label, in the second row's label column.
            val switchW = labelColW - 6f
            ImGui.setCursorScreenPos(startX, row2Y)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                if (ButtonChrome.button(if (ends) "Ends##roll_space" else "Base·Amt##roll_space", switchW, buttonSize)) {
                    onReplace(mod.withRollSpace(if (ends) llm.slop.liquidlsd.parameters.RollSpace.BASE_AMOUNT else llm.slop.liquidlsd.parameters.RollSpace.ENDS, unipolar))
                }
            }
            itemTooltip(if (ends)
                "Rolling the ends: Min and Max each land somewhere in their own row.\nClick to roll Base and Amount instead (pin Base to keep the range centred while its size varies)."
            else
                "Rolling Base and Amount: pin Base to keep the range where it is while its size varies.\nClick to roll the Min and Max ends instead.")

            data class Row(
                val name: String, val prop: String, val lo: Float, val hi: Float, val limitLo: Float, val limitHi: Float,
                val rolls: Boolean, val value: Float,
                val setRange: (Float, Float) -> Unit, val setValue: (Float) -> Unit, val togglePin: () -> Unit
            )
            val rows = if (ends) listOf(
                Row("Min", "lfoMinRoll", mod.lfoMinRollMin, mod.lfoMinRollMax, minLimit, maxLimit, mod.randomizeLfoMin, curMin,
                    { a, b -> onReplace(mod.copy(lfoMinRollMin = a, lfoMinRollMax = b)) },
                    { v -> onReplace(mod.withLfoRange(v, curMax, unipolar)) },
                    { onReplace(mod.copy(randomizeLfoMin = !mod.randomizeLfoMin).let { if (it.randomizeLfoMin && it.lfoMinRollMin == it.lfoMinRollMax) it.copy(lfoMinRollMin = (curMin - 0.1f).coerceAtLeast(minLimit), lfoMinRollMax = (curMin + 0.1f).coerceAtMost(maxLimit)) else it }) }),
                Row("Max", "lfoMaxRoll", mod.lfoMaxRollMin, mod.lfoMaxRollMax, minLimit, maxLimit, mod.randomizeLfoMax, curMax,
                    { a, b -> onReplace(mod.copy(lfoMaxRollMin = a, lfoMaxRollMax = b)) },
                    { v -> onReplace(mod.withLfoRange(curMin, v, unipolar)) },
                    { onReplace(mod.copy(randomizeLfoMax = !mod.randomizeLfoMax).let { if (it.randomizeLfoMax && it.lfoMaxRollMin == it.lfoMaxRollMax) it.copy(lfoMaxRollMin = (curMax - 0.1f).coerceAtLeast(minLimit), lfoMaxRollMax = (curMax + 0.1f).coerceAtMost(maxLimit)) else it }) })
            ) else listOf(
                Row("Base", "dcOffset", mod.dcOffsetMin, mod.dcOffsetMax, minLimit, maxLimit, mod.randomizeDcOffset, mod.dcOffset,
                    { a, b -> onReplace(mod.copy(dcOffsetMin = a, dcOffsetMax = b)) },
                    { v -> onReplace(mod.copy(dcOffset = v)) },
                    { onReplace(mod.copy(randomizeDcOffset = !mod.randomizeDcOffset).let { if (it.randomizeDcOffset && it.dcOffsetMin == it.dcOffsetMax) it.copy(dcOffsetMin = (mod.dcOffset - 0.1f).coerceAtLeast(minLimit), dcOffsetMax = (mod.dcOffset + 0.1f).coerceAtMost(maxLimit)) else it }) }),
                Row("Amt", "depth", mod.depthMin, mod.depthMax, 0f, amountLimit, mod.randomizeDepth, mod.depth,
                    { a, b -> onReplace(mod.copy(depthMin = a, depthMax = b)) },
                    { v -> onReplace(mod.copy(depth = v)) },
                    { onReplace(mod.copy(randomizeDepth = !mod.randomizeDepth).let { if (it.randomizeDepth && it.depthMin == it.depthMax) it.copy(depthMin = (mod.depth - 0.1f).coerceAtLeast(0f), depthMax = (mod.depth + 0.1f).coerceAtMost(amountLimit)) else it }) })
            )

            val rowTrackX = sliderStartX + buttonSize + 10f
            val rowTrackW = (lineEndX - rowTrackX).coerceAtLeast(1f)
            rows.forEachIndexed { i, row ->
                val y = if (i == 0) startY else row2Y
                // The pin sits at the start of the row's track, so the label column keeps its width.
                ImGui.setCursorScreenPos(sliderStartX, y)
                if (!row.rolls) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, TangoPalette.u32(TangoPalette.SYNC.normal, 1f))
                session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                    if (ButtonChrome.button("${if (row.rolls) Icons.UNLOCK else Icons.LOCK}##pin_${row.prop}", buttonSize, buttonSize)) row.togglePin()
                }
                if (!row.rolls) ImGui.popStyleColor()
                itemTooltip(if (row.rolls) "${row.name} rolls within this row. Click to pin it at its current value."
                    else "${row.name} is pinned: rolls leave it alone and this row edits its value. Click to roll it.")

                val labelY = y - 14f
                val (loProp, hiProp) = when (row.prop) {
                    "dcOffset" -> "dcOffsetMin" to "dcOffsetMax"
                    "depth" -> "depthMin" to "depthMax"
                    else -> "${row.prop}Min" to "${row.prop}Max"
                }
                if (row.rolls) {
                    caption(row.name, textBoxesStartX, labelY, boxWidth, loProp, row.limitLo, row.limitHi, "The lowest a roll can put ${row.name}.")
                    caption("to", textBoxesStartX + boxWidth + boxSpacing, labelY, boxWidth, hiProp, row.limitLo, row.limitHi, "The highest a roll can put ${row.name}.")
                    val loLock = minMaxLock(paramKey, modulatorId, loProp)
                    val hiLock = minMaxLock(paramKey, modulatorId, hiProp)
                    drawTextInput(session, "${idPrefix}_${row.prop}_r_lo", row.lo, row.limitLo, row.limitHi, textBoxesStartX, y, boxWidth, null, { row.setRange(it, maxOf(it, row.hi)) }, formatValue, macroBindingInfo = loLock, displayScale = displayScale)
                    drawTextInput(session, "${idPrefix}_${row.prop}_r_hi", row.hi, row.limitLo, row.limitHi, textBoxesStartX + boxWidth + boxSpacing, y, boxWidth, null, { row.setRange(minOf(it, row.lo), it) }, formatValue, macroBindingInfo = hiLock, displayScale = displayScale)
                    renderInternalDualSlider(idPrefix + label + "_" + row.prop + "_r", row.lo, row.hi, row.limitLo, row.limitHi, rowTrackX, y, rowTrackW, themeColor, row.setRange, locked = loLock ?: hiLock, slidable = true)
                } else {
                    val valueProp = when (row.prop) { "lfoMinRoll" -> "lfoMin"; "lfoMaxRoll" -> "lfoMax"; else -> row.prop }
                    caption(row.name, textBoxesStartX, labelY, boxWidth, valueProp, row.limitLo, row.limitHi, "Pinned: rolls leave ${row.name} at this value.")
                    val lock = minMaxLock(paramKey, modulatorId, valueProp)
                    drawTextInput(session, "${idPrefix}_${row.prop}_pin", row.value, row.limitLo, row.limitHi, textBoxesStartX, y, boxWidth, null, row.setValue, formatValue, macroBindingInfo = lock, displayScale = displayScale)
                    val dim = TangoPalette.u32(TangoPalette.SYNC.normal, 0.6f)
                    renderInternalDualSlider(idPrefix + label + "_" + row.prop + "_pin", row.value, row.value, row.limitLo, row.limitHi, rowTrackX, y, rowTrackW, dim,
                        { a, _ -> row.setValue(a) }, locked = lock, singleValue = true)
                }
            }

            ImGui.setCursorPosY(ImGui.getCursorPosY() + buttonSize + 4f)
        }

        ImGui.popID()
        ImGui.spacing()
    }

    private fun drawMinMaxBoundLabel(
        session: llm.slop.liquidlsd.SessionContext,
        text: String,
        isMacroLearning: Boolean,
        x: Float,
        y: Float,
        w: Float,
        paramKey: String? = null,
        modulatorIndex: Int? = null,
        modulatorId: String? = null,
        propertyName: String? = null,
        minLimit: Float = 0f,
        maxLimit: Float = 1f,
        macroKey: String? = paramKey,
        macroModId: String? = modulatorId,
        macroProp: String? = propertyName,
        about: String? = null,
        onBind: () -> Unit
    ) {
        val captionHeight = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        ImGui.setCursorScreenPos(x, y)
        ImGui.invisibleButton("##bound_label_$text", w, captionHeight)
        val isHovered = ImGui.isItemHovered()
        if (isMacroLearning && ImGui.isItemClicked(0)) {
            onBind()
        }
        val macroInfo = if (macroKey != null && macroProp != null) {
            llm.slop.liquidlsd.macro.MacroEngine.findPrimaryBindingInfo(null, macroKey, macroModId, macroProp)
        } else null
        if (macroInfo != null && !isMacroLearning && ImGui.isItemClicked(0)) {
            llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId = macroInfo.control.id
            ImGui.openPopup(MacroBindingEditor.popupIdFor(macroProp!!))
        }

        val targetPath = if (paramKey != null && propertyName != null && modulatorId != null) {
            "$paramKey:mod/$modulatorId/$propertyName"
        } else null
        val isOscLearningThis = targetPath != null && llm.slop.liquidlsd.osc.OscLearnState.isTargetLearning(targetPath)
        val isMidiLearningThis = targetPath != null && session.parametersState.isMidiTargetLearning(targetPath)

        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("bnd_ctx_$text")) {
            pushOpenDropdownFont()
            if (targetPath != null) {
                if (isOscLearningThis) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                        llm.slop.liquidlsd.osc.OscLearnState.cancelLearn()
                    }
                } else {
                    if (ImGui.menuItem(if (macroInfo != null) "${Icons.ACTIVITY} OSC Learn (locked by ${macroInfo.badgeLabel})" else "${Icons.ACTIVITY} OSC Learn ($text)", "", false, macroInfo == null)) {
                        llm.slop.liquidlsd.osc.OscLearnState.startLearn(
                            parameterPath = targetPath,
                            minVal = minLimit,
                            maxVal = maxLimit,
                            displayLabel = "$paramKey ($text)"
                        )
                    }
                }
                val oscAddress = llm.slop.liquidlsd.osc.OscMappingManager.getAddressForParameter(targetPath)
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, oscAddress != null)) {
                    llm.slop.liquidlsd.osc.OscMappingManager.removeMapping(oscAddress!!)
                    llm.slop.liquidlsd.osc.OscMappingManager.saveActiveProfile()
                }

                if (isMidiLearningThis) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                        session.parametersState.midiLearnTarget = null
                    }
                } else {
                    if (ImGui.menuItem(if (macroInfo != null) "${Icons.SETTINGS} MIDI Learn (locked by ${macroInfo.badgeLabel})" else "${Icons.SETTINGS} MIDI Learn ($text)", "", false, macroInfo == null)) {
                        session.parametersState.startMidiLearn(
                            MidiLearnTarget.ModulatorProperty(
                                fullPath = targetPath,
                                label = "$paramKey ($text)",
                                min = minLimit,
                                max = maxLimit
                            )
                        )
                    }
                }
                val hasMidiMap = session.midiMappingManager.hasMapping(targetPath)
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping", null, false, hasMidiMap)) {
                    session.midiMappingManager.removeMapping(targetPath)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        if (macroInfo != null) {
            MacroBindingEditor.drawPopup(session, MacroBindingEditor.popupIdFor(macroProp!!), macroInfo, minLimit, maxLimit)
        }

        val col = if ((isMacroLearning && isHovered) || macroInfo != null) floatArrayOf(TangoPalette.SYNC.normal[0], TangoPalette.SYNC.normal[1], TangoPalette.SYNC.normal[2], 1.0f) else floatArrayOf(0.6f, 0.6f, 0.6f, 0.7f)
        ImGui.setCursorScreenPos(x, y)
        session.uiTheme.captionColored(col[0], col[1], col[2], col[3], text)
        if (isMacroLearning) {
            val dl = ImGui.getWindowDrawList()
            val pulseAlpha = (kotlin.math.sin(llm.slop.liquidlsd.utils.TimeSource.getEpochMillis() * 0.008) * 0.35 + 0.65).toFloat()
            val borderCol = TangoPalette.u32(TangoPalette.SYNC.bright, pulseAlpha)
            dl.addRect(x - 2f, y - 2f, x + w + 2f, y + captionHeight + 2f, borderCol, 3f, 0, 1.5f)
        } else if (isOscLearningThis) {
            TangoPalette.drawOscLearnPulseBorder(ImGui.getWindowDrawList(), x - 2f, y - 2f, x + w + 2f, y + captionHeight + 2f, 3f, 2.0f)
        } else if (isMidiLearningThis) {
            val dl = ImGui.getWindowDrawList()
            val pulseAlpha = (kotlin.math.sin(llm.slop.liquidlsd.utils.TimeSource.getEpochMillis() * 0.008) * 0.35 + 0.65).toFloat()
            val borderCol = TangoPalette.u32(TangoPalette.SYNC.normal, pulseAlpha)
            dl.addRect(x - 2f, y - 2f, x + w + 2f, y + captionHeight + 2f, borderCol, 3f, 0, 2.0f)
        }
        if (isHovered) {
            labelTooltip(text, null, if (isMacroLearning) null else macroInfo, isMacroLearning, isOscLearningThis, isMidiLearningThis,
                learnable = targetPath != null, resettable = false, about = about)
        }
    }

    private fun renderInternalDualSlider(
        id: String,
        curMin: Float,
        curMax: Float,
        minLimit: Float,
        maxLimit: Float,
        lineStartX: Float,
        startY: Float,
        lineWidth: Float,
        themeColor: Int,
        onChanged: (Float, Float) -> Unit,
        locked: llm.slop.liquidlsd.macro.MacroBindingInfo? = null,
        slidable: Boolean = false,
        /** One value drawn as a collapsed range: dragging and the wheel move it, reported as (v, v). */
        singleValue: Boolean = false
    ) {
        val buttonSize = ImGui.getFrameHeight()
        val centerY = startY + buttonSize / 2f
        val lineEndX = lineStartX + lineWidth
        val dl = ImGui.getWindowDrawList()
        val io = ImGui.getIO()
        val mouseX = io.mousePos.x

        val toPct = { v: Float -> (v - minLimit) / (maxLimit - minLimit) }
        val toVal = { p: Float -> minLimit + p * (maxLimit - minLimit) }

        // A Base/Amount driven from MIDI can push the ends past the track: draw them at the track ends.
        val minPct = toPct(curMin).coerceIn(0f, 1f)
        val maxPct = toPct(curMax).coerceIn(0f, 1f)
        val minHandleX = lineStartX + minPct * lineWidth
        val maxHandleX = lineStartX + maxPct * lineWidth

        val trackPadX = 6f
        ImGui.setCursorScreenPos(lineStartX - trackPadX, startY)
        ImGui.invisibleButton("##track_$id", lineWidth + trackPadX * 2f, buttonSize)
        val isTrackHovered = ImGui.isItemHovered()
        val isTrackActive = ImGui.isItemActive()
        val isTrackActivated = ImGui.isItemActivated() && locked == null

        val loHandleX = minOf(minHandleX, maxHandleX)
        val hiHandleX = maxOf(minHandleX, maxHandleX)
        val overBar = slidable && hiHandleX - loHandleX > 16f && mouseX > loHandleX + 6f && mouseX < hiHandleX - 6f
        if (isTrackHovered && locked == null && (overBar || (slidable && io.keyShift))) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeEW)
        }

        if (isTrackActivated && (overBar || (slidable && io.keyShift))) {
            activeSliderLabel = id
            clickMouseX = mouseX
            draggingBoth = true; draggingMin = false; draggingMax = false
            slideStartMin = curMin; slideStartMax = curMax
        } else if (isTrackActivated) {
            activeSliderLabel = id
            clickMouseX = mouseX
            draggingBoth = false
            val isOverlapping = kotlin.math.abs(minHandleX - maxHandleX) < 4f
            if (isOverlapping) {
                if (mouseX < minHandleX - 5f) { draggingMin = true; draggingMax = false }
                else if (mouseX > maxHandleX + 5f) { draggingMax = true; draggingMin = false }
            } else {
                val dMin = kotlin.math.abs(mouseX - minHandleX)
                val dMax = kotlin.math.abs(mouseX - maxHandleX)
                if (dMin < dMax) { draggingMin = true; draggingMax = false }
                else { draggingMax = true; draggingMin = false }
            }
        }

        if (locked == null && isTrackActive && activeSliderLabel == id) {
            val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
            val rawVal = toVal(pct)
            if (singleValue) {
                onChanged(rawVal, rawVal)
            } else if (draggingBoth) {
                // Slide with the span locked; stop where either end meets the track end.
                val lo = minOf(slideStartMin, slideStartMax)
                val hi = maxOf(slideStartMin, slideStartMax)
                val delta = ((mouseX - clickMouseX) / lineWidth * (maxLimit - minLimit))
                    .coerceIn(minOf(0f, minLimit - lo), maxOf(0f, maxLimit - hi))
                onChanged(slideStartMin + delta, slideStartMax + delta)
            } else if (!draggingMin && !draggingMax) {
                if (mouseX > clickMouseX + 2f) { draggingMax = true; onChanged(curMin, rawVal.coerceIn(curMin, maxLimit)) }
                else if (mouseX < clickMouseX - 2f) { draggingMin = true; onChanged(rawVal.coerceIn(minLimit, curMax), curMax) }
            } else if (draggingMin) {
                onChanged(rawVal.coerceIn(minLimit, curMax), curMax)
            } else if (draggingMax) {
                onChanged(curMin, rawVal.coerceIn(curMin, maxLimit))
            }
        } else if (!isTrackActive && activeSliderLabel == id) {
            draggingMin = false; draggingMax = false; draggingBoth = false; activeSliderLabel = null
        }

        // Draw
        val lineCol = TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 1.0f)
        dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
        dl.addLine(minHandleX, centerY, maxHandleX, centerY, themeColor, 3f)

        val handleW = 6f
        val handleH = 16f
        val handleBgCol = TangoPalette.u32(TangoPalette.NEUTRAL_DARK.light, 1.0f)
        val handleBorderCol = TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 1.0f)

        dl.addRectFilled(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
        dl.addRect(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
        dl.addRectFilled(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
        dl.addRect(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

        if (isTrackHovered) {
            isAnySliderHovered = true
            val borderCol = if (isTrackActive) TangoPalette.u32(TangoPalette.SYNC.normal, 1.0f)
                            else TangoPalette.u32(TangoPalette.ALERT.normal, 0.9f)
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, borderCol, 4f, 0, 1.5f)
            
            if (locked != null) {
                showTooltip("Controlled by ${locked.controlName} [${locked.badgeLabel}].\nManual edits are disabled while macro is active.")
            } else if (io.mouseWheel != 0f) {
                val delta = io.mouseWheel * (if (io.keyCtrl && io.keyShift) 0.1f else if (io.keyShift) 0.01f else 0.001f)
                val dMin = kotlin.math.abs(mouseX - minHandleX)
                val dMax = kotlin.math.abs(mouseX - maxHandleX)
                if (singleValue) { val v = (curMin + delta).coerceIn(minLimit, maxLimit); onChanged(v, v) }
                else if (dMin < dMax) onChanged((curMin + delta).coerceIn(minLimit, curMax), curMax)
                else onChanged(curMin, (curMax + delta).coerceIn(curMin, maxLimit))
            }
            if (locked == null && !singleValue && ImGui.isMouseClicked(2)) onChanged(minLimit, maxLimit)
        }
    }

    fun drawCompactSlider(
        session: llm.slop.liquidlsd.SessionContext,
        label: String,
        currentValue: Float,
        minLimit: Float,
        maxLimit: Float,
        defaultValue: Float? = null,
        formatValue: (Float) -> String = ValueFormat.AUTO,
        idPrefix: String = "",
        themeColor: Int = TangoPalette.u32(TangoPalette.SYNC.normal, 0.9f),
        isLogarithmic: Boolean = false,
        parseValue: (String) -> Float? = DEFAULT_PARSE,
        showCurrentLabel: Boolean = true,
        customBoxWidth: Float? = null,
        onValueChanged: (Float) -> Unit,
        readOnly: Boolean = false,
        modulatorIndex: Int? = null,
        modulatorId: String? = null,
        propertyName: String? = null,
        paramKey: String? = null,
        description: String? = null
    ) {
        drawCustomRangeSlider(
            session = session,
            label = label,
            currentValue = currentValue,
            currentMin = currentValue,
            currentMax = currentValue,
            minLimit = minLimit,
            maxLimit = maxLimit,
            isRandomizable = false,
            showControls = false,
            defaultValue = defaultValue,
            formatValue = formatValue,
            onValueChanged = onValueChanged,
            idPrefix = idPrefix,
            themeColor = themeColor,
            isLogarithmic = isLogarithmic,
            parseValue = parseValue,
            showCurrentLabel = showCurrentLabel,
            customBoxWidth = customBoxWidth,
            readOnly = readOnly,
            modulatorIndex = modulatorIndex,
            modulatorId = modulatorId,
            propertyName = propertyName,
            paramKey = paramKey,
            description = description
        )
    }

    fun drawCustomRangeSlider(
        session: llm.slop.liquidlsd.SessionContext,
        label: String,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        formatValue: (Float) -> String,
        formatLabel: ((Float) -> String)? = null,
        onRangeChanged: (Float, Float) -> Unit,
        idPrefix: String = "",
        themeColor: Int = TangoPalette.u32(TangoPalette.SYNC.normal, 0.6f),
        isLogarithmic: Boolean = false,
        parseValue: (String) -> Float? = DEFAULT_PARSE,
        showCurrentLabel: Boolean = true,
        customBoxWidth: Float? = null,
        readOnly: Boolean = false,
        modulatorIndex: Int? = null,
        modulatorId: String? = null,
        propertyName: String? = null,
        paramKey: String? = null,
        description: String? = null
    ) {
        drawCustomRangeSlider(
            session = session,
            label = label,
            currentValue = currentMin,
            currentMin = currentMin,
            currentMax = currentMax,
            minLimit = minLimit,
            maxLimit = maxLimit,
            isRandomizable = true,
            showControls = false,
            formatValue = formatValue,
            formatLabel = formatLabel,
            onRangeChanged = onRangeChanged,
            idPrefix = idPrefix,
            themeColor = themeColor,
            isLogarithmic = isLogarithmic,
            parseValue = parseValue,
            showCurrentLabel = showCurrentLabel,
            customBoxWidth = customBoxWidth,
            readOnly = readOnly,
            modulatorIndex = modulatorIndex,
            modulatorId = modulatorId,
            propertyName = propertyName,
            paramKey = paramKey,
            description = description
        )
    }

    fun drawCustomRangeSlider(
        session: llm.slop.liquidlsd.SessionContext,
        label: String,
        currentValue: Float,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        isRandomizable: Boolean,
        showControls: Boolean = true,
        defaultValue: Float? = null,
        formatValue: (Float) -> String,
        formatLabel: ((Float) -> String)? = null,
        onRandomizableChanged: (Boolean) -> Unit = {},
        onRandomizeNow: () -> Unit = {},
        onRangeChanged: (Float, Float) -> Unit = { _, _ -> },
        onValueChanged: (Float) -> Unit = {},
        idPrefix: String = "",
        themeColor: Int = TangoPalette.u32(TangoPalette.SYNC.normal, 0.6f),
        isLogarithmic: Boolean = false,
        parseValue: (String) -> Float? = DEFAULT_PARSE,
        showCurrentLabel: Boolean = true,
        customBoxWidth: Float? = null,
        isRandomizeDisabled: Boolean = false,
        randomizeDisabledTooltip: String? = null,
        readOnly: Boolean = false,
        modulatorIndex: Int? = null,
        modulatorId: String? = null,
        propertyName: String? = null,
        paramKey: String? = null,
        displayScale: Float? = null,
        driveThroughKnob: Boolean = false,
        description: String? = null
    ) {
        val dispScale = displayScale ?: if (formatValue === ValueFormat.AUTO) ValueFormat.scaleFor(minLimit, maxLimit) else 1f
        val effectiveIsRandomizable = if (isRandomizeDisabled) false else ((isRandomizable && session.uiTheme.randomizationEnabled) || (!showControls && isRandomizable))
        val effectiveShowControls = showControls && session.uiTheme.randomizationEnabled

        val rowStartX = ImGui.getCursorScreenPosX()
        val rowStartY = ImGui.getCursorScreenPosY()

        ImGui.pushID(label)

        val w = ImGui.getContentRegionAvailX()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        val buttonSize = ImGui.getFrameHeight()
        val fontScale = 0.95f
        val captionHeight = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        
        val labelY = startY + 2f * fontScale
        val row2Y = labelY + captionHeight + 2.5f * fontScale
        val centerY = row2Y + buttonSize * 0.5f
        val h = (row2Y - startY) + buttonSize + 3f * fontScale
        
        // Reserve space
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        val io = ImGui.getIO()
        val mouseX = io.mousePos.x
        val mouseY = io.mousePos.y
        
        val spacing = ImGui.getStyle().itemSpacing.x
        val combinedWidth = buttonSize
        
        val labelColW = if (label.isEmpty()) 0f else (110f * fontScale)
        val textBoxesStartX = startX + labelColW + (if (label.isEmpty()) 0f else (10f * fontScale))
        
        val boxWidth = customBoxWidth ?: (44f * fontScale)
        val boxSpacing = 8f
        
        val sliderStartX = textBoxesStartX + (if (effectiveIsRandomizable) (boxWidth * 2f + boxSpacing) else boxWidth) + 15f
        val lineStartX = sliderStartX
        val lineEndX = maxOf(lineStartX + 1f, startX + w - 10f)
        val lineWidth = (lineEndX - lineStartX).coerceAtLeast(1f)
        
        val rangeSpan = maxLimit - minLimit
        val labelFormatFunc = formatLabel ?: ValueFormat.resolve(formatValue, dispScale)

        val toPct: (Float) -> Float = { v ->
            if (isLogarithmic) {
                val logMin = java.lang.Math.log10(minLimit.toDouble())
                val logMax = java.lang.Math.log10(maxLimit.toDouble())
                val logVal = java.lang.Math.log10(v.toDouble().coerceAtLeast(minLimit.toDouble()))
                ((logVal - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)
            } else {
                if (rangeSpan > 0f) (v - minLimit) / rangeSpan else 0f
            }
        }

        val toVal: (Float) -> Float = { p ->
            if (isLogarithmic) {
                val logMin = java.lang.Math.log10(minLimit.toDouble())
                val logMax = java.lang.Math.log10(maxLimit.toDouble())
                val logVal = logMin + p.toDouble() * (logMax - logMin)
                java.lang.Math.pow(10.0, logVal).toFloat().coerceIn(minLimit, maxLimit)
            } else {
                minLimit + p * rangeSpan
            }
        }

        // --- ROW 1: Labels ---
        if (effectiveIsRandomizable) {
            ImGui.setCursorScreenPos(textBoxesStartX, labelY)
            session.uiTheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Min")
            
            ImGui.setCursorScreenPos(textBoxesStartX + boxWidth + boxSpacing, labelY)
            session.uiTheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Max")
            
            if (showCurrentLabel) {
                // Add "Current" label with [value] centered above the dynamic dot on Row 1
                val curPct = toPct(currentValue)
                val curX = lineStartX + curPct * lineWidth
                val formattedVal = labelFormatFunc(currentValue)
                val labelText = "Current: $formattedVal"
                val currentTextWidth = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.calcTextSize(labelText).x }
                val minAllowedX = lineStartX
                val maxAllowedX = (lineEndX - currentTextWidth).coerceAtLeast(minAllowedX)
                val textX = (curX - currentTextWidth / 2f).coerceIn(minAllowedX, maxAllowedX)
                
                ImGui.setCursorScreenPos(textX, labelY)
                session.uiTheme.captionColored(0.8f, 0.8f, 0.8f, 0.9f, labelText)
            }
        } else {
            if (showCurrentLabel) {
                ImGui.setCursorScreenPos(textBoxesStartX, labelY)
                val formattedVal = labelFormatFunc(currentValue)
                session.uiTheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Current: $formattedVal")
            }
        }
        
        // driveThroughKnob: the caller routes edits onto the driving knob (KnobDrive), so the slider stays editable.
        val macroInfo = if (paramKey != null && !driveThroughKnob) {
            llm.slop.liquidlsd.macro.MacroEngine.lockingBindingInfo(paramKey, modulatorId, propertyName)
        } else null
        val isMacroBound = macroInfo != null

        // Draw bounding box / tint around the slider row when controlled by a Macro
        if (isMacroBound) {
            val cyanOutline = TangoPalette.u32(TangoPalette.SYNC.normal, 0.8f)
            val cyanBg = TangoPalette.u32(TangoPalette.SYNC.normal, 0.05f)
            dl.addRectFilled(startX - 4f, startY - 2f, startX + w - 4f, startY + h + 2f, cyanBg, 4f)
            dl.addRect(startX - 4f, startY - 2f, startX + w - 4f, startY + h + 2f, cyanOutline, 4f, 0, 1.5f)
        }

        // --- ROW 2: Widgets ---
        
        if (label.isNotEmpty()) {
            val labelW = if (effectiveShowControls) (labelColW - buttonSize - 4f).coerceAtLeast(10f) else labelColW
            ImGui.setCursorScreenPos(startX, row2Y)
            ImGui.invisibleButton("##label_btn_${idPrefix}_$label", labelW, buttonSize)
            val isLabelHovered = ImGui.isItemHovered()
            val isMacroBindable = propertyName != null && paramKey != null
            val isMacroLearning = isMacroBindable && llm.slop.liquidlsd.macro.MacroLearnState.isLearning()
            if (isMacroLearning && ImGui.isItemClicked(0)) {
                llm.slop.liquidlsd.macro.MacroLearnState.bindTarget(
                    bank = llm.slop.liquidlsd.macro.MacroEngine.bankForParamPath(paramKey!!),
                    targetType = llm.slop.liquidlsd.macro.MacroTargetType.MODULATOR_PROPERTY,
                    parameterId = paramKey!!,
                    modulatorId = modulatorId,
                    propertyName = propertyName!!,
                    minVal = minLimit,
                    maxVal = maxLimit
                )
            } else if (isMacroBound && ImGui.isItemClicked(0)) {
                macroInfo?.control?.id?.let { ctrlId ->
                    llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId = ctrlId
                }
                if (propertyName != null) ImGui.openPopup(MacroBindingEditor.popupIdFor(propertyName))
            }
            if (ImGui.isItemClicked(2) && !isMacroBound) {
                val resetTarget = defaultValue ?: 0.0f.coerceIn(minLimit, maxLimit)
                if (effectiveIsRandomizable) {
                    onRangeChanged(resetTarget, resetTarget)
                } else {
                    onValueChanged(resetTarget)
                }
            }

            val targetPath = if (paramKey != null) {
                if (propertyName == null) paramKey else if (modulatorId != null) "$paramKey:mod/$modulatorId/$propertyName" else null
            } else null
            val isOscLearningThis = targetPath != null && llm.slop.liquidlsd.osc.OscLearnState.isTargetLearning(targetPath)
            val isMidiLearningThis = targetPath != null && session.parametersState.isMidiTargetLearning(targetPath)

            pushOpenDropdownPadding()
            if (ImGui.beginPopupContextItem("crs_ctx_${idPrefix}_$label")) {
                pushOpenDropdownFont()
                if (targetPath != null) {
                    val propLabel = if (propertyName != null) {
                        llm.slop.liquidlsd.parameters.ModulatorPropertyAccessor.formatPropertyLabel(modulatorIndex ?: 0, propertyName)
                    } else "Base Value"
                    if (isOscLearningThis) {
                        if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                            llm.slop.liquidlsd.osc.OscLearnState.cancelLearn()
                        }
                    } else {
                        if (ImGui.menuItem(if (macroInfo != null) "${Icons.ACTIVITY} OSC Learn (locked by ${macroInfo.badgeLabel})" else "${Icons.ACTIVITY} OSC Learn ($propLabel)", "", false, macroInfo == null)) {
                            llm.slop.liquidlsd.osc.OscLearnState.startLearn(
                                parameterPath = targetPath,
                                minVal = minLimit,
                                maxVal = maxLimit,
                                displayLabel = "$paramKey ($propLabel)"
                            )
                        }
                    }
                    val oscAddress = llm.slop.liquidlsd.osc.OscMappingManager.getAddressForParameter(targetPath)
                    if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, oscAddress != null)) {
                        llm.slop.liquidlsd.osc.OscMappingManager.removeMapping(oscAddress!!)
                        llm.slop.liquidlsd.osc.OscMappingManager.saveActiveProfile()
                    }

                    if (isMidiLearningThis) {
                        if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                            session.parametersState.midiLearnTarget = null
                        }
                    } else {
                        if (ImGui.menuItem(if (macroInfo != null) "${Icons.SETTINGS} MIDI Learn (locked by ${macroInfo.badgeLabel})" else "${Icons.SETTINGS} MIDI Learn ($propLabel)", "", false, macroInfo == null)) {
                            val midiTarget = if (propertyName != null) {
                                MidiLearnTarget.ModulatorProperty(
                                    fullPath = targetPath,
                                    label = "$paramKey ($propLabel)",
                                    min = minLimit,
                                    max = maxLimit
                                )
                            } else {
                                MidiLearnTarget.BaseValueSlider(
                                    paramKey = targetPath,
                                    label = "$paramKey ($propLabel)",
                                    min = minLimit,
                                    max = maxLimit
                                )
                            }
                            session.parametersState.startMidiLearn(midiTarget)
                        }
                    }
                    val hasMidiMap = session.midiMappingManager.hasMapping(targetPath)
                    if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping", null, false, hasMidiMap)) {
                        session.midiMappingManager.removeMapping(targetPath)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()
            if (isMacroBound && propertyName != null) {
                MacroBindingEditor.drawPopup(session, MacroBindingEditor.popupIdFor(propertyName), macroInfo!!, minLimit, maxLimit, isLogarithmic)
            }

            if (isLabelHovered) {
                labelTooltip(label, defaultValue?.let { labelFormatFunc(it) }, if (isMacroBound) macroInfo else null, isMacroLearning,
                    isOscLearningThis, isMidiLearningThis, learnable = targetPath != null, resettable = true, about = description)
            }
            if (isMacroLearning) {
                val pulseAlpha = (kotlin.math.sin(llm.slop.liquidlsd.utils.TimeSource.getEpochMillis() * 0.008) * 0.35 + 0.65).toFloat()
                val borderCol = TangoPalette.u32(TangoPalette.SYNC.bright, pulseAlpha)
                dl.addRect(startX - 2f, row2Y - 2f, startX + labelW + 2f, row2Y + buttonSize + 2f, borderCol, 3f, 0, 1.5f)
            } else if (isOscLearningThis) {
                TangoPalette.drawOscLearnPulseBorder(dl, startX - 2f, row2Y - 2f, startX + labelW + 2f, row2Y + buttonSize + 2f, 3f, 2.0f)
            } else if (isMidiLearningThis) {
                val pulseAlpha = (kotlin.math.sin(llm.slop.liquidlsd.utils.TimeSource.getEpochMillis() * 0.008) * 0.35 + 0.65).toFloat()
                val borderCol = TangoPalette.u32(TangoPalette.SYNC.normal, pulseAlpha)
                dl.addRect(startX - 2f, row2Y - 2f, startX + labelW + 2f, row2Y + buttonSize + 2f, borderCol, 3f, 0, 2.0f)
            }

            // Render name of variable (and macro badge if bound)
            val textHeight = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() }
            val textY = row2Y + (buttonSize - textHeight) / 2f
            ImGui.setCursorScreenPos(startX, textY)
            if (isMacroBound) {
                val badge = "[${macroInfo!!.badgeLabel}]"
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                    ImGui.textColored(TangoPalette.SYNC.normal[0], TangoPalette.SYNC.normal[1], TangoPalette.SYNC.normal[2], 1.0f, badge)
                }
                ImGui.sameLine(0f, 4f)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, TangoPalette.u32(TangoPalette.SYNC.normal, 1.0f))
                session.uiTheme.body(label)
                ImGui.popStyleColor()
            } else {
                session.uiTheme.body(label)
            }
        }
        
        if (effectiveShowControls) {
            val randBtnX = startX + labelColW - buttonSize
            ImGui.setCursorScreenPos(randBtnX, row2Y)
            
            if (isRandomizeDisabled) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, TangoPalette.TEXT_FAINT.u32())
                session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                    ButtonChrome.button("${Icons.DICES}##rand_$label", buttonSize, buttonSize)
                }
                ImGui.popStyleColor()
                itemTooltip(randomizeDisabledTooltip ?: llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP)
            } else {
                if (!effectiveIsRandomizable) {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, TangoPalette.TEXT_DIM.u32())
                }
                session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                    if (ButtonChrome.button("${Icons.DICES}##rand_$label", buttonSize, buttonSize)) {
                        onRandomizableChanged(!effectiveIsRandomizable)
                    }
                }
                if (!effectiveIsRandomizable) {
                    ImGui.popStyleColor()
                }
                
                if (ImGui.isItemClicked(1)) { // Right click
                    if (!effectiveIsRandomizable) {
                        onRandomizableChanged(true)
                    }
                    onRandomizeNow()
                }
                itemTooltip(if (effectiveIsRandomizable) RANDOM_RANGE_ON_TIP else RANDOM_RANGE_OFF_TIP)
            }
        }
        
        // 3. Text inputs
        if (effectiveIsRandomizable) {
            drawTextInput(
                session = session,
                key = "${idPrefix}_${label}_min",
                tourKey = "slider_${label}_min",
                currentValue = currentMin,
                minLimit = minLimit,
                maxLimit = maxLimit,
                posX = textBoxesStartX,
                posY = row2Y,
                width = boxWidth,
                defaultValue = defaultValue,
                onChanged = { nextMin ->
                    onRangeChanged(nextMin, maxOf(nextMin, currentMax))
                },
                formatValue = formatValue,
                parseValue = parseValue,
                macroBindingInfo = macroInfo,
                displayScale = dispScale
            )
            drawTextInput(
                session = session,
                key = "${idPrefix}_${label}_max",
                tourKey = "slider_${label}_max",
                currentValue = currentMax,
                minLimit = minLimit,
                maxLimit = maxLimit,
                posX = textBoxesStartX + boxWidth + boxSpacing,
                posY = row2Y,
                width = boxWidth,
                defaultValue = defaultValue,
                onChanged = { nextMax ->
                    onRangeChanged(minOf(nextMax, currentMin), nextMax)
                },
                formatValue = formatValue,
                parseValue = parseValue,
                macroBindingInfo = macroInfo,
                displayScale = dispScale
            )
        } else {
            drawTextInput(
                session = session,
                key = "${idPrefix}_${label}_value",
                tourKey = "slider_${label}_value",
                currentValue = currentValue,
                minLimit = minLimit,
                maxLimit = maxLimit,
                posX = textBoxesStartX,
                posY = row2Y,
                width = boxWidth,
                defaultValue = defaultValue,
                onChanged = { newVal ->
                    onValueChanged(newVal)
                },
                formatValue = formatValue,
                parseValue = parseValue,
                readOnly = readOnly,
                macroBindingInfo = macroInfo,
                displayScale = dispScale
            )
        }
        
        // --- Dragging & Slider Render ---
        val effectiveThemeColor = if (isMacroBound) {
            TangoPalette.u32(TangoPalette.SYNC.normal, 0.95f) // Sync cyan track when bound
        } else {
            themeColor
        }

        val trackPadX = 6f
        val trackW = (lineWidth + trackPadX * 2f).coerceAtLeast(1f)
        val trackH = buttonSize
        ImGui.setCursorScreenPos(lineStartX - trackPadX, row2Y)
        ImGui.invisibleButton("##track_${idPrefix}_$label", trackW, trackH)
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "slider_${label}_track" }
        val isTrackHovered = ImGui.isItemHovered()
        val isTrackItemActive = ImGui.isItemActive()
        val isTrackActivated = ImGui.isItemActivated()
        val isTrackMiddleClicked = ImGui.isItemClicked(2)

        val mousePressed = (isTrackActivated || (isTrackHovered && ImGui.isMouseClicked(0))) && !isMacroBound
        val mouseDown = isTrackItemActive && !isMacroBound
        
        if (effectiveIsRandomizable) {
            val minPct = toPct(currentMin)
            val maxPct = toPct(currentMax)
            val minHandleX = lineStartX + minPct * lineWidth
            val maxHandleX = lineStartX + maxPct * lineWidth
            
            if (mousePressed) {
                activeSliderLabel = idPrefix + label
                val isOverlapping = kotlin.math.abs(minHandleX - maxHandleX) < 4f
                if (isOverlapping) {
                    if (mouseX < minHandleX - 5f) {
                        draggingMin = true
                        draggingMax = false
                    } else if (mouseX > maxHandleX + 5f) {
                        draggingMax = true
                        draggingMin = false
                    } else {
                        draggingMin = false
                        draggingMax = false
                        clickMouseX = mouseX
                    }
                } else {
                    val distToMin = kotlin.math.abs(mouseX - minHandleX)
                    val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                    if (distToMin < distToMax) {
                        draggingMin = true
                        draggingMax = false
                    } else {
                        draggingMax = true
                        draggingMin = false
                    }
                }
            }
            
            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = toVal(pct)
                if (!draggingMin && !draggingMax) {
                    val dragThreshold = 2f
                    if (mouseX > clickMouseX + dragThreshold) {
                        draggingMax = true
                        val nextMax = rawVal.coerceIn(currentMin, maxLimit)
                        onRangeChanged(currentMin, nextMax)
                    } else if (mouseX < clickMouseX - dragThreshold) {
                        draggingMin = true
                        val nextMin = rawVal.coerceIn(minLimit, currentMax)
                        onRangeChanged(nextMin, currentMax)
                    }
                } else if (draggingMin) {
                    val nextMin = rawVal.coerceIn(minLimit, currentMax)
                    onRangeChanged(nextMin, currentMax)
                } else if (draggingMax) {
                    val nextMax = rawVal.coerceIn(currentMin, maxLimit)
                    onRangeChanged(currentMin, nextMax)
                }
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false
                draggingMax = false
                activeSliderLabel = null
            }
            
            val isLight = session.uiTheme.theme == UITheme.Theme.ORANGE_SUNSHINE

            // Draw tracks
            val lineCol = if (isLight) ImGui.colorConvertFloat4ToU32(0.80f, 0.82f, 0.85f, 1.0f) else TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 1.0f)
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(minHandleX, centerY, maxHandleX, centerY, effectiveThemeColor, 3f) // Active track

            // Draw handles
            val handleW = 6f
            val handleH = 16f
            val handleBgCol = if (isLight) TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 1.0f) else TangoPalette.u32(TangoPalette.NEUTRAL_LIGHT.normal, 1.0f)
            val handleBorderCol = if (isLight) ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f) else TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 1.0f)

            dl.addRectFilled(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
            dl.addRectFilled(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

            // Draw dynamic current value indicator (Alert amber dot, or Sync cyan if bound)
            val curPct = toPct(currentValue)
            val curX = lineStartX + curPct * lineWidth
            val dotY = centerY
            val dotR = 4f
            val curDotCol = if (isMacroBound) TangoPalette.u32(TangoPalette.SYNC.normal, 1.0f) else TangoPalette.u32(TangoPalette.ALERT.normal, 1.0f)
            dl.addCircleFilled(curX, dotY, dotR, curDotCol)
            dl.addCircle(curX, dotY, dotR + 0.5f, TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 1.0f), 12, 1.0f)
        } else {
            val valPct = toPct(currentValue)
            val valHandleX = lineStartX + valPct * lineWidth
            
            if (mousePressed) {
                activeSliderLabel = idPrefix + label
                draggingMin = true
                draggingMax = false
            }
            
            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = toVal(pct)
                onValueChanged(rawVal)
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false
                draggingMax = false
                activeSliderLabel = null
            }
            
            val isLight = session.uiTheme.theme == UITheme.Theme.ORANGE_SUNSHINE

            // Draw tracks
            val lineCol = if (isLight) ImGui.colorConvertFloat4ToU32(0.80f, 0.82f, 0.85f, 1.0f) else TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 1.0f)
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(lineStartX, centerY, valHandleX, centerY, effectiveThemeColor, 3f) // Active track

            // Draw single handle
            val handleW = 6f
            val handleH = 16f
            val handleBgCol = when {
                isMacroBound -> TangoPalette.u32(TangoPalette.SYNC.normal, 1.0f)
                isLight -> TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 1.0f)
                else -> TangoPalette.u32(TangoPalette.NEUTRAL_DARK.light, 1.0f)
            }
            val handleBorderCol = if (isLight) ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f) else TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 1.0f)
            
            dl.addRectFilled(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
        }
        
        // Hover-zone handling & tooltips for custom range slider track/handles
        val isTrackActive = isTrackItemActive || activeSliderLabel == (idPrefix + label)
        if (isTrackHovered || isTrackActive) {
            isAnySliderHovered = true
            val borderCol = if (isMacroBound) {
                TangoPalette.u32(TangoPalette.SYNC.normal, 0.9f)
            } else if (isTrackActive) {
                TangoPalette.u32(TangoPalette.SYNC.normal, 1.0f) // Sync cyan while active dragging
            } else {
                TangoPalette.u32(TangoPalette.ALERT.normal, 0.9f) // Alert amber on hover target
            }
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, borderCol, 4f, 0, 1.5f)
            
            if (!isMacroBound) {
                val io = ImGui.getIO()
                if (io.mouseWheel != 0f) {
                    val shift = io.keyShift
                    val ctrl = io.keyCtrl
                    val deltaStep = if (ctrl && shift) 0.1f else if (shift) 0.01f else 0.001f
                    val delta = io.mouseWheel * deltaStep
                    if (effectiveIsRandomizable) {
                        val minPct = toPct(currentMin)
                        val maxPct = toPct(currentMax)
                        val minHandleX = lineStartX + minPct * lineWidth
                        val maxHandleX = lineStartX + maxPct * lineWidth
                        val distToMin = kotlin.math.abs(mouseX - minHandleX)
                        val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                        if (distToMin < distToMax) {
                            val nextMin = (currentMin + delta).coerceIn(minLimit, currentMax)
                            onRangeChanged(nextMin, currentMax)
                        } else {
                            val nextMax = (currentMax + delta).coerceIn(currentMin, maxLimit)
                            onRangeChanged(currentMin, nextMax)
                        }
                    } else {
                        val nextVal = (currentValue + delta).coerceIn(minLimit, maxLimit)
                        onValueChanged(nextVal)
                    }
                    io.mouseWheel = 0f // Consume mouse wheel event so parent panel does not scroll
                }
                if (ImGui.isMouseClicked(2) || isTrackMiddleClicked) { // Middle click reset
                    val resetTarget = defaultValue ?: 0.0f.coerceIn(minLimit, maxLimit)
                    if (effectiveIsRandomizable) {
                        onRangeChanged(resetTarget, resetTarget)
                    } else {
                        onValueChanged(resetTarget)
                    }
                }
            }

            val spot = if (!isMacroBound && effectiveIsRandomizable) {
                val distToMin = kotlin.math.abs(mouseX - (lineStartX + toPct(currentMin) * lineWidth))
                val distToMax = kotlin.math.abs(mouseX - (lineStartX + toPct(currentMax) * lineWidth))
                val distToCur = kotlin.math.abs(mouseX - (lineStartX + toPct(currentValue) * lineWidth))
                when {
                    distToMin < 8f -> TrackSpot.MIN
                    distToMax < 8f -> TrackSpot.MAX
                    distToCur < 6f -> TrackSpot.CURRENT
                    else -> TrackSpot.NONE
                }
            } else TrackSpot.NONE
            trackTooltip(label, "value", if (isMacroBound) macroInfo else null, effectiveIsRandomizable, spot, "move the nearer handle", description)
        }

        if (isMacroBound && macroInfo!!.binding.enabled && propertyName != null) {
            drawMacroBrackets(dl, macroInfo.binding, toPct(macroInfo.binding.minVal), toPct(macroInfo.binding.maxVal), lineStartX, lineWidth, centerY, buttonSize)
        }

        ImGui.popID()
        // The last item is this empty dummy, so an itemTooltip() after the slider never shows: pass the
        // explanation as `description` instead, which the label and track tooltips carry.
        ImGui.setCursorScreenPos(rowStartX, startY + h)
        ImGui.dummy(0f, 0f)
    }

    /** Cyan "[ ]" on a slider track marking the macro knob's min→max travel for a bound property. */
    private fun drawMacroBrackets(
        dl: imgui.ImDrawList, binding: llm.slop.liquidlsd.macro.MacroBinding,
        pctA: Float, pctB: Float, lineStartX: Float, lineWidth: Float, centerY: Float, buttonSize: Float
    ) {
        val xa = lineStartX + minOf(pctA, pctB) * lineWidth
        val xb = lineStartX + maxOf(pctA, pctB) * lineWidth
        val half = buttonSize * 0.4f
        val serif = 4f
        val col = TangoPalette.u32(TangoPalette.SYNC.bright, 0.95f)
        dl.addLine(xa, centerY - half, xa, centerY + half, col, 2f)
        dl.addLine(xa, centerY - half, xa + serif, centerY - half, col, 2f)
        dl.addLine(xa, centerY + half, xa + serif, centerY + half, col, 2f)
        dl.addLine(xb, centerY - half, xb, centerY + half, col, 2f)
        dl.addLine(xb, centerY - half, xb - serif, centerY - half, col, 2f)
        dl.addLine(xb, centerY + half, xb - serif, centerY + half, col, 2f)
    }
}
