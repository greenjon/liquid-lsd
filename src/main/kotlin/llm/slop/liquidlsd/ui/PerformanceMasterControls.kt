package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons
import java.io.File

/**
 * Master row controls:
 * - Header bar: Deck A/B snap badges, crossfader track, AUTO/FADING button, and the
 *   crossfader-time (Fade Speed) badge.
 * - Left of the knobs, two stacked rows mirroring the deck rows: [MIX] knob-assign pill over
 *   [FX] knob-assign pill + Master FX chain header.
 * - Right of the knobs: Master FX bypass.
 */
internal object PerformanceMasterControls {

    /**
     * [MIX] / [FX] knob-assign pills (see [PerformanceUiContext.isMasterRowFx]) stacked like a
     * deck row's [SRC] / [FX], with the Master FX chain header beside [FX].
     */
    fun drawModeControls(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        ctx: PerformanceUiContext,
        startX: Float,
        row1Y: Float,
        row2Y: Float,
        ctrlH: Float,
        rowW: Float
    ) {
        val gap = 4f
        val modeBtnW = 28f
        val isFx = ctx.isMasterRowFx(parametersState)
        val inactiveCol = ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.7f)

        ImGui.setCursorScreenPos(startX, row1Y)
        ImGui.pushStyleColor(ImGuiCol.Button, if (!isFx) ImGui.colorConvertFloat4ToU32(0.20f, 0.45f, 0.70f, 1f) else inactiveCol)
        if (ImGui.button("MIX##perf_mode_mix_mst", modeBtnW, ctrlH)) {
            llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection("Mixer", "CTRL")
            ctx.masterRowMode = "MIX"
            if (parametersState.activeMixerSubTab == "FX") parametersState.activeMixerSubTab = "CTRL"
        }
        ImGui.popStyleColor()
        itemTooltip("Assign the Master row's knobs to the mix: Deck A / Deck B / Deck BG alphas and master level.")

        ImGui.setCursorScreenPos(startX, row2Y)
        ImGui.beginGroup()
        ImGui.pushStyleColor(ImGuiCol.Button, if (isFx) ImGui.colorConvertFloat4ToU32(0.80f, 0.40f, 0.15f, 1f) else inactiveCol)
        if (ImGui.button("FX##perf_mode_fx_mst", modeBtnW, ctrlH)) {
            llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection("Mixer", "FX")
            ctx.masterRowMode = "FX"
            parametersState.activeMixerSubTab = "FX"
            llm.slop.liquidlsd.macro.FxMacroSync.syncFor(MacroEngine.MASTER_FX, mixer)
        }
        ImGui.popStyleColor()
        itemTooltip("Assign the Master row's knobs to the Master FX chain (Super Knob + 3 Metaknobs). FX chain controls stay available either way.")

