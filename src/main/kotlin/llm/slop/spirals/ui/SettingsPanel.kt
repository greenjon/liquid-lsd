package llm.slop.spirals.ui

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

    fun open() = ImGui.openPopup(POPUP_ID)

    fun draw(currentSize: Float, displayW: Float, displayH: Float,
             onSizeChanged: (Float) -> Unit,
             onAutocollapseChanged: () -> Unit) {

        // Centre the modal on the screen every time it appears.
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            ImGuiCond.Appearing, 0.5f, 0.5f
        )

        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                    ImGuiWindowFlags.NoMove            or
                    ImGuiWindowFlags.NoCollapse

        if (!ImGui.beginPopupModal(POPUP_ID, flags)) return

        // --- Font Debugger Block ---
        UITheme.h3("Font Icon Debugger (E100-E17F)")
        for (i in 0xE100..0xE17F) {
            val hex = Integer.toHexString(i).uppercase()
            ImGui.text("$hex: ${i.toChar()}")
            if ((i - 0xE100 + 1) % 8 != 0) ImGui.sameLine(0f, 15f)
        }
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        // ---------------------------

        // -- Width anchor -- ensures the modal is never narrower than MODAL_W --
        ImGui.dummy(MODAL_W - 32f, 1f)   // 32 = 2 x default window padding

        // ---------------------------------------------------------------------
        // Fonts section
        // ---------------------------------------------------------------------
        ImGui.spacing()
        UITheme.h2("Fonts")
        ImGui.separator()
        ImGui.spacing()

        // Two-column table: label on the left, controls on the right.
        if (ImGui.beginTable("##fontSettings", 2)) {
            ImGui.tableSetupColumn("##lbl",  ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn("##ctrl", ImGuiTableColumnFlags.WidthFixed, 160f)

            ImGui.tableNextRow()

            // Left: field label + preview
            ImGui.tableSetColumnIndex(0)
            UITheme.body("Global Size")
            ImGui.spacing()
            val t = UITheme
            UITheme.caption(
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
            if (!canDecrease) ImGui.popStyleVar()

            ImGui.sameLine()
            UITheme.withFont(UITheme.FontLevel.CODE) {
                ImGui.text("%2.0f px".format(currentSize))
            }
            ImGui.sameLine()

            // Dim the + button when at maximum
            if (!canIncrease) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.35f)
            if (ImGui.button("  +  ##inc") && canIncrease)
                onSizeChanged(currentSize + STEP)
            if (!canIncrease) ImGui.popStyleVar()

            ImGui.endTable()
        }

        // ---------------------------------------------------------------------
        // Audio Engine Settings
        // ---------------------------------------------------------------------
        ImGui.spacing()
        UITheme.h2("Audio")
        ImGui.separator()
        ImGui.spacing()

        val audioEnabled = ImBoolean(UITheme.audioEngineEnabled)
        if (ImGui.checkbox("Enable Audio Engine (JACK)", audioEnabled)) {
            val nextVal = audioEnabled.get()
            if (nextVal != UITheme.audioEngineEnabled) {
                UITheme.audioEngineEnabled = nextVal
                UITheme.saveSettings()
                if (nextVal) {
                    llm.slop.spirals.audio.AudioEngine.start()
                } else {
                    llm.slop.spirals.audio.AudioEngine.stop()
                }
            }
        }
        ImGui.spacing()
        UITheme.caption("Disabling the audio engine stops JACK audio processing")
        UITheme.caption("and limits patch grid columns to LFO, RAND, and MIDI.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // Video Settings
        // ---------------------------------------------------------------------
        UITheme.h2("Video")
        ImGui.separator()
        ImGui.spacing()

        val bgVideoEnabled = ImBoolean(UITheme.backgroundVideoEnabled)
        if (ImGui.checkbox("Background Video", bgVideoEnabled)) {
            val nextVal = bgVideoEnabled.get()
            if (nextVal != UITheme.backgroundVideoEnabled) {
                UITheme.backgroundVideoEnabled = nextVal
                UITheme.saveSettings()
            }
        }
        ImGui.spacing()
        UITheme.caption("When enabled, the final output video renders behind the UI,")
        UITheme.caption("and the interface panels become semi-transparent.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // Interface Settings
        // ---------------------------------------------------------------------
        UITheme.h2("Interface")
        ImGui.separator()
        ImGui.spacing()

        val autocollapseEnabled = ImBoolean(UITheme.autocollapseEnabled)
        if (ImGui.checkbox("Autocollapse Grid Sections", autocollapseEnabled)) {
            val nextVal = autocollapseEnabled.get()
            if (nextVal != UITheme.autocollapseEnabled) {
                UITheme.autocollapseEnabled = nextVal
                UITheme.saveSettings()
                onAutocollapseChanged()
            }
        }
        ImGui.spacing()
        UITheme.caption("When enabled, opening a grid section or subgroup will")
        UITheme.caption("automatically collapse other sections at that same level.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // Setlist & Live Mode Settings
        // ---------------------------------------------------------------------
        UITheme.h2("Setlist & Live Mode")
        ImGui.separator()
        ImGui.spacing()

        val behaviors = UITheme.SetlistTransitionBehavior.values()
        val behaviorNames = behaviors.map { it.name }.toTypedArray()
        val currentBehaviorIdx = imgui.type.ImInt(UITheme.setlistTransitionBehavior.ordinal)
        if (ImGui.combo("Transition Behavior", currentBehaviorIdx, behaviorNames)) {
            UITheme.setlistTransitionBehavior = behaviors[currentBehaviorIdx.get()]
            UITheme.saveSettings()
        }
        ImGui.spacing()

        val autoVjBehaviors = UITheme.AutoVjDirtyBehavior.values()
        val autoVjBehaviorNames = autoVjBehaviors.map { it.name }.toTypedArray()
        val currentAutoVjIdx = imgui.type.ImInt(UITheme.autoVjDirtyBehavior.ordinal)
        if (ImGui.combo("AutoVJ Dirty Behavior", currentAutoVjIdx, autoVjBehaviorNames)) {
            UITheme.autoVjDirtyBehavior = autoVjBehaviors[currentAutoVjIdx.get()]
            UITheme.saveSettings()
        }
        ImGui.spacing()

        val triggers = UITheme.SetlistKeyTrigger.values()
        val triggerNames = triggers.map { it.name }.toTypedArray()
        val currentTriggerIdx = imgui.type.ImInt(UITheme.setlistKeyTrigger.ordinal)
        if (ImGui.combo("Keyboard Trigger", currentTriggerIdx, triggerNames)) {
            UITheme.setlistKeyTrigger = triggers[currentTriggerIdx.get()]
            UITheme.saveSettings()
        }
        ImGui.spacing()

        val midiDir = java.io.File("presets/midi")
        val profileFiles = (midiDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray())
            .map { it.nameWithoutExtension }
            .toMutableList()
        if (profileFiles.isEmpty()) profileFiles.add("default")
        if (!profileFiles.contains(UITheme.activeMidiProfile)) {
            profileFiles.add(UITheme.activeMidiProfile)
        }

        val currentProfileIdx = imgui.type.ImInt(profileFiles.indexOf(UITheme.activeMidiProfile).coerceAtLeast(0))
        val profileNamesArray = profileFiles.toTypedArray()
        if (ImGui.combo("MIDI Profile", currentProfileIdx, profileNamesArray)) {
            val nextProfile = profileNamesArray[currentProfileIdx.get()]
            llm.slop.spirals.midi.MidiMappingManager.loadProfile(nextProfile)
            UITheme.activeMidiProfile = nextProfile
            UITheme.saveSettings()
        }
        ImGui.spacing()

        val nextCc = imgui.type.ImInt(llm.slop.spirals.midi.MidiMappingManager.getCcForSpecial("Global/setlistNext"))
        if (ImGui.inputInt("Next CC", nextCc)) {
            val newVal = nextCc.get().coerceIn(-1, 127)
            llm.slop.spirals.midi.MidiMappingManager.addMapping("Global/setlistNext", newVal)
            llm.slop.spirals.midi.MidiMappingManager.saveActiveProfile()
        }
        ImGui.spacing()

        val prevCc = imgui.type.ImInt(llm.slop.spirals.midi.MidiMappingManager.getCcForSpecial("Global/setlistPrev"))
        if (ImGui.inputInt("Prev CC", prevCc)) {
            val newVal = prevCc.get().coerceIn(-1, 127)
            llm.slop.spirals.midi.MidiMappingManager.addMapping("Global/setlistPrev", newVal)
            llm.slop.spirals.midi.MidiMappingManager.saveActiveProfile()
        }
        ImGui.spacing()
        UITheme.caption("Set to -1 to disable MIDI CC triggers.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // Centred Close button
        val closeW = 110f
        ImGui.setCursorPosX((MODAL_W - 32f - closeW) * 0.5f + ImGui.getWindowContentRegionMinX())
        if (ImGui.button("Close", closeW, 0f)) ImGui.closeCurrentPopup()

        ImGui.spacing()
        ImGui.endPopup()
    }
}
