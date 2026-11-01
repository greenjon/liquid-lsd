package llm.slop.liquidlsd.audio

import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Golden vectors for the web TV's shader spectrum (`SpectrumAnalyzer` in web/audiotex.js): the desktop
 * [SpectrumAnalyzer] runs over a short music-like signal (a kick, a tone, a sweep and hashed noise) in 512-sample
 * blocks, then silence, and `node --test web/tools/audiotex.test.mjs` replays the same input through the port.
 * Fails when the committed web/tools/spectrum_vectors.json is stale. Regenerate with
 * `UPDATE_WEB_VECTORS=1 ./gradlew test --tests '*WebSpectrumVectorsTest*'`.
 */
class WebSpectrumVectorsTest {
    private val projectRoot: File by lazy {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "web/sync_manifest.json").exists()) dir = dir.parentFile
        dir
    }

    private val sr = 48000f
    private val block = 512
    private val signalBlocks = 6
    private val silentBlocks = 4

    private fun f(v: Float) = String.format(Locale.ROOT, "%.6g", v)

    @Test
    fun webVectorsMatchDesktop() {
        var seed = 12345L
        val n = signalBlocks * block
        // Rounded before use, so the web replays exactly the input the desktop saw.
        val x = FloatArray(n) { i ->
            val t = i / sr.toDouble()
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            val noise = (seed.toDouble() / 0x7fffffffL - 0.5) * 0.1
            val kick = 0.6 * sin(2 * PI * 60 * t) * (1 - t * 10).coerceAtLeast(0.0)
            val v = kick + 0.2 * sin(2 * PI * 440 * t) + 0.15 * sin(2 * PI * (500 + 20000 * t) * t) + noise
            String.format(Locale.ROOT, "%.4f", v).toFloat()
        }
        val a = SpectrumAnalyzer()
        val b = FloatArray(block)
        val out = FloatArray(SpectrumAnalyzer.BINS)
        val snaps = mutableListOf<String>()
        for (k in 0 until signalBlocks + silentBlocks) {
            if (k < signalBlocks) System.arraycopy(x, k * block, b, 0, block) else b.fill(0f)
            a.process(b, block, sr)
            if (k == signalBlocks - 1 || k == signalBlocks + silentBlocks - 1) {
                a.copyTo(out)
                snaps += "{\"afterBlock\":$k,\"spectrum\":[${out.joinToString(",") { f(it) }}]}"
            }
        }
        val text = "{\n\"sampleRate\": ${sr.toInt()},\n\"block\": $block,\n\"signalBlocks\": $signalBlocks,\n" +
            "\"silentBlocks\": $silentBlocks,\n\"input\": [${x.joinToString(",") { it.toString() }}],\n" +
            "\"expect\": [\n${snaps.joinToString(",\n")}\n]\n}\n"
        val file = File(projectRoot, "web/tools/spectrum_vectors.json")
        if (System.getenv("UPDATE_WEB_VECTORS") == "1" || !file.exists()) {
            file.writeText(text)
        } else {
            assertTrue(sameNumbers(file.readText(), text), "web/tools/spectrum_vectors.json is stale; regenerate with UPDATE_WEB_VECTORS=1")
        }
    }

    private fun sameNumbers(a: String, b: String): Boolean {
        if (a == b) return true
        val num = Regex("-?[0-9.]+(e-?[0-9]+)?")
        val na = num.findAll(a).map { it.value.toDouble() }.toList()
        val nb = num.findAll(b).map { it.value.toDouble() }.toList()
        return num.replace(a, "#") == num.replace(b, "#") && na.size == nb.size &&
            na.indices.all { abs(na[it] - nb[it]) <= 1e-5 * maxOf(1.0, abs(na[it])) }
    }
}
