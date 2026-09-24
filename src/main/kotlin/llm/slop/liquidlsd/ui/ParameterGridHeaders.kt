package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.analyzeDependencies

/**
 * Column layout and header row for the parameter grid in Performance Mode's Deep Edit
 * ([PerformanceMatrixPanel.drawRackDeepEdit]): which CV columns are visible, their x offsets,
 * and the VAL/MIDI/LFO/SEQ/AUD headers with the section tabs.
 */
object ParameterGridHeaders {

    private const val SECTION_TABS_INSET_X = ParametersTabs.PARAM_INDENT

    fun getCvColumns(session: llm.slop.liquidlsd.SessionContext): List<String> {
        val cols = mutableListOf<String>()
        if (session.uiTheme.showLfoCol) cols.add("lfo")
        if (session.uiTheme.sequencerEnabled) cols.add("seq")
        if (session.uiTheme.audioEngineEnabled) cols.add("audio")
        return cols
    }

    private fun getCvLabels(session: llm.slop.liquidlsd.SessionContext): List<String> {
        val labels = mutableListOf<String>()
        if (session.uiTheme.showLfoCol) labels.add("LFO")
        if (session.uiTheme.sequencerEnabled) labels.add("SEQ")
        if (session.uiTheme.audioEngineEnabled) labels.add("AUD")
        return labels
    }

    fun getVisibleColumns(session: llm.slop.liquidlsd.SessionContext): List<String> {
        val visibleCols = mutableListOf("value")
        if (session.uiTheme.midiEnabled) visibleCols.add("midi")
        visibleCols.addAll(getCvColumns(session))
        return visibleCols
    }

    fun getColumnOffset(session: llm.slop.liquidlsd.SessionContext, colId: String): Float {
        val metrics = GridMetrics.compute(session)
        val visibleCols = getVisibleColumns(session)
        
        val targetId = if (colId == "final") "value" else colId
        val index = visibleCols.indexOf(targetId)
        if (index < 0) return 0f
        
        return index * (metrics.cell + metrics.cellPad)
    }

    private fun getCvColor(colId: String, alpha: Float = 1f): Int {
        return CvTheme.getThemeColor(colId, alpha)
    }

    fun getKebabWidth(session: llm.slop.liquidlsd.SessionContext): Float {
        val scrollbarW = ImGui.getStyle().scrollbarSize
        return maxOf(18f, scrollbarW)
    }

