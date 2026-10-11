package llm.slop.liquidlsd.rendering

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlameAestheticsTest {
    private val w = 64; private val h = 64

    private fun image(f: (Int, Int) -> Int): ByteArray {
        val out = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val c = f(x, y)
            out[(y * w + x) * 4] = (c shr 16).toByte(); out[(y * w + x) * 4 + 1] = (c shr 8).toByte()
            out[(y * w + x) * 4 + 2] = c.toByte(); out[(y * w + x) * 4 + 3] = 255.toByte()
        }
        return out
    }

    @Test fun blackImageHasNoCoverageAndScoresZero() {
        val m = FlameAesthetics.analyse(image { _, _ -> 0 }, w, h)
        assertEquals(0f, m.coverage)
        assertEquals(0f, m.symmetry)
        assertEquals(0f, FlameAesthetics.score(m), 0.01f)
    }

    @Test fun staticHasHighEntropyAndEdges() {
        val r = java.util.Random(1)
        val noise = FlameAesthetics.analyse(image { _, _ -> r.nextInt(256).let { (it shl 16) or (it shl 8) or it } }, w, h)
        val flat = FlameAesthetics.analyse(image { _, _ -> 0x808080 }, w, h)
        assertTrue(noise.entropy > 0.9f, noise.toString())
        assertTrue(noise.edgeFine > 0.1f, noise.toString())
        assertEquals(0f, flat.edgeFine, 1e-6f)
        assertTrue(FlameAesthetics.score(noise) < 0.2f, "static must not score well: ${FlameAesthetics.score(noise)}")
    }

    @Test fun mirroredImageIsSymmetric() {
        val r = java.util.Random(2)
        val half = IntArray(w * h) { r.nextInt(256) }
        val sym = FlameAesthetics.analyse(image { x, y -> half[y * w + minOf(x, w - 1 - x)].let { (it shl 16) or (it shl 8) or it } }, w, h)
        val asym = FlameAesthetics.analyse(image { x, y -> half[y * w + x].let { (it shl 16) or (it shl 8) or it } }, w, h)
        assertTrue(sym.symmetry > 0.95f, sym.toString())
        assertTrue(asym.symmetry < 0.3f, asym.toString())
    }

    @Test fun hueSpreadAndDominance() {
        val one = FlameAesthetics.analyse(image { x, _ -> if (x in 16..48) 0xFF2020 else 0 }, w, h)
        val many = FlameAesthetics.analyse(image { x, _ ->
            when (x / 16) { 0 -> 0xFF2020; 1 -> 0x20FF20; 2 -> 0x2020FF; else -> 0xFFFF20 }
        }, w, h)
        assertTrue(one.dominantShare > 0.95f, one.toString())
        assertTrue(many.colourSpread > one.colourSpread + 0.4f, "${many.colourSpread} vs ${one.colourSpread}")
    }

    @Test fun bandIsATrapezoid() {
        assertEquals(0f, FlameAesthetics.band(0f, 1f, 2f, 3f, 4f))
        assertEquals(0.5f, FlameAesthetics.band(1.5f, 1f, 2f, 3f, 4f))
        assertEquals(1f, FlameAesthetics.band(2.5f, 1f, 2f, 3f, 4f))
        assertEquals(0.5f, FlameAesthetics.band(3.5f, 1f, 2f, 3f, 4f))
        assertEquals(0f, FlameAesthetics.band(5f, 1f, 2f, 3f, 4f))
    }
}
