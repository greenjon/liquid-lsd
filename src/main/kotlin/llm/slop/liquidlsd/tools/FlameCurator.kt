package llm.slop.liquidlsd.tools

import kotlinx.serialization.encodeToString
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.rendering.*
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import java.awt.image.BufferedImage
import java.io.File
import kotlin.random.Random
import javax.imageio.ImageIO
import kotlin.math.ln

/**
 * Offline generator of the shipped Fractal Flame library: searches for interesting genomes with a quality-diversity
 * loop (a MAP-Elites archive over fill, structure, hue and symmetry) and writes each survivor as a preset, a
 * thumbnail and a row in `sheet.html`, a contact sheet to accept or reject by eye. Every stage is a filter:
 * [FlameViability] (numbers only), the [FlameProbe] gate (points on the GPU), then a real render scored by
 * [FlameAesthetics], then the same render with the performance handles pushed (must still look good).
 *
 * Run from the repo root (it needs a GL 3.3 context):
 *   ./gradlew curateFlames -Pargs="--candidates 20000 --out build/curated-flames"
 *   ./gradlew curateFlames -Pargs="--prune build/curated-flames rejected.txt"
 */
object FlameCurator {
    private const val W = 512
    private const val H = 288
    private const val THUMB_W = 256
    private const val THUMB_H = 144
    private const val FRAMES = 14
    private const val DECAY = 0.85f
    private const val SMOOTH = 0.3f

    private class Config(
        var candidates: Int = 20000, var out: File = File("build/curated-flames"), var seed: Long = 1L,
        var perCell: Int = 2, var minScore: Float = 0.35f, var minutes: Float = 0f
    )

    private class Elite(val name: String, val values: Map<String, Float>, val extras: Map<String, Float>, val score: Float, val metrics: FlameAesthetics.Metrics, val image: BufferedImage, val cell: Int)

