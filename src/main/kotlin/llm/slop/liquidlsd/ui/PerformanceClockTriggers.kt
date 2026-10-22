package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.midi.MidiControlMapping
import llm.slop.liquidlsd.midi.MidiLearnTarget
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer

/**
 * The Clock row's trigger buttons, right of the tempo controls ([PerformanceClockControls]): line 1 the three queues' prev/next
 * pairs (A/B, BG, Transitions), line 2 the five randomize triggers (Deck A/B/BG/PV, All; only while randomization is enabled).
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
    private const val GROUP_GAP = 8f
    private const val CAPTION_W = 44f
    private const val NAV_W = 24f
    private const val QUEUE_LABEL_W = 30f

    private fun nav(
        paramKey: String, glyph: String, title: String, action: String,
        param: (Mixer) -> ModulatableParameter, fire: (SessionContext, Mixer) -> Unit
    ) = Trigger(paramKey, glyph, title, action, NAV_W, param, fire)

    private fun rand(
        paramKey: String, label: String, w: Float, title: String, action: String,
        param: (Mixer) -> ModulatableParameter, fire: (SessionContext, Mixer) -> Unit
    ) = Trigger(paramKey, label, title, action, w, param, fire)

    // [prev, next] per queue.
    private val QUEUE_LABELS = arrayOf("A/B", "BG", "TRN")
    private val QUEUES: Array<Array<Trigger>> = arrayOf(
        arrayOf(
            nav("Mixer/queuePrev", "◀", "A/B Queue Prev", "Previous item in the A/B play queue.", { it.queuePrev }) { s, m -> s.playQueueManager.advanceBy(-1, m) },
            nav("Mixer/queueNext", "▶", "A/B Queue Next", "Next item in the A/B play queue.", { it.queueNext }) { s, m -> s.playQueueManager.advanceBy(1, m) }
        ),
        arrayOf(
            nav("Mixer/bgQueuePrev", "◀", "BG Queue Prev", "Previous item in the BG queue.", { it.bgQueuePrev }) { s, m -> s.bgQueueManager.advanceBy(-1, m) },
            nav("Mixer/bgQueueNext", "▶", "BG Queue Next", "Next item in the BG queue.", { it.bgQueueNext }) { s, m -> s.bgQueueManager.advanceBy(1, m) }
        ),
        arrayOf(
            nav("Mixer/transQueuePrev", "◀", "Transition Queue Prev", "Previous transition in the Transition queue.", { it.transQueuePrev }) { _, m -> TransitionQueueManager.advancePrevious(m) },
            nav("Mixer/transQueueNext", "▶", "Transition Queue Next", "Next transition in the Transition queue.", { it.transQueueNext }) { _, m -> TransitionQueueManager.advanceNext(m) }
        )
    )

    private val RANDS: Array<Trigger> = arrayOf(
        rand("Mixer/randDeckA", "A", 30f, "Randomize Deck A", "Randomize Deck A's parameters and modulators.", { it.randDeckA }) { s, m -> undoThen(s, m) { m.randomizeDeckA() } },
        rand("Mixer/randDeckB", "B", 30f, "Randomize Deck B", "Randomize Deck B's parameters and modulators.", { it.randDeckB }) { s, m -> undoThen(s, m) { m.randomizeDeckB() } },
        rand("Mixer/randDeckBG", "BG", 30f, "Randomize Deck BG", "Randomize Deck BG's parameters and modulators.", { it.randDeckBG }) { s, m -> undoThen(s, m) { m.randomizeDeckBG() } },
        rand("Mixer/randDeckPV", "PV", 30f, "Randomize Deck PV", "Randomize Deck PV's parameters and modulators.", { it.randDeckPV }) { s, m -> undoThen(s, m) { m.randomizeDeckPV() } },
        rand("Mixer/randAll", "ALL", 36f, "Randomize All", "Randomize all decks and the Master parameters.", { it.randAll }) { s, m -> undoThen(s, m) { m.randomizeAll() } }
    )

    /** Every button's (param key, [Mixer] parameter): what a mapping made on it is stored against. For tests. */
    internal fun boundParameters(mixer: Mixer): List<Pair<String, ModulatableParameter>> =
        (QUEUES.flatMap { it.asList() } + RANDS).map { it.paramKey to it.param(mixer) }

    /** Width of the queue line: caption, then three label + prev + next groups. */
    val QUEUE_LINE_W: Float = CAPTION_W + 3 * (QUEUE_LABEL_W + GAP + 2 * NAV_W + GAP) + 2 * GROUP_GAP - GAP

    /** Width of the randomize line: caption, then the five buttons. */
    val RAND_LINE_W: Float = CAPTION_W + RANDS.sumOf { it.w.toDouble() }.toFloat() + (RANDS.size - 1) * GAP

    /** Gap kept between the tempo controls and these lines. */
    const val LEFT_GAP = 16f

    /** Randomizing is undoable (Ctrl+Z), like the decks' dice: the snapshot is taken first. */
    private inline fun undoThen(session: SessionContext, mixer: Mixer, randomize: () -> Unit) {
        ParametersUndo.pushUndoState(session.parametersState, mixer)
        randomize()
    }

    private val suffixCache =HashMap<MidiControlMapping, String>()
    private fun midiSuffix(m: MidiControlMapping?): String {
        if (m == null) return ""
        return suffixCache.getOrPut(m) { if (m.channel == 0) " [CC ${m.cc}]" else " [Ch ${m.channel + 1} CC ${m.cc}]" }
    }

    /**
     * Draws the lines that fit between [x] and [rightX]: line 1 at [row1Y] (queues), line 2 at [row2Y] (randomize, when enabled and it fits).
     * Each line is dropped whole when it does not fit, so no button is ever cut off; the randomize line, optional, is the one that gives way first.
     */
    fun draw(session: SessionContext, mixer: Mixer, x: Float, rightX: Float, row1Y: Float, row2Y: Float, h: Float) {
        val room = rightX - x
        if (room < QUEUE_LINE_W) return
        val dl = ImGui.getWindowDrawList()

        ImGui.setCursorScreenPos(x, row1Y)
        ImGui.beginGroup()
        caption(session, "QUEUE", h)
        for (q in QUEUES.indices) {
            if (q > 0) ImGui.sameLine(0f, GROUP_GAP)
            caption(session, QUEUE_LABELS[q], h, QUEUE_LABEL_W)
            for (t in QUEUES[q]) {
                ImGui.sameLine(0f, GAP)
                button(session, mixer, t, h, dl)
            }
        }
        ImGui.endGroup()

        if (!session.uiTheme.randomizationEnabled || room < RAND_LINE_W) return
        ImGui.setCursorScreenPos(x, row2Y)
        ImGui.beginGroup()
        caption(session, "RAND", h)
        for (t in RANDS) {
            ImGui.sameLine(0f, GAP)
            button(session, mixer, t, h, dl)
        }
        ImGui.endGroup()
    }

    /** A dim, vertically centred caption in a [w]-wide slot. */
    private fun caption(session: SessionContext, text: String, h: Float, w: Float = CAPTION_W) {
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        ImGui.dummy(w, h)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val ty = y + (h - ImGui.getTextLineHeight()) * 0.5f
            ImGui.getWindowDrawList().addText(x, ty, ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled), text)
        }
    }

    private fun button(session: SessionContext, mixer: Mixer, t: Trigger, h: Float, dl: imgui.ImDrawList) {
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
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (ButtonChrome.button(t.id, t.w, h)) t.fire(session, mixer)
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "clock_trig_" + key.substringAfter('/') }
        if (isMidiLearn) {
            dl.addRect(bx - 1f, by - 1f, bx + t.w + 1f, by + h + 1f, TangoPalette.learnBorder(), 3f, 0, 1.5f)
        } else if (isOscLearn) {
            TangoPalette.drawOscLearnPulseBorder(dl, bx - 1f, by - 1f, bx + t.w + 1f, by + h + 1f)
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
