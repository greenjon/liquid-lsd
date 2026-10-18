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
class TourRunner(private val script: TourScript, private val outFile: File, dump: String? = null, private val scriptDir: File = File(".")) {
    /** Set by Main once the mixer exists (scripted deck actions need it). */
    var mixer: llm.slop.liquidlsd.rendering.Mixer? = null
    /** Set by UIManager with the tour: scripted view actions (focus pair, Params, tabs) go through it. */
    var parametersState: llm.slop.liquidlsd.ui.ParametersState? = null
    private var audio: TourAudioFeeder? = null
    companion object {
        const val FPS = 60
        private const val DT = 1.0 / FPS
        private const val CLICK_HOLD_SEC = 0.12
        private const val RIPPLE_SEC = 0.45
        private const val HIGHLIGHT_LINGER_SEC = 0.9
        private const val CAPTION_SIZE = 30f

        /**
         * Uncaptured frames rendered before frame 0 so ImGui layout, fonts and first-use lazy init settle.
         * Background work is not waited for by sleeping: listing scans run inline (FileSystemManager.synchronousScans),
         * file watchers are off, and [PresetManager.awaitIo] is a barrier before every frame.
         */
        const val WARMUP_FRAMES = 10
    }

    val totalFrames = (script.durationSec * FPS).toInt()
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

