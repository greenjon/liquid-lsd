package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean

/**
 * Modal settings overlay with a left vertical navigation bar.
 * Call [open] when the menu item is clicked.
 * Call [draw] once per frame inside the active ImGui frame.
 */
object SettingsPanel {

    private const val POPUP_ID  = "Settings##modal"
    private const val MODAL_W   = 580f

    // Scale percentage model: 100% == BASE_PX (15 px).
    // Range 75 %–200 % in 5 % steps.
    private const val BASE_PX   = 15f
    private const val MIN_PCT   = 75
    private const val MAX_PCT   = 200
    private const val STEP_PCT  = 5

    /** Convert a raw baseSize (px) to the nearest 5 % step integer. */
    private fun pxToPct(px: Float): Int {
        val raw = (px / BASE_PX * 100f).toInt()
        return (raw / STEP_PCT * STEP_PCT).coerceIn(MIN_PCT, MAX_PCT)
    }

    /** Convert a percentage integer back to baseSize px. */
    fun pctToPx(pct: Int): Float = pct / 100f * BASE_PX

    enum class Category(val label: String) {
        APPEARANCE("Appearance"),
        PRESET_GRID("Preset Grid"),
        VIDEO_DISPLAY("Video & Display"),
        AUDIO_ENGINE("Audio Engine"),
        MIDI_CONTROL("MIDI & Controls"),
        GENERAL("General")
    }

    private var activeCategory = Category.APPEARANCE

    fun open() = ImGui.openPopup(POPUP_ID)

    fun draw(session: llm.slop.liquidlsd.SessionContext, currentSize: Float, displayW: Float, displayH: Float,
             onSizeChanged: (Float) -> Unit) {

        val fontScale = (currentSize / 15f).coerceAtLeast(1.0f)
        val targetW = (MODAL_W * fontScale).coerceIn(540f, displayW * 0.90f)

        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            ImGuiCond.Always, 0.5f, 0.5f
        )
        ImGui.setNextWindowSize(targetW, 0f, ImGuiCond.Always)

        val flags = ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoScrollbar

        if (!ImGui.beginPopupModal(POPUP_ID, flags)) return

