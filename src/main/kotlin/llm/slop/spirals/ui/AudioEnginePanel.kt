package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import llm.slop.spirals.audio.AudioEngine
import llm.slop.spirals.cv.CVRegistry

/**
 * Overlay panel that displays a real-time monitor for the audio engine:
 * - Live BPM readout with a flashing beat visualizer.
 * - Raw audio input oscilloscope.
 * - Oscilloscopes for all sound-derived Control Voltage (CV) signals.
 */
object AudioEnginePanel {

    private const val POPUP_ID = "Audio Engine##modal"
    private const val MODAL_W  = 540f  // width of the overlay

    // Pre-allocated arrays to avoid runtime allocations
    private val rawSamples = FloatArray(1024)
    private val cvSamples = FloatArray(200)

    fun open() = ImGui.openPopup(POPUP_ID)

    fun draw(displayWidth: Float, displayHeight: Float) {
        val modalH = displayHeight * 0.9f

        // Center the modal
        ImGui.setNextWindowPos(
            displayWidth * 0.5f, displayHeight * 0.5f,
            ImGuiCond.Always, 0.5f, 0.5f
        )
        ImGui.setNextWindowSize(MODAL_W, modalH, ImGuiCond.Always)

        val flags = ImGuiWindowFlags.NoCollapse or
                    ImGuiWindowFlags.NoResize or
                    ImGuiWindowFlags.NoMove

        if (!ImGui.beginPopupModal(POPUP_ID, flags)) return

        // ─────────────────────────────────────────────────────────────────────
        // Header: Title & Info
        // ─────────────────────────────────────────────────────────────────────
        UITheme.h2("Audio Engine Monitor")
        ImGui.separator()
        ImGui.spacing()

        // BPM Sync & Flashing Beat Indicator
        val bpm = AudioEngine.getEstimatedBpm()
        val totalBeats = CVRegistry.getSynchronizedTotalBeats()
        val beatPhase = totalBeats % 1.0
        val flashIntensity = if (beatPhase < 0.25) {
            (1.0 - (beatPhase / 0.25)).toFloat() // linear decay over 1/4 of a beat
        } else {
            0.0f
        }

        ImGui.alignTextToFramePadding()
        UITheme.h3("BPM: ")
        ImGui.sameLine()
        
        // Pulse the BPM text color slightly on beat
        val r = 1.0f
        val g = 0.8f + 0.2f * (1.0f - flashIntensity)
        val b = 0.2f + 0.8f * (1.0f - flashIntensity)
        UITheme.h3Colored(r, g, b, 1.0f, "%.1f".format(bpm))

        ImGui.sameLine(0f, 12f)
        
        // Beat flashing dot
        val indicatorSize = 14f
        val curX = ImGui.getCursorScreenPosX()
        val curY = ImGui.getCursorScreenPosY() + (ImGui.getTextLineHeight() - indicatorSize) / 2f
        ImGui.dummy(indicatorSize, indicatorSize)
        val dl = ImGui.getWindowDrawList()
        val indicatorCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.0f, 0.15f + 0.85f * flashIntensity)
        val borderCol = ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.5f)
        dl.addCircleFilled(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, indicatorCol)
        dl.addCircle(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, borderCol, 16, 1.0f)

        ImGui.spacing()

        // Show inactive banner if JACK is not running
        if (!AudioEngine.isActive()) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.3f, 0.3f, 1.0f)
            ImGui.textWrapped("Warning: Audio Engine is inactive. No audio source detected.")
            ImGui.popStyleColor()
            UITheme.caption("You can enable JACK in Settings or run a JACK/PipeWire backend.")
            ImGui.spacing()
            if (ImGui.button("Retry JACK Connection", ImGui.getContentRegionAvailX(), 0f)) {
                Thread {
                    AudioEngine.tryReconnect()
                }.start()
            }
            ImGui.spacing()
        }

        // MIDI status
        val midiCount = llm.slop.spirals.midi.MidiEngine.getActiveDeviceCount()
        if (midiCount == 0) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f)
            ImGui.textWrapped("Warning: No MIDI devices detected. Connect a controller to map controls.")
            ImGui.popStyleColor()
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.2f, 0.9f, 0.4f, 1.0f)
            ImGui.textWrapped("MIDI Status: $midiCount active MIDI input device(s) connected.")
            ImGui.popStyleColor()
        }
        ImGui.spacing()

        // ─────────────────────────────────────────────────────────────────────
        // Scrollable Oscilloscopes Area
        // ─────────────────────────────────────────────────────────────────────
        // Use a negative height to tell ImGui to auto-size the child to fill all remaining
        // vertical space except for the 50px needed for the footer.
        if (ImGui.beginChild("##oscopes_scroll", 0f, -50f, true)) {

            // 1. Raw Audio Oscilloscope
            UITheme.h3("Raw Audio Input")
            AudioEngine.rawHistory.copyTo(rawSamples)
            val rawColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.9f, 0.4f, 1.0f) // Neon Green
            drawCustomOscilloscope("Raw Buffer", rawSamples, -1.0f, 1.0f, rawColor, 90f)
            
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // 2. Sound Derived CV Oscilloscopes
            UITheme.h3("Sound-Derived CVs")
            ImGui.spacing()

            val cvSignals = listOf(
                Triple("amp", "Amplitude (RMS)", ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, 1.0f)), // Neon Cyan
                Triple("bass", "Bass Band (Low-pass)", ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.6f, 1.0f)), // Neon Pink
                Triple("mid", "Mid Band (Band-pass)", ImGui.colorConvertFloat4ToU32(1.0f, 0.6f, 0.1f, 1.0f)), // Neon Orange
                Triple("high", "High Band (High-pass)", ImGui.colorConvertFloat4ToU32(0.1f, 0.9f, 0.8f, 1.0f)), // Neon Teal
                Triple("beatSine", "Beat Sine (Oscillator)", ImGui.colorConvertFloat4ToU32(0.6f, 0.4f, 1.0f, 1.0f)), // Neon Purple
                Triple("onset", "Onset Signal", ImGui.colorConvertFloat4ToU32(0.9f, 0.8f, 0.1f, 1.0f)), // Neon Yellow
                Triple("accent", "Accent Level (Decay)", ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.3f, 1.0f)) // Neon Red
            )

            for ((id, title, color) in cvSignals) {
                val history = CVRegistry.getHistory(id)
                if (history != null) {
                    history.copyTo(cvSamples)
                    drawCustomOscilloscope(
                        title,
                        cvSamples,
                        0.0f,
                        2.0f,
                        color,
                        60f
                    )
                    ImGui.spacing()
                }
            }

            ImGui.endChild()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ─────────────────────────────────────────────────────────────────────
        // Footer: Close Button
        // ─────────────────────────────────────────────────────────────────────
        val closeW = 120f
        ImGui.setCursorPosX((MODAL_W - 32f - closeW) * 0.5f + ImGui.getWindowContentRegionMinX())
        if (ImGui.button("Close", closeW, 0f)) {
            ImGui.closeCurrentPopup()
        }

        ImGui.spacing()
        ImGui.endPopup()
    }

    /**
     * Draws a highly customized, styled oscilloscope in an ImGui draw list.
     */
    private fun drawCustomOscilloscope(
        title: String,
        samples: FloatArray,
        minVal: Float,
        maxVal: Float,
        lineColor: Int,
        height: Float
    ) {
        val w = ImGui.getContentRegionAvailX()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        // Reserve display box space
        ImGui.dummy(w, height)

        val dl = ImGui.getWindowDrawList()

        // Background
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + height, bgCol, 4f)

        // Grid lines
        val range = maxVal - minVal
        val zeroY = if (minVal <= 0f && maxVal >= 0f) {
            startY + height * (maxVal / range)
        } else {
            startY + height / 2f
        }
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.10f, 0.10f, 0.10f, 0.4f)

        // Center / Zero line
        dl.addLine(startX, zeroY, startX + w, zeroY, gridColCenter, 1.5f)
        // Top and bottom boundaries
        dl.addLine(startX, startY + 4f, startX + w, startY + 4f, gridColFaint, 1.0f)
        dl.addLine(startX, startY + height - 4f, startX + w, startY + height - 4f, gridColFaint, 1.0f)

        // Vertical division lines (4 sections)
        val numDivisions = 4
        for (i in 1 until numDivisions) {
            val gridX = startX + (w * i / numDivisions)
            dl.addLine(gridX, startY, gridX, startY + height, gridColFaint, 1.0f)
        }

        // Draw waveform lines
        if (samples.isNotEmpty()) {
            val stepX = w / (samples.size - 1)
            val usableHeight = height - 8f

            for (i in 0 until samples.size - 1) {
                val val1 = samples[i].coerceIn(minVal, maxVal)
                val val2 = samples[i + 1].coerceIn(minVal, maxVal)

                val x1 = startX + i * stepX
                val normVal1 = (val1 - minVal) / range
                val normVal2 = (val2 - minVal) / range

                // Calculate Y coordinates (subtracting from bottom boundary)
                val y1 = startY + height - 4f - normVal1 * usableHeight
                val x2 = startX + (i + 1) * stepX
                val y2 = startY + height - 4f - normVal2 * usableHeight

                dl.addLine(x1, y1, x2, y2, lineColor, 2.0f)
            }
        }

        // Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.16f, 0.16f, 0.16f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + height, borderCol, 4f)

        // Axis boundary labels
        ImGui.setCursorScreenPos(startX + 6f, startY + 3f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.1f".format(maxVal))

        ImGui.setCursorScreenPos(startX + 6f, startY + height - 15f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.1f".format(minVal))

        // Left-aligned chart title, offset slightly to not overlap with upper boundary label
        ImGui.setCursorScreenPos(startX + 45f, startY + 3f)
        UITheme.captionColored(0.85f, 0.85f, 0.85f, 0.9f, title)

        // Reset cursor location
        ImGui.setCursorScreenPos(startX, startY + height)
    }
}