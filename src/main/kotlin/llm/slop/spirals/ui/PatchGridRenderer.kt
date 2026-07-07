package llm.slop.spirals.ui

import imgui.ImDrawList
import imgui.ImGui
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.models.ClipboardManager
import llm.slop.spirals.models.CellClipboardData
import llm.slop.spirals.models.RowClipboardData
import llm.slop.spirals.models.toDto
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object PatchGridRenderer {
    
    fun drawParamRow(
        label: String, 
        paramKey: String, 
        param: ModulatableParameter, 
        state: PatchGridState, 
        labelColW: Float, 
        mixer: Mixer,
        gridStartX: Float,
        rowIndex: Int,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        val isEven = (rowIndex % 2 == 0)
        val CELL = 35f
        val CELL_PAD = 5f

        val mousePos = ImGui.getIO().mousePos
        val rowScreenY = ImGui.getCursorScreenPosY()
        val lastVisibleCol = getCvColumns().lastOrNull() ?: "midi"
        val rowWidth = labelColW + getColumnOffset(lastVisibleCol) + CELL + CELL_PAD * 0.5f
        val isHoveredRow = mousePos.y >= rowScreenY && mousePos.y <= (rowScreenY + CELL) && mousePos.x >= gridStartX && mousePos.x <= (gridStartX + rowWidth)

        ImGui.pushID(paramKey)

        if (isEven || isHoveredRow) {
            val dl = ImGui.getWindowDrawList()
            val stripeCol = if (isHoveredRow) {
                ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.06f)
            } else {
                ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.03f)
            }
            dl.addRectFilled(gridStartX, rowScreenY, gridStartX + rowWidth, rowScreenY + CELL, stripeCol)
        }

        // Row label
        val rowX = ImGui.getCursorPosX()
        val rowY = ImGui.getCursorPosY()
        
        ImGui.setCursorPosY(rowY + (CELL - ImGui.getTextLineHeight()) * 0.5f)
        val cursorStartX = ImGui.getCursorPosX()
        val indent = ImGui.getCursorScreenPosX() - gridStartX
        val labelBtnW = labelColW - indent - CELL_PAD
        UITheme.body(label)
        ImGui.sameLine(cursorStartX)
        ImGui.invisibleButton("row_label_btn_$paramKey", labelBtnW, CELL)
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Click to open context menu: Randomize, Copy/Paste, or Reset parameter $label.")
        }
        if (ImGui.beginPopupContextItem("row_menu_$paramKey")) {
            if (ImGui.menuItem("Randomize row")) {
                onPushUndo()
                val randomized = param.modulators.map { it.randomizeActiveValues() }
                param.modulators.clear()
                param.modulators.addAll(randomized)
                param.randomizeBaseValue()
            }
            ImGui.separator()
            if (ImGui.menuItem("Copy Row Modulations")) {
                ClipboardManager.rowClipboard = RowClipboardData(paramKey, param.toDto())
            }
            val hasRowClip = ClipboardManager.rowClipboard != null
            if (ImGui.menuItem("Paste Row Modulations", null, false, hasRowClip)) {
                onPushUndo()
                ClipboardManager.rowClipboard?.let { ClipboardManager.applyRowClipboard(param, it, mixer) }
            }
            ImGui.separator()
            if (ImGui.menuItem("Reset Parameter to Default")) {
                onPushUndo()
                param.reset()
            }
            if (ImGui.menuItem("Clear all CVs")) {
                onPushUndo()
                param.modulators.clear()
            }
            val hasMidiMap = llm.slop.spirals.midi.MidiMappingManager.hasMapping(paramKey)
            if (ImGui.menuItem("Clear MIDI mapping", null, false, hasMidiMap)) {
                llm.slop.spirals.midi.MidiMappingManager.removeMapping(paramKey)
                llm.slop.spirals.midi.MidiMappingManager.saveActiveProfile()
            }
            ImGui.endPopup()
        }
        ImGui.setCursorPosY(rowY)

        val dl = ImGui.getWindowDrawList()
        val r = CELL * 0.5f

        // 1. FINAL Cell
        val finalX = gridStartX + labelColW + getColumnOffset("final")
        val finalY = rowScreenY
        val isFinalSelected = state.selectedCell?.paramKey == paramKey && state.selectedCell?.cvSourceId == "final"
        val isFinalHoveredCol = mousePos.x >= finalX && mousePos.x <= (finalX + CELL)
        
        ImGui.setCursorScreenPos(finalX, finalY)
        ImGui.invisibleButton("##final_cell", CELL, CELL)
        if (ImGui.isItemClicked()) {
            state.select(PatchCellId(paramKey, "final"), param)
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            if (param.modulatorFilter != null) {
                ImGui.beginTooltip()
                ImGui.text("Parameter value: ${"%.3f".format(param.value)} (Base: ${"%.3f".format(param.baseValue)})\nClick to configure bounds, random ranges, and default values.\n\nNote: Modulators for this parameter are conditionally filtered.\nWhen AUTO-VJ is OFF, LFO, Audio, and CV modulators are bypassed.\nMIDI CC remains active.")
                ImGui.endTooltip()
            } else {
                ImGui.setTooltip("Parameter value: ${"%.3f".format(param.value)} (Base: ${"%.3f".format(param.baseValue)})\nClick to configure bounds, random ranges, and default values.")
            }
        }
        
        val finalBgCol = when {
            isFinalSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            else            -> getCvColor("final", 0.05f) // Subtle background tint
        }
        val finalBorderCol = when {
            isFinalSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            else            -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        val finalColor = getCvColor("final", 1f) // Active meter color
        
        drawKnobMeter(
            dl = dl, x = finalX, y = finalY, r = r,
            value = param.value, min = param.minClamp, max = param.maxClamp,
            meterType = param.meterType,
            baseValue = param.baseValue, baseMin = param.baseMin, baseMax = param.baseMax,
            color = finalColor, bgCol = finalBgCol, borderCol = finalBorderCol,
            isHoveredRow = isHoveredRow, isHoveredCol = isFinalHoveredCol
        )

        // 2.5 MIDI Cell
        val midiX = gridStartX + labelColW + getColumnOffset("midi")
        val midiY = rowScreenY
        val midiCellId = PatchCellId(paramKey, "midi")
        val isMidiSelected = state.selectedCell == midiCellId
        val isMidiHoveredCol = mousePos.x >= midiX && mousePos.x <= (midiX + CELL)
        val isMidiCrosshair = isHoveredRow && isMidiHoveredCol
        
        // Find any MIDI modulator for this parameter
        val midiMods = param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
        val hasMidiMod = midiMods.any { mod ->
            val isAllowed = param.modulatorFilter?.invoke(mod) ?: true
            isAllowed && !mod.bypassed
        }
        val isMidiBypassed = midiMods.isNotEmpty() && midiMods.all { mod ->
            val isAllowed = param.modulatorFilter?.invoke(mod) ?: true
            !isAllowed || mod.bypassed
        }
        
        val isMidiTarget = state.midiLearnTarget?.let {
            it is MidiLearnTarget.GridCell && it.cellId == midiCellId
        } ?: false

        ImGui.setCursorScreenPos(midiX, midiY)
        ImGui.invisibleButton("##midi_cell", CELL, CELL)
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            val details = if (hasMidiMod) {
                val ccList = midiMods.joinToString(", ") { it.sourceId.removePrefix("midi_cc_") }
                "Mapped to MIDI CC: $ccList\nClick to edit MIDI settings."
            } else if (state.isMidiLearnMode) {
                "MIDI Learn active. Click this cell, then move/turn a control on your controller to bind it."
            } else {
                "No MIDI mapping. Click to view CC mapping options (MIDI Map mode)."
            }
            ImGui.setTooltip(details)
        }
        if (ImGui.isItemClicked()) {
            if (state.isMidiLearnMode) {
                state.midiLearnTarget = MidiLearnTarget.GridCell(midiCellId, param)
            } else {
                state.select(midiCellId, param)
            }
        }
        
        if (ImGui.beginPopupContextItem("midi_cell_menu_$paramKey")) {
            if (midiMods.isNotEmpty()) {
                if (ImGui.menuItem("Clear MIDI Modulator")) {
                    onPushUndo()
                    param.modulators.removeAll(midiMods)
                }
                if (ImGui.menuItem(if (isMidiBypassed) "Enable MIDI Modulator" else "Bypass MIDI Modulator")) {
                    onPushUndo()
                    val updated = param.modulators.map {
                        if (it.sourceId.startsWith("midi_cc_")) it.copy(bypassed = !it.bypassed) else it
                    }
                    param.modulators.clear()
                    param.modulators.addAll(updated)
                }
            }
            ImGui.endPopup()
        }

        val midiBgCol = when {
            isMidiTarget   -> ImGui.colorConvertFloat4ToU32(0.0f, 0.4f, 0.5f, 1f)
            isMidiSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            hasMidiMod     -> ImGui.colorConvertFloat4ToU32(0.05f, 0.15f, 0.2f, 1f)
            else           -> getCvColor("midi", 0.05f)
        }
        val midiBorderCol = when {
            isMidiTarget   -> ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 1.0f, 1f)
            isMidiSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            hasMidiMod     -> ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.7f, 0.8f)
            else           -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        
        if (hasMidiMod || isMidiBypassed) {
            val liveVal = llm.slop.spirals.cv.getCombinedModulatorValue(midiMods).coerceIn(-1f, 1f)
            val displayValue = run {
                val range = param.maxClamp - param.minClamp
                param.minClamp + ((liveVal + 1f) / 2f) * range
            }
            
            drawKnobMeter(
                dl = dl, x = midiX, y = midiY, r = r,
                value = displayValue, min = param.minClamp, max = param.maxClamp,
                meterType = param.meterType,
                baseValue = null, baseMin = null, baseMax = null,
                color = getCvColor("midi", 1f),
                bgCol = midiBgCol, borderCol = midiBorderCol,
                isBypassed = isMidiBypassed,
                isHoveredRow = isHoveredRow, isHoveredCol = isMidiHoveredCol
            )
        } else {
            dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, midiBgCol, 3f)
            if (isMidiCrosshair) {
                dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), 3f)
            } else if (isMidiHoveredCol) {
                dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f), 3f)
            }
            val border = if (isMidiCrosshair) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f) else midiBorderCol
            dl.addRect(midiX, midiY, midiX + CELL, midiY + CELL, border, 3f)
        }

        // 3. CV cells
        val cvCols = getCvColumns()
        for (cvId in cvCols) {
            val cellId = PatchCellId(paramKey, cvId)
            val isSelected = state.selectedCell == cellId
            val activeMods = if (cvId == "audio") {
                param.modulators.filter { it.sourceId in setOf("audio_amp", "audio_bass", "audio_mid", "audio_high") }
            } else if (cvId == "trigger") {
                param.modulators.filter { it.sourceId in setOf("trigger_onset", "trigger_accent") }
            } else {
                param.modulators.filter { it.sourceId == cvId }
            }
            val hasModulator = activeMods.any { mod ->
                val isAllowed = param.modulatorFilter?.invoke(mod) ?: true
                isAllowed && !mod.bypassed
            }
            val isBypassed = activeMods.isNotEmpty() && activeMods.all { mod ->
                val isAllowed = param.modulatorFilter?.invoke(mod) ?: true
                !isAllowed || mod.bypassed
            }

            val x = gridStartX + labelColW + getColumnOffset(cvId)
            val y = rowScreenY

            val isHoveredCol = mousePos.x >= x && mousePos.x <= (x + CELL)
            val isCrosshair = isHoveredRow && isHoveredCol

            val isTarget = state.midiLearnTarget?.let {
                it is MidiLearnTarget.GridCell && it.cellId == cellId
            } ?: false

            ImGui.setCursorScreenPos(x, y)
            ImGui.invisibleButton("##cell_$cvId", CELL, CELL)
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                val isFiltered = param.modulatorFilter != null && activeMods.any { mod -> param.modulatorFilter?.invoke(mod) == false }
                val statusText = when {
                    isFiltered -> "Bypassed: Bypassed because AUTO-VJ is OFF."
                    hasModulator -> "Active: Modulation routed to parameter."
                    isBypassed -> "Bypassed: Bypassed by user toggle."
                    else -> "Unmapped: Click to route this modulation source."
                }
                val modSource = when (cvId) {
                    "gen1" -> "LFO / Oscillator"
                    "audio" -> "Audio Envelope Follower"
                    "trigger" -> "Transient Trigger"
                    else -> cvId
                }
                val tipText = "Source: $modSource\nStatus: $statusText\nClick to configure modulation settings. Right-click to copy/paste."
                ImGui.setTooltip(tipText)
            }
            if (ImGui.isItemClicked()) {
                if (state.isMidiLearnMode) {
                    state.midiLearnTarget = MidiLearnTarget.GridCell(cellId, param)
                } else {
                    state.select(cellId, param)
                }
            }
            if (ImGui.beginPopupContextItem("cell_menu_$paramKey-$cvId")) {
                if (ImGui.menuItem("Copy Cell Modulators")) {
                    ClipboardManager.cellClipboard = CellClipboardData(paramKey, cvId, activeMods.map { it.toDto() })
                }
                val hasCellClip = ClipboardManager.cellClipboard != null
                if (ImGui.menuItem("Paste Modulator(s)", null, false, hasCellClip)) {
                    onPushUndo()
                    ClipboardManager.cellClipboard?.let { ClipboardManager.applyCellClipboard(param, cvId, it) }
                }
                if (activeMods.isNotEmpty()) {
                    if (ImGui.menuItem("Clear Modulator(s)")) {
                        onPushUndo()
                        param.modulators.removeAll(activeMods)
                    }
                    if (ImGui.menuItem(if (isBypassed) "Enable Modulator(s)" else "Bypass Modulator(s)")) {
                        onPushUndo()
                        val updated = param.modulators.map { mod ->
                            if (activeMods.any { it.id == mod.id }) {
                                mod.copy(bypassed = !mod.bypassed)
                            } else mod
                        }
                        param.modulators.clear()
                        param.modulators.addAll(updated)
                    }
                }
                ImGui.endPopup()
            }

            val bgCol = when {
                isTarget      -> ImGui.colorConvertFloat4ToU32(0.0f, 0.4f, 0.5f, 1f)
                isSelected    -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
                hasModulator  -> ImGui.colorConvertFloat4ToU32(0.05f, 0.15f, 0.2f, 1f)
                else          -> getCvColor(cvId, 0.05f)
            }
            val borderCol = when {
                isTarget     -> ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 1.0f, 1f)
                isSelected   -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
                hasModulator -> ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.7f, 0.8f)
                else         -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
            }

            if (hasModulator || isBypassed) {
                val liveVal = llm.slop.spirals.cv.getCombinedModulatorValue(activeMods).coerceIn(-1f, 1f)
                val displayValue = run {
                    val range = param.maxClamp - param.minClamp
                    param.minClamp + ((liveVal + 1f) / 2f) * range
                }
                
                drawKnobMeter(
                    dl = dl, x = x, y = y, r = r,
                    value = displayValue, min = param.minClamp, max = param.maxClamp,
                    meterType = param.meterType,
                    baseValue = null, baseMin = null, baseMax = null,
                    color = getCvColor(cvId, 1f),
                    bgCol = bgCol, borderCol = borderCol,
                    isBypassed = isBypassed,
                    isHoveredRow = isHoveredRow, isHoveredCol = isHoveredCol
                )
            } else {
                dl.addRectFilled(x, y, x + CELL, y + CELL, bgCol, 3f)
                if (isCrosshair) {
                    dl.addRectFilled(x, y, x + CELL, y + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), 3f)
                } else if (isHoveredCol) {
                    dl.addRectFilled(x, y, x + CELL, y + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f), 3f)
                }
                val border = if (isCrosshair) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f) else borderCol
                dl.addRect(x, y, x + CELL, y + CELL, border, 3f)
            }
        }

        ImGui.popID()
        ImGui.setCursorPos(rowX, rowY + CELL)
    }

    private fun drawKnobMeter(
        dl: ImDrawList,
        x: Float, y: Float, r: Float,
        value: Float,
        min: Float, max: Float,
        meterType: llm.slop.spirals.parameters.MeterType,
        baseValue: Float?,
        baseMin: Float?,
        baseMax: Float?,
        color: Int,
        bgCol: Int,
        borderCol: Int,
        isBypassed: Boolean = false,
        isHoveredRow: Boolean = false,
        isHoveredCol: Boolean = false
    ) {
        val cx = x + r
        val cy = y + r

        dl.addRectFilled(x, y, x + r * 2f, y + r * 2f, bgCol, 3f)
        
        if (isHoveredRow && isHoveredCol) {
            dl.addRectFilled(x, y, x + r * 2f, y + r * 2f, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), 3f)
        } else if (isHoveredCol) {
            dl.addRectFilled(x, y, x + r * 2f, y + r * 2f, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f), 3f)
        }

        val border = if (isHoveredRow && isHoveredCol) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f) else borderCol
        dl.addRect(x, y, x + r * 2f, y + r * 2f, border, 3f)

        val trackRadius = r - 5f
        val trackCol = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, if (isBypassed) 0.2f else 0.4f)
        val fillCol = if (isBypassed) ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.5f) else color

        val aMin = PI.toFloat() * 0.75f  // 135 deg
        val aMax = PI.toFloat() * 2.25f  // 405 deg
        val aCenter = PI.toFloat() * 1.5f // 270 deg

        val range = max - min
        val normalized = if (range == 0f) 0.5f else ((value - min) / range).coerceIn(0f, 1f)

        when (meterType) {
            llm.slop.spirals.parameters.MeterType.ENDLESS, llm.slop.spirals.parameters.MeterType.DISCRETE -> {
                dl.addCircle(cx, cy, trackRadius, trackCol, 32, 1.5f)
                val angle = (PI / 2.0) + normalized * 2.0 * PI
                val dotX = cx + trackRadius * cos(angle).toFloat()
                val dotY = cy + trackRadius * sin(angle).toFloat()
                dl.addCircleFilled(dotX, dotY, 3f, fillCol)

                if (baseValue != null) {
                    val bNorm = if (range == 0f) 0.5f else ((baseValue - min) / range).coerceIn(0f, 1f)
                    val bAngle = (PI / 2.0) + bNorm * 2.0 * PI
                    val bX = cx + trackRadius * cos(bAngle).toFloat()
                    val bY = cy + trackRadius * sin(bAngle).toFloat()
                    val bCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)
                    dl.addCircleFilled(bX, bY, 3f, bCol)
                }
            }
            llm.slop.spirals.parameters.MeterType.MONOPOLAR -> {
                dl.pathArcTo(cx, cy, trackRadius, aMin, aMax, 32)
                dl.pathStroke(trackCol, 0, 1.5f)

                if (normalized > 0f) {
                    val fillAngle = aMin + normalized * (aMax - aMin)
                    dl.pathArcTo(cx, cy, trackRadius, aMin, fillAngle, 32)
                    dl.pathStroke(fillCol, 0, 2.5f)
                }

                val valAngle = aMin + normalized * (aMax - aMin)
                val dotX = cx + trackRadius * cos(valAngle)
                val dotY = cy + trackRadius * sin(valAngle)
                dl.addCircleFilled(dotX, dotY, 3f, fillCol)

                if (baseValue != null) {
                    val bNorm = if (range == 0f) 0.5f else ((baseValue - min) / range).coerceIn(0f, 1f)
                    val bAngle = aMin + bNorm * (aMax - aMin)
                    val bX = cx + trackRadius * cos(bAngle)
                    val bY = cy + trackRadius * sin(bAngle)
                    val bCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)
                    dl.addCircleFilled(bX, bY, 2.5f, bCol)

                    if (baseMin != null && baseMax != null && baseMin != baseMax) {
                        val rMinNorm = if (range == 0f) 0.5f else ((baseMin - min) / range).coerceIn(0f, 1f)
                        val rMaxNorm = if (range == 0f) 0.5f else ((baseMax - min) / range).coerceIn(0f, 1f)
                        val rMinA = aMin + rMinNorm * (aMax - aMin)
                        val rMaxA = aMin + rMaxNorm * (aMax - aMin)
                        val rangeCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 0.4f)
                        dl.pathArcTo(cx, cy, trackRadius - 3f, rMinA, rMaxA, 16)
                        dl.pathStroke(rangeCol, 0, 2f)
                    }
                }
            }
            llm.slop.spirals.parameters.MeterType.BIPOLAR -> {
                dl.pathArcTo(cx, cy, trackRadius, aMin, aMax, 32)
                dl.pathStroke(trackCol, 0, 1.5f)

                val cX = cx + (trackRadius - 2f) * cos(aCenter)
                val cY = cy + (trackRadius - 2f) * sin(aCenter)
                val cX2 = cx + (trackRadius + 2f) * cos(aCenter)
                val cY2 = cy + (trackRadius + 2f) * sin(aCenter)
                dl.addLine(cX, cY, cX2, cY2, trackCol, 1.5f)

                if (normalized != 0.5f) {
                    val valAngle = aMin + normalized * (aMax - aMin)
                    if (normalized > 0.5f) {
                        dl.pathArcTo(cx, cy, trackRadius, aCenter, valAngle, 16)
                    } else {
                        dl.pathArcTo(cx, cy, trackRadius, valAngle, aCenter, 16)
                    }
                    dl.pathStroke(fillCol, 0, 2.5f)
                }

                val valAngle = aMin + normalized * (aMax - aMin)
                val dotX = cx + trackRadius * cos(valAngle)
                val dotY = cy + trackRadius * sin(valAngle)
                dl.addCircleFilled(dotX, dotY, 3f, fillCol)

                if (baseValue != null) {
                    val bNorm = if (range == 0f) 0.5f else ((baseValue - min) / range).coerceIn(0f, 1f)
                    val bAngle = aMin + bNorm * (aMax - aMin)
                    val bX = cx + trackRadius * cos(bAngle)
                    val bY = cy + trackRadius * sin(bAngle)
                    val bCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)
                    dl.addCircleFilled(bX, bY, 2.5f, bCol)

                    if (baseMin != null && baseMax != null && baseMin != baseMax) {
                        val rMinNorm = if (range == 0f) 0.5f else ((baseMin - min) / range).coerceIn(0f, 1f)
                        val rMaxNorm = if (range == 0f) 0.5f else ((baseMax - min) / range).coerceIn(0f, 1f)
                        val rMinA = aMin + rMinNorm * (aMax - aMin)
                        val rMaxA = aMin + rMaxNorm * (aMax - aMin)
                        val rangeCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 0.4f)
                        dl.pathArcTo(cx, cy, trackRadius - 3f, rMinA, rMaxA, 16)
                        dl.pathStroke(rangeCol, 0, 2f)
                    }
                }
            }
        }
    }

    fun drawRandomizeRow(
        label: String,
        groupKey: String,
        gridStartX: Float,
        labelColW: Float,
        triggerColOffset: Float,
        y: Float,
        cellW: Float,
        onRandomize: () -> Unit
    ) {
        val scale = 35f / 30f
        val btnWidth = 50f * scale
        val btnHeight = 35f
        if (drawDiceButton("dice_$groupKey", gridStartX, y, scale, btnWidth, btnHeight)) {
            onRandomize()
        }
        ImGui.sameLine(0f, 6f)
        val textY = y + (btnHeight - ImGui.getTextLineHeight()) * 0.5f
        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), textY)
        UITheme.body(label)
        // Advance the ImGui layout cursor past this row so the next drawParamRow
        // picks up the correct rowScreenY.  setCursorScreenPos alone does not
        // update the window-local cursor, so we convert to window-local coords.
        val winY = ImGui.getWindowPos().y
        val scrollY = ImGui.getScrollY()
        ImGui.setCursorPosY((y + btnHeight) - winY + scrollY)
    }

    fun drawDiceButton(id: String, x: Float, y: Float, scale: Float, btnWidth: Float, btnHeight: Float): Boolean {
        ImGui.setCursorScreenPos(x, y)
        return UITheme.iconButton("##$id", Icons.DICES, "Randomize parameter.", width = btnWidth, height = btnHeight)
    }
}
