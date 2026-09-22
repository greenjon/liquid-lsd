package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
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
        SHADER_LOCATIONS("Shader Locations"),
        VIDEO_DISPLAY("Video & Display"),
        AUDIO_ENGINE("Audio Hardware"),
        TEMPO_SYNC("Tempo & Sync"),
        MIDI_CONTROLLER("MIDI Controls"),
        OSC_CONTROLLER("OSC Controls"),
        SHORTCUTS("Keyboard Shortcuts"),
        BROADCAST("Web Broadcast")
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
            AppPreferencesStore.savePreferences()
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
                Category.SHADER_LOCATIONS -> drawShaderLocationsPreferences(session)
                Category.VIDEO_DISPLAY    -> drawVideoDisplayPreferences(session)
                Category.AUDIO_ENGINE     -> drawAudioEnginePreferences(session)
                Category.TEMPO_SYNC       -> drawTempoSyncPreferences(session)
                Category.MIDI_CONTROLLER  -> MidiPreferencesPanel.drawContent(session, parametersState)
                Category.OSC_CONTROLLER   -> OscPreferencesPanel.drawContent(session)
                Category.SHORTCUTS        -> ShortcutsPreferencesPanel.drawContent(session)
                Category.BROADCAST        -> BroadcastPreferencesPanel.drawContent(session, mixer)
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
        VideoDisplayPreferencesPanel.drawContent(session)
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
                AppPreferencesStore.savePreferences()
            }
        }
        itemTooltip("Toggle parameter and modulator randomization controls.")

        val seqEnabled = ImBoolean(session.uiTheme.sequencerEnabled)
        if (ImGui.checkbox("Enable Step Sequencer", seqEnabled)) {
            val nextVal = seqEnabled.get()
            if (nextVal != session.uiTheme.sequencerEnabled) {
                session.uiTheme.sequencerEnabled = nextVal
                AppPreferencesStore.savePreferences()
            }
        }
        itemTooltip("Enable or disable the step sequencer modulation engine across presets and parameter properties.")

        session.uiTheme.captionColored(0.5f, 0.7f, 1.0f, 1.0f, "MIDI hardware configuration has moved to the 'MIDI Controls' tab.")

        val framelessEnabled = ImBoolean(session.uiTheme.framelessWindow)
        if (ImGui.checkbox("Frameless Window (Custom Title Bar) [Requires restart]", framelessEnabled)) {
            val nextVal = framelessEnabled.get()
            if (nextVal != session.uiTheme.framelessWindow) {
                session.uiTheme.framelessWindow = nextVal
                AppPreferencesStore.savePreferences()
            }
        }
        itemTooltip("Removes OS window borders to integrate navigation, telemetry, and window controls into a unified top bar.\nDisable if using a tiling window manager (e.g. i3/sway) that manages decorations natively.")

        val trackpadEnabled = ImBoolean(session.uiTheme.trackpadConsoleEnabled)
        if (ImGui.checkbox("Enable CapsLock Trackpad Console", trackpadEnabled)) {
            val nextVal = trackpadEnabled.get()
            if (nextVal != session.uiTheme.trackpadConsoleEnabled) {
                session.uiTheme.trackpadConsoleEnabled = nextVal
                AppPreferencesStore.savePreferences()
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
                AppPreferencesStore.savePreferences()
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
            AppPreferencesStore.savePreferences()
        }

        ImGui.spacing()
        val autoVjBehaviors = UITheme.AutoVjDirtyBehavior.values()
        val autoVjBehaviorNames = autoVjBehaviors.map { it.name }.toTypedArray()
        val currentAutoVjIdx = imgui.type.ImInt(session.uiTheme.autoVjDirtyBehavior.ordinal)
        ImGui.setNextItemWidth(comboWidth)
        if (ImGui.combo("AutoVJ Dirty Behavior", currentAutoVjIdx, autoVjBehaviorNames)) {
            session.uiTheme.autoVjDirtyBehavior = autoVjBehaviors[currentAutoVjIdx.get()]
            AppPreferencesStore.savePreferences()
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
            AppPreferencesStore.savePreferences()
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

    private fun drawShaderLocationsPreferences(session: llm.slop.liquidlsd.SessionContext) {
        ShaderLocationsPreferencesPanel.drawContent(session)
    }
}

typealias SettingsPanel = PreferencesPanel

