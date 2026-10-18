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
    private var audio: TourAudioFeeder? = null
    companion object {
        const val FPS = 60
        private const val DT = 1.0 / FPS
        private const val CLICK_HOLD_SEC = 0.12
        private const val RIPPLE_SEC = 0.45

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

    private var pipe: FFmpegProcessPipe? = null
    private var pbo: PboReadbackPipeline? = null
    private var frameW = 0
    private var frameH = 0
    private val digest = MessageDigest.getInstance("SHA-256")
    private val hashes = StringBuilder()
    private var startNs = 0L
    private var maxFrameMs = 0.0
    private var awaitNs = 0L
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
        while (nextStep < steps.size && steps[nextStep].t <= t + 1e-9) startStep(steps[nextStep++], t)
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
                moveFromX = curX; moveFromY = curY; moveToX = x; moveToY = y
                moveStart = t; moveDur = (step.durationSec ?: 0.6).coerceAtLeast(DT); moving = true
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
            else -> warnTour("Tour: unknown action '${step.action}'")
        }
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
        val x = curX
        val y = curY
        val fill = if (mouseDown) 0xFF3C9CFF.toInt() else 0xFFFFFFFF.toInt()
        dl.addTriangleFilled(x, y, x, y + 21f, x + 15f, y + 15f, fill)
        dl.addTriangle(x, y, x, y + 21f, x + 15f, y + 15f, 0xFF000000.toInt(), 1.5f)
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
        writeVtt(File(outFile.path.substringBeforeLast('.') + ".vtt"))
        val avg = totalMs / frame.coerceAtLeast(1)
        println(String.format(Locale.ROOT, "TOUR_DONE frames=%d total_ms=%.0f avg_ms=%.2f max_ms=%.1f ffmpeg_ok=%b await_ms=%.1f", frame, totalMs, avg, maxFrameMs, ok, awaitNs / 1e6))
    }

    private fun ts(sec: Double): String {
        val ms = Math.round(sec * 1000)
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
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
