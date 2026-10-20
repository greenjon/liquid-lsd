package llm.slop.liquidlsd.tour

import imgui.ImDrawList
import imgui.ImFont
import imgui.ImGui
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.FxSlotTaps
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.ui.TangoPalette
import llm.slop.liquidlsd.ui.UITheme

/**
 * The tour's live schematic of the video pipeline (`pipe*` script actions): decks, FX chains, transition, BG behind,
 * master FX, output, and PV off to the side. Boxes show the real textures (deck source, each FX slot via [FxSlotTaps],
 * transition, composite, output), so the diagram is the app running, not a picture of it.
 *
 * Coordinates are in a 1920x1080 design space; a camera (screen = p * s + o) eases between the overview, a focused
 * node and the corner mini-map. Everything animates on the tour clock passed to [draw], so frames stay deterministic.
 */
class PipelineDiagram {

    enum class Kind { DECK, FX, TRANS, BEHIND, OUT }

    /** [group] is what `pipe_add` names; [deck] is A/B/BG/PV for deck and deck-FX nodes, MASTER for master FX. */
    class Node(val id: String, val kind: Kind, val group: String, val x: Float, val cy: Float, val w: Float, val h: Float,
               val deck: String? = null, val slot: Int = -1) {
        val y get() = cy - h / 2
    }

    private class Wire(val from: String, val to: String, val group: String, val intoBottom: Boolean = false)

    private class Cam(val s: Float, val ox: Float, val oy: Float)

    companion object {
        private const val DW = 200f
        private const val DH = 112f
        private const val FW = 104f
        private const val FH = 58f
        private val FX_X = floatArrayOf(304f, 432f, 560f)
        private val MASTER_X = floatArrayOf(1200f, 1328f, 1456f)
        private const val BUS_Y = 290f
        private const val DIVIDER_Y = 740f
        private const val LABEL_SIZE = 20f
        private const val NAME_SIZE = 16f
        private const val MINI_S = 0.18f
        /** Design-space area the mini-map shows. */
        private const val MINI_X0 = 60f
        private const val MINI_Y0 = 120f
        private const val MINI_X1 = 1820f
        private const val MINI_Y1 = 940f
        private const val MINI_PAD = 10f
        private const val MINI_FADE_SEC = 0.4
        private const val DIM = 0.12f
        private const val PARAM_W = 300f
        private const val PARAM_H = 64f
        private const val PARAM_GAP = 18f
        private val GROUPS = listOf("decks", "fx", "trans", "bg", "master", "pv")
        private val CONTROLS = listOf("On-screen knob", "Audio", "LFO", "Sequencer", "MIDI / OSC")

        private fun smooth(k: Double): Float { val c = k.coerceIn(0.0, 1.0); return (c * c * (3 - 2 * c)).toFloat() }
    }

    val nodes: List<Node> = buildList {
        val rows = listOf("A" to 200f, "B" to 380f, "BG" to 560f, "PV" to 880f)
        for ((d, cy) in rows) {
            add(Node(d, Kind.DECK, if (d == "PV") "pv" else "decks", 80f, cy, DW, DH, deck = d))
            for (i in 0 until FxChain.SLOT_COUNT) add(Node("$d.fx${i + 1}", Kind.FX, if (d == "PV") "pv" else "fx", FX_X[i], cy, FW, FH, deck = d, slot = i))
        }
        add(Node("trans", Kind.TRANS, "trans", 720f, BUS_Y, DW, DH))
        add(Node("behind", Kind.BEHIND, "bg", 960f, BUS_Y, DW, DH))
        for (i in 0 until FxChain.SLOT_COUNT) add(Node("master.fx${i + 1}", Kind.FX, "master", MASTER_X[i], BUS_Y, FW, FH, deck = "MASTER", slot = i))
        add(Node("out", Kind.OUT, "master", 1600f, BUS_Y, DW, DH))
    }
    private val byId = nodes.associateBy { it.id }

    private val wires: List<Wire> = buildList {
        for (d in listOf("A", "B", "BG", "PV")) {
            val g = if (d == "PV") "pv" else "fx"
            add(Wire(d, "$d.fx1", g)); add(Wire("$d.fx1", "$d.fx2", g)); add(Wire("$d.fx2", "$d.fx3", g))
        }
        add(Wire("A.fx3", "trans", "trans")); add(Wire("B.fx3", "trans", "trans"))
        add(Wire("trans", "behind", "bg")); add(Wire("BG.fx3", "behind", "bg", intoBottom = true))
        add(Wire("behind", "master.fx1", "master")); add(Wire("master.fx1", "master.fx2", "master"))
        add(Wire("master.fx2", "master.fx3", "master")); add(Wire("master.fx3", "out", "master"))
    }

