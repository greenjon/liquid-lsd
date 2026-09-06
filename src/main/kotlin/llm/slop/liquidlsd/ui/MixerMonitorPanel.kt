package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.presets.PresetManager

import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons

class MixerMonitorPanel(
    private val presetState: PresetGridState,
    private val drawDeckControls: (Mixer, String, Deck, Float, Float, Boolean) -> Unit,
    private val onUtilityAction: (Int, Deck, Deck) -> Unit, // (mode: 0=Move, 1=Copy, 2=Swap, from, to)
    private val onSaveDeck: (Deck, Boolean, Boolean) -> Unit,
    private val onEjectDeck: (Deck, Boolean, Boolean) -> Unit
) {
    private var pendingRightDragFrom: String? = null

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        val style = ImGui.getStyle()
        val layout = MixerMonitorLayoutCalculator.calculate(
            windowWidth = ImGui.getWindowWidth(),
            availableHeight = ImGui.getContentRegionAvailY(),
            windowPaddingX = style.getWindowPaddingX(),
            scrollbarWidth = style.getScrollbarSize(),
            textLineHeightWithSpacing = ImGui.getTextLineHeightWithSpacing(),
            frameHeightWithSpacing = ImGui.getFrameHeightWithSpacing(),
            itemSpacingY = style.getItemSpacingY(),
            aspectRatio = session.uiTheme.renderAspectRatio,
            randomizationEnabled = session.uiTheme.randomizationEnabled
        )
        val availW = layout.renderWidth.coerceAtLeast(1f)
        val masterH = layout.masterHeight.coerceAtLeast(1f)
        val offsetX = layout.offsetX

        val baseScreenX = ImGui.getCursorScreenPosX()
        val imgScreenX = baseScreenX + offsetX
        val imgScreenY = ImGui.getCursorScreenPosY()

        val dlMaster = ImGui.getWindowDrawList()
        dlMaster.addRectFilled(imgScreenX, imgScreenY, imgScreenX + availW, imgScreenY + masterH, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))

        ImGui.setCursorScreenPos(imgScreenX, imgScreenY)
        ImGui.image(mixer.masterFBO.texture, availW, masterH, 0f, 1f, 1f, 0f)

        ImGui.setCursorScreenPos(imgScreenX, imgScreenY)
        ImGui.invisibleButton("##main_output_monitor", availW.coerceAtLeast(1f), masterH.coerceAtLeast(1f))
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Main output monitor. Click to focus Preset Grid Mix tab.")
        }
        if (ImGui.isItemClicked(0)) {
            presetState.activeTopTab = "Mixer"
        }

        // Live recording tally badge overlay
        if (llm.slop.liquidlsd.export.RealtimeRecorder.isRecording) {
            val elapsed = llm.slop.liquidlsd.export.RealtimeRecorder.elapsedSeconds.toInt()
            val mins = elapsed / 60
            val secs = elapsed % 60
            val recText = "REC %02d:%02d".format(mins, secs)
            val badgeX = imgScreenX + availW - 100f
            val badgeY = imgScreenY + 8f
            val pulse = (kotlin.math.sin(llm.slop.liquidlsd.utils.TimeSource.getTimeSec() * 4.0) * 0.25 + 0.75).toFloat()
            dlMaster.addRectFilled(badgeX - 4f, badgeY - 2f, badgeX + 94f, badgeY + 20f, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0.75f), 4f)
            dlMaster.addCircleFilled(badgeX + 6f, badgeY + 9f, 4f, ImGui.colorConvertFloat4ToU32(1f, 0.15f, 0.15f, pulse))
            dlMaster.addText(badgeX + 16f, badgeY + 1f, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f), recText)
        }

        // Restore Y cursor position
        ImGui.setCursorScreenPos(imgScreenX, imgScreenY + masterH)
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- Master Mixer Controls ---
        val numRows = if (session.uiTheme.randomizationEnabled) 2f else 1f
        val masterControlsH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            (ImGui.getFrameHeightWithSpacing() * numRows) + (ImGui.getStyle().itemSpacing.y * (numRows - 1f)) + 12f
        }.coerceAtLeast(if (session.uiTheme.randomizationEnabled) 58f else 34f)

        ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.colorConvertFloat4ToU32(0.05f, 0.1f, 0.08f, 0.4f)) // Faint mint background
        ImGui.setCursorScreenPos(imgScreenX, ImGui.getCursorScreenPosY())
        ImGui.beginChild("MasterControls", availW, masterControlsH, true, imgui.flag.ImGuiWindowFlags.NoScrollbar)
        
        // Row 1: Crossfader with Deck A box on left and Deck B box on right
        drawCrossfaderSlider(session, mixer, availW)

        // Row 2: Momentary Controls: Randomize A/B/BG/PV/All
        if (session.uiTheme.randomizationEnabled) {
            ImGui.spacing()
            val spacingX = ImGui.getStyle().itemSpacing.x
            val totalAvailW = ImGui.getContentRegionAvailX()
            val numButtons = 5
            val mBtnW = ((totalAvailW - (spacingX * (numButtons - 1))) / numButtons).coerceAtLeast(20f)
            val mBtnH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFrameHeight() * 0.9f }.coerceAtLeast(20f)

            // Rand A Button
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.28f, 0.20f, 0.26f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.38f, 0.28f, 0.36f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.48f, 0.36f, 0.46f, 1f))
            if (ImGui.button("${Icons.DICES} A##rand_deck_a", mBtnW, mBtnH)) {
                PresetGridUndo.pushUndoState(presetState, mixer)
                mixer.randomizeDeckA()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Randomize Deck A modulators & base values (Mixer/randDeckA).\nSupports continuous 0-1 morphing when modulated by CV, LFOs, or MIDI.")
            }
            ImGui.popStyleColor(3)

            ImGui.sameLine()

            // Rand B Button
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.28f, 0.20f, 0.26f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.38f, 0.28f, 0.36f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.48f, 0.36f, 0.46f, 1f))
            if (ImGui.button("${Icons.DICES} B##rand_deck_b", mBtnW, mBtnH)) {
                PresetGridUndo.pushUndoState(presetState, mixer)
                mixer.randomizeDeckB()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Randomize Deck B modulators & base values (Mixer/randDeckB).\nSupports continuous 0-1 morphing when modulated by CV, LFOs, or MIDI.")
            }
            ImGui.popStyleColor(3)

            ImGui.sameLine()

            // Rand BG Button
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.28f, 0.20f, 0.26f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.38f, 0.28f, 0.36f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.48f, 0.36f, 0.46f, 1f))
            if (ImGui.button("${Icons.DICES} BG##rand_deck_bg", mBtnW, mBtnH)) {
                PresetGridUndo.pushUndoState(presetState, mixer)
                mixer.randomizeDeckBG()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Randomize Deck BG modulators & base values (Mixer/randDeckBG).\nSupports continuous 0-1 morphing when modulated by CV, LFOs, or MIDI.")
            }
            ImGui.popStyleColor(3)

            ImGui.sameLine()

            // Rand PV Button
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.28f, 0.20f, 0.26f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.38f, 0.28f, 0.36f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.48f, 0.36f, 0.46f, 1f))
            if (ImGui.button("${Icons.DICES} PV##rand_deck_pv", mBtnW, mBtnH)) {
                PresetGridUndo.pushUndoState(presetState, mixer)
                mixer.randomizeDeckPV()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Randomize Deck PV modulators & base values (Mixer/randDeckPV).\nSupports continuous 0-1 morphing when modulated by CV, LFOs, or MIDI.")
            }
            ImGui.popStyleColor(3)

            ImGui.sameLine()

            // Rand All Button
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.36f, 0.22f, 0.32f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.46f, 0.30f, 0.42f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.56f, 0.38f, 0.52f, 1f))
            if (ImGui.button("${Icons.DICES} All##rand_all", mBtnW, mBtnH)) {
                PresetGridUndo.pushUndoState(presetState, mixer)
                mixer.randomizeAll()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Randomize all Decks (A, B, BG, PV) and Master parameters (Mixer/randAll).\nSupports continuous 0-1 morphing when modulated by CV, LFOs, or MIDI.")
            }
            ImGui.popStyleColor(3)
        }

        ImGui.endChild()
        ImGui.popStyleColor()

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- Deck Monitors (2x2 Grid) ---
        val padding = 16f
        val halfW = ((availW - padding) * 0.5f).coerceAtLeast(1f)
        
        val startX = baseScreenX + offsetX
        val centerY = ImGui.getCursorScreenPosY()
        val rightColStartX = startX + halfW + padding

        val subH = layout.deckChildHeight.coerceAtLeast(1f)
        
        // --- Row 1: Deck A & Deck B ---
        ImGui.setCursorScreenPos(startX, centerY)
        drawDeckControls(mixer, "Deck A", mixer.deckA, halfW, subH, true)
        
        ImGui.setCursorScreenPos(rightColStartX, centerY)
        drawDeckControls(mixer, "Deck B", mixer.deckB, halfW, subH, false)
        
        // --- Row 2: Deck BG & Deck PV ---
        val row2Y = centerY + subH + ImGui.getStyle().getItemSpacingY() + 6f
        
        ImGui.setCursorScreenPos(startX, row2Y)
        drawDeckControls(mixer, "Deck BG", mixer.deckBG, halfW, subH, false)
        
        ImGui.setCursorScreenPos(rightColStartX, row2Y)
        drawDeckControls(mixer, "Deck PV", mixer.deckPV, halfW, subH, false)
        
        ImGui.setCursorScreenPos(startX, row2Y + subH + 4f)
    }

    private fun drawCrossfaderSlider(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        availW: Float
    ) {
        val fontLevel = UITheme.FontLevel.H2
        var textWA = 0f
        var textWB = 0f
        var textH = 0f
        session.uiTheme.withFont(fontLevel) {
            textWA = ImGui.calcTextSize("A").x
            textWB = ImGui.calcTextSize("B").x
            textH = ImGui.getTextLineHeight()
        }

        val badgePadX = 8f
        val badgePadY = 3f
        val badgeW = (maxOf(textWA, textWB) + badgePadX * 2f).coerceAtLeast(24f)
        val badgeH = (textH + badgePadY * 2f).coerceAtLeast(24f)

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val rowH = badgeH
        val centerY = startY + rowH * 0.5f

        // Reserve row space
        ImGui.dummy(availW, rowH)
        val dl = ImGui.getWindowDrawList()

        // 1. Deck A Box (styled identically to Deck A monitor badge)
        val badgeAX = startX
        val badgeAY = startY
        val rgbA = BrowserDeckButtons.colorA()
        val colorA = ImGui.colorConvertFloat4ToU32(rgbA[0], rgbA[1], rgbA[2], 1f)

        dl.addRectFilled(badgeAX, badgeAY, badgeAX + badgeW, badgeAY + badgeH, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
        dl.addRect(badgeAX, badgeAY, badgeAX + badgeW, badgeAY + badgeH, colorA, 4f, 0, 1.5f)

        val textAX = badgeAX + (badgeW - textWA) * 0.5f
        val textAY = badgeAY + (badgeH - textH) * 0.5f
        session.uiTheme.withFont(fontLevel) {
            dl.addText(textAX, textAY, colorA, "A")
        }

        ImGui.setCursorScreenPos(badgeAX, badgeAY)
        ImGui.invisibleButton("##btn_crossfade_deck_a", badgeW, badgeH)
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
            if (session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Deck A (Click to snap crossfader to Deck A)")
            }
        }
        if (ImGui.isItemClicked(0)) {
            mixer.onCrossfadeManualTakeover()
            mixer.crossfade.set(-1.0f)
        }

        // 2. Deck B Box (styled identically to Deck B monitor badge)
        val badgeBX = startX + availW - badgeW
        val badgeBY = startY
        val rgbB = BrowserDeckButtons.colorB()
        val colorB = ImGui.colorConvertFloat4ToU32(rgbB[0], rgbB[1], rgbB[2], 1f)

        dl.addRectFilled(badgeBX, badgeBY, badgeBX + badgeW, badgeBY + badgeH, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
        dl.addRect(badgeBX, badgeBY, badgeBX + badgeW, badgeBY + badgeH, colorB, 4f, 0, 1.5f)

        val textBX = badgeBX + (badgeW - textWB) * 0.5f
        val textBY = badgeBY + (badgeH - textH) * 0.5f
        session.uiTheme.withFont(fontLevel) {
            dl.addText(textBX, textBY, colorB, "B")
        }

        ImGui.setCursorScreenPos(badgeBX, badgeBY)
        ImGui.invisibleButton("##btn_crossfade_deck_b", badgeW, badgeH)
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
            if (session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Deck B (Click to snap crossfader to Deck B)")
            }
        }
        if (ImGui.isItemClicked(0)) {
            mixer.onCrossfadeManualTakeover()
            mixer.crossfade.set(1.0f)
        }

        // 3. Crossfader Slider (Standard track slider style from CustomRangeSlider)
        val gap = 10f
        val lineStartX = badgeAX + badgeW + gap
        val lineEndX = badgeBX - gap
        val lineWidth = (lineEndX - lineStartX).coerceAtLeast(10f)

        val trackPadX = 3f
        val trackW = lineWidth + trackPadX * 2f
        val trackH = maxOf(badgeH, 18f)
        ImGui.setCursorScreenPos(lineStartX - trackPadX, centerY - trackH * 0.5f)
        ImGui.invisibleButton("##crossfader_slider_track", trackW, trackH)

        val isTrackHovered = ImGui.isItemHovered()
        val isTrackActive = ImGui.isItemActive()
        val mouseDown = isTrackActive

        val paramKey = "Mixer/crossfade"
        val isTarget = presetState.midiLearnTarget?.let {
            it is MidiLearnTarget.BaseValueSlider && it.paramKey == paramKey
        } ?: false

        if (presetState.isMidiLearnMode) {
            if (ImGui.isItemClicked(0)) {
                presetState.midiLearnTarget = MidiLearnTarget.BaseValueSlider(paramKey, "Crossfader", mixer.crossfade, -1f, 1f)
            }
        } else if (mouseDown) {
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
            if (ImGui.isMouseClicked(2) || ImGui.isItemClicked(2)) { // Middle-click center reset
                mixer.onCrossfadeManualTakeover()
                mixer.crossfade.set(0.0f)
            }
        }

        if (isTrackHovered && session.uiTheme.tooltipsEnabled) {
            val curVal = mixer.crossfade.value
            val blendText = when {
                curVal <= -0.99f -> "100% Deck A"
                curVal >= 0.99f -> "100% Deck B"
                kotlin.math.abs(curVal) < 0.02f -> "Center (50% A / 50% B)"
                curVal < 0f -> "Deck A: %.0f%% | Deck B: %.0f%%".format((1f - (curVal + 1f) * 0.5f) * 100f, ((curVal + 1f) * 0.5f) * 100f)
                else -> "Deck A: %.0f%% | Deck B: %.0f%%".format((1f - (curVal + 1f) * 0.5f) * 100f, ((curVal + 1f) * 0.5f) * 100f)
            }
            val mapping = session.midiMappingManager.getMappingForParameter(paramKey)
            val midiText = mapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""
            ImGui.setTooltip("Crossfader$midiText: $blendText\nDrag or scroll to blend. Middle-click to center.")
        }

        // --- Render Slider Visuals ---
        // Inactive track line
        val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f)
        dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)

        // Faint vertical marks: ends (-1.0, +1.0), midway (-0.5, +0.5), and middle (0.0)
        val markColFaint = ImGui.colorConvertFloat4ToU32(0.65f, 0.65f, 0.65f, 0.28f)
        val markColCenter = ImGui.colorConvertFloat4ToU32(0.85f, 0.85f, 0.85f, 0.45f)
        val markColEnds = ImGui.colorConvertFloat4ToU32(0.70f, 0.70f, 0.70f, 0.35f)

        // Ends: 0% (-1.0) and 100% (+1.0)
        dl.addLine(lineStartX, centerY - 6f, lineStartX, centerY + 6f, markColEnds, 1.5f)
        dl.addLine(lineEndX, centerY - 6f, lineEndX, centerY + 6f, markColEnds, 1.5f)

        // Midway points: 25% (-0.5) and 75% (+0.5)
        val midLeftX = lineStartX + lineWidth * 0.25f
        val midRightX = lineStartX + lineWidth * 0.75f
        dl.addLine(midLeftX, centerY - 5f, midLeftX, centerY + 5f, markColFaint, 1f)
        dl.addLine(midRightX, centerY - 5f, midRightX, centerY + 5f, markColFaint, 1f)

        // Middle: 50% (0.0 center)
        val centerX = lineStartX + lineWidth * 0.50f
        dl.addLine(centerX, centerY - 8f, centerX, centerY + 8f, markColCenter, 1.5f)

        // Active track line (standard theme color from CustomRangeSlider)
        val themeColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f)
        val valPct = ((mixer.crossfade.baseValue - (-1f)) / 2f).coerceIn(0f, 1f)
        val valHandleX = lineStartX + valPct * lineWidth
        dl.addLine(lineStartX, centerY, valHandleX, centerY, themeColor, 3f)

        // Single handle (standard CustomRangeSlider dimensions and styling)
        val handleW = 6f
        val handleH = 16f
        val handleBgCol = if (isTrackActive) {
            ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f)
        } else {
            ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f)
        }
        val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)

        dl.addRectFilled(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
        dl.addRect(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

        // Hover / Active / MIDI learn highlight
        if (isTarget) {
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, ImGui.colorConvertFloat4ToU32(0f, 0.8f, 1f, 1f), 4f, 0, 1.5f)
        } else if (presetState.isMidiLearnMode) {
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, ImGui.colorConvertFloat4ToU32(0.8f, 0.5f, 0f, 0.4f), 4f, 0, 1f)
        } else if (isTrackHovered || isTrackActive) {
            val borderCol = if (isTrackActive) {
                ImGui.colorConvertFloat4ToU32(0.0f, 0.85f, 1.0f, 1.0f)
            } else {
                ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f)
            }
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, borderCol, 4f, 0, 1.5f)
        }

        // Dynamic modulated value indicator (Amber Gold dot when modulated)
        val hasModulators = mixer.crossfade.modulators.any { !it.bypassed }
        if (hasModulators || mixer.isAutoFading) {
            val livePct = ((mixer.crossfade.value - (-1f)) / 2f).coerceIn(0f, 1f)
            val liveX = lineStartX + livePct * lineWidth
            val dotR = 4f
            val curDotCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 1.0f) // Bright Amber Gold
            dl.addCircleFilled(liveX, centerY, dotR, curDotCol)
            dl.addCircle(liveX, centerY, dotR + 0.5f, ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f), 12, 1.0f)
        }
    }
}

