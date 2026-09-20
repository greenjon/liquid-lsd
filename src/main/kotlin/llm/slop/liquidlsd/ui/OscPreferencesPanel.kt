package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTableColumnFlags
import llm.slop.liquidlsd.osc.OscEngine
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.osc.OscPacketDirection
import llm.slop.liquidlsd.osc.OscPreferences
import llm.slop.liquidlsd.osc.OscTakeoverMode
import llm.slop.liquidlsd.osc.sanitiseOscProfileName

/**
 * Dedicated UI component for the OSC / TouchOSC transport & mapping editor:
 * - Enable toggle, inbound/outbound port configuration, and learned remote client status.
 * - Mapping profile CRUD (create, load, save, delete).
 * - Live OSC packet sniffer log showing recent inbound/outbound traffic.
 * - Interactive "Learn OSC" binding flow and an editable address -> parameter mapping table.
 *
 * Rendered within the "OSC Controls" category of [PreferencesPanel].
 */
object OscPreferencesPanel {

    private val newProfileInput = ImString(32)
    private val learnParamInput = ImString(128)
    private val filterMappingInput = ImString(64)

    fun drawContent(session: llm.slop.liquidlsd.SessionContext) {
        val theme = session.uiTheme
        theme.withFont(UITheme.FontLevel.H2) {
            ImGui.text("OSC / TouchOSC Control Surface")
        }
        theme.caption("Configure the UDP OSC 1.0 server for wireless control from TouchOSC and other OSC surfaces.")
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 1. Enable Toggle & Port Configuration
        val enabled = ImBoolean(OscPreferences.enabled)
        if (ImGui.checkbox("Enable OSC Server", enabled)) {
            val nextVal = enabled.get()
            if (nextVal != OscPreferences.enabled) {
                OscPreferences.enabled = nextVal
                OscPreferences.savePreferences()
                if (nextVal) {
                    OscEngine.start(OscPreferences.inboundPort, OscPreferences.outboundPort)
                } else {
                    OscEngine.stop()
                }
            }
        }

        ImGui.spacing()
        val inPortField = ImInt(OscPreferences.inboundPort)
        ImGui.setNextItemWidth(100f)
        if (ImGui.inputInt("Inbound Port (Server)##osc_in_port", inPortField)) {
            OscPreferences.inboundPort = inPortField.get().coerceIn(1, 65535)
            OscPreferences.savePreferences()
        }
        itemTooltip("UDP port Liquid LSD listens on for incoming OSC messages from TouchOSC. Default 8000. Restart the OSC server to apply changes.")

        val outPortField = ImInt(OscPreferences.outboundPort)
        ImGui.setNextItemWidth(100f)
        if (ImGui.inputInt("Outbound Port (Feedback)##osc_out_port", outPortField)) {
            OscPreferences.outboundPort = outPortField.get().coerceIn(1, 65535)
            OscPreferences.savePreferences()
        }
        itemTooltip("UDP port used to send feedback packets (macro values, labels) back to the learned TouchOSC client. Default 9000.")

        if (OscPreferences.enabled) {
            ImGui.sameLine(0f, 20f)
            if (ImGui.button("${Icons.REFRESH} Restart Server##osc_restart")) {
                OscEngine.stop()
                OscEngine.start(OscPreferences.inboundPort, OscPreferences.outboundPort)
            }
            itemTooltip("Restart the UDP server with the currently configured ports.")
        }

        ImGui.spacing()
        if (OscEngine.isRunning) {
            theme.captionColored(0.2f, 0.9f, 0.4f, 1.0f, "${Icons.ACTIVITY} Server listening on UDP port ${OscEngine.boundInboundPort()}. Remote client: ${OscEngine.remoteAddressLabel()}")
        } else {
            theme.captionColored(0.6f, 0.6f, 0.6f, 1.0f, "OSC server is stopped. Check 'Enable OSC Server' to activate input processing.")
        }

        if (!OscPreferences.enabled) {
            return
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 2. Profile Management Bar
        theme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Active Mapping Profile")
        }
        val profiles = OscMappingManager.listProfiles()
        val currentIdx = ImInt(profiles.indexOf(OscPreferences.activeProfile).coerceAtLeast(0))
        val profileArray = profiles.toTypedArray()
        ImGui.setNextItemWidth(200f)
        if (ImGui.combo("##osc_active_profile_combo", currentIdx, profileArray)) {
            val selected = profileArray[currentIdx.get()]
            OscMappingManager.loadProfile(selected)
            OscPreferences.activeProfile = selected
            OscPreferences.savePreferences()
        }
        ImGui.sameLine()
        if (ImGui.button("${Icons.SAVE} Save##osc_save_profile")) {
            OscMappingManager.saveActiveProfile()
        }
        ImGui.sameLine()
        val canDelete = OscMappingManager.activeProfileName != "default"
        if (!canDelete) ImGui.beginDisabled()
        if (ImGui.button("${Icons.TRASH} Delete##osc_delete_profile")) {
            OscMappingManager.deleteProfile(OscMappingManager.activeProfileName)
            OscPreferences.activeProfile = "default"
            OscPreferences.savePreferences()
        }
        if (!canDelete) ImGui.endDisabled()

        ImGui.sameLine(0f, 20f)
        ImGui.setNextItemWidth(140f)
        ImGui.inputTextWithHint("##osc_new_profile_name", "New Profile Name", newProfileInput)
        ImGui.sameLine()
        if (ImGui.button("${Icons.PLUS} Create Profile##osc_create_profile")) {
            val name = newProfileInput.get().trim()
            if (name.isNotEmpty()) {
                val safeName = runCatching { sanitiseOscProfileName(name) }.getOrNull() ?: name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                OscMappingManager.loadProfile(safeName)
                OscMappingManager.saveActiveProfile()
                OscPreferences.activeProfile = safeName
                OscPreferences.savePreferences()
                newProfileInput.set("")
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 3. Live Packet Sniffer
        if (ImGui.collapsingHeader("${Icons.ACTIVITY} Live Packet Sniffer##osc_sniffer", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            val recent = OscEngine.getRecentPackets().reversed().take(10)
            if (recent.isEmpty()) {
                theme.caption("No recent OSC traffic. Move a control on your TouchOSC layout to see packets here.")
            } else {
                val monitorTableFlags = ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp
                if (ImGui.beginTable("##osc_monitor_table", 4, monitorTableFlags)) {
                    ImGui.tableSetupColumn("Dir", ImGuiTableColumnFlags.WidthFixed, 40f)
                    ImGui.tableSetupColumn("Address", ImGuiTableColumnFlags.WidthStretch, 1.2f)
                    ImGui.tableSetupColumn("Args", ImGuiTableColumnFlags.WidthStretch, 1f)
                    ImGui.tableSetupColumn("From/To", ImGuiTableColumnFlags.WidthFixed, 110f)
                    ImGui.tableHeadersRow()

                    for (pkt in recent) {
                        ImGui.tableNextRow()
                        ImGui.tableNextColumn()
                        if (pkt.direction == OscPacketDirection.IN) {
                            ImGui.textColored(0.2f, 0.7f, 1.0f, 1f, "IN")
                        } else {
                            ImGui.textColored(0.9f, 0.6f, 0.2f, 1f, "OUT")
                        }
                        ImGui.tableNextColumn()
                        ImGui.text(pkt.address)
                        ImGui.tableNextColumn()
                        theme.caption(pkt.argsSummary)
                        ImGui.tableNextColumn()
                        theme.caption(pkt.remoteHost)
                    }
                    ImGui.endTable()
                }
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 4. Learn OSC
        theme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Learn OSC")
        }
        val activeStatus = OscLearnState.getActiveStatus()
        if (activeStatus != null) {
            theme.captionColored(0.9f, 0.75f, 0.2f, 1.0f, activeStatus)
        }
        ImGui.setNextItemWidth(320f)
        ImGui.inputTextWithHint("##osc_learn_target", "Target path (e.g. Deck A/geometry/zoom:mod/0/subdivision)", learnParamInput)
        ImGui.sameLine()
        if (OscLearnState.isLearning()) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.72f, 0.45f, 1.00f, 0.6f)
            if (ImGui.button("Cancel##osc_learn_cancel")) {
                OscLearnState.cancelLearn()
            }
            ImGui.popStyleColor()
        } else {
            val target = learnParamInput.get().trim()
            if (target.isEmpty()) ImGui.beginDisabled()
            if (ImGui.button("${Icons.ACTIVITY} Start Learn##osc_learn_start")) {
                OscLearnState.startLearn(target, displayLabel = OscMappingManager.formatDisplayPath(target))
            }
            if (target.isEmpty()) ImGui.endDisabled()
        }
        itemTooltip("Type a target parameter path or modulator variable (e.g. 'Deck A/geometry/zoom:mod/0/subdivision'), click Start Learn, then move a control on your OSC surface to bind it. You can also right-click any slider in the UI to Learn OSC directly.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 5. Active Address Mappings Table
        theme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Address Mappings")
        }
        ImGui.sameLine(0f, 20f)
        ImGui.setNextItemWidth(200f)
        ImGui.inputTextWithHint("##osc_filter_mappings", "Filter by address or path...", filterMappingInput)
        ImGui.sameLine()
        if (ImGui.button("${Icons.TRASH} Clear All##osc_clear_all")) {
            OscMappingManager.clearAllMappings()
            OscMappingManager.saveActiveProfile()
        }

        val filterText = filterMappingInput.get()
        val mappings = OscMappingManager.getMappings()
            .filter { filterText.isEmpty() || it.key.contains(filterText, ignoreCase = true) || it.value.parameterPath.contains(filterText, ignoreCase = true) }

        if (mappings.isEmpty()) {
            ImGui.spacing()
            theme.caption("No address mappings in this profile. Use 'Learn OSC' above or right-click any slider in the UI to bind controls.")
        } else {
            val paramTableFlags = ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp or ImGuiTableFlags.ScrollY
            if (ImGui.beginTable("##osc_mappings_table", 7, paramTableFlags, 0f, 320f)) {
                ImGui.tableSetupColumn("OSC Address", ImGuiTableColumnFlags.WidthStretch, 1f)
                ImGui.tableSetupColumn("Parameter", ImGuiTableColumnFlags.WidthStretch, 1.2f)
                ImGui.tableSetupColumn("Min / Max", ImGuiTableColumnFlags.WidthFixed, 130f)
                ImGui.tableSetupColumn("Invert", ImGuiTableColumnFlags.WidthFixed, 50f)
                ImGui.tableSetupColumn("Slew", ImGuiTableColumnFlags.WidthFixed, 80f)
                ImGui.tableSetupColumn("Takeover", ImGuiTableColumnFlags.WidthFixed, 110f)
                ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, 60f)
                ImGui.tableHeadersRow()

                val takeoverValues = OscTakeoverMode.values()
                val takeoverNames = takeoverValues.map { it.name }.toTypedArray()

                for ((address, map) in mappings) {
                    ImGui.tableNextRow()

                    ImGui.tableNextColumn()
                    theme.body(address)
                    if (map.takeoverMode == OscTakeoverMode.SOFT_TAKEOVER) {
                        val isLocked = OscMappingManager.isSoftTakeoverActive(address)
                        if (!isLocked) {
                            theme.captionColored(1.0f, 0.65f, 0.2f, 1.0f, "${Icons.ALERT} Awaiting Pickup")
                        } else {
                            theme.captionColored(0.2f, 0.8f, 0.4f, 1.0f, "Takeover Synced")
                        }
                    }

                    ImGui.tableNextColumn()
                    theme.body(OscMappingManager.formatDisplayPath(map.parameterPath))
                    if (map.parameterPath.contains(":mod/")) {
                        itemTooltip("Full Path: ${map.parameterPath}")
                    }

                    ImGui.tableNextColumn()
                    val minArr = floatArrayOf(map.minVal)
                    val maxArr = floatArrayOf(map.maxVal)
                    ImGui.setNextItemWidth(55f)
                    val changedMin = ImGui.dragFloat("##osc_min_$address", minArr, 0.01f, -100f, 100f, "%.2f")
                    ImGui.sameLine()
                    ImGui.setNextItemWidth(55f)
                    val changedMax = ImGui.dragFloat("##osc_max_$address", maxArr, 0.01f, -100f, 100f, "%.2f")
                    if (changedMin || changedMax) {
                        OscMappingManager.updateMapping(address, map.copy(minVal = minArr[0], maxVal = maxArr[0]))
                        OscMappingManager.saveActiveProfile()
                    }

                    ImGui.tableNextColumn()
                    val inv = ImBoolean(map.inverted)
                    if (ImGui.checkbox("##osc_inv_$address", inv)) {
                        OscMappingManager.updateMapping(address, map.copy(inverted = inv.get()))
                        OscMappingManager.saveActiveProfile()
                    }

                    ImGui.tableNextColumn()
                    val slewArr = floatArrayOf(map.slewMs)
                    ImGui.setNextItemWidth(70f)
                    if (ImGui.dragFloat("##osc_slew_$address", slewArr, 1f, 0f, 250f, "%.0f ms")) {
                        OscMappingManager.updateMapping(address, map.copy(slewMs = slewArr[0]))
                        OscMappingManager.saveActiveProfile()
                    }

                    ImGui.tableNextColumn()
                    val toIdx = ImInt(takeoverValues.indexOf(map.takeoverMode).coerceAtLeast(0))
                    ImGui.setNextItemWidth(100f)
                    if (ImGui.combo("##osc_to_$address", toIdx, takeoverNames)) {
                        OscMappingManager.updateMapping(address, map.copy(takeoverMode = takeoverValues[toIdx.get()]))
                        OscMappingManager.saveActiveProfile()
                    }

                    ImGui.tableNextColumn()
                    if (ImGui.button("${Icons.TRASH}##osc_del_$address")) {
                        OscMappingManager.removeMapping(address)
                        OscMappingManager.saveActiveProfile()
                    }
                }
                ImGui.endTable()
            }
        }
    }
}
