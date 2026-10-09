package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.midi.MidiLearnTarget
import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.audio.ClockSource
import llm.slop.liquidlsd.link.AbletonLinkEngine

/**
 * Clock row (Performance MASTER tab) controls left of the knobs, beside the CLOCK title badge
 * drawn by [PerformanceMatrixPanel]. Line 1: clock-source pills [MAN | AUDIO], Ableton Link
 * status, BPM readout, 4-beat bar dots. Line 2: tempo actions [TAP] [RESYNC] [/2] [x2] [-] [+].
 * Same actions as Preferences > Tempo & Sync ([TempoSyncPanel]) -- surfaced here because they're
 * pressed mid-set. The BPM itself is deliberately not a knob (one bump drifts the whole show); the Twister's clock knob ([ClockKnobFeed]) only shows it and taps.
 */
internal object PerformanceClockControls {

    // Per-frame strings/colors are precomputed or cached by value (the render path must not allocate).
    private val SOURCES = arrayOf(ClockSource.MANUAL, ClockSource.AUDIO_TRACKER)
    private val SOURCE_LABELS = arrayOf("MAN", "AUDIO")
    private val SOURCE_IDS = Array(SOURCES.size) { "${SOURCE_LABELS[it]}##perf_clock_src_${SOURCES[it].name}" }
    private val SOURCE_TIPS = Array(SOURCES.size) { "Clock source: ${SOURCES[it].displayName}." }
    private val BPM_TIPS = Array(ClockSource.values().size) {
        "Current tempo (${ClockSource.values()[it].displayName}). Click to open Tempo & Sync preferences."
    }

    private val LINK_NO_PEERS_INK = TangoPalette.inkFor(TangoPalette.LINK_NO_PEERS)
    private val TAP_FLASH_INK = TangoPalette.inkFor(TangoPalette.TAP_FLASH)
    private val TAP_COUNTING_INK = TangoPalette.inkFor(TangoPalette.TAP_COUNTING)

    private var linkPeers = -1
    private var linkLabel = ""
    private var linkTip = ""
    private var bpmTenths = Int.MIN_VALUE
    private var bpmLabel = ""
    private var tapCountShown = -1
    private var tapLabel = "TAP##perf_clock_tap"
    private var tapMapping: Any? = null
    private var tapTip = ""

    fun draw(
        session: SessionContext,
        startX: Float,
        headerY: Float,
        row2Y: Float,
        headerH: Float
    ) {
        val gap = 4f
        val audioEngine = session.audioEngine
        val currentClock = audioEngine.clockSource
        val dl = ImGui.getWindowDrawList()

        ImGui.setCursorScreenPos(startX, headerY)
        ImGui.beginGroup()

        // 1. Clock source pills [MAN] [AUDIO]
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            for (i in SOURCES.indices) {
                val source = SOURCES[i]
                val isActive = currentClock == source
                ButtonChrome.pushColor(if (isActive) TangoPalette.u32(TangoPalette.ACTIVE_BLUE) else TangoPalette.CLOCK_IDLE_BG.u32())
                if (ButtonChrome.button(SOURCE_IDS[i], 50f, headerH)) {
                    audioEngine.clockSource = source
                    AppPreferencesStore.savePreferences()
                }
                ImGui.popStyleColor(3)
                itemTooltip(SOURCE_TIPS[i])
                ImGui.sameLine(0f, 2f)
            }
        }

