package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.midi.ParameterCellId
import llm.slop.liquidlsd.midi.MidiLearnTarget
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
                    val activeRgb = floatArrayOf(rgb[0] * 0.70f, rgb[1] * 0.70f, rgb[2] * 0.70f)
                    val hoverRgb = floatArrayOf(rgb[0] * 0.85f, rgb[1] * 0.85f, rgb[2] * 0.85f)
                    val ink = TangoPalette.inkFor(activeRgb, hoverRgb, rgb)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(activeRgb[0], activeRgb[1], activeRgb[2], 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(hoverRgb[0], hoverRgb[1], hoverRgb[2], 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(rgb[0], rgb[1], rgb[2], 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
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
                ImGui.popStyleColor(if (isActive) 4 else 3)
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
                    val ink = TangoPalette.inkFor(floatArrayOf(0.8f, 0.6f, 0.1f), floatArrayOf(0.9f, 0.7f, 0.2f), floatArrayOf(1.0f, 0.8f, 0.3f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.1f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.9f, 0.7f, 0.2f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.3f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
                } else {
                    val ink = TangoPalette.inkFor(floatArrayOf(0.1f, 0.5f, 0.4f), floatArrayOf(0.2f, 0.6f, 0.5f), floatArrayOf(0.3f, 0.7f, 0.6f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.1f, 0.5f, 0.4f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.5f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 0.6f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
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
                ImGui.popStyleColor(4)
            }
        }
        ImGui.popStyleVar()
        ImGui.spacing()
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext, state: ParametersState, mixer: Mixer) {
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
            session.uiTheme.caption("No MIDI controller mapped to this parameter.")
            ImGui.spacing()
            session.uiTheme.caption("To connect a MIDI controller, click 'Learn MIDI' and move a knob, fader, or button on your device:")
            ImGui.spacing()

            val currentTarget = state.midiLearnTarget
            val isThisCellLearning = currentTarget is MidiLearnTarget.GridCell && currentTarget.cellId == cell

            if (isThisCellLearning) {
                val ink = TangoPalette.inkFor(floatArrayOf(0.72f, 0.45f, 1.00f), floatArrayOf(0.80f, 0.55f, 1.00f))
                imgui.ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.72f, 0.45f, 1.00f, 0.6f)
                imgui.ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.80f, 0.55f, 1.00f, 0.8f)
                imgui.ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
                if (imgui.ImGui.button("${Icons.REFRESH} Waiting for MIDI CC... (Click to Cancel)##midi_learn")) {
                    state.midiLearnTarget = null
                }
                imgui.ImGui.popStyleColor(3)
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
                // Display-only position in the full param.modulators list (used for "LFO n"
                // labels). Bindings and mappings are keyed by CvModulator.id, not this index.
                // Falls back to `idx` for virtual (not-yet-added) placeholders.
                val globalModIndex = param.modulators.indexOfFirst { it.id == existing.id }.let { if (it >= 0) it else idx }
                
                val panelStartX = ImGui.getCursorScreenPosX()
                val panelStartY = ImGui.getCursorScreenPosY()
                val dl = ImGui.getWindowDrawList()
                
                // For Audio Slot 2 when inactive/virtual: render a clean collapsed enable bar
                if (cvId == "audio" && idx == 1 && idx >= activeMods.size) {
                    val fontScale = 0.95f
                    val btnH = session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 8f * fontScale }.coerceAtLeast(26f * fontScale)
                    val enableAudioInk = TangoPalette.inkFor(floatArrayOf(0.18f, 0.18f, 0.18f), floatArrayOf(0.28f, 0.28f, 0.28f), CvTheme.getThemeColorRGB(cvId))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.28f, 0.28f, 0.28f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, themeColor)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, enableAudioInk[0], enableAudioInk[1], enableAudioInk[2], 1.0f)
                    if (ImGui.button("${Icons.PLUS} Enable Audio Slot 2##enable_audio_2", ImGui.getContentRegionAvailX(), btnH)) {
                        val newMod = existing.copy(id = java.util.UUID.randomUUID().toString(), bypassed = false, depth = 0.5f)
                        replaceModulator(state, param, newMod, mixer)
                    }
                    itemTooltip("Enable a second concurrent audio-reactive modulator on this parameter.")
                    ImGui.popStyleColor(4)
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

                    val isMacroLearning = llm.slop.liquidlsd.macro.MacroLearnState.isLearning()
                    if (isMacroLearning) {
                        ImGui.textColored(0.2f, 0.85f, 1.0f, 1.0f, "${Icons.REFRESH} Click a slider's name to add it as a target.")
                        ImGui.spacing()
                    }

                    // Read the controls' own bindings (not the enabled-only cache) so a target the user
                    // just unchecked stays listed and can be re-enabled.
                    val boundInfos = llm.slop.liquidlsd.macro.MacroEngine.findBindingInfos(null, cell.paramKey, modulatorId = existing.id, includeDisabled = true)
                    if (boundInfos.isNotEmpty()) {
                        for (info in boundInfos) {
                            val b = info.binding
                            if (b.targetType != llm.slop.liquidlsd.macro.MacroTargetType.MODULATOR_PROPERTY) continue
                            ImGui.pushID("macro_bind_${info.control.id}_${b.propertyName}")
                            session.uiTheme.caption("${if (b.enabled) Icons.LOCK else Icons.UNLOCK} [${b.propertyName}] ${if (b.enabled) "controlled by" else "released from"} ${info.controlName} [${info.badgeLabel}]")
                            itemTooltip("This modulator property is continuously updated by a macro knob. Uncheck the target to release it.")
                            if (MacroBindingEditor.drawFull(session, info.control, b, null, ImGui.getContentRegionAvailX() - 10f)) {
                                info.control.bindings.remove(b)
                                llm.slop.liquidlsd.macro.MacroEngine.invalidate()
                            }
                            ImGui.popID()
                        }
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
                                paramKey = cell.paramKey,
                                modulatorIndex = globalModIndex,
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
