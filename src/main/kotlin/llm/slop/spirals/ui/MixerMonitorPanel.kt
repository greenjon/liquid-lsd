package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.patches.PlayQueueManager
import llm.slop.spirals.models.toDto
import llm.slop.spirals.models.applyDto

class MixerMonitorPanel(
    private val patchState: PatchGridState,
    private val advanceSetlist: (Int) -> Unit,
    private val drawDeckControls: (String, Deck, Float, Float, Boolean) -> Unit
) {

    fun draw(mixer: Mixer) {
        val availW = ImGui.getContentRegionAvailX()
        val masterH = availW * (9f / 16f)

        val imgScreenX = ImGui.getCursorScreenPosX()
        val imgScreenY = ImGui.getCursorScreenPosY()

        ImGui.image(mixer.masterFBO.texture, availW, masterH, 0f, 1f, 1f, 0f)

        // Draw overlay text on top of the master output image
        val dl = ImGui.getWindowDrawList()
        val overlayStr = "Master Output"
        val overlayH = ImGui.calcTextSize(overlayStr).y + 10f
        dl.addRectFilled(imgScreenX, imgScreenY, imgScreenX + availW, imgScreenY + overlayH, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0.6f))
        
        ImGui.setCursorScreenPos(imgScreenX + 10f, imgScreenY + 5f)
        UITheme.captionColored(0.4f, 1.0f, 0.8f, 1.0f, overlayStr) // Mint green text

        // Restore Y cursor position
        ImGui.setCursorScreenPos(imgScreenX, imgScreenY + masterH)
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- Master Mixer Controls ---
        ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.colorConvertFloat4ToU32(0.05f, 0.1f, 0.08f, 0.4f)) // Faint mint background
        ImGui.beginChild("MasterControls", availW, 150f, true)
        
        // Crossfader (mapped display value from -1.0 to 1.0)
        drawFlatSlider("Mixer/crossfade", "Crossfader", mixer.crossfade, 0f, 1f, 80f, -1f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f)) {
            "A <-- %.2f --> B".format(it)
        }
        if (ImGui.isItemActive()) {
            mixer.isAutoFading = false
            mixer.targetCrossfade = mixer.crossfade.baseValue
        }

        ImGui.columns(2, "masterMixerCols", false)
        
        // Alpha
        drawFlatSlider("Mixer/masterAlpha", "Alpha", mixer.masterAlpha, 0f, 1f, 50f, 0f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f))
        ImGui.nextColumn()
        
        drawFlatSlider("Mixer/bloom", "Bloom", mixer.bloom, 0f, 1f, 50f, 0f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f))
        ImGui.nextColumn()

        drawFlatSlider("Mixer/xfadeSpeed", "XFade Speed", mixer.xfadeSpeed, 0.001f, 1.0f, 50f, 0f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f))
        ImGui.nextColumn()

        drawFlatSlider("Mixer/setlistNext", "Next Trigger", mixer.setlistNext, 0f, 1f, 50f, 0f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f))
        ImGui.nextColumn()

        drawFlatSlider("Mixer/setlistPrev", "Prev Trigger", mixer.setlistPrev, 0f, 1f, 50f, 0f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f))
        ImGui.nextColumn()
        
        ImGui.columns(1)
        
        // Blend mode inline text readout
        val modes = arrayOf("ADD", "SCREEN", "MULT", "MAX", "XFADE")
        val modeIdx = mixer.mode.value.toInt().coerceIn(0, 4)
        ImGui.spacing()
        UITheme.captionColored(0.4f, 1.0f, 0.8f, 1.0f, "Blend Mode: ${modes[modeIdx]}")
        
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
        
        // 2. Centered Copy Buttons (drawn exactly in the middle of availW)
        val centerOfPanel = startX + availW * 0.5f
        ImGui.setCursorScreenPos(centerOfPanel - btnW - 5f, centerY + 2f)
        if (ImGui.button("<##copyToA", btnW, 25f)) {
            val dto = mixer.deckB.toDto("Deck B")
            mixer.deckA.applyDto(dto)
        }
        
        ImGui.setCursorScreenPos(centerOfPanel + 5f, centerY + 2f)
        if (ImGui.button(">##copyToB", btnW, 25f)) {
            val dto = mixer.deckA.toDto("Deck A")
            mixer.deckB.applyDto(dto)
        }
        
        // 3. Deck B Header
        val deckBStartX = startX + halfW + padding
        ImGui.setCursorScreenPos(deckBStartX + (halfW - twB) * 0.5f, centerY)
        UITheme.h2("Deck B")
        
        // 4. Presets Row
        val presetY = centerY + headerRowH
        
        val activePresetA = llm.slop.spirals.patches.PatchManager.activePresetA ?: "None"
        val isDirtyA = llm.slop.spirals.patches.PatchManager.isDeckDirty(mixer.deckA, true)
        val displayNameA = if (isDirtyA) "$activePresetA *" else activePresetA
        
        ImGui.setCursorScreenPos(startX, presetY + 3f)
        UITheme.body("Preset: $displayNameA")
        
        val activePresetB = llm.slop.spirals.patches.PatchManager.activePresetB ?: "None"
        val isDirtyB = llm.slop.spirals.patches.PatchManager.isDeckDirty(mixer.deckB, false)
        val displayNameB = if (isDirtyB) "$activePresetB *" else activePresetB
        
        ImGui.setCursorScreenPos(deckBStartX, presetY + 3f)
        UITheme.body("Preset: $displayNameB")
        
        ImGui.spacing()
        
        // --- Render the exact exact child panels ---
        val subH = halfW * (9f / 16f)
        
        val childY = ImGui.getCursorScreenPosY()
        
        ImGui.setCursorScreenPos(startX, childY)
        drawDeckControls("Deck A", mixer.deckA, halfW, subH, true)
        
        ImGui.setCursorScreenPos(deckBStartX, childY)
        drawDeckControls("Deck B", mixer.deckB, halfW, subH, false)
        
        val endY = ImGui.getCursorScreenPosY()
        ImGui.setCursorScreenPos(startX, endY)
        
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- Play Queue ---
        PlayQueuePanel.draw(mixer)
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