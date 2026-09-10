package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.BeatDetectionSettings
import llm.slop.liquidlsd.audio.AudioTarget
import llm.slop.liquidlsd.audio.SignalState
import llm.slop.liquidlsd.audio.SystemAudioVolume
import llm.slop.liquidlsd.midi.MidiEngine

/**
 * Dedicated UI component for the Audio Engine settings and real-time monitor:
 * - Device & backend selection (Auto, JACK, Java Sound).
 * - Real-time BPM readout with flashing beat indicator.
 * - Auto Beat Detection parameters & preset buttons.
 * - Input gain and system recording volume controls.
 * - Raw audio buffer oscilloscope and sound-derived Control Voltage (CV) oscilloscopes.
 *
 * Rendered within the "Audio Engine" category of [SettingsPanel].
 */
object AudioEnginePanel {

    // Pre-allocated arrays and primitive wrappers to avoid runtime allocations
    private val rawSamples = FloatArray(1024)
    private val cvSamples = FloatArray(400)

    // Beat Detection & Lock UI state
    private val isLocked = ImBoolean()

    // Pre-allocated enum arrays to eliminate per-frame allocations
    private val backendModes = AudioEngine.AudioBackendMode.values()
    private val audioTargets = AudioTarget.values()

    // Device & Backend UI wrappers
    private val isAudioEnabled = ImBoolean()
    private val currentBackendIdx = ImInt()
    private val currentDeviceIdx = ImInt()

    private val backendNames = arrayOf(
        "Auto (JACK -> Java Sound fallback)",
        "JACK Only (Linux Pro Audio)",
        "Java Sound Only (Cross-Platform)"
    )

    private data class CvSignalDef(val id: String, val title: String, val colorU32: Int)

    private val cvSignals = arrayOf(
        CvSignalDef("beatSine", "Beat Sine (Oscillator)", CvTheme.getThemeColor("beatSine")),
        CvSignalDef("audio_amp", "Full Mix (RMS)", CvTheme.getThemeColor("audio_amp")),
        CvSignalDef("audio_bass", "Bass Band (RMS)", CvTheme.getThemeColor("audio_bass")),
        CvSignalDef("audio_mid", "Mid Band (RMS)", CvTheme.getThemeColor("audio_mid")),
        CvSignalDef("audio_high", "High Band (RMS)", CvTheme.getThemeColor("audio_high")),
        CvSignalDef("audio_flux_amp", "Full Mix Transient (Flux)", CvTheme.getThemeColor("audio_flux_amp")),
        CvSignalDef("audio_flux_bass", "Kick Transient (Flux)", CvTheme.getThemeColor("audio_flux_bass")),
        CvSignalDef("audio_flux_mid", "Snare Transient (Flux)", CvTheme.getThemeColor("audio_flux_mid")),
        CvSignalDef("audio_flux_high", "Hat Transient (Flux)", CvTheme.getThemeColor("audio_flux_high"))
    )

    /**
     * Opens the Settings modal focused directly on the Audio Engine tab.
     */
    fun open() {
        SettingsPanel.open(SettingsPanel.Category.AUDIO_ENGINE)
    }

