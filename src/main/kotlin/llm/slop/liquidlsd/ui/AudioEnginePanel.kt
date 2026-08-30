package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.BeatDetectionMode
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
    private val cvSamples = FloatArray(200)

    private val manualBpmArr = FloatArray(1)
    private val gainArr = FloatArray(1)
    private val sysVolArr = FloatArray(1)

    // Beat Detection UI arrays
    private val floorArr = IntArray(1)
    private val ceilArr = IntArray(1)
    private val winLenArr = FloatArray(1)
    private val thresholdArr = FloatArray(1)
    private val pllRateArr = FloatArray(1)
    private val biquadQArr = FloatArray(1)
    private val isLocked = ImBoolean()

    // Pre-allocated enum arrays to eliminate per-frame allocations
    private val backendModes = AudioEngine.AudioBackendMode.values()
    private val beatDetectionModes = BeatDetectionMode.values()
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
        CvSignalDef("audio_amp", "Amplitude (RMS)", ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, 1.0f)), // Neon Cyan
        CvSignalDef("audio_bass", "Bass Band (Low-pass)", ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.6f, 1.0f)), // Neon Pink
        CvSignalDef("audio_mid", "Mid Band (Band-pass)", ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.1f, 1.0f)), // Neon Orange
        CvSignalDef("audio_high", "High Band (High-pass)", ImGui.colorConvertFloat4ToU32(0.1f, 0.9f, 0.8f, 1.0f)), // Neon Teal
        CvSignalDef("beatSine", "Beat Sine (Oscillator)", ImGui.colorConvertFloat4ToU32(0.6f, 0.4f, 1.0f, 1.0f)), // Neon Purple
        CvSignalDef("trigger_onset", "Onset Signal", ImGui.colorConvertFloat4ToU32(0.9f, 0.8f, 0.1f, 1.0f)), // Neon Yellow
        CvSignalDef("trigger_accent", "Accent Level (Decay)", ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.3f, 1.0f)) // Neon Red
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

        // Audio Backend Selection
        theme.body("Audio Backend:")
        currentBackendIdx.set(audioEngine.backendMode.ordinal)
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX().coerceAtMost(380f))
        if (ImGui.combo("##AudioBackend", currentBackendIdx, backendNames)) {
            val nextBackend = backendModes[currentBackendIdx.get()]
            audioEngine.selectDevice(audioEngine.selectedDeviceName, nextBackend)
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
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX().coerceAtMost(340f))
        if (ImGui.combo("##InputDevice", currentDeviceIdx, deviceNames)) {
            val chosenDevice = devices.getOrNull(currentDeviceIdx.get())
            if (chosenDevice != null) {
                audioEngine.selectDevice(if (chosenDevice.isDefault) null else chosenDevice.name)
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

        // ---------------------------------------------------------------------
        // 2. Real-Time BPM Readout & Beat Synchronization
        // ---------------------------------------------------------------------
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

        ImGui.sameLine(0f, 24f)
        isLocked.set(audioEngine.isBpmLocked)
        if (ImGui.checkbox("Lock to Manual BPM", isLocked)) {
            audioEngine.isBpmLocked = isLocked.get()
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Ignore incoming audio tempo and lock entirely to the Manual BPM slider.")
        }

        ImGui.spacing()

        // Manual BPM Slider
        ImGui.alignTextToFramePadding()
        theme.body("Manual BPM:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(180f)
        manualBpmArr[0] = audioEngine.manualBpm
        if (ImGui.sliderFloat("##manual_bpm", manualBpmArr, 40f, 200f, "%.1f")) {
            audioEngine.manualBpm = manualBpmArr[0]
            audioEngine.setBpmDirectly(manualBpmArr[0])
            theme.saveSettings()
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Set fallback tempo. Used when 'Lock to Manual BPM' is active or input signal is silent.")
        }

        ImGui.spacing()

        // Auto Beat Detection Parameters
        val settings = audioEngine.beatDetector.settings

        theme.body("Beat Detection Mode:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(180f)
        if (ImGui.beginCombo("##BeatDetectionMode", settings.mode.name)) {
            for (mode in beatDetectionModes) {
                val isSelected = settings.mode == mode
                if (ImGui.selectable(mode.name, isSelected)) {
                    settings.mode = mode
                }
                if (isSelected) ImGui.setItemDefaultFocus()
            }
            ImGui.endCombo()
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Select algorithm used for beat tracking (e.g., spectral energy flux).")
        }

        ImGui.sameLine(0f, 16f)
        theme.body("Target:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(140f)
        if (ImGui.beginCombo("##BeatDetectionTarget", settings.target.name)) {
            for (target in audioTargets) {
                val isSelected = settings.target == target
                if (ImGui.selectable(target.name, isSelected)) {
                    settings.target = target
                }
                if (isSelected) ImGui.setItemDefaultFocus()
            }
            ImGui.endCombo()
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Choose which audio input channel to analyze (Left, Right, or Mixed Mono).")
        }

        ImGui.spacing()

        // Detection Presets
        theme.body("Presets:")
        ImGui.sameLine()
        if (ImGui.button("High Accuracy")) audioEngine.beatDetector.applyPreset(BeatDetectionSettings.highAccuracy())
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Apply configuration tuned for precise tempo detection.")
        }
        ImGui.sameLine()
        if (ImGui.button("Balanced")) audioEngine.beatDetector.applyPreset(BeatDetectionSettings.balanced())
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Apply configuration balanced between latency and precision.")
        }
        ImGui.sameLine()
        if (ImGui.button("Eco")) audioEngine.beatDetector.applyPreset(BeatDetectionSettings.eco())
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Apply configuration with low CPU usage.")
        }

        ImGui.spacing()

        floorArr[0] = settings.bpmSearchFloor
        if (ImGui.sliderInt("BPM Floor", floorArr, 40, 120)) {
            settings.bpmSearchFloor = floorArr[0]
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Minimum limit for tempo estimation to prevent half-tempo octave errors.")
        }

        ceilArr[0] = settings.bpmSearchCeiling
        if (ImGui.sliderInt("BPM Ceiling", ceilArr, 120, 240)) {
            settings.bpmSearchCeiling = ceilArr[0]
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Maximum limit for tempo estimation to prevent double-tempo octave errors.")
        }

        winLenArr[0] = settings.analysisWindowLength
        if (ImGui.sliderFloat("Analysis Length (s)", winLenArr, 1.0f, 8.0f, "%.1f")) {
            settings.analysisWindowLength = winLenArr[0]
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Duration of onset history buffer analyzed for autocorrelation.")
        }

        thresholdArr[0] = settings.energyThreshold
        if (ImGui.sliderFloat("Energy Threshold", thresholdArr, 1.0f, 3.0f, "%.2f")) {
            settings.energyThreshold = thresholdArr[0]
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Threshold multiplier for energy-difference trigger.")
        }

        pllRateArr[0] = settings.pllAdaptationRate
        if (ImGui.sliderFloat("PLL Adaptation", pllRateArr, 0.01f, 1.0f, "%.2f")) {
            settings.pllAdaptationRate = pllRateArr[0]
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("How quickly the PLL tracks tempo changes.")
        }

        biquadQArr[0] = settings.biquadQ
        if (ImGui.sliderFloat("Resonator Q", biquadQArr, 0.5f, 10.0f, "%.2f")) {
            settings.biquadQ = biquadQArr[0]
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Quality factor (resonance) for the Complex Domain biquad filter.")
        }

        if (!audioEngine.beatDetector.isTargetLevelSufficient) {
            ImGui.spacing()
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f)
            ImGui.textWrapped("${Icons.ALERT} Low Signal: Not enough energy in the selected target band (${settings.target.name}) for reliable analysis. Consider switching the Target to HIGH or UNFILTERED if playing from small laptop speakers.")
            ImGui.popStyleColor()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // 3. Raw Audio Input Level & Oscilloscope
        // ---------------------------------------------------------------------
        theme.h2("Raw Audio Input")
        ImGui.separator()
        ImGui.spacing()

        gainArr[0] = audioEngine.inputGain
        ImGui.alignTextToFramePadding()
        theme.body("Input Level Gain:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(180f)
        if (ImGui.sliderFloat("##input_gain", gainArr, 0.0f, 10.0f, "%.2fx")) {
            audioEngine.inputGain = gainArr[0]
            theme.saveSettings()
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Pre-amplify incoming audio before analysis and oscilloscope display.")
        }
        ImGui.sameLine()
        if (ImGui.button("Reset##gain")) {
            audioEngine.inputGain = 1.0f
            theme.saveSettings()
        }
        if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
            ImGui.setTooltip("Reset input gain to 1.0x.")
        }

        if (SystemAudioVolume.isSupported) {
            SystemAudioVolume.queryAsync()
            sysVolArr[0] = SystemAudioVolume.systemInputVolume
            ImGui.alignTextToFramePadding()
            theme.body("System Input Volume:")
            ImGui.sameLine()
            ImGui.setNextItemWidth(180f)
            if (ImGui.sliderFloat("##system_gain", sysVolArr, 0.0f, 1.0f, "%.2f")) {
                SystemAudioVolume.updateSystemVolume(sysVolArr[0])
            }
            if (ImGui.isItemHovered() && theme.tooltipsEnabled) {
                ImGui.setTooltip("System-level recording input volume.")
            }
            if (SystemAudioVolume.isMuted) {
                ImGui.sameLine()
                theme.bodyColored(1f, 0.3f, 0.3f, 1f, "[MUTED]")
            }
        }

        ImGui.spacing()
        audioEngine.rawHistory.copyTo(rawSamples)
        val rawColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.9f, 0.4f, 1.0f) // Neon Green
        OscilloscopeDrawer.drawBufferOscilloscope(session, "Raw Buffer", rawSamples, -1.0f, 1.0f, rawColor, 80f)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // 4. Sound-Derived CV Oscilloscopes
        // ---------------------------------------------------------------------
        theme.h2("Sound-Derived Control Voltages (CV)")
        ImGui.separator()
        ImGui.spacing()

        val availW = ImGui.getContentRegionAvailX()
        val numCols = if (availW >= 480f) 2 else 1

        if (ImGui.beginTable("##cv_osc_grid", numCols, ImGuiTableFlags.SizingStretchProp)) {
            for (i in 0 until numCols) {
                ImGui.tableSetupColumn("##col_$i", ImGuiTableColumnFlags.WidthStretch, 1f)
            }

            for (sig in cvSignals) {
                val history = session.cvRegistry.getHistory(sig.id) ?: continue
                ImGui.tableNextColumn()
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
                    55f
                )
                ImGui.spacing()
            }
            ImGui.endTable()
        }
    }
}