        ImGui.sameLine(0f, gap)
        FxChainHeader.drawControls(session, mixer, mixer.masterFxChain, MacroEngine.MASTER_FX, "Master FX", ctrlH, maxW = rowW - modeBtnW - gap)
        ImGui.endGroup()
    }

    /** Master FX chain bypass, placed to the right of the knobs like a deck row's. */
    fun drawBypassControls(mixer: Mixer, startX: Float, startY: Float, ctrlH: Float, width: Float) {
        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()
        FxChainHeader.drawBypassButton(mixer.masterFxChain, "MST", ctrlH, width - 4f)
        ImGui.endGroup()
    }

    fun draw(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        boxX1: Float,
        boxX2: Float,
        headerY: Float,
        headerH: Float
    ) {
        val pad = 6f
        val gap = 4f
        val availW = (boxX2 - boxX1 - pad * 2f).coerceAtLeast(1f)
        val dl = ImGui.getWindowDrawList()
        val centerY = headerY + headerH * 0.5f

        ImGui.setCursorScreenPos(boxX1 + pad, headerY)
        ImGui.beginGroup()

        // 1. Deck A Snap Badge [ A ]
        val badgeW = (headerH * 1.05f).coerceIn(24f, 32f)
        val badgeAX = boxX1 + pad
        val badgeAY = headerY
        val rgbA = BrowserDeckButtons.colorA()
        val colorA = ImGui.colorConvertFloat4ToU32(rgbA[0], rgbA[1], rgbA[2], 1f)
        val snapAKey = "Global/snapDeckA"
        val isMidiLearnSnapA = session.parametersState.isMidiTargetLearning(snapAKey)
        val snapAMapping = session.midiMappingManager.getMappingForParameter(snapAKey)
        val snapAMidiText = snapAMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        dl.addRectFilled(badgeAX, badgeAY, badgeAX + badgeW, badgeAY + headerH, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
        val badgeBorderColorA = if (isMidiLearnSnapA) ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f) else colorA
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
        if (ImGui.beginPopupContextItem("perf_snap_a_ctx")) {
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
            ImGui.endPopup()
        }
        itemTooltip("Deck A (Click to snap crossfader to Deck A)$snapAMidiText\nRight-click for MIDI Learn.")

        ImGui.sameLine(0f, gap)

        // Calculate layout allocations for right-side elements first:
        // Auto-Fade button
        val autoBtnW = (availW * 0.10f).coerceIn(48f, 75f)

        // Fade Speed widget (drag/badge)
        val speedBtnW = (availW * 0.08f).coerceIn(44f, 65f)

        // Deck B badge width
        val badgeBW = badgeW

        // Crossfader slider takes whatever remaining width is available between Deck A and Deck B
        val fixedRightW = gap + badgeBW + gap * 2f + autoBtnW + gap + speedBtnW
        val crossfaderW = (availW - badgeW - fixedRightW).coerceAtLeast(50f)

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
                if (file.extension.equals("lsdtrans", ignoreCase = true) && file.exists()) {
                    session.presetRepository.loadTransitionPresetAsync(file).thenAccept { dto ->
                        mixer.applyTransitionPreset(dto)
                    }
                } else {
                    val id = if (file.extension.equals("fs", ignoreCase = true) || file.extension.equals("isf", ignoreCase = true)) {
                        file.nameWithoutExtension
                    } else {
                        file.nameWithoutExtension.ifBlank { file.name }
                    }
                    mixer.setTransition(id)
                }
            }
            ImGui.endDragDropTarget()
        }

        val isTrackHovered = ImGui.isItemHovered()
        val isTrackActive = ImGui.isItemActive()
        val paramKey = "Mixer/crossfade"
        val isTarget = parametersState.midiLearnTarget?.let {
            it is MidiLearnTarget.BaseValueSlider && it.paramKey == paramKey
        } ?: false
        val isMidiLearnXfader = parametersState.isMidiTargetLearning(paramKey)
        val isOscLearnXfader = OscLearnState.isTargetLearning(paramKey)
        val xfaderMidiMapping = session.midiMappingManager.getMappingForParameter(paramKey)

        if (ImGui.beginPopupContextItem("perf_xfader_ctx")) {
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
            val oscAddress = OscMappingManager.getMappings().entries.find { it.value.parameterPath == paramKey }?.key
            if (oscAddress != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping ($oscAddress)")) {
                    OscMappingManager.removeMapping(oscAddress)
                    OscMappingManager.saveActiveProfile()
                }
            }
            ImGui.endPopup()
        }

        if (isTrackActive) {
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
            val midiText = mapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""
            itemTooltip("Crossfader$midiText\nDrag or scroll to blend. Middle-click to center.\nRight-click for MIDI/OSC Learn.")
        }

        // Render crossfader visual tracks & ticks
        val rgbB = BrowserDeckButtons.colorB()
        val colorB = ImGui.colorConvertFloat4ToU32(rgbB[0], rgbB[1], rgbB[2], 1f)
        val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f)
        dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)

        val markColFaint = ImGui.colorConvertFloat4ToU32(0.65f, 0.65f, 0.65f, 0.28f)
        val markColCenter = ImGui.colorConvertFloat4ToU32(0.85f, 0.85f, 0.85f, 0.45f)
        val markColEnds = ImGui.colorConvertFloat4ToU32(0.70f, 0.70f, 0.70f, 0.35f)

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
        val handleBgCol = if (isTrackActive) ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f) else ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f)
        val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
        dl.addRectFilled(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
        dl.addRect(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

        // Hover / Active border
        if (isTarget) {
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, ImGui.colorConvertFloat4ToU32(0f, 0.8f, 1f, 1f), 4f, 0, 1.5f)
        } else if (isTrackHovered || isTrackActive) {
            val borderCol = if (isTrackActive) ImGui.colorConvertFloat4ToU32(0.0f, 0.85f, 1.0f, 1.0f) else ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f)
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, borderCol, 4f, 0, 1.5f)
        }

        // Dynamic modulated value indicator (Amber Gold dot)
        val hasModulators = mixer.crossfade.modulators.any { !it.bypassed }
        if (hasModulators || mixer.isAutoFading) {
            val livePct = ((mixer.crossfade.value - (-1f)) / 2f).coerceIn(0f, 1f)
            val liveX = lineStartX + livePct * lineWidth
            val dotR = 4f
            val curDotCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 1.0f)
            dl.addCircleFilled(liveX, centerY, dotR, curDotCol)
            dl.addCircle(liveX, centerY, dotR + 0.5f, ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f), 12, 1.0f)
        }

        ImGui.sameLine(0f, gap)

        // 3. Deck B Snap Badge [ B ]
        val badgeBX = lineEndX + gap
        val badgeBY = headerY
        val snapBKey = "Global/snapDeckB"
        val isMidiLearnSnapB = session.parametersState.isMidiTargetLearning(snapBKey)
        val snapBMapping = session.midiMappingManager.getMappingForParameter(snapBKey)
        val snapBMidiText = snapBMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val badgeBorderColorB = if (isMidiLearnSnapB) ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f) else colorB
        dl.addRectFilled(badgeBX, badgeBY, badgeBX + badgeBW, badgeBY + headerH, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
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
        if (ImGui.beginPopupContextItem("perf_snap_b_ctx")) {
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
            ImGui.endPopup()
        }
        itemTooltip("Deck B (Click to snap crossfader to Deck B)$snapBMidiText\nRight-click for MIDI Learn.")

        ImGui.sameLine(0f, gap * 2f)

        // 4. Auto-Fade Button [ AUTO ]
        val autoFadeKey = "Global/autoFade"
        val isMidiLearnAutoFade = session.parametersState.isMidiTargetLearning(autoFadeKey)
        val autoFadeMapping = session.midiMappingManager.getMappingForParameter(autoFadeKey)
        val autoFadeMidiText = autoFadeMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val autoX = ImGui.getCursorScreenPosX()
        val autoY = ImGui.getCursorScreenPosY()
        val isAuto = mixer.isAutoFading
        if (isAuto) {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.9f, 0.6f, 0.1f, 0.9f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(1.0f, 0.7f, 0.2f, 1.0f))
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.24f, 0.28f, 0.35f, 1f))
        }
        val autoLabel = if (isAuto) "FADING" else "AUTO"
        if (ImGui.button("$autoLabel##perf_autofade_btn", autoBtnW, headerH)) {
            if (mixer.isAutoFading) {
                mixer.onCrossfadeManualTakeover()
            } else {
                val targetIsA = mixer.crossfade.baseValue > 0.0f
                mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
                mixer.isAutoFading = true
                mixer.muteCrossfadeNonMidiCv()
            }
        }
        ImGui.popStyleColor(2)
        if (isMidiLearnAutoFade) {
            dl.addRect(autoX - 1f, autoY - 1f, autoX + autoBtnW + 1f, autoY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_autofade_ctx")) {
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
            ImGui.endPopup()
        }
        itemTooltip("Auto-fade between Deck A and Deck B over ${String.format(java.util.Locale.US, "%.1f", mixer.xfadeSpeed.value)}s.$autoFadeMidiText\nClick while fading to stop. Right-click for MIDI Learn.")

        ImGui.sameLine(0f, gap)

        // 5. Fade Speed Widget [ N.Ns ]
        val xfadeSpeedParamKey = "Mixer/xfadeSpeed"
        val isMidiLearnSpeed = session.parametersState.isMidiTargetLearning(xfadeSpeedParamKey)
        val isOscLearnSpeed = OscLearnState.isTargetLearning(xfadeSpeedParamKey)
        val speedMidiMapping = session.midiMappingManager.getMappingForParameter(xfadeSpeedParamKey)
        val speedMidiText = speedMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val speedX = ImGui.getCursorScreenPosX()
        val speedY = ImGui.getCursorScreenPosY()
        val speedStr = "${String.format(java.util.Locale.US, "%.1f", mixer.xfadeSpeed.baseValue)}s"

        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.90f))
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.20f, 0.24f, 0.32f, 1f))
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.button("$speedStr##perf_speed_badge", speedBtnW, headerH)
        }
        ImGui.popStyleColor(2)

        val isSpeedHovered = ImGui.isItemHovered()
        val isSpeedActive = ImGui.isItemActive()
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

        if (isMidiLearnSpeed) {
            dl.addRect(speedX - 1f, speedY - 1f, speedX + speedBtnW + 1f, speedY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_speed_ctx")) {
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
            val oscAddress = OscMappingManager.getMappings().entries.find { it.value.parameterPath == xfadeSpeedParamKey }?.key
            if (oscAddress != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping ($oscAddress)")) {
                    OscMappingManager.removeMapping(oscAddress)
                    OscMappingManager.saveActiveProfile()
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Auto-fade duration: $speedStr$speedMidiText\nDrag or scroll to adjust speed.\nRight-click for quick presets & MIDI/OSC Learn.")

        ImGui.endGroup()
    }
}
