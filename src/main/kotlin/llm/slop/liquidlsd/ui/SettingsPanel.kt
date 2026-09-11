package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString

import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTableColumnFlags
import llm.slop.liquidlsd.input.TouchBackendState

/**
 * Modal settings overlay with a left vertical navigation bar.
 * Call [open] when the menu item is clicked.
 * Call [draw] once per frame inside the active ImGui frame.
 */
object SettingsPanel {

    private const val POPUP_ID  = "Settings##modal"
    private const val MODAL_W   = 1000f

    // Library preset name scale model: Range 80%–120% in 10% steps.
    private const val MIN_PRESET_SCALE_PCT = 80
    private const val MAX_PRESET_SCALE_PCT = 120
    private const val STEP_PCT             = 10

    enum class Category(val label: String) {
        GENERAL("General"),
        VIDEO_DISPLAY("Video & Display"),
        TEMPO_SYNC("Tempo & Sync"),
        AUDIO_ENGINE("Audio Hardware"),
        SHADER_LOCATIONS("Shader Locations"),
        BROADCAST("Web Broadcast"),
        SHORTCUTS("Keyboard Shortcuts")
    }

    var isOpen: Boolean = false
        private set

    var activeCategory = Category.GENERAL
        private set

    private var pendingPresetScale: Int? = null

    fun open(category: Category? = null) {
        isOpen = true
        pendingPresetScale = null
        if (category != null) {
            activeCategory = category
        }
        ImGui.openPopup(POPUP_ID)
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext, currentSize: Float = session.uiTheme.baseSize, displayW: Float, displayH: Float,
             mixer: llm.slop.liquidlsd.rendering.Mixer? = null,
             onPresetScaleChanged: (Int) -> Unit) {

        val minW = 1000f.coerceAtMost(displayW * 0.98f)
        val minH = 320f.coerceAtMost(displayH * 0.98f)

        val defaultW = MODAL_W.coerceIn(minW, displayW * 0.98f)
        val defaultH = 520f.coerceIn(minH, displayH * 0.90f)

        val targetW = if (session.uiTheme.settingsWidth > 100f) session.uiTheme.settingsWidth.coerceIn(minW, displayW * 0.98f) else defaultW
        val targetH = if (session.uiTheme.settingsHeight > 100f) session.uiTheme.settingsHeight.coerceIn(minH, displayH * 0.98f) else defaultH

        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            ImGuiCond.Appearing, 0.5f, 0.5f
        )
        ImGui.setNextWindowSize(targetW, targetH, ImGuiCond.Appearing)
        ImGui.setNextWindowSizeConstraints(minW, minH, displayW * 0.98f, displayH * 0.98f)

        val flags = ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoScrollbar

        if (!ImGui.beginPopupModal(POPUP_ID, flags)) {
            isOpen = false
            return
        }
        isOpen = true

        val currentWinW = ImGui.getWindowWidth()
        val currentWinH = ImGui.getWindowHeight()
        if (kotlin.math.abs(currentWinW - session.uiTheme.settingsWidth) > 1f ||
            kotlin.math.abs(currentWinH - session.uiTheme.settingsHeight) > 1f) {
            session.uiTheme.settingsWidth = currentWinW
            session.uiTheme.settingsHeight = currentWinH
            session.uiTheme.saveSettings()
        }

