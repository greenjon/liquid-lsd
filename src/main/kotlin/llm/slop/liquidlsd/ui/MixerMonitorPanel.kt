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
        drawFlatSlider(session, "Mixer/crossfade", "Crossfader", mixer.crossfade, -1f, 1f, 80f, -1f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f), "Blend between Deck A (-1.0) and Deck B (1.0). Deck C runs in parallel as a preview.", mixer = mixer) {
            ""
        }

        ImGui.spacing()

        // --- Momentary Controls: Playlist Prev/Next & Randomize A/B/C/All ---
        val spacingX = ImGui.getStyle().itemSpacing.x
        val totalAvailW = ImGui.getContentRegionAvailX()
        val numButtons = if (session.uiTheme.randomizationEnabled) 6 else 2
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
                ImGui.setTooltip("Randomize Deck A modulators & base values (Mixer/randDeckA).\nDoes not trigger manual takeover; can be modulated by CV or MIDI concurrently.")
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
                ImGui.setTooltip("Randomize Deck B modulators & base values (Mixer/randDeckB).\nDoes not trigger manual takeover; can be modulated by CV or MIDI concurrently.")
            }
            ImGui.popStyleColor(3)

            ImGui.sameLine()

            // Rand C Button
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.28f, 0.20f, 0.26f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.38f, 0.28f, 0.36f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.48f, 0.36f, 0.46f, 1f))
            if (ImGui.button("${Icons.DICES} C##rand_deck_c", mBtnW, mBtnH)) {
                PresetGridUndo.pushUndoState(presetState, mixer)
                mixer.randomizeDeckC()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Randomize Deck C modulators & base values (Mixer/randDeckC).\nDoes not trigger manual takeover; can be modulated by CV or MIDI concurrently.")
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
                ImGui.setTooltip("Randomize all Decks (A, B, C) and Master parameters (Mixer/randAll).\nDoes not trigger manual takeover; can be modulated by CV or MIDI concurrently.")
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

        // --- Deck Monitors ---
        val btnW = 40f
        val padding = 16f
        val halfW = ((availW - padding) * 0.5f).coerceAtLeast(1f)
        
        val startX = baseScreenX + offsetX
        val centerY = ImGui.getCursorScreenPosY()
        val deckBStartX = startX + halfW + padding

        // --- Render child panels ---
        val subH = layout.deckChildHeight.coerceAtLeast(1f)
        val deckCH = layout.deckCHeight.coerceAtLeast(1f)
        
        val childY = centerY
        
        ImGui.setCursorScreenPos(startX, childY)
        drawDeckControls(mixer, "Deck A", mixer.deckA, halfW, subH, true)
        
        ImGui.setCursorScreenPos(deckBStartX, childY)
        drawDeckControls(mixer, "Deck B", mixer.deckB, halfW, subH, false)
        
        val endY = ImGui.getCursorScreenPosY()
        ImGui.setCursorScreenPos(startX, endY)

        // --- Deck C / Preview Monitor ---
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // Interactive top preset bar for Deck C
        ImGui.setCursorScreenPos(startX, ImGui.getCursorScreenPosY())
        drawDeckMonitorToolbar(session, "Deck C", mixer.deckC, isDeckA = false, isDeckC = true, mixer = mixer, onSaveDeck = onSaveDeck, onEjectDeck = onEjectDeck, targetW = availW)
        ImGui.spacing()
        
        val imgX = startX
        val imgY = ImGui.getCursorScreenPosY()
        
        val dlPreview = ImGui.getWindowDrawList()
        dlPreview.addRectFilled(imgX, imgY, imgX + availW, imgY + deckCH, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))

        ImGui.image(mixer.deckC.getOutputTexture(), availW, deckCH, 0f, 1f, 1f, 0f)

        ImGui.setCursorScreenPos(imgX, imgY)
        ImGui.invisibleButton("##drag_source_C", availW.coerceAtLeast(1f), deckCH.coerceAtLeast(1f))
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Interactive Deck C monitor. Click to focus Preset Grid, drag to copy/move/swap, or drop preset files to load.")
        }
        if (ImGui.isItemClicked(0)) {
            presetState.activeTopTab = "Deck C"
        }

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("MONITOR_DRAG", "C")
            ImGui.text("Move Deck C")
            ImGui.endDragDropSource()
        }

        if (ImGui.beginDragDropSource(128)) { // 128 = ImGuiDragDropFlags.SourceButtonMouseButtonRight
            ImGui.setDragDropPayload("MONITOR_DRAG_RIGHT", "C")
            ImGui.text("Copy/Move/Swap Deck C")
            ImGui.endDragDropSource()
        }

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = java.io.File(payload)
                if (file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                    session.presetManager.loadDeckPresetAsync(file, isDeckA = false, isDeckC = true)
                }
            }
            val payloadMonitor = ImGui.acceptDragDropPayload<String>("MONITOR_DRAG")
            if (payloadMonitor != null) {
                val fromName = payloadMonitor
                val toDeck = mixer.deckC
                val fromDeck = if (fromName == "A") mixer.deckA
                               else if (fromName == "B") mixer.deckB
                               else mixer.deckC
                if (fromDeck !== toDeck) {
                    onUtilityAction(0, fromDeck, toDeck)
                }
            }
            val payloadMonitorRight = ImGui.acceptDragDropPayload<String>("MONITOR_DRAG_RIGHT")
            if (payloadMonitorRight != null) {
                pendingRightDragFrom = payloadMonitorRight
                ImGui.openPopup("monitor_drag_menu_C")
            }
            ImGui.endDragDropTarget()
        }

        if (ImGui.beginPopup("monitor_drag_menu_C")) {
            val fromName = pendingRightDragFrom
            if (fromName != null) {
                val fromDeck = if (fromName == "A") mixer.deckA
                               else if (fromName == "B") mixer.deckB
                               else mixer.deckC
                val toDeck = mixer.deckC
                if (ImGui.menuItem("Move")) {
                    onUtilityAction(0, fromDeck, toDeck)
                }
                if (ImGui.menuItem("Copy")) {
                    onUtilityAction(1, fromDeck, toDeck)
                }
                if (ImGui.menuItem("Swap")) {
                    onUtilityAction(2, fromDeck, toDeck)
                }
            }
            ImGui.endPopup()
        }

        val deckCColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.5f, 1f)
        dlPreview.addRect(imgX - 1f, imgY - 1f, imgX + availW + 1f, imgY + layout.deckCHeight + 1f, deckCColor, 0f, 0, 2f)

        // Draw lower-left letter badge overlay ("C") on Deck C monitor
        val letterC = "C"
        val badgePadXC = 8f
        val badgePadYC = 3f
        val fontLevelC = UITheme.FontLevel.H2
        var textWC = 0f
        var textHC = 0f
        session.uiTheme.withFont(fontLevelC) {
            val sz = ImGui.calcTextSize(letterC)
            textWC = sz.x
            textHC = sz.y
        }
        val badgeWC = (textWC + badgePadXC * 2f).coerceAtLeast(24f)
        val badgeHC = (textHC + badgePadYC * 2f).coerceAtLeast(24f)
        val badgeMarginC = 6f
        val badgeMinXC = imgX + badgeMarginC
        val badgeMaxYC = imgY + layout.deckCHeight - badgeMarginC
        val badgeMinYC = badgeMaxYC - badgeHC
        val badgeMaxXC = badgeMinXC + badgeWC

        dlPreview.addRectFilled(badgeMinXC, badgeMinYC, badgeMaxXC, badgeMaxYC, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
        dlPreview.addRect(badgeMinXC, badgeMinYC, badgeMaxXC, badgeMaxYC, deckCColor, 4f, 0, 1.5f)

        val textXC = badgeMinXC + (badgeWC - textWC) * 0.5f
        val textYC = badgeMinYC + (badgeHC - textHC) * 0.5f
        session.uiTheme.withFont(fontLevelC) {
            dlPreview.addText(textXC, textYC, deckCColor, letterC)
        }
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