        // 2. Ableton Link status (only while enabled) -- click opens Tempo & Sync preferences.
        if (AbletonLinkEngine.isEnabled) {
            ImGui.sameLine(0f, gap)
            val peers = AbletonLinkEngine.getNumPeers()
            if (peers > 0) {
                ButtonChrome.pushColor(TangoPalette.u32(TangoPalette.SYNC.normal))
            } else {
                val linkInk = LINK_NO_PEERS_INK
                ButtonChrome.pushColor(TangoPalette.u32(TangoPalette.LINK_NO_PEERS))
                ImGui.pushStyleColor(ImGuiCol.Text, linkInk[0], linkInk[1], linkInk[2], 1.0f)
            }
            if (peers != linkPeers) {
                linkPeers = peers
                linkLabel = "LINK $peers##perf_clock_link"
                linkTip = "Ableton Link: $peers peer(s). Click to open Tempo & Sync preferences."
            }
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                if (ButtonChrome.button(linkLabel, 56f, headerH)) {
                    PreferencesPanel.open(PreferencesPanel.Category.TEMPO_SYNC)
                }
            }
            ImGui.popStyleColor(if (peers > 0) 3 else 4)
            itemTooltip(linkTip)
        }

        // 3. BPM readout -- click opens Tempo & Sync preferences.
        ImGui.sameLine(0f, gap * 2f)
        val tenths = Math.round(audioEngine.getEstimatedBpm() * 10f)
        if (tenths != bpmTenths) {
            bpmTenths = tenths
            bpmLabel = "${"%.1f".format(tenths / 10.0)} BPM##perf_clock_bpm"
        }
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ButtonChrome.pushColor(TangoPalette.PILL_BG.u32())
            if (ButtonChrome.button(bpmLabel, 96f, headerH)) {
                PreferencesPanel.open(PreferencesPanel.Category.TEMPO_SYNC)
            }
            ImGui.popStyleColor(3)
        }
        itemTooltip(BPM_TIPS[currentClock.ordinal])

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
                val col = if (i == 0) TangoPalette.BEAT_DOWNBEAT.u32() else TangoPalette.BEAT_DOT.u32()
                dl.addCircleFilled(cx, dotsCy, dotR, col)
            } else {
                dl.addCircle(cx, dotsCy, dotR, TangoPalette.BEAT_IDLE_RING.u32(), 0, 1.2f)
            }
        }
        ImGui.dummy(dotR * 2f * 4f + dotGap * 3f, headerH)
        itemTooltip("4/4 bar phase. Downbeat is cyan.")

        ImGui.endGroup()

        // 5. Tempo actions (second line)
        ImGui.setCursorScreenPos(startX, row2Y)
        ImGui.beginGroup()
        drawTapButton(session, headerH)

        ImGui.sameLine(0f, gap)
        if (ButtonChrome.button("RESYNC##perf_clock_resync", 64f, headerH)) {
            audioEngine.resyncDownbeat()
        }
        itemTooltip("Snap the beat phase to the downbeat now.")

        ImGui.sameLine(0f, gap)
        if (ButtonChrome.button("/2##perf_clock_half", 32f, headerH)) {
            audioEngine.halveTempo()
            AppPreferencesStore.savePreferences()
        }
        itemTooltip("Halve tempo (e.g. 140 -> 70 BPM).")

        ImGui.sameLine(0f, 2f)
        if (ButtonChrome.button("x2##perf_clock_double", 32f, headerH)) {
            audioEngine.doubleTempo()
            AppPreferencesStore.savePreferences()
        }
        itemTooltip("Double tempo (e.g. 70 -> 140 BPM).")

        ImGui.sameLine(0f, gap)
        if (ButtonChrome.button("-##perf_clock_nudge_down", 26f, headerH)) {
            audioEngine.nudgeTempo(-0.5f)
            AppPreferencesStore.savePreferences()
        }
        itemTooltip("Nudge tempo down 0.5 BPM.")

        ImGui.sameLine(0f, 2f)
        if (ButtonChrome.button("+##perf_clock_nudge_up", 26f, headerH)) {
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
        if (tapMapping != this.tapMapping || tapTip.isEmpty()) {
            this.tapMapping = tapMapping
            val tapMidiText = tapMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""
            tapTip = "Tap tempo (hotkey: T).$tapMidiText In Audio Tracker mode, nudges the detected tempo and phase.\nRight-click for MIDI Learn."
        }
        if (tapCount != tapCountShown) {
            tapCountShown = tapCount
            tapLabel = (if (tapCount > 0) "TAP [$tapCount]" else "TAP") + "##perf_clock_tap"
        }
        // Idle uses the theme's button states (grey, lighter on hover, accent while held). The flash and
        // counting states override all three button colors together, so fill and ink can't disagree
        // (overriding only Button let the hover color show through under the flash ink).
        val tapState = when {
            tapFlash > 0.05f -> TangoPalette.TAP_FLASH
            tapCount > 0 -> TangoPalette.TAP_COUNTING
            else -> null
        }
        val tapX = ImGui.getCursorScreenPosX()
        val tapY = ImGui.getCursorScreenPosY()
        val tapW = 64f
        if (tapState != null) {
            val ink = if (tapState === TangoPalette.TAP_FLASH) TAP_FLASH_INK else TAP_COUNTING_INK
            ButtonChrome.pushColor(TangoPalette.u32(tapState), hoverShift = 0f)
            ImGui.pushStyleColor(ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
        }
        if (ButtonChrome.button(tapLabel, tapW, headerH)) {
            tapController.tap()
        }
        if (tapState != null) ImGui.popStyleColor(4)
        if (isMidiLearnTap) {
            ImGui.getWindowDrawList().addRect(tapX - 1f, tapY - 1f, tapX + tapW + 1f, tapY + headerH + 1f, TangoPalette.learnBorder(), 3f, 0, 1.5f)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_clock_tap_ctx")) {
            pushOpenDropdownFont()
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
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        itemTooltip(tapTip)
    }
}
