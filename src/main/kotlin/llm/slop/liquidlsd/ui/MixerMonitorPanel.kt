package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.presets.PresetManager

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
            aspectRatio = session.uiTheme.renderAspectRatio
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
        val masterControlsH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            (ImGui.getFrameHeightWithSpacing() * 3f) + (ImGui.getStyle().itemSpacing.y * 2f) + 8f
        }.coerceAtLeast(85f)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.colorConvertFloat4ToU32(0.05f, 0.1f, 0.08f, 0.4f)) // Faint mint background
        ImGui.setCursorScreenPos(imgScreenX, ImGui.getCursorScreenPosY())
        ImGui.beginChild("MasterControls", availW, masterControlsH, true, imgui.flag.ImGuiWindowFlags.NoScrollbar)
        
        // Crossfader (mapped display value from -1.0 to 1.0)
        drawFlatSlider(session, "Mixer/crossfade", "Crossfader", mixer.crossfade, -1f, 1f, 80f, -1f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f), "Blend between Deck A (-1.0) and Deck B (1.0). Deck PV runs in parallel as a preview.", mixer = mixer) {
            ""
        }

        ImGui.spacing()

        // --- Momentary Controls: Playlist Prev/Next & Randomize A/B/C/All ---
        val spacingX = ImGui.getStyle().itemSpacing.x
        val totalAvailW = ImGui.getContentRegionAvailX()
        val numButtons = if (session.uiTheme.randomizationEnabled) 7 else 2
        val mBtnW = ((totalAvailW - (spacingX * (numButtons - 1))) / numButtons).coerceAtLeast(20f)
        val mBtnH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFrameHeight() * 0.9f }.coerceAtLeast(20f)

        // Prev Button
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.18f, 0.26f, 0.32f, 1f))
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.36f, 0.45f, 1f))
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.32f, 0.46f, 0.58f, 1f))
        if (ImGui.button("< Prev##queue_prev", mBtnW, mBtnH)) {
            session.playQueueManager.triggerPrevious(mixer)
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Trigger previous preset in the playlist queue (Mixer/queuePrev).\nDoes not trigger manual takeover; can be modulated by CV or MIDI concurrently.")
        }
        ImGui.popStyleColor(3)

        ImGui.sameLine()

        // Next Button
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.18f, 0.26f, 0.32f, 1f))
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.36f, 0.45f, 1f))
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.32f, 0.46f, 0.58f, 1f))
        if (ImGui.button("Next >##queue_next", mBtnW, mBtnH)) {
            session.playQueueManager.triggerNext(mixer)
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Trigger next preset in the playlist queue (Mixer/queueNext).\nDoes not trigger manual takeover; can be modulated by CV or MIDI concurrently.")
        }
        ImGui.popStyleColor(3)

        if (session.uiTheme.randomizationEnabled) {
            ImGui.sameLine()

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

        ImGui.spacing()

        drawFlatSlider(session, "Mixer/xfadeSpeed", "Fade Speed", mixer.xfadeSpeed, 0.1f, 30.0f, 80f, 0.1f, 30.0f, ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f), "Adjust transition duration for automatic crossfading and Auto-VJ transitions.", mixer = mixer) {
            "%.1fs".format(it)
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

    fun drawFlatSlider(
        session: llm.slop.liquidlsd.SessionContext,
        paramKey: String,
        label: String,
        param: ModulatableParameter,
        min: Float,
        max: Float,
        labelW: Float = 100f,
        displayMin: Float = min,
        displayMax: Float = max,
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f),
        tooltip: String? = null,
        mixer: Mixer? = null,
        formatValue: (Float) -> String = { "%.3f".format(it) }
    ) {
        ImGui.pushID(label)

        val totalAvailW = ImGui.getContentRegionAvailX()
        var textW = 0f
        session.uiTheme.withFont(UITheme.FontLevel.BODY) { textW = ImGui.calcTextSize(label).x }
        session.uiTheme.body(label)

        val minSliderW = 40f
        val canFitSameLine = totalAvailW - (textW + 15f) >= minSliderW
        if (canFitSameLine) {
            ImGui.sameLine(textW + 15f)
        } else {
            ImGui.spacing()
        }

        val barStartX = ImGui.getCursorScreenPosX()
        val barScreenY = ImGui.getCursorScreenPosY() + 3f
        val barW = (ImGui.getContentRegionAvailX() - 5f).coerceAtLeast(minSliderW)
        val barH = maxOf(14f, session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() * 0.75f })

        ImGui.invisibleButton("##slider", barW, barH)
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            val baseTip = tooltip ?: "Click and drag to adjust $label."
            ImGui.setTooltip(baseTip)
        }

        val isTarget = presetState.midiLearnTarget?.let {
            it is MidiLearnTarget.BaseValueSlider && it.paramKey == paramKey
        } ?: false

        if (presetState.isMidiLearnMode) {
            if (ImGui.isItemClicked(0)) {
                presetState.midiLearnTarget = MidiLearnTarget.BaseValueSlider(paramKey, label, param, min, max)
            }
        } else if (ImGui.isItemActive()) {
            if (paramKey == "Mixer/crossfade") {
                mixer?.onCrossfadeManualTakeover()
            }
            val mouseX = ImGui.getIO().mousePos.x
            val pct = if (barW > 0f) ((mouseX - barStartX) / barW).coerceIn(0f, 1f) else 0f
            val newValue = min + pct * (max - min)
            param.set(newValue)
        }

        val valueRange = max - min
        val displayRange = displayMax - displayMin

        // Draw the flat bar visual using DrawList
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(
            barStartX, barScreenY,
            barStartX + barW, barScreenY + barH,
            ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f),
            3f
        )

        // Draw learning highlight if active
        if (isTarget) {
            dl.addRect(
                barStartX - 1f, barScreenY - 1f,
                barStartX + barW + 1f, barScreenY + barH + 1f,
                ImGui.colorConvertFloat4ToU32(0f, 0.8f, 1f, 1f),
                3f,
                0,
                1.5f
            )
        } else if (presetState.isMidiLearnMode) {
            // Subtle dotted or low alpha border to show map-ability
            dl.addRect(
                barStartX, barScreenY,
                barStartX + barW, barScreenY + barH,
                ImGui.colorConvertFloat4ToU32(0.8f, 0.5f, 0f, 0.4f),
                3f,
                0,
                1f
            )
        }

        // Fill mapping slider value
        val activeVal = if (paramKey == "Mixer/crossfade" || param.modulators.any { !it.bypassed }) param.value else param.baseValue
        val currentDisplayVal = displayMin + if (valueRange > 0f) ((activeVal - min) / valueRange) * displayRange else 0f
        val pct = if (valueRange > 0f) ((activeVal - min) / valueRange).coerceIn(0f, 1f) else 0f

        val isBipolar = min < 0f
        if (isBipolar && valueRange > 0f) {
            val centerPct = ((0f - min) / valueRange).coerceIn(0f, 1f)
            val startX = barStartX + barW * centerPct
            val endX = barStartX + barW * pct
            val x1 = minOf(startX, endX)
            val x2 = maxOf(startX, endX)
            if (x2 > x1) {
                dl.addRectFilled(
                    x1, barScreenY,
                    x2, barScreenY + barH,
                    themeColor,
                    3f
                )
            }
            // Draw a subtle vertical line at the center to mark the zero point
            val zeroCol = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.8f)
            dl.addLine(startX, barScreenY - 1f, startX, barScreenY + barH + 1f, zeroCol, 1.5f)
        } else {
            val fillWidth = barW * pct
            if (fillWidth > 0f) {
                dl.addRectFilled(
                    barStartX, barScreenY,
                    barStartX + fillWidth, barScreenY + barH,
                    themeColor,
                    3f
                )
            }
        }

        // Draw fader position thumb indicator (especially helpful for live crossfader modulation)
        if (paramKey == "Mixer/crossfade" && valueRange > 0f) {
            val thumbX = (barStartX + barW * pct).coerceIn(barStartX + 1f, barStartX + barW - 1f)
            val thumbCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.95f)
            dl.addLine(thumbX, barScreenY - 1.5f, thumbX, barScreenY + barH + 1.5f, thumbCol, 2.5f)
        }

        // MIDI mapped indicator
        val mapping = session.midiMappingManager.getMappingForParameter(paramKey)
        val midiIndicator = mapping?.let { m ->
            if (m.channel == 0) "[CC ${m.cc}]" else "[Ch ${m.channel + 1} CC ${m.cc}]"
        }

        // Value text overlay
        val baseValStr = formatValue(currentDisplayVal)
        val valStr = if (midiIndicator != null) {
            if (baseValStr.isNotEmpty()) "$midiIndicator $baseValStr" else midiIndicator
        } else {
            baseValStr
        }
        
        if (valStr.isNotEmpty()) {
            val textWidth = ImGui.calcTextSize(valStr).x
            val valTextH = ImGui.calcTextSize(valStr).y
            val valTextX = barStartX + barW - textWidth - 5f
            val valTextY = barScreenY + (barH - valTextH) * 0.5f

            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                dl.addText(valTextX, valTextY, ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 0.8f), valStr)
            }
        }

        ImGui.popID()
    }
}
