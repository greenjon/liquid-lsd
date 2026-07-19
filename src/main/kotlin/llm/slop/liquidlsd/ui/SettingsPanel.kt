package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean

/**
 * Modal settings overlay. Call [open] when the menu item is clicked.
 * Call [draw] once per frame inside the active ImGui frame.
 *
 * [onSizeChanged] receives the new requested base-size in pixels; the
 * caller (UIManager) is responsible for rebuilding fonts and scaling style.
 */
object SettingsPanel {

    private const val POPUP_ID = "Settings##modal"
    private const val MIN_SIZE = 10f
    private const val MAX_SIZE = 28f
    private const val STEP     = 1f
    private const val MODAL_W  = 380f  // inner content target width
    private const val MODAL_MARGIN = 48f
    private const val WINDOW_PADDING_W = 32f

    fun open() = ImGui.openPopup(POPUP_ID)

    fun draw(session: llm.slop.liquidlsd.SessionContext, currentSize: Float, displayW: Float, displayH: Float,
             onSizeChanged: (Float) -> Unit) {
        val modalW = MODAL_W.coerceAtMost((displayW - MODAL_MARGIN).coerceAtLeast(240f))
        val contentAnchorW = (modalW - WINDOW_PADDING_W).coerceAtLeast(180f)
        val controlColumnW = 160f.coerceAtMost(contentAnchorW * 0.48f)

        // Centre the modal on the screen every time it appears.
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            ImGuiCond.Appearing, 0.5f, 0.5f
        )

        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                    ImGuiWindowFlags.NoMove            or
                    ImGuiWindowFlags.NoCollapse

        if (!ImGui.beginPopupModal(POPUP_ID, flags)) return


        // -- Width anchor -- ensures the modal is never narrower than MODAL_W --
        ImGui.dummy(contentAnchorW, 1f)

        // ---------------------------------------------------------------------
        // Appearance section
        // ---------------------------------------------------------------------
        ImGui.spacing()
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

        // ---------------------------------------------------------------------
        // Fonts section
        // ---------------------------------------------------------------------
        ImGui.spacing()
        session.uiTheme.h2("Fonts")
        ImGui.separator()
        ImGui.spacing()

        // Two-column table: label on the left, controls on the right.
        if (ImGui.beginTable("##fontSettings", 2)) {
            ImGui.tableSetupColumn("##lbl",  ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn("##ctrl", ImGuiTableColumnFlags.WidthFixed, controlColumnW)

            ImGui.tableNextRow()

            // Left: field label + preview
            ImGui.tableSetColumnIndex(0)
            session.uiTheme.body("Global Size")
            ImGui.spacing()
            val t = UITheme
            session.uiTheme.caption(
                "Cap ${(currentSize * t.multCaption).toInt()}  " +
                "Body ${(currentSize * t.multBody).toInt()}  " +
                "H3 ${(currentSize * t.multH3).toInt()}  " +
                "H2 ${(currentSize * t.multH2).toInt()}  " +
                "H1 ${(currentSize * t.multH1).toInt()}  px"
            )

            // Right: [-]  15 px  [+]  (vertically centred in the row)
            ImGui.tableSetColumnIndex(1)
            ImGui.spacing()

            val canDecrease = currentSize > MIN_SIZE
            val canIncrease = currentSize < MAX_SIZE

            // Dim the - button when at minimum
            if (!canDecrease) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.35f)
            if (ImGui.button("  -  ##dec") && canDecrease)
                onSizeChanged(currentSize - STEP)
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Decrease global interface and font size.")
            }
            if (!canDecrease) ImGui.popStyleVar()

            ImGui.sameLine()
            session.uiTheme.withFont(UITheme.FontLevel.CODE) {
                ImGui.text("%2.0f px".format(currentSize))
            }
            ImGui.sameLine()