    @JvmStatic fun main(args: Array<String>) {
        if (args.firstOrNull() == "--prune") { prune(File(args[1]), File(args[2])); return }
        val cfg = Config()
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--candidates" -> cfg.candidates = args[++i].toInt()
                "--out" -> cfg.out = File(args[++i])
                "--seed" -> cfg.seed = args[++i].toLong()
                "--per-cell" -> cfg.perCell = args[++i].toInt()
                "--min-score" -> cfg.minScore = args[++i].toFloat()
                "--minutes" -> cfg.minutes = args[++i].toFloat()
                else -> error("unknown argument ${args[i]}")
            }
            i++
        }
        check(glfwInit()) { "GLFW unavailable" }
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE); glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        val window = glfwCreateWindow(64, 64, "Flame curator", 0L, 0L)
        check(window != 0L) { "No offscreen GL context" }
        glfwMakeContextCurrent(window)
        GL.createCapabilities()
        try { run(cfg) } finally { glfwDestroyWindow(window) }
    }

    private fun resource(path: String) = FlameCurator::class.java.classLoader.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }

    private fun run(cfg: Config) {
        // judge every flame at full points, as a machine without a governor would draw it (in-memory settings only)
        llm.slop.liquidlsd.ui.UITheme.flameBudgetMs = 0f
        llm.slop.liquidlsd.ui.UITheme.flameQuality = FlameQualityTier.MEDIUM
        val shader = Shader(resource("shaders/blit.vert"), resource("default_sources/fractal_flame/shader.frag"))
        val src = FlameSource(FlameSource.SOURCE_ID, "Fractal Flame", shader, FlameGenome.buildParameters(), ownsShader = true)
        val params = src.parameters
        val probe = FlameProbe()
        val fbo = FBO(W, H)
        val pixels = BufferUtils.createByteBuffer(W * H * 4)
        val rnd = Random(cfg.seed)

        val genomeNames = params.filter { (n, p) -> p.randomizeBase && !n.startsWith("XF") }.keys
        val defaults = genomeNames.associateWith { params[it]!!.defaultValue }
        fun stored(name: String, real: Float) = FlameUnits.stored(name, real)

        /** Renders [values] (+ [extras], + handle overrides in [mods]) to a fresh image. */
        fun render(values: Map<String, Float>, extras: Map<String, Float>, mods: Map<String, Float>): Pair<ByteArray, BufferedImage> {
            for ((n, p) in params) p.baseValue = p.defaultValue
            for ((n, v) in values) params[n]?.baseValue = v
            for ((n, v) in extras) params[n]?.baseValue = v
            params["Decay"]!!.baseValue = stored("Decay", DECAY); params["Smooth"]!!.baseValue = SMOOTH
            for ((n, v) in mods) params[n]!!.baseValue = v
            src.update()
            src.clear()
            shader.bind()
            src.setupUniforms(shader)
            repeat(FRAMES) { src.renderTopology(fbo) }
            glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo.framebufferId)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
            val bytes = ByteArray(W * H * 4); pixels.get(bytes); pixels.rewind()
            val img = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until H) for (x in 0 until W) {
                val o = ((H - 1 - y) * W + x) * 4   // GL rows run bottom-up
                img.setRGB(x, y, ((bytes[o].toInt() and 255) shl 16) or ((bytes[o + 1].toInt() and 255) shl 8) or (bytes[o + 2].toInt() and 255))
            }
            // flip the byte rows too so the analysis sees the picture the right way up
            val up = ByteArray(bytes.size)
            for (y in 0 until H) System.arraycopy(bytes, (H - 1 - y) * W * 4, up, y * W * 4, W * 4)
            return up to img
        }

        val archive = HashMap<Int, MutableList<Elite>>()
        val counts = LinkedHashMap<String, Int>()
        fun count(k: String) { counts[k] = (counts[k] ?: 0) + 1 }
        val started = System.nanoTime()
        var named = 0
        var lastReport = started

        for (iter in 0 until cfg.candidates) {
            if (cfg.minutes > 0f && (System.nanoTime() - started) / 6e10f > cfg.minutes) break
            val now = System.nanoTime()
            if (now - lastReport > 15e9) { lastReport = now; report(iter, archive, counts, started) }

            // 1. propose: fresh random, or mutate / breed elites already in the archive
            val parents = archive.values.flatten()
            val values: Map<String, Float>
            var extras: Map<String, Float>
            if (parents.size < 30 || rnd.nextFloat() < 0.25f) {
                values = defaults + FlameSampler.random(params, rnd)
                extras = mapOf("Palette" to rnd.nextFloat())
            } else {
                val a = parents[rnd.nextInt(parents.size)]
                val strength = 0.1f + 0.5f * rnd.nextFloat()
                if (rnd.nextBoolean()) {
                    values = a.values + FlameSampler.mutate(params, { a.values[it] ?: defaults[it]!! }, strength, rnd)
                    extras = a.extras
                } else {
                    val b = parents[rnd.nextInt(parents.size)]
                    values = defaults + FlameSampler.breed(params, { a.values[it] ?: defaults[it]!! }, { b.values[it] ?: defaults[it]!! }, strength, rnd)
                    extras = if (rnd.nextBoolean()) a.extras else b.extras
                }
                if (rnd.nextFloat() < 0.3f) extras = mapOf("Palette" to ((extras["Palette"] ?: 0f) + 0.2f * (rnd.nextFloat() - 0.5f) + 1f) % 1f)
            }

            // 2. numbers only
            val via = FlameViability.check { values[it] ?: defaults[it] ?: 0f }
            if (!via.ok) { count("viability"); continue }
            // 3. points on the GPU
            val gate = probe.evaluate { values[it] }
            if (!gate.ok) { count("probe:${gate.verdict}"); continue }
            val framed = values + mapOf("Zoom" to stored("Zoom", gate.zoom), "CenterX" to stored("CenterX", gate.centerX), "CenterY" to stored("CenterY", gate.centerY))

            // 4. a real render
            val (bytes, img) = render(framed, extras, emptyMap())
            val m = FlameAesthetics.analyse(bytes, W, H)
            val base = FlameAesthetics.score(m) * (0.6f + 0.4f * via.balance)
            if (base < cfg.minScore) { count("aesthetics"); continue }
            val cell = cellOf(m)
            val list = archive.getOrPut(cell) { ArrayList() }
            if (list.size >= cfg.perCell && base <= list.minOf { it.score }) { count("cell-full"); continue }

            // 5. the performance handles must not break it
            val variants = listOf(
                mapOf("Spin" to 0.8f), mapOf("Spin" to -0.8f),
                mapOf("Warp" to stored("Warp", 0.3f)), mapOf("Breathe" to stored("Breathe", 0.85f))
            )
            val worst = variants.minOf { v ->
                val (vb, _) = render(framed, extras, v)
                FlameAesthetics.score(FlameAesthetics.analyse(vb, W, H))
            }
            val score = 0.6f * base + 0.4f * minOf(base, worst)
            if (score < cfg.minScore) { count("robustness"); continue }
            if (list.size >= cfg.perCell) {
                val weakest = list.minBy { it.score }
                if (score <= weakest.score) { count("cell-full"); continue }
                list.remove(weakest)
            }
            list += Elite("curated-%04d".format(++named), framed, extras, score, m, img, cell)
            count("accepted")
        }
        report(cfg.candidates, archive, counts, started)
        write(cfg, archive.values.flatten().sortedByDescending { it.score }, params)
        probe.dispose(); fbo.dispose(); src.dispose()
    }

    private fun thumbnail(img: BufferedImage): BufferedImage {
        val t = BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_RGB)
        val g = t.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(img.getScaledInstance(THUMB_W, THUMB_H, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
        g.dispose()
        return t
    }

    /** Fill (5) x structure (5) x hue (7: six hues + multicolour) x symmetry (3). */
    private fun cellOf(m: FlameAesthetics.Metrics): Int {
        val fill = ((m.coverage - 0.05f) / 0.85f * 5).toInt().coerceIn(0, 4)
        val structure = (ln(m.edgeFine.coerceAtLeast(0.004f) / 0.004f) / ln(0.12f / 0.004f) * 5).toInt().coerceIn(0, 4)
        val hue = if (m.colourSpread > 0.75f) 6 else (m.dominantHue / 2).coerceIn(0, 5)
        val sym = if (m.symmetry < 0.35f) 0 else if (m.symmetry < 0.7f) 1 else 2
        return ((fill * 5 + structure) * 7 + hue) * 3 + sym
    }

    private fun report(iter: Int, archive: Map<Int, List<Elite>>, counts: Map<String, Int>, started: Long) {
        val secs = (System.nanoTime() - started) / 1e9
        println("[%5.0fs] %d candidates, %d elites in %d/525 cells | %s".format(secs, iter, archive.values.sumOf { it.size }, archive.size, counts))
    }

    private fun write(cfg: Config, elites: List<Elite>, params: Map<String, llm.slop.liquidlsd.parameters.ModulatableParameter>) {
        cfg.out.mkdirs()
        val sb = StringBuilder(SHEET_HEAD)
        val csv = StringBuilder("name,score,cell,coverage,entropy,edgeFine,edgeCoarse,colourSpread,dominantHue,symmetry,flat,meanLuma\n")
        for (e in elites) {
            for ((n, p) in params) p.baseValue = p.defaultValue
            for ((n, v) in e.values) params[n]?.baseValue = v
            for ((n, v) in e.extras) params[n]?.baseValue = v
            params["Decay"]!!.baseValue = FlameUnits.stored("Decay", DECAY); params["Smooth"]!!.baseValue = SMOOTH
            val dto = DeckPresetDto(
                name = e.name, tags = listOf("curated", "flame"), visualSourceType = FlameSource.SOURCE_ID,
                parameters = params.mapValues { it.value.toDto() }, feedbackParameters = emptyMap(),
                presetNotes = "score %.2f".format(e.score)
            )
            File(cfg.out, e.name + ".lsd").writeText(PresetManager.json.encodeToString(dto))
            ImageIO.write(thumbnail(e.image), "png", File(cfg.out, e.name + ".png"))
            sb.append("<figure data-name=\"${e.name}\"><img src=\"${e.name}.png\" loading=\"lazy\"><figcaption>${e.name} ${"%.2f".format(e.score)}</figcaption></figure>\n")
            val m = e.metrics
            csv.append("%s,%.3f,%d,%.3f,%.3f,%.4f,%.4f,%.3f,%d,%.3f,%.3f,%.3f\n".format(e.name, e.score, e.cell, m.coverage, m.entropy, m.edgeFine, m.edgeCoarse, m.colourSpread, m.dominantHue, m.symmetry, m.flat, m.meanLuma))
        }
        sb.append(SHEET_TAIL)
        File(cfg.out, "sheet.html").writeText(sb.toString())
        File(cfg.out, "metrics.csv").writeText(csv.toString())
        println("Wrote ${elites.size} flames to ${cfg.out}. Open sheet.html, click the ones to REJECT, press Download, then:")
        println("  ./gradlew curateFlames -Pargs=\"--prune ${cfg.out} <downloaded rejected.txt>\"")
    }

    /** Deletes the presets and thumbnails named (one per line) in [rejected] from [dir]. */
    private fun prune(dir: File, rejected: File) {
        var n = 0
        for (name in rejected.readLines().map { it.trim() }.filter { Regex("curated-\\d+").matches(it) }) {
            for (ext in listOf("lsd", "png")) if (File(dir, "$name.$ext").delete()) n++
        }
        println("Removed $n files.")
    }

    private const val SHEET_HEAD = """<!doctype html><meta charset="utf-8"><title>Flame contact sheet</title>
<style>body{background:#111;color:#ccc;font:12px sans-serif;margin:0}#bar{position:sticky;top:0;background:#222;padding:8px;z-index:2}
main{display:flex;flex-wrap:wrap;gap:6px;padding:8px}figure{margin:0;width:256px;cursor:pointer;border:3px solid transparent}
figure.rej{border-color:#d33;opacity:.35}img{display:block;width:256px;height:144px}figcaption{padding:2px}</style>
<div id="bar"><b id="n"></b> rejected &nbsp; <button onclick="dl()">Download rejected.txt</button> &nbsp; click a flame to toggle reject (kept in this browser)</div><main>
"""
    private const val SHEET_TAIL = """</main><script>
const key='flameRejects:'+location.pathname;let rej=new Set(JSON.parse(localStorage.getItem(key)||'[]'));
const figs=[...document.querySelectorAll('figure')];
function upd(){figs.forEach(f=>f.classList.toggle('rej',rej.has(f.dataset.name)));document.getElementById('n').textContent=rej.size+' / '+figs.length;localStorage.setItem(key,JSON.stringify([...rej]))}
figs.forEach(f=>f.onclick=()=>{rej.has(f.dataset.name)?rej.delete(f.dataset.name):rej.add(f.dataset.name);upd()});
function dl(){const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([[...rej].join('\n')+'\n']));a.download='rejected.txt';a.click()}
upd()</script>
"""
}