    private val titles = mapOf(
        "trans" to "Transition", "behind" to "BG behind", "out" to "Audience",
        "A" to "Deck A", "B" to "Deck B", "BG" to "Deck BG", "PV" to "Deck PV"
    )

    // ---- State, driven by script steps ----

    private enum class Mode { OFF, FULL, REVEAL, MINI }
    private var mode = Mode.OFF
    private var modeStart = 0.0
    private var modeDur = 0.0
    private val groupStart = HashMap<String, Pair<Double, Double>>()
    private var camFrom = Cam(1f, 0f, 0f)
    private var camTo = Cam(1f, 0f, 0f)
    private var camStart = 0.0
    private var camDur = 0.0
    private var focus: String? = null
    private var focusStart = 0.0
    private var prevFocus: String? = null
    private var paramNode: String? = null
    private var paramNames: List<String> = emptyList()
    private var paramStart = 0.0
    private var controlsParam: String? = null
    private var controlsStart = 0.0
    private var controlsFlash = -10.0
    private val tags = LinkedHashMap<String, String>()
    private var miniLit: List<String> = emptyList()
    private var miniStart = 0.0
    private var miniDur = 0.0
    private var miniX = 24f
    private var miniY = 1080f - 24f - (MINI_Y1 - MINI_Y0) * MINI_S - 2 * MINI_PAD

    val isActive get() = mode != Mode.OFF

    /** Handles a `pipe*` step; returns a warning, or null when it applied. */
    fun handle(step: TourStep, t: Double): String? {
        val dur = step.durationSec ?: 0.0
        when (step.action) {
            "pipe" -> when (step.target) {
                "show" -> { mode = Mode.FULL; modeStart = t; modeDur = dur; groupStart.clear(); tags.clear(); clearFocus(t); setCam(overviewCam(), t, 0.0) }
                "hide" -> mode = Mode.OFF
                "reveal" -> {
                    mode = Mode.REVEAL; modeStart = t; modeDur = dur.coerceAtLeast(0.1)
                    paramNode = null; controlsParam = null
                    miniLit = GROUPS; miniStart = t; miniDur = dur + 2.5
                    setCam(miniCam(), t, modeDur)
                }
                else -> return "pipe wants show, hide or reveal"
            }
            "pipe_add" -> for (g in csv(step.target)) {
                if (g !in GROUPS) return "pipe_add: unknown group '$g' (${GROUPS.joinToString()})"
                groupStart[g] = t to dur.coerceAtLeast(0.01)
            }
            "pipe_focus" -> {
                val id = step.target ?: "all"
                if (id != "all" && chainOf(id) == null && id !in byId) return "pipe_focus: unknown node '$id' (a node, a chain like A.fx or master, or all)"
                prevFocus = focus
                focus = id.takeIf { it != "all" }
                focusStart = t
                paramNode = null; controlsParam = null
                setCam(if (focus == null) overviewCam() else focusCam(id), t, dur.coerceAtLeast(0.01))
            }
            "pipe_params" -> {
                val id = step.target ?: return "pipe_params needs a node"
                if (id !in byId) return "pipe_params: unknown node '$id'"
                paramNode = id; paramNames = step.params ?: emptyList(); paramStart = t
            }
            "pipe_controls" -> when ((step.value ?: 1f).toInt()) {
                0 -> controlsParam = null
                2 -> controlsFlash = t
                else -> { controlsParam = step.target; controlsStart = t }
            }
            "pipe_tag" -> {
                val id = step.target ?: return "pipe_tag needs a node"
                if (id !in byId) return "pipe_tag: unknown node '$id'"
                if ((step.value ?: 1f) < 0.5f || step.title.isNullOrBlank()) tags.remove(id) else tags[id] = step.title
            }
            "pipe_minimap" -> {
                if (mode == Mode.FULL) return "pipe_minimap while the full diagram is up; use pipe reveal first"
                if (step.x != null && step.y != null) { miniX = step.x; miniY = step.y }
                mode = Mode.MINI
                setCam(miniCam(), t, 0.0)
                miniLit = csv(step.target); miniStart = t; miniDur = if (dur > 0) dur else 2.5
            }
            else -> return "unknown pipe action '${step.action}'"
        }
        return null
    }