    /**
     * Renders the complete Audio Engine settings and real-time monitor content inside [SettingsPanel].
     */
    fun drawContent(session: llm.slop.liquidlsd.SessionContext) {
        val theme = session.uiTheme
        val audioEngine = session.audioEngine

        // ---------------------------------------------------------------------
        // 1. Audio Backend & Input Device Configuration
        // ---------------------------------------------------------------------
        theme.h2("${Icons.ACTIVITY} Audio Engine & Input Device")
        ImGui.separator()
        ImGui.spacing()

        isAudioEnabled.set(theme.audioEngineEnabled)
        if (ImGui.checkbox("Enable Audio Engine", isAudioEnabled)) {
            val nextVal = isAudioEnabled.get()
            if (nextVal != theme.audioEngineEnabled) {
                theme.audioEngineEnabled = nextVal
                theme.saveSettings()
                if (nextVal) audioEngine.start() else audioEngine.stop()
            }
        }
        itemTooltip("Toggle audio capture and analysis. Disabling stops audio processing.")

        if (!theme.audioEngineEnabled) {
            ImGui.spacing()
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.95f, 0.75f, 0.35f, 1.0f)
            ImGui.textWrapped("${Icons.ALERT} Audio engine is disabled. Live audio input and audio-reactive CV signals (Amp, Bass, Mid, High, Flux) are inactive.")
            ImGui.popStyleColor()
            ImGui.spacing()
            theme.caption("Beat synchronization runs on the internal manual tempo clock below.")

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            theme.h2("${Icons.SETTINGS} Beat Sync & Manual Tempo")
            ImGui.separator()
            ImGui.spacing()

            val sliderBoxW = 50f

            val bpm = audioEngine.getEstimatedBpm()
            val totalBeats = session.cvRegistry.getSynchronizedTotalBeats()
            val beatPhase = totalBeats % 1.0
            val flashIntensity = if (beatPhase < 0.25) {
                (1.0 - (beatPhase / 0.25)).toFloat()
            } else {
                0.0f
            }

            ImGui.alignTextToFramePadding()
            theme.h3("BPM: ")
            ImGui.sameLine()

            val r = 1.0f
            val g = 0.8f + 0.2f * (1.0f - flashIntensity)
            val b = 0.2f + 0.8f * (1.0f - flashIntensity)
            theme.h3Colored(r, g, b, 1.0f, "%.1f".format(bpm))

            ImGui.sameLine(0f, 12f)

            // Beat flashing dot
            val indicatorSize = 14f
            val curX = ImGui.getCursorScreenPosX()
            val curY = ImGui.getCursorScreenPosY() + (ImGui.getTextLineHeight() - indicatorSize) / 2f
            ImGui.dummy(indicatorSize, indicatorSize)
            itemTooltip("Manual tempo clock. Flashes on internal beat phase.")
            val dl = ImGui.getWindowDrawList()
            val indicatorCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.0f, 0.15f + 0.85f * flashIntensity)
            val borderCol = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.5f)
            dl.addCircleFilled(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, indicatorCol)
            dl.addCircle(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, borderCol, 16, 1.0f)

            ImGui.sameLine(0f, 20f)
            theme.captionColored(0.85f, 0.75f, 0.35f, 1.0f, "Manual Fixed Clock")

            ImGui.spacing()

            // Manual BPM Slider
            CustomRangeSlider.drawCompactSlider(
                session = session,
                label = "Manual BPM",
                currentValue = audioEngine.manualBpm,
                minLimit = 40f,
                maxLimit = 200f,
                defaultValue = 120f,
                formatValue = { "%.1f".format(it) },
                idPrefix = "audio_engine_manual_bpm_disabled",
                themeColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f),
                showCurrentLabel = false,
                customBoxWidth = sliderBoxW,
                onValueChanged = { newVal ->
                    audioEngine.manualBpm = newVal
                    audioEngine.setBpmDirectly(newVal)
                    theme.saveSettings()
                }
            )

            ImGui.spacing()

            if (ImGui.button("${Icons.REFRESH} Reset to 120.0 BPM", 180f, 26f)) {
                audioEngine.manualBpm = 120.0f
                audioEngine.setBpmDirectly(120.0f)
                theme.saveSettings()
            }
            itemTooltip("Resets the manual tempo clock to standard 120.0 BPM.")

            ImGui.spacing()
            theme.caption("Note: BEAT-synced LFOs, Sequencers, and the title bar 4-beat meter track this manual BPM clock.")
            return
        }

        ImGui.spacing()

        val sliderBoxW = 35.2f // ~20% smaller than standard 44f

        val tableFlags = ImGuiTableFlags.SizingStretchSame
        if (ImGui.beginTable("##audio_engine_2col_table", 2, tableFlags)) {
            ImGui.tableSetupColumn("##audio_col_left", ImGuiTableColumnFlags.WidthStretch, 1f)
            ImGui.tableSetupColumn("##audio_col_right", ImGuiTableColumnFlags.WidthStretch, 1f)
            ImGui.tableNextRow()

            // -----------------------------------------------------------------
            // LEFT COLUMN: Audio Device Settings & Beat Sync / Detection
            // -----------------------------------------------------------------
            ImGui.tableSetColumnIndex(0)

            // Audio Backend Selection
            theme.body("Audio Backend:")
            currentBackendIdx.set(audioEngine.backendMode.ordinal)
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX().coerceAtMost(380f))
            if (ImGui.combo("##AudioBackend", currentBackendIdx, backendNames)) {
                val nextBackend = backendModes[currentBackendIdx.get()]
                audioEngine.selectDevice(audioEngine.selectedDeviceName, nextBackend)
                theme.saveSettings()
            }
            itemTooltip("Select audio capture backend (JACK for low-latency Linux, Java Sound for cross-platform).")

            ImGui.spacing()

            // Hardware Input Device Selection (cached list to prevent ALSA resource leakage)
            theme.body("Input Hardware Device:")
            val devices = audioEngine.getAvailableInputDevices()
            val deviceNames = audioEngine.getAvailableDeviceNames()
            val currentDevIdx = devices.indexOfFirst { it.name == audioEngine.selectedDeviceName }.coerceAtLeast(0)
            currentDeviceIdx.set(currentDevIdx)
            val refreshBtnW = 32f
            ImGui.setNextItemWidth((ImGui.getContentRegionAvailX() - refreshBtnW - 8f).coerceAtLeast(100f))
            if (ImGui.combo("##InputDevice", currentDeviceIdx, deviceNames)) {
                val chosenDevice = devices.getOrNull(currentDeviceIdx.get())
                if (chosenDevice != null) {
                    audioEngine.selectDevice(if (chosenDevice.isDefault) null else chosenDevice.name)
                    theme.saveSettings()
                }
            }
            itemTooltip("Select the audio input capture device.")
            ImGui.sameLine()
            if (ImGui.button("${Icons.REFRESH}##refreshDevices")) {
                audioEngine.refreshInputDevices()
            }
            itemTooltip("Rescan for newly connected audio input hardware.")

            ImGui.spacing()

            // Backend Status & Reconnection Options
            val backend = audioEngine.getActiveBackendName()
            val isAudioActive = audioEngine.isActive()
            val state = audioEngine.currentState

            ImGui.alignTextToFramePadding()
            theme.body("Driver: ")
            ImGui.sameLine()
            theme.bodyColored(0.2f, 0.7f, 0.9f, 1.0f, backend)
            ImGui.sameLine(0f, 20f)
            theme.body("Sync State: ")
            ImGui.sameLine()
            when (state) {
                SignalState.SILENT -> theme.bodyColored(0.5f, 0.5f, 0.5f, 1.0f, "SILENT")
                SignalState.ACTIVE -> theme.bodyColored(0.2f, 0.9f, 0.4f, 1.0f, "ACTIVE")
            }
            itemTooltip("Active: Signal detected and tracking tempo. Silent: No input audio or level too low.")

            if (!isAudioActive) {
                ImGui.spacing()
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.35f, 0.35f, 1.0f)
                ImGui.textWrapped("${Icons.ALERT} Warning: Audio Engine is inactive. No audio signal received.")
                ImGui.popStyleColor()
                ImGui.spacing()
                if (ImGui.button("${Icons.REFRESH} Retry Connection", 220f, 28f)) {
                    Thread {
                        audioEngine.tryReconnect(force = true)
                    }.start()
                }
                itemTooltip("Attempts to reconnect to the JACK or PipeWire audio backend.")
            } else if (backend == "Java Sound") {
                ImGui.spacing()
                if (ImGui.button("${Icons.REFRESH} Switch to JACK Audio", 220f, 28f)) {
                    Thread {
                        audioEngine.tryReconnect(force = true)
                    }.start()
                }
                itemTooltip("Stops Java Sound and attempts to connect to a running JACK/PipeWire audio server.")
            }

            // MIDI Controllers Status
            val midiCount = MidiEngine.getActiveDeviceCount()
            ImGui.spacing()
            if (midiCount == 0) {
                theme.captionColored(0.9f, 0.6f, 0.2f, 1.0f, "MIDI: No hardware controllers detected.")
            } else {
                theme.captionColored(0.2f, 0.9f, 0.4f, 1.0f, "MIDI: $midiCount active MIDI controller(s) connected.")
            }

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // -----------------------------------------------------------------
            // 2. Real-Time BPM Readout & Beat Synchronization
            // -----------------------------------------------------------------
            theme.h2("${Icons.SETTINGS} Beat Sync & Detection")
            ImGui.separator()
            ImGui.spacing()

            val bpm = audioEngine.getEstimatedBpm()
            val totalBeats = session.cvRegistry.getSynchronizedTotalBeats()
            val beatPhase = totalBeats % 1.0
            val flashIntensity = if (beatPhase < 0.25) {
                (1.0 - (beatPhase / 0.25)).toFloat()
            } else {
                0.0f
            }

            ImGui.alignTextToFramePadding()
            theme.h3("BPM: ")
            ImGui.sameLine()

            val r = 1.0f
            val g = 0.8f + 0.2f * (1.0f - flashIntensity)
            val b = 0.2f + 0.8f * (1.0f - flashIntensity)
            theme.h3Colored(r, g, b, 1.0f, "%.1f".format(bpm))

            ImGui.sameLine(0f, 12f)

            // Beat flashing dot
            val indicatorSize = 14f
            val curX = ImGui.getCursorScreenPosX()
            val curY = ImGui.getCursorScreenPosY() + (ImGui.getTextLineHeight() - indicatorSize) / 2f
            ImGui.dummy(indicatorSize, indicatorSize)
            itemTooltip("Real-time tempo estimate. Flashes on detected beat phase.")
            val dl = ImGui.getWindowDrawList()
            val indicatorCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.0f, 0.15f + 0.85f * flashIntensity)
            val borderCol = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.5f)
            dl.addCircleFilled(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, indicatorCol)
            dl.addCircle(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, borderCol, 16, 1.0f)

            ImGui.sameLine(0f, 20f)
            isLocked.set(audioEngine.isBpmLocked)
            if (ImGui.checkbox("Lock Manual BPM", isLocked)) {
                audioEngine.isBpmLocked = isLocked.get()
                theme.saveSettings()
            }
            itemTooltip("Ignore incoming audio tempo and lock entirely to the Manual BPM slider.")

            ImGui.spacing()

            // Manual BPM Slider
            CustomRangeSlider.drawCompactSlider(
                session = session,
                label = "Manual BPM",
                currentValue = audioEngine.manualBpm,
                minLimit = 40f,
                maxLimit = 200f,
                defaultValue = 120f,
                formatValue = { "%.1f".format(it) },
                idPrefix = "audio_engine_manual_bpm",
                themeColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f),
                showCurrentLabel = false,
                customBoxWidth = sliderBoxW,
                onValueChanged = { newVal ->
                    audioEngine.manualBpm = newVal
                    audioEngine.setBpmDirectly(newVal)
                    theme.saveSettings()
                }
            )

            ImGui.spacing()

            // Auto Beat Detection Parameters (Beat Tracker)
            val settings = audioEngine.beatDetector.settings

            theme.body("Target Band:")
            ImGui.sameLine()
            ImGui.setNextItemWidth(140f)
            if (ImGui.beginCombo("##BeatDetectionTarget", settings.target.name)) {
                for (target in audioTargets) {
                    val isSelected = settings.target == target
                    if (ImGui.selectable(target.name, isSelected)) {
                        settings.target = target
                        theme.saveSettings()
                    }
                    if (isSelected) ImGui.setItemDefaultFocus()
                }
                ImGui.endCombo()
            }
            itemTooltip("Select frequency band for primary onset detection (LOW/Kick, MID/Snare, HIGH/Hi-hat, or UNFILTERED).")

            ImGui.spacing()

            // Detection Presets
            theme.body("Presets:")
            ImGui.sameLine()
            if (ImGui.button("High Accuracy")) {
                audioEngine.beatDetector.applyPreset(BeatDetectionSettings.highAccuracy())
                theme.saveSettings()
            }
            itemTooltip("Apply Beat Tracker configuration tuned for precise tempo detection.")
            ImGui.sameLine()
            if (ImGui.button("Balanced")) {
                audioEngine.beatDetector.applyPreset(BeatDetectionSettings.balanced())
                theme.saveSettings()
            }
            itemTooltip("Apply Beat Tracker configuration balanced between tracking reactivity and stability.")
            ImGui.sameLine()
            if (ImGui.button("Eco")) {
                audioEngine.beatDetector.applyPreset(BeatDetectionSettings.eco())
                theme.saveSettings()
            }
            itemTooltip("Apply Beat Tracker configuration with relaxed inertia.")

            ImGui.spacing()

            val beatThemeCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f)

            // BPM Search Range Dual-Headed Slider
            CustomRangeSlider.drawCustomRangeSlider(
                session = session,
                label = "BPM Range",
                currentValue = bpm,
                currentMin = settings.bpmSearchFloor.toFloat(),
                currentMax = settings.bpmSearchCeiling.toFloat(),
                minLimit = 40f,
                maxLimit = 240f,
                isRandomizable = true,
                showControls = false,
                defaultValue = 40f,
                formatValue = { "${it.toInt()}" },
                idPrefix = "audio_engine_bpm_range",
                themeColor = beatThemeCol,
                showCurrentLabel = false,
                customBoxWidth = sliderBoxW,
                onRangeChanged = { nextMin, nextMax ->
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    settings.bpmSearchFloor = safeMin.toInt()
                    settings.bpmSearchCeiling = safeMax.toInt()
                    theme.saveSettings()
                }
            )

            if (!audioEngine.beatDetector.isTargetLevelSufficient) {
                ImGui.spacing()
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f)
                ImGui.textWrapped("${Icons.ALERT} Low Signal: Not enough energy in the selected target band (${settings.target.name}) for reliable analysis. Tempo is gracefully locked to 120.0 BPM fallback.")
                ImGui.popStyleColor()
            }

            // -----------------------------------------------------------------
            // Clock Sync & Ableton Link Section
            // -----------------------------------------------------------------
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            theme.h2("${Icons.ACTIVITY} Clock Sync & Ableton Link")
            ImGui.separator()
            ImGui.spacing()

            val currentMode = llm.slop.liquidlsd.link.LinkSyncManager.currentMode
            theme.body("Link Sync Mode:")
            for (mode in llm.slop.liquidlsd.link.SyncMode.entries) {
                if (ImGui.radioButton("${mode.displayName}##sync_mode_${mode.name}", currentMode == mode)) {
                    llm.slop.liquidlsd.link.LinkSyncManager.setSyncMode(mode)
                    theme.saveSettings()
                }
                ImGui.sameLine(0f, 12f)
            }
            ImGui.newLine()

            val currentClock = AudioEngine.clockSource
            theme.body("Active Clock Source:")
            for (source in llm.slop.liquidlsd.audio.ClockSource.entries) {
                if (ImGui.radioButton("${source.displayName}##clock_${source.name}", currentClock == source)) {
                    AudioEngine.clockSource = source
                    if (source == llm.slop.liquidlsd.audio.ClockSource.ABLETON_LINK) {
                        llm.slop.liquidlsd.link.AbletonLinkEngine.setEnabled(true)
                    }
                    theme.saveSettings()
                }
                ImGui.sameLine(0f, 16f)
            }
            ImGui.newLine()

            val linkEngine = llm.slop.liquidlsd.link.AbletonLinkEngine
            val isLinkEnabled = linkEngine.isEnabled

            val linkState = imgui.type.ImBoolean(isLinkEnabled)
            if (ImGui.checkbox("Enable Ableton Link", linkState)) {
                linkEngine.setEnabled(linkState.get())
                theme.saveSettings()
            }
            itemTooltip("Participate in local network Ableton Link session for peer tempo & beat sync.")

            if (isLinkEnabled) {
                val peers = linkEngine.getNumPeers()
                val backendName = linkEngine.getActiveBackendName()

                ImGui.alignTextToFramePadding()
                theme.body("Peers Connected: ")
                ImGui.sameLine()
                if (peers > 0) {
                    theme.bodyColored(0.2f, 0.9f, 0.4f, 1.0f, "$peers peer(s)")
                } else {
                    theme.bodyColored(0.9f, 0.7f, 0.2f, 1.0f, "0 peers (searching...)")
                }

                ImGui.sameLine(0f, 20f)
                theme.body("Driver: ")
                ImGui.sameLine()
                theme.captionColored(0.6f, 0.8f, 1.0f, 1.0f, backendName)

                // Transmission & Active BPM status
                ImGui.spacing()
                theme.body("Active BPM: ")
                ImGui.sameLine()
                theme.bodyColored(0.2f, 0.9f, 0.9f, 1.0f, "${llm.slop.liquidlsd.link.LinkSyncManager.formattedActiveBpm} BPM")

                val isTx = llm.slop.liquidlsd.link.LinkSyncManager.isTransmitting
                if (currentMode == llm.slop.liquidlsd.link.SyncMode.AUDIO_BROADCAST) {
                    ImGui.sameLine(0f, 20f)
                    theme.body("Transmission: ")
                    ImGui.sameLine()
                    if (isTx) {
                        theme.bodyColored(0.2f, 0.9f, 0.4f, 1.0f, "[TRANSMITTING]")
                        itemTooltip("Audio beat tracker is actively driving tempo and beat phase alignment to Link session.")
                    } else {
                        theme.bodyColored(0.9f, 0.6f, 0.2f, 1.0f, "[STANDBY / CONNECTING]")
                        itemTooltip("Audio broadcast mode is active, waiting for Link backend socket connection.")
                    }
                }

                // Beat Tracker Confidence meter
                ImGui.spacing()
                val confidence = llm.slop.liquidlsd.link.LinkSyncManager.confidence
                val confPercent = llm.slop.liquidlsd.link.LinkSyncManager.confidencePercent
                ImGui.alignTextToFramePadding()
                theme.body("Beat Tracker Confidence: ")
                ImGui.sameLine()
                val (cr, cg, cb) = when {
                    confidence >= 0.70f -> Triple(0.2f, 0.9f, 0.4f)
                    confidence >= 0.40f -> Triple(0.9f, 0.8f, 0.2f)
                    else -> Triple(0.9f, 0.3f, 0.3f)
                }
                theme.bodyColored(cr, cg, cb, 1.0f, "$confPercent%")
                ImGui.sameLine(0f, 10f)
                ImGui.progressBar(confidence, 120f, 16f, "")
                itemTooltip("Rhythmic tracking stability metric from audio beat tracker.")

                // Quantum Selector
                ImGui.spacing()
                theme.body("Link Quantum:")
                val currentQuantum = linkEngine.quantum
                val quantums = doubleArrayOf(1.0, 4.0, 8.0, 16.0)
                val quantumLabels = arrayOf("1 Beat", "4 Beats (1 Bar)", "8 Beats (2 Bars)", "16 Beats (4 Bars)")
                for (i in quantums.indices) {
                    val q = quantums[i]
                    if (ImGui.radioButton("${quantumLabels[i]}##quantum_$q", currentQuantum == q)) {
                        linkEngine.quantum = q
                        theme.saveSettings()
                    }
                    if (i < quantums.size - 1) ImGui.sameLine(0f, 12f)
                }

                // Transport Sync
                val ssSync = imgui.type.ImBoolean(linkEngine.isStartStopSyncEnabled())
                if (ImGui.checkbox("Enable Start/Stop Transport Sync", ssSync)) {
                    linkEngine.setStartStopSyncEnabled(ssSync.get())
                    theme.saveSettings()
                }
                itemTooltip("Synchronize play/pause transport state across connected Ableton Link peers.")
            }

            // -----------------------------------------------------------------
            // RIGHT COLUMN: Raw Audio Input + Sound-Derived CV Oscilloscopes
            // -----------------------------------------------------------------
            ImGui.tableSetColumnIndex(1)

            // Section 3: Raw Audio Input
            theme.h2("Raw Audio Input")
            ImGui.separator()
            ImGui.spacing()

            CustomRangeSlider.drawCompactSlider(
                session = session,
                label = "Input Gain",
                currentValue = audioEngine.inputGain,
                minLimit = 0.0f,
                maxLimit = 10.0f,
                defaultValue = 1.0f,
                formatValue = { "%.2fx".format(it) },
                idPrefix = "audio_engine_input_gain",
                themeColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.9f, 0.4f, 0.9f),
                showCurrentLabel = false,
                customBoxWidth = sliderBoxW,
                onValueChanged = { newVal ->
                    audioEngine.inputGain = newVal
                    theme.saveSettings()
                }
            )

            if (SystemAudioVolume.isSupported) {
                SystemAudioVolume.queryAsync()
                val sysVol = SystemAudioVolume.systemInputVolume
                val isMuted = SystemAudioVolume.isMuted
                val label = if (isMuted) "System Volume [MUTED]" else "System Volume"
                CustomRangeSlider.drawCompactSlider(
                    session = session,
                    label = label,
                    currentValue = sysVol,
                    minLimit = 0.0f,
                    maxLimit = 1.0f,
                    defaultValue = 1.0f,
                    formatValue = { "%.2f".format(it) },
                    idPrefix = "audio_engine_sys_vol",
                    themeColor = if (isMuted) ImGui.colorConvertFloat4ToU32(1f, 0.3f, 0.3f, 0.9f) else ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f),
                    showCurrentLabel = false,
                    customBoxWidth = sliderBoxW,
                    onValueChanged = { newVal ->
                        SystemAudioVolume.updateSystemVolume(newVal)
                    }
                )
            }

            ImGui.spacing()
            audioEngine.rawHistory.copyTo(rawSamples)
            val rawColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.9f, 0.4f, 1.0f) // Neon Green
            OscilloscopeDrawer.drawBufferOscilloscope(session, "Raw Buffer", rawSamples, -1.0f, 1.0f, rawColor, 65f)

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // Section 4: Sound-Derived CV Oscilloscopes (Stacked Vertically)
            theme.h2("Sound-Derived Control Voltages (CV)")
            ImGui.separator()
            ImGui.spacing()

            for (sig in cvSignals) {
                val history = session.cvRegistry.getHistory(sig.id) ?: continue
                history.copyTo(cvSamples)
                val minV = if (sig.id == "beatSine") -1.0f else 0.0f
                val maxV = 1.0f
                OscilloscopeDrawer.drawBufferOscilloscope(
                    session,
                    sig.title,
                    cvSamples,
                    minV,
                    maxV,
                    sig.colorU32,
                    50f
                )
                ImGui.spacing()
            }

            ImGui.endTable()
        }
    }
}

