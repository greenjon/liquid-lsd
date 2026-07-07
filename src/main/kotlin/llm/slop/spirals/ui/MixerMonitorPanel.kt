package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.models.toDto
import llm.slop.spirals.models.applyDto
import llm.slop.spirals.patches.PatchManager

class MixerMonitorPanel(
    private val patchState: PatchGridState,
    private val drawDeckControls: (Mixer, String, Deck, Float, Float, Boolean) -> Unit,
    private val onUtilityAction: (Int, Deck, Deck) -> Unit, // (mode: 0=Move, 1=Copy, 2=Swap, from, to)
    private val onSaveDeck: (Deck, Boolean, Boolean) -> Unit
) {
    private var pendingRightDragFrom: String? = null

    fun draw(mixer: Mixer) {
        val style = ImGui.getStyle()
        val layout = MixerMonitorLayoutCalculator.calculate(
            windowWidth = ImGui.getWindowWidth(),
            availableHeight = ImGui.getContentRegionAvailY(),
            windowPaddingX = style.getWindowPaddingX(),
            scrollbarWidth = style.getScrollbarSize(),
            textLineHeightWithSpacing = ImGui.getTextLineHeightWithSpacing(),
            frameHeightWithSpacing = ImGui.getFrameHeightWithSpacing(),
            itemSpacingY = style.getItemSpacingY()
        )
        val availW = layout.contentWidth
        val masterH = layout.masterHeight

        val imgScreenX = ImGui.getCursorScreenPosX()
        val imgScreenY = ImGui.getCursorScreenPosY()

        ImGui.image(mixer.masterFBO.texture, availW, masterH, 0f, 1f, 1f, 0f)

        // Restore Y cursor position
        ImGui.setCursorScreenPos(imgScreenX, imgScreenY + masterH)
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- Master Mixer Controls ---
        ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.colorConvertFloat4ToU32(0.05f, 0.1f, 0.08f, 0.4f)) // Faint mint background
        ImGui.beginChild("MasterControls", availW, 85f, true)
        
        // Crossfader (mapped display value from -1.0 to 1.0)
        drawFlatSlider("Mixer/crossfade", "Crossfader", mixer.crossfade, -1f, 1f, 80f, -1f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f), "Blend between Deck A (-1.0) and Deck B (1.0). Deck C runs in parallel as a preview.") {
            ""
        }
        if (ImGui.isItemActive()) {
            mixer.isAutoFading = false
            mixer.targetCrossfade = mixer.crossfade.baseValue
        }
        
        ImGui.spacing()
        
        // Fade Speed (Mixer/xfadeSpeed)
        drawFlatSlider("Mixer/xfadeSpeed", "Fade Speed", mixer.xfadeSpeed, 0.1f, 30.0f, 80f, 0.1f, 30.0f, ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f), "Adjust transition duration for automatic crossfading and Auto-VJ transitions.") {
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
        val halfW = (availW - padding) * 0.5f
        
        val startX = ImGui.getCursorScreenPosX()
        val centerY = ImGui.getCursorScreenPosY()
        
        // Use an invisible full-width dummy to reserve vertical space for the two rows
        val headerRowH = ImGui.getTextLineHeightWithSpacing()
        val presetRowH = ImGui.getFrameHeightWithSpacing()
        ImGui.dummy(availW, headerRowH + presetRowH)
        
        // 1. Deck A Header
        var twA = 0f
        var twB = 0f
        UITheme.withFont(UITheme.FontLevel.H2) {
            twA = ImGui.calcTextSize("Deck A").x
            twB = ImGui.calcTextSize("Deck B").x
        }
        
        ImGui.setCursorScreenPos(startX + (halfW - twA) * 0.5f, centerY)
        UITheme.h2("Deck A")
        
        // 3. Deck B Header
        val deckBStartX = startX + halfW + padding
        ImGui.setCursorScreenPos(deckBStartX + (halfW - twB) * 0.5f, centerY)
        UITheme.h2("Deck B")
        
        // 4. Presets Row
        val presetY = centerY + headerRowH
        
        val activePresetA = PatchManager.activePresetA ?: "None"
        val isDirtyA = PatchManager.isDeckDirty(mixer.deckA, mixer)
        val displayNameA = if (isDirtyA) "$activePresetA *" else activePresetA
        
        ImGui.setCursorScreenPos(startX, presetY + 3f)
        UITheme.body("Preset: $displayNameA")
        ImGui.sameLine(0f, 6f)
        if (UITheme.iconButton("##SaveA", Icons.SAVE, "Save or save as a new preset for Deck A.", size = ImGui.getFrameHeight())) {
            ImGui.openPopup("save_menu_A")
        }
        if (ImGui.beginPopup("save_menu_A")) {
            if (ImGui.menuItem("Save")) {
                onSaveDeck(mixer.deckA, true, false)
            }
            if (ImGui.menuItem("Save As...")) {
                onSaveDeck(mixer.deckA, true, true)
            }
            ImGui.endPopup()
        }
        
        val activePresetB = PatchManager.activePresetB ?: "None"
        val isDirtyB = PatchManager.isDeckDirty(mixer.deckB, mixer)
        val displayNameB = if (isDirtyB) "$activePresetB *" else activePresetB
        
        ImGui.setCursorScreenPos(deckBStartX, presetY + 3f)
        UITheme.body("Preset: $displayNameB")
        ImGui.sameLine(0f, 6f)
        if (UITheme.iconButton("##SaveB", Icons.SAVE, "Save or save as a new preset for Deck B.", size = ImGui.getFrameHeight())) {
            ImGui.openPopup("save_menu_B")
        }
        if (ImGui.beginPopup("save_menu_B")) {
            if (ImGui.menuItem("Save")) {
                onSaveDeck(mixer.deckB, false, false)
            }
            if (ImGui.menuItem("Save As...")) {
                onSaveDeck(mixer.deckB, false, true)
            }
            ImGui.endPopup()
        }
        
        ImGui.spacing()
        
        // --- Render the exact exact child panels ---
        val subH = layout.deckChildHeight
        
        val childY = ImGui.getCursorScreenPosY()
        
        ImGui.setCursorScreenPos(startX, childY)
        drawDeckControls(mixer, "Deck A", mixer.deckA, halfW, subH, true)
        
        ImGui.setCursorScreenPos(deckBStartX, childY)
        drawDeckControls(mixer, "Deck B", mixer.deckB, halfW, subH, false)
        
        val endY = ImGui.getCursorScreenPosY()
        ImGui.setCursorScreenPos(startX, endY)

        // --- Deck C / Preview Monitor (Aligned to Center / Full Width) ---
        val previewLabel = "DECK C / PREVIEW"
        
        // Push Deck C monitor to the bottom of the panel
        val contentHeightRemaining = ImGui.getContentRegionAvailY()
        val deckCHeightNeeded = layout.deckCHeight + ImGui.getFrameHeightWithSpacing() * 2f + 20f
        
        if (contentHeightRemaining > deckCHeightNeeded) {
            ImGui.setCursorPosY(ImGui.getCursorPosY() + (contentHeightRemaining - deckCHeightNeeded))
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        
        // Row 1: Monitor Label
        val row1Y = ImGui.getCursorScreenPosY()
        var twC = 0f
        UITheme.withFont(UITheme.FontLevel.H2) {
            twC = ImGui.calcTextSize(previewLabel).x
        }
        ImGui.setCursorScreenPos(startX + (availW - twC) * 0.5f, row1Y)
        UITheme.h2(previewLabel)

        // Row 2: Preset Info
        val activePresetC = PatchManager.activePresetC ?: "None"
        val isDirtyC = PatchManager.isDeckDirty(mixer.deckC, mixer)
        val displayNameC = if (isDirtyC) "$activePresetC *" else activePresetC
        ImGui.setCursorScreenPos(startX, row1Y + ImGui.getTextLineHeightWithSpacing())
        UITheme.body("Preset: $displayNameC")
        ImGui.sameLine(0f, 6f)
        if (UITheme.iconButton("##SaveC", Icons.SAVE, "Save or save as a new preset for Deck C.", size = ImGui.getFrameHeight())) {
            ImGui.openPopup("save_menu_C")
        }
        if (ImGui.beginPopup("save_menu_C")) {
            if (ImGui.menuItem("Save")) {
                onSaveDeck(mixer.deckC, false, false) // Note: boolean isDeckA is overloaded for Deck C in some contexts, but here it's just a save trigger
            }
            if (ImGui.menuItem("Save As...")) {
                onSaveDeck(mixer.deckC, false, true)
            }
            ImGui.endPopup()
        }
        
        val imgX = ImGui.getCursorScreenPosX()
        val imgY = ImGui.getCursorScreenPosY()
        
        ImGui.image(mixer.deckC.getOutputTexture(), availW, layout.deckCHeight, 0f, 1f, 1f, 0f)

        ImGui.setCursorScreenPos(imgX, imgY)
        ImGui.invisibleButton("##drag_source_C", availW, layout.deckCHeight)
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Interactive Deck C monitor. Drag to copy/move/swap, or drop patch files to load.")
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
                    PatchManager.loadDeckPresetAsync(file, isDeckA = false, isDeckC = true)
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
        val dlPreview = ImGui.getWindowDrawList()
        dlPreview.addRect(imgX - 1f, imgY - 1f, imgX + availW + 1f, imgY + layout.deckCHeight + 1f, deckCColor, 0f, 0, 2f)
    }

    fun drawFlatSlider(
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
        formatValue: (Float) -> String = { "%.3f".format(it) }
    ) {
        ImGui.pushID(label)

        var textW = 0f
        UITheme.withFont(UITheme.FontLevel.BODY) { textW = ImGui.calcTextSize(label).x }
        UITheme.body(label)
        ImGui.sameLine(textW + 15f)

        val barStartX = ImGui.getCursorScreenPosX()
        val barScreenY = ImGui.getCursorScreenPosY() + 3f
        val barW = ImGui.getContentRegionAvailX() - 5f
        val barH = 14f

        ImGui.invisibleButton("##slider", barW, barH)
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            val baseTip = tooltip ?: "Click and drag to adjust $label."
            ImGui.setTooltip(baseTip)
        }

        val isTarget = patchState.midiLearnTarget?.let {
            it is MidiLearnTarget.BaseValueSlider && it.paramKey == paramKey
        } ?: false

        if (patchState.isMidiLearnMode) {
            if (ImGui.isItemClicked(0)) {
                patchState.midiLearnTarget = MidiLearnTarget.BaseValueSlider(paramKey, label, param, min, max)
            }
        } else if (ImGui.isItemActive()) {
            val mouseX = ImGui.getIO().mousePos.x
            val pct = ((mouseX - barStartX) / barW).coerceIn(0f, 1f)
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
        } else if (patchState.isMidiLearnMode) {
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
        val currentDisplayVal = displayMin + if (valueRange > 0f) ((param.baseValue - min) / valueRange) * displayRange else 0f
        val pct = if (valueRange > 0f) ((param.baseValue - min) / valueRange).coerceIn(0f, 1f) else 0f

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

        // MIDI mapped indicator
        val mapping = llm.slop.spirals.midi.MidiMappingManager.getMappingForParameter(paramKey)
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

            UITheme.withFont(UITheme.FontLevel.CAPTION) {
                dl.addText(valTextX, valTextY, ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 0.8f), valStr)
            }
        }

        ImGui.popID()
    }
}
