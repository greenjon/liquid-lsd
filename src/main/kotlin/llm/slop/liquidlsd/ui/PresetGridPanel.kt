package llm.slop.liquidlsd.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.models.ClipboardManager
import llm.slop.liquidlsd.models.CellClipboardData
import llm.slop.liquidlsd.models.RowClipboardData
import llm.slop.liquidlsd.models.toDto
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

import llm.slop.liquidlsd.rendering.MandalaLibrary
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import java.io.File

/**
 * Draws the Preset Grid panel. Rows = grouped ModulatableParameters.
 * Columns = CV sources. Each intersection is a clickable cell.
 */
object PresetGridPanel {

    private fun getCvColumns(session: llm.slop.liquidlsd.SessionContext): List<String> {
        val cols = mutableListOf<String>()
        if (session.uiTheme.showLfoCol) cols.add("lfo")
        if (session.uiTheme.audioEngineEnabled) {
            if (session.uiTheme.showAudioCol) cols.add("audio")
            if (session.uiTheme.showTriggerCol) cols.add("trigger")
        }
        return cols
    }

    private fun getCvLabels(session: llm.slop.liquidlsd.SessionContext): List<String> {
        val labels = mutableListOf<String>()
        if (session.uiTheme.showLfoCol) labels.add("LFO")
        if (session.uiTheme.audioEngineEnabled) {
            if (session.uiTheme.showAudioCol) labels.add("AUD")
            if (session.uiTheme.showTriggerCol) labels.add("TRIG")
        }
        return labels
    }

    private fun getVisibleColumns(session: llm.slop.liquidlsd.SessionContext): List<String> {
        val visibleCols = mutableListOf("value")
        if (session.uiTheme.showMidiCol) visibleCols.add("midi")
        visibleCols.addAll(getCvColumns(session))
        return visibleCols
    }

    private fun getColumnOffset(session: llm.slop.liquidlsd.SessionContext, colId: String): Float {
        val metrics = GridMetrics.compute(session)
        val visibleCols = getVisibleColumns(session)
        
        val targetId = if (colId == "final") "value" else colId
        val index = visibleCols.indexOf(targetId)
        if (index < 0) return 0f
        
        return index * (metrics.cell + metrics.cellPad)
    }