    private fun csv(s: String?) = s.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun clearFocus(t: Double) { focus = null; prevFocus = null; focusStart = t; paramNode = null; controlsParam = null }

    private fun setCam(to: Cam, t: Double, dur: Double) {
        camFrom = if (camDur <= 0 || t >= camStart + camDur) camTo else camAt(t)
        camTo = to; camStart = t; camDur = dur
    }

    private fun camAt(t: Double): Cam {
        if (camDur <= 0) return camTo
        val e = smooth((t - camStart) / camDur)
        return Cam(camFrom.s + (camTo.s - camFrom.s) * e, camFrom.ox + (camTo.ox - camFrom.ox) * e, camFrom.oy + (camTo.oy - camFrom.oy) * e)
    }

    private fun overviewCam() = Cam(1f, 0f, 0f)

    /** The FX slots of a chain focus id: `A.fx` .. `PV.fx`, or `master`; null for anything else. */
    private fun chainOf(id: String): List<Node>? {
        val deck = when {
            id == "master" -> "MASTER"
            id.endsWith(".fx") -> id.removeSuffix(".fx")
            else -> return null
        }
        return nodes.filter { it.kind == Kind.FX && it.deck == deck }.takeIf { it.isNotEmpty() }
    }

    private fun miniCam() = Cam(MINI_S, miniX + MINI_PAD - MINI_X0 * MINI_S, miniY + MINI_PAD - MINI_Y0 * MINI_S)

    /** The camera that puts [id] large on the left, with room for parameter boxes and the control fan on the right. */
    private fun focusCam(id: String): Cam {
        chainOf(id)?.let { slots ->
            val s = 2.2f
            val cx = (slots.first().x + slots.last().x + FW) / 2
            return Cam(s, 960f - cx * s, 500f - slots.first().cy * s)
        }
        val n = byId.getValue(id)
        val s = if (n.kind == Kind.FX) 3f else 2f
        return Cam(s, 520f - (n.x + n.w / 2) * s, 500f - n.cy * s)
    }

    /** Screen rect of [id] once the camera has arrived (for `move` targets `pipe:<id>`), or null when unknown or hidden. */
    fun targetRect(id: String): WidgetRegistry.Rect? {
        if (mode == Mode.OFF) return null
        val c = camTo
        chainOf(id)?.let { slots ->
            val a = slots.first(); val b = slots.last()
            return WidgetRegistry.Rect(a.x * c.s + c.ox, a.y * c.s + c.oy, (b.x + b.w) * c.s + c.ox, (b.y + b.h) * c.s + c.oy)
        }
        if (id.startsWith("param:")) return paramRect(id.removePrefix("param:"), c)
        if (id.startsWith("control:")) return controlRect(id.removePrefix("control:"), c)
        val n = byId[id] ?: return null
        return WidgetRegistry.Rect(n.x * c.s + c.ox, n.y * c.s + c.oy, (n.x + n.w) * c.s + c.ox, (n.y + n.h) * c.s + c.oy)
    }

    // ---- Drawing ----

    private lateinit var dl: ImDrawList
    private var font: ImFont? = null

    /** Draws the diagram for tour time [t]; call from the tour overlay before the cursor and caption. */
    fun draw(t: Double, mixer: Mixer?) {
        FxSlotTaps.enabled = mode != Mode.OFF
        if (mode == Mode.OFF || mixer == null) { FxSlotTaps.endFrame(); return }
        dl = ImGui.getForegroundDrawList()
        font = UITheme.fontFor(UITheme.FontLevel.H3) ?: ImGui.getFont()
        val cam = camAt(t)
        when (mode) {
            Mode.FULL -> {
                drawBackdrop(smooth(if (modeDur > 0) (t - modeStart) / modeDur else 1.0))
                drawFull(t, cam, mixer, 1f)
            }
            Mode.REVEAL -> {
                val k = smooth((t - modeStart) / modeDur)
                drawBackdrop(1f - k)
                // The full diagram is gone before the camera lands, so the mini-map takes over from it.
                drawFull(t, cam, mixer, (1f - k * 1.6f).coerceAtLeast(0f))
                drawMini(t, cam, k * miniEnvelope(t))
                if (t >= modeStart + modeDur) mode = Mode.MINI
            }
            Mode.MINI -> {
                val a = miniEnvelope(t)
                if (a <= 0f && t > miniStart + miniDur) mode = Mode.OFF else drawMini(t, cam, a)
            }
            Mode.OFF -> {}
        }
        FxSlotTaps.endFrame()
    }

