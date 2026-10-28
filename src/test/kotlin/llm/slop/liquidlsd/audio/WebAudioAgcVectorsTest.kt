package llm.slop.liquidlsd.audio

import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden vectors for the web TV's auto gain (`AudioAgc` in web/dsp.js, `EnergyWatch` in web/energy.js):
 * the desktop [AudioAgc] and [SectionWatch] run over a synthetic set (steady music, a breakdown and drop,
 * silence, a quieter and a louder song), and `node --test web/tools/beatclock.test.mjs` replays the same
 * input through the port. Fails when the committed web/tools/agc_vectors.json is stale. Regenerate with
 * `UPDATE_WEB_VECTORS=1 ./gradlew test --tests '*WebAudioAgcVectorsTest*'`.
 */
class WebAudioAgcVectorsTest {
    private val projectRoot: File by lazy {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "web/sync_manifest.json").exists()) dir = dir.parentFile
        dir
    }

    private val dt = 0.05

    /** seconds, amp, bass, mid, high */
    private val segments = listOf(
        doubleArrayOf(30.0, 0.12, 0.2, 0.08, 0.03),   // steady music
        doubleArrayOf(20.0, 0.06, 0.02, 0.1, 0.03),   // breakdown: the low end goes, the mids get louder
        doubleArrayOf(15.0, 0.13, 0.22, 0.08, 0.03),  // the drop
        doubleArrayOf(3.0, 0.0, 0.0, 0.0, 0.0),       // a gap between sets
        doubleArrayOf(30.0, 0.03, 0.05, 0.02, 0.008), // a quieter feed
        doubleArrayOf(12.0, 0.1, 0.16, 0.07, 0.025),  // a louder song
        doubleArrayOf(70.0, 0.025, 0.035, 0.02, 0.008), // a long quiet stretch: the new normal
        doubleArrayOf(2.0, 0.025, 0.005, 0.02, 0.008), // a short dip
        doubleArrayOf(10.0, 0.025, 0.035, 0.02, 0.008),
    )

    private fun f(v: Float) = String.format(Locale.ROOT, "%.6g", v)

    @Test
    fun webVectorsMatchDesktop() {
        val agc = AudioAgc()
        val rms = mutableListOf<String>()
        val expect = mutableListOf<String>()
        var t = 0.0
        var i = 0
        var breakdowns = 0
        for (seg in segments) {
            repeat((seg[0] / dt).toInt()) {
                t += dt
                val wobble = 1.0 + 0.3 * sin(t * 2 * PI * 2.1) // a beat in the level
                // Rounded before use, so the web replays exactly the input the desktop saw.
                val b = FloatArray(4) { k -> String.format(Locale.ROOT, "%.5f", seg[k + 1] * wobble).toFloat() }
                rms += "[${b.joinToString(",") { it.toString() }}]"
                val before = agc.watch.section
                agc.update(b[0], b[1], b[2], b[3], dt)
                if (before != agc.watch.section && agc.watch.section == SectionWatch.Section.BREAKDOWN) breakdowns++
                if (i % 10 == 0) {
                    val div = (0 until AudioAgc.BANDS).joinToString(",") { f(agc.divisor(it)) }
                    expect += "{\"i\":$i,\"div\":[$div],\"section\":\"${agc.watch.section.name.lowercase()}\",\"drops\":${agc.watch.drops}}"
                }
                i++
            }
        }
        assertEquals(1, agc.watch.drops, "the synthetic set should contain exactly one drop")
        assertEquals(2, breakdowns, "a breakdown, then a long quiet stretch")
        val text = "{\n\"dt\": $dt,\n\"rms\": [\n${rms.joinToString(",\n")}\n],\n\"expect\": [\n${expect.joinToString(",\n")}\n]\n}\n"
        val file = File(projectRoot, "web/tools/agc_vectors.json")
        if (System.getenv("UPDATE_WEB_VECTORS") == "1" || !file.exists()) {
            file.writeText(text)
        } else {
            assertTrue(sameNumbers(file.readText(), text), "web/tools/agc_vectors.json is stale; regenerate with UPDATE_WEB_VECTORS=1")
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
