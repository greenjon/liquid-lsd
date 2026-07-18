package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.patches.PatchManager
import mu.KotlinLogging
import llm.slop.liquidlsd.midi.MidiEngine
import llm.slop.liquidlsd.audio.AudioEngine

class MenuBar(
    private val popupManager: PopupManager,
    private val patchState: PatchGridState,
    private val onTriggerExitFlow: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onOpenAudioEngineMonitor: () -> Unit
) {
    private val logger = KotlinLogging.logger {}

    fun draw(mixer: Mixer) {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.beginMenu("New Patch")) {
                    if (ImGui.menuItem("To Deck A")) {
                        mixer.deckA.reset()
                        PatchManager.activePresetA = null
                        PatchManager.cachedDtoA = null
                    }
                    if (ImGui.menuItem("To Deck B")) {
                        mixer.deckB.reset()
                        PatchManager.activePresetB = null
                        PatchManager.cachedDtoB = null
                    }
                    if (ImGui.menuItem("To Deck C")) {
                        mixer.deckC.reset()
                        PatchManager.activePresetC = null
                        PatchManager.cachedDtoC = null
                    }
                    ImGui.endMenu()
                }
                ImGui.separator()
                if (ImGui.menuItem("Exit")) {
                    logger.info { "Exit clicked" }
                    onTriggerExitFlow()
                }
                ImGui.endMenu()
            }

            if (UITheme.randomizationEnabled) {
                if (ImGui.beginMenu("Randomize")) {
                    if (ImGui.selectable("All", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                        PatchGridUndo.pushUndoState(patchState, mixer)
                        mixer.deckA.randomizeModulators()
                        mixer.deckB.randomizeModulators()
                        mixer.deckC.randomizeModulators()
                        listOf(mixer.crossfade, mixer.masterAlpha).forEach { param ->
                            val randomized = param.modulators.map { it.randomizeActiveValues() }
                            param.modulators.clear()
                            param.modulators.addAll(randomized)
                            param.randomizeBaseValue()
                        }
                    }
                    if (ImGui.selectable("Deck A", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                        PatchGridUndo.pushUndoState(patchState, mixer)
                        mixer.deckA.randomizeModulators()
                    }
                    if (ImGui.selectable("Deck B", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                        PatchGridUndo.pushUndoState(patchState, mixer)
                        mixer.deckB.randomizeModulators()
                    }
                    if (ImGui.selectable("Deck C", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                        PatchGridUndo.pushUndoState(patchState, mixer)
                        mixer.deckC.randomizeModulators()
                    }
                    ImGui.endMenu()
                }
            }


            // MIDI Map toggle button
            val isMidiLearn = patchState.isMidiLearnMode
            if (isMidiLearn) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f) // orange
            }
            if (ImGui.menuItem("MIDI Map", "", isMidiLearn)) {
                patchState.isMidiLearnMode = !isMidiLearn
                if (!patchState.isMidiLearnMode) {
                    patchState.midiLearnTarget = null
                } else {
                    if (MidiEngine.getActiveDeviceCount() == 0) {
                        popupManager.pendingOpenMidiWarningPopup = true
                    }
                }
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Toggle MIDI Learn mode. Click a control, then move a knob/fader on your controller to bind it.")
            }
            if (isMidiLearn) {
                ImGui.popStyleColor()
            }

            if (ImGui.menuItem("Settings")) {
                onOpenSettings()
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Configure interface scaling, JACK settings, startup behavior, and MIDI profiles.")
            }

            val isAudioActive = llm.slop.liquidlsd.audio.AudioEngine.isActive()
            if (!isAudioActive && UITheme.audioEngineEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f) // orange warning
            }
            val audioEngineLabel = if (!isAudioActive && UITheme.audioEngineEnabled) "Audio Engine [!]" else "Audio Engine"
            if (ImGui.menuItem(audioEngineLabel)) {
                onOpenAudioEngineMonitor()
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("View real-time input waveforms, estimated BPM, and sound-derived modulation signals.")
            }
            if (!isAudioActive && UITheme.audioEngineEnabled) {
                ImGui.popStyleColor()
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("Documentation")) {
                    DocManager.openDocumentation()
                }
                ImGui.endMenu()
            }

            val tooltipsEnabled = UITheme.tooltipsEnabled
            if (tooltipsEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 0.2f, 1.0f) // green
            } else {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.2f, 0.2f, 1.0f) // red
            }
            if (ImGui.menuItem("Tooltips", "", tooltipsEnabled)) {
                UITheme.tooltipsEnabled = !tooltipsEnabled
                UITheme.saveSettings()
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Toggle visibility of helpful on-hover tooltips across the application.")
            }
            ImGui.popStyleColor()

            val showPerf = UITheme.showPerformanceOverlay
            if (showPerf) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 0.2f, 1.0f) // green
            } else {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.2f, 0.2f, 1.0f) // red
            }
            if (ImGui.menuItem("Perf Overlay", "", showPerf)) {
                UITheme.showPerformanceOverlay = !showPerf
                UITheme.saveSettings()
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Toggle the real-time frame timing and audio latency budget overlay window.")
            }
            ImGui.popStyleColor()

            // ── Right-aligned performance stats ──────────────────────────────────
            drawPerformanceStats()

            ImGui.endMainMenuBar()
        }
    }

    /**
     * Renders FPS, frame time, CPU%, and BPM right-aligned inside the main menu bar.
     * Each metric is colourised: green = healthy, yellow = marginal, red = problematic.
     * Zero allocations per frame (all formatting is done with pre-allocated StringBuilder).
     */
    private fun drawPerformanceStats() {
        val fps        = PerformanceStats.fps
        val ftMs       = PerformanceStats.frameTimeMs
        val cpuFrac    = PerformanceStats.processCpuFraction   // -1 if unavailable
        val bpm        = PerformanceStats.bpm
        val audioActive = AudioEngine.isActive()

        val cpuText = if (cpuFrac >= 0.0) "CPU: %2.0f%%  ".format(cpuFrac * 100.0) else ""
        val bpmText = if (audioActive && UITheme.audioEngineEnabled) "BPM: %3.0f  ".format(bpm) else ""
        val fpsText = "%3.0f fps  ".format(fps)
        val ftText  = "%.0f ms  ".format(ftMs)
        val fullLabel = cpuText + bpmText + fpsText + ftText

        UITheme.withFont(UITheme.FontLevel.CODE) {
            val barWidth  = ImGui.getContentRegionAvailX()
            val textWidth = ImGui.calcTextSize(fullLabel).x
            val startX    = ImGui.getCursorPosX() + barWidth - textWidth

            if (startX > ImGui.getCursorPosX()) {
                ImGui.setCursorPosX(startX)
            }

            // ── CPU % ──────────────────────────────────────────────────────────────
            if (cpuFrac >= 0.0) {
                val cpuPct = cpuFrac * 100.0
                when {
                    cpuPct >= 80.0 -> ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.25f, 0.25f, 1.0f) // red
                    cpuPct >= 50.0 -> ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.75f, 0.0f,  1.0f) // yellow
                    else           -> ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 1.0f, 0.55f, 1.0f) // green
                }
                ImGui.text(cpuText)
                ImGui.popStyleColor()
                ImGui.sameLine(0f, 0f)
            }

            // ── BPM ───────────────────────────────────────────────────────────────
            if (audioActive && UITheme.audioEngineEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.85f, 1.0f, 1.0f) // light blue
                ImGui.text(bpmText)
                ImGui.popStyleColor()
                ImGui.sameLine(0f, 0f)
            }

            // ── FPS ───────────────────────────────────────────────────────────────
            val maxFpsConfig = UITheme.maxFps
            val (fpsRed, fpsYellow) = if (maxFpsConfig <= 30) {
                20f to 27f
            } else {
                30f to 50f
            }
            when {
                fps < fpsRed    -> ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.25f, 0.25f, 1.0f) // red
                fps < fpsYellow -> ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.75f, 0.0f,  1.0f) // yellow
                else            -> ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 1.0f, 0.55f, 1.0f) // green
            }
            ImGui.text(fpsText)
            ImGui.popStyleColor()
            ImGui.sameLine(0f, 0f)

            // ── Frame time ────────────────────────────────────────────────────────
            val (ftRed, ftYellow) = if (maxFpsConfig <= 30) {
                50.0f to 37.0f
            } else {
                33.3f to 20.0f
            }
            when {
                ftMs > ftRed    -> ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.25f, 0.25f, 1.0f) // red
                ftMs > ftYellow -> ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.75f, 0.0f,  1.0f) // yellow
                else            -> ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 1.0f, 0.55f, 1.0f) // green
            }
            ImGui.text(ftText)
            ImGui.popStyleColor()
        }
    }
}
