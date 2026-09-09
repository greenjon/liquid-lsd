package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.presets.PresetManager
import kotlin.math.roundToInt

import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons
import llm.slop.liquidlsd.input.TouchBackendState

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

        val overlayW = 60f
        val monitorBtnW = (availW - overlayW).coerceAtLeast(1f)

        ImGui.setCursorScreenPos(imgScreenX, imgScreenY)
        ImGui.invisibleButton("##main_output_monitor", monitorBtnW, masterH.coerceAtLeast(1f))
        itemTooltip("Main output monitor. Click to focus Preset Grid Mix tab.")
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

        // --- Master Output Overlays: [M] Badge, [🎲 ALL], and Vertical Master Level Fader ---
        val masterThemeCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.82f, 0.65f, 1f) // Mint accent for Master
        val fontLevel = UITheme.FontLevel.H2
        var textW = 0f
        var textH = 0f
        session.uiTheme.withFont(fontLevel) {
            val sz = ImGui.calcTextSize("M")
            textW = sz.x
            textH = sz.y
        }
        val badgePadX = 8f
        val badgePadY = 3f
        val badgeW = (textW + badgePadX * 2f).coerceAtLeast(24f)
        val badgeH = (textH + badgePadY * 2f).coerceAtLeast(24f)
        val badgeMargin = 6f

        val badgeMaxX = imgScreenX + availW - badgeMargin
        val badgeMinX = badgeMaxX - badgeW
        val badgeMaxY = imgScreenY + masterH - badgeMargin
        val badgeMinY = badgeMaxY - badgeH

        // 1. Master Badge Pill [M]
        dlMaster.addRectFilled(badgeMinX, badgeMinY, badgeMaxX, badgeMaxY, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
        dlMaster.addRect(badgeMinX, badgeMinY, badgeMaxX, badgeMaxY, masterThemeCol, 4f, 0, 1.5f)

        val textX = badgeMinX + (badgeW - textW) * 0.5f
        val textY = badgeMinY + (badgeH - textH) * 0.5f
        session.uiTheme.withFont(fontLevel) {
            dlMaster.addText(textX, textY, masterThemeCol, "M")
        }

        ImGui.setCursorScreenPos(badgeMinX, badgeMinY)
        if (ImGui.invisibleButton("##badge_btn_master", badgeW, badgeH) || ImGui.isItemClicked(0)) {
            presetState.activeTopTab = "Mixer"
        }
        itemTooltip("Master output. Click to focus Preset Grid Mix tab.")

        // 2. [🎲 ALL] Button (to the left of [M])
        if (session.uiTheme.randomizationEnabled) {
            val dieW = badgeH
            val dieH = badgeH
            val dieMinX = badgeMinX - 4f - dieW
            ImGui.setCursorScreenPos(dieMinX, badgeMinY)
            val isDieClicked = ImGui.invisibleButton("##btn_rand_all_monitor", dieW, dieH)
            val isDieHovered = ImGui.isItemHovered()
            val isDieActive = ImGui.isItemActive()
            if (isDieClicked) {
                PresetGridUndo.pushUndoState(presetState, mixer)
                mixer.randomizeAll()
            }
            itemTooltip("Randomize all Decks (A, B, BG, PV) and Master parameters (Mixer/randAll).\nClick to randomize with undo support.")

            val dieBg = when {
                isDieActive -> ImGui.colorConvertFloat4ToU32(0.48f, 0.36f, 0.46f, 0.95f)
                isDieHovered -> ImGui.colorConvertFloat4ToU32(0.38f, 0.28f, 0.36f, 0.90f)
                else -> ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.14f, 0.80f)
            }
            val dieBorder = if (isDieHovered) masterThemeCol else ImGui.colorConvertFloat4ToU32(0.35f, 0.30f, 0.38f, 0.8f)
            dlMaster.addRectFilled(dieMinX, badgeMinY, dieMinX + dieW, badgeMinY + dieH, dieBg, 4f)
            dlMaster.addRect(dieMinX, badgeMinY, dieMinX + dieW, badgeMinY + dieH, dieBorder, 4f, 0, 1.5f)

            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                val sz = ImGui.calcTextSize(Icons.DICES)
                val iconX = dieMinX + (dieW - sz.x) * 0.5f
                val iconY = badgeMinY + (dieH - sz.y) * 0.5f
                val iconCol = if (isDieHovered) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f) else ImGui.colorConvertFloat4ToU32(0.85f, 0.85f, 0.85f, 0.9f)
                dlMaster.addText(iconX, iconY, iconCol, Icons.DICES)
            }
        }

        // 3. Vertical Master Level Fader (directly above [M], extending upward)
        val stripW = 14f
        val stripMinX = badgeMinX + (badgeW - stripW) * 0.5f
        val stripMaxY = badgeMinY - 4f
        val stripH = (masterH - badgeH - badgeMargin * 2f - 24f).coerceIn(40f, 140f)
        val stripMinY = stripMaxY - stripH

        ImGui.setCursorScreenPos(stripMinX, stripMinY)
        ImGui.invisibleButton("##fader_master", stripW, stripH)
        val isFaderHovered = ImGui.isItemHovered()
        val isFaderActive = ImGui.isItemActive()

        if (isFaderActive) {
            val mouseY = ImGui.getIO().mousePos.y
            val pct = ((stripMaxY - mouseY) / stripH).coerceIn(0f, 1f)
            mixer.masterLevel = pct
        }

        val io = ImGui.getIO()
        if (isFaderHovered || isFaderActive) {
            if (io.mouseWheel != 0f) {
                val delta = if (io.keyShift) 0.01f else 0.05f
                mixer.masterLevel = (mixer.masterLevel + io.mouseWheel * delta).coerceIn(0f, 1f)
                io.mouseWheel = 0f
            }
            if (ImGui.isMouseClicked(2) || ImGui.isItemClicked(2)) { // Middle-click reset to 100%
                mixer.masterLevel = 1.0f
            }
            itemTooltip("Master Output Level\nDrag or scroll to adjust. Middle-click to reset (100%).")
        }

        // Draw Fader Track
        val faderBg = ImGui.colorConvertFloat4ToU32(0.06f, 0.06f, 0.08f, 0.85f)
        val faderBorder = if (isFaderHovered || isFaderActive) masterThemeCol else ImGui.colorConvertFloat4ToU32(0.25f, 0.28f, 0.35f, 0.7f)
        dlMaster.addRectFilled(stripMinX, stripMinY, stripMinX + stripW, stripMaxY, faderBg, 3f)
        dlMaster.addRect(stripMinX, stripMinY, stripMinX + stripW, stripMaxY, faderBorder, 3f, 0, 1.0f)

        // Draw Filled Level Bar (bottom to top)
        val fillH = stripH * mixer.masterLevel
        val fillTop = stripMaxY - fillH
        if (fillH > 1f) {
            dlMaster.addRectFilled(stripMinX + 2f, fillTop, stripMinX + stripW - 2f, stripMaxY - 1f, masterThemeCol, 2f)
        }

        // Draw Handle Indicator line
        val handleCol = if (isFaderActive) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f) else ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 0.85f)
        dlMaster.addLine(stripMinX + 1f, fillTop, stripMinX + stripW - 1f, fillTop, handleCol, 2f)

        // Restore Y cursor position
        ImGui.setCursorScreenPos(imgScreenX, imgScreenY + masterH)
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- Master Mixer Controls (Single Row: Crossfader) ---
        val masterControlsH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            ImGui.getFrameHeightWithSpacing() + 12f
        }.coerceAtLeast(34f)

        ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.colorConvertFloat4ToU32(0.05f, 0.1f, 0.08f, 0.4f)) // Faint mint background
        ImGui.setCursorScreenPos(imgScreenX, ImGui.getCursorScreenPosY())
        ImGui.beginChild("MasterControls", availW, masterControlsH, true, imgui.flag.ImGuiWindowFlags.NoScrollbar)
        
        // Row 1: Crossfader with Deck A box on left and Deck B box on right
        drawCrossfaderSlider(session, mixer)

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

    private fun drawTouchConsoleHUD(
        session: llm.slop.liquidlsd.SessionContext,
        contentW: Float
    ) {
        val controller = session.touchConsoleController
        val state = controller.backend.state

        if (controller.isElevatingPermissions) {
            ImGui.textDisabled("${Icons.ACTIVITY} Configuring Touchpad Permissions (Polkit)...")
            ImGui.spacing()
            return
        }

        if (state == TouchBackendState.PERMISSION_REQUIRED) {
            val badgeH = 22f
            val dl = ImGui.getWindowDrawList()
            val startX = ImGui.getCursorScreenPosX()
            val startY = ImGui.getCursorScreenPosY()

            ImGui.dummy(contentW, badgeH)
            val isHovered = ImGui.isItemHovered()
            val isClicked = ImGui.isItemClicked(0)

            val bgCol = if (isHovered) ImGui.colorConvertFloat4ToU32(0.35f, 0.15f, 0.05f, 0.9f)
                        else ImGui.colorConvertFloat4ToU32(0.20f, 0.08f, 0.02f, 0.8f)
            val borderCol = ImGui.colorConvertFloat4ToU32(0.90f, 0.50f, 0.10f, 0.8f)
            val textCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.70f, 0.20f, 1.0f)

            dl.addRectFilled(startX, startY, startX + contentW, startY + badgeH, bgCol, 4f)
            dl.addRect(startX, startY, startX + contentW, startY + badgeH, borderCol, 4f, 0, 1.0f)

            val label = "${Icons.ALERT} Touchpad: Permission Required (Click to Install udev Access)"
            val textW = ImGui.calcTextSize(label).x
            val textX = startX + (contentW - textW) * 0.5f
            val textY = startY + (badgeH - ImGui.getTextLineHeight()) * 0.5f
            dl.addText(textX, textY, textCol, label)

            if (isHovered) {
                ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
                itemTooltip("Click to run Polkit elevation (pkexec) to grant non-root touch access for the Performance Console")
            }
            if (isClicked) {
                controller.requestPermissionElevation()
            }
            ImGui.spacing()
        }
    }

    private fun drawCrossfaderSlider(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer
    ) {
        val contentW = ImGui.getContentRegionAvailX()
        drawTouchConsoleHUD(session, contentW)

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

        // Reserve row space using actual available content width
        ImGui.dummy(contentW, rowH)
        val dl = ImGui.getWindowDrawList()

        // 1. Deck A Box (styled identically to Deck A monitor badge)
        val badgeAX = startX + 1f
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
            itemTooltip("Deck A (Click to snap crossfader to Deck A)")
        }
        if (ImGui.isItemClicked(0)) {
            mixer.onCrossfadeManualTakeover()
            mixer.crossfade.set(-1.0f)
        }

        // 2. Deck B Box & Transition Picker
        val gap = 10f
        val transBtnW = 140f
        val badgeBX = startX + contentW - badgeW - transBtnW - gap - 1f
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
            itemTooltip("Deck B (Click to snap crossfader to Deck B)")
        }
        if (ImGui.isItemClicked(0)) {
            mixer.onCrossfadeManualTakeover()
            mixer.crossfade.set(1.0f)
        }

        // Transition Shader Selector Button
        val transBtnX = badgeBX + badgeW + gap
        ImGui.setCursorScreenPos(transBtnX, badgeBY)
        val transName = mixer.transitionFilter?.displayName ?: "Default Blend"
        if (ImGui.button("${Icons.SETTINGS} $transName##trans_picker_btn", transBtnW, badgeH)) {
            ShaderPickerPopup.show("Select Mixer Transition", ShaderPickerPopup.PickerType.MIXER_TRANSITION) { id ->
                mixer.setTransition(id)
            }
        }
        itemTooltip("Select ISF transition shader (wipes, glitches, dissolves) or default non-ISF blend modes.")

        // 3. Crossfader Slider (Standard track slider style from CustomRangeSlider)
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
            val mapping = session.midiMappingManager.getMappingForParameter(paramKey)
            val midiText = mapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""
            itemTooltip("Crossfader$midiText\nDrag or scroll to blend. Middle-click to center.")
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

        // Active track line (Zero-centered bipolar slider from -1.0 [Deck A] to +1.0 [Deck B])
        val valPct = ((mixer.crossfade.baseValue - (-1f)) / 2f).coerceIn(0f, 1f)
        val valHandleX = lineStartX + valPct * lineWidth
        val barColor = if (mixer.crossfade.baseValue < 0f) colorA else colorB
        if (kotlin.math.abs(valHandleX - centerX) > 0.5f) {
            dl.addLine(centerX, centerY, valHandleX, centerY, barColor, 3f)
        }

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

        // Touch Console active border & contact dots
        if (session.touchConsoleController.isActive) {
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, ImGui.colorConvertFloat4ToU32(0.0f, 0.9f, 1.0f, 0.75f), 4f, 0, 1.5f)

            val xfContacts = session.touchConsoleController.getCrossfaderContacts()
            if (xfContacts.isNotEmpty()) {
                val lastContact = xfContacts.last()
                for (c in xfContacts) {
                    val rawX = c.x
                    val mappedPct = when {
                        rawX <= 0.05f -> 0.0f
                        rawX >= 0.95f -> 1.0f
                        kotlin.math.abs(rawX - 0.50f) <= 0.02f -> 0.5f
                        else -> ((rawX - 0.05f) / 0.90f).coerceIn(0.0f, 1.0f)
                    }
                    val cx = lineStartX + mappedPct * lineWidth
                    if (c == lastContact) {
                        dl.addCircleFilled(cx, centerY, 5.0f, ImGui.colorConvertFloat4ToU32(0.0f, 0.95f, 1.0f, 1.0f))
                        dl.addCircle(cx, centerY, 7.5f, ImGui.colorConvertFloat4ToU32(0.0f, 0.95f, 1.0f, 0.5f), 12, 1.5f)
                    } else {
                        dl.addCircleFilled(cx, centerY, 4.0f, ImGui.colorConvertFloat4ToU32(1.0f, 0.70f, 0.15f, 0.85f))
                        dl.addCircle(cx, centerY, 5.5f, ImGui.colorConvertFloat4ToU32(1.0f, 0.70f, 0.15f, 0.4f), 12, 1.0f)
                    }
                }
            }
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