    private fun getCvColor(colId: String, alpha: Float = 1f): Int {
        return when (colId) {
            // Special
            "value", "final" -> ImGui.colorConvertFloat4ToU32(0.0f, 1.0f, 0.7f, alpha)
            "base"           -> ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, alpha)
            "midi"           -> ImGui.colorConvertFloat4ToU32(0.7f, 0.3f, 1.0f, alpha)
            // Synthetic / Generators
            "lfo"            -> ImGui.colorConvertFloat4ToU32(0.0f, 0.7f, 1.0f, alpha)
            "sampleAndHold"  -> ImGui.colorConvertFloat4ToU32(0.7f, 0.4f, 1.0f, alpha)
            "beatPhase"      -> ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 1.0f, alpha)
            // Audio / Spectral
            "audio"          -> ImGui.colorConvertFloat4ToU32(0.3f, 0.9f, 0.0f, alpha)
            "amp"            -> ImGui.colorConvertFloat4ToU32(0.3f, 0.9f, 0.0f, alpha)
            "bass"           -> ImGui.colorConvertFloat4ToU32(0.9f, 0.2f, 0.2f, alpha)
            "mid"            -> ImGui.colorConvertFloat4ToU32(0.9f, 0.5f, 0.1f, alpha)
            "high"           -> ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.2f, alpha)
            // Transients / Triggers
            "trigger"        -> ImGui.colorConvertFloat4ToU32(1.0f, 0.0f, 0.5f, alpha)
            "onset"          -> ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.6f, alpha)
            "accent"         -> ImGui.colorConvertFloat4ToU32(0.9f, 0.1f, 0.9f, alpha)
            else             -> ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, alpha)
        }
    }

    fun calculateRequiredWidth(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, state: PresetGridState): Float {
        val metrics = GridMetrics.compute(session)
        val sideTabWidth = PresetGridTabs.calculateLeftTabsWidth(session) + 4f
        val activeDeck = when (state.activeTopTab) {
            "Deck A" -> mixer.deckA
            "Deck B" -> mixer.deckB
            "Deck BG" -> mixer.deckBG
            "Deck PV" -> mixer.deckPV
            else -> null
        }
        val subTabsW = if (activeDeck != null && !activeDeck.isEmpty) {
            PresetGridTabs.calculateSubTabsWidth(session, state, activeDeck)
        } else 0f

        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
        val baseLabelW = 160f * fontScale
        val labelColW = maxOf(baseLabelW, subTabsW + 16f)

        val lastVisibleCol = getCvColumns(session).lastOrNull() ?: if (session.uiTheme.showMidiCol) "midi" else "value"
        val maxGridW = getColumnOffset(session, lastVisibleCol) + metrics.cell + metrics.cellPad * 0.5f

        return sideTabWidth + 12f + labelColW + maxGridW + 24f
    }

    private var gridStartX = 0f
    private var rowIndex = 0
    private var lastBoxBottomY = 0f

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, state: PresetGridState) {
        rowIndex = 0

        PresetGridKeyboard.handleKeyboardShortcuts(state, mixer, { s, m -> PresetGridUndo.pushUndoState(s, m) }, { s, m -> PresetGridUndo.performUndo(s, m) })

        val sideTabWidth = PresetGridTabs.calculateLeftTabsWidth(session)
        if (ImGui.beginTable("##preset_grid_layout_table", 2, imgui.flag.ImGuiTableColumnFlags.None)) {
            ImGui.tableSetupColumn("##side_tabs", imgui.flag.ImGuiTableColumnFlags.WidthFixed, sideTabWidth + 4f)
            ImGui.tableSetupColumn("##main_grid", imgui.flag.ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableNextRow()

            val activeDeck = when (state.activeTopTab) {
                "Deck A" -> mixer.deckA
                "Deck B" -> mixer.deckB
                "Deck BG" -> mixer.deckBG
                "Deck PV" -> mixer.deckPV
                else -> null
            }
            val isDeckEmpty = activeDeck?.isEmpty == true
            val headerH = if (!isDeckEmpty) calculateHeaderHeight(session) else 0f

            // Left column: Side tabs (MIX, A, B, BG, PV)
            ImGui.tableSetColumnIndex(0)
            PresetGridTabs.drawLeftTabs(session, state, mixer, topOffset = headerH)

            // Right column: Main Preset Grid content
            ImGui.tableSetColumnIndex(1)

            val avail = ImGui.getContentRegionAvailX()
            gridStartX = ImGui.getCursorScreenPosX()

            val subTabsW = if (activeDeck != null && !activeDeck.isEmpty) {
                PresetGridTabs.calculateSubTabsWidth(session, state, activeDeck)
            } else 0f

            val metrics = GridMetrics.compute(session)
            val CELL = metrics.cell
            val CELL_PAD = metrics.cellPad

            val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
            val baseLabelW = 160f * fontScale
            val idealLabelColW = maxOf(baseLabelW, subTabsW + 16f)

            val lastVisibleCol = getCvColumns(session).lastOrNull() ?: if (session.uiTheme.showMidiCol) "midi" else "value"
            val maxGridW = getColumnOffset(session, lastVisibleCol) + CELL + CELL_PAD * 0.5f
            val maxAllowedLabelColW = (avail - maxGridW - 20f).coerceAtLeast(120f)
            val labelColW = minOf(idealLabelColW, maxAllowedLabelColW)
            val boxMaxX = (gridStartX + labelColW + maxGridW + 6f).coerceAtMost(gridStartX + avail)

            val containerTopY = ImGui.getCursorScreenPosY() - 2f

            // Column Headers (draw column headers & inline subtabs)
            if (!isDeckEmpty) {
                drawColumnHeaders(session, labelColW, state, mixer, metrics)
            } else {
                ImGui.spacing()
            }

            if (ImGui.beginChild("##preset_grid_scroll", 0f, 0f, false)) {
                if (state.activeTopTab == "Mixer") {
                    PresetGridTabs.drawSubGroupContent(session, "Mixer", "Mixer", state) {
                        var row = 0
                        PresetGridRenderer.drawParamRow(session, "crossfade",  "Mixer/crossfade",  mixer.crossfade,  state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                        PresetGridRenderer.drawParamRow(session, "master Alpha",   "Mixer/masterAlpha", mixer.masterAlpha, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                        PresetGridRenderer.drawParamRow(session, "bloom",      "Mixer/bloom",       mixer.bloom,       state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                        PresetGridRenderer.drawParamRow(session, "fade speed",  "Mixer/xfadeSpeed",  mixer.xfadeSpeed,  state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                        PresetGridRenderer.drawParamRow(session, "queue prev", "Mixer/queuePrev", mixer.queuePrev, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                        PresetGridRenderer.drawParamRow(session, "queue next", "Mixer/queueNext", mixer.queueNext, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                        PresetGridRenderer.drawParamRow(session, "bg queue prev", "Mixer/bgQueuePrev", mixer.bgQueuePrev, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                        PresetGridRenderer.drawParamRow(session, "bg queue next", "Mixer/bgQueueNext", mixer.bgQueueNext, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                        if (session.uiTheme.randomizationEnabled) {
                            PresetGridRenderer.drawParamRow(session, "rand Deck A", "Mixer/randDeckA", mixer.randDeckA, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                            PresetGridRenderer.drawParamRow(session, "rand Deck B", "Mixer/randDeckB", mixer.randDeckB, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                            PresetGridRenderer.drawParamRow(session, "rand Deck BG", "Mixer/randDeckBG", mixer.randDeckBG, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                            PresetGridRenderer.drawParamRow(session, "rand Deck PV", "Mixer/randDeckPV", mixer.randDeckPV, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                            PresetGridRenderer.drawParamRow(session, "rand All", "Mixer/randAll", mixer.randAll, state, labelColW, mixer, gridStartX, row++, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                        }
                    }
                } else if (state.activeTopTab == "Deck A") {
                    if (mixer.deckA.isEmpty) {
                        drawLaunchpad(session, "Deck A", mixer.deckA, state, mixer)
                    } else {
                        PresetGridTabs.drawDeckGroupContent(session, "Deck A", mixer.deckA, state, labelColW, mixer, gridStartX, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                    }
                } else if (state.activeTopTab == "Deck B") {
                    if (mixer.deckB.isEmpty) {
                        drawLaunchpad(session, "Deck B", mixer.deckB, state, mixer)
                    } else {
                        PresetGridTabs.drawDeckGroupContent(session, "Deck B", mixer.deckB, state, labelColW, mixer, gridStartX, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                    }
                } else if (state.activeTopTab == "Deck BG") {
                    if (mixer.deckBG.isEmpty) {
                        drawLaunchpad(session, "Deck BG", mixer.deckBG, state, mixer)
                    } else {
                        PresetGridTabs.drawDeckGroupContent(session, "Deck BG", mixer.deckBG, state, labelColW, mixer, gridStartX, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                    }
                } else if (state.activeTopTab == "Deck PV") {
                    if (mixer.deckPV.isEmpty) {
                        drawLaunchpad(session, "Deck PV", mixer.deckPV, state, mixer)
                    } else {
                        PresetGridTabs.drawDeckGroupContent(session, "Deck PV", mixer.deckPV, state, labelColW, mixer, gridStartX, { getCvColumns(session) }, { col -> getColumnOffset(session, col) }, ::getCvColor) { PresetGridUndo.pushUndoState(state, mixer) }
                    }
                }
            }
            val childMaxY = ImGui.getCursorScreenPosY()
            ImGui.endChild()

            ImGui.endTable()

            // Draw Connected Folder Frame around subtabs & parameters
            val dl = ImGui.getWindowDrawList()
            val accentColor = PresetGridTabs.getDeckColor(state.activeTopTab, 0.7f)
            val accentFill  = PresetGridTabs.getDeckColor(state.activeTopTab, 0.04f)
            val btnColor    = PresetGridTabs.getDeckColor(state.activeTopTab, 1.0f)

            val boxMinX = gridStartX - 6f
            val boxTopY = containerTopY
            val boxBottomY = childMaxY.coerceAtLeast(boxTopY + 100f)
            lastBoxBottomY = boxBottomY

            // 1. Card background fill and outline
            if (!isDeckEmpty) {
                val strokeW = 1.5f
                dl.addRectFilled(boxMinX, boxTopY, boxMaxX, boxBottomY, accentFill, 4f)
                dl.addRect(boxMinX, boxTopY, boxMaxX, boxBottomY, accentColor, 4f, 0, strokeW)
            } else {
                dl.addRectFilled(boxMinX, boxTopY, boxMaxX, boxBottomY, accentFill, 6f)
                dl.addRect(boxMinX, boxTopY, boxMaxX, boxBottomY, accentColor, 6f, 0, 1.5f)
            }

            // 2. Seamless folder tab bridge connecting active side tab button to the container
            if (PresetGridTabs.activeBtnMaxX > 0f) {
                val btnTop = PresetGridTabs.activeBtnMinY
                val btnBot = PresetGridTabs.activeBtnMaxY
                val btnRight = PresetGridTabs.activeBtnMaxX

                // Overwrite the left border segment alongside the button with button color to form seamless tab connection
                dl.addLine(boxMinX, btnTop + 1f, boxMinX, btnBot - 1f, btnColor, 3.5f)

                // Fill any micro gap between the button right edge and the container left edge
                if (boxMinX > btnRight) {
                    dl.addRectFilled(btnRight - 1f, btnTop, boxMinX + 1f, btnBot, btnColor, 0f)
                }
            }
        }
    }

    // -- Helpers --------------------------------------------------------------

    fun calculateHeaderHeight(session: llm.slop.liquidlsd.SessionContext): Float {
        val subTabH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            ImGui.getTextLineHeight() + 8f
        }.coerceAtLeast(24f)
        return subTabH + 4f
    }

    private fun drawColumnHeaders(session: llm.slop.liquidlsd.SessionContext, labelColW: Float, state: PresetGridState, mixer: Mixer, metrics: GridMetrics) {
        val CELL = metrics.cell
        val CELL_PAD = metrics.cellPad

        val dl = ImGui.getWindowDrawList()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val mousePos = ImGui.getIO().mousePos
        
        // Calculate the maximum height needed for the headers
        val headerH = calculateHeaderHeight(session)
        val cvCols = getCvColumns(session)
        val cvLabels = getCvLabels(session)
        
        // Reserve vertical space for headers
        ImGui.dummy(10f, headerH)
        val afterHeadersY = ImGui.getCursorScreenPosY()
        
        // Render Subtabs inline in empty space to left of column headers
        val activeDeck = when (state.activeTopTab) {
            "Deck A" -> mixer.deckA
            "Deck B" -> mixer.deckB
            "Deck BG" -> mixer.deckBG
            "Deck PV" -> mixer.deckPV
            else -> null
        }
        if (activeDeck != null && !activeDeck.isEmpty) {
            val subTabH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() + 8f }.coerceAtLeast(24f)
            ImGui.setCursorScreenPos(startX, startY + (headerH - subTabH) * 0.5f)
            PresetGridTabs.drawSubTabs(session, state, mixer)
        }
        
        val lineCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f) // VERY subtle extended grid line
        val bottomY = if (lastBoxBottomY > startY) lastBoxBottomY else (startY + 300f)
        
        // Draw VALUE header
        val valueColX = startX + labelColW + getColumnOffset(session, "value")
        dl.addLine(valueColX - CELL_PAD * 0.5f, startY, valueColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        
        val isValueHeaderHovered = mousePos.x >= valueColX && mousePos.x <= (valueColX + CELL) && mousePos.y >= startY && mousePos.y <= (startY + headerH)
        if (isValueHeaderHovered) {
            dl.addRectFilled(valueColX, startY, valueColX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
        }
        
        var twValue = 0f
        var thValue = 0f
        val labelValue = "VAL"
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val sz = ImGui.calcTextSize(labelValue)
            twValue = sz.x
            thValue = sz.y
        }
        val offsetValX = ((CELL - twValue) * 0.5f).coerceAtLeast(0f)
        val textValY = startY + (headerH - thValue) * 0.5f
        ImGui.setCursorScreenPos(valueColX + offsetValX, textValY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("value"))
        session.uiTheme.body(labelValue)
        ImGui.popStyleColor()
        if (isValueHeaderHovered && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("VAL: Base parameter value and modulation bounds/limits.")
        }

        // Draw MIDI header
        if (session.uiTheme.showMidiCol) {
            val midiColX = startX + labelColW + getColumnOffset(session, "midi")
            dl.addLine(midiColX - CELL_PAD * 0.5f, startY, midiColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
            
            val isMidiHeaderHovered = mousePos.x >= midiColX && mousePos.x <= (midiColX + CELL) && mousePos.y >= startY && mousePos.y <= (startY + headerH)
            if (isMidiHeaderHovered) {
                dl.addRectFilled(midiColX, startY, midiColX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
            }
            
            var twMidi = 0f
            var thMidi = 0f
            val labelMidi = "MIDI"
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                val sz = ImGui.calcTextSize(labelMidi)
                twMidi = sz.x
                thMidi = sz.y
            }
            val offsetMidiX = ((CELL - twMidi) * 0.5f).coerceAtLeast(0f)
            val textMidiY = startY + (headerH - thMidi) * 0.5f
            ImGui.setCursorScreenPos(midiColX + offsetMidiX, textMidiY)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("midi"))
            session.uiTheme.body(labelMidi)
            ImGui.popStyleColor()
            if (isMidiHeaderHovered && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("MIDI: Map MIDI CC/Notes from controllers to modulate this parameter.")
            }
        }

        // Draw each column header horizontally
        for ((idx, label) in cvLabels.withIndex()) {
            val cvId = cvCols[idx]
            val colX = startX + labelColW + getColumnOffset(session, cvId)
            dl.addLine(colX - CELL_PAD * 0.5f, startY, colX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
            
            val isCvHeaderHovered = mousePos.x >= colX && mousePos.x <= (colX + CELL) && mousePos.y >= startY && mousePos.y <= (startY + headerH)
            if (isCvHeaderHovered) {
                dl.addRectFilled(colX, startY, colX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
            }
            
            var tw = 0f
            var th = 0f
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                val sz = ImGui.calcTextSize(label)
                tw = sz.x
                th = sz.y
            }
            val offX = ((CELL - tw) * 0.5f).coerceAtLeast(0f)
            val textY = startY + (headerH - th) * 0.5f
            ImGui.setCursorScreenPos(colX + offX, textY)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor(cvId))
            session.uiTheme.body(label)
            ImGui.popStyleColor()
            if (isCvHeaderHovered && session.uiTheme.tooltipsEnabled) {
                val cvDesc = when (cvId) {
                    "lfo" -> "LFO: Synthetic low-frequency oscillator waveforms (Sine, Triangle, Square, Random)."
                    "audio" -> "AUD: Modulator envelopes tracked from input audio frequency bands (Bass, Mid, High, Amplitude)."
                    "trigger" -> "TRIG: Modulator envelopes tracked from transient onsets or peak accents."
                    else -> "CV Modulator source."
                }
                ImGui.setTooltip(cvDesc)
            }
        }
        
        // Draw final separator line on the right edge
        val lastColId = if (cvCols.isNotEmpty()) cvCols.last() else "midi"
        val rightColX = startX + labelColW + getColumnOffset(session, lastColId) + CELL + CELL_PAD * 0.5f
        dl.addLine(rightColX, startY, rightColX, bottomY, lineCol, 1f)
        
        // Restore cursor to where the dummy left off
        ImGui.setCursorScreenPos(startX, afterHeadersY)
    }

    private fun drawLaunchpad(
        session: llm.slop.liquidlsd.SessionContext,
        deckLabel: String,
        deck: Deck,
        state: PresetGridState,
        mixer: Mixer
    ) {
        val isDeckA = deckLabel == "Deck A"
        val isDeckBG = deckLabel == "Deck BG"
        val isDeckPV = deckLabel == "Deck PV"
        val deckColorU32 = PresetGridTabs.getDeckColor(deckLabel, 1f)

        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)

        val availW = ImGui.getContentRegionAvailX()
        val cardW = (availW - 16f).coerceIn(160f, 360f * fontScale).coerceAtMost(availW)
        val paddingX = ((availW - cardW) * 0.5f).coerceAtLeast(0f)

        ImGui.dummy(0f, 12f * fontScale)
        ImGui.indent(paddingX)

        val cardH = (220f * fontScale).coerceAtLeast(180f)
        if (ImGui.beginChild("##launchpad_$deckLabel", cardW, cardH, true)) {
            ImGui.spacing()
            ImGui.spacing()

            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, deckColorU32)
            session.uiTheme.withFont(UITheme.FontLevel.H2) {
                ImGui.textWrapped("$deckLabel is Empty")
            }
            ImGui.popStyleColor()

            ImGui.spacing()
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0.65f, 0.65f, 0.70f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                ImGui.textWrapped("No visual generator is currently assigned to this deck. Choose an action below to activate:")
            }
            ImGui.popStyleColor()

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            val availBtnW = ImGui.getContentRegionAvailX()
            val buttonWidth = availBtnW
            val buttonHeight = (32f * fontScale).coerceAtLeast(26f)

            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 6f * fontScale)

            // --- Button 1: Add Visual Source ---
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.18f, 0.22f, 0.30f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.28f, 0.34f, 0.46f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.38f, 0.44f, 0.58f, 1f))
            if (ImGui.button("${Icons.PLUS}  Add Source", buttonWidth, buttonHeight)) {
                ImGui.openPopup("##launchpad_source_popup_$deckLabel")
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Select a visual generator source (Mandala, Gyroid, Dynamic Spiral, etc.)")
            }
            ImGui.popStyleColor(3)

            if (ImGui.beginPopup("##launchpad_source_popup_$deckLabel")) {
                ImGui.textDisabled("Select Visual Source:")
                ImGui.separator()

                val clearPresetState = {
                    when {
                        deck === mixer.deckA -> {
                            session.presetManager.activePresetA = null
                            session.presetManager.cachedDtoA = null
                        }
                        deck === mixer.deckB -> {
                            session.presetManager.activePresetB = null
                            session.presetManager.cachedDtoB = null
                        }
                        deck === mixer.deckBG -> {
                            session.presetManager.activePresetBG = null
                            session.presetManager.cachedDtoBG = null
                        }
                        deck === mixer.deckPV -> {
                            session.presetManager.activePresetPV = null
                            session.presetManager.cachedDtoPV = null
                        }
                    }
                }

                if (ImGui.menuItem("Mandala")) {
                    val masterMandala = VisualSourceRegistry.availableSources.firstOrNull { it.id == "mandala" } as? Mandala
                    if (masterMandala != null) {
                        deck.source = masterMandala.clone()
                        deck.isEmpty = false
                        clearPresetState()
                        PresetGridUndo.pushUndoState(state, mixer)
                    }
                }

                for (source in VisualSourceRegistry.availableSources) {
                    if (source.id == "mandala") continue
                    if (ImGui.menuItem(source.displayName)) {
                        deck.source = source.clone()
                        deck.isEmpty = false
                        clearPresetState()
                        PresetGridUndo.pushUndoState(state, mixer)
                    }
                }
                ImGui.endPopup()
            }

            ImGui.spacing()

            // --- Button 2: Load Preset ---
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.18f, 0.26f, 0.24f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.28f, 0.38f, 0.34f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.38f, 0.48f, 0.44f, 1f))
            if (ImGui.button("${Icons.FOLDER}  Load Preset", buttonWidth, buttonHeight)) {
                ImGui.openPopup("##launchpad_preset_popup_$deckLabel")
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Choose a saved preset for $deckLabel")
            }
            ImGui.popStyleColor(3)

            if (ImGui.beginPopup("##launchpad_preset_popup_$deckLabel")) {
                ImGui.textDisabled("Quick Select Preset:")
                ImGui.separator()

                val presetFiles = FileSystemManager.scanAllPresets()

                if (presetFiles.isEmpty()) {
                    ImGui.textDisabled("No presets found.")
                } else {
                    for (asset in presetFiles.sortedBy { it.name }) {
                        if (ImGui.menuItem(asset.displayName)) {
                            session.presetManager.loadDeckPresetAsync(File(asset.path), isDeckA = isDeckA, isDeckBG = isDeckBG, isDeckPV = isDeckPV)
                        }
                    }
                }
                ImGui.separator()
                if (ImGui.menuItem("Open Library Panel...")) {
                    session.uiTheme.libraryMode = UITheme.LibraryMode.HALF
                }
                ImGui.endPopup()
            }

            ImGui.popStyleVar()
        }
        ImGui.endChild()
        ImGui.unindent(paddingX)
    }
}