        val sidebarW = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            Category.values().maxOf { ImGui.calcTextSize(it.label).x } + 36f
        }.coerceAtLeast(140f)

        val btnH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            ImGui.getFrameHeight() + 6f
        }.coerceAtLeast(30f)

        val footerH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            ImGui.getFrameHeightWithSpacing() + ImGui.getStyle().itemSpacing.y * 2f + 14f
        }

        val availW = ImGui.getContentRegionAvailX()
        val availH = ImGui.getContentRegionAvailY()
        val contentH = (availH - footerH).coerceAtLeast(180f)
        val rightContentW = (availW - sidebarW - ImGui.getStyle().itemSpacing.x).coerceAtLeast(50f)

        // Left Sidebar Child
        if (ImGui.beginChild("##settings_sidebar", sidebarW, contentH, true)) {
            Category.values().forEach { cat ->
                val selected = activeCategory == cat
                if (selected) {
                    val activeCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.8f, 1f)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        activeCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, activeCol)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  activeCol)
                } else {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.22f, 0.22f, 0.22f, 1f))
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.32f, 0.32f, 0.32f, 1f))
                }

                if (ImGui.button(cat.label, sidebarW - 16f, btnH)) {
                    activeCategory = cat
                }
                ImGui.popStyleColor(3)
                ImGui.spacing()
            }
        }
        ImGui.endChild()

        ImGui.sameLine()

        // Right Content Child
        if (ImGui.beginChild("##settings_content", rightContentW, contentH, true)) {
            when (activeCategory) {
                Category.GENERAL          -> drawGeneralSettings(session, currentSize, onPresetScaleChanged)
                Category.VIDEO_DISPLAY    -> drawVideoDisplaySettings(session)
                Category.TEMPO_SYNC       -> drawTempoSyncSettings(session)
                Category.AUDIO_ENGINE     -> drawAudioEngineSettings(session)
                Category.SHADER_LOCATIONS -> drawShaderLocationsSettings(session)
                Category.BROADCAST        -> drawBroadcastSettings(session, mixer)
                Category.SHORTCUTS        -> drawShortcutsSettings(session)
            }
        }
        ImGui.endChild()

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // Centred Close button
        val closeW = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            ImGui.calcTextSize("  Close  ").x + 40f
        }.coerceAtLeast(110f)
        ImGui.setCursorPosX(ImGui.getWindowContentRegionMinX() + (availW - closeW) * 0.5f)
        if (ImGui.button("Close", closeW, 0f)) {
            isOpen = false
            ImGui.closeCurrentPopup()
        }

        ImGui.endPopup()
    }



    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun drawVideoDisplaySettings(session: llm.slop.liquidlsd.SessionContext) {
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
            session.uiTheme.saveSettings()
        }
        itemTooltip("Select internal target rendering resolution. Lower resolutions (e.g. 720p or 540p) significantly reduce GPU load on heavy raymarch shaders.")

        if (session.uiTheme.renderResolutionPreset == UITheme.ResolutionPreset.CUSTOM) {
            ImGui.spacing()
            val customW = imgui.type.ImInt(session.uiTheme.customRenderWidth)
            if (ImGui.inputInt("Custom Width", customW)) {
                session.uiTheme.customRenderWidth = customW.get().coerceIn(128, 7680)
                session.uiTheme.saveSettings()
            }
            val customH = imgui.type.ImInt(session.uiTheme.customRenderHeight)
            if (ImGui.inputInt("Custom Height", customH)) {
                session.uiTheme.customRenderHeight = customH.get().coerceIn(128, 4320)
                session.uiTheme.saveSettings()
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
            session.uiTheme.saveSettings()
        }
        itemTooltip("How output is scaled when target screen aspect ratio differs from render resolution: Fit (Letterbox/Pillarbox), Fill (Crop), or Stretch.")

        ImGui.spacing()
        session.uiTheme.h2("Performance & Background")
        ImGui.separator()
        ImGui.spacing()

        val bgVideoEnabled = ImBoolean(session.uiTheme.backgroundVideoEnabled)
        if (ImGui.checkbox("Background Video", bgVideoEnabled)) {
            session.uiTheme.backgroundVideoEnabled = bgVideoEnabled.get()
            session.uiTheme.saveSettings()
        }
        itemTooltip("Render master output video behind the semi-transparent interface (Hotkey: B).")

        val fpsCapVal = ImBoolean(session.uiTheme.maxFps <= 30)
        if (ImGui.checkbox("Cap UI to 30 FPS", fpsCapVal)) {
            session.uiTheme.maxFps = if (fpsCapVal.get()) 30 else 60
            session.uiTheme.saveSettings()
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
                    session.uiTheme.saveSettings()
                }
                
                ImGui.tableNextColumn()
                val nameInput = imgui.type.ImString(config.customName, 64)
                ImGui.setNextItemWidth(-1f)
                if (ImGui.inputText("##name_${endpoint.name}", nameInput)) {
                    session.uiTheme.updateVideoOutputConfig(endpoint, config.copy(customName = nameInput.get()))
                    session.uiTheme.saveSettings()
                }
                
                ImGui.tableNextColumn()
                val resModes = llm.slop.liquidlsd.rendering.OutputResolutionMode.values()
                val resModeNames = resModes.map { it.displayName }.toTypedArray()
                val currentResIdx = imgui.type.ImInt(config.resolutionMode.ordinal)
                ImGui.setNextItemWidth(-1f)
                if (ImGui.combo("##res_${endpoint.name}", currentResIdx, resModeNames)) {
                    session.uiTheme.updateVideoOutputConfig(endpoint, config.copy(resolutionMode = resModes[currentResIdx.get()]))
                    session.uiTheme.saveSettings()
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
                    session.uiTheme.saveSettings()
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
            session.uiTheme.saveSettings()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset to Default##RecDir")) {
            session.uiTheme.recordingDirectory = ""
            session.uiTheme.saveSettings()
        }
        itemTooltip("Reset recording output folder to standard system Videos directory: $defaultDir")

        val recAudioVal = ImBoolean(session.uiTheme.recordingIncludeAudio)
        if (ImGui.checkbox("Record with Audio Muxing", recAudioVal)) {
            session.uiTheme.recordingIncludeAudio = recAudioVal.get()
            session.uiTheme.saveSettings()
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
            idPrefix = "settings_video_bitrate",
            themeColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f),
            showCurrentLabel = false,
            customBoxWidth = sliderBoxW,
            onValueChanged = { newVal ->
                session.uiTheme.recordingBitrateMbps = newVal.toInt()
                session.uiTheme.saveSettings()
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
            session.uiTheme.saveSettings()
        }
    }

    private fun drawTempoSyncSettings(session: llm.slop.liquidlsd.SessionContext) {
        TempoSyncPanel.drawContent(session)
    }

    private fun drawAudioEngineSettings(session: llm.slop.liquidlsd.SessionContext) {
        AudioEnginePanel.drawContent(session)
    }

    private fun drawGeneralSettings(session: llm.slop.liquidlsd.SessionContext, currentSize: Float, onPresetScaleChanged: (Int) -> Unit) {
        session.uiTheme.h2("Features")
        ImGui.separator()
        ImGui.spacing()

        val randEnabled = ImBoolean(session.uiTheme.randomizationEnabled)
        if (ImGui.checkbox("Enable Parameter Randomization", randEnabled)) {
            val nextVal = randEnabled.get()
            if (nextVal != session.uiTheme.randomizationEnabled) {
                session.uiTheme.randomizationEnabled = nextVal
                session.uiTheme.saveSettings()
            }
        }
        itemTooltip("Toggle parameter and modulator randomization controls.")

        val seqEnabled = ImBoolean(session.uiTheme.sequencerEnabled)
        if (ImGui.checkbox("Enable Step Sequencer", seqEnabled)) {
            val nextVal = seqEnabled.get()
            if (nextVal != session.uiTheme.sequencerEnabled) {
                session.uiTheme.sequencerEnabled = nextVal
                session.uiTheme.saveSettings()
            }
        }
        itemTooltip("Enable or disable the step sequencer modulation engine across presets and cell configuration.")

        val midiEnabled = ImBoolean(session.uiTheme.midiEnabled)
        if (ImGui.checkbox("Enable MIDI", midiEnabled)) {
            val nextVal = midiEnabled.get()
            if (nextVal != session.uiTheme.midiEnabled) {
                session.uiTheme.midiEnabled = nextVal
                session.uiTheme.saveSettings()
                if (nextVal) {
                    llm.slop.liquidlsd.midi.MidiEngine.scanForNewDevices()
                } else {
                    llm.slop.liquidlsd.midi.MidiEngine.close()
                }
            }
        }
        itemTooltip("Toggle MIDI controller input and CC mapping.")
        ImGui.sameLine(0f, 20f)
        val midiCount = llm.slop.liquidlsd.midi.MidiEngine.getActiveDeviceCount()
        if (midiCount == 0) {
            session.uiTheme.captionColored(0.9f, 0.6f, 0.2f, 1.0f, "MIDI: No hardware controllers detected.")
        } else {
            session.uiTheme.captionColored(0.2f, 0.9f, 0.4f, 1.0f, "MIDI: $midiCount active MIDI controller(s) connected.")
        }

        if (session.uiTheme.midiEnabled) {
            val midiDir = java.io.File("library/midi")
            val profileFiles = (midiDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray())
                .map { it.nameWithoutExtension }
                .toMutableList()
            if (profileFiles.isEmpty()) profileFiles.add("default")
            if (!profileFiles.contains(session.uiTheme.activeMidiProfile)) {
                profileFiles.add(session.uiTheme.activeMidiProfile)
            }

            val currentProfileIdx = imgui.type.ImInt(profileFiles.indexOf(session.uiTheme.activeMidiProfile).coerceAtLeast(0))
            val profileNamesArray = profileFiles.toTypedArray()
            if (ImGui.combo("MIDI Profile", currentProfileIdx, profileNamesArray)) {
                val nextProfile = profileNamesArray[currentProfileIdx.get()]
                session.midiMappingManager.loadProfile(nextProfile)
                session.uiTheme.activeMidiProfile = nextProfile
                session.uiTheme.saveSettings()
            }
            itemTooltip("Select active MIDI controller CC assignment profile.")

            ImGui.spacing()
            val nextCc = imgui.type.ImInt(session.midiMappingManager.getCcForSpecial("Global/queueNext"))
            if (ImGui.inputInt("A/B Next CC", nextCc)) {
                val newVal = nextCc.get().coerceIn(-1, 127)
                session.midiMappingManager.addMapping("Global/queueNext", newVal)
                session.midiMappingManager.saveActiveProfile()
            }

            val prevCc = imgui.type.ImInt(session.midiMappingManager.getCcForSpecial("Global/queuePrev"))
            if (ImGui.inputInt("A/B Prev CC", prevCc)) {
                val newVal = prevCc.get().coerceIn(-1, 127)
                session.midiMappingManager.addMapping("Global/queuePrev", newVal)
                session.midiMappingManager.saveActiveProfile()
            }

            val bgNextCc = imgui.type.ImInt(session.midiMappingManager.getCcForSpecial("Global/bgQueueNext"))
            if (ImGui.inputInt("BG Next CC", bgNextCc)) {
                val newVal = bgNextCc.get().coerceIn(-1, 127)
                session.midiMappingManager.addMapping("Global/bgQueueNext", newVal)
                session.midiMappingManager.saveActiveProfile()
            }

            val bgPrevCc = imgui.type.ImInt(session.midiMappingManager.getCcForSpecial("Global/bgQueuePrev"))
            if (ImGui.inputInt("BG Prev CC", bgPrevCc)) {
                val newVal = bgPrevCc.get().coerceIn(-1, 127)
                session.midiMappingManager.addMapping("Global/bgQueuePrev", newVal)
                session.midiMappingManager.saveActiveProfile()
            }

            val tapCc = imgui.type.ImInt(session.midiMappingManager.getCcForSpecial("Global/tapTempo"))
            if (ImGui.inputInt("Tap Tempo CC", tapCc)) {
                val newVal = tapCc.get().coerceIn(-1, 127)
                session.midiMappingManager.addMapping("Global/tapTempo", newVal)
                session.midiMappingManager.saveActiveProfile()
            }
            itemTooltip("MIDI CC assigned to tap tempo. Set to -1 to unmap.")
        }

        val framelessEnabled = ImBoolean(session.uiTheme.framelessWindow)
        if (ImGui.checkbox("Frameless Window (Custom Title Bar) [Requires restart]", framelessEnabled)) {
            val nextVal = framelessEnabled.get()
            if (nextVal != session.uiTheme.framelessWindow) {
                session.uiTheme.framelessWindow = nextVal
                session.uiTheme.saveSettings()
            }
        }
        itemTooltip("Removes OS window borders to integrate navigation, telemetry, and window controls into a unified top bar.\nDisable if using a tiling window manager (e.g. i3/sway) that manages decorations natively.")

        val trackpadEnabled = ImBoolean(session.uiTheme.trackpadConsoleEnabled)
        if (ImGui.checkbox("Enable CapsLock Trackpad Console", trackpadEnabled)) {
            val nextVal = trackpadEnabled.get()
            if (nextVal != session.uiTheme.trackpadConsoleEnabled) {
                session.uiTheme.trackpadConsoleEnabled = nextVal
                session.uiTheme.saveSettings()
                if (!nextVal && session.touchConsoleController.isActive) {
                    session.touchConsoleController.toggleActive(false)
                }
            }
        }
        itemTooltip("Transforms the laptop trackpad into an SCS.3m virtual console when CapsLock is engaged.\nBottom 28%: Crossfader cut/stutter; Top 55%: Deck A/BG/B Alpha faders.\nDisables cursor movement and gestures while active.")

        ImGui.sameLine(0f, 15f)
        val controller = session.touchConsoleController
        val state = controller.backend.state
        if (controller.isElevatingPermissions) {
            ImGui.textDisabled("${Icons.ACTIVITY} Touchpad Status: Configuring permissions via Polkit...")
        } else when (state) {
            TouchBackendState.READY -> {
                ImGui.textColored(0.2f, 0.9f, 0.3f, 1f, "${Icons.ACTIVITY} Touchpad Status: Ready (Press CapsLock to engage)")
            }
            TouchBackendState.PERMISSION_REQUIRED -> {
                ImGui.textColored(1.0f, 0.6f, 0.1f, 1f, "${Icons.ALERT} Touchpad Status: Read/Write Permission Required")
                ImGui.sameLine()
                if (ImGui.button("Install Permissions (Polkit)")) {
                    controller.requestPermissionElevation()
                }
                itemTooltip("Runs pkexec to add a uaccess udev rule for your seat user without rebooting.")
            }
            TouchBackendState.NO_DEVICE -> {
                ImGui.textDisabled("Touchpad Status: No hardware trackpad detected")
            }
            TouchBackendState.DISABLED -> {
                ImGui.textDisabled("Touchpad Status: Disabled on this platform")
            }
        }

        ImGui.spacing()
        val updatesOnStartup = ImBoolean(session.uiTheme.checkUpdatesOnStartup)
        if (ImGui.checkbox("Automatically check for updates on launch", updatesOnStartup)) {
            val nextVal = updatesOnStartup.get()
            if (nextVal != session.uiTheme.checkUpdatesOnStartup) {
                session.uiTheme.checkUpdatesOnStartup = nextVal
                session.uiTheme.saveSettings()
            }
        }
        itemTooltip("Checks GitHub for new releases when Liquid LSD starts up.")

        ImGui.sameLine(0f, 15f)
        val checking = llm.slop.liquidlsd.update.UpdateChecker.isChecking
        val checkBtnLabel = if (checking) "${Icons.REFRESH} Checking..." else "${Icons.REFRESH} Check for Updates Now##settings_check_now"
        if (ImGui.button(checkBtnLabel, 180f, 0f)) {
            if (!checking) {
                llm.slop.liquidlsd.update.UpdateChecker.checkForUpdatesAsync(isManualCheck = true)
            }
        }
        itemTooltip("Query GitHub for the latest release version.")

        val lastResult = llm.slop.liquidlsd.update.UpdateChecker.lastResult
        when (lastResult) {
            is llm.slop.liquidlsd.update.UpdateCheckResult.UpdateAvailable -> {
                ImGui.spacing()
                ImGui.alignTextToFramePadding()
                ImGui.textColored(0.3f, 0.9f, 0.4f, 1.0f, "${Icons.DOWNLOAD} Update available: ${lastResult.latestRelease.tagName}")
                ImGui.sameLine()
                if (ImGui.button("View Update##settings_update", 120f, 0f)) {
                    UpdatePromptModal.request(lastResult.latestRelease, lastResult.currentVersion)
                }
            }
            is llm.slop.liquidlsd.update.UpdateCheckResult.UpToDate -> {
                ImGui.spacing()
                ImGui.alignTextToFramePadding()
                ImGui.textColored(0.5f, 0.9f, 0.5f, 1.0f, "Liquid LSD is up to date (${lastResult.currentVersion}).")
            }
            is llm.slop.liquidlsd.update.UpdateCheckResult.Error -> {
                ImGui.spacing()
                ImGui.alignTextToFramePadding()
                ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, "Check failed: ${lastResult.message}")
            }
            is llm.slop.liquidlsd.update.UpdateCheckResult.Idle, is llm.slop.liquidlsd.update.UpdateCheckResult.Checking -> {
                // Handled inline via the button / checking state above
            }
        }

        ImGui.spacing()
        session.uiTheme.h2("Startup Behavior")
        ImGui.separator()
        ImGui.spacing()

        val startupBehaviors = UITheme.StartupBehavior.values()
        val startupOptions = arrayOf("Restore Previous Session", "Start Empty")
        val currentStartupIdx = imgui.type.ImInt(session.uiTheme.startupBehavior.ordinal)
        val comboWidth = (ImGui.getContentRegionAvailX() * 0.33f).coerceAtLeast(160f)
        ImGui.setNextItemWidth(comboWidth)
        if (ImGui.combo("Startup Behavior", currentStartupIdx, startupOptions)) {
            session.uiTheme.startupBehavior = startupBehaviors[currentStartupIdx.get()]
            session.uiTheme.saveSettings()
        }

        ImGui.spacing()
        val autoVjBehaviors = UITheme.AutoVjDirtyBehavior.values()
        val autoVjBehaviorNames = autoVjBehaviors.map { it.name }.toTypedArray()
        val currentAutoVjIdx = imgui.type.ImInt(session.uiTheme.autoVjDirtyBehavior.ordinal)
        ImGui.setNextItemWidth(comboWidth)
        if (ImGui.combo("AutoVJ Dirty Behavior", currentAutoVjIdx, autoVjBehaviorNames)) {
            session.uiTheme.autoVjDirtyBehavior = autoVjBehaviors[currentAutoVjIdx.get()]
            session.uiTheme.saveSettings()
        }

        ImGui.spacing()
        session.uiTheme.h2("Theme")
        ImGui.separator()
        ImGui.spacing()

        val themes = UITheme.Theme.values()
        val themeNames = themes.map { theme ->
            theme.name.split("_")
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { it.uppercaseChar() }
                }
        }.toTypedArray()
        val currentThemeIdx = imgui.type.ImInt(session.uiTheme.theme.ordinal)
        val themeComboW = (ImGui.getContentRegionAvailX() * 0.33f * 0.5f).coerceAtLeast(80f)
        ImGui.setNextItemWidth(themeComboW)
        if (ImGui.combo("##ui_theme", currentThemeIdx, themeNames)) {
            val nextTheme = themes[currentThemeIdx.get()]
            session.uiTheme.theme = nextTheme
            session.uiTheme.saveSettings()
        }
        itemTooltip("Select the user interface color palette theme.")
        ImGui.spacing()

        session.uiTheme.h2("Font Size")
        ImGui.sameLine(0f, 15f)
        session.uiTheme.captionColored(0.7f, 0.7f, 0.7f, 0.85f, "only changes the size of the text in the Library of Presets")
        ImGui.separator()
        ImGui.spacing()

        // Library Preset Name Scale: 80% to 120%
        val sliderBoxW = 50f
        val committedScale = session.uiTheme.presetNameScalePercent
        val currentScale = pendingPresetScale ?: committedScale
        val sliderW = (ImGui.getContentRegionAvailX() * 0.25f).coerceAtLeast(160f)
        if (ImGui.beginChild("##preset_slider_child", sliderW, 46f, false)) {
            CustomRangeSlider.drawCompactSlider(
                session = session,
                label = "",
                currentValue = currentScale.toFloat(),
                minLimit = MIN_PRESET_SCALE_PCT.toFloat(),
                maxLimit = MAX_PRESET_SCALE_PCT.toFloat(),
                defaultValue = 100f,
                formatValue = {
                    val snapped = (kotlin.math.round(it / STEP_PCT) * STEP_PCT).toInt().coerceIn(MIN_PRESET_SCALE_PCT, MAX_PRESET_SCALE_PCT)
                    "$snapped%"
                },
                idPrefix = "settings_preset_name_scale",
                themeColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f),
                showCurrentLabel = false,
                customBoxWidth = sliderBoxW,
                readOnly = true,
                onValueChanged = { newVal ->
                    val snapped = (kotlin.math.round(newVal / STEP_PCT) * STEP_PCT).toInt().coerceIn(MIN_PRESET_SCALE_PCT, MAX_PRESET_SCALE_PCT)
                    pendingPresetScale = snapped
                }
            )
        }
        ImGui.endChild()
        itemTooltip(
            "Scale preset names in the Library browser ($MIN_PRESET_SCALE_PCT% to $MAX_PRESET_SCALE_PCT%).\n" +
            "Drag smoothly and release mouse to apply.\n" +
            "Ctrl+- and Ctrl+= adjust by 10% steps."
        )
        ImGui.spacing()

        // Commit on mouse release
        if (!ImGui.isMouseDown(0) && pendingPresetScale != null) {
            val target = pendingPresetScale!!
            pendingPresetScale = null
            if (target != committedScale) {
                onPresetScaleChanged(target)
            }
        }
    }

    private val serverUrlBuf = imgui.type.ImString(llm.slop.liquidlsd.broadcast.BroadcastSettings.serverUrl, 256)
    private val tokenBuf = imgui.type.ImString(llm.slop.liquidlsd.broadcast.BroadcastSettings.token, 128)
    private var showToken = false

    private fun drawBroadcastSettings(session: llm.slop.liquidlsd.SessionContext, mixer: llm.slop.liquidlsd.rendering.Mixer?) {
        val theme = session.uiTheme
        theme.h2("Web Broadcast Relay")
        ImGui.separator()
        ImGui.spacing()

        ImGui.textWrapped("Broadcasts real-time preset state, mixer balance, and parameter modulations to the Liquid LSD Web TV client.")
        ImGui.spacing()

        // Server URL
        theme.caption("RELAY SERVER URL")
        if (serverUrlBuf.get() != llm.slop.liquidlsd.broadcast.BroadcastSettings.serverUrl) {
            serverUrlBuf.set(llm.slop.liquidlsd.broadcast.BroadcastSettings.serverUrl)
        }
        if (ImGui.inputText("##broadcast_url", serverUrlBuf)) {
            llm.slop.liquidlsd.broadcast.BroadcastSettings.serverUrl = serverUrlBuf.get().trim()
            llm.slop.liquidlsd.broadcast.BroadcastSettings.saveSettings()
        }
        itemTooltip("WebSocket relay URL (e.g. ws://127.0.0.1:9000 or wss://spaz.org/lsd-relay)")

        ImGui.spacing()

        // Secret Token
        theme.caption("BROADCASTER SECRET TOKEN")
        if (tokenBuf.get() != llm.slop.liquidlsd.broadcast.BroadcastSettings.token) {
            tokenBuf.set(llm.slop.liquidlsd.broadcast.BroadcastSettings.token)
        }
        val tokenFlags = if (showToken) 0 else imgui.flag.ImGuiInputTextFlags.Password
        if (ImGui.inputText("##broadcast_token", tokenBuf, tokenFlags)) {
            llm.slop.liquidlsd.broadcast.BroadcastSettings.token = tokenBuf.get().trim()
            llm.slop.liquidlsd.broadcast.BroadcastSettings.saveSettings()
        }
        ImGui.sameLine()
        val eyeLabel = if (showToken) "Hide" else "Show"
        if (ImGui.button(eyeLabel)) {
            showToken = !showToken
        }

        ImGui.spacing()

        // Target Update Rate
        val sliderBoxW = 50f
        CustomRangeSlider.drawCompactSlider(
            session = session,
            label = "Rate Limit",
            currentValue = llm.slop.liquidlsd.broadcast.BroadcastSettings.targetFps.toFloat(),
            minLimit = 5f,
            maxLimit = 60f,
            defaultValue = 30f,
            formatValue = { "${it.toInt()} Hz" },
            idPrefix = "settings_broadcast_target_fps",
            themeColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f),
            showCurrentLabel = false,
            customBoxWidth = sliderBoxW,
            onValueChanged = { newVal ->
                llm.slop.liquidlsd.broadcast.BroadcastSettings.targetFps = newVal.toInt()
                llm.slop.liquidlsd.broadcast.BroadcastSettings.saveSettings()
            }
        )
        itemTooltip("Maximum rate to dispatch parameter delta packets to the relay.")

        ImGui.spacing()

        // Auto-connect checkbox
        val autoConn = ImBoolean(llm.slop.liquidlsd.broadcast.BroadcastSettings.autoConnect)
        if (ImGui.checkbox("Auto-connect on launch", autoConn)) {
            llm.slop.liquidlsd.broadcast.BroadcastSettings.autoConnect = autoConn.get()
            llm.slop.liquidlsd.broadcast.BroadcastSettings.saveSettings()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // Connection Status HUD
        theme.h3("Connection Status")
        val state = llm.slop.liquidlsd.broadcast.BroadcastEngine.connectionState
        val isLive = llm.slop.liquidlsd.broadcast.BroadcastEngine.isLive

        when (state) {
            llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTED -> {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.2f, 0.9f, 0.2f, 1f)
                ImGui.text("${Icons.ACTIVITY} CONNECTED (LIVE)")
                ImGui.popStyleColor()
            }
            llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTING -> {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.9f, 0.8f, 0.2f, 1f)
                ImGui.text("${Icons.REFRESH} CONNECTING...")
                ImGui.popStyleColor()
            }
            llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.ERROR -> {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.95f, 0.3f, 0.3f, 1f)
                ImGui.text("${Icons.ALERT} ERROR: ${llm.slop.liquidlsd.broadcast.BroadcastEngine.lastError ?: "Connection failed"}")
                ImGui.popStyleColor()
            }
            llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.DISCONNECTED -> {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1f)
                ImGui.text("${Icons.POWER} OFFLINE (DISCONNECTED)")
                ImGui.popStyleColor()
            }
        }

        ImGui.spacing()

        // Control Buttons
        if (isLive) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 1f)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 1f)
            if (ImGui.button("${Icons.POWER} Disconnect Broadcast", 200f, 32f)) {
                llm.slop.liquidlsd.broadcast.BroadcastEngine.stopBroadcast()
            }
            ImGui.popStyleColor(2)
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.15f, 0.6f, 0.25f, 1f)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.25f, 0.75f, 0.35f, 1f)
            if (ImGui.button("${Icons.ZAP} Go Live (Connect)", 200f, 32f)) {
                if (mixer != null) {
                    llm.slop.liquidlsd.broadcast.BroadcastEngine.startBroadcast(mixer)
                }
            }
            ImGui.popStyleColor(2)
        }

        if (state == llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTED) {
            ImGui.sameLine()
            if (ImGui.button("${Icons.REFRESH} Force Sync State", 160f, 32f)) {
                llm.slop.liquidlsd.broadcast.BroadcastEngine.forceSync()
            }
            itemTooltip("Re-send full state snapshot to relay immediately.")
        }
    }

    private data class ShortcutItem(val key: String, val action: String, val detail: String? = null)

    private fun drawShortcutTable(
        session: llm.slop.liquidlsd.SessionContext,
        tableId: String,
        shortcuts: List<ShortcutItem>
    ) {
        val keyColW = session.uiTheme.withFont(UITheme.FontLevel.CODE) {
            shortcuts.maxOfOrNull { ImGui.calcTextSize(it.key).x } ?: 120f
        } + 24f

        val tableFlags = ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp
        if (ImGui.beginTable(tableId, 2, tableFlags)) {
            ImGui.tableSetupColumn("Key / Input", ImGuiTableColumnFlags.WidthFixed, keyColW.coerceAtLeast(130f))
            ImGui.tableSetupColumn("Action & Description", ImGuiTableColumnFlags.WidthStretch, 1f)
            ImGui.tableHeadersRow()

            shortcuts.forEach { item ->
                ImGui.tableNextRow()
                ImGui.tableNextColumn()
                session.uiTheme.withFont(UITheme.FontLevel.CODE) {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.35f, 0.85f, 1.0f, 1.0f)
                    ImGui.text(item.key)
                    ImGui.popStyleColor()
                }
                ImGui.tableNextColumn()
                session.uiTheme.body(item.action)
                if (item.detail != null) {
                    session.uiTheme.captionColored(0.7f, 0.7f, 0.7f, 0.85f, item.detail)
                }
            }
            ImGui.endTable()
        }
    }

    private fun drawShortcutsSettings(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("Keyboard Shortcuts & Gestures")
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.caption("Quick reference for keyboard shortcuts and interactive gestures grouped by panel:")
        ImGui.spacing()

        // 1. Global & Display
        session.uiTheme.h3("Global & Display Controls")
        ImGui.spacing()
        drawShortcutTable(
            session,
            "##global_shortcuts",
            listOf(
                ShortcutItem("F", "Toggle Fullscreen / Clean Mode", "Hides all UI chrome to display full master video output."),
                ShortcutItem("Esc", "Exit Fullscreen / Clean Mode", "Restores the user interface when in Fullscreen Clean Mode."),
                ShortcutItem("B", "Toggle Background Video", "Renders master visuals behind the semi-transparent interface."),
                ShortcutItem("Ctrl + - / Cmd + -", "Decrease Preset Name Size", "Reduces Library browser preset name font size by 10% (80%–120%)."),
                ShortcutItem("Ctrl + = / Cmd + =", "Increase Preset Name Size", "Increases Library browser preset name font size by 10% (80%–120%)."),
                ShortcutItem("Ctrl + R / Cmd + R", "Start / Stop Recording", "Toggles live master output recording to MP4 video.")
            )
        )

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 2. Preset Grid & Modulation Matrix
        session.uiTheme.h3("Preset Grid & Modulation Matrix")
        ImGui.spacing()
        drawShortcutTable(
            session,
            "##grid_shortcuts",
            listOf(
                ShortcutItem("Ctrl + S / Cmd + S", "Save Active Deck Preset", "Saves the active deck preset in Patch Grid (opens Save As if untitled; ignored on Mixer)."),
                ShortcutItem("Shift + Ctrl + S / Shift + Cmd + S", "Save Active Deck Preset As...", "Opens the Save As modal for the active deck in Patch Grid (ignored on Mixer)."),
                ShortcutItem("Ctrl + Z / Cmd + Z", "Undo Parameter Action", "Reverts last parameter tweak, randomize, paste, or reset."),
                ShortcutItem("Ctrl + C / Cmd + C", "Copy Cell or Row", "Copies modulation routing (or row settings if Base/Final cell is selected)."),
                ShortcutItem("Ctrl + V / Cmd + V", "Paste Cell or Row", "Applies copied modulators or parameter settings with an undo point."),
                ShortcutItem("Delete / Backspace", "Clear Cell / Reset Parameter", "Clears modulators on active cell, or resets parameter to default."),
                ShortcutItem("Middle Click", "Mute / Bypass Modulation Cell", "Toggles modulation source on/off without discarding dial parameters.")
            )
        )

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 3. Cell Config & Number Inputs
        session.uiTheme.h3("Cell Config & Number Inputs")
        ImGui.spacing()
        drawShortcutTable(
            session,
            "##cellconfig_shortcuts",
            listOf(
                ShortcutItem("Up / Down Arrow", "Step Numeric Value (Focused Input)", "Increments/decrements focused number box: \u00B10.001 (fine), Shift: \u00B10.01, Ctrl+Shift: \u00B10.1."),
                ShortcutItem("Mouse Wheel (Hover)", "Adjust Value / Range Bounds", "Scrolls value or hovered min/max range handle: \u00B10.001 (fine), Shift: \u00B10.01, Ctrl+Shift: \u00B10.1."),
                ShortcutItem("Middle Click", "Reset to Default Value", "Resets focused input field or range slider track to default value."),
                ShortcutItem("Left Click (Dice)", "Toggle Random Range", "Enables or disables randomized modulation boundaries."),
                ShortcutItem("Right Click (Dice)", "Randomize Now", "Immediately samples a random value within the active parameter range.")
            )
        )

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 4. Library & Asset Browser
        session.uiTheme.h3("Library & Asset Browser")
        ImGui.spacing()
        drawShortcutTable(
            session,
            "##library_shortcuts",
            listOf(
                ShortcutItem("1", "Load to Deck A", "Loads selected preset into Deck A."),
                ShortcutItem("2", "Load to Deck B", "Loads selected preset into Deck B."),
                ShortcutItem("3", "Load to Deck BG", "Loads selected preset into Background Deck (BG)."),
                ShortcutItem("4", "Preview on Deck PV", "Loads selected preset into Preview Deck (PV)."),
                ShortcutItem("Q", "Add to A/B Queue", "Appends selected preset to the A/B Play Queue."),
                ShortcutItem("Shift + Q", "Add to Background Queue", "Appends selected preset to the Background Queue (BG)."),
                ShortcutItem("Up / Down Arrow", "Navigate List Items", "Moves focus selection across presets, playlists, and queue items (auto-auditions when locked)."),
                ShortcutItem("Delete / Backspace", "Delete Preset (Library)", "Deletes selected user preset with permanent deletion confirmation."),
                ShortcutItem("Delete / Backspace", "Remove from Playlist", "Removes selected preset entry from active playlist editor."),
                ShortcutItem("Delete / Backspace", "Remove from Play Queue", "Removes selected item from current or background play queue.")
            )
        )

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 5. Audio & Clock Controls
        session.uiTheme.h3("Audio & Clock Controls")
        ImGui.spacing()
        drawShortcutTable(
            session,
            "##clock_shortcuts",
            listOf(
                ShortcutItem("T", "Tap Tempo", "Taps in manual BPM tempo when not typing in text fields.")
            )
        )
    }

    private var customFolderPathBuf: ImString? = null

    private fun drawShaderLocationsSettings(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("ISF Shader Locations & Libraries")
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.caption("Manage search paths for Interactive Shader Format (ISF) generators, filters, and transitions. Directories are scanned recursively and prioritized by origin.")
        ImGui.spacing()

        if (customFolderPathBuf == null) {
            customFolderPathBuf = ImString(256)
        }
        ImGui.setNextItemWidth(360f)
        ImGui.inputTextWithHint("##custom_folder_path", "Enter absolute path to folder...", customFolderPathBuf!!)
        ImGui.sameLine()
        if (ImGui.button("Add Folder##add_isf_dir")) {
            val pathStr = customFolderPathBuf!!.get().trim()
            if (pathStr.isNotBlank()) {
                val added = llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.addCustomDirectory(pathStr)
                if (added) {
                    customFolderPathBuf!!.set("")
                    // Async scan so the render thread is never stalled by disk I/O.
                    llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibraryAsync(onComplete = {
                        llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll()
                        llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.loadAll()
                        llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.loadAll()
                    })
                }
            }
        }
        itemTooltip("Add an arbitrary local directory containing ISF shaders.")

        ImGui.sameLine()
        if (ImGui.button("Rescan Now##rescan_isf")) {
            // Async scan so the render thread is never stalled by disk I/O.
            llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibraryAsync(onComplete = {
                llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll()
                llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.loadAll()
                llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.loadAll()
            })
        }
        itemTooltip("Force immediate re-scan of all enabled ISF directories.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        val resolvedDirs = llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.getResolvedDirectories()

        val tableFlags = ImGuiTableFlags.Borders or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingFixedFit
        if (ImGui.beginTable("##isf_dirs_table", 5, tableFlags)) {
            ImGui.tableSetupColumn("En", ImGuiTableColumnFlags.WidthFixed, 30f)
            ImGui.tableSetupColumn("Path / Expanded Path", ImGuiTableColumnFlags.WidthStretch, 0.5f)
            ImGui.tableSetupColumn("Origin Badge", ImGuiTableColumnFlags.WidthFixed, 110f)
            ImGui.tableSetupColumn("Status", ImGuiTableColumnFlags.WidthFixed, 90f)
            ImGui.tableSetupColumn("Actions", ImGuiTableColumnFlags.WidthFixed, 90f)
            ImGui.tableHeadersRow()

            resolvedDirs.forEach { resolved ->
                ImGui.tableNextRow()

                // Col 0: Enabled checkbox
                ImGui.tableSetColumnIndex(0)
                val enabled = ImBoolean(resolved.config.isEnabled)
                if (ImGui.checkbox("##en_${resolved.config.path}", enabled)) {
                    llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.toggleDirectoryEnabled(resolved.config.path, enabled.get())
                    llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibraryAsync(onComplete = {
                        llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll()
                        llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.loadAll()
                        llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.loadAll()
                    })
                }

                // Col 1: Path
                ImGui.tableSetColumnIndex(1)
                ImGui.text(resolved.config.path)
                if (resolved.config.path != resolved.expandedPath) {
                    session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                        ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, "-> ${resolved.expandedPath}")
                    }
                }

                // Col 2: Badge
                ImGui.tableSetColumnIndex(2)
                val badgeText = when (resolved.config.type) {
                    llm.slop.liquidlsd.rendering.isf.DirectorySourceType.BUILT_IN -> "Built-in"
                    llm.slop.liquidlsd.rendering.isf.DirectorySourceType.SYSTEM_STANDARD -> "System"
                    llm.slop.liquidlsd.rendering.isf.DirectorySourceType.USER_STANDARD -> "User"
                    llm.slop.liquidlsd.rendering.isf.DirectorySourceType.CUSTOM -> "Custom"
                }
                ImGui.text(badgeText)

                // Col 3: Status
                ImGui.tableSetColumnIndex(3)
                when (resolved.status) {
                    llm.slop.liquidlsd.rendering.isf.DirectoryStatus.ACTIVE -> ImGui.textColored(0.2f, 0.8f, 0.2f, 1.0f, "Active")
                    llm.slop.liquidlsd.rendering.isf.DirectoryStatus.MISSING -> ImGui.textColored(0.9f, 0.7f, 0.1f, 1.0f, "Missing")
                    llm.slop.liquidlsd.rendering.isf.DirectoryStatus.UNREADABLE -> ImGui.textColored(0.9f, 0.2f, 0.2f, 1.0f, "Unreadable")
                }

                // Col 4: Actions
                ImGui.tableSetColumnIndex(4)
                if (resolved.config.type == llm.slop.liquidlsd.rendering.isf.DirectorySourceType.BUILT_IN) {
                    ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "Protected")
                } else {
                    if (ImGui.button("Remove##${resolved.config.path}")) {
                        llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.removeDirectory(resolved.config.path)
                        llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibraryAsync(onComplete = {
                            llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll()
                            llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.loadAll()
                            llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.loadAll()
                        })
                    }
                }
            }
            ImGui.endTable()
        }
    }
}
