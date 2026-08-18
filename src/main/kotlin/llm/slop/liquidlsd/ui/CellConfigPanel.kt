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

/**
 * Draws the Cell Config panel contents.
 * Call this inside an ImGui.begin("Cell Config") / ImGui.end() block.
 */
object CellConfigPanel {

    private var activeHistory: CvHistoryBuffer? = null
    private val modulatorHistories = mutableMapOf<String, CvHistoryBuffer>()
    private var activeCellId: PresetCellId? = null
    private val virtualModulators = mutableListOf<CvModulator>()
    private var lastActiveIds: Set<String> = emptySet()

    private fun initializeVirtualModulators(cvId: String, activeMods: List<CvModulator>, hasAdvanced: Boolean) {
        virtualModulators.clear()
        if (cvId == "audio") {
            val bands = listOf("audio_amp", "audio_bass", "audio_mid", "audio_high")
            for (band in bands) {
                val exists = activeMods.any { it.sourceId == band }
                if (!exists) {
                    virtualModulators.add(CvModulator(sourceId = band, bypassed = true))
                }
            }
        } else if (cvId == "trigger") {
            val bands = listOf("trigger_onset", "trigger_accent")
            for (band in bands) {
                val exists = activeMods.any { it.sourceId == band }
                if (!exists) {
                    virtualModulators.add(CvModulator(sourceId = band, bypassed = true))
                }
            }
        } else if (hasAdvanced) {
            val activeCount = activeMods.size
            if (activeCount == 0) {
                virtualModulators.add(CvModulator(sourceId = cvId, bypassed = true))
                // virtualModulators.add(CvModulator(sourceId = cvId, bypassed = true))
            } else if (activeCount == 1) {
                // virtualModulators.add(CvModulator(sourceId = cvId, bypassed = true))
            }
        } else {
            if (activeMods.isEmpty()) {
                virtualModulators.add(CvModulator(sourceId = cvId, bypassed = true))
            }
        }
    }

    private fun drawCvTabRow(session: llm.slop.liquidlsd.SessionContext, state: PresetGridState, currentParamKey: String, currentCvId: String) {
        val availableTabs = mutableListOf<Pair<String, String>>()
        availableTabs.add("Final" to "final")
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
                val isActive = currentCvId == targetCvId
                if (isActive) {
                    val activeCol = when (targetCvId) {
                        "final"   -> ImGui.colorConvertFloat4ToU32(0.0f, 0.7f, 0.5f, 1f)
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
            paramKey.startsWith("Deck C/") -> mixer.deckC
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
            param.modulators.filter { it.sourceId in setOf("audio_amp", "audio_bass", "audio_mid", "audio_high") }
        } else if (cvId == "trigger") {
            param.modulators.filter { it.sourceId in setOf("trigger_onset", "trigger_accent") }
        } else {
            param.modulators.filter { it.sourceId == cvId }
        }
        val isMidiMod = cvId == "midi"

        val isBeat = cvId == "beatPhase"
        val isLfo = cvId == "lfo"
        val isSnh = cvId == "sampleAndHold"
        val isGen = cvId == "lfo"
        val isAudio = cvId == "audio"
        val isTrigger = cvId == "trigger"
        val hasAdvanced = isBeat || isLfo || isSnh || isGen

        if (cvId == "final") {
            FinalParamSection.draw(session, state, param, paramKey, themeColor, mandala, modulatorHistories)
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
            activeHistory = CvHistoryBuffer(200)
            activeCellId = cell
            lastActiveIds = currentActiveIds
            initializeVirtualModulators(cvId, activeMods, hasAdvanced)
        }

        var modsToDraw = activeMods + virtualModulators.filter { vm -> activeMods.none { am -> am.id == vm.id } }
        if (cvId == "audio") {
            val order = listOf("audio_amp", "audio_bass", "audio_mid", "audio_high")
            modsToDraw = modsToDraw.sortedBy { order.indexOf(it.sourceId) }
        } else if (cvId == "trigger") {
            val order = listOf("trigger_onset", "trigger_accent")
            modsToDraw = modsToDraw.sortedBy { order.indexOf(it.sourceId) }
        }
        val isBipolar = param.minClamp < 0f
        // Use the same formula as the engine so the O-scope displays what the parameter actually receives.
        val combinedVal = llm.slop.liquidlsd.cv.getCombinedEffectiveValue(activeMods, isBipolar)
        activeHistory?.add(combinedVal)

        // -- Unified Oscilloscope ---------------------------------
        OscilloscopeDrawer.drawOscilloscope(session, param, themeColor, activeHistory, activeMods)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // -- Modulators -------------------------------------------
        for ((idx, existing) in modsToDraw.withIndex()) {
            ImGui.pushID(existing.id)
            
            val bypassed = existing.bypassed
            val currentThemeColor = CvTheme.getThemeColor(existing.sourceId)
            
            val panelStartX = ImGui.getCursorScreenPosX()
            val panelStartY = ImGui.getCursorScreenPosY()
            val dl = ImGui.getWindowDrawList()
            
            ModulatorHeaderRow.draw(
                session = session,
                state = state,
                param = param,
                existing = existing,
                idx = idx,
                modsToDraw = modsToDraw,
                activeMods = activeMods,
                isVirtual = isVirtual,
                isLfo = isLfo,
                hasAdvanced = hasAdvanced,
                onReplace = { newMod -> replaceModulator(state, param, newMod) },
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

            // Draw LFO 1 / primary timing/wave controls
            Lfo1Section.draw(
                session = session,
                state = state,
                param = param,
                existing = existing,
                isLfo = isLfo,
                isBeat = isBeat,
                isSnh = isSnh,
                isGen = isGen,
                hasAdvanced = hasAdvanced,
                themeColor = currentThemeColor,
                onReplace = { newMod -> replaceModulator(state, param, newMod) }
            )

            // Draw LFO 2 / secondary generator modulator controls
            if (isGen) {
                Lfo2Section.draw(
                    session = session,
                    state = state,
                    param = param,
                    existing = existing,
                    idx = idx,
                    themeColor = currentThemeColor,
                    onReplace = { newMod -> replaceModulator(state, param, newMod) }
                )
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

            ImGui.popID()
            if (idx < modsToDraw.size - 1) {
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }
        }
    }

    private fun replaceModulator(state: PresetGridState, param: llm.slop.liquidlsd.parameters.ModulatableParameter, newMod: CvModulator) {
        val idx = param.modulators.indexOfFirst { it.id == newMod.id }
        val existing = if (idx >= 0) param.modulators[idx] else virtualModulators.firstOrNull { it.id == newMod.id }
        val wasBypassed = existing?.bypassed ?: false

        // Auto-activate: if depth is adjusted to a non-zero value, activate/unbypass the modulator
        val finalizedMod = if (wasBypassed && newMod.bypassed && newMod.depth != 0.0f) {
            newMod.copy(bypassed = false)
        } else {
            newMod
        }

        if (idx >= 0) {
            param.modulators[idx] = finalizedMod
        } else {
            param.modulators.add(finalizedMod)
        }
    }
}
