package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.CvHistoryBuffer
import llm.slop.liquidlsd.cv.evaluateModulator
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.GenUnit
import llm.slop.liquidlsd.parameters.ModulationOperator
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Mandala

private val AUDIO_BANDS = listOf("audio_amp", "audio_bass", "audio_mid", "audio_high")
private val TRIGGER_BANDS = listOf("trigger_onset", "trigger_accent")

/**
 * Draws the Cell Config panel contents.
 * Call this inside an ImGui.begin("Cell Config") / ImGui.end() block.
 */
object CellConfigPanel {

    private var activeHistory: CvHistoryBuffer? = null
    private var ghostHistory: CvHistoryBuffer? = null
    private var activeCellId: PresetCellId? = null
    private val virtualModulators = mutableListOf<CvModulator>()
    private var lastActiveIds: Set<String> = emptySet()

    private fun initializeVirtualModulators(cvId: String, activeMods: List<CvModulator>, hasAdvanced: Boolean) {
        virtualModulators.clear()
        if (cvId == "audio") {
            for (band in AUDIO_BANDS) {
                val exists = activeMods.any { it.sourceId == band }
                if (!exists) {
                    virtualModulators.add(CvModulator(sourceId = band, bypassed = true))
                }
            }
        } else if (cvId == "trigger") {
            for (band in TRIGGER_BANDS) {
                val exists = activeMods.any { it.sourceId == band }
                if (!exists) {
                    virtualModulators.add(CvModulator(sourceId = band, bypassed = true))
                }
            }
        } else {
            if (activeMods.isEmpty()) {
                virtualModulators.add(CvModulator(sourceId = cvId, bypassed = true))
            }
        }
    }

