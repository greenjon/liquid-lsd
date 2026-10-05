package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.midi.MidiLearnTarget
import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMapModeState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons
import java.io.File

/**
 * Transitions row (MASTER tab) controls beside the TRANS title badge drawn by [PerformanceMatrixPanel]:
 * - Left of the knobs:
 *   - Line 1: Transition picker button showing the active transition with a modified indicator (`*`)
 *     and TransitionQueue prev/status/next navigation.
 *   - Line 2: The crossfader line (Deck A/B snap badges, crossfader slider track, AUTO/FADING button,
 *     fade-time badge).
 * - Right of the knobs: Randomize die button on Line 1, matching the deck rows.
 */
internal object PerformanceTransitionsControls {

    // Per-frame strings are cached by value (the render path must not allocate).
    private val suffixCache = HashMap<llm.slop.liquidlsd.midi.MidiControlMapping, String>()
    private fun midiSuffix(m: llm.slop.liquidlsd.midi.MidiControlMapping?): String {
        if (m == null) return ""
        return suffixCache.getOrPut(m) { if (m.channel == 0) " [CC ${m.cc}]" else " [Ch ${m.channel + 1} CC ${m.cc}]" }
    }
    private val tipPickerLabel = TipCache()
    private val tipPicker = TipCache()
    private val tipQCount = TipCache()
    private val tipQStatus = TipCache()
    private val tipQPrev = TipCache()
    private val tipQNext = TipCache()
    private val tipXfade = TipCache()
    private val tipSnapA = TipCache()
    private val tipSnapB = TipCache()
    private val tipAutoFade = TipCache()
    private val tipSpeedLabel = TipCache()
    private val tipSpeed = TipCache()
    private val tipSpeedBadge = TipCache()
    private val AUTOFADE_INK = TangoPalette.inkFor(TangoPalette.AUTOFADE_ACTIVE, TangoPalette.AUTOFADE_HOVER)


    fun draw(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        startX: Float,
        row1Y: Float,
        row2Y: Float,
        ctrlH: Float,
        width: Float
    ) {
        drawTransitionPickerAndQueue(session, mixer, parametersState, startX, row1Y, ctrlH, width)
        drawCrossfader(session, mixer, parametersState, startX, row2Y, ctrlH, width)
    }