    private fun miniEnvelope(t: Double): Float {
        val fadeIn = smooth((t - miniStart) / MINI_FADE_SEC)
        val fadeOut = smooth((miniStart + miniDur - t) / MINI_FADE_SEC)
        return minOf(fadeIn, fadeOut)
    }

    private fun col(rgb: FloatArray, a: Float) = TangoPalette.u32(rgb, a.coerceIn(0f, 1f))

    private val bgDark get() = floatArrayOf(TangoPalette.ALUMINIUM_2.dark[0] * 0.45f, TangoPalette.ALUMINIUM_2.dark[1] * 0.45f, TangoPalette.ALUMINIUM_2.dark[2] * 0.45f)
    private val boxFill get() = TangoPalette.ALUMINIUM_2.dark
    private val text get() = TangoPalette.NEUTRAL_LIGHT.light
    private val textDim get() = TangoPalette.NEUTRAL_LIGHT.dark
    private val fxAccent get() = TangoPalette.SYNC.normal

    private fun accent(n: Node): FloatArray = when (n.deck) {
        "A" -> TangoPalette.DECK_A.normal
        "B" -> TangoPalette.DECK_B.normal
        "BG" -> TangoPalette.DECK_BG.normal
        "PV" -> TangoPalette.DECK_PV.normal
        else -> TangoPalette.NEUTRAL_LIGHT.normal
    }

    private fun drawBackdrop(a: Float) {
        if (a <= 0f) return
        val io = ImGui.getIO()
        dl.addRectFilled(0f, 0f, io.displaySizeX, io.displaySizeY, col(bgDark, a))
    }

    private fun groupAlpha(g: String, t: Double): Float {
        val (start, dur) = groupStart[g] ?: return 0f
        return smooth((t - start) / dur)
    }

    /** Dimming of [n] by the focus: the focused node (or the master chain) stays bright, the rest fade to [DIM]. */
    private fun focusAlpha(n: Node?, t: Double): Float {
        val k = smooth((t - focusStart) / 0.5)
        fun lit(f: String?) = f == null || n == null || n.id == f || chainOf(f)?.contains(n) == true
        val from = if (lit(prevFocus)) 1f else DIM
        val to = if (lit(focus)) 1f else DIM
        return from + (to - from) * k
    }

    private fun drawFull(t: Double, c: Cam, mixer: Mixer, alpha: Float) {
        if (alpha <= 0f) return
        val pvA = groupAlpha("pv", t) * alpha * (if (focus == null) 1f else DIM)
        if (pvA > 0f) {
            val y = DIVIDER_Y * c.s + c.oy
            dl.addLine(60f * c.s + c.ox, y, 1860f * c.s + c.ox, y, col(textDim, pvA * 0.6f), 1.5f * c.s)
        }
        for (w in wires) {
            val a = groupAlpha(w.group, t) * alpha * minOf(focusAlpha(byId[w.from], t), focusAlpha(byId[w.to], t))
            if (a > 0f) drawWire(w, c, a, t)
        }
        for (n in nodes) {
            val a = groupAlpha(n.group, t) * alpha * focusAlpha(n, t)
            if (a <= 0f) continue
            val slide = (1f - groupAlpha(n.group, t)) * -24f
            drawNode(n, c, mixer, a, slide)
        }
        for ((id, label) in tags) {
            val n = byId[id] ?: continue
            val a = groupAlpha(n.group, t) * alpha * focusAlpha(n, t)
            if (a > 0f) drawTag(n, label, c, a)
        }
        paramNode?.let { drawParams(it, t, c, mixer, alpha) }
    }

    private fun sx(c: Cam, x: Float) = x * c.s + c.ox
    private fun sy(c: Cam, y: Float) = y * c.s + c.oy

