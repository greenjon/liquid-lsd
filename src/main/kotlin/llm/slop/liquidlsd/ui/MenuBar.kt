package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.PresetManager
import mu.KotlinLogging

class MenuBar(
    private val popupManager: PopupManager,
    private val parametersState: ParametersState,
    private val onTriggerExitFlow: () -> Unit,
    private val onOpenPreferences: () -> Unit,
    private val onOpenAudioEngineMonitor: () -> Unit,
    private val onToggleOutputWindow: () -> Unit = {},
    private val isOutputWindowOpen: () -> Boolean = { false },
    private val windowFrameController: WindowFrameController? = null
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        fun calculateHeight(session: llm.slop.liquidlsd.SessionContext): Float {
            val baseH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFrameHeight() }
            return baseH * 1.5f
        }

        fun calculateFramePaddingY(session: llm.slop.liquidlsd.SessionContext): Float {
            val targetH = calculateHeight(session)
            val fontSize = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFontSize() }
            return ((targetH - fontSize) * 0.5f).coerceAtLeast(0f)
        }
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        val padY = calculateFramePaddingY(session)
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), padY)
        try {
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.beginMainMenuBar()) {
                    // ── App Brand / Logo ─────────────────────────────────────────────────
                    session.uiTheme.withFont(UITheme.FontLevel.H3) {
                        ImGui.textColored(0.2f, 0.8f, 1.0f, 1.0f, "${Icons.ACTIVITY} Liquid LSD")
                    }
                    ImGui.sameLine(0f, 10f)

                    drawPerformanceTabStrip(session, mixer)
                    ImGui.sameLine(0f, 10f)

                    if (ImGui.beginMenu("File")) {
                        if (ImGui.beginMenu("New Preset")) {
                            if (ImGui.menuItem("To Deck A")) {
                                UIManager.newPresetSafely(mixer, mixer.deckA)
                            }
                            if (ImGui.menuItem("To Deck B")) {
                                UIManager.newPresetSafely(mixer, mixer.deckB)
                            }
                            if (ImGui.menuItem("To Deck Background")) {
                                UIManager.newPresetSafely(mixer, mixer.deckBG)
                            }
                            if (ImGui.menuItem("To Deck Preview")) {
                                UIManager.newPresetSafely(mixer, mixer.deckPV)
                            }
                            ImGui.endMenu()
                        }
                        if (ImGui.menuItem("Restore Factory Presets...")) {
                            popupManager.pendingOpenRestoreDefaultsPopup = true
                        }
                        itemTooltip("Restore missing factory presets and playlists from the app bundle.\nExisting custom presets will not be overwritten.")
                        ImGui.separator()
                        if (ImGui.menuItem("Preferences...", "Ctrl+P", PreferencesPanel.isOpen)) {
                            onOpenPreferences()
                        }
                        itemTooltip("Configure interface scaling, JACK preferences, startup behavior, and MIDI profiles.")
                        ImGui.separator()
                        if (ImGui.menuItem("Exit")) {
                            logger.info { "Exit clicked" }
                            onTriggerExitFlow()
                        }
                        ImGui.endMenu()
                    }

                    // ── View Menu ─────────────────────────────────────────────────────────
                    if (ImGui.beginMenu("View")) {
                        if (ImGui.menuItem("Close Preferences", "Esc", false, PreferencesPanel.isOpen)) {
                            PreferencesPanel.close()
                        }
                        if (ImGui.menuItem("Close Edit", "Esc", false, session.parametersState.anyRackModuleExpanded())) {
                            session.parametersState.collapseAllRackModules()
                        }
                        ImGui.separator()
                        if (ImGui.beginMenu("Library")) {
                            // Picking a size always lands on the Library: leave Edit view first (it hides the Library).
                            if (ImGui.menuItem("Full", "", session.uiTheme.libraryMode == UITheme.LibraryMode.FULL)) {
                                LibraryPanel.show(session)
                                session.uiTheme.libraryMode = UITheme.LibraryMode.FULL
                                AppPreferencesStore.savePreferences()
                            }
                            if (ImGui.menuItem("Half", "", session.uiTheme.libraryMode == UITheme.LibraryMode.HALF && !LibraryPanel.isEditView(session))) {
                                LibraryPanel.show(session)
                                session.uiTheme.libraryMode = UITheme.LibraryMode.HALF
                                AppPreferencesStore.savePreferences()
                            }
                            ImGui.endMenu()
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
                        itemTooltip("Toggle secondary / external video output window (e.g. for projector or OBS window capture).")

                        if (ImGui.menuItem("Record Output (REC)", "Ctrl+R", isRec)) {
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
                        itemTooltip("Toggle live master output recording.")

                        if (llm.slop.liquidlsd.broadcast.BroadcastPreferences.isConfigured) {
                            if (ImGui.menuItem("Web Broadcast", "", isBroadcasting)) {
                                if (isBroadcasting) {
                                    llm.slop.liquidlsd.broadcast.BroadcastEngine.stopBroadcast()
                                } else {
                                    llm.slop.liquidlsd.broadcast.BroadcastEngine.startBroadcast(mixer)
                                }
                            }
                            itemTooltip("Connect and broadcast live session state to the Web TV client.")
                        }

                        ImGui.separator()
                        if (ImGui.menuItem("Render Video (Offline)...")) {
                            VideoExportModal.open()
                        }
                        itemTooltip("Render high-quality offline video with precise per-frame timing.")
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

                        val recCol = TangoPalette.DANGER
                        ImGui.pushStyleColor(ImGuiCol.Button, recCol.normal[0], recCol.normal[1], recCol.normal[2], 1.0f)
                        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, recCol.light[0], recCol.light[1], recCol.light[2], 1.0f)
                        if (ButtonChrome.button("REC %02d:%02d (%.1fMB)".format(mins, secs, sizeMb))) {
                            llm.slop.liquidlsd.export.RealtimeRecorder.stopRecording()
                        }
                        ImGui.popStyleColor(2)
                        itemTooltip("Click to stop recording and finalize video file.")

                        ImGui.sameLine(0f, 4f)
                        if (dropped > 0) {
                            val c = TangoPalette.DANGER.light
                            ImGui.pushStyleColor(ImGuiCol.Text, c[0], c[1], c[2], 1.0f)
                        } else {
                            val c = TangoPalette.ACTIVE.light
                            ImGui.pushStyleColor(ImGuiCol.Text, c[0], c[1], c[2], 1.0f)
                        }
                        ImGui.textUnformatted("Drop: %d (%.1f%%)".format(dropped, dropPct))
                        ImGui.popStyleColor()
                        itemTooltip("Dropped frame indicator: 0 drops means silky-smooth 60fps recording.")
                    }

                    // ── Web Broadcast Status Pill (visible only when active/connecting/error) ─
                    when (broadcastState) {
                        llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTED -> {
                            val c = TangoPalette.ACTIVE
                            val ink = TangoPalette.inkFor(c.dark, c.normal)
                            ImGui.pushStyleColor(ImGuiCol.Button, c.dark[0], c.dark[1], c.dark[2], 1.0f)
                            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, c.normal[0], c.normal[1], c.normal[2], 1.0f)
                            ImGui.pushStyleColor(ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
                            if (ButtonChrome.button("${Icons.ACTIVITY} LIVE")) {
                                llm.slop.liquidlsd.broadcast.BroadcastEngine.stopBroadcast()
                            }
                            ImGui.popStyleColor(3)
                            itemTooltip("Broadcasting live session state to Web TV client.\nClick to stop.")
                        }
                        llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.CONNECTING -> {
                            val c = TangoPalette.ALERT
                            val ink = TangoPalette.inkFor(c.dark, c.normal)
                            ImGui.pushStyleColor(ImGuiCol.Button, c.dark[0], c.dark[1], c.dark[2], 1.0f)
                            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, c.normal[0], c.normal[1], c.normal[2], 1.0f)
                            ImGui.pushStyleColor(ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
                            if (ButtonChrome.button("${Icons.REFRESH} CONNECTING")) {
                                llm.slop.liquidlsd.broadcast.BroadcastEngine.stopBroadcast()
                            }
                            ImGui.popStyleColor(3)
                            itemTooltip("Connecting to relay server...\nClick to cancel.")
                        }
                        llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.ERROR -> {
                            val c = TangoPalette.DANGER
                            ImGui.pushStyleColor(ImGuiCol.Button, c.dark[0], c.dark[1], c.dark[2], 1.0f)
                            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, c.normal[0], c.normal[1], c.normal[2], 1.0f)
                            if (ButtonChrome.button("${Icons.ALERT} LIVE ERR")) {
                                llm.slop.liquidlsd.broadcast.BroadcastEngine.startBroadcast(mixer)
                            }
                            ImGui.popStyleColor(2)
                            itemTooltip("Broadcast error: ${llm.slop.liquidlsd.broadcast.BroadcastEngine.lastError ?: "Failed"}\nClick to retry.")
                        }
                        llm.slop.liquidlsd.broadcast.BroadcastEngine.ConnectionState.DISCONNECTED -> {
                            // Inactive: hidden from top-level bar to reduce clutter
                        }
                    }

                    // ── ISF Scanner Status Pill (visible while background scan is running) ──
                    if (llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.isScanning) {
                        val progress = (llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanProgress * 100f).toInt()
                        val currentPath = llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanCurrentPath
                        val scanInk = TangoPalette.inkFor(TangoPalette.SYNC.normal, TangoPalette.SYNC.bright)
                        ImGui.pushStyleColor(ImGuiCol.Button, TangoPalette.SYNC.normal[0], TangoPalette.SYNC.normal[1], TangoPalette.SYNC.normal[2], 0.9f)
                        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, TangoPalette.SYNC.bright[0], TangoPalette.SYNC.bright[1], TangoPalette.SYNC.bright[2], 1.0f)
                        ImGui.pushStyleColor(ImGuiCol.Text, scanInk[0], scanInk[1], scanInk[2], 1.0f)
                        ButtonChrome.button("${Icons.REFRESH} SCANNING ($progress%)")
                        ImGui.popStyleColor(3)
                        itemTooltip("Scanning ISF Shaders ($progress% complete)\n${if (currentPath.isNotEmpty()) currentPath else "Indexing library..."}")
                    }

                    // ── Ableton Link Status Pill (visible only when enabled) ─────────
                    val linkEngine = llm.slop.liquidlsd.link.AbletonLinkEngine
                    val syncManager = llm.slop.liquidlsd.link.LinkSyncManager

                    if (linkEngine.isEnabled) {
                        val peers = linkEngine.getNumPeers()
                        val peerText = if (peers == 1) "1 peer" else "$peers peers"
                        val label = "${Icons.ACTIVITY} LINK [$peerText]"

                        if (peers > 0) {
                            val ink = TangoPalette.inkFor(TangoPalette.SYNC.normal, TangoPalette.SYNC.bright)
                            ImGui.pushStyleColor(ImGuiCol.Button, TangoPalette.SYNC.normal[0], TangoPalette.SYNC.normal[1], TangoPalette.SYNC.normal[2], 1.0f)
                            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, TangoPalette.SYNC.bright[0], TangoPalette.SYNC.bright[1], TangoPalette.SYNC.bright[2], 1.0f)
                            ImGui.pushStyleColor(ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
                        } else {
                            val c = TangoPalette.ALERT
                            val ink = TangoPalette.inkFor(c.dark, c.normal)
                            ImGui.pushStyleColor(ImGuiCol.Button, c.dark[0], c.dark[1], c.dark[2], 1.0f)
                            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, c.normal[0], c.normal[1], c.normal[2], 1.0f)
                            ImGui.pushStyleColor(ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
                        }

                        if (ButtonChrome.button(label)) {
                            PreferencesPanel.open(PreferencesPanel.Category.TEMPO_SYNC)
                        }
                        ImGui.popStyleColor(3)

                        val backendName = linkEngine.getActiveBackendName()
                        val bpmText = syncManager.formattedActiveBpm
                        val confPercent = syncManager.confidencePercent
                        val linkTip = "Ableton Link Sync: Active\nActive BPM: $bpmText\nPeers: $peers connected\nTracking Confidence: $confPercent%\nBackend: $backendName\nClick to open Tempo & Link deck."
                        itemTooltip(linkTip)
                    }

                    if (ImGui.beginMenu("Help")) {
                        if (ImGui.menuItem("Documentation")) {
                            DocManager.openDocumentation()
                        }
                        if (ImGui.menuItem("Check for Updates...")) {
                            AboutModal.open()
                            llm.slop.liquidlsd.update.UpdateChecker.checkForUpdatesAsync(isManualCheck = true)
                        }
                        itemTooltip("Check GitHub releases for the latest version of Liquid LSD.")
                        ImGui.separator()
                        val tooltipsEnabled = session.uiTheme.tooltipsEnabled
                        if (ImGui.menuItem("Show Tooltips", "", tooltipsEnabled)) {
                            session.uiTheme.tooltipsEnabled = !tooltipsEnabled
                            AppPreferencesStore.savePreferences()
                        }
                        itemTooltip("Toggle visibility of helpful on-hover tooltips across the application.")
                        ImGui.separator()
                        if (ImGui.menuItem("About Liquid LSD")) {
                            AboutModal.open()
                        }
                        itemTooltip("View version details, check for updates, and visit GitHub.")
                        ImGui.endMenu()
                    }

                    // ── Right-aligned performance stats & window controls ────────────────
                    drawPerformanceStatsAndControls(session)

                    ImGui.endMainMenuBar()
                }
            }
        } finally {
            ImGui.popStyleVar()
        }
    }

    /**
     * The Perform page tab toggle (A/B, BG/PV, MIXER, MASTER, plus user pages) for [PerformanceMatrixPanel], plus the Learn indicator and
     * Randomize ALL button that used to sit in the matrix's own tab-strip row. Moved here so that
     * row can be removed entirely, recovering its height for the knob grid.
     */
    private fun drawPerformanceTabStrip(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        val theme = session.uiTheme
        val pages = theme.visiblePerformPages()
        val gap = PerfTabStrip.GAP
        val tabH = ImGui.getFrameHeight()
        val tabW = PerfTabStrip.tabWidth(pages.size)
        val isLight = theme.theme == UITheme.Theme.ORANGE_SUNSHINE

        for ((i, page) in pages.withIndex()) {
            if (i > 0) ImGui.sameLine(0f, gap)
            val isActive = theme.performancePageId == page.id
            val activeBg = if (isLight) TangoPalette.u32(TangoPalette.SYNC.normal) else ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f)
            val activeHover = if (isLight) TangoPalette.u32(TangoPalette.SYNC.bright) else ImGui.colorConvertFloat4ToU32(0.15f, 0.62f, 0.82f, 1f)
            val activeText = if (isLight) ImGui.colorConvertFloat4ToU32(0.05f, 0.05f, 0.05f, 1f) else ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f)

            val inactiveBg = if (isLight) ImGui.getColorU32(ImGuiCol.Button) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f)
            val inactiveHover = if (isLight) ImGui.getColorU32(ImGuiCol.ButtonHovered) else ImGui.colorConvertFloat4ToU32(0.22f, 0.25f, 0.30f, 1f)
            val inactiveText = ImGui.getColorU32(ImGuiCol.Text)

            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Button, activeBg)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, activeHover)
                ImGui.pushStyleColor(ImGuiCol.Text, activeText)
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, inactiveBg)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, inactiveHover)
                ImGui.pushStyleColor(ImGuiCol.Text, inactiveText)
            }
            session.uiTheme.withFont(UITheme.FontLevel.H3) {
                if (ButtonChrome.button("${page.name}##perf_tab_${page.id}", tabW, tabH)) {
                    session.parametersState.leaveEditForPageChange()
                    theme.performancePageId = page.id
                    AppPreferencesStore.savePreferences()
                }
            }
            // A narrowed tab clips its name, so the tooltip always carries it.
            itemTooltip(if (page.tooltip.isBlank()) page.name else "${page.name}\n${page.tooltip}")
            ImGui.popStyleColor(3)
        }

        ImGui.sameLine(0f, 10f)
        llm.slop.liquidlsd.ui.rack.RackUnit.drawLearnIndicator()

        if (theme.randomizationEnabled) {
            ImGui.sameLine(0f, 10f)
            ImGui.pushStyleColor(ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.25f, 0.18f, 0.32f, 0.90f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.38f, 0.25f, 0.48f, 1f))
            ImGui.pushStyleColor(ImGuiCol.Text,          ImGui.colorConvertFloat4ToU32(0.95f, 0.85f, 1.0f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ButtonChrome.button("${Icons.DICES} ALL##perf_rand_all", 76f, tabH)) {
                    ParametersUndo.pushUndoState(parametersState, mixer)
                    mixer.randomizeAll()
                }
            }
            ImGui.popStyleColor(3)
            itemTooltip("Randomize ALL Decks (A, B, BG, PV) and Modulators.\nClick to randomize all decks with undo support.")
        }
    }

    /**
     * Renders empty drag zone, telemetry stats (FPS, frame time, CPU%, DSP), and
     * custom window control buttons (Minimize, Maximize/Restore, Close) when running in frameless mode.
     */
    private fun drawPerformanceStatsAndControls(session: llm.slop.liquidlsd.SessionContext) {
        val fps        = PerformanceStats.fps
        val ftMs       = PerformanceStats.frameTimeMs
        val cpuFrac    = PerformanceStats.processCpuFraction   // -1 if unavailable
        val audioActive = session.audioEngine.isActive()
        val audioLatency = PerformanceStats.audioCallbackMs
        val isAudioDisabled = !session.uiTheme.audioEngineEnabled
        val showAudio = audioActive && session.uiTheme.audioEngineEnabled && audioLatency > 0.0f

        val cpuText = if (cpuFrac >= 0.0) "CPU: %2.0f%%  ".format(cpuFrac * 100.0) else ""
        val dspText = if (showAudio) "DSP: %.2fms  ".format(audioLatency) else if (isAudioDisabled) "DSP: OFF  " else "DSP: --  "
        val fpsText = "%3.0f fps  ".format(fps)
        val ftText  = "%3.0f ms  ".format(ftMs)
        val fboText = "GPU buffers: %d (%.0f MB)".format(PerformanceStats.fboCount, PerformanceStats.fboMemoryMB)
        val fullLabel = cpuText + dspText + fpsText + ftText + fboText

        val isFrameless = session.uiTheme.framelessWindow && windowFrameController != null
        val btnW = 24f
        val btnH = ImGui.getFrameHeight()
        val btnGap = 2f
        val statsToBtnsGap = if (isFrameless) 10f else 0f
        val windowBtnsW = if (isFrameless) (btnW * 3f) + (btnGap * 2f) else 0f

        session.uiTheme.withFont(UITheme.FontLevel.CODE) {
            val textH = ImGui.getTextLineHeight()
            val contentRightX = ImGui.getCursorPosX() + ImGui.getContentRegionAvailX()
            val statsTotalW = ImGui.calcTextSize(fullLabel).x
            val btnsStartX = contentRightX - windowBtnsW
            val statsEndX = if (isFrameless) btnsStartX - statsToBtnsGap else contentRightX
            val statsStartX = (statsEndX - statsTotalW).coerceAtLeast(ImGui.getCursorPosX())

            // ── Top Bar Center Drag Region ───────────────────────────────────────────
            val currentX = ImGui.getCursorPosX()
            val dragWidth = (statsStartX - currentX - 8f).coerceAtLeast(0f)
            if (dragWidth > 5f) {
                ImGui.invisibleButton("##header_drag_region", dragWidth, btnH)
                val isHovered = ImGui.isItemHovered()
                val isDoubleClicked = ImGui.isMouseDoubleClicked(0) && isHovered
                windowFrameController?.onTopBarInteraction(isHovered, isDoubleClicked)
                ImGui.sameLine(0f, 8f)
            }

            if (statsStartX > ImGui.getCursorPosX()) {
                ImGui.setCursorPosX(statsStartX)
            }

            val isLight = session.uiTheme.theme == UITheme.Theme.ORANGE_SUNSHINE
            val statGreen = if (isLight) TangoPalette.ACTIVE.dark else floatArrayOf(0.55f, 1.0f, 0.55f)
            val statYellow = if (isLight) TangoPalette.ALERT.dark else floatArrayOf(1.0f, 0.75f, 0.0f)
            val statRed = if (isLight) TangoPalette.DANGER.normal else floatArrayOf(1.0f, 0.25f, 0.25f)
            val statDim = if (isLight) TangoPalette.NEUTRAL_DARK.normal else floatArrayOf(0.50f, 0.55f, 0.60f)
            val fboCol = if (isLight) TangoPalette.NEUTRAL_DARK.dark else floatArrayOf(0.70f, 0.75f, 0.80f)

            // ── CPU % ──────────────────────────────────────────────────────────────
            if (cpuFrac >= 0.0) {
                val cpuPct = cpuFrac * 100.0
                when {
                    cpuPct >= 80.0 -> ImGui.pushStyleColor(ImGuiCol.Text, statRed[0], statRed[1], statRed[2], 1.0f)
                    cpuPct >= 50.0 -> ImGui.pushStyleColor(ImGuiCol.Text, statYellow[0], statYellow[1], statYellow[2], 1.0f)
                    else           -> ImGui.pushStyleColor(ImGuiCol.Text, statGreen[0], statGreen[1], statGreen[2], 1.0f)
                }
                ImGui.textUnformatted(cpuText)
                ImGui.popStyleColor()
                ImGui.sameLine(0f, 0f)
            }

            // ── DSP Latency ───────────────────────────────────────────────────────
            val dspW = ImGui.calcTextSize(dspText).x
            val dspPosX = ImGui.getCursorPosX()
            val dspPosY = ImGui.getCursorPosY()

            ImGui.invisibleButton("##dsp_monitor_button", dspW, textH)
            val isDspHovered = ImGui.isItemHovered()
            val isDspClicked = ImGui.isItemClicked(0)

            ImGui.setCursorPos(dspPosX, dspPosY)

            if (!showAudio) {
                ImGui.pushStyleColor(ImGuiCol.Text, statDim[0], statDim[1], statDim[2], 1.0f)
            } else when {
                audioLatency >= 5.0f -> ImGui.pushStyleColor(ImGuiCol.Text, statRed[0], statRed[1], statRed[2], 1.0f)
                audioLatency >= 2.0f -> ImGui.pushStyleColor(ImGuiCol.Text, statYellow[0], statYellow[1], statYellow[2], 1.0f)
                else                 -> ImGui.pushStyleColor(ImGuiCol.Text, statGreen[0], statGreen[1], statGreen[2], 1.0f)
            }
            ImGui.textUnformatted(dspText)
            ImGui.popStyleColor()

            if (isDspClicked) {
                onOpenAudioEngineMonitor()
            }
            if (isDspHovered) {
                val dspTip = if (showAudio) {
                    "Audio callback DSP execution time\nClick to open Audio Engine preferences."
                } else if (isAudioDisabled) {
                    "Audio engine is disabled.\nClick to open Audio Engine preferences."
                } else {
                    "Audio engine is inactive.\nClick to open Audio Engine preferences."
                }
                showTooltip(dspTip, "dsp_badge_tooltip".hashCode())
            }
            ImGui.sameLine(0f, 0f)

            // ── FPS ───────────────────────────────────────────────────────────────
            val maxFpsConfig = session.uiTheme.maxFps
            val (fpsRed, fpsYellow) = if (maxFpsConfig <= 30) {
                20f to 27f
            } else {
                30f to 50f
            }
            when {
                fps < fpsRed    -> ImGui.pushStyleColor(ImGuiCol.Text, statRed[0], statRed[1], statRed[2], 1.0f)
                fps < fpsYellow -> ImGui.pushStyleColor(ImGuiCol.Text, statYellow[0], statYellow[1], statYellow[2], 1.0f)
                else            -> ImGui.pushStyleColor(ImGuiCol.Text, statGreen[0], statGreen[1], statGreen[2], 1.0f)
            }
            ImGui.textUnformatted(fpsText)
            ImGui.popStyleColor()
            ImGui.sameLine(0f, 0f)

            // ── Frame time ────────────────────────────────────────────────────────
            val (ftRed, ftYellow) = if (maxFpsConfig <= 30) {
                50.0f to 37.0f
            } else {
                33.3f to 20.0f
            }
            when {
                ftMs > ftRed    -> ImGui.pushStyleColor(ImGuiCol.Text, statRed[0], statRed[1], statRed[2], 1.0f)
                ftMs > ftYellow -> ImGui.pushStyleColor(ImGuiCol.Text, statYellow[0], statYellow[1], statYellow[2], 1.0f)
                else            -> ImGui.pushStyleColor(ImGuiCol.Text, statGreen[0], statGreen[1], statGreen[2], 1.0f)
            }
            ImGui.textUnformatted(ftText)
            ImGui.popStyleColor()
            ImGui.sameLine(0f, 0f)

            // ── FBO & GPU Memory ──────────────────────────────────────────────────
            ImGui.pushStyleColor(ImGuiCol.Text, fboCol[0], fboCol[1], fboCol[2], 1.0f)
            ImGui.textUnformatted(fboText)
            ImGui.popStyleColor()
            if (ImGui.isItemHovered()) {
                val fboTip = "GPU Framebuffers (FBO)\nLive FBO count: ${PerformanceStats.fboCount}\nEstimated VRAM: %.1f MB".format(PerformanceStats.fboMemoryMB)
                showTooltip(fboTip, "fbo_badge_tooltip".hashCode())
            }

            // ── Custom Window Controls (Frameless CSD Mode) ──────────────────────────
            if (windowFrameController != null && session.uiTheme.framelessWindow) {
                ImGui.sameLine(0f, 0f)
                ImGui.setCursorPosX(btnsStartX)
                session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                    // Minimize
                    if (ButtonChrome.button("${Icons.MINUS}##win_min", btnW, btnH)) {
                        windowFrameController.minimize()
                    }
                    itemTooltip("Minimize")

                    ImGui.sameLine(0f, btnGap)

                    // Maximize / Restore
                    val isMax = windowFrameController.isMaximized()
                    val maxIcon = if (isMax) Icons.COPY else Icons.SQUARE
                    if (ButtonChrome.button("$maxIcon##win_max", btnW, btnH)) {
                        windowFrameController.toggleMaximize()
                    }
                    itemTooltip(if (isMax) "Restore" else "Maximize")

                    ImGui.sameLine(0f, btnGap)

                    // Close
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.85f, 0.15f, 0.15f, 1.0f)
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.70f, 0.10f, 0.10f, 1.0f)
                    if (ButtonChrome.button("${Icons.X}##win_close", btnW, btnH)) {
                        onTriggerExitFlow()
                    }
                    ImGui.popStyleColor(2)
                    itemTooltip("Close Liquid LSD")
                }
            }
        }
    }
}
