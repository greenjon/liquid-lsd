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
    private val isOutputWindowOpen: () -> Boolean = { false },
    private val windowFrameController: WindowFrameController? = null
) {
    private val logger = KotlinLogging.logger {}

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.beginMainMenuBar()) {
                // ── App Brand / Logo ─────────────────────────────────────────────────
                session.uiTheme.withFont(UITheme.FontLevel.H3) {
                    ImGui.textColored(0.2f, 0.8f, 1.0f, 1.0f, "${Icons.ACTIVITY} Liquid LSD")
                }
                ImGui.sameLine(0f, 10f)

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
                    ImGui.separator()
                    if (ImGui.menuItem("Settings...")) {
                        onOpenSettings()
                    }
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip("Configure interface scaling, JACK settings, startup behavior, and MIDI profiles.")
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Exit")) {
                        logger.info { "Exit clicked" }
                        onTriggerExitFlow()
                    }
                    ImGui.endMenu()
                }

                // ── Output Menu ──────────────────────────────────────────────────────
                val isOutOpen = isOutputWindowOpen()
                val isRec = llm.slop.liquidlsd.export.RealtimeRecorder.isRecording
                val broadcastState = llm.slop.liquidlsd.broadcast.BroadcastEngine.connectionState
                val isBroadcasting = broadcastState == llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTED ||
                    broadcastState == llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTING

                if (ImGui.beginMenu("Output")) {
                    if (ImGui.menuItem("Secondary Output Window", "", isOutOpen)) {
                        onToggleOutputWindow()
                    }
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip("Toggle secondary / external video output window (e.g. for projector or OBS window capture).")
                    }

                    if (ImGui.menuItem("Record Master Output (REC)", "Ctrl+R", isRec)) {
                        if (isRec) {
                            llm.slop.liquidlsd.export.RealtimeRecorder.stopRecording()
                        } else {
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
                    }
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        val audioTxt = if (session.uiTheme.recordingIncludeAudio) "with audio" else "video only"
                        ImGui.setTooltip("Toggle live master output recording.\nFolder: ${session.uiTheme.getDefaultVideosDirectory().absolutePath}\nSettings: ${session.uiTheme.recordingFps} FPS @ ${session.uiTheme.recordingBitrateMbps} Mbps ($audioTxt)")
                    }

                    if (ImGui.menuItem("Web Broadcast", "", isBroadcasting)) {
                        if (isBroadcasting) {
                            llm.slop.liquidlsd.broadcast.BroadcastEngine.stopBroadcast()
                        } else {
                            llm.slop.liquidlsd.broadcast.BroadcastEngine.startBroadcast(mixer)
                        }
                    }
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip("Connect and broadcast live session state to the Web TV client.")
                    }

                    ImGui.separator()
                    if (ImGui.menuItem("Export Video (Offline Studio)...")) {
                        VideoExportModal.open()
                    }
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip("Render high-quality offline video with precise per-frame timing.")
                    }
                    ImGui.endMenu()
                }

                // ── Live Video Recording HUD (visible only when actively recording) ──
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
                }

                // ── Web Broadcast Status Pill (visible only when active/connecting/error) ─
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
                        // Inactive: hidden from top-level bar to reduce clutter
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

                if (ImGui.menuItem("Color", "", ColorTunerPanel.isOpen)) {
                    ColorTunerPanel.toggle()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Open live Theme Color Tuner to adjust element colors in real-time.")
                }

                if (ImGui.beginMenu("Help")) {
                    if (ImGui.menuItem("Documentation")) {
                        DocManager.openDocumentation()
                    }
                    ImGui.separator()
                    val tooltipsEnabled = session.uiTheme.tooltipsEnabled
                    if (ImGui.menuItem("Show Tooltips", "", tooltipsEnabled)) {
                        session.uiTheme.tooltipsEnabled = !tooltipsEnabled
                        session.uiTheme.saveSettings()
                    }
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip("Toggle visibility of helpful on-hover tooltips across the application.")
                    }
                    ImGui.endMenu()
                }

                // ── Right-aligned performance stats & window controls ────────────────
                drawPerformanceStatsAndControls(session)

                ImGui.endMainMenuBar()
            }
        }
    }

    /**
     * Renders empty drag zone, telemetry stats (FPS, frame time, CPU%, BPM, DSP), and
     * custom window control buttons (Minimize, Maximize/Restore, Close) when running in frameless mode.
     */
    private fun drawPerformanceStatsAndControls(session: llm.slop.liquidlsd.SessionContext) {
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

        val isFrameless = session.uiTheme.framelessWindow && windowFrameController != null
        val btnW = (24f * fontScale).coerceIn(24f, 40f)
        val btnH = ImGui.getFrameHeight()
        val windowBtnsW = if (isFrameless) (btnW * 3f) + (4f * 2f) + 12f else 0f

        session.uiTheme.withFont(UITheme.FontLevel.CODE) {
            val barWidth  = ImGui.getContentRegionAvailX()
            val textWidth = ImGui.calcTextSize(fullLabel).x
            val totalRightW = textWidth + dotsTotalW + windowBtnsW
            val startX    = ImGui.getCursorPosX() + barWidth - totalRightW

            // ── Top Bar Center Drag Region ───────────────────────────────────────────
            val currentX = ImGui.getCursorPosX()
            val dragWidth = (startX - currentX - 8f).coerceAtLeast(0f)
            if (dragWidth > 5f) {
                ImGui.invisibleButton("##header_drag_region", dragWidth, btnH)
                val isHovered = ImGui.isItemHovered()
                val isDoubleClicked = ImGui.isMouseDoubleClicked(0) && isHovered
                windowFrameController?.onTopBarInteraction(isHovered, isDoubleClicked)
                ImGui.sameLine(0f, 8f)
            }

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
                if (ImGui.isItemClicked()) {
                    onOpenAudioEngineMonitor()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Audio Engine BPM: estimated tempo.\nClick to open Audio Engine settings.")
                }
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
                if (ImGui.isItemClicked()) {
                    onOpenAudioEngineMonitor()
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Audio callback DSP execution time.\nClick to open Audio Engine settings.")
                }
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

            // ── Custom Window Controls (Frameless CSD Mode) ──────────────────────────
            if (windowFrameController != null && session.uiTheme.framelessWindow) {
                ImGui.sameLine(0f, 8f)
                session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                    // Minimize
                    if (ImGui.button("${Icons.MINUS}##win_min", btnW, btnH)) {
                        windowFrameController.minimize()
                    }
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip("Minimize")
                    }

                    ImGui.sameLine(0f, 2f)

                    // Maximize / Restore
                    val isMax = windowFrameController.isMaximized()
                    val maxIcon = if (isMax) Icons.COPY else Icons.SQUARE
                    if (ImGui.button("$maxIcon##win_max", btnW, btnH)) {
                        windowFrameController.toggleMaximize()
                    }
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip(if (isMax) "Restore" else "Maximize")
                    }

                    ImGui.sameLine(0f, 2f)

                    // Close
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.85f, 0.15f, 0.15f, 1.0f)
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.70f, 0.10f, 0.10f, 1.0f)
                    if (ImGui.button("${Icons.X}##win_close", btnW, btnH)) {
                        onTriggerExitFlow()
                    }
                    ImGui.popStyleColor(2)
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip("Close Liquid LSD")
                    }
                }
            }
        }
    }
}