    private fun drawWire(w: Wire, c: Cam, a: Float, t: Double) {
        val f = byId.getValue(w.from); val to = byId.getValue(w.to)
        val x0 = sx(c, f.x + f.w); val y0 = sy(c, f.cy)
        val x1: Float; val y1: Float; val c1x: Float; val c1y: Float; val c2x: Float; val c2y: Float
        if (w.intoBottom) {
            x1 = sx(c, to.x + to.w / 2); y1 = sy(c, to.y + to.h)
            c1x = x1; c1y = y0; c2x = x1; c2y = y0
        } else {
            x1 = sx(c, to.x); y1 = sy(c, to.cy)
            val mid = (x0 + x1) / 2
            c1x = mid; c1y = y0; c2x = mid; c2y = y1
        }
        val th = 2.5f * c.s
        dl.addBezierCubic(x0, y0, c1x, c1y, c2x, c2y, x1, y1, col(textDim, a * 0.7f), th)
        // Flow dots: three per wire, travelling left to right.
        for (i in 0 until 3) {
            val u = (((t * 0.45) + i / 3.0) % 1.0).toFloat()
            val v = 1f - u
            val px = v * v * v * x0 + 3 * v * v * u * c1x + 3 * v * u * u * c2x + u * u * u * x1
            val py = v * v * v * y0 + 3 * v * v * u * c1y + 3 * v * u * u * c2y + u * u * u * y1
            dl.addCircleFilled(px, py, 3.2f * c.s, col(text, a * 0.9f), 12)
        }
    }

    private fun drawNode(n: Node, c: Cam, mixer: Mixer, a: Float, slideX: Float) {
        val x0 = sx(c, n.x + slideX); val y0 = sy(c, n.y)
        val x1 = sx(c, n.x + slideX + n.w); val y1 = sy(c, n.y + n.h)
        val r = 6f * c.s
        dl.addRectFilled(x0, y0, x1, y1, col(boxFill, a), r)
        val tex = textureFor(n, mixer)
        if (tex != 0) {
            drawChecker(x0, y0, x1, y1, c.s, a)
            dl.addImageRounded(tex.toLong(), x0, y0, x1, y1, 0f, 1f, 1f, 0f, col(floatArrayOf(1f, 1f, 1f), a), r)
        } else {
            centeredText(if (n.kind == Kind.FX) "empty" else "", (x0 + x1) / 2, (y0 + y1) / 2 - NAME_SIZE * c.s / 2, NAME_SIZE * c.s, col(textDim, a))
        }
        val border = if (n.kind == Kind.FX) fxAccent else accent(n)
        dl.addRect(x0, y0, x1, y1, col(border, a), r, 0, 2f * c.s)
        val ls = LABEL_SIZE * c.s
        dl.addText(font, ls.toInt(), x0, y0 - ls * 1.25f, col(border, a), titleFor(n))
        val name = nameFor(n, mixer)
        if (name.isNotEmpty()) {
            val ns = NAME_SIZE * c.s
            dl.pushClipRect(x0 - 40f * c.s, y1, x1 + 40f * c.s, y1 + ns * 1.6f, true)
            centeredText(name, (x0 + x1) / 2, y1 + ns * 0.3f, ns, col(text, a))
            dl.popClipRect()
        }
    }

    private fun drawChecker(x0: Float, y0: Float, x1: Float, y1: Float, s: Float, a: Float) {
        val q = 12f * s
        val light = col(TangoPalette.ALUMINIUM_2.normal, a * 0.55f)
        var row = 0
        var y = y0
        while (y < y1) {
            var x = x0 + if (row % 2 == 0) 0f else q
            while (x < x1) {
                dl.addRectFilled(x, y, minOf(x + q, x1), minOf(y + q, y1), light)
                x += 2 * q
            }
            y += q; row++
        }
    }

    private fun centeredText(s: String, cx: Float, y: Float, size: Float, color: Int) {
        if (s.isEmpty()) return
        val w = font?.calcTextSizeAX(size, Float.MAX_VALUE, 0f, s) ?: 0f
        dl.addText(font, size.toInt(), cx - w / 2, y, color, s)
    }

    private fun titleFor(n: Node): String = when {
        n.kind == Kind.FX && n.deck == "MASTER" -> "Master FX${n.slot + 1}"
        n.kind == Kind.FX -> "FX${n.slot + 1}"
        else -> titles[n.id] ?: n.id
    }

    private fun deck(mixer: Mixer, d: String?) = DeckSlot.entries.firstOrNull { it.name == d }?.deck(mixer)

    private fun chainOf(n: Node, mixer: Mixer): FxChain? = if (n.deck == "MASTER") mixer.masterFxChain else deck(mixer, n.deck)?.fxChain

    private fun nameFor(n: Node, mixer: Mixer): String = when (n.kind) {
        Kind.DECK -> deck(mixer, n.deck)?.source?.displayName ?: ""
        Kind.FX -> chainOf(n, mixer)?.slots?.getOrNull(n.slot)?.displayName ?: ""
        Kind.TRANS -> mixer.transitionFilter?.displayName ?: "Crossfade"
        else -> ""
    }

