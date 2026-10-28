package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.midi.MidiControlMapping
import llm.slop.liquidlsd.midi.MidiLearnTarget
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer

/**
 * The Clock row's trigger buttons, right of the tempo controls ([PerformanceClockControls]), laid out on the row's two lines. The QUEUE
 * block: captions A/B, A/B FX, BG, BG FX, TRN on line 1, each column's buttons on line 2: prev, next. On the A/B, BG and Transition queues the play / pause button (their auto-advance) sits on line 1 beside the caption. The RAND block (only while
 * randomization is enabled) sits at the right edge: A, B, ALL over BG, PV.
 * Captions and button text are BODY in the normal text color, like the captions under the knobs on the other rows.
 *
 * Each button stands for the same [ModulatableParameter] the MST > CTRL rows list (`Mixer/queueNext`, `Mixer/randAll`, ...), under the
 * same param key: right-click Learn MIDI / Learn OSC / Clear and the tooltip's mapping all go through that key, so a mapping made here
 * is the one the CTRL row shows, and the other way round. A click advances the queue (or randomizes the deck) directly, as the
 * keyboard shortcuts and the deck dice do, so it works whether or not Auto-VJ is on (the CTRL rows' edge detection only counts
 * while it is). Tap tempo is the TAP button beside the BPM, not repeated here.
 */
internal object PerformanceClockTriggers {
    /** One trigger button: what it fires, its param identity and its cached tooltip. Built once, so drawing allocates nothing. */
    private class Trigger(
        val paramKey: String,
        val label: String,
        /** What Learn / the context menu call it ("A/B Queue Next"). */
        val title: String,
        /** First tooltip line. */
        val action: String,
        val w: Float,
        val param: (Mixer) -> ModulatableParameter,
        val fire: (SessionContext, Mixer) -> Unit
    ) {
        val id = "$label##perf_clock_trig_${paramKey.substringAfter('/')}"
        val ctxId = "perf_clock_trig_ctx_${paramKey.substringAfter('/')}"
        var mapping: MidiControlMapping? = null
        var tip = ""
    }

    private const val GAP = 2f
    private const val GROUP_GAP = 8.6f
    private const val BLOCK_GAP = 4f
    private const val TINT_PAD_Y = 2f
    private const val TINT_DARK_THEME = 0x14FFFFFF
    private const val TINT_LIGHT_THEME = 0x14000000
    // Caption slots: the BODY text (QUEUE is 48 px wide, RAND 39) plus 3 px before the first button, so both blocks read alike.
    private const val QUEUE_CAPTION_W = 49.4f
    private const val RAND_CAPTION_W = 40.3f
    private const val NAV_W = 26f

    /** A queue column: its two buttons; the caption above is centred over it. */
    private const val COL_W = 2 * NAV_W + GAP

    /** Caption slot beside the play / pause button; the prev button below is as wide, so next sits under play / pause. */
    private const val AUTO_LABEL_W = 32f

    private fun nav(
        paramKey: String, glyph: String, title: String, action: String,
        param: (Mixer) -> ModulatableParameter, fire: (SessionContext, Mixer) -> Unit
    ) = Trigger(paramKey, glyph, title, action, NAV_W, param, fire)

    private fun rand(
        paramKey: String, label: String, w: Float, title: String, action: String,
        param: (Mixer) -> ModulatableParameter, fire: (SessionContext, Mixer) -> Unit
    ) = Trigger(paramKey, label, title, action, w, param, fire)

    /** One queue column: its caption on line 1, its prev / next buttons on line 2. */
    private class QueueGroup(val label: String, val pair: Array<Trigger>, val auto: AutoToggle? = null) {
        /** Width of the column: prev and next, or a caption slot plus the play / pause button when the queue has an auto-advance. */
        val width = if (auto != null) AUTO_LABEL_W + GAP + NAV_W else COL_W
    }

    /** A queue's auto-advance as a play / pause button: lit and showing pause while the queue runs on its own. */
    private class AutoToggle(
        val id: String,
        val tipRunning: String,
        val tipPaused: String,
        val isOn: (SessionContext) -> Boolean,
        val toggle: (SessionContext, Mixer) -> Unit
    )

