package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.PresetManager
import mu.KotlinLogging
import llm.slop.liquidlsd.midi.MidiEngine
import llm.slop.liquidlsd.audio.AudioEngine

class MenuBar(
    private val popupManager: PopupManager,
    private val presetState: PresetGridState,
    private val onTriggerExitFlow: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onOpenAudioEngineMonitor: () -> Unit,
    private val onToggleOutputWindow: () -> Unit = {},
    private val isOutputWindowOpen: () -> Boolean = { false }
) {
    private val logger = KotlinLogging.logger {}

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.beginMainMenuBar()) {
                if (ImGui.beginMenu("File")) {
                    if (ImGui.beginMenu("New Preset")) {
                        if (ImGui.menuItem("To Deck A")) {
                            UIManager.newPresetSafely(mixer, mixer.deckA)
                        }
                        if (ImGui.menuItem("To Deck B")) {
                            UIManager.newPresetSafely(mixer, mixer.deckB)
                        }
                        if (ImGui.menuItem("To Deck BG")) {
                            UIManager.newPresetSafely(mixer, mixer.deckBG)
                        }
                        if (ImGui.menuItem("To Deck PV")) {
                            UIManager.newPresetSafely(mixer, mixer.deckPV)
                        }
                        ImGui.endMenu()
                    }
                    if (ImGui.menuItem("Export Video (Offline Studio)...")) {
                        VideoExportModal.open()
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Exit")) {
                        logger.info { "Exit clicked" }
                        onTriggerExitFlow()
                    }
                    ImGui.endMenu()
                }

                // ── Live Video Recording & Dropped Frames HUD ────────────────────────
                val isRec = llm.slop.liquidlsd.export.RealtimeRecorder.isRecording
                if (isRec) {
                    val elapsed = llm.slop.liquidlsd.export.RealtimeRecorder.elapsedSeconds.toInt()
                    val mins = elapsed / 60
                    val secs = elapsed % 60
                    val sizeMb = llm.slop.liquidlsd.export.RealtimeRecorder.fileSizeBytes / (1024f * 1024f)
                    val dropped = llm.slop.liquidlsd.export.RealtimeRecorder.droppedFramesCount
                    val dropPct = llm.slop.liquidlsd.export.RealtimeRecorder.droppedPercentage

                    ImGui.pushStyleColor(ImGuiCol.Button, 0.85f, 0.15f, 0.15f, 1.0f)
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.95f, 0.25f, 0.25f, 1.0f)
                    if (ImGui.button("REC %02d:%02d (%.1fMB)".format(mins, secs, sizeMb))) {
                        llm.slop.liquidlsd.export.RealtimeRecorder.stopRecording()
                    }
                    ImGui.popStyleColor(2)
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Click to stop recording and finalize video file.")
                    }

                    ImGui.sameLine(0f, 4f)
                    if (dropped > 0) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.35f, 0.35f, 1.0f)
                    } else {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.45f, 0.95f, 0.45f, 1.0f)
                    }
                    ImGui.text("Drop: %d (%.1f%%)".format(dropped, dropPct))
                    ImGui.popStyleColor()
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Dropped frame indicator: 0 drops means silky-smooth 60fps recording.")
                    }
                } else {
                    if (ImGui.menuItem("REC")) {
                        val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
                        val recDir = session.uiTheme.getDefaultVideosDirectory()
                        val outFile = java.io.File(recDir, "liquid_lsd_$dateStr.mp4")
                        llm.slop.liquidlsd.export.RealtimeRecorder.startRecording(
                            outputFile = outFile,
                            width = mixer.width,
                            height = mixer.height,
                            fps = session.uiTheme.recordingFps,
                            bitrateMbps = session.uiTheme.recordingBitrateMbps,
                            includeAudio = session.uiTheme.recordingIncludeAudio
                        )
                    }
                    if (ImGui.isItemHovered()) {
                        val audioTxt = if (session.uiTheme.recordingIncludeAudio) "with audio" else "video only"
                        ImGui.setTooltip("Start live master output recording (Hotkey: Ctrl+R)\nFolder: ${session.uiTheme.getDefaultVideosDirectory().absolutePath}\nSettings: ${session.uiTheme.recordingFps} FPS @ ${session.uiTheme.recordingBitrateMbps} Mbps ($audioTxt)")
                    }
                }

                // ── Web Broadcast Status & Quick Toggle ──────────────────────────────
                val broadcastState = llm.slop.liquidlsd.broadcast.BroadcastEngine.connectionState
                when (broadcastState) {
                    llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTED -> {
                        ImGui.pushStyleColor(ImGuiCol.Button, 0.15f, 0.65f, 0.25f, 1.0f)
                        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.25f, 0.75f, 0.35f, 1.0f)
                        if (ImGui.button("${Icons.ACTIVITY} LIVE")) {
                            llm.slop.liquidlsd.broadcast.BroadcastEngine.stopBroadcast()
                        }
                        ImGui.popStyleColor(2)
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("Broadcasting live session state to Web TV client.\nClick to stop.")
                        }
                    }
                    llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTING -> {
                        ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.7f, 0.15f, 1.0f)
                        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.8f, 0.25f, 1.0f)
                        if (ImGui.button("${Icons.REFRESH} CONNECTING")) {
                            llm.slop.liquidlsd.broadcast.BroadcastEngine.stopBroadcast()
                        }
                        ImGui.popStyleColor(2)
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("Connecting to relay server...\nClick to cancel.")
                        }
                    }
                    llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.ERROR -> {
                        ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 1.0f)
                        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 1.0f)
                        if (ImGui.button("${Icons.ALERT} LIVE ERR")) {
                            llm.slop.liquidlsd.broadcast.BroadcastEngine.startBroadcast(mixer)
                        }
                        ImGui.popStyleColor(2)
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("Broadcast error: ${llm.slop.liquidlsd.broadcast.BroadcastEngine.lastError ?: "Failed"}\nClick to retry.")
                        }
                    }
                    llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.DISCONNECTED -> {
                        if (ImGui.menuItem("Broadcast", "", false)) {
                            llm.slop.liquidlsd.broadcast.BroadcastEngine.startBroadcast(mixer)
                        }
                        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                            ImGui.setTooltip("Connect and start broadcasting live state to the Web TV client.")
                        }
                    }
                }

                if (session.uiTheme.randomizationEnabled) {
                    if (ImGui.beginMenu("Randomize")) {
                        if (ImGui.selectable("All", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                            PresetGridUndo.pushUndoState(presetState, mixer)
                            mixer.randomizeAll()
                        }
                        if (ImGui.selectable("Deck A", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                            PresetGridUndo.pushUndoState(presetState, mixer)
                            mixer.randomizeDeckA()
                        }
                        if (ImGui.selectable("Deck B", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                            PresetGridUndo.pushUndoState(presetState, mixer)
                            mixer.randomizeDeckB()
                        }
                        if (ImGui.selectable("Deck BG", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                            PresetGridUndo.pushUndoState(presetState, mixer)
                            mixer.randomizeDeckBG()
                        }
                        if (ImGui.selectable("Deck PV", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                            PresetGridUndo.pushUndoState(presetState, mixer)
                            mixer.randomizeDeckPV()
                        }
                        ImGui.endMenu()
                    }
                }


                // MIDI Map toggle button
                val isMidiLearn = presetState.isMidiLearnMode
                if (isMidiLearn) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f) // orange
                }
                if (ImGui.menuItem("MIDI Map", "", isMidiLearn)) {
                    presetState.isMidiLearnMode = !isMidiLearn
                    if (!presetState.isMidiLearnMode) {
                        presetState.midiLearnTarget = null
                    } else {
                        if (MidiEngine.getActiveDeviceCount() == 0) {
                            popupManager.pendingOpenMidiWarningPopup = true
                        }
                    }
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Toggle MIDI Learn mode. Click a control, then move a knob/fader on your controller to bind it.")
                }
                if (isMidiLearn) {
                    ImGui.popStyleColor()
                }

                if (ImGui.menuItem("Settings")) {
                    onOpenSettings()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Configure interface scaling, JACK settings, startup behavior, and MIDI profiles.")
                }

                val isAudioActive = session.audioEngine.isActive()
                if (!isAudioActive && session.uiTheme.audioEngineEnabled) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f) // orange warning
                }
                val audioEngineLabel = if (!isAudioActive && session.uiTheme.audioEngineEnabled) "Audio Engine [!]" else "Audio Engine"
                if (ImGui.menuItem(audioEngineLabel)) {
                    onOpenAudioEngineMonitor()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("View real-time input waveforms, estimated BPM, and sound-derived modulation signals.")
                }
                if (!isAudioActive && session.uiTheme.audioEngineEnabled) {
                    ImGui.popStyleColor()
                }

                if (ImGui.menuItem("Color", "", ColorTunerPanel.isOpen)) {
                    ColorTunerPanel.toggle()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Open live Theme Color Tuner to adjust element colors in real-time.")
                }

                val isOutOpen = isOutputWindowOpen()
                if (ImGui.menuItem("Output Window", "", isOutOpen)) {
                    onToggleOutputWindow()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Toggle secondary / external video output window (e.g. for projector or OBS window capture).")
                }

                if (ImGui.beginMenu("Help")) {
                    if (ImGui.menuItem("Documentation")) {
                        DocManager.openDocumentation()
                    }
                    ImGui.endMenu()
                }

                val tooltipsEnabled = session.uiTheme.tooltipsEnabled
                if (tooltipsEnabled) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 0.2f, 1.0f) // green
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.2f, 0.2f, 1.0f) // red
                }
                if (ImGui.menuItem("Tooltips", "", tooltipsEnabled)) {
                    session.uiTheme.tooltipsEnabled = !tooltipsEnabled
                    session.uiTheme.saveSettings()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Toggle visibility of helpful on-hover tooltips across the application.")
                }
                ImGui.popStyleColor()

                // ── Right-aligned performance stats ──────────────────────────────────
                drawPerformanceStats(session)

                ImGui.endMainMenuBar()
            }
        }
    }

    /**
     * Renders FPS, frame time, CPU%, and BPM right-aligned inside the main menu bar.
     * Each metric is colourised: green = healthy, yellow = marginal, red = problematic.
     * Zero allocations per frame (all formatting is done with pre-allocated StringBuilder).
     */
    private fun drawPerformanceStats(session: llm.slop.liquidlsd.SessionContext) {
        val fps        = PerformanceStats.fps
        val ftMs       = PerformanceStats.frameTimeMs
        val cpuFrac    = PerformanceStats.processCpuFraction   // -1 if unavailable
        val bpm        = PerformanceStats.bpm
        val audioActive = session.audioEngine.isActive()
        val audioLatency = PerformanceStats.audioCallbackMs
        val showAudio = audioActive && session.uiTheme.audioEngineEnabled && audioLatency > 0.0f
        val showBeatDots = audioActive && session.uiTheme.audioEngineEnabled

        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
        val dotR = 3.5f * fontScale
        val dotGap = 7f * fontScale
        val dotsTotalW = if (showBeatDots) (dotR * 2f * 4f) + (dotGap * 3f) + (10f * fontScale) else 0f

        val cpuText = if (cpuFrac >= 0.0) "CPU: %2.0f%%  ".format(cpuFrac * 100.0) else ""
        val bpmText = if (audioActive && session.uiTheme.audioEngineEnabled) "BPM: %3.0f  ".format(bpm) else ""
        val dspText = if (showAudio) "DSP: %.2fms  ".format(audioLatency) else ""
        val fpsText = "%3.0f fps  ".format(fps)
        val ftText  = "%3.0f ms  ".format(ftMs)
        val fullLabel = cpuText + bpmText + dspText + fpsText + ftText

        session.uiTheme.withFont(UITheme.FontLevel.CODE) {
            val barWidth  = ImGui.getContentRegionAvailX()
            val textWidth = ImGui.calcTextSize(fullLabel).x
            val startX    = ImGui.getCursorPosX() + barWidth - textWidth - dotsTotalW

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

            // ── 4-Beat Phase Meter ────────────────────────────────────────────────
            if (showBeatDots) {
                val totalBeats = llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats()
                val currentBeat = (((totalBeats.toLong() % 4) + 4) % 4).toInt()
                val beatFract = (totalBeats - kotlin.math.floor(totalBeats)).toFloat()

                val dotsStartX = ImGui.getCursorScreenPosX()
                val dotsStartY = ImGui.getCursorScreenPosY()
                val dl = ImGui.getWindowDrawList()
                val textH = ImGui.getTextLineHeight()
                val cy = dotsStartY + (textH * 0.5f)

                for (i in 0..3) {
                    val cx = dotsStartX + dotR + (i * (dotR * 2f + dotGap))
                    if (i == currentBeat) {
                        val intensity = (1.0f - beatFract * 0.35f).coerceIn(0.65f, 1.0f)
                        val col = if (i == 0) {
                            ImGui.colorConvertFloat4ToU32(0.2f * intensity, 0.95f * intensity, 1.0f * intensity, 1.0f)
                        } else {
                            ImGui.colorConvertFloat4ToU32(0.85f * intensity, 0.95f * intensity, 0.85f * intensity, 1.0f)
                        }
                        dl.addCircleFilled(cx, cy, dotR, col)
                    } else {
                        val dimCol = ImGui.colorConvertFloat4ToU32(0.40f, 0.45f, 0.50f, 0.6f)
                        dl.addCircle(cx, cy, dotR, dimCol, 0, 1.2f)
                    }
                }

                ImGui.invisibleButton("##beat_phase_meter", dotsTotalW - (4f * fontScale), textH)
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Beat Phase (4/4 Bar Sync)\nBeat ${currentBeat + 1} of 4")
                }
                ImGui.sameLine(0f, 4f * fontScale)
            }

            // ── BPM ───────────────────────────────────────────────────────────────
            if (audioActive && session.uiTheme.audioEngineEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.85f, 1.0f, 1.0f) // light blue
                ImGui.text(bpmText)
                ImGui.popStyleColor()
                ImGui.sameLine(0f, 0f)
            }

            // ── DSP Latency ───────────────────────────────────────────────────────
            if (showAudio) {
                when {
                    audioLatency >= 5.0f -> ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.25f, 0.25f, 1.0f) // red
                    audioLatency >= 2.0f -> ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.75f, 0.0f,  1.0f) // yellow
                    else                 -> ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 1.0f, 0.55f, 1.0f) // green
                }
                ImGui.text(dspText)
                ImGui.popStyleColor()
                ImGui.sameLine(0f, 0f)
            }

            // ── FPS ───────────────────────────────────────────────────────────────
            val maxFpsConfig = session.uiTheme.maxFps
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
