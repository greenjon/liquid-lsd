package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.type.ImBoolean
import llm.slop.liquidlsd.SessionContext

object VideoDisplayPreferencesPanel {
    fun drawContent(session: SessionContext) {
        session.uiTheme.h2("Render Resolution")
        ImGui.sameLine(0f, 15f)
        session.uiTheme.caption("Internal render resolution for Decks, Mixer, and Video Output:")
        ImGui.separator()
        ImGui.spacing()

        val presets = UITheme.ResolutionPreset.values()
        val presetNames = presets.map { it.displayName }.toTypedArray()
        val currentPresetIdx = imgui.type.ImInt(session.uiTheme.renderResolutionPreset.ordinal)
        val renderPresetComboW = (ImGui.getContentRegionAvailX() * 0.4f).coerceAtLeast(160f)
        ImGui.setNextItemWidth(renderPresetComboW)
        if (ImGui.combo("##Resolution Preset", currentPresetIdx, presetNames, presetNames.size)) {
            val nextPreset = presets[currentPresetIdx.get()]
            session.uiTheme.renderResolutionPreset = nextPreset
            if (nextPreset != UITheme.ResolutionPreset.CUSTOM) {
                session.uiTheme.customRenderWidth = nextPreset.width
                session.uiTheme.customRenderHeight = nextPreset.height
            }
            session.uiTheme.savePreferences()
        }
        itemTooltip("Select internal target rendering resolution. Lower resolutions (e.g. 720p or 540p) significantly reduce GPU load on heavy raymarch shaders.")

        if (session.uiTheme.renderResolutionPreset == UITheme.ResolutionPreset.CUSTOM) {
            ImGui.spacing()
            val customW = imgui.type.ImInt(session.uiTheme.customRenderWidth)
            if (ImGui.inputInt("Custom Width", customW)) {
                session.uiTheme.customRenderWidth = customW.get().coerceIn(128, 7680)
                session.uiTheme.savePreferences()
            }
            val customH = imgui.type.ImInt(session.uiTheme.customRenderHeight)
            if (ImGui.inputInt("Custom Height", customH)) {
                session.uiTheme.customRenderHeight = customH.get().coerceIn(128, 4320)
                session.uiTheme.savePreferences()
            }
        }

        ImGui.spacing()
        session.uiTheme.h2("Display Scaling")
        ImGui.separator()
        ImGui.spacing()

        val scaleModes = UITheme.OutputScaleMode.values()
        val scaleModeNames = scaleModes.map { it.displayName }.toTypedArray()
        val currentScaleIdx = imgui.type.ImInt(session.uiTheme.outputScaleMode.ordinal)
        val displayScaleComboW = (ImGui.getContentRegionAvailX() * 0.4f).coerceAtLeast(160f)
        ImGui.setNextItemWidth(displayScaleComboW)
        if (ImGui.combo("##Output Scaling", currentScaleIdx, scaleModeNames)) {
            session.uiTheme.outputScaleMode = scaleModes[currentScaleIdx.get()]
            session.uiTheme.savePreferences()
        }
        itemTooltip("How output is scaled when target screen aspect ratio differs from render resolution: Fit (Letterbox/Pillarbox), Fill (Crop), or Stretch.")

        ImGui.spacing()
        session.uiTheme.h2("Performance & Background")
        ImGui.separator()
        ImGui.spacing()

        val bgVideoEnabled = ImBoolean(session.uiTheme.backgroundVideoEnabled)
        if (ImGui.checkbox("Background Video", bgVideoEnabled)) {
            session.uiTheme.backgroundVideoEnabled = bgVideoEnabled.get()
            session.uiTheme.savePreferences()
        }
        itemTooltip("Render master output video behind the semi-transparent interface (Hotkey: B).")

        val fpsCapVal = ImBoolean(session.uiTheme.maxFps <= 30)
        if (ImGui.checkbox("Cap UI to 30 FPS", fpsCapVal)) {
            session.uiTheme.maxFps = if (fpsCapVal.get()) 30 else 60
            session.uiTheme.savePreferences()
        }
        itemTooltip("Limit frame rate to 30 FPS to conserve power.")

        ImGui.spacing()
        session.uiTheme.h2("Live Video Sharing (Spout / Syphon / PipeWire)")
        ImGui.separator()
        ImGui.spacing()

        val backendName = llm.slop.liquidlsd.rendering.TextureStreamerManager.getBackendName()
        ImGui.textWrapped("Zero-copy GPU texture sharing to Resolume, OBS, and MadMapper. Active driver: $backendName")
        ImGui.spacing()

        val tableFlags = ImGuiTableFlags.BordersOuter or ImGuiTableFlags.RowBg or ImGuiTableFlags.Resizable
        if (ImGui.beginTable("##video_sharing_matrix", 6, tableFlags)) {
            ImGui.tableSetupColumn("Endpoint", ImGuiTableColumnFlags.WidthFixed, 100f)
            ImGui.tableSetupColumn("Enable",   ImGuiTableColumnFlags.WidthFixed, 50f)
            ImGui.tableSetupColumn("Name",     ImGuiTableColumnFlags.WidthStretch, 1f)
            ImGui.tableSetupColumn("Res",      ImGuiTableColumnFlags.WidthFixed, 120f)
            ImGui.tableSetupColumn("Scale",    ImGuiTableColumnFlags.WidthFixed, 80f)
            ImGui.tableSetupColumn("Status",   ImGuiTableColumnFlags.WidthFixed, 60f)
            ImGui.tableHeadersRow()

            llm.slop.liquidlsd.rendering.VideoOutputEndpoint.values().forEach { endpoint ->
                val config = session.uiTheme.videoOutputConfigs[endpoint] ?: llm.slop.liquidlsd.rendering.VideoOutputConfig()
                
                ImGui.tableNextRow()
                ImGui.tableNextColumn()
                session.uiTheme.body(endpoint.displayName)
                
                ImGui.tableNextColumn()
                val enabled = ImBoolean(config.isEnabled)
                if (ImGui.checkbox("##enable_${endpoint.name}", enabled)) {
                    session.uiTheme.updateVideoOutputConfig(endpoint, config.copy(isEnabled = enabled.get()))
                    session.uiTheme.savePreferences()
                }
                
                ImGui.tableNextColumn()
                val nameInput = imgui.type.ImString(config.customName, 64)
                ImGui.setNextItemWidth(-1f)
                if (ImGui.inputText("##name_${endpoint.name}", nameInput)) {
                    session.uiTheme.updateVideoOutputConfig(endpoint, config.copy(customName = nameInput.get()))
                    session.uiTheme.savePreferences()
                }
                
                ImGui.tableNextColumn()
                val resModes = llm.slop.liquidlsd.rendering.OutputResolutionMode.values()
                val resModeNames = resModes.map { it.displayName }.toTypedArray()
                val currentResIdx = imgui.type.ImInt(config.resolutionMode.ordinal)
                ImGui.setNextItemWidth(-1f)
                if (ImGui.combo("##res_${endpoint.name}", currentResIdx, resModeNames)) {
                    session.uiTheme.updateVideoOutputConfig(endpoint, config.copy(resolutionMode = resModes[currentResIdx.get()]))
                    session.uiTheme.savePreferences()
                }
                if (config.resolutionMode != llm.slop.liquidlsd.rendering.OutputResolutionMode.SYNC_MASTER && config.resolutionMode != llm.slop.liquidlsd.rendering.OutputResolutionMode.RES_540P) {
                    itemTooltip("${Icons.ALERT} High resolution outputs significantly impact GPU performance!")
                }

                ImGui.tableNextColumn()
                val scaleModes = UITheme.OutputScaleMode.values()
                val scaleModeNames = scaleModes.map { it.displayName.split(" ")[0] }.toTypedArray()
                val currentScaleIdx = imgui.type.ImInt(config.scalingMode.ordinal)
                ImGui.setNextItemWidth(-1f)
                if (ImGui.combo("##scale_${endpoint.name}", currentScaleIdx, scaleModeNames)) {
                    session.uiTheme.updateVideoOutputConfig(endpoint, config.copy(scalingMode = scaleModes[currentScaleIdx.get()]))
                    session.uiTheme.savePreferences()
                }

                ImGui.tableNextColumn()
                if (config.isEnabled) {
                    ImGui.textColored(0.2f, 0.9f, 0.3f, 1f, "LIVE")
                    val streamer = llm.slop.liquidlsd.rendering.TextureStreamerManager.getStreamer(endpoint)
                    val statusText = if (streamer is llm.slop.liquidlsd.rendering.LinuxTextureBridge) streamer.getDriverStatus() else "LIVE"
                    itemTooltip("Active Backend: $backendName\nStatus: $statusText")
                } else {
                    ImGui.textDisabled("OFF")
                }
            }
            ImGui.endTable()
        }

        ImGui.spacing()
        session.uiTheme.h2("Live Video Recording")
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.body("Recording Output Directory:")
        val defaultDir = session.uiTheme.getDefaultVideosDirectory().absolutePath
        val currentRecDir = if (session.uiTheme.recordingDirectory.isNotBlank()) session.uiTheme.recordingDirectory else defaultDir
        val dirInput = imgui.type.ImString(currentRecDir, 512)
        if (ImGui.inputText("##RecDir", dirInput)) {
            session.uiTheme.recordingDirectory = dirInput.get().trim()
            session.uiTheme.savePreferences()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset to Default##RecDir")) {
            session.uiTheme.recordingDirectory = ""
            session.uiTheme.savePreferences()
        }
        itemTooltip("Reset recording output folder to standard system Videos directory: $defaultDir")

        val recAudioVal = ImBoolean(session.uiTheme.recordingIncludeAudio)
        if (ImGui.checkbox("Record with Audio Muxing", recAudioVal)) {
            session.uiTheme.recordingIncludeAudio = recAudioVal.get()
            session.uiTheme.savePreferences()
        }
        itemTooltip("When enabled, live recordings capture audio from AudioEngine and multiplex it into the video output container.")

        val sliderBoxW = 50f
        CustomRangeSlider.drawCompactSlider(
            session = session,
            label = "Video Bitrate",
            currentValue = session.uiTheme.recordingBitrateMbps.toFloat(),
            minLimit = 4f,
            maxLimit = 50f,
            defaultValue = 16f,
            formatValue = { "${it.toInt()} Mbps" },
            idPrefix = "preferences_video_bitrate",
            themeColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f),
            showCurrentLabel = false,
            customBoxWidth = sliderBoxW,
            onValueChanged = { newVal ->
                session.uiTheme.recordingBitrateMbps = newVal.toInt()
                session.uiTheme.savePreferences()
            }
        )

        ImGui.spacing()
        session.uiTheme.body("Recording Framerate:")
        ImGui.sameLine(0f, 15f)
        val fpsOptions = arrayOf("30 FPS", "60 FPS")
        val fpsIdx = imgui.type.ImInt(if (session.uiTheme.recordingFps <= 30) 0 else 1)
        val recordingFpsComboW = (ImGui.getContentRegionAvailX() * 0.6f).coerceAtLeast(100f)
        ImGui.setNextItemWidth(recordingFpsComboW)
        if (ImGui.combo("##Recording Framerate", fpsIdx, fpsOptions)) {
            session.uiTheme.recordingFps = if (fpsIdx.get() == 0) 30 else 60
            session.uiTheme.savePreferences()
        }
    }
}