    /** Live texture for [n]: an FX slot that did not render this frame shows what came into it. */
    private fun textureFor(n: Node, mixer: Mixer): Int = when (n.kind) {
        Kind.DECK -> deck(mixer, n.deck)?.cleanFBO?.texture ?: 0
        Kind.TRANS -> mixer.blendFBO.texture
        Kind.BEHIND -> mixer.masterCompositeFBO.texture
        Kind.OUT -> mixer.masterFBO.texture
        Kind.FX -> {
            val chain = chainOf(n, mixer)
            if (chain == null || chain.slots[n.slot] == null) 0
            else {
                var tex = 0
                var i = n.slot
                while (tex == 0 && i >= 0) { tex = FxSlotTaps.rendered(chain, i); i-- }
                if (tex != 0) tex
                else if (n.deck == "MASTER") mixer.masterCompositeFBO.texture
                else deck(mixer, n.deck)?.cleanFBO?.texture ?: 0
            }
        }
    }

    private fun drawTag(n: Node, label: String, c: Cam, a: Float) {
        val size = NAME_SIZE * c.s
        val w = (font?.calcTextSizeAX(size, Float.MAX_VALUE, 0f, label) ?: 0f) + 18f * c.s
        val h = size * 1.5f
        // Left-aligned on the box's title line, after the title.
        val x0 = sx(c, n.x) + (font?.calcTextSizeAX(LABEL_SIZE * c.s, Float.MAX_VALUE, 0f, titleFor(n)) ?: 0f) + 12f * c.s
        val y0 = sy(c, n.y) - LABEL_SIZE * c.s * 1.25f - (h - LABEL_SIZE * c.s) / 2
        val x1 = x0 + w
        dl.addRectFilled(x0, y0, x1, y0 + h, col(bgDark, a), h / 2)
        dl.addRect(x0, y0, x1, y0 + h, col(TangoPalette.SYNC.bright, a), h / 2, 0, 1.5f * c.s)
        dl.addText(font, size.toInt(), x0 + 9f * c.s, y0 + h * 0.17f, col(text, a), label)
    }

    // ---- Parameter boxes and the control fan (screen space, beside the focused node) ----

    private class ParamView(val label: String, val p: ModulatableParameter)

    /** [paramNames] that [id]'s current generator, effect or transition has, by name. */
    private fun paramsOf(id: String, mixer: Mixer): Map<String, ParamView> {
        val n = byId[id] ?: return emptyMap()
        val (params, header) = when (n.kind) {
            Kind.DECK -> deck(mixer, n.deck)?.source?.let { s -> s.parameters to (s as? llm.slop.liquidlsd.rendering.isf.ISFVisualSource)?.header }
            Kind.FX -> chainOf(n, mixer)?.slots?.getOrNull(n.slot)?.let { f -> f.parameters to f.header }
            Kind.TRANS -> mixer.transitionFilter?.let { f -> f.parameters to f.header }
            else -> null
        } ?: return emptyMap()
        return paramNames.mapNotNull { name -> params[name]?.let { name to ParamView(labelOf(header, name), it) } }.toMap()
    }

    private fun labelOf(h: ISFHeader?, name: String) = h?.INPUTS?.find { it.NAME == name }?.LABEL ?: name

    private fun paramRect(name: String, c: Cam): WidgetRegistry.Rect? {
        val i = paramNames.indexOf(name).takeIf { it >= 0 } ?: return null
        val n = byId[paramNode ?: return null] ?: return null
        val x0 = (n.x + n.w) * c.s + c.ox + 150f
        val total = paramNames.size * PARAM_H + (paramNames.size - 1) * PARAM_GAP
        val y0 = n.cy * c.s + c.oy - total / 2 + i * (PARAM_H + PARAM_GAP)
        return WidgetRegistry.Rect(x0, y0, x0 + PARAM_W, y0 + PARAM_H)
    }

    private fun controlRect(i: String, c: Cam): WidgetRegistry.Rect? {
        val idx = i.toIntOrNull() ?: CONTROLS.indexOfFirst { it.equals(i, true) }.takeIf { it >= 0 } ?: return null
        val n = byId[paramNode ?: return null] ?: return null
        val x0 = (n.x + n.w) * c.s + c.ox + 150f + PARAM_W + 170f
        val size = 52f; val gap = 22f
        val total = CONTROLS.size * size + (CONTROLS.size - 1) * gap
        val y0 = n.cy * c.s + c.oy - total / 2 + idx * (size + gap)
        return WidgetRegistry.Rect(x0, y0, x0 + size, y0 + size)
    }

