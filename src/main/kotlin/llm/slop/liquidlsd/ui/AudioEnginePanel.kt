package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.SignalState
import llm.slop.liquidlsd.audio.SystemAudioVolume
import llm.slop.liquidlsd.cv.CVRegistry

/**
 * Overlay panel that displays a real-time monitor for the audio engine:
 * - Live BPM readout with a flashing beat visualizer.
 * - Raw audio input oscilloscope.
 * - Oscilloscopes for all sound-derived Control Voltage (CV) signals.
 */
object AudioEnginePanel {

    private const val POPUP_ID = "Audio Engine##modal"
    private const val MODAL_W = 1080f
    private const val MODAL_MARGIN = 48f
    private const val MIN_MODAL_W = 640f
    private const val MIN_MODAL_H = 360f

    // Pre-allocated arrays to avoid runtime allocations
    private val rawSamples = FloatArray(1024)
    private val cvSamples = FloatArray(200)

    private val manualBpmArr = FloatArray(1)
    private val gainArr = FloatArray(1)
    private val sysVolArr = FloatArray(1)
    
    // Beat Detection UI arrays
    private val floorArr = IntArray(1)
    private val ceilArr = IntArray(1)
    private val resArr = FloatArray(1)
    private val winLenArr = FloatArray(1)
    private val pllArr = FloatArray(1)
    private val isLocked = imgui.type.ImBoolean()

    fun open() = ImGui.openPopup(POPUP_ID)