    private val QUEUE_GROUPS: Array<QueueGroup> = arrayOf(
        QueueGroup("A/B", arrayOf(
            nav("Mixer/queuePrev", "◀", "A/B Queue Prev", "Previous item in the A/B play queue.", { it.queuePrev }) { s, m -> s.playQueueManager.advanceBy(-1, m) },
            nav("Mixer/queueNext", "▶", "A/B Queue Next", "Next item in the A/B play queue.", { it.queueNext }) { s, m -> s.playQueueManager.advanceBy(1, m) }
        ), AutoToggle("perf_clock_auto_ab", "Auto-VJ is running: click to pause the A/B queue.", "Auto-VJ is paused: click to cycle through the A/B queue at set intervals.",
            { it.playQueueManager.isAutoVJEnabled }) { s, m ->
            val on = !s.playQueueManager.isAutoVJEnabled
            s.playQueueManager.isAutoVJEnabled = on
            if (on) m.muteCrossfadeNonMidiCv()
        }),
        QueueGroup("A/B FX", arrayOf(
            nav("Mixer/fxQueuePrev", "◀", "A/B FX Queue Prev", "Previous item in the A/B FX queue, applied to whichever of Deck A/B the crossfader favours.", { it.fxQueuePrev }) { s, m -> FXQueueManager.advancePrevious(s, m) },
            nav("Mixer/fxQueueNext", "▶", "A/B FX Queue Next", "Next item in the A/B FX queue, applied to whichever of Deck A/B the crossfader favours.", { it.fxQueueNext }) { s, m -> FXQueueManager.advanceNext(s, m) }
        )),
        QueueGroup("BG", arrayOf(
            nav("Mixer/bgQueuePrev", "◀", "BG Queue Prev", "Previous item in the BG queue.", { it.bgQueuePrev }) { s, m -> s.bgQueueManager.advanceBy(-1, m) },
            nav("Mixer/bgQueueNext", "▶", "BG Queue Next", "Next item in the BG queue.", { it.bgQueueNext }) { s, m -> s.bgQueueManager.advanceBy(1, m) }
        ), AutoToggle("perf_clock_auto_bg", "Auto-BG is running: click to pause the BG queue.", "Auto-BG is paused: click to cycle through the BG queue with dip-to-black transitions.",
            { it.bgQueueManager.isAutoBGEnabled }) { s, _ -> s.bgQueueManager.isAutoBGEnabled = !s.bgQueueManager.isAutoBGEnabled }),
        QueueGroup("BG FX", arrayOf(
            nav("Mixer/fxBgQueuePrev", "◀", "BG FX Queue Prev", "Previous item in the BG FX queue, applied to Deck BG.", { it.fxBgQueuePrev }) { s, m -> FXBgQueueManager.advancePrevious(s, m) },
            nav("Mixer/fxBgQueueNext", "▶", "BG FX Queue Next", "Next item in the BG FX queue, applied to Deck BG.", { it.fxBgQueueNext }) { s, m -> FXBgQueueManager.advanceNext(s, m) }
        )),
        QueueGroup("TRN", arrayOf(
            nav("Mixer/transQueuePrev", "◀", "Transition Queue Prev", "Previous transition in the Transition queue.", { it.transQueuePrev }) { _, m -> TransitionQueueManager.advancePrevious(m) },
            nav("Mixer/transQueueNext", "▶", "Transition Queue Next", "Next transition in the Transition queue.", { it.transQueueNext }) { _, m -> TransitionQueueManager.advanceNext(m) }
        ), AutoToggle("perf_clock_auto_trn", "Auto-advance is on: click to hold the current transition.", "Auto-advance is off: click to step to the next transition whenever a crossfade triggers.",
            { TransitionQueueManager.isAutoAdvanceEnabled }) { _, _ -> TransitionQueueManager.isAutoAdvanceEnabled = !TransitionQueueManager.isAutoAdvanceEnabled })
    )

    private const val RAND_A_W = 34f
    private const val RAND_B_W = 34f
    private const val RAND_ALL_W = 42f

    private val RAND_A = rand("Mixer/randDeckA", "A", RAND_A_W, "Randomize Deck A", "Randomize Deck A's parameters and modulators, its effects included.", { it.randDeckA }) { s, m -> undoThen(s, m) { m.randomizeDeckA() } }
    private val RAND_B = rand("Mixer/randDeckB", "B", RAND_B_W, "Randomize Deck B", "Randomize Deck B's parameters and modulators, its effects included.", { it.randDeckB }) { s, m -> undoThen(s, m) { m.randomizeDeckB() } }
    private val RAND_ALL = rand("Mixer/randAll", "ALL", RAND_ALL_W, "Randomize All", "Randomize all decks and their effects, the Master parameters and FX, and the transition.", { it.randAll }) { s, m -> undoThen(s, m) { m.randomizeAll() } }
    private val RAND_BG = rand("Mixer/randDeckBG", "BG", RAND_A_W, "Randomize Deck BG", "Randomize Deck BG's parameters and modulators, its effects included.", { it.randDeckBG }) { s, m -> undoThen(s, m) { m.randomizeDeckBG() } }
    private val RAND_PV = rand("Mixer/randDeckPV", "PV", RAND_B_W, "Randomize Deck PV", "Randomize Deck PV's parameters and modulators, its effects included.", { it.randDeckPV }) { s, m -> undoThen(s, m) { m.randomizeDeckPV() } }

