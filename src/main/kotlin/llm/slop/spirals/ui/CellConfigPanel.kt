package llm.slop.spirals.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.cv.CvHistoryBuffer
import llm.slop.spirals.cv.evaluateModulator
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.GenUnit
import llm.slop.spirals.parameters.ModulationOperator
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.rendering.Mandala

/**
 * Draws the Cell Config panel contents.
 * Call this inside an ImGui.begin("Cell Config") / ImGui.end() block.
 */
object CellConfigPanel {

    private var activeHistory: CvHistoryBuffer? = null
    private val modulatorHistories = mutableMapOf<String, CvHistoryBuffer>()
    private var activeCellId: PatchCellId? = null
    private val virtualModulators = mutableListOf<CvModulator>()
    private var lastActiveIds: Set<String> = emptySet()

    private fun drawEmptyState() {
        val availW = ImGui.getContentRegionAvailX()
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(120f)
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val centerY = startY + availH * 0.42f
        val drawList = ImGui.getWindowDrawList()

        val title = "No cell selected"
        val subtitle = "Patch Grid"
        var titleW = 0f
        var titleH = 0f
        UITheme.withFont(UITheme.FontLevel.H2) {
            val size = ImGui.calcTextSize(title)
            titleW = size.x
            titleH = size.y
        }
        val subtitleSize = ImGui.calcTextSize(subtitle)
        val accent = UITheme.colorU32(UITheme.Colors.ACCENT_R, UITheme.Colors.ACCENT_G, UITheme.Colors.ACCENT_B, 0.42f)
        val muted = UITheme.colorU32(UITheme.Colors.MUTED_R, UITheme.Colors.MUTED_G, UITheme.Colors.MUTED_B, 0.72f)

        drawList.addLine(startX + availW * 0.5f - 28f, centerY - 16f, startX + availW * 0.5f + 28f, centerY - 16f, accent, 2f)
        UITheme.withFont(UITheme.FontLevel.H2) {
            drawList.addText(startX + (availW - titleW) * 0.5f, centerY, muted, title)
        }
        drawList.addText(startX + (availW - subtitleSize.x) * 0.5f, centerY + titleH + 8f, muted, subtitle)
        ImGui.dummy(availW, availH)
    }

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

    fun draw(state: PatchGridState, mixer: Mixer) {
        val cell = state.selectedCell
        val param = state.selectedParam

        if (cell == null || param == null) {
            activeHistory = null
            activeCellId = null
            drawEmptyState()
            return
        }

        val cvId = cell.cvSourceId
        val paramKey = cell.paramKey

        val themeRGB = CvTheme.getThemeColorRGB(cvId)
        val themeColor = CvTheme.getThemeColor(cvId)

        val deck = when {
            paramKey.startsWith("Deck A/") -> mixer.deckA
            paramKey.startsWith("Deck B/") -> mixer.deckB
            else -> null
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
        val isGen = cvId == "gen1" || cvId == "gen2"
        val isAudio = cvId == "audio"
        val isTrigger = cvId == "trigger"
        val hasAdvanced = isBeat || isLfo || isSnh || isGen

        if (cvId == "final") {
            FinalParamSection.draw(state, param, paramKey, themeColor, mandala, modulatorHistories)
            return
        }

        UITheme.h2Colored(themeRGB[0], themeRGB[1], themeRGB[2], 1.0f, paramKey.replace("/", " | "))
        ImGui.separator()
        ImGui.spacing()

        val isVirtual = activeMods.isEmpty()
        if (isVirtual && cvId == "midi") {
            activeHistory = null
            activeCellId = null
            UITheme.caption("No MIDI mapping on this parameter.")
            ImGui.spacing()
            UITheme.caption("To map a controller:")
            UITheme.caption("1. Enable [MIDI Map] in the main menu bar.")
            UITheme.caption("2. Click this cell (which will highlight in cyan).")
            UITheme.caption("3. Turn a knob or move a fader on your MIDI controller.")
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
        val combinedVal = llm.slop.spirals.cv.getCombinedEffectiveValue(activeMods, isBipolar)
        activeHistory?.add(combinedVal)

        // -- Unified Oscilloscope ---------------------------------
        OscilloscopeDrawer.drawOscilloscope(param, themeColor, activeHistory)

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

    private fun replaceModulator(state: PatchGridState, param: llm.slop.spirals.parameters.ModulatableParameter, newMod: CvModulator) {
        val idx = param.modulators.indexOfFirst { it.id == newMod.id }
        val existing = if (idx >= 0) param.modulators[idx] else virtualModulators.firstOrNull { it.id == newMod.id }
        val wasBypassed = existing?.bypassed ?: false

        // Auto-activate: if amplitude is adjusted to a non-zero value, activate/unbypass the modulator
        val finalizedMod = if (wasBypassed && newMod.bypassed && newMod.amplitude != 0.0f) {
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