    private var pipe: FFmpegProcessPipe? = null
    private var pbo: PboReadbackPipeline? = null
    private var frameW = 0
    private var frameH = 0
    private val digest = MessageDigest.getInstance("SHA-256")
    private val hashes = StringBuilder()
    private var startNs = 0L
    private var maxFrameMs = 0.0
    private var awaitNs = 0L
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
            audio = TourAudioFeeder(f, TimeSource.getTimeNanos())
        }
        startNs = System.nanoTime()
        lastFrameNs = startNs
    }

    /** Sets the simulated clock for [frame] and advances the script state machine. Call before any per-frame app work. */
    fun beginFrame() {
        val t = now
        TimeSource.setSimulatedTime((warm + frame) * DT, DT)
        if (warming) return
        val a0 = System.nanoTime()
        llm.slop.liquidlsd.presets.PresetManager.awaitIo() // loads requested by last frame's UI have queued their results
        awaitNs += System.nanoTime() - a0
        audio?.feed(frame.toLong())
        while (nextStep < steps.size && steps[nextStep].t <= t + 1e-9) {
            val st = steps[nextStep++]
            if (st.caption != null) caption = st.caption.takeIf { it.isNotBlank() }
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
            return r.centerX to r.centerY
        }
        if (step.x != null && step.y != null) return step.x to step.y
        return null
    }

    private fun startStep(step: TourStep, t: Double) {
        when (step.action) {
            "move" -> resolve(step)?.let { (x, y) ->
                highlight = step.target?.let { WidgetRegistry.find(it) }
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
            "hold" -> {}
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
            "fx_chain" -> fxChain(step)?.let { c ->
                val f = File(step.target ?: return@let).let { if (it.isAbsolute) it else File(scriptDir, it.path).takeIf { x -> x.exists() } ?: it }
                try {
                    val dto = llm.slop.liquidlsd.presets.PresetManager.json.decodeFromString<llm.slop.liquidlsd.models.FXChainDto>(f.readText())
                    llm.slop.liquidlsd.presets.FxOps.applyChain(c, dto, source = f, isBaseline = true)
                } catch (e: Exception) {
                    warnTour("Tour t=${step.t}: cannot load FX chain '${f.path}': ${e.message}")
                }
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
            "super_knob" -> fxChain(step)?.superKnob?.set(step.value ?: 0f)
            "meta_knob" -> fxChain(step)?.let { c ->
                val slot = slotIndex(step) ?: return@let
                c.slots[slot]?.metaKnob?.set(step.value ?: 0f) ?: warnTour("Tour t=${step.t}: ${step.deck} slot $slot is empty")
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

    /** Burned-in caption bar (script.burnCaptions): centred near the bottom, drawn over everything incl. the cursor. */
    private fun drawCaption() {
        val text = caption ?: return
        val dl = ImGui.getForegroundDrawList()
        val font = ImGui.getFont()
        val sz = ImGui.calcTextSize(text).x * CAPTION_SIZE / ImGui.getFontSize()
        val w = ImGui.getIO().displaySizeX
        val h = ImGui.getIO().displaySizeY
        val x = (w - sz) * 0.5f
        val y = h - 90f
        dl.addRectFilled(x - 22f, y - 12f, x + sz + 22f, y + CAPTION_SIZE + 12f, 0xCC101010.toInt(), 10f)
        dl.addText(font, CAPTION_SIZE.toInt(), x, y, 0xFFFFFFFF.toInt(), text)
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
            outFile.absoluteFile.parentFile?.mkdirs()
            val p = FFmpegProcessPipe(
                VideoExportConfig(outFile, fbW, fbH, FPS, VideoCodec.H264, crf = 18, audioFile = script.audio?.let { n -> File(n).let { f -> if (f.isAbsolute) f else File(scriptDir, n) } }, forceSoftwareEncoder = true, padAudioToVideo = true)
            )
            check(p.start()) { "Failed to start ffmpeg: ${p.getRecentErrorLines(4)}" }
            pipe = p
            pbo = PboReadbackPipeline(fbW, fbH)
            logger.info { "Tour: capturing ${fbW}x$fbH, $totalFrames frames -> ${outFile.absolutePath}" }
        }
        check(fbW == frameW && fbH == frameH) { "Tour: framebuffer size changed mid-render (${frameW}x$frameH -> ${fbW}x$fbH)" }
        maxBass = maxOf(maxBass, llm.slop.liquidlsd.cv.CVRegistry.get("audio_bass"))
        llm.slop.liquidlsd.audio.AudioEngine.copyAudioTextureData(fftProbe, waveProbe)
        for (v in fftProbe) if (v > maxFft) maxFft = v
        val buf = pbo!!.readFrameSync(0)
        digest.reset()
        digest.update(buf)
        buf.rewind()
        hashes.append(frame).append(' ').append(digest.digest().joinToString("") { "%02x".format(it) }).append('\n')
        dumpFrames?.let { d ->
            if (frame in d.first) {
                val bytes = ByteArray(buf.remaining())
                buf.get(bytes)
                buf.rewind()
                File(d.second, "frame_$frame.rgba").writeBytes(bytes)
            }
        }
        pipe!!.pushFrame(buf)
        val n = System.nanoTime()
        maxFrameMs = maxOf(maxFrameMs, (n - lastFrameNs) / 1e6)
        lastFrameNs = n
        frame++
    }

    fun finish() {
        TimeSource.clearSimulatedTime()
        val ok = pipe?.finish() ?: false
        pbo?.dispose()
        val totalMs = (System.nanoTime() - startNs) / 1e6
        File(outFile.path + ".hashes").writeText(hashes.toString())
        val base = outFile.path.substringBeforeLast('.')
        writeVtt(File("$base.vtt"))
        writeSidecars(base)
        File("$base.keys").writeText(WidgetRegistry.keys().sorted().joinToString("\n", postfix = "\n"))
        val avg = totalMs / frame.coerceAtLeast(1)
        println(String.format(Locale.ROOT, "TOUR_DONE frames=%d total_ms=%.0f avg_ms=%.2f max_ms=%.1f ffmpeg_ok=%b await_ms=%.1f audio_bass_max=%.3f fft_max=%.3f", frame, totalMs, avg, maxFrameMs, ok, awaitNs / 1e6, maxBass, maxFft))
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

    private fun chapterAt(t: Double): TourChapter? = script.chapters.sortedBy { it.t }.lastOrNull { it.t <= t + 1e-9 }

    /**
     * `<base>.chapters.json` (id, title, start, end, clip length), `<base>.captions.json` (index, start, end, chapter, text for the
     * interactive page and transcript) and `<base>.ffmeta` (ffmpeg chapter metadata, merged by scripts/render_tour.sh).
     */
    private fun writeSidecars(base: String) {
        val chapters = script.chapters.sortedBy { it.t }
        fun endOf(i: Int) = chapters[i].endSec ?: chapters.getOrNull(i + 1)?.t ?: script.durationSec
        fun num(d: Double) = String.format(Locale.ROOT, "%.3f", d)
        File("$base.chapters.json").writeText(chapters.withIndex().joinToString(",\n", "[\n", "\n]\n") { (i, c) ->
            "  {\"id\":${jsonStr(c.id)},\"title\":${jsonStr(c.title)},\"start\":${num(c.t)},\"end\":${num(endOf(i))},\"clip\":${num(c.clipSec ?: (endOf(i) - c.t))}}"
        })
        val cues = steps.filter { !it.caption.isNullOrBlank() }
        File("$base.captions.json").writeText(cues.withIndex().joinToString(",\n", "[\n", "\n]\n") { (i, c) ->
            val end = cues.getOrNull(i + 1)?.t ?: script.durationSec
            "  {\"index\":${i + 1},\"start\":${num(c.t)},\"end\":${num(end)},\"chapter\":${jsonStr(chapterAt(c.t)?.id ?: "")},\"text\":${jsonStr(c.caption!!)}}"
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
        val cues = steps.filter { !it.caption.isNullOrBlank() }
        val sb = StringBuilder("WEBVTT\n\n")
        for ((i, c) in cues.withIndex()) {
            val end = cues.getOrNull(i + 1)?.t ?: script.durationSec
            sb.append(ts(c.t)).append(" --> ").append(ts(end)).append('\n').append(c.caption).append("\n\n")
        }
        file.writeText(sb.toString())
    }
}