    private val RAND_ROW1 = arrayOf(RAND_A, RAND_B, RAND_ALL)
    private val RAND_ROW2 = arrayOf(RAND_BG, RAND_PV)

    /** Every button's (param key, [Mixer] parameter): what a mapping made on it is stored against. For tests. */
    internal fun boundParameters(mixer: Mixer): List<Pair<String, ModulatableParameter>> =
        (QUEUE_GROUPS.flatMap { it.pair.asList() } + RAND_ROW1 + RAND_ROW2).map { it.paramKey to it.param!!(mixer) }

    /** Width of the QUEUE block: caption, then five columns (three with a play / pause button). */
    val QUEUE_LINE_W: Float = QUEUE_CAPTION_W + GAP + QUEUE_GROUPS.sumOf { it.width.toDouble() }.toFloat() + (QUEUE_GROUPS.size - 1) * GROUP_GAP

    /** Width of the RAND block: caption, then the three columns. */
    val RAND_LINE_W: Float = RAND_CAPTION_W + GAP + RAND_A_W + RAND_B_W + RAND_ALL_W + 2 * GAP

    /** Gap kept between the tempo controls and these blocks. */
    const val LEFT_GAP = 4f

    /** Randomizing is undoable (Ctrl+Z), like the decks' dice: the snapshot is taken first. */
    private inline fun undoThen(session: SessionContext, mixer: Mixer, randomize: () -> Unit) {
        ParametersUndo.pushRandomizeUndo(session.parametersState, mixer)
        randomize()
    }

    private val suffixCache =HashMap<MidiControlMapping, String>()
    private fun midiSuffix(m: MidiControlMapping?): String {
        if (m == null) return ""
        return suffixCache.getOrPut(m) { if (m.channel == 0) " [CC ${m.cc}]" else " [Ch ${m.channel + 1} CC ${m.cc}]" }
    }

    /**
     * Draws the blocks that fit between [x] and [rightX], on the two lines starting at [row1Y] and [row2Y]. QUEUE sits left: captions on
     * line 1, prev / next buttons on line 2. RAND (when randomization is enabled) is pushed to the right edge. A block is dropped whole
     * when it does not fit, so no button is ever cut off; RAND is the one that gives way first.
     */
    fun draw(session: SessionContext, mixer: Mixer, x: Float, rightX: Float, row1Y: Float, row2Y: Float, h: Float) {
        val room = rightX - x
        if (room < QUEUE_LINE_W) return
        val dl = ImGui.getWindowDrawList()
        val blockH = row2Y + h - row1Y

        caption(session, "QUEUE", x, row1Y, QUEUE_CAPTION_W, blockH, centered = false)
        var cx = x + QUEUE_CAPTION_W + GAP
        for ((gi, g) in QUEUE_GROUPS.withIndex()) {
            if (gi % 2 == 0) tint(dl, cx - GROUP_GAP / 2f, row1Y, g.width + GROUP_GAP, blockH)
            if (g.auto != null) {
                // Line 1: caption, then play / pause. Line 2: prev (under the caption), next (under play / pause).
                caption(session, g.label, cx, row1Y, AUTO_LABEL_W, h, centered = true)
                place(cx + AUTO_LABEL_W + GAP, row1Y)
                autoButton(session, mixer, g.auto, h)
                place(cx, row2Y)
                button(session, mixer, g.pair[0], h, dl, AUTO_LABEL_W)
                place(cx + AUTO_LABEL_W + GAP, row2Y)
                button(session, mixer, g.pair[1], h, dl)
            } else {
                caption(session, g.label, cx, row1Y, g.width, h, centered = true)
                place(cx, row2Y)
                button(session, mixer, g.pair[0], h, dl)
                place(cx + NAV_W + GAP, row2Y)
                button(session, mixer, g.pair[1], h, dl)
            }
            cx += g.width + GROUP_GAP
        }

        if (!session.uiTheme.randomizationEnabled || room < QUEUE_LINE_W + BLOCK_GAP + RAND_LINE_W) return
        cx = rightX - RAND_LINE_W
        tint(dl, cx - GROUP_GAP / 2f, row1Y, RAND_LINE_W + GROUP_GAP, blockH)
        caption(session, "RAND", cx, row1Y, RAND_CAPTION_W, blockH, centered = false)
        cx += RAND_CAPTION_W + GAP
        var bx = cx
        for (t in RAND_ROW1) { place(bx, row1Y); button(session, mixer, t, h, dl); bx += t.w + GAP }
        bx = cx
        for (t in RAND_ROW2) { place(bx, row2Y); button(session, mixer, t, h, dl); bx += t.w + GAP }
    }