    fun calculateHeaderHeight(session: llm.slop.liquidlsd.SessionContext): Float {
        val subTabH = (session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.getTextLineHeight() + 8f
        }.coerceAtLeast(26f)) * 1.5f
        return subTabH + 4f
    }

    /** VAL/MIDI/LFO/SEQ/AUD column headers above Deep Edit's parameter grid, with the section tabs (SRC/FX, CTRL/FX/TRANS) drawn inside them. */
    fun drawColumnHeaders(
        session: llm.slop.liquidlsd.SessionContext,
        labelColW: Float,
        state: ParametersState,
        mixer: Mixer,
        metrics: GridMetrics,
        headerH: Float
    ) {
        val CELL = metrics.cell
        val CELL_PAD = metrics.cellPad

        val dl = ImGui.getWindowDrawList()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val mousePos = ImGui.getIO().mousePos
        
        val cvCols = getCvColumns(session)
        val cvLabels = getCvLabels(session)
        
        // Reserve vertical space for headers
        ImGui.dummy(10f, headerH)
        val afterHeadersY = ImGui.getCursorScreenPosY()

        // Render Section Tabs (e.g. [FX], [View]) above first parameter name with inset
        val activeDeck = when (state.activeTopTab) {
            "Deck A" -> mixer.deckA
            "Deck B" -> mixer.deckB
            "Deck BG" -> mixer.deckBG
            "Deck PV" -> mixer.deckPV
            else -> null
        }
        if (state.activeTopTab == "Mixer" || (activeDeck != null && !activeDeck.isEmpty)) {
            val subTabH = (headerH - 4f).coerceAtLeast(24f)
            ImGui.setCursorScreenPos(startX + SECTION_TABS_INSET_X, startY + (headerH - subTabH) * 0.5f)
            ParametersTabs.drawSectionTabs(session, state, mixer, btnH = subTabH)
        }
        
        // Draw VALUE header
        val valueColX = startX + labelColW + getColumnOffset(session, "value")
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
            showTooltip("VAL: Base parameter value and modulation bounds/limits. Right-click or middle-click cell to toggle mute for all modulators.", (valueColX.toInt() shl 16) xor startY.toInt())
        }

        // Draw MIDI header
        if (session.uiTheme.midiEnabled) {
            val midiColX = startX + labelColW + getColumnOffset(session, "midi")
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
                showTooltip("MIDI: Map MIDI CC/Notes from controllers to modulate this parameter.", (midiColX.toInt() shl 16) xor startY.toInt())
            }
        }

        // Draw each column header horizontally
        for ((idx, label) in cvLabels.withIndex()) {
            val cvId = cvCols[idx]
            val colX = startX + labelColW + getColumnOffset(session, cvId)
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
                    "audio" -> "AUD: Audio-reactive modulators (Continuous RMS envelopes & Transient triggers across 4 frequency bands)."
                    else -> "CV Modulator source."
                }
                showTooltip(cvDesc, (colX.toInt() shl 16) xor startY.toInt())
            }
        }

        // ── Draw Column Settings Kebab (⋮) ──────────────────────────────────
        val deckDeps = activeDeck?.analyzeDependencies()
            ?: llm.slop.liquidlsd.presets.PresetDependencies()

        val midiMissing = deckDeps.usesMidi && !session.uiTheme.midiEnabled
        val lfoMissing = deckDeps.usesLfo && !session.uiTheme.showLfoCol
        val seqMissing = deckDeps.usesSeq && !session.uiTheme.sequencerEnabled
        val audioMissing = deckDeps.usesAudio && !session.uiTheme.audioEngineEnabled

        val anyMissing = midiMissing || lfoMissing || seqMissing || audioMissing

        val isMidiVisible = session.uiTheme.midiEnabled
        val lastColId = if (cvCols.isNotEmpty()) cvCols.last() else if (isMidiVisible) "midi" else "value"
        val lastColRightX = startX + labelColW + getColumnOffset(session, lastColId) + CELL
        val kebabX = lastColRightX + CELL_PAD * 0.5f
        val kebabW = getKebabWidth(session)
        val popupId = "parameters_columns_popup"
        val isPopupOpen = ImGui.isPopupOpen(popupId)

        ImGui.setCursorScreenPos(kebabX, startY)
        val isKebabClicked = ImGui.invisibleButton("##parameters_columns_kebab_btn", kebabW, headerH)
        val isKebabHovered = ImGui.isItemHovered()

        if (isKebabHovered || isPopupOpen) {
            dl.addRectFilled(kebabX, startY, kebabX + kebabW, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
        }

        if (isKebabClicked || (isKebabHovered && ImGui.isItemClicked(0))) {
            ImGui.openPopup(popupId)
        }

        // Draw vertical dots
        val dotCol = if (isKebabHovered || isPopupOpen) {
            ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.95f)
        } else {
            ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 0.6f)
        }
        val cx = kebabX + kebabW * 0.5f
        val cy = startY + headerH * 0.5f
        val r = 1.8f
        val dotSpacing = 5.2f
        dl.addCircleFilled(cx, cy - dotSpacing, r, dotCol)
        dl.addCircleFilled(cx, cy, r, dotCol)
        dl.addCircleFilled(cx, cy + dotSpacing, r, dotCol)

        // If any column needed by the patch is missing or engine is off, draw red [!] badge
        if (anyMissing) {
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val badgeX = cx + 5.2f
                val badgeY = cy - 5.2f
                dl.addCircleFilled(badgeX, badgeY, 4.75f, ImGui.colorConvertFloat4ToU32(0.85f, 0.15f, 0.15f, 0.95f))
                val alertText = "!"
                val alertW = ImGui.calcTextSize(alertText).x
                val alertH = ImGui.getTextLineHeight()
                dl.addText(badgeX - alertW * 0.5f, badgeY - alertH * 0.5f, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f), alertText)
            }
        }

        if (isKebabHovered && session.uiTheme.tooltipsEnabled && !isPopupOpen) {
            val key = popupId.hashCode()
            if (anyMissing) {
                showCustomTooltip(key, estimatedWidth = 300f, estimatedHeight = 140f) {
                    ImGui.textColored(0.95f, 0.40f, 0.40f, 1f, "[!] Parameter Columns:")
                    ImGui.text("Active patch uses modulators that are disabled:")
                    if (midiMissing) ImGui.bulletText("MIDI is disabled")
                    if (lfoMissing) ImGui.bulletText("LFO column is hidden")
                    if (seqMissing) ImGui.bulletText("Step Sequencer is disabled")
                    if (audioMissing) ImGui.bulletText("Audio Engine is disabled")
                    ImGui.spacing()
                    ImGui.textDisabled("Click to toggle modulators or enable missing features.")
                }
            } else {
                showTooltip("Configure visible CV columns and modulator engines.", key)
            }
        }

        // Kebab popup menu
        if (ImGui.beginPopup(popupId)) {
            session.uiTheme.h3("Parameter Modulators")
            ImGui.separator()
            ImGui.spacing()

            // 1. MIDI
            val midiVal = imgui.type.ImBoolean(session.uiTheme.midiEnabled)
            if (ImGui.checkbox("MIDI Column##grid_col_kebab", midiVal)) {
                val nextVal = midiVal.get()
                session.uiTheme.midiEnabled = nextVal
                AppPreferencesStore.savePreferences()
                if (nextVal) {
                    llm.slop.liquidlsd.midi.MidiEngine.scanForNewDevices()
                } else {
                    llm.slop.liquidlsd.midi.MidiEngine.close()
                }
            }
            if (deckDeps.usesMidi) {
                ImGui.sameLine()
                if (!session.uiTheme.midiEnabled) {
                    ImGui.textColored(0.95f, 0.40f, 0.40f, 1f, " [!] Needed by patch")
                } else {
                    ImGui.textDisabled(" (used)")
                }
            }

            // 2. LFO
            val lfoVal = imgui.type.ImBoolean(session.uiTheme.showLfoCol)
            if (ImGui.checkbox("LFO Column##grid_col_kebab", lfoVal)) {
                session.uiTheme.showLfoCol = lfoVal.get()
                AppPreferencesStore.savePreferences()
            }
            if (deckDeps.usesLfo) {
                ImGui.sameLine()
                if (!session.uiTheme.showLfoCol) {
                    ImGui.textColored(0.95f, 0.40f, 0.40f, 1f, " [!] Needed by patch")
                } else {
                    ImGui.textDisabled(" (used)")
                }
            }

            // 3. SEQ
            val seqVal = imgui.type.ImBoolean(session.uiTheme.sequencerEnabled)
            if (ImGui.checkbox("Step Sequencer (SEQ)##grid_col_kebab", seqVal)) {
                session.uiTheme.sequencerEnabled = seqVal.get()
                AppPreferencesStore.savePreferences()
            }
            if (deckDeps.usesSeq) {
                ImGui.sameLine()
                if (!session.uiTheme.sequencerEnabled) {
                    ImGui.textColored(0.95f, 0.40f, 0.40f, 1f, " [!] Needed by patch")
                } else {
                    ImGui.textDisabled(" (used)")
                }
            }

            // 4. AUD
            val audioVal = imgui.type.ImBoolean(session.uiTheme.audioEngineEnabled)
            if (ImGui.checkbox("Audio Engine (AUD)##grid_col_kebab", audioVal)) {
                val nextVal = audioVal.get()
                session.uiTheme.audioEngineEnabled = nextVal
                AppPreferencesStore.savePreferences()
                if (nextVal) {
                    session.audioEngine.start()
                } else {
                    session.audioEngine.stop()
                }
            }
            if (deckDeps.usesAudio) {
                ImGui.sameLine()
                if (!session.uiTheme.audioEngineEnabled) {
                    ImGui.textColored(0.95f, 0.40f, 0.40f, 1f, " [!] Needed by patch")
                } else {
                    ImGui.textDisabled(" (used)")
                }
            }

            // Quick action buttons
            if (anyMissing) {
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
                if (ImGui.button("Turn On Needed Columns", -1f, 28f)) {
                    if (deckDeps.usesMidi && !session.uiTheme.midiEnabled) {
                        session.uiTheme.midiEnabled = true
                        llm.slop.liquidlsd.midi.MidiEngine.scanForNewDevices()
                    }
                    if (deckDeps.usesLfo) {
                        session.uiTheme.showLfoCol = true
                    }
                    if (deckDeps.usesSeq) {
                        session.uiTheme.sequencerEnabled = true
                    }
                    if (deckDeps.usesAudio && !session.uiTheme.audioEngineEnabled) {
                        session.uiTheme.audioEngineEnabled = true
                        session.audioEngine.start()
                    }
                    if (deckDeps.usesRandomization) {
                        session.uiTheme.randomizationEnabled = true
                    }
                    AppPreferencesStore.savePreferences()
                }
            }

            ImGui.endPopup()
        }
        
        // Restore cursor
        ImGui.setCursorScreenPos(startX, afterHeadersY)
        ImGui.dummy(0f, 0f)
    }
}
