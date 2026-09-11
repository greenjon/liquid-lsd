package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImBoolean
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.audio.AudioTarget
import llm.slop.liquidlsd.audio.BeatDetectionSettings
import llm.slop.liquidlsd.audio.ClockSource
import llm.slop.liquidlsd.link.AbletonLinkEngine
import llm.slop.liquidlsd.link.LinkSyncManager

/**
 * Resolume-inspired Master Tempo & Ableton Link control deck.
 * Provides unified, accessible controls for manual BPM, tap tempo, beat phase resync,
 * audio beat tracking calibration, and Ableton Link network session synchronization.
 */
object TempoSyncPanel {

    private val audioTargets = AudioTarget.values()

    private fun itemTooltip(text: String) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text)
        }
    }

    fun drawContent(session: SessionContext) {
        val theme = session.uiTheme
        val audioEngine = session.audioEngine
        val linkEngine = AbletonLinkEngine
        val isLinkEnabled = linkEngine.isEnabled
        val currentClock = audioEngine.clockSource

        val currentBpm = audioEngine.getEstimatedBpm()
        val totalBeats = session.cvRegistry.getSynchronizedTotalBeats()
        val beatPhase = totalBeats % 1.0
        val flashIntensity = if (beatPhase < 0.25) {
            (1.0 - (beatPhase / 0.25)).toFloat()
        } else {
            0.0f
        }

        val tapController = session.tapTempoController
        val tapFlash = tapController.getFlashIntensity()
        val tapCount = tapController.getActiveTapCount()

        // ---------------------------------------------------------------------
        // 1. MASTER TEMPO DECK (Resolume-style Transport Bar)
        // ---------------------------------------------------------------------
        theme.h2("${Icons.ACTIVITY} Master Tempo Deck")
        ImGui.separator()
        ImGui.spacing()

        // Top Row: Big BPM Readout + Beat Phase Indicator + 4-Beat Bar Dots
        ImGui.alignTextToFramePadding()
        theme.h1("BPM: ")
        ImGui.sameLine()

        val r = 1.0f
        val g = if (tapFlash > 0.05f) 0.95f else (0.8f + 0.2f * (1.0f - flashIntensity))
        val b = if (tapFlash > 0.05f) 0.20f else (0.2f + 0.8f * (1.0f - flashIntensity))
        theme.h1Colored(r, g, b, 1.0f, "%.1f".format(currentBpm))
        ImGui.sameLine(0f, 16f)

        // Beat pulse dot
        val indicatorSize = 18f
        val curX = ImGui.getCursorScreenPosX()
        val curY = ImGui.getCursorScreenPosY() + (ImGui.getTextLineHeight() - indicatorSize) / 2f
        ImGui.dummy(indicatorSize, indicatorSize)
        itemTooltip("Real-time beat phase. Flashes on downbeat.")
        val dl = ImGui.getWindowDrawList()
        val indicatorCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.65f, 0.1f, 0.2f + 0.8f * flashIntensity)
        val borderCol = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.6f)
        dl.addCircleFilled(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, indicatorCol)
        dl.addCircle(curX + indicatorSize / 2f, curY + indicatorSize / 2f, indicatorSize / 2f, borderCol, 16, 1.2f)

        ImGui.sameLine(0f, 20f)

        // 4-Beat Bar Indicator Dots
        val dotR = 5f
        val dotGap = 8f
        val dotsStartX = ImGui.getCursorScreenPosX()
        val dotsStartY = ImGui.getCursorScreenPosY() + (ImGui.getTextLineHeight() - dotR * 2f) / 2f
        val currentBeat = (((totalBeats.toLong() % 4) + 4) % 4).toInt()
        val beatFract = (totalBeats - kotlin.math.floor(totalBeats)).toFloat()

        for (i in 0..3) {
            val cx = dotsStartX + dotR + (i * (dotR * 2f + dotGap))
            val cy = dotsStartY + dotR
            if (i == currentBeat) {
                val intensity = (1.0f - beatFract * 0.35f).coerceIn(0.65f, 1.0f)
                val col = if (i == 0) {
                    ImGui.colorConvertFloat4ToU32(0.2f * intensity, 0.95f * intensity, 1.0f * intensity, 1.0f)
                } else {
                    ImGui.colorConvertFloat4ToU32(0.9f * intensity, 0.95f * intensity, 0.4f * intensity, 1.0f)
                }
                dl.addCircleFilled(cx, cy, dotR, col)
            } else {
                val dimCol = ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.40f, 0.6f)
                dl.addCircle(cx, cy, dotR, dimCol, 0, 1.2f)
            }
        }
        ImGui.dummy((dotR * 2f * 4f) + (dotGap * 3f) + 4f, ImGui.getTextLineHeight())
        itemTooltip("4/4 Bar phase alignment meter.")

        ImGui.spacing()

        // ---------------------------------------------------------------------
        // Action Buttons: [TAP] [RESYNC] [/2] [*2] [-0.5] [+0.5]
        // ---------------------------------------------------------------------
        val tapLabel = when {
            tapCount == 1 -> "TAP [1]"
            tapCount >= 2 -> "TAP [$tapCount]"
            currentClock == ClockSource.AUDIO_TRACKER -> "TAP NUDGE"
            else -> "TAP TEMPO"
        }

        // Tap Button styling: flash bright amber/gold on tap cadence
        if (tapFlash > 0.05f) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.95f, 0.75f, 0.15f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1.0f, 0.85f, 0.25f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1.0f, 0.90f, 0.35f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.05f, 0.05f, 0.05f, 1.0f)
        } else if (tapCount > 0) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.75f, 0.50f, 0.10f, 0.9f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.85f, 0.60f, 0.15f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.95f, 0.70f, 0.20f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 1.0f, 1.0f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.45f, 0.70f, 0.9f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.25f, 0.55f, 0.85f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.30f, 0.65f, 0.95f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 1.0f, 1.0f)
        }

        if (ImGui.button("$tapLabel##tempo_tap_btn", 110f, 32f)) {
            tapController.tap()
        }
        ImGui.popStyleColor(4)
        itemTooltip("Tap to rhythmically calculate BPM (Hotkey: T). In Audio Tracker mode, nudges detected tempo and phase.")

        ImGui.sameLine(0f, 10f)

        // RESYNC Button (Resolume-style instant downbeat snap)
        if (ImGui.button("RESYNC##tempo_resync_btn", 85f, 32f)) {
            audioEngine.resyncDownbeat()
        }
        itemTooltip("Instantly aligns beat phase to 1.0 downbeat and zeros phase slew.")

        ImGui.sameLine(0f, 10f)

        // Half tempo (/2)
        if (ImGui.button("/2##tempo_half_btn", 46f, 32f)) {
            audioEngine.halveTempo()
            theme.saveSettings()
        }
        itemTooltip("Halve tempo (e.g. 140 -> 70 BPM).")

        ImGui.sameLine(0f, 6f)

        // Double tempo (*2)
        if (ImGui.button("*2##tempo_double_btn", 46f, 32f)) {
            audioEngine.doubleTempo()
            theme.saveSettings()
        }
        itemTooltip("Double tempo (e.g. 70 -> 140 BPM).")

        ImGui.sameLine(0f, 10f)

        // Fine Pitch Nudge: [-0.5] and [+0.5]
        if (ImGui.button("-0.5##tempo_nudge_down", 50f, 32f)) {
            audioEngine.nudgeTempo(-0.5f)
            theme.saveSettings()
        }
        itemTooltip("Nudge tempo down by 0.5 BPM.")

        ImGui.sameLine(0f, 6f)

        if (ImGui.button("+0.5##tempo_nudge_up", 50f, 32f)) {
            audioEngine.nudgeTempo(0.5f)
            theme.saveSettings()
        }
        itemTooltip("Nudge tempo up by 0.5 BPM.")

        ImGui.spacing()

        // Full Interactive BPM Slider
        val sliderBoxW = 42f
        val sliderThemeColor = if (currentClock == ClockSource.MANUAL) {
            ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f)
        } else {
            ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f)
        }

        CustomRangeSlider.drawCompactSlider(
            session = session,
            label = "Master BPM",
            currentValue = if (currentClock == ClockSource.MANUAL) audioEngine.manualBpm else currentBpm,
            minLimit = 40f,
            maxLimit = 240f,
            defaultValue = 120f,
            formatValue = { "%.1f BPM".format(it) },
            idPrefix = "tempo_sync_master_bpm",
            themeColor = sliderThemeColor,
            showCurrentLabel = false,
            customBoxWidth = sliderBoxW,
            onValueChanged = { newVal ->
                audioEngine.manualBpm = newVal
                audioEngine.setBpmDirectly(newVal)
                theme.saveSettings()
            }
        )

        ImGui.spacing()

        // Quick Preset Buttons
        ImGui.alignTextToFramePadding()
        theme.caption("Presets: ")
        val presets = floatArrayOf(120.0f, 128.0f, 140.0f, 174.0f)
        for (p in presets) {
            ImGui.sameLine()
            if (ImGui.button("${p.toInt()} BPM##preset_$p", 70f, 22f)) {
                audioEngine.setBpmDirectly(p)
                theme.saveSettings()
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // 2. CLOCK MODE SELECTION: Manual vs Audio Beat Tracker
        // ---------------------------------------------------------------------
        theme.h2("${Icons.SETTINGS} Beat Clock Mode")
        ImGui.separator()
        ImGui.spacing()

        val isManual = currentClock == ClockSource.MANUAL
        if (ImGui.radioButton("Manual Fixed Tempo##clock_manual", isManual)) {
            audioEngine.clockSource = ClockSource.MANUAL
            theme.saveSettings()
        }
        itemTooltip("Clock is driven directly by the manual BPM slider, tap tempo, or Ableton Link.")

        ImGui.sameLine(0f, 24f)

        val isAudioTracker = currentClock == ClockSource.AUDIO_TRACKER
        if (ImGui.radioButton("Audio Beat Tracker (Auto)##clock_audio", isAudioTracker)) {
            audioEngine.clockSource = ClockSource.AUDIO_TRACKER
            theme.saveSettings()
        }
        itemTooltip("Clock tracks real-time rhythmic transients and tempo directly from the incoming audio stream.")

        ImGui.spacing()

        // Sub-panel: Audio Beat Tracker Settings (revealed when Audio Beat Tracker is selected)
        if (isAudioTracker) {
            val detectorSettings = audioEngine.beatDetector.settings
            val isAudioActive = audioEngine.isActive()

            if (!isAudioActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.4f, 0.4f, 1.0f)
                ImGui.textWrapped("${Icons.ALERT} Audio Engine is inactive. Beat tracker is coasting on flywheel momentum. Configure input devices in Audio Hardware settings.")
                ImGui.popStyleColor()
                ImGui.spacing()
            } else if (!audioEngine.beatDetector.isTargetLevelSufficient) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.65f, 0.1f, 1.0f)
                ImGui.textWrapped("${Icons.ALERT} Low Signal: Insufficient energy in target band (${detectorSettings.target.name}). Coasting on flywheel.")
                ImGui.popStyleColor()
                ImGui.spacing()
            }

            // Beat Tracker Confidence Meter
            val confidence = LinkSyncManager.confidence
            val confPercent = LinkSyncManager.confidencePercent
            ImGui.alignTextToFramePadding()
            theme.body("Tracker Confidence: ")
            ImGui.sameLine()
            val (cr, cg, cb) = when {
                confidence >= 0.70f -> Triple(0.2f, 0.9f, 0.4f)
                confidence >= 0.40f -> Triple(0.9f, 0.8f, 0.2f)
                else -> Triple(0.9f, 0.3f, 0.3f)
            }
            theme.bodyColored(cr, cg, cb, 1.0f, "$confPercent%")
            ImGui.sameLine(0f, 12f)
            ImGui.progressBar(confidence, 150f, 16f, "")
            itemTooltip("Rhythmic tracking stability metric calculated from FFT spectral flux onsets.")

            ImGui.spacing()

            // Target Frequency Band
            theme.body("Target Band:")
            ImGui.sameLine()
            ImGui.setNextItemWidth(150f)
            if (ImGui.beginCombo("##BeatDetectionTargetCombo", detectorSettings.target.name)) {
                for (target in audioTargets) {
                    val isSelected = detectorSettings.target == target
                    if (ImGui.selectable(target.name, isSelected)) {
                        detectorSettings.target = target
                        theme.saveSettings()
                    }
                    if (isSelected) ImGui.setItemDefaultFocus()
                }
                ImGui.endCombo()
            }
            itemTooltip("Select frequency band for primary onset detection (LOW/Kick, MID/Snare, HIGH/Hi-hat, or UNFILTERED).")

            ImGui.sameLine(0f, 20f)

            // Detection Presets
            theme.body("Presets:")
            ImGui.sameLine()
            if (ImGui.button("High Accuracy##acc_btn")) {
                audioEngine.beatDetector.applyPreset(BeatDetectionSettings.highAccuracy())
                theme.saveSettings()
            }
            itemTooltip("Tuned for precise tempo detection.")

            ImGui.sameLine()
            if (ImGui.button("Balanced##bal_btn")) {
                audioEngine.beatDetector.applyPreset(BeatDetectionSettings.balanced())
                theme.saveSettings()
            }
            itemTooltip("Balanced between tracking reactivity and stability.")

            ImGui.sameLine()
            if (ImGui.button("Eco##eco_btn")) {
                audioEngine.beatDetector.applyPreset(BeatDetectionSettings.eco())
                theme.saveSettings()
            }
            itemTooltip("Relaxed inertia for lower CPU usage.")

            ImGui.spacing()

            // BPM Search Range Dual-Headed Slider
            val beatThemeCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f)
            CustomRangeSlider.drawCustomRangeSlider(
                session = session,
                label = "BPM Search Range",
                currentValue = currentBpm,
                currentMin = detectorSettings.bpmSearchFloor.toFloat(),
                currentMax = detectorSettings.bpmSearchCeiling.toFloat(),
                minLimit = 40f,
                maxLimit = 240f,
                isRandomizable = true,
                showControls = false,
                defaultValue = 40f,
                formatValue = { "${it.toInt()}" },
                idPrefix = "tempo_sync_bpm_range",
                themeColor = beatThemeCol,
                showCurrentLabel = false,
                customBoxWidth = sliderBoxW,
                onRangeChanged = { nextMin, nextMax ->
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    detectorSettings.bpmSearchFloor = safeMin.toInt()
                    detectorSettings.bpmSearchCeiling = safeMax.toInt()
                    theme.saveSettings()
                }
            )
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ---------------------------------------------------------------------
        // 3. ABLETON LINK SECTION (Resolume-style Link deck)
        // ---------------------------------------------------------------------
        theme.h2("${Icons.ACTIVITY} Ableton Link")
        ImGui.separator()
        ImGui.spacing()

        val linkState = ImBoolean(isLinkEnabled)
        if (ImGui.checkbox("Enable Ableton Link##tempo_link_toggle", linkState)) {
            val enabled = linkState.get()
            linkEngine.setEnabled(enabled)
            theme.saveSettings()
        }
        itemTooltip("Synchronize beat timeline, tempo, and downbeat phase across the local network with Ableton Live, Resolume, Traktor, etc.")

        if (isLinkEnabled) {
            val peers = linkEngine.getNumPeers()
            val backendName = linkEngine.getActiveBackendName()

            ImGui.sameLine(0f, 20f)
            if (peers > 0) {
                theme.bodyColored(0.2f, 0.9f, 0.4f, 1.0f, "${Icons.ACTIVITY} $peers peer(s) connected")
            } else {
                theme.bodyColored(0.9f, 0.7f, 0.2f, 1.0f, "${Icons.REFRESH} 0 peers (searching network...)")
            }

            ImGui.sameLine(0f, 20f)
            theme.caption("Driver: ")
            ImGui.sameLine()
            theme.captionColored(0.6f, 0.8f, 1.0f, 1.0f, backendName)

            ImGui.spacing()

            // Link Quantum Selector
            theme.body("Link Quantum:")
            ImGui.sameLine()
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
            itemTooltip("Beat boundary interval for quantum phase alignment.")

            ImGui.spacing()

            // Transport Sync Checkbox
            val ssSync = ImBoolean(linkEngine.isStartStopSyncEnabled())
            if (ImGui.checkbox("Enable Start/Stop Transport Sync##link_transport_sync", ssSync)) {
                linkEngine.setStartStopSyncEnabled(ssSync.get())
                theme.saveSettings()
            }
            itemTooltip("Synchronize play/pause transport commands across connected Ableton Link peers.")

            ImGui.spacing()
            theme.caption("When Link is active, tempo and downbeats stay phase-aligned with all peers on your local Wi-Fi or Ethernet.")
        }
    }
}