    /** The play / pause button between a queue's prev and next: shows what a click does, and is lit while the queue is running. */
    private fun autoButton(session: SessionContext, mixer: Mixer, a: AutoToggle, h: Float) {
        val on = a.isOn(session)
        if (on) ButtonChrome.pushColor(TangoPalette.u32(TangoPalette.ACTIVE_BLUE))
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ButtonChrome.button("${if (on) Icons.PAUSE else Icons.PLAY}##${a.id}", NAV_W, h)) a.toggle(session, mixer)
        }
        if (on) ImGui.popStyleColor(3)
        itemTooltip(if (on) a.tipRunning else a.tipPaused)
    }

    /**
     * A faint lighter (darker in the light theme) wash behind every other group, as the Library's queue columns alternate their fill, so
     * it is clear at a glance which captions and buttons belong together. Drawn first, under the group's own items.
     */
    private fun tint(dl: imgui.ImDrawList, x: Float, y: Float, w: Float, h: Float) {
        val col = if (TangoPalette.isLightTheme) TINT_LIGHT_THEME else TINT_DARK_THEME
        dl.addRectFilled(x, y - TINT_PAD_Y, x + w, y + h + TINT_PAD_Y, col, 4f)
    }

    private fun place(x: Float, y: Float) = ImGui.setCursorScreenPos(x, y)

    /** A caption in a [w] x [h] slot at ([x], [y]): BODY in the normal text color, vertically centred, left-aligned or [centered]. */
    private fun caption(session: SessionContext, text: String, x: Float, y: Float, w: Float, h: Float, centered: Boolean) {
        ImGui.setCursorScreenPos(x, y)
        ImGui.dummy(w, h)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val ty = y + (h - ImGui.getTextLineHeight()) * 0.5f
            val tx = if (centered) x + (w - ImGui.calcTextSize(text).x) * 0.5f else x
            ImGui.getWindowDrawList().addText(tx, ty, ImGui.getColorU32(imgui.flag.ImGuiCol.Text), text)
        }
    }

    private fun button(session: SessionContext, mixer: Mixer, t: Trigger, h: Float, dl: imgui.ImDrawList, w: Float = t.w) {
        val state = session.parametersState
        val key = t.paramKey
        val isMidiLearn = state.isMidiTargetLearning(key)
        val isOscLearn = OscLearnState.isTargetLearning(key)
        val mapping = session.midiMappingManager.getMappingForParameter(key)
        if (mapping != t.mapping || t.tip.isEmpty()) {
            t.mapping = mapping
            t.tip = "${t.action}${midiSuffix(mapping)}\nRight-click for MIDI/OSC Learn. The same trigger is on the MST > CTRL tab in Edit."
        }

        val bx = ImGui.getCursorScreenPosX()
        val by = ImGui.getCursorScreenPosY()
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ButtonChrome.button(t.id, w, h)) t.fire(session, mixer)
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "clock_trig_" + key.substringAfter('/') }
        if (isMidiLearn) {
            dl.addRect(bx - 1f, by - 1f, bx + w + 1f, by + h + 1f, TangoPalette.learnBorder(), 3f, 0, 1.5f)
        } else if (isOscLearn) {
            TangoPalette.drawOscLearnPulseBorder(dl, bx - 1f, by - 1f, bx + w + 1f, by + h + 1f)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem(t.ctxId)) {
            pushOpenDropdownFont()
            ImGui.textDisabled(t.title)
            ImGui.separator()
            if (ImGui.menuItem("Trigger")) t.fire(session, mixer)
            ImGui.separator()
            if (isMidiLearn) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) state.midiLearnTarget = null
            } else if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (${t.title})")) {
                state.startMidiLearn(MidiLearnTarget.BaseValueSlider(paramKey = key, label = t.title, param = t.param(mixer), min = 0f, max = 1f))
            }
            if (mapping != null && ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                session.midiMappingManager.removeMapping(key)
                session.midiMappingManager.saveActiveProfile()
            }
            if (isOscLearn) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) OscLearnState.cancelLearn()
            } else if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (${t.title})")) {
                OscLearnState.startLearn(key, 0f, 1f, t.title)
            }
            val oscAddress = OscMappingManager.getAddressForParameter(key)
            if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, oscAddress != null)) {
                OscMappingManager.removeMapping(oscAddress!!)
                OscMappingManager.saveActiveProfile()
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        itemTooltip(t.tip)
    }
}