    private fun drawParams(id: String, t: Double, c: Cam, mixer: Mixer, alpha: Float) {
        val n = byId[id] ?: return
        val views = paramsOf(id, mixer)
        val nodeX = sx(c, n.x + n.w); val nodeY = sy(c, n.cy)
        for ((i, name) in paramNames.withIndex()) {
            // A name the current source does not have (e.g. just after a swap, before the next pipe_params) draws nothing.
            val pv = views[name] ?: continue
            val r = paramRect(name, c) ?: continue
            val a = alpha * smooth((t - paramStart - i * 0.15) / 0.4)
            if (a <= 0f) continue
            dl.addBezierCubic(r.minX, r.centerY, (r.minX + nodeX) / 2, r.centerY, (r.minX + nodeX) / 2, nodeY, nodeX, nodeY, col(textDim, a * 0.8f), 2.5f)
            dl.addRectFilled(r.minX, r.minY, r.maxX, r.maxY, col(boxFill, a), 8f)
            dl.addRect(r.minX, r.minY, r.maxX, r.maxY, col(fxAccentFor(n), a), 8f, 0, 2f)
            dl.addText(font, 24, r.minX + 14f, r.minY + 8f, col(text, a), pv.label)
            val p = pv.p
            val range = p.maxClamp - p.minClamp
            val k = if (range > 0f) ((p.value - p.minClamp) / range).coerceIn(0f, 1f) else 0f
            val bx0 = r.minX + 14f; val bx1 = r.maxX - 14f; val by = r.maxY - 16f
            dl.addRectFilled(bx0, by, bx1, by + 6f, col(bgDark, a), 3f)
            dl.addRectFilled(bx0, by, bx0 + (bx1 - bx0) * k, by + 6f, col(fxAccentFor(n), a), 3f)
            if ((p.steps ?: 0) >= 2) {
                val s = p.labels?.getOrNull(Math.round(k * (p.steps!! - 1))) ?: Math.round(p.value).toString()
                val w = font?.calcTextSizeAX(22f, Float.MAX_VALUE, 0f, s) ?: 0f
                dl.addText(font, 22, r.maxX - 14f - w, r.minY + 9f, col(textDim, a), s)
            }
        }
        controlsParam?.let { drawControls(it, t, c, alpha) }
    }

    private fun fxAccentFor(n: Node) = if (n.kind == Kind.FX) fxAccent else accent(n)

    private fun drawControls(param: String, t: Double, c: Cam, alpha: Float) {
        val target = paramRect(param, c) ?: return
        val flash = (t - controlsFlash).let { if (it in 0.0..1.2) (0.5 + 0.5 * kotlin.math.cos(it * 10.0)).toFloat() * (1f - (it / 1.2).toFloat()) else 0f }
        for (i in CONTROLS.indices) {
            val r = controlRect(i.toString(), c) ?: continue
            val a = alpha * smooth((t - controlsStart - i * 0.12) / 0.4)
            if (a <= 0f) continue
            val wire = if (flash > 0f) TangoPalette.SYNC.bright else textDim
            dl.addBezierCubic(r.minX, r.centerY, (r.minX + target.maxX) / 2, r.centerY, (r.minX + target.maxX) / 2, target.centerY, target.maxX, target.centerY,
                col(wire, a * (0.7f + 0.3f * flash)), 2.5f + 2f * flash)
            dl.addRectFilled(r.minX, r.minY, r.maxX, r.maxY, col(boxFill, a), 10f)
            dl.addRect(r.minX, r.minY, r.maxX, r.maxY, col(TangoPalette.SYNC.normal, a), 10f, 0, 2f)
            drawIcon(i, r, t, col(text, a))
            dl.addText(font, 22, r.maxX + 14f, r.centerY - 12f, col(text, a), CONTROLS[i])
        }
    }

