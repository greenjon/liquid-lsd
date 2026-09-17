package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.CvHistoryBuffer
import llm.slop.liquidlsd.cv.evaluateModulator
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.GenUnit
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ModulationOperator
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.DynamicVisualSource

private val AUDIO_RMS_BANDS = listOf("audio_amp", "audio_bass", "audio_mid", "audio_high")

/**
 * Draws the Properties panel contents.
 * Call this inside an ImGui.begin("Properties") / ImGui.end() block.
 */
object PropertiesPanel {

    private var activeHistory: CvHistoryBuffer? = null
    private var ghostHistory: CvHistoryBuffer? = null
    private var activeCellId: ParameterCellId? = null
    private val virtualModulators = mutableListOf<CvModulator>()
    private var lastActiveIds: Set<String> = emptySet()

    private fun initializeVirtualModulators(cvId: String, activeMods: List<CvModulator>, hasAdvanced: Boolean) {
        virtualModulators.clear()
        if (cvId == "audio") {
            if (activeMods.isEmpty()) {
                virtualModulators.add(CvModulator(id = "virtual_audio_1", sourceId = "audio_amp", depth = 0.5f, bypassed = true))
                virtualModulators.add(CvModulator(id = "virtual_audio_2", sourceId = "audio_flux_bass", depth = 0.5f, bypassed = true))
            } else if (activeMods.size == 1) {
                val fallbackSource = if (activeMods[0].sourceId.startsWith("audio_flux_")) "audio_amp" else "audio_flux_bass"
                virtualModulators.add(CvModulator(id = "virtual_audio_2", sourceId = fallbackSource, depth = 0.5f, bypassed = true))
            }
        } else {
            if (activeMods.isEmpty()) {
                val defaultDepth = if (cvId == "seq") 1.0f else 0.5f
                virtualModulators.add(CvModulator(id = "virtual_$cvId", sourceId = cvId, depth = defaultDepth, bypassed = true))
            }
        }
    }