    /**
     * Transitions row right-side controls: randomize die button placed on Row 1, matching deck rows.
     */
    fun drawRightControls(
        session: SessionContext,
        mixer: Mixer,
        startX: Float,
        startY: Float,
        ctrlH: Float,
        width: Float
    ) {
        if (!session.uiTheme.randomizationEnabled) return

        val randBtnBg = TangoPalette.RANDOM_BG.u32()
        val randBtnHov = TangoPalette.RANDOM_HOVER.u32()

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()
        ImGui.pushStyleColor(ImGuiCol.Button, randBtnBg)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, randBtnHov)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.DICES}##perf_trans_rand", width, ctrlH)) {
                ParametersUndo.pushUndoState(session.parametersState, mixer)
                mixer.transitionFilter?.let { filter ->
                    filter.parameters.values.forEach { param ->
                        val min = param.minClamp
                        val max = param.maxClamp
                        param.baseValue = (min + Math.random().toFloat() * (max - min)).coerceIn(min, max)
                    }
                }
            }
        }
        ImGui.popStyleColor(2)
        itemTooltip("Randomize transition parameters.\nClick to randomize with undo support.")
        ImGui.endGroup()
    }

    /**
     * True when the transition filter's dry/wet or any parameter differs from its default or carries an active
     * modulator. Indexed loops over the filter's own lists, so a per-frame call allocates nothing.
     */
    private fun isTransitionModified(filter: ISFFilter?): Boolean {
        if (filter == null) return false
        if (filter.dryWet.baseValue != 1.0f) return true
        // ISFFilter creates exactly one parameter per header input, so walking the inputs list (no map
        // iterator) visits every parameter with its default alongside.
        val inputs = filter.header.INPUTS
        val params = filter.parameters
        for (i in inputs.indices) {
            val input = inputs[i]
            val param = params[input.NAME] ?: continue
            val d = input.DEFAULT
            val defaultVal = if (d is Number) d.toFloat() else d?.toString()?.toFloatOrNull() ?: 0.0f
            if (kotlin.math.abs(param.baseValue - defaultVal) > 0.001f) return true
            val mods = param.modulators
            for (j in mods.indices) if (!mods[j].bypassed) return true
        }
        return false
    }

    private fun drawTransitionPickerAndQueue(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        startX: Float,
        headerY: Float,
        headerH: Float,
        width: Float
    ) {
        val gap = 4f
        val dl = ImGui.getWindowDrawList()

        ImGui.setCursorScreenPos(startX, headerY)
        ImGui.beginGroup()

        // Transition Queue nav: Prev + Count + Next.
        val navBtnW = (headerH * 0.9f).coerceIn(22f, 28f)
        val qTextW = 46f

        // Transition Picker button takes the rest of the line.
        val transBtnW = (width - gap - navBtnW - 2f - qTextW - 2f - navBtnW).coerceAtLeast(60f)

        // 1. Transition Picker Button [ Settings Icon + Name * ]
        val transName = mixer.transitionFilter?.displayName ?: "Default Blend"
        val isTransModified = isTransitionModified(mixer.transitionFilter)
        val modBadge = if (isTransModified) " *" else ""

        if (ImGui.button(tipPickerLabel.get(transName, modBadge) { "${Icons.SETTINGS} $transName$modBadge##perf_trans_picker_btn" }, transBtnW, headerH)) {
            parametersState.openTransitionBrowse()
        }
        itemTooltip(tipPicker.get(transName, modBadge) { "Select ISF transition shader or blend mode.\nActive: $transName$modBadge" })

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = File(payload)
                llm.slop.liquidlsd.presets.TransitionOps.applyItem(file)
            }
            ImGui.endDragDropTarget()
        }

        ImGui.sameLine(0f, gap)

        // 2. Transition Queue Navigation [ < ] [ N/Total ] [ > ]
        val transQ = TransitionQueueManager.queue
        val transQIdx = TransitionQueueManager.activeIndex
        val transCountStr = tipQCount.get(transQIdx, transQ.size) {
            if (transQ.isNotEmpty() && transQIdx in transQ.indices) "${transQIdx + 1}/${transQ.size}" else if (transQ.isNotEmpty()) "-/${transQ.size}" else "--"
        }

        val transQPrevKey = "Global/transQueuePrev"
        val transQPrevOscKey = "Mixer/transQueuePrev"
        val isMidiLearnTransQPrev = session.parametersState.isMidiTargetLearning(transQPrevKey)
        val isOscLearnTransQPrev = OscLearnState.isTargetLearning(transQPrevOscKey)
        val transQPrevMidiMapping = session.midiMappingManager.getMappingForParameter(transQPrevKey)
        val transQPrevMidiText = midiSuffix(transQPrevMidiMapping)

        val transPrevX = ImGui.getCursorScreenPosX()
        val transPrevY = ImGui.getCursorScreenPosY()
        if (ImGui.button("<##perf_trans_q_prev", navBtnW, headerH)) {
            TransitionQueueManager.advancePrevious(mixer)
        }
        if (isMidiLearnTransQPrev) {
            dl.addRect(transPrevX - 1f, transPrevY - 1f, transPrevX + navBtnW + 1f, transPrevY + headerH + 1f, TangoPalette.learnBorder(), 3f, 0, 1.5f)
        } else if (isOscLearnTransQPrev) {
            TangoPalette.drawOscLearnPulseBorder(dl, transPrevX - 1f, transPrevY - 1f, transPrevX + navBtnW + 1f, transPrevY + headerH + 1f)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_trans_q_prev_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Transition Queue Prev (<)")
            ImGui.separator()
            if (ImGui.menuItem("Trigger Previous")) {
                TransitionQueueManager.advancePrevious(mixer)
            }
            ImGui.separator()
            if (isMidiLearnTransQPrev) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Transition Queue Prev)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(transQPrevKey))
                }
            }
            if (transQPrevMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(transQPrevKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnTransQPrev) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Transition Queue Prev)")) {
                    OscLearnState.startLearn(transQPrevOscKey, 0f, 1f, "Transition Queue Prev")
                }
            }
            val transQPrevOscAddress = OscMappingManager.getAddressForParameter(transQPrevOscKey)
            if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, transQPrevOscAddress != null)) {
                OscMappingManager.removeMapping(transQPrevOscAddress!!)
                OscMappingManager.saveActiveProfile()
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        itemTooltip(tipQPrev.get(transQPrevMidiText) { "Advance to previous transition in Transition Queue.$transQPrevMidiText\nRight-click for MIDI/OSC Learn." })

        ImGui.sameLine(0f, 2f)

        val transCurX = ImGui.getCursorScreenPosX()
        val transCurY = ImGui.getCursorScreenPosY()
        val genBgCol = TangoPalette.CELL_BG.u32()
        val genTextCol = TangoPalette.CELL_TEXT.u32()
        dl.addRectFilled(transCurX, transCurY, transCurX + qTextW, transCurY + headerH, genBgCol, 3f)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val sz = ImGui.calcTextSize(transCountStr)
            dl.addText(transCurX + (qTextW - sz.x) * 0.5f, transCurY + (headerH - sz.y) * 0.5f, genTextCol, transCountStr)
        }
        ImGui.invisibleButton("##perf_trans_q_idx", qTextW, headerH)
        itemTooltip(tipQStatus.get(transCountStr) { "Transition Queue status: $transCountStr" })

        ImGui.sameLine(0f, 2f)

        val transQNextKey = "Global/transQueueNext"
        val transQNextOscKey = "Mixer/transQueueNext"
        val isMidiLearnTransQNext = session.parametersState.isMidiTargetLearning(transQNextKey)
        val isOscLearnTransQNext = OscLearnState.isTargetLearning(transQNextOscKey)
        val transQNextMidiMapping = session.midiMappingManager.getMappingForParameter(transQNextKey)
        val transQNextMidiText = midiSuffix(transQNextMidiMapping)

        val transNextX = ImGui.getCursorScreenPosX()
        val transNextY = ImGui.getCursorScreenPosY()
        if (ImGui.button(">##perf_trans_q_next", navBtnW, headerH)) {
            TransitionQueueManager.advanceNext(mixer)
        }
        if (isMidiLearnTransQNext) {
            dl.addRect(transNextX - 1f, transNextY - 1f, transNextX + navBtnW + 1f, transNextY + headerH + 1f, TangoPalette.learnBorder(), 3f, 0, 1.5f)
        } else if (isOscLearnTransQNext) {
            TangoPalette.drawOscLearnPulseBorder(dl, transNextX - 1f, transNextY - 1f, transNextX + navBtnW + 1f, transNextY + headerH + 1f)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_trans_q_next_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Transition Queue Next (>)")
            ImGui.separator()
            if (ImGui.menuItem("Trigger Next")) {
                TransitionQueueManager.advanceNext(mixer)
            }
            ImGui.separator()
            if (isMidiLearnTransQNext) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Transition Queue Next)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(transQNextKey))
                }
            }
            if (transQNextMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(transQNextKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnTransQNext) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Transition Queue Next)")) {
                    OscLearnState.startLearn(transQNextOscKey, 0f, 1f, "Transition Queue Next")
                }
            }
            val transQNextOscAddress = OscMappingManager.getAddressForParameter(transQNextOscKey)
            if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, transQNextOscAddress != null)) {
                OscMappingManager.removeMapping(transQNextOscAddress!!)
                OscMappingManager.saveActiveProfile()
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        itemTooltip(tipQNext.get(transQNextMidiText) { "Advance to next transition in Transition Queue.$transQNextMidiText\nRight-click for MIDI/OSC Learn." })

        ImGui.endGroup()
    }

    /**
     * The crossfader line, fitted into [width] starting at [startX]: [A] snap badge, crossfader
     * track, [B] snap badge, AUTO/FADING, fade-time badge. The track takes whatever width is left.
     */
    private fun drawCrossfader(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        startX: Float,
        headerY: Float,
        headerH: Float,
        width: Float
    ) {
        val gap = 4f
        val dl = ImGui.getWindowDrawList()
        val centerY = headerY + headerH * 0.5f

        val badgeAX = startX
        val badgeAY = headerY
        ImGui.setCursorScreenPos(badgeAX, headerY)
        ImGui.beginGroup()

        // 1. Deck A Snap Badge [ A ]
        val badgeW = (headerH * 1.05f).coerceIn(24f, 32f)
        val rgbA = BrowserDeckButtons.colorA()
        val colorA = TangoPalette.u32(rgbA)
        val snapAKey = "Global/snapDeckA"
        val isMidiLearnSnapA = session.parametersState.isMidiTargetLearning(snapAKey)
        val snapAMapping = session.midiMappingManager.getMappingForParameter(snapAKey)
        val snapAMidiText = midiSuffix(snapAMapping)

        dl.addRectFilled(badgeAX, badgeAY, badgeAX + badgeW, badgeAY + headerH, TangoPalette.PILL_BG.u32(), 4f)
        val badgeBorderColorA = if (isMidiLearnSnapA) TangoPalette.learnBorder() else colorA
        dl.addRect(badgeAX, badgeAY, badgeAX + badgeW, badgeAY + headerH, badgeBorderColorA, 4f, 0, if (isMidiLearnSnapA) 2f else 1.5f)

        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            val textSz = ImGui.calcTextSize("A")
            dl.addText(badgeAX + (badgeW - textSz.x) * 0.5f, badgeAY + (headerH - textSz.y) * 0.5f, colorA, "A")
        }
        ImGui.invisibleButton("##perf_crossfade_deck_a", badgeW, headerH)
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
        }
        if (ImGui.isItemClicked(0)) {
            mixer.onCrossfadeManualTakeover()
            mixer.crossfade.set(-1.0f)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_snap_a_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Snap Deck A")
            ImGui.separator()
            if (ImGui.menuItem("Snap Crossfader to Deck A")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(-1.0f)
            }
            ImGui.separator()
            if (isMidiLearnSnapA) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Snap Deck A)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(snapAKey))
                }
            }
            if (snapAMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(snapAKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        itemTooltip(tipSnapA.get(snapAMidiText) { "Deck A (Click to snap crossfader to Deck A)$snapAMidiText\nRight-click for MIDI Learn." })

        ImGui.sameLine(0f, gap)

        // Fixed-width elements right of the track: [B] badge, AUTO, fade-time badge.
        val autoBtnW = 52f
        val speedBtnW = 44f
        val badgeBW = badgeW

        // Crossfader track takes whatever width is left between the [A] and [B] badges.
        val fixedRightW = gap + badgeBW + gap * 2f + autoBtnW + gap + speedBtnW
        val crossfaderW = (width - badgeW - gap - fixedRightW).coerceAtLeast(40f)

        // 2. Crossfader Slider Track
        val lineStartX = badgeAX + badgeW + gap
        val lineEndX = lineStartX + crossfaderW
        val lineWidth = crossfaderW

        val trackPadX = 2f
        val trackW = lineWidth + trackPadX * 2f
        ImGui.setCursorScreenPos(lineStartX - trackPadX, headerY)
        ImGui.invisibleButton("##perf_crossfader_track", trackW, headerH)

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = File(payload)
                llm.slop.liquidlsd.presets.TransitionOps.applyItem(file)
            }
            ImGui.endDragDropTarget()
        }

        val isTrackHovered = ImGui.isItemHovered()
        val isTrackActive = ImGui.isItemActive()
        val isTrackClicked = ImGui.isItemClicked(0)
        val paramKey = "Mixer/crossfade"
        val isTarget = parametersState.midiLearnTarget?.let {
            it is MidiLearnTarget.BaseValueSlider && it.paramKey == paramKey
        } ?: false
        val isMidiLearnXfader = parametersState.isMidiTargetLearning(paramKey)
        val isOscLearnXfader = OscLearnState.isTargetLearning(paramKey)
        val xfaderMidiMapping = session.midiMappingManager.getMappingForParameter(paramKey)

        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_xfader_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Crossfader (Mixer/crossfade)")
            ImGui.separator()
            if (ImGui.menuItem("Reset to Center (0.0)")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(0.0f)
            }
            if (ImGui.menuItem("Snap to Deck A (-1.0)")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(-1.0f)
            }
            if (ImGui.menuItem("Snap to Deck B (+1.0)")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(1.0f)
            }
            ImGui.separator()
            if (isMidiLearnXfader) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Crossfader)")) {
                    parametersState.startMidiLearn(
                        MidiLearnTarget.BaseValueSlider(
                            paramKey = paramKey,
                            label = "Crossfader",
                            param = mixer.crossfade,
                            min = -1.0f,
                            max = 1.0f
                        )
                    )
                }
            }
            if (xfaderMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(paramKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnXfader) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Crossfader)")) {
                    OscLearnState.startLearn(
                        parameterPath = paramKey,
                        minVal = -1.0f,
                        maxVal = 1.0f,
                        displayLabel = "Crossfader"
                    )
                }
            }
            val oscAddress = OscMappingManager.getAddressForParameter(paramKey)
            if (oscAddress != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping ($oscAddress)")) {
                    OscMappingManager.removeMapping(oscAddress)
                    OscMappingManager.saveActiveProfile()
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()

        if (OscMapModeState.active) {
            if (isTrackClicked) {
                if (isOscLearnXfader) OscLearnState.cancelLearn()
                else OscLearnState.startLearn(parameterPath = paramKey, minVal = -1.0f, maxVal = 1.0f, displayLabel = "Crossfader")
            }
        } else if (isTrackActive) {
            mixer.onCrossfadeManualTakeover()
            val mouseX = ImGui.getIO().mousePos.x
            val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
            val newVal = -1.0f + pct * 2.0f
            mixer.crossfade.set(newVal)
        }

        val io = ImGui.getIO()
        if (isTrackHovered || isTrackActive) {
            if (io.mouseWheel != 0f) {
                mixer.onCrossfadeManualTakeover()
                val shift = io.keyShift
                val ctrl = io.keyCtrl
                val delta = if (ctrl && shift) 0.1f else if (shift) 0.02f else 0.05f
                val newVal = (mixer.crossfade.baseValue + io.mouseWheel * delta).coerceIn(-1.0f, 1.0f)
                mixer.crossfade.set(newVal)
                io.mouseWheel = 0f
            }
            if (ImGui.isMouseClicked(2) || ImGui.isItemClicked(2)) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(0.0f)
            }
        }

        if (isTrackHovered && session.uiTheme.tooltipsEnabled) {
            val mapping = session.midiMappingManager.getMappingForParameter(paramKey)
            val midiText = midiSuffix(mapping)
            itemTooltip(tipXfade.get(midiText) { "Crossfader$midiText\nDrag or scroll to blend. Middle-click to center.\nRight-click for MIDI/OSC Learn." })
        }

        // Render crossfader visual tracks & ticks
        val rgbB = BrowserDeckButtons.colorB()
        val colorB = TangoPalette.u32(rgbB)
        val lineCol = TangoPalette.XF_LINE.u32()
        dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)

        val markColFaint = TangoPalette.XF_MARK_FAINT.u32()
        val markColCenter = TangoPalette.XF_MARK_CENTER.u32()
        val markColEnds = TangoPalette.XF_MARK_ENDS.u32()

        // Ends (-1.0, +1.0)
        dl.addLine(lineStartX, centerY - 6f, lineStartX, centerY + 6f, markColEnds, 1.5f)
        dl.addLine(lineEndX, centerY - 6f, lineEndX, centerY + 6f, markColEnds, 1.5f)

        // Midway points (-0.5, +0.5)
        val midLeftX = lineStartX + lineWidth * 0.25f
        val midRightX = lineStartX + lineWidth * 0.75f
        dl.addLine(midLeftX, centerY - 5f, midLeftX, centerY + 5f, markColFaint, 1f)
        dl.addLine(midRightX, centerY - 5f, midRightX, centerY + 5f, markColFaint, 1f)

        // Middle (0.0)
        val centerX = lineStartX + lineWidth * 0.50f
        dl.addLine(centerX, centerY - 8f, centerX, centerY + 8f, markColCenter, 1.5f)

        // Active bipolar colored bar
        val valPct = ((mixer.crossfade.baseValue - (-1f)) / 2f).coerceIn(0f, 1f)
        val valHandleX = lineStartX + valPct * lineWidth
        val barColor = if (mixer.crossfade.baseValue < 0f) colorA else colorB
        if (kotlin.math.abs(valHandleX - centerX) > 0.5f) {
            dl.addLine(centerX, centerY, valHandleX, centerY, barColor, 3f)
        }

        // Handle
        val handleW = 6f
        val handleH = 16f
        val handleBgCol = if (isTrackActive) TangoPalette.XF_HANDLE_ACTIVE.u32() else TangoPalette.XF_HANDLE_IDLE.u32()
        val handleBorderCol = TangoPalette.XF_HANDLE_BORDER.u32()
        dl.addRectFilled(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
        dl.addRect(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

        // Hover / Active border
        if (isTarget) {
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, TangoPalette.learnBorder(), 4f, 0, 1.5f)
        } else if (isOscLearnXfader) {
            TangoPalette.drawOscLearnPulseBorder(dl, lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, 4f, 1.5f)
        } else if (isTrackHovered || isTrackActive) {
            val borderCol = if (isTrackActive) TangoPalette.learnBorder() else TangoPalette.XF_HOVER_BORDER.u32()
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, borderCol, 4f, 0, 1.5f)
        }

        // Dynamic modulated value indicator (Amber Gold dot)
        val hasModulators = mixer.crossfade.modulators.any { !it.bypassed }
        if (hasModulators || mixer.isAutoFading) {
            val livePct = ((mixer.crossfade.value - (-1f)) / 2f).coerceIn(0f, 1f)
            val liveX = lineStartX + livePct * lineWidth
            val dotR = 4f
            val curDotCol = TangoPalette.XF_LIVE_DOT.u32()
            dl.addCircleFilled(liveX, centerY, dotR, curDotCol)
            dl.addCircle(liveX, centerY, dotR + 0.5f, TangoPalette.XF_HANDLE_BORDER.u32(), 12, 1.0f)
        }

        ImGui.sameLine(0f, gap)

        // 3. Deck B Snap Badge [ B ]
        val badgeBX = lineEndX + gap
        val badgeBY = headerY
        val snapBKey = "Global/snapDeckB"
        val isMidiLearnSnapB = session.parametersState.isMidiTargetLearning(snapBKey)
        val snapBMapping = session.midiMappingManager.getMappingForParameter(snapBKey)
        val snapBMidiText = midiSuffix(snapBMapping)

        val badgeBorderColorB = if (isMidiLearnSnapB) TangoPalette.learnBorder() else colorB
        dl.addRectFilled(badgeBX, badgeBY, badgeBX + badgeBW, badgeBY + headerH, TangoPalette.PILL_BG.u32(), 4f)
        dl.addRect(badgeBX, badgeBY, badgeBX + badgeBW, badgeBY + headerH, badgeBorderColorB, 4f, 0, if (isMidiLearnSnapB) 2f else 1.5f)

        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            val textSz = ImGui.calcTextSize("B")
            dl.addText(badgeBX + (badgeBW - textSz.x) * 0.5f, badgeBY + (headerH - textSz.y) * 0.5f, colorB, "B")
        }
        ImGui.invisibleButton("##perf_crossfade_deck_b", badgeBW, headerH)
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
        }
        if (ImGui.isItemClicked(0)) {
            mixer.onCrossfadeManualTakeover()
            mixer.crossfade.set(1.0f)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_snap_b_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Snap Deck B")
            ImGui.separator()
            if (ImGui.menuItem("Snap Crossfader to Deck B")) {
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(1.0f)
            }
            ImGui.separator()
            if (isMidiLearnSnapB) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Snap Deck B)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(snapBKey))
                }
            }
            if (snapBMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(snapBKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        itemTooltip(tipSnapB.get(snapBMidiText) { "Deck B (Click to snap crossfader to Deck B)$snapBMidiText\nRight-click for MIDI Learn." })

        ImGui.sameLine(0f, gap * 2f)

        // 4. Auto-Fade Button [ AUTO ]
        val autoFadeKey = "Global/autoFade"
        val isMidiLearnAutoFade = session.parametersState.isMidiTargetLearning(autoFadeKey)
        val autoFadeMapping = session.midiMappingManager.getMappingForParameter(autoFadeKey)
        val autoFadeMidiText = midiSuffix(autoFadeMapping)

        val autoX = ImGui.getCursorScreenPosX()
        val autoY = ImGui.getCursorScreenPosY()
        val isAuto = mixer.isAutoFading
        if (isAuto) {
            val autoInk = AUTOFADE_INK
            ImGui.pushStyleColor(ImGuiCol.Button, TangoPalette.u32(TangoPalette.AUTOFADE_ACTIVE, 0.9f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, TangoPalette.u32(TangoPalette.AUTOFADE_HOVER))
            ImGui.pushStyleColor(ImGuiCol.Text, autoInk[0], autoInk[1], autoInk[2], 1.0f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, TangoPalette.BUTTON_BG.u32())
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, TangoPalette.BUTTON_HOVER.u32())
        }
        val autoLabel = if (isAuto) "FADING##perf_autofade_btn" else "AUTO##perf_autofade_btn"
        if (ImGui.button(autoLabel, autoBtnW, headerH)) {
            if (mixer.isAutoFading) {
                mixer.onCrossfadeManualTakeover()
            } else {
                val targetIsA = mixer.crossfade.baseValue > 0.0f
                mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
                mixer.isAutoFading = true
                mixer.muteCrossfadeNonMidiCv()
            }
        }
        ImGui.popStyleColor(if (isAuto) 3 else 2)
        if (isMidiLearnAutoFade) {
            dl.addRect(autoX - 1f, autoY - 1f, autoX + autoBtnW + 1f, autoY + headerH + 1f, TangoPalette.learnBorder(), 3f, 0, 1.5f)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_autofade_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Crossfader Auto-Fade")
            ImGui.separator()
            if (ImGui.menuItem(if (mixer.isAutoFading) "Stop Auto-Fade" else "Start Auto-Fade")) {
                if (mixer.isAutoFading) {
                    mixer.onCrossfadeManualTakeover()
                } else {
                    val targetIsA = mixer.crossfade.baseValue > 0.0f
                    mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
                    mixer.isAutoFading = true
                    mixer.muteCrossfadeNonMidiCv()
                }
            }
            ImGui.separator()
            if (isMidiLearnAutoFade) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Auto-Fade)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(autoFadeKey))
                }
            }
            if (autoFadeMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(autoFadeKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        itemTooltip(tipAutoFade.get(Math.round(mixer.xfadeSpeed.value * 10f), autoFadeMidiText) { "Auto-fade between Deck A and Deck B over ${String.format(java.util.Locale.US, "%.1f", mixer.xfadeSpeed.value)}s.$autoFadeMidiText\nClick while fading to stop. Right-click for MIDI Learn." })

        ImGui.sameLine(0f, gap)

        // 5. Fade Speed Widget [ N.Ns ]
        val xfadeSpeedParamKey = "Mixer/xfadeSpeed"
        val isMidiLearnSpeed = session.parametersState.isMidiTargetLearning(xfadeSpeedParamKey)
        val isOscLearnSpeed = OscLearnState.isTargetLearning(xfadeSpeedParamKey)
        val speedMidiMapping = session.midiMappingManager.getMappingForParameter(xfadeSpeedParamKey)
        val speedMidiText = midiSuffix(speedMidiMapping)

        val speedX = ImGui.getCursorScreenPosX()
        val speedY = ImGui.getCursorScreenPosY()
        val speedTenths = Math.round(mixer.xfadeSpeed.baseValue * 10f)
        val speedStr = tipSpeedLabel.get(speedTenths) { "${String.format(java.util.Locale.US, "%.1f", mixer.xfadeSpeed.baseValue)}s" }

        ImGui.pushStyleColor(ImGuiCol.Button, TangoPalette.SPEED_BG.u32())
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, TangoPalette.SPEED_HOVER.u32())
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.button(tipSpeedBadge.get(speedStr) { "$speedStr##perf_speed_badge" }, speedBtnW, headerH)
        }
        ImGui.popStyleColor(2)

        val isSpeedHovered = ImGui.isItemHovered()
        val isSpeedActive = ImGui.isItemActive()
        val isSpeedClicked = ImGui.isItemClicked(0)
        if (OscMapModeState.active) {
            if (isSpeedClicked) {
                if (isOscLearnSpeed) OscLearnState.cancelLearn()
                else OscLearnState.startLearn(xfadeSpeedParamKey, 0.1f, 15.0f, "Fade Speed")
            }
        } else {
            if (isSpeedActive) {
                val dragDelta = ImGui.getIO().mouseDelta.x - ImGui.getIO().mouseDelta.y
                if (dragDelta != 0f) {
                    val step = if (ImGui.getIO().keyShift) 0.02f else 0.1f
                    mixer.xfadeSpeed.baseValue = (mixer.xfadeSpeed.baseValue + dragDelta * step).coerceIn(0.1f, 30.0f)
                }
            }
            if (isSpeedHovered && ImGui.getIO().mouseWheel != 0f) {
                val step = if (ImGui.getIO().keyShift) 0.05f else 0.2f
                mixer.xfadeSpeed.baseValue = (mixer.xfadeSpeed.baseValue + ImGui.getIO().mouseWheel * step).coerceIn(0.1f, 30.0f)
                ImGui.getIO().mouseWheel = 0f
            }
        }

        if (isMidiLearnSpeed) {
            dl.addRect(speedX - 1f, speedY - 1f, speedX + speedBtnW + 1f, speedY + headerH + 1f, TangoPalette.learnBorder(), 3f, 0, 1.5f)
        } else if (isOscLearnSpeed) {
            TangoPalette.drawOscLearnPulseBorder(dl, speedX - 1f, speedY - 1f, speedX + speedBtnW + 1f, speedY + headerH + 1f)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_speed_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Auto-Fade Duration ($speedStr)")
            ImGui.separator()
            listOf(0.5f, 1.0f, 2.0f, 4.0f, 8.0f).forEach { s ->
                if (ImGui.menuItem("${s}s", "", kotlin.math.abs(mixer.xfadeSpeed.baseValue - s) < 0.05f)) {
                    mixer.xfadeSpeed.baseValue = s
                }
            }
            ImGui.separator()
            if (isMidiLearnSpeed) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Fade Speed)")) {
                    session.parametersState.startMidiLearn(
                        MidiLearnTarget.BaseValueSlider(
                            paramKey = xfadeSpeedParamKey,
                            label = "Fade Speed",
                            param = mixer.xfadeSpeed,
                            min = 0.1f,
                            max = 15.0f
                        )
                    )
                }
            }
            if (speedMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(xfadeSpeedParamKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnSpeed) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Fade Speed)")) {
                    OscLearnState.startLearn(xfadeSpeedParamKey, 0.1f, 15.0f, "Fade Speed")
                }
            }
            val oscAddress = OscMappingManager.getAddressForParameter(xfadeSpeedParamKey)
            if (oscAddress != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping ($oscAddress)")) {
                    OscMappingManager.removeMapping(oscAddress)
                    OscMappingManager.saveActiveProfile()
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        itemTooltip(tipSpeed.get(speedStr, speedMidiText) { "Auto-fade duration: $speedStr$speedMidiText\nDrag or scroll to adjust speed.\nRight-click for quick presets & MIDI/OSC Learn." })

        ImGui.endGroup()
    }
}