    /** Small line icons: knob, audio bars, LFO sine, sequencer steps, a controller with four knobs. */
    private fun drawIcon(i: Int, r: WidgetRegistry.Rect, t: Double, c: Int) {
        val cx = r.centerX; val cy = r.centerY
        when (i) {
            0 -> {
                dl.addCircle(cx, cy, 15f, c, 24, 2.5f)
                val ang = -2.2 + 1.2 * kotlin.math.sin(t * 1.3)
                dl.addLine(cx, cy, cx + 13f * kotlin.math.cos(ang).toFloat(), cy + 13f * kotlin.math.sin(ang).toFloat(), c, 2.5f)
            }
            1 -> for (b in 0 until 5) {
                val h = 6f + 18f * (0.5f + 0.5f * kotlin.math.sin((t * 5.0 + b * 1.7)).toFloat())
                dl.addRectFilled(cx - 16f + b * 7f, cy + 12f - h, cx - 12f + b * 7f, cy + 12f, c)
            }
            2 -> {
                var px = cx - 17f; var py = cy
                for (s in 1..24) {
                    val x = cx - 17f + s * 34f / 24f
                    val y = cy - 10f * kotlin.math.sin(s / 24.0 * 2 * Math.PI + t * 2.0).toFloat()
                    dl.addLine(px, py, x, y, c, 2.5f); px = x; py = y
                }
            }
            3 -> {
                val hs = floatArrayOf(0.4f, 1f, 0.2f, 0.7f)
                val on = ((t * 2.0).toInt() % 4)
                for (s in 0 until 4) {
                    val h = 4f + 18f * hs[s]
                    val x = cx - 16f + s * 8.5f
                    if (s == on) dl.addRectFilled(x, cy + 11f - h, x + 6f, cy + 11f, c) else dl.addRect(x, cy + 11f - h, x + 6f, cy + 11f, c, 0f, 0, 1.5f)
                }
            }
            else -> {
                dl.addRect(cx - 17f, cy - 13f, cx + 17f, cy + 13f, c, 4f, 0, 2f)
                for (q in 0 until 4) dl.addCircle(cx - 8f + (q % 2) * 16f, cy - 5f + (q / 2) * 10f, 3.5f, c, 12, 1.5f)
            }
        }
    }

    // ---- Mini-map ----

    private fun lit(n: Node, keys: List<String>) = keys.any { k ->
        k == "all" || n.id == k || n.group == k || (k.endsWith(".fx") && n.id.startsWith(k)) || (k == "master" && n.group == "master")
    }

    private fun drawMini(t: Double, c: Cam, a: Float) {
        if (a <= 0f) return
        val x0 = sx(c, MINI_X0) - MINI_PAD; val y0 = sy(c, MINI_Y0) - MINI_PAD
        val x1 = sx(c, MINI_X1) + MINI_PAD; val y1 = sy(c, MINI_Y1) + MINI_PAD
        dl.addRectFilled(x0, y0, x1, y1, col(bgDark, a * 0.92f), 8f)
        dl.addRect(x0, y0, x1, y1, col(textDim, a * 0.6f), 8f, 0, 1f)
        val dy = sy(c, DIVIDER_Y)
        dl.addLine(sx(c, 60f), dy, sx(c, 1860f).coerceAtMost(x1), dy, col(textDim, a * 0.5f), 1f)
        for (w in wires) {
            val f = byId.getValue(w.from); val to = byId.getValue(w.to)
            val on = lit(f, miniLit) && lit(to, miniLit)
            val xa = sx(c, f.x + f.w); val ya = sy(c, f.cy)
            val xb = if (w.intoBottom) sx(c, to.x + to.w / 2) else sx(c, to.x)
            val yb = if (w.intoBottom) sy(c, to.y + to.h) else sy(c, to.cy)
            val mid = (xa + xb) / 2
            if (w.intoBottom) dl.addBezierCubic(xa, ya, xb, ya, xb, ya, xb, yb, col(textDim, a * if (on) 0.9f else 0.35f), 1.2f)
            else dl.addBezierCubic(xa, ya, mid, ya, mid, yb, xb, yb, col(textDim, a * if (on) 0.9f else 0.35f), 1.2f)
        }
        for (n in nodes) {
            val on = lit(n, miniLit)
            val nx0 = sx(c, n.x); val ny0 = sy(c, n.y); val nx1 = sx(c, n.x + n.w); val ny1 = sy(c, n.y + n.h)
            val hue = if (n.kind == Kind.FX) fxAccent else accent(n)
            val pulse = if (on) 0.75f + 0.25f * kotlin.math.sin((t - miniStart) * 5.0).toFloat() else 0f
            dl.addRectFilled(nx0, ny0, nx1, ny1, col(hue, a * if (on) pulse else 0.18f), 2f)
            dl.addRect(nx0, ny0, nx1, ny1, col(hue, a * if (on) 1f else 0.4f), 2f, 0, 1f)
        }
    }
}