    private fun drawCvTabRow(
        session: llm.slop.liquidlsd.SessionContext,
        state: ParametersState,
        currentParamKey: String,
        currentCvId: String,
        param: ModulatableParameter
    ) {
        val availableTabs = mutableListOf<Pair<String, String>>()
        availableTabs.add("Value" to "value")
        if (session.uiTheme.midiEnabled) availableTabs.add("MIDI" to "midi")
        if (session.uiTheme.showLfoCol) availableTabs.add("LFO" to "lfo")
        if (session.uiTheme.sequencerEnabled) availableTabs.add("SEQ" to "seq")
        if (session.uiTheme.audioEngineEnabled) {
            availableTabs.add("Audio" to "audio")
        }

        val fontScale = 0.95f
        val btnH = (session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 8f * fontScale }.coerceAtLeast(26f * fontScale)) * 1.5f

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 4f * fontScale, 4f * fontScale)
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            availableTabs.forEachIndexed { i, (label, targetCvId) ->
                if (i > 0) ImGui.sameLine()
                val isActive = currentCvId == targetCvId || (targetCvId == "value" && currentCvId == "final")
                if (isActive) {
                    val rgb = CvTheme.getThemeColorRGB(targetCvId)
                    val activeCol = ImGui.colorConvertFloat4ToU32(rgb[0] * 0.70f, rgb[1] * 0.70f, rgb[2] * 0.70f, 1f)
                    val hoverCol = ImGui.colorConvertFloat4ToU32(rgb[0] * 0.85f, rgb[1] * 0.85f, rgb[2] * 0.85f, 1f)
                    val pressedCol = ImGui.colorConvertFloat4ToU32(rgb[0], rgb[1], rgb[2], 1f)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        activeCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, hoverCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  pressedCol)
                } else {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
                }
                val btnW = (ImGui.calcTextSize(label).x + 18f * fontScale).coerceAtLeast(44f * fontScale)
                if (ImGui.button(label, btnW, btnH)) {
                    state.selectedCell = ParameterCellId(currentParamKey, targetCvId)
                }
                itemTooltip("Switch Properties view to $label CV modulation for parameter")
                ImGui.popStyleColor(3)
            }

            // Right-aligned [ LIVE ] / [ MUTED ] Master Cell Mute Toggle
            val liveMods = if (currentCvId == "value" || currentCvId == "final") {
                param.modulators
            } else if (currentCvId == "midi") {
                param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
            } else if (currentCvId == "audio") {
                param.modulators.filter { llm.slop.liquidlsd.cv.isAudioSource(it.sourceId) }
            } else {
                param.modulators.filter { it.sourceId == currentCvId }
            }

            if (liveMods.isNotEmpty()) {
                val isMuted = liveMods.all { it.bypassed }
                val btnText = if (isMuted) "[ MUTED ]" else "[ LIVE ]"
                val maxBtnW = maxOf(ImGui.calcTextSize("[ MUTED ]").x, ImGui.calcTextSize("[ LIVE ]").x)
                val liveBtnW = (maxBtnW + 18f * fontScale).coerceAtLeast(57f)

                val rightX = ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - liveBtnW
                if (rightX > ImGui.getCursorPosX() + 4f) {
                    ImGui.sameLine(rightX)
                } else {
                    ImGui.sameLine()
                }

                if (isMuted) {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.1f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.9f, 0.7f, 0.2f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.3f, 1f))
                } else {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.1f, 0.5f, 0.4f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.5f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 0.6f, 1f))
                }

                if (ImGui.button(btnText, liveBtnW, btnH)) {
                    val targetBypassed = !isMuted
                    val updated = param.modulators.map { mod ->
                        if (liveMods.any { it.id == mod.id }) mod.copy(bypassed = targetBypassed) else mod
                    }
                    param.modulators.clear()
                    param.modulators.addAll(updated)
                }
                val tip = if (currentCvId == "value" || currentCvId == "final") {
                    if (isMuted) "Unmute all parameter modulation" else "Mute all parameter modulation"
                } else {
                    if (isMuted) "Unmute cell modulation (Route to Value)" else "Mute cell modulation (Preview on O-scope)"
                }
                itemTooltip(tip)
                ImGui.popStyleColor(3)
            }
        }
        ImGui.popStyleVar()
        ImGui.spacing()
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, mixer: Mixer) {
        PanelTitleBar.draw(session, "Properties")

        val cell = state.selectedCell
        val param = state.selectedParam

        if (cell == null || param == null) {
            activeHistory = null
            activeCellId = null
            session.uiTheme.caption("Click a parameter or cell to view its properties.")
            return
        }

        val resolvedParam = llm.slop.liquidlsd.parameters.ParameterResolver.findParameterByPath(mixer, cell.paramKey)
        if (resolvedParam == null || resolvedParam !== param) {
            state.clearSelection()
            activeHistory = null
            activeCellId = null
            session.uiTheme.caption("Click a parameter or cell to view its properties.")
            return
        }

        val cvId = cell.cvSourceId
        val paramKey = cell.paramKey

        // Render top CV tab bar
        drawCvTabRow(session, state, paramKey, cvId, param)

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
        val dynamicSource = deck?.source as? DynamicVisualSource

        val activeMods = if (cvId == "midi") {
            param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
        } else if (cvId == "audio") {
            param.modulators.filter { llm.slop.liquidlsd.cv.isAudioSource(it.sourceId) }
        } else {
            param.modulators.filter { it.sourceId == cvId }
        }

        val isBeat = cvId == "beatPhase"
        val isLfo = cvId == "lfo"
        val isSnh = cvId == "sampleAndHold"
        val isGen = cvId == "lfo"
        val hasAdvanced = isBeat || isLfo || isSnh

        if (cvId == "value" || cvId == "final") {
            ValueParamSection.draw(session, state, param, paramKey, themeColor, dynamicSource)
            return
        }

        val isVirtual = activeMods.isEmpty()
        if (isVirtual && cvId == "midi") {
            activeHistory = null
            activeCellId = null
            session.uiTheme.caption("No MIDI controller bound to this parameter.")
            ImGui.spacing()
            session.uiTheme.caption("To connect a MIDI controller, click 'Learn MIDI' and move a knob, fader, or button on your device:")
            ImGui.spacing()

            val currentTarget = state.midiLearnTarget
            val isThisCellLearning = currentTarget is MidiLearnTarget.GridCell && currentTarget.cellId == cell

            if (isThisCellLearning) {
                imgui.ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.72f, 0.45f, 1.00f, 0.6f)
                imgui.ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.80f, 0.55f, 1.00f, 0.8f)
                if (imgui.ImGui.button("${Icons.REFRESH} Waiting for MIDI CC... (Click to Cancel)##midi_learn")) {
                    state.midiLearnTarget = null
                }
                imgui.ImGui.popStyleColor(2)
            } else {
                if (imgui.ImGui.button("Learn MIDI##midi_learn")) {
                    state.midiLearnTarget = MidiLearnTarget.GridCell(cell, param)
                    state.midiLearnStartTimeMs = System.currentTimeMillis()
                    if (llm.slop.liquidlsd.midi.MidiEngine.getActiveDeviceCount() == 0) {
                        PopupManager.globalPendingMidiWarning = true
                    }
                }
            }
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
            modsToDraw = modsToDraw.take(2)
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
        ImGui.pushID("${cell.paramKey}_${cell.cvSourceId}")
        if (ImGui.beginChild("##cell_config_mods_scroll", 0f, 0f, false, childFlags)) {
            for ((idx, existing) in modsToDraw.withIndex()) {
                ImGui.pushID(existing.sourceId.ifEmpty { existing.id })
                
                val bypassed = existing.bypassed
                val currentThemeColor = CvTheme.getThemeColor(existing.sourceId)
                // MacroBinding.modulatorIndex is consumed by MacroEngine as an index into
                // param.modulators (the full, unfiltered list) — but `idx` above is only the
                // position within modsToDraw (activeMods filtered to this cvId, plus virtual
                // placeholders). Those only coincide when this is the sole modulator on the
                // parameter; any additional modulator of another sourceId (e.g. an audio-reactive
                // envelope stacked alongside this LFO) shifts the real index, causing macro binds
                // created here to silently drive the wrong CvModulator. Falls back to `idx` for
                // virtual (not-yet-added) placeholders, which aren't in param.modulators at all.
                val globalModIndex = param.modulators.indexOfFirst { it.id == existing.id }.let { if (it >= 0) it else idx }
                
                val panelStartX = ImGui.getCursorScreenPosX()
                val panelStartY = ImGui.getCursorScreenPosY()
                val dl = ImGui.getWindowDrawList()
                
                // For Audio Slot 2 when inactive/virtual: render a clean collapsed enable bar
                if (cvId == "audio" && idx == 1 && idx >= activeMods.size) {
                    val fontScale = 0.95f
                    val btnH = session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 8f * fontScale }.coerceAtLeast(26f * fontScale)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.28f, 0.28f, 0.28f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, themeColor)
                    if (ImGui.button("${Icons.PLUS} Enable Audio Slot 2##enable_audio_2", ImGui.getContentRegionAvailX(), btnH)) {
                        val newMod = existing.copy(id = java.util.UUID.randomUUID().toString(), bypassed = false, depth = 0.5f)
                        replaceModulator(state, param, newMod, mixer)
                    }
                    itemTooltip("Enable a second concurrent audio-reactive modulator on this parameter.")
                    ImGui.popStyleColor(3)
                    ImGui.popID()
                    continue
                }

                val isMultiBand = if (cvId == "audio") activeMods.size > 1 else modsToDraw.size > 1
                val isBandActive = !existing.bypassed && existing.depth != 0.0f
                val bandLabel = when (existing.sourceId) {
                    "audio_amp" -> "Full Mix (RMS)"
                    "audio_bass" -> "Low / Bass (RMS)"
                    "audio_mid" -> "Mid (RMS)"
                    "audio_high" -> "High (RMS)"
                    "audio_flux_amp" -> "Full Mix (Flux)"
                    "audio_flux_bass" -> "Low / Kick (Flux)"
                    "audio_flux_mid" -> "Mid / Snare (Flux)"
                    "audio_flux_high" -> "High / Hat (Flux)"
                    else -> "Modulator ${idx + 1}"
                }
                val dirtyMarker = if (isBandActive) " [ACTIVE] •" else if (!existing.bypassed) " •" else ""
                val headerTitle = if (cvId == "audio") "Audio ${idx + 1}: $bandLabel$dirtyMarker###audio_slot_${idx + 1}" else "$bandLabel$dirtyMarker###band_header"
                val defaultOpen = 0

                val isHeaderOpen = if (isMultiBand) ImGui.collapsingHeader(headerTitle, defaultOpen) else true
                if (isHeaderOpen) {
                    ImGui.indent(10f)
                    ModulatorHeaderRow.draw(
                        session = session,
                        existing = existing,
                        idx = idx,
                        modsToDraw = modsToDraw,
                        isVirtual = isVirtual,
                        isLfo = isLfo,
                        hasAdvanced = hasAdvanced,
                        isRandomizeDisabled = param.isRandomizeDisabled,
                        randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                        onReplace = { newMod -> replaceModulator(state, param, newMod, mixer) },
                        onReset = {
                            if (cvId == "audio") {
                                param.modulators.remove(existing)
                            } else {
                                val toRemove = activeMods.toList()
                                for (mod in toRemove) {
                                    param.modulators.remove(mod)
                                }
                            }
                        }
                    )

                    if (bypassed) {
                        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f) // Re-push style var for sub-controls
                    }

                    ImGui.spacing()

                    // Macro Learn indicator for "depth" — the one modulator property that still needs
                    // a bespoke chip because it lives inside the combined dcOffset/depth "Modulation
                    // Range" dual slider (drawMinMaxRangeSlider) rather than its own label-clickable
                    // row. Every other property (subdivision, phase, morph, hold, slope, attack/decay,
                    // and all LFO2 mod* fields) is now bound by clicking its slider's name label directly.
                    val isMacroLearning = llm.slop.liquidlsd.macro.MacroLearnState.isLearning()
                    if (isMacroLearning) {
                        ImGui.textColored(0.2f, 0.85f, 1.0f, 1.0f, "${Icons.REFRESH} Click a slider's name to bind it, or:")
                        ImGui.sameLine()
                        if (ImGui.smallButton("+depth##bind_${cell.paramKey}_${idx}_depth")) {
                            llm.slop.liquidlsd.macro.MacroLearnState.bindTarget(
                                bank = llm.slop.liquidlsd.macro.MacroEngine.globalBank(),
                                targetType = llm.slop.liquidlsd.macro.MacroTargetType.MODULATOR_PROPERTY,
                                parameterId = cell.paramKey,
                                modulatorIndex = globalModIndex,
                                propertyName = "depth",
                                minVal = 0f,
                                maxVal = 2f
                            )
                        }
                        ImGui.spacing()
                    }

                    val boundProps = llm.slop.liquidlsd.macro.MacroEngine.findBindingsTargeting(null, cell.paramKey, modulatorIndex = globalModIndex)
                    if (boundProps.isNotEmpty()) {
                        val propNames = boundProps.joinToString(", ") { it.propertyName }
                        val bank = llm.slop.liquidlsd.macro.MacroEngine.globalBank()
                        val owner = bank.knobs.find { k -> k.bindings.any { boundProps.contains(it) } }
                            ?: bank.switches.find { s -> s.bindings.any { boundProps.contains(it) } }
                        val ownerName = owner?.label?.ifEmpty { owner.id } ?: "Macro"

                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.1f, 0.45f, 0.65f, 0.6f))
                        if (ImGui.button("${Icons.LOCK} Properties [$propNames] controlled by $ownerName. Click to inspect##macro_inspect_${cell.paramKey}_$idx", ImGui.getContentRegionAvailX(), 22f)) {
                            if (owner != null) {
                                llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId = owner.id
                            }
                            session.uiTheme.column3Mode = UITheme.Column3Mode.MACROS
                        }
                        ImGui.popStyleColor()
                        itemTooltip("These modulator properties are continuously updated by a Macro Control. Uncheck their bindings in the Column 3 Binding Inspector to release them.")
                        ImGui.spacing()
                    }

                    when {
                        llm.slop.liquidlsd.cv.isAudioSource(existing.sourceId) -> {
                            // Draw dedicated Audio Envelope Follower + dynamics controls
                            AudioModulatorSection.draw(
                                session = session,
                                param = param,
                                existing = existing,
                                paramKey = cell.paramKey,
                                modulatorIndex = globalModIndex,
                                themeColor = currentThemeColor,
                                onReplace = { newMod -> replaceModulator(state, param, newMod, mixer) }
                            )
                        }
                        existing.sourceId.startsWith("midi_cc_") -> {
                            // Draw dedicated MIDI CC controller controls
                            MidiModulatorSection.draw(
                                session = session,
                                state = state,
                                cell = cell,
                                param = param,
                                existing = existing,
                                themeColor = currentThemeColor,
                                onReplace = { newMod -> replaceModulator(state, param, newMod, mixer) },
                                onUnbind = {
                                    ParametersUndo.pushUndoState(state, mixer)
                                    param.modulators.removeAll { it.sourceId.startsWith("midi_cc_") }
                                }
                            )
                        }
                        existing.sourceId == "seq" -> {
                            // Draw Step Sequencer controls
                            SeqSection.draw(
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
                                paramKey = cell.paramKey,
                                modulatorIndex = globalModIndex,
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
                                    paramKey = cell.paramKey,
                                    idx = globalModIndex,
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
        }
        ImGui.endChild()
        ImGui.popID()
        ImGui.popStyleVar()
    }

    private fun replaceModulator(state: ParametersState, param: llm.slop.liquidlsd.parameters.ModulatableParameter, newMod: CvModulator, mixer: Mixer? = null) {
        val idx = param.modulators.indexOfFirst { it.id == newMod.id || (it.sourceId.isNotEmpty() && it.sourceId == newMod.sourceId) }
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
