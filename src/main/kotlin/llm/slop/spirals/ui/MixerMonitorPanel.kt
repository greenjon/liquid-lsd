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
    private val advanceSetlist: (Int) -> Unit,
    private val drawDeckControls: (Mixer, String, Deck, Float, Float, Boolean) -> Unit
) {
    private var utilityMode = 1 // 0=Move, 1=Copy, 2=Swap

    fun draw(mixer: Mixer) {
        val availW = ImGui.getContentRegionAvailX()
        val masterH = availW * (9f / 16f)

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
        ImGui.beginChild("MasterControls", availW, 55f, true)
        
        // Crossfader (mapped display value from -1.0 to 1.0)
        drawFlatSlider("Mixer/crossfade", "Crossfader", mixer.crossfade, 0f, 1f, 80f, -1f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f)) {
            ""
        }
        if (ImGui.isItemActive()) {
            mixer.isAutoFading = false
            mixer.targetCrossfade = mixer.crossfade.baseValue
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
        
        val activePresetB = PatchManager.activePresetB ?: "None"
        val isDirtyB = PatchManager.isDeckDirty(mixer.deckB, mixer)
        val displayNameB = if (isDirtyB) "$activePresetB *" else activePresetB
        
        ImGui.setCursorScreenPos(deckBStartX, presetY + 3f)
        UITheme.body("Preset: $displayNameB")
        
        ImGui.spacing()
        
        // --- Render the exact exact child panels ---
        val subH = halfW * (9f / 16f)
        
        val childY = ImGui.getCursorScreenPosY()
        
        ImGui.setCursorScreenPos(startX, childY)
        drawDeckControls(mixer, "Deck A", mixer.deckA, halfW, subH, true)
        
        ImGui.setCursorScreenPos(deckBStartX, childY)
        drawDeckControls(mixer, "Deck B", mixer.deckB, halfW, subH, false)
        
        val endY = ImGui.getCursorScreenPosY()
        ImGui.setCursorScreenPos(startX, endY)

        // --- Deck C / Preview Monitor (Aligned to Lower Left) ---
        val previewMode = UITheme.previewModeEnabled
        val previewLabel = if (previewMode) "PREVIEW MONITOR" else "DECK C MONITOR"
        
        // Push Deck C monitor to the bottom of the panel
        val contentHeightRemaining = ImGui.getContentRegionAvailY()
        val deckCHeightNeeded = subH + ImGui.getFrameHeightWithSpacing() * 2f + 20f
        
        if (contentHeightRemaining > deckCHeightNeeded) {
            ImGui.setCursorPosY(ImGui.getCursorPosY() + (contentHeightRemaining - deckCHeightNeeded))
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        
        // Row 1: Monitor Label and Toggle
        val row1Y = ImGui.getCursorScreenPosY()
        ImGui.setCursorScreenPos(startX, row1Y)
        UITheme.body(previewLabel)
        ImGui.sameLine(halfW - 80f)
        if (ImGui.checkbox("Preview Mode", UITheme.previewModeEnabled)) {
            UITheme.previewModeEnabled = !UITheme.previewModeEnabled
            UITheme.saveSettings()
        }

        // Row 2: Preset Info
        val activePresetC = PatchManager.activePresetC ?: "None"
        val isDirtyC = PatchManager.isDeckDirty(mixer.deckC, mixer)
        val displayNameC = if (isDirtyC) "$activePresetC *" else activePresetC
        ImGui.setCursorScreenPos(startX, row1Y + ImGui.getFrameHeightWithSpacing())
        UITheme.body("Preset: $displayNameC")
        
        val imgX = ImGui.getCursorScreenPosX()
        val imgY = ImGui.getCursorScreenPosY()
        
        ImGui.image(mixer.deckC.getOutputTexture(), halfW, subH, 0f, 1f, 1f, 0f)

        val deckCColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.5f, 1f)
        val dlPreview = ImGui.getWindowDrawList()
        dlPreview.addRect(imgX - 1f, imgY - 1f, imgX + halfW + 1f, imgY + subH + 1f, deckCColor, 0f, 0, 2f)

        // --- Utility Grid (to the right of Deck C) ---
        ImGui.setCursorScreenPos(deckBStartX, row1Y)
        drawUtilityGrid(mixer, halfW, subH + ImGui.getFrameHeightWithSpacing() * 2f)
    }

    private fun drawUtilityGrid(mixer: Mixer, width: Float, height: Float) {
        ImGui.beginChild("UtilityGrid", width, height, false)
        ImGui.dummy(0f, ImGui.getTextLineHeightWithSpacing())
        ImGui.spacing()

        val startY = ImGui.getCursorPosY()
        val cellW = (width - 10f) / 3f
        val cellH = (height - 30f) / 3f

        // Column 1: Labels/Selectables
        val labels = listOf("Move", "Copy", "Swap")
        for (i in 0..2) {
            if (ImGui.selectable(labels[i], utilityMode == i, 0, cellW, cellH * 0.8f)) {
                utilityMode = i
            }
            if (i < 2) ImGui.spacing()
        }

        ImGui.sameLine(cellW + 5f)
        ImGui.setCursorPosY(startY)
        
        // Column 2 & 3: Action Buttons
        ImGui.beginGroup()
        when (utilityMode) {
            0 -> { // Move
                if (ImGui.button("A > B", cellW, cellH)) PatchManager.moveDeck(mixer, mixer.deckA, mixer.deckB)
                if (ImGui.button("B > A", cellW, cellH)) PatchManager.moveDeck(mixer, mixer.deckB, mixer.deckA)
                if (ImGui.button("C > A", cellW, cellH)) PatchManager.moveDeck(mixer, mixer.deckC, mixer.deckA)
            }
            1 -> { // Copy
                if (ImGui.button("A > B", cellW, cellH)) PatchManager.copyDeck(mixer, mixer.deckA, mixer.deckB)
                if (ImGui.button("B > A", cellW, cellH)) PatchManager.copyDeck(mixer, mixer.deckB, mixer.deckA)
                if (ImGui.button("C > A", cellW, cellH)) PatchManager.copyDeck(mixer, mixer.deckC, mixer.deckA)
            }
            2 -> { // Swap
                if (ImGui.button("A + B", cellW, cellH)) PatchManager.swapDecks(mixer, mixer.deckA, mixer.deckB)
                if (ImGui.button("B + C", cellW, cellH)) PatchManager.swapDecks(mixer, mixer.deckB, mixer.deckC)
                if (ImGui.button("C + A", cellW, cellH)) PatchManager.swapDecks(mixer, mixer.deckC, mixer.deckA)
            }
        }
        ImGui.endGroup()

        ImGui.sameLine(cellW * 2f + 10f)
        ImGui.setCursorPosY(startY)

        ImGui.beginGroup()
        when (utilityMode) {
            0 -> { // Move
                if (ImGui.button("A > C", cellW, cellH)) PatchManager.moveDeck(mixer, mixer.deckA, mixer.deckC)
                if (ImGui.button("B > C", cellW, cellH)) PatchManager.moveDeck(mixer, mixer.deckB, mixer.deckC)
                if (ImGui.button("C > B", cellW, cellH)) PatchManager.moveDeck(mixer, mixer.deckC, mixer.deckB)
            }
            1 -> { // Copy
                if (ImGui.button("A > C", cellW, cellH)) PatchManager.copyDeck(mixer, mixer.deckA, mixer.deckC)
                if (ImGui.button("B > C", cellW, cellH)) PatchManager.copyDeck(mixer, mixer.deckB, mixer.deckC)
                if (ImGui.button("C > B", cellW, cellH)) PatchManager.copyDeck(mixer, mixer.deckC, mixer.deckB)
            }
            2 -> { // Swap (Only 3 combinations for 3 decks, so last column is empty or spacers)
                // Already covered all pairs in column 2
            }
        }
        ImGui.endGroup()

        ImGui.endChild()
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

        val isTarget = patchState.midiLearnTarget?.let {
            it is MidiLearnTarget.BaseValueSlider && it.paramKey == paramKey
        } ?: false

        if (patchState.isMidiLearnMode) {
            if (ImGui.isItemClicked(0)) {
                patchState.midiLearnTarget = MidiLearnTarget.BaseValueSlider(paramKey, label, param, min, max)
            }
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
        val fillWidth = barW * pct

        if (fillWidth > 0f) {
            dl.addRectFilled(
                barStartX, barScreenY,
                barStartX + fillWidth, barScreenY + barH,
                themeColor,
                3f
            )
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