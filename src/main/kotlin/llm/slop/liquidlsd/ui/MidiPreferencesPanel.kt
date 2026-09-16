package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImString
import imgui.type.ImInt
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTableColumnFlags
import llm.slop.liquidlsd.midi.MidiEngine
import llm.slop.liquidlsd.midi.MidiMessageType
import llm.slop.liquidlsd.midi.MidiInputType
import llm.slop.liquidlsd.midi.TriggerMode
import llm.slop.liquidlsd.midi.TakeoverMode
import llm.slop.liquidlsd.midi.sanitiseProfileName

/**
 * Dedicated UI component for the MIDI hardware & control mapping editor:
 * - Hardware scan / enable toggle for the MIDI subsystem.
 * - Mapping profile CRUD (create, load, save, delete).
 * - Live MIDI sniffer table of recently received hardware events.
 * - Global performance action CC/Note mapping table.
 * - Per-parameter mapping table with inline combo/drag editing.
 *
 * Rendered within the "MIDI Controls" category of [PreferencesPanel].
 */
object MidiPreferencesPanel {

    private val newProfileInput = ImString(32)
    private val filterMappingInput = ImString(64)

    fun drawContent(session: llm.slop.liquidlsd.SessionContext, parametersState: ParametersState?) {
        session.uiTheme.withFont(UITheme.FontLevel.H2) {
            ImGui.text("MIDI Hardware & Control Mappings")
        }
        session.uiTheme.caption("Configure hardware MIDI controllers, live input sniffing, profiles, and parameter bindings.")
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 1. Hardware Status & Global Toggle
        val midiEnabled = ImBoolean(session.uiTheme.midiEnabled)
        if (ImGui.checkbox("Enable MIDI Subsystem", midiEnabled)) {
            val nextVal = midiEnabled.get()
            if (nextVal != session.uiTheme.midiEnabled) {
                session.uiTheme.midiEnabled = nextVal
                AppPreferencesStore.savePreferences()
                if (nextVal) {
                    MidiEngine.scanForNewDevices()
                } else {
                    MidiEngine.close()
                }
            }
        }
        ImGui.sameLine(0f, 20f)
        if (ImGui.button("${Icons.REFRESH} Rescan Controllers##midi_rescan")) {
            MidiEngine.scanForNewDevices()
        }

        val midiCount = MidiEngine.getActiveDeviceCount()
        val deviceNames = MidiEngine.getConnectedDeviceNames()
        if (midiCount == 0) {
            session.uiTheme.captionColored(0.9f, 0.6f, 0.2f, 1.0f, "Status: No hardware MIDI controllers detected.")
        } else {
            val namesStr = deviceNames.joinToString(", ")
            session.uiTheme.captionColored(0.2f, 0.9f, 0.4f, 1.0f, "Status: $midiCount active controller(s) connected ($namesStr).")
        }

        if (!session.uiTheme.midiEnabled) {
            ImGui.spacing()
            session.uiTheme.captionColored(0.6f, 0.6f, 0.6f, 1.0f, "MIDI is currently disabled. Check 'Enable MIDI Subsystem' to activate input processing.")
            return
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 2. Profile Management Bar
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Active Mapping Profile")
        }
        val profiles = session.midiMappingManager.listProfiles()
        val currentIdx = ImInt(profiles.indexOf(session.uiTheme.activeMidiProfile).coerceAtLeast(0))
        val profileArray = profiles.toTypedArray()
        ImGui.setNextItemWidth(200f)
        if (ImGui.combo("##active_profile_combo", currentIdx, profileArray)) {
            val selected = profileArray[currentIdx.get()]
            session.midiMappingManager.loadProfile(selected)
            session.uiTheme.activeMidiProfile = selected
            AppPreferencesStore.savePreferences()
        }
        ImGui.sameLine()
        if (ImGui.button("${Icons.SAVE} Save##midi_save_profile")) {
            session.midiMappingManager.saveActiveProfile()
        }
        ImGui.sameLine()
        val canDelete = session.midiMappingManager.activeProfileName != "default"
        if (!canDelete) ImGui.beginDisabled()
        if (ImGui.button("${Icons.TRASH} Delete##midi_delete_profile")) {
            session.midiMappingManager.deleteProfile(session.midiMappingManager.activeProfileName)
            session.uiTheme.activeMidiProfile = "default"
            AppPreferencesStore.savePreferences()
        }
        if (!canDelete) ImGui.endDisabled()

        ImGui.sameLine(0f, 20f)
        ImGui.setNextItemWidth(140f)
        ImGui.inputTextWithHint("##new_profile_name", "New Profile Name", newProfileInput)
        ImGui.sameLine()
        if (ImGui.button("${Icons.PLUS} Create Profile##midi_create_profile")) {
            val name = newProfileInput.get().trim()
            if (name.isNotEmpty()) {
                val safeName = runCatching { sanitiseProfileName(name) }.getOrNull() ?: name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                session.midiMappingManager.loadProfile(safeName)
                session.midiMappingManager.saveActiveProfile()
                session.uiTheme.activeMidiProfile = safeName
                AppPreferencesStore.savePreferences()
                newProfileInput.set("")
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 3. Live MIDI Monitor (Sniffer)
        if (ImGui.collapsingHeader("${Icons.ACTIVITY} Live MIDI Monitor (Sniffer)##midi_monitor", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            val recent = MidiEngine.getRecentEvents().reversed().take(8)
            if (recent.isEmpty()) {
                session.uiTheme.caption("No recent MIDI messages received. Turn a knob, press a pad, or move a fader on your controller.")
            } else {
                val monitorTableFlags = ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp
                if (ImGui.beginTable("##midi_monitor_table", 5, monitorTableFlags)) {
                    ImGui.tableSetupColumn("Type", ImGuiTableColumnFlags.WidthFixed, 90f)
                    ImGui.tableSetupColumn("Channel", ImGuiTableColumnFlags.WidthFixed, 60f)
                    ImGui.tableSetupColumn("Index (#)", ImGuiTableColumnFlags.WidthFixed, 70f)
                    ImGui.tableSetupColumn("Raw Val", ImGuiTableColumnFlags.WidthFixed, 70f)
                    ImGui.tableSetupColumn("Normalized", ImGuiTableColumnFlags.WidthStretch, 1f)
                    ImGui.tableHeadersRow()

                    for (evt in recent) {
                        ImGui.tableNextRow()
                        ImGui.tableNextColumn()
                        val (r, g, b) = when (evt.type) {
                            MidiMessageType.CC -> Triple(0.2f, 0.7f, 1.0f)
                            MidiMessageType.NOTE -> Triple(0.2f, 0.9f, 0.4f)
                            MidiMessageType.PITCH_BEND -> Triple(0.9f, 0.5f, 0.9f)
                        }
                        ImGui.textColored(r, g, b, 1f, evt.type.name)
                        ImGui.tableNextColumn()
                        ImGui.text("${evt.channel + 1}")
                        ImGui.tableNextColumn()
                        ImGui.text("${evt.index}")
                        ImGui.tableNextColumn()
                        ImGui.text("${evt.rawValue}")
                        ImGui.tableNextColumn()
                        ImGui.progressBar(evt.normalizedValue.coerceIn(0f, 1f), -1f, 12f, "%.2f".format(evt.normalizedValue))
                    }
                    ImGui.endTable()
                }
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 4. Global Action Triggers
        if (ImGui.collapsingHeader("Global Performance Action Triggers##midi_globals", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            val globalActions = listOf(
                "Global/queueNext" to "Queue Advance A/B Next",
                "Global/queuePrev" to "Queue Step Back A/B Prev",
                "Global/bgQueueNext" to "BG Shader Advance Next",
                "Global/bgQueuePrev" to "BG Shader Step Back Prev",
                "Global/tapTempo" to "Tap Tempo Trigger"
            )

            val gTableFlags = ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp
            if (ImGui.beginTable("##global_actions_table", 3, gTableFlags)) {
                ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, 220f)
                ImGui.tableSetupColumn("Assigned Input", ImGuiTableColumnFlags.WidthStretch, 1f)
                ImGui.tableSetupColumn("Controls", ImGuiTableColumnFlags.WidthFixed, 140f)
                ImGui.tableHeadersRow()

                for ((actionKey, actionLabel) in globalActions) {
                    val mapping = session.midiMappingManager.getMappingForParameter(actionKey)
                    ImGui.tableNextRow()
                    ImGui.tableNextColumn()
                    session.uiTheme.body(actionLabel)
                    session.uiTheme.captionColored(0.6f, 0.6f, 0.6f, 1.0f, actionKey)

                    ImGui.tableNextColumn()
                    if (mapping != null && mapping.cc != -1) {
                        val typeLabel = mapping.messageType.name
                        ImGui.textColored(0.3f, 0.8f, 1.0f, 1.0f, "$typeLabel Ch ${mapping.channel + 1} # ${mapping.cc}")
                    } else {
                        ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "Unassigned")
                    }

                    ImGui.tableNextColumn()
                    val isLearning = parametersState?.midiLearnTarget?.let {
                        it is MidiLearnTarget.GlobalAction && it.actionKey == actionKey
                    } ?: false

                    if (isLearning) {
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.72f, 0.45f, 1.00f, 0.6f)
                        if (ImGui.button("Cancel##cancel_$actionKey")) {
                            parametersState.midiLearnTarget = null
                        }
                        ImGui.popStyleColor()
                    } else {
                        if (ImGui.button("Learn##learn_$actionKey")) {
                            parametersState?.let { ps ->
                                ps.midiLearnTarget = MidiLearnTarget.GlobalAction(actionKey)
                                ps.midiLearnStartTimeMs = System.currentTimeMillis()
                            }
                        }
                    }
                    ImGui.sameLine()
                    if (ImGui.button("Clear##clear_$actionKey")) {
                        session.midiMappingManager.removeMapping(actionKey)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                ImGui.endTable()
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 5. Active Parameter Mappings Table
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Parameter Mappings")
        }
        ImGui.sameLine(0f, 20f)
        ImGui.setNextItemWidth(200f)
        ImGui.inputTextWithHint("##filter_mappings", "Filter by path...", filterMappingInput)
        ImGui.sameLine()
        if (ImGui.button("${Icons.TRASH} Clear All##midi_clear_all")) {
            session.midiMappingManager.clearAllMappings()
            session.midiMappingManager.saveActiveProfile()
        }

        val mappings = session.midiMappingManager.getMappings()
            .filter { !it.key.startsWith("Global/") }
            .filter { filterMappingInput.get().isEmpty() || it.key.contains(filterMappingInput.get(), ignoreCase = true) }

        if (mappings.isEmpty()) {
            ImGui.spacing()
            session.uiTheme.caption("No parameter mappings in this profile. Click 'Learn MIDI' on any knob or grid cell in the main UI to map hardware controls.")
        } else {
            val paramTableFlags = ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp or ImGuiTableFlags.ScrollY
            if (ImGui.beginTable("##param_mappings_table", 8, paramTableFlags, 0f, 320f)) {
                ImGui.tableSetupColumn("Parameter", ImGuiTableColumnFlags.WidthStretch, 1.2f)
                ImGui.tableSetupColumn("Target", ImGuiTableColumnFlags.WidthFixed, 90f)
                ImGui.tableSetupColumn("Input Type", ImGuiTableColumnFlags.WidthFixed, 140f)
                ImGui.tableSetupColumn("Mode", ImGuiTableColumnFlags.WidthFixed, 120f)
                ImGui.tableSetupColumn("Min / Max", ImGuiTableColumnFlags.WidthFixed, 130f)
                ImGui.tableSetupColumn("Invert", ImGuiTableColumnFlags.WidthFixed, 50f)
                ImGui.tableSetupColumn("Slew", ImGuiTableColumnFlags.WidthFixed, 80f)
                ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, 60f)
                ImGui.tableHeadersRow()

                val inputTypeValues = MidiInputType.values()
                val inputTypeNames = inputTypeValues.map { it.name }.toTypedArray()
                val triggerModeValues = TriggerMode.values()
                val triggerModeNames = triggerModeValues.map { it.name }.toTypedArray()
                val takeoverModeValues = TakeoverMode.values()
                val takeoverModeNames = takeoverModeValues.map { it.name }.toTypedArray()

                for ((paramPath, map) in mappings) {
                    ImGui.tableNextRow()

                    // Col 0: Path & Takeover Status
                    ImGui.tableNextColumn()
                    session.uiTheme.body(paramPath)
                    if (map.takeoverMode == TakeoverMode.SOFT_TAKEOVER) {
                        val isLocked = session.midiMappingManager.isSoftTakeoverActive(paramPath)
                        if (!isLocked) {
                            val physPos = session.midiMappingManager.getPhysicalPosition(paramPath)
                            val physText = if (physPos != null) " (Phys: %.2f)".format(physPos) else ""
                            session.uiTheme.captionColored(1.0f, 0.65f, 0.2f, 1.0f, "${Icons.ALERT} Awaiting Pickup$physText")
                        } else {
                            session.uiTheme.captionColored(0.2f, 0.8f, 0.4f, 1.0f, "Takeover Synced")
                        }
                    }

                    // Col 1: Target Channel & CC/Note
                    ImGui.tableNextColumn()
                    ImGui.text("${map.messageType.name} Ch ${map.channel + 1}")
                    session.uiTheme.caption("# ${map.cc}")

                    // Col 2: Input Type Combo
                    ImGui.tableNextColumn()
                    val typeIdx = ImInt(inputTypeValues.indexOf(map.inputType).coerceAtLeast(0))
                    ImGui.setNextItemWidth(130f)
                    if (ImGui.combo("##type_$paramPath", typeIdx, inputTypeNames)) {
                        val newType = inputTypeValues[typeIdx.get()]
                        session.midiMappingManager.updateMapping(paramPath, map.copy(inputType = newType))
                        session.midiMappingManager.saveActiveProfile()
                    }

                    // Col 3: Trigger Mode / Takeover Mode Combo
                    ImGui.tableNextColumn()
                    ImGui.setNextItemWidth(110f)
                    if (map.inputType == MidiInputType.BUTTON_NOTE || map.inputType == MidiInputType.BUTTON_CC) {
                        val trgIdx = ImInt(triggerModeValues.indexOf(map.triggerMode).coerceAtLeast(0))
                        if (ImGui.combo("##trg_$paramPath", trgIdx, triggerModeNames)) {
                            val newTrg = triggerModeValues[trgIdx.get()]
                            session.midiMappingManager.updateMapping(paramPath, map.copy(triggerMode = newTrg))
                            session.midiMappingManager.saveActiveProfile()
                        }
                    } else if (map.inputType == MidiInputType.CONTINUOUS_CC || map.inputType == MidiInputType.PITCH_BEND) {
                        val toIdx = ImInt(takeoverModeValues.indexOf(map.takeoverMode).coerceAtLeast(0))
                        if (ImGui.combo("##to_$paramPath", toIdx, takeoverModeNames)) {
                            val newTo = takeoverModeValues[toIdx.get()]
                            session.midiMappingManager.updateMapping(paramPath, map.copy(takeoverMode = newTo))
                            session.midiMappingManager.saveActiveProfile()
                        }
                    } else {
                        // Rotary step size
                        val stepArr = floatArrayOf(map.stepSize)
                        if (ImGui.dragFloat("##step_$paramPath", stepArr, 0.005f, 0.001f, 0.5f, "Step: %.3f")) {
                            session.midiMappingManager.updateMapping(paramPath, map.copy(stepSize = stepArr[0]))
                            session.midiMappingManager.saveActiveProfile()
                        }
                    }

                    // Col 4: Min / Max Range
                    ImGui.tableNextColumn()
                    val minArr = floatArrayOf(map.minVal)
                    val maxArr = floatArrayOf(map.maxVal)
                    ImGui.setNextItemWidth(55f)
                    val changedMin = ImGui.dragFloat("##min_$paramPath", minArr, 0.01f, -100f, 100f, "%.2f")
                    ImGui.sameLine()
                    ImGui.setNextItemWidth(55f)
                    val changedMax = ImGui.dragFloat("##max_$paramPath", maxArr, 0.01f, -100f, 100f, "%.2f")
                    if (changedMin || changedMax) {
                        session.midiMappingManager.updateMapping(paramPath, map.copy(minVal = minArr[0], maxVal = maxArr[0]))
                        session.midiMappingManager.saveActiveProfile()
                    }

                    // Col 5: Invert Checkbox
                    ImGui.tableNextColumn()
                    val inv = ImBoolean(map.inverted)
                    if (ImGui.checkbox("##inv_$paramPath", inv)) {
                        session.midiMappingManager.updateMapping(paramPath, map.copy(inverted = inv.get()))
                        session.midiMappingManager.saveActiveProfile()
                    }

                    // Col 6: Slew (ms)
                    ImGui.tableNextColumn()
                    val slewArr = floatArrayOf(map.slewMs)
                    ImGui.setNextItemWidth(70f)
                    if (ImGui.dragFloat("##slew_$paramPath", slewArr, 1f, 0f, 250f, "%.0f ms")) {
                        session.midiMappingManager.updateMapping(paramPath, map.copy(slewMs = slewArr[0]))
                        session.midiMappingManager.saveActiveProfile()
                    }

                    // Col 7: Actions (Delete)
                    ImGui.tableNextColumn()
                    if (ImGui.button("${Icons.TRASH}##del_$paramPath")) {
                        session.midiMappingManager.removeMapping(paramPath)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                ImGui.endTable()
            }
        }
    }
}