    fun draw(session: llm.slop.liquidlsd.SessionContext, displayWidth: Float, displayHeight: Float) {
        val modalW = MODAL_W.coerceAtMost((displayWidth - MODAL_MARGIN).coerceAtLeast(MIN_MODAL_W))
        val modalH = (displayHeight - MODAL_MARGIN).coerceAtLeast(MIN_MODAL_H)

        // Center the modal
        ImGui.setNextWindowPos(
            displayWidth * 0.5f, displayHeight * 0.5f,
            ImGuiCond.Always, 0.5f, 0.5f
        )
        ImGui.setNextWindowSize(modalW, modalH, ImGuiCond.Always)

        val flags = ImGuiWindowFlags.NoCollapse or
                    ImGuiWindowFlags.NoResize or
                    ImGuiWindowFlags.NoMove

        if (!ImGui.beginPopupModal(POPUP_ID, flags)) return

        // ---------------------------------------------------------------------
        // Header: Title & Info
        // ---------------------------------------------------------------------
        session.uiTheme.h2("${Icons.ACTIVITY} Audio Engine Monitor")
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // 2-Column Area
        // ---------------------------------------------------------------------
        if (ImGui.beginTable("##audio_layout_table", 2)) {
            ImGui.tableNextColumn()

        // BPM Sync & Flashing Beat Indicator
        val bpm = session.audioEngine.getEstimatedBpm()
        val totalBeats = session.cvRegistry.getSynchronizedTotalBeats()
        val beatPhase = totalBeats % 1.0
        val flashIntensity = if (beatPhase < 0.25) {
            (1.0 - (beatPhase / 0.25)).toFloat() // linear decay over 1/4 of a beat
        } else {
            0.0f
        }

        ImGui.alignTextToFramePadding()
        session.uiTheme.h3("BPM: ")
        ImGui.sameLine()
        
        // Pulse the BPM text color slightly on beat
        val r = 1.0f
        val g = 0.8f + 0.2f * (1.0f - flashIntensity)
        val b = 0.2f + 0.8f * (1.0f - flashIntensity)
        session.uiTheme.h3Colored(r, g, b, 1.0f, "%.1f".format(bpm))

        ImGui.sameLine(0f, 12f)
        
        // Beat flashing dot
        val indicatorSize = 14f
        val curX = ImGui.getCursorScreenPosX()
        val curY = ImGui.getCursorScreenPosY() + (ImGui.getTextLineHeight() - indicatorSize) / 2f
        ImGui.dummy(indicatorSize, indicatorSize)
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Real-time tempo estimate. Flashes on the detected beat phase.")
        }
        val dl = ImGui.getWindowDrawList()
        val indicatorCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.0f, 0.15f + 0.85f * flashIntensity)
        val borderCol = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.5f)
        dl.addCircleFilled(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, indicatorCol)
        dl.addCircle(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, borderCol, 16, 1.0f)

        ImGui.spacing()

        // Display tracking state
        val state = session.audioEngine.currentState
        val backend = session.audioEngine.getActiveBackendName()

        ImGui.alignTextToFramePadding()
        session.uiTheme.body("Sync State: ")
        ImGui.sameLine()
        when (state) {
            SignalState.SILENT -> session.uiTheme.bodyColored(0.5f, 0.5f, 0.5f, 1.0f, "SILENT")
            SignalState.ACTIVE -> session.uiTheme.bodyColored(0.2f, 0.9f, 0.4f, 1.0f, "ACTIVE")
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Active: Signal detected and tracking tempo. Silent: No input audio or level too low.")
        }

        ImGui.alignTextToFramePadding()
        session.uiTheme.body("Audio Driver: ")
        ImGui.sameLine()
        session.uiTheme.bodyColored(0.2f, 0.7f, 0.9f, 1.0f, backend)
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("The active audio input capture backend.")
        }

        ImGui.spacing()

        // Show inactive banner or options to switch to JACK if using Java Sound fallback
        if (!session.audioEngine.isActive()) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.3f, 0.3f, 1.0f)
            ImGui.textWrapped("Warning: Audio Engine is inactive. No audio source detected.")
            ImGui.popStyleColor()
            session.uiTheme.caption("You can enable JACK in Settings or run a JACK/PipeWire backend.")
            ImGui.spacing()
            if (ImGui.button("Retry JACK Connection", ImGui.getContentRegionAvailX(), 0f)) {
                Thread {
                    session.audioEngine.tryReconnect(force = true)
                }.start()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Attempts to reconnect to the JACK or PipeWire audio backend.")
            }
            ImGui.spacing()
        } else if (backend == "Java Sound") {
            if (ImGui.button("Switch to JACK Audio", ImGui.getContentRegionAvailX(), 0f)) {
                Thread {
                    session.audioEngine.tryReconnect(force = true)
                }.start()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Stops Java Sound and attempts to connect to a running JACK/PipeWire audio server.")
            }
            ImGui.spacing()
        }

        // MIDI status
        val midiCount = llm.slop.liquidlsd.midi.MidiEngine.getActiveDeviceCount()
        if (midiCount == 0) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f)
            ImGui.textWrapped("Warning: No MIDI devices detected. Connect a controller to map controls.")
            ImGui.popStyleColor()
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.2f, 0.9f, 0.4f, 1.0f)
            ImGui.textWrapped("MIDI Status: $midiCount active MIDI input device(s) connected.")
            ImGui.popStyleColor()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Displays the count of connected MIDI input controllers.")
        }
        ImGui.spacing()

        // Beat Sync Settings
        session.uiTheme.h3("${Icons.SETTINGS} Beat Sync Settings")

            ImGui.alignTextToFramePadding()
            session.uiTheme.body("Manual BPM:")
            ImGui.sameLine()
            ImGui.setNextItemWidth(180f)
            manualBpmArr[0] = session.audioEngine.manualBpm
            if (ImGui.sliderFloat("##manual_bpm", manualBpmArr, 40f, 200f, "%.1f")) {
                session.audioEngine.manualBpm = manualBpmArr[0]
                session.audioEngine.setBpmDirectly(manualBpmArr[0])
                session.uiTheme.saveSettings()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Set fallback tempo. Used when 'Lock to Manual BPM' is active or input signal is silent.")
            }

            ImGui.spacing()
            
            // -- New Beat Detection UI --
            session.uiTheme.h3("${Icons.ZAP} Auto Beat Detection")
            ImGui.spacing()
            
            val settings = session.audioEngine.beatDetector.settings
            
            if (ImGui.beginCombo("Mode", settings.mode.name)) {
                llm.slop.liquidlsd.audio.BeatDetectionMode.values().forEach { mode ->
                    val isSelected = settings.mode == mode
                    if (ImGui.selectable(mode.name, isSelected)) {
                        settings.mode = mode
                    }
                    if (isSelected) ImGui.setItemDefaultFocus()
                }
                ImGui.endCombo()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Select algorithm used for beat tracking (e.g., spectral energy flux).")
            }
            
            if (ImGui.beginCombo("Target", settings.target.name)) {
                llm.slop.liquidlsd.audio.AudioTarget.values().forEach { target ->
                    val isSelected = settings.target == target
                    if (ImGui.selectable(target.name, isSelected)) {
                        settings.target = target
                    }
                    if (isSelected) ImGui.setItemDefaultFocus()
                }
                ImGui.endCombo()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Choose which audio input channel to analyze (Left, Right, or Mixed Mono).")
            }

            ImGui.spacing()
            
                        floorArr[0] = settings.bpmSearchFloor
            if (ImGui.sliderInt("BPM Floor", floorArr, 40, 120)) { 
                settings.bpmSearchFloor = floorArr[0]
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Minimum limit for tempo estimation to prevent half-tempo octave tracking errors.")
            }
            
            ceilArr[0] = settings.bpmSearchCeiling
            if (ImGui.sliderInt("BPM Ceiling", ceilArr, 120, 240)) { 
                settings.bpmSearchCeiling = ceilArr[0]
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Maximum limit for tempo estimation to prevent double-tempo octave tracking errors.")
            }
            
            val winLenArr = FloatArray(1) { settings.analysisWindowLength }
            if (ImGui.sliderFloat("Analysis Length (s)", winLenArr, 1.0f, 8.0f, "%.1f")) {
                settings.analysisWindowLength = winLenArr[0]
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Duration of onset history buffer analyzed for autocorrelation and beat estimation.")
            }

            val thresholdArr = FloatArray(1) { settings.energyThreshold }
            if (ImGui.sliderFloat("Energy Threshold", thresholdArr, 1.0f, 3.0f, "%.2f")) {
                settings.energyThreshold = thresholdArr[0]
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Threshold multiplier for energy-difference trigger.")
            }

            val pllRateArr = FloatArray(1) { settings.pllAdaptationRate }
            if (ImGui.sliderFloat("PLL Adaptation", pllRateArr, 0.01f, 1.0f, "%.2f")) {
                settings.pllAdaptationRate = pllRateArr[0]
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("How quickly the PLL tracks tempo changes.")
            }

            val biquadQArr = FloatArray(1) { settings.biquadQ }
            if (ImGui.sliderFloat("Resonator Q", biquadQArr, 0.5f, 10.0f, "%.2f")) {
                settings.biquadQ = biquadQArr[0]
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Quality factor (resonance) for the Complex Domain biquad filter.")
            }
            
            if (!session.audioEngine.beatDetector.isTargetLevelSufficient) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f)
                ImGui.textWrapped("${Icons.ALERT} Low Signal: Not enough energy in the selected target band (${settings.target.name}) for reliable analysis. Consider switching the Target to HIGH or UNFILTERED if playing from small laptop speakers.")
                ImGui.popStyleColor()
            }
            
            ImGui.spacing()
            session.uiTheme.body("Presets:")
            ImGui.sameLine()
            if (ImGui.button("High Accuracy")) session.audioEngine.beatDetector.applyPreset(llm.slop.liquidlsd.audio.BeatDetectionSettings.highAccuracy())
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Apply configuration tuned for precise tempo detection (larger FFT window).")
            }
            ImGui.sameLine()
            if (ImGui.button("Balanced")) session.audioEngine.beatDetector.applyPreset(llm.slop.liquidlsd.audio.BeatDetectionSettings.balanced())
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Apply configuration balanced between latency and precision.")
            }
            ImGui.sameLine()
            if (ImGui.button("Eco")) session.audioEngine.beatDetector.applyPreset(llm.slop.liquidlsd.audio.BeatDetectionSettings.eco())
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Apply configuration with low CPU usage (smaller FFT window).")
            }
            
            ImGui.spacing()
            isLocked.set(session.audioEngine.isBpmLocked)
            if (ImGui.checkbox("Lock to Manual BPM", isLocked)) {
                session.audioEngine.isBpmLocked = isLocked.get()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Ignore incoming audio tempo and lock entirely to the Manual BPM slider.")
            }

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // 1. Raw Audio Oscilloscope
            session.uiTheme.h3("Raw Audio Input")

            gainArr[0] = session.audioEngine.inputGain
            ImGui.alignTextToFramePadding()
            session.uiTheme.body("Input Level Gain:")
            ImGui.sameLine()
            ImGui.setNextItemWidth(180f)
            if (ImGui.sliderFloat("##input_gain", gainArr, 0.0f, 10.0f, "%.2fx")) {
                session.audioEngine.inputGain = gainArr[0]
                session.uiTheme.saveSettings()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Pre-amplify the incoming audio signal before analysis and oscilloscope display.")
            }
            ImGui.sameLine()
            if (ImGui.button("Reset##gain")) {
                session.audioEngine.inputGain = 1.0f
                session.uiTheme.saveSettings()
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Reset input gain to 1.0x.")
            }
            ImGui.spacing()

            // System input volume slider
            if (SystemAudioVolume.isSupported) {
                SystemAudioVolume.queryAsync()
                sysVolArr[0] = SystemAudioVolume.systemInputVolume
                ImGui.alignTextToFramePadding()
                session.uiTheme.body("System Input Volume:")
                ImGui.sameLine()
                ImGui.setNextItemWidth(180f)
                if (ImGui.sliderFloat("##system_gain", sysVolArr, 0.0f, 1.0f, "%.2f")) {
                    SystemAudioVolume.updateSystemVolume(sysVolArr[0])
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("System-level recording input volume (operating system volume control).")
                }
                if (SystemAudioVolume.isMuted) {
                    ImGui.sameLine()
                    session.uiTheme.bodyColored(1f, 0.3f, 0.3f, 1f, "[MUTED]")
                }
                ImGui.spacing()
            } else {
                ImGui.alignTextToFramePadding()
                session.uiTheme.body("System Input Volume:")
                ImGui.sameLine()
                session.uiTheme.caption("(System control not supported on this OS)")
                ImGui.spacing()
            }

            session.audioEngine.rawHistory.copyTo(rawSamples)
            val rawColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.9f, 0.4f, 1.0f) // Neon Green
            OscilloscopeDrawer.drawBufferOscilloscope(session, "Raw Buffer", rawSamples, -1.0f, 1.0f, rawColor, 90f)
            
            ImGui.spacing()

            // Column 2: CV Oscilloscopes
            ImGui.tableNextColumn()

            // 2. Sound Derived CV Oscilloscopes
            session.uiTheme.h3("Sound-Derived CVs")
            ImGui.spacing()

            val cvSignals = listOf(
                Triple("audio_amp", "Amplitude (RMS)", ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, 1.0f)), // Neon Cyan
                Triple("audio_bass", "Bass Band (Low-pass)", ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.6f, 1.0f)), // Neon Pink
                Triple("audio_mid", "Mid Band (Band-pass)", ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.1f, 1.0f)), // Neon Orange
                Triple("audio_high", "High Band (High-pass)", ImGui.colorConvertFloat4ToU32(0.1f, 0.9f, 0.8f, 1.0f)), // Neon Teal
                Triple("beatSine", "Beat Sine (Oscillator)", ImGui.colorConvertFloat4ToU32(0.6f, 0.4f, 1.0f, 1.0f)), // Neon Purple
                Triple("trigger_onset", "Onset Signal", ImGui.colorConvertFloat4ToU32(0.9f, 0.8f, 0.1f, 1.0f)), // Neon Yellow
                Triple("trigger_accent", "Accent Level (Decay)", ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.3f, 1.0f)) // Neon Red
            )

            for ((id, title, color) in cvSignals) {
                val history = session.cvRegistry.getHistory(id)
                if (history != null) {
                    history.copyTo(cvSamples)
                    OscilloscopeDrawer.drawBufferOscilloscope(session, title,
                        cvSamples,
                        0.0f,
                        2.0f,
                        color,
                        60f
                    )
                    ImGui.spacing()
                }
            }

            ImGui.endTable()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // Footer: Close Button
        // ---------------------------------------------------------------------
        val closeW = 120f
        ImGui.setCursorPosX(ImGui.getWindowContentRegionMinX() + (ImGui.getContentRegionAvailX() - closeW) * 0.5f)
        if (ImGui.button("Close", closeW, 0f)) {
            ImGui.closeCurrentPopup()
        }

        ImGui.spacing()
        ImGui.endPopup()
    }
}
