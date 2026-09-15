package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import imgui.type.ImInt
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTableColumnFlags
import llm.slop.liquidlsd.input.TouchBackendState

/**
 * Modal preferences overlay with a left vertical navigation bar.
 * Call [open] when the menu item is clicked.
 * Call [draw] once per frame inside the active ImGui frame.
 */
object PreferencesPanel {

    private const val POPUP_ID  = "Preferences##modal"
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
        MIDI_CONTROLLER("MIDI Controls"),
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
             onPresetScaleChanged: (Int) -> Unit,
             parametersState: ParametersState? = null) {

        val minW = 1000f.coerceAtMost(displayW * 0.98f)
        val minH = 320f.coerceAtMost(displayH * 0.98f)

        val defaultW = MODAL_W.coerceIn(minW, displayW * 0.98f)
        val defaultH = 520f.coerceIn(minH, displayH * 0.90f)

        val targetW = if (session.uiTheme.preferencesWidth > 100f) session.uiTheme.preferencesWidth.coerceIn(minW, displayW * 0.98f) else defaultW
        val targetH = if (session.uiTheme.preferencesHeight > 100f) session.uiTheme.preferencesHeight.coerceIn(minH, displayH * 0.98f) else defaultH

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
        if (kotlin.math.abs(currentWinW - session.uiTheme.preferencesWidth) > 1f ||
            kotlin.math.abs(currentWinH - session.uiTheme.preferencesHeight) > 1f) {
            session.uiTheme.preferencesWidth = currentWinW
            session.uiTheme.preferencesHeight = currentWinH
            session.uiTheme.savePreferences()
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
        if (ImGui.beginChild("##preferences_sidebar", sidebarW, contentH, true)) {
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
        if (ImGui.beginChild("##preferences_content", rightContentW, contentH, true)) {
            when (activeCategory) {
                Category.GENERAL          -> drawGeneralPreferences(session, currentSize, onPresetScaleChanged)
                Category.VIDEO_DISPLAY    -> drawVideoDisplayPreferences(session)
                Category.TEMPO_SYNC       -> drawTempoSyncPreferences(session)
                Category.AUDIO_ENGINE     -> drawAudioEnginePreferences(session)
                Category.MIDI_CONTROLLER  -> MidiPreferencesPanel.drawContent(session, parametersState)
                Category.SHADER_LOCATIONS -> drawShaderLocationsPreferences(session)
                Category.BROADCAST        -> BroadcastPreferencesPanel.drawContent(session, mixer)
                Category.SHORTCUTS        -> drawShortcutsPreferences(session)
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

    private fun drawVideoDisplayPreferences(session: llm.slop.liquidlsd.SessionContext) {
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

    private fun drawTempoSyncPreferences(session: llm.slop.liquidlsd.SessionContext) {
        TempoSyncPanel.drawContent(session)
    }

    private fun drawAudioEnginePreferences(session: llm.slop.liquidlsd.SessionContext) {
        AudioEnginePanel.drawContent(session)
    }

    private fun drawGeneralPreferences(session: llm.slop.liquidlsd.SessionContext, currentSize: Float, onPresetScaleChanged: (Int) -> Unit) {
        session.uiTheme.h2("Features")
        ImGui.separator()
        ImGui.spacing()

        val randEnabled = ImBoolean(session.uiTheme.randomizationEnabled)
        if (ImGui.checkbox("Enable Parameter Randomization", randEnabled)) {
            val nextVal = randEnabled.get()
            if (nextVal != session.uiTheme.randomizationEnabled) {
                session.uiTheme.randomizationEnabled = nextVal
                session.uiTheme.savePreferences()
            }
        }
        itemTooltip("Toggle parameter and modulator randomization controls.")

        val seqEnabled = ImBoolean(session.uiTheme.sequencerEnabled)
        if (ImGui.checkbox("Enable Step Sequencer", seqEnabled)) {
            val nextVal = seqEnabled.get()
            if (nextVal != session.uiTheme.sequencerEnabled) {
                session.uiTheme.sequencerEnabled = nextVal
                session.uiTheme.savePreferences()
            }
        }
        itemTooltip("Enable or disable the step sequencer modulation engine across presets and parameter properties.")

        session.uiTheme.captionColored(0.5f, 0.7f, 1.0f, 1.0f, "MIDI hardware configuration has moved to the 'MIDI Controls' tab.")

        val framelessEnabled = ImBoolean(session.uiTheme.framelessWindow)
        if (ImGui.checkbox("Frameless Window (Custom Title Bar) [Requires restart]", framelessEnabled)) {
            val nextVal = framelessEnabled.get()
            if (nextVal != session.uiTheme.framelessWindow) {
                session.uiTheme.framelessWindow = nextVal
                session.uiTheme.savePreferences()
            }
        }
        itemTooltip("Removes OS window borders to integrate navigation, telemetry, and window controls into a unified top bar.\nDisable if using a tiling window manager (e.g. i3/sway) that manages decorations natively.")

        val trackpadEnabled = ImBoolean(session.uiTheme.trackpadConsoleEnabled)
        if (ImGui.checkbox("Enable CapsLock Trackpad Console", trackpadEnabled)) {
            val nextVal = trackpadEnabled.get()
            if (nextVal != session.uiTheme.trackpadConsoleEnabled) {
                session.uiTheme.trackpadConsoleEnabled = nextVal
                session.uiTheme.savePreferences()
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
                session.uiTheme.savePreferences()
            }
        }
        itemTooltip("Checks GitHub for new releases when Liquid LSD starts up.")

        ImGui.sameLine(0f, 15f)
        val checking = llm.slop.liquidlsd.update.UpdateChecker.isChecking
        val checkBtnLabel = if (checking) "${Icons.REFRESH} Checking..." else "${Icons.REFRESH} Check for Updates Now##preferences_check_now"
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
                if (ImGui.button("View Update##preferences_update", 120f, 0f)) {
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
            session.uiTheme.savePreferences()
        }

        ImGui.spacing()
        val autoVjBehaviors = UITheme.AutoVjDirtyBehavior.values()
        val autoVjBehaviorNames = autoVjBehaviors.map { it.name }.toTypedArray()
        val currentAutoVjIdx = imgui.type.ImInt(session.uiTheme.autoVjDirtyBehavior.ordinal)
        ImGui.setNextItemWidth(comboWidth)
        if (ImGui.combo("AutoVJ Dirty Behavior", currentAutoVjIdx, autoVjBehaviorNames)) {
            session.uiTheme.autoVjDirtyBehavior = autoVjBehaviors[currentAutoVjIdx.get()]
            session.uiTheme.savePreferences()
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
            session.uiTheme.savePreferences()
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
        if (ImGui.beginChild("##preset_slider_child", sliderW, 52f, false, imgui.flag.ImGuiWindowFlags.NoScrollbar)) {
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

    private var shortcutsFilterBuf = ImString(128)
    private val actionInputBuffers = mutableMapOf<String, ImString>()

    private fun drawShortcutGridTable(
        session: llm.slop.liquidlsd.SessionContext,
        tableId: String,
        actions: List<llm.slop.liquidlsd.ui.shortcuts.ShortcutAction>
    ) {
        val tableFlags = ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp
        if (ImGui.beginTable(tableId, 2, tableFlags)) {
            ImGui.tableSetupColumn("Action & Description", ImGuiTableColumnFlags.WidthStretch, 1f)
            ImGui.tableSetupColumn("Shortcut Binding & Actions", ImGuiTableColumnFlags.WidthFixed, 240f)
            ImGui.tableHeadersRow()

            actions.forEach { action ->
                val conflicts = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.findConflicts(action.id, action.currentKey)
                val hasConflict = conflicts.isNotEmpty()

                ImGui.tableNextRow()

                // Column 0 (Left): Action Name & Detailed Description
                ImGui.tableNextColumn()
                session.uiTheme.body(action.name)
                if (action.description.isNotEmpty()) {
                    session.uiTheme.captionColored(0.7f, 0.7f, 0.7f, 0.85f, action.description)
                }
                if (hasConflict) {
                    val conflictNames = conflicts.joinToString(", ") { it.name }
                    session.uiTheme.captionColored(
                        1.0f, 0.65f, 0.2f, 1.0f,
                        "${Icons.ALERT} Conflict with: $conflictNames"
                    )
                }

                // Column 1 (Right): Editable Text Box & Rebind Controls
                ImGui.tableNextColumn()
                val buf = actionInputBuffers.getOrPut(action.id) { ImString(64) }
                val displayKey = action.currentKey?.toDisplayString() ?: "None"

                if (hasConflict) {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, 0.5f, 0.2f, 0.05f, 0.8f)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.7f, 0.3f, 1.0f)
                }

                ImGui.setNextItemWidth(140f)
                session.uiTheme.withFont(UITheme.FontLevel.CODE) {
                    val changed = ImGui.inputText("##input_${action.id}", buf)
                    val isFocused = ImGui.isItemActive() || ImGui.isItemFocused()

                    if (!isFocused && !changed) {
                        if (buf.get() != displayKey) {
                            buf.set(displayKey)
                        }
                    }

                    if (isFocused) {
                        val io = ImGui.getIO()
                        if (ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
                            buf.set(displayKey)
                        } else if (ImGui.isKeyPressed(ImGuiKey.Backspace, false) && buf.get().isEmpty()) {
                            llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.updateKeyBinding(action.id, null)
                            buf.set("None")
                        } else {
                            var capturedKey = 0
                            for (k in 32..348) {
                                if (k in 256..257 || k in 340..347) continue // Skip modifiers like Left/Right Ctrl, Shift, Alt, Super
                                val imguiKey = llm.slop.liquidlsd.ui.shortcuts.KeyCombination.glfwKeyToImGuiKey(k)
                                if (imguiKey != ImGuiKey.None && ImGui.isKeyPressed(imguiKey, false)) {
                                    capturedKey = k
                                    break
                                }
                            }
                            if (capturedKey != 0) {
                                var mods = 0
                                if (io.keyCtrl) mods = mods or org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL
                                if (io.keyShift) mods = mods or org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT
                                if (io.keyAlt) mods = mods or org.lwjgl.glfw.GLFW.GLFW_MOD_ALT
                                if (io.keySuper) mods = mods or org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER

                                val newCombo = llm.slop.liquidlsd.ui.shortcuts.KeyCombination(capturedKey, mods)
                                llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.updateKeyBinding(action.id, newCombo)
                                buf.set(newCombo.toDisplayString())
                            }
                        }
                    }

                    if (changed || ImGui.isItemDeactivatedAfterEdit()) {
                        val parsed = llm.slop.liquidlsd.ui.shortcuts.KeyCombination.parse(buf.get())
                        llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.updateKeyBinding(action.id, parsed)
                    }
                }

                if (hasConflict) {
                    ImGui.popStyleColor(2)
                    itemTooltip("Warning: Key combination conflicts with ${conflicts.size} other action(s). Focus box and press keys or type combo.")
                } else {
                    itemTooltip("Click or focus box and press keys directly, or type shortcut text (e.g., 'Ctrl+S', 'F', 'None').")
                }

                if (hasConflict) {
                    ImGui.sameLine()
                    if (ImGui.button("Swap##swap_${action.id}")) {
                        llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.swapBindings(action.id, conflicts.first().id)
                        buf.set(action.currentKey?.toDisplayString() ?: "None")
                    }
                    itemTooltip("Swap keybinding with '${conflicts.first().name}'")
                }

                if (action.isModified) {
                    ImGui.sameLine()
                    if (ImGui.button("${Icons.REFRESH}##reset_${action.id}")) {
                        llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.resetToDefault(action.id)
                        buf.set(action.defaultKey?.toDisplayString() ?: "None")
                    }
                    itemTooltip("Reset to factory default (${action.defaultKey?.toDisplayString() ?: "None"})")
                }
            }
            ImGui.endTable()
        }
    }

    private fun drawShortcutsPreferences(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("Keyboard Shortcuts & Input Settings")
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.caption("Filter actions, edit keyboard shortcuts directly in inline text boxes, and resolve key collisions:")
        ImGui.spacing()

        // Top Filter & Reset Bar
        ImGui.setNextItemWidth(340f)
        ImGui.inputTextWithHint("##shortcut_filter", "${Icons.SEARCH} Filter shortcuts by name or key...", shortcutsFilterBuf)
        if (shortcutsFilterBuf.get().isNotEmpty()) {
            ImGui.sameLine()
            if (ImGui.button("${Icons.X}##clear_filter")) {
                shortcutsFilterBuf.set("")
            }
            itemTooltip("Clear search filter")
        }

        ImGui.sameLine()
        if (ImGui.button("${Icons.REFRESH} Reset All Defaults")) {
            llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.resetAllToDefaults()
            actionInputBuffers.clear()
        }
        itemTooltip("Restore factory default keybindings for all actions.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        val query = shortcutsFilterBuf.get().trim().lowercase()
        val categories = llm.slop.liquidlsd.ui.shortcuts.ShortcutCategory.values()

        categories.forEach { category ->
            val actions = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.getActionsForCategory(category)
            val filtered = actions.filter { action ->
                query.isEmpty() ||
                action.name.lowercase().contains(query) ||
                action.description.lowercase().contains(query) ||
                (action.currentKey?.toDisplayString()?.lowercase()?.contains(query) == true)
            }

            if (filtered.isNotEmpty()) {
                session.uiTheme.h3(category.label)
                ImGui.spacing()
                drawShortcutGridTable(session, "##table_${category.name.lowercase()}", filtered)
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }
        }
    }

    private var customFolderPathBuf: ImString? = null

    private fun drawShaderLocationsPreferences(session: llm.slop.liquidlsd.SessionContext) {
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

typealias SettingsPanel = PreferencesPanel