    private fun drawCvTabRow(session: llm.slop.liquidlsd.SessionContext, state: PresetGridState, currentParamKey: String, currentCvId: String) {
        val availableTabs = mutableListOf<Pair<String, String>>()
        availableTabs.add("Value" to "value")
        if (session.uiTheme.showMidiCol) availableTabs.add("MIDI" to "midi")
        if (session.uiTheme.showLfoCol) availableTabs.add("LFO" to "lfo")
        if (session.uiTheme.audioEngineEnabled) {
            if (session.uiTheme.showAudioCol) availableTabs.add("Audio" to "audio")
            if (session.uiTheme.showTriggerCol) availableTabs.add("Trigger" to "trigger")
        }

        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
        val btnH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() + 8f * fontScale }.coerceAtLeast(24f * fontScale)

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 4f * fontScale, 4f * fontScale)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            availableTabs.forEachIndexed { i, (label, targetCvId) ->
                if (i > 0) ImGui.sameLine()
                val isActive = currentCvId == targetCvId || (targetCvId == "value" && currentCvId == "final")
                if (isActive) {
                    val activeCol = when (targetCvId) {
                        "value", "final" -> ImGui.colorConvertFloat4ToU32(0.0f, 0.7f, 0.5f, 1f)
                        "midi"    -> ImGui.colorConvertFloat4ToU32(0.5f, 0.2f, 0.8f, 1f)
                        "lfo"     -> ImGui.colorConvertFloat4ToU32(0.0f, 0.5f, 0.8f, 1f)
                        "audio"   -> ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.0f, 1f)
                        "trigger" -> ImGui.colorConvertFloat4ToU32(0.8f, 0.0f, 0.4f, 1f)
                        else      -> ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 1f)
                    }
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        activeCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, activeCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  activeCol)
                } else {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
                }
                val btnW = (ImGui.calcTextSize(label).x + 18f * fontScale).coerceAtLeast(44f * fontScale)
                if (ImGui.button(label, btnW, btnH)) {
                    state.selectedCell = PresetCellId(currentParamKey, targetCvId)
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Switch CellConfig view to $label CV modulation for parameter")
                }
                ImGui.popStyleColor(3)
            }
        }
        ImGui.popStyleVar()
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext, state: PresetGridState, mixer: Mixer) {
        val cell = state.selectedCell
        val param = state.selectedParam

        if (cell == null || param == null) {
            activeHistory = null
            activeCellId = null
            session.uiTheme.caption("Click a cell in the Preset Grid to configure it.")
            return
        }

        val cvId = cell.cvSourceId
        val paramKey = cell.paramKey

        // Render top CV tab bar
        drawCvTabRow(session, state, paramKey, cvId)

        val themeRGB = CvTheme.getThemeColorRGB(cvId)
        val themeColor = CvTheme.getThemeColor(cvId)

        val deck = when {
            paramKey.startsWith("Deck A/") -> mixer.deckA
            paramKey.startsWith("Deck B/") -> mixer.deckB
            paramKey.startsWith("Deck BG/") -> mixer.deckBG
            paramKey.startsWith("Deck PV/") -> mixer.deckPV
            else -> null
        }
        if (deck?.isEmpty == true) {
            activeHistory = null
            activeCellId = null
            session.uiTheme.caption("Deck is empty. Add a source or load a preset to configure cell modulation.")
            return
        }
        val mandala = deck?.source as? Mandala

        val activeMods = if (cvId == "midi") {
            param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
        } else if (cvId == "audio") {
            param.modulators.filter { llm.slop.liquidlsd.cv.isAudioSource(it.sourceId) }
        } else if (cvId == "trigger") {
            param.modulators.filter { llm.slop.liquidlsd.cv.isTriggerSource(it.sourceId) }
        } else {
            param.modulators.filter { it.sourceId == cvId }
        }

        val isBeat = cvId == "beatPhase"
        val isLfo = cvId == "lfo"
        val isSnh = cvId == "sampleAndHold"
        val isGen = cvId == "lfo"
        val hasAdvanced = isBeat || isLfo || isSnh

        if (cvId == "value" || cvId == "final") {
            ValueParamSection.draw(session, state, param, paramKey, themeColor, mandala)
            return
        }

        session.uiTheme.h2Colored(themeRGB[0], themeRGB[1], themeRGB[2], 1.0f, paramKey.replace("/", " | "))
        ImGui.separator()
        ImGui.spacing()

        val isVirtual = activeMods.isEmpty()
        if (isVirtual && cvId == "midi") {
            activeHistory = null
            activeCellId = null
            session.uiTheme.caption("No MIDI mapping on this parameter.")
            ImGui.spacing()
            session.uiTheme.caption("To map a controller:")
            session.uiTheme.caption("1. Enable [MIDI Map] in the main menu bar.")
            session.uiTheme.caption("2. Click this cell (which will highlight in cyan).")
            session.uiTheme.caption("3. Turn a knob or move a fader on your MIDI controller.")
            return
        }

        // Initialize or update oscilloscope history and virtual modulators
        val currentActiveIds = activeMods.map { it.id }.toSet()
        if (activeCellId != cell || activeHistory == null || currentActiveIds != lastActiveIds) {
            activeHistory = CvHistoryBuffer(600)
            ghostHistory = if (cvId == "audio") CvHistoryBuffer(600) else null
            activeCellId = cell
            lastActiveIds = currentActiveIds
            initializeVirtualModulators(cvId, activeMods, hasAdvanced)
        }

        var modsToDraw = activeMods + virtualModulators.filter { vm -> activeMods.none { am -> am.id == vm.id } }
        if (cvId == "audio") {
            modsToDraw = modsToDraw.sortedBy { AUDIO_BANDS.indexOf(it.sourceId) }
        } else if (cvId == "trigger") {
            modsToDraw = modsToDraw.sortedBy { TRIGGER_BANDS.indexOf(it.sourceId) }
        }
        val isBipolar = param.minClamp < 0f
        val hasAnyUnbypassed = activeMods.any { !it.bypassed }
        // If there are unbypassed modulators, only evaluate active ones.
        // If all are bypassed, evaluate with includeBypassed = true so the preview oscilloscope works in muted state.
        val targetMods = if (hasAnyUnbypassed) activeMods.filter { !it.bypassed } else activeMods
        val combinedVal = llm.slop.liquidlsd.cv.getCombinedEffectiveValue(targetMods, isBipolar, includeBypassed = true)
        activeHistory?.add(combinedVal)

        if (cvId == "audio") {
            if (ghostHistory == null) ghostHistory = CvHistoryBuffer(600)
            var rawResult = 0f
            var first = true
            for (mod in targetMods) {
                val rawCv = llm.slop.liquidlsd.cv.CVRegistry.get(mod.sourceId)
                val modAmount = rawCv * mod.depth + mod.dcOffset
                if (first) {
                    rawResult = when (mod.operator) {
                        llm.slop.liquidlsd.parameters.ModulationOperator.ADD -> modAmount
                        llm.slop.liquidlsd.parameters.ModulationOperator.MUL -> modAmount
                        llm.slop.liquidlsd.parameters.ModulationOperator.SCALE -> 1.0f - mod.depth + modAmount
                    }
                    first = false
                } else {
                    rawResult = when (mod.operator) {
                        llm.slop.liquidlsd.parameters.ModulationOperator.ADD -> rawResult + modAmount
                        llm.slop.liquidlsd.parameters.ModulationOperator.MUL -> rawResult * (1.0f + modAmount)
                        llm.slop.liquidlsd.parameters.ModulationOperator.SCALE -> rawResult * (1.0f - mod.depth + modAmount)
                    }
                }
            }
            val clampedRaw = if (isBipolar) rawResult.coerceIn(-1f, 1f) else rawResult.coerceIn(0f, 1f)
            ghostHistory?.add(clampedRaw)
        } else {
            ghostHistory = null
        }

        // -- Unified Oscilloscope ---------------------------------
        OscilloscopeDrawer.drawOscilloscope(session, param, themeColor, activeHistory, activeMods, scopeKey = cvId, ghostHistory = ghostHistory)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // -- Modulators (Scrollable body below sticky header & oscilloscope) --
        val childFlags = if (CustomRangeSlider.isAnySliderHovered) imgui.flag.ImGuiWindowFlags.NoScrollWithMouse else 0
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        if (ImGui.beginChild("##cell_config_mods_scroll", 0f, 0f, false, childFlags)) {
            for ((idx, existing) in modsToDraw.withIndex()) {
                ImGui.pushID(existing.id)
                
                val bypassed = existing.bypassed
                val currentThemeColor = CvTheme.getThemeColor(existing.sourceId)
                
                val panelStartX = ImGui.getCursorScreenPosX()
                val panelStartY = ImGui.getCursorScreenPosY()
                val dl = ImGui.getWindowDrawList()
                
                val isMultiBand = modsToDraw.size > 1
                val isBandActive = !existing.bypassed && existing.depth != 0.0f
                val bandLabel = when (existing.sourceId) {
                    "audio_amp" -> "Amplitude / Master"
                    "audio_bass" -> "Low / Bass"
                    "audio_mid" -> "Mid"
                    "audio_high" -> "High"
                    "trigger_onset" -> "Onset / Beat"
                    "trigger_accent" -> "Accent / Peak"
                    else -> "Modulator ${idx + 1}"
                }
                val dirtyMarker = if (isBandActive) " [ACTIVE] •" else if (!existing.bypassed) " •" else ""
                val headerTitle = "$bandLabel$dirtyMarker"
                val defaultOpen = if (isBandActive || (idx == 0 && activeMods.isEmpty()) || existing.sourceId == "audio_bass" || !isMultiBand) imgui.flag.ImGuiTreeNodeFlags.DefaultOpen else 0

                val isHeaderOpen = if (isMultiBand) ImGui.collapsingHeader(headerTitle, defaultOpen) else true
                if (isHeaderOpen) {
                    ModulatorHeaderRow.draw(
                        session = session,
                        existing = existing,
                        idx = idx,
                        modsToDraw = modsToDraw,
                        isVirtual = isVirtual,
                        isLfo = isLfo,
                        hasAdvanced = hasAdvanced,
                        onReplace = { newMod -> replaceModulator(state, param, newMod, mixer) },
                        onReset = {
                            val toRemove = activeMods.toList()
                            for (mod in toRemove) {
                                param.modulators.remove(mod)
                            }
                        }
                    )

                    if (bypassed) {
                        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f) // Re-push style var for sub-controls
                    }

                    ImGui.spacing()

                    when {
                        llm.slop.liquidlsd.cv.isAudioSource(existing.sourceId) -> {
                            // Draw dedicated Audio Envelope Follower + dynamics controls
                            AudioModulatorSection.draw(
                                session = session,
                                param = param,
                                existing = existing,
                                themeColor = currentThemeColor,
                                onReplace = { newMod -> replaceModulator(state, param, newMod, mixer) }
                            )
                        }
                        llm.slop.liquidlsd.cv.isTriggerSource(existing.sourceId) -> {
                            // Draw dedicated Trigger transient impulse controls
                            TriggerModulatorSection.draw(
                                session = session,
                                param = param,
                                existing = existing,
                                themeColor = currentThemeColor,
                                onReplace = { newMod -> replaceModulator(state, param, newMod, mixer) }
                            )
                        }
                        existing.sourceId.startsWith("midi_cc_") -> {
                            // Draw dedicated MIDI CC controller controls
                            MidiModulatorSection.draw(
                                session = session,
                                param = param,
                                existing = existing,
                                themeColor = currentThemeColor,
                                onReplace = { newMod -> replaceModulator(state, param, newMod, mixer) }
                            )
                        }
                        else -> {
                            // Draw LFO 1 / generator carrier timing and waveshaping controls
                            Lfo1Section.draw(
                                session = session,
                                param = param,
                                existing = existing,
                                isBeat = isBeat,
                                isSnh = isSnh,
                                isGen = isGen,
                                hasAdvanced = hasAdvanced,
                                themeColor = currentThemeColor,
                                onReplace = { newMod -> replaceModulator(state, param, newMod, mixer) }
                            )

                            // Draw LFO 2 / secondary generator modulator controls
                            if (isGen) {
                                Lfo2Section.draw(
                                    session = session,
                                    param = param,
                                    existing = existing,
                                    idx = idx,
                                    themeColor = currentThemeColor,
                                    onReplace = { newMod -> replaceModulator(state, param, newMod, mixer) }
                                )
                            }
                        }
                    }

                    ImGui.unindent(10f) // Unindent at the end of block
                    
                    if (bypassed) {
                        ImGui.popStyleVar()
                    }
                    
                    val panelEndY = ImGui.getCursorScreenPosY()
                    
                    // Draw margin line for active modulators
                    if (!bypassed) {
                        dl.addLine(panelStartX + 2f, panelStartY, panelStartX + 2f, panelEndY - 10f, currentThemeColor, 4f)
                    }
                }

                ImGui.popID()
                if (idx < modsToDraw.size - 1) {
                    ImGui.spacing()
                    ImGui.separator()
                    ImGui.spacing()
                }
            }
            ImGui.endChild()
        }
        ImGui.popStyleVar()
    }

    private fun replaceModulator(state: PresetGridState, param: llm.slop.liquidlsd.parameters.ModulatableParameter, newMod: CvModulator, mixer: Mixer? = null) {
        val idx = param.modulators.indexOfFirst { it.id == newMod.id }
        val wasBypassed = if (idx >= 0) param.modulators[idx].bypassed else true
        if (idx >= 0) {
            param.modulators[idx] = newMod
        } else {
            // Newly added from virtual placeholder: activate immediately so it routes to parameter
            val modToAdd = if (newMod.bypassed && wasBypassed) newMod.copy(bypassed = false) else newMod
            param.modulators.add(modToAdd)
        }
        if (wasBypassed && !newMod.bypassed && state.selectedCell?.paramKey == "Mixer/crossfade") {
            mixer?.onCrossfadeCvUnmuted()
        }
    }
}
