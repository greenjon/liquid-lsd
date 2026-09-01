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
        CvSignalDef("audio_amp", "Amplitude (RMS)", CvTheme.getThemeColor("audio_amp")),
        CvSignalDef("audio_bass", "Bass Band (Low-pass)", CvTheme.getThemeColor("audio_bass")),
        CvSignalDef("audio_mid", "Mid Band (Band-pass)", CvTheme.getThemeColor("audio_mid")),
        CvSignalDef("audio_high", "High Band (High-pass)", CvTheme.getThemeColor("audio_high")),
        CvSignalDef("trigger_onset", "Onset Signal", CvTheme.getThemeColor("trigger_onset")),
        CvSignalDef("trigger_accent", "Accent Level (Decay)", CvTheme.getThemeColor("trigger_accent"))
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
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Toggle audio capture and analysis. Disabling stops audio processing.")
        }

        if (!theme.audioEngineEnabled) {
            ImGui.spacing()
            theme.caption("Audio engine is currently disabled. Enable it above to process live audio and CV signals.")
            return
        }

        ImGui.spacing()

        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
        val sliderBoxW = 52f * fontScale // ~20% smaller than standard 65f

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
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Select audio capture backend (JACK for low-latency Linux, Java Sound for cross-platform).")
            }

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
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Select the audio input capture device.")
            }
            ImGui.sameLine()
            if (ImGui.button("${Icons.REFRESH}##refreshDevices")) {
                audioEngine.refreshInputDevices()
            }
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Rescan for newly connected audio input hardware.")
            }

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
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Active: Signal detected and tracking tempo. Silent: No input audio or level too low.")
            }

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
                if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                    ImGui.setTooltip("Attempts to reconnect to the JACK or PipeWire audio backend.")
                }
            } else if (backend == "Java Sound") {
                ImGui.spacing()
                if (ImGui.button("${Icons.REFRESH} Switch to JACK Audio", 220f, 28f)) {
                    Thread {
                        audioEngine.tryReconnect(force = true)
                    }.start()
                }
                if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                    ImGui.setTooltip("Stops Java Sound and attempts to connect to a running JACK/PipeWire audio server.")
                }
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
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Real-time tempo estimate. Flashes on detected beat phase.")
            }
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
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Ignore incoming audio tempo and lock entirely to the Manual BPM slider.")
            }

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
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Select frequency band for primary onset detection (LOW/Kick, MID/Snare, HIGH/Hi-hat, or UNFILTERED).")
            }

            ImGui.spacing()

            // Detection Presets
            theme.body("Presets:")
            ImGui.sameLine()
            if (ImGui.button("High Accuracy")) {
                audioEngine.beatDetector.applyPreset(BeatDetectionSettings.highAccuracy())
                theme.saveSettings()
            }
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Apply Beat Tracker configuration tuned for precise tempo detection.")
            }
            ImGui.sameLine()
            if (ImGui.button("Balanced")) {
                audioEngine.beatDetector.applyPreset(BeatDetectionSettings.balanced())
                theme.saveSettings()
            }
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Apply Beat Tracker configuration balanced between tracking reactivity and stability.")
            }
            ImGui.sameLine()
            if (ImGui.button("Eco")) {
                audioEngine.beatDetector.applyPreset(BeatDetectionSettings.eco())
                theme.saveSettings()
            }
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("Apply Beat Tracker configuration with relaxed inertia.")
            }

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

