package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.audio.ClockSource
import llm.slop.liquidlsd.link.AbletonLinkEngine

/**
 * Clock row header bar (Performance MASTER tab): clock-source pills [MAN | AUDIO], Ableton Link
 * status, BPM readout, 4-beat bar dots, and the tempo actions [TAP] [RESYNC] [/2] [x2] [-] [+].
 * Same actions as Preferences > Tempo & Sync ([TempoSyncPanel]) -- surfaced here because they're
 * pressed mid-set. The BPM itself is deliberately not a knob (one bump drifts the whole show).
 */
internal object PerformanceClockControls {

    fun draw(
        session: SessionContext,
        boxX1: Float,
        boxX2: Float,
        headerY: Float,
        headerH: Float
    ) {
        val pad = 6f
        val gap = 4f
        val audioEngine = session.audioEngine
        val currentClock = audioEngine.clockSource
        val dl = ImGui.getWindowDrawList()

        ImGui.setCursorScreenPos(boxX1 + pad, headerY)
        ImGui.beginGroup()

        // 1. Clock source pills [MAN] [AUDIO]
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            for ((source, label) in listOf(ClockSource.MANUAL to "MAN", ClockSource.AUDIO_TRACKER to "AUDIO")) {
                val isActive = currentClock == source
                ImGui.pushStyleColor(ImGuiCol.Button, if (isActive) ImGui.colorConvertFloat4ToU32(0.20f, 0.45f, 0.70f, 1f) else ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.7f))
                if (ImGui.button("$label##perf_clock_src_${source.name}", 50f, headerH)) {
                    audioEngine.clockSource = source
                    AppPreferencesStore.savePreferences()
                }
                ImGui.popStyleColor()
                itemTooltip("Clock source: ${source.displayName}.")
                ImGui.sameLine(0f, 2f)
            }
        }

        // 2. Ableton Link status (only while enabled) -- click opens Tempo & Sync preferences.
        if (AbletonLinkEngine.isEnabled) {
            ImGui.sameLine(0f, gap)
            val peers = AbletonLinkEngine.getNumPeers()
            if (peers > 0) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.15f, 0.60f, 0.75f, 1.0f)
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.75f, 0.55f, 0.15f, 1.0f)
            }
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                if (ImGui.button("LINK $peers##perf_clock_link", 56f, headerH)) {
                    PreferencesPanel.open(PreferencesPanel.Category.TEMPO_SYNC)
                }
            }
            ImGui.popStyleColor()
            itemTooltip("Ableton Link: $peers peer(s). Click to open Tempo & Sync preferences.")
        }

        // 3. BPM readout -- click opens Tempo & Sync preferences.
        ImGui.sameLine(0f, gap * 2f)
        val bpmText = "%.1f".format(audioEngine.getEstimatedBpm())
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f))
            if (ImGui.button("$bpmText BPM##perf_clock_bpm", 96f, headerH)) {
                PreferencesPanel.open(PreferencesPanel.Category.TEMPO_SYNC)
            }
            ImGui.popStyleColor()
        }
        itemTooltip("Current tempo (${currentClock.displayName}). Click to open Tempo & Sync preferences.")

        // 4. 4-beat bar dots
        ImGui.sameLine(0f, gap * 2f)
        val totalBeats = session.cvRegistry.getSynchronizedTotalBeats()
        val currentBeat = (((totalBeats.toLong() % 4) + 4) % 4).toInt()
        val dotR = 5f
        val dotGap = 7f
        val dotsX = ImGui.getCursorScreenPosX()
        val dotsCy = headerY + headerH / 2f
        for (i in 0..3) {
            val cx = dotsX + dotR + i * (dotR * 2f + dotGap)
            if (i == currentBeat) {
                val col = if (i == 0) ImGui.colorConvertFloat4ToU32(0.2f, 0.95f, 1.0f, 1f) else ImGui.colorConvertFloat4ToU32(0.9f, 0.95f, 0.4f, 1f)
                dl.addCircleFilled(cx, dotsCy, dotR, col)
            } else {
                dl.addCircle(cx, dotsCy, dotR, ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.40f, 0.6f), 0, 1.2f)
            }
        }
        ImGui.dummy(dotR * 2f * 4f + dotGap * 3f, headerH)
        itemTooltip("4/4 bar phase. Downbeat is cyan.")

        // 5. Tempo actions
        ImGui.sameLine(0f, gap * 2f)
        drawTapButton(session, headerH)

        ImGui.sameLine(0f, gap)
        if (ImGui.button("RESYNC##perf_clock_resync", 64f, headerH)) {
            audioEngine.resyncDownbeat()
        }
        itemTooltip("Snap the beat phase to the downbeat now.")

        ImGui.sameLine(0f, gap)
        if (ImGui.button("/2##perf_clock_half", 32f, headerH)) {
            audioEngine.halveTempo()
            AppPreferencesStore.savePreferences()
        }
        itemTooltip("Halve tempo (e.g. 140 -> 70 BPM).")

        ImGui.sameLine(0f, 2f)
        if (ImGui.button("x2##perf_clock_double", 32f, headerH)) {
            audioEngine.doubleTempo()
            AppPreferencesStore.savePreferences()
        }
        itemTooltip("Double tempo (e.g. 70 -> 140 BPM).")

        ImGui.sameLine(0f, gap)
        if (ImGui.button("-##perf_clock_nudge_down", 26f, headerH)) {
            audioEngine.nudgeTempo(-0.5f)
            AppPreferencesStore.savePreferences()
        }
        itemTooltip("Nudge tempo down 0.5 BPM.")

        ImGui.sameLine(0f, 2f)
        if (ImGui.button("+##perf_clock_nudge_up", 26f, headerH)) {
            audioEngine.nudgeTempo(0.5f)
            AppPreferencesStore.savePreferences()
        }
        itemTooltip("Nudge tempo up 0.5 BPM.")

        ImGui.endGroup()
    }

    /** [TAP] button: flashes on tap cadence; right-click for MIDI Learn on "Global/tapTempo". */
    private fun drawTapButton(session: SessionContext, headerH: Float) {
        val tapController = session.tapTempoController
        val tapFlash = tapController.getFlashIntensity()
        val tapCount = tapController.getActiveTapCount()
        val tapKey = "Global/tapTempo"
        val isMidiLearnTap = session.parametersState.isMidiTargetLearning(tapKey)
        val tapMapping = session.midiMappingManager.getMappingForParameter(tapKey)
        val tapMidiText = tapMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val label = if (tapCount > 0) "TAP [$tapCount]" else "TAP"
        val btnCol = when {
            tapFlash > 0.05f -> ImGui.colorConvertFloat4ToU32(0.95f, 0.75f, 0.15f, 1f)
            tapCount > 0 -> ImGui.colorConvertFloat4ToU32(0.75f, 0.50f, 0.10f, 0.9f)
            else -> ImGui.colorConvertFloat4ToU32(0.20f, 0.45f, 0.70f, 0.9f)
        }
        val tapX = ImGui.getCursorScreenPosX()
        val tapY = ImGui.getCursorScreenPosY()
        val tapW = 64f
        ImGui.pushStyleColor(ImGuiCol.Button, btnCol)
        if (ImGui.button("$label##perf_clock_tap", tapW, headerH)) {
            tapController.tap()
        }
        ImGui.popStyleColor()
        if (isMidiLearnTap) {
            ImGui.getWindowDrawList().addRect(tapX - 1f, tapY - 1f, tapX + tapW + 1f, tapY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_clock_tap_ctx")) {
            ImGui.textDisabled("Tap Tempo")
            ImGui.separator()
            if (isMidiLearnTap) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Tap Tempo)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(tapKey))
                }
            }
            if (tapMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(tapKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Tap tempo (hotkey: T).$tapMidiText In Audio Tracker mode, nudges the detected tempo and phase.\nRight-click for MIDI Learn.")
    }
}
