package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean

/**
 * Modal settings overlay with a left vertical navigation bar.
 * Call [open] when the menu item is clicked.
 * Call [draw] once per frame inside the active ImGui frame.
 */
object SettingsPanel {

    private const val POPUP_ID = "Settings##modal"
    private const val MIN_SIZE = 10f
    private const val MAX_SIZE = 36f
    private const val STEP     = 1f
    private const val MODAL_W  = 580f
    private const val MODAL_H  = 420f

    enum class Category(val label: String) {
        APPEARANCE("Appearance"),
        PATCH_GRID("Patch Grid"),
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

        val contentH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            (ImGui.getTextLineHeightWithSpacing() * 15f)
        }.coerceIn(300f, displayH * 0.78f)

        val availW = ImGui.getContentRegionAvailX()
        val rightContentW = (availW - sidebarW - ImGui.getStyle().itemSpacing.x).coerceAtLeast(100f)

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
                Category.APPEARANCE   -> drawAppearance(session, currentSize, onSizeChanged)
                Category.PATCH_GRID   -> drawPatchGridSettings(session)
                Category.AUDIO_ENGINE -> drawAudioEngineSettings(session)
                Category.MIDI_CONTROL -> drawMidiControlSettings(session)
                Category.GENERAL      -> drawGeneralSettings(session)
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

        if (ImGui.beginTable("##fontSettings", 2)) {
            val fontCtrlW = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                ImGui.calcTextSize("  -  ").x + ImGui.calcTextSize(" 00 px ").x + ImGui.calcTextSize("  +  ").x + 40f
            }.coerceAtLeast(140f)
            ImGui.tableSetupColumn("##lbl",  ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn("##ctrl", ImGuiTableColumnFlags.WidthFixed, fontCtrlW)
            ImGui.tableNextRow()

            ImGui.tableSetColumnIndex(0)
            session.uiTheme.body("Global Size")
            val t = UITheme
            session.uiTheme.caption(
                "Cap ${(currentSize * t.multCaption).toInt()}  " +
                "Body ${(currentSize * t.multBody).toInt()}  " +
                "H3 ${(currentSize * t.multH3).toInt()}  " +
                "H2 ${(currentSize * t.multH2).toInt()}  " +
                "H1 ${(currentSize * t.multH1).toInt()}  px"
            )

            ImGui.tableSetColumnIndex(1)
            val canDecrease = currentSize > MIN_SIZE
            val canIncrease = currentSize < MAX_SIZE

            if (!canDecrease) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.35f)
            if (ImGui.button("  -  ##dec") && canDecrease) onSizeChanged(currentSize - STEP)
            if (!canDecrease) ImGui.popStyleVar()

            ImGui.sameLine()
            session.uiTheme.withFont(UITheme.FontLevel.CODE) {
                ImGui.text("%2.0f px".format(currentSize))
            }
            ImGui.sameLine()

            if (!canIncrease) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.35f)
            if (ImGui.button("  +  ##inc") && canIncrease) onSizeChanged(currentSize + STEP)
            if (!canIncrease) ImGui.popStyleVar()

            ImGui.endTable()
        }
    }

    private fun drawPatchGridSettings(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("Patch Grid CV Columns")
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.caption("Toggle which CV source columns appear in the Patch Grid:")
        ImGui.spacing()

        val midiVal = ImBoolean(session.uiTheme.showMidiCol)
        if (ImGui.checkbox("Show MIDI Column", midiVal)) {
            session.uiTheme.showMidiCol = midiVal.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Display MIDI CC modulation column in Patch Grid")
        }

        val lfoVal = ImBoolean(session.uiTheme.showLfoCol)
        if (ImGui.checkbox("Show LFO Column", lfoVal)) {
            session.uiTheme.showLfoCol = lfoVal.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Display LFO / Oscillator modulation column in Patch Grid")
        }

        val audioVal = ImBoolean(session.uiTheme.showAudioCol)
        if (ImGui.checkbox("Show Audio Column", audioVal)) {
            session.uiTheme.showAudioCol = audioVal.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Display Audio spectral analysis modulation column in Patch Grid")
        }

        val trigVal = ImBoolean(session.uiTheme.showTriggerCol)
        if (ImGui.checkbox("Show Trigger Column", trigVal)) {
            session.uiTheme.showTriggerCol = trigVal.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Display Audio transient / trigger modulation column in Patch Grid")
        }

        ImGui.spacing()
        session.uiTheme.h2("Video & Performance")
        ImGui.separator()
        ImGui.spacing()

        val bgVideoEnabled = ImBoolean(session.uiTheme.backgroundVideoEnabled)
        if (ImGui.checkbox("Background Video", bgVideoEnabled)) {
            session.uiTheme.backgroundVideoEnabled = bgVideoEnabled.get()
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Render master output video behind the semi-transparent interface.")
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

        val midiDir = java.io.File("library/midi").let { if (!it.exists() && java.io.File("presets/midi").exists()) java.io.File("presets/midi") else it }
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
