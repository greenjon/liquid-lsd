package llm.slop.liquidlsd.tour

import imgui.ImGui
import llm.slop.liquidlsd.export.FFmpegProcessPipe
import llm.slop.liquidlsd.export.PboReadbackPipeline
import llm.slop.liquidlsd.export.VideoCodec
import llm.slop.liquidlsd.export.VideoExportConfig
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.utils.TimeSource
import mu.KotlinLogging
import java.io.File
import java.security.MessageDigest
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * Deterministic scripted-tour renderer. Drives the normal per-frame path on a fixed 60 fps simulated clock:
 * [beginFrame] -> (app render + UI, with [injectInput]/[drawOverlay] hooks inside UIManager) -> [endFrame]
 * (sync read of FBO 0, hash, pipe to ffmpeg). Nothing here reads wall-clock time except the timing statistics.
 */
class TourRunner(
    private val script: TourScript,
    private val outFile: File,
    dump: String? = null,
    private val scriptDir: File = File("."),
    private val options: TourOptions = TourOptions()
) {
    /** Set by Main once the mixer exists (scripted deck actions need it). */
    var mixer: llm.slop.liquidlsd.rendering.Mixer? = null
    /** Set by UIManager with the tour: scripted view actions (focus pair, Params, tabs) go through it. */
    var parametersState: llm.slop.liquidlsd.ui.ParametersState? = null
    private var audio: TourAudioFeeder? = null
    companion object {
        private const val CLICK_HOLD_SEC = 0.12
        private const val RIPPLE_SEC = 0.45
        private const val HIGHLIGHT_LINGER_SEC = 0.9
        private const val CAPTION_SIZE = 25f
        private const val CAPTION_WRAP = 540f
        private const val CAPTION_PAD_X = 16f
        private const val CAPTION_PAD_Y = 11f
        /** Reading pace for the caption lint (characters per second; a caption also gets at least [MIN_CAPTION_SEC]). */
        private const val READ_CPS = 14.0
        private const val MIN_CAPTION_SEC = 2.5

        /**
         * Uncaptured frames rendered before frame 0 so ImGui layout, fonts and first-use lazy init settle.
         * Background work is not waited for by sleeping: listing scans run inline (FileSystemManager.synchronousScans),
         * file watchers are off, and [PresetManager.awaitIo] is a barrier before every frame.
         */
        const val WARMUP_FRAMES = 10
    }

    private val fps = options.fps
    private val DT = 1.0 / fps
    val totalFrames = (script.length * fps).toInt()
    var frame = 0
        private set
    val isDone get() = frame >= totalFrames

    private val steps = script.steps.sortedBy { it.t }
    private var nextStep = 0

    private var curX = 960f
    private var curY = 540f
    private var moveFromX = 0f
    private var moveFromY = 0f
    private var moveToX = 0f
    private var moveToY = 0f
    private var moveStart = 0.0
    private var moveDur = 0.0
    private var moving = false
    private var pressUntil = -1.0
    private var rippleStart = -1.0
    private var rippleX = 0f
    private var rippleY = 0f
    private var mouseDown = false
    private var highlight: WidgetRegistry.Rect? = null
    private var highlightFrom = 0.0
    private var caption: String? = null
    private var captionLines: List<String> = emptyList()
    private var captionPos = "below"
    /** What the caption box sits beside: the last move's target rect (or its point). Drags and clicks leave it alone. */
    private var anchor = WidgetRegistry.Rect(960f, 540f, 960f, 540f)
    private var boxX = Float.NaN
    private var boxY = 0f

    /** A caption cue: [text] from [start] until the next captioned step ("" clears) or the tour end. */
    class Cue(val start: Double, val end: Double, val text: String)

    val cues: List<Cue> by lazy {
        val captioned = steps.filter { it.caption != null }
        captioned.withIndex().filter { it.value.caption!!.isNotBlank() && it.value.t < script.length }.map { (i, s) ->
            Cue(s.t, minOf(captioned.getOrNull(i + 1)?.t ?: script.length, script.length), s.caption!!)
        }
    }

    private var pipe: FFmpegProcessPipe? = null
    private var pbo: PboReadbackPipeline? = null
    private var frameW = 0
    private var frameH = 0
    private val digest = MessageDigest.getInstance("SHA-256")
    private val hashes = StringBuilder()
    private var startNs = 0L
    private var maxFrameMs = 0.0
    private var awaitNs = 0L
    private var readNs = 0L
    private var hashNs = 0L
    private var pushNs = 0L
    private var appNs = 0L
    private var maxBass = 0f
    private var maxFft = 0f
    private val fftProbe = FloatArray(512)
    private val waveProbe = FloatArray(512)
    private var lastFrameNs = 0L

    /** Debug aid: --tour-dump=<dir>:70,81 writes those frames as raw RGBA (bottom-up) for diffing determinism failures. */
    private val dumpFrames: Pair<Set<Int>, File>? = dump?.let {
        val dir = it.substringBefore(':')
        File(dir).mkdirs()
        it.substringAfter(':').split(',').mapNotNull { n -> n.trim().toIntOrNull() }.toSet() to File(dir)
    }

    private var warm = 0
    private val warming get() = warm < WARMUP_FRAMES
    private val now get() = frame * DT

    fun begin() {
        TimeSource.setSimulatedTime(0.0, DT)
        WidgetRegistry.enabled = true
        UITheme.tooltipsEnabled = script.tooltips
        for (c in cues) {
            val need = maxOf(MIN_CAPTION_SEC, c.text.length / READ_CPS)
            if (c.end - c.start < need - 1e-6) warnTour(String.format(Locale.ROOT, "Tour t=%.2f: caption shown %.1f s, needs about %.1f s to read: \"%s\"", c.start, c.end - c.start, need, c.text))
        }
        startNs = System.nanoTime()
        lastFrameNs = startNs
    }

    /** Called when the warm-up ends: clocks, beat anchor and analyzer state restart so script time 0 is frame 0. */
    private fun startCapture() {
        TimeSource.setSimulatedTime(WARMUP_FRAMES * DT, DT)
        llm.slop.liquidlsd.cv.CVRegistry.resetClocksFromTimeSource()
        llm.slop.liquidlsd.audio.AudioEngine.resetForOfflineFeed(script.bpm.toFloat(), TimeSource.getTimeNanos())
        script.audio?.let { name ->
            val f = File(name).let { if (it.isAbsolute) it else File(scriptDir, name) }
            audio = TourAudioFeeder(f, TimeSource.getTimeNanos(), fps)
        }
        startNs = System.nanoTime()
        lastFrameNs = startNs
    }

    /** Sets the simulated clock for [frame] and advances the script state machine. Call before any per-frame app work. */
    fun beginFrame() {
        val t = now
        TimeSource.setSimulatedTime((warm + frame) * DT, DT)
        WidgetRegistry.frame = warm + frame
        if (warming) return
        val a0 = System.nanoTime()
        llm.slop.liquidlsd.presets.PresetManager.awaitIo() // loads requested by last frame's UI have queued their results
        awaitNs += System.nanoTime() - a0
        audio?.feed(frame.toLong())
        while (nextStep < steps.size && steps[nextStep].t <= t + 1e-9) {
            val st = steps[nextStep++]
            if (st.caption != null) {
                caption = st.caption.takeIf { it.isNotBlank() }
                captionPos = st.captionPos ?: "below"
                captionLines = emptyList() // re-wrapped on the UI thread with the caption font
            }
            startStep(st, t)
        }
        if (moving) {
            val k = ((t - moveStart) / moveDur).coerceIn(0.0, 1.0)
            val e = (k * k * (3 - 2 * k)).toFloat()
            curX = moveFromX + (moveToX - moveFromX) * e
            curY = moveFromY + (moveToY - moveFromY) * e
            if (k >= 1.0) moving = false
        }
        mouseDown = t < pressUntil
    }

    private fun resolve(step: TourStep): Pair<Float, Float>? {
        if (step.target != null) {
            val r = WidgetRegistry.find(step.target)
            if (r == null) {
                warnTour("Tour t=${step.t}: target '${step.target}' not registered (known: ${WidgetRegistry.keys().size} widgets)")
                return null
            }
            if (!WidgetRegistry.isCurrent(step.target)) warnTour("Tour t=${step.t}: target '${step.target}' is not on screen now (last drawn earlier; wrong page or view?)")
            return r.centerX to r.centerY
        }
        if (step.x != null && step.y != null) return step.x to step.y
        return null
    }

    private fun startStep(step: TourStep, t: Double) {
        when (step.action) {
            "move" -> resolve(step)?.let { (x, y) ->
                highlight = step.target?.let { WidgetRegistry.find(it) }
                anchor = highlight ?: WidgetRegistry.Rect(x, y, x, y)
                highlightFrom = t
                moveFromX = curX; moveFromY = curY; moveToX = x; moveToY = y
                moveStart = t; moveDur = (step.durationSec ?: 0.6).coerceAtLeast(DT); moving = true
            }
            "drag" -> {
                // Press at the cursor, drag by (dx, dy) over durationSec, release. Move to the knob first with a "move" step.
                val dur = (step.durationSec ?: 0.8).coerceAtLeast(DT)
                moveFromX = curX; moveFromY = curY; moveToX = curX + (step.dx ?: 0f); moveToY = curY + (step.dy ?: 0f)
                moveStart = t; moveDur = dur; moving = true
                pressUntil = t + dur + DT
                rippleStart = t; rippleX = curX; rippleY = curY
            }
            "click" -> {
                pressUntil = t + (step.durationSec ?: CLICK_HOLD_SEC)
                rippleStart = t; rippleX = curX; rippleY = curY
            }
            "set_page" -> step.target?.let { UITheme.performancePageId = it }
            "hold", "chapter" -> {}
            "tooltips" -> UITheme.tooltipsEnabled = (step.value ?: 1f) >= 0.5f
            "load_source" -> deckAction(step) { slot, mx ->
                val id = step.target ?: return@deckAction
                val src = llm.slop.liquidlsd.rendering.VisualSourceRegistry.availableSources.firstOrNull { it.id == id }
                if (src == null) warnTour("Tour t=${step.t}: unknown source '$id'")
                else llm.slop.liquidlsd.presets.DeckOps.request(slot, llm.slop.liquidlsd.presets.DeckChange.Source(src))
            }
            "load_preset" -> deckAction(step) { slot, _ ->
                val f = File(step.target ?: return@deckAction).let { if (it.isAbsolute) it else File(scriptDir, it.path).takeIf { x -> x.exists() } ?: it }
                llm.slop.liquidlsd.presets.DeckOps.request(slot, llm.slop.liquidlsd.presets.DeckChange.Preset(f))
            }
            "set_param" -> deckAction(step) { slot, mx ->
                val p = slot.deck(mx).source.parameters[step.param]
                if (p == null) warnTour("Tour t=${step.t}: deck ${step.deck} has no parameter '${step.param}'")
                else p.set(step.value ?: 0f)
            }
            "bind_modulator" -> deckAction(step) { slot, mx ->
                val p = slot.deck(mx).source.parameters[step.param]
                if (p == null) warnTour("Tour t=${step.t}: deck ${step.deck} has no parameter '${step.param}'")
                else p.modulators.add(
                    llm.slop.liquidlsd.parameters.CvModulator(
                        sourceId = step.target ?: "lfo",
                        depth = step.value ?: 0.3f,
                        genUnit = if (step.unit.equals("BEAT", true)) llm.slop.liquidlsd.parameters.GenUnit.BEAT else llm.slop.liquidlsd.parameters.GenUnit.TIME
                    )
                )
            }
            "fx_filter" -> fxChain(step)?.let { c ->
                val slot = slotIndex(step) ?: return@let
                llm.slop.liquidlsd.presets.FxOps.setSlotFilter(c, slot, step.target?.takeIf { it.isNotBlank() })
            }
            "fx_enable" -> fxChain(step)?.let { c ->
                val slot = slotIndex(step) ?: return@let
                llm.slop.liquidlsd.presets.FxOps.setSlotEnabled(c, slot, (step.value ?: 1f) >= 0.5f)
            }
            "fx_chain_enable" -> fxChain(step)?.let { it.enabled = (step.value ?: 1f) >= 0.5f }
            "fx_chain" -> fxChain(step)?.let { c ->
                val f = File(step.target ?: return@let).let { if (it.isAbsolute) it else File(scriptDir, it.path).takeIf { x -> x.exists() } ?: it }
                try {
                    val dto = llm.slop.liquidlsd.presets.PresetManager.json.decodeFromString<llm.slop.liquidlsd.models.FXChainDto>(f.readText())
                    llm.slop.liquidlsd.presets.FxOps.applyChain(c, dto, source = f, isBaseline = true)
                } catch (e: Exception) {
                    warnTour("Tour t=${step.t}: cannot load FX chain '${f.path}': ${e.message}")
                }
            }
            "fx_swap" -> fxChain(step)?.let { c ->
                val a = slotIndex(step) ?: return@let
                val b = step.slot2
                if (b == null || b !in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT || b == a) warnTour("Tour t=${step.t}: fx_swap needs a different slot2")
                else llm.slop.liquidlsd.presets.FxOps.swapSlots(c, a, c, b)
            }
            "fx_clear" -> fxChain(step)?.let { llm.slop.liquidlsd.presets.FxOps.clearChain(it) }
            "fx_link" -> fxChain(step)?.let { c ->
                val slot = slotIndex(step) ?: return@let
                c.setSlotLinked(slot, (step.value ?: 1f) >= 0.5f)
            }
            "fx_dry_wet" -> fxChain(step)?.dryWet?.set(step.value ?: 1f)
            "fx_param" -> fxChain(step)?.let { c ->
                val slot = slotIndex(step) ?: return@let
                val p = c.slots[slot]?.parameters?.get(step.param)
                if (p == null) warnTour("Tour t=${step.t}: ${step.deck} slot $slot has no parameter '${step.param}'")
                else p.set(step.value ?: 0f)
            }
            // Super Knob and slot Metaknob are separate on purpose: the Super Knob drives linked slots' Metaknobs by soft-takeover.
            // The FX row's macro knobs drive these through bindings every frame, so set the knob (as a hand on it would);
            // a linked slot's knob is unbound and mirrors its Metaknob, so that one is set directly.
            "super_knob" -> fxChain(step)?.let { c -> fxKnob(c, 0)?.value = step.value ?: 0f }
            "meta_knob" -> fxChain(step)?.let { c ->
                val slot = slotIndex(step) ?: return@let
                val fx = c.slots[slot] ?: run { warnTour("Tour t=${step.t}: ${step.deck} slot $slot is empty"); return@let }
                if (c.slotSuperKnobLink[slot]) fx.metaKnob.set(step.value ?: 0f) else fxKnob(c, slot + 1)?.value = step.value ?: 0f
            }
            "transition" -> llm.slop.liquidlsd.presets.TransitionOps.setStock(step.target)
            "transition_preset" -> {
                val f = File(step.target ?: return).let { if (it.isAbsolute) it else File(scriptDir, it.path).takeIf { x -> x.exists() } ?: it }
                llm.slop.liquidlsd.presets.TransitionOps.loadPreset(f)
                llm.slop.liquidlsd.presets.PresetManager.awaitIo()
            }
            "library_mode" -> when (step.target?.uppercase()) {
                "FULL" -> UITheme.libraryMode = UITheme.LibraryMode.FULL
                "HALF" -> UITheme.libraryMode = UITheme.LibraryMode.HALF
                else -> warnTour("Tour t=${step.t}: library_mode wants FULL or HALF")
            }
            "open_params" -> {
                val id = step.target ?: return
                if (llm.slop.liquidlsd.ui.PerfRows.CATALOG.values.none { it.bankId == id }) warnTour("Tour t=${step.t}: open_params: unknown module '$id' (e.g. deckA, deckA_fx, master)")
                else parametersState?.openParams(id)
            }
            "open_params_pair" -> parametersState?.openParamsForPair(step.target ?: return)
            "focus_pair" -> parametersState?.focusPair(step.target ?: return)
            "close_focus" -> parametersState?.let { it.leavePair(); it.collapseAllRackModules() }
            "preferences" -> {
                val cat = step.target?.let { n -> llm.slop.liquidlsd.ui.PreferencesPanel.Category.entries.firstOrNull { it.name.equals(n, true) } }
                if (step.target != null && cat == null) warnTour("Tour t=${step.t}: unknown preferences category '${step.target}'")
                else llm.slop.liquidlsd.ui.PreferencesPanel.open(cat)
            }
            "preferences_close" -> llm.slop.liquidlsd.ui.PreferencesPanel.close()
            "theme" -> when (step.target?.lowercase()) {
                "light" -> UITheme.theme = UITheme.Theme.ORANGE_SUNSHINE
                "dark" -> UITheme.theme = UITheme.Theme.GREY_ACID
                else -> warnTour("Tour t=${step.t}: theme wants light or dark")
            }
            "crossfade" -> mixer?.crossfade?.set(step.value ?: -1f)
            else -> warnTour("Tour: unknown action '${step.action}'")
        }
    }

    /** Knob [index] of [chain]'s FX row bank (group mode: 0 = Super Knob, 1-3 = slot Metaknobs). */
    private fun fxKnob(chain: llm.slop.liquidlsd.rendering.FxChain, index: Int) =
        mixer?.let { llm.slop.liquidlsd.macro.FxMacroSync.bankIdFor(chain, it) }
            ?.let { llm.slop.liquidlsd.macro.MacroEngine.getBank(it) }?.knobs?.getOrNull(index)

    /** Deck A/B/BG/PV or MASTER -> its FX chain. */
    private fun fxChain(step: TourStep): llm.slop.liquidlsd.rendering.FxChain? {
        val mx = mixer ?: return null
        if (step.deck.equals("MASTER", true)) return mx.masterFxChain
        val slot = llm.slop.liquidlsd.presets.DeckSlot.entries.firstOrNull { it.name.equals(step.deck, true) }
        if (slot == null) { warnTour("Tour t=${step.t}: bad deck '${step.deck}' (A, B, BG, PV or MASTER)"); return null }
        return slot.deck(mx).fxChain
    }

    private fun slotIndex(step: TourStep): Int? {
        val i = step.slot
        if (i == null || i !in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) {
            warnTour("Tour t=${step.t}: ${step.action} needs slot 0..${llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT - 1}, got $i")
            return null
        }
        return i
    }

    private fun warnTour(msg: String) {
        logger.warn { msg }
        println("TOUR_WARN $msg")
    }

    private fun deckAction(step: TourStep, block: (llm.slop.liquidlsd.presets.DeckSlot, llm.slop.liquidlsd.rendering.Mixer) -> Unit) {
        val mx = mixer ?: return
        val slot = llm.slop.liquidlsd.presets.DeckSlot.entries.firstOrNull { it.name.equals(step.deck, true) }
        if (slot == null) { warnTour("Tour t=${step.t}: bad deck '${step.deck}'"); return }
        block(slot, mx)
        // Join the preset IO thread so the result is queued; Main drains DeckOps/FxOps/TransitionOps later this same frame.
        llm.slop.liquidlsd.presets.PresetManager.awaitIo()
    }

    /** Called by UIManager between imguiGlfw.newFrame() and ImGui.newFrame(). */
    fun injectInput() {
        val io = ImGui.getIO()
        io.deltaTime = DT.toFloat()
        io.addFocusEvent(true) // OS focus changes (compositor timing) must not clear hover state
        io.addMousePosEvent(curX, curY)
        io.addMouseButtonEvent(0, mouseDown)
    }

    /** Called by UIManager just before ImGui.render(): synthetic cursor + click ripple in the foreground draw list. */
    fun drawOverlay() {
        val dl = ImGui.getForegroundDrawList()
        val t = now
        if (rippleStart >= 0 && t - rippleStart < RIPPLE_SEC) {
            val k = ((t - rippleStart) / RIPPLE_SEC).toFloat()
            val a = ((1f - k) * 255).toInt()
            dl.addCircle(rippleX, rippleY, 6f + 30f * k, (a shl 24) or 0x00FFFFFF, 32, 3f)
        }
        highlight?.let { r ->
            // Ring from the start of the approach until the cursor has been there a moment, then it fades.
            val endAt = highlightFrom + moveDur + HIGHLIGHT_LINGER_SEC
            if (t < endAt) {
                val fade = ((endAt - t) / HIGHLIGHT_LINGER_SEC).coerceIn(0.0, 1.0).toFloat()
                val pulse = 0.5f + 0.5f * kotlin.math.sin((t * 6.0).toFloat())
                val a = (fade * (170 + 70 * pulse)).toInt().coerceIn(0, 255)
                dl.addRect(r.minX - 4f, r.minY - 4f, r.maxX + 4f, r.maxY + 4f, (a shl 24) or 0x003C9CFF, 6f, 0, 3f)
            }
        }
        val x = curX
        val y = curY
        val fill = if (mouseDown) 0xFF3C9CFF.toInt() else 0xFFFFFFFF.toInt()
        dl.addTriangleFilled(x, y, x, y + 21f, x + 15f, y + 15f, fill)
        dl.addTriangle(x, y, x, y + 21f, x + 15f, y + 15f, 0xFF000000.toInt(), 1.5f)
        if (script.burnCaptions) drawCaption()
    }

    /** Greedy word wrap at [maxW] pixels for [font] at [size]. */
    private fun wrap(text: String, font: imgui.ImFont, size: Float, maxW: Float): List<String> {
        val out = ArrayList<String>()
        for (para in text.split('\n')) {
            var line = ""
            for (word in para.split(' ')) {
                val cand = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && textW(font, size, cand) > maxW) { out += line; line = word } else line = cand
            }
            out += line
        }
        return out
    }

    private fun textW(font: imgui.ImFont, size: Float, text: String): Float {
        ImGui.pushFont(font, size)
        val w = ImGui.calcTextSize(text).x
        ImGui.popFont()
        return w
    }

    /**
     * Burned-in caption (script.burnCaptions). "cursor": an opaque box beside the cursor, below-right like a tooltip, flipping
     * left/up near the screen edges and gliding (fixed per-frame easing, so still deterministic). "bottom": a centred bar.
     */
    private fun drawCaption() {
        val text = caption ?: run { boxX = Float.NaN; return }
        val font = UITheme.fontFor(UITheme.FontLevel.H3) ?: ImGui.getFont()
        if (captionLines.isEmpty()) captionLines = wrap(text, font, CAPTION_SIZE, CAPTION_WRAP)
        val lineH = CAPTION_SIZE * 1.3f
        val textW = captionLines.maxOf { textW(font, CAPTION_SIZE, it) }
        val w = textW + CAPTION_PAD_X * 2
        val h = lineH * captionLines.size + CAPTION_PAD_Y * 2 - (lineH - CAPTION_SIZE)
        val sw = ImGui.getIO().displaySizeX
        val sh = ImGui.getIO().displaySizeY
        var tx: Float
        var ty: Float
        if (script.captionStyle == "bottom") {
            tx = (sw - w) * 0.5f
            ty = sh - h - 40f
        } else {
            // Beside the anchor (last move target). Below/above: left edge just left of its centre. Left/right: vertically
            // centred on it. Flip when it does not fit. A point anchor (x,y move) leaves room for the cursor arrow.
            val a = anchor
            val point = a.maxX - a.minX < 1f
            val belowY = a.maxY + if (point) 36f else 12f
            val aboveY = a.minY - 12f - h
            val rightX = a.maxX + if (point) 34f else 14f
            val leftX = a.minX - 14f - w
            when (captionPos) {
                "left", "right" -> {
                    val wantRight = captionPos == "right"
                    tx = if (wantRight && rightX + w <= sw - 12f || !wantRight && leftX < 12f) rightX else leftX
                    ty = a.centerY - h * 0.5f
                }
                else -> {
                    val wantAbove = captionPos == "above"
                    tx = a.centerX - 24f
                    ty = if (wantAbove && aboveY >= 12f || !wantAbove && belowY + h > sh - 12f) aboveY else belowY
                }
            }
        }
        val cx = tx.coerceIn(12f, sw - 12f - w)
        val cy = ty.coerceIn(12f, sh - 12f - h)
        // Glide: 20% of the way per 1/60 s, whatever the frame rate.
        val k = 1f - Math.pow(0.8, DT * 60.0).toFloat()
        if (boxX.isNaN()) { boxX = cx; boxY = cy } else { boxX += (cx - boxX) * k; boxY += (cy - boxY) * k }
        val dl = ImGui.getForegroundDrawList()
        dl.addRectFilled(boxX, boxY, boxX + w, boxY + h, 0xFF1C1A18.toInt(), 8f)
        dl.addRect(boxX, boxY, boxX + w, boxY + h, 0xFF3C9CFF.toInt(), 8f, 0, 2f)
        for ((i, line) in captionLines.withIndex()) {
            dl.addText(font, CAPTION_SIZE.toInt(), boxX + CAPTION_PAD_X, boxY + CAPTION_PAD_Y + i * lineH, 0xFFFFFFFF.toInt(), line)
        }
    }

    private var outW = 0
    private var outH = 0
    private var downFbo = 0
    private var downTex = 0

    private fun createDownscaleTarget() {
        downTex = org.lwjgl.opengl.GL11.glGenTextures()
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, downTex)
        org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA8, outW, outH, 0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, 0L)
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0)
        downFbo = org.lwjgl.opengl.GL30.glGenFramebuffers()
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, downFbo)
        org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, downTex, 0)
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0)
    }

    /** With a scale below 1, blits the window (FBO 0) into the small target with linear filtering; returns the FBO to read. */
    private fun downscaleToFbo(): Int {
        if (downFbo == 0) return 0
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, 0)
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, downFbo)
        org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, frameW, frameH, 0, 0, outW, outH, org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_LINEAR)
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0)
        return downFbo
    }

    /** Reads FBO 0 after the UI has been drawn and before the buffer swap. */
    fun endFrame(fbW: Int, fbH: Int) {
        if (warming) {
            warm++
            if (!warming) startCapture()
            return
        }
        if (pipe == null) {
            frameW = fbW
            frameH = fbH
            // Even dimensions for yuv420p.
            outW = (Math.round(fbW * options.scale) / 2 * 2).coerceAtLeast(2)
            outH = (Math.round(fbH * options.scale) / 2 * 2).coerceAtLeast(2)
            if (outW != fbW || outH != fbH) createDownscaleTarget()
            outFile.absoluteFile.parentFile?.mkdirs()
            val encoder = when (options.encoder) {
                "nvenc" -> "h264_nvenc"
                "x264" -> null
                else -> options.encoder // any ffmpeg encoder name
            }
            val p = FFmpegProcessPipe(
                VideoExportConfig(
                    outFile, outW, outH, fps, VideoCodec.H264, crf = if (encoder == "h264_nvenc") 23 else 18,
                    audioFile = script.audio?.let { n -> File(n).let { f -> if (f.isAbsolute) f else File(scriptDir, n) } },
                    forceSoftwareEncoder = true, padAudioToVideo = true, encoder = encoder
                )
            )
            check(p.start()) { "Failed to start ffmpeg: ${p.getRecentErrorLines(4)}" }
            pipe = p
            pbo = PboReadbackPipeline(outW, outH)
            println("TOUR_OUT ${outW}x$outH @ $fps fps, encoder ${encoder ?: "libx264"}, hashes ${if (options.hashes) "on" else "off"}, $totalFrames frames -> ${outFile.absolutePath}")
        }
        check(fbW == frameW && fbH == frameH) { "Tour: framebuffer size changed mid-render (${frameW}x$frameH -> ${fbW}x$fbH)" }
        maxBass = maxOf(maxBass, llm.slop.liquidlsd.cv.CVRegistry.get("audio_bass"))
        llm.slop.liquidlsd.audio.AudioEngine.copyAudioTextureData(fftProbe, waveProbe)
        for (v in fftProbe) if (v > maxFft) maxFft = v
        val r0 = System.nanoTime()
        appNs += r0 - lastFrameNs
        val buf = pbo!!.readFrameSync(downscaleToFbo())
        val r1 = System.nanoTime()
        if (options.hashes) {
            digest.reset()
            digest.update(buf)
            buf.rewind()
            hashes.append(frame).append(' ').append(digest.digest().joinToString("") { "%02x".format(it) }).append('\n')
        }
        dumpFrames?.let { d ->
            if (frame in d.first) {
                val bytes = ByteArray(buf.remaining())
                buf.get(bytes)
                buf.rewind()
                File(d.second, "frame_$frame.rgba").writeBytes(bytes)
            }
        }
        val r2 = System.nanoTime()
        pipe!!.pushFrame(buf)
        val n = System.nanoTime()
        readNs += r1 - r0; hashNs += r2 - r1; pushNs += n - r2
        maxFrameMs = maxOf(maxFrameMs, (n - lastFrameNs) / 1e6)
        lastFrameNs = n
        frame++
    }

    fun finish() {
        TimeSource.clearSimulatedTime()
        val ok = pipe?.finish() ?: false
        pbo?.dispose()
        val totalMs = (System.nanoTime() - startNs) / 1e6
        if (options.hashes) File(outFile.path + ".hashes").writeText(hashes.toString())
        if (downFbo != 0) { org.lwjgl.opengl.GL30.glDeleteFramebuffers(downFbo); org.lwjgl.opengl.GL11.glDeleteTextures(downTex) }
        val base = outFile.path.substringBeforeLast('.')
        writeVtt(File("$base.vtt"))
        writeSidecars(base)
        File("$base.keys").writeText(WidgetRegistry.keys().sorted().joinToString("\n", postfix = "\n"))
        val avg = totalMs / frame.coerceAtLeast(1)
        println(String.format(Locale.ROOT, "TOUR_DONE frames=%d total_ms=%.0f avg_ms=%.2f max_ms=%.1f ffmpeg_ok=%b await_ms=%.1f audio_bass_max=%.3f fft_max=%.3f", frame, totalMs, avg, maxFrameMs, ok, awaitNs / 1e6, maxBass, maxFft))
        val n = frame.coerceAtLeast(1) * 1e6
        println(String.format(Locale.ROOT, "TOUR_TIMING per frame ms: app+ui=%.2f readback=%.2f hash=%.2f encode_push=%.2f", appNs / n, readNs / n, hashNs / n, pushNs / n))
    }

    private fun ts(sec: Double): String {
        val ms = Math.round(sec * 1000)
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
    }

    private fun jsonStr(v: String) = buildString {
        append('"')
        for (c in v) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n")
            else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
        }
        append('"')
    }

    private fun chapterAt(t: Double): TourChapter? = script.allChapters.lastOrNull { it.t <= t + 1e-9 }

    /**
     * `<base>.chapters.json` (id, title, start, end, clip length), `<base>.captions.json` (index, start, end, chapter, text for the
     * interactive page and transcript) and `<base>.ffmeta` (ffmpeg chapter metadata, merged by scripts/render_tour.sh).
     */
    private fun writeSidecars(base: String) {
        val chapters = script.allChapters
        fun endOf(i: Int) = chapters[i].endSec ?: chapters.getOrNull(i + 1)?.t ?: script.length
        fun num(d: Double) = String.format(Locale.ROOT, "%.3f", d)
        File("$base.chapters.json").writeText(chapters.withIndex().joinToString(",\n", "[\n", "\n]\n") { (i, c) ->
            "  {\"id\":${jsonStr(c.id)},\"title\":${jsonStr(c.title)},\"start\":${num(c.t)},\"end\":${num(endOf(i))},\"clip\":${num(c.clipSec ?: (endOf(i) - c.t))}}"
        })
        File("$base.captions.json").writeText(cues.withIndex().joinToString(",\n", "[\n", "\n]\n") { (i, c) ->
            "  {\"index\":${i + 1},\"start\":${num(c.start)},\"end\":${num(c.end)},\"chapter\":${jsonStr(chapterAt(c.start)?.id ?: "")},\"text\":${jsonStr(c.text)}}"
        })
        File("$base.ffmeta").writeText(buildString {
            append(";FFMETADATA1\n")
            for ((i, c) in chapters.withIndex()) {
                append("[CHAPTER]\nTIMEBASE=1/1000\nSTART=").append(Math.round(c.t * 1000)).append("\nEND=").append(Math.round(endOf(i) * 1000))
                    .append("\ntitle=").append(c.title.replace("\\", "\\\\").replace("=", "\\=").replace(";", "\\;").replace("#", "\\#").replace("\n", " ")).append("\n")
            }
        })
    }

    private fun writeVtt(file: File) {
        val sb = StringBuilder("WEBVTT\n\n")
        for (c in cues) sb.append(ts(c.start)).append(" --> ").append(ts(c.end)).append('\n').append(c.text).append("\n\n")
        file.writeText(sb.toString())
    }
}