            // Dim the + button when at maximum
            if (!canIncrease) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.35f)
            if (ImGui.button("  +  ##inc") && canIncrease)
                onSizeChanged(currentSize + STEP)
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Increase global interface and font size.")
            }
            if (!canIncrease) ImGui.popStyleVar()

            ImGui.endTable()
        }

        // ---------------------------------------------------------------------
        // Audio Engine Settings
        // ---------------------------------------------------------------------
        ImGui.spacing()
        session.uiTheme.h2("Audio")
        ImGui.separator()
        ImGui.spacing()

        val audioEnabled = ImBoolean(session.uiTheme.audioEngineEnabled)
        if (ImGui.checkbox("Enable Audio Engine (JACK)", audioEnabled)) {
            val nextVal = audioEnabled.get()
            if (nextVal != session.uiTheme.audioEngineEnabled) {
                session.uiTheme.audioEngineEnabled = nextVal
                session.uiTheme.saveSettings()
                if (nextVal) {
                    session.audioEngine.start()
                } else {
                    session.audioEngine.stop()
                }
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Toggle the JACK audio backend. When disabled, audio-derived modulation columns are hidden.")
        }
        ImGui.spacing()
        session.uiTheme.caption("Disabling the audio engine stops JACK audio processing")
        session.uiTheme.caption("and limits patch grid columns to LFO, RAND, and MIDI.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // Video Settings
        // ---------------------------------------------------------------------
        session.uiTheme.h2("Video")
        ImGui.separator()
        ImGui.spacing()

        val bgVideoEnabled = ImBoolean(session.uiTheme.backgroundVideoEnabled)
        if (ImGui.checkbox("Background Video", bgVideoEnabled)) {
            val nextVal = bgVideoEnabled.get()
            if (nextVal != session.uiTheme.backgroundVideoEnabled) {
                session.uiTheme.backgroundVideoEnabled = nextVal
                session.uiTheme.saveSettings()
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Render master output video in the background with semi-transparent panels.")
        }
        ImGui.spacing()
        session.uiTheme.caption("When enabled, the final output video renders behind the UI,")
        session.uiTheme.caption("and the interface panels become semi-transparent.")

        ImGui.spacing()
        val limit30 = ImBoolean(session.uiTheme.maxFps == 30)
        if (ImGui.checkbox("Limit FPS to 30", limit30)) {
            session.uiTheme.maxFps = if (limit30.get()) 30 else 60
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Limit the rendering frame rate to 30 FPS instead of 60 FPS.")
        }
        ImGui.spacing()
        session.uiTheme.caption("Limits the rendering frame rate of the main loop. Checked is 30 FPS,")
        session.uiTheme.caption("unchecked is 60 FPS.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // Randomization Settings
        // ---------------------------------------------------------------------
        session.uiTheme.h2("Randomization")
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
            ImGui.setTooltip("Toggle parameter and modulator randomization features.")
        }
        ImGui.spacing()
        session.uiTheme.caption("When disabled, all sliders collapse to single static values")
        session.uiTheme.caption("and randomization controls/menus are hidden.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // Startup Settings
        // ---------------------------------------------------------------------
        session.uiTheme.h2("Startup")
        ImGui.separator()
        ImGui.spacing()

        val startupBehaviors = UITheme.StartupBehavior.values()
        val startupOptions = arrayOf("Restore Previous Session", "Start Empty")
        val currentStartupIdx = imgui.type.ImInt(session.uiTheme.startupBehavior.ordinal)
        if (ImGui.combo("Startup Behavior", currentStartupIdx, startupOptions)) {
            session.uiTheme.startupBehavior = startupBehaviors[currentStartupIdx.get()]
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Restore Previous Session: Reload decks and play queue on launch.\nStart Empty: Clean slate.")
        }
        ImGui.spacing()
        session.uiTheme.caption("Choose whether to load the previous session (active deck contents and play queue)")
        session.uiTheme.caption("or start with empty decks and queue.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // Queue & Live Mode Settings
        // ---------------------------------------------------------------------
        session.uiTheme.h2("Queue & Live Mode")
        ImGui.separator()
        ImGui.spacing()

        val autoVjBehaviors = UITheme.AutoVjDirtyBehavior.values()
        val autoVjBehaviorNames = autoVjBehaviors.map { it.name }.toTypedArray()
        val currentAutoVjIdx = imgui.type.ImInt(session.uiTheme.autoVjDirtyBehavior.ordinal)
        if (ImGui.combo("AutoVJ Dirty Behavior", currentAutoVjIdx, autoVjBehaviorNames)) {
            session.uiTheme.autoVjDirtyBehavior = autoVjBehaviors[currentAutoVjIdx.get()]
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Configure how Auto-VJ acts if a deck has unsaved manual changes.")
        }
        ImGui.spacing()

        val triggers = UITheme.QueueKeyTrigger.values()
        val triggerNames = triggers.map { it.name }.toTypedArray()
        val currentTriggerIdx = imgui.type.ImInt(session.uiTheme.queueKeyTrigger.ordinal)
        if (ImGui.combo("Keyboard Trigger", currentTriggerIdx, triggerNames)) {
            session.uiTheme.queueKeyTrigger = triggers[currentTriggerIdx.get()]
            session.uiTheme.saveSettings()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Set keyboard key sequence used to manually trigger queue advancement.")
        }
        ImGui.spacing()

        val midiDir = java.io.File("presets/midi")
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
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("MIDI CC number to advance the play queue. Set to -1 to disable.")
        }
        ImGui.spacing()

        val prevCc = imgui.type.ImInt(session.midiMappingManager.getCcForSpecial("Global/queuePrev"))
        if (ImGui.inputInt("Prev CC", prevCc)) {
            val newVal = prevCc.get().coerceIn(-1, 127)
            session.midiMappingManager.addMapping("Global/queuePrev", newVal)
            session.midiMappingManager.saveActiveProfile()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("MIDI CC number to trigger previous queue item. Set to -1 to disable.")
        }
        ImGui.spacing()
        session.uiTheme.caption("Set to -1 to disable MIDI CC triggers.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // Centred Close button
        val closeW = 110f
        ImGui.setCursorPosX(ImGui.getWindowContentRegionMinX() + (ImGui.getContentRegionAvailX() - closeW) * 0.5f)
        if (ImGui.button("Close", closeW, 0f)) ImGui.closeCurrentPopup()

        ImGui.spacing()
        ImGui.endPopup()
    }
}