        val sidebarW = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            Category.values().maxOf { ImGui.calcTextSize(it.label).x } + 36f
        }.coerceAtLeast(140f)

        val btnH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            ImGui.getFrameHeight() + 6f
        }.coerceAtLeast(30f)

        val desiredH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            (ImGui.getTextLineHeightWithSpacing() * 15f)
        }
        val maxH = (displayH * 0.78f).coerceAtLeast(100f)
        val minH = 300f.coerceAtMost(maxH)
        val contentH = desiredH.coerceIn(minH, maxH)

        val availW = ImGui.getContentRegionAvailX()
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
                Category.APPEARANCE    -> drawAppearance(session, currentSize, onSizeChanged)
                Category.PRESET_GRID   -> drawPresetGridSettings(session)
                Category.VIDEO_DISPLAY -> drawVideoDisplaySettings(session)
                Category.AUDIO_ENGINE  -> drawAudioEngineSettings(session)
                Category.MIDI_CONTROL  -> drawMidiControlSettings(session)
                Category.GENERAL       -> drawGeneralSettings(session)
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
        if (ImGui.button("Close", closeW, 0f)) ImGui.closeCurrentPopup()

        ImGui.endPopup()
    }

    private fun drawAppearance(session: llm.slop.liquidlsd.SessionContext, currentSize: Float, onSizeChanged: (Float) -> Unit) {
        session.uiTheme.h2("Appearance")
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
        if (ImGui.combo("UI Theme", currentThemeIdx, themeNames)) {
            val nextTheme = themes[currentThemeIdx.get()]
            session.uiTheme.theme = nextTheme
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Select the user interface color palette theme.")
        }
        ImGui.spacing()

        session.uiTheme.h2("Fonts & Sizing")
        ImGui.separator()
        ImGui.spacing()

        // Percentage scale slider – 75 % to 200 %, locked to 5 % steps.
        val currentPct = pxToPct(currentSize)
        val t = UITheme
        session.uiTheme.body("GUI Scale")
        session.uiTheme.caption(
            "Cap ${(currentSize * t.multCaption).toInt()}  " +
            "Body ${(currentSize * t.multBody).toInt()}  " +
            "H3 ${(currentSize * t.multH3).toInt()}  " +
            "H2 ${(currentSize * t.multH2).toInt()}  " +
            "H1 ${(currentSize * t.multH1).toInt()}  px"
        )
        ImGui.spacing()

        val steps = (MAX_PCT - MIN_PCT) / STEP_PCT   // number of discrete steps
        val stepIndex = imgui.type.ImInt((currentPct - MIN_PCT) / STEP_PCT)
        val availW = ImGui.getContentRegionAvailX()
        ImGui.setNextItemWidth(availW * 0.72f)
        if (ImGui.sliderInt("##guiScale", stepIndex.getData(), 0, steps, "$currentPct%%")) {
            val snappedPct = MIN_PCT + stepIndex.get() * STEP_PCT
            onSizeChanged(pctToPx(snappedPct))
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip(
                "Scale the entire GUI (fonts, padding, widgets) from $MIN_PCT%% to $MAX_PCT%%.\n" +
                "Ctrl+- and Ctrl+= adjust by 5%% steps.\n" +
                "100%% = 15 px body text."
            )
        }
        ImGui.sameLine()
        val canDecrease = currentPct > MIN_PCT
        val canIncrease = currentPct < MAX_PCT
        if (!canDecrease) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.35f)
        if (ImGui.button("-##sdec") && canDecrease) onSizeChanged(pctToPx(currentPct - STEP_PCT))
        if (!canDecrease) ImGui.popStyleVar()
        ImGui.sameLine()
        if (!canIncrease) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.35f)
        if (ImGui.button("+##sinc") && canIncrease) onSizeChanged(pctToPx(currentPct + STEP_PCT))
        if (!canIncrease) ImGui.popStyleVar()

        ImGui.spacing()
        session.uiTheme.body("Grid Knob Cell Scale")
        session.uiTheme.caption("Scale Preset Grid readouts relative to font size (0.70x – 2.00x):")
        ImGui.spacing()
        val ratioVal = imgui.type.ImFloat(session.uiTheme.gridCellRatio)
        if (ImGui.sliderFloat("##GridKnobScale", ratioVal.getData(), 0.70f, 2.00f, "%.2fx")) {
            session.uiTheme.gridCellRatio = ratioVal.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Scale circular readout knobs and grid cells relative to global font size (0.70x – 2.00x).")
        }
    }

    private fun drawPresetGridSettings(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("Preset Grid CV Columns")
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.caption("Toggle which CV source columns appear in the Preset Grid:")
        ImGui.spacing()

        val midiVal = ImBoolean(session.uiTheme.showMidiCol)
        if (ImGui.checkbox("Show MIDI Column", midiVal)) {
            session.uiTheme.showMidiCol = midiVal.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Display MIDI CC modulation column in Preset Grid")
        }

        val lfoVal = ImBoolean(session.uiTheme.showLfoCol)
        if (ImGui.checkbox("Show LFO Column", lfoVal)) {
            session.uiTheme.showLfoCol = lfoVal.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Display LFO / Oscillator modulation column in Preset Grid")
        }

        val audioVal = ImBoolean(session.uiTheme.showAudioCol)
        if (ImGui.checkbox("Show Audio Column", audioVal)) {
            session.uiTheme.showAudioCol = audioVal.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Display Audio spectral analysis modulation column in Preset Grid")
        }

        val trigVal = ImBoolean(session.uiTheme.showTriggerCol)
        if (ImGui.checkbox("Show Trigger Column", trigVal)) {
            session.uiTheme.showTriggerCol = trigVal.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Display Audio transient / trigger modulation column in Preset Grid")
        }
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun drawVideoDisplaySettings(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("Render Resolution")
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.caption("Internal render resolution for Decks, Mixer, and Video Output:")
        ImGui.spacing()

        val presets = UITheme.ResolutionPreset.values()
        val presetNames = presets.map { it.displayName }.toTypedArray()
        val currentPresetIdx = imgui.type.ImInt(session.uiTheme.renderResolutionPreset.ordinal)
        if (ImGui.combo("Resolution Preset", currentPresetIdx, presetNames)) {
            val nextPreset = presets[currentPresetIdx.get()]
            session.uiTheme.renderResolutionPreset = nextPreset
            if (nextPreset != UITheme.ResolutionPreset.CUSTOM) {
                session.uiTheme.customRenderWidth = nextPreset.width
                session.uiTheme.customRenderHeight = nextPreset.height
            }
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Select internal target rendering resolution. Lower resolutions (e.g. 720p or 540p) significantly reduce GPU load on heavy raymarch shaders.")
        }

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
        val activeW = session.uiTheme.renderWidth
        val activeH = session.uiTheme.renderHeight
        val gcdVal = gcd(activeW, activeH).coerceAtLeast(1)
        session.uiTheme.body("Active: ${activeW}x${activeH} (${activeW / gcdVal}:${activeH / gcdVal})")

        ImGui.spacing()
        session.uiTheme.h2("Display Scaling")
        ImGui.separator()
        ImGui.spacing()

        val scaleModes = UITheme.OutputScaleMode.values()
        val scaleModeNames = scaleModes.map { it.displayName }.toTypedArray()
        val currentScaleIdx = imgui.type.ImInt(session.uiTheme.outputScaleMode.ordinal)
        if (ImGui.combo("Output Scaling", currentScaleIdx, scaleModeNames)) {
            session.uiTheme.outputScaleMode = scaleModes[currentScaleIdx.get()]
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("How output is scaled when target screen aspect ratio differs from render resolution: Fit (Letterbox/Pillarbox), Fill (Crop), or Stretch.")
        }

        ImGui.spacing()
        session.uiTheme.h2("Performance & Background")
        ImGui.separator()
        ImGui.spacing()

        val bgVideoEnabled = ImBoolean(session.uiTheme.backgroundVideoEnabled)
        if (ImGui.checkbox("Background Video", bgVideoEnabled)) {
            session.uiTheme.backgroundVideoEnabled = bgVideoEnabled.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Render master output video behind the semi-transparent interface (Hotkey: B).")
        }

        val limit30 = ImBoolean(session.uiTheme.maxFps == 30)
        if (ImGui.checkbox("Limit FPS to 30", limit30)) {
            session.uiTheme.maxFps = if (limit30.get()) 30 else 60
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Limit frame rate to 30 FPS to conserve power.")
        }
    }

    private fun drawAudioEngineSettings(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("Audio Engine (JACK)")
        ImGui.separator()
        ImGui.spacing()

        val audioEnabled = ImBoolean(session.uiTheme.audioEngineEnabled)
        if (ImGui.checkbox("Enable Audio Engine (JACK)", audioEnabled)) {
            val nextVal = audioEnabled.get()
            if (nextVal != session.uiTheme.audioEngineEnabled) {
                session.uiTheme.audioEngineEnabled = nextVal
                session.uiTheme.saveSettings()
                if (nextVal) session.audioEngine.start() else session.audioEngine.stop()
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Toggle the JACK audio backend. Disabling stops audio processing.")
        }

        ImGui.spacing()
        session.uiTheme.caption("Disabling the audio engine stops JACK audio processing")
        session.uiTheme.caption("and limits preset grid columns to LFO, RAND, and MIDI.")
    }

    private fun drawMidiControlSettings(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("MIDI Controller & Shortcuts")
        ImGui.separator()
        ImGui.spacing()

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
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Select active MIDI controller CC assignment profile.")
        }

        ImGui.spacing()
        val nextCc = imgui.type.ImInt(session.midiMappingManager.getCcForSpecial("Global/queueNext"))
        if (ImGui.inputInt("Next CC", nextCc)) {
            val newVal = nextCc.get().coerceIn(-1, 127)
            session.midiMappingManager.addMapping("Global/queueNext", newVal)
            session.midiMappingManager.saveActiveProfile()
        }

        val prevCc = imgui.type.ImInt(session.midiMappingManager.getCcForSpecial("Global/queuePrev"))
        if (ImGui.inputInt("Prev CC", prevCc)) {
            val newVal = prevCc.get().coerceIn(-1, 127)
            session.midiMappingManager.addMapping("Global/queuePrev", newVal)
            session.midiMappingManager.saveActiveProfile()
        }

        ImGui.spacing()
        val triggers = UITheme.QueueKeyTrigger.values()
        val triggerNames = triggers.map { it.name }.toTypedArray()
        val currentTriggerIdx = imgui.type.ImInt(session.uiTheme.queueKeyTrigger.ordinal)
        if (ImGui.combo("Keyboard Trigger", currentTriggerIdx, triggerNames)) {
            session.uiTheme.queueKeyTrigger = triggers[currentTriggerIdx.get()]
            session.uiTheme.saveSettings()
        }
    }

    private fun drawGeneralSettings(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("Randomization & Features")
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
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Toggle parameter and modulator randomization controls.")
        }

        ImGui.spacing()
        session.uiTheme.h2("Startup & AutoVJ")
        ImGui.separator()
        ImGui.spacing()

        val startupBehaviors = UITheme.StartupBehavior.values()
        val startupOptions = arrayOf("Restore Previous Session", "Start Empty")
        val currentStartupIdx = imgui.type.ImInt(session.uiTheme.startupBehavior.ordinal)
        if (ImGui.combo("Startup Behavior", currentStartupIdx, startupOptions)) {
            session.uiTheme.startupBehavior = startupBehaviors[currentStartupIdx.get()]
            session.uiTheme.saveSettings()
        }

        ImGui.spacing()
        val autoVjBehaviors = UITheme.AutoVjDirtyBehavior.values()
        val autoVjBehaviorNames = autoVjBehaviors.map { it.name }.toTypedArray()
        val currentAutoVjIdx = imgui.type.ImInt(session.uiTheme.autoVjDirtyBehavior.ordinal)
        if (ImGui.combo("AutoVJ Dirty Behavior", currentAutoVjIdx, autoVjBehaviorNames)) {
            session.uiTheme.autoVjDirtyBehavior = autoVjBehaviors[currentAutoVjIdx.get()]
            session.uiTheme.saveSettings()
        }
    }
}